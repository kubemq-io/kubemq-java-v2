package io.kubemq.sdk.auth;

import io.kubemq.sdk.exception.AuthenticationException;
import io.kubemq.sdk.exception.ConnectionException;
import io.kubemq.sdk.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages credential lifecycle: caching, serialized retrieval,
 * reactive invalidation, and proactive refresh scheduling.
 *
 * <p>Thread-safe. All public methods can be called from any thread.
 */
public class CredentialManager {

    private static final Logger log = LoggerFactory.getLogger(CredentialManager.class);

    private static final double REFRESH_BUFFER_FRACTION = 0.1;
    private static final Duration MIN_REFRESH_BUFFER = Duration.ofSeconds(30);

    private final CredentialProvider provider;
    private final ScheduledExecutorService scheduler;
    private final ReentrantLock refreshLock = new ReentrantLock();
    private final AtomicReference<TokenResult> cachedToken = new AtomicReference<>();
    private volatile ScheduledFuture<?> proactiveRefreshFuture;
    private volatile Instant tokenFetchedAt;

    /**
     * @param provider  the credential provider to delegate to
     * @param scheduler executor for scheduling proactive refresh (may be null
     *                  to disable proactive refresh)
     */
    public CredentialManager(CredentialProvider provider, ScheduledExecutorService scheduler) {
        if (provider == null) {
            throw new IllegalArgumentException("CredentialProvider must not be null");
        }
        this.provider = provider;
        this.scheduler = scheduler;
    }

    /**
     * Returns the current token, fetching from the provider if no cached
     * token exists.
     *
     * @return the current authentication token (never null)
     */
    public String getToken() {
        TokenResult cached = cachedToken.get();
        if (cached != null) {
            return cached.getToken();
        }
        return refreshToken();
    }

    /**
     * Invalidates the cached token. Called on UNAUTHENTICATED response
     * for reactive refresh.
     */
    public void invalidate() {
        log.debug("Token invalidated (reactive refresh triggered)");
        cachedToken.set(null);
        cancelProactiveRefresh();
    }

    /**
     * Fetches a new token from the provider, serializing concurrent calls.
     */
    private String refreshToken() {
        refreshLock.lock();
        try {
            TokenResult cached = cachedToken.get();
            if (cached != null) {
                return cached.getToken();
            }

            log.debug("Fetching token from credential provider");
            TokenResult result = provider.getToken();
            cachedToken.set(result);
            tokenFetchedAt = Instant.now();
            log.debug("Token fetched, token_present: true, expiresAt: {}",
                       result.getExpiresAt());

            scheduleProactiveRefresh(result);

            return result.getToken();
        } catch (CredentialException e) {
            if (e.isRetryable()) {
                log.warn("Credential provider returned retryable error: {}", e.getMessage());
                throw ConnectionException.builder()
                    .code(ErrorCode.CONNECTION_FAILED)
                    .message("Transient credential provider failure: " + e.getMessage())
                    .operation("getToken")
                    .cause(e)
                    .build();
            } else {
                log.error("Credential provider returned non-retryable error: {}", e.getMessage());
                throw AuthenticationException.builder()
                    .message("Credential provider error: " + e.getMessage())
                    .operation("getToken")
                    .cause(e)
                    .build();
            }
        } finally {
            refreshLock.unlock();
        }
    }

    private void scheduleProactiveRefresh(TokenResult result) {
        if (scheduler == null || result.getExpiresAt() == null) {
            return;
        }

        cancelProactiveRefresh();

        Duration lifetime = Duration.between(tokenFetchedAt, result.getExpiresAt());
        if (lifetime.isNegative() || lifetime.isZero()) {
            log.warn("Token expiresAt is in the past; skipping proactive refresh");
            return;
        }

        Duration buffer = Duration.ofMillis(
            (long) (lifetime.toMillis() * REFRESH_BUFFER_FRACTION));
        if (buffer.compareTo(MIN_REFRESH_BUFFER) < 0) {
            buffer = MIN_REFRESH_BUFFER;
        }
        Duration delay = lifetime.minus(buffer);
        if (delay.isNegative()) {
            delay = Duration.ZERO;
        }

        log.debug("Scheduling proactive token refresh in {} seconds", delay.getSeconds());
        proactiveRefreshFuture = scheduler.schedule(() -> {
            log.debug("Proactive token refresh triggered");
            cachedToken.set(null);
            try {
                refreshToken();
            } catch (Exception e) {
                log.warn("Proactive token refresh failed: {}", e.getMessage());
            }
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void cancelProactiveRefresh() {
        ScheduledFuture<?> future = proactiveRefreshFuture;
        if (future != null && !future.isDone()) {
            future.cancel(false);
        }
    }

    /**
     * Shuts down the proactive refresh scheduler.
     * Call during client close.
     */
    public void shutdown() {
        cancelProactiveRefresh();
    }
}

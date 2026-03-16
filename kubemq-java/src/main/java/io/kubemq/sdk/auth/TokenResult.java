package io.kubemq.sdk.auth;

import java.time.Instant;

/**
 * Result returned by a {@link CredentialProvider} containing the token and an optional expiry hint
 * for proactive refresh scheduling.
 */
public final class TokenResult {

  private final String token;
  private final Instant expiresAt;

  /**
   * Creates a TokenResult with no expiry hint. The token will only be refreshed reactively (on
   * UNAUTHENTICATED response).
   *
   * @param token the authentication token (must not be null or empty)
   */
  public TokenResult(String token) {
    this(token, null);
  }

  /**
   * Creates a TokenResult with an expiry hint. When provided, the SDK will proactively refresh the
   * token before expiry.
   *
   * @param token the authentication token (must not be null or empty)
   * @param expiresAt when the token expires (null means no expiry hint)
   */
  public TokenResult(String token, Instant expiresAt) {
    if (token == null || token.isEmpty()) {
      throw new IllegalArgumentException("Token must not be null or empty");
    }
    this.token = token;
    this.expiresAt = expiresAt;
  }

  public String getToken() {
    return token;
  }

  /**
   * Returns when the token expires, or null if no expiry hint was provided.
   *
   * @return the result
   */
  public Instant getExpiresAt() {
    return expiresAt;
  }

  /**
   * Returns a string representation.
   *
   * @return the result
   */
  @Override
  public String toString() {
    return "TokenResult{token_present=true, expiresAt=" + expiresAt + "}";
  }
}

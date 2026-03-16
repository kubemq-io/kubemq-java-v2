package io.kubemq.sdk.auth;

/**
 * Pluggable interface for providing authentication tokens.
 *
 * <p>Implementations may retrieve tokens from any source: static configuration, environment
 * variables, Vault, OIDC providers, cloud IAM, Kubernetes service account tokens, etc.
 *
 * <h3>Threading guarantee</h3>
 *
 * The SDK serializes calls to {@code getToken()} -- at most one call is outstanding at a time.
 * Implementations do NOT need to be thread-safe. The SDK caches the returned token and only
 * re-invokes the provider when:
 *
 * <ul>
 *   <li>No cached token exists (first call)
 *   <li>The cached token is invalidated by a server {@code UNAUTHENTICATED} response
 *   <li>Proactive refresh determines the token is approaching expiry (when {@link
 *       TokenResult#getExpiresAt()} is provided)
 * </ul>
 *
 * <h3>Error handling</h3>
 *
 * Throw {@link CredentialException} with {@code retryable=true} for transient failures (Vault
 * unavailable, network timeout). Throw with {@code retryable=false} for permanent failures (invalid
 * credentials, revoked access).
 *
 * @see StaticTokenProvider
 * @see TokenResult
 */
@FunctionalInterface
public interface CredentialProvider {

  /**
   * Retrieves an authentication token.
   *
   * @return the token result containing the token and optional expiry hint
   * @throws CredentialException if token retrieval fails
   */
  TokenResult getToken() throws CredentialException;
}

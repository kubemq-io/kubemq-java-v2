package io.kubemq.sdk.auth;

/**
 * A {@link CredentialProvider} that always returns the same token. Used for static API keys and
 * long-lived tokens that do not expire.
 */
public final class StaticTokenProvider implements CredentialProvider {

  private final TokenResult tokenResult;

  /**
   * Creates a static token provider.
   *
   * @param token the static authentication token
   */
  public StaticTokenProvider(String token) {
    this.tokenResult = new TokenResult(token);
  }

  @Override
  public TokenResult getToken() {
    return tokenResult;
  }

  /**
   * Returns a string representation of this object.
   *
   * @return the result
   */
  @Override
  public String toString() {
    return "StaticTokenProvider{token_present=true}";
  }
}

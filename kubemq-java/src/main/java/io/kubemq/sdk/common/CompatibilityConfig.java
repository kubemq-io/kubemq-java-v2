package io.kubemq.sdk.common;

/**
 * Holds the compatible server version range for this SDK version. Updated when new SDK versions are
 * released.
 *
 * <p>The SDK performs a non-blocking compatibility check on first operation, logging a warning if
 * the server version falls outside the tested range. The connection always proceeds regardless of
 * the result.
 *
 * @see KubeMQVersion
 */
public final class CompatibilityConfig {

  private CompatibilityConfig() {}

  /**
   * SDK version, delegated to {@link KubeMQVersion#getVersion()} to avoid maintaining a duplicate
   * hardcoded constant.
   */
  public static final String SDK_VERSION = KubeMQVersion.getVersion();

  /** Minimum KubeMQ server version tested with this SDK (inclusive). */
  public static final String MIN_SERVER_VERSION = "2.0.0";

  /** Maximum KubeMQ server version tested with this SDK (inclusive). */
  public static final String MAX_SERVER_VERSION = "2.2.99";

  /**
   * Checks whether the given server version falls within the tested range.
   *
   * @param serverVersion the version string from ServerInfo (e.g., "2.1.0")
   * @return true if the version is within the tested range, false otherwise
   */
  public static boolean isCompatible(String serverVersion) {
    if (serverVersion == null || serverVersion.isEmpty()) {
      return false;
    }
    try {
      int[] sv = parseVersion(serverVersion);
      int[] minV = parseVersion(MIN_SERVER_VERSION);
      int[] maxV = parseVersion(MAX_SERVER_VERSION);
      return compareVersions(sv, minV) >= 0 && compareVersions(sv, maxV) <= 0;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /**
   * Parses a semver-like string "X.Y.Z" into int[3]. Tolerates versions with only major or
   * major.minor components.
   *
   * <p>Pre-release suffixes (e.g., "-beta", "-rc.1") are stripped before parsing. A leading 'v'
   * prefix is also stripped.
   */
  static int[] parseVersion(String version) {
    String v = version.startsWith("v") ? version.substring(1) : version;
    int dashIdx = v.indexOf('-');
    if (dashIdx > 0) {
      v = v.substring(0, dashIdx);
    }
    String[] parts = v.split("\\.");
    int[] result = new int[3];
    for (int i = 0; i < Math.min(parts.length, 3); i++) {
      result[i] = Integer.parseInt(parts[i].trim());
    }
    return result;
  }

  /**
   * Compares two parsed version arrays.
   *
   * @return negative if a &lt; b, 0 if equal, positive if a &gt; b
   */
  static int compareVersions(int[] a, int[] b) {
    for (int i = 0; i < 3; i++) {
      if (a[i] != b[i]) {
        return Integer.compare(a[i], b[i]);
      }
    }
    return 0;
  }
}

package io.kubemq.sdk.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Provides the SDK version at runtime.
 *
 * <p>The version is injected from pom.xml at build time via Maven resource filtering. This class
 * reads the generated properties file from the classpath.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * String version = KubeMQVersion.getVersion(); // e.g., "2.1.1"
 * }</pre>
 */
public final class KubeMQVersion {

  private static final String PROPERTIES_FILE = "kubemq-sdk-version.properties";
  private static final String UNKNOWN = "unknown";

  /**
   * The SDK version string, loaded once at class initialization. Returns "unknown" if the version
   * properties file cannot be read (e.g., when running from source without a Maven build).
   */
  public static final String VERSION;

  /** The SDK group ID (e.g., "io.kubemq.sdk"). */
  public static final String GROUP_ID;

  /** The SDK artifact ID (e.g., "kubemq-sdk-Java"). */
  public static final String ARTIFACT_ID;

  static {
    Properties props = new Properties();
    try (InputStream is =
        KubeMQVersion.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
      if (is != null) {
        props.load(is);
      }
    } catch (IOException e) {
      // Fall back to UNKNOWN values
    }
    VERSION = props.getProperty("sdk.version", UNKNOWN);
    GROUP_ID = props.getProperty("sdk.groupId", UNKNOWN);
    ARTIFACT_ID = props.getProperty("sdk.artifactId", UNKNOWN);
  }

  private KubeMQVersion() {}

  /**
   * Returns the SDK version string (e.g., "2.1.1").
   *
   * @return the SDK version, or "unknown" if not available
   */
  public static String getVersion() {
    return VERSION;
  }
}

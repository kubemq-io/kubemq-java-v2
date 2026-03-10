package io.kubemq.sdk.observability;

import java.io.InputStream;
import java.util.Properties;

/**
 * Resolves the SDK version from Maven artifact metadata.
 * Falls back to "unknown" when running from source or IDE.
 */
final class SdkVersion {

    private static final String VERSION;

    static {
        String v = "unknown";
        try (InputStream is = SdkVersion.class.getResourceAsStream(
                "/META-INF/maven/io.kubemq.sdk/kubemq-sdk-Java/pom.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                v = props.getProperty("version", "unknown");
            }
        } catch (Exception ignored) {
            // Silently fall back to "unknown"
        }
        VERSION = v;
    }

    static String get() {
        return VERSION;
    }

    private SdkVersion() {}
}

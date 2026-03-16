package io.kubemq.sdk.unit.common;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.common.KubeMQVersion;
import org.junit.jupiter.api.Test;

class KubeMQVersionTest {

  @Test
  void getVersion_returnsNonNull() {
    assertNotNull(KubeMQVersion.getVersion());
  }

  @Test
  void getVersion_returnsNonEmpty() {
    assertFalse(KubeMQVersion.getVersion().isEmpty());
  }

  @Test
  void getVersion_matchesSemVerPattern() {
    String version = KubeMQVersion.getVersion();
    if (!"unknown".equals(version)) {
      assertTrue(
          version.matches("\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9.]+)?"),
          "Version '" + version + "' does not match SemVer pattern");
    }
  }

  @Test
  void versionConstantMatchesMethod() {
    assertEquals(KubeMQVersion.VERSION, KubeMQVersion.getVersion());
  }

  @Test
  void groupIdIsNotNull() {
    assertNotNull(KubeMQVersion.GROUP_ID);
  }

  @Test
  void artifactIdIsNotNull() {
    assertNotNull(KubeMQVersion.ARTIFACT_ID);
  }
}

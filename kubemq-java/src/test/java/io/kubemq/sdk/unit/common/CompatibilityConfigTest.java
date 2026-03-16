package io.kubemq.sdk.unit.common;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.common.CompatibilityConfig;
import org.junit.jupiter.api.Test;

class CompatibilityConfigTest {

  @Test
  void isCompatible_withinRange() {
    assertTrue(CompatibilityConfig.isCompatible("2.1.0"));
  }

  @Test
  void isCompatible_atMinBoundary() {
    assertTrue(CompatibilityConfig.isCompatible(CompatibilityConfig.MIN_SERVER_VERSION));
  }

  @Test
  void isCompatible_atMaxBoundary() {
    assertTrue(CompatibilityConfig.isCompatible(CompatibilityConfig.MAX_SERVER_VERSION));
  }

  @Test
  void isCompatible_belowRange() {
    assertFalse(CompatibilityConfig.isCompatible("1.9.0"));
  }

  @Test
  void isCompatible_aboveRange() {
    assertFalse(CompatibilityConfig.isCompatible("3.0.0"));
  }

  @Test
  void isCompatible_nullVersion() {
    assertFalse(CompatibilityConfig.isCompatible(null));
  }

  @Test
  void isCompatible_emptyVersion() {
    assertFalse(CompatibilityConfig.isCompatible(""));
  }

  @Test
  void isCompatible_unparseable() {
    assertFalse(CompatibilityConfig.isCompatible("abc"));
  }

  @Test
  void isCompatible_withVPrefix() {
    assertTrue(CompatibilityConfig.isCompatible("v2.1.0"));
  }

  @Test
  void isCompatible_withPreRelease() {
    assertTrue(CompatibilityConfig.isCompatible("2.1.0-beta"));
  }

  @Test
  void isCompatible_majorOnly() {
    assertTrue(CompatibilityConfig.isCompatible("2"));
  }

  @Test
  void isCompatible_majorMinorOnly() {
    assertTrue(CompatibilityConfig.isCompatible("2.1"));
  }

  @Test
  void isCompatible_vPrefixAndPreRelease() {
    assertTrue(CompatibilityConfig.isCompatible("v2.0.5-rc.1"));
  }

  @Test
  void isCompatible_justBelowMin() {
    assertFalse(CompatibilityConfig.isCompatible("1.99.99"));
  }

  @Test
  void isCompatible_justAboveMax() {
    assertFalse(CompatibilityConfig.isCompatible("2.3.0"));
  }

  @Test
  void sdkVersion_isAvailable() {
    assertNotNull(CompatibilityConfig.SDK_VERSION);
  }

  @Test
  void constants_areNonNull() {
    assertNotNull(CompatibilityConfig.MIN_SERVER_VERSION);
    assertNotNull(CompatibilityConfig.MAX_SERVER_VERSION);
  }
}

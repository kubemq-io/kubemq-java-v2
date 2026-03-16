package io.kubemq.sdk.unit.observability;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.observability.KubeMQLogger;
import io.kubemq.sdk.observability.NoOpLogger;
import org.junit.jupiter.api.Test;

class NoOpLoggerTest {

  @Test
  void singletonInstance() {
    assertNotNull(NoOpLogger.INSTANCE);
    assertSame(NoOpLogger.INSTANCE, NoOpLogger.INSTANCE);
  }

  @Test
  void implementsKubeMQLogger() {
    assertTrue(NoOpLogger.INSTANCE instanceof KubeMQLogger);
  }

  @Test
  void allMethodsAreNoOps() {
    NoOpLogger logger = NoOpLogger.INSTANCE;
    // Should not throw
    logger.trace("msg");
    logger.trace("msg", "key", "value");
    logger.debug("msg");
    logger.debug("msg", "key", "value");
    logger.info("msg");
    logger.info("msg", "key", "value");
    logger.warn("msg");
    logger.warn("msg", "key", "value");
    logger.error("msg");
    logger.error("msg", "key", "value");
    logger.error("msg", new RuntimeException("test"));
    logger.error("msg", new RuntimeException("test"), "key", "value");
  }

  @Test
  void isEnabledReturnsFalse() {
    NoOpLogger logger = NoOpLogger.INSTANCE;
    for (KubeMQLogger.LogLevel level : KubeMQLogger.LogLevel.values()) {
      assertFalse(logger.isEnabled(level), "isEnabled should return false for " + level);
    }
  }

  @Test
  void nullKeysAndValuesDoNotThrow() {
    NoOpLogger logger = NoOpLogger.INSTANCE;
    logger.debug("msg", (Object[]) null);
    logger.info("msg", (Object[]) null);
  }
}

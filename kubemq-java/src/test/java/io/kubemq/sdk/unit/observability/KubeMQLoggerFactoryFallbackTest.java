package io.kubemq.sdk.unit.observability;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.observability.KubeMQLogger;
import io.kubemq.sdk.observability.KubeMQLoggerFactory;
import io.kubemq.sdk.observability.NoOpLogger;
import io.kubemq.sdk.observability.Slf4jLoggerAdapter;
import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

/**
 * Tests for KubeMQLoggerFactory including the SLF4J-unavailable fallback path. Uses sun.misc.Unsafe
 * to modify static final fields on JDK 21.
 */
@DisplayName("KubeMQLoggerFactory fallback paths")
class KubeMQLoggerFactoryFallbackTest {

  @Test
  @DisplayName("isSlf4jAvailable returns true when SLF4J is on classpath")
  void slf4jAvailable_returnsTrue() {
    assertTrue(KubeMQLoggerFactory.isSlf4jAvailable());
  }

  @Test
  @DisplayName("getLogger(String) returns Slf4jLoggerAdapter when SLF4J is available")
  void getLoggerByName_returnsSl4jAdapter() {
    KubeMQLogger logger = KubeMQLoggerFactory.getLogger("test-logger");
    assertInstanceOf(Slf4jLoggerAdapter.class, logger);
  }

  @Test
  @DisplayName("getLogger(Class) returns Slf4jLoggerAdapter when SLF4J is available")
  void getLoggerByClass_returnsSl4jAdapter() {
    KubeMQLogger logger = KubeMQLoggerFactory.getLogger(KubeMQLoggerFactoryFallbackTest.class);
    assertInstanceOf(Slf4jLoggerAdapter.class, logger);
  }

  @Test
  @DisplayName("getLogger(String) returns NoOpLogger when SLF4J is unavailable")
  void getLoggerByName_noSlf4j_returnsNoOp() throws Exception {
    setSlf4jAvailable(false);
    try {
      KubeMQLogger logger = KubeMQLoggerFactory.getLogger("test-logger");
      assertSame(NoOpLogger.INSTANCE, logger);
    } finally {
      setSlf4jAvailable(true);
    }
  }

  @Test
  @DisplayName("getLogger(Class) returns NoOpLogger when SLF4J is unavailable")
  void getLoggerByClass_noSlf4j_returnsNoOp() throws Exception {
    setSlf4jAvailable(false);
    try {
      KubeMQLogger logger = KubeMQLoggerFactory.getLogger(KubeMQLoggerFactoryFallbackTest.class);
      assertSame(NoOpLogger.INSTANCE, logger);
    } finally {
      setSlf4jAvailable(true);
    }
  }

  @Test
  @DisplayName("isSlf4jAvailable returns false when SLF4J field is overridden")
  void isSlf4jAvailable_whenOverriddenToFalse() throws Exception {
    setSlf4jAvailable(false);
    try {
      assertFalse(KubeMQLoggerFactory.isSlf4jAvailable());
    } finally {
      setSlf4jAvailable(true);
    }
  }

  /**
   * Uses sun.misc.Unsafe to override the static final SLF4J_AVAILABLE field. This works on JDK 21
   * where VarHandle and reflection-based approaches fail.
   */
  private static void setSlf4jAvailable(boolean value) throws Exception {
    Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
    unsafeField.setAccessible(true);
    Unsafe unsafe = (Unsafe) unsafeField.get(null);

    Field field = KubeMQLoggerFactory.class.getDeclaredField("SLF4J_AVAILABLE");
    long offset = unsafe.staticFieldOffset(field);
    unsafe.putBoolean(KubeMQLoggerFactory.class, offset, value);
  }
}

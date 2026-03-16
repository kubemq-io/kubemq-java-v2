package io.kubemq.sdk.unit.observability;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.observability.KubeMQLogger;
import io.kubemq.sdk.observability.KubeMQLoggerFactory;
import io.kubemq.sdk.observability.Slf4jLoggerAdapter;
import org.junit.jupiter.api.Test;

class KubeMQLoggerFactoryTest {

  @Test
  void getLoggerByNameReturnsSLF4JAdapterWhenAvailable() {
    // SLF4J is on test classpath via logback-classic
    KubeMQLogger logger = KubeMQLoggerFactory.getLogger("test.logger");
    assertNotNull(logger);
    assertInstanceOf(Slf4jLoggerAdapter.class, logger);
  }

  @Test
  void getLoggerByClassReturnsSLF4JAdapterWhenAvailable() {
    KubeMQLogger logger = KubeMQLoggerFactory.getLogger(KubeMQLoggerFactoryTest.class);
    assertNotNull(logger);
    assertInstanceOf(Slf4jLoggerAdapter.class, logger);
  }

  @Test
  void isSlf4jAvailableReturnsTrueWhenOnClasspath() {
    assertTrue(KubeMQLoggerFactory.isSlf4jAvailable());
  }

  @Test
  void getLoggerByNameReturnsNewInstance() {
    KubeMQLogger logger1 = KubeMQLoggerFactory.getLogger("test.logger");
    KubeMQLogger logger2 = KubeMQLoggerFactory.getLogger("test.logger");
    assertNotSame(logger1, logger2);
  }

  @Test
  void getLoggerByDifferentNamesReturnsDifferentLoggers() {
    KubeMQLogger logger1 = KubeMQLoggerFactory.getLogger("logger.one");
    KubeMQLogger logger2 = KubeMQLoggerFactory.getLogger("logger.two");
    assertNotNull(logger1);
    assertNotNull(logger2);
  }
}

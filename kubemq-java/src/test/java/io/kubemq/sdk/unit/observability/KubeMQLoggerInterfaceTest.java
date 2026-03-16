package io.kubemq.sdk.unit.observability;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.observability.KubeMQLogger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for KubeMQLogger interface contract: verifies custom implementations work correctly and
 * that the user-injectable logger pattern functions as expected.
 */
class KubeMQLoggerInterfaceTest {

  @Test
  void customLoggerReceivesCalls() {
    List<String> messages = new ArrayList<>();
    KubeMQLogger custom =
        new KubeMQLogger() {
          @Override
          public void trace(String msg, Object... kv) {
            messages.add("TRACE:" + msg);
          }

          @Override
          public void debug(String msg, Object... kv) {
            messages.add("DEBUG:" + msg);
          }

          @Override
          public void info(String msg, Object... kv) {
            messages.add("INFO:" + msg);
          }

          @Override
          public void warn(String msg, Object... kv) {
            messages.add("WARN:" + msg);
          }

          @Override
          public void error(String msg, Object... kv) {
            messages.add("ERROR:" + msg);
          }

          @Override
          public void error(String msg, Throwable cause, Object... kv) {
            messages.add("ERROR:" + msg + ":" + cause.getMessage());
          }

          @Override
          public boolean isEnabled(LogLevel level) {
            return true;
          }
        };

    custom.trace("t1");
    custom.debug("d1");
    custom.info("i1");
    custom.warn("w1");
    custom.error("e1");
    custom.error("e2", new RuntimeException("boom"));

    assertEquals(6, messages.size());
    assertEquals("TRACE:t1", messages.get(0));
    assertEquals("DEBUG:d1", messages.get(1));
    assertEquals("INFO:i1", messages.get(2));
    assertEquals("WARN:w1", messages.get(3));
    assertEquals("ERROR:e1", messages.get(4));
    assertEquals("ERROR:e2:boom", messages.get(5));
  }

  @Test
  void customLoggerReceivesKeyValuePairs() {
    List<Object[]> captured = new ArrayList<>();
    KubeMQLogger custom =
        new KubeMQLogger() {
          @Override
          public void trace(String msg, Object... kv) {
            captured.add(kv);
          }

          @Override
          public void debug(String msg, Object... kv) {
            captured.add(kv);
          }

          @Override
          public void info(String msg, Object... kv) {
            captured.add(kv);
          }

          @Override
          public void warn(String msg, Object... kv) {
            captured.add(kv);
          }

          @Override
          public void error(String msg, Object... kv) {
            captured.add(kv);
          }

          @Override
          public void error(String msg, Throwable cause, Object... kv) {
            captured.add(kv);
          }

          @Override
          public boolean isEnabled(LogLevel level) {
            return true;
          }
        };

    custom.info("Connected", "address", "localhost:50000", "clientId", "my-client");

    assertEquals(1, captured.size());
    Object[] kv = captured.get(0);
    assertEquals(4, kv.length);
    assertEquals("address", kv[0]);
    assertEquals("localhost:50000", kv[1]);
    assertEquals("clientId", kv[2]);
    assertEquals("my-client", kv[3]);
  }

  @Test
  void logLevelEnumValues() {
    KubeMQLogger.LogLevel[] levels = KubeMQLogger.LogLevel.values();
    assertEquals(5, levels.length);
    assertEquals(KubeMQLogger.LogLevel.TRACE, levels[0]);
    assertEquals(KubeMQLogger.LogLevel.DEBUG, levels[1]);
    assertEquals(KubeMQLogger.LogLevel.INFO, levels[2]);
    assertEquals(KubeMQLogger.LogLevel.WARN, levels[3]);
    assertEquals(KubeMQLogger.LogLevel.ERROR, levels[4]);
  }

  @Test
  void isEnabledSelectivelyReturnsTrue() {
    KubeMQLogger custom =
        new KubeMQLogger() {
          @Override
          public void trace(String msg, Object... kv) {}

          @Override
          public void debug(String msg, Object... kv) {}

          @Override
          public void info(String msg, Object... kv) {}

          @Override
          public void warn(String msg, Object... kv) {}

          @Override
          public void error(String msg, Object... kv) {}

          @Override
          public void error(String msg, Throwable cause, Object... kv) {}

          @Override
          public boolean isEnabled(LogLevel level) {
            return level == LogLevel.WARN || level == LogLevel.ERROR;
          }
        };

    assertFalse(custom.isEnabled(KubeMQLogger.LogLevel.TRACE));
    assertFalse(custom.isEnabled(KubeMQLogger.LogLevel.DEBUG));
    assertFalse(custom.isEnabled(KubeMQLogger.LogLevel.INFO));
    assertTrue(custom.isEnabled(KubeMQLogger.LogLevel.WARN));
    assertTrue(custom.isEnabled(KubeMQLogger.LogLevel.ERROR));
  }
}

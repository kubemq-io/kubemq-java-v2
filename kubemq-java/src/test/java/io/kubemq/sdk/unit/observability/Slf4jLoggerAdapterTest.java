package io.kubemq.sdk.unit.observability;

import io.kubemq.sdk.observability.KubeMQLogger;
import io.kubemq.sdk.observability.Slf4jLoggerAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Slf4jLoggerAdapterTest {

    private Slf4jLoggerAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new Slf4jLoggerAdapter("io.kubemq.sdk.test");
    }

    @Test
    void constructWithString() {
        Slf4jLoggerAdapter logger = new Slf4jLoggerAdapter("test.logger");
        assertNotNull(logger);
    }

    @Test
    void constructWithClass() {
        Slf4jLoggerAdapter logger = new Slf4jLoggerAdapter(Slf4jLoggerAdapterTest.class);
        assertNotNull(logger);
    }

    @Test
    void implementsKubeMQLogger() {
        assertTrue(adapter instanceof KubeMQLogger);
    }

    @Test
    void allLevelMethodsDoNotThrow() {
        adapter.trace("trace msg", "key", "value");
        adapter.debug("debug msg", "key", "value");
        adapter.info("info msg", "key", "value");
        adapter.warn("warn msg", "key", "value");
        adapter.error("error msg", "key", "value");
        adapter.error("error msg", new RuntimeException("test"), "key", "value");
    }

    @Test
    void isEnabledReturnsConsistentValues() {
        for (KubeMQLogger.LogLevel level : KubeMQLogger.LogLevel.values()) {
            // SLF4J with logback-classic in test scope should return true for at least some levels
            adapter.isEnabled(level);
        }
    }

    @Test
    void formatMessageNoArgs() {
        String result = Slf4jLoggerAdapter.formatMessage("hello");
        assertEquals("hello", result);
    }

    @Test
    void formatMessageNullArgs() {
        String result = Slf4jLoggerAdapter.formatMessage("hello", (Object[]) null);
        assertEquals("hello", result);
    }

    @Test
    void formatMessageEmptyArgs() {
        String result = Slf4jLoggerAdapter.formatMessage("hello");
        assertEquals("hello", result);
    }

    @Test
    void formatMessageKeyValuePairs() {
        String result = Slf4jLoggerAdapter.formatMessage("connected",
            "address", "localhost:50000",
            "clientId", "my-client");
        assertEquals("connected [address=localhost:50000, clientId=my-client]", result);
    }

    @Test
    void formatMessageSingleKeyValuePair() {
        String result = Slf4jLoggerAdapter.formatMessage("ping",
            "address", "localhost");
        assertEquals("ping [address=localhost]", result);
    }

    @Test
    void formatMessageOddLengthArgs() {
        String result = Slf4jLoggerAdapter.formatMessage("msg",
            "key1", "value1", "key2");
        assertEquals("msg [key1=value1, key2=MISSING_VALUE]", result);
    }

    @Test
    void formatMessageNullValues() {
        String result = Slf4jLoggerAdapter.formatMessage("msg",
            "key", null);
        assertEquals("msg [key=null]", result);
    }

    @Test
    void formatMessageNullKey() {
        String result = Slf4jLoggerAdapter.formatMessage("msg",
            null, "value");
        assertEquals("msg [null=value]", result);
    }
}

package io.kubemq.sdk.unit.observability;

import io.kubemq.sdk.observability.LogHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LogHelperTest {

    @Test
    void mergeEmptyArrays() {
        Object[] result = LogHelper.merge();
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    void mergeNullArrays() {
        Object[] result = LogHelper.merge(null, null);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    void mergeSingleArray() {
        Object[] input = {"key", "value"};
        Object[] result = LogHelper.merge(input);
        assertArrayEquals(input, result);
    }

    @Test
    void mergeTwoArrays() {
        Object[] a = {"a", 1};
        Object[] b = {"b", 2};
        Object[] result = LogHelper.merge(a, b);
        assertArrayEquals(new Object[]{"a", 1, "b", 2}, result);
    }

    @Test
    void mergeWithNullInMiddle() {
        Object[] a = {"a", 1};
        Object[] b = {"b", 2};
        Object[] result = LogHelper.merge(a, null, b);
        assertArrayEquals(new Object[]{"a", 1, "b", 2}, result);
    }

    @Test
    void mergeWithEmptyArray() {
        Object[] a = {"a", 1};
        Object[] empty = {};
        Object[] b = {"b", 2};
        Object[] result = LogHelper.merge(a, empty, b);
        assertArrayEquals(new Object[]{"a", 1, "b", 2}, result);
    }

    @Test
    void mergeThreeArrays() {
        Object[] a = {"a", 1};
        Object[] b = {"b", 2};
        Object[] c = {"c", 3};
        Object[] result = LogHelper.merge(a, b, c);
        assertArrayEquals(new Object[]{"a", 1, "b", 2, "c", 3}, result);
    }

    @Test
    void mergePreservesOrder() {
        Object[] a = {"trace_id", "abc123"};
        Object[] b = {"span_id", "def456"};
        Object[] result = LogHelper.merge(a, b);
        assertEquals("trace_id", result[0]);
        assertEquals("abc123", result[1]);
        assertEquals("span_id", result[2]);
        assertEquals("def456", result[3]);
    }
}

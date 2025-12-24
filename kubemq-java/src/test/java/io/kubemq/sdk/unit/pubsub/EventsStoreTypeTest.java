package io.kubemq.sdk.unit.pubsub;

import io.kubemq.sdk.pubsub.EventsStoreType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EventsStoreType enum.
 */
class EventsStoreTypeTest {

    @Test
    void allEnumValues_exist() {
        EventsStoreType[] values = EventsStoreType.values();

        assertEquals(7, values.length);
        assertNotNull(EventsStoreType.Undefined);
        assertNotNull(EventsStoreType.StartNewOnly);
        assertNotNull(EventsStoreType.StartFromFirst);
        assertNotNull(EventsStoreType.StartFromLast);
        assertNotNull(EventsStoreType.StartAtSequence);
        assertNotNull(EventsStoreType.StartAtTime);
        assertNotNull(EventsStoreType.StartAtTimeDelta);
    }

    @Test
    void valueOf_validValues_returnsCorrectEnum() {
        assertEquals(EventsStoreType.Undefined, EventsStoreType.valueOf("Undefined"));
        assertEquals(EventsStoreType.StartNewOnly, EventsStoreType.valueOf("StartNewOnly"));
        assertEquals(EventsStoreType.StartFromFirst, EventsStoreType.valueOf("StartFromFirst"));
        assertEquals(EventsStoreType.StartFromLast, EventsStoreType.valueOf("StartFromLast"));
        assertEquals(EventsStoreType.StartAtSequence, EventsStoreType.valueOf("StartAtSequence"));
        assertEquals(EventsStoreType.StartAtTime, EventsStoreType.valueOf("StartAtTime"));
        assertEquals(EventsStoreType.StartAtTimeDelta, EventsStoreType.valueOf("StartAtTimeDelta"));
    }

    @Test
    void valueOf_invalidValue_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            EventsStoreType.valueOf("InvalidType");
        });
    }

    @Test
    void getValue_returnsCorrectInt() {
        assertEquals(0, EventsStoreType.Undefined.getValue());
        assertEquals(1, EventsStoreType.StartNewOnly.getValue());
        assertEquals(2, EventsStoreType.StartFromFirst.getValue());
        assertEquals(3, EventsStoreType.StartFromLast.getValue());
        assertEquals(4, EventsStoreType.StartAtSequence.getValue());
        assertEquals(5, EventsStoreType.StartAtTime.getValue());
        assertEquals(6, EventsStoreType.StartAtTimeDelta.getValue());
    }

    @Test
    void undefined_hasValueZero() {
        assertEquals(0, EventsStoreType.Undefined.getValue());
    }

    @Test
    void startNewOnly_hasValueOne() {
        assertEquals(1, EventsStoreType.StartNewOnly.getValue());
    }

    @Test
    void startFromFirst_hasValueTwo() {
        assertEquals(2, EventsStoreType.StartFromFirst.getValue());
    }

    @Test
    void startFromLast_hasValueThree() {
        assertEquals(3, EventsStoreType.StartFromLast.getValue());
    }

    @Test
    void startAtSequence_hasValueFour() {
        assertEquals(4, EventsStoreType.StartAtSequence.getValue());
    }

    @Test
    void startAtTime_hasValueFive() {
        assertEquals(5, EventsStoreType.StartAtTime.getValue());
    }

    @Test
    void startAtTimeDelta_hasValueSix() {
        assertEquals(6, EventsStoreType.StartAtTimeDelta.getValue());
    }

    @Test
    void valuesAreSequential() {
        int expectedValue = 0;
        for (EventsStoreType type : EventsStoreType.values()) {
            assertEquals(expectedValue, type.getValue(),
                    "Expected " + type.name() + " to have value " + expectedValue);
            expectedValue++;
        }
    }
}

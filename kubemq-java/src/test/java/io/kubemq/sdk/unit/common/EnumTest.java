package io.kubemq.sdk.unit.common;

import io.kubemq.sdk.common.RequestType;
import io.kubemq.sdk.common.SubscribeType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RequestType and SubscribeType enums.
 */
class EnumTest {

    @Nested
    class RequestTypeTests {

        @Test
        void allValues_exist() {
            RequestType[] values = RequestType.values();

            assertEquals(3, values.length);
            assertNotNull(RequestType.RequestTypeUnknown);
            assertNotNull(RequestType.Command);
            assertNotNull(RequestType.Query);
        }

        @Test
        void valueOf_validValues_returnsCorrectEnum() {
            assertEquals(RequestType.RequestTypeUnknown, RequestType.valueOf("RequestTypeUnknown"));
            assertEquals(RequestType.Command, RequestType.valueOf("Command"));
            assertEquals(RequestType.Query, RequestType.valueOf("Query"));
        }

        @Test
        void valueOf_invalidValue_throwsException() {
            assertThrows(IllegalArgumentException.class, () -> {
                RequestType.valueOf("InvalidType");
            });
        }

        @Test
        void getValue_returnsCorrectIntValue() {
            assertEquals(0, RequestType.RequestTypeUnknown.getValue());
            assertEquals(1, RequestType.Command.getValue());
            assertEquals(2, RequestType.Query.getValue());
        }

        @Test
        void requestTypeUnknown_hasZeroValue() {
            assertEquals(0, RequestType.RequestTypeUnknown.getValue());
        }

        @Test
        void command_hasValueOne() {
            assertEquals(1, RequestType.Command.getValue());
        }

        @Test
        void query_hasValueTwo() {
            assertEquals(2, RequestType.Query.getValue());
        }
    }

    @Nested
    class SubscribeTypeTests {

        @Test
        void allValues_exist() {
            SubscribeType[] values = SubscribeType.values();

            assertEquals(5, values.length);
            assertNotNull(SubscribeType.SubscribeTypeUndefined);
            assertNotNull(SubscribeType.Events);
            assertNotNull(SubscribeType.EventsStore);
            assertNotNull(SubscribeType.Commands);
            assertNotNull(SubscribeType.Queries);
        }

        @Test
        void valueOf_validValues_returnsCorrectEnum() {
            assertEquals(SubscribeType.SubscribeTypeUndefined, SubscribeType.valueOf("SubscribeTypeUndefined"));
            assertEquals(SubscribeType.Events, SubscribeType.valueOf("Events"));
            assertEquals(SubscribeType.EventsStore, SubscribeType.valueOf("EventsStore"));
            assertEquals(SubscribeType.Commands, SubscribeType.valueOf("Commands"));
            assertEquals(SubscribeType.Queries, SubscribeType.valueOf("Queries"));
        }

        @Test
        void valueOf_invalidValue_throwsException() {
            assertThrows(IllegalArgumentException.class, () -> {
                SubscribeType.valueOf("InvalidType");
            });
        }

        @Test
        void getValue_returnsCorrectIntValue() {
            assertEquals(0, SubscribeType.SubscribeTypeUndefined.getValue());
            assertEquals(1, SubscribeType.Events.getValue());
            assertEquals(2, SubscribeType.EventsStore.getValue());
            assertEquals(3, SubscribeType.Commands.getValue());
            assertEquals(4, SubscribeType.Queries.getValue());
        }

        @Test
        void subscribeTypeUndefined_hasZeroValue() {
            assertEquals(0, SubscribeType.SubscribeTypeUndefined.getValue());
        }

        @Test
        void events_hasValueOne() {
            assertEquals(1, SubscribeType.Events.getValue());
        }

        @Test
        void eventsStore_hasValueTwo() {
            assertEquals(2, SubscribeType.EventsStore.getValue());
        }

        @Test
        void commands_hasValueThree() {
            assertEquals(3, SubscribeType.Commands.getValue());
        }

        @Test
        void queries_hasValueFour() {
            assertEquals(4, SubscribeType.Queries.getValue());
        }
    }
}

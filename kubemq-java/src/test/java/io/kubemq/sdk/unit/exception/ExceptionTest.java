package io.kubemq.sdk.unit.exception;

import io.kubemq.sdk.exception.CreateChannelException;
import io.kubemq.sdk.exception.DeleteChannelException;
import io.kubemq.sdk.exception.GRPCException;
import io.kubemq.sdk.exception.ListChannelsException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for all SDK exception classes.
 */
class ExceptionTest {

    @Nested
    class CreateChannelExceptionTests {

        @Test
        void noArgConstructor_createsExceptionWithNullMessage() {
            CreateChannelException ex = new CreateChannelException();

            assertNull(ex.getMessage());
            assertNull(ex.getCause());
        }

        @Test
        void messageConstructor_setsMessage() {
            CreateChannelException ex = new CreateChannelException("Channel creation failed");

            assertEquals("Channel creation failed", ex.getMessage());
            assertNull(ex.getCause());
        }

        @Test
        void messageAndCauseConstructor_setsMessageAndCause() {
            Throwable cause = new RuntimeException("Root cause");
            CreateChannelException ex = new CreateChannelException("Channel creation failed", cause);

            assertEquals("Channel creation failed", ex.getMessage());
            assertEquals(cause, ex.getCause());
        }

        @Test
        void exception_extendsRuntimeException() {
            CreateChannelException ex = new CreateChannelException();

            assertTrue(ex instanceof RuntimeException);
        }

        @Test
        void exception_canBeThrown() {
            assertThrows(CreateChannelException.class, () -> {
                throw new CreateChannelException("Test throw");
            });
        }
    }

    @Nested
    class DeleteChannelExceptionTests {

        @Test
        void noArgConstructor_createsExceptionWithNullMessage() {
            DeleteChannelException ex = new DeleteChannelException();

            assertNull(ex.getMessage());
            assertNull(ex.getCause());
        }

        @Test
        void messageConstructor_setsMessage() {
            DeleteChannelException ex = new DeleteChannelException("Channel deletion failed");

            assertEquals("Channel deletion failed", ex.getMessage());
            assertNull(ex.getCause());
        }

        @Test
        void messageAndCauseConstructor_setsMessageAndCause() {
            Throwable cause = new RuntimeException("Root cause");
            DeleteChannelException ex = new DeleteChannelException("Channel deletion failed", cause);

            assertEquals("Channel deletion failed", ex.getMessage());
            assertEquals(cause, ex.getCause());
        }

        @Test
        void exception_extendsRuntimeException() {
            DeleteChannelException ex = new DeleteChannelException();

            assertTrue(ex instanceof RuntimeException);
        }

        @Test
        void exception_canBeThrown() {
            assertThrows(DeleteChannelException.class, () -> {
                throw new DeleteChannelException("Test throw");
            });
        }
    }

    @Nested
    class GRPCExceptionTests {

        @Test
        void noArgConstructor_createsExceptionWithNullMessage() {
            GRPCException ex = new GRPCException();

            assertNull(ex.getMessage());
            assertNull(ex.getCause());
        }

        @Test
        void messageConstructor_setsMessage() {
            GRPCException ex = new GRPCException("gRPC communication failed");

            assertEquals("gRPC communication failed", ex.getMessage());
            assertNull(ex.getCause());
        }

        @Test
        void causeConstructor_setsCause() {
            Throwable cause = new RuntimeException("Connection refused");
            GRPCException ex = new GRPCException(cause);

            // When only cause is provided, message is cause.toString()
            assertTrue(ex.getMessage().contains("Connection refused"));
            assertEquals(cause, ex.getCause());
        }

        @Test
        void causeConstructor_withNullCause_hasNullMessage() {
            GRPCException ex = new GRPCException((Throwable) null);

            assertNull(ex.getMessage());
            assertNull(ex.getCause());
        }

        @Test
        void exception_extendsRuntimeException() {
            GRPCException ex = new GRPCException();

            assertTrue(ex instanceof RuntimeException);
        }

        @Test
        void exception_canBeThrown() {
            assertThrows(GRPCException.class, () -> {
                throw new GRPCException("Test throw");
            });
        }
    }

    @Nested
    class ListChannelsExceptionTests {

        @Test
        void noArgConstructor_createsExceptionWithNullMessage() {
            ListChannelsException ex = new ListChannelsException();

            assertNull(ex.getMessage());
            assertNull(ex.getCause());
        }

        @Test
        void messageConstructor_setsMessage() {
            ListChannelsException ex = new ListChannelsException("Failed to list channels");

            assertEquals("Failed to list channels", ex.getMessage());
            assertNull(ex.getCause());
        }

        @Test
        void messageAndCauseConstructor_setsMessageAndCause() {
            Throwable cause = new RuntimeException("Root cause");
            ListChannelsException ex = new ListChannelsException("Failed to list channels", cause);

            assertEquals("Failed to list channels", ex.getMessage());
            assertEquals(cause, ex.getCause());
        }

        @Test
        void exception_extendsRuntimeException() {
            ListChannelsException ex = new ListChannelsException();

            assertTrue(ex instanceof RuntimeException);
        }

        @Test
        void exception_canBeThrown() {
            assertThrows(ListChannelsException.class, () -> {
                throw new ListChannelsException("Test throw");
            });
        }
    }
}

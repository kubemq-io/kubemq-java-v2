package io.kubemq.sdk.exception;

/**
 * Exception thrown when there is an error listing channels.
 */
public class ListChannelsException extends RuntimeException {

    /**
     * Constructs a new ListChannelsException with {@code null} as its detail message.
     * The cause is not initialized.
     */
    public ListChannelsException() {
        super();
    }

    /**
     * Constructs a new ListChannelsException with the specified detail message.
     * The cause is not initialized.
     *
     * @param message the detail message. The detail message is saved for later retrieval by the {@link #getMessage()} method.
     */
    public ListChannelsException(String message) {
        super(message);
    }

    /**
     * Constructs a new ListChannelsException with the specified detail message and cause.
     *
     * @param message the detail message. The detail message is saved for later retrieval by the {@link #getMessage()} method.
     * @param cause   the cause (which is saved for later retrieval by the {@link #getCause()} method). A {@code null} value is permitted, and indicates that the cause is nonexistent or unknown.
     */
    public ListChannelsException(String message, Throwable cause) {
        super(message, cause);
    }

}

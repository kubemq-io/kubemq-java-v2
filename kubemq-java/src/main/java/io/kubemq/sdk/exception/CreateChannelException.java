package io.kubemq.sdk.exception;

/**
 * Exception thrown when there is an error creating a channel.
 */
public class CreateChannelException extends RuntimeException {

    /**
     * Constructs a new CreateChannelException with {@code null} as its detail message.
     * The cause is not initialized.
     */
    public CreateChannelException() {
        super();
    }

    /**
     * Constructs a new CreateChannelException with the specified detail message.
     * The cause is not initialized.
     *
     * @param message the detail message. The detail message is saved for later retrieval by the {@link #getMessage()} method.
     */
    public CreateChannelException(String message) {
        super(message);
    }

    /**
     * Constructs a new CreateChannelException with the specified detail message and cause.
     *
     * @param message the detail message. The detail message is saved for later retrieval by the {@link #getMessage()} method.
     * @param cause   the cause (which is saved for later retrieval by the {@link #getCause()} method). A {@code null} value is permitted, and indicates that the cause is nonexistent or unknown.
     */
    public CreateChannelException(String message, Throwable cause) {
        super(message, cause);
    }

}

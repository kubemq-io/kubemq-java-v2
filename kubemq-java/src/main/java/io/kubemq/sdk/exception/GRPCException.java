package io.kubemq.sdk.exception;

/**
 * Exception thrown when there is a gRPC error.
 */
public class GRPCException extends RuntimeException {

    /**
     * Constructs a new GRPCException with {@code null} as its detail message.
     * The cause is not initialized.
     */
    public GRPCException() {
        super();
    }

    /**
     * Constructs a new GRPCException with the specified detail message.
     * The cause is not initialized.
     *
     * @param message the detail message. The detail message is saved for later retrieval by the {@link #getMessage()} method.
     */
    public GRPCException(String message) {
        super(message);
    }

    /**
     * Constructs a new GRPCException with the specified cause and a detail message of {@code (cause==null ? null : cause.toString())}.
     * This constructor is useful for exceptions that are primarily caused by another throwable.
     *
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method). A {@code null} value is permitted, and indicates that the cause is nonexistent or unknown.
     */
    public GRPCException(Throwable cause) {
        super(cause);
    }
}

package io.kubemq.sdk.exception;

/**
 * Machine-readable error codes for KubeMQ SDK errors.
 * Stable across releases per SemVer: new codes may be added in minor versions,
 * existing codes are never removed or changed in meaning within a major version.
 */
public enum ErrorCode {
    CONNECTION_FAILED,
    CONNECTION_TIMEOUT,
    CONNECTION_CLOSED,

    AUTHENTICATION_FAILED,
    AUTHORIZATION_DENIED,

    OPERATION_TIMEOUT,
    DEADLINE_EXCEEDED,

    INVALID_ARGUMENT,
    FAILED_PRECONDITION,
    ALREADY_EXISTS,
    OUT_OF_RANGE,

    NOT_FOUND,

    SERVER_INTERNAL,
    SERVER_UNIMPLEMENTED,
    DATA_LOSS,
    UNKNOWN_ERROR,

    UNAVAILABLE,
    ABORTED,

    RESOURCE_EXHAUSTED,
    BUFFER_FULL,
    RETRY_THROTTLED,

    CANCELLED_BY_CLIENT,
    CANCELLED_BY_SERVER,

    STREAM_BROKEN,

    HANDLER_ERROR,

    PARTIAL_FAILURE,

    FEATURE_NOT_IMPLEMENTED
}

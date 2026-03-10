# WU-1 Output: 01-error-handling-spec.md

**Status:** COMPLETED
**Build:** PASS
**Build Fix Attempts:** 2 (KubeMQException builder method hiding, onErrorCallback type mismatch in tests)
**Tests:** 985 passed, 0 failed, 0 skipped

## REQ Status

- [x] REQ-ERR-1: Typed exception hierarchy — implemented
- [x] REQ-ERR-2: Error classification — implemented
- [x] REQ-ERR-3: Auto-retry with configurable policy — implemented
- [x] REQ-ERR-4: Per-operation timeouts — implemented
- [x] REQ-ERR-5: Actionable error messages — implemented
- [x] REQ-ERR-6: gRPC error mapping — implemented
- [x] REQ-ERR-7: Retry throttling — implemented (integrated in RetryExecutor)
- [x] REQ-ERR-8: Streaming error handling — implemented
- [x] REQ-ERR-9: Async error propagation — implemented

## Files Created

- `kubemq-java/src/main/java/io/kubemq/sdk/exception/ErrorCode.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/exception/ErrorCategory.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/exception/KubeMQException.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/exception/ConnectionException.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/exception/AuthenticationException.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/exception/AuthorizationException.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/exception/KubeMQTimeoutException.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/exception/ValidationException.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/exception/ServerException.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/exception/ThrottlingException.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/exception/HandlerException.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/exception/StreamBrokenException.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/exception/TransportException.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/exception/BackpressureException.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/exception/OperationCancelledException.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/exception/RetryThrottledException.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/exception/PartialFailureException.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/exception/ErrorClassifier.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/exception/ErrorMessageBuilder.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/exception/GrpcErrorMapper.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/retry/RetryPolicy.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/retry/OperationSafety.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/retry/RetryExecutor.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/common/Defaults.java`
- `kubemq-java/src/test/java/io/kubemq/sdk/unit/exception/KubeMQExceptionTest.java`
- `kubemq-java/src/test/java/io/kubemq/sdk/unit/exception/ErrorClassifierTest.java`
- `kubemq-java/src/test/java/io/kubemq/sdk/unit/exception/ErrorMessageBuilderTest.java`
- `kubemq-java/src/test/java/io/kubemq/sdk/unit/exception/GrpcErrorMapperTest.java`
- `kubemq-java/src/test/java/io/kubemq/sdk/unit/exception/ErrorCategoryTest.java`
- `kubemq-java/src/test/java/io/kubemq/sdk/unit/exception/ErrorCodeTest.java`
- `kubemq-java/src/test/java/io/kubemq/sdk/unit/retry/RetryPolicyTest.java`
- `kubemq-java/src/test/java/io/kubemq/sdk/unit/retry/OperationSafetyTest.java`
- `kubemq-java/src/test/java/io/kubemq/sdk/unit/retry/RetryExecutorTest.java`
- `kubemq-java/src/test/java/io/kubemq/sdk/unit/common/DefaultsTest.java`

## Files Modified

- `kubemq-java/src/main/java/io/kubemq/sdk/exception/GRPCException.java`: Deprecated, now extends KubeMQException
- `kubemq-java/src/main/java/io/kubemq/sdk/exception/CreateChannelException.java`: Deprecated, now extends KubeMQException
- `kubemq-java/src/main/java/io/kubemq/sdk/exception/DeleteChannelException.java`: Deprecated, now extends KubeMQException
- `kubemq-java/src/main/java/io/kubemq/sdk/exception/ListChannelsException.java`: Deprecated, now extends KubeMQException
- `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java`: Integrated GrpcErrorMapper for gRPC errors, throw ConnectionException for SSL errors, removed .enableRetry()
- `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/PubSubClient.java`: All RuntimeException throws replaced with GrpcErrorMapper-based KubeMQException
- `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventStreamHelper.java`: RuntimeException replaced with KubeMQException
- `kubemq-java/src/main/java/io/kubemq/sdk/common/KubeMQUtils.java`: GrpcErrorMapper integration for gRPC errors, KubeMQException for decode errors
- `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventsSubscription.java`: Typed onErrorCallback (Consumer<KubeMQException>), handler error isolation, GrpcErrorMapper in stream errors
- `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventsStoreSubscription.java`: Same as EventsSubscription
- `kubemq-java/src/main/java/io/kubemq/sdk/cq/CommandsSubscription.java`: Same as EventsSubscription
- `kubemq-java/src/main/java/io/kubemq/sdk/cq/QueriesSubscription.java`: Same as EventsSubscription
- `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueUpstreamHandler.java`: RuntimeException replaced with KubeMQException
- `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueDownstreamHandler.java`: RuntimeException replaced with KubeMQException
- `kubemq-java/src/test/java/io/kubemq/sdk/unit/pubsub/EventsSubscriptionTest.java`: Updated onErrorCallback to Consumer<KubeMQException>
- `kubemq-java/src/test/java/io/kubemq/sdk/unit/pubsub/EventsStoreSubscriptionTest.java`: Updated onErrorCallback to Consumer<KubeMQException>
- `kubemq-java/src/test/java/io/kubemq/sdk/unit/cq/CommandsSubscriptionTest.java`: Updated onErrorCallback to Consumer<KubeMQException>
- `kubemq-java/src/test/java/io/kubemq/sdk/unit/cq/QueriesSubscriptionTest.java`: Updated onErrorCallback to Consumer<KubeMQException>
- `kubemq-java/src/test/java/io/kubemq/sdk/integration/PubSubIntegrationTest.java`: Updated onErrorCallback to Consumer<KubeMQException>
- `kubemq-java/src/test/java/io/kubemq/sdk/integration/CQIntegrationTest.java`: Updated onErrorCallback to Consumer<KubeMQException>
- `kubemq-java/src/test/java/io/kubemq/sdk/unit/common/KubeMQUtilsTest.java`: Updated assertThrows to expect KubeMQException

## Breaking Changes Applied

- **onErrorCallback type changed** from `Consumer<String>` to `Consumer<KubeMQException>` in EventsSubscription, EventsStoreSubscription, CommandsSubscription, QueriesSubscription. Users must update their error callback lambdas. (migration guide needed)
- **Legacy exceptions deprecated**: GRPCException, CreateChannelException, DeleteChannelException, ListChannelsException now extend KubeMQException and are marked @Deprecated. Existing catch blocks still work due to inheritance, but new code should catch KubeMQException subtypes.
- **RuntimeException no longer thrown**: All throw sites now throw KubeMQException subtypes instead of raw RuntimeException. Code catching `RuntimeException` still works due to inheritance, but this is a semantic change.

## Notes

- **KubeMQTimeoutException naming**: Used `KubeMQTimeoutException` instead of `TimeoutException` per user resolution to avoid JDK clash with `java.util.concurrent.TimeoutException`.
- **builder() vs newBuilder()**: Renamed `KubeMQException.builder()` to `KubeMQException.newBuilder()` to avoid Java static method hiding issues with subclass `builder()` methods. Subclasses use `builder()` as their factory method.
- **RetryExecutor concurrency limiter**: When `maxConcurrentRetries` is 0, no semaphore is created (null), meaning no throttling occurs. This matches the code logic where 0 is "unlimited".
- **Rich error detail extraction**: GrpcErrorMapper extracts `ErrorInfo`, `RetryInfo`, and `DebugInfo` from gRPC `google.rpc.Status` when available.
- **Handler error isolation**: Subscription `onNext` handlers now catch user callback exceptions and wrap them as `HandlerException`, calling `raiseOnError` without terminating the stream.
- **Reconnection guard**: Stream `onError` only triggers `reconnect()` if the mapped error is retryable.

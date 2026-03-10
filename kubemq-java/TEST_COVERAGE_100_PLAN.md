# Test Coverage Plan: Achieving 100% Coverage

> Generated: 2025-12-24
> Current Overall SDK Coverage: ~73%
> Target: 100%

## Executive Summary

This document outlines the specific tests needed to achieve 100% code coverage for the KubeMQ Java SDK. The analysis excludes auto-generated gRPC code (`kubemq` package) and focuses only on SDK code (`io.kubemq.sdk.*`).

### Current Coverage by Module

| Module | Lines Covered | Lines Missed | Line Coverage | Status |
|--------|--------------|--------------|---------------|--------|
| `io.kubemq.sdk.client` | 75 | 35 | 68.2% | 🟡 |
| `io.kubemq.sdk.common` | 86 | 58 | 59.7% | 🟡 |
| `io.kubemq.sdk.exception` | 24 | 0 | 100% | ✅ |
| `io.kubemq.sdk.cq` | 184 | 41 | 81.8% | 🟡 |
| `io.kubemq.sdk.pubsub` | 271 | 68 | 79.9% | 🟡 |
| `io.kubemq.sdk.queues` | 346 | 150 | 69.8% | 🟡 |

---

## Module 1: `io.kubemq.sdk.client` (68.2% → 100%)

### Class: `KubeMQClient.java`

#### Uncovered Code Paths

| ID | Line(s) | Code Path | Priority | Status |
|----|---------|-----------|----------|--------|
| C-01 | 116-143 | TLS connection initialization with SSL context | High | ⬜ |
| C-02 | 125-132 | CA cert file and client cert/key configuration | High | ⬜ |
| C-03 | 134-138 | TLS with keepAlive configuration | Medium | ⬜ |
| C-04 | 140-143 | SSLException handling | High | ⬜ |
| C-05 | 149-153 | Non-TLS keepAlive configuration branches | Medium | ⬜ |
| C-06 | 178-192 | `handleStateChange()` - TRANSIENT_FAILURE and SHUTDOWN states | High | ⬜ |
| C-07 | 217-224 | `close()` with InterruptedException | Medium | ⬜ |
| C-08 | 253-256 | `ping()` with StatusRuntimeException | High | ⬜ |
| C-09 | 276-286 | `MetadataInterceptor` inner class | Medium | ⬜ |

#### Tests to Implement

```java
// File: src/test/java/io/kubemq/sdk/unit/client/KubeMQClientTLSTest.java

@Nested
class TLSConnectionTests {

    @Test
    @DisplayName("C-01: TLS enabled with CA cert creates secure channel")
    void builder_withTlsEnabled_andCaCert_createsSecureChannel() {
        // Test TLS with CA cert file
        // Status: ⬜ TODO
    }

    @Test
    @DisplayName("C-02: TLS enabled with client certs creates secure channel")
    void builder_withTlsEnabled_andClientCerts_createsSecureChannel() {
        // Test TLS with client cert and key files
        // Status: ⬜ TODO
    }

    @Test
    @DisplayName("C-03: TLS enabled with keepAlive sets keepAlive options")
    void builder_withTlsEnabled_andKeepAlive_setsKeepAliveOptions() {
        // Test TLS + keepAlive combinations
        // Status: ⬜ TODO
    }

    @Test
    @DisplayName("C-04: TLS enabled with invalid cert throws RuntimeException")
    void builder_withTlsEnabled_invalidCert_throwsRuntimeException() {
        // Test SSLException path (line 140-143)
        // Status: ⬜ TODO
    }
}

@Nested
class ChannelStateChangeTests {

    @Test
    @DisplayName("C-06a: TRANSIENT_FAILURE state resets connect backoff")
    void handleStateChange_transientFailure_resetsConnectBackoff() {
        // Mock channel state transitions
        // Status: ⬜ TODO
    }

    @Test
    @DisplayName("C-06b: SHUTDOWN state logs shutdown message")
    void handleStateChange_shutdown_logsShutdown() {
        // Test SHUTDOWN state handling
        // Status: ⬜ TODO
    }
}

@Nested
class CloseInterruptedTests {

    @Test
    @DisplayName("C-07: Close interrupted during shutdown logs error")
    void close_interruptedDuringShutdown_logsError() {
        // Test InterruptedException in close()
        // Status: ⬜ TODO
    }
}

@Nested
class PingErrorTests {

    @Test
    @DisplayName("C-08: Ping with server unavailable throws RuntimeException")
    void ping_serverUnavailable_throwsRuntimeException() {
        // Mock StatusRuntimeException from blockingStub.ping()
        // Status: ⬜ TODO
    }
}

@Nested
class MetadataInterceptorTests {

    @Test
    @DisplayName("C-09: InterceptCall with metadata merges headers")
    void interceptCall_withMetadata_mergesHeaders() {
        // Test the MetadataInterceptor.interceptCall method
        // Status: ⬜ TODO
    }
}

@Nested
class PlaintextKeepAliveTests {

    @Test
    @DisplayName("C-05: Non-TLS with keepAlive sets keepAlive options")
    void builder_withoutTls_withKeepAlive_setsKeepAliveOptions() {
        // Test non-TLS keepAlive configuration
        // Status: ⬜ TODO
    }
}
```

---

## Module 2: `io.kubemq.sdk.common` (59.7% → 100%)

### Class: `KubeMQUtils.java`

#### Uncovered Code Paths

| ID | Line(s) | Code Path | Priority | Status |
|----|---------|-----------|----------|--------|
| U-01 | 54-58 | `createChannelRequest()` - Response not executed | High | ⬜ |
| U-02 | 59-61 | `createChannelRequest()` - StatusRuntimeException | High | ⬜ |
| U-03 | 62 | `createChannelRequest()` - null response returns null | Medium | ⬜ |
| U-04 | 93-95 | `deleteChannelRequest()` - Response not executed | High | ⬜ |
| U-05 | 96-98 | `deleteChannelRequest()` - StatusRuntimeException | High | ⬜ |
| U-06 | 99 | `deleteChannelRequest()` - null response returns null | Medium | ⬜ |
| U-07 | 129-131 | `listQueuesChannels()` - Response not executed | High | ⬜ |
| U-08 | 133-135 | `listQueuesChannels()` - StatusRuntimeException | High | ⬜ |
| U-09 | 136-139 | `listQueuesChannels()` - IOException | Medium | ⬜ |
| U-10 | 140 | `listQueuesChannels()` - null response returns null | Medium | ⬜ |
| U-11 | 170-172 | `listPubSubChannels()` - Response not executed | High | ⬜ |
| U-12 | 174-176 | `listPubSubChannels()` - StatusRuntimeException | High | ⬜ |
| U-13 | 177-180 | `listPubSubChannels()` - IOException | Medium | ⬜ |
| U-14 | 212-214 | `listCQChannels()` - Response not executed | High | ⬜ |
| U-15 | 216-218 | `listCQChannels()` - StatusRuntimeException | High | ⬜ |
| U-16 | 219-222 | `listCQChannels()` - IOException | Medium | ⬜ |

#### Tests to Implement

```java
// File: src/test/java/io/kubemq/sdk/unit/common/KubeMQUtilsTest.java

class KubeMQUtilsTest {

    private KubeMQClient mockClient;
    private kubemqGrpc.kubemqBlockingStub mockBlockingStub;

    @BeforeEach
    void setup() {
        mockClient = mock(KubeMQClient.class);
        mockBlockingStub = mock(kubemqGrpc.kubemqBlockingStub.class);
        when(mockClient.getClient()).thenReturn(mockBlockingStub);
    }

    @Nested
    class CreateChannelTests {

        @Test
        @DisplayName("U-01: Create channel not executed throws CreateChannelException")
        void createChannel_notExecuted_throwsCreateChannelException() {
            // Mock response.getExecuted() = false
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("U-02: Create channel gRPC error throws GRPCException")
        void createChannel_grpcError_throwsGRPCException() {
            // Mock StatusRuntimeException
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("U-03: Create channel null response returns null")
        void createChannel_nullResponse_returnsNull() {
            // Edge case: null response
            // Status: ⬜ TODO
        }
    }

    @Nested
    class DeleteChannelTests {

        @Test
        @DisplayName("U-04: Delete channel not executed throws DeleteChannelException")
        void deleteChannel_notExecuted_throwsDeleteChannelException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("U-05: Delete channel gRPC error throws GRPCException")
        void deleteChannel_grpcError_throwsGRPCException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("U-06: Delete channel null response returns null")
        void deleteChannel_nullResponse_returnsNull() {
            // Status: ⬜ TODO
        }
    }

    @Nested
    class ListQueuesChannelsTests {

        @Test
        @DisplayName("U-07: List queues not executed throws ListChannelsException")
        void listQueuesChannels_notExecuted_throwsListChannelsException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("U-08: List queues gRPC error throws GRPCException")
        void listQueuesChannels_grpcError_throwsGRPCException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("U-09: List queues IO error throws RuntimeException")
        void listQueuesChannels_ioError_throwsRuntimeException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("U-10: List queues null response returns null")
        void listQueuesChannels_nullResponse_returnsNull() {
            // Status: ⬜ TODO
        }
    }

    @Nested
    class ListPubSubChannelsTests {

        @Test
        @DisplayName("U-11: List PubSub not executed throws ListChannelsException")
        void listPubSubChannels_notExecuted_throwsListChannelsException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("U-12: List PubSub gRPC error throws GRPCException")
        void listPubSubChannels_grpcError_throwsGRPCException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("U-13: List PubSub IO error throws RuntimeException")
        void listPubSubChannels_ioError_throwsRuntimeException() {
            // Status: ⬜ TODO
        }
    }

    @Nested
    class ListCQChannelsTests {

        @Test
        @DisplayName("U-14: List CQ not executed throws ListChannelsException")
        void listCQChannels_notExecuted_throwsListChannelsException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("U-15: List CQ gRPC error throws GRPCException")
        void listCQChannels_grpcError_throwsGRPCException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("U-16: List CQ IO error throws RuntimeException")
        void listCQChannels_ioError_throwsRuntimeException() {
            // Status: ⬜ TODO
        }
    }
}
```

---

## Module 3: `io.kubemq.sdk.cq` (81.8% → 100%)

### Classes: `CommandsSubscription.java`, `QueriesSubscription.java`, `CommandMessage.java`, `QueryMessage.java`

#### Uncovered Code Paths

| ID | Class | Line(s) | Code Path | Priority | Status |
|----|-------|---------|-----------|----------|--------|
| CQ-01 | CommandsSubscription | StreamObserver | `onNext` callback | High | ⬜ |
| CQ-02 | CommandsSubscription | StreamObserver | `onError` callback | High | ⬜ |
| CQ-03 | CommandsSubscription | StreamObserver | `onCompleted` callback | Medium | ⬜ |
| CQ-04 | QueriesSubscription | StreamObserver | `onNext` callback | High | ⬜ |
| CQ-05 | QueriesSubscription | StreamObserver | `onError` callback | High | ⬜ |
| CQ-06 | QueriesSubscription | StreamObserver | `onCompleted` callback | Medium | ⬜ |
| CQ-07 | CommandMessage | encode | Empty tags branch | Low | ⬜ |
| CQ-08 | CommandMessage | encode | Null tags branch | Low | ⬜ |
| CQ-09 | QueryMessage | encode | Empty tags branch | Low | ⬜ |
| CQ-10 | QueryMessage | encode | Null tags branch | Low | ⬜ |

#### Tests to Implement

```java
// File: src/test/java/io/kubemq/sdk/unit/cq/CommandsSubscriptionTest.java

class CommandsSubscriptionTest {

    @Nested
    class StreamObserverTests {

        @Test
        @DisplayName("CQ-01: onNext receives command and invokes callback")
        void onNext_receivesCommand_invokesCallback() {
            // Simulate receiving a command message
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("CQ-02: onError receives error and invokes error callback")
        void onError_receivesError_invokesErrorCallback() {
            // Simulate stream error
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("CQ-03: onCompleted stream completes and invokes error callback")
        void onCompleted_streamCompletes_invokesErrorCallback() {
            // Simulate stream completion
            // Status: ⬜ TODO
        }
    }
}

// File: src/test/java/io/kubemq/sdk/unit/cq/QueriesSubscriptionTest.java

class QueriesSubscriptionTest {

    @Nested
    class StreamObserverTests {

        @Test
        @DisplayName("CQ-04: onNext receives query and invokes callback")
        void onNext_receivesQuery_invokesCallback() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("CQ-05: onError receives error and invokes error callback")
        void onError_receivesError_invokesErrorCallback() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("CQ-06: onCompleted stream completes and handles")
        void onCompleted_streamCompletes_handles() {
            // Status: ⬜ TODO
        }
    }
}

// File: src/test/java/io/kubemq/sdk/unit/cq/CommandMessageTest.java

class CommandMessageTest {

    @Nested
    class EncodingEdgeCases {

        @Test
        @DisplayName("CQ-07: Encode with empty tags encodes correctly")
        void encode_withEmptyTags_encodesCorrectly() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("CQ-08: Encode with null tags handles gracefully")
        void encode_withNullTags_handlesGracefully() {
            // Status: ⬜ TODO
        }
    }
}

// File: src/test/java/io/kubemq/sdk/unit/cq/QueryMessageTest.java

class QueryMessageTest {

    @Nested
    class EncodingEdgeCases {

        @Test
        @DisplayName("CQ-09: Encode with empty tags encodes correctly")
        void encode_withEmptyTags_encodesCorrectly() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("CQ-10: Encode with null tags handles gracefully")
        void encode_withNullTags_handlesGracefully() {
            // Status: ⬜ TODO
        }
    }
}
```

---

## Module 4: `io.kubemq.sdk.pubsub` (79.9% → 100%)

### Classes: `PubSubClient.java`, `EventsSubscription.java`, `EventsStoreSubscription.java`, `EventStreamHelper.java`

#### Uncovered Code Paths

| ID | Class | Line(s) | Code Path | Priority | Status |
|----|-------|---------|-----------|----------|--------|
| PS-01 | PubSubClient | 41-44 | `sendEventsMessage` exception path | High | ⬜ |
| PS-02 | PubSubClient | 60-63 | `sendEventsStoreMessage` exception path | High | ⬜ |
| PS-03 | PubSubClient | 90-93 | `createEventsChannel` exception path | High | ⬜ |
| PS-04 | PubSubClient | 120-123 | `createEventsStoreChannel` exception path | High | ⬜ |
| PS-05 | PubSubClient | 152-158 | `listEventsChannels` not executed + exception | High | ⬜ |
| PS-06 | PubSubClient | 186-192 | `listEventsStoreChannels` error paths | High | ⬜ |
| PS-07 | PubSubClient | 208-211 | `subscribeToEvents` exception path | High | ⬜ |
| PS-08 | PubSubClient | 227-230 | `subscribeToEventsStore` exception path | High | ⬜ |
| PS-09 | PubSubClient | 257-260 | `deleteEventsChannel` exception path | High | ⬜ |
| PS-10 | PubSubClient | 287-290 | `deleteEventsStoreChannel` exception path | High | ⬜ |
| PS-11 | EventsSubscription | StreamObserver | `onNext` callback | High | ⬜ |
| PS-12 | EventsSubscription | StreamObserver | `onError` callback | High | ⬜ |
| PS-13 | EventsSubscription | StreamObserver | `onCompleted` callback | Medium | ⬜ |
| PS-14 | EventsStoreSubscription | StreamObserver | `onNext` callback | High | ⬜ |
| PS-15 | EventsStoreSubscription | StreamObserver | `onError` callback | High | ⬜ |
| PS-16 | EventsStoreSubscription | StreamObserver | `onCompleted` callback | Medium | ⬜ |
| PS-17 | EventStreamHelper | StreamObserver | `onNext` callback | Medium | ⬜ |
| PS-18 | EventStreamHelper | StreamObserver | `onError` callback | High | ⬜ |
| PS-19 | EventStreamHelper | StreamObserver | `onCompleted` callback | Medium | ⬜ |

#### Tests to Implement

```java
// File: src/test/java/io/kubemq/sdk/unit/pubsub/PubSubClientExceptionTest.java

class PubSubClientExceptionTest {

    private PubSubClient client;
    private kubemqGrpc.kubemqBlockingStub mockBlockingStub;
    private kubemqGrpc.kubemqStub mockAsyncStub;

    @Nested
    class SendExceptionTests {

        @Test
        @DisplayName("PS-01: sendEventsMessage gRPC error throws RuntimeException")
        void sendEventsMessage_grpcError_throwsRuntimeException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("PS-02: sendEventsStoreMessage gRPC error throws RuntimeException")
        void sendEventsStoreMessage_grpcError_throwsRuntimeException() {
            // Status: ⬜ TODO
        }
    }

    @Nested
    class CreateChannelExceptionTests {

        @Test
        @DisplayName("PS-03: createEventsChannel gRPC error throws RuntimeException")
        void createEventsChannel_grpcError_throwsRuntimeException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("PS-04: createEventsStoreChannel gRPC error throws RuntimeException")
        void createEventsStoreChannel_grpcError_throwsRuntimeException() {
            // Status: ⬜ TODO
        }
    }

    @Nested
    class ListChannelsExceptionTests {

        @Test
        @DisplayName("PS-05a: listEventsChannels not executed throws RuntimeException")
        void listEventsChannels_notExecuted_throwsRuntimeException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("PS-05b: listEventsChannels gRPC error throws RuntimeException")
        void listEventsChannels_grpcError_throwsRuntimeException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("PS-06a: listEventsStoreChannels not executed throws RuntimeException")
        void listEventsStoreChannels_notExecuted_throwsRuntimeException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("PS-06b: listEventsStoreChannels gRPC error throws RuntimeException")
        void listEventsStoreChannels_grpcError_throwsRuntimeException() {
            // Status: ⬜ TODO
        }
    }

    @Nested
    class SubscribeExceptionTests {

        @Test
        @DisplayName("PS-07: subscribeToEvents gRPC error throws RuntimeException")
        void subscribeToEvents_grpcError_throwsRuntimeException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("PS-08: subscribeToEventsStore gRPC error throws RuntimeException")
        void subscribeToEventsStore_grpcError_throwsRuntimeException() {
            // Status: ⬜ TODO
        }
    }

    @Nested
    class DeleteChannelExceptionTests {

        @Test
        @DisplayName("PS-09: deleteEventsChannel gRPC error throws RuntimeException")
        void deleteEventsChannel_grpcError_throwsRuntimeException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("PS-10: deleteEventsStoreChannel gRPC error throws RuntimeException")
        void deleteEventsStoreChannel_grpcError_throwsRuntimeException() {
            // Status: ⬜ TODO
        }
    }
}

// File: src/test/java/io/kubemq/sdk/unit/pubsub/EventsSubscriptionTest.java

class EventsSubscriptionTest {

    @Nested
    class StreamObserverTests {

        @Test
        @DisplayName("PS-11: onNext receives event and invokes callback")
        void onNext_receivesEvent_invokesCallback() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("PS-12: onError receives error and invokes error callback")
        void onError_receivesError_invokesErrorCallback() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("PS-13: onCompleted stream completes and logs")
        void onCompleted_streamCompletes_logsAndHandles() {
            // Status: ⬜ TODO
        }
    }
}

// File: src/test/java/io/kubemq/sdk/unit/pubsub/EventsStoreSubscriptionTest.java

class EventsStoreSubscriptionTest {

    @Nested
    class StreamObserverTests {

        @Test
        @DisplayName("PS-14: onNext receives event store message and invokes callback")
        void onNext_receivesEventStoreMessage_invokesCallback() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("PS-15: onError receives error and invokes error callback")
        void onError_receivesError_invokesErrorCallback() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("PS-16: onCompleted stream completes and logs")
        void onCompleted_streamCompletes_logsAndHandles() {
            // Status: ⬜ TODO
        }
    }
}

// File: src/test/java/io/kubemq/sdk/unit/pubsub/EventStreamHelperTest.java

class EventStreamHelperTest {

    @Nested
    class StreamObserverTests {

        @Test
        @DisplayName("PS-17: onNext processes result correctly")
        void onNext_receivesResult_processesCorrectly() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("PS-18: onError stream error logs error")
        void onError_streamError_logsError() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("PS-19: onCompleted stream completes logs completion")
        void onCompleted_streamCompletes_logsCompletion() {
            // Status: ⬜ TODO
        }
    }
}
```

---

## Module 5: `io.kubemq.sdk.queues` (69.8% → 100%)

### Classes: Multiple classes in queues package

#### Uncovered Code Paths

| ID | Class | Line(s) | Code Path | Priority | Status |
|----|-------|---------|-----------|----------|--------|
| Q-01 | QueuesClient | 92-125 | `waiting()` with messages | High | ⬜ |
| Q-02 | QueuesClient | 94-96 | `waiting()` null channel validation | High | ⬜ |
| Q-03 | QueuesClient | 97-99 | `waiting()` maxMessages validation | High | ⬜ |
| Q-04 | QueuesClient | 100-102 | `waiting()` timeout validation | High | ⬜ |
| Q-05 | QueuesClient | 137-170 | `pull()` with messages | High | ⬜ |
| Q-06 | QueuesClient | 139-147 | `pull()` validation branches | High | ⬜ |
| Q-07 | QueueDownstreamHandler | 77-80 | StreamObserver `onError` | High | ⬜ |
| Q-08 | QueueDownstreamHandler | 84-87 | StreamObserver `onCompleted` | High | ⬜ |
| Q-09 | QueueDownstreamHandler | 106-120 | `closeStreamWithError()` | High | ⬜ |
| Q-10 | QueueDownstreamHandler | 140-145 | `receiveQueuesMessagesAsync` exception | High | ⬜ |
| Q-11 | QueueDownstreamHandler | 168-170 | `sendRequest` reconnection | Medium | ⬜ |
| Q-12 | QueueDownstreamHandler | 180-182 | `sendRequest` null observer | Medium | ⬜ |
| Q-13 | QueueUpstreamHandler | 51-58 | `onNext` with error response | High | ⬜ |
| Q-14 | QueueUpstreamHandler | 59-66 | `onNext` with no results | High | ⬜ |
| Q-15 | QueueUpstreamHandler | 76-79 | StreamObserver `onError` | High | ⬜ |
| Q-16 | QueueUpstreamHandler | 82-85 | StreamObserver `onCompleted` | High | ⬜ |
| Q-17 | QueueUpstreamHandler | 101-112 | `closeStreamWithError()` | High | ⬜ |
| Q-18 | QueueUpstreamHandler | 128-133 | Async send exception | High | ⬜ |
| Q-19 | QueueUpstreamHandler | 143-146 | Sync send exception | High | ⬜ |
| Q-20 | QueueUpstreamHandler | 161-163 | `sendRequest` null observer | Medium | ⬜ |
| Q-21 | QueuesPollResponse | 58-60 | `ackAll()` | High | ⬜ |
| Q-22 | QueuesPollResponse | 62-64 | `rejectAll()` | High | ⬜ |
| Q-23 | QueuesPollResponse | 66-68 | `reQueueAll()` | High | ⬜ |
| Q-24 | QueuesPollResponse | 71-73 | `doOperation` auto-acked check | High | ⬜ |
| Q-25 | QueuesPollResponse | 74-76 | `doOperation` completed check | High | ⬜ |
| Q-26 | QueuesPollResponse | 77-79 | `doOperation` null sender check | High | ⬜ |
| Q-27 | QueuesPollResponse | 88-90 | `doOperation` reQueue channel | Medium | ⬜ |
| Q-28 | QueueMessageReceived | 70-72 | `ack()` | High | ⬜ |
| Q-29 | QueueMessageReceived | 75-77 | `reject()` | High | ⬜ |
| Q-30 | QueueMessageReceived | 80-85 | `reQueue()` with validation | High | ⬜ |
| Q-31 | QueueMessageReceived | 89-91 | `doOperation` auto-acked | High | ⬜ |
| Q-32 | QueueMessageReceived | 92-94 | `doOperation` completed | High | ⬜ |
| Q-33 | QueueMessageReceived | 95-97 | `doOperation` null sender | High | ⬜ |
| Q-34 | QueueMessageReceived | 113-116 | Timer cancellation | Medium | ⬜ |
| Q-35 | QueueMessageReceived | 172-177 | `onVisibilityExpired()` | High | ⬜ |
| Q-36 | QueueMessageReceived | 179-195 | `extendVisibilityTimer()` all branches | High | ⬜ |
| Q-37 | QueueMessageReceived | 197-213 | `resetVisibilityTimer()` all branches | High | ⬜ |
| Q-38 | QueueDownStreamProcessor | 20-46 | Static executor and task queue | Medium | ⬜ |
| Q-39 | QueueMessagesPulled | All | Entire class | Medium | ⬜ |
| Q-40 | QueueMessagesReceived | All | Entire class | Medium | ⬜ |

#### Tests to Implement

```java
// File: src/test/java/io/kubemq/sdk/unit/queues/QueuesClientWaitingPullTest.java

class QueuesClientWaitingPullTest {

    @Nested
    class WaitingTests {

        @Test
        @DisplayName("Q-01: waiting with messages returns QueueMessagesWaiting")
        void waiting_withMessages_returnsQueueMessagesWaiting() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-02: waiting null channel throws IllegalArgumentException")
        void waiting_nullChannel_throwsIllegalArgumentException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-03: waiting zero maxMessages throws IllegalArgumentException")
        void waiting_zeroMaxMessages_throwsIllegalArgumentException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-04: waiting zero timeout throws IllegalArgumentException")
        void waiting_zeroTimeout_throwsIllegalArgumentException() {
            // Status: ⬜ TODO
        }
    }

    @Nested
    class PullTests {

        @Test
        @DisplayName("Q-05: pull with messages returns QueueMessagesPulled")
        void pull_withMessages_returnsQueueMessagesPulled() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-06a: pull null channel throws IllegalArgumentException")
        void pull_nullChannel_throwsIllegalArgumentException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-06b: pull zero maxMessages throws IllegalArgumentException")
        void pull_zeroMaxMessages_throwsIllegalArgumentException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-06c: pull zero timeout throws IllegalArgumentException")
        void pull_zeroTimeout_throwsIllegalArgumentException() {
            // Status: ⬜ TODO
        }
    }
}

// File: src/test/java/io/kubemq/sdk/unit/queues/QueueDownstreamHandlerTest.java

class QueueDownstreamHandlerTest {

    @Nested
    class StreamObserverTests {

        @Test
        @DisplayName("Q-07: onError closes stream with error")
        void streamObserver_onError_closesStreamWithError() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-08: onCompleted closes stream with error")
        void streamObserver_onCompleted_closesStreamWithError() {
            // Status: ⬜ TODO
        }
    }

    @Nested
    class CloseStreamTests {

        @Test
        @DisplayName("Q-09: closeStreamWithError completes all pending futures")
        void closeStreamWithError_completesAllPendingFutures() {
            // Status: ⬜ TODO
        }
    }

    @Nested
    class ReceiveMessagesTests {

        @Test
        @DisplayName("Q-10: receiveQueuesMessagesAsync encoding error completes exceptionally")
        void receiveQueuesMessagesAsync_encodingError_completesExceptionally() {
            // Status: ⬜ TODO
        }
    }

    @Nested
    class SendRequestTests {

        @Test
        @DisplayName("Q-11: sendRequest not connected triggers reconnection")
        void sendRequest_notConnected_connects() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-12: sendRequest null observer logs warning")
        void sendRequest_nullObserver_logsWarning() {
            // Status: ⬜ TODO
        }
    }
}

// File: src/test/java/io/kubemq/sdk/unit/queues/QueueUpstreamHandlerTest.java

class QueueUpstreamHandlerTest {

    @Nested
    class OnNextTests {

        @Test
        @DisplayName("Q-13: onNext with error response completes with error")
        void streamObserver_onNext_withError_completesWithError() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-14: onNext with no results completes with no results error")
        void streamObserver_onNext_noResults_completesWithNoResultsError() {
            // Status: ⬜ TODO
        }
    }

    @Nested
    class StreamObserverTests {

        @Test
        @DisplayName("Q-15: onError closes stream with error")
        void streamObserver_onError_closesStreamWithError() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-16: onCompleted closes stream with error")
        void streamObserver_onCompleted_closesStreamWithError() {
            // Status: ⬜ TODO
        }
    }

    @Nested
    class CloseStreamTests {

        @Test
        @DisplayName("Q-17: closeStreamWithError completes all pending futures")
        void closeStreamWithError_completesAllPendingFutures() {
            // Status: ⬜ TODO
        }
    }

    @Nested
    class SendTests {

        @Test
        @DisplayName("Q-18: sendQueuesMessageAsync exception completes exceptionally")
        void sendQueuesMessageAsync_exception_completesExceptionally() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-19: sendQueuesMessage future exception throws RuntimeException")
        void sendQueuesMessage_futureException_throwsRuntimeException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-20: sendRequest null observer logs warning")
        void sendRequest_nullObserver_logsWarning() {
            // Status: ⬜ TODO
        }
    }
}

// File: src/test/java/io/kubemq/sdk/unit/queues/QueuesPollResponseTest.java

class QueuesPollResponseTest {

    @Nested
    class BatchOperationsTests {

        @Test
        @DisplayName("Q-21: ackAll sends AckAll request")
        void ackAll_validTransaction_sendsAckAllRequest() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-22: rejectAll sends NAckAll request")
        void rejectAll_validTransaction_sendsNAckAllRequest() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-23: reQueueAll sends ReQueueAll request")
        void reQueueAll_validTransaction_sendsReQueueAllRequest() {
            // Status: ⬜ TODO
        }
    }

    @Nested
    class DoOperationValidationTests {

        @Test
        @DisplayName("Q-24: doOperation auto-acked throws IllegalStateException")
        void ackAll_autoAcked_throwsIllegalStateException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-25: doOperation completed throws IllegalStateException")
        void ackAll_transactionCompleted_throwsIllegalStateException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-26: doOperation no sender throws IllegalStateException")
        void ackAll_noRequestSender_throwsIllegalStateException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-27: reQueueAll with channel sets reQueue channel")
        void reQueueAll_withChannel_setsReQueueChannel() {
            // Status: ⬜ TODO
        }
    }
}

// File: src/test/java/io/kubemq/sdk/unit/queues/QueueMessageReceivedTest.java

class QueueMessageReceivedTest {

    @Nested
    class MessageOperationsTests {

        @Test
        @DisplayName("Q-28: ack sends Ack request")
        void ack_validMessage_sendsAckRequest() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-29: reject sends Reject request")
        void reject_validMessage_sendsRejectRequest() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-30a: reQueue sends ReQueue request")
        void reQueue_validMessage_sendsReQueueRequest() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-30b: reQueue null channel throws IllegalArgumentException")
        void reQueue_nullChannel_throwsIllegalArgumentException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-30c: reQueue empty channel throws IllegalArgumentException")
        void reQueue_emptyChannel_throwsIllegalArgumentException() {
            // Status: ⬜ TODO
        }
    }

    @Nested
    class DoOperationValidationTests {

        @Test
        @DisplayName("Q-31: ack auto-acked throws IllegalStateException")
        void ack_autoAcked_throwsIllegalStateException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-32: ack already completed throws IllegalStateException")
        void ack_alreadyCompleted_throwsIllegalStateException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-33: ack no sender throws IllegalStateException")
        void ack_noRequestSender_throwsIllegalStateException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-34: ack with active timer cancels timer")
        void ack_withActiveTimer_cancelsTimer() {
            // Status: ⬜ TODO
        }
    }

    @Nested
    class VisibilityTimerTests {

        @Test
        @DisplayName("Q-35: visibility expired rejects message and throws")
        void visibilityExpired_rejectsMessageAndThrowsException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-36a: extendVisibilityTimer valid extension extends timer")
        void extendVisibilityTimer_validExtension_extendsTimer() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-36b: extendVisibilityTimer zero seconds throws")
        void extendVisibilityTimer_zeroSeconds_throwsIllegalArgumentException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-36c: extendVisibilityTimer no active timer throws")
        void extendVisibilityTimer_noActiveTimer_throwsIllegalStateException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-36d: extendVisibilityTimer timer expired throws")
        void extendVisibilityTimer_timerExpired_throwsIllegalStateException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-36e: extendVisibilityTimer message completed throws")
        void extendVisibilityTimer_messageCompleted_throwsIllegalStateException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-37a: resetVisibilityTimer valid reset resets timer")
        void resetVisibilityTimer_validReset_resetsTimer() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-37b: resetVisibilityTimer zero seconds throws")
        void resetVisibilityTimer_zeroSeconds_throwsIllegalArgumentException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-37c: resetVisibilityTimer no active timer throws")
        void resetVisibilityTimer_noActiveTimer_throwsIllegalStateException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-37d: resetVisibilityTimer timer expired throws")
        void resetVisibilityTimer_timerExpired_throwsIllegalStateException() {
            // Status: ⬜ TODO
        }

        @Test
        @DisplayName("Q-37e: resetVisibilityTimer message completed throws")
        void resetVisibilityTimer_messageCompleted_throwsIllegalStateException() {
            // Status: ⬜ TODO
        }
    }
}

// File: src/test/java/io/kubemq/sdk/unit/queues/QueueDownStreamProcessorTest.java

class QueueDownStreamProcessorTest {

    @Test
    @DisplayName("Q-38a: addTask valid task executes task")
    void addTask_validTask_executesTask() {
        // Status: ⬜ TODO
    }

    @Test
    @DisplayName("Q-38b: addTask interrupted during put handles interrupt")
    void addTask_interruptedDuringPut_handlesInterrupt() {
        // Status: ⬜ TODO
    }
}

// File: src/test/java/io/kubemq/sdk/unit/queues/QueueMessagesPulledTest.java

class QueueMessagesPulledTest {

    @Test
    @DisplayName("Q-39a: builder creates empty pulled messages")
    void builder_createsEmptyPulledMessages() {
        // Status: ⬜ TODO
    }

    @Test
    @DisplayName("Q-39b: getMessages returns message list")
    void getMessages_returnsMessageList() {
        // Status: ⬜ TODO
    }
}
```

---

## Implementation Strategy

### Phase 1: High Priority Tests (Week 1)
Focus on exception handling and error paths:
- All tests marked as "High" priority
- Estimated: 60 tests

### Phase 2: Medium Priority Tests (Week 2)
Focus on edge cases and validation:
- All tests marked as "Medium" priority
- Estimated: 35 tests

### Phase 3: Low Priority Tests (Week 3)
Focus on encoding edge cases:
- All tests marked as "Low" priority
- Estimated: 17 tests

---

## Testing Infrastructure Required

### 1. Mock Dependencies
```xml
<!-- Already in pom.xml -->
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <version>5.x</version>
    <scope>test</scope>
</dependency>
```

### 2. gRPC Testing Support
```xml
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-testing</artifactId>
    <version>${grpc.version}</version>
    <scope>test</scope>
</dependency>
```

### 3. Awaitility for Async Testing
```xml
<!-- Already in pom.xml -->
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <version>4.x</version>
    <scope>test</scope>
</dependency>
```

### 4. Test Certificates for TLS Testing
Create test certificates in `src/test/resources/certs/`:
- `test-ca.pem`
- `test-client.pem`
- `test-client.key`

---

## Progress Tracking

### Summary

| Module | Total Tests | Completed | Remaining | Coverage |
|--------|------------|-----------|-----------|----------|
| `io.kubemq.sdk.client` | 15 | 0 | 15 | 68.2% → 100% |
| `io.kubemq.sdk.common` | 16 | 0 | 16 | 59.7% → 100% |
| `io.kubemq.sdk.cq` | 10 | 0 | 10 | 81.8% → 100% |
| `io.kubemq.sdk.pubsub` | 19 | 0 | 19 | 79.9% → 100% |
| `io.kubemq.sdk.queues` | 52 | 0 | 52 | 69.8% → 100% |
| **Total** | **112** | **0** | **112** | **~73% → 100%** |

### Legend
- ⬜ TODO
- 🟡 In Progress
- ✅ Completed
- ❌ Blocked

---

## Notes

1. **TLS Tests**: Require test certificates. Consider using self-signed certs for testing.

2. **Timer Tests**: Use short timeouts (100ms) to avoid slow test execution.

3. **gRPC Mocking**: Use `@Mock` annotations with `MockitoExtension` for clean mocking.

4. **StreamObserver Testing**: Create helper methods to simulate gRPC callbacks.

5. **Concurrency Tests**: Use `CountDownLatch` and `ExecutorService` for thread-safety testing.

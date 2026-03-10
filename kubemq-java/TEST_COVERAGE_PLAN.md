# KubeMQ Java SDK - Test Coverage Plan

## Executive Summary

This plan outlines the strategy to achieve 100% test coverage for the KubeMQ Java SDK. The approach prioritizes **integration tests** to validate real-world behavior against a running KubeMQ server, supplemented by **unit tests** for edge cases, error handling, and code paths that cannot be easily triggered via integration tests.

## Current State

### Coverage Summary (as of 2024-12-24)

| Package | Instruction | Branch | Status |
|---------|-------------|--------|--------|
| io.kubemq.sdk.cq | 71% | 70% | Good |
| io.kubemq.sdk.pubsub | 71% | 72% | Good |
| io.kubemq.sdk.queues | 57% | 49% | Needs Work |
| io.kubemq.sdk.common | 52% | 26% | Needs Work |
| io.kubemq.sdk.client | 51% | 25% | Needs Work |
| io.kubemq.sdk.exception | 0% | n/a | Not Tested |

### Test Counts
- **Unit Tests**: 209 tests
- **Integration Tests**: 43 tests
- **Total**: 252 tests (all passing)

## Motivation

1. **Production Readiness**: 100% coverage ensures all code paths are exercised, reducing production bugs
2. **Confidence in Refactoring**: Complete test coverage allows safe refactoring and feature additions
3. **Documentation by Example**: Tests serve as living documentation for SDK usage
4. **Regression Prevention**: Comprehensive tests catch regressions early in development
5. **API Contract Verification**: Integration tests validate the SDK works correctly with KubeMQ server

## Testing Strategy

### Priority 1: Integration Tests (Preferred)
Integration tests are preferred because they:
- Test real gRPC communication with KubeMQ server
- Validate end-to-end message flow
- Catch serialization/deserialization issues
- Verify timeout and retry behavior
- Test actual error responses from server

### Priority 2: Unit Tests (Where Integration Not Possible)
Unit tests are used when:
- Testing edge cases that are hard to trigger with a real server
- Testing validation logic and error messages
- Testing encoding/decoding with specific byte sequences
- Testing exception handling paths
- Testing POJO/DTO serialization with Jackson

### Handler Testing Strategy
Per previous architectural decisions:
- **Stream logic**: Tested via integration tests only (avoids complex gRPC mocking)
- **Response parsing**: Can be unit tested with hand-crafted protobuf responses
- **Rationale**: Mocking `StreamObserver` is complex, brittle, and doesn't catch real protocol issues

---

## Detailed Coverage Plan

### Phase 1: Exception Package (0% → 100%)

**Target**: `io.kubemq.sdk.exception`

| Class | Test Type | Tests Needed |
|-------|-----------|--------------|
| CreateChannelException | Unit | Constructor tests, message verification |
| DeleteChannelException | Unit | Constructor tests, message verification |
| GRPCException | Unit | Constructor tests (including no-arg), cause chaining |
| ListChannelsException | Unit | Constructor tests, message verification |

**Approach**: Unit tests only - exceptions are simple wrapper classes

**New File**: `src/test/java/io/kubemq/sdk/unit/exception/ExceptionTest.java`

```java
// Tests to implement:
- testCreateChannelException_withMessage()
- testCreateChannelException_withMessageAndCause()
- testDeleteChannelException_withMessage()
- testDeleteChannelException_withMessageAndCause()
- testGRPCException_noArg()
- testGRPCException_withMessage()
- testGRPCException_withMessageAndCause()
- testListChannelsException_withMessage()
- testListChannelsException_withMessageAndCause()
```

**Total: 9 tests**

---

### Phase 2: Common Package (52% → 95%+)

**Target**: `io.kubemq.sdk.common`

| Class | Current | Test Type | Tests Needed |
|-------|---------|-----------|--------------|
| ChannelDecoder | ~80% | Unit | Edge cases for malformed data |
| KubeMQUtils | ~30% | **Integration only** | Methods require gRPC client - covered by existing integration tests |
| RequestType | ~50% | Unit | Enum value tests |
| ServerInfo | ~60% | Unit + Integration | POJO field tests, decode from real ping |
| SubscribeType | ~50% | Unit | Enum value tests |

**Note**: `KubeMQUtils` contains only gRPC client wrapper methods (`createChannelRequest`, `deleteChannelRequest`, `listQueuesChannels`, etc.) - these are already exercised by integration tests. No unit tests needed.

**New File**: `src/test/java/io/kubemq/sdk/unit/common/EnumTest.java`
```java
// Tests to implement:
- testRequestType_allValues()
- testRequestType_valueOf()
- testRequestType_unknownValue_throwsException()
- testSubscribeType_allValues()
- testSubscribeType_valueOf()
- testSubscribeType_unknownValue_throwsException()
```

**Enhance File**: `src/test/java/io/kubemq/sdk/unit/common/ChannelDecoderTest.java`
```java
// Additional edge case tests:
- testDecode_withNullInput()
- testDecode_withEmptyInput()
- testDecode_withMalformedData()
```

**New File**: `src/test/java/io/kubemq/sdk/unit/common/ServerInfoTest.java`
```java
// Tests to implement:
- testDecode_fromPingResponse()
- testGetters_returnCorrectValues()
- testToString()
```

**Total: 12 tests**

---

### Phase 3: Client Package (51% → 90%+)

**Target**: `io.kubemq.sdk.client`

| Class | Current | Test Type | Tests Needed |
|-------|---------|-----------|--------------|
| KubeMQClient | 51% | Unit + Integration | Builder validation, connection handling |
| KubeMQClient.Level | 100% | - | Already covered |
| KubeMQClient.MetadataInterceptor | ~20% | Unit | Auth token injection |

**New File**: `src/test/java/io/kubemq/sdk/unit/client/KubeMQClientTest.java`
```java
// Tests to implement:
- testBuilder_withMinimalConfig()
- testBuilder_withAllOptions()
- testBuilder_missingAddress_throwsException()
- testBuilder_emptyAddress_throwsException()
- testBuilder_withAuthToken()
- testBuilder_withTls()
- testBuilder_allLogLevels()
- testClose_releasesResources()
- testClose_idempotent()
- testGetClientId_returnsConfiguredId()
- testGetAddress_returnsConfiguredAddress()
```

**Integration Test Addition**: All integration test files
```java
// Add connection tests:
- testConnection_withValidAddress_succeeds()
- testConnection_ping_returnsServerInfo()
```

**Total: 13 tests**

---

### Phase 4: CQ Package (71% → 95%+)

**Target**: `io.kubemq.sdk.cq`

| Class | Current | Test Type | Tests Needed |
|-------|---------|-----------|--------------|
| CQChannel | ~50% | Unit | POJO field tests |
| CQClient | ~70% | Integration | Edge cases |
| CQStats | ~30% | Unit | POJO/Jackson deserialization |
| CommandMessage | ~90% | Unit | Edge cases |
| CommandMessageReceived | ~80% | Unit | Edge cases |
| CommandResponseMessage | ~60% | Unit + Integration | Encode/decode round-trip |
| CommandsSubscription | ~70% | Integration | Error callback, reconnection |
| QueriesSubscription | ~70% | Integration | Error callback, reconnection |
| QueryMessage | ~90% | Unit | Edge cases |
| QueryMessageReceived | ~80% | Unit | Edge cases |
| QueryResponseMessage | ~60% | Unit + Integration | Encode/decode round-trip |

**New File**: `src/test/java/io/kubemq/sdk/unit/cq/CQChannelTest.java`
```java
// Tests to implement:
- testBuilder_withAllFields()
- testBuilder_withMinimalFields()
- testGetters_returnCorrectValues()
- testToString_includesAllFields()
```

**New File**: `src/test/java/io/kubemq/sdk/unit/cq/CQStatsTest.java`
```java
// Tests to implement (POJO tests, no decode method):
- testBuilder_withAllFields()
- testBuilder_withZeroValues()
- testGetters_returnCorrectValues()
- testToString_includesAllFields()
```

**New File**: `src/test/java/io/kubemq/sdk/unit/cq/CommandResponseMessageTest.java`
```java
// Tests to implement:
- testEncode_withAllFields()
- testEncode_withMinimalFields()
- testEncode_withNullError_usesEmptyString()
- testDecode_fromProtobuf()
- testValidate_withoutQueryReceived_throwsException()
- testValidate_withEmptyReplyChannel_throwsException()
- testRoundTrip_encodeAndDecode()
- testToString()
```

**New File**: `src/test/java/io/kubemq/sdk/unit/cq/QueryResponseMessageTest.java`
```java
// Tests to implement:
- testEncode_withAllFields()
- testEncode_withMetadata()
- testEncode_withTags()
- testEncode_withNullMetadata_usesEmptyString()
- testDecode_fromProtobuf()
- testValidate_withoutQueryReceived_throwsException()
- testRoundTrip_encodeAndDecode()
- testToString()
```

**Integration Test Addition**: `CQIntegrationTest.java`
```java
// Add tests:
- listCommandsChannels_shouldReturnChannelWithStats()
- listQueriesChannels_shouldReturnChannelWithStats()
- subscribeToCommands_withErrorCallback_shouldInvokeOnError()
- subscribeToQueries_withErrorCallback_shouldInvokeOnError()
- commandSubscription_cancel_shouldStopReceiving()
- querySubscription_cancel_shouldStopReceiving()
```

**Total: 30 tests**

---

### Phase 5: PubSub Package (71% → 95%+)

**Target**: `io.kubemq.sdk.pubsub`

| Class | Current | Test Type | Tests Needed |
|-------|---------|-----------|--------------|
| EventMessage | ~90% | Unit | Edge cases |
| EventMessageReceived | ~85% | Unit | Edge cases |
| EventSendResult | ~40% | Unit | POJO field tests, error handling |
| EventStoreMessage | ~90% | Unit | Edge cases |
| EventStoreMessageReceived | ~85% | Unit | Edge cases |
| EventStreamHelper | ~30% | **Integration only** | Stream logic tested via integration |
| EventsStoreSubscription | ~80% | Integration | Reconnection |
| EventsStoreType | ~60% | Unit | All enum values |
| EventsSubscription | ~80% | Integration | Reconnection |
| PubSubChannel | ~50% | Unit | POJO field tests |
| PubSubClient | ~70% | Integration | Edge cases |
| PubSubStats | ~30% | Unit | POJO field tests |

**Note**: `EventStreamHelper` contains gRPC stream handling logic - tested via integration tests only (per handler testing strategy).

**New File**: `src/test/java/io/kubemq/sdk/unit/pubsub/EventSendResultTest.java`
```java
// Tests to implement:
- testBuilder_withAllFields()
- testBuilder_withError()
- testIsSent_trueWhenNoError()
- testIsSent_falseWhenError()
- testGetters_returnCorrectValues()
- testToString()
```

**New File**: `src/test/java/io/kubemq/sdk/unit/pubsub/EventsStoreTypeTest.java`
```java
// Tests to implement:
- testAllEnumValues_exist()
- testValueOf_validValues()
- testGetValue_returnsCorrectInt()
- testStartNewOnly_value()
- testStartFromFirst_value()
- testStartFromLast_value()
- testStartAtSequence_value()
- testStartAtTime_value()
- testStartAtTimeDelta_value()
```

**New File**: `src/test/java/io/kubemq/sdk/unit/pubsub/PubSubChannelTest.java`
```java
// Tests to implement:
- testBuilder_withAllFields()
- testBuilder_withStats()
- testGetters_returnCorrectValues()
- testToString()
```

**New File**: `src/test/java/io/kubemq/sdk/unit/pubsub/PubSubStatsTest.java`
```java
// Tests to implement (POJO tests):
- testBuilder_withAllFields()
- testBuilder_withZeroValues()
- testGetters_returnCorrectValues()
- testToString()
```

**Integration Test Addition**: `PubSubIntegrationTest.java`
```java
// Add tests:
- eventsSubscription_withErrorCallback_shouldInvokeOnError()
- eventsStoreSubscription_withErrorCallback_shouldInvokeOnError()
- eventsStoreSubscription_startFromTimestamp_shouldWork()
- eventsStoreSubscription_startFromTimeDelta_shouldWork()
- listEventsChannels_shouldReturnChannelWithStats()
- listEventsStoreChannels_shouldReturnChannelWithStats()
```

**Total: 29 tests**

---

### Phase 6: Queues Package (57% → 95%+)

**Target**: `io.kubemq.sdk.queues`

| Class | Current | Test Type | Tests Needed |
|-------|---------|-----------|--------------|
| QueueDownStreamProcessor | ~30% | **Integration only** | Stream logic via integration |
| QueueDownstreamHandler | ~40% | **Integration only** | Stream logic via integration |
| QueueMessage | ~85% | Unit | Edge cases |
| QueueMessageReceived | ~60% | Unit + Integration | Response parsing, Ack/Reject |
| QueueMessageWaitingPulled | ~20% | Unit | POJO field tests |
| QueueMessagesPulled | ~20% | Unit | POJO field tests |
| QueueMessagesReceived | ~30% | Unit | POJO field tests |
| QueueMessagesWaiting | ~40% | Unit + Integration | POJO + integration |
| QueueSendResult | ~50% | Unit | Decode method, error handling |
| QueueUpstreamHandler | ~30% | **Integration only** | Stream logic via integration |
| QueuesChannel | ~50% | Unit | POJO field tests |
| QueuesClient | ~70% | Integration | Edge cases |
| QueuesPollRequest | ~90% | Unit | Edge cases |
| QueuesPollResponse | ~60% | Unit | Response parsing |
| QueuesStats | ~30% | Unit | POJO field tests |
| RequestSender | ~20% | N/A | Functional interface - no logic to test |
| UpstreamResponse | ~20% | Unit | POJO field tests |

**Note**: Handler classes (`QueueDownStreamProcessor`, `QueueDownstreamHandler`, `QueueUpstreamHandler`) contain gRPC stream logic - tested via integration tests only (per handler testing strategy).

**New File**: `src/test/java/io/kubemq/sdk/unit/queues/QueueSendResultTest.java`
```java
// Tests to implement:
- testDecode_success()
- testDecode_withError()
- testDecode_withDelay()
- testDecode_withExpiration()
- testIsError_trueWhenError()
- testIsError_falseWhenSuccess()
- testGetters_returnCorrectValues()
- testToString()
```

**New File**: `src/test/java/io/kubemq/sdk/unit/queues/QueueMessageReceivedTest.java`
```java
// Tests to implement:
- testDecode_withAllFields()
- testDecode_withTags()
- testDecode_withMetadata()
- testGetters_returnCorrectValues()
- testToString()
// Note: ack(), reject(), requeue() require active stream - tested via integration
```

**New File**: `src/test/java/io/kubemq/sdk/unit/queues/QueuesPollResponseTest.java`
```java
// Tests to implement:
- testBuilder_withMessages()
- testBuilder_withNoMessages()
- testBuilder_withError()
- testIsError_trueWhenError()
- testIsError_falseWhenSuccess()
- testGetMessages_returnsMessageList()
- testToString()
```

**New File**: `src/test/java/io/kubemq/sdk/unit/queues/QueuesChannelTest.java`
```java
// Tests to implement:
- testBuilder_withAllFields()
- testBuilder_withStats()
- testGetters_returnCorrectValues()
- testToString()
```

**New File**: `src/test/java/io/kubemq/sdk/unit/queues/QueuesStatsTest.java`
```java
// Tests to implement (POJO tests):
- testBuilder_withAllFields()
- testBuilder_withZeroValues()
- testGetters_returnCorrectValues()
- testToString()
```

**New File**: `src/test/java/io/kubemq/sdk/unit/queues/QueueMessageWaitingPulledTest.java`
```java
// Tests to implement:
- testBuilder_withAllFields()
- testGetters_returnCorrectValues()
- testToString()
```

**New File**: `src/test/java/io/kubemq/sdk/unit/queues/UpstreamResponseTest.java`
```java
// Tests to implement:
- testBuilder_withAllFields()
- testBuilder_withError()
- testGetters_returnCorrectValues()
- testToString()
```

**Integration Test Addition**: `QueuesIntegrationTest.java`
```java
// Add tests:
- sendMessage_withExpiration_shouldExpire()
- sendMessage_withMaxReceiveCount_shouldMoveToDLQ()
- receiveMessage_requeue_shouldReturnToQueue()
- receiveMessage_requeueWithChannel_shouldMoveToChannel()
- extendVisibility_shouldExtendTimeout()
- listQueuesChannels_shouldReturnChannelWithStats()
- ackAll_shouldAckAllMessages()
- rejectAll_shouldRejectAllMessages()
```

**Total: 48 tests**

---

## Implementation Order

### Sprint 1: High-Impact Decode/Response Classes
1. Create `QueueSendResultTest.java` - Has actual decode logic
2. Create `QueuesPollResponseTest.java` - Critical path for message receiving
3. Create `CommandResponseMessageTest.java` - Encode/decode logic
4. Create `QueryResponseMessageTest.java` - Encode/decode logic
5. Create `EventSendResultTest.java` - Response handling

**Estimated Tests**: 35 new tests
**Estimated Coverage Gain**: +8-10%

### Sprint 2: Exception + Enum Classes
1. Create `ExceptionTest.java` - All 4 exception classes
2. Create `EnumTest.java` - RequestType, SubscribeType
3. Create `EventsStoreTypeTest.java` - EventsStoreType enum

**Estimated Tests**: 20 new tests
**Estimated Coverage Gain**: +3-5%

### Sprint 3: Channel/Stats POJO Classes
1. Create `CQChannelTest.java` + `CQStatsTest.java`
2. Create `PubSubChannelTest.java` + `PubSubStatsTest.java`
3. Create `QueuesChannelTest.java` + `QueuesStatsTest.java`
4. Create `QueueMessageWaitingPulledTest.java`
5. Create `UpstreamResponseTest.java`

**Estimated Tests**: 30 new tests
**Estimated Coverage Gain**: +5-7%

### Sprint 4: Message Received Classes
1. Create `QueueMessageReceivedTest.java` - Response parsing only
2. Enhance `ChannelDecoderTest.java` - Edge cases

**Estimated Tests**: 10 new tests
**Estimated Coverage Gain**: +2-3%

### Sprint 5: Client + Integration Enhancements
1. Create `KubeMQClientTest.java`
2. Add missing integration tests to existing test files
3. Add error scenario tests
4. Add edge case tests

**Estimated Tests**: 25 new tests
**Estimated Coverage Gain**: +5-7%

---

## Test File Structure

```
src/test/java/io/kubemq/sdk/
├── integration/
│   ├── BaseIntegrationTest.java      (existing)
│   ├── CQIntegrationTest.java        (existing - enhance)
│   ├── PubSubIntegrationTest.java    (existing - enhance)
│   └── QueuesIntegrationTest.java    (existing - enhance)
└── unit/
    ├── client/
    │   └── KubeMQClientTest.java     (NEW)
    ├── common/
    │   ├── ChannelDecoderTest.java   (existing - enhance)
    │   ├── EnumTest.java             (NEW)
    │   └── ServerInfoTest.java       (NEW)
    ├── cq/
    │   ├── CQChannelTest.java        (NEW)
    │   ├── CQStatsTest.java          (NEW)
    │   ├── CommandMessageReceivedTest.java    (existing)
    │   ├── CommandMessageTest.java            (existing)
    │   ├── CommandResponseMessageTest.java    (NEW)
    │   ├── CommandsSubscriptionTest.java      (existing)
    │   ├── QueriesSubscriptionTest.java       (existing)
    │   ├── QueryMessageReceivedTest.java      (existing)
    │   ├── QueryMessageTest.java              (existing)
    │   └── QueryResponseMessageTest.java      (NEW)
    ├── exception/
    │   └── ExceptionTest.java        (NEW)
    ├── pubsub/
    │   ├── EventMessageReceivedTest.java      (existing)
    │   ├── EventMessageTest.java              (existing)
    │   ├── EventSendResultTest.java           (NEW)
    │   ├── EventStoreMessageReceivedTest.java (existing)
    │   ├── EventStoreMessageTest.java         (existing)
    │   ├── EventsStoreSubscriptionTest.java   (existing)
    │   ├── EventsStoreTypeTest.java           (NEW)
    │   ├── EventsSubscriptionTest.java        (existing)
    │   ├── PubSubChannelTest.java             (NEW)
    │   └── PubSubStatsTest.java               (NEW)
    └── queues/
        ├── QueueMessageReceivedTest.java      (NEW)
        ├── QueueMessageTest.java              (existing)
        ├── QueueMessageWaitingPulledTest.java (NEW)
        ├── QueueSendResultTest.java           (NEW)
        ├── QueuesPollRequestTest.java         (existing)
        ├── QueuesPollResponseTest.java        (NEW)
        ├── QueuesChannelTest.java             (NEW)
        ├── QueuesStatsTest.java               (NEW)
        └── UpstreamResponseTest.java          (NEW)
```

**Total New Files: 18**
**Total New Tests: ~123**

---

## Classes NOT Getting Unit Tests (By Design)

| Class | Reason |
|-------|--------|
| KubeMQUtils | All methods require gRPC client - covered by integration tests |
| EventStreamHelper | gRPC stream handling - tested via integration |
| QueueDownStreamProcessor | gRPC stream handling - tested via integration |
| QueueDownstreamHandler | gRPC stream handling - tested via integration |
| QueueUpstreamHandler | gRPC stream handling - tested via integration |
| RequestSender | Functional interface with no logic |
| CQClient | Client orchestration - tested via integration |
| PubSubClient | Client orchestration - tested via integration |
| QueuesClient | Client orchestration - tested via integration |

---

## Expected Final Coverage

| Package | Current | Target | Method |
|---------|---------|--------|--------|
| io.kubemq.sdk.exception | 0% | 100% | Unit tests only |
| io.kubemq.sdk.common | 52% | 90%+ | Unit + Integration |
| io.kubemq.sdk.client | 51% | 85%+ | Unit + Integration |
| io.kubemq.sdk.cq | 71% | 95%+ | Unit + Integration |
| io.kubemq.sdk.pubsub | 71% | 95%+ | Unit + Integration |
| io.kubemq.sdk.queues | 57% | 90%+ | Unit + Integration |
| **SDK Total** | ~60% | **90-95%** | |

**Note**: 100% coverage is not achievable due to:
- gRPC stream handling code (requires live server)
- Some defensive null checks that can't be triggered
- Lombok-generated code not directly testable

---

## Exclusions from Coverage Requirements

Some code paths are excluded from coverage requirements:
1. **Generated gRPC stubs** - Already excluded (kubemq package)
2. **Lombok-generated code** - Getters/setters/builders are not directly testable
3. **gRPC stream handlers** - Complex mocking provides little value vs integration tests
4. **Unreachable defensive code** - Some null checks may be defensive only

---

## Running the Tests

> **Note**: Building/testing requires Java 21+ due to Lombok compatibility, but the SDK targets Java 8 runtime (see `pom.xml`).

```bash
# Run all tests with coverage
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  mvn clean test jacoco:report -Dtest="**/*Test"

# View coverage report
open target/site/jacoco/index.html

# Run only unit tests
mvn test

# Run only integration tests (requires running KubeMQ server)
mvn test -Dtest="**/*IntegrationTest"
```

---

## Prerequisites

1. **KubeMQ Server**: Integration tests require a running KubeMQ server at `localhost:50000`
2. **Java 21**: Required for building and running tests
3. **Maven 3.9+**: For build and test execution

---

## Success Criteria

- [ ] All SDK packages have 90%+ instruction coverage
- [ ] All SDK packages have 85%+ branch coverage
- [ ] All ~370 tests pass consistently
- [ ] Integration tests complete in under 5 minutes
- [ ] Unit tests complete in under 30 seconds
- [ ] No flaky tests
- [ ] Coverage report generated automatically on each build
- [ ] All new test files follow naming convention (*Test.java)
- [ ] No mocking of gRPC client classes in unit tests (integration tests cover those paths)

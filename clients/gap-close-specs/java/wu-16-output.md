# WU-16 Output: Spec 09 API Design Phase 2 — Verb Alignment

**Status:** COMPLETED
**Build:** PASS
**Tests:** 1254 passed, 0 failed (was 1224, +30 new)

## REQs Completed

- [x] REQ-DX-3: Consistent verbs across SDKs (verb-aligned alias methods)

## Changes Made

### PubSubClient.java
- Added `publishEvent(EventMessage message)` — verb-aligned alias delegating to `sendEventsMessage(EventMessage)`
- Added `publishEventStore(EventStoreMessage message)` — verb-aligned alias delegating to `sendEventsStoreMessage(EventStoreMessage)`
- Added `@Deprecated(since = "2.2.0", forRemoval = true)` to `sendEventsMessage(EventMessage)`
- Added `@Deprecated(since = "2.2.0", forRemoval = true)` to `sendEventsStoreMessage(EventStoreMessage)`
- Added `@SuppressWarnings("deprecation")` to internal callers of deprecated methods (convenience methods, aliases)

### QueuesClient.java
- Added `sendQueueMessage(QueueMessage)` — verb-aligned alias delegating to `sendQueuesMessage(QueueMessage)`
- Added `receiveQueueMessages(QueuesPollRequest)` — verb-aligned alias delegating to `receiveQueuesMessages(QueuesPollRequest)`
- Added `@Deprecated(since = "2.2.0", forRemoval = true)` to `sendQueuesMessage(QueueMessage)`
- Added `@Deprecated(since = "2.2.0", forRemoval = true)` to `receiveQueuesMessages(QueuesPollRequest)`
- Added `@SuppressWarnings("deprecation")` to internal callers of deprecated methods

### CQClient.java
- Added `sendCommand(CommandMessage)` — verb-aligned alias delegating to `sendCommandRequest(CommandMessage)`
- Added `sendQuery(QueryMessage)` — verb-aligned alias delegating to `sendQueryRequest(QueryMessage)`
- Added `@Deprecated(since = "2.2.0", forRemoval = true)` to `sendCommandRequest(CommandMessage)`
- Added `@Deprecated(since = "2.2.0", forRemoval = true)` to `sendQueryRequest(QueryMessage)`
- Added `@SuppressWarnings("deprecation")` to internal callers of deprecated methods (convenience methods, aliases)

### New Test File
- `VerbAlignmentTest.java` (30 tests) — reflection-based tests verifying:
  - All 6 verb-aligned alias methods exist with correct return types
  - All 6 deprecated methods have `@Deprecated(since = "2.2.0", forRemoval = true)`
  - Alias methods are NOT deprecated
  - Already-aligned methods (subscribeToEvents, subscribeToEventsStore, subscribeToCommands, subscribeToQueries) remain non-deprecated
  - Convenience methods (publishEvent(String, byte[]), etc.) still exist

## Verb Mapping Summary

| GS Verb | New Method (v2.2+) | Deprecated Method | Removal |
|---------|--------------------|--------------------|---------|
| `publishEvent` | `publishEvent(EventMessage)` | `sendEventsMessage(EventMessage)` | v3.0 |
| `publishEventStore` | `publishEventStore(EventStoreMessage)` | `sendEventsStoreMessage(EventStoreMessage)` | v3.0 |
| `sendQueueMessage` | `sendQueueMessage(QueueMessage)` | `sendQueuesMessage(QueueMessage)` | v3.0 |
| `receiveQueueMessages` | `receiveQueueMessages(QueuesPollRequest)` | `receiveQueuesMessages(QueuesPollRequest)` | v3.0 |
| `sendCommand` | `sendCommand(CommandMessage)` | `sendCommandRequest(CommandMessage)` | v3.0 |
| `sendQuery` | `sendQuery(QueryMessage)` | `sendQueryRequest(QueryMessage)` | v3.0 |

## Already-Aligned (No Change)

| Method | Class |
|--------|-------|
| `subscribeToEvents(EventsSubscription)` | PubSubClient |
| `subscribeToEventsStore(EventsStoreSubscription)` | PubSubClient |
| `subscribeToCommands(CommandsSubscription)` | CQClient |
| `subscribeToQueries(QueriesSubscription)` | CQClient |
| `ack()` | QueueMessageReceived |
| `reject()` | QueueMessageReceived |

## Design Decisions

1. **Alias-then-deprecate pattern:** New alias methods delegate to old methods (which retain the implementation). This allows old methods to be removed in v3.0 by moving the implementation to the alias.
2. **@SuppressWarnings("deprecation"):** Added to alias methods and convenience methods that internally call deprecated methods to keep the build warning-free.
3. **Scope limited to main verb methods:** Async variants (`sendEventsMessageAsync`), timeout overloads (`sendCommandRequest(CommandMessage, Duration)`), and batch methods (`sendQueuesMessages(List)`) were NOT deprecated per spec scope. Only the primary single-message methods were verb-aligned.
4. **No breaking changes:** All existing methods continue to work. New aliases are purely additive.

## Files Modified
- `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/PubSubClient.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueuesClient.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/cq/CQClient.java`

## Files Created
- `kubemq-java/src/test/java/io/kubemq/sdk/unit/apicompleteness/VerbAlignmentTest.java`

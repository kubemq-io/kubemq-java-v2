# WU-7 Output: Spec 08 (API Completeness)

**Status:** COMPLETED
**Build:** PASS
**Tests:** 1167 passed, 0 failed (was 1115 at WU-5)
**New tests added:** 17

---

## REQ-API-1: Core Feature Coverage — DONE

### Gap A: Subscribe Return Types

Changed 4 subscribe methods from `void` to returning the subscription object passed in:

| Method | Class | Before | After |
|--------|-------|--------|-------|
| `subscribeToEvents()` | `PubSubClient` | `void` | `EventsSubscription` |
| `subscribeToEventsStore()` | `PubSubClient` | `void` | `EventsStoreSubscription` |
| `subscribeToCommands()` | `CQClient` | `void` | `CommandsSubscription` |
| `subscribeToQueries()` | `CQClient` | `void` | `QueriesSubscription` |

**Breaking change:** Binary-incompatible (method descriptor changes from `()V` to return type). Source-compatible for all callers that ignore the return value (all existing usage patterns). Recompilation required when upgrading.

### Gap B: Batch Queue Send

Added `sendQueuesMessages(List<QueueMessage>)` to `QueuesClient` and `QueueUpstreamHandler`:

- **QueuesClient.sendQueuesMessages()**: Validates all messages before sending (fail-fast), delegates to upstream handler
- **QueueUpstreamHandler.sendQueuesMessages()**: Builds single `QueuesUpstreamRequest` with all messages, uses `pendingBatchResponses` map for response routing
- **Response handling**: Updated `onNext` to route responses to either single or batch futures based on request ID lookup
- **Error handling**: Updated `closeStreamWithError()` and cleanup task to handle batch futures

**Files modified:**
- `pubsub/PubSubClient.java` — return types changed
- `cq/CQClient.java` — return types changed
- `queues/QueuesClient.java` — added `sendQueuesMessages()`, `purgeQueue()`
- `queues/QueueUpstreamHandler.java` — added batch send support, `pendingBatchResponses` map, updated `onNext`/`closeStreamWithError`/cleanup

---

## REQ-API-2: Feature Matrix Document — DONE

Created `clients/feature-matrix.md` with:
- All feature categories (Client Management, Events, Events Store, Queues Stream, Queues Simple, RPC Commands, RPC Queries)
- Core/Extended tier classification
- Java SDK status column (✅ for implemented, ❌ (NI) for purgeQueue)
- Legend explaining status indicators
- Release update reminder in header

---

## REQ-API-3: No Silent Feature Gaps — DONE

### ErrorCode Extension
Added `FEATURE_NOT_IMPLEMENTED` to `ErrorCode` enum (now 27 codes). Updated `ErrorCodeTest.totalCodeCount` from 26 to 27.

### NotImplementedException
Created `io.kubemq.sdk.exception.NotImplementedException`:
- Extends `KubeMQException` (from WU-1 error hierarchy)
- Uses builder pattern matching other exception subclasses
- Defaults: `code=FEATURE_NOT_IMPLEMENTED`, `category=FATAL`, `retryable=false`

### purgeQueue Stub
Added `QueuesClient.purgeQueue(String channel)`:
- Throws `NotImplementedException` with operation="purgeQueue", channel set, message referencing AckAllQueueMessages and feature-matrix.md
- Documented as intentional gap, not a bug

---

## Files Created/Modified

| File | Change |
|------|--------|
| `pubsub/PubSubClient.java` | MODIFIED — subscribe return types |
| `cq/CQClient.java` | MODIFIED — subscribe return types |
| `queues/QueuesClient.java` | MODIFIED — batch send + purgeQueue stub |
| `queues/QueueUpstreamHandler.java` | MODIFIED — batch response handling |
| `exception/ErrorCode.java` | MODIFIED — added FEATURE_NOT_IMPLEMENTED |
| `exception/NotImplementedException.java` | NEW |
| `clients/feature-matrix.md` | NEW |
| `unit/exception/ErrorCodeTest.java` | MODIFIED — count 26→27 |
| `unit/apicompleteness/SubscribeReturnTypeTest.java` | NEW — 4 tests |
| `unit/apicompleteness/BatchQueueSendTest.java` | NEW — 4 tests |
| `unit/apicompleteness/BatchUpstreamHandlerTest.java` | NEW — 3 tests |
| `unit/apicompleteness/NotImplementedExceptionTest.java` | NEW — 6 tests |

## Blocked Items
None.

## Notes
- No scope creep — only spec-mandated changes made
- All existing tests continue to pass (return type change is source-compatible)
- Feature matrix covers Java SDK only; other SDK columns left empty for future assessment

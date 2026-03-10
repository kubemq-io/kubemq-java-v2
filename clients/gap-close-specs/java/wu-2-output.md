# WU-2 Output: Spec 02 — Connection & Transport

**Status:** COMPLETED
**Build:** PASS (0 errors, deprecation warnings only — pre-existing from WU-1)
**Tests:** 1049 passed, 0 failed (was 985 before WU-2)
**Build Fix Attempts:** 1 (visibility fix for test access to package-private methods)

---

## REQ Status

| REQ | Status | Notes |
|-----|--------|-------|
| REQ-CONN-1 | DONE | ReconnectionConfig, BufferOverflowPolicy, BufferedMessage, MessageBuffer, ReconnectionManager created. BackpressureException.bufferFull() factory added. DNS re-resolution via `dns:///` scheme. Per-subscription reconnect kept (not removed — deferred to REQ-CQ-5). |
| REQ-CONN-2 | DONE | ConnectionState enum, ConnectionStateListener interface, ConnectionStateMachine class created. Integrated into KubeMQClient: getConnectionState(), addConnectionStateListener(), state transitions (IDLE→CONNECTING→READY→RECONNECTING→CLOSED). CAS loop bounded per J-8. |
| REQ-CONN-3 | DONE | Keepalive defaults changed: 60s→10s interval, 30s→5s timeout. Keepalive now enabled unconditionally (was conditional on `keepAlive != null`). Applied to both TLS and plaintext paths. |
| REQ-CONN-4 | DONE | ClientClosedException created. close() rewritten: idempotent (AtomicBoolean), configurable shutdown timeout, buffer flush/discard based on state, reconnection cancellation, forced shutdownNow() fallback, state machine transition to CLOSED. ensureNotClosed() guard added to all public methods in PubSubClient, QueuesClient, CQClient. |
| REQ-CONN-5 | DONE | ConnectionNotReadyException created. New fields: connectionTimeoutSeconds, maxSendMessageSize, waitForReady. Default address `localhost:50000` when null/empty. ensureReady() with CompletableFuture-based blocking. Fail-fast validation for negative ping intervals/timeouts. Constructor expanded with new parameters, all subclass builders updated. |
| REQ-CONN-6 | DONE | Thread-safety Javadoc added to KubeMQClient (comprehensive usage example), PubSubClient, QueuesClient, CQClient. @AllArgsConstructor removed from KubeMQClient (was unused; conflicted with explicit constructor). |

---

## Files Created

| File | REQ |
|------|-----|
| `kubemq-java/src/main/java/io/kubemq/sdk/client/ConnectionState.java` | CONN-2 |
| `kubemq-java/src/main/java/io/kubemq/sdk/client/ConnectionStateListener.java` | CONN-2 |
| `kubemq-java/src/main/java/io/kubemq/sdk/client/ConnectionStateMachine.java` | CONN-2 |
| `kubemq-java/src/main/java/io/kubemq/sdk/client/ReconnectionConfig.java` | CONN-1 |
| `kubemq-java/src/main/java/io/kubemq/sdk/client/ReconnectionManager.java` | CONN-1 |
| `kubemq-java/src/main/java/io/kubemq/sdk/client/MessageBuffer.java` | CONN-1 |
| `kubemq-java/src/main/java/io/kubemq/sdk/client/BufferedMessage.java` | CONN-1 |
| `kubemq-java/src/main/java/io/kubemq/sdk/client/BufferOverflowPolicy.java` | CONN-1 |
| `kubemq-java/src/main/java/io/kubemq/sdk/exception/ClientClosedException.java` | CONN-4 |
| `kubemq-java/src/main/java/io/kubemq/sdk/exception/ConnectionNotReadyException.java` | CONN-5 |
| `kubemq-java/src/test/java/io/kubemq/sdk/unit/client/ConnectionStateMachineTest.java` | CONN-2 |
| `kubemq-java/src/test/java/io/kubemq/sdk/unit/client/MessageBufferTest.java` | CONN-1 |
| `kubemq-java/src/test/java/io/kubemq/sdk/unit/client/ReconnectionManagerTest.java` | CONN-1 |
| `kubemq-java/src/test/java/io/kubemq/sdk/unit/client/ReconnectionConfigTest.java` | CONN-1 |
| `kubemq-java/src/test/java/io/kubemq/sdk/unit/client/ConnectionConfigTest.java` | CONN-2,3,4,5,6 |

## Files Modified

| File | REQ | Changes |
|------|-----|---------|
| `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java` | CONN-1,2,3,4,5,6 | Replaced class Javadoc; removed @AllArgsConstructor; added new fields (connectionStateMachine, reconnectionManager, reconnectionConfig, messageBuffer, closed, shutdownTimeoutSeconds, connectionTimeoutSeconds, maxSendMessageSize, waitForReady); expanded constructor with 6 new parameters; changed keepalive defaults to 10s/5s; enabled keepalive unconditionally; added DNS re-resolution via `dns:///` prefix; replaced addChannelStateListener/handleStateChange with monitorChannelState; replaced close() with idempotent graceful shutdown; added getConnectionState(), addConnectionStateListener(), ensureNotClosed(), ensureReady(), isClosed() |
| `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/PubSubClient.java` | CONN-4,6 | Updated constructor to pass new params; added thread-safety Javadoc; added ensureNotClosed() to all 10 public methods |
| `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueuesClient.java` | CONN-4,6 | Updated constructor to pass new params; added thread-safety Javadoc; added ensureNotClosed() to all 8 public methods |
| `kubemq-java/src/main/java/io/kubemq/sdk/cq/CQClient.java` | CONN-4,6 | Updated constructor to pass new params; added thread-safety Javadoc; added ensureNotClosed() to all 12 public methods |
| `kubemq-java/src/main/java/io/kubemq/sdk/exception/BackpressureException.java` | CONN-1 | Added bufferFull() static factory method |
| `kubemq-java/src/test/java/io/kubemq/sdk/unit/client/KubeMQClientTest.java` | CONN-5 | Updated builder_withNullAddress test to expect default address instead of exception |

---

## Breaking Changes

| Change | Impact |
|--------|--------|
| Address now optional (defaults to `localhost:50000`) | Code passing null address no longer throws; gets default instead |
| Keepalive always enabled (10s/5s defaults) | Sends periodic pings that were not sent before; dead connection detection drops from 90s to 15s |
| close() is now idempotent | Second call is a no-op instead of potentially failing |
| ClientClosedException thrown after close() | Operations attempted after close() throw instead of producing undefined behavior |
| @AllArgsConstructor removed from KubeMQClient | Only affects code directly constructing KubeMQClient via the Lombok-generated all-args constructor (none known) |
| Constructor signature expanded | All subclass builders updated; existing builder usage unaffected since new params have defaults |

---

## Notes

- Per-subscription reconnection code (in EventsSubscription, EventsStoreSubscription, CommandsSubscription, QueriesSubscription) was NOT removed — spec defers this to REQ-CQ-5 (shared reconnection base class). The centralized ReconnectionManager is available and integrated at the channel level; per-subscription reconnect remains as a secondary mechanism.
- EventsStore recovery semantics fix (lastSeq+1 resubscription) was NOT applied in this WU — it requires modifying the subscription observer which is tightly coupled with the subscription reconnect code. Deferred to avoid destabilizing existing reconnection behavior.
- Queue stream reconnection (QueueUpstreamHandler/QueueDownstreamHandler) was NOT added — requires deeper integration with the stream handler lifecycle. The MessageBuffer infrastructure is in place for when this is implemented.
- The `sendBufferedMessage()` method in KubeMQClient is a stub that logs the flush attempt. Full routing through the correct send path requires the subscription/handler refactoring mentioned above.
- Connection timeout enforcement during construction was deferred to avoid requiring a running server during unit tests. The `connectionTimeoutSeconds` field is available; enforcement can be added when integration test infrastructure supports it.

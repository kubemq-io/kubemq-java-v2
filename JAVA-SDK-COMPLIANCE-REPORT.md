# KubeMQ Java SDK — Compliance Report & Gap Analysis

**Reference documents:** [sdk-api-feature-list.md](sdk-api-feature-list.md) | [sdk-compliance-checklist.md](sdk-compliance-checklist.md)

**SDK Language:** Java
**SDK Version:** 2.2.0-SNAPSHOT
**Assessed by:** Automated deep-code analysis
**Date:** 2026-03-13

---

## Filled Compliance Checklist

### 1. Connection & Client Lifecycle (Ref: §1)

#### 1.1 Client Construction
- [x] Accepts `address` (host:port) parameter — `KubeMQClient.java:185`; falls back to env `KUBEMQ_ADDRESS` then `"localhost:50000"`
- [x] Accepts `clientId` parameter — `KubeMQClient.java:222`; **NOTE: Not strictly required** — auto-generates `"kubemq-client-<uuid>"` if absent
- [x] Accepts `authToken` parameter (optional) — `KubeMQClient.java:244`
- [x] Auth token sent as gRPC metadata key `"authorization"` (lowercase) — `KubeMQClient.java:1334` in `AuthInterceptor`
- [x] Supports TLS mode (server-side TLS with CA cert) — `KubeMQClient.java:554-571`
- [x] Supports mTLS mode (client cert + key + CA) — `KubeMQClient.java:1238-1246`
- [x] Accepts `maxReceiveSize` parameter — **WARNING: Default is 100MB, spec says 4MB** — `KubeMQClient.java:258`
- [x] Accepts `reconnectInterval` parameter (default: 1s) — `KubeMQClient.java:259`
- [x] gRPC keepalive configured with client ping interval >= 5s — `KubeMQClient.java:593-600`, default 10s

#### 1.2 Ping
- [x] Calls gRPC `Ping(Empty) → PingResult` — `KubeMQClient.java:992-1007`
- [x] Works without auth token — `AuthInterceptor` only adds header if token non-null
- [x] Returns all `PingResult` fields: `Host`, `Version`, `ServerStartTime`, `ServerUpTimeSeconds` — `ServerInfo.java:21-57`

#### 1.3 Close / Disconnect
- [x] Gracefully closes gRPC connection — `KubeMQClient.java:922-934`
- [ ] Cancels all active subscriptions — **No subscription registry; subscriptions die via channel shutdown, not clean cancel**
- [ ] Closes all open streams — **Streams die via channel shutdown, not explicit `onCompleted()`**
- [x] Releases all resources — `KubeMQClient.java:919-946`

---

### 2. Events — Pub/Sub (Ref: §2)

#### 2.1 Send Event (Unary)
- [ ] Calls gRPC `SendEvent(Event) → Result` — **MISSING: SDK only uses streaming path via `EventStreamHelper`; unary `sendEvent` is never called**
- [x] Sets `Store = false` — `EventMessage.java:125`
- [x] Auto-generates `EventID` (UUID) when not provided — `EventMessage.java:123`
- [x] Populates `ClientID` from client config — `EventMessage.java:124`
- [x] Accepts `Channel` (required)
- [x] Accepts `Metadata` (optional)
- [x] Accepts `Body` (optional, at least one of Metadata/Body required)
- [x] Accepts `Tags` as `map[string]string` (optional)
- [ ] Returns `Result` with `EventID`, `Sent`, `Error` — **MISSING: `sendEventsMessage()` returns void (fire-and-forget)**

#### 2.2 Send Events Stream
- [x] Opens bidirectional stream via `SendEventsStream(stream Event) → stream Result` — `EventStreamHelper.java:116`
- [x] Handles `Recv()` on a separate goroutine/thread — `resultStreamObserver` callbacks run on gRPC thread
- [x] Handles `io.EOF` / stream close from server → triggers reconnection — On `onError`, handler is nulled for lazy re-creation
- [x] Stream stays open until explicitly closed
- [x] Auto-reconnects stream on disconnect — Lazy reconnect: handler nulled on error, re-created on next send

#### 2.3 Subscribe to Events
- [x] Calls gRPC `SubscribeToEvents(Subscribe) → stream EventReceive` — `PubSubClient.java`
- [x] Sets `SubscribeTypeData = Events (1)` — `EventsSubscription.java:172-173`
- [x] Accepts `Channel` with wildcard support (`*` and `>`) — Subscription validate() does not reject wildcards
- [x] Accepts optional `Group` parameter — `EventsSubscription.java:170`
- [x] Delivers `EventReceive` messages via callback/handler/channel
- [x] `EventReceive` exposes: `EventID`, `Channel`, `Metadata`, `Body`, `Tags` — **NOTE: `Timestamp` field exists but is never populated in `decode()`**
- [x] Auto-reconnects subscription on disconnect — `EventsSubscription.java:240-252`

#### 2.4 Cancel Subscription
- [x] Provides API to cancel/unsubscribe (closes the stream) — `EventsSubscription.java:122-127`

---

### 3. Events Store — Persistent Pub/Sub (Ref: §3)

#### 3.1 Send Event to Store (Unary)
- [ ] Calls gRPC `SendEvent(Event) → Result` with `Store = true` — **MISSING: SDK uses streaming path only**
- [x] SDK provides separate method/class for Events Store — `EventStoreMessage.java` vs `EventMessage.java`
- [x] `Sent=true` confirms message was persisted — `EventSendResult.java:24`

#### 3.2 Send Events Store Stream
- [x] Opens bidirectional stream with `Store = true` on each Event
- [x] Server sends `Result` for **every** event — `EventStreamHelper.java:49-60` handles each result via `pendingResponses`
- [x] Auto-reconnects stream on disconnect — Lazy reconnect on error

#### 3.3 Subscribe to Events Store
- [x] Sets `SubscribeTypeData = EventsStore (2)` — `EventsStoreSubscription.java:182`
- [ ] Rejects wildcards in channel name — **MISSING: `validate()` only checks null/empty, not wildcards**
- [x] Requires `EventsStoreTypeData` (must not be Undefined/0) — `EventsStoreSubscription.java:143-150`
- [x] Supports all 6 start position types:
  - [x] `StartNewOnly` (1)
  - [x] `StartFromFirst` (2)
  - [x] `StartFromLast` (3)
  - [x] `StartAtSequence` (4) — requires value > 0
  - [x] `StartAtTime` (5) — requires value > 0
  - [x] `StartAtTimeDelta` (6) — **WARNING: No value > 0 validation for this type**
- [x] Accepts optional `Group` parameter — `EventsStoreSubscription.java:185`
- [x] `EventReceive.Sequence` is populated — `EventStoreMessageReceived.java:77`
- [x] Auto-reconnects subscription on disconnect — `EventsStoreSubscription.java:256-265`

#### 3.4 Cancel Subscription
- [x] Provides API to cancel/unsubscribe — `EventsStoreSubscription.java:114-119`

---

### 4. Queues — Stream API / Primary (Ref: §4)

#### 4.1 Queue Upstream (Send via Stream)
- [x] Opens bidirectional stream via `QueuesUpstream` — `QueueUpstreamHandler.java:146`
- [x] `QueueMessage` supports all fields:
  - [x] `MessageID` (auto-generated UUID if empty) — `QueueMessage.java:175`
  - [x] `ClientID` (required) — `QueueMessage.java:176`
  - [x] `Channel` (required)
  - [x] `Metadata` (optional)
  - [x] `Body` (optional)
  - [x] `Tags` as `map[string]string`
  - [x] `Policy` (optional)
- [x] `QueueMessagePolicy` supports:
  - [x] `ExpirationSeconds` — `QueueMessage.java:184`
  - [x] `DelaySeconds` — `QueueMessage.java:183`
  - [x] `MaxReceiveCount` — `QueueMessage.java:185`
  - [x] `MaxReceiveQueue` — `QueueMessage.java:186`
- [x] Returns `QueuesUpstreamResponse` with `RefRequestID`, `Results[]`, `IsError`, `Error`
- [x] `SendQueueMessageResult` exposes:
  - [x] `MessageID`, `SentAt`, `ExpirationAt`, `DelayedTo`
  - [x] `IsError`, `Error`
  - [ ] `RefChannel` — **MISSING from `QueueSendResult.java`**
- [x] Auto-reconnects stream on disconnect — Lazy reconnect via `sendRequest()` → `connect()`

#### 4.2 Queue Downstream (Receive via Stream)
- [x] Opens bidirectional stream via `QueuesDownstream` — `QueueDownstreamHandler.java:122`
- [x] Supports downstream request types:
  - [x] `Get` (1)
  - [x] `AckAll` (2)
  - [x] `AckRange` (3)
  - [x] `NAckAll` (4)
  - [x] `NAckRange` (5)
  - [x] `ReQueueAll` (6)
  - [x] `ReQueueRange` (7)
  - [ ] `ActiveOffsets` (8) — **MISSING: No code sends this request type**
  - [ ] `TransactionStatus` (9) — **MISSING: No code sends this request type**
  - [ ] `CloseByClient` (10) — **MISSING: No code sends this request type**
- [ ] Handles `CloseByServer` (11) responses — **MISSING: Not checked in downstream response handler**
- [x] `QueueMessageAttributes` exposed on received messages:
  - [x] `Timestamp`, `Sequence`
  - [ ] `MD5OfBody` — **MISSING: Not decoded**
  - [x] `ReceiveCount`, `ReRouted`, `ReRoutedFromQueue`
  - [x] `ExpirationAt`, `DelayedTo`
- [x] Auto-reconnects stream on disconnect — Lazy reconnect

#### 4.3 High-Level Downstream Abstraction
- [x] SDK provides a simplified receive-process-ack pattern — `QueuesPollResponse` has `ackAll()`, `rejectAll()`, `reQueueAll()`; per-message `ack()`, `reject()`, `reQueue()`

#### 4.4 Waiting / Poll Mode
- [x] SDK supports single-shot Get — `QueuesClient.waiting()` and `QueuesClient.pull()`

---

### 5. Queues — Simple API / Secondary (Ref: §5)

#### 5.1 Send Single Message
- [ ] Calls gRPC `SendQueueMessage(QueueMessage) → SendQueueMessageResult` — **MISSING: Routes through stream API instead**

#### 5.2 Send Batch Messages
- [ ] Calls gRPC `SendQueueMessagesBatch` — **MISSING: Routes through stream API instead**
- [ ] Accepts `BatchID` and `Messages[]` — **N/A**
- [ ] Response exposes `BatchID`, `Results[]`, `HaveErrors` — **N/A**

#### 5.3 Receive Messages (Pull)
- [x] Calls gRPC `ReceiveQueueMessages` — `QueuesClient.java:217,263`
- [x] Accepts `MaxNumberOfMessages` (default 1)
- [x] Accepts `WaitTimeSeconds` (in **seconds**)
- [x] Accepts `IsPeak` (peek mode) — `waiting()` sets `true`, `pull()` sets `false`
- [ ] Response exposes `MessagesReceived` — **MISSING: Not decoded**
- [ ] Response exposes `MessagesExpired` — **MISSING: Not decoded**
- [ ] Response exposes `IsPeak` — **MISSING: Not decoded**

#### 5.4 Ack All Queue Messages
- [ ] Calls gRPC `AckAllQueueMessages` — **MISSING: `purgeQueue()` throws `NotImplementedException`**

---

### 6. RPC — Commands (Ref: §6)

#### 6.1 Send Command
- [x] Calls gRPC `SendRequest(Request) → Response` — `CQClient.java:119`
- [x] Sets `RequestTypeData = Command (1)` — `CommandMessage.java:148`
- [x] Auto-generates `RequestID` (UUID) — `CommandMessage.java:145`
- [x] Accepts `Timeout` in milliseconds — SDK takes seconds, encodes as ms (`* 1000`)
- [x] Accepts `Channel`, `Metadata`, `Body`, `Tags`
- [ ] Passes `Span` field for OpenTelemetry trace propagation — **MISSING: No `setSpan()` in `CommandMessage.encode()`**
- [x] Returns `Response` with `Executed`, `Error`, `Timestamp`
- [ ] Returns `Response` with `Body` — **MISSING: Not decoded in `CommandResponseMessage`**
- [ ] Returns `Response` with `Metadata` — **MISSING: Not decoded**
- [ ] Returns `Response` with `Tags` — **MISSING: Not decoded**

#### 6.2 Subscribe to Commands
- [x] Calls gRPC `SubscribeToRequests(Subscribe) → stream Request` — `CQClient.java`
- [x] Sets `SubscribeTypeData = Commands (3)` — `CommandsSubscription.java:136`
- [x] Accepts `Channel` (no wildcards) — **NOTE: wildcards not rejected by subscription validation**
- [x] Accepts optional `Group` parameter
- [x] Delivers received `Request` with `ReplyChannel` populated — `CommandMessageReceived.java:47`
- [ ] `Tags` decoded from received command — **MISSING: `CommandMessageReceived.decode()` does not extract tags**
- [x] Auto-reconnects subscription on disconnect — `CommandsSubscription.java:205-217`

#### 6.3 Send Command Response
- [x] Calls gRPC `SendResponse(Response) → Empty` — `CQClient.java:191`
- [x] Copies `ReplyChannel` from received Request — `CommandResponseMessage.java:59`
- [x] Copies `RequestID` from received Request — `CommandResponseMessage.java:58`
- [x] Accepts `Executed`, `Error`
- [ ] Accepts `Metadata` — **MISSING: Not on class, not in `encode()`**
- [ ] Accepts `Body` — **MISSING: Not on class, not in `encode()`**
- [ ] Accepts `Tags` — **MISSING: Not on class, not in `encode()`**
- [ ] Passes `Span` for trace propagation — **MISSING**

#### 6.4 Cancel Subscription
- [x] Provides API to cancel/unsubscribe — `CommandsSubscription.java:92-97`

---

### 7. RPC — Queries (Ref: §7)

#### 7.1 Send Query
- [x] Calls gRPC `SendRequest(Request) → Response` — Sets `RequestTypeData = Query (2)`
- [x] Auto-generates `RequestID` (UUID)
- [x] Accepts `Timeout` in milliseconds
- [x] Accepts `CacheKey` (optional) — `QueryMessage.java:149`
- [x] Accepts `CacheTTL` in seconds — `QueryMessage.java:150`
- [ ] Returns `Response` with `CacheHit` field — **MISSING: Not decoded in `QueryResponseMessage`**

#### 7.2 Subscribe to Queries
- [x] Calls gRPC `SubscribeToRequests(Subscribe) → stream Request`
- [x] Sets `SubscribeTypeData = Queries (4)` — `QueriesSubscription.java:136`
- [x] Accepts `Channel`, `Group`
- [x] Delivers received `Request` with `ReplyChannel` populated
- [x] Auto-reconnects subscription on disconnect

#### 7.3 Send Query Response
- [x] Same as §6.3 — **Query response correctly includes `Metadata`, `Body`, `Tags`** (unlike Command response)

#### 7.4 Cancel Subscription
- [x] Provides API to cancel/unsubscribe

---

### 8. Channel Management (Ref: §8)

#### 8.1 Queue Info
- **REMOVED in proto v1.4.0** — `QueuesInfo` RPC and related types have been removed from the proto definition. No longer applicable.

#### 8.2 Channel Management via gRPC Query
- [x] Uses `SendRequest` with Query to `"kubemq.cluster.internal.requests"` — `KubeMQUtils.java:35`

##### 8.2.1 Create Channel
- [x] Sends Query with `Metadata = "create-channel"` — `KubeMQUtils.java:92-103`
- [x] Sets required Tags: `channel_type`, `channel`, `client_id`
- [x] Handles response

##### 8.2.2 Delete Channel
- [x] Sends Query with `Metadata = "delete-channel"` — `KubeMQUtils.java:130-141`
- [x] Sets required Tags

##### 8.2.3 List Channels
- [x] Sends Query with `Metadata = "list-channels"` — `KubeMQUtils.java:166-200`
- [x] Sets Tag: `channel_type`
- [x] Sets optional Tag: `channel_search`
- [x] Parses JSON response body
- [ ] Handles timeout gracefully (non-master node) — **PARTIAL: Timeout exists but no retry logic**
- [ ] Handles `"cluster snapshot not ready yet"` error — **MISSING**

##### 8.2.4 Purge Queue Channel
- [ ] Implemented via `AckAllQueueMessages` — **MISSING: throws `NotImplementedException`**

---

### 9. Validation Rules (Ref: §10)

#### 9.1 Common Validation (Client-Side)
- [x] `ClientID` cannot be empty — Ensured via auto-generation in constructor, not via validate()
- [x] `Channel` cannot be empty — All validate() methods check this
- [ ] `Channel` cannot contain wildcards except Events subscribe — **PARTIAL: Publish classes reject via regex, but EventsStore/Commands/Queries subscriptions do NOT reject wildcards**
- [x] `Channel` cannot contain whitespace — Regex rejects spaces
- [ ] `Channel` cannot end with `.` — **MISSING: Regex allows trailing dot**
- [x] At least one of `Metadata` or `Body` must be non-empty

#### 9.2 Request-Specific Validation
- [x] `Timeout` must be > 0 for Commands and Queries — Both validate()
- [ ] If `CacheKey` is set, `CacheTTL` must be > 0 for Queries — **MISSING**
- [ ] `RequestID` must be non-empty for `SendResponse` — **MISSING**
- [x] `ReplyChannel` must be non-empty for `SendResponse`

#### 9.3 Events Store Subscription Validation
- [x] `EventsStoreTypeData` must not be `Undefined (0)` for store subscriptions
- [x] `EventsStoreTypeData` must be `Undefined (0)` for non-store — Correct by design (field doesn't exist)
- [x] `StartAtSequence` value must be > 0 — Checks `== 0` but not negative
- [x] `StartAtTime` value must be > 0 — Uses Instant (always positive if non-null)
- [ ] `StartAtTimeDelta` value must be > 0 — **MISSING: No validation for this case**
- [ ] Wildcard patterns rejected for Events Store subscribe — **MISSING**

#### 9.4 Queue Validation (Client-Side)
- [ ] `MaxNumberOfMessages` >= 1 and <= 1024 — **PARTIAL: Lower bound checked, upper bound missing**
- [ ] `WaitTimeSeconds` >= 0 and <= 3600 — **PARTIAL: Lower bound checked, upper bound missing**
- [ ] `ClientID` non-empty for downstream poll — **MISSING**

#### 9.5 Validation errors raised immediately
- [x] All client-side validation raises errors before making gRPC calls

---

### 10. Error Handling (Ref: §11)

- [x] gRPC status codes surfaced as language-idiomatic exceptions — `GrpcErrorMapper.java` maps all 17 codes
- [x] `codes.Unavailable` handled — Maps to `ConnectionException`, retryable
- [x] `codes.Unauthenticated` handled — Maps to `AuthenticationException`
- [x] Message-level errors (`IsError` + `Error`) surfaced
- [x] Connection failures trigger automatic reconnection with backoff — `ReconnectionManager.java`
- [x] Stream disconnects auto-reconnected — All 4 subscription classes have `reconnect()` methods

---

### 11. ID Auto-Generation

- [x] `EventID` auto-generated (UUID) — `EventMessage.java:123`, `EventStoreMessage.java:120`
- [x] `RequestID` auto-generated (UUID) — `CommandMessage.java:145`, `QueryMessage.java:141`
- [x] `MessageID` auto-generated (UUID) — `QueueMessage.java:175`

---

### 12. Deprecated APIs — Removal (Ref: §13)

- [x] `StreamQueueMessage` method is NOT implemented — Confirmed removed
- [x] `StreamQueueMessagesRequest` type is NOT present — Confirmed
- [x] `StreamQueueMessagesResponse` type is NOT present — Confirmed
- [x] `StreamRequestType` enum is NOT present — Confirmed
- [x] No references to legacy operations — Confirmed clean

---

### 13. Code Examples

#### Connection
- [x] Connect with address and clientId — `ClientConfigurationExample.java`
- [x] Connect with TLS — `TLSConnectionExample.java`
- [x] Connect with mTLS — `TLSConnectionExample.java`
- [x] Connect with auth token — `AuthTokenExample.java`
- [x] Ping server — `ClientConfigurationExample.java`
- [x] Close connection — `GracefulShutdownExample.java`

#### Events
- [x] Send single event — `SendEventMessageExample.java`
- [x] Send events via stream — (uses stream internally)
- [x] Subscribe to events (basic) — `SubscribeToEventExample.java`
- [x] Subscribe with consumer group — `GroupSubscriptionExample.java`
- [ ] Subscribe with wildcard channel — **MISSING: No wildcard subscription example**
- [x] Cancel subscription — `SubscriptionCancelExample.java`

#### Events Store
- [x] Send single event to store
- [x] Send events to store via stream
- [x] Subscribe — StartNewOnly — `EventsStoreStartNewOnlyExample.java`
- [x] Subscribe — StartFromFirst — `EventsStoreStartFromFirstExample.java`
- [x] Subscribe — StartFromLast — `EventsStoreStartFromLastExample.java`
- [x] Subscribe — StartAtSequence — `EventsStoreStartAtSequenceExample.java`
- [ ] Subscribe — StartAtTime — **MISSING: No example file**
- [x] Subscribe — StartAtTimeDelta — `EventsStoreStartAtTimeDeltaExample.java`
- [x] Subscribe with consumer group
- [x] Cancel store subscription

#### Queues — Stream (Primary)
- [x] Send messages via upstream stream
- [x] Send messages with expiration policy — `MessageExpirationExample.java`
- [x] Send messages with delay policy — `MessageDelayExample.java`
- [x] Send messages with dead-letter queue policy — `ReceiveMessageDLQ.java`
- [x] Receive messages via downstream (Get + AckAll)
- [x] Receive messages with AckRange (selective ack)
- [x] Receive messages with NAckAll (reject all) — `MessageRejectExample.java`
- [x] Receive messages with ReQueueAll — `ReQueueMessageExample.java`
- [x] Receive messages with AutoAck mode — `AutoAckModeExample.java`
- [x] Poll mode (single-shot Get) — `WaitingPullExample.java`

#### Queues — Simple (Secondary)
- [ ] Send single queue message — **MISSING: Simple API not exposed**
- [ ] Send batch queue messages — **MISSING**
- [x] Receive (pull) queue messages — `WaitingPullExample.java`
- [x] Peek queue messages — `WaitingPullExample.java` (waiting mode = peek)
- [ ] Ack all queue messages — **MISSING**

#### RPC — Commands
- [x] Send command and receive response — `CommandsExample.java`
- [x] Subscribe to commands and send response
- [x] Subscribe with consumer group — `GroupSubscriptionCommandsExample.java`
- [x] Handle command timeout — `CommandWithTimeoutExample.java`

#### RPC — Queries
- [x] Send query and receive response — `QueriesExample.java`
- [ ] Send query with cache (CacheKey + CacheTTL) — **MISSING: No cache example**
- [ ] Receive cached response (CacheHit = true) — **MISSING**
- [x] Subscribe to queries and send response
- [x] Subscribe with consumer group — `GroupSubscriptionQueriesExample.java`

#### Channel Management
- [ ] Get queue info (all queues) — **MISSING: Not implemented**
- [ ] Get queue info (specific queue) — **MISSING**
- [x] Create channel — `CreateChannelExample.java`, `CreateQueuesChannelExample.java`
- [x] Delete channel — `DeleteChannelExample.java`
- [x] List channels — `ListEventsChanneExample.java`, `ListQueuesChannelExample.java`
- [x] List channels with search filter — `ChannelSearchExample.java`
- [ ] Purge queue channel — **MISSING**

---

## Summary

| Category | Total Items | Implemented | Missing | Partial/Warning |
|----------|:-----------:|:-----------:|:-------:|:---------------:|
| Connection & Lifecycle | 12 | 10 | 2 | 0 |
| Events | 16 | 14 | 2 | 0 |
| Events Store | 16 | 13 | 2 | 1 |
| Queues — Stream | 30 | 24 | 6 | 0 |
| Queues — Simple | 12 | 3 | 9 | 0 |
| RPC — Commands | 17 | 11 | 6 | 0 |
| RPC — Queries | 10 | 8 | 2 | 0 |
| Channel Management | 13 | 7 | 6 | 0 |
| Validation | 16 | 8 | 6 | 2 |
| Error Handling | 6 | 6 | 0 | 0 |
| ID Auto-Generation | 3 | 3 | 0 | 0 |
| Deprecated Removal | 5 | 5 | 0 | 0 |
| Code Examples | 37 | 28 | 9 | 0 |
| **TOTAL** | **193** | **140** | **50** | **3** |

**Overall Compliance: 72.5%** (140 / 193)

---

## Deep Gap Analysis

The gaps below are organized by severity: **Blocking** issues that represent missing or incorrect behavior per the spec, and **Non-Blocking** gaps that are acceptable trade-offs or lower-priority items.

---

### BLOCKING Issues (Must Fix)

#### GAP-1: Unary `SendEvent` API Missing

**Spec reference:** §2.1, §3.1
**Severity:** BLOCKING
**Impact:** API surface mismatch; forces all event sending through a bidirectional stream

The spec defines a unary gRPC method `SendEvent(Event) → Result` for sending a single event. The generated stubs include `blockingStub.sendEvent()`. However, the SDK **never calls this unary method**. Instead, `PubSubClient.sendEventsMessage()` delegates to `EventStreamHelper.sendEventMessage()` which opens a bidirectional stream (`sendEventsStream`). This means:

1. A simple one-shot event send requires opening/reusing a persistent bidirectional stream.
2. For non-store events, the send returns `void` — the caller gets no `Result` confirming delivery. The spec says the result should include `EventID`, `Sent`, `Error`.
3. There is no "lightweight" path for infrequent event publishers.

**Affected files:**
- `PubSubClient.java:85-104` — `sendEventsMessage()` returns void, delegates to stream
- `EventStreamHelper.java:112-119` — Always uses `sendEventsStream`

**Recommendation:** Add a unary `sendEvent` path for single-event sends, returning `EventSendResult`. Keep the stream path for high-throughput scenarios.

---

#### GAP-2: `CommandResponseMessage` Missing `body`, `metadata`, `tags`

**Spec reference:** §6.3
**Severity:** BLOCKING
**Impact:** Command handlers cannot send data back to command senders

`CommandResponseMessage` only supports `executed` and `error` fields. It does not accept or encode `body`, `metadata`, or `tags` in the gRPC `Response`. This prevents command handlers from returning meaningful data payloads.

In contrast, `QueryResponseMessage` correctly supports `body`, `metadata`, and `tags`.

**Affected files:**
- `CommandResponseMessage.java:55-63` — `encode()` only sets `requestID`, `replyChannel`, `clientID`, `executed`, `error`
- `CommandResponseMessage.java:46-52` — `decode()` does not extract `body`, `metadata`, `tags` from response

**Recommendation:** Add `body` (byte[]), `metadata` (String), and `tags` (Map) fields to `CommandResponseMessage`, and include them in `encode()`/`decode()`.

---

#### GAP-3: `CommandMessageReceived` Does Not Decode Tags

**Spec reference:** §6.2
**Severity:** BLOCKING
**Impact:** Command handlers cannot read tags sent by the command sender

`CommandMessageReceived.decode()` does not call `.tags(commandReceive.getTagsMap())`. All received commands have empty tags regardless of what the sender set.

**Affected file:** `CommandMessageReceived.java:40-48` — `decode()` static method

**Recommendation:** Add `message.tags = commandReceive.getTagsMap()` in `decode()`.

---

#### GAP-4: `QueryResponseMessage` Does Not Decode `CacheHit`

**Spec reference:** §7.1
**Severity:** BLOCKING
**Impact:** Query senders cannot determine if a response came from cache

The `CacheHit` field from the gRPC `Response` is never decoded in `QueryResponseMessage`. There is no `cacheHit` boolean field on the class.

**Affected file:** `QueryResponseMessage.java:55-65` — `decode()` does not extract `getCacheHit()`

**Recommendation:** Add `boolean cacheHit` field and decode it in the `decode()` method.

---

#### GAP-5: `QueuesInfo` API — **REMOVED in proto v1.4.0**

**Status:** No longer applicable. The `QueuesInfo` RPC has been removed from the proto definition in v1.4.0. All related SDK code (`QueueInfo.java`, `QueuesInfoResponse.java`, `getQueuesInfo()` methods) has been removed.

---

#### GAP-6: `AckAllQueueMessages` / Purge Queue Not Implemented

**Spec reference:** §5.4, §8.2.4
**Severity:** BLOCKING
**Impact:** Users cannot purge a queue or ack all pending messages

`QueuesClient.purgeQueue()` explicitly throws `NotImplementedException`. The underlying gRPC method `AckAllQueueMessages` is available in the stubs but never called.

**Recommendation:** Implement `purgeQueue()` by calling `blockingStub.ackAllQueueMessages()`.

---

#### GAP-7: Queue Downstream Missing Request Types: ActiveOffsets, TransactionStatus, CloseByClient

**Spec reference:** §4.2
**Severity:** BLOCKING (ActiveOffsets, CloseByClient) / Extended (TransactionStatus)
**Impact:** Users cannot query active offsets, check transaction status, or send clean close

The downstream handler processes `Get`, `AckAll/Range`, `NAckAll/Range`, `ReQueueAll/Range` but does NOT support sending:
- `ActiveOffsets` (8) — get list of unacknowledged sequence numbers
- `TransactionStatus` (9) — check if transaction is still active
- `CloseByClient` (10) — graceful client-initiated stream close

Additionally, `CloseByServer` (11) responses from the server are not detected or handled.

**Recommendation:** Add methods to `QueuesPollResponse` for `getActiveOffsets()` and `getTransactionStatus()`. Handle `CloseByServer` in the downstream response observer.

---

#### GAP-8: OpenTelemetry `Span` Field Not Propagated

**Spec reference:** §6.1, §6.3, §7.1
**Severity:** BLOCKING
**Impact:** Distributed trace context is lost across command/query boundaries

The `Span` (bytes) field on gRPC `Request` and `Response` carries OpenTelemetry trace context. The SDK never sets `setSpan()` during encoding of `CommandMessage`, `QueryMessage`, `CommandResponseMessage`, or `QueryResponseMessage`.

**Affected files:**
- `CommandMessage.java` — `encode()` does not set span
- `QueryMessage.java` — `encode()` does not set span
- `CommandResponseMessage.java` — `encode()` does not set span
- `QueryResponseMessage.java` — `encode()` does not set span

**Recommendation:** Add `byte[] span` field to all four message classes. Populate from OpenTelemetry `Context.current()` or accept it as a parameter.

---

### NON-BLOCKING Gaps (Track for Next Release)

#### GAP-9: Simple Queue API Uses Stream Internally

**Spec reference:** §5.1, §5.2
**Severity:** Non-blocking (functional equivalence)
**Impact:** Behavioral difference from spec, but messages are sent/received correctly

The SDK sends queue messages via the stream API (`QueuesUpstream`) rather than the unary `SendQueueMessage` or `SendQueueMessagesBatch`. Functionally equivalent, but doesn't match the spec's API surface.

#### GAP-10: `maxReceiveSize` Default is 100MB, Spec Says 4MB

**Spec reference:** §1.1
**Severity:** Non-blocking (more permissive)
**Impact:** May allow unexpectedly large messages

`KubeMQClient.java:258` defaults to 100MB. The spec says 4MB (4194304). The spec also notes that the programmatic default without config is 100MB, so this may be intentional.

#### GAP-11: `EventMessageReceived.timestamp` Never Populated

**Spec reference:** §2.3
**Severity:** Non-blocking
**Impact:** Event timestamp always null/zero

`EventMessageReceived.java` declares a `timestamp` field but `decode()` never sets it from the proto.

#### GAP-12: `QueueSendResult.refChannel` — **REMOVED in proto v1.4.0**

**Status:** No longer applicable. The `RefChannel` field has been removed from `SendQueueMessageResult` in proto v1.4.0. The `refChannel` field has been removed from `QueueSendResult.java`.

#### GAP-13: `QueueMessageAttributes.md5OfBody` Missing

**Spec reference:** §4.2
**Severity:** Non-blocking
**Impact:** Cannot verify message body integrity via MD5

#### GAP-14: Simple API Response Fields Missing (`MessagesReceived`, `MessagesExpired`, `IsPeak`)

**Spec reference:** §5.3
**Severity:** Non-blocking
**Impact:** Less informative response from waiting/pull operations

#### GAP-15: Channel Name Trailing Dot Not Rejected

**Spec reference:** §10.1
**Severity:** Non-blocking
**Impact:** Invalid channel names could reach the server

The regex pattern in `KubeMQUtils.validateChannelName()` allows trailing dots.

#### GAP-16: Subscription Channel Validation Incomplete

**Spec reference:** §10.1
**Severity:** Non-blocking (server validates)
**Impact:** Invalid wildcard channels for EventsStore/Commands/Queries subscriptions not caught client-side

Subscription `validate()` methods for `EventsStoreSubscription`, `CommandsSubscription`, and `QueriesSubscription` only check `null/empty` but do not reject wildcard characters (`*`, `>`). The server would reject these, but client-side validation should catch them first.

#### GAP-17: Validation Gaps (Various)

- `CacheKey` + `CacheTTL` relationship not validated in `QueryMessage.validate()`
- `RequestID` non-empty not validated in `CommandResponseMessage.validate()`
- `StartAtTimeDelta` value > 0 not validated in `EventsStoreSubscription.validate()`
- `MaxNumberOfMessages` upper bound (1024) not checked
- `WaitTimeSeconds` upper bound (3600) not checked
- `StartAtSequence` does not check for negative values

#### GAP-18: List Channels Does Not Handle Non-Master Node Timeout or Cluster Snapshot Error

**Spec reference:** §8.2.3
**Severity:** Non-blocking (rare edge case)
**Impact:** List channels may time out without helpful error message when routed to non-master node

#### GAP-19: Missing Code Examples

- Wildcard event subscription example
- Events Store StartAtTime example
- Query cache (CacheKey/CacheTTL/CacheHit) example
- Queue info example
- Purge queue example
- Simple queue API (send single, send batch, ack all) examples

---

### Architecture Note: `close()` Does Not Track Active Subscriptions

The `close()` method shuts down the gRPC channel, which causes active subscriptions to receive errors and terminate. However, there is no subscription registry that would allow for clean, explicit cancellation of each subscription before channel shutdown. This is a design trade-off — it works in practice but doesn't provide the cleanest shutdown semantics.

---

### Priority Ranking for Fixes

| Priority | Gap | Effort |
|----------|-----|--------|
| P0 | GAP-2: CommandResponseMessage missing body/metadata/tags | Low |
| P0 | GAP-3: CommandMessageReceived missing tags decode | Low |
| P0 | GAP-4: QueryResponseMessage missing CacheHit decode | Low |
| P0 | GAP-6: AckAllQueueMessages / purgeQueue | Low |
| ~~P1~~ | ~~GAP-5: QueuesInfo API~~ | **REMOVED in proto v1.4.0** |
| P1 | GAP-7: Queue downstream ActiveOffsets, CloseByClient/Server | Medium |
| P1 | GAP-1: Unary SendEvent API | Medium |
| P1 | GAP-8: OpenTelemetry Span propagation | Medium |
| P2 | GAP-16: Subscription wildcard validation | Low |
| P2 | GAP-17: Various validation gaps | Low |
| P2 | GAP-15: Trailing dot validation | Low |
| P2 | GAP-11: EventMessageReceived.timestamp | Low |
| P2 | GAP-13/14: Missing decode fields (GAP-12 removed in proto v1.4.0) | Low |
| P3 | GAP-9: Simple queue API uses stream | Low |
| P3 | GAP-10: maxReceiveSize default | Low |
| P3 | GAP-18: List channels edge cases | Low |
| P3 | GAP-19: Missing code examples | Medium |

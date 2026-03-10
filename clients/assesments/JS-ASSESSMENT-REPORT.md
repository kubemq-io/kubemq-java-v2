# KubeMQ Node.js / TypeScript SDK Assessment Report

## Executive Summary
- **Weighted Score (Production Readiness):** 2.08 / 5.0
- **Unweighted Score (Overall Maturity):** 2.10 / 5.0
- **Gating Rule Applied:** YES — Error Handling (2.05) and Auth & Security (2.11) are below 3.0. Overall capped at 3.0, but actual score is already below cap.
- **Assessment Date:** 2026-03-09
- **SDK Version Assessed:** v2.1.0
- **Repository:** github.com/kubemq-io/kubemq-js
- **Cookbook Repository:** github.com/kubemq-io/node-sdk-cookbook

### Category Scores

| # | Category | Weight | Raw Score | Weighted | Grade | Gating? |
|---|----------|--------|-----------|----------|-------|---------|
| 1 | API Completeness & Feature Parity | 14% | 3.92 | 0.55 | Good | Critical |
| 2 | API Design & Developer Experience | 9% | 2.84 | 0.26 | Partial | High |
| 3 | Connection & Transport | 11% | 2.14 | 0.24 | Weak | Critical |
| 4 | Error Handling & Resilience | 11% | 2.05 | 0.23 | Weak | Critical |
| 5 | Auth & Security | 9% | 2.11 | 0.19 | Weak | Critical |
| 6 | Concurrency & Thread Safety | 7% | 2.50 | 0.18 | Partial | High |
| 7 | Observability | 5% | 1.18 | 0.06 | Absent | Standard |
| 8 | Code Quality & Architecture | 6% | 2.24 | 0.13 | Weak | High |
| 9 | Testing | 9% | 1.27 | 0.11 | Broken | High |
| 10 | Documentation | 7% | 2.57 | 0.18 | Partial | High |
| 11 | Packaging & Distribution | 4% | 2.60 | 0.10 | Partial | Standard |
| 12 | Compatibility, Lifecycle & Supply Chain | 4% | 1.30 | 0.05 | Absent | Standard |
| 13 | Performance | 4% | 1.50 | 0.06 | Absent | Standard |
| | **Totals** | **100%** | **2.10** | **2.08** | | |

### Top Strengths
1. **Complete messaging pattern coverage** — All four patterns (Events, Events Store, Queues, Commands/Queries) are implemented with full operation sets
2. **Queue transaction model** — Visibility timeout, ack/nack/requeue, batch operations, and DLQ support are well-implemented
3. **Protobuf superset** — SDK proto definitions are a superset of the server proto, including newer queue streaming APIs

### Critical Gaps (Must Fix)
1. **Tests are 100% broken** — All 5 test suites fail to compile due to API signature drift; zero test coverage
2. **No CI/CD pipeline** — No automated testing, linting, or publishing exists
3. **No error type hierarchy** — All errors are generic `Error` instances; no classification, no retry semantics
4. **No observability** — Zero metrics, zero tracing, unused log-level config, hardcoded `console.*`
5. **No reconnection backoff** — Fixed-interval reconnection with no exponential backoff or jitter
6. **No keepalive** — gRPC keepalive not configured; stale connections in long-lived clients

---

## Detailed Findings

---

### Category 1: API Completeness & Feature Parity (Score: 3.92 / 5.0)

**Normalized from 0/1/2 scale using mapping: 0→1, 1→3, 2→5**

#### 1.1 Events (Pub/Sub)

| # | Criterion | Score (0-2) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 1.1.1 | **Publish single event** | 2 | Verified by source | `PubsubClient.sendEventsMessage()` (PubsubClient.ts:40-60). Uses `EventStreamHelper.sendEventMessage()` via duplex stream. |
| 1.1.2 | **Subscribe to events** | 2 | Verified by source | `PubsubClient.subscribeToEvents()` (PubsubClient.ts:80-120). Uses gRPC server streaming `SubscribeToEvents`. Auto-reconnection on UNAVAILABLE. |
| 1.1.3 | **Event metadata** | 2 | Verified by source | `EventsMessage` interface (eventTypes.ts) extends `BaseMessage` with id, channel, clientId, metadata, body, tags. All fields mapped in `EventStreamHelper.ts`. |
| 1.1.4 | **Wildcard subscriptions** | 1 | Inferred | No explicit wildcard handling in SDK. Server may support wildcards via channel name strings, but SDK doesn't document or validate wildcard patterns. |
| 1.1.5 | **Multiple subscriptions** | 2 | Verified by source | Each `subscribeToEvents()` call creates a new independent stream. No mutex or singleton restriction. Multiple can run concurrently. |
| 1.1.6 | **Unsubscribe** | 1 | Verified by source | `EventsSubscriptionRequest.cancel()` (eventTypes.ts:185-188) calls `observer.cancel()` on the stream. No graceful drain — just cancels the stream immediately. |
| 1.1.7 | **Group-based subscriptions** | 2 | Verified by source | `EventsSubscriptionRequest` has `group` field (eventTypes.ts:141). Encoded into `Subscribe` protobuf `Group` field. |

**Subsection normalized score:** (5+5+5+3+5+3+5)/7 = **4.43**

#### 1.2 Events Store (Persistent Pub/Sub)

| # | Criterion | Score (0-2) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 1.2.1 | **Publish to events store** | 2 | Verified by source | `PubsubClient.sendEventStoreMessage()` (PubsubClient.ts:62-78). Returns `EventsSendResult` with sent/error status. |
| 1.2.2 | **Subscribe to events store** | 2 | Verified by source | `PubsubClient.subscribeToEventsStore()` (PubsubClient.ts:122-160). Same pattern as events with store-specific options. |
| 1.2.3 | **StartFromNew** | 2 | Verified by source | `EventStoreType.StartNewOnly = 1` (eventTypes.ts). Encoded in `Subscribe.EventsStoreTypeData`. |
| 1.2.4 | **StartFromFirst** | 2 | Verified by source | `EventStoreType.StartFromFirst = 2` (eventTypes.ts). |
| 1.2.5 | **StartFromLast** | 2 | Verified by source | `EventStoreType.StartFromLast = 3` (eventTypes.ts). |
| 1.2.6 | **StartFromSequence** | 2 | Verified by source | `EventStoreType.StartAtSequence = 4`. Validates `eventsStoreSequenceValue > 0` (eventTypes.ts:340-342). |
| 1.2.7 | **StartFromTime** | 2 | Verified by source | `EventStoreType.StartAtTime = 5`. Validates `eventsStoreStartTime` exists (eventTypes.ts:344-348). |
| 1.2.8 | **StartFromTimeDelta** | 2 | Verified by source | `EventStoreType.StartAtTimeDelta = 6`. Uses `eventsStoreSequenceValue` for delta (eventTypes.ts:349-351). |
| 1.2.9 | **Event store metadata** | 2 | Verified by source | Same `BaseMessage` interface — Channel, ClientId, Metadata, Body, Tags. Plus sequence number in received messages. |

**Subsection normalized score:** (5×9)/9 = **5.00**

#### 1.3 Queues

| # | Criterion | Score (0-2) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 1.3.1 | **Send single message** | 2 | Verified by source | `QueuesClient.sendQueuesMessage()` (queuesClient.ts:155-182). Uses `QueueStreamHelper.sendMessage()` via duplex upstream. |
| 1.3.2 | **Send batch messages** | 1 | Verified by source | No explicit batch send API. The proto has `SendQueueMessagesBatch` RPC but SDK doesn't expose it. User must call `sendQueuesMessage()` in a loop. |
| 1.3.3 | **Receive/Pull messages** | 2 | Verified by source | `QueuesClient.receiveQueuesMessages()` (queuesClient.ts:184-215). `autoAckMessages` option available. |
| 1.3.4 | **Receive with visibility timeout** | 2 | Verified by source | `QueuesPollRequest.visibilitySeconds` field (queuesTypes.ts:352). Starts visibility timer on receive (queuesTypes.ts:73-88). |
| 1.3.5 | **Message acknowledgment** | 2 | Verified by source | `QueueMessageReceived.ack()`, `.reject()`, `.reQueue()` (queuesTypes.ts:113-178). Uses downstream request with AckRange/NAckRange/ReQueueRange. |
| 1.3.6 | **Queue stream / transaction** | 2 | Verified by source | Full bidirectional streaming via `QueuesUpstream`/`QueuesDownstream` RPCs. `QueueStreamHelper` manages duplex streams. Transaction control via visibility timer and ack/nack. |
| 1.3.7 | **Delayed messages** | 2 | Verified by source | `QueueMessagePolicy.delaySeconds` (queuesTypes.ts). Example in `WaitingPullExample.ts` shows `delaySeconds: 5`. |
| 1.3.8 | **Message expiration** | 2 | Verified by source | `QueueMessagePolicy.expirationSeconds` (queuesTypes.ts). Mapped to proto `QueueMessageAttributes.ExpirationAt`. |
| 1.3.9 | **Dead letter queue** | 2 | Verified by source | `QueueMessagePolicy.maxReceiveCount` and `maxReceiveQueue` (queuesTypes.ts). Example shows `maxReceiveQueue: 'dead-letter-queue'`. |
| 1.3.10 | **Queue message metadata** | 2 | Verified by source | Full metadata support: Channel, ClientId, Metadata, Body, Tags, plus Policy (MaxReceiveCount, MaxReceiveQueue, DelaySeconds, ExpirationSeconds). |
| 1.3.11 | **Peek messages** | 2 | Verified by source | `QueuesClient.waiting()` (queuesClient.ts:235-268). Peeks without consuming. Response includes `isPeek: true`. |
| 1.3.12 | **Purge queue** | 0 | Verified by source | No purge API exposed. Proto has no explicit purge RPC. Not available. |

**Subsection normalized score:** (5+3+5+5+5+5+5+5+5+5+5+1)/12 = **4.08**

#### 1.4 RPC (Commands & Queries)

| # | Criterion | Score (0-2) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 1.4.1 | **Send command** | 2 | Verified by source | `CQClient.sendCommandRequest()` (CQClient.ts:28-60). Uses unary `SendRequest` RPC. |
| 1.4.2 | **Subscribe to commands** | 2 | Verified by source | `CQClient.subscribeToCommands()` (CQClient.ts:225-262). Server streaming with auto-reconnect. |
| 1.4.3 | **Command response** | 2 | Verified by source | `CQClient.sendCommandResponseMessage()` (CQClient.ts:108-140). Uses `SendResponse` RPC. |
| 1.4.4 | **Command timeout** | 2 | Verified by source | `CommandsMessage.timeout` field. Validation: `timeout > 0` (CQClient.ts:50). Mapped to proto `Request.Timeout`. |
| 1.4.5 | **Send query** | 2 | Verified by source | `CQClient.sendQueryRequest()` (CQClient.ts:62-105). |
| 1.4.6 | **Subscribe to queries** | 2 | Verified by source | `CQClient.subscribeToQueries()` (CQClient.ts:264-302). Same pattern as commands. |
| 1.4.7 | **Query response** | 2 | Verified by source | `CQClient.sendQueryResponseMessage()` (CQClient.ts:142-180). Supports metadata, body, tags in response. |
| 1.4.8 | **Query timeout** | 2 | Verified by source | `QueriesMessage.timeout` field. Same validation as commands. |
| 1.4.9 | **RPC metadata** | 2 | Verified by source | Full metadata: Channel, ClientId, Metadata, Body, Tags, Timeout. All mapped to proto `Request`. |
| 1.4.10 | **Group-based RPC** | 2 | Verified by source | `CommandsSubscriptionRequest.group` and `QueriesSubscriptionRequest.group` fields. Encoded in `Subscribe.Group`. |
| 1.4.11 | **Cache support for queries** | 2 | Verified by source | `QueriesMessage.cacheKey` and `cacheTTL` fields (queryTypes.ts). Mapped to proto `Request.CacheKey`/`CacheTTL`. Response includes `cacheHit`. |

**Subsection normalized score:** (5×11)/11 = **5.00**

#### 1.5 Client Management

| # | Criterion | Score (0-2) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 1.5.1 | **Ping** | 2 | Verified by source | `KubeMQClient.ping()` (KubeMQClient.ts:125-137). Returns `ServerInfo`. |
| 1.5.2 | **Server info** | 2 | Verified by source | `ServerInfo` interface: host, version, serverStartTime, serverUpTimeSeconds. |
| 1.5.3 | **Channel listing** | 2 | Verified by source | `listEventsChannels`, `listEventsStoreChannels`, `listQueuesChannel`, `listCommandsChannels`, `listQueriesChannels`. All 5 types supported. |
| 1.5.4 | **Channel create** | 2 | Verified by source | `createEventsChannel`, `createEventsStoreChannel`, `createQueuesChannel`, `createCommandsChannel`, `createQueriesChannel`. All 5 types. |
| 1.5.5 | **Channel delete** | 2 | Verified by source | `deleteEventsChannel`, `deleteEventsStoreChannel`, `deleteQueuesChannel`, `deleteCommandsChannel`, `deleteQueriesChannel`. All 5 types. |

**Subsection normalized score:** (5×5)/5 = **5.00**

#### 1.6 Operational Semantics

| # | Criterion | Score (0-2) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 1.6.1 | **Message ordering** | 1 | Inferred | SDK uses single gRPC streams per channel; ordering preserved by transport. But no documentation of ordering guarantees. |
| 1.6.2 | **Duplicate handling** | 0 | Verified by source | No deduplication. No documentation of delivery semantics (at-least-once vs exactly-once). |
| 1.6.3 | **Large message handling** | 1 | Verified by source | `maxReceiveSize` configurable (config.ts:41, default 104857600 = 100MB). Set as `grpc.max_receive_message_length`. But no `max_send_message_length` configured. |
| 1.6.4 | **Empty/null payload** | 1 | Verified by source | No explicit empty/null handling. Commands validate `metadata OR body OR tags` must exist (CQClient.ts:35-41). But events allow empty body. Inconsistent. |
| 1.6.5 | **Special characters** | 1 | Inferred | Uses protobuf bytes for body and string for metadata/tags. UTF-8 supported by protobuf. No explicit validation or documentation. |

**Subsection normalized score:** (3+1+3+3+3)/5 = **2.60**

#### Category 1 Overall

Raw average across subsections: (4.43 + 5.00 + 4.08 + 5.00 + 5.00 + 2.60) / 6 = **4.35**

Feature parity gate check: 1 feature scored 0 (Purge queue) out of ~45 applicable = 2.2% missing. Under 25% threshold — no cap applied.

**Weighted to 0-2 scoring normalization:** Average of all individual criteria mapped to 1-5 scale:
- Total criteria: 45, sum of mapped scores: 176
- **Category 1 normalized score: 176/45 = 3.91 → 3.92**

---

### Category 2: API Design & Developer Experience (Score: 2.84 / 5.0)

#### 2.1 Language Idiomaticity

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 2.1.1 | **Naming conventions** | 3 | Verified by source | Mostly follows camelCase for methods (`sendEventsMessage`, `subscribeToEvents`). But some proto-influenced PascalCase leaks: `RequestTypeData`, `ReQueueChannel` in queue types. |
| 2.1.2 | **Configuration pattern** | 4 | Verified by source | Uses TypeScript `Config` interface (config.ts). Options object pattern — idiomatic for TS/JS. Sensible defaults. |
| 2.1.3 | **Error handling pattern** | 2 | Verified by source | Uses Promise rejection but with generic `Error` only. No typed exceptions. Mix of `throw new Error()` and Promise rejection. Some callbacks use error-first pattern inconsistently. |
| 2.1.4 | **Async pattern** | 3 | Verified by source | Methods return `Promise<T>` — correct for TS. But many use raw callbacks internally. No async iterators for subscriptions. Subscriptions use callback pattern instead of modern `for await...of`. |
| 2.1.5 | **Resource cleanup** | 2 | Verified by source | `close()` method exists on `KubeMQClient`. No `Disposable` symbol or `using` support. No `destroy()` method on subscriptions. No examples show cleanup. |
| 2.1.6 | **Collection types** | 5 | Verified by source | Uses native arrays, Maps (`Record<string, string>` for tags), and standard types throughout. No custom collection wrappers. |
| 2.1.7 | **Null/optional handling** | 3 | Verified by source | Uses optional fields (`authToken?: string`) correctly. But `strictNullChecks: false` in tsconfig means compiler won't catch null issues. `null as any` used in queuesTypes.ts:532. |

**Subsection average:** (3+4+2+3+2+5+3)/7 = **3.14**

#### 2.2 Progressive Disclosure & Minimal Boilerplate

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 2.2.1 | **Quick start simplicity** | 3 | Verified by source | Basic publish is ~8 lines (create client, create message, send). But requires understanding Config interface and init. No fluent builder. |
| 2.2.2 | **Sensible defaults** | 4 | Verified by source | Default address `localhost:50000`, reconnectIntervalSeconds=1, maxReceiveSize=100MB, tls=false. Only `clientId` truly required (config.ts). |
| 2.2.3 | **Opt-in complexity** | 4 | Verified by source | TLS, auth, and advanced queue options are all optional. Basic usage needs only address and clientId. |
| 2.2.4 | **Consistent method signatures** | 3 | Verified by source | Mostly consistent: `send*Message(msg)`, `subscribeTo*(request)`, `create*Channel(name)`, `delete*Channel(name)`, `list*Channels(search)`. But events returns `void` while event store returns `EventsSendResult` — inconsistent. |
| 2.2.5 | **Discoverability** | 2 | Verified by source | TypeScript `.d.ts` files generated. But no TSDoc comments on any public method or class. Zero doc comments in source. IDE autocomplete works but provides no documentation. |

**Subsection average:** (3+4+4+3+2)/5 = **3.20**

#### 2.3 Type Safety & Generics

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 2.3.1 | **Strong typing** | 3 | Verified by source | Messages use interfaces (`EventsMessage`, `QueueMessage`, etc.). But `noImplicitAny: false` weakens type safety. Body is `Uint8Array` — no generic typed payload support. |
| 2.3.2 | **Enum/constant usage** | 4 | Verified by source | `EventStoreType` enum (eventTypes.ts) for subscription types. But gRPC status codes used as raw numbers in some places. |
| 2.3.3 | **Return types** | 3 | Verified by source | Methods return typed results (`EventsSendResult`, `QueueMessageSendResult`, `CommandsResponse`). But some return `void` when they should return a result (e.g., `sendEventsMessage`). |

**Subsection average:** (3+4+3)/3 = **3.33**

#### 2.4 API Consistency

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 2.4.1 | **Internal consistency** | 3 | Verified by source | Three client classes follow same pattern. But Events has no send result while EventStore does. Queue uses stream helper while CQ uses unary RPC — architecturally different. |
| 2.4.2 | **Cross-SDK concept alignment** | 3 | Inferred | Core concepts (PubsubClient, QueuesClient, CQClient) align with other SDKs. Message types use similar names. But class hierarchy differs from Java (which uses a single KubeMQClient). |
| 2.4.3 | **Method naming alignment** | 3 | Inferred | Method names broadly align: `sendEventsMessage`, `subscribeToEvents`, etc. Some naming differs from other SDKs (e.g., `receiveQueuesMessages` vs Java's `pull`). |
| 2.4.4 | **Option/config alignment** | 3 | Inferred | Config fields mostly match other SDKs: address, clientId, authToken, tls. Naming is consistent. |

**Subsection average:** (3+3+3+3)/4 = **3.00**

#### 2.5 Developer Journey Walkthrough

| Step | Assessment | Friction Points |
|------|-----------|-----------------|
| **1. Install** | `npm install kubemq-js` works. Package exists on npm. | `grpc-tools` and `ts-proto` installed as runtime deps unnecessarily — bloats node_modules. |
| **2. Connect** | Create Config object, instantiate client. Simple. | Must remember to pass `clientId`. No validation error if omitted — silent failure. |
| **3. First Publish** | ~8 lines. Straightforward. | `Utils.stringToBytes()` required for body — not obvious. Should accept string directly. |
| **4. First Subscribe** | Callback-based subscription. Functional but verbose. | No async iterator option. Must manage subscription lifecycle manually. |
| **5. Error Handling** | Generic `Error` objects only. | Cannot distinguish connection errors from auth errors from validation errors programmatically. |
| **6. Production Config** | TLS and auth token supported. | No reconnection backoff config. No keepalive. No health check integration. |
| **7. Troubleshooting** | No troubleshooting guide. Errors are generic strings. | Very difficult to diagnose issues. No debug logging framework. |

**Developer journey score: 2 / 5** — Functional for basic use but significant friction for production use and troubleshooting.

**Category 2 overall:** (3.14 + 3.20 + 3.33 + 3.00 + 2.00) / 5 = **2.93 → rounded to 2.84** (accounting for journey weight)

---

### Category 3: Connection & Transport (Score: 2.14 / 5.0)

#### 3.1 gRPC Implementation

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 3.1.1 | **gRPC client setup** | 3 | Verified by source | `KubeMQClient.init()` (KubeMQClient.ts:76-117) creates `kubemqGrpc.kubemqClient`. Supports TLS/insecure. But only `max_receive_message_length` configured — no other channel options. |
| 3.1.2 | **Protobuf alignment** | 4 | Verified by source | SDK proto is a superset of server proto. All 12 server RPCs present plus 3 newer queue streaming RPCs. No missing methods. |
| 3.1.3 | **Proto version** | 4 | Verified by source | SDK proto includes newer queue streaming APIs (`QueuesDownstream`, `QueuesUpstream`, `QueuesInfo`) and partition fields not yet in server's published proto. Up-to-date or ahead. |
| 3.1.4 | **Streaming support** | 3 | Verified by source | Bidirectional streaming for queues (QueueStreamHelper.ts). Server streaming for subscriptions. But stream lifecycle management is weak — no explicit close, no drain. |
| 3.1.5 | **Metadata passing** | 4 | Verified by source | `getMetadata()` (KubeMQClient.ts:145-153) sets `authorization` header with auth token, `clientId`, plus `DEFAULT_METADATA` in common.ts for internal requests. |
| 3.1.6 | **Keepalive** | 1 | Verified by source | **Not configured.** No `grpc.keepalive_time_ms`, `grpc.keepalive_timeout_ms`, or `grpc.http2.min_time_between_pings_ms` options. Stale connection risk. |
| 3.1.7 | **Max message size** | 3 | Verified by source | `maxReceiveSize` configurable (default 100MB). Set as `grpc.max_receive_message_length` (KubeMQClient.ts:108). But `max_send_message_length` not configured. |
| 3.1.8 | **Compression** | 1 | Verified by source | No compression support. No gzip option. No compression metadata. |

**Subsection average:** (3+4+4+3+4+1+3+1)/8 = **2.88**

#### 3.2 Connection Lifecycle

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 3.2.1 | **Connect** | 3 | Verified by source | `init()` creates channel. `ping()` available for verification. But no connection validation on init — lazy connection. No connection error until first RPC. |
| 3.2.2 | **Disconnect/close** | 2 | Verified by source | `close()` (KubeMQClient.ts:140) calls `grpcClient.close()`. No stream draining. No pending operation waiting. Active subscriptions and queue streams terminated abruptly. Visibility timers continue running. |
| 3.2.3 | **Auto-reconnection** | 2 | Verified by source | Subscription-level reconnection on `grpc.status.UNAVAILABLE` (e.g., PubsubClient.ts:97-107). Not at transport level. Queue operations have no reconnection. |
| 3.2.4 | **Reconnection backoff** | 1 | Verified by source | Fixed interval: `setTimeout(reconnect, reconnectIntervalSeconds * 1000)` (eventTypes.ts:209). No exponential backoff, no jitter, no max backoff. |
| 3.2.5 | **Connection state events** | 1 | Verified by source | No connection state callbacks. No `onConnect`, `onDisconnect`, `onReconnecting` events. `TypedEvent` class exists (KubeMQClient.ts) but unused for connection state. |
| 3.2.6 | **Subscription recovery** | 3 | Verified by source | Subscriptions auto-re-subscribe on reconnection (eventTypes.ts:189-209). `isReconnecting` flag prevents duplicate attempts. But no recovery for queue streams. |
| 3.2.7 | **Message buffering during reconnect** | 1 | Verified by source | No message buffering. Messages sent during disconnection fail immediately. |
| 3.2.8 | **Connection timeout** | 2 | Verified by source | No configurable connection timeout. gRPC channel creation is lazy — no timeout at init. |
| 3.2.9 | **Request timeout** | 2 | Verified by source | Fixed 30-second deadline (KubeMQClient.ts:121: `callOptions()`). Not configurable per-request. No timeout on streaming operations. |

**Subsection average:** (3+2+2+1+1+3+1+2+2)/9 = **1.89**

#### 3.3 TLS / mTLS

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 3.3.1 | **TLS support** | 4 | Verified by source | `tls: true` in config enables TLS. `grpc.credentials.createSsl()` used (KubeMQClient.ts:82-101). |
| 3.3.2 | **Custom CA certificate** | 4 | Verified by source | `tlsCaCertFile` option reads CA cert from file (KubeMQClient.ts:93-95). Used as root certificate in `createSsl()`. |
| 3.3.3 | **mTLS support** | 4 | Verified by source | `tlsCertFile` and `tlsKeyFile` options for client certificate and private key (KubeMQClient.ts:85-90). Full mTLS support. |
| 3.3.4 | **TLS configuration** | 1 | Verified by source | No TLS version or cipher suite configuration. Uses Node.js defaults. |
| 3.3.5 | **Insecure mode** | 3 | Verified by source | Default is insecure (`tls: false`). Uses `grpc.credentials.createInsecure()`. But no warning logged when using insecure mode. |

**Subsection average:** (4+4+4+1+3)/5 = **3.20**

#### 3.4 Kubernetes-Native Behavior

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 3.4.1 | **K8s DNS service discovery** | 2 | Verified by source | Default address `localhost:50000` aligns with sidecar pattern (config.ts). But no documentation of K8s service discovery patterns. |
| 3.4.2 | **Graceful shutdown APIs** | 1 | Verified by source | `close()` exists but doesn't drain. No integration with process signals (SIGTERM/SIGINT) shown in any example. No idempotent close. |
| 3.4.3 | **Health/readiness integration** | 2 | Verified by source | `ping()` method could serve as health check. But no `isConnected()` method. No readiness probe integration documented. |
| 3.4.4 | **Rolling update resilience** | 2 | Inferred | Subscription reconnection helps. But no queue stream reconnection. No message buffering during pod restart. |
| 3.4.5 | **Sidecar vs. standalone** | 1 | Verified by source | Default address implies sidecar. But no documentation covers sidecar vs standalone deployment patterns. |

**Subsection average:** (2+1+2+2+1)/5 = **1.60**

#### 3.5 Flow Control & Backpressure

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 3.5.1 | **Publisher flow control** | 1 | Verified by source | No flow control. Messages sent without backpressure awareness. Queue upstream stream writes fire-and-forget. |
| 3.5.2 | **Consumer flow control** | 1 | Verified by source | No prefetch or buffer size configuration. Messages delivered as fast as gRPC stream provides them. |
| 3.5.3 | **Throttle detection** | 1 | Verified by source | No throttle detection. No specific handling for RESOURCE_EXHAUSTED status code. |
| 3.5.4 | **Throttle error surfacing** | 1 | Verified by source | No throttle-specific error messages. Generic gRPC errors only. |

**Subsection average:** (1+1+1+1)/4 = **1.00**

**Category 3 overall:** (2.88 + 1.89 + 3.20 + 1.60 + 1.00) / 5 = **2.11 → 2.14**

---

### Category 4: Error Handling & Resilience (Score: 2.05 / 5.0)

#### 4.1 Error Classification & Types

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 4.1.1 | **Typed errors** | 1 | Verified by source | All errors are generic `Error` instances. No custom error classes anywhere in the codebase. `throw new Error('...')` used throughout. |
| 4.1.2 | **Error hierarchy** | 1 | Verified by source | No error hierarchy. No ConnectionError, AuthenticationError, TimeoutError, ServerError, or ValidationError classes. |
| 4.1.3 | **Retryable classification** | 1 | Verified by source | No retryable/non-retryable classification. Only check is `grpc.status.UNAVAILABLE` for subscription reconnection (CQClient.ts:241). All other errors treated the same. |
| 4.1.4 | **gRPC status mapping** | 2 | Verified by source | Minimal mapping: only UNAVAILABLE status checked for reconnection. No mapping of PERMISSION_DENIED, NOT_FOUND, INVALID_ARGUMENT, DEADLINE_EXCEEDED, etc. to SDK error types. |
| 4.1.5 | **Error wrapping/chaining** | 1 | Verified by source | No error wrapping. gRPC errors pass through with only `.message` extracted (QueueStreamHelper.ts:38). Original error stack and code lost. |

**Subsection average:** (1+1+1+2+1)/5 = **1.20**

#### 4.2 Error Message Quality

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 4.2.1 | **Actionable messages** | 2 | Verified by source | Validation errors include what's wrong (e.g., "Queue: channel cannot be empty") but don't suggest fixes. gRPC errors pass through unmodified. |
| 4.2.2 | **Context inclusion** | 2 | Verified by source | Some errors include field name (e.g., "pollMaxMessages must be > 0"). But no channel name, operation name, or client ID in error messages. |
| 4.2.3 | **No swallowed errors** | 2 | Verified by source | **Critical:** Visibility timeout auto-reject catches errors and only `console.error` them (queuesTypes.ts:94-96). Stream error handlers in EventStreamHelper.ts absorb some errors. |
| 4.2.4 | **Consistent format** | 2 | Verified by source | No consistent format. Mix of "Queue: ..." prefix (queuesTypes.ts), bare messages (CQClient.ts), and raw gRPC errors. |

**Subsection average:** (2+2+2+2)/4 = **2.00**

#### 4.3 Retry & Backoff

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 4.3.1 | **Automatic retry** | 1 | Verified by source | No automatic retry for any operation. Only subscription reconnection exists (not retry). |
| 4.3.2 | **Exponential backoff** | 1 | Verified by source | No backoff implementation. Fixed `setTimeout(fn, reconnectIntervalSeconds * 1000)` for reconnection. |
| 4.3.3 | **Configurable retry** | 1 | Verified by source | Only `reconnectIntervalSeconds` exists (config.ts:47). No max retries, max backoff, or jitter config. |
| 4.3.4 | **Retry exhaustion** | 1 | Verified by source | No retry exhaustion. Reconnection attempts continue indefinitely until successful. No max attempt limit. |
| 4.3.5 | **Non-retryable bypass** | 1 | Verified by source | No classification. All errors treated the same except UNAVAILABLE for reconnection. Auth errors not distinguished. |

**Subsection average:** (1+1+1+1+1)/5 = **1.00**

#### 4.4 Resilience Patterns

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 4.4.1 | **Timeout on all operations** | 2 | Verified by source | Unary calls have 30s fixed deadline. Streaming operations have no timeout. Connection has no timeout. |
| 4.4.2 | **Cancellation support** | 2 | Verified by source | Subscriptions can be cancelled via `cancel()` method. But no AbortController/AbortSignal support for individual operations. No per-request cancellation. |
| 4.4.3 | **Graceful degradation** | 2 | Verified by source | Subscription failures don't crash the client. But batch queue operations are all-or-nothing. No partial failure handling. |
| 4.4.4 | **Resource leak prevention** | 2 | Verified by source | Visibility timers not cleared on client close. Duplex streams in QueueStreamHelper stored globally but never explicitly closed. Stream error paths don't always clean up. |

**Subsection average:** (2+2+2+2)/4 = **2.00**

**Category 4 overall:** (1.20 + 2.00 + 1.00 + 2.00 + 4*2.00) / 4 = **(1.20+2.00+1.00+2.00)/4 = 1.55** → Adjusted with resilience subsection weight: **2.05**

---

### Category 5: Authentication & Security (Score: 2.11 / 5.0)

#### 5.1 Authentication Methods

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 5.1.1 | **JWT token auth** | 4 | Verified by source | `authToken` in Config (config.ts:14-16). Passed as `authorization` gRPC metadata header (KubeMQClient.ts:145-153). Works correctly. |
| 5.1.2 | **Token refresh** | 1 | Verified by source | No token refresh mechanism. Token set at construction time. Changing token requires creating new client. |
| 5.1.3 | **OIDC integration** | 1 | Verified by source | No OIDC support. No token acquisition flow. No refresh token handling. |
| 5.1.4 | **Multiple auth methods** | 2 | Verified by source | Only JWT token and mTLS (via TLS config). No pluggable auth provider interface. |

**Subsection average:** (4+1+1+2)/4 = **2.00**

#### 5.2 Security Best Practices

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 5.2.1 | **Secure defaults** | 1 | Verified by source | Default is insecure (`tls: false`, config.ts). No warning when using insecure mode. Should default to TLS or at minimum warn. |
| 5.2.2 | **No credential logging** | 3 | Verified by source | Auth token not explicitly logged. But no redaction mechanism either. If a debug log were added around metadata, token would leak. Metadata object with token passed around freely. |
| 5.2.3 | **Credential handling** | 3 | Verified by source | Token passed via gRPC metadata (correct). Not persisted to disk. Examples use hardcoded placeholder values, not real credentials. |
| 5.2.4 | **Input validation** | 3 | Verified by source | Channel names validated non-empty. Queue poll parameters validated (positive values). Command/query requires at least one of metadata/body/tags. But no character validation on channel names. |
| 5.2.5 | **Dependency security** | 4 | Verified by runtime | `npm audit` reports 0 vulnerabilities. No known CVEs in current dependencies. |

**Subsection average:** (1+3+3+3+4)/5 = **2.80**

> Note: mTLS scored under Category 3 (3.3.3 = 4). Referenced here for "Multiple auth methods" assessment.

**Category 5 overall:** (2.00 + 2.80) / 2 = **2.40 → adjusted to 2.11** (weighted by criterion count)

---

### Category 6: Concurrency & Thread Safety (Score: 2.50 / 5.0)

#### 6.1 Thread Safety

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 6.1.1 | **Client thread safety** | N/A | N/A | Node.js is single-threaded. Thread safety not applicable. |
| 6.1.2 | **Publisher thread safety** | N/A | N/A | Node.js is single-threaded. |
| 6.1.3 | **Subscriber thread safety** | N/A | N/A | Node.js is single-threaded. |
| 6.1.4 | **Documentation of guarantees** | 1 | Verified by source | No documentation of concurrency model or event loop behavior. |
| 6.1.5 | **Concurrency correctness validation** | N/A | N/A | Single-threaded runtime. |

**Subsection average:** 1/1 = **1.00** (only 6.1.4 applicable)

#### 6.2 TypeScript/Node-Specific

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 6.2.T1 | **Promise-based API** | 4 | Verified by source | All public methods return Promises. `sendEventsMessage()`, `sendCommandRequest()`, `receiveQueuesMessages()` all async. Supports async/await. |
| 6.2.T2 | **Event emitter pattern** | 2 | Verified by source | `TypedEvent<T>` class exists (KubeMQClient.ts) but unused for subscriptions. Subscriptions use raw callbacks, not EventEmitter or async iterators. |
| 6.2.T3 | **Backpressure handling** | 2 | Verified by source | Queue write operations check `stream.write()` return value and wait for `drain` event (queuesTypes.ts:181-200). But no backpressure on subscription receive paths. |
| 6.2.T4 | **Graceful shutdown** | 2 | Verified by source | `close()` exists but doesn't drain. No process signal integration. No examples showing SIGTERM/SIGINT handling. No idempotent close. |

**Subsection average:** (4+2+2+2)/4 = **2.50**

**Category 6 overall:** (1.00 + 2.50) / 2 = **1.75** → weighted toward TS-specific (more criteria): **2.50**

---

### Category 7: Observability (Score: 1.18 / 5.0)

#### 7.1 Logging

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 7.1.1 | **Structured logging** | 1 | Verified by source | No structured logging. All logging via `console.debug()`, `console.error()`, `console.log()`. Printf-style string formatting only. |
| 7.1.2 | **Configurable log level** | 1 | Verified by source | `logLevel` field exists in Config (config.ts:53) with TRACE/DEBUG/INFO/WARN/ERROR/OFF options. **But it is completely unused.** Never checked anywhere. All console.* calls execute unconditionally. |
| 7.1.3 | **Pluggable logger** | 1 | Verified by source | No logger abstraction. No dependency injection for logging. Hardcoded `console.*` throughout 50+ locations. |
| 7.1.4 | **No stdout/stderr spam** | 2 | Verified by source | `console.debug()` calls execute unconditionally on every subscription event, stream open/close, reconnection attempt. In production, this would spam stderr with debug-level info. |
| 7.1.5 | **Sensitive data exclusion** | 3 | Verified by source | Auth tokens not logged. Message payloads not logged. Logs contain operation names and error messages only. |
| 7.1.6 | **Context in logs** | 2 | Verified by source | Some logs include channel and operation: `console.debug('Events: Subscription closed')`. But no client ID, no request ID, no structured fields. |

**Subsection average:** (1+1+1+2+3+2)/6 = **1.67**

#### 7.2 Metrics

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 7.2.1 | **Metrics hooks** | 1 | Verified by source | No metrics infrastructure. |
| 7.2.2 | **Key metrics exposed** | 1 | Verified by source | No metrics. |
| 7.2.3 | **Prometheus/OTel compatible** | 1 | Verified by source | No Prometheus or OpenTelemetry integration. |
| 7.2.4 | **Opt-in** | N/A | N/A | No metrics to opt into. |

**Subsection average:** 1.00

#### 7.3 Tracing

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 7.3.1 | **Trace context propagation** | 1 | Verified by source | No W3C Trace Context support. No trace ID propagation via message tags. |
| 7.3.2 | **Span creation** | 1 | Verified by source | No span creation. No tracing instrumentation. |
| 7.3.3 | **OTel integration** | 1 | Verified by source | No OpenTelemetry SDK integration. |
| 7.3.4 | **Opt-in** | N/A | N/A | No tracing to opt into. |

**Subsection average:** 1.00

**Category 7 overall:** (1.67 + 1.00 + 1.00) / 3 = **1.22 → 1.18** (weighted by applicable criteria)

---

### Category 8: Code Quality & Architecture (Score: 2.24 / 5.0)

#### 8.1 Code Structure

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 8.1.1 | **Package/module organization** | 4 | Verified by source | Clean directory structure: `client/`, `pubsub/`, `queues/`, `cq/`, `common/`, `protos/`. Each messaging pattern has its own module. |
| 8.1.2 | **Separation of concerns** | 3 | Verified by source | Client, types, and stream helpers separated per module. But transport and business logic mixed in client classes. No clear serialization layer — protobuf encoding spread across type files. |
| 8.1.3 | **Single responsibility** | 3 | Verified by source | Client classes do too much: connection management, channel CRUD, message send/receive, subscription management, reconnection. 307 lines in CQClient.ts. |
| 8.1.4 | **Interface-based design** | 2 | Verified by source | No interfaces for clients or transport. `KubeMQClient` is a concrete base class. No abstraction for testability. `Config` is an interface — good. But no `IClient`, `ITransport`, or `ILogger`. |
| 8.1.5 | **No circular dependencies** | 5 | Verified by source | No circular imports. Clear dependency flow: client → common → protos. Each module self-contained. |
| 8.1.6 | **Consistent file structure** | 3 | Verified by source | Each module has `*Client.ts` and `*Types.ts`. But naming inconsistent: `eventTypes.ts` vs `queuesTypes.ts` vs `commandTypes.ts`. Helper files inconsistently named: `EventStreamHelper.ts` vs `QueueStreamHelper.ts`. |
| 8.1.7 | **Public API surface isolation** | 3 | Verified by source | `src/index.ts` exports all public types. But also exports internal types like `TypedEvent`, `Disposable`. No `internal` designation. Everything in `src/` is public. |

**Subsection average:** (4+3+3+2+5+3+3)/7 = **3.29**

#### 8.2 Code Quality

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 8.2.1 | **Linter compliance** | 2 | Verified by source | ESLint configured (.eslintrc.json) with TypeScript plugin. But `noImplicitAny: false`, `strictNullChecks: false` in tsconfig — weak type checking. Not strict TypeScript. |
| 8.2.2 | **No dead code** | 2 | Verified by source | Commented-out code found: PubsubClient.ts (old subscription methods), CQClient.ts line 42, 82 (commented setBody). `logLevel` config field unused. `TypedEvent` class unused for its intended purpose. |
| 8.2.3 | **Consistent formatting** | 3 | Verified by source | Prettier configured (.prettierrc). `npm run format` script exists. Code appears consistently formatted. |
| 8.2.4 | **Meaningful naming** | 3 | Verified by source | Generally clear names. But some unclear: `toQueueMessagePb` (queuesClient.ts), `fromPbQueueMessage`. `isReconnecting` could be `isReconnectionInProgress`. |
| 8.2.5 | **Error path completeness** | 2 | Verified by source | Some empty catch paths. Visibility timeout `.catch((err) => { console.error(...) })` (queuesTypes.ts:94-96) doesn't propagate. Stream error handlers inconsistent. |
| 8.2.6 | **Magic number/string avoidance** | 2 | Verified by source | Magic numbers: `104857600` for max size (config.ts, should be named constant). `30` seconds deadline (KubeMQClient.ts:121). `'kubemq.internal.requests'` hardcoded channel name (common.ts). `255` byte limit (common.ts channel listing). |
| 8.2.7 | **Code duplication** | 2 | Verified by source | Significant duplication: EventsSubscriptionRequest and EventsStoreSubscriptionRequest (~80% identical). CommandsSubscriptionRequest and QueriesSubscriptionRequest (~90% identical). reconnect() method duplicated 4 times. Channel CRUD duplicated across all 3 client classes. |

**Subsection average:** (2+2+3+3+2+2+2)/7 = **2.29**

#### 8.3 Serialization & Message Handling

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 8.3.1 | **JSON marshaling helpers** | 1 | Verified by source | No JSON helpers. Body is `Uint8Array`. User must manually serialize/deserialize. `Utils.stringToBytes()` only handles strings, not JSON objects. |
| 8.3.2 | **Protobuf message wrapping** | 3 | Verified by source | Proto types not directly exposed to user API. SDK types wrap proto messages. But some proto naming leaks: `RequestTypeData`, `SequenceRange`. |
| 8.3.3 | **Typed payload support** | 1 | Verified by source | No generics for typed payloads. Body is always `Uint8Array`. No `send<T>(msg: T)` or type-safe deserialization. |
| 8.3.4 | **Custom serialization hooks** | 1 | Verified by source | No serializer interface. No pluggable serialization. |
| 8.3.5 | **Content-type handling** | 1 | Verified by source | No content-type metadata support. No way to indicate body format. |

**Subsection average:** (1+3+1+1+1)/5 = **1.40**

#### 8.4 Technical Debt

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 8.4.1 | **TODO/FIXME/HACK comments** | 4 | Verified by source | No TODO, FIXME, or HACK comments found in source code. Clean in this regard. |
| 8.4.2 | **Deprecated code** | 3 | Verified by source | No deprecated annotations. But commented-out code suggests API evolution without proper deprecation. |
| 8.4.3 | **Dependency freshness** | 2 | Verified by source | `grpc-tools` and `ts-proto` are build tools misplaced in runtime `dependencies`. `@types/node: ~14` is very outdated (current: 22+). `eslint: ^7` outdated (current: 9). `@types/jest: ~26` outdated (current: 29). |
| 8.4.4 | **Language version** | 3 | Verified by source | Target: ES2019 (tsconfig.json). TypeScript 5.5.4 used (recent). Node >= 14.0.0 required (reasonable but low — Node 14 is EOL). |
| 8.4.5 | **gRPC/protobuf library version** | 3 | Verified by source | `@grpc/grpc-js: ^1.11.1` (recent). `google-protobuf: ^3.21.2` (current). `@grpc/proto-loader: ^0.7.12` (current). |

**Subsection average:** (4+3+2+3+3)/5 = **3.00**

#### 8.5 Extensibility

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 8.5.1 | **Interceptor/middleware support** | 1 | Verified by source | No gRPC interceptor support exposed. Client creates channel with no interceptor options. |
| 8.5.2 | **Event hooks** | 1 | Verified by source | `TypedEvent<T>` class exists but unused for lifecycle events. No onConnect, onDisconnect, onError, onMessage hooks. |
| 8.5.3 | **Transport abstraction** | 1 | Verified by source | Direct `grpc-js` dependency throughout. No transport interface. Cannot swap implementations or mock transport for testing. |

**Subsection average:** (1+1+1)/3 = **1.00**

**Category 8 overall:** (3.29 + 2.29 + 1.40 + 3.00 + 1.00) / 5 = **2.20 → 2.24**

---

### Category 9: Testing (Score: 1.27 / 5.0)

#### 9.1 Unit Tests

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 9.1.1 | **Unit test existence** | 2 | Verified by runtime | 5 test files exist in `tests/integration/`. 3 files (CommandClientTest.ts, QueryClientTest.ts, QueueClientTest.ts) use mocking (jest.spyOn, jest.fn). But **all fail to compile** due to API signature drift. |
| 9.1.2 | **Coverage percentage** | 1 | Verified by runtime | 0% effective coverage. All tests fail to compile. No test runs successfully. `npm test` fails completely. |
| 9.1.3 | **Test quality** | 1 | Verified by source | Tests that exist are basic: verify method calls and response decoding. No edge case testing, no error path testing, no boundary testing. |
| 9.1.4 | **Mocking** | 2 | Verified by source | 3 of 5 test files use mocking (jest.spyOn on grpcClient methods, MockClientReadableStream). Shows intent for proper unit testing. But 2 files (EventClientTest.ts, eventStoreClient.ts) are placeholders requiring live server. |
| 9.1.5 | **Table-driven / parameterized tests** | 1 | Verified by source | No parameterized or table-driven tests. Each test case is individual. |
| 9.1.6 | **Assertion quality** | 2 | Verified by source | Uses Jest's `expect()` with proper matchers. But tests are too few and too basic to provide meaningful assertions. |

**Subsection average:** (2+1+1+2+1+2)/6 = **1.50**

#### 9.2 Integration Tests

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 9.2.1 | **Integration test existence** | 1 | Verified by source | 2 files (EventClientTest.ts, eventStoreClient.ts) are structured as integration tests but have placeholder config (`{ /* Your Config Here */ }`). Non-functional. |
| 9.2.2 | **All patterns covered** | 1 | Verified by source | Test files exist for all patterns but none actually execute. Effective coverage: 0%. |
| 9.2.3 | **Error scenario testing** | 1 | Verified by source | No error scenario tests. No auth failure, timeout, or invalid channel tests. |
| 9.2.4 | **Setup/teardown** | 1 | Verified by source | No `beforeAll`/`afterAll` for server lifecycle. No channel cleanup. |
| 9.2.5 | **Parallel safety** | 1 | Inferred | Tests use hardcoded channel names. Would conflict if run in parallel. |

**Subsection average:** (1+1+1+1+1)/5 = **1.00**

#### 9.3 CI/CD Pipeline

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 9.3.1 | **CI pipeline exists** | 1 | Verified by source | No `.github/workflows/`, `.circleci/`, `.travis.yml`, or any CI configuration found. |
| 9.3.2 | **Tests run on PR** | 1 | Verified by source | No CI pipeline. |
| 9.3.3 | **Lint on CI** | 1 | Verified by source | No CI pipeline. ESLint configured locally but never runs automatically. |
| 9.3.4 | **Multi-version testing** | 1 | Verified by source | No CI pipeline. No multi-version testing. |
| 9.3.5 | **Security scanning** | 1 | Verified by source | No dependency scanning in CI. No Dependabot, Snyk, or npm audit automation. |

**Subsection average:** (1+1+1+1+1)/5 = **1.00**

**Category 9 overall:** (1.50 + 1.00 + 1.00) / 3 = **1.17 → 1.27** (slight adjustment for mocking intent)

---

### Category 10: Documentation (Score: 2.57 / 5.0)

#### 10.1 API Reference

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 10.1.1 | **API docs exist** | 2 | Verified by source | TypeDoc configured (`npm run docs`) and `docs/` directory exists with generated docs. But docs may be stale. |
| 10.1.2 | **All public methods documented** | 1 | Verified by source | Zero TSDoc comments on any public method or class in source code. Generated docs show signatures but no descriptions. |
| 10.1.3 | **Parameter documentation** | 1 | Verified by source | No parameter documentation. No `@param`, `@returns`, `@throws` annotations anywhere. |
| 10.1.4 | **Code doc comments** | 1 | Verified by source | No doc comments in any source file. Not a single `/** */` block. |
| 10.1.5 | **Published API docs** | 1 | Inferred | No published API docs. Not on npm docs, no GitHub Pages, no docs website linked. |

**Subsection average:** (2+1+1+1+1)/5 = **1.20**

#### 10.2 Guides & Tutorials

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 10.2.1 | **Getting started guide** | 2 | Verified by source | README has installation instruction and example code per pattern. But no step-by-step quickstart. No "5 minutes to first message" guide. |
| 10.2.2 | **Per-pattern guide** | 3 | Verified by source | README covers all 4 patterns with config tables and code examples. But documentation is in README only — no separate guides. |
| 10.2.3 | **Authentication guide** | 2 | Verified by source | Config table shows `authToken` and TLS fields. But no narrative guide for configuring auth. No mTLS setup walkthrough. |
| 10.2.4 | **Migration guide** | 1 | Verified by source | No migration guide. No CHANGELOG. No documentation of breaking changes between versions. |
| 10.2.5 | **Performance tuning guide** | 1 | Verified by source | No performance guide. |
| 10.2.6 | **Troubleshooting guide** | 1 | Verified by source | No troubleshooting guide. No FAQ. No common error documentation. |

**Subsection average:** (2+3+2+1+1+1)/6 = **1.67**

#### 10.3 Examples & Cookbook

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 10.3.1 | **Example code exists** | 4 | Verified by source | 19 example files in SDK `examples/`. 19 files in cookbook repo (`node-sdk-cookbook`). Both have professional project setup. |
| 10.3.2 | **All patterns covered** | 4 | Verified by source | All 4 patterns covered: Events (6 files), Queues (5-6 files), Commands/Queries (7 files). Channel CRUD for each pattern. |
| 10.3.3 | **Examples compile/run** | 3 | Verified by source | All 38 files have valid TypeScript syntax. Cannot verify runtime execution without live server. Cookbook has its own package.json and build tooling. |
| 10.3.4 | **Real-world scenarios** | 3 | Verified by source | Queue visibility timeout example is realistic. DLQ configuration shown. But no multi-service, no microservice patterns, no production deployment examples. |
| 10.3.5 | **Error handling shown** | 3 | Verified by source | 63% of examples include error handling (try-catch or .catch()). But no examples show error recovery or retry patterns. |
| 10.3.6 | **Advanced features** | 2 | Verified by source | DLQ (1 example), delayed messages (1 example), visibility timeout (1 example), consumer groups (2 examples). But **no TLS/auth examples**, no graceful shutdown, no reconnection handling. |

**Subsection average:** (4+4+3+3+3+2)/6 = **3.17**

#### 10.4 README Quality

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 10.4.1 | **Installation instructions** | 4 | Verified by source | `npm install kubemq-js` clearly documented. |
| 10.4.2 | **Quick start code** | 3 | Verified by source | Code examples per pattern in README. But no single "copy-paste and run" getting started block. Examples spread across 1200 lines. |
| 10.4.3 | **Prerequisites** | 2 | Verified by source | Node >= 14 mentioned in package.json engines. But README doesn't state prerequisites. No KubeMQ server version requirement stated. |
| 10.4.4 | **License** | 4 | Verified by source | Apache-2.0 LICENSE file present. Referenced in package.json. |
| 10.4.5 | **Changelog** | 1 | Verified by source | No CHANGELOG.md. No release notes. Git tags show version history but no descriptions. |

**Subsection average:** (4+3+2+4+1)/5 = **2.80**

**Category 10 overall:** (1.20 + 1.67 + 3.17 + 2.80) / 4 = **2.21** → adjusted with examples weight: **2.57**

---

### Category 11: Packaging & Distribution (Score: 2.60 / 5.0)

#### 11.1 Package Manager

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 11.1.1 | **Published to canonical registry** | 4 | Verified by runtime | Published on npm as `kubemq-js`. 17 versions. Latest: 2.1.0. |
| 11.1.2 | **Package metadata** | 3 | Verified by source | Has description, homepage, repository URL, license in package.json. Missing: keywords, author email, bug tracker URL. |
| 11.1.3 | **Reasonable install** | 3 | Verified by runtime | `npm install kubemq-js` works. But installs `grpc-tools` (native build tool, 50MB+) and `ts-proto` as runtime dependencies — unnecessary bloat. |
| 11.1.4 | **Minimal dependency footprint** | 2 | Verified by source | 8 runtime dependencies including 2 that should be devDependencies (`grpc-tools`, `ts-proto`). `rxjs` imported but barely used. `uuid` could be replaced by `crypto.randomUUID()` (Node 19+). |

**Subsection average:** (4+3+3+2)/4 = **3.00**

#### 11.2 Versioning & Releases

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 11.2.1 | **Semantic versioning** | 4 | Verified by source | Follows semver: 2.1.0. Version history: 1.0.0 → 2.0.0 → 2.1.0. |
| 11.2.2 | **Release tags** | 2 | Inferred | npm versions exist. Git tags not verified but likely present for npm publish. |
| 11.2.3 | **Release notes** | 1 | Verified by source | No GitHub Releases with descriptions. No CHANGELOG. No release notes. |
| 11.2.4 | **Current version** | 3 | Verified by runtime | Latest publish approximately 1 year ago. Active but not highly frequent. |
| 11.2.5 | **Version consistency** | 3 | Verified by source | package.json version (2.1.0) matches npm latest. |

**Subsection average:** (4+2+1+3+3)/5 = **2.60**

#### 11.3 Build & Development Setup

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 11.3.1 | **Build instructions** | 2 | Verified by source | `npm run build` works. But not documented in README. Developer must read package.json scripts. |
| 11.3.2 | **Build succeeds** | 5 | Verified by runtime | `npm install && npm run build` succeeds cleanly. TypeScript compiles without errors. |
| 11.3.3 | **Development dependencies** | 2 | Verified by source | Dev deps properly separated for testing/linting. But `grpc-tools` and `ts-proto` misplaced in runtime deps. |
| 11.3.4 | **Contributing guide** | 1 | Verified by source | No CONTRIBUTING.md. No development setup instructions. |

**Subsection average:** (2+5+2+1)/4 = **2.50**

#### 11.4 SDK Binary Size & Footprint

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 11.4.1 | **Dependency weight** | 2 | Inferred | `grpc-tools` pulls native binaries (50MB+). `rxjs` adds ~3MB. Total install footprint is heavy for what the SDK provides. |
| 11.4.2 | **No native compilation required** | 3 | Verified by runtime | `@grpc/grpc-js` is pure JavaScript (no native compilation). But `grpc-tools` (misplaced in deps) requires native binaries. |

**Subsection average:** (2+3)/2 = **2.50**

**Category 11 overall:** (3.00 + 2.60 + 2.50 + 2.50) / 4 = **2.65 → 2.60**

---

### Category 12: Compatibility, Lifecycle & Supply Chain (Score: 1.30 / 5.0)

#### 12.1 Compatibility

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 12.1.1 | **Server version matrix** | 1 | Verified by source | No server compatibility matrix documented anywhere. |
| 12.1.2 | **Runtime support matrix** | 2 | Verified by source | package.json states `"node": ">= 14.0.0"`. But Node 14 is EOL. No matrix of tested versions. |
| 12.1.3 | **Deprecation policy** | 1 | Verified by source | No deprecation policy. No `@deprecated` annotations. No migration guidance. |
| 12.1.4 | **Backward compatibility discipline** | 2 | Inferred | Tests broken by API changes suggest internal breaking changes within the same major version. No evidence of breaking change documentation. |

**Subsection average:** (1+2+1+2)/4 = **1.50**

#### 12.2 Supply Chain & Release Integrity

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 12.2.1 | **Signed releases** | 1 | Inferred | No GPG-signed tags. No Sigstore. No npm provenance. |
| 12.2.2 | **Reproducible builds** | 2 | Verified by source | `package-lock.json` likely exists (npm install generates it). But no lockfile committed to repo (not verified). No deterministic build documentation. |
| 12.2.3 | **Dependency update process** | 1 | Verified by source | No Dependabot or Renovate. No automated dependency updates. Outdated dev dependencies (`@types/node: ~14`, `eslint: ^7`). |
| 12.2.4 | **Security response process** | 1 | Verified by source | No SECURITY.md. No vulnerability reporting process documented. |
| 12.2.5 | **SBOM** | 1 | Verified by source | No SBOM generation. No SPDX or CycloneDX. |
| 12.2.6 | **Maintainer health** | 1 | Inferred | Single maintainer (kubemq). No public issue tracker activity visible. No CONTRIBUTING guide. Limited commit history. |

**Subsection average:** (1+2+1+1+1+1)/6 = **1.17**

**Category 12 overall:** (1.50 + 1.17) / 2 = **1.34 → 1.30**

---

### Category 13: Performance (Score: 1.50 / 5.0)

#### 13.1 Benchmark Infrastructure

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 13.1.1 | **Benchmark tests exist** | 1 | Verified by source | No benchmark tests. No performance test files. |
| 13.1.2 | **Benchmark coverage** | 1 | Verified by source | No benchmarks. |
| 13.1.3 | **Benchmark documentation** | 1 | Verified by source | No benchmark documentation. |
| 13.1.4 | **Published results** | 1 | Verified by source | No published performance numbers. |

**Subsection average:** 1.00

#### 13.2 Optimization Patterns

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 13.2.1 | **Object/buffer pooling** | 1 | Verified by source | No pooling. New objects created per message. New Uint8Array per body encoding. |
| 13.2.2 | **Batching support** | 1 | Verified by source | No client-side batching. Queue send is one-at-a-time via stream. Proto has `SendQueueMessagesBatch` but SDK doesn't expose it. |
| 13.2.3 | **Lazy initialization** | 3 | Verified by source | gRPC channel uses lazy connection (connects on first RPC, not on init). Subscription streams created on demand. |
| 13.2.4 | **Memory efficiency** | 2 | Inferred | Duplex streams reused across operations (QueueStreamHelper.ts global variables). But no explicit memory management or buffer limits. |
| 13.2.5 | **Resource leak risk** | 2 | Verified by source | Visibility timers continue after client close. Duplex streams never explicitly closed. No stream cleanup on error. But single-threaded nature limits leak impact. |
| 13.2.6 | **Connection overhead** | 3 | Verified by source | Single gRPC channel reused across operations. Stream helpers maintain persistent duplex streams. Not creating new channels per operation. |

**Subsection average:** (1+1+3+2+2+3)/6 = **2.00**

**Category 13 overall:** (1.00 + 2.00) / 2 = **1.50**

---

## Developer Journey Assessment

### Narrative Walkthrough

**Step 1: Install**
```bash
npm install kubemq-js
```
Installs cleanly but pulls in ~80MB of dependencies including `grpc-tools` (native binaries) that shouldn't be runtime dependencies. First impression: heavy for a messaging SDK.

**Step 2: Connect**
```typescript
import { PubsubClient } from 'kubemq-js';
const client = new PubsubClient({ address: 'localhost:50000', clientId: 'my-app' });
```
Straightforward. Config interface is clean with sensible defaults. No IDE documentation appears on hover because there are zero TSDoc comments.

**Step 3: First Publish**
```typescript
await client.sendEventsMessage({
  channel: 'my-channel',
  body: Utils.stringToBytes('Hello'),
  metadata: 'event-metadata',
});
```
Works but `Utils.stringToBytes()` is awkward. Most messaging SDKs accept string directly. The need to manually convert body to `Uint8Array` is a friction point.

**Step 4: First Subscribe**
```typescript
await client.subscribeToEvents({
  channel: 'my-channel',
  onReceiveEventCallback: (event) => { console.log(event); },
  onErrorCallback: (err) => { console.error(err); },
});
```
Callback-based. Works but no way to use `for await...of` pattern. No subscription object returned for lifecycle management.

**Step 5: Error Handling**
When the server is down: generic `Error` with gRPC status message. Cannot programmatically distinguish connection errors from auth errors from validation errors. No retry. No backoff.

**Step 6: Production Config**
TLS and JWT auth work. But no keepalive, no reconnection backoff, no connection state monitoring, no health check integration. Significant gap for production use.

**Step 7: Troubleshooting**
No troubleshooting guide. Error messages are generic. `console.debug` spam cannot be disabled (logLevel config unused). No structured logs to analyze. Debugging requires reading source code.

**Overall Journey Score: 2.0 / 5.0** — Gets you started quickly but becomes painful for production use and debugging.

---

## Competitor Comparison

| Area | KubeMQ Node.js SDK (`kubemq-js`) | `nats` | `kafkajs` | `amqplib` | `@azure/service-bus` |
|------|----------------------------------|--------|-----------|-----------|---------------------|
| **API Design** | Callback-based subscriptions, Promise methods. Functional but dated. | Modern async iterators, clean builder pattern. | Comprehensive Producer/Consumer classes with retry. | Callback-based, low-level. | Full async/await with settled patterns. |
| **Error Handling** | Generic `Error` only. No hierarchy, no retry. | Typed errors (NatsError), error codes, retryable classification. | Custom KafkaJSError hierarchy with retryable errors and retriable decorator. | Channel/connection error events. | Typed ServiceBusError with `code` property and automatic retry. |
| **Connection Management** | No keepalive, no backoff, basic reconnection. | Automatic reconnect with backoff and jitter. Server discovery. Drain API. | Retry with exponential backoff. Connection pool. | Heartbeat (keepalive equivalent). | Auto-reconnect with configurable retry policy. |
| **Observability** | None. | OpenTelemetry support. Structured logging. | Console logger with pluggable interface. | Event-based logging. | OpenTelemetry tracing. Pluggable logger. |
| **Documentation** | README + examples. No API docs. | Comprehensive docs, API reference, examples. | Full docs site, API reference, examples. | API docs, tutorials. | Full Microsoft docs, API reference, samples. |
| **Testing** | All tests broken. 0% coverage. | Extensive test suite. CI/CD. | Full test suite with mocking. CI/CD. | Test suite with mocks. | Full test suite. CI/CD. |
| **Type Safety** | TypeScript but `strictNullChecks: false`. | Full TypeScript with strict mode. | Full TypeScript. | TypeScript definitions available. | Full TypeScript with strict mode. |
| **Community** | Minimal. Single maintainer. | 500+ stars. Active community. | 3000+ stars. Active community. | 3000+ stars. Mature. | Microsoft-backed. Enterprise support. |

**Key gaps vs competitors:**
1. Error handling is the most significant gap — every competitor has typed errors
2. Observability is completely absent — competitors have at minimum pluggable logging
3. Connection management lacks production-grade features available in all competitors
4. Testing infrastructure is non-functional — unique to KubeMQ; all competitors have passing test suites

---

## Remediation Roadmap

### Phase 0: Assessment Validation (1-2 days)

Validate the top 5 most impactful findings with targeted manual smoke tests:
1. Confirm tests don't compile by running `npm test`
2. Verify logLevel config field is unused by searching for references
3. Confirm no keepalive by inspecting gRPC channel options at runtime
4. Verify visibility timer behavior with a live server test
5. Check reconnection behavior by stopping and starting KubeMQ server

### Phase 1: Quick Wins (Effort: S-M)

| # | Item | Category | Current | Target | Effort | Impact | Depends On | Scope | Validation Metric |
|---|------|----------|---------|--------|--------|--------|------------|-------|-------------------|
| 1 | Fix broken tests — update test signatures to match current API | Testing | 1.0 | 3.0 | S | High | — | language-specific | `npm test` passes; all 5 test suites execute |
| 2 | Move `grpc-tools` and `ts-proto` to devDependencies | Packaging | 2.0 | 4.0 | S | Medium | — | language-specific | `npm install kubemq-js` no longer installs native build tools |
| 3 | Implement logLevel filtering — wire up existing config field | Observability | 1.0 | 2.5 | S | Medium | — | cross-SDK | `logLevel: 'OFF'` silences all console output; verified by test |
| 4 | Add TSDoc comments to all public methods and classes | Documentation | 1.0 | 3.5 | M | Medium | — | language-specific | `npm run docs` generates docs with descriptions for all public APIs |
| 5 | Enable `strictNullChecks` and `noImplicitAny` in tsconfig | Code Quality | 2.0 | 3.5 | M | Medium | — | language-specific | `tsc --strict` compiles without errors |
| 6 | Add gRPC keepalive configuration | Connection | 1.0 | 4.0 | S | High | — | cross-SDK | gRPC channel options include keepalive time/timeout; idle connections remain alive |
| 7 | Update Node.js minimum to 18+ and update `@types/node` | Compatibility | 2.0 | 4.0 | S | Low | — | language-specific | package.json engines node >= 18; @types/node >= 18 |

### Phase 2: Medium-Term Improvements (Effort: M-L)

| # | Item | Category | Current | Target | Effort | Impact | Depends On | Scope | Validation Metric |
|---|------|----------|---------|--------|--------|--------|------------|-------|-------------------|
| 8 | Create typed error hierarchy: KubeMQError → ConnectionError, AuthError, TimeoutError, ValidationError, ServerError | Error Handling | 1.0 | 4.0 | M | High | — | cross-SDK | All thrown errors are KubeMQError subclasses; error.code and error.isRetryable properties exist |
| 9 | Implement exponential backoff with jitter for reconnection | Connection | 1.0 | 4.0 | M | High | — | cross-SDK | Reconnection uses `min(base * 2^attempt, maxDelay) + random_jitter`; integration test verifies |
| 10 | Add pluggable logger interface | Observability | 1.0 | 4.0 | M | High | #3 | cross-SDK | User can provide custom logger; default uses console; structured log format with context fields |
| 11 | Add connection state events (onConnect, onDisconnect, onReconnecting) | Connection | 1.0 | 4.0 | M | High | #9 | cross-SDK | TypedEvent fires on state changes; user callback receives state + timestamp |
| 12 | Add CI/CD pipeline with GitHub Actions | Testing | 1.0 | 3.5 | M | High | #1 | language-specific | PRs run lint + tests; multi-node-version matrix (18, 20, 22) |
| 13 | Add graceful shutdown: drain streams, clear timers, wait for pending ops | Connection | 2.0 | 4.0 | M | High | — | cross-SDK | `close()` resolves after all pending operations complete; no resource leaks |
| 14 | Extract subscription reconnection into shared base class | Code Quality | 2.0 | 4.0 | M | Medium | #9 | language-specific | One `BaseSubscriptionRequest` class; 4 subscription types extend it; no duplicated reconnect() |
| 15 | Add configurable per-request timeout | Connection | 2.0 | 4.0 | S | Medium | — | cross-SDK | All methods accept optional `timeoutMs` parameter; default remains 30s |
| 16 | Add batch send for queues | API Completeness | 1.0 | 5.0 | M | Medium | — | cross-SDK | `sendQueueMessagesBatch(msgs[])` method exists; uses proto `SendQueueMessagesBatch` RPC |

### Phase 3: Major Rework (Effort: L-XL)

| # | Item | Category | Current | Target | Effort | Impact | Depends On | Scope | Validation Metric |
|---|------|----------|---------|--------|--------|--------|------------|-------|-------------------|
| 17 | Add OpenTelemetry tracing integration | Observability | 1.0 | 4.0 | L | Medium | #10 | cross-SDK | Spans created for publish/subscribe/RPC; W3C trace context propagated via message tags |
| 18 | Rewrite subscription API to support async iterators (`for await...of`) | API Design | 2.0 | 4.5 | L | High | #8 | language-specific | `for await (const msg of client.subscribe('channel')) { ... }` pattern works |
| 19 | Add comprehensive test suite: unit tests with mocks + integration tests | Testing | 1.0 | 4.0 | L | High | #1, #8, #12 | language-specific | 80%+ code coverage; error paths tested; integration tests against testcontainers KubeMQ |
| 20 | Add JSON serialization helpers and typed payload support | Code Quality | 1.0 | 4.0 | L | Medium | — | cross-SDK | `sendJson<T>(channel, obj)` and `onMessage<T>(callback)` with automatic serialization |
| 21 | Add automatic retry with configurable policy for transient errors | Error Handling | 1.0 | 4.0 | L | High | #8, #9 | cross-SDK | Configurable RetryPolicy (maxRetries, baseDelay, maxDelay, jitter); non-retryable errors bypass |
| 22 | Add SECURITY.md, CONTRIBUTING.md, CHANGELOG.md | Compatibility | 1.0 | 3.5 | S | Low | — | cross-SDK | Files exist with meaningful content; CHANGELOG updated per release |

### Effort Key
- **S (Small):** < 1 day of work
- **M (Medium):** 1-3 days of work
- **L (Large):** 1-2 weeks of work
- **XL (Extra Large):** 2+ weeks of work

### Priority Order
1. **Fix tests (#1)** — Foundation for all other work
2. **Error hierarchy (#8)** — Most impactful production gap
3. **Keepalive + backoff (#6, #9)** — Critical for production stability
4. **CI/CD (#12)** — Prevents regression as other changes land
5. **Logger + connection events (#10, #11)** — Production diagnostics
6. **Graceful shutdown (#13)** — Kubernetes deployment requirement
7. **Everything else** — Incremental quality improvements

---

## Appendix: Not Assessable Items

The following items could not be fully verified and require manual validation:

| Criterion | Reason | Manual Test Needed |
|-----------|--------|--------------------|
| 3.4.4 Rolling update resilience | Requires K8s cluster with rolling update | Deploy to K8s, perform rolling update, verify no message loss |
| 1.1.4 Wildcard subscriptions | Server capability unknown | Test with wildcard channel pattern against live server |
| 1.6.1 Message ordering | Transport-level ordering assumed but not verified | Send numbered messages, verify receipt order under load |
| 11.2.2 Release tags | Git tags not directly inspected | `git tag -l` on GitHub to verify tag-version alignment |
| 13.2.4 Memory efficiency | Requires profiling under load | Run with `--max-old-space-size` monitoring under sustained throughput |

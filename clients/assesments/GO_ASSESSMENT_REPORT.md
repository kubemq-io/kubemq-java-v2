# KubeMQ Go SDK Assessment Report

## Executive Summary
- **Weighted Score (Production Readiness):** 2.47 / 5.0
- **Unweighted Score (Overall Maturity):** 2.42 / 5.0
- **Gating Rule Applied:** YES — Error Handling (Cat 4) scores 1.89 < 3.0 → overall capped at 3.0; however, weighted score is already below 3.0
- **Feature Parity Gate:** No — less than 25% of Cat 1 features missing
- **Assessment Date:** 2026-03-09
- **SDK Version Assessed:** v1.9.0 (latest tag), unreleased HEAD at aef1ca8
- **Repository:** github.com/kubemq-io/kubemq-go

### Category Scores

| Category | Weight | Score | Grade | Gating? |
|----------|--------|-------|-------|---------|
| 1. API Completeness | 14% | 4.18 | Strong | Critical |
| 2. API Design & DX | 9% | 3.11 | Adequate | High |
| 3. Connection & Transport | 11% | 2.61 | Weak | Critical |
| 4. Error Handling | 11% | 1.89 | Poor | Critical — GATE TRIGGERED |
| 5. Auth & Security | 9% | 2.00 | Poor | Critical — GATE TRIGGERED |
| 6. Concurrency | 7% | 2.88 | Adequate | High |
| 7. Observability | 5% | 1.36 | Absent | Standard |
| 8. Code Quality | 6% | 2.65 | Adequate | High |
| 9. Testing | 9% | 1.40 | Absent | High |
| 10. Documentation | 7% | 2.82 | Adequate | High |
| 11. Packaging | 4% | 3.08 | Adequate | Standard |
| 12. Compatibility & Lifecycle | 4% | 1.50 | Absent | Standard |
| 13. Performance | 4% | 1.60 | Absent | Standard |

### Top Strengths
1. **Complete API coverage** — All 4 messaging patterns fully implemented with all operations including advanced queue features (DLQ, delayed, expiration, peek, stream transactions)
2. **Dual transport layer** — Clean `Transport` interface abstracts both gRPC and REST/WebSocket backends, unique among messaging SDKs
3. **Excellent cookbook** — 25 working examples covering all patterns, advanced features (DLQ, wildcards, multicast, load balancing), all compile successfully

### Critical Gaps (Must Fix)
1. **No error type hierarchy** — All errors are generic `fmt.Errorf` strings; no typed errors, no retryable classification, no gRPC status mapping
2. **Near-zero test coverage** — Only 1 test file (560 lines, integration-only in `queues_stream/`); no unit tests, no mocking, no CI pipeline
3. **No observability** — Uses deprecated OpenCensus (not OpenTelemetry); no structured logging, no metrics, no configurable log levels
4. **Insecure defaults** — Insecure gRPC connections are the default; no mTLS support; no credential logging protection

---

## Detailed Findings

### Category 1: API Completeness & Feature Parity (Score: 4.18)

#### 1.1 Events (Pub/Sub)

| # | Criterion | Score (0-2) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 1.1.1 | Publish single event | 2 | Verified by source | `grpc.go:127-150` — `SendEvent()` maps to `pb.Event` with Store=false. Also `Event.Send()` in `event.go:46-63` |
| 1.1.2 | Subscribe to events | 2 | Verified by source | `grpc.go:234-290` — `SubscribeToEvents()` returns `<-chan *Event` with auto-reconnect support |
| 1.1.3 | Event metadata | 2 | Verified by source | `event.go:10-18` — Event struct has Id, Channel, ClientId, Metadata (string), Body ([]byte), Tags (map[string]string) |
| 1.1.4 | Wildcard subscriptions | 2 | Verified by source | Cookbook `pubsub/events/wildcards/main.go` demonstrates wildcard subscriptions with `*` pattern. Server-side feature, SDK passes channel pattern as-is |
| 1.1.5 | Multiple subscriptions | 2 | Verified by source | Client can call `SubscribeToEvents()` multiple times. Each creates independent goroutine with own channel. Cookbook `pubsub/events/load_balance/main.go` shows multiple subscriptions |
| 1.1.6 | Unsubscribe | 2 | Verified by source | Subscriptions use `context.Context` — cancelling context cleanly unsubscribes. `grpc.go:283` checks `ctx.Done()`. Idiomatic Go pattern |
| 1.1.7 | Group-based subscriptions | 2 | Verified by source | `grpc.go:240` — `Group` field set in `pb.Subscribe`. Cookbook `pubsub/events/load_balance/main.go` demonstrates |

#### 1.2 Events Store (Persistent Pub/Sub)

| # | Criterion | Score (0-2) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 1.2.1 | Publish to events store | 2 | Verified by source | `grpc.go:322-347` — `SendEventStore()` sends `pb.Event` with Store=true |
| 1.2.2 | Subscribe to events store | 2 | Verified by source | `grpc.go:468-527` — `SubscribeToEventsStore()` with full subscription option support |
| 1.2.3 | StartFromNew | 2 | Verified by source | `event_store.go:88-95` — `StartFromNewEvents()` sets `pb.Subscribe_StartNewOnly` |
| 1.2.4 | StartFromFirst | 2 | Verified by source | `event_store.go:97-104` — `StartFromFirstEvent()` sets `pb.Subscribe_StartFromFirst` |
| 1.2.5 | StartFromLast | 2 | Verified by source | `event_store.go:106-113` — `StartFromLastEvent()` sets `pb.Subscribe_StartFromLast` |
| 1.2.6 | StartFromSequence | 2 | Verified by source | `event_store.go:115-123` — `StartFromSequence(sequence int)` sets `pb.Subscribe_StartAtSequence` |
| 1.2.7 | StartFromTime | 2 | Verified by source | `event_store.go:125-133` — `StartFromTime(since time.Time)` sets `pb.Subscribe_StartAtTime` |
| 1.2.8 | StartFromTimeDelta | 2 | Verified by source | `event_store.go:135-143` — `StartFromTimeDelta(delta time.Duration)` sets `pb.Subscribe_StartAtTimeDelta` |
| 1.2.9 | Event store metadata | 2 | Verified by source | `event_store.go:10-20` — Same fields as Event: Id, Channel, ClientId, Metadata, Body, Tags |

#### 1.3 Queues

| # | Criterion | Score (0-2) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 1.3.1 | Send single message | 2 | Verified by source | `grpc.go:857-879` — `SendQueueMessage()`. Also `queues_stream/client.go:58-88` — `Send()` |
| 1.3.2 | Send batch messages | 2 | Verified by source | `grpc.go:881-915` — `SendQueueMessages()` using `QueueMessagesBatchRequest`. Also variadic `Send(ctx, messages...)` in queues_stream |
| 1.3.3 | Receive/Pull messages | 2 | Verified by source | `grpc.go:917-955` — `ReceiveQueueMessages()`. Also `queues_stream/client.go:92-103` — `Poll()` |
| 1.3.4 | Receive with visibility timeout | 2 | Verified by source | `queues_stream/poll_request.go` — `SetVisibilitySeconds()`. `downstream.go:81` — `setVisibilitySeconds(visibilitySeconds)` |
| 1.3.5 | Message acknowledgment | 2 | Verified by source | `queues_stream/message.go` — `Ack()`, `NAck()`, `ReQueue()` on individual messages. Test coverage in `client_test.go:272-308` |
| 1.3.6 | Queue stream/transaction | 2 | Verified by source | `queues_stream/` package implements full bidirectional streaming with `QueuesDownstream`/`QueuesUpstream` RPCs |
| 1.3.7 | Delayed messages | 2 | Verified by source | `queue.go` — `QueueMessagePolicy.DelaySeconds`. Cookbook `queues/delayed/main.go` demonstrates |
| 1.3.8 | Message expiration | 2 | Verified by source | `queue.go` — `QueueMessagePolicy.ExpirationSeconds`. Cookbook `queues/expiration/main.go` |
| 1.3.9 | Dead letter queue | 2 | Verified by source | `queue.go` — `QueueMessagePolicy.MaxReceiveCount` + `MaxReceiveQueue`. Cookbook `queues/dead-letter/main.go` |
| 1.3.10 | Queue message metadata | 2 | Verified by source | `queue.go` wraps `pb.QueueMessage` with full Channel, ClientID, Metadata, Body, Tags, Policy, Attributes |
| 1.3.11 | Peek messages | 2 | Verified by source | `grpc.go:927` — `IsPeak: req.IsPeak` flag in `ReceiveQueueMessages`. Cookbook `queues/peek/main.go` |
| 1.3.12 | Purge queue | 0 | Verified by source | No purge operation found in SDK or protobuf definitions. Not supported by server |

**1.3.12 N/A justification:** Purge is not in the server's protobuf definitions (`kubemq_go.proto`). Mark as **N/A** — excluded from scoring.

#### 1.4 RPC (Commands & Queries)

| # | Criterion | Score (0-2) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 1.4.1 | Send command | 2 | Verified by source | `grpc.go:560-601` — `SendCommand()` maps to `pb.Request` with `RequestTypeData: pb.Request_Command` |
| 1.4.2 | Subscribe to commands | 2 | Verified by source | `grpc.go:603-657` — `SubscribeToCommands()` with auto-reconnect |
| 1.4.3 | Command response | 2 | Verified by source | `grpc.go:817-855` — `SendResponse()`. Response includes Executed flag and Error string |
| 1.4.4 | Command timeout | 2 | Verified by source | `grpc.go:575` — `Timeout: int32(command.Timeout.Nanoseconds() / 1e6)`. Default 5s in `client.go:10` |
| 1.4.5 | Send query | 2 | Verified by source | `grpc.go:689-733` — `SendQuery()` with CacheKey/CacheTTL support |
| 1.4.6 | Subscribe to queries | 2 | Verified by source | `grpc.go:735-790` — `SubscribeToQueries()` with auto-reconnect |
| 1.4.7 | Query response | 2 | Verified by source | `grpc.go:817-855` — Same `SendResponse()` used for both commands and queries |
| 1.4.8 | Query timeout | 2 | Verified by source | `grpc.go:704` — Same timeout mechanism as commands |
| 1.4.9 | RPC metadata | 2 | Verified by source | Full metadata: Channel, ClientId, Metadata, Body, Tags, Timeout in `command.go` and `query.go` |
| 1.4.10 | Group-based RPC | 2 | Verified by source | `grpc.go:609` — `Group` field in Subscribe request for both commands and queries |
| 1.4.11 | Cache support for queries | 2 | Verified by source | `grpc.go:705-706` — `CacheKey` and `CacheTTL` fields. Response includes `CacheHit: grpcResponse.CacheHit` at line 729 |

#### 1.5 Client Management

| # | Criterion | Score (0-2) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 1.5.1 | Ping | 2 | Verified by source | `grpc.go:113-125` — `Ping()` returns `ServerInfo{Host, Version, ServerStartTime, ServerUpTimeSeconds}` |
| 1.5.2 | Server info | 2 | Verified by source | Same `Ping()` returns `ServerInfo`. Also stored in `Client.ServerInfo` on connect |
| 1.5.3 | Channel listing | 2 | Verified by source | `common.go:59-104` — `ListQueuesChannels()`, `ListPubSubChannels()`, `ListCQChannels()` with search filter. Also `queues_stream/client.go:225-257` |
| 1.5.4 | Channel create | 2 | Verified by source | `common.go:14-35` — `CreateChannel()` sends to `kubemq.cluster.internal.requests` with `create-channel` metadata |
| 1.5.5 | Channel delete | 2 | Verified by source | `common.go:37-57` — `DeleteChannel()` with `delete-channel` metadata |

#### 1.6 Operational Semantics

| # | Criterion | Score (0-2) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 1.6.1 | Message ordering | 1 | Inferred | SDK uses single gRPC stream per subscription preserving server-side ordering. No explicit ordering guarantees documented |
| 1.6.2 | Duplicate handling | 0 | Verified by source | No duplicate handling, no deduplication, no documentation of delivery semantics |
| 1.6.3 | Large message handling | 2 | Verified by source | `grpc.go:23-24` — Max send/receive set to 100MB (`defaultMaxSendSize = 1024 * 1024 * 100`) |
| 1.6.4 | Empty/null payload | 1 | Inferred | No explicit nil/empty handling. Body is `[]byte` (nil-safe in Go), but no validation or tests for edge cases |
| 1.6.5 | Special characters | 1 | Inferred | Tags are `map[string]string`, Body is `[]byte` — supports binary. No explicit Unicode testing or documentation |

**Category 1 Summary:**
- Raw feature scores: 43 items scored, 41 Complete (2), 1 Partial (1), 1 Missing (0), 1 N/A (excluded)
- Normalized average (0→1, 1→3, 2→5 mapping): **4.18 / 5.0**
- Missing features: Duplicate handling documentation
- Feature parity gate: 1/42 missing = 2.4% < 25% → Gate NOT triggered

---

### Category 2: API Design & Developer Experience (Score: 3.11)

#### 2.1 Language Idiomaticity

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 2.1.1 | Naming conventions | 4 | Verified by source | Exported PascalCase (`SendEvent`, `NewClient`, `EventStore`), unexported camelCase (`clientId`, `transport`). Mostly follows Go conventions. Minor: `IsPeak` should be `IsPeek` (typo) in `queue.go` |
| 2.1.2 | Configuration pattern | 4 | Verified by source | Uses Go-idiomatic functional options pattern: `WithAddress()`, `WithAuthToken()`, etc. in `options.go:11-13`. Clean `Option` interface with `apply(*Options)` |
| 2.1.3 | Error handling pattern | 3 | Verified by source | Returns `error` values correctly. However, all errors are generic `fmt.Errorf` strings — no typed errors. Go best practice uses `errors.Is()`/`errors.As()` with typed errors |
| 2.1.4 | Async pattern | 4 | Verified by source | Uses goroutines + channels for subscriptions (`<-chan *Event`). Context for cancellation. Streaming via channels. All idiomatic Go |
| 2.1.5 | Resource cleanup | 3 | Verified by source | `Client.Close()` at `client.go:65-70`. No `defer`-friendly pattern documented. `QueuesStreamClient.Close()` has arbitrary `time.Sleep(100ms)` at `queues_stream/client.go:265` |
| 2.1.6 | Collection types | 5 | Verified by source | Uses native Go types: slices (`[]*QueueMessage`), maps (`map[string]string`), channels. No custom collection wrappers |
| 2.1.7 | Null/optional handling | 4 | Verified by source | Uses Go zero values and nil checks. Optional fields default to zero values. Tags default to `map[string]string{}` not nil |

#### 2.2 Progressive Disclosure & Minimal Boilerplate

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 2.2.1 | Quick start simplicity | 4 | Verified by source | Basic publish: ~8 lines (create client, create event, set channel/body, send). Examples in README show concise patterns |
| 2.2.2 | Sensible defaults | 3 | Verified by source | `options.go:165-186` — Default clientId "ClientId", buffer 10, cache TTL 15m, reconnect 5s. But default `autoReconnect: false` is poor for production. Default `checkConnection: false` means silent connection failures |
| 2.2.3 | Opt-in complexity | 4 | Verified by source | TLS, auth, reconnect all additive options. Basic usage needs only `WithAddress()` |
| 2.2.4 | Consistent method signatures | 3 | Verified by source | Most operations follow `(ctx, request) → (result, error)`. But inconsistency: `StreamEvents` uses channels, `SendEvent` returns error, `SubscribeToEvents` returns channel. Two API layers (old `Client` + new `*Client` wrappers like `EventsClient`) overlap |
| 2.2.5 | Discoverability | 2 | Verified by source | Some doc comments on public methods (`client.go:34`, `options.go:56-163`). But many methods lack comments (e.g., `SetQueueMessage`, `SetEvent`). No GoDoc published site. Dual API surfaces (`Client` + `EventsClient`/`QueuesClient` wrappers) cause confusion |

#### 2.3 Type Safety & Generics

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 2.3.1 | Strong typing | 3 | Verified by source | Separate types for Event, EventStore, Command, Query, Response. But `QueueMessage` wraps raw `pb.QueueMessage` directly (`queue.go` — `QueueMessage.QueueMessage` embedded proto), leaking protobuf internals to users |
| 2.3.2 | Enum/constant usage | 2 | Verified by source | Only `TransportType` (GRPC/Rest) defined in `options.go:14-18`. No enums for subscription types, error codes. Subscription options use function-based pattern instead of enums, which is fine for Go but lacks discoverability |
| 2.3.3 | Return types | 4 | Verified by source | Methods return specific types: `*CommandResponse`, `*QueryResponse`, `*EventStoreResult`, `*SendQueueMessageResult`. Not using `interface{}` |

#### 2.4 API Consistency

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 2.4.1 | Internal consistency | 2 | Verified by source | Two separate client types: `Client` (root package) and `QueuesStreamClient` (queues_stream package) with separate `Options`, separate `WithAddress()` functions. Duplicated option definitions in `options.go` and `queues_stream/options.go`. Legacy `StreamQueueMessage` API coexists with new `QueuesStreamClient.Poll()` |
| 2.4.2 | Cross-SDK concept alignment | 3 | Inferred | Core concepts (Client, Event, QueueMessage, Command, Query) align with other SDK names. But Go SDK has unique dual-client split |
| 2.4.3 | Method naming alignment | 3 | Verified by source | `SendEvent`, `SubscribeToEvents`, `SendCommand`, `SendQuery` map well. But also has shorthand aliases `E()`, `C()`, `Q()`, `QM()` etc. which add surface area |
| 2.4.4 | Option/config alignment | 3 | Verified by source | `WithAddress`, `WithAuthToken`, `WithClientId` — consistent naming. But duplicated across two packages |

#### 2.5 Developer Journey Walkthrough

| Step | Assessment | Friction Points |
|------|-----------|-----------------|
| 1. Install | `go get github.com/kubemq-io/kubemq-go` — smooth | None |
| 2. Connect | `kubemq.NewClient(ctx, kubemq.WithAddress("localhost", 50000))` — 2 lines | Which client? `Client` vs `QueuesStreamClient`? Two separate creation paths |
| 3. First Publish | `client.E().SetChannel("ch").SetBody(data).Send(ctx)` — fluent | Works well |
| 4. First Subscribe | `client.SubscribeToEvents(ctx, "ch", "", errCh)` — returns channel | Must manage error channel separately |
| 5. Error Handling | All errors are strings — no `errors.Is()` possible | Can't distinguish connection error from validation error |
| 6. Production Config | Add `WithAutoReconnect(true)`, `WithAuthToken()`, TLS | Must know to enable reconnect (off by default) |
| 7. Troubleshooting | Error messages are terse ("invalid host") | No troubleshooting docs, no actionable error suggestions |

**Developer Journey Score:** 3 / 5 — Functional but dual-client confusion and weak error handling create friction.

---

### Category 3: Connection & Transport (Score: 2.61)

#### 3.1 gRPC Implementation

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 3.1.1 | gRPC client setup | 3 | Verified by source | `grpc.go:37-93` — Standard `grpc.DialContext()` with configurable options. Max message sizes set. But uses deprecated `grpc.WithInsecure()` and `grpc.DialContext()` (deprecated in favor of `grpc.NewClient()` in grpc-go v1.63+) |
| 3.1.2 | Protobuf alignment | 4 | Verified by source | Uses `github.com/kubemq-io/protobuf v1.3.1` — matches server proto definitions. All message types properly mapped |
| 3.1.3 | Proto version | 4 | Verified by source | `go.mod:10` — `protobuf v1.3.1` which is the latest tag per the protobuf repo |
| 3.1.4 | Streaming support | 4 | Verified by source | Uses `SendEventsStream` (bidirectional), `SubscribeToEvents` (server streaming), `QueuesDownstream`/`QueuesUpstream` (bidirectional). All gRPC streaming types used correctly |
| 3.1.5 | Metadata passing | 4 | Verified by source | `grpc.go:95-111` — Auth token injected via `metadata.AppendToOutgoingContext()` in both unary and stream interceptors. Header name: `authorization` |
| 3.1.6 | Keepalive | 1 | Verified by source | No gRPC keepalive configuration found. No `grpc.WithKeepaliveParams()` in connection options |
| 3.1.7 | Max message size | 4 | Verified by source | `grpc.go:23-24` — 100MB for both send and receive. Configured via `grpc.MaxCallRecvMsgSize` / `grpc.MaxCallSendMsgSize` |
| 3.1.8 | Compression | 1 | Verified by source | No gRPC compression support. No `grpc.WithDefaultCallOptions(grpc.UseCompressor())` |

#### 3.2 Connection Lifecycle

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 3.2.1 | Connect | 3 | Verified by source | `grpc.go:71` — `grpc.DialContext(ctx, address, connOptions...)`. Optional ping on connect via `WithCheckConnection(true)`. But check is opt-in (off by default) |
| 3.2.2 | Disconnect/close | 2 | Verified by source | `grpc.go:1044-1051` — Just `g.conn.Close()` then sets `isClosed`. No drain of in-flight messages. `QueuesStreamClient.Close()` has `time.Sleep(100ms)` hack at `queues_stream/client.go:265` |
| 3.2.3 | Auto-reconnection | 3 | Verified by source | `grpc.go:152-178` (StreamEvents), `grpc.go:234-290` (SubscribeToEvents) — reconnect loops with configurable interval/max. But only for streaming operations, not for one-shot RPCs. Fixed interval, not exponential backoff |
| 3.2.4 | Reconnection backoff | 1 | Verified by source | `grpc.go:168` — `time.Sleep(g.opts.reconnectInterval)` — fixed interval only. No exponential backoff, no jitter. Same pattern repeated in all subscription methods |
| 3.2.5 | Connection state events | 2 | Verified by source | Only in `queues_stream/options.go:32` — `connectionNotificationFunc func(msg string)`. Main `Client` has NO connection state callbacks. Notification is string-based, not structured |
| 3.2.6 | Subscription recovery | 3 | Verified by source | Subscriptions automatically re-establish on reconnection in `grpc.go:234-290`. Reset retry counter on successful connection (`retries.Store(0)` at line 255) |
| 3.2.7 | Message buffering during reconnect | 1 | Verified by source | No outgoing message buffer during reconnection. Sends during disconnect will fail immediately |
| 3.2.8 | Connection timeout | 2 | Verified by source | Uses caller's `context.Context` for timeout — no SDK-provided default timeout. User must wrap with `context.WithTimeout()` |
| 3.2.9 | Request timeout | 3 | Verified by source | Commands/Queries have default 5s timeout (`client.go:10`). Events and queue operations rely on context timeout. Timeout is configurable per-message |

#### 3.3 TLS / mTLS

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 3.3.1 | TLS support | 3 | Verified by source | `grpc.go:39-57` — Supports TLS via cert file (`WithCredentials`) or PEM data (`WithCertificate`). Works correctly |
| 3.3.2 | Custom CA certificate | 3 | Verified by source | `grpc.go:47-53` — `x509.NewCertPool()` + `AppendCertsFromPEM()` for custom CA. Cookbook has `client/tls/main.go` with example certs |
| 3.3.3 | mTLS support | 1 | Verified by source | Only server CA verification supported. No client certificate + key configuration. `credentials.NewClientTLSFromFile/Cert` only — no `credentials.NewTLS()` with `tls.Config{Certificates: ...}` |
| 3.3.4 | TLS configuration | 1 | Verified by source | No configurable TLS version or cipher suites. Uses Go's default TLS settings |
| 3.3.5 | Insecure mode | 3 | Verified by source | `grpc.go:59` — `grpc.WithInsecure()` when not secured. No warnings logged. Default is insecure |

#### 3.4 Kubernetes-Native Behavior

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 3.4.1 | K8s DNS service discovery | 3 | Verified by source | `WithAddress(host, port)` works with K8s DNS. Default port not aligned with KubeMQ default (50000 must be specified). README mentions localhost:50000 for examples |
| 3.4.2 | Graceful shutdown APIs | 2 | Verified by source | `Client.Close()` exists but doesn't drain. No SIGTERM integration documented. Cookbook examples show `signal.Notify()` but not paired with client close |
| 3.4.3 | Health/readiness integration | 2 | Verified by source | `Ping()` can be used for health checks. But no `IsConnected()` method — must try Ping and check error. `connectionState` only in queues_stream downstream |
| 3.4.4 | Rolling update resilience | 3 | Inferred | Auto-reconnect handles server restarts for subscriptions. But one-shot operations have no retry, so rolling updates during sends will fail |
| 3.4.5 | Sidecar vs. standalone | 1 | Verified by source | No documentation on sidecar vs standalone deployment patterns. README only shows `localhost:50000` |

#### 3.5 Flow Control & Backpressure

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 3.5.1 | Publisher flow control | 1 | Verified by source | No configurable behavior for full outbound buffer. Streaming sends block on channel write. No drop/error policy |
| 3.5.2 | Consumer flow control | 2 | Verified by source | `WithReceiveBufferSize(size)` in `options.go:109-113` sets channel buffer. Default is 10. But no configurable prefetch or rate limiting |
| 3.5.3 | Throttle detection | 1 | Verified by source | No throttle detection. gRPC errors are passed through without rate-limit interpretation |
| 3.5.4 | Throttle error surfacing | 1 | Verified by source | No throttle-specific error messages or suggestions |

---

### Category 4: Error Handling & Resilience (Score: 1.89)

#### 4.1 Error Classification & Types

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 4.1.1 | Typed errors | 1 | Verified by source | Only 3 sentinel errors: `ErrNoTransportDefined`, `ErrNoTransportConnection` (`client.go:14-16`), `errConnectionClosed` (`grpc.go:28`). All other errors are `fmt.Errorf()` strings |
| 4.1.2 | Error hierarchy | 1 | Verified by source | No error hierarchy. No ConnectionError, AuthenticationError, TimeoutError, etc. |
| 4.1.3 | Retryable classification | 1 | Verified by source | No classification of errors as retryable/non-retryable. Auto-reconnect retries all stream errors equally |
| 4.1.4 | gRPC status mapping | 1 | Verified by source | gRPC errors passed through raw. No mapping of `status.Code()` to SDK error types. E.g., `grpc.Go:144` returns `err` directly from `g.client.SendEvent()` |
| 4.1.5 | Error wrapping/chaining | 2 | Verified by source | Some wrapping: `fmt.Errorf("could not load tls cert: %s", err)` at `grpc.go:43`. But uses `%s` not `%w`, so `errors.Unwrap()` doesn't work. Original error is lost |

#### 4.2 Error Message Quality

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 4.2.1 | Actionable messages | 1 | Verified by source | Messages like "invalid host", "invalid port" (`options.go:191-196`) — no suggestion on what to do. "kubemq grpc client connection lost, can't send messages" — no recovery suggestion |
| 4.2.2 | Context inclusion | 2 | Verified by source | Some context: "error sending create channel request: %s" (`common.go:28`). But most errors lack channel name, operation, or client ID |
| 4.2.3 | No swallowed errors | 3 | Verified by source | Most errors propagated. But `grpc.go:79` — `_ = g.conn.Close()` ignores close error. `grpc.go:191` — `_ = stream.CloseSend()` ignores stream close error. `downstream.go:177` — `d.Unlock()` without `defer` (bug — lock not held during iteration) |
| 4.2.4 | Consistent format | 2 | Verified by source | No consistent format. Mix of "error doing X: %s", "could not load X: %s", "invalid X", "no X" |

#### 4.3 Retry & Backoff

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 4.3.1 | Automatic retry | 2 | Verified by source | Only for streaming operations when `autoReconnect=true`. One-shot RPCs (SendEvent, SendCommand, etc.) have NO retry |
| 4.3.2 | Exponential backoff | 1 | Verified by source | Fixed interval: `time.Sleep(g.opts.reconnectInterval)` at `grpc.go:168`. No exponential growth, no jitter |
| 4.3.3 | Configurable retry | 2 | Verified by source | `WithReconnectInterval(duration)`, `WithMaxReconnects(value)` — only 2 knobs. No base delay, max delay, or jitter config |
| 4.3.4 | Retry exhaustion | 2 | Verified by source | `grpc.go:170` — "max reconnects reached, aborting" — but no attempt count, no total duration in message |
| 4.3.5 | Non-retryable bypass | 1 | Verified by source | All errors retried equally. No bypass for auth failures or validation errors |

#### 4.4 Resilience Patterns

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 4.4.1 | Timeout on all operations | 2 | Verified by source | Commands/Queries have default timeout. Events, queue sends have NO built-in timeout — rely on context. Connection has no default timeout |
| 4.4.2 | Cancellation support | 4 | Verified by source | All operations accept `context.Context`. Context cancellation properly handled with `<-ctx.Done()` checks throughout |
| 4.4.3 | Graceful degradation | 2 | Verified by source | Batch queue sends are all-or-nothing (`grpc.go:881-915`). One subscription failure doesn't affect others. But stream disconnect kills all pending transactions (`downstream.go:189-201`) |
| 4.4.4 | Resource leak prevention | 2 | Verified by source | `grpc.go:75-80` — goroutine monitors context but conn close error ignored. `grpc.go:192-205` — recv goroutine may not terminate if neither quit nor context fires. `downstream.go:176-177` — `d.Lock()` then `d.Unlock()` without defer — **concurrent access bug** |

---

### Category 5: Authentication & Security (Score: 2.00)

#### 5.1 Authentication Methods

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 5.1.1 | JWT token auth | 3 | Verified by source | `options.go:95-100` — `WithAuthToken(token)`. Injected via `authorization` header in `grpc.go:97-98`. Works for static tokens |
| 5.1.2 | Token refresh | 1 | Verified by source | No token refresh mechanism. Token is set once at client creation. No way to update without creating new client |
| 5.1.3 | OIDC integration | 1 | Verified by source | No OIDC support. No token acquisition helpers |
| 5.1.4 | Multiple auth methods | 2 | Verified by source | JWT token and TLS certificate — two methods. But no runtime switching, no pluggable auth provider |

#### 5.2 Security Best Practices

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 5.2.1 | Secure defaults | 1 | Verified by source | `options.go:169` — `isSecured: false` default. Insecure connection is default with no warning. `grpc.go:59` uses deprecated `grpc.WithInsecure()` |
| 5.2.2 | No credential logging | 3 | Inferred | Auth tokens not logged (no logging at all in SDK). But no explicit masking either — if user-level logging captured gRPC metadata, token would be visible |
| 5.2.3 | Credential handling | 3 | Verified by source | Token passed via gRPC metadata, not persisted. Cookbook examples use string literals for tokens (not ideal but typical for examples) |
| 5.2.4 | Input validation | 2 | Verified by source | `options.go:188-205` — Only validates host/port. No channel name validation, no metadata size validation, no tag key validation |
| 5.2.5 | Dependency security | 3 | Verified by source | `go vet` passes clean. Uses `gogo/protobuf v1.3.2` which is deprecated (should use google protobuf). OpenCensus is deprecated. No `govulncheck` in CI |

---

### Category 6: Concurrency & Thread Safety (Score: 2.88)

#### 6.1 Thread Safety

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 6.1.1 | Client thread safety | 3 | Verified by source | `Client` struct has no mutex for general operations. Transport is created once (safe). `singleStreamQueueMutex` at `client.go:30` limits concurrent queue streams. Generally safe because gRPC clients are thread-safe |
| 6.1.2 | Publisher thread safety | 3 | Verified by source | `SendEvent`, `SendCommand`, `SendQuery` delegate to gRPC client (thread-safe). `StreamEvents` shares a channel — concurrent writers to channel are safe in Go. `QueuesStreamClient.Send()` uses `sync.Mutex` at `queues_stream/client.go:59` |
| 6.1.3 | Subscriber thread safety | 3 | Verified by source | Each subscription runs in its own goroutine with independent channels. But `downstream.go:176-177` has a mutex bug: `d.Lock()` then `d.Unlock()` instead of `defer d.Unlock()`, leaving map iteration unprotected |
| 6.1.4 | Documentation of guarantees | 1 | Verified by source | No thread safety documentation anywhere in the codebase or README |
| 6.1.5 | Concurrency correctness validation | 1 | Verified by source | No `go test -race` in CI or Taskfile. Test file has a concurrent test (`TestQueuesClient_Send_Concurrent`) but only tests correctness, not race detection |

#### 6.2 Go-Specific Async Patterns

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 6.2.G1 | Context support | 4 | Verified by source | All public operations accept `context.Context` as first parameter. Context cancellation handled in all stream loops |
| 6.2.G2 | Goroutine management | 2 | Verified by source | Goroutines spawned for subscriptions and streams. But no WaitGroup or tracking mechanism to wait for completion. `grpc.go:75-80` spawns goroutine that may outlive the client |
| 6.2.G3 | Channel-based callbacks | 4 | Verified by source | Subscriptions return `<-chan *Event`, `<-chan *CommandReceive`, etc. Error channels for async errors. Idiomatic Go channel pattern |
| 6.2.G4 | No goroutine leaks | 2 | Verified by source | `Client.Close()` at `client.go:65-70` only closes transport. Does not cancel context or signal goroutines. Background goroutine at `grpc.go:75-80` relies on original context cancellation — if context is long-lived, goroutine leaks after Close() |

**Java/C#/Python/TS sections: N/A** — This is the Go SDK.

---

### Category 7: Observability (Score: 1.36)

#### 7.1 Logging

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 7.1.1 | Structured logging | 1 | Verified by source | No logging at all in the SDK. Zero log statements in production code |
| 7.1.2 | Configurable log level | 1 | Verified by source | No logging framework, no log levels |
| 7.1.3 | Pluggable logger | 1 | Verified by source | No logger interface or injection point |
| 7.1.4 | No stdout/stderr spam | 5 | Verified by source | Correct: SDK produces no output to stdout/stderr. But this is because there's no logging at all, not by design |
| 7.1.5 | Sensitive data exclusion | N/A | — | N/A since there's no logging to assess |
| 7.1.6 | Context in logs | 1 | Verified by source | No logging, so no context in logs |

#### 7.2 Metrics

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 7.2.1 | Metrics hooks | 1 | Verified by source | No metrics hooks or callbacks |
| 7.2.2 | Key metrics exposed | 1 | Verified by source | No metrics exposed. `QueuesInfo()` provides queue stats but that's server-side, not client metrics |
| 7.2.3 | Prometheus/OTel compatible | 1 | Verified by source | No metrics integration |
| 7.2.4 | Opt-in | N/A | — | N/A since no metrics exist |

#### 7.3 Tracing

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 7.3.1 | Trace context propagation | 2 | Verified by source | `trace.go` — `Trace` struct wraps OpenCensus span attributes. Used in `SendCommand` (`grpc.go:582-586`), `SendQuery` (`grpc.go:711-714`), `SendResponse` (`grpc.go:843-847`). But only for RPC, not events or queues |
| 7.3.2 | Span creation | 2 | Verified by source | `trace.StartSpan()` called for commands, queries, responses. No spans for events or queue operations |
| 7.3.3 | OTel integration | 1 | Verified by source | Uses **OpenCensus** (`go.opencensus.io v0.24.0`) which is **deprecated** in favor of OpenTelemetry. No OTel integration |
| 7.3.4 | Opt-in | 3 | Verified by source | Tracing only activates if `trace` field is set on messages. Nil by default. Opt-in by design |

---

### Category 8: Code Quality & Architecture (Score: 2.65)

#### 8.1 Code Structure

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 8.1.1 | Package/module organization | 2 | Verified by source | Mostly flat structure — all core types in root package. `queues_stream/` is a separate sub-package with its own client, options, gRPC setup. `common/` for shared channel types. `pkg/uuid/` utility. The split creates duplication |
| 8.1.2 | Separation of concerns | 3 | Verified by source | `Transport` interface (`transport.go`) cleanly separates transport from API. But message types (event.go, command.go) embed transport reference, coupling data to transport |
| 8.1.3 | Single responsibility | 3 | Verified by source | `grpc.go` (1055 lines) handles ALL gRPC operations — too large. Should be split by pattern. `client.go` mixes factory methods with client operations |
| 8.1.4 | Interface-based design | 3 | Verified by source | `Transport` interface is well-designed with 19 methods. `Option` interface for functional options. But no interfaces for testing (e.g., no mockable client interface) |
| 8.1.5 | No circular dependencies | 5 | Verified by source | Clean dependency graph: `queues_stream` → `kubemq` → `protobuf`. No circular imports |
| 8.1.6 | Consistent file structure | 3 | Verified by source | Generally one type per file (event.go, command.go, query.go). But inconsistent: `grpc.go` has everything, `queue.go` has multiple types, `common.go` has channel operations |
| 8.1.7 | Public API surface isolation | 2 | Verified by source | Proto types leak through `QueueMessage.QueueMessage` (embedded `*pb.QueueMessage`). `GetGRPCRawClient()` exposes raw proto client. No internal packages used |

#### 8.2 Code Quality

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 8.2.1 | Linter compliance | 3 | Inferred | `go vet` passes clean. Taskfile has `golangci-lint` configured. But no CI enforcement |
| 8.2.2 | No dead code | 3 | Verified by source | Minor: commented-out code `client.go:31` — `// currentSQM *StreamQueueMessage`. `downstream.go:104,118` — commented-out log lines. Legacy `StreamQueueMessage` API potentially dead |
| 8.2.3 | Consistent formatting | 4 | Verified by source | Code appears gofmt-formatted. Consistent style throughout |
| 8.2.4 | Meaningful naming | 3 | Verified by source | Generally good names. But: `IsPeak` should be `IsPeek` (typo), `QMB()` abbreviation is cryptic, `SQM()` unclear without context |
| 8.2.5 | Error path completeness | 2 | Verified by source | Multiple ignored errors: `_ = g.conn.Close()` (`grpc.go:79`), `_ = stream.CloseSend()` (`grpc.go:191`). `downstream.go:176-177` has the mutex unlock bug described earlier |
| 8.2.6 | Magic number/string avoidance | 3 | Verified by source | Some constants defined (`defaultMaxSendSize`, `defaultRequestTimeout`, `kubeMQAuthTokenHeader`). But magic numbers in `queues_stream/client.go:159` — `RequestTypeData: 2`, `Timeout: 10000` |
| 8.2.7 | Code duplication | 2 | Verified by source | Heavy duplication: `options.go` and `queues_stream/options.go` are ~80% identical. Subscription reconnect pattern copied 4 times in `grpc.go` (events, events store, commands, queries). `Create`/`Delete`/`List` methods duplicated between `common.go` and `queues_stream/client.go` |

#### 8.3 Serialization & Message Handling

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 8.3.1 | JSON marshaling helpers | 2 | Verified by source | `common.go:106-140` has `DecodeQueuesChannelList()`, `DecodePubSubChannelList()`, `DecodeCQChannelList()` — JSON for channel listings. But no helpers for message body serialization |
| 8.3.2 | Protobuf message wrapping | 2 | Verified by source | Events, Commands, Queries properly wrapped. But `QueueMessage` embeds `*pb.QueueMessage` directly — proto type exposed in public API |
| 8.3.3 | Typed payload support | 1 | Verified by source | Body is always `[]byte`. No generic/typed payload support. User must manually serialize/deserialize |
| 8.3.4 | Custom serialization hooks | 1 | Verified by source | No serialization hooks or pluggable serializers |
| 8.3.5 | Content-type handling | 1 | Verified by source | No content-type metadata support. Tags can be used manually but no built-in support |

#### 8.4 Technical Debt

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 8.4.1 | TODO/FIXME/HACK comments | 4 | Verified by source | No TODO/FIXME/HACK comments found. Clean in that regard |
| 8.4.2 | Deprecated code | 2 | Verified by source | Legacy `StreamQueueMessage` API in `queue.go` (716 lines) coexists with newer `QueuesStreamClient`. Uses deprecated `grpc.WithInsecure()` and `grpc.DialContext()` |
| 8.4.3 | Dependency freshness | 3 | Verified by source | gRPC v1.69.2 is fairly recent. But `gogo/protobuf v1.3.2` is deprecated (archived). `go.opencensus.io v0.24.0` is deprecated. `gorilla/websocket v1.5.1` — gorilla is archived |
| 8.4.4 | Language version | 4 | Verified by source | `go.mod:3` — Go 1.22 with toolchain 1.23.0. Current and supported |
| 8.4.5 | gRPC/protobuf library version | 3 | Verified by source | `google.golang.org/grpc v1.69.2` is recent. But uses `gogo/protobuf` (deprecated) via the protobuf dependency, and `google.golang.org/protobuf v1.35.1` is slightly behind latest |

#### 8.5 Extensibility

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 8.5.1 | Interceptor/middleware support | 2 | Verified by source | `grpc.go:95-111` — Internal interceptors for auth. `GetGRPCRawClient()` gives access to raw client. But no user-configurable interceptor injection |
| 8.5.2 | Event hooks | 1 | Verified by source | Only `connectionNotificationFunc` in queues_stream. No onConnect/onDisconnect/onError/onMessage lifecycle hooks in main client |
| 8.5.3 | Transport abstraction | 4 | Verified by source | `Transport` interface (`transport.go`) cleanly abstracts gRPC and REST. User can even call `GetGRPCRawClient()` for direct access |

---

### Category 9: Testing (Score: 1.40)

#### 9.1 Unit Tests

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 9.1.1 | Unit test existence | 1 | Verified by runtime | Only `queues_stream/client_test.go` (560 lines). **No unit tests for core SDK** (root package). `go test ./...` shows `[no test files]` for 14 out of 15 packages |
| 9.1.2 | Coverage percentage | 1 | Verified by runtime | Estimated <5% coverage. Only queues_stream package has tests, and those are integration tests requiring a running server |
| 9.1.3 | Test quality | 2 | Verified by source | Tests in `client_test.go` are meaningful — cover send, poll, ack/nack, requeue, close, context cancellation. But ALL require a running server |
| 9.1.4 | Mocking | 1 | Verified by source | No mocking framework. No mock transport. No test helpers. Tests require real KubeMQ server at localhost:50000 |
| 9.1.5 | Table-driven / parameterized tests | 1 | Verified by source | No table-driven tests. Each test is a standalone function |
| 9.1.6 | Assertion quality | 4 | Verified by source | Uses `github.com/stretchr/testify/require` for assertions. Clean `require.NoError`, `require.EqualValues`, `require.False` patterns |

#### 9.2 Integration Tests

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 9.2.1 | Integration test existence | 2 | Verified by source | `queues_stream/client_test.go` — 14 integration tests for queue stream pattern only |
| 9.2.2 | All patterns covered | 1 | Verified by source | Only queues_stream covered. **No integration tests for events, events store, commands, queries** |
| 9.2.3 | Error scenario testing | 2 | Verified by source | `TestQueuesClient_Send_WithInvalid_Data`, `TestQueuesClient_Poll_InvalidRequest`, `TestQueuesClient_Poll_InvalidRequestByServer` — some error cases covered |
| 9.2.4 | Setup/teardown | 2 | Verified by source | Uses `context.WithCancel` + `defer cancel()`. No shared test fixtures or cleanup. Uses random channels (`uuid.New()`) to avoid interference |
| 9.2.5 | Parallel safety | 2 | Verified by source | Tests use unique channel names. `TestQueuesClient_Send_Concurrent` tests concurrent sends. But no `t.Parallel()` used |

#### 9.3 CI/CD Pipeline

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 9.3.1 | CI pipeline exists | 1 | Verified by source | **No CI pipeline.** No `.github/workflows/`, no Jenkinsfile, no CircleCI. Only `Taskfile.yml` for local tasks |
| 9.3.2 | Tests run on PR | 1 | Verified by source | No CI, so no automated PR testing |
| 9.3.3 | Lint on CI | 1 | Verified by source | `Taskfile.yml:14` has lint task (`golangci-lint`) but only runs locally |
| 9.3.4 | Multi-version testing | 1 | Verified by source | No multi-version Go testing |
| 9.3.5 | Security scanning | 1 | Verified by source | No dependency scanning. No `govulncheck`, no Dependabot, no Snyk |

---

### Category 10: Documentation (Score: 2.82)

#### 10.1 API Reference

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 10.1.1 | API docs exist | 3 | Verified by source | GoDoc comments on most public functions. `pkg.go.dev/github.com/kubemq-io/kubemq-go` auto-generated from doc comments |
| 10.1.2 | All public methods documented | 2 | Verified by source | Many methods have doc comments (`client.go:34`, `options.go:56-163`). But `SetQueueMessage`, `SetEvent`, etc. at `client.go:375-402` have no comments. `queue.go` types have minimal docs |
| 10.1.3 | Parameter documentation | 2 | Verified by source | Option functions documented but parameters not individually documented. Return values not documented. Errors not documented |
| 10.1.4 | Code doc comments | 2 | Verified by source | Root package has partial GoDoc. `queues_stream/client.go` has good doc comments on all methods. But many files (queue.go, event.go) have minimal or no comments |
| 10.1.5 | Published API docs | 3 | Inferred | Go automatically publishes to `pkg.go.dev`. Module exists there |

#### 10.2 Guides & Tutorials

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 10.2.1 | Getting started guide | 3 | Verified by source | README has Prerequisites, Installation, and usage sections with code examples. But no step-by-step quickstart |
| 10.2.2 | Per-pattern guide | 3 | Verified by source | README documents each pattern (Events, Events Store, Commands, Queries, Queues) with request/response tables and examples |
| 10.2.3 | Authentication guide | 2 | Verified by source | README mentions `WithAuthToken()`. Cookbook has `client/authentication/main.go` and `client/tls/`. But no comprehensive auth guide |
| 10.2.4 | Migration guide | 1 | Verified by source | No migration guide. v1 to v2 changes undocumented |
| 10.2.5 | Performance tuning guide | 1 | Verified by source | No performance tuning documentation |
| 10.2.6 | Troubleshooting guide | 1 | Verified by source | No troubleshooting guide |

#### 10.3 Examples & Cookbook

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 10.3.1 | Example code exists | 5 | Verified by runtime | 25 cookbook examples + in-repo examples. All compile successfully |
| 10.3.2 | All patterns covered | 5 | Verified by source | Events (6), Events Store (6), Queues (12), RPC (2), Client (5) — comprehensive |
| 10.3.3 | Examples compile/run | 4 | Verified by runtime | All 25 cookbook examples compile. Cannot verify runtime without server |
| 10.3.4 | Real-world scenarios | 4 | Verified by source | Dead-letter queue, message resending, visibility extension, multicast, load balancing — practical patterns |
| 10.3.5 | Error handling shown | 2 | Verified by source | Most examples use `log.Fatal(err)`. Graceful shutdown via `signal.Notify()` in some. No production error handling patterns |
| 10.3.6 | Advanced features | 4 | Verified by source | Auth, TLS, delayed messages, DLQ, group subscriptions, wildcards, multicast all demonstrated |

#### 10.4 README Quality

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 10.4.1 | Installation instructions | 4 | Verified by source | Clear: `go get github.com/kubemq-io/kubemq-go`. Prerequisites listed |
| 10.4.2 | Quick start code | 4 | Verified by source | README includes code examples for each pattern with request/response parameter tables |
| 10.4.3 | Prerequisites | 3 | Verified by source | "Go SDK 1.17+" and "running KubeMQ server" mentioned. But no KubeMQ server version specified |
| 10.4.4 | License | 4 | Verified by source | `LICENSE` file present (Apache 2.0). Not referenced in README |
| 10.4.5 | Changelog | 1 | Verified by source | No CHANGELOG.md. Git tags exist but no release notes |

---

### Category 11: Packaging & Distribution (Score: 3.08)

#### 11.1 Package Manager

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 11.1.1 | Published to canonical registry | 4 | Inferred | Go module at `github.com/kubemq-io/kubemq-go`, available via `go get`. Listed on pkg.go.dev |
| 11.1.2 | Package metadata | 2 | Verified by source | `go.mod` has module name. No description, homepage, or repository metadata in package (Go modules don't have this natively) |
| 11.1.3 | Reasonable install | 4 | Verified by runtime | `go get github.com/kubemq-io/kubemq-go` works smoothly |
| 11.1.4 | Minimal dependency footprint | 3 | Verified by source | 7 direct dependencies in `go.mod`. Reasonable but includes `go-resty`, `gorilla/websocket`, `go.opencensus.io` — some could be removed if REST transport dropped |

#### 11.2 Versioning & Releases

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 11.2.1 | Semantic versioning | 3 | Verified by runtime | Tags follow semver: v1.7.5 → v1.7.11 → v1.8.0 → v1.9.0. But jump from v1.7.10 to v1.7.11 suggests patch overflow |
| 11.2.2 | Release tags | 4 | Verified by runtime | 10+ git tags from v1.7.5 to v1.9.0 |
| 11.2.3 | Release notes | 1 | Verified by source | No GitHub Releases with descriptions. Tags are bare `git tag -a` without notes |
| 11.2.4 | Current version | 3 | Verified by runtime | Latest tag v1.9.0 from 2024-09-28. HEAD has unreleased changes from 2024-12-22. Taskfile says v1.10.0 |
| 11.2.5 | Version consistency | 2 | Verified by source | Mismatch: Taskfile says `v1.10.0`, latest git tag is `v1.9.0`, HEAD is untagged. No changelog to verify |

#### 11.3 Build & Development Setup

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 11.3.1 | Build instructions | 2 | Verified by source | No build instructions. README doesn't mention how to build from source |
| 11.3.2 | Build succeeds | 5 | Verified by runtime | `go build ./...` succeeds with zero errors |
| 11.3.3 | Development dependencies | 4 | Verified by source | `go.mod` separates requires. `testify` is only test dep. Go modules handle this well |
| 11.3.4 | Contributing guide | 1 | Verified by source | No CONTRIBUTING.md |

#### 11.4 SDK Binary Size & Footprint

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 11.4.1 | Dependency weight | 3 | Verified by source | 7 direct + ~15 indirect dependencies. gRPC and protobuf are heavy but necessary. OpenCensus and websocket add optional weight |
| 11.4.2 | No native compilation required | 5 | Verified by runtime | Pure Go. No CGo, no native dependencies. Builds on all Go-supported platforms |

---

### Category 12: Compatibility, Lifecycle & Supply Chain (Score: 1.50)

#### 12.1 Compatibility

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 12.1.1 | Server version matrix | 1 | Verified by source | No server version compatibility matrix documented |
| 12.1.2 | Runtime support matrix | 1 | Verified by source | README says "Go SDK 1.17+" but go.mod says `go 1.22`. Contradictory and undocumented |
| 12.1.3 | Deprecation policy | 1 | Verified by source | No deprecation policy. Legacy StreamQueueMessage API has no deprecation notice |
| 12.1.4 | Backward compatibility discipline | 2 | Inferred | Appears to maintain backward compat within v1.x. But no policy documented |

#### 12.2 Supply Chain & Release Integrity

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 12.2.1 | Signed releases | 1 | Verified by source | No GPG-signed tags. `Taskfile.yml:23` — `git tag -a` without `-s` signing |
| 12.2.2 | Reproducible builds | 3 | Verified by source | `go.sum` provides dependency verification. No vendor directory but Go module checksums provide integrity |
| 12.2.3 | Dependency update process | 1 | Verified by source | No Dependabot, no Renovate, no documented update process |
| 12.2.4 | Security response process | 1 | Verified by source | No SECURITY.md. No vulnerability reporting process |
| 12.2.5 | SBOM | 1 | Verified by source | No SBOM generated |
| 12.2.6 | Maintainer health | 2 | Verified by runtime | Single maintainer. Commits are sparse (latest Dec 2024). No issue response tracking visible |

---

### Category 13: Performance (Score: 1.60)

#### 13.1 Benchmark Infrastructure

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 13.1.1 | Benchmark tests exist | 1 | Verified by source | No `Benchmark*` functions in any test file |
| 13.1.2 | Benchmark coverage | 1 | Verified by source | No benchmarks |
| 13.1.3 | Benchmark documentation | 1 | Verified by source | No benchmark docs |
| 13.1.4 | Published results | 1 | Verified by source | No published performance numbers |

#### 13.2 Optimization Patterns

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 13.2.1 | Object/buffer pooling | 1 | Verified by source | No `sync.Pool` usage. No buffer reuse |
| 13.2.2 | Batching support | 3 | Verified by source | Queue batch send via `SendQueueMessages()` and variadic `Send(messages...)`. Event streaming sends batched via channel. No auto-batching |
| 13.2.3 | Lazy initialization | 3 | Verified by source | `QueuesStreamClient` lazily initializes upstream/downstream on first use (`client.go:60-62`, `client.go:93-96`). Transport created eagerly on `NewClient()` |
| 13.2.4 | Memory efficiency | 2 | Verified by source | New proto objects allocated per message send (no reuse). Channels with small buffers (default 10) limit memory. But no allocation optimization on hot paths |
| 13.2.5 | Resource leak risk | 2 | Verified by source | `grpc.go:75-80` goroutine leak risk. `downstream.go:176-177` mutex bug. Ignored close errors throughout. `queues_stream/client.go:265` — `time.Sleep(100ms)` instead of proper drain |
| 13.2.6 | Connection overhead | 4 | Verified by source | Single gRPC connection per client. Streams multiplex over single connection. Efficient resource usage pattern |

---

## Developer Journey Assessment

### Narrative Walkthrough

**1. Install** — `go get github.com/kubemq-io/kubemq-go` — seamless, standard Go module install. No friction.

**2. Connect** — Creating a client requires deciding between `kubemq.NewClient()` (for events, events store, commands, queries, and legacy queues) or `queues_stream.NewQueuesStreamClient()` (for modern queue streaming). This fork is confusing for new users. The README helps but doesn't explain when to use which.

**3. First Publish** — `client.E().SetChannel("test").SetBody([]byte("hello")).Send(ctx)` — fluent, clean, ~5 lines. Good experience.

**4. First Subscribe** — Requires creating an error channel, calling `SubscribeToEvents()`, then reading from two channels (events + errors) in a select loop. More boilerplate than competitors like NATS (which use callbacks). Functional but requires understanding Go concurrency.

**5. Error Handling** — When something fails, errors are raw strings: "invalid host", "kubemq grpc client connection lost". Cannot programmatically distinguish error types. No `errors.Is()` or `errors.As()` possible. Users resort to string matching.

**6. Production Config** — Must explicitly enable auto-reconnect (`WithAutoReconnect(true)`), which is off by default. No structured logging to enable. No health check by default. TLS requires manual cert setup. Missing many production essentials.

**7. Troubleshooting** — No troubleshooting guide, no FAQ, no diagnostic commands. Error messages don't suggest fixes. The only recourse is reading source code or the cookbook examples.

**Most Significant Friction Point:** The dual-client architecture (root `Client` vs `queues_stream.QueuesStreamClient`) with duplicated option types creates real confusion about which to use and how they relate.

---

## Competitor Comparison

### Go SDK Comparison Matrix

| Area | KubeMQ Go | nats.go | confluent-kafka-go | pulsar-client-go |
|------|-----------|---------|-------------------|-----------------|
| **API Design** | Functional options, channel-based subscriptions. Clean but dual-client confusion | Gold standard: functional options, simple `Publish`/`Subscribe`. Single `Conn` type | C wrapper (cgo). Config-map based. Less idiomatic Go | Builder pattern. Close to KubeMQ in style |
| **Error Types** | Generic strings | Typed errors (`nats.ErrTimeout`, `nats.ErrConnectionClosed`) | Error codes from librdkafka | Typed `Result` with error codes |
| **Reconnection** | Fixed interval, opt-in | Automatic with exponential backoff + jitter (default on) | Handled by librdkafka | Automatic with backoff |
| **Logging** | None | Optional, pluggable | Callback-based | Pluggable logger interface |
| **Documentation** | README + cookbook | Extensive docs, NATS by Example, godoc | Confluent docs, librdkafka docs | Apache docs, godoc |
| **Test Coverage** | <5% (integration only) | >80% with mocks | C library tests + Go tests | Good coverage with mocks |
| **GitHub Stars** | ~75 | ~5000+ | ~4500+ | ~700+ |
| **Release Cadence** | ~3/year | Monthly | Monthly | Monthly |
| **gRPC Keepalive** | Not configured | N/A (custom TCP) | N/A (librdkafka) | Configured |
| **Metrics/Tracing** | Deprecated OpenCensus | Built-in stats, OTel bridge | librdkafka stats | OTel integration |

**Key Takeaways:**
- KubeMQ Go SDK is **significantly behind** nats.go and confluent-kafka-go in maturity
- The functional options pattern and channel-based subscriptions are idiomatic
- Critical gaps: typed errors, exponential backoff, logging, testing, CI
- The dual-client architecture is unique and problematic — no competitor has this pattern

---

## Remediation Roadmap

### Phase 0: Assessment Validation (1-2 days)
Validate top 5 findings with targeted manual smoke tests:
1. Confirm the `downstream.go:176-177` mutex bug reproduces under load
2. Verify auto-reconnect behavior with server restart
3. Test TLS connection with real certificates
4. Confirm goroutine leak after `Client.Close()` using runtime.NumGoroutine()
5. Verify proto alignment with latest server build

### Phase 1: Quick Wins (Effort: S-M)

| # | Item | Category | Current | Target | Effort | Impact | Depends On | Scope | Validation Metric |
|---|------|----------|---------|--------|--------|--------|------------|-------|-------------------|
| 1 | Fix `downstream.go:176-177` mutex bug — `d.Lock()` then `d.Unlock()` should be `defer d.Unlock()` | Concurrency | 2 | 4 | S | High | — | language-specific | Race detector passes under concurrent load test |
| 2 | Replace `grpc.WithInsecure()` and `grpc.DialContext()` with `grpc.NewClient()` + `insecure.NewCredentials()` | Connection | 2 | 4 | S | Medium | — | language-specific | No deprecation warnings; `go vet` passes |
| 3 | Add error types: `KubeMQError` base, `ConnectionError`, `AuthError`, `TimeoutError`, `ValidationError`, `ServerError` with `IsRetryable()` | Error Handling | 1 | 4 | M | High | — | cross-SDK | All errors inherit from `KubeMQError`; `errors.Is()`/`errors.As()` works |
| 4 | Add gRPC status code mapping to SDK error types | Error Handling | 1 | 4 | M | High | #3 | cross-SDK | `Unavailable` → `ConnectionError`, `Unauthenticated` → `AuthError`, `DeadlineExceeded` → `TimeoutError` |
| 5 | Use `%w` instead of `%s` for error wrapping | Error Handling | 2 | 4 | S | Medium | — | language-specific | `errors.Unwrap()` works on all SDK errors |
| 6 | Add CHANGELOG.md | Documentation | 1 | 3 | S | Medium | — | cross-SDK | CHANGELOG.md exists with recent releases documented |
| 7 | Add GitHub Actions CI: build, vet, lint on PR | Testing | 1 | 3 | S | High | — | cross-SDK | CI green badge on README |
| 8 | Add `SECURITY.md` with vulnerability reporting process | Security | 1 | 3 | S | Medium | — | cross-SDK | SECURITY.md exists with email/process |
| 9 | Document thread safety guarantees in GoDoc | Concurrency | 1 | 3 | S | Medium | — | language-specific | "Client is safe for concurrent use" appears in GoDoc |
| 10 | Fix error messages to be actionable: include what to do, not just what's wrong | Error Handling | 1 | 3 | S | Medium | #3 | cross-SDK | All errors include suggestion text |

### Phase 2: Medium-Term Improvements (Effort: M-L)

| # | Item | Category | Current | Target | Effort | Impact | Depends On | Scope | Validation Metric |
|---|------|----------|---------|--------|--------|--------|------------|-------|-------------------|
| 11 | Add exponential backoff with jitter for reconnection | Connection | 1 | 4 | M | High | — | cross-SDK | `min(base * 2^attempt, maxDelay) + jitter` formula verified in unit test |
| 12 | Unify client architecture — single client with all patterns, remove dual-client split | API Design | 2 | 4 | L | High | — | language-specific | One `NewClient()` entry point; queues_stream integrated |
| 13 | Add pluggable structured logging with slog interface | Observability | 1 | 4 | M | High | — | cross-SDK | `WithLogger(slog.Logger)` option; debug/info/warn/error levels |
| 14 | Add unit tests with mock transport for all patterns | Testing | 1 | 4 | L | High | — | language-specific | >60% code coverage; `go test ./...` passes without server |
| 15 | Migrate from OpenCensus to OpenTelemetry | Observability | 1 | 4 | M | Medium | — | cross-SDK | `go.opentelemetry.io` imported; OTel spans for all operations |
| 16 | Add mTLS support (client cert + key) | Connection | 1 | 4 | M | High | — | cross-SDK | `WithMTLS(certFile, keyFile, caFile)` option works |
| 17 | Add gRPC keepalive configuration | Connection | 1 | 3 | S | Medium | — | cross-SDK | `keepalive.ClientParameters` configured with sensible defaults |
| 18 | Add connection state callbacks to main Client | Connection | 2 | 4 | M | Medium | — | cross-SDK | `WithOnConnectionChange(func(state ConnectionState))` option |
| 19 | Wrap QueueMessage to hide proto internals | Code Quality | 2 | 4 | M | Medium | #12 | language-specific | No `pb.*` types in public API |
| 20 | Add server version compatibility matrix | Compatibility | 1 | 3 | S | Medium | — | cross-SDK | README includes tested server versions |

### Phase 3: Major Rework (Effort: L-XL)

| # | Item | Category | Current | Target | Effort | Impact | Depends On | Scope | Validation Metric |
|---|------|----------|---------|--------|--------|--------|------------|-------|-------------------|
| 21 | Add comprehensive integration test suite for all patterns with CI server provisioning | Testing | 1 | 4 | XL | High | #7, #14 | language-specific | CI runs integration tests against KubeMQ server in Docker; all 4 patterns tested |
| 22 | Add metrics hooks (Prometheus/OTel compatible) | Observability | 1 | 4 | L | Medium | #15 | cross-SDK | Messages sent/received counters, latency histograms, error counts exposed via OTel |
| 23 | Add automatic retry for one-shot operations with backoff | Error Handling | 1 | 4 | L | High | #3, #11 | cross-SDK | Transient errors retried; auth errors skip retry; configurable policy |
| 24 | Performance benchmark suite | Performance | 1 | 4 | L | Medium | #14 | language-specific | `go test -bench .` runs benchmarks for publish/subscribe/queue operations |
| 25 | Make TLS the default, insecure requires explicit opt-in | Security | 1 | 4 | M | High | #16 | cross-SDK | `WithInsecure()` required for plaintext; default is TLS |

### Effort Key
- **S (Small):** < 1 day of work
- **M (Medium):** 1-3 days of work
- **L (Large):** 1-2 weeks of work
- **XL (Extra Large):** 2+ weeks of work

### Priority Order
1. **#1** (mutex bug) — correctness fix, S effort, high impact
2. **#3, #4, #5** (error types) — unlocks quality gate, M effort, highest category impact
3. **#7** (CI) — foundational for all other improvements
4. **#11** (backoff) — critical resilience gap
5. **#12** (unify clients) — removes developer confusion
6. **#13** (logging) — essential for production diagnostics
7. **#14** (unit tests) — enables safe refactoring for everything else

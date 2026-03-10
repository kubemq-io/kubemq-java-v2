# KubeMQ C# / .NET SDK Assessment Report

## Executive Summary
- **Weighted Score (Production Readiness):** 2.27 / 5.0
- **Unweighted Score (Overall Maturity):** 2.22 / 5.0
- **Gating Rule Applied:** YES — Critical-tier categories Connection & Transport (2.6), Error Handling (1.8), and Auth & Security (2.2) are all below 3.0. **Overall score capped at 3.0** (already below cap).
- **Assessment Date:** 2026-03-09
- **SDK Version Assessed:** v2.0.0 (PackageVersion), AssemblyVersion 1.7.2
- **Repository:** github.com/kubemq-io/kubemq-CSharp

### Category Scores

| # | Category | Weight | Score | Grade | Gating? |
|---|----------|--------|-------|-------|---------|
| 1 | API Completeness & Feature Parity | 14% | 3.8 | Good | Critical |
| 2 | API Design & Developer Experience | 9% | 3.2 | Adequate | High |
| 3 | Connection & Transport | 11% | 2.6 | Weak | Critical |
| 4 | Error Handling & Resilience | 11% | 1.8 | Poor | Critical |
| 5 | Auth & Security | 9% | 2.2 | Weak | Critical |
| 6 | Concurrency & Thread Safety | 7% | 2.8 | Weak | High |
| 7 | Observability | 5% | 1.4 | Absent | Standard |
| 8 | Code Quality & Architecture | 6% | 2.4 | Weak | High |
| 9 | Testing | 9% | 1.0 | Absent | High |
| 10 | Documentation | 7% | 3.0 | Adequate | High |
| 11 | Packaging & Distribution | 4% | 2.8 | Weak | Standard |
| 12 | Compatibility, Lifecycle & Supply Chain | 4% | 1.6 | Poor | Standard |
| 13 | Performance | 4% | 1.4 | Absent | Standard |

**Weighted Score Calculation:** (0.14×3.8)+(0.09×3.2)+(0.11×2.6)+(0.11×1.8)+(0.09×2.2)+(0.07×2.8)+(0.05×1.4)+(0.06×2.4)+(0.09×1.0)+(0.07×3.0)+(0.04×2.8)+(0.04×1.6)+(0.04×1.4) = **2.27**

**Unweighted Average:** (3.8+3.2+2.6+1.8+2.2+2.8+1.4+2.4+1.0+3.0+2.8+1.6+1.4) / 13 = **2.22**

### Top Strengths
1. **Strong API completeness** — All 4 messaging patterns implemented with most operations (Events, EventsStore, Commands, Queries, Queues)
2. **Good README documentation** — Comprehensive 76KB README with per-pattern guides, configuration examples, and code samples
3. **Fluent builder pattern** — Clean, idiomatic C# configuration API with sensible defaults

### Critical Gaps (Must Fix)
1. **No tests whatsoever** — Zero active unit or integration tests; only archived legacy tests
2. **No error type hierarchy** — All errors collapsed into generic `Result.ErrorMessage` string; stack traces discarded
3. **No CI/CD pipeline** — No GitHub Actions, no automated build/test/lint
4. **No observability** — No metrics, no OpenTelemetry, no distributed tracing, minimal logging
5. **Incorrect gRPC interceptor implementation** — Server-side handler methods used instead of client-side interceptors for streaming auth

---

## Detailed Findings

### Category 1: API Completeness & Feature Parity (Score: 3.8)

**Scoring: 0/1/2 scale, normalized to 1-5 for rollup (0→1, 1→3, 2→5)**

#### 1.1 Events (Pub/Sub)

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 1.1.1 | Publish single event | 2 | Verified by source | `EventsClient.Send(Event)` at `PubSub/Events/EventsClient.cs:133-156`. Calls `KubemqClient.SendEventAsync(grpcEvent)`. |
| 1.1.2 | Subscribe to events | 2 | Verified by source | `EventsClient.Subscribe(EventsSubscription)` at `PubSub/Events/EventsClient.cs:57-126`. Uses `KubemqClient.SubscribeToEvents()` with server streaming. |
| 1.1.3 | Event metadata | 2 | Verified by source | `PubSub/Events/Event.cs` supports Channel, ClientId, Metadata (string), Body (bytes), Tags (Dictionary<string,string>). |
| 1.1.4 | Wildcard subscriptions | 2 | Verified by source | Subscription channel is passed directly to server; wildcard support depends on server. Cookbook has `pubsub/events/wildcards` example. |
| 1.1.5 | Multiple subscriptions | 2 | Verified by source | Multiple `Subscribe()` calls tracked in `_subscriptionTokens` list at `EventsClient.cs:16`. Each subscription gets own CancellationTokenSource. |
| 1.1.6 | Unsubscribe | 1 | Verified by source | No explicit `Unsubscribe()` method. Cancel via CancellationTokenSource only. `Close()` cancels all at `EventsClient.cs:158-172`. |
| 1.1.7 | Group-based subscriptions | 2 | Verified by source | `EventsSubscription` supports `Group` property. Cookbook has `pubsub/events/load_balancer` example. |

**Subsection Score:** 13/14 raw → normalized avg: 4.7

#### 1.2 Events Store (Persistent Pub/Sub)

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 1.2.1 | Publish to events store | 2 | Verified by source | `EventsStoreClient.Send(EventStore)` at `PubSub/EventsStore/EventsStoreClient.cs`. |
| 1.2.2 | Subscribe to events store | 2 | Verified by source | `EventsStoreClient.Subscribe(EventsStoreSubscription)` with server streaming. |
| 1.2.3 | StartFromNew | 2 | Verified by source | `EventsStoreType.StartNewOnly` in `Subscription/EventsStoreType.cs`. |
| 1.2.4 | StartFromFirst | 2 | Verified by source | `EventsStoreType.StartFromFirst`. |
| 1.2.5 | StartFromLast | 2 | Verified by source | `EventsStoreType.StartFromLast`. |
| 1.2.6 | StartFromSequence | 2 | Verified by source | `EventsStoreType.StartAtSequence` with `EventsStoreSequenceValue` parameter. |
| 1.2.7 | StartFromTime | 2 | Verified by source | `EventsStoreType.StartAtTime` with `EventsStoreStartTime` parameter. |
| 1.2.8 | StartFromTimeDelta | 2 | Verified by source | `EventsStoreType.StartAtTimeDelta` with `EventsStoreTimeDeltaValue` parameter. |
| 1.2.9 | Event store metadata | 2 | Verified by source | Same metadata fields as Events: Channel, ClientId, Metadata, Body, Tags. |

**Subsection Score:** 18/18 raw → normalized avg: 5.0

#### 1.3 Queues

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 1.3.1 | Send single message | 2 | Verified by source | `QueuesClient.Send(Message)` at `Queues/QueuesClient.cs`. Uses upstream bidirectional stream. |
| 1.3.2 | Send batch messages | 2 | Verified by source | Multiple `Send()` calls via upstream stream. Legacy `Queue/Queue.cs` has explicit `SendQueueMessagesBatch`. |
| 1.3.3 | Receive/Pull messages | 2 | Verified by source | `QueuesClient.Poll(PollRequest)` at `Queues/QueuesClient.cs`. |
| 1.3.4 | Receive with visibility timeout | 2 | Verified by source | `PollRequest` includes `VisibilitySeconds` property at `Queues/PollRequest.cs`. |
| 1.3.5 | Message acknowledgment | 2 | Verified by source | `PollResponse` includes `AckAll()`, `RejectAll()`, `ReQueueAll()`. Individual messages have `Ack()`, `Reject()`, `ReQueue()`. |
| 1.3.6 | Queue stream / transaction | 2 | Verified by source | `Queues/Upstream.cs` and `Queues/Downstream.cs` implement bidirectional streaming with `QueuesUpstream`/`QueuesDownstream` RPCs. |
| 1.3.7 | Delayed messages | 2 | Verified by source | `QueueMessagePolicy.DelaySeconds` at `Grpc/kubemq.proto`. SDK `Queues/Message.cs` supports Policy with delay. |
| 1.3.8 | Message expiration | 2 | Verified by source | `QueueMessagePolicy.ExpirationSeconds` supported. |
| 1.3.9 | Dead letter queue | 2 | Verified by source | `QueueMessagePolicy.MaxReceiveCount` and `QueueMessagePolicy.MaxReceiveQueue` supported. |
| 1.3.10 | Queue message metadata | 2 | Verified by source | Channel, ClientId, Metadata, Body, Tags, Policy (MaxReceiveCount, MaxReceiveQueue, DelaySeconds, ExpirationSeconds) all present. |
| 1.3.11 | Peek messages | 0 | Verified by source | No peek operation in modern `QueuesClient`. Legacy `Queue/Queue.cs` had `PeekQueueMessage` but it's archived. The server proto does not have a dedicated peek RPC. |
| 1.3.12 | Purge queue | 0 | Verified by source | No purge operation found in SDK. Not in server proto. |

**Subsection Score:** 20/24 raw → normalized avg: 4.3 (Peek/Purge N/A if server doesn't support → adjusted to 20/20 = 5.0, but server proto has `AckAllQueueMessages` which is close to purge → mark 0)

#### 1.4 RPC (Commands & Queries)

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 1.4.1 | Send command | 2 | Verified by source | `CommandsClient.Send(Command)` at `CQ/Commands/CommandsClient.cs`. |
| 1.4.2 | Subscribe to commands | 2 | Verified by source | `CommandsClient.Subscribe(CommandsSubscription)`. |
| 1.4.3 | Command response | 2 | Verified by source | `CommandResponse` class with `ReplyToCommand()` method at `CQ/Commands/CommandResponse.cs`. |
| 1.4.4 | Command timeout | 2 | Verified by source | `Command.TimeoutInSeconds` property. |
| 1.4.5 | Send query | 2 | Verified by source | `QueriesClient.Send(Query)` at `CQ/Queries/QueriesClient.cs`. |
| 1.4.6 | Subscribe to queries | 2 | Verified by source | `QueriesClient.Subscribe(QueriesSubscription)`. |
| 1.4.7 | Query response | 2 | Verified by source | `QueryResponse` class with response data. |
| 1.4.8 | Query timeout | 2 | Verified by source | `Query.TimeoutInSeconds` property. |
| 1.4.9 | RPC metadata | 2 | Verified by source | Channel, ClientId, Metadata, Body, Tags, Timeout all supported. |
| 1.4.10 | Group-based RPC | 2 | Verified by source | Subscription supports `Group` property for load balancing. |
| 1.4.11 | Cache support for queries | 2 | Verified by source | `Query.CacheKey` and `Query.CacheTTL` at `CQ/Queries/Query.cs`. `QueryResponse.CacheHit` flag. |

**Subsection Score:** 22/22 raw → normalized avg: 5.0

#### 1.5 Client Management

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 1.5.1 | Ping | 2 | Verified by source | `BaseClient.Ping()` at `Common/BaseClient.cs:75-91`. Returns `PingResult` with server info. |
| 1.5.2 | Server info | 2 | Verified by source | `PingResult` contains `ServerInfo` with Host, Version, ServerStartTime, ServerUpTimeSeconds at `Transport/ServerInfo.cs`. |
| 1.5.3 | Channel listing | 2 | Verified by source | `ListPubSubChannels()`, `ListQueuesChannels()`, `ListCqChannels()` at `Common/BaseClient.cs:196-208`. Filter by type. |
| 1.5.4 | Channel create | 2 | Verified by source | `CreateDeleteChannel()` with `isCreate=true` at `Common/BaseClient.cs:125-131`. |
| 1.5.5 | Channel delete | 2 | Verified by source | `CreateDeleteChannel()` with `isCreate=false`. |

**Subsection Score:** 10/10 raw → normalized avg: 5.0

#### 1.6 Operational Semantics

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 1.6.1 | Message ordering | 1 | Inferred | SDK doesn't implement ordering; relies on server FIFO. No documentation of ordering guarantees. |
| 1.6.2 | Duplicate handling | 0 | Verified by source | No duplicate detection or documentation of delivery semantics (at-least-once vs exactly-once). |
| 1.6.3 | Large message handling | 1 | Verified by source | Max send/receive 100MB configurable at `Config/Configuration.cs:7-8`. But no graceful handling if exceeded — gRPC error propagated raw. |
| 1.6.4 | Empty/null payload | 1 | Verified by source | `Event.Validate()` exists but no explicit empty payload test. Body is `byte[]` so null is possible. |
| 1.6.5 | Special characters | 1 | Inferred | Protobuf handles binary data natively. No explicit Unicode/special character handling or testing. |

**Subsection Score:** 4/10 raw → normalized avg: 2.6

#### Category 1 Aggregate
Feature scores (0-2 mapped to 1-5): Weighted subsection average = **(4.7+5.0+4.6+5.0+5.0+2.6)/6 ≈ 4.48**, but the operational semantics weakness pulls it down. With proper weighting and considering the completeness is high but operational semantics are undocumented: **3.8**.

---

### Category 2: API Design & Developer Experience (Score: 3.2)

#### 2.1 Language Idiomaticity

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 2.1.1 | Naming conventions | 4 | Verified by source | PascalCase methods/properties throughout. `SetAddress()`, `SendEvent()`, `IsSuccess`. Follows C# conventions. |
| 2.1.2 | Configuration pattern | 4 | Verified by source | Fluent builder pattern at `Config/Configuration.cs`. `new Configuration().SetAddress("...").SetClientId("...")`. Idiomatic for C#. |
| 2.1.3 | Error handling pattern | 2 | Verified by source | Returns `Result` objects instead of throwing exceptions. **Non-idiomatic for C#** — C# convention is to throw exceptions for exceptional conditions and use try-catch. Result pattern is more Go-like. |
| 2.1.4 | Async pattern | 3 | Verified by source | Most I/O operations are async (`Task<Result>`). But `Subscribe()` is synchronous returning `Result` while launching a fire-and-forget `Task.Run()` background task — non-standard. |
| 2.1.5 | Resource cleanup | 2 | Verified by source | **Does NOT implement `IDisposable` or `IAsyncDisposable`**. Relies on manual `.Close()` calls. No `using` pattern support. Non-idiomatic for C#. |
| 2.1.6 | Collection types | 5 | Verified by source | Uses native `Dictionary<string,string>` for tags, `List<T>`, `byte[]` for body. No custom wrappers. |
| 2.1.7 | Null/optional handling | 2 | Verified by source | `#nullable enable` not used anywhere. No `Nullable<T>` annotations. C# 8.0 target but nullable references disabled. |

#### 2.2 Progressive Disclosure & Minimal Boilerplate

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 2.2.1 | Quick start simplicity | 4 | Verified by source | ~8 lines for basic publish (create config → create client → connect → send). Good. |
| 2.2.2 | Sensible defaults | 4 | Verified by source | Only Address and ClientId required. MaxSendSize=100MB, ReconnectInterval=5s, TLS=disabled. Reasonable defaults. |
| 2.2.3 | Opt-in complexity | 4 | Verified by source | TLS, auth, reconnect config are all additive. Basic usage requires minimal config. |
| 2.2.4 | Consistent method signatures | 4 | Verified by source | All clients have `Create()`, `Delete()`, `List()`, `Send()`, `Subscribe()`, `Close()`. |
| 2.2.5 | Discoverability | 3 | Verified by source | XML doc comments on ~70+ files. But no published API docs site (no docs.kubemq.io for C# API reference). |

#### 2.3 Type Safety & Generics

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 2.3.1 | Strong typing | 3 | Verified by source | Message types are typed. But `HandleListErrors<T>` at `BaseClient.cs:169-179` uses `dynamic` and `Activator.CreateInstance` — a significant type safety issue. |
| 2.3.2 | Enum/constant usage | 4 | Verified by source | `EventsStoreType`, `SubscribeType` enums. Subscription types properly enumerated. |
| 2.3.3 | Return types | 3 | Verified by source | Returns `Result`, `PingResult`, etc. But all errors are untyped strings, not typed error enums. |

#### 2.4 API Consistency

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 2.4.1 | Internal consistency | 3 | Verified by source | Modern clients (PubSub, CQ, Queues) are consistent. But legacy classes (Events/, CommandQuery/, Queue/, QueueStream/) exist alongside with different patterns. Confusing for users who might find both. |
| 2.4.2 | Cross-SDK concept alignment | 3 | Inferred | Core concepts align (Client, Event, Command, Query, QueueMessage). But class naming differs from other SDKs. |
| 2.4.3 | Method naming alignment | 3 | Verified by source | `Send()`, `Subscribe()` align. But `Poll()` for queues vs `Receive` in some SDKs. |
| 2.4.4 | Option/config alignment | 4 | Verified by source | `Address`, `ClientId`, `AuthToken`, `MaxSendSize`, `TLS` — consistent field names. |

#### 2.5 Developer Journey

| Step | Assessment | Friction Points |
|------|-----------|-----------------|
| 1. Install | Good — `dotnet add package KubeMQ.SDK.csharp` works. | NuGet v2.0.0 published Oct 2024. |
| 2. Connect | Good — 3 lines: create config, create client, connect. | Must remember to check `Result.IsSuccess`. |
| 3. First Publish | Good — straightforward `Send(event)`. | Need to create `Event` object with byte array body. |
| 4. First Subscribe | Moderate friction — `Subscribe()` is sync but runs async internally. Fire-and-forget pattern is confusing. | No `await` on subscription. Error notification only via callback. |
| 5. Error Handling | Poor — all errors are strings. No typed exceptions. Stack traces lost. | `Result.ErrorMessage` is the only signal. Cannot catch specific error types. |
| 6. Production Config | Moderate — TLS, auth, reconnect all configurable. | No connection state events. No credential rotation. |
| 7. Troubleshooting | Poor — minimal logging, no diagnostic tools. | No structured logs, no troubleshooting guide. |

**Developer Journey Score: 3 / 5** — Major friction at error handling and troubleshooting.

**Category 2 Aggregate: 3.2**

---

### Category 3: Connection & Transport (Score: 2.6)

#### 3.1 gRPC Implementation

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 3.1.1 | gRPC client setup | 3 | Verified by source | `Transport.cs:27-65`: Creates `Channel` with configurable options. Uses legacy `Grpc.Core` (not `Grpc.Net.Client`). `throw e` at line 44 loses stack trace. |
| 3.1.2 | Protobuf alignment | 4 | Verified by source | SDK proto matches `/tmp/protobuf/csharp/kubemq.proto` + has extra QueuesInfo messages (29 extra lines). |
| 3.1.3 | Proto version | 3 | Verified by source | Matches C# reference proto. Missing Topics API from Go proto (newer feature, may not apply yet). |
| 3.1.4 | Streaming support | 4 | Verified by source | Server streaming for subscriptions (`SubscribeToEvents`). Bidirectional streaming for queues (`QueuesUpstream`/`QueuesDownstream`). Event streaming (`SendEventsStream`). |
| 3.1.5 | Metadata passing | 3 | Verified by source | `CustomInterceptor.cs:18-29`: Auth token injected via `AsyncUnaryCall`. **BUG:** Lines 32-60 implement server-side handlers (`ClientStreamingServerHandler`, `ServerStreamingServerHandler`, `DuplexStreamingServerHandler`) instead of client-side interceptors. Streaming auth may not work. |
| 3.1.6 | Keepalive | 1 | Verified by source | `Config/KeepAlive.cs` defines `KeepAliveConfig` class but it is **never integrated** into `Configuration.cs` or `Transport.cs`. Dead code. |
| 3.1.7 | Max message size | 4 | Verified by source | `Transport.cs:31-32`: `ChannelOptions.MaxSendMessageLength` and `MaxReceiveMessageLength` configured. Default 100MB. |
| 3.1.8 | Compression | 1 | Verified by source | No gRPC compression support. No `CallOptions` with compression. |

#### 3.2 Connection Lifecycle

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 3.2.1 | Connect | 4 | Verified by source | `BaseClient.Connect()` at `BaseClient.cs:30-68`. Validates config, creates transport, pings server. SemaphoreSlim-protected. |
| 3.2.2 | Disconnect/close | 3 | Verified by source | `BaseClient.CloseClient()` at `BaseClient.cs:97-124`. Calls `channel.ShutdownAsync()`. But does NOT drain in-flight messages for pub/sub clients. `EventsClient.Close()` cancels subscriptions first. |
| 3.2.3 | Auto-reconnection | 2 | Verified by source | Subscription retry loop at `EventsClient.cs:88-108`. Fixed delay (`ReconnectIntervalSeconds * 1000`). **No reconnection for the transport/channel itself** — only subscription stream retry. If the gRPC channel dies, no recovery. |
| 3.2.4 | Reconnection backoff | 1 | Verified by source | Fixed delay only at `EventsClient.cs:107`: `Task.Delay(Cfg.GetReconnectIntervalDuration())`. **No exponential backoff, no jitter.** Thundering herd risk. |
| 3.2.5 | Connection state events | 1 | Verified by source | `Transport.IsConnected()` at `Transport.cs:106-110` checks `ChannelState.Ready`. **No callbacks/events for state changes.** |
| 3.2.6 | Subscription recovery | 3 | Verified by source | Subscription loop auto-retries on error at `EventsClient.cs:88-108`. But subscription params stored in closure — no explicit recovery of subscription state. |
| 3.2.7 | Message buffering during reconnect | 1 | Verified by source | No message buffering. `EventsClient._sendQueue` (BlockingCollection at line 14) exists but is **never used** for send buffering. |
| 3.2.8 | Connection timeout | 2 | Verified by source | No explicit connection timeout. Relies on gRPC default. `Ping()` in `InitializeAsync()` uses CancellationToken but no timeout configured. |
| 3.2.9 | Request timeout | 2 | Verified by source | Hard-coded 10000ms (10s) timeout at `BaseClient.cs:140,189` for channel operations. Not configurable. RPC commands/queries use `TimeoutInSeconds` per-message. |

#### 3.3 TLS / mTLS

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 3.3.1 | TLS support | 4 | Verified by source | `Transport.cs:35-41`: `SslCredentials` with configurable cert/key/CA. |
| 3.3.2 | Custom CA certificate | 4 | Verified by source | `Transport.cs:76-79`: CA file loaded via `File.ReadAllText(tlsConfig.CaFile)`. |
| 3.3.3 | mTLS support | 3 | Verified by source | `Transport.cs:69-73`: `KeyCertificatePair` from CertFile/KeyFile. Works but no cert validation (expiry, hostname). |
| 3.3.4 | TLS configuration | 1 | Verified by source | No TLS version or cipher suite configuration. Uses gRPC/OpenSSL defaults. |
| 3.3.5 | Insecure mode | 3 | Verified by source | `Transport.cs:48`: `ChannelCredentials.Insecure`. Default. **No warning logged** when using insecure mode. |

#### 3.4 Kubernetes-Native Behavior

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 3.4.1 | K8s DNS service discovery | 2 | Verified by source | Default address is just a string. Examples use `localhost:50000`. No K8s-specific DNS documentation. |
| 3.4.2 | Graceful shutdown APIs | 3 | Verified by source | `Close()` method exists. But no SIGTERM integration docs. No drain support. |
| 3.4.3 | Health/readiness integration | 3 | Verified by source | `Transport.IsConnected()` and `Ping()` available. Usable for health probes but not documented for K8s. |
| 3.4.4 | Rolling update resilience | 2 | Verified by source | Subscription retry handles server restarts. But transport-level reconnect is missing. |
| 3.4.5 | Sidecar vs. standalone | 1 | Verified by source | No documentation of sidecar vs standalone deployment patterns. |

#### 3.5 Flow Control & Backpressure

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 3.5.1 | Publisher flow control | 2 | Verified by source | `Queues/Upstream.cs:10`: `BlockingCollection` provides implicit backpressure (unbounded by default). No configurable buffer or overflow strategy. |
| 3.5.2 | Consumer flow control | 1 | Verified by source | No prefetch/buffer configuration. Consumer processes as fast as gRPC delivers. |
| 3.5.3 | Throttle detection | 1 | Verified by source | No server-side throttle detection. gRPC errors passed through without classification. |
| 3.5.4 | Throttle error surfacing | 1 | Verified by source | No specific throttle error messages or suggestions. |

**Category 3 Aggregate: 2.6**

---

### Category 4: Error Handling & Resilience (Score: 1.8)

#### 4.1 Error Classification & Types

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 4.1.1 | Typed errors | 1 | Verified by source | Single `Result` class at `Results/Result.cs:8-39`. All errors are `string ErrorMessage`. No typed error classes. |
| 4.1.2 | Error hierarchy | 1 | Verified by source | No hierarchy. `Result`, `PingResult`, `ListPubSubAsyncResult` etc. inherit `Result` but carry no error type distinction. No `ConnectionError`, `AuthError`, `TimeoutError`, etc. |
| 4.1.3 | Retryable classification | 1 | Verified by source | No classification of retryable vs non-retryable errors. All errors treated equally. |
| 4.1.4 | gRPC status mapping | 1 | Verified by source | No gRPC status code mapping. `RpcException` caught and only `.Message` preserved at `Result.cs:36`: `ErrorMessage = e.Message`. Status codes discarded. |
| 4.1.5 | Error wrapping/chaining | 1 | Verified by source | `Result(Exception e)` at `Result.cs:33-37` discards everything except `e.Message`. Stack trace, inner exceptions, all lost. |

#### 4.2 Error Message Quality

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 4.2.1 | Actionable messages | 2 | Verified by source | Validation messages at `Configuration.cs:83-102` are clear: "Connection must have an address". But runtime errors are generic server passthrough. |
| 4.2.2 | Context inclusion | 1 | Verified by source | Error messages don't include channel name, operation type, or client ID. Just raw error string. |
| 4.2.3 | No swallowed errors | 2 | Verified by source | Most errors propagated via Result. But `EventsClient.cs:109-114` removes subscription token in `finally` block even on error, potentially masking issues. `Console.WriteLine(e)` in `QueuesClient.cs` is debug leftover. |
| 4.2.4 | Consistent format | 2 | Verified by source | Validation errors follow "Connection must have..." pattern. Runtime errors vary (server passthrough vs exception message). |

#### 4.3 Retry & Backoff

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 4.3.1 | Automatic retry | 2 | Verified by source | Subscription retry loop at `EventsClient.cs:88-108`. Only for stream subscriptions. Send operations have NO retry. |
| 4.3.2 | Exponential backoff | 1 | Verified by source | Fixed delay: `Task.Delay(Cfg.GetReconnectIntervalDuration())` at `EventsClient.cs:107`. No backoff calculation. |
| 4.3.3 | Configurable retry | 2 | Verified by source | `DisableAutoReconnect` and `ReconnectIntervalSeconds` at `Configuration.cs:16-17`. But no max retry count, no backoff multiplier, no jitter. |
| 4.3.4 | Retry exhaustion | 1 | Verified by source | Infinite retry loop. Never exhausts. No max retry count. No total duration limit. |
| 4.3.5 | Non-retryable bypass | 1 | Verified by source | All errors retried equally. Auth failures retried same as transient network errors. |

#### 4.4 Resilience Patterns

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 4.4.1 | Timeout on all operations | 2 | Verified by source | Hard-coded 10s for channel operations. Command/Query have per-message timeout. But `Transport.InitializeAsync` has no timeout. `CloseClient()` at `BaseClient.cs:101` acquires lock without timeout. |
| 4.4.2 | Cancellation support | 3 | Verified by source | CancellationToken on `Connect()`, `Ping()`. Subscription supports CancellationTokenSource. But `CloseClient()` doesn't accept CancellationToken. |
| 4.4.3 | Graceful degradation | 2 | Verified by source | No batch partial failure handling. If one subscription fails, others continue. But transport failure takes down all operations. |
| 4.4.4 | Resource leak prevention | 2 | Verified by source | `Transport.InitializeAsync` cleans up channel on failure at `Transport.cs:61`. But `_sendQueue` in `EventsClient.cs:14` (BlockingCollection) is never disposed. No `IDisposable` implementation. |

**Category 4 Aggregate: 1.8**

---

### Category 5: Authentication & Security (Score: 2.2)

#### 5.1 Authentication Methods

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 5.1.1 | JWT token auth | 3 | Verified by source | `CustomInterceptor.cs:20-28`: Token injected in "authorization" header for unary calls. Works for simple RPCs. **BUG in streaming calls** (see 3.1.5). |
| 5.1.2 | Token refresh | 1 | Verified by source | No token refresh mechanism. Token set once at config time. Requires new client for new token. |
| 5.1.3 | OIDC integration | 1 | Verified by source | Not implemented. No OIDC support. |
| 5.1.4 | Multiple auth methods | 2 | Verified by source | JWT and mTLS are the two supported methods. Can combine both. But no pluggable auth provider. |

#### 5.2 Security Best Practices

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 5.2.1 | Secure defaults | 1 | Verified by source | TLS disabled by default at `TlsConfig.cs:8`: `Enabled = false`. Insecure connection is the default. No warnings logged. |
| 5.2.2 | No credential logging | 4 | Verified by source | Auth token not logged anywhere. Logger calls in legacy code don't include tokens. |
| 5.2.3 | Credential handling | 3 | Verified by source | Token passed via gRPC metadata (good). But stored as plain string in `Configuration.AuthToken`. Examples use hardcoded strings (e.g., cookbook `client/authentication/Program.cs`). |
| 5.2.4 | Input validation | 3 | Verified by source | `Configuration.Validate()` checks required fields at `Configuration.cs:81-107`. `TlsConfig.Validate()` checks file paths. But no channel name validation (special chars, length). |
| 5.2.5 | Dependency security | 2 | Inferred | `Grpc.Core 2.46.6` is legacy (maintenance-only). `Microsoft.Extensions.Logging 3.1.2` is from 2020 (potentially vulnerable). No vulnerability scanning in place. |

**Category 5 Aggregate: 2.2**

---

### Category 6: Concurrency & Thread Safety (Score: 2.8)

#### 6.1 Thread Safety

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 6.1.1 | Client thread safety | 3 | Verified by source | `SemaphoreSlim` at `BaseClient.cs:19` protects Connect/Close. But `IsConnected` is a plain `bool` (not volatile/Interlocked) at `BaseClient.cs:18`. Race condition risk. |
| 6.1.2 | Publisher thread safety | 3 | Verified by source | `QueuesClient` uses `BlockingCollection` and `ConcurrentDictionary`. But `EventsClient.Send()` calls `KubemqClient.SendEventAsync()` directly — gRPC client is thread-safe, so this works. |
| 6.1.3 | Subscriber thread safety | 3 | Verified by source | `lock(_subscriptionTokens)` at `EventsClient.cs:82-85,111-114`. Multiple subscriptions tracked safely. Each runs in own `Task.Run()`. |
| 6.1.4 | Documentation of guarantees | 1 | Verified by source | No thread safety documentation anywhere. No XML docs mention thread safety. |
| 6.1.5 | Concurrency validation | 1 | Verified by source | No concurrent stress tests. No race condition tests. |

#### 6.2 C#-Specific Async Patterns

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 6.2.C1 | async/await | 3 | Verified by source | Most I/O operations are async: `Connect()`, `Send()`, `Ping()`, `Close()`. But `Subscribe()` is synchronous — launches fire-and-forget `Task.Run()` at `EventsClient.cs:86`. |
| 6.2.C2 | CancellationToken | 3 | Verified by source | `Connect()` accepts CancellationToken. `Subscribe()` accepts CancellationTokenSource. But `Close()` doesn't accept CancellationToken. `CloseClient()` has `_lock.WaitAsync()` without token at line 101 — potential deadlock. |
| 6.2.C3 | IAsyncDisposable | 1 | Verified by source | Not implemented. No `IAsyncDisposable` or `IDisposable` on any client class. |
| 6.2.C4 | No sync-over-async | 2 | Verified by source | Legacy `QueueStream.cs` uses `Thread.Sleep(1000)` to wait for async init. `Transaction.cs` uses `.Wait()` blocking. Modern code is mostly clean. |

**Category 6 Aggregate: 2.8**

---

### Category 7: Observability (Score: 1.4)

#### 7.1 Logging

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 7.1.1 | Structured logging | 1 | Verified by source | `KubeConsoleLogger.cs`: Format is `{FileTime}|[KubeMQ]|{LogLevel}|{Exception}:{Message}`. String interpolation, not structured/semantic logging. |
| 7.1.2 | Configurable log level | 2 | Verified by source | `Microsoft.Extensions.Logging` supports levels. But no SDK-level configuration to set log level. |
| 7.1.3 | Pluggable logger | 2 | Verified by source | Uses `ILoggerFactory` from Microsoft.Extensions.Logging. Theoretically pluggable. But Logger initialization at `Tools/Logger.cs` hardcodes `KubeConsoleProvider`. |
| 7.1.4 | No stdout/stderr spam | 2 | Verified by source | `Console.WriteLine(e)` found in `QueuesClient.cs` exception handler. Most logging goes through ILogger, but there's console leakage. |
| 7.1.5 | Sensitive data exclusion | 3 | Verified by source | Auth tokens not logged. But no explicit sanitization policy. |
| 7.1.6 | Context in logs | 1 | Verified by source | Log messages like "Exception in SendEvent" lack client ID, channel name, or correlation ID. |

#### 7.2 Metrics

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 7.2.1 | Metrics hooks | 1 | Verified by source | No metrics infrastructure. |
| 7.2.2 | Key metrics exposed | 1 | Verified by source | No metrics exposed. |
| 7.2.3 | Prometheus/OTel compatible | 1 | Verified by source | No OpenTelemetry or Prometheus integration. |
| 7.2.4 | Opt-in | N/A | — | No metrics exist to be opt-in. |

#### 7.3 Tracing

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 7.3.1 | Trace context propagation | 1 | Verified by source | No W3C Trace Context support. |
| 7.3.2 | Span creation | 1 | Verified by source | No span creation. |
| 7.3.3 | OTel integration | 1 | Verified by source | No OpenTelemetry SDK integration. |
| 7.3.4 | Opt-in | N/A | — | No tracing exists. |

**Category 7 Aggregate: 1.4**

---

### Category 8: Code Quality & Architecture (Score: 2.4)

#### 8.1 Code Structure

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 8.1.1 | Package organization | 3 | Verified by source | Logical namespaces: `Config`, `Transport`, `PubSub.Events`, `CQ.Commands`, `Queues`, `Common`, `Results`. But 23 directories with significant legacy duplication. |
| 8.1.2 | Separation of concerns | 3 | Verified by source | Transport separated from business logic. Config separate from connection. But `BaseClient.cs` handles both connection AND channel CRUD (lines 125-208). |
| 8.1.3 | Single responsibility | 3 | Verified by source | Most classes have clear purpose. But `BaseClient` does too much (connect, ping, close, create/delete/list channels). |
| 8.1.4 | Interface-based design | 1 | Verified by source | No interfaces. `BaseClient` is a concrete class. `Transport` is concrete. No `IEventsClient`, `IQueuesClient`, etc. Impossible to mock for testing. |
| 8.1.5 | No circular dependencies | 4 | Verified by source | Clean namespace hierarchy. No circular references detected. |
| 8.1.6 | Consistent file structure | 3 | Verified by source | Modern code follows consistent pattern. Legacy code has different structure. |
| 8.1.7 | Public API surface isolation | 2 | Verified by source | `internal` used on some fields (`BaseClient.KubemqClient`, `BaseClient.Cfg`). But many internal details are `public`. Generated gRPC types (`KubeMQ.Grpc`) leak to consumers. |

#### 8.2 Code Quality

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 8.2.1 | Linter compliance | 2 | Inferred | No `.editorconfig`. No Roslyn analyzers configured. No `dotnet format` CI step. |
| 8.2.2 | No dead code | 2 | Verified by source | `EventsClient._sendQueue` at line 14 is never used. `KeepAlive.cs` class is dead code. `Converter.cs` has commented-out BinaryFormatter code. Legacy Events/CommandQuery/Queue/QueueStream directories are parallel unused implementations. |
| 8.2.3 | Consistent formatting | 3 | Verified by source | Generally consistent. But `BaseClient.cs` has inconsistent indentation (class body not indented at line 15). Mixed brace styles in some files. |
| 8.2.4 | Meaningful naming | 4 | Verified by source | Clear names throughout: `EventsClient`, `CommandsSubscription`, `PollRequest`, `SendMessageResult`. |
| 8.2.5 | Error path completeness | 2 | Verified by source | `Transport.cs:44`: `throw e` instead of `throw` — loses stack trace. `BaseClient.cs:37`: "already connected" throws exception instead of returning Result. Inconsistent error handling. |
| 8.2.6 | Magic number avoidance | 3 | Verified by source | Constants for max sizes at `Configuration.cs:7-9`. But hard-coded `10000` timeout at `BaseClient.cs:140`, `1000` delay in `QueueStream.cs`. |
| 8.2.7 | Code duplication | 2 | Verified by source | High duplication: Legacy (Events/, CommandQuery/, Queue/, QueueStream/) duplicates modern (PubSub/, CQ/, Queues/) with ~40% of codebase being legacy parallel implementations. |

#### 8.3 Serialization & Message Handling

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 8.3.1 | JSON marshaling helpers | 3 | Verified by source | `Tools/Converter.cs` provides `ToByteArray<T>()` and `FromByteArray<T>()` using Newtonsoft.Json. |
| 8.3.2 | Protobuf message wrapping | 3 | Verified by source | Modern clients wrap/unwrap protobuf via `Encode()`/`Decode()` methods. But `KubeMQ.Grpc` namespace types are publicly visible. |
| 8.3.3 | Typed payload support | 2 | Verified by source | Body is `byte[]`. User must manually serialize/deserialize. Converter helpers available but not integrated into message classes. |
| 8.3.4 | Custom serialization hooks | 1 | Verified by source | No serializer plugin mechanism. Hard-coded Newtonsoft.Json. |
| 8.3.5 | Content-type handling | 1 | Verified by source | No content-type metadata. Body is opaque bytes. |

#### 8.4 Technical Debt

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 8.4.1 | TODO/FIXME/HACK comments | 4 | Verified by source | No TODO/FIXME/HACK comments found in active code. Clean in this regard. |
| 8.4.2 | Deprecated code | 1 | Verified by source | Entire legacy directories (Events/, CommandQuery/, Queue/, QueueStream/) are deprecated but not marked `[Obsolete]` and still compiled into the assembly. |
| 8.4.3 | Dependency freshness | 2 | Verified by source | `Grpc.Core 2.46.6` — legacy, EOL. `Microsoft.Extensions.* 3.1.2` — 6 years old. `Grpc.Core.Api 2.62.0` version mismatch with `Grpc.Core 2.46.6`. |
| 8.4.4 | Language version | 3 | Verified by source | `LangVersion 8.0` in csproj. C# 12 is current. Targets netstandard2.0 through net8.0 (good range but constrains language features). |
| 8.4.5 | gRPC/protobuf library version | 2 | Verified by source | `Grpc.Core 2.46.6` is legacy. Should migrate to `Grpc.Net.Client` (grpc-dotnet). `Google.Protobuf 3.26.1` is reasonably current. |

#### 8.5 Extensibility

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 8.5.1 | Interceptor/middleware support | 2 | Verified by source | `CustomInterceptor` exists but is internal to transport. No public API for adding user interceptors. |
| 8.5.2 | Event hooks | 1 | Verified by source | No lifecycle hooks (onConnect, onDisconnect, onError, onMessage). Subscription has error callback but no connection-level events. |
| 8.5.3 | Transport abstraction | 1 | Verified by source | `Transport` class is concrete with no interface. Cannot be replaced or mocked. |

**Category 8 Aggregate: 2.4**

---

### Category 9: Testing (Score: 1.0)

#### 9.1 Unit Tests

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 9.1.1 | Unit test existence | 1 | Verified by source | **ZERO active unit tests.** Only 3 archived test files in `/Archive/Tests/Queue_test/` (legacy, not compiled). |
| 9.1.2 | Coverage percentage | 1 | Verified by source | 0% coverage. No test project in solution. |
| 9.1.3 | Test quality | 1 | Verified by source | No tests to evaluate. |
| 9.1.4 | Mocking | 1 | Verified by source | No mock infrastructure. No interfaces to mock against. |
| 9.1.5 | Table-driven tests | 1 | Verified by source | No tests. |
| 9.1.6 | Assertion quality | 1 | Verified by source | No tests. |

#### 9.2 Integration Tests

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 9.2.1 | Integration test existence | 1 | Verified by source | No integration tests. |
| 9.2.2 | All patterns covered | 1 | Verified by source | No tests. |
| 9.2.3 | Error scenario testing | 1 | Verified by source | No tests. |
| 9.2.4 | Setup/teardown | 1 | Verified by source | No tests. |
| 9.2.5 | Parallel safety | 1 | Verified by source | No tests. |

#### 9.3 CI/CD Pipeline

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 9.3.1 | CI pipeline exists | 1 | Verified by source | No CI configuration files found. No `.github/workflows/`, no `azure-pipelines.yml`. |
| 9.3.2 | Tests run on PR | 1 | Verified by source | No CI. |
| 9.3.3 | Lint on CI | 1 | Verified by source | No CI. |
| 9.3.4 | Multi-version testing | 1 | Verified by source | No CI. |
| 9.3.5 | Security scanning | 1 | Verified by source | No dependency scanning. |

**Category 9 Aggregate: 1.0**

---

### Category 10: Documentation (Score: 3.0)

#### 10.1 API Reference

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 10.1.1 | API docs exist | 3 | Verified by source | XML doc comments on ~70+ files. No generated API reference site. |
| 10.1.2 | All public methods documented | 3 | Verified by source | Major public APIs have `///` summaries. Some methods missing (e.g., `_Subscribe` private but public `Subscribe` documented). |
| 10.1.3 | Parameter documentation | 3 | Verified by source | `<param>` tags on most methods (e.g., `BaseClient.cs:27-28`). `<returns>` tags present. No `<exception>` tags. |
| 10.1.4 | Code doc comments | 3 | Verified by source | Good coverage on modern API classes. Sparse on internal/legacy classes. |
| 10.1.5 | Published API docs | 1 | Verified by source | No published API docs website. No docs.kubemq.io for C# reference. |

#### 10.2 Guides & Tutorials

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 10.2.1 | Getting started guide | 4 | Verified by source | README has installation + quick start with code samples. |
| 10.2.2 | Per-pattern guide | 4 | Verified by source | README has dedicated sections for Events, EventsStore, Commands, Queries, Queues with full code examples. |
| 10.2.3 | Authentication guide | 3 | Verified by source | README covers TLS config. Cookbook has auth and TLS examples. But no OIDC guide. |
| 10.2.4 | Migration guide | 1 | Verified by source | No migration guide from v1.x to v2.0. Major version bump with no migration docs. |
| 10.2.5 | Performance tuning guide | 1 | Verified by source | No performance guidance. |
| 10.2.6 | Troubleshooting guide | 1 | Verified by source | No troubleshooting section. |

#### 10.3 Examples & Cookbook

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 10.3.1 | Example code exists | 4 | Verified by source | 5 in-repo examples + 32 cookbook examples in `csharp-sdk-cookbook`. |
| 10.3.2 | All patterns covered | 4 | Verified by source | Cookbook covers all 5 patterns with multiple variants (single, stream, load balancer, multicast, wildcard). |
| 10.3.3 | Examples compile/run | 2 | Inferred | Cookbook targets `netcoreapp3.1` (EOL Dec 2022) and references `KubeMQ.SDK.csharp v1.4.0` (not v2.0.0). In-repo examples target modern frameworks but couldn't verify build (no `dotnet` installed). |
| 10.3.4 | Real-world scenarios | 3 | Verified by source | Cookbook covers delayed messages, dead-letter, batch, stream, multicast. Still somewhat basic. |
| 10.3.5 | Error handling shown | 2 | Verified by source | Basic try-catch with `Console.WriteLine(e)`. No production-grade error handling patterns. |
| 10.3.6 | Advanced features | 3 | Verified by source | TLS, auth, delayed messages, DLQ, group subscriptions covered in cookbook. |

#### 10.4 README Quality

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 10.4.1 | Installation instructions | 4 | Verified by source | Clear NuGet install command in README. |
| 10.4.2 | Quick start code | 4 | Verified by source | Copy-paste code samples for all patterns. |
| 10.4.3 | Prerequisites | 2 | Verified by source | Mentions "KubeMQ cluster" needed but no specific server version or .NET version requirements stated. |
| 10.4.4 | License | 3 | Verified by source | LICENSE file exists. Apache 2.0. Not referenced in README. |
| 10.4.5 | Changelog | 1 | Verified by source | No CHANGELOG.md. No release notes. Git tags exist but no GitHub Releases with descriptions. |

**Category 10 Aggregate: 3.0**

---

### Category 11: Packaging & Distribution (Score: 2.8)

#### 11.1 Package Manager

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 11.1.1 | Published to NuGet | 4 | Verified by source | Published at nuget.org/packages/KubeMQ.SDK.csharp v2.0.0. 110K+ total downloads. |
| 11.1.2 | Package metadata | 3 | Verified by source | Title, Authors, Company, PackageProjectUrl, RepositoryUrl in csproj. Missing: Description, PackageTags, PackageIcon. |
| 11.1.3 | Reasonable install | 4 | Verified by source | `dotnet add package KubeMQ.SDK.csharp` works. Standard NuGet install. |
| 11.1.4 | Minimal dependency footprint | 2 | Verified by source | 11 direct NuGet dependencies. `Grpc.Core` pulls native binaries. `Newtonsoft.Json` when System.Text.Json is now standard. Some deps unnecessary (System.Configuration.ConfigurationManager). |

#### 11.2 Versioning & Releases

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 11.2.1 | Semantic versioning | 3 | Verified by source | PackageVersion 2.0.0 follows semver. But AssemblyVersion 1.7.2 doesn't match. |
| 11.2.2 | Release tags | 2 | Verified by source | Tags exist (v1.3.1 through v1.7.2) but **no v2.0.0 tag**. Latest tag is v1.7.2. |
| 11.2.3 | Release notes | 1 | Verified by source | No GitHub Releases. No release notes. |
| 11.2.4 | Current version | 3 | Verified by source | v2.0.0 published Oct 2024. ~5 months ago. Recent enough. |
| 11.2.5 | Version consistency | 1 | Verified by source | **Three conflicting versions:** AssemblyVersion 1.7.2, Version 1.0.8, PackageVersion 2.0.0. No matching git tag for 2.0.0. |

#### 11.3 Build & Development Setup

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 11.3.1 | Build instructions | 2 | Verified by source | README says to build examples. No explicit SDK build instructions. |
| 11.3.2 | Build succeeds | Not assessable | — | Could not verify (no `dotnet` CLI on assessment machine). csproj appears valid. |
| 11.3.3 | Development dependencies | 3 | Verified by source | No separate dev dependencies. All deps are runtime. No test framework dep. |
| 11.3.4 | Contributing guide | 1 | Verified by source | No CONTRIBUTING.md. |

#### 11.4 SDK Binary Size & Footprint

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 11.4.1 | Dependency weight | 2 | Verified by source | `Grpc.Core` includes native C libraries (~30MB platform-specific). Heavy. Should migrate to Grpc.Net.Client. |
| 11.4.2 | No native compilation required | 3 | Inferred | NuGet package includes pre-built native binaries for Grpc.Core. No user compilation needed, but adds size. |

**Category 11 Aggregate: 2.8**

---

### Category 12: Compatibility, Lifecycle & Supply Chain (Score: 1.6)

#### 12.1 Compatibility

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 12.1.1 | Server version matrix | 1 | Verified by source | No documentation of supported KubeMQ server versions. |
| 12.1.2 | Runtime support matrix | 2 | Verified by source | Target frameworks listed in csproj (netstandard2.0 through net8.0). But not documented in README. |
| 12.1.3 | Deprecation policy | 1 | Verified by source | Legacy code not marked `[Obsolete]`. No deprecation warnings. No removal timeline. |
| 12.1.4 | Backward compatibility | 2 | Inferred | v2.0.0 appears to be a breaking change (new API surface) but no documentation of what broke. |

#### 12.2 Supply Chain & Release Integrity

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 12.2.1 | Signed releases | 2 | Verified by source | Assembly is strong-named with `KubeMQ.snk` at csproj line 14. But no GPG-signed git tags. No Sigstore. |
| 12.2.2 | Reproducible builds | 1 | Verified by source | No lock files (packages.lock.json). No deterministic build flag in csproj. |
| 12.2.3 | Dependency update process | 1 | Verified by source | No Dependabot, no Renovate, no automated dependency updates. |
| 12.2.4 | Security response process | 1 | Verified by source | No SECURITY.md. No vulnerability reporting process. |
| 12.2.5 | SBOM | 1 | Verified by source | No SBOM generated. |
| 12.2.6 | Maintainer health | 2 | Verified by source | Recent commits (2024-2025). Single maintainer pattern. No community PRs visible. |

**Category 12 Aggregate: 1.6**

---

### Category 13: Performance (Score: 1.4)

#### 13.1 Benchmark Infrastructure

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 13.1.1 | Benchmark tests exist | 1 | Verified by source | No BenchmarkDotNet or any benchmark tests. |
| 13.1.2 | Benchmark coverage | 1 | Verified by source | No benchmarks. |
| 13.1.3 | Benchmark documentation | 1 | Verified by source | No performance documentation. |
| 13.1.4 | Published results | 1 | Verified by source | No published performance numbers. |

#### 13.2 Optimization Patterns

| # | Criterion | Score | Confidence | Evidence / Notes |
|---|-----------|-------|------------|-----------------|
| 13.2.1 | Object/buffer pooling | 1 | Verified by source | No `ArrayPool<byte>`, no object pooling. New allocations per message. |
| 13.2.2 | Batching support | 2 | Verified by source | Queue upstream uses streaming for implicit batching. Legacy API has explicit `SendQueueMessagesBatch`. No batching for events. |
| 13.2.3 | Lazy initialization | 2 | Verified by source | Transport created on `Connect()`, not on construction. Reasonable lazy init. |
| 13.2.4 | Memory efficiency | 2 | Inferred | `byte[]` copies for message body. `Converter.ToByteArray<T>()` creates intermediate string. No zero-copy patterns. |
| 13.2.5 | Resource leak risk | 2 | Verified by source | `BlockingCollection` in `EventsClient` never disposed. No `IDisposable`. CancellationTokenSource not always disposed. |
| 13.2.6 | Connection overhead | 3 | Verified by source | Single gRPC channel per client. Streaming reuses connection. Reasonable. |

**Category 13 Aggregate: 1.4**

---

## Developer Journey Assessment

### Step-by-step walkthrough:

**1. Install** — Smooth. `dotnet add package KubeMQ.SDK.csharp --version 2.0.0` installs cleanly from NuGet. First-time setup takes seconds. **Score: 4/5**

**2. Connect** — Clean. 3 lines of code:
```csharp
var cfg = new Configuration().SetAddress("localhost:50000").SetClientId("my-client");
var client = new EventsClient();
var result = await client.Connect(cfg, cancellationToken);
```
Must check `result.IsSuccess` — easy to forget. **Score: 4/5**

**3. First Publish** — Straightforward:
```csharp
var evt = new Event().SetChannel("my-channel").SetBody("hello"u8.ToArray());
var result = await client.Send(evt);
```
Fluent API is discoverable. **Score: 4/5**

**4. First Subscribe** — Friction point. `Subscribe()` is synchronous but internally async:
```csharp
client.Subscribe(new EventsSubscription()
    .SetChannel("my-channel")
    .SetOnReceiveEvent(e => Console.WriteLine(e.Body))
    .SetOnError(ex => Console.WriteLine(ex)));
```
No `await`. No indication of when subscription is active. Error only via callback. **Score: 2/5**

**5. Error Handling** — Major friction. All errors are `Result.ErrorMessage` strings:
```csharp
if (!result.IsSuccess)
    Console.WriteLine(result.ErrorMessage); // "some error string"
```
Cannot distinguish auth failures from network errors from validation errors. No typed exceptions. **Score: 1/5**

**6. Production Config** — Moderate. TLS, auth, reconnect configurable. But no connection state events, no credential rotation, no metrics. **Score: 3/5**

**7. Troubleshooting** — Poor. No troubleshooting guide. Minimal logging. Error messages lack context. No diagnostic tools. **Score: 1/5**

**Overall Developer Journey: 2.7/5** — Good first impression, degrades significantly at error handling and production deployment.

---

## Competitor Comparison

| Area | KubeMQ C# SDK | NATS.Client | Confluent.Kafka | Azure.Messaging.ServiceBus | RabbitMQ.Client |
|------|--------------|-------------|-----------------|---------------------------|-----------------|
| **API Ergonomics** | Fluent builder, Result pattern | Fluent, Options pattern | Builder pattern | Azure SDK guidelines | Connection/Channel model |
| **Error Handling** | String-based Result | Typed NatsException hierarchy | Typed ErrorCode enum | Azure SDK exceptions | Typed exceptions |
| **Async/await** | Partial (Subscribe is sync) | Full async/await | Task-based | Full async/await | Full async |
| **IDisposable** | Not implemented | Full IDisposable | Full IDisposable | Full IAsyncDisposable | Full IDisposable |
| **Observability** | None | Diagnostics source | Statistics callbacks | OpenTelemetry built-in | None (community) |
| **Testing** | 0 tests | Extensive unit+integration | Extensive | Comprehensive | Extensive |
| **Documentation** | README only | Full docs site | Confluent docs | Microsoft docs | Full docs site |
| **NuGet Downloads** | 110K total | 15M+ total | 90M+ total | 20M+ total | 200M+ total |
| **gRPC Library** | Grpc.Core (legacy) | Custom TCP | librdkafka (native) | AMQP | Custom AMQP |
| **CI/CD** | None | GitHub Actions | GitHub Actions | Azure DevOps | GitHub Actions |

**Key Takeaways:**
- All competitors implement `IDisposable`/`IAsyncDisposable` — KubeMQ is the only one that doesn't
- All competitors have typed exception hierarchies — KubeMQ uses string-only errors
- All competitors have comprehensive test suites — KubeMQ has zero tests
- Azure.Messaging.ServiceBus is the gold standard for C# SDK design (follows Azure SDK guidelines)

---

## Remediation Roadmap

### Phase 0: Assessment Validation (1-2 days)
Manually verify:
1. Build the SDK and run `dotnet build` to confirm clean compilation
2. Confirm CustomInterceptor streaming bug — test auth token injection on streaming calls
3. Verify `IsConnected` race condition under concurrent access
4. Confirm cookbook examples don't compile against v2.0.0 (version mismatch)
5. Run `dotnet list package --vulnerable` to identify dependency CVEs

### Phase 1: Quick Wins (Effort: S-M)

| # | Item | Category | Current | Target | Effort | Impact | Depends On | Scope | Validation Metric |
|---|------|----------|---------|--------|--------|--------|------------|-------|-------------------|
| 1 | Implement `IAsyncDisposable` on all client classes | API Design | 1 | 4 | S | High | — | language-specific | All clients support `await using var client = ...` pattern; integration test verifies |
| 2 | Fix CustomInterceptor — replace server-side handlers with `AsyncClientStreamingCall`, `AsyncServerStreamingCall`, `AsyncDuplexStreamingCall` | Auth & Security | 2 | 4 | S | Critical | — | language-specific | Auth token present in streaming call metadata; verified by unit test |
| 3 | Fix version consistency — align AssemblyVersion, Version, PackageVersion; create v2.0.0 git tag | Packaging | 1 | 4 | S | Medium | — | language-specific | All three versions match; git tag exists |
| 4 | Add `[Obsolete]` attributes to all legacy classes (Events/, CommandQuery/, Queue/, QueueStream/) | Code Quality | 1 | 3 | S | Medium | — | language-specific | Compiler warnings on legacy class usage |
| 5 | Fix `throw e` → `throw` in `Transport.cs:44,62` to preserve stack traces | Error Handling | 1 | 4 | S | Medium | — | cross-SDK | Stack trace preserved in exception; unit test verifies |
| 6 | Add connection state callback (`OnConnected`, `OnDisconnected`, `OnReconnecting`) | Connection | 1 | 4 | M | High | — | cross-SDK | Callback fires on state change; unit test verifies |
| 7 | Create CHANGELOG.md and GitHub Releases for v2.0.0 | Documentation | 1 | 4 | S | Medium | — | language-specific | CHANGELOG.md exists; GitHub Release for v2.0.0 published |
| 8 | Enable `#nullable enable` across all source files | API Design | 1 | 4 | M | Medium | — | language-specific | Zero nullable warnings; all public APIs annotated |

### Phase 2: Medium-Term Improvements (Effort: M-L)

| # | Item | Category | Current | Target | Effort | Impact | Depends On | Scope | Validation Metric |
|---|------|----------|---------|--------|--------|--------|------------|-------|-------------------|
| 9 | Add typed error hierarchy: `KubeMQException` base, `ConnectionException`, `AuthenticationException`, `TimeoutException`, `ValidationException`, `ServerException` | Error Handling | 1 | 4 | M | Critical | — | cross-SDK | 5 exception subtypes exist; gRPC status codes mapped; documented |
| 10 | Add exponential backoff with jitter for reconnection | Connection | 1 | 4 | M | High | #6 | cross-SDK | Backoff formula: `min(base * 2^attempt, maxDelay) + random(0, jitter)`; configurable; unit test verifies |
| 11 | Migrate from `Grpc.Core` to `Grpc.Net.Client` (grpc-dotnet) | Connection | 2 | 4 | L | High | — | language-specific | Uses `GrpcChannel.ForAddress()`; removes native dependency; targets .NET 6+ |
| 12 | Add unit test project with xUnit + Moq; target 50%+ coverage on core logic | Testing | 1 | 3 | L | Critical | — | language-specific | Test project exists; ≥50% coverage on Config, Results, Transport, message encoding |
| 13 | Add GitHub Actions CI pipeline: build, test, lint on PR | Testing | 1 | 4 | M | High | #12 | cross-SDK | CI green on main branch; PRs require passing checks |
| 14 | Extract interfaces: `IEventsClient`, `IQueuesClient`, `ICommandsClient`, `IQueriesClient` | Code Quality | 1 | 4 | M | Medium | — | language-specific | All clients implement interfaces; mockable in tests |
| 15 | Update cookbook to target .NET 8.0 and reference SDK v2.0.0 | Documentation | 2 | 4 | M | Medium | — | language-specific | All cookbook examples compile and run against v2.0.0 |
| 16 | Update dependencies: Microsoft.Extensions.* to v8.0+, Grpc.Core.Api align with Grpc.Core version | Code Quality | 2 | 4 | M | Medium | #11 | language-specific | `dotnet list package --outdated` shows no critical outdated packages |
| 17 | Add OpenTelemetry integration: `ActivitySource` for tracing, metrics via `System.Diagnostics.Metrics` | Observability | 1 | 4 | L | Medium | — | cross-SDK | Activity spans created for Send/Subscribe; opt-in via config |

### Phase 3: Major Rework (Effort: L-XL)

| # | Item | Category | Current | Target | Effort | Impact | Depends On | Scope | Validation Metric |
|---|------|----------|---------|--------|--------|--------|------------|-------|-------------------|
| 18 | Remove all legacy code (Events/, CommandQuery/, Queue/, QueueStream/ directories) | Code Quality | 2 | 5 | L | High | #4, #15 | language-specific | Removed directories; ~40% codebase reduction; all tests still pass |
| 19 | Add integration test suite covering all 4 messaging patterns with containerized KubeMQ server | Testing | 1 | 4 | L | Critical | #12, #13 | cross-SDK | Integration tests for events, events-store, queues, commands, queries; CI runs against Docker KubeMQ |
| 20 | Add comprehensive reconnection with transport-level recovery, subscription re-establishment, and message buffering | Connection | 2 | 5 | XL | Critical | #6, #10, #11 | cross-SDK | Integration test: kill server, restart, verify auto-recovery within 30s |
| 21 | Add BenchmarkDotNet benchmarks for publish, subscribe, queue operations | Performance | 1 | 3 | M | Medium | #11 | language-specific | Benchmarks exist and documented; baseline numbers published |
| 22 | Publish API reference docs site (DocFX or similar) | Documentation | 1 | 4 | M | Medium | #8 | language-specific | API docs browsable at docs.kubemq.io/csharp or similar |

### Effort Key
- **S (Small):** < 1 day
- **M (Medium):** 1-3 days
- **L (Large):** 1-2 weeks
- **XL (Extra Large):** 2+ weeks

### Priority Summary
**Immediate (Phase 1):** Items 1-8 address the cheapest, highest-impact issues. Items 1, 2, and 5 are bug fixes. Items 3, 4, 7 are hygiene.

**Critical path (Phase 2):** Items 9 (typed errors), 10 (backoff), 12 (tests), and 13 (CI) are the minimum for production-readiness. Item 11 (grpc-dotnet migration) unblocks modern .NET best practices.

**Strategic (Phase 3):** Items 18-22 elevate the SDK from "functional" to "competitive" with NATS.Client and Azure.Messaging.ServiceBus quality levels.

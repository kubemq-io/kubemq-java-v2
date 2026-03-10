# KubeMQ Python SDK Assessment Report

## Executive Summary

- **Weighted Score (Production Readiness):** 2.96 / 5.0
- **Unweighted Score (Overall Maturity):** 2.92 / 5.0
- **Gating Rule Applied:** Yes — Error Handling (2.50) and Auth & Security (2.60) are Critical-tier categories below 3.0. Overall capped at 3.0.
- **Feature Parity Gate:** No — only 1/46 applicable features scores 0 (2.2%, well below 25% threshold).
- **Assessment Date:** 2026-03-09
- **SDK Version Assessed:** v4.0.0-dev (branch `v4`, unpublished; latest published: v3.6.0 on PyPI)
- **Repository:** `github.com/kubemq-io/kubemq-Python`

### Category Scores

| # | Category | Weight | Score | Grade | Gating? |
|---|----------|--------|-------|-------|---------|
| 1 | API Completeness & Feature Parity | 14% | 4.07 | Strong | Critical |
| 2 | API Design & Developer Experience | 9% | 3.63 | Good | High |
| 3 | Connection & Transport | 11% | 3.00 | Adequate | Critical |
| 4 | Error Handling & Resilience | 11% | 2.50 | Weak | Critical |
| 5 | Auth & Security | 9% | 2.60 | Weak | Critical |
| 6 | Concurrency & Thread Safety | 7% | 3.75 | Good | High |
| 7 | Observability | 5% | 1.50 | Absent | Standard |
| 8 | Code Quality & Architecture | 6% | 3.48 | Adequate | High |
| 9 | Testing | 9% | 2.73 | Weak | High |
| 10 | Documentation | 7% | 2.00 | Poor | High |
| 11 | Packaging & Distribution | 4% | 2.90 | Adequate | Standard |
| 12 | Compatibility, Lifecycle & Supply Chain | 4% | 1.90 | Poor | Standard |
| 13 | Performance | 4% | 2.60 | Weak | Standard |

### Top Strengths

1. **Comprehensive API coverage** — All four messaging patterns (Events, Events Store, Queues, CQ) fully implemented with both sync and native async variants. The SDK covers 15/15 gRPC methods from the protobuf definition.
2. **Well-structured typed exception hierarchy** — `KubeMQError` base with 7 domain-specific subclasses and automatic gRPC status code mapping (`core/exceptions.py`).
3. **Native async architecture** — Separate `AsyncClient` classes using `grpc.aio` without thread pools, proper cancellation token hierarchy with parent-child cascading, and async iterator subscription pattern.

### Critical Gaps (Must Fix)

1. **No retry/backoff mechanism** — Zero exponential backoff implementation anywhere; reconnection uses fixed-interval sleep only.
2. **No observability** — No structured logging, no metrics hooks, no OpenTelemetry/tracing integration.
3. **Cookbook completely broken** — Python cookbook uses v2/v3 API; zero compatibility with v4 SDK. All 27 examples will fail to import.
4. **CI pipeline has no tests** — Deploys to PyPI on tag push without running any tests, linting, or security scanning.
5. **Insecure defaults** — TLS disabled by default; no secure-by-default posture.

---

## Detailed Findings

---

### Category 1: API Completeness & Feature Parity (Score: 4.07)

#### 1.1 Events (Pub/Sub)

| # | Criterion | Score (0-2) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 1.1.1 | Publish single event | **2** | Verified by source | `PubSubClient.send_events_message()` in `pubsub/client.py`, `AsyncPubSubClient.send_event()` in `pubsub/async_client.py`. Uses `SendEvent` RPC. |
| 1.1.2 | Subscribe to events | **2** | Verified by source | `subscribe_to_events()` in both sync (daemon thread + callback) and async (AsyncIterator pattern). Uses `SubscribeToEvents` server streaming RPC via `async_transport.py:361`. |
| 1.1.3 | Event metadata | **2** | Verified by source | `EventMessage` model in `pubsub/event_message.py`: `id`, `channel`, `metadata` (str), `body` (bytes), `tags` (dict[str,str]). Maps to protobuf `Event`. |
| 1.1.4 | Wildcard subscriptions | **2** | Inferred | Channel name passed directly to server; wildcard resolution is server-side. Cookbook has `pubsub/events/wildcards` example. |
| 1.1.5 | Multiple subscriptions | **2** | Verified by source | Sync: each subscription runs in its own daemon thread. Async: `NativeAsyncBaseClient._active_subscriptions` set tracks multiple concurrent subscriptions (`core/client.py:390`). |
| 1.1.6 | Unsubscribe | **2** | Verified by source | `CancellationToken.cancel()` / `AsyncCancellationToken.cancel()` cleanly terminates subscription loops. `AsyncTransport` calls `call.cancel()` on the gRPC stream (`async_transport.py:388`). |
| 1.1.7 | Group-based subscriptions | **2** | Verified by source | `EventsSubscription.group` field in `pubsub/events_subscription.py`. Encoded as `Subscribe.Group` in protobuf. |

**Sub-score: 5.0** (all 14/14 mapped to 5.0)

#### 1.2 Events Store (Persistent Pub/Sub)

| # | Criterion | Score (0-2) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 1.2.1 | Publish to events store | **2** | Verified by source | `send_events_store_message()` (sync), `send_event_store()` (async). Sets `Event.Store = True` in protobuf encoding. |
| 1.2.2 | Subscribe to events store | **2** | Verified by source | `subscribe_to_events_store()` with `EventsStoreSubscription` specifying delivery options. |
| 1.2.3 | StartFromNew | **2** | Verified by source | `EventsStoreType.StartNewOnly` in `pubsub/events_store_subscription.py`. Maps to `EventsStoreTypeData = 1`. |
| 1.2.4 | StartFromFirst | **2** | Verified by source | `EventsStoreType.StartFromFirst` maps to value `2`. |
| 1.2.5 | StartFromLast | **2** | Verified by source | `EventsStoreType.StartFromLast` maps to value `3`. |
| 1.2.6 | StartFromSequence | **2** | Verified by source | `EventsStoreType.StartAtSequence` with `events_store_sequence_value`. Validator ensures value > 0. |
| 1.2.7 | StartFromTime | **2** | Verified by source | `EventsStoreType.StartAtTime` with `events_store_start_time: datetime`. Converts to Unix nanoseconds. |
| 1.2.8 | StartFromTimeDelta | **2** | Verified by source | `EventsStoreType.StartAtTimeDelta` with `events_store_sequence_value` as seconds delta. |
| 1.2.9 | Event store metadata | **2** | Verified by source | `EventStoreMessage` has same fields as `EventMessage`: channel, metadata, body, tags. |

**Sub-score: 5.0** (18/18)

#### 1.3 Queues

| # | Criterion | Score (0-2) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 1.3.1 | Send single message | **2** | Verified by source | `send_queues_message()` (sync) in `queues/client.py`, `send_queue_message()` (async) in `queues/async_client.py`. Uses `QueuesUpstream` streaming RPC. |
| 1.3.2 | Send batch messages | **2** | Verified by source | Async: `send_queue_messages_batch()` with semaphore-based concurrency. `SendQueueMessagesBatch` unary RPC exposed in `AsyncTransport.send_queue_messages_batch()` at `async_transport.py:475`. |
| 1.3.3 | Receive/Pull messages | **2** | Verified by source | `receive_queues_messages()` using `QueuesDownstream` streaming, `pull()` using `ReceiveQueueMessages` unary. |
| 1.3.4 | Receive with visibility timeout | **2** | Verified by source | `visibility_seconds` parameter in `receive_queues_messages()` and `QueuesPollRequest` model. |
| 1.3.5 | Message acknowledgment | **2** | Verified by source | `QueuesPollResponse` has `ack_all_messages()`, `reject_all_messages()`, `re_queue_all_messages()`. Async variant: `AsyncQueuesPollResponse.ack_all()`, `reject_all()`, `re_queue_all()`. Individual ack via downstream request. |
| 1.3.6 | Queue stream / transaction | **2** | Verified by source | `QueuesDownstream` bidirectional streaming in `AsyncTransport.queues_downstream()` at `async_transport.py:601`. Transaction-based with `TransactionId`, supports AckAll/AckRange/NAckAll/NAckRange/ReQueueAll/ReQueueRange. |
| 1.3.7 | Delayed messages | **2** | Verified by source | `QueueMessage.delay_in_seconds` field with `MAX_DELAY_SECONDS = 43200` validation in `queues/queues_message.py`. Maps to `QueueMessagePolicy.DelaySeconds`. |
| 1.3.8 | Message expiration | **2** | Verified by source | `QueueMessage.expiration_in_seconds` with `MAX_EXPIRATION_SECONDS = 43200`. Maps to `QueueMessagePolicy.ExpirationSeconds`. |
| 1.3.9 | Dead letter queue | **2** | Verified by source | `QueueMessage.attempts_before_dead_letter_queue` and `dead_letter_queue` fields. Maps to `Policy.MaxReceiveCount` and `Policy.MaxReceiveQueue`. DLQ consistency validator present. |
| 1.3.10 | Queue message metadata | **2** | Verified by source | Full metadata: channel, metadata, body, tags, plus policy fields. 305-line model with comprehensive validation in `queues/queues_message.py`. |
| 1.3.11 | Peek messages | **2** | Verified by source | Sync: `waiting()` method with `IsPeak=True` in `queues/client.py`. Async: `peek_queue_messages()` in `queues/async_client.py`. |
| 1.3.12 | Purge queue | **0** | Verified by source | No purge/delete-all-messages API found in SDK. Server protobuf has `AckAllQueueMessages` but no dedicated purge. |

**Sub-score: 4.67** (22/24)

#### 1.4 RPC (Commands & Queries)

| # | Criterion | Score (0-2) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 1.4.1 | Send command | **2** | Verified by source | `send_command_request()` (sync) in `cq/client.py`, `send_command()` (async) in `cq/async_client.py`. Uses `SendRequest` RPC with `RequestTypeData = Command`. |
| 1.4.2 | Subscribe to commands | **2** | Verified by source | `subscribe_to_commands()` in both sync/async. Uses `SubscribeToRequests` server streaming. |
| 1.4.3 | Command response | **2** | Verified by source | `send_response_message()` (sync), `send_response()` (async). `CommandResponseMessage.encode()` creates `pb.Response`. |
| 1.4.4 | Command timeout | **2** | Verified by source | `CommandMessage.timeout_in_seconds` (required, gt=0). Converted to milliseconds in protobuf encoding. |
| 1.4.5 | Send query | **2** | Verified by source | `send_query_request()` (sync), `send_query()` (async). Uses `SendRequest` with `RequestTypeData = Query`. |
| 1.4.6 | Subscribe to queries | **2** | Verified by source | `subscribe_to_queries()` in both sync/async. |
| 1.4.7 | Query response | **2** | Verified by source | `QueryResponseMessage` with `metadata`, `body`, `tags` for data return. |
| 1.4.8 | Query timeout | **2** | Verified by source | `QueryMessage.timeout_in_seconds` (required, gt=0). |
| 1.4.9 | RPC metadata | **2** | Verified by source | Full metadata: channel, client_id, metadata, body, tags, timeout. |
| 1.4.10 | Group-based RPC | **2** | Verified by source | `CommandsSubscription.group` and `QueriesSubscription.group` fields in `cq/commands_subscription.py` and `cq/queries_subscription.py`. |
| 1.4.11 | Cache support for queries | **2** | Verified by source | `QueryMessage.cache_key` and `cache_ttl_int_seconds` fields. `QueryResponseMessage` decodes `CacheHit` from response. |

**Sub-score: 5.0** (22/22)

#### 1.5 Client Management

| # | Criterion | Score (0-2) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 1.5.1 | Ping | **2** | Verified by source | `BaseClient.ping()` and `NativeAsyncBaseClient.ping()` return `ServerInfo`. Uses `Ping` RPC. |
| 1.5.2 | Server info | **2** | Verified by source | `ServerInfo` contains `host`, `version`, `server_start_time`, `server_up_time_seconds` in `transport/server_info.py`. |
| 1.5.3 | Channel listing | **2** | Verified by source | `list_events_channels()`, `list_events_store_channels()`, `list_queues_channels()`, `list_commands_channels()`, `list_queries_channels()` all 5 types. Returns typed `PubSubChannel`/`QueuesChannel`/`CQChannel`. |
| 1.5.4 | Channel create | **2** | Verified by source | `create_events_channel()`, `create_events_store_channel()`, `create_queues_channel()`, etc. across all clients. |
| 1.5.5 | Channel delete | **2** | Verified by source | Corresponding `delete_*_channel()` methods on all clients. |

**Sub-score: 5.0** (10/10)

#### 1.6 Operational Semantics

| # | Criterion | Score (0-2) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 1.6.1 | Message ordering | **1** | Inferred | SDK preserves ordering from gRPC stream (FIFO). No explicit ordering documentation or guarantees stated. |
| 1.6.2 | Duplicate handling | **0** | Verified by source | No documentation of at-least-once vs exactly-once semantics. No deduplication mechanism. |
| 1.6.3 | Large message handling | **2** | Verified by source | Configurable `max_send_size`/`max_receive_size` (default 100MB). Applied to gRPC channel options in `transport.py:33-36` and `async_transport.py:144-147`. |
| 1.6.4 | Empty/null payload | **2** | Verified by source | `EventMessage` model_validator allows any combination of metadata/body/tags as long as at least one is present. Empty body (`b""`) valid if metadata or tags exist. |
| 1.6.5 | Special characters | **1** | Inferred | Tags are `dict[str, str]`, metadata is `str`, body is `bytes` -- protobuf handles encoding. No explicit Unicode testing or documentation. |

**Sub-score: 3.40** (mapped: 3+1+5+5+3 = 17/5 = 3.4)

#### Category 1 Aggregate

Raw feature scores: 86/98 applicable points (87.8%). One missing feature (Purge queue, 1.3.12) and two partial features (1.6.1, 1.6.5). Missing features well below 25% threshold.

**Category 1 Normalized Score: 4.07** (weighted average of sub-sections)

---

### Category 2: API Design & Developer Experience (Score: 3.63)

#### 2.1 Language Idiomaticity

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 2.1.1 | Naming conventions | **4** | Verified by source | snake_case throughout: `send_events_message`, `subscribe_to_events`, `cancel_token`. Minor inconsistency: `EventsStoreType` enum uses PascalCase values (`StartNewOnly`) -- uncommon for Python enums but acceptable. |
| 2.1.2 | Configuration pattern | **5** | Verified by source | `ClientConfig` dataclass with defaults, `from_env()`, `from_file()` (TOML), `from_dotenv()`. `TLSConfig` and `KeepAliveConfig` frozen dataclasses. Idiomatic modern Python. `core/config.py`. |
| 2.1.3 | Error handling pattern | **4** | Verified by source | Exception-based with typed hierarchy (`core/exceptions.py`). Pydantic validators raise `ValueError`. Minor: legacy code still uses bare `Exception` (`queues/async_client.py:543`). |
| 2.1.4 | Async pattern | **5** | Verified by source | Full asyncio with native `async/await`. `AsyncClient` classes use `grpc.aio`. Async iterators for subscriptions. Both sync and async variants. Three-tier: sync, legacy thread-wrapped, native async. |
| 2.1.5 | Resource cleanup | **4** | Verified by source | Context managers (`with`/`async with`) on all clients (`core/client.py:164-175`, `519-531`). Minor: sync transport's `close()` has awkward `get_event_loop().run_until_complete()` fallback (`transport.py:196`). |
| 2.1.6 | Collection types | **5** | Verified by source | Native Python collections: `list`, `dict`, `set`. No custom collection wrappers. |
| 2.1.7 | Null/optional handling | **4** | Verified by source | Uses `Optional[str]` and `str | None` (via `from __future__ import annotations`). Mixed styles across files -- inconsistent but functional. |

**Sub-score: 4.43**

#### 2.2 Progressive Disclosure & Minimal Boilerplate

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 2.2.1 | Quick start simplicity | **4** | Verified by source | Basic publish: ~8 lines. `with PubSubClient(address="localhost:50000") as client: client.send_events_message(EventMessage(channel="ch", body=b"msg"))`. Good but requires knowing import paths. |
| 2.2.2 | Sensible defaults | **5** | Verified by source | `ClientConfig` works with just `address`. Auto-generated `client_id` from hostname (`config.py:120`). Default 100MB message size, 30s timeout. |
| 2.2.3 | Opt-in complexity | **5** | Verified by source | TLS, auth, keepalive, reconnection all optional. Constructor accepts flat kwargs for simple cases or `config=ClientConfig(...)` for complex ones. |
| 2.2.4 | Consistent method signatures | **3** | Verified by source | Inconsistent naming between sync/async: `send_events_message` vs `send_event`, `receive_queues_messages` vs `receive_queue_messages`. Channel management consistent across patterns. |
| 2.2.5 | Discoverability | **3** | Verified by source | `__all__` exports 42 symbols in `__init__.py`. Docstrings on most public methods. No generated API docs, no published type stubs. |

**Sub-score: 4.00**

#### 2.3 Type Safety & Generics

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 2.3.1 | Strong typing | **4** | Verified by source | Pydantic models for all messages. Type hints on all public methods. mypy configured in strict mode (`pyproject.toml`). Some `Any` in callback types. |
| 2.3.2 | Enum/constant usage | **4** | Verified by source | `EventsStoreType`, `SubscribeType` as proper enums. `QueueMessage.MAX_DELAY_SECONDS` as class constants. |
| 2.3.3 | Return types | **4** | Verified by source | Specific return types: `EventSendResult`, `QueueSendResult`, `CommandResponseMessage`, `ServerInfo`. Not generic maps. |

**Sub-score: 4.00**

#### 2.4 API Consistency

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 2.4.1 | Internal consistency | **3** | Verified by source | Sync clients follow same pattern (BaseClient, subscription loops, channel management). Async clients differ: PubSub `send_event`, Queues `send_queue_message`, CQ `send_command`. Sync: `send_events_message`, `send_queues_message`, `send_command_request`. |
| 2.4.2 | Cross-SDK concept alignment | **3** | Inferred | Core concepts map to other SDKs: Client, EventMessage, QueueMessage, CommandMessage, QueryMessage, Subscription. |
| 2.4.3 | Method naming alignment | **3** | Inferred | Python uses snake_case equivalents. Method names are longer than Java but recognizable. |
| 2.4.4 | Option/config alignment | **3** | Inferred | `ClientConfig` fields align with other SDKs conceptually (address, client_id, auth_token, tls, keep_alive). |

**Sub-score: 3.00**

#### 2.5 Developer Journey Walkthrough

| Step | Assessment | Friction Points |
|------|-----------|-----------------|
| 1. Install | `pip install kubemq` works for v3.6.0. v4 not published. | v4 requires source install; README says Python 3.2+ (wrong). |
| 2. Connect | `PubSubClient(address="localhost:50000")` -- simple, clean. | Eagerly connects on init; no lazy option for sync. |
| 3. First Publish | `client.send_events_message(EventMessage(channel="ch", body=b"hi"))` -- straightforward. | Must know `EventMessage` vs `EventStoreMessage`. |
| 4. First Subscribe | Subscription requires creating subscription object with callbacks -- moderate complexity. | Callback-based API for sync; must understand cancellation tokens. |
| 5. Error Handling | Typed exceptions with gRPC mapping. Catchable. | No `is_retryable` flag to determine if retry is safe. |
| 6. Production Config | `ClientConfig` with TLS, keepalive, reconnection -- well structured. | No auto-reconnect on async path. Must handle manually. |
| 7. Troubleshooting | No troubleshooting guide. Error messages include gRPC details. | No structured logging; hard to correlate in production. |

**Developer Journey Score: 3.0** -- Functional for basic use cases but with friction on subscription setup, no published v4, and poor troubleshooting support.

**Category 2 Overall: 3.63**

---

### Category 3: Connection & Transport (Score: 3.00)

#### 3.1 gRPC Implementation

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 3.1.1 | gRPC client setup | **4** | Verified by source | Proper channel creation with configurable options in both `SyncTransport` (`transport.py:70`) and `AsyncTransport` (`async_transport.py:37`). Separate interceptor chains for auth. |
| 3.1.2 | Protobuf alignment | **4** | Verified by source | Proto files in `protos/kubemq/grpc/`. Generated stubs in `src/kubemq/grpc/`. Covers all 15 RPC methods. |
| 3.1.3 | Proto version | **3** | Inferred | Proto includes all modern methods (QueuesUpstream, QueuesDownstream, QueuesInfo). No version stamp to verify exact alignment. |
| 3.1.4 | Streaming support | **4** | Verified by source | `SubscribeToEvents` (server streaming), `QueuesUpstream`/`QueuesDownstream` (bidirectional) in `async_transport.py:567-632`. Proper stream tracking and cancellation. |
| 3.1.5 | Metadata passing | **4** | Verified by source | Auth token via gRPC interceptors (`transport/interceptors.py`). Four separate async interceptors for each call type (lines 139-232). Client ID embedded in protobuf messages. |
| 3.1.6 | Keepalive | **4** | Verified by source | `KeepAliveConfig`: `ping_interval_in_seconds`, `ping_timeout_in_seconds`, `permit_without_calls`. Maps to 5 gRPC options in `_get_call_options()` (`transport.py:42-66`) and `_build_channel_options()` (`async_transport.py:142`). |
| 3.1.7 | Max message size | **4** | Verified by source | Configurable (default 100MB). Applied via `grpc.max_send_message_length`/`grpc.max_receive_message_length`. |
| 3.1.8 | Compression | **1** | Verified by source | gRPC compression not exposed. No `compression` parameter passed to any RPC call. |

**Sub-score: 3.50**

#### 3.2 Connection Lifecycle

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 3.2.1 | Connect | **4** | Verified by source | Sync: `SyncTransport.initialize()` with `ChannelManager`. Async: `AsyncTransport.connect()` with ping verification (5s timeout). Proper error reporting via `KubeMQConnectionError`. |
| 3.2.2 | Disconnect/close | **4** | Verified by source | Async: `AsyncTransport.close()` cancels all active streams via `_active_streams` set, closes channel (`async_transport.py:217-236`). Sync: `ChannelManager.close()`. Idempotent with `_closing` flag. |
| 3.2.3 | Auto-reconnection | **2** | Verified by source | Sync only: `ChannelManager.recreate_channel()` with fixed-interval sleep. Async: **no auto-reconnection** at all. |
| 3.2.4 | Reconnection backoff | **1** | Verified by source | Fixed interval only (`reconnect_interval_seconds`, default 1s). No exponential backoff, no jitter anywhere. `time.sleep(reconnect_seconds)` in `channel_manager.py:150`. |
| 3.2.5 | Connection state events | **2** | Verified by source | Internal `ConnectionState` in `ChannelManager` tracks state. `_monitor_connection` thread in QueuesClient logs changes. **No user-facing callbacks** exposed. |
| 3.2.6 | Subscription recovery | **2** | Verified by source | Sync: auto-retry loop in `pubsub/client.py:468-482` catches errors, logs, sleeps, retries. Async: no built-in recovery; exception propagates to caller. |
| 3.2.7 | Message buffering during reconnect | **2** | Verified by source | `EventSender` uses `queue.Queue` for outbound buffering. `UpstreamSender`/`DownstreamReceiver` also buffer. But unbounded queues with no size limits. |
| 3.2.8 | Connection timeout | **3** | Verified by source | Async: 5s timeout on initial ping verification (`async_transport.py:118`). Configurable `default_timeout_seconds` (30s). No dedicated connection timeout separate from request timeout. |
| 3.2.9 | Request timeout | **4** | Verified by source | `asyncio.wait_for()` wraps all async operations. Commands/queries accept per-request `timeout_seconds` in `async_transport.py:308`. |

**Sub-score: 2.67**

#### 3.3 TLS / mTLS

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 3.3.1 | TLS support | **4** | Verified by source | `grpc.aio.secure_channel()` with `ssl_channel_credentials` in `async_transport.py:97`. Both sync and async transports support TLS. |
| 3.3.2 | Custom CA certificate | **4** | Verified by source | `TLSConfig.ca_file` read and passed as `root_certificates` in `_get_ssl_credentials()` (`async_transport.py:174`). |
| 3.3.3 | mTLS support | **4** | Verified by source | `TLSConfig` accepts `cert_file`, `key_file`, `ca_file`. Mapped to `private_key` and `certificate_chain` in credentials (`async_transport.py:180-184`). |
| 3.3.4 | TLS configuration | **2** | Verified by source | No cipher suite or TLS version configuration. gRPC defaults are used. |
| 3.3.5 | Insecure mode | **3** | Verified by source | `grpc.aio.insecure_channel()` used when TLS disabled. No explicit warning logged when using insecure mode. |

**Sub-score: 3.40**

#### 3.4 Kubernetes-Native Behavior

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 3.4.1 | K8s DNS service discovery | **2** | Verified by source | SDK uses address string (DNS or IP). Default address not set to `localhost:50000`. No K8s service discovery documentation. |
| 3.4.2 | Graceful shutdown APIs | **3** | Verified by source | `close()` is idempotent, cancels subscriptions. No explicit `drain()` API. No SIGTERM integration docs or examples. |
| 3.4.3 | Health/readiness integration | **3** | Verified by source | `HealthChecker`/`AsyncHealthChecker` classes exist (`core/health.py`). `is_connected` property on all clients. No K8s probe integration docs. |
| 3.4.4 | Rolling update resilience | **2** | Verified by source | Sync auto-reconnection handles pod restarts. Async has no auto-reconnect -- rolling updates break async clients. |
| 3.4.5 | Sidecar vs. standalone | **1** | Verified by source | No documentation of sidecar vs standalone patterns. README doesn't mention Kubernetes at all. |

**Sub-score: 2.20**

#### 3.5 Flow Control & Backpressure

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 3.5.1 | Publisher flow control | **2** | Verified by source | Async batch sends use `asyncio.Semaphore(max_concurrent=100)` in `pubsub/async_client.py:139`. Sync `EventSender` uses unbounded `queue.Queue` -- no backpressure. |
| 3.5.2 | Consumer flow control | **2** | Verified by source | `max_messages` parameter on queue polling. No configurable prefetch for event subscriptions. |
| 3.5.3 | Throttle detection | **1** | Verified by source | `RESOURCE_EXHAUSTED` mapped to `KubeMQMessageError` but no automatic backoff response. |
| 3.5.4 | Throttle error surfacing | **2** | Verified by source | Error raised with gRPC details. No specific throttle suggestion in error message. |

**Sub-score: 1.75**

**Category 3 Overall: 3.00**

---

### Category 4: Error Handling & Resilience (Score: 2.50)

#### 4.1 Error Classification & Types

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 4.1.1 | Typed errors | **4** | Verified by source | 8 typed exception classes in `core/exceptions.py`: `KubeMQError` (line 12), `KubeMQConnectionError` (47), `KubeMQAuthenticationError` (53), `KubeMQTimeoutError` (59), `KubeMQValidationError` (66), `KubeMQChannelError` (73), `KubeMQMessageError` (79), `KubeMQTransactionError` (85), `KubeMQCircuitOpenError` (92). |
| 4.1.2 | Error hierarchy | **4** | Verified by source | Well-structured: `KubeMQError` base with domain subclasses. `KubeMQAuthenticationError` extends `KubeMQConnectionError`. All store `message`, `code`, `details` dict, `cause`. |
| 4.1.3 | Retryable classification | **1** | Verified by source | No `is_retryable` property. Callers cannot programmatically determine if an error is transient. |
| 4.1.4 | gRPC status mapping | **4** | Verified by source | `from_grpc_error()` at line 102 maps 12 gRPC status codes: UNAVAILABLE->Connection, UNAUTHENTICATED->Auth, DEADLINE_EXCEEDED->Timeout, NOT_FOUND->Channel, INVALID_ARGUMENT->Validation, RESOURCE_EXHAUSTED->Message, ABORTED->Transaction. |
| 4.1.5 | Error wrapping/chaining | **4** | Verified by source | Uses `from e` for cause chaining. `cause` stored as attribute. Dual support for sync `grpc.RpcError` and async `grpc.aio.AioRpcError`. Duck-typing support via `_convert_grpc_like_error()` at line 165. |

**Sub-score: 3.40**

#### 4.2 Error Message Quality

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 4.2.1 | Actionable messages | **2** | Verified by source | Messages include gRPC details but rarely suggest fixes. Example: `"Failed to connect to localhost:50000: ..."` -- no suggestion to check server status. |
| 4.2.2 | Context inclusion | **3** | Verified by source | gRPC status code and details included. Some messages include address. Missing: channel name, operation name, client ID in most errors. |
| 4.2.3 | No swallowed errors | **3** | Verified by source | Most errors propagated. `transport.py:117-118` has no-op `except`. Subscription loops catch and log but continue -- acceptable for resilience but could mask issues. |
| 4.2.4 | Consistent format | **3** | Verified by source | `KubeMQError.__str__()` produces consistent `"message (code: X, details: Y)"` format. But legacy `decode_grpc_error()` in `common/helpers.py` returns plain strings. |

**Sub-score: 2.75**

#### 4.3 Retry & Backoff

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 4.3.1 | Automatic retry | **1** | Verified by source | No automatic retry for transient errors. Subscription loops reconnect but are not a general retry mechanism. |
| 4.3.2 | Exponential backoff | **1** | Verified by source | Zero exponential backoff. All reconnection uses `time.sleep(fixed_interval)`. |
| 4.3.3 | Configurable retry | **1** | Verified by source | No retry policy configuration. Only `reconnect_interval_seconds` (flat interval for subscription recovery). |
| 4.3.4 | Retry exhaustion | **1** | Verified by source | No retry count limits. Subscription loops retry indefinitely. |
| 4.3.5 | Non-retryable bypass | **1** | Verified by source | No retryable classification, so no bypass. Auth errors treated same as transient. |

**Sub-score: 1.00**

#### 4.4 Resilience Patterns

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 4.4.1 | Timeout on all operations | **4** | Verified by source | `asyncio.wait_for()` wraps all async operations in `async_transport.py`. Sync relies on gRPC built-in deadline. `default_timeout_seconds = 30`. |
| 4.4.2 | Cancellation support | **4** | Verified by source | `CancellationToken` (sync, `threading.Event`) and `AsyncCancellationToken` (async, `asyncio.Event`). Parent-child cascading, linked tokens, bridge between sync/async in `common/async_cancellation_token.py`. |
| 4.4.3 | Graceful degradation | **3** | Verified by source | Batch sends track per-item errors. Subscription failure doesn't crash other subscriptions (separate threads/tasks). Stream errors caught, not fatal. |
| 4.4.4 | Resource leak prevention | **3** | Verified by source | `AsyncTransport.close()` cancels all registered streams via `_active_streams`. Context managers ensure cleanup. Risk: `SyncTransport.close()` nullifies `_async_channel` in async context without proper close (`transport.py:192`). |

**Sub-score: 3.50**

**Category 4 Overall: 2.50**

---

### Category 5: Authentication & Security (Score: 2.60)

#### 5.1 Authentication Methods

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 5.1.1 | JWT token auth | **4** | Verified by source | Auth token passed via gRPC metadata `("authorization", auth_token)` through interceptors in `transport/interceptors.py`. `PyJWT` dependency. |
| 5.1.2 | Token refresh | **1** | Verified by source | No token refresh mechanism. Token set at connection time; rotation requires new client instance. |
| 5.1.3 | OIDC integration | **1** | Verified by source | No OIDC support. No token acquisition or refresh flows. |
| 5.1.4 | Multiple auth methods | **2** | Verified by source | Supports JWT token and mTLS. No dynamic switching. |

**Sub-score: 2.00**

#### 5.2 Security Best Practices

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 5.2.1 | Secure defaults | **1** | Verified by source | TLS **disabled** by default. Insecure `grpc.insecure_channel()` used unless TLS explicitly configured. No warning for insecure connections. Opposite of best practice. |
| 5.2.2 | No credential logging | **3** | Verified by source | `ClientConfig.__repr__()` masks auth_token with `'***'` at `config.py:143`. `Connection.auth_token` and interceptor instances store plaintext with no logging-path redaction. |
| 5.2.3 | Credential handling | **4** | Verified by source | Tokens passed via gRPC metadata only, not persisted to disk. Cookbook examples use `"your_auth_token"` placeholder. |
| 5.2.4 | Input validation | **3** | Verified by source | Pydantic validates message fields (channel non-empty, delay limits). `ClientConfig.__post_init__` validates address. No channel name injection-safe validation. |
| 5.2.5 | Dependency security | **4** | Verified by runtime | `pip-audit` reports no known vulnerabilities. Dependencies are minimal and mainstream. |

**Sub-score: 3.00**

**Category 5 Overall: 2.60**

**GATING: Category 5 score (2.60) < 3.0 -> Overall SDK score CAPPED at 3.0**

---

### Category 6: Concurrency & Thread Safety (Score: 3.75)

#### 6.1 Thread Safety

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 6.1.1 | Client thread safety | **4** | Verified by source | `BaseClient` uses `threading.RLock` for close (`core/client.py:70`), `threading.Event` for shutdown. `SyncTransport` has `_is_connected_lock`. |
| 6.1.2 | Publisher thread safety | **4** | Verified by source | `EventSender` uses `threading.Lock` for response dict and `queue.Queue` (thread-safe) for message passing in `pubsub/event_sender.py`. |
| 6.1.3 | Subscriber thread safety | **4** | Verified by source | Each subscription in dedicated daemon thread (sync) or asyncio task (async). `_active_subscriptions` protected by `asyncio.Lock` at `core/client.py:390`. |
| 6.1.4 | Documentation of guarantees | **1** | Verified by source | No thread safety documentation anywhere. No docstring mentions thread safety. |
| 6.1.5 | Concurrency correctness validation | **2** | Verified by source | No race condition tests, no threading stress tests. `pytest-asyncio` used but no concurrent access testing. |

**Sub-score: 3.00**

#### 6.2 Python-Specific Async Patterns

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 6.2.P1 | asyncio support | **5** | Verified by source | Full native asyncio via `AsyncClient` classes and `AsyncTransport`. Uses `grpc.aio`, `asyncio.Lock`, `asyncio.Semaphore`, async iterators. |
| 6.2.P2 | Sync + async variants | **5** | Verified by source | Three tiers: sync `Client`, legacy async (deprecated thread-wrapped `*_async`), native `AsyncClient`. Complete coverage for all patterns. |
| 6.2.P3 | Context manager | **5** | Verified by source | Sync: `__enter__`/`__exit__` on `BaseClient` (`core/client.py:164-175`). Async: `__aenter__`/`__aexit__` on `NativeAsyncBaseClient` (lines 519-531) and `AsyncTransport` (lines 670-683). |
| 6.2.P4 | GIL awareness | **3** | Inferred | I/O through gRPC C-extension (releases GIL) and asyncio (avoids blocking). No documentation of GIL considerations. |

Other language-specific sections (Go, Java, C#, TypeScript): **N/A** -- not applicable to Python SDK.

**Sub-score: 4.50**

**Category 6 Overall: 3.75**

---

### Category 7: Observability (Score: 1.50)

#### 7.1 Logging

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 7.1.1 | Structured logging | **1** | Verified by source | Plain f-string formatting throughout. No structured logging (JSON, key-value). Example: `self._logger.debug("Starting graceful shutdown")` in `async_transport.py:223`. |
| 7.1.2 | Configurable log level | **3** | Verified by source | `ClientConfig.log_level` passed to `logging.getLogger().setLevel()`. Default: `logging.CRITICAL + 1` (effectively silent) at `core/client.py:78`. |
| 7.1.3 | Pluggable logger | **2** | Verified by source | Uses Python's standard `logging` module -- logger can be configured externally via `logging.getLogger("kubemq")`. No documented extension point. |
| 7.1.4 | No stdout/stderr spam | **4** | Verified by source | All output through `logging`. Default level suppresses everything. Exception: `grpc/client.py` has `print()` but it's a legacy debug file. |
| 7.1.5 | Sensitive data exclusion | **3** | Inferred | Logs use generic messages without message bodies. Auth token redacted in `__repr__`. No explicit policy or audit. |
| 7.1.6 | Context in logs | **1** | Verified by source | Log messages are generic with no structured context. Missing: client_id, channel, operation, request_id. |

**Sub-score: 2.33**

#### 7.2 Metrics

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 7.2.1 | Metrics hooks | **1** | Verified by source | No metrics hooks or callbacks. |
| 7.2.2 | Key metrics exposed | **1** | Verified by source | No metrics tracked -- no message count, latency, or error counters. |
| 7.2.3 | Prometheus/OTel compatible | **1** | Verified by source | No Prometheus or OpenTelemetry integration. |
| 7.2.4 | Opt-in | **N/A** | -- | No metrics system exists. |

**Sub-score: 1.00**

#### 7.3 Tracing

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 7.3.1 | Trace context propagation | **1** | Verified by source | No W3C Trace Context propagation. Tags could carry trace IDs manually but no built-in support. |
| 7.3.2 | Span creation | **1** | Verified by source | No span creation. |
| 7.3.3 | OTel integration | **1** | Verified by source | No OpenTelemetry dependency or integration. |
| 7.3.4 | Opt-in | **N/A** | -- | No tracing system exists. |

**Sub-score: 1.00**

**Category 7 Overall: 1.50**

---

### Category 8: Code Quality & Architecture (Score: 3.48)

#### 8.1 Code Structure

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 8.1.1 | Package/module organization | **5** | Verified by source | Clean src layout: `core/`, `transport/`, `common/`, `pubsub/`, `queues/`, `cq/`, `grpc/`. Each pattern is self-contained. |
| 8.1.2 | Separation of concerns | **4** | Verified by source | Transport layer separate from business logic. Config separate from transport. Message models separate from clients. Minor: `SyncTransport` creates async channel internally. |
| 8.1.3 | Single responsibility | **4** | Verified by source | Each file has clear purpose. `EventMessage` encodes/decodes. `Client` manages connections. `AsyncTransport` handles gRPC calls. |
| 8.1.4 | Interface-based design | **3** | Verified by source | `BaseClient(ABC)` and `NativeAsyncBaseClient(ABC)` provide abstract bases. Protocol types in `core/types.py`. Transport layer is concrete -- no interface for testing. |
| 8.1.5 | No circular dependencies | **4** | Verified by source | Clean import hierarchy: core -> transport -> common -> pattern clients. `TYPE_CHECKING` guards for forward references. |
| 8.1.6 | Consistent file structure | **4** | Verified by source | Each pattern has: `client.py`, `async_client.py`, message models, subscription models. |
| 8.1.7 | Public API surface isolation | **4** | Verified by source | `__all__` in `__init__.py` defines exact public surface (42 symbols). Internal modules not exported. |

**Sub-score: 4.00**

#### 8.2 Code Quality

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 8.2.1 | Linter compliance | **2** | Verified by runtime | **204 ruff errors** found. 42 auto-fixable. Issues include unused imports (F401), setattr with constant (B010). Ruff well-configured in `pyproject.toml` but not enforced in CI. |
| 8.2.2 | No dead code | **3** | Verified by source | `grpc/client.py` is a legacy debug script with `print()` statements. `common/exceptions.py` has deprecated error classes still present. Zero TODO/FIXME/HACK comments. |
| 8.2.3 | Consistent formatting | **4** | Verified by source | Ruff formatter configured with line-length=100. Consistent style. Minor import ordering issues. |
| 8.2.4 | Meaningful naming | **4** | Verified by source | Clear, descriptive names: `send_events_message`, `subscribe_to_events`, `AsyncCancellationToken`, `QueuesPollRequest`. |
| 8.2.5 | Error path completeness | **3** | Verified by source | Most error paths handled. `transport.py:117-118` has no-op `except`. Some async error paths could leak resources. |
| 8.2.6 | Magic number/string avoidance | **4** | Verified by source | Constants: `MAX_DELAY_SECONDS`, `DEFAULT_MAX_MESSAGE_SIZE`, `DEFAULT_TIMEOUT_SECONDS`. Minor: `5.0` hardcoded in `AsyncTransport.connect()`. |
| 8.2.7 | Code duplication | **3** | Verified by source | Sync and async clients have significant structural duplication. Three base client classes with overlapping logic. Subscription loop repeated across PubSub and CQ. |

**Sub-score: 3.29**

#### 8.3 Serialization & Message Handling

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 8.3.1 | JSON marshaling helpers | **1** | Verified by source | No JSON helpers for message bodies. Only internal `json.loads()` in `common/channel_stats.py`. |
| 8.3.2 | Protobuf message wrapping | **4** | Verified by source | All message types have `encode()` and `decode()` methods. Proto types not leaked to public API -- users work with Pydantic models. |
| 8.3.3 | Typed payload support | **2** | Verified by source | Body is `bytes`, metadata is `str`, tags is `dict[str,str]`. No typed payload deserialization, no generics for body type. |
| 8.3.4 | Custom serialization hooks | **1** | Verified by source | No serialization extension points. |
| 8.3.5 | Content-type handling | **1** | Verified by source | No content-type support. |

**Sub-score: 1.80**

#### 8.4 Technical Debt

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 8.4.1 | TODO/FIXME/HACK comments | **5** | Verified by source | Zero TODO/FIXME/HACK comments in source. Clean codebase. |
| 8.4.2 | Deprecated code | **3** | Verified by source | 26 `deprecated` references across 8 files. Legacy `*_async` methods marked with `@deprecated_async_method`. Legacy `common/exceptions.py` deprecated classes still present. |
| 8.4.3 | Dependency freshness | **4** | Verified by runtime | All dependencies up-to-date per `pip-audit`. No known CVEs. |
| 8.4.4 | Language version | **4** | Verified by source | Targets Python 3.9+ (current supported: 3.9-3.13). Uses `from __future__ import annotations`. |
| 8.4.5 | gRPC/protobuf library version | **4** | Verified by source | `grpcio >= 1.51.0`, `protobuf >= 4.21.0`. Both current. |

**Sub-score: 4.00**

#### 8.5 Extensibility

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 8.5.1 | Interceptor/middleware support | **3** | Verified by source | Auth interceptors exist as pattern in `transport/interceptors.py`. Users could add custom gRPC interceptors but no public API to inject them. |
| 8.5.2 | Event hooks | **1** | Verified by source | No lifecycle hooks (onConnect, onDisconnect, onError, onMessage). |
| 8.5.3 | Transport abstraction | **2** | Verified by source | Two concrete transport classes. No transport interface -- impossible to substitute for testing or custom implementations. |

**Sub-score: 2.00**

**Category 8 Overall: 3.48**

---

### Category 9: Testing (Score: 2.73)

#### 9.1 Unit Tests

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 9.1.1 | Unit test existence | **4** | Verified by runtime | **851 unit tests** across 53 test files. All pass (1 skipped, 3 warnings). Organized by module: core (7), transport (6), common (5), pubsub (4), queues (7), cq (4). |
| 9.1.2 | Coverage percentage | **3** | Inferred | Coverage configured (`[tool.coverage]` in pyproject.toml) with branch coverage. Estimated 60-70% based on test count vs source. Coverage report not generated. |
| 9.1.3 | Test quality | **3** | Verified by source | Tests cover core logic, message encode/decode, config validation, exception hierarchy. Edge cases tested. Missing: error path testing in transport, reconnection logic. |
| 9.1.4 | Mocking | **4** | Verified by source | `pytest-mock` in dev deps. Transport layer mocked in client tests. No running server required for unit tests. |
| 9.1.5 | Table-driven / parameterized tests | **3** | Inferred | `@pytest.mark.parametrize` used in some tests. `hypothesis` in dev deps for property-based testing. |
| 9.1.6 | Assertion quality | **4** | Verified by source | Proper pytest assertions (`assert`, `pytest.raises`, `pytest.approx`). Not just boolean checks. |

**Sub-score: 3.50**

#### 9.2 Integration Tests

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 9.2.1 | Integration test existence | **3** | Verified by source | 3 integration test files: `test_async_pubsub.py`, `test_async_queues.py`, `test_async_cq.py`. Require live KubeMQ server. |
| 9.2.2 | All patterns covered | **3** | Verified by source | Integration tests cover async PubSub, Queues, and CQ. No sync integration tests. |
| 9.2.3 | Error scenario testing | **2** | Verified by source | Characterization tests exist (4 files). Limited error scenarios -- no auth failure, timeout, invalid channel tests. |
| 9.2.4 | Setup/teardown | **3** | Inferred | Pytest fixtures for setup/teardown. Separate test markers (`@pytest.mark.integration`). |
| 9.2.5 | Parallel safety | **2** | Inferred | No evidence of parallel test isolation (unique channel names, test namespacing). |

**Sub-score: 2.60**

#### 9.3 CI/CD Pipeline

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 9.3.1 | CI pipeline exists | **2** | Verified by source | `.github/workflows/deploy.yml` exists but only handles deployment (build + publish to PyPI on tag push). No test pipeline. |
| 9.3.2 | Tests run on PR | **1** | Verified by source | **Tests do NOT run on PR.** Pipeline triggers on tag push only. |
| 9.3.3 | Lint on CI | **1** | Verified by source | No linting in CI. Ruff configured but only runnable locally. 204 errors currently exist. |
| 9.3.4 | Multi-version testing | **1** | Verified by source | Single `python-version: '3.x'` in workflow. No matrix testing against 3.9/3.10/3.11/3.12/3.13. |
| 9.3.5 | Security scanning | **1** | Verified by source | No dependency scanning in CI. `bandit` and `pip-audit` in dev deps but not wired into CI. |

**Sub-score: 1.20**

**Category 9 Overall: 2.73**

---

### Category 10: Documentation (Score: 2.00)

#### 10.1 API Reference

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 10.1.1 | API docs exist | **2** | Verified by source | Docstrings in source. `mkdocs` + `mkdocstrings` in optional deps suggests planned docs site, not yet built. |
| 10.1.2 | All public methods documented | **3** | Verified by source | ~80% of public methods have docstrings. Sync clients excellent. Some models minimal (CommandMessage, EventMessage). |
| 10.1.3 | Parameter documentation | **3** | Verified by source | Args/Returns/Raises sections in most client methods. Missing on some model methods. |
| 10.1.4 | Code doc comments | **3** | Verified by source | Module-level docstrings on transport files. `QueueMessage` exemplary (305-line model with examples). |
| 10.1.5 | Published API docs | **1** | Verified by source | No published API documentation site. |

**Sub-score: 2.40**

#### 10.2 Guides & Tutorials

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 10.2.1 | Getting started guide | **1** | Verified by source | README has install instructions but no quickstart code. No step-by-step guide. |
| 10.2.2 | Per-pattern guide | **1** | Verified by source | No pattern-specific guides. Only examples in `examples/` directory. |
| 10.2.3 | Authentication guide | **1** | Verified by source | No auth guide. Cookbook `client/authentication/main.py` uses v2/v3 API. |
| 10.2.4 | Migration guide | **1** | Verified by source | No v3->v4 migration guide despite major API changes. |
| 10.2.5 | Performance tuning guide | **1** | Verified by source | No performance tuning documentation. |
| 10.2.6 | Troubleshooting guide | **1** | Verified by source | No troubleshooting guide. |

**Sub-score: 1.00**

#### 10.3 Examples & Cookbook

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 10.3.1 | Example code exists | **3** | Verified by source | 31 examples in `examples/` directory. Cookbook has 27 examples (v2/v3 API only). |
| 10.3.2 | All patterns covered | **4** | Verified by source | In-repo examples cover events, events_store, queues, commands, queries. Both sync and async. |
| 10.3.3 | Examples compile/run | **2** | Verified by runtime | In-repo: syntax valid. Cookbook: syntax valid but **all imports fail with v4 SDK** -- completely broken. All 27 cookbook examples use removed import paths (`kubemq.basic.grpc_client.GrpcClient`, etc.). |
| 10.3.4 | Real-world scenarios | **2** | Verified by source | Cookbook covers multicast, load balancing, DLQ, stream processing -- realistic but all broken with v4. In-repo examples are basic. |
| 10.3.5 | Error handling shown | **2** | Verified by source | Basic try/except in cookbook. In-repo has some error handling. |
| 10.3.6 | Advanced features | **2** | Verified by source | Cookbook covers TLS, auth, delayed messages, DLQ, groups -- all v2/v3. In-repo: async, channel management, but no TLS/auth for v4 API. |

**Sub-score: 2.50**

#### 10.4 README Quality

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 10.4.1 | Installation instructions | **2** | Verified by source | `pip install kubemq` works for v3.6.0 but v4 not published. |
| 10.4.2 | Quick start code | **1** | Verified by source | No quickstart code in README. Just links to cookbook (broken for v4). |
| 10.4.3 | Prerequisites | **1** | Verified by source | README says "Python 3.2+" -- **incorrect** (actual: 3.9+). No server version stated. |
| 10.4.4 | License | **3** | Verified by source | MIT License in `pyproject.toml` (`license = {text = "MIT"}`). |
| 10.4.5 | Changelog | **1** | Verified by source | No CHANGELOG.md. `pyproject.toml` references URL that likely doesn't exist. |

**Sub-score: 1.60**

**Category 10 Overall: 2.00**

---

### Category 11: Packaging & Distribution (Score: 2.90)

#### 11.1 Package Manager

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 11.1.1 | Published to canonical registry | **4** | Verified by runtime | Published to PyPI as `kubemq` with 30 releases (v1.0.2 through v3.6.0). v4.0.0 not yet published. |
| 11.1.2 | Package metadata | **3** | Verified by source | `pyproject.toml`: name, version, description, license, requires-python, URLs. Missing: keywords, incomplete classifiers. |
| 11.1.3 | Reasonable install | **4** | Verified by runtime | `pip install kubemq` works. `uv build` succeeds. Hatchling build system. |
| 11.1.4 | Minimal dependency footprint | **3** | Verified by source | 6 runtime deps. `grpcio-tools` arguably should be dev-only. |

**Sub-score: 3.50**

#### 11.2 Versioning & Releases

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 11.2.1 | Semantic versioning | **4** | Verified by source | Follows semver: 1.x -> 2.x -> 3.x with breaking changes. |
| 11.2.2 | Release tags | **2** | Verified by runtime | 15 git tags but many PyPI versions have no corresponding tag (no v2.x tags). |
| 11.2.3 | Release notes | **1** | Verified by source | No GitHub Release descriptions. No CHANGELOG. |
| 11.2.4 | Current version | **3** | Verified by runtime | v3.6.0 published recently. v4 in active development. |
| 11.2.5 | Version consistency | **2** | Verified by source | `pyproject.toml` says `4.0.0-dev` but `__init__.py` says `"4.0.0"`. Mismatches on older releases. |

**Sub-score: 2.40**

#### 11.3 Build & Development Setup

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 11.3.1 | Build instructions | **2** | Verified by source | No build instructions in README. |
| 11.3.2 | Build succeeds | **4** | Verified by runtime | `uv build` produces `.tar.gz` and `.whl` without errors. |
| 11.3.3 | Development dependencies | **4** | Verified by source | Dev deps in separate `[project.optional-dependencies]` group. |
| 11.3.4 | Contributing guide | **1** | Verified by source | No CONTRIBUTING.md. |

**Sub-score: 2.75**

#### 11.4 SDK Binary Size & Footprint

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 11.4.1 | Dependency weight | **3** | Verified by source | grpcio is heavy (~4MB wheel). 6 direct deps with transitive deps from pydantic/grpcio. Reasonable but heavier than nats-py. |
| 11.4.2 | No native compilation required | **4** | Verified by runtime | grpcio ships pre-compiled wheels. No source compilation on common platforms. |

**Sub-score: 3.50**

**Category 11 Overall: 2.90**

---

### Category 12: Compatibility, Lifecycle & Supply Chain (Score: 1.90)

#### 12.1 Compatibility

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 12.1.1 | Server version matrix | **1** | Verified by source | No documentation of compatible KubeMQ server versions. |
| 12.1.2 | Runtime support matrix | **3** | Verified by source | `requires-python = ">=3.9"` in pyproject.toml. Classifiers for 3.9-3.13. README incorrectly says 3.2+. |
| 12.1.3 | Deprecation policy | **2** | Verified by source | `@deprecated_async_method` decorator marks legacy methods pointing to alternatives. No documented timeline or removal policy. |
| 12.1.4 | Backward compatibility discipline | **3** | Verified by source | Major version bumps for breaking changes. `Transport` alias for backward compat. `to_legacy_connection()` bridge. v4 breaks all v3 API with no migration guide. |

**Sub-score: 2.25**

#### 12.2 Supply Chain & Release Integrity

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 12.2.1 | Signed releases | **1** | Verified by source | No GPG-signed tags, no Sigstore, no package signing. |
| 12.2.2 | Reproducible builds | **2** | Verified by source | No lock file committed. `pyproject.toml` uses `>=` ranges -- non-reproducible. |
| 12.2.3 | Dependency update process | **1** | Verified by source | No Dependabot, Renovate, or documented manual process. |
| 12.2.4 | Security response process | **1** | Verified by source | No SECURITY.md. No vulnerability disclosure process. |
| 12.2.5 | SBOM | **1** | Verified by source | No SBOM generation. |
| 12.2.6 | Maintainer health | **2** | Inferred | Single maintainer (kubemq-io org). Active v4 development. No community contributors visible. |

**Sub-score: 1.33**

**Category 12 Overall: 1.90**

---

### Category 13: Performance (Score: 2.60)

#### 13.1 Benchmark Infrastructure

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 13.1.1 | Benchmark tests exist | **3** | Verified by source | `tests/benchmarks/` directory with 1 file. `BENCHMARK_BASELINE.md` exists as untracked file. |
| 13.1.2 | Benchmark coverage | **2** | Inferred | Single benchmark file -- likely basic coverage, not comprehensive across all patterns. |
| 13.1.3 | Benchmark documentation | **1** | Verified by source | No documentation on running benchmarks or interpreting results. |
| 13.1.4 | Published results | **1** | Verified by source | `BENCHMARK_BASELINE.md` untracked and unpublished. |

**Sub-score: 1.75**

#### 13.2 Optimization Patterns

| # | Criterion | Score (1-5) | Confidence | Evidence / Notes |
|---|-----------|-------------|------------|-----------------|
| 13.2.1 | Object/buffer pooling | **1** | Verified by source | No object pooling or buffer reuse. New protobuf objects per message. |
| 13.2.2 | Batching support | **4** | Verified by source | Async batch sends with `asyncio.Semaphore(max_concurrent)`. `SendQueueMessagesBatch` RPC. `send_events_batch` and `send_events_store_batch`. |
| 13.2.3 | Lazy initialization | **4** | Verified by source | `EventSender` lazily initialized via `_sender_lock`. `UpstreamSender`/`DownstreamReceiver` lazily created. Async clients use lazy `connect()`. |
| 13.2.4 | Memory efficiency | **3** | Inferred | Frozen Pydantic models prevent accidental mutation. Protobuf serialization efficient. Risk: unbounded `queue.Queue` in sync sender. |
| 13.2.5 | Resource leak risk | **3** | Verified by source | Active stream tracking in `AsyncTransport` with register/unregister. Context managers for cleanup. Risk: sync `_async_channel` nullification issue. |
| 13.2.6 | Connection overhead | **4** | Verified by source | Single gRPC channel per client, reused for all operations. No per-operation channel creation. |

**Sub-score: 3.17**

**Category 13 Overall: 2.60**

---

## Developer Journey Assessment

### Narrative Walkthrough

**1. Install:** `pip install kubemq` installs v3.6.0 cleanly. But v4 (assessed version) requires `pip install -e .` from source. README incorrectly states Python 3.2+. **Friction: moderate.**

**2. Connect:** `PubSubClient(address="localhost:50000")` or `async with AsyncPubSubClient(address="localhost:50000") as client:` -- clean and simple. **Friction: low.**

**3. First Publish:** `client.send_events_message(EventMessage(channel="test", body=b"hello"))` -- ~5 lines of code. Auto-generated UUID for message ID. **Friction: low.**

**4. First Subscribe:** Requires subscription object with callbacks:
```python
sub = EventsSubscription(
    channel="test",
    on_receive_event_callback=lambda e: print(e),
)
client.subscribe_to_events(sub, cancel=CancellationToken())
```
Async variant is more Pythonic (AsyncIterator). **Friction: moderate.**

**5. Error Handling:** Typed exceptions are catchable. Error messages include gRPC details. No `is_retryable` forces manual classification. **Friction: moderate.**

**6. Production Config:** `ClientConfig` with TLS, keepalive, reconnect is well-designed. Env var loading is nice. But: no exponential backoff, no async auto-reconnect, no connection state callbacks. **Friction: high.**

**7. Troubleshooting:** No troubleshooting guide. No structured logging. Generic error messages. **Friction: very high.**

**Overall Developer Journey Score: 3.0 / 5.0**

---

## Competitor Comparison

### Python Messaging SDK Comparison

| Area | kubemq (Python) | nats-py | confluent-kafka | aio-pika (RabbitMQ) | azure-servicebus |
|------|----------------|---------|----------------|--------------------|--------------------|
| **API Design** | Good -- typed Pydantic models, dual sync/async | Excellent -- minimal, Pythonic | Good -- C-based, less Pythonic | Excellent -- fully async, aio | Excellent -- Azure SDK conventions |
| **Feature Richness** | 4 patterns, DLQ, events store, cache | PubSub, JetStream, KV, Object Store | PubSub, exactly-once, schemas | PubSub, RPC, DLQ, priority | Queues, topics, sessions, DLQ |
| **Retry/Resilience** | **None** | Built-in reconnect with backoff | Producer retries, idempotency | Built-in reconnect | Auto-retry with configurable policy |
| **Observability** | **None** | None built-in | Prometheus metrics | None built-in | Azure Monitor, OTel |
| **Documentation** | Poor -- no guides, broken cookbook | Good -- NATS docs site | Excellent -- Confluent docs | Good -- aio-pika docs | Excellent -- Azure docs, samples |
| **Community** | Minimal | Active | Very active | Active | Very active |
| **Async Support** | Native asyncio | Native asyncio | Thread-based only | Native asyncio | Native asyncio |
| **Type Safety** | Strong (Pydantic) | Moderate | Weak (C wrapper) | Strong | Strong (Azure SDK types) |
| **Maintenance** | Single maintainer | Active | Confluent-backed | Community | Microsoft-backed |

### Key Takeaways

1. **kubemq-Python is competitive on API design and type safety** -- Pydantic models and dual sync/async are strong differentiators.
2. **Major gap in resilience** -- Every competitor except basic aio-pika has retry/reconnect with backoff. This is table-stakes for production messaging.
3. **Observability is industry-standard** -- azure-servicebus has OTel, confluent-kafka has Prometheus. kubemq has nothing.
4. **Documentation gap is the largest** -- All competitors have dedicated doc sites, tutorials, and maintained examples.

---

## Remediation Roadmap

### Phase 0: Assessment Validation (1-2 days)

Validate top 5 findings with targeted manual smoke tests:
1. Verify async client has no auto-reconnect by killing/restarting server during subscription
2. Verify cookbook imports fail with v4 SDK (`from kubemq.basic.grpc_client import GrpcClient` -> ImportError)
3. Confirm CI deploys without tests by reviewing GitHub Actions run logs
4. Run `ruff check src/kubemq/` and confirm 204+ errors
5. Test token rotation requires new client instance

### Phase 1: Quick Wins (Effort: S-M)

| # | Item | Category | Current | Target | Effort | Impact | Depends On | Scope | Validation Metric |
|---|------|----------|---------|--------|--------|--------|------------|-------|-------------------|
| 1 | Fix README: correct Python version (3.9+), add quickstart code | Documentation | 1 | 4 | S | High | -- | language-specific | README has working copy-paste quickstart, correct version |
| 2 | Add CI test pipeline (run pytest on PR) | Testing | 1 | 4 | S | High | -- | cross-SDK | GitHub Actions runs `pytest tests/unit/` on every PR |
| 3 | Fix 204 ruff linting errors, add ruff to CI | Code Quality | 2 | 4 | S | Medium | #2 | language-specific | `ruff check src/` passes with zero errors in CI |
| 4 | Add `is_retryable` property to exception hierarchy | Error Handling | 1 | 4 | S | High | -- | cross-SDK | All exceptions expose `is_retryable: bool`; UNAVAILABLE/DEADLINE_EXCEEDED retryable |
| 5 | Add insecure connection warning log | Auth & Security | 1 | 3 | S | Medium | -- | cross-SDK | `logging.warning("Using insecure connection...")` when TLS disabled |
| 6 | Document thread safety guarantees in docstrings | Concurrency | 1 | 4 | S | Medium | -- | language-specific | Class docstrings state thread safety guarantees |
| 7 | Add CHANGELOG.md, SECURITY.md, CONTRIBUTING.md | Lifecycle | 1 | 3 | S | Medium | -- | cross-SDK | All three files exist following GitHub standards |
| 8 | Fix version consistency (`pyproject.toml` vs `__init__.py`) | Packaging | 2 | 4 | S | Low | -- | language-specific | Single source of truth for version |

### Phase 2: Medium-Term Improvements (Effort: M-L)

| # | Item | Category | Current | Target | Effort | Impact | Depends On | Scope | Validation Metric |
|---|------|----------|---------|--------|--------|--------|------------|-------|-------------------|
| 9 | Implement exponential backoff with jitter for reconnection | Error Handling | 1 | 4 | M | High | #4 | cross-SDK | `min(base * 2^attempt, max_delay) + jitter`; configurable; unit tests verify |
| 10 | Add async auto-reconnection to AsyncTransport | Connection | 1 | 4 | L | High | #9 | language-specific | AsyncTransport reconnects on loss; subscriptions recover after reconnect |
| 11 | Rewrite Python cookbook for v4 API | Documentation | 1 | 4 | L | High | -- | language-specific | All cookbook examples use v4 imports; pass syntax check; work against test server |
| 12 | Add connection state callbacks (on_connected, on_disconnected) | Connection | 2 | 4 | M | Medium | -- | cross-SDK | Callbacks fire correctly; unit test verifies |
| 13 | Add structured logging with context | Observability | 1 | 4 | M | Medium | -- | cross-SDK | Logs include client_id, channel, operation; JSON formatter option available |
| 14 | Add CI multi-version testing (3.9-3.13 matrix) | Testing | 1 | 4 | M | Medium | #2 | language-specific | CI matrix tests all supported Python versions |
| 15 | Publish v4 to PyPI | Packaging | 3 | 5 | M | High | #1, #3 | language-specific | `pip install kubemq` installs v4 |
| 16 | Add K8s deployment documentation (sidecar + standalone) | Documentation | 1 | 4 | M | Medium | -- | cross-SDK | Docs cover both patterns with working examples |
| 17 | Remove `grpcio-tools` from runtime deps (move to dev) | Packaging | 3 | 4 | S | Low | -- | language-specific | `grpcio-tools` only in dev dependency group |

### Phase 3: Major Rework (Effort: L-XL)

| # | Item | Category | Current | Target | Effort | Impact | Depends On | Scope | Validation Metric |
|---|------|----------|---------|--------|--------|--------|------------|-------|-------------------|
| 18 | Add OpenTelemetry tracing integration | Observability | 1 | 4 | L | Medium | #13 | cross-SDK | OTel spans for publish/subscribe/RPC; trace context propagation via tags |
| 19 | Add metrics hooks (OTel/Prometheus) | Observability | 1 | 4 | L | Medium | #18 | cross-SDK | Messages sent/received, latency, error count as OTel metrics |
| 20 | Build and publish API docs site (mkdocs) | Documentation | 1 | 4 | L | Medium | -- | language-specific | Published API reference at documented URL |
| 21 | Add token refresh mechanism | Auth & Security | 1 | 4 | L | Medium | -- | cross-SDK | Refresh callback fires before expiry; connection maintained |
| 22 | Add transport interface (Protocol) for testability | Code Quality | 2 | 4 | L | Medium | -- | language-specific | Transport Protocol allows mock injection in all client tests |
| 23 | Add comprehensive integration test suite with error scenarios | Testing | 2 | 4 | XL | Medium | #14 | cross-SDK | Integration tests cover all patterns, errors, reconnection, TLS |
| 24 | Normalize sync/async method naming for consistency | API Design | 3 | 4 | L | Medium | -- | language-specific | Sync and async method names follow consistent pattern |

### Effort Key

- **S (Small):** < 1 day of work
- **M (Medium):** 1-3 days of work
- **L (Large):** 1-2 weeks of work
- **XL (Extra Large):** 2+ weeks of work

---

## Score Calculation Detail

### Raw Category Scores and Weighted Contribution

| # | Category | Weight | Raw Score | Weighted |
|---|----------|--------|-----------|----------|
| 1 | API Completeness | 14% | 4.07 | 0.570 |
| 2 | API Design & DX | 9% | 3.63 | 0.327 |
| 3 | Connection & Transport | 11% | 3.00 | 0.330 |
| 4 | Error Handling | 11% | 2.50 | 0.275 |
| 5 | Auth & Security | 9% | 2.60 | 0.234 |
| 6 | Concurrency | 7% | 3.75 | 0.263 |
| 7 | Observability | 5% | 1.50 | 0.075 |
| 8 | Code Quality | 6% | 3.48 | 0.209 |
| 9 | Testing | 9% | 2.73 | 0.246 |
| 10 | Documentation | 7% | 2.00 | 0.140 |
| 11 | Packaging | 4% | 2.90 | 0.116 |
| 12 | Compatibility & Lifecycle | 4% | 1.90 | 0.076 |
| 13 | Performance | 4% | 2.60 | 0.104 |
| | **TOTAL** | **100%** | | **2.96** |

- **Weighted Score: 2.96 / 5.0**
- **Unweighted Average: 2.92 / 5.0** ((4.07+3.63+3.00+2.50+2.60+3.75+1.50+3.48+2.73+2.00+2.90+1.90+2.60)/13)

### Gating Check

- Category 1 (API Completeness): 4.07 >= 3.0 -- **PASS**
- Category 3 (Connection): 3.00 >= 3.0 -- **PASS** (borderline)
- Category 4 (Error Handling): 2.50 < 3.0 -- **FAIL** (quality gate triggered)
- Category 5 (Auth & Security): 2.60 < 3.0 -- **FAIL** (quality gate triggered)

Two Critical-tier categories below 3.0. Score would be capped at 3.0, but weighted score (2.96) is already below cap.

### Feature Parity Gate

1 out of ~46 applicable features scores 0 (2.2%) -- well below 25% threshold. No feature parity cap.

**Final Scores: Weighted 2.96, Unweighted 2.92**

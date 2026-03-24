# Java Burn-In Test App — Implementation Plan

## Context

Fifth and final SDK burn-in implementation after Go, Python, JS, and C#. The Java SDK at `kubemq-java-v2/kubemq-java/` uses **3 separate clients** (`PubSubClient`, `QueuesClient`, `CQClient`) — same pattern as Python. Subscriptions are **callback-based and non-blocking** via gRPC `StreamObserver`. The SDK uses Lombok `@Builder` pattern. All 39 consolidated rules from Go + Python + JS + C# are pre-applied.

---

## Key Java SDK Characteristics

1. **3 separate clients** — `PubSubClient`, `QueuesClient`, `CQClient` (like Python, unlike Go/JS/C# which use 1)
2. **Lombok @Builder constructors** — clients built via `PubSubClient.builder().address(...).clientId(...).build()`
3. **Callback-based non-blocking subscriptions** — `Consumer<EventMessageReceived>` callbacks, returns `Subscription` handle with `.cancel()`
4. **`EventSendResult.isSent()`** for events_store confirmation — must check before counting
5. **`QueueSendResult.isError()`** for queue sends
6. **`CommandResponseMessage` with `.isExecuted()` + `.getError()`** for RPC
7. **`QueuesPollResponse` with `.ackAll()` / `.rejectAll()`** — batch ack on response object
8. **`QueuesPollRequest` with builder** — `.pollMaxMessages()`, `.pollWaitTimeoutInSeconds()`, `.autoAckMessages()`, `.visibilitySeconds()`
9. **`ReconnectionConfig` with builder** — `.maxReconnectAttempts(-1)`, `.reconnectJitterEnabled(true)`
10. **`EventsStoreType` enum** — `StartNewOnly`, `StartAtSequence`, etc.
11. **`publishEvent()` / `publishEventStore()`** — sync methods that throw on error
12. **`sendCommand()` / `sendQuery()`** — sync, return response message
13. **`sendResponseMessage(CommandResponseMessage)` / `sendResponseMessage(QueryResponseMessage)`** — response takes full received message ref
14. **Java 11+ target** — can use `var`, `ConcurrentHashMap`, `CompletableFuture`, etc.

---

## File Structure

```
kubemq-java-v2/burnin/
  pom.xml                       # Maven: SDK local dependency, SnakeYAML, micrometer/prometheus, HdrHistogram
  Dockerfile                    # Multi-arch, non-root user

  src/main/java/io/kubemq/burnin/
    Main.java                   # Entry point: CLI args, signal handling, exit codes
    Config.java                 # Config POJO, YAML load, env override, validation
    Engine.java                 # Orchestrator: 3 clients, warmup, periodic tasks, 2-phase shutdown
    Payload.java                # JSON encode/decode, CRC32 (java.util.zip.CRC32), random padding
    Tracker.java                # Bitset sequence tracker (sliding window, delta gaps)
    RateLimiter.java            # Token-bucket with 1-second burst capacity
    TimestampStore.java         # ConcurrentHashMap: (producerId, seq) → System.nanoTime()
    PeakRate.java               # PeakRateTracker + LatencyAccumulator (HdrHistogram)
    Metrics.java                # All 26 Prometheus metrics + helpers (micrometer-prometheus)
    HttpServer.java             # com.sun.net.httpserver: /health /ready /status /summary /metrics
    Report.java                 # 10 checks + advisory, PASSED/PASSED_WITH_WARNINGS/FAILED
    Disconnect.java             # Forced disconnect manager

    workers/
      BaseWorker.java           # 2-phase shutdown, dual tracking, all counters
      EventsWorker.java         # publishEvent() + subscribeToEvents()
      EventsStoreWorker.java    # publishEventStore() + subscribeToEventsStore()
      QueueStreamWorker.java    # sendQueueMessage() + receiveQueueMessages(autoAck=false, visibility)
      QueueSimpleWorker.java    # sendQueueMessage() + receiveQueueMessages(autoAck=true)
      CommandsWorker.java       # sendCommand() + subscribeToCommands() + sendResponseMessage()
      QueriesWorker.java        # sendQuery() + subscribeToQueries() + sendResponseMessage()

  burnin-config.yaml            # Example config
  IMPLEMENTATION-RETROSPECTIVE.md
```

~18 Java source files.

---

## Threading Architecture (Java Thread Pool)

```
Main Thread → Engine.run()
  ├── Per-pattern: Consumer callback (executed on client's callbackExecutor)
  ├── Per-pattern: Producer Thread (ExecutorService.submit async loop)
  ├── ScheduledExecutorService: periodicReporter (every BURNIN_REPORT_INTERVAL)
  ├── ScheduledExecutorService: peakRateAdvancer (every 1s)
  ├── ScheduledExecutorService: memoryTracker (every 10s)
  ├── ScheduledExecutorService: timestampPurger (every 60s)
  ├── com.sun.net.httpserver: HTTP Server (daemon thread)
  └── [Optional] ScheduledExecutorService: disconnectManager
```

2-phase shutdown via two `AtomicBoolean` flags + `CountDownLatch`:
```
producerStop.set(true) ──→ stops all producer loops
       ↓ drain timeout (Thread.sleep)
consumerStop → subscription.cancel() on all subscriptions
```

---

## SDK API Mapping (Java ↔ Go ↔ Spec)

| Spec Pattern | Go API | Java API |
|-------------|--------|----------|
| Events send | `SendEventStream()` | `pubSubClient.publishEvent(EventMessage)` (uses stream internally) |
| Events subscribe | `SubscribeToEvents()` | `pubSubClient.subscribeToEvents(EventsSubscription)` → `Subscription` |
| Events Store send+confirm | `SendEventStoreStream()` | `pubSubClient.publishEventStore(EventStoreMessage)` → `EventSendResult` |
| Events Store subscribe | `SubscribeToEventsStore()` | `pubSubClient.subscribeToEventsStore(EventsStoreSubscription)` |
| Queue send | `SendQueueMessage()` | `queuesClient.sendQueueMessage(QueueMessage)` → `QueueSendResult` |
| Queue receive+ack | `ReceiveQueueMessages()` | `queuesClient.receiveQueueMessages(QueuesPollRequest)` → `QueuesPollResponse.ackAll()` |
| Commands send | `SendCommand()` | `cqClient.sendCommand(CommandMessage)` → `CommandResponseMessage` |
| Commands subscribe | `SubscribeToCommands()` | `cqClient.subscribeToCommands(CommandsSubscription)` |
| Commands respond | `SendResponse()` | `cqClient.sendResponseMessage(CommandResponseMessage)` |
| Queries send | `SendQuery()` | `cqClient.sendQuery(QueryMessage)` → `QueryResponseMessage` |
| Queries subscribe | `SubscribeToQueries()` | `cqClient.subscribeToQueries(QueriesSubscription)` |
| Queries respond | `SendResponse()` | `cqClient.sendResponseMessage(QueryResponseMessage)` |
| Ping | `Ping()` | `client.ping()` → `ServerInfo` |
| Channel create | `CreateChannel()` | `pubSubClient.createEventsChannel()` etc. |
| Channel delete | `DeleteChannel()` | `pubSubClient.deleteEventsChannel()` etc. |
| Channel list | `ListChannels()` | `pubSubClient.listEventsChannels()` etc. |

---

## Pre-Applied Lessons (39 rules)

| # | Source | Rule | Java Implementation |
|---|--------|------|---------------------|
| 1 | Go#1 | Prometheus counter deltas only | `Tracker.detectGaps()` returns delta via `lastReportedLost` |
| 2 | Go#2 | 2-phase shutdown in base | `BaseWorker` has `producerStop` + `consumerStop` AtomicBooleans |
| 3 | Go#3/20 | Memory baseline 5-min | Seed first, update at 300s, `Runtime.getRuntime().totalMemory() - freeMemory()` |
| 4 | Go#5 | Dual tracking | Prometheus + AtomicLong for all counters |
| 5 | Go#6 | Warmup ALL 6 | Each uses production API |
| 6 | Go#7 | Sent after success | Events: no throw. ES: `result.isSent()`. Queue: `!result.isError()`. RPC: `resp.isExecuted()` |
| 7 | Go#8 | Cleanup broad prefix | `java_burnin_` (no run_id) |
| 8 | Go#9 | Error rate formula | `errors / (sent + received) * 100` |
| 9 | Go#10 | RPC periodic format | `resp=/tout=` for commands/queries |
| 10 | Go#13 | 3-state verdict | PASSED, PASSED_WITH_WARNINGS, FAILED |
| 11 | Go#14 | Peak rate wired | Record/Advance/Peak lifecycle |
| 12 | Go#17 | Warmup CRC tag | All warmup messages include `content_hash` |
| 13 | Go#18 | Responders check warmup | `tags.get("warmup").equals("true")` FIRST |
| 14 | Go#19 | Queue batch drain | `response.ackAll()` after processing full batch |
| 15 | Py#2 | Non-blocking subs | Callback-based, just cancel on shutdown. SDK handles reconnect |
| 16 | JS-F5 | Config priority | env > CLI > ./burnin-config.yaml > /etc/burnin/config.yaml |
| 17 | JS-F6 | Histogram buckets exact | Latency + RPC bucket arrays match spec |
| 18 | JS-F14 | Shutdown order | channels → report file → console → close |
| 19 | JS-F17 | Throughput skip benchmark | Check mode before evaluating |
| 20 | JS-F18 | Downtime max across patterns | `max(downtime_pct)` not sum |
| 21 | JS-F19 | Benchmark rate=0 | Auto-set all rates to 0 when benchmark |

---

## Implementation Order

### Phase 1: Foundation
1. `pom.xml` — Maven config, SDK dependency, prometheus, HdrHistogram, SnakeYAML
2. `Config.java` — full config with YAML + env overrides + validation
3. `Payload.java` — CRC32 (java.util.zip), JSON, random padding
4. `Tracker.java` — bitset sliding window
5. `RateLimiter.java` — async token-bucket
6. `TimestampStore.java` — ConcurrentHashMap
7. `PeakRate.java` — PeakRateTracker + LatencyAccumulator
8. `Metrics.java` — 26 Prometheus metrics (micrometer)

### Phase 2: Infrastructure
9. `HttpServer.java` — 5 endpoints
10. `workers/BaseWorker.java` — full interface
11. `Main.java` — CLI entry point

### Phase 3: Workers (all 6)
12-17. All 6 worker classes

### Phase 4: Engine + Report + Disconnect
18. `Engine.java` — orchestrator
19. `Report.java` — verdict
20. `Disconnect.java` — forced disconnect

### Phase 5: Polish
21. `burnin-config.yaml`
22. `Dockerfile`

---

## Startup Sequence (Section 12.1)

1. Parse CLI args, load YAML, apply env overrides, validate — exit 2 on error
2. Create 3 clients (`PubSubClient.builder()...`, `QueuesClient.builder()...`, `CQClient.builder()...`)
3. `pubSubClient.ping()` — verify broker
4. Clean stale channels (broad `java_burnin_` prefix across all 3 clients)
5. Create all channels (with runId)
6. Start HTTP server → `/health` returns 200
7. Create workers, start consumers only (subscribe with callbacks)
8. Run warmup: 10 messages per pattern, verify receipt
9. `/ready` returns 200
10. Start producers (after warmup)
11. Print banner
12. If warmup_duration > 0: wait, then reset counters
13. Start periodic tasks (ScheduledExecutorService)
14. Start disconnect manager (if configured)
15. `Thread.sleep(duration)` or wait for signal

## Shutdown Sequence (Section 12.2)

1. `producerStop.set(true)` — all producer loops exit
2. `Thread.sleep(drainTimeoutSeconds * 1000)`
3. Cancel all subscriptions + close queue poll loops
4. Hard deadline: `drainTimeoutSeconds + 5` seconds total
5. Delete channels (best-effort across all 3 clients)
6. Write JSON report to file
7. Print console report
8. `pubSubClient.close()`, `queuesClient.close()`, `cqClient.close()`
9. Stop HTTP server, shutdown executor services
10. `System.exit(exitCode)` — 0=PASSED, 1=FAILED, 2=config error

---

## Verification Steps

1. `mvn exec:java -Dexec.args="--validate-config --config burnin-config.yaml"` — exit 0
2. `mvn exec:java -Dexec.args="--cleanup-only --config burnin-config.yaml"` — broad prefix in logs
3. Grep every metric helper → at least 1 call site
4. 15-minute run against live broker on localhost:50000
5. `curl localhost:8888/summary | jq .` — all fields non-zero
6. `curl localhost:8888/metrics | grep burnin_` — all 26 metrics
7. SIGTERM → clean 2-phase shutdown in logs
8. Exit code 0 for PASSED

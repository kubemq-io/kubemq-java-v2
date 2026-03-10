# WU-8 Output: Spec 09 — API Design & DX (Phase 1)

**Status:** COMPLETED
**Build:** PASS
**Tests:** 1167 passed, 0 failed (no regression from WU-7 baseline)

## Requirements Implemented

### REQ-DX-1: Idiomatic Configuration
- **File:** `KubeMQClient.java`
  - Address resolution chain: explicit parameter → `KUBEMQ_ADDRESS` env var → `localhost:50000` (with WARN log for localhost fallback)
  - Address format validation (`host:port`, port range 1–65535) via new `validateAddress()` method; throws `ValidationException`
  - `clientId` defaults to `"kubemq-client-" + UUID.randomUUID().toString().substring(0, 8)` when null or empty
  - Added `validateOnBuild` parameter to constructor; when `true`, performs `ping()` after `initChannel()` and throws `ConnectionException` on failure
  - All three subclass constructors (`PubSubClient`, `QueuesClient`, `CQClient`) updated to pass `validateOnBuild` to `super()`

### REQ-DX-2: Minimal Code Happy Path
- **File:** `PubSubClient.java`
  - `publishEvent(String channel, byte[] body)` — fire-and-forget event convenience
  - `publishEvent(String channel, String body)` — string-body variant
  - `publishEventStore(String channel, byte[] body)` — event-store convenience
- **File:** `QueuesClient.java`
  - `sendQueueMessage(String channel, byte[] body)` — single queue message convenience
- **File:** `CQClient.java`
  - `sendCommand(String channel, byte[] body, int timeoutInSeconds)` — command convenience
  - `sendQuery(String channel, byte[] body, int timeoutInSeconds)` — query convenience

### REQ-DX-4: Fail-Fast Validation
- **File:** `KubeMQUtils.java`
  - New `validateChannelName(String channel, String operation)` method: validates null/empty, max length (256), allowed characters (alphanumeric, `.`, `-`, `_`, `/`, `:`)
  - Constants: `MAX_CHANNEL_LENGTH = 256`, `CHANNEL_NAME_PATTERN`
- **Files:** `EventMessage.java`, `EventStoreMessage.java`, `QueueMessage.java`, `CommandMessage.java`, `QueryMessage.java`
  - All `validate()` methods now throw `ValidationException` (was `IllegalArgumentException`)
  - Channel validation delegated to `KubeMQUtils.validateChannelName()`
  - Content, body size, and timeout validations all throw `ValidationException` with `ErrorCode.INVALID_ARGUMENT`
- **Files:** `EventsSubscription.java`, `EventsStoreSubscription.java`, `CommandsSubscription.java`, `QueriesSubscription.java`, `QueuesPollRequest.java`
  - All `validate()` methods now throw `ValidationException` (was `IllegalArgumentException`)

### REQ-DX-5: Message Builder (Non-Breaking Phase)
- **Files:** `EventMessage.java`, `EventStoreMessage.java`, `QueueMessage.java`, `CommandMessage.java`, `QueryMessage.java`
  - Custom `build()` method in Lombok-generated builder class
  - Lenient build-time validation: validates channel name format only when channel is non-null and non-empty
  - Correctly applies `@Builder.Default` values for `body` (empty byte array) and `tags` (empty HashMap) via Lombok's `$set`/`$value` fields
  - Setters preserved for backward compatibility; Javadoc notes removal in v3.0

## Test Updates

Updated test files to accommodate `IllegalArgumentException` → `ValidationException` migration:
- `EventMessageTest.java`, `EventStoreMessageTest.java`, `QueueMessageTest.java`, `CommandMessageTest.java`, `QueryMessageTest.java`
- `EventsSubscriptionTest.java`, `EventsStoreSubscriptionTest.java`, `CommandsSubscriptionTest.java`, `QueriesSubscriptionTest.java`
- `QueuesPollRequestTest.java`, `QueuesClientTest.java`, `BatchQueueSendTest.java`
- `KubeMQClientTest.java` — null clientId test updated to verify default UUID generation
- `ConnectionConfigTest.java` — null/empty clientId tests updated to verify default UUID generation

## Files Modified (Source)

| File | Changes |
|------|---------|
| `KubeMQClient.java` | Address resolution chain, address validation, clientId default, `validateOnBuild` |
| `KubeMQUtils.java` | `validateChannelName()`, `MAX_CHANNEL_LENGTH`, `CHANNEL_NAME_PATTERN` |
| `PubSubClient.java` | `validateOnBuild` in constructor, 3 convenience methods |
| `QueuesClient.java` | `validateOnBuild` in constructor, 1 convenience method |
| `CQClient.java` | `validateOnBuild` in constructor, 2 convenience methods |
| `EventMessage.java` | `validate()` → `ValidationException`, custom builder `build()` |
| `EventStoreMessage.java` | `validate()` → `ValidationException`, custom builder `build()` |
| `QueueMessage.java` | `validate()` → `ValidationException`, custom builder `build()` |
| `CommandMessage.java` | `validate()` → `ValidationException`, custom builder `build()` |
| `QueryMessage.java` | `validate()` → `ValidationException`, custom builder `build()` |
| `EventsSubscription.java` | `validate()` → `ValidationException` |
| `EventsStoreSubscription.java` | `validate()` → `ValidationException` |
| `CommandsSubscription.java` | `validate()` → `ValidationException` |
| `QueriesSubscription.java` | `validate()` → `ValidationException` |
| `QueuesPollRequest.java` | `validate()` → `ValidationException` |

## Files Modified (Tests)

14 test files updated (see Test Updates section above)

## Out of Scope (Deferred)
- **REQ-DX-3** (verb alignment): Deferred to WU-16 (Phase 2)
- **Message setter removal**: Deferred to v3.0 (breaking change)

## Notes
- All exception types from WU-1 (`ValidationException`, `ConnectionException`, `ErrorCode`) were used per spec
- No new classes created; all changes are modifications to existing files
- Build-time validation is intentionally lenient per spec: format-only when value is present
- The `validateOnBuild` feature defaults to `false` (no behavioral change for existing users)

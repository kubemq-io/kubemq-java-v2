# KubeMQ Java SDK Examples

Runnable examples demonstrating all KubeMQ messaging patterns and features.

## Prerequisites

- Java 11+
- KubeMQ server running on `localhost:50000`
- Build the SDK first: `cd kubemq-java && mvn install -DskipTests`

## Running an Example

```bash
cd kubemq-java-example
mvn compile exec:java -Dexec.mainClass="io.kubemq.example.pubsub.SendEventMessageExample"
```

## Examples by Category

### Events (Pub/Sub)

| Example | Description |
|---------|-------------|
| `pubsub/SendEventMessageExample` | Publish a single fire-and-forget event |
| `pubsub/SubscribeToEventExample` | Subscribe to events on a channel |
| `pubsub/GroupSubscriptionExample` | Load-balanced subscription using consumer groups |
| `pubsub/SubscriptionCancelExample` | Cancel an active subscription |
| `pubsub/CreateChannelExample` | Create an events channel |
| `pubsub/DeleteChannelExample` | Delete an events channel |
| `pubsub/ListEventsChanneExample` | List available events channels |

### Events Store (Persistent Pub/Sub)

| Example | Description |
|---------|-------------|
| `pubsub/SubscribeToEventStoreExample` | Subscribe to persistent events |
| `pubsub/EventsStoreStartFromFirstExample` | Replay from the first stored event |
| `pubsub/EventsStoreStartFromLastExample` | Start from the last stored event |
| `pubsub/EventsStoreStartAtSequenceExample` | Replay from a specific sequence number |
| `pubsub/EventsStoreStartAtTimeDeltaExample` | Replay from a relative time offset |
| `pubsub/EventsStoreStartNewOnlyExample` | Receive only new events going forward |

### Queues

| Example | Description |
|---------|-------------|
| `queues/CreateQueuesChannelExample` | Create a queue channel |
| `queues/DeleteQueuesChannelExample` | Delete a queue channel |
| `queues/ListQueuesChannelExample` | List queue channels |
| `queues/Send_ReceiveMessageUsingStreamExample` | Send and receive via bidirectional stream |
| `queues/SendBatchMessagesExample` | Send multiple messages in a batch |
| `queues/WaitingPullExample` | Long-poll for messages with wait timeout |
| `queues/MessageDelayExample` | Send with delivery delay |
| `queues/MessageExpirationExample` | Send with expiration time |
| `queues/MessageRejectExample` | Reject a message (return to queue) |
| `queues/ReQueueMessageExample` | Re-queue to a different channel |
| `queues/ReceiveMessageDLQ` | Handle dead letter queue messages |
| `queues/ReceiveMessageWithVisibilityExample` | Visibility timeout for processing |
| `queues/AutoAckModeExample` | Automatic acknowledgment mode |
| `queues/ReceiveMessageMultiThreadedExample` | Multi-threaded message processing |
| `queues/ChannelSearchExample` | Search for queue channels by pattern |

### Commands & Queries (RPC)

| Example | Description |
|---------|-------------|
| `cq/CommandsExample` | Send and handle commands |
| `cq/CommandWithTimeoutExample` | Commands with custom timeout |
| `cq/QueriesExample` | Send and handle queries |
| `cq/QueryWithDataResponseExample` | Queries returning data payloads |
| `cq/GroupSubscriptionCommandsExample` | Load-balanced command handling |
| `cq/GroupSubscriptionQueriesExample` | Load-balanced query handling |
| `cq/CreateExample` | Create CQ channels |
| `cq/DeleteExample` | Delete CQ channels |
| `cq/ListExample` | List CQ channels |

### Configuration

| Example | Description |
|---------|-------------|
| `config/ClientConfigurationExample` | Client builder configuration options |
| `config/TLSConnectionExample` | TLS-encrypted connection setup |
| `config/AuthTokenExample` | JWT token authentication |

### Error Handling

| Example | Description |
|---------|-------------|
| `errorhandling/ConnectionErrorHandlingExample` | Handle connection failures gracefully |
| `errorhandling/GracefulShutdownExample` | Clean client shutdown with resource cleanup |
| `errorhandling/ReconnectionHandlerExample` | Automatic reconnection handling |

### Patterns

| Example | Description |
|---------|-------------|
| `patterns/PubSubFanOutExample` | Fan-out to multiple subscribers |
| `patterns/RequestReplyPatternExample` | Request-reply using commands |
| `patterns/WorkQueuePatternExample` | Work distribution via queues |

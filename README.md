# KubeMQ Java SDK

The **KubeMQ SDK for Java** enables Java developers to seamlessly communicate with the [KubeMQ](https://kubemq.io/) server, implementing various communication patterns such as Events, EventStore, Commands, Queries, and Queues.

<!-- TOC -->
* [KubeMQ Java SDK](#kubemq-java-sdk)
  * [Prerequisites](#prerequisites)
  * [Installation](#installation)
  * [Running Examples](#running-examples)
  * [Building from Source](#building-from-source)
  * [SDK Overview](#sdk-overview)
  * [KubeMQ Client Configuration](#kubemq-client-configuration)
    * [Configuration Parameters](#configuration-parameters)
    * [Example Usage](#example-usage)
    * [Notes](#notes)
  * [Optional Ping Operation](#optional-ping-operation)
    * [Ping Method](#ping-method)
      * [Return Value: `ServerInfo`](#return-value-serverinfo)
      * [Example Usage](#example-usage-1)
    * [When to Use Ping](#when-to-use-ping)
    * [Important Notes](#important-notes)
  * [PubSub Events Operations](#pubsub-events-operations)
    * [Create Channel](#create-channel)
      * [Request Parameters](#request-parameters)
      * [Response](#response)
      * [Example](#example)
    * [Delete Channel](#delete-channel)
      * [Request Parameters](#request-parameters-1)
      * [Response](#response-1)
      * [Example](#example-1)
    * [List Channels](#list-channels)
      * [Request Parameters](#request-parameters-2)
      * [Response](#response-2)
      * [Example](#example-2)
    * [Send Event Message](#send-event-message)
      * [Request: `EventMessage` Class Attributes](#request-eventmessage-class-attributes)
      * [Response](#response-3)
      * [Example](#example-3)
    * [Subscribe To Events Messages](#subscribe-to-events-messages)
      * [Request: `EventsSubscription` Class Attributes](#request-eventssubscription-class-attributes)
      * [Response](#response-4)
      * [Callback: `EventMessageReceived` Class Detail](#callback-eventmessagereceived-class-detail)
      * [Example](#example-4)
  * [PubSub EventsStore Operations](#pubsub-eventsstore-operations)
    * [Create Channel](#create-channel-1)
      * [Request Parameters](#request-parameters-3)
      * [Response](#response-5)
      * [Example](#example-5)
    * [Delete Channel](#delete-channel-1)
      * [Request Parameters](#request-parameters-4)
      * [Response](#response-6)
      * [Example](#example-6)
    * [List Channels](#list-channels-1)
      * [Request Parameters](#request-parameters-5)
      * [Response](#response-7)
      * [Example](#example-7)
    * [Send EventStore Message](#send-eventstore-message)
      * [Request: `EventStoreMessage` Class Attributes](#request-eventstoremessage-class-attributes)
      * [Response](#response-8)
      * [Example](#example-8)
    * [Subscribe To EventsStore Messages](#subscribe-to-eventsstore-messages)
      * [Request: `EventsStoreSubscription` Class Attributes](#request-eventsstoresubscription-class-attributes)
      * [EventsStoreType Options](#eventsstoretype-options)
      * [Response](#response-9)
      * [Callback: `EventStoreMessageReceived` Class Detail](#callback-eventstoremessagereceived-class-detail)
      * [Example](#example-9)
  * [Commands & Queries – Commands Operations](#commands--queries--commands-operations)
    * [Create Channel](#create-channel-2)
      * [Request Parameters](#request-parameters-6)
      * [Response](#response-10)
      * [Example](#example-10)
    * [Delete Channel](#delete-channel-2)
      * [Request Parameters](#request-parameters-7)
      * [Response](#response-11)
      * [Example](#example-11)
    * [List Channels](#list-channels-2)
      * [Request Parameters](#request-parameters-8)
      * [Response](#response-12)
      * [Example](#example-12)
    * [Send Command Request](#send-command-request)
      * [Request: `CommandMessage` Class Attributes](#request-commandmessage-class-attributes)
      * [Response: `CommandResponseMessage` Class Attributes](#response-commandresponsemessage-class-attributes)
      * [Example](#example-13)
    * [Subscribe To Commands](#subscribe-to-commands)
      * [Request: `CommandsSubscription` Class Attributes](#request-commandssubscription-class-attributes)
      * [Response](#response-13)
      * [Callback: `CommandMessageReceived` Class Attributes](#callback-commandmessagereceived-class-attributes)
      * [Command Response: `CommandResponseMessage` Class Attributes](#command-response-commandresponsemessage-class-attributes)
      * [Example](#example-14)
  * [Commands & Queries – Queries Operations](#commands--queries--queries-operations)
    * [Create Channel](#create-channel-3)
      * [Request Parameters](#request-parameters-9)
      * [Response](#response-14)
      * [Example](#example-15)
    * [Delete Channel](#delete-channel-3)
      * [Request Parameters](#request-parameters-10)
      * [Response](#response-15)
      * [Example](#example-16)
    * [List Channels](#list-channels-3)
      * [Request Parameters](#request-parameters-11)
      * [Response](#response-16)
      * [Example](#example-17)
    * [Send Query Request](#send-query-request)
      * [Request: `QueryMessage` Class Attributes](#request-querymessage-class-attributes)
      * [Response: `QueryResponseMessage` Class Attributes](#response-queryresponsemessage-class-attributes)
      * [Example](#example-18)
    * [Subscribe To Queries](#subscribe-to-queries)
      * [Request: `QueriesSubscription` Class Attributes](#request-queriessubscription-class-attributes)
      * [Response](#response-17)
      * [Callback: `QueryMessageReceived` Class Attributes](#callback-querymessagereceived-class-attributes)
      * [Query Response: `QueryResponseMessage` Class Attributes](#query-response-queryresponsemessage-class-attributes)
      * [Example](#example-19)
  * [Queues Operations](#queues-operations)
    * [Create Channel](#create-channel-4)
      * [Request Parameters](#request-parameters-12)
      * [Response](#response-18)
      * [Example](#example-20)
    * [Delete Channel](#delete-channel-4)
      * [Request Parameters](#request-parameters-13)
      * [Response](#response-19)
      * [Example](#example-21)
    * [List Channels](#list-channels-4)
      * [Request Parameters](#request-parameters-14)
      * [Response](#response-20)
      * [Example](#example-22)
    * [Send Queue Message](#send-queue-message)
      * [Request: `QueueMessage` Class Attributes](#request-queuemessage-class-attributes)
      * [Response: `QueueSendResult` Class Attributes](#response-queuesendresult-class-attributes)
      * [Example](#example-23)
    * [Receive Queue Messages](#receive-queue-messages)
      * [Request: `QueuesPollRequest` Class Attributes](#request-queuespollrequest-class-attributes)
      * [Response: `QueuesPollResponse` Class Attributes](#response-queuespollresponse-class-attributes)
      * [Example](#example-24)
      * [Message Handling Options:](#message-handling-options)
      * [Additional Example: Bulk Message Handling](#additional-example-bulk-message-handling)
    * [Waiting Queue Messages](#waiting-queue-messages)
      * [Request Parameters](#request-parameters-15)
      * [Response: `QueueMessagesWaiting` Class Attributes](#response-queuemessageswaiting-class-attributes)
      * [`QueueMessageWaitingPulled` Class Attributes](#queuemessagewaitingpulled-class-attributes)
      * [Example](#example-25)
      * [Important Notes:](#important-notes-1)
    * [Pull Messages](#pull-messages)
      * [Request Parameters](#request-parameters-16)
      * [Response: `QueueMessagesPulled` Class Attributes](#response-queuemessagespulled-class-attributes)
      * [`QueueMessageWaitingPulled` Class Attributes](#queuemessagewaitingpulled-class-attributes-1)
      * [Example](#example-26)
      * [Important Notes:](#important-notes-2)
<!-- TOC -->
## Prerequisites

- Java Development Kit (JDK) 8 or higher
- Maven
- KubeMQ server running locally or accessible over the network


## Installation

The recommended way to use the SDK for Java in your project is to add it as a dependency in Maven:

```xml
<dependency>
   <groupId>io.kubemq.sdk</groupId>
   <artifactId>kubemq-sdk-Java</artifactId>
   <version>LATEST</version>
</dependency>
```

To build with Gradle, add the dependency to your `build.gradle` file:

```java
compile group: 'io.kubemq.sdk', name: 'kubemq-sdk-Java', version: '2.0.0'
```

## Running Examples

The [examples](https://github.com/kubemq-io/kubemq-java-v2/tree/main/kubemq-java-example) are standalone projects that showcase the usage of the SDK. To run the examples, ensure you have a running instance of KubeMQ. Import the project into any IDE of your choice (e.g., IntelliJ, Eclipse, NetBeans). The example project contains three packages demonstrating different implementations:

- `io.kubemq.example.cq`: Examples related to Commands and Queries
- `io.kubemq.example.pubsub`: Examples related to Events and EventStore
- `io.kubemq.example.queues`: Examples related to Queues

## Building from Source

Once you check out the code from GitHub, you can build it using Maven:

```bash
mvn clean install
```

This command will run the tests and install the JAR file to your local Maven repository. To skip the tests, use the following command:

```bash
mvn clean install -D skipTests=true
```

## SDK Overview

The SDK implements all communication patterns available through the KubeMQ server:
- PubSub
  - Events
  - EventStore
- Commands & Queries (CQ)
  - Commands
  - Queries
- Queues

## KubeMQ Client Configuration

All KubeMQ clients (PubSubClient, QueuesClient, and CQClient) share the same configuration parameters. To create any client instance, you need to use the respective builder with at least two mandatory parameters: `address` (KubeMQ server address) and `clientId`.

### Configuration Parameters

The table below describes all available configuration parameters:

| Name                      | Type    | Description                                             | Default Value     | Mandatory |
|---------------------------|---------|---------------------------------------------------------|-------------------|-----------|
| address                   | String  | The address of the KubeMQ server.                       | None              | Yes       |
| clientId                  | String  | The client ID used for authentication.                  | None              | Yes       |
| authToken                 | String  | The authorization token for secure communication.       | None              | No        |
| tls                       | boolean | Indicates if TLS (Transport Layer Security) is enabled. | false             | No        |
| tlsCertFile               | String  | The path to the TLS certificate file.                   | None              | No        |
| tlsKeyFile                | String  | The path to the TLS key file.                           | None              | No        |
| caCertFile                | String  | The path to the CA certificate file.                    | None              | No        |
| maxReceiveize             | int     | The maximum size of the messages to receive (in bytes). | 104857600 (100MB) | No        |
| reconnectIntervalSeconds  | int     | The interval in seconds between reconnection attempts.  | 1                 | No        |
| keepAlive                 | boolean | Indicates if the connection should be kept alive.       | false             | No        |
| pingIntervalInSeconds     | int     | The interval in seconds between ping messages.          | 60                | No        |
| pingTimeoutInSeconds      | int     | The timeout in seconds for ping messages.               | 30                | No        |
| logLevel                  | Level   | The logging level to use.                               | Level.INFO        | No        |

### Example Usage

Here's an example of how to create a client instance (using PubSubClient as an example):

```java
PubSubClient pubSubClient = PubSubClient.builder()
    .address("localhost:50000")
    .clientId("test-client-id")
    .authToken("your-auth-token")
    .tls(true)
    .tlsCertFile("/path/to/cert.pem")
    .tlsKeyFile("/path/to/key.pem")
    .caCertFile("/path/to/cacert.crt")
    .maxReceiveize(5 * 1024 * 1024)  // 5 MB
    .reconnectIntervalSeconds(10)
    .keepAlive(true)
    .pingIntervalInSeconds(30)
    .pingTimeoutInSeconds(10)
    .logLevel(Level.DEBUG)
    .build();
```

Replace `PubSubClient` with `QueuesClient` or `CQClient` to create instances of other client types. The configuration parameters remain the same for all client types.

### Notes

- For secure connections, set `tls` to `true` and provide the paths to your TLS certificate and key files.
- Adjust `maxReceiveize` based on your expected message sizes to optimize performance.
- Fine-tune `reconnectIntervalSeconds`, `keepAlive`, `pingIntervalInSeconds`, and `pingTimeoutInSeconds` based on your network conditions and requirements.
- Choose an appropriate `logLevel` for your development or production environment.

Remember to handle any exceptions that might be thrown during client creation, such as connection errors or invalid configuration parameters.

## Optional Ping Operation

All KubeMQ clients (PubSubClient, QueuesClient, and CQClient) provide an optional `ping()` method to verify connectivity with the KubeMQ server. This method is not required for normal operations and should be used sparingly.

### Ping Method

```java
ServerInfo ping() throws IOException
```

#### Return Value: `ServerInfo`

The `ping()` method returns a `ServerInfo` object with the following attributes:

| Attribute | Type | Description |
|-----------|------|-------------|
| host | String | The host address of the KubeMQ server |
| version | String | The version of the KubeMQ server |
| serverStartTime | long | The start time of the server (in seconds since epoch) |
| serverUpTimeSeconds | long | The uptime of the server in seconds |

#### Example Usage

```java
try {
    ServerInfo serverInfo = client.ping();
    System.out.println("Successfully connected to KubeMQ server: " + serverInfo.getHost());
} catch (IOException e) {
    System.err.println("Failed to ping KubeMQ server: " + e.getMessage());
}
```

### When to Use Ping

The ping operation is optional and should be used judiciously. Here are some appropriate scenarios for using ping:

1. **Initial Connection Verification**: You may use ping once after creating the client to verify the initial connection.

2. **Troubleshooting**: If you suspect connectivity issues, ping can help diagnose if the problem is with the connection to the KubeMQ server.

3. **Long Periods of Inactivity**: In applications with long periods of inactivity, you might use ping to check if the connection is still alive before performing an operation.

### Important Notes

- **Not Required for Regular Operations**: The ping operation is not needed for regular message sending or receiving operations. The client handles connection management internally.

- **Performance Consideration**: Excessive use of ping can introduce unnecessary network traffic and potential performance overhead.

- **Not a Guarantee**: A successful ping doesn't guarantee that all server functionalities are working correctly. It only verifies basic connectivity.

- **Error Handling**: Always handle potential IOException when using ping, as network issues can occur.

Remember, the KubeMQ client is designed to handle connection management efficiently. In most cases, you can rely on the client to maintain the connection without explicit ping operations.

## PubSub Events Operations

### Create Channel

Create a new Events channel.

#### Request Parameters

| Name        | Type   | Description                             | Default Value | Mandatory |
|-------------|--------|-----------------------------------------|---------------|-----------|
| channelName | String | Name of the channel you want to create  | None          | Yes       |

#### Response

| Name              | Type    | Description                    |
|-------------------|---------|--------------------------------|
| isChannelCreated  | boolean | Indicates if channel was created |

#### Example

```java
public void createEventsChannel(String eventChannelName) {
    try {
        boolean isChannelCreated = pubSubClient.createEventsChannel(eventChannelName);
        System.out.println("Events Channel created: " + isChannelCreated);
    } catch (RuntimeException e) {
        System.err.println("Failed to create events channel: " + e.getMessage());
    }
}
```

### Delete Channel

Delete an existing Events channel.

#### Request Parameters

| Name        | Type   | Description                             | Default Value | Mandatory |
|-------------|--------|-----------------------------------------|---------------|-----------|
| channelName | String | Name of the channel you want to delete  | None          | Yes       |

#### Response

| Name              | Type    | Description                    |
|-------------------|---------|--------------------------------|
| isChannelDeleted  | boolean | Indicates if channel was deleted |

#### Example

```java
public void deleteEventsChannel(String eventChannelName) {
    try {
        boolean isChannelDeleted = pubSubClient.deleteEventsChannel(eventChannelName);
        System.out.println("Events Channel deleted: " + isChannelDeleted);
    } catch (RuntimeException e) {
        System.err.println("Failed to delete events channel: " + e.getMessage());
    }
}
```

### List Channels

Retrieve a list of Events channels.

#### Request Parameters

| Name         | Type   | Description                                | Default Value | Mandatory |
|--------------|--------|--------------------------------------------|---------------|-----------|
| searchQuery  | String | Search query to filter channels (optional) | None          | No        |

#### Response

Returns a `List<PubSubChannel>` where each `PubSubChannel` has the following attributes:

| Name         | Type        | Description                                                                                   |
|--------------|-------------|-----------------------------------------------------------------------------------------------|
| name         | String      | The name of the Pub/Sub channel.                                                              |
| type         | String      | The type of the Pub/Sub channel.                                                              |
| lastActivity | long        | The timestamp of the last activity on the channel, represented in milliseconds since epoch.   |
| isActive     | boolean     | Indicates whether the channel is active or not.                                               |
| incoming     | PubSubStats | The statistics related to incoming messages for this channel.                                 |
| outgoing     | PubSubStats | The statistics related to outgoing messages for this channel.                                 |

#### Example

```java
public void listEventsChannels(String searchQuery) {
    try {
        System.out.println("Listing Events Channels");
        List<PubSubChannel> eventChannels = pubSubClient.listEventsChannels(searchQuery);
        eventChannels.forEach(channel -> {
            System.out.println("Name: " + channel.getName() + 
                               " Channel Type: " + channel.getType() + 
                               " Is Active: " + channel.getIsActive());
        });
    } catch (RuntimeException e) {
        System.err.println("Failed to list event channels: " + e.getMessage());
    }
}
```

### Send Event Message

Send a message to an Events channel.

#### Request: `EventMessage` Class Attributes

| Name     | Type                | Description                                                | Default Value    | Mandatory |
|----------|---------------------|------------------------------------------------------------|------------------|-----------|
| id       | String              | Unique identifier for the event message.                   | None             | No        |
| channel  | String              | The channel to which the event message is sent.            | None             | Yes       |
| metadata | String              | Metadata associated with the event message.                | None             | No        |
| body     | byte[]              | Body of the event message in bytes.                        | Empty byte array | No        |
| tags     | Map<String, String> | Tags associated with the event message as key-value pairs. | Empty Map        | No        |

#### Response

This method doesn't return a value. Successful execution implies the message was sent.

#### Example

```java
public void sendEventMessage() {
  try {
    String data = "Any data can be passed in byte, JSON or anything";
    Map<String, String> tags = new HashMap<>();
    tags.put("tag1", "kubemq");
    tags.put("tag2", "kubemq2");

    EventMessage eventMessage = EventMessage.builder()
            .id(UUID.randomUUID().toString())
            .channel(eventChannelName)
            .metadata("something you want to describe")
            .body(data.getBytes())
            .tags(tags)
            .build();

    pubSubClient.sendEventsMessage(eventMessage);
    System.out.println("Event message sent ");
  } catch (RuntimeException e) {
    System.err.println("Failed to send event message: " + e.getMessage());
  }
}
```

### Subscribe To Events Messages

Subscribes to receive messages from an Events channel.

#### Request: `EventsSubscription` Class Attributes

| Name                    | Type                           | Description                                                          | Default Value | Mandatory |
|-------------------------|--------------------------------|----------------------------------------------------------------------|---------------|-----------|
| channel                 | String                         | The channel to subscribe to.                                         | None          | Yes       |
| group                   | String                         | The group to subscribe with.                                         | None          | No        |
| onReceiveEventCallback  | Consumer<EventMessageReceived> | Callback function to be called when an event message is received.    | None          | Yes       |
| onErrorCallback         | Consumer<String>               | Callback function to be called when an error occurs.                 | None          | No        |

#### Response

This method doesn't return a value. It sets up a subscription that will invoke the provided callbacks.

#### Callback: `EventMessageReceived` Class Detail

| Name         | Type                | Description                                            |
|--------------|---------------------|--------------------------------------------------------|
| id           | String              | The unique identifier of the message.                  |
| fromClientId | String              | The ID of the client that sent the message.            |
| timestamp    | long                | The timestamp when the message was received, in seconds.|
| channel      | String              | The channel to which the message belongs.              |
| metadata     | String              | The metadata associated with the message.              |
| body         | byte[]              | The body of the message.                               |
| sequence     | long                | The sequence number of the message.                    |
| tags         | Map<String, String> | The tags associated with the message.                  |

#### Example

```java
public void subscribeToEvents() {
  try {
    // Consumer for handling received events
    Consumer<EventMessageReceived> onReceiveEventCallback = event -> {
      System.out.println("Received event:");
      System.out.println("ID: " + event.getId());
      System.out.println("Channel: " + event.getChannel());
      System.out.println("Metadata: " + event.getMetadata());
      System.out.println("Body: " + new String(event.getBody()));
      System.out.println("Tags: " + event.getTags());
    };


    // Consumer for handling errors
    Consumer<String> onErrorCallback = error -> {
      System.err.println("Error Received: " + error);
    };

    EventsSubscription subscription = EventsSubscription.builder()
            .channel(eventChannelName)
            .onReceiveEventCallback(onReceiveEventCallback)
            .onErrorCallback(onErrorCallback)
            .build();

    pubSubClient.subscribeToEvents(subscription);
    System.out.println("Events Subscribed");

    // Wait for 10 seconds and call the cancel subscription
    try{
      Thread.sleep(10 * 1000);
      subscription.cancel();
    } catch(Exception ex){

    }

  } catch (RuntimeException e) {
    System.err.println("Failed to subscribe to events: " + e.getMessage());
  }
}
```

Note: Remember to handle the subscription lifecycle appropriately in your application. You may want to store the subscription object to cancel it when it's no longer needed.


## PubSub EventsStore Operations

### Create Channel

Create a new EventsStore channel.

#### Request Parameters

| Name        | Type   | Description                             | Default Value | Mandatory |
|-------------|--------|-----------------------------------------|---------------|-----------|
| channelName | String | Name of the channel you want to create  | None          | Yes       |

#### Response

| Name              | Type    | Description                    |
|-------------------|---------|--------------------------------|
| isChannelCreated  | boolean | Indicates if channel was created |

#### Example

```java
public void createEventsStoreChannel(String eventStoreChannelName) {
    try {
        boolean isChannelCreated = pubSubClient.createEventsStoreChannel(eventStoreChannelName);
        System.out.println("EventsStore Channel created: " + isChannelCreated);
    } catch (RuntimeException e) {
        System.err.println("Failed to create events store channel: " + e.getMessage());
    }
}
```

### Delete Channel

Delete an existing EventsStore channel.

#### Request Parameters

| Name        | Type   | Description                             | Default Value | Mandatory |
|-------------|--------|-----------------------------------------|---------------|-----------|
| channelName | String | Name of the channel you want to delete  | None          | Yes       |

#### Response

| Name              | Type    | Description                    |
|-------------------|---------|--------------------------------|
| isChannelDeleted  | boolean | Indicates if channel was deleted |

#### Example

```java
public void deleteEventsStoreChannel(String eventStoreChannelName) {
    try {
        boolean isChannelDeleted = pubSubClient.deleteEventsStoreChannel(eventStoreChannelName);
        System.out.println("EventsStore Channel deleted: " + isChannelDeleted);
    } catch (RuntimeException e) {
        System.err.println("Failed to delete events store channel: " + e.getMessage());
    }
}
```

### List Channels

Retrieve a list of EventsStore channels.

#### Request Parameters

| Name         | Type   | Description                                | Default Value | Mandatory |
|--------------|--------|--------------------------------------------|---------------|-----------|
| searchQuery  | String | Search query to filter channels (optional) | None          | No        |

#### Response

Returns a `List<PubSubChannel>` where each `PubSubChannel` has the following attributes:

| Name         | Type        | Description                                                                                   |
|--------------|-------------|-----------------------------------------------------------------------------------------------|
| name         | String      | The name of the Pub/Sub channel.                                                              |
| type         | String      | The type of the Pub/Sub channel.                                                              |
| lastActivity | long        | The timestamp of the last activity on the channel, represented in milliseconds since epoch.   |
| isActive     | boolean     | Indicates whether the channel is active or not.                                               |
| incoming     | PubSubStats | The statistics related to incoming messages for this channel.                                 |
| outgoing     | PubSubStats | The statistics related to outgoing messages for this channel.                                 |

#### Example

```java
public void listEventsStoreChannels(String searchQuery) {
    try {
        System.out.println("Listing EventsStore Channels");
        List<PubSubChannel> eventStoreChannels = pubSubClient.listEventsStoreChannels(searchQuery);
        eventStoreChannels.forEach(channel -> {
            System.out.println("Name: " + channel.getName() + 
                               " Channel Type: " + channel.getType() + 
                               " Is Active: " + channel.getIsActive());
        });
    } catch (RuntimeException e) {
        System.err.println("Failed to list events store channels: " + e.getMessage());
    }
}
```

### Send EventStore Message

Send a message to an EventsStore channel.

#### Request: `EventStoreMessage` Class Attributes

| Name      | Type               | Description                                                   | Default Value   | Mandatory |
|-----------|--------------------|------------------------------------------------------------|-----------------|-----------|
| id        | String             | Unique identifier for the event message.                    | None            | No        |
| channel   | String             | The channel to which the event message is sent.             | None            | Yes       |
| metadata  | String             | Metadata associated with the event message.                 | None            | No        |
| body      | byte[]             | Body of the event message in bytes.                         | Empty byte array| No        |
| tags      | Map<String, String>| Tags associated with the event message as key-value pairs.  | Empty Map       | No        |

**Note:** At least one of `metadata`, `body`, or `tags` is required.

#### Response

Returns an `EventSendResult` object (details not provided in the original content).

#### Example

```java
public void sendEventStoreMessage(String eventStoreChannelName) {
    try {
        String data = "Sample event store data";
        Map<String, String> tags = new HashMap<>();
        tags.put("tag1", "value1");
        tags.put("tag2", "value2");

        EventStoreMessage eventStoreMessage = EventStoreMessage.builder()
                .id(UUID.randomUUID().toString())
                .channel(eventStoreChannelName)
                .metadata("Sample metadata")
                .body(data.getBytes())
                .tags(tags)
                .build();
        
        EventSendResult result = pubSubClient.sendEventsStoreMessage(eventStoreMessage);
        System.out.println("Event store message sent. Result: " + result);
    } catch (RuntimeException e) {
        System.err.println("Failed to send event store message: " + e.getMessage());
    }
}
```

### Subscribe To EventsStore Messages

Subscribes to receive messages from an EventsStore channel.

#### Request: `EventsStoreSubscription` Class Attributes

| Name                    | Type                                | Description                                                          | Default Value | Mandatory |
|-------------------------|-------------------------------------|----------------------------------------------------------------------|---------------|-----------|
| channel                 | String                              | The channel to subscribe to.                                         | None          | Yes       |
| group                   | String                              | The group to subscribe with.                                         | None          | No        |
| onReceiveEventCallback  | Consumer<EventStoreMessageReceived> | Callback function to be called when an event message is received.    | None          | Yes       |
| onErrorCallback         | Consumer<String>                    | Callback function to be called when an error occurs.                 | None          | No        |
| eventsStoreType         | EventsStoreType                     | Type of EventsStore subscription (e.g., StartAtTime, StartAtSequence)| None          | Yes       |
| eventsStoreStartTime    | Instant                             | Start time for EventsStore subscription (if applicable)              | None          | Conditional |


#### EventsStoreType Options

| Type              | Value | Description                                                        |
|-------------------|-------|--------------------------------------------------------------------|
| Undefined         | 0     | Default value, should be explicitly set to a valid type before use |
| StartNewOnly      | 1     | Start storing events from the point when the subscription is made  |
| StartFromFirst    | 2     | Start storing events from the first event available                |
| StartFromLast     | 3     | Start storing events from the last event available                 |
| StartAtSequence   | 4     | Start storing events from a specific sequence number               |
| StartAtTime       | 5     | Start storing events from a specific point in time                 |
| StartAtTimeDelta  | 6     | Start storing events from a specific time delta in seconds         |

#### Response

This method doesn't return a value. It sets up a subscription that will invoke the provided callbacks.

#### Callback: `EventStoreMessageReceived` Class Detail

| Name         | Type                | Description                                            |
|--------------|---------------------|--------------------------------------------------------|
| id           | String              | The unique identifier of the message.                  |
| fromClientId | String              | The ID of the client that sent the message.            |
| timestamp    | long                | The timestamp when the message was received, in seconds.|
| channel      | String              | The channel to which the message belongs.              |
| metadata     | String              | The metadata associated with the message.              |
| body         | byte[]              | The body of the message.                               |
| sequence     | long                | The sequence number of the message.                    |
| tags         | Map<String, String> | The tags associated with the message.                  |

#### Example

```java
public void subscribeToEventsStore() {
  try {
    // Consumer for handling received event store messages
    Consumer<EventStoreMessageReceived> onReceiveEventCallback = event -> {
      System.out.println("Received event store:");
      System.out.println("ID: " + event.getId());
      System.out.println("Channel: " + event.getChannel());
      System.out.println("Metadata: " + event.getMetadata());
      System.out.println("Body: " + new String(event.getBody()));
      System.out.println("Tags: " + event.getTags());
    };

    // Consumer for handling errors
    Consumer<String> onErrorCallback = error -> {
      System.err.println("Error Received: " + error);
    };

    EventsStoreSubscription subscription = EventsStoreSubscription.builder()
            .channel(eventStoreChannelName)
            //.group("All IT Team")
            .eventsStoreType(EventsStoreType.StartAtTime)
            .eventsStoreStartTime(Instant.now().minus(1, ChronoUnit.HOURS))
            .onReceiveEventCallback(onReceiveEventCallback)
            .onErrorCallback(onErrorCallback)
            .build();

    pubSubClient.subscribeToEventsStore(subscription);
    System.out.println("EventsStore Subscribed");

    // Wait for 10 seconds and call the cancel subscription
    try{
      Thread.sleep(10 * 1000);
      subscription.cancel();
    }catch(Exception ex){}

  } catch (RuntimeException e) {
    System.err.println("Failed to subscribe to events store: " + e.getMessage());
  }
}
```

Note: Remember to handle the subscription lifecycle appropriately in your application. You may want to store the subscription object to cancel it when it's no longer needed.


## Commands & Queries – Commands Operations

### Create Channel

Create a new Command channel.

#### Request Parameters

| Name        | Type   | Description                             | Default Value | Mandatory |
|-------------|--------|-----------------------------------------|---------------|-----------|
| channelName | String | Name of the channel you want to create  | None          | Yes       |

#### Response

| Name              | Type    | Description                    |
|-------------------|---------|--------------------------------|
| isChannelCreated  | boolean | Indicates if channel was created |

#### Example

```java
public void createCommandsChannel(String channelName) {
    try {
        boolean isChannelCreated = cqClient.createCommandsChannel(channelName);
        System.out.println("Commands channel created: " + isChannelCreated);
    } catch (RuntimeException e) {
        System.err.println("Failed to create commands channel: " + e.getMessage());
    }
}
```

### Delete Channel

Delete an existing Command channel.

#### Request Parameters

| Name        | Type   | Description                             | Default Value | Mandatory |
|-------------|--------|-----------------------------------------|---------------|-----------|
| channelName | String | Name of the channel you want to delete  | None          | Yes       |

#### Response

| Name              | Type    | Description                    |
|-------------------|---------|--------------------------------|
| isChannelDeleted  | boolean | Indicates if channel was deleted |

#### Example

```java
public void deleteCommandsChannel(String channelName) {
    try {
        boolean isChannelDeleted = cqClient.deleteCommandsChannel(channelName);
        System.out.println("Commands channel deleted: " + isChannelDeleted);
    } catch (RuntimeException e) {
        System.err.println("Failed to delete commands channel: " + e.getMessage());
    }
}
```

### List Channels

Retrieve a list of Command channels.

#### Request Parameters

| Name         | Type   | Description                                | Default Value | Mandatory |
|--------------|--------|--------------------------------------------|---------------|-----------|
| searchString | String | Search query to filter channels (optional) | None          | No        |

#### Response

Returns a `List<CQChannel>` where each `CQChannel` has the following attributes:

| Name         | Type    | Description                                      |
|--------------|---------|--------------------------------------------------|
| name         | String  | The name of the channel.                         |
| type         | String  | The type of the channel.                         |
| lastActivity | long    | The timestamp of the last activity on the channel. |
| isActive     | boolean | Indicates whether the channel is currently active. |
| incoming     | CQStats | Statistics about incoming messages to the channel. |
| outgoing     | CQStats | Statistics about outgoing messages from the channel. |

#### Example

```java
public void listCommandsChannels(String searchString) {
    try {
        List<CQChannel> channels = cqClient.listCommandsChannels(searchString);
        System.out.println("Command Channels:");
        channels.forEach(channel -> {
            System.out.println("Name: " + channel.getName() + 
                               ", Type: " + channel.getType() + 
                               ", Active: " + channel.getIsActive());
        });
    } catch (RuntimeException e) {
        System.err.println("Failed to list command channels: " + e.getMessage());
    }
}
```

### Send Command Request

Send a command request to a Command channel.

#### Request: `CommandMessage` Class Attributes

| Name             | Type                | Description                                                                            | Default Value     | Mandatory |
|------------------|---------------------|----------------------------------------------------------------------------------------|-------------------|-----------|
| id               | String              | The ID of the command message.                                                         | None              | Yes       |
| channel          | String              | The channel through which the command message will be sent.                            | None          | Yes       |
| metadata         | String              | Additional metadata associated with the command message.                               | None             | No        |
| body             | byte[]              | The body of the command message as bytes.                                              | Empty byte array  | No        |
| tags             | Map<String, String> | A dictionary of key-value pairs representing tags associated with the command message. | Empty Map | No |
| timeoutInSeconds | int                 | The maximum time in seconds for waiting to response.                                   | None    | Yes       |

#### Response: `CommandResponseMessage` Class Attributes

| Name            | Type                    | Description                                          |
|-----------------|-------------------------|------------------------------------------------------|
| commandReceived | CommandMessageReceived  | The command message received in the response.        |
| clientId        | String                  | The client ID associated with the command response.  |
| requestId       | String                  | The unique request ID of the command response.       |
| isExecuted      | boolean                 | Indicates if the command has been executed.          |
| timestamp       | LocalDateTime           | The timestamp when the command response was created. |
| error           | String                  | The error message if there was an error.             |

#### Example

```java
public void sendCommandRequest(String channel) {
    try {
        Map<String, String> tags = new HashMap<>();
        tags.put("tag1", "Command Message example");
        tags.put("tag2", "cq1");
        
        CommandMessage commandMessage = CommandMessage.builder()
                .channel(channel)
                .body("Test Command".getBytes())
                .metadata("Metadata add some extra information")
                .tags(tags)
                .timeoutInSeconds(20)
                .build();

        CommandResponseMessage response = cqClient.sendCommandRequest(commandMessage);
        System.out.println("Command Response: " + response);
    } catch (RuntimeException e) {
        System.err.println("Failed to send command request: " + e.getMessage());
    }
}
```

### Subscribe To Commands

Subscribes to receive command messages from a Command channel.

#### Request: `CommandsSubscription` Class Attributes

| Name                     | Type                             | Description                                   | Default Value | Mandatory |
|--------------------------|----------------------------------|-----------------------------------------------|---------------|-----------|
| channel                  | String                           | The channel for the subscription.             | None          | Yes       |
| group                    | String                           | The group associated with the subscription.   | None          | No        |
| onReceiveCommandCallback | Consumer<CommandMessageReceived> | Callback function for receiving commands.     | None          | Yes       |
| onErrorCallback          | Consumer<String>                 | Callback function for error handling.         | None          | No        |

#### Response

This method doesn't return a value. It sets up a subscription that will invoke the provided callbacks.

#### Callback: `CommandMessageReceived` Class Attributes

| Name         | Type                | Description                                         |
|--------------|---------------------|-----------------------------------------------------|
| id           | String              | The unique identifier of the command message.       |
| fromClientId | String              | The ID of the client who sent the command message.  |
| timestamp    | Instant             | The timestamp when the command message was received.|
| channel      | String              | The channel through which the command message was sent. |
| metadata     | String              | Additional metadata associated with the command message. |
| body         | byte[]              | The body of the command message as bytes.           |
| replyChannel | String              | The channel to which the reply should be sent.      |
| tags         | Map<String, String> | A dictionary of key-value pairs representing tags associated with the command message. |


#### Command Response: `CommandResponseMessage` Class Attributes

When responding to a received command, you should construct a `CommandResponseMessage` with the following attributes:

| Name            | Type                    | Description                                          |
|-----------------|-------------------------|------------------------------------------------------|
| commandReceived | CommandMessageReceived  | The command message received in the response.        |
| clientId        | String                  | The client ID associated with the command response.  |
| requestId       | String                  | The unique request ID of the command response.       |
| isExecuted      | boolean                 | Indicates if the command has been executed.          |
| timestamp       | LocalDateTime           | The timestamp when the command response was created. |
| error           | String                  | The error message if there was an error.             |

#### Example

```java
public void subscribeToCommands(String channel) {
    try {
        Consumer<CommandMessageReceived> onReceiveCommandCallback = receivedCommand -> {
            System.out.println("Received Command: " + new String(receivedCommand.getBody()));
            
            // Create a response message
            CommandResponseMessage response = CommandResponseMessage.builder()
                .commandReceived(receivedCommand)
                .clientId("responder-client-id")  // Set your client ID here
                .requestId(receivedCommand.getId())  // Use the received command's ID as the request ID
                .isExecuted(true)
                .timestamp(LocalDateTime.now())
                .error(null)  // Set an error message if execution failed
                .build();

            // Send the response
            cqClient.sendResponseMessage(response);
        };

        Consumer<String> onErrorCallback = errorMessage -> {
            System.err.println("Command Subscription Error: " + errorMessage);
        };

        CommandsSubscription subscription = CommandsSubscription.builder()
                .channel(channel)
                .onReceiveCommandCallback(onReceiveCommandCallback)
                .onErrorCallback(onErrorCallback)
                .build();

        cqClient.subscribeToCommands(subscription);
        System.out.println("Subscribed to Commands channel: " + channel);
        
        // To cancel the subscription later:
        // subscription.cancel();
    } catch (RuntimeException e) {
        System.err.println("Failed to subscribe to commands: " + e.getMessage());
    }
}
```

Note: Remember to handle the subscription lifecycle appropriately in your application. You may want to store the subscription object to cancel it when it's no longer needed. Also, ensure that you properly construct and send a `CommandResponseMessage` for each received command to complete the request-response cycle.


## Commands & Queries – Queries Operations

### Create Channel

Create a new Query channel.

#### Request Parameters

| Name        | Type   | Description                             | Default Value | Mandatory |
|-------------|--------|-----------------------------------------|---------------|-----------|
| channelName | String | Name of the channel you want to create  | None          | Yes       |

#### Response

| Name              | Type    | Description                    |
|-------------------|---------|--------------------------------|
| isChannelCreated  | boolean | Indicates if channel was created |

#### Example

```java
public void createQueriesChannel(String channelName) {
    try {
        boolean isChannelCreated = cqClient.createQueriesChannel(channelName);
        System.out.println("Queries channel created: " + isChannelCreated);
    } catch (RuntimeException e) {
        System.err.println("Failed to create queries channel: " + e.getMessage());
    }
}
```

### Delete Channel

Delete an existing Query channel.

#### Request Parameters

| Name        | Type   | Description                             | Default Value | Mandatory |
|-------------|--------|-----------------------------------------|---------------|-----------|
| channelName | String | Name of the channel you want to delete  | None          | Yes       |

#### Response

| Name              | Type    | Description                    |
|-------------------|---------|--------------------------------|
| isChannelDeleted  | boolean | Indicates if channel was deleted |

#### Example

```java
public void deleteQueriesChannel(String channelName) {
    try {
        boolean isChannelDeleted = cqClient.deleteQueriesChannel(channelName);
        System.out.println("Queries channel deleted: " + isChannelDeleted);
    } catch (RuntimeException e) {
        System.err.println("Failed to delete queries channel: " + e.getMessage());
    }
}
```

### List Channels

Retrieve a list of Query channels.

#### Request Parameters

| Name         | Type   | Description                                | Default Value | Mandatory |
|--------------|--------|--------------------------------------------|---------------|-----------|
| searchString | String | Search query to filter channels (optional) | None          | No        |

#### Response

Returns a `List<CQChannel>` where each `CQChannel` has the following attributes:

| Name         | Type    | Description                                      |
|--------------|---------|--------------------------------------------------|
| name         | String  | The name of the channel.                         |
| type         | String  | The type of the channel.                         |
| lastActivity | long    | The timestamp of the last activity on the channel. |
| isActive     | boolean | Indicates whether the channel is currently active. |
| incoming     | CQStats | Statistics about incoming messages to the channel. |
| outgoing     | CQStats | Statistics about outgoing messages from the channel. |

#### Example

```java
public void listQueriesChannels(String searchString) {
    try {
        List<CQChannel> channels = cqClient.listQueriesChannels(searchString);
        System.out.println("Query Channels:");
        channels.forEach(channel -> {
            System.out.println("Name: " + channel.getName() + 
                               ", Type: " + channel.getType() + 
                               ", Active: " + channel.getIsActive());
        });
    } catch (RuntimeException e) {
        System.err.println("Failed to list query channels: " + e.getMessage());
    }
}
```

### Send Query Request

Send a query request to a Query channel.

#### Request: `QueryMessage` Class Attributes

| Name             | Type                | Description                                                                          | Default Value     | Mandatory |
|------------------|---------------------|--------------------------------------------------------------------------------------|-------------------|-----------|
| id               | String              | The ID of the query message.                                                         | None              | Yes       |
| channel          | String              | The channel through which the query message will be sent.                            | None             | Yes       |
| metadata         | String              | Additional metadata associated with the query message.                               | None              | No        |
| body             | byte[]              | The body of the query message as bytes.                                              | Empty byte array  | No        |
| tags             | Map<String, String> | A dictionary of key-value pairs representing tags associated with the query message. | Empty Map | No |
| timeoutInSeconds | int                 | The maximum time in seconds for waiting response.                                    | None     | Yes       |

#### Response: `QueryResponseMessage` Class Attributes

| Name           | Type                  | Description                                          |
|----------------|------------------------|------------------------------------------------------|
| queryReceived  | QueryMessageReceived   | The query message received in the response.          |
| clientId       | String                 | The client ID associated with the query response.    |
| requestId      | String                 | The unique request ID of the query response.         |
| executed       | boolean                | Indicates if the query has been executed.            |
| timestamp      | LocalDateTime          | The timestamp when the query response was created.   |
| metadata       | String                 | Additional metadata associated with the response.    |
| body           | byte[]                 | The body of the query response as bytes.             |
| error          | String                 | The error message if there was an error.             |

#### Example

```java
public void sendQueryRequest(String channel) {
    try {
        Map<String, String> tags = new HashMap<>();
        tags.put("tag1", "Query Message example");
        tags.put("tag2", "cq1");
        
        QueryMessage queryMessage = QueryMessage.builder()
                .channel(channel)
                .body("Test Query".getBytes())
                .metadata("Metadata add some extra information")
                .tags(tags)
                .timeoutInSeconds(20)
                .build();

        QueryResponseMessage response = cqClient.sendQueryRequest(queryMessage);
        System.out.println("Query Response: " + response);
    } catch (RuntimeException e) {
        System.err.println("Failed to send query request: " + e.getMessage());
    }
}
```

### Subscribe To Queries

Subscribes to receive query messages from a Query channel.

#### Request: `QueriesSubscription` Class Attributes

| Name                   | Type                           | Description                                   | Default Value | Mandatory |
|------------------------|--------------------------------|-----------------------------------------------|---------------|-----------|
| channel                | String                         | The channel for the subscription.             | None          | Yes       |
| group                  | String                         | The group associated with the subscription.   | None          | No        |
| onReceiveQueryCallback | Consumer<QueryMessageReceived> | Callback function for receiving queries.      | None          | Yes       |
| onErrorCallback        | Consumer<String>               | Callback function for error handling.         | None          | No        |

#### Response

This method doesn't return a value. It sets up a subscription that will invoke the provided callbacks.

#### Callback: `QueryMessageReceived` Class Attributes

| Name         | Type                | Description                                         |
|--------------|---------------------|-----------------------------------------------------|
| id           | String              | The unique identifier of the query message.         |
| fromClientId | String              | The ID of the client who sent the query message.    |
| timestamp    | Instant             | The timestamp when the query message was received.  |
| channel      | String              | The channel through which the query message was sent. |
| metadata     | String              | Additional metadata associated with the query message. |
| body         | byte[]              | The body of the query message as bytes.             |
| replyChannel | String              | The channel to which the reply should be sent.      |
| tags         | Map<String, String> | A dictionary of key-value pairs representing tags associated with the query message. |

#### Query Response: `QueryResponseMessage` Class Attributes

When responding to a received query, you should construct a `QueryResponseMessage` with the following attributes:

| Name           | Type                 | Description                                          |
|----------------|----------------------|------------------------------------------------------|
| queryReceived  | QueryMessageReceived | The query message received in the response.          |
| clientId       | String               | The client ID associated with the query response.    |
| requestId      | String               | The unique request ID of the query response.         |
| executed       | boolean              | Indicates if the query has been executed.            |
| timestamp      | LocalDateTime        | The timestamp when the query response was created.   |
| metadata       | String               | Additional metadata associated with the response.    |
| body           | byte[]               | The body of the query response as bytes.             |
| error          | String               | The error message if there was an error.             |

#### Example

```java
public void subscribeToQueries(String channel) {
    try {
        Consumer<QueryMessageReceived> onReceiveQueryCallback = receivedQuery -> {
            System.out.println("Received Query: " + new String(receivedQuery.getBody()));
            
            // Process the query and prepare a response
            String responseData = "Processed query result";
            
            // Create a response message
            QueryResponseMessage response = QueryResponseMessage.builder()
                .queryReceived(receivedQuery)
                .clientId("responder-client-id")  // Set your client ID here
                .requestId(receivedQuery.getId())  // Use the received query's ID as the request ID
                .executed(true)
                .timestamp(LocalDateTime.now())
                .metadata("Response metadata")
                .body(responseData.getBytes())
                .error(null)  // Set an error message if execution failed
                .build();

            // Send the response
            cqClient.sendQueryResponse(response);
        };

        Consumer<String> onErrorCallback = errorMessage -> {
            System.err.println("Query Subscription Error: " + errorMessage);
        };

        QueriesSubscription subscription = QueriesSubscription.builder()
                .channel(channel)
                .onReceiveQueryCallback(onReceiveQueryCallback)
                .onErrorCallback(onErrorCallback)
                .build();

        cqClient.subscribeToQueries(subscription);
        System.out.println("Subscribed to Queries channel: " + channel);
        
        // To cancel the subscription later:
        // subscription.cancel();
    } catch (RuntimeException e) {
        System.err.println("Failed to subscribe to queries: " + e.getMessage());
    }
}
```

Note: Remember to handle the subscription lifecycle appropriately in your application. You may want to store the subscription object to cancel it when it's no longer needed. Also, ensure that you properly construct and send a `QueryResponseMessage` for each received query to complete the request-response cycle.

## Queues Operations

### Create Channel

Create a new Queue channel.

#### Request Parameters

| Name        | Type   | Description                             | Default Value | Mandatory |
|-------------|--------|-----------------------------------------|---------------|-----------|
| channelName | String | Name of the channel you want to create  | None          | Yes       |

#### Response

| Name              | Type    | Description                    |
|-------------------|---------|--------------------------------|
| isChannelCreated  | boolean | Indicates if channel was created |

#### Example

```java
public void createQueueChannel(String queueChannelName) {
    try {
        boolean isChannelCreated = queuesClient.createQueuesChannel(queueChannelName);
        System.out.println("Queue Channel created: " + isChannelCreated);
    } catch (RuntimeException e) {
        System.err.println("Failed to create queue channel: " + e.getMessage());
    }
}
```

### Delete Channel

Delete an existing Queue channel.

#### Request Parameters

| Name        | Type   | Description                             | Default Value | Mandatory |
|-------------|--------|-----------------------------------------|---------------|-----------|
| channelName | String | Name of the channel you want to delete  | None          | Yes       |

#### Response

| Name              | Type    | Description                    |
|-------------------|---------|--------------------------------|
| isChannelDeleted  | boolean | Indicates if channel was deleted |

#### Example

```java
public void deleteQueueChannel(String queueChannelName) {
    try {
        boolean isChannelDeleted = queuesClient.deleteQueuesChannel(queueChannelName);
        System.out.println("Queue Channel deleted: " + isChannelDeleted);
    } catch (RuntimeException e) {
        System.err.println("Failed to delete queue channel: " + e.getMessage());
    }
}
```

### List Channels

Retrieve a list of Queue channels.

#### Request Parameters

| Name         | Type   | Description                                | Default Value | Mandatory |
|--------------|--------|--------------------------------------------|---------------|-----------|
| searchString | String | Search query to filter channels (optional) | None          | No        |

#### Response

Returns a `List<QueuesChannel>` where each `QueuesChannel` has the following attributes:

| Name         | Type        | Description                                                |
|--------------|-------------|------------------------------------------------------------|
| name         | String      | The name of the queue channel.                             |
| type         | String      | The type of the queue channel.                             |
| lastActivity | long        | The timestamp of the last activity in the queue channel.   |
| isActive     | boolean     | Indicates whether the queue channel is currently active.   |
| incoming     | QueuesStats | The statistics for incoming messages in the queue channel. |
| outgoing     | QueuesStats | The statistics for outgoing messages in the queue channel. |

#### Example

```java
public void listQueueChannels(String searchString) {
    try {
        List<QueuesChannel> channels = queuesClient.listQueuesChannels(searchString);
        System.out.println("Queue Channels:");
        for (QueuesChannel channel : channels) {
            System.out.println("Channel Name: " + channel.getName());
            System.out.println("Type: " + channel.getType());
            System.out.println("Last Activity: " + channel.getLastActivity());
            System.out.println("Is Active: " + channel.getIsActive());
            System.out.println("Incoming Stats: " + channel.getIncoming());
            System.out.println("Outgoing Stats: " + channel.getOutgoing());
            System.out.println();
        }
    } catch (RuntimeException e) {
        System.err.println("Failed to list queue channels: " + e.getMessage());
    }
}
```
### Send Queue Message

Send a message to a Queue channel.

#### Request: `QueueMessage` Class Attributes

| Name                         | Type                | Description                                                                                 | Default Value | Mandatory |
|------------------------------|---------------------|---------------------------------------------------------------------------------------------|---------------|-----------|
| id                           | String              | The unique identifier for the message.                                                      | None          | No        |
| channel                      | String              | The channel of the message.                                                                 | None          | Yes       |
| metadata                     | String              | The metadata associated with the message.                                                   | None          | No        |
| body                         | byte[]              | The body of the message.                                                                    | new byte[0]   | No        |
| tags                         | Map<String, String> | The tags associated with the message.                                                       | new HashMap<>()| No        |
| delayInSeconds               | int                 | The delay in seconds before the message becomes available in the queue.                     | None          | No        |
| expirationInSeconds          | int                 | The expiration time in seconds for the message.                                             | None          | No        |
| attemptsBeforeDeadLetterQueue| int                 | The number of receive attempts allowed for the message before it is moved to the dead letter queue. | None | No |
| deadLetterQueue              | String              | The dead letter queue where the message will be moved after reaching the maximum receive attempts. | None | No |

#### Response: `QueueSendResult` Class Attributes

| Name       | Type            | Description                                                   |
|------------|-----------------|---------------------------------------------------------------|
| id         | String          | The unique identifier of the message.                         |
| sentAt     | LocalDateTime   | The timestamp when the message was sent.                      |
| expiredAt  | LocalDateTime   | The timestamp when the message will expire.                   |
| delayedTo  | LocalDateTime   | The timestamp when the message will be delivered.             |
| isError    | boolean         | Indicates if there was an error while sending the message.    |
| error      | String          | The error message if `isError` is true.                       |

#### Example

```java
public void sendQueueMessage() {
  System.out.println("\n============================== Send Queue Message Started =============================\n");
  try {
    Map<String, String> tags = new HashMap<>();
    tags.put("tag1", "kubemq");
    tags.put("tag2", "kubemq2");
    QueueMessage message = QueueMessage.builder()
            .body("Sending data in queue message stream".getBytes())
            .channel(channelName)
            .metadata("Sample metadata")
            .id(UUID.randomUUID().toString())
            // Optional parameters
            .tags(tags)
            .delayInSeconds(1)
            .expirationInSeconds(3600)
            .attemptsBeforeDeadLetterQueue(3)
            .deadLetterQueue("dlq-" + channelName)
            .build();

    QueueSendResult sendResult = queuesClient.sendQueuesMessage(message);

    System.out.println("Message sent Response:");
    System.out.println("ID: " + sendResult.getId());
    System.out.println("Sent At: " + sendResult.getSentAt());
    System.out.println("Expired At: " + sendResult.getExpiredAt());
    System.out.println("Delayed To: " + sendResult.getDelayedTo());
    System.out.println("Is Error: " + sendResult.isError());
    if (sendResult.isError()) {
      System.out.println("Error: " + sendResult.getError());
    }
  } catch (RuntimeException e) {
    System.err.println("Failed to send queue message: " + e.getMessage());
  }

}
```

This method allows you to send a message to a specified Queue channel. You can customize various aspects of the message, such as its content, metadata, tags, delay, expiration, and dead letter queue settings. The response provides information about the sent message, including its ID, timestamps, and any potential errors.

### Receive Queue Messages

Receive messages from a Queue channel.

#### Request: `QueuesPollRequest` Class Attributes

| Name                     | Type    | Description                                          | Default Value | Mandatory |
|--------------------------|---------|------------------------------------------------------|---------------|-----------|
| channel                  | String  | The channel to poll messages from.                   | None          | Yes       |
| pollMaxMessages          | int     | The maximum number of messages to poll.              | 1             | No        |
| pollWaitTimeoutInSeconds | int     | The wait timeout in seconds for polling messages.    | 60            | No        |
| autoAckMessages             | boolean| Indicates if messages should be auto-acknowledged.  | false         | No        |
| visibilitySeconds           | int| Add a visibility timeout feature for messages.  | 0         | No        |

#### Response: `QueuesPollResponse` Class Attributes

| Name                   | Type                       | Description                                             |
|------------------------|----------------------------|---------------------------------------------------------|
| refRequestId           | String                     | The reference ID of the request.                        |
| transactionId          | String                     | The unique identifier for the transaction.              |
| messages               | List<QueueMessageReceived> | The list of received queue messages.                    |
| error                  | String                     | The error message, if any error occurred.               |
| isError                | boolean                    | Indicates if there was an error.                        |
| isTransactionCompleted | boolean                    | Indicates if the transaction is completed.              |
| activeOffsets          | List<Long>                 | The list of active offsets.                             |
| receiverClientId       | String                     | The client ID of the receiver.                          |
| visibilitySeconds      | int                        | The visibility timeout for the message in seconds.      |
| isAutoAcked            | boolean                    | Indicates whether the message was auto-acknowledged.    |


##### Response: `QueueMessageReceived` class attributes 
Here's the requested Markdown table for the `QueueMessageReceived` class:

| Name                  | Type                                  | Description                                             |
|-----------------------|---------------------------------------|---------------------------------------------------------|
| id                    | String                                | The unique identifier for the message.                  |
| channel               | String                                | The channel from which the message was received.         |
| metadata              | String                                | Metadata associated with the message.                   |
| body                  | byte[]                                | The body of the message in byte array format.           |
| fromClientId          | String                                | The ID of the client that sent the message.             |
| tags                  | Map`<String, String>`                 | Key-value pairs representing tags for the message.      |
| timestamp             | Instant                               | The timestamp when the message was created.             |
| sequence              | long                                  | The sequence number of the message.                     |
| receiveCount          | int                                   | The number of times the message has been received.       |
| isReRouted            | boolean                               | Indicates whether the message was rerouted.             |
| reRouteFromQueue      | String                                | The name of the queue from which the message was rerouted.|
| expiredAt             | Instant                               | The expiration time of the message, if applicable.      |
| delayedTo             | Instant                               | The time the message is delayed until, if applicable.   |


#### Example

```java
public void receiveQueuesMessages(String channelName) {
    try {
        QueuesPollRequest queuesPollRequest = QueuesPollRequest.builder()
                .channel(channelName)
                .pollMaxMessages(1)
                .pollWaitTimeoutInSeconds(10)
                .build();

        QueuesPollResponse pollResponse = queuesClient.receiveQueuesMessages(queuesPollRequest);
        
        System.out.println("Received Message Response:");
        System.out.println("RefRequestId: " + pollResponse.getRefRequestId());
        System.out.println("ReceiverClientId: " + pollResponse.getReceiverClientId());
        System.out.println("TransactionId: " + pollResponse.getTransactionId());
        
        if (pollResponse.isError()) {
            System.out.println("Error: " + pollResponse.getError());
        } else {
            pollResponse.getMessages().forEach(msg -> {
                System.out.println("Message ID: " + msg.getId());
                System.out.println("Message Body: " + new String(msg.getBody()));
                
                // Message handling options:
                
                // 1. Acknowledge message (mark as processed)
                msg.ack();
                
                // 2. Reject message (won't be requeued)
                // msg.reject();
                
                // 3. Requeue message (send back to queue)
                // msg.reQueue(channelName);
            });
        }
    } catch (RuntimeException e) {
        System.err.println("Failed to receive queue messages: " + e.getMessage());
    }
}


public void receiveExampleWithVisibility() {
        System.out.println("\n============================== receiveExampleWithVisibility =============================\n");
       try {
            QueuesPollRequest queuesPollRequest = QueuesPollRequest.builder()
                    .channel(channelName)
                    .pollMaxMessages(1)  // Pull 10 messages at a time
                    .pollWaitTimeoutInSeconds(10)
                    .visibilitySeconds(5)
                    .autoAckMessages(false)
                    .build();

            QueuesPollResponse pollResponse = queuesClient.receiveQueuesMessages(queuesPollRequest);

            System.out.println("Received Message Response:"+" TransactionId: " + pollResponse.getTransactionId());
            System.out.println("RefRequestId: " + pollResponse.getRefRequestId()+" ReceiverClientId: " + pollResponse.getReceiverClientId());

            if (pollResponse.isError()) {
                System.out.println("Error: " + pollResponse.getError());
            } else {
                pollResponse.getMessages().forEach(msg -> {
                    try {
                        System.out.println("Message ID: " + msg.getId()+" Message Body: " + new String(msg.getBody()));
                        Thread.sleep(1000);
                        msg.ack();
                        System.out.println("Acknowledged to message");
                    } catch (InterruptedException ex) {
                        Logger.getLogger(ReceiveMessageWithVisibilityExample.class.getName()).log(Level.SEVERE, null, ex);
                    }
                });
            }

        } catch (RuntimeException e) {
            System.err.println("Failed to receive queue messages: " + e.getMessage());
        }
    }

    public void receiveExampleWithVisibilityExpired() {
        System.out.println("\n============================== receiveExampleWithVisibilityExpired =============================\n");
        try {
            QueuesPollRequest queuesPollRequest = QueuesPollRequest.builder()
                    .channel(channelName)
                    .pollMaxMessages(1)  // Pull 10 messages at a time
                    .pollWaitTimeoutInSeconds(10)
                    .visibilitySeconds(2)
                    .autoAckMessages(false)
                    .build();

            QueuesPollResponse pollResponse = queuesClient.receiveQueuesMessages(queuesPollRequest);

            System.out.println("Received Message Response:"+" TransactionId: " + pollResponse.getTransactionId());
            System.out.println("RefRequestId: " + pollResponse.getRefRequestId()+" ReceiverClientId: " + pollResponse.getReceiverClientId());

            if (pollResponse.isError()) {
                System.out.println("Error: " + pollResponse.getError());
            } else {
                pollResponse.getMessages().forEach(msg -> {
                    try {
                        System.out.println("Message ID: " + msg.getId()+" Message Body: " + new String(msg.getBody()));
                        Thread.sleep(3000);
                        msg.ack();
                        System.out.println("Acknowledged to message");
                    } catch (InterruptedException ex) {
                        Logger.getLogger(ReceiveMessageWithVisibilityExample.class.getName()).log(Level.SEVERE, null, ex);
                    }
                });
            }

        } catch (RuntimeException e) {
            System.err.println("Failed to receive queue messages: " + e.getMessage());
        }
    }

    public void receiveExampleWithVisibilityExtension() {
        System.out.println("\n============================== receiveExampleWithVisibilityExtension =============================\n");
        try {
            QueuesPollRequest queuesPollRequest = QueuesPollRequest.builder()
                    .channel(channelName)
                    .pollMaxMessages(1)  // Pull 10 messages at a time
                    .pollWaitTimeoutInSeconds(10)
                    .visibilitySeconds(3)
                    .autoAckMessages(false)
                    .build();

            QueuesPollResponse pollResponse = queuesClient.receiveQueuesMessages(queuesPollRequest);

            System.out.println("Received Message Response:");
            System.out.println("RefRequestId: " + pollResponse.getRefRequestId());
            System.out.println("ReceiverClientId: " + pollResponse.getReceiverClientId());
            System.out.println("TransactionId: " + pollResponse.getTransactionId());

            if (pollResponse.isError()) {
                System.out.println("Error: " + pollResponse.getError());
            } else {
                pollResponse.getMessages().forEach(msg -> {
                    try {
                        System.out.println("Message ID: " + msg.getId()+" Message Body: " + new String(msg.getBody()));
                        Thread.sleep(1000);
                        msg.extendVisibilityTimer(3);
                        Thread.sleep(2000);
                        msg.ack();
                        System.out.println("Acknowledged to message");
                    } catch (InterruptedException ex) {
                        Logger.getLogger(ReceiveMessageWithVisibilityExample.class.getName()).log(Level.SEVERE, null, ex);
                    }
                });
            }

        } catch (RuntimeException e) {
            System.err.println("Failed to receive queue messages: " + e.getMessage());
        }
    }

```

This method allows you to receive messages from a specified Queue channel. You can configure the polling behavior, including the maximum number of messages to receive and the wait timeout. The response provides detailed information about the received messages and the transaction.

#### Message Handling Options:

1. **Acknowledge (ack)**: Mark the message as processed and remove it from the queue.
2. **Reject**: Reject the message. It won't be requeued.
3. **Requeue**: Send the message back to the queue for later processing.

Choose the appropriate handling option based on your application's logic and requirements.

#### Additional Example: Bulk Message Handling

This example demonstrates how to use the bulk operations `ackAll`, `rejectAll`, and `requeueAll` on the `QueuesPollResponse` object.

```java
public void receiveAndBulkHandleQueueMessages(String channelName) {
  System.out.println("\n============================== Receive and Bulk Handle Queue Messages =============================\n");
  try {
    QueuesPollRequest queuesPollRequest = QueuesPollRequest.builder()
            .channel(channelName)
            .pollMaxMessages(10)  // Increased to receive multiple messages
            .pollWaitTimeoutInSeconds(15)
            .build();

    QueuesPollResponse pollResponse = queuesClient.receiveQueuesMessages(queuesPollRequest);

    System.out.println("Received Message Response:");
    System.out.println("RefRequestId: " + pollResponse.getRefRequestId());
    System.out.println("ReceiverClientId: " + pollResponse.getReceiverClientId());
    System.out.println("TransactionId: " + pollResponse.getTransactionId());

    if (pollResponse.isError()) {
      System.out.println("Error: " + pollResponse.getError());
    } else {
      int messageCount = pollResponse.getMessages().size();
      System.out.println("Received " + messageCount + " messages.");

      // Print details of received messages
      pollResponse.getMessages().forEach(msg -> {
        System.out.println("Message ID: " + msg.getId());
        System.out.println("Message Body: " + new String(msg.getBody()));
      });

      // Acknowledge all messages
      pollResponse.ackAll();
      System.out.println("Acknowledged all messages.");

      // Reject all messages
      // pollResponse.rejectAll();
      // System.out.println("Rejected all messages.");

      // Requeue all messages
      // pollResponse.reQueueAll(channelName);
      // System.out.println("Requeued all messages.");
    }

  } catch (RuntimeException e) {
    System.err.println("Failed to receive or handle queue messages: " + e.getMessage());
  }
}
```

This example showcases the following bulk operations:

1. **ackAll()**: Acknowledges all received messages, marking them as processed and removing them from the queue.
2. **requeueAll(String channel)**: Requeues all received messages back to the specified channel for later processing.
3. **rejectAll()**: Rejects all received messages. They won't be requeued.

These bulk operations are particularly useful when you need to apply the same action to all received messages based on certain conditions or business logic. They can significantly simplify your code when dealing with multiple messages at once.


### Waiting Queue Messages

The "Waiting" operation allows you to retrieve information about messages waiting in a queue without removing them from the queue. This can be useful for monitoring queue status or implementing custom processing logic based on waiting messages.

#### Request Parameters

| Name                | Type   | Description                                            | Default Value | Mandatory |
|---------------------|--------|--------------------------------------------------------|---------------|-----------|
| channelName         | String | The name of the channel to check for waiting messages. | None          | Yes       |
| maxNumberOfMessages | int    | The maximum number of waiting messages to retrieve.    | None          | Yes       |
| waitTimeSeconds     | int    | The maximum time to wait for messages, in seconds.     | None          | Yes       |

#### Response: `QueueMessagesWaiting` Class Attributes

| Name     | Type                           | Description                             |
|----------|--------------------------------|-----------------------------------------|
| messages | List<QueueMessageWaitingPulled>| List of waiting messages in the queue.  |
| isError  | boolean                        | Indicates if there was an error.        |
| error    | String                         | The error message, if any.              |

#### `QueueMessageWaitingPulled` Class Attributes

| Name              | Type                | Description                                                       |
|-------------------|---------------------|-------------------------------------------------------------------|
| id                | String              | The unique identifier of the message.                             |
| channel           | String              | The channel name of the message.                                  |
| metadata          | String              | Additional metadata associated with the message.                  |
| body              | byte[]              | The body content of the message.                                  |
| fromClientId      | String              | The ID of the client that sent the message.                       |
| tags              | Map<String, String> | Key-value pairs of tags associated with the message.              |
| timestamp         | Instant             | The timestamp when the message was sent.                          |
| sequence          | long                | The sequence number of the message in the queue.                  |
| receiveCount      | int                 | The number of times this message has been received.               |
| isReRouted        | boolean             | Indicates if the message has been re-routed.                      |
| reRouteFromQueue  | String              | The original queue name if the message was re-routed.             |
| expiredAt         | Instant             | The timestamp when the message will expire.                       |
| delayedTo         | Instant             | The timestamp until which the message is delayed for processing.  |
| receiverClientId  | String              | The ID of the client receiving the message.                       |

#### Example

```java
public void getWaitingMessages() {
    System.out.println("\n============================== getWaitingMessages Started =============================\n");
    try {
        String channelName = "mytest-channel";
        int maxNumberOfMessages = 1;
        int waitTimeSeconds = 10;

        QueueMessagesWaiting rcvMessages = queuesClient.waiting(channelName, maxNumberOfMessages, waitTimeSeconds);
        
        if (rcvMessages.isError()) {
            System.out.println("Error occurred: " + rcvMessages.getError());
            return;
        }
        
        System.out.println("Waiting Messages Count: " + rcvMessages.getMessages().size());
        
        for (QueueMessageWaitingPulled msg : rcvMessages.getMessages()) {
            System.out.println("Message ID: " + msg.getId());
            System.out.println("Channel: " + msg.getChannel());
            System.out.println("Metadata: " + msg.getMetadata());
            System.out.println("Body: " + new String(msg.getBody()));
            System.out.println("From Client ID: " + msg.getFromClientId());
            System.out.println("Tags: " + msg.getTags());
            System.out.println("Timestamp: " + msg.getTimestamp());
            System.out.println("Sequence: " + msg.getSequence());
            System.out.println("Receive Count: " + msg.getReceiveCount());
            System.out.println("Is Re-routed: " + msg.isReRouted());
            System.out.println("Re-route From Queue: " + msg.getReRouteFromQueue());
            System.out.println("Expired At: " + msg.getExpiredAt());
            System.out.println("Delayed To: " + msg.getDelayedTo());
            System.out.println("Receiver Client ID: " + msg.getReceiverClientId());
            System.out.println("--------------------");
        }
    } catch (RuntimeException e) {
        System.err.println("Failed to get waiting messages: " + e.getMessage());
    }
}
```

This method allows you to peek at messages waiting in a specified queue channel without removing them. It's particularly useful for:

#### Important Notes:
1. Monitoring queue depth and content.
2. Implementing custom logic based on the number or content of waiting messages.
3. Previewing messages before deciding whether to process them.


### Pull Messages

The "Pull Messages" operation allows you to retrieve and remove messages from a queue. Unlike the "Waiting" operation, this actually dequeues the messages, making them unavailable for other consumers.

#### Request Parameters

| Name                | Type   | Description                                            | Default Value | Mandatory |
|---------------------|--------|--------------------------------------------------------|---------------|-----------|
| channelName         | String | The name of the channel to pull messages from.         | None          | Yes       |
| maxNumberOfMessages | int    | The maximum number of messages to pull.                | None          | Yes       |
| waitTimeSeconds     | int    | The maximum time to wait for messages, in seconds.     | None          | Yes       |

#### Response: `QueueMessagesPulled` Class Attributes

| Name     | Type                           | Description                             |
|----------|--------------------------------|-----------------------------------------|
| messages | List<QueueMessageWaitingPulled>| List of pulled messages from the queue. |
| isError  | boolean                        | Indicates if there was an error.        |
| error    | String                         | The error message, if any.              |

#### `QueueMessageWaitingPulled` Class Attributes

| Name              | Type                | Description                                                       |
|-------------------|---------------------|-------------------------------------------------------------------|
| id                | String              | The unique identifier of the message.                             |
| channel           | String              | The channel name of the message.                                  |
| metadata          | String              | Additional metadata associated with the message.                  |
| body              | byte[]              | The body content of the message.                                  |
| fromClientId      | String              | The ID of the client that sent the message.                       |
| tags              | Map<String, String> | Key-value pairs of tags associated with the message.              |
| timestamp         | Instant             | The timestamp when the message was sent.                          |
| sequence          | long                | The sequence number of the message in the queue.                  |
| receiveCount      | int                 | The number of times this message has been received.               |
| isReRouted        | boolean             | Indicates if the message has been re-routed.                      |
| reRouteFromQueue  | String              | The original queue name if the message was re-routed.             |
| expiredAt         | Instant             | The timestamp when the message will expire.                       |
| delayedTo         | Instant             | The timestamp until which the message is delayed for processing.  |
| receiverClientId  | String              | The ID of the client receiving the message.                       |

#### Example

```java
public void getPullMessages() {
    System.out.println("\n============================== getPullMessages Started =============================\n");
    try {
        String channelName = "mytest-channel";
        int maxNumberOfMessages = 1;
        int waitTimeSeconds = 10;

        QueueMessagesPulled rcvMessages = queuesClient.pull(channelName, maxNumberOfMessages, waitTimeSeconds);

        if (rcvMessages.isError()) {
            System.out.println("Error occurred: " + rcvMessages.getError());
            return;
        }

        System.out.println("Pulled Messages Count: " + rcvMessages.getMessages().size());

        for (QueueMessageWaitingPulled msg : rcvMessages.getMessages()) {
            System.out.println("Message ID: " + msg.getId());
            System.out.println("Channel: " + msg.getChannel());
            System.out.println("Metadata: " + msg.getMetadata());
            System.out.println("Body: " + new String(msg.getBody()));
            System.out.println("From Client ID: " + msg.getFromClientId());
            System.out.println("Tags: " + msg.getTags());
            System.out.println("Timestamp: " + msg.getTimestamp());
            System.out.println("Sequence: " + msg.getSequence());
            System.out.println("Receive Count: " + msg.getReceiveCount());
            System.out.println("Is Re-routed: " + msg.isReRouted());
            System.out.println("Re-route From Queue: " + msg.getReRouteFromQueue());
            System.out.println("Expired At: " + msg.getExpiredAt());
            System.out.println("Delayed To: " + msg.getDelayedTo());
            System.out.println("Receiver Client ID: " + msg.getReceiverClientId());
            System.out.println("--------------------");
        }
    } catch (RuntimeException e) {
        System.err.println("Failed to pull messages: " + e.getMessage());
    }
}
```

This example demonstrates how to pull messages from a specified queue channel, process them, and access all the available metadata for each message.

#### Important Notes:

- The `pull` operation removes messages from the queue. Once pulled, these messages are no longer available to other consumers.
- The `QueueMessagesPulled` object includes an `isError` flag and an `error` string, which should be checked before processing the messages.
- The structure of `QueueMessageWaitingPulled` is the same for both "waiting" and "pull" operations, providing consistent access to message metadata.


# Java

The **KubeMQ SDK for Java** enables Java developers to communicate with [KubeMQ](https://kubemq.io/) server.

## Prerequisites

- Java Development Kit (JDK) 8 or higher
- Maven
- KubeMQ server running locally or accessible over the network

## Install KubeMQ Community Edition
Please visit [KubeMQ Community](https://github.com/kubemq-io/kubemq-community) for intallation steps.

## General SDK description
The SDK implements all communication patterns available through the KubeMQ server:
- Events
- EventStore
- Command
- Query
- Queue

### Installing

The recommended way to use the SDK for Java in your project is to consume it from Maven.

    <dependency>
       <groupId>io.kubemq.sdk</groupId>
       <artifactId>kubemq-sdk-Java</artifactId>
       <version>2.0.0</version>
    </dependency>

To build with Gradle, add the dependency below to your build.gradle file.

``` java
compile group: 'io.kubemq.sdk', name: 'kubemq-sdk-Java', version: '2.0.0'
```

## Running the examples

The [examples](https://github.com/kubemq-io/kubemq-java-example)
are standalone projects that showcase the usage of the SDK.

To run the examples, you need to have a running instance of KubeMQ.
Import the project in any IDE of choice like IntelliJ , Eclipse or Netbeans .
You will see three packages in example project which contains files to showing
implementation.
Packages are:

    io.kubemq.example.cq
    io.kubemq.example.pubsub
    io.kubemq.example.queues
**cq** package contains the example related to Command and Query
**pubsub** package contains the example related to Event and EventStore
**queues** package contains the example related to Queues


## Building from source

Once you check out the code from GitHub, you can build it using Maven.

``` bash
mvn clean install
```
Above command will runt the test and install the jar file to your local maven repository.
If you wish to skip the test then use below command to build.
```bash
mvn clean install -DskipTests=true
```

## Payload Details

- **Metadata:** The metadata allows us to pass additional information with the event. Can be in any form that can be presented as a string, i.e., struct, JSON, XML and many more.
- **Body:** The actual content of the event. Can be in any form that is serializable into a byte array, i.e., string, struct, JSON, XML, Collection, binary file and many more.
- **ClientID:** Displayed in logs, tracing, and KubeMQ dashboard(When using Events Store, it must be unique).
- **Tags:** Set of Key value pair that help categorize the message

# KubeMQ PubSub Client Examples
Below examples demonstrating the usage of KubeMQ PubSub (Event and EventStore) client. The examples include creating, deleting, listing channels, and sending/subscribing event messages.

## Project Structure

- `CreateChannelExample.java`: Demonstrates creating event & eventStore channels.
- `DeleteChannelExample.java`: Demonstrates deleting event & eventStore channels.
- `ListEventsChanneExample.java`: Demonstrates listing event & eventStore channels.
- `SendEventMessageExample.java`: Demonstrates sending message in event & eventStore channels.
- `SubscribeToEventExample.java`: Demonstrates subscribing to event & eventStore channels.

## Getting Started

### Construct the PubSubClient
For executing PubSub operation we have to create the instance of PubSubClient, it's instance can created with minimum two parameter `address` (KubeMQ server address) & `clientId` . With these two parameter plainText connection are established.
 ```java
 PubSubClient pubSubClient = PubSubClient.builder()
        .address(address)
        .clientId(clientId)
        .build();
```              

Below example demonstrate to construct PubSubClient with ssl and other configurations:
 ```java
 PubSubClient pubSubClient = PubSubClient.builder()
        .address(address)
        .clientId(clientId)
        .authToken("authToken")
        .tls(true)
        .tlsCertFile("path/to/cert/file")
        .tlsKeyFile("path/to/key/file")
        .maxReceiveSize(4 * 1048576)  // 4 MB
        .reconnectIntervalSeconds(10)
        .keepAlive(true)
        .pingIntervalInSeconds(5)
        .pingTimeoutInSeconds(10)
        .logLevel(Level.INFO)
        .build();
```    

**Ping To KubeMQ server**
You can ping the server to check connection is established or not
```java
ServerInfo pingResult = pubSubClient.ping();
System.out.println("Ping Response: " + pingResult.toString());

```
**PubSub CreateEventsChannel Example:**
```java
public void createEventsChannel() {
        try {
            boolean isChannelCreated = pubSubClient.createEventsChannel(eventChannelName);
            System.out.println("EventsChannel created: " + isChannelCreated);
        } catch (RuntimeException e) {
            System.err.println("Failed to create events channel: " + e.getMessage());
        }
    }
```
**PubSub CreateEventsStoreChannel Example:**
```java 
    public void createEventsStoreChannel() {
        try {
            boolean isChannelCreated = pubSubClient.createEventsStoreChannel(eventStoreChannelName);
            System.out.println("EventsStoreChannel created: " + isChannelCreated);
        } catch (RuntimeException e) {
            System.err.println("Failed to create events store channel: " + e.getMessage());
        }
    }
```
**PubSub ListEventsChannel Example:**
```java   
  public void listEventsChannel() {
        try {
           System.out.println("Events Channel listing");
           List<PubSubChannel> eventChannel = pubSubClient.listEventsChannels(searchQuery);
           eventChannel.forEach(  evt -> {
               System.out.println("Name: "+evt.getName()+" ChannelTYpe: "+evt.getType()+" isActive: "+evt.getIsActive());
           });
           
        } catch (RuntimeException e) {
            System.err.println("Failed to list event channel: " + e.getMessage());
        }
    }
```
**PubSub ListEventsStoreChannel Example:**
```java 
    public void listEventsStoreChannel() {
        try {
           System.out.println("Events Channel listing");
           List<PubSubChannel> eventChannel = pubSubClient.listEventsStoreChannels(searchQuery);
           eventChannel.forEach(  evt -> {
               System.out.println("Name: "+evt.getName()+" ChannelTYpe: "+evt.getType()+" isActive: "+evt.getIsActive());
           });
        } catch (RuntimeException e) {
            System.err.println("Failed to list events store channel: " + e.getMessage());
        }
    }
```
**PubSub SendEventMessage Example:**
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
**PubSub SendEventStoreMessage Example:**
```java 
    public void sendEventStoreMessage() {
        try {
            String data = "Any data can be passed in byte, JSON or anything";
            Map<String, String> tags = new HashMap<>();
            tags.put("tag1", "kubemq");
            tags.put("tag2", "kubemq2");

            EventStoreMessage eventStoreMessage = EventStoreMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .channel(eventStoreChannelName)
                    .metadata("something you want to describe")
                    .body(data.getBytes())
                    .tags(tags)
                    .build();
            
            EventSendResult result = pubSubClient.sendEventsStoreMessage(eventStoreMessage);
            System.out.println("Send event result: " + result);
        } catch (RuntimeException e) {
            System.err.println("Failed to send event store message: " + e.getMessage());
        }
    }
```
**PubSub SubscribeEvents Example:**
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
            
            // *** When you want to cancel subscrtipn call cancel function
                subscription.cancel();
            
        } catch (RuntimeException e) {
            System.err.println("Failed to subscribe to events: " + e.getMessage());
        }
    }
```
**PubSub SubscribeEventsStore Example:**
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

		// *** When you want to cancel subscrtipn call cancel function
                subscription.cancel();
           
        } catch (RuntimeException e) {
            System.err.println("Failed to subscribe to events store: " + e.getMessage());
        }
    }
```
**PubSub DeleteEventsChannel Example:**
```java 
public void deleteEventsChannel() {
        try {
            boolean isChannelDeleted = pubSubClient.deleteEventsChannel(eventChannelName);
            System.out.println("Events Channel deleted: " + isChannelDeleted);
        } catch (RuntimeException e) {
            System.err.println("Failed to delete events channel: " + e.getMessage());
        }
    }

```
**PubSub DeleteEventsStoreChannel Example:**
```java 
    public void deleteEventsStoreChannel() {
        try {
            boolean isChannelDeleted = pubSubClient.deleteEventsStoreChannel(eventStoreChannelName);
            System.out.println("Events store Channel deleted: " + isChannelDeleted);
        } catch (RuntimeException e) {
            System.err.println("Failed to delete events store channel: " + e.getMessage());
        }
    }
```

# KubeMQ Queues Client Examples
Below examples demonstrating the usage of KubeMQ Queues client. The examples include creating, deleting, listing channels, and sending/receiving queues messages.

## Project Structure

- `CreateQueuesChannelExample.java`: Demonstrates creating queues channels.
- `DeleteQueuesChannelExample.java`: Demonstrates deleting queues channels.
- `ListQueuesChannelExample.java`: Demonstrates listing queues channels.
- `GetQueuesInfoExample`: Demonstrates getting the detailed information of queue.
- `SendQueuesMessageExample.java`: Demonstrates sending message in queue channels.
- `Send_ReceiveMessageUsingStreamExample.java`: Demonstrates sending meesage using queue upstream and receive message using downstream.

## Getting Started

### Construct the QueuesClient
For executing Queues operation we have to create the instance of QueuesClient, it's instance can created with minimum two parameter `address` (KubeMQ server address) & `clientId` . With these two parameter plainText connection are established.
 ```java
 QueuesClient queuesClient = QueuesClient.builder()
                 .address(address)
                 .clientId(clientId)
                 .build();
```              

Below example demonstrate to construct PubSubClient with ssl and other configurations:
 ```java
 QueuesClient queuesClient = QueuesClient.builder()
                 .address(address)
                 .clientId(clientId)
                 .authToken("authToken") 
                 .tls(true) 
                 .tlsCertFile("path/to/cert/file") 
                 .tlsKeyFile("path/to/key/file") 
                 .maxReceiveSize(4 * 1048576)  // 4 MB
                 .reconnectIntervalSeconds(10)
                 .keepAlive(true) 
                 .pingIntervalInSeconds(5) 
                 .pingTimeoutInSeconds(10) 
                 .logLevel(Level.INFO)
                 .build();
```   

**Ping To KubeMQ server**
You can ping the server to check connection is established or not
```java
ServerInfo pingResult = queuesClient.ping();
System.out.println("Ping Response: " + pingResult.toString());

```
**Queues CreateQueueChannel Example:**
```java
public void createQueueChannel() {
        try {
            boolean isChannelCreated = queuesClient.createQueuesChannel(queueChannelName);
            System.out.println("QueueChannel created: " + isChannelCreated);
        } catch (RuntimeException e) {
            System.err.println("Failed to create queue channel: " + e.getMessage());
        }
    }
```   
**Queues listQueueChannels Example:**
```java 
public void listQueueChannels() {
        try {
            List<QueuesChannel> channels = queuesClient.listQueuesChannels("");
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
**Queues GetQueueDetails Example:**
```java 
public void getQueueDetails() {
        try {
            // Get the queue information
            QueuesDetailInfo queuesDetailInfo = queuesClient.getQueuesInfo(channelName);

            // Print the queue information
            System.out.println("Queue Information:");
            System.out.println("RefRequestID: " + queuesDetailInfo.getRefRequestID());
            System.out.println("TotalQueue: " + queuesDetailInfo.getTotalQueue());
            System.out.println("Sent: " + queuesDetailInfo.getSent());
            System.out.println("Delivered: " + queuesDetailInfo.getDelivered());
            System.out.println("Waiting: " + queuesDetailInfo.getWaiting());

            queuesDetailInfo.getQueues().forEach(queueInfo -> {
                System.out.println("Queue Name: " + queueInfo.getName());
                System.out.println("Messages: " + queueInfo.getMessages());
                System.out.println("Bytes: " + queueInfo.getBytes());
                System.out.println("FirstSequence: " + queueInfo.getFirstSequence());
                System.out.println("LastSequence: " + queueInfo.getLastSequence());
                System.out.println("Sent: " + queueInfo.getSent());
                System.out.println("Delivered: " + queueInfo.getDelivered());
                System.out.println("Waiting: " + queueInfo.getWaiting());
                System.out.println("Subscribers: " + queueInfo.getSubscribers());
            });
        } catch (Exception e) {
            System.out.println("Error while getting queue information");
            e.printStackTrace();
        }
    }
```   
**Queues SendSingleMessage Example:**
```java

public void sendSingleMessage() {
        try {
            Map<String, String> tags = new HashMap<>();
            tags.put("tag1", "kubemq");
            tags.put("tag2", "kubemq2");

            QueueMessage message = QueueMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .body("Hello KubeMQ!".getBytes())
                    .channel(queueChannelName)
                    .metadata("metadata")
                    .tags(tags)
                    .expirationInSeconds(60 * 10) // 10 minutes
                    .build();

            QueueSendResult sendResult = queuesClient.sendQueuesMessage(message);
            System.out.println("Message sent result: " + sendResult);
        } catch (RuntimeException e) {
            System.err.println("Failed to send message: " + e.getMessage());
            e.printStackTrace();
        }
    }
```   
**Queues SendBatchMessage Example:**
```java

    public void sendBatchMessages() {
        try {
            Map<String, String> tags = new HashMap<>();
            tags.put("tag1", "kubemq");
            tags.put("tag2", "kubemq2");

            QueueMessage message1 = QueueMessage.builder()
                    .body("Message 1".getBytes())
                    .channel(queueChannelName)
                    .id(UUID.randomUUID().toString())
                    .tags(tags)
                    .build();

            QueueMessage message2 = QueueMessage.builder()
                    .body("Message 2".getBytes())
                    .channel(queueChannelName)
                    .id(UUID.randomUUID().toString())
                    .build();

            List<QueueMessage> messages = Arrays.asList(message1, message2);
            String batchId = UUID.randomUUID().toString();
            QueueMessagesBatchSendResult batchSendResult = queuesClient.sendQueuesMessageInBatch(messages, batchId);
            System.out.println("Batch messages sent result: " + batchSendResult);
        } catch (RuntimeException e) {
            System.err.println("Failed to send batch messages: " + e.getMessage());
            e.printStackTrace();
        }
    }
```   
**Queues SendQueueMessage Using UpStream Example:**
```java

 public void sendQueueMessage() {
         System.out.println("\n============================== sendMessage Started =============================\n");
            // Send message in Stream 
            QueueMessage message = QueueMessage.builder()
                    .body(("Sending data in queue message stream").getBytes())
                    .channel(channelName)
                    .metadata("metadata")
                    .id(UUID.randomUUID().toString())
                    .build();
            QueueSendResult sendResult = queuesClient.sendQueuesMessageUpStream(message);

            System.out.println("Message sent Response: " + sendResult);

    }
   
```
**Queues ReceiveQueueMessage Using DownStream Example:**
```java 

    public void receiveQueuesMessages() {
        System.out.println("\n============================== receiveQueuesMessages =============================\n");

        QueuesPollRequest queuesPollRequest = QueuesPollRequest.builder()
                .channel(channelName)
                .pollMaxMessages(1)
                .pollWaitTimeoutInSeconds(10)
                .build();

       QueuesPollResponse pollResponse = queuesClient.receiveQueuesMessagesDownStream(queuesPollRequest);
       
        System.out.println("Received Message: {}" + pollResponse);

            System.out.println("RefRequestId: " + pollResponse.getRefRequestId());
            System.out.println("ReceiverClientId: " + pollResponse.getReceiverClientId());
            System.out.println("TransactionId: " + pollResponse.getTransactionId());
            pollResponse.getMessages().forEach(msg -> {
                System.out.println("Message  Id: " + msg.getId());
                System.out.println("Message Body: "+ByteString.copyFrom(msg.getBody()).toStringUtf8());
               // Acknowledge message
               msg.ack();
               
               // *** Reject message
              // msg.reject();
              
               // *** ReQueue message
              // msg.reQueue(channelName);
            });

    }
```

# KubeMQ Command & Query Client Examples

Below examples demonstrating the usage of KubeMQ CQ (Commands and Queries) Client. The examples include creating, deleting, listing channels, and sending/subscribing to command and query messages.

## Project Structure

- `CommandsExample.java`: Demonstrates sending and subscribing to command messages.
- `CreateExample.java`: Demonstrates creating command and query channels.
- `DeleteExample.java`: Demonstrates deleting command and query channels.
- `ListExample.java`: Demonstrates listing command and query channels.
- `QueriesExample.java`: Demonstrates sending and subscribing to query messages.

## Getting Started

### Construct the CQClient
For executing command & query operation we have to create the instance of CQClient, it's instance can created with minimum two parameter `address` (KubeMQ server address) & `clientId` . With these two parameter plainText connection are established.
 ```java
 CQClient cqClient = CQClient.builder()
                 .address(address)
                 .clientId(clientId)
                 .build();
```              

Below example demonstrate to construct CQClient with ssl and other configurations:
 ```java
 CQClient cqClient = CQClient.builder()
                 .address(address)
                 .clientId(clientId)
                 .authToken("authToken") 
                 .tls(true) 
                 .tlsCertFile("path/to/cert/file") 
                 .tlsKeyFile("path/to/key/file") 
                 .maxReceiveSize(4 * 1048576)  // 4 MB
                 .reconnectIntervalSeconds(10)
                 .keepAlive(true) 
                 .pingIntervalInSeconds(5) 
                 .pingTimeoutInSeconds(10) 
                 .logLevel(Level.INFO)
                 .build();
```    

**Ping To KubeMQ server**
You can ping the server to check connection is established or not
```java
ServerInfo pingResult = cqClient.ping();
System.out.println("Ping Response: " + pingResult.toString());

```

**Command CreateCommandsChannel Example:**
```java

 private void createCommandsChannel(String channel) {
          System.out.println("Executing createCommandsChannel...");
        boolean result = cqClient.createCommandsChannel(channel);
        System.out.println("Commands channel created: " + result);
    }
```
**Queries CreateQueriesChannel Example:**
```java 
    private void createQueriesChannel(String channel) {
        System.out.println("\nExecuting createQueriesChannel...");
        boolean result = cqClient.createQueriesChannel(channel);
        System.out.println("Queries channel created: " + result);
    }
```
**Command ListCommandsChannel Example:**
```java 
 private void listCommandsChannels(String channelSearch) {
         System.out.println("\nExecuting listCommandsChannels...");
        List<CQChannel> channels = cqClient.listCommandsChannels(channelSearch);
        System.out.println("Command Channels: " + channels);
    }
```
**Queries ListQueriesChannel Example:**
```java 
    private void listQueriesChannels(String channelSearch) {
         System.out.println("\nExecuting listQueriesChannels...");
        List<CQChannel> channels = cqClient.listQueriesChannels(channelSearch);
        System.out.println("Query Channels: " + channels);
    }
```
**Command SubscribeToCommandsChannel Example:**
```java 
private void subscribeToCommands(String channel) {
        // Consumer for handling received events
        Consumer<CommandMessageReceived> onReceiveCommandCallback = receivedCommand -> {
            System.out.println("Received CommandMessage: " + receivedCommand);
            // Reply this message 
           CommandResponseMessage response= CommandResponseMessage.builder().
                    commandReceived(receivedCommand)
                     .isExecuted(true)
                     .build();
            cqClient.sendResponseMessage(response);
        };

        // Consumer for handling errors
        Consumer<String> onErrorCallback = errorMessage -> {
            System.err.println("Error in Command Subscription: " + errorMessage);
        };

        CommandsSubscription subscription = CommandsSubscription.builder()
                .channel(channel)
                .onReceiveCommandCallback(onReceiveCommandCallback)
                .onErrorCallback(onErrorCallback)
                .build();

        cqClient.subscribeToCommands(subscription);
        System.out.println("");
        
        // *** When you want to cancel subscrtipn call cancel function
        //subscription.cancel();
    }
```
**Command SendCommandRequest Example:**
```java 
   private void sendCommandRequest(String channel) {
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
    }
```
**Command DeleteCommandsChannel Example:**
```java 
 private void deleteCommandsChannel(String channel) {
        System.out.println("Executing deleteCommandsChannel...");
        boolean result = cqClient.deleteCommandsChannel(channel);
        System.out.println("Commands channel deleted: " + result);
    }
```
**Queries DeleteQueriesChannel Example:**
```java 
    private void deleteQueriesChannel(String channel) {
    System.out.println("Executing deleteQueriesChannel...");
    boolean result = cqClient.deleteQueriesChannel(channel);
    System.out.println("Queries channel deleted: " + result);
}
```


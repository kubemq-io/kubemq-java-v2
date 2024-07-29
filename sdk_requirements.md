# SDK Client Requirements

## Types of Clients

The SDK should support the following types of clients:
1. PubSub
2. Queues
3. Commands & Query (CQ)

## Common Functions for all clients
1. Client Initialization with configuration
2. Ping
3. Create Channel
4. Delete Channel
5. List Channels
6. Close/Shutdown 

### Ping

**Name**

ping

**Input Parameters**

None

**Output Results**

| Type       | Description                                          |
|------------|------------------------------------------------------|
| serverInfo | server info object, exception with details otherwise |

### Create Channel

**Name** 

| Client | Message Type | Function Name            |
|--------|--------------|--------------------------|
| PubSub | Events       | createEventsChannel      |
| PubSub | Events Store | createEventsStoreChannel |
| Queues | Queues       | createQueuesChannel      |
| CQ     | Commands     | createCommandsChannel    |
| CQ     | Queries      | createQueriesChannel     |


**Input Parameters**

| Name        | Type   | Description                       |
|-------------|--------|-----------------------------------|
| channelName | string | the name of the channel to create |

**Output Results**

| Type | Description                                                    |
|------|----------------------------------------------------------------|
| bool | True if operation successful, exception with details otherwise |


**GRPC RPC To Use**
1. Use the CQ request for sending the command

### Delete Channel

**Name**

| Client | Message Type | Function Name            |
|--------|--------------|--------------------------|
| PubSub | Events       | deleteEventsChannel      |
| PubSub | Events Store | deleteEventsStoreChannel |
| Queues | Queues       | deleteQueuesChannel      |
| CQ     | Commands     | deleteCommandsChannel    |
| CQ     | Queries      | deleteQueriesChannel     |


**Input Parameters**

| Name        | Type   | Description                       |
|-------------|--------|-----------------------------------|
| channelName | string | the name of the channel to delete |

**Output Results**

| Type | Description                                                    |
|------|----------------------------------------------------------------|
| bool | True if operation successful, exception with details otherwise |


**GRPC RPC To Use**
1. Use the CQ request for sending the command


### List Channels

**Name**

| Client | Message Type | Function Name           |
|--------|--------------|-------------------------|
| PubSub | Events       | listEventsChannels      |
| PubSub | Events Store | listEventsStoreChannels |
| Queues | Queues       | listQueuesChannels      |
| CQ     | Commands     | listCommandsChannels    |
| CQ     | Queries      | listQueriesChannels     |


**Input Parameters**

| Name          | Type   | Description                     |
|---------------|--------|---------------------------------|
| searchPattern | string | regex string to match the list  |

**Output Results**

| Type             | Description                       |
|------------------|-----------------------------------|
| List of channels | The list of channels or exception |


**GRPC RPC To Use**
1. Use the CQ request for sending the command


### Close/Shutdown

**Name**

close

**Input Parameters**

None

**Output Results**

None


## PubSub Functions

### Send 

**Definitions**

| Type         | Name                   | Input             | Result      |
|--------------|------------------------|-------------------|-------------|
| Events       | sendEventMessage       | EventMessage      | No result   |
| Events Store | sendEventsStoreMessage | EventStoreMessage | Send Result |


**GRPC RPC To Use**
1. All sending functions must use the SendEventsStream grpc function
2. When the client is initialized, the client must create the stream object and be ready to send the messages on the grpc channel


### Subscribe

**Definitions**

| Type         | Name                   | Input                             |
|--------------|------------------------|-----------------------------------|
| Events       | subscribeToEvents      | Events Subscription Request       |
| Events Store | subscribeToEventsStore | Events Store Subscription Request |

**CallBacks**

Each subscription request must have 2 callbacks:
1. On Receive Message
2. On Error


**GRPC RPC To Use**
1. All subscriptions must use the SubscribeToEvents grpc function


## Queues Functions

### Send

**Function Name**

sendQueuesMessage

**Input Parameters**


**Input Parameters**

| Name     | Type          | Description           |
|----------|---------------|-----------------------|
| message | Queue Message | Queue Message to send |

**Output Parameters**

Send Queue Message result or exception


**GRPC RPC To Use**
1. All sending functions must use the QueuesUpstream grpc function in order to send the message


### Receive 

**Function Name**

receiveQueuesMessages

**Input Parameters**


| Name                 | Type   | Description                                     |
|----------------------|--------|-------------------------------------------------|
| channel              | string | channel name                                    |
| maxMessages          | int    | max messages to pull - default is 1             |
| waitTimeoutInSeconds | int    | the time to wait for all the messages to arrive |
| autoAck              | bool   | auto ack when pulling the messages              |


**Output Parameters**

Receive Queue Results

This object contains a list of all messages received and can do 3 functions:
1. AckAll - will acc all the messages received
2. RejectAll - will reject all the messages received
3. RequeueAll - will re queue all the messages received to specific channel


For every received message, three functions can be called:
1. Ack – will ack this message
2. Reject – will reject this message
3. Requeue – will requeue this message with a specified channel

## CQ Functions

### Send

**Definitions**

| Type     | Name               | Input         | Result          |
|----------|--------------------|---------------|-----------------|
| Commands | sendCommandRequest | CommandRequest | CommandResponse |
| Queries  | sendQueriesRequest | QueryRequest  | QueryResponse   |


**GRPC RPC To Use**
1. All sending functions must use the SendRequest grpc function




### Subscribe

**Definitions**

| Type         | Name                | Input                         |
|--------------|---------------------|-------------------------------|
| Commands       | subscribeToCommands | Commands Subscription Request |
| Queries | subscribeToQueries  | Queries Subscription Request  |

**CallBacks**

Each subscription request must have 2 callbacks:
1. On Receive Request
2. On Error

**Sending Results** 

When the request is received, a send response call should be send.
CommandResponseMessage for commands and QueryResponseMessage for queries


| Type         | Name                | Input                         |
|--------------|---------------------|-------------------------------|
| Commands       | subscribeToCommands | Commands Subscription Request |
| Queries | subscribeToQueries  | Queries Subscription Request  |


**GRPC RPC To Use**
1. All subscriptions must use the SubscribeToRequests grpc function

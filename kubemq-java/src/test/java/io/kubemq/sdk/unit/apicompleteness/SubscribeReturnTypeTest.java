package io.kubemq.sdk.unit.apicompleteness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.cq.CommandsSubscription;
import io.kubemq.sdk.cq.QueriesSubscription;
import io.kubemq.sdk.pubsub.EventsStoreSubscription;
import io.kubemq.sdk.pubsub.EventsStoreType;
import io.kubemq.sdk.pubsub.EventsSubscription;
import io.kubemq.sdk.pubsub.PubSubClient;
import kubemq.kubemqGrpc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tests that subscribe methods return the subscription handle (REQ-API-1). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscribeReturnTypeTest {

  @Mock private kubemqGrpc.kubemqStub mockAsyncStub;

  private PubSubClient pubSubClient;
  private CQClient cqClient;

  @BeforeEach
  void setup() {
    pubSubClient =
        PubSubClient.builder().address("localhost:50000").clientId("test-client").build();
    cqClient = CQClient.builder().address("localhost:50000").clientId("test-client").build();
  }

  @AfterEach
  void teardown() {
    if (pubSubClient != null) pubSubClient.close();
    if (cqClient != null) cqClient.close();
  }

  @Test
  @DisplayName("API-1: subscribeToEvents returns the same subscription object")
  void subscribeToEvents_returnsSubscription() {
    doNothing().when(mockAsyncStub).subscribeToEvents(any(), any());
    pubSubClient.setAsyncStub(mockAsyncStub);

    EventsSubscription input =
        EventsSubscription.builder()
            .channel("test-channel")
            .onReceiveEventCallback(event -> {})
            .onErrorCallback(err -> {})
            .build();

    EventsSubscription returned = pubSubClient.subscribeToEvents(input);

    assertNotNull(returned);
    assertSame(input, returned);
  }

  @Test
  @DisplayName("API-2: subscribeToEventsStore returns the same subscription object")
  void subscribeToEventsStore_returnsSubscription() {
    doNothing().when(mockAsyncStub).subscribeToEvents(any(), any());
    pubSubClient.setAsyncStub(mockAsyncStub);

    EventsStoreSubscription input =
        EventsStoreSubscription.builder()
            .channel("test-channel")
            .eventsStoreType(EventsStoreType.StartNewOnly)
            .onReceiveEventCallback(event -> {})
            .onErrorCallback(err -> {})
            .build();

    EventsStoreSubscription returned = pubSubClient.subscribeToEventsStore(input);

    assertNotNull(returned);
    assertSame(input, returned);
  }

  @Test
  @DisplayName("API-3: subscribeToCommands returns the same subscription object")
  void subscribeToCommands_returnsSubscription() {
    doNothing().when(mockAsyncStub).subscribeToRequests(any(), any());
    cqClient.setAsyncStub(mockAsyncStub);

    CommandsSubscription input =
        CommandsSubscription.builder()
            .channel("test-channel")
            .onReceiveCommandCallback(cmd -> {})
            .onErrorCallback(err -> {})
            .build();

    CommandsSubscription returned = cqClient.subscribeToCommands(input);

    assertNotNull(returned);
    assertSame(input, returned);
  }

  @Test
  @DisplayName("API-4: subscribeToQueries returns the same subscription object")
  void subscribeToQueries_returnsSubscription() {
    doNothing().when(mockAsyncStub).subscribeToRequests(any(), any());
    cqClient.setAsyncStub(mockAsyncStub);

    QueriesSubscription input =
        QueriesSubscription.builder()
            .channel("test-channel")
            .onReceiveQueryCallback(query -> {})
            .onErrorCallback(err -> {})
            .build();

    QueriesSubscription returned = cqClient.subscribeToQueries(input);

    assertNotNull(returned);
    assertSame(input, returned);
  }
}

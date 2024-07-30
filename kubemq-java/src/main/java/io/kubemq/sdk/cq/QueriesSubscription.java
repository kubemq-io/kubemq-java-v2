package io.kubemq.sdk.cq;

import kubemq.Kubemq.Subscribe;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.function.Consumer;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueriesSubscription {

    private String channel;
    private String group;
    private Consumer<QueryMessageReceived> onReceiveQueryCallback;
    private Consumer<String> onErrorCallback;

    public void raiseOnReceiveMessage(QueryMessageReceived receivedQuery) {
        if (onReceiveQueryCallback != null) {
            onReceiveQueryCallback.accept(receivedQuery);
        }
    }

    public void raiseOnError(String msg) {
        if (onErrorCallback != null) {
            onErrorCallback.accept(msg);
        }
    }

    public void validate() {
        if (channel == null || channel.isEmpty()) {
            throw new IllegalArgumentException("query subscription must have a channel.");
        }
        if (onReceiveQueryCallback == null) {
            throw new IllegalArgumentException("query subscription must have a on_receive_query_callback function.");
        }
    }

    public Subscribe encode(String clientId) {
        Subscribe request = Subscribe.newBuilder()
                .setChannel(this.channel)
                .setGroup(this.group != null ? this.group :"")
                .setClientID(clientId)
                .setSubscribeTypeData(Subscribe.SubscribeType.Queries)
                .setSubscribeTypeDataValue(Subscribe.SubscribeType.Queries_VALUE)
                .build();
        return request;
    }

    @Override
    public String toString() {
        return "QueriesSubscription: channel=" + channel + ", group=" + group;
    }
}

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
public class CommandsSubscription {

    private String channel;
    private String group;
    private Consumer<CommandMessageReceived> onReceiveCommandCallback;
    private Consumer<String> onErrorCallback;

    public void raiseOnReceiveMessage(CommandMessageReceived receivedCommand) {
        if (onReceiveCommandCallback != null) {
            onReceiveCommandCallback.accept(receivedCommand);
        }
    }

    public void raiseOnError(String msg) {
        if (onErrorCallback != null) {
            onErrorCallback.accept(msg);
        }
    }

    public void validate() {
        if (channel == null || channel.isEmpty()) {
            throw new IllegalArgumentException("command subscription must have a channel.");
        }
        if (onReceiveCommandCallback == null) {
            throw new IllegalArgumentException("command subscription must have a on_receive_command_callback function.");
        }
    }

    public kubemq.Kubemq.Subscribe encode(String clientId) {
        Subscribe request =  Subscribe.newBuilder()
                .setChannel(this.channel)
                .setGroup(this.group)
                .setClientID(clientId)
                .setSubscribeTypeData(Subscribe.SubscribeType.Commands)
                .setSubscribeTypeDataValue(Subscribe.SubscribeType.Commands_VALUE)
                .build();
        return request;
    }

    @Override
    public String toString() {
        return "CommandsSubscription: channel=" + channel + ", group=" + group;
    }
}

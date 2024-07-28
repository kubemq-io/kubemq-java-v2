package io.kubemq.sdk.queues;

import lombok.Builder;
import lombok.Data;

import java.util.function.Consumer;

@Data
@Builder
public class UpstreamSender {

    /**
     * Callback function to be invoked when message send result is received.
     */
    private Consumer<UpstreamResponse> onReceiveMessageSendCallback;

    /**
     * Callback function to be invoked when an error occurs.
     */
    private Consumer<String> onErrorCallback;

    /**
     * Invokes the raiseOnReceiveMessage with the given event.
     *
     * @param receivedEvent The received event.
     */
    public void raiseOnReceiveMessage(UpstreamResponse receivedEvent) {
        if (onReceiveMessageSendCallback != null) {
            onReceiveMessageSendCallback.accept(receivedEvent);
        }
    }

    /**
     * Invokes the onErrorCallback with the given error message result.
     *
     * @param msg The error message.
     */
    public void raiseOnError(String msg) {
        if (onErrorCallback != null) {
            onErrorCallback.accept(msg);
        }
    }

}

package io.kubemq.sdk.queues;

import lombok.Builder;
import lombok.Data;

/**
 * Represents information about a queue.
 */
@Data
@Builder
public class QueueDetail {

    private String name;
    private long messages;
    private long bytes;
    private long firstSequence;
    private long lastSequence;
    private long sent;
    private long delivered;
    private long waiting;
    private long subscribers;


    /**
     * Parameterized constructor.
     *
     * @param name          the name of the queue
     * @param messages      the number of messages
     * @param bytes         the total size of the messages in bytes
     * @param firstSequence the sequence number of the first message
     * @param lastSequence  the sequence number of the last message
     * @param sent          the number of sent messages
     * @param delivered     the number of delivered messages
     * @param waiting       the number of messages waiting
     * @param subscribers   the number of subscribers
     */
    public QueueDetail(String name, long messages, long bytes, long firstSequence, long lastSequence, long sent, long delivered, long waiting, long subscribers) {
        this.name = name;
        this.messages = messages;
        this.bytes = bytes;
        this.firstSequence = firstSequence;
        this.lastSequence = lastSequence;
        this.sent = sent;
        this.delivered = delivered;
        this.waiting = waiting;
        this.subscribers = subscribers;
    }

    @Override
    public String toString() {
        return "QueueDetail{" +
                "name='" + name + '\'' +
                ", messages=" + messages +
                ", bytes=" + bytes +
                ", firstSequence=" + firstSequence +
                ", lastSequence=" + lastSequence +
                ", sent=" + sent +
                ", delivered=" + delivered +
                ", waiting=" + waiting +
                ", subscribers=" + subscribers +
                '}';
    }
}

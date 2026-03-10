package io.kubemq.sdk.cq;

import kubemq.Kubemq.Request;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a query message received from a KubeMQ subscription.
 * Contains the query payload and reply channel for sending responses.
 */
/**
 * Represents a query message received from KubeMQ.
 *
 * <p>Contains the query payload and metadata. Query handlers use this to
 * inspect the incoming request and construct a {@link QueryResponseMessage}.</p>
 */
@Data
@NoArgsConstructor
public class QueryMessageReceived {

    private String id;
    private String fromClientId;
    private LocalDateTime timestamp;
    private String channel;
    private String metadata;
    private byte[] body;
    private String replyChannel;
    private Map<String, String> tags = new HashMap<>();

    public static QueryMessageReceived decode(Request queryReceive) {
        QueryMessageReceived message = new QueryMessageReceived();
        message.id = queryReceive.getRequestID();
        message.fromClientId = queryReceive.getClientID();
        message.timestamp = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
        message.channel = queryReceive.getChannel();
        message.metadata = queryReceive.getMetadata();
        message.body = queryReceive.getBody().toByteArray();
        message.replyChannel = queryReceive.getReplyChannel();
        message.tags = queryReceive.getTagsMap();
        return message;
    }

    @Override
    public String toString() {
        return "QueryMessageReceived: id=" + id + ", channel=" + channel + ", metadata=" + metadata + ", body=" + new String(body) + ", fromClientId=" + fromClientId + ", timestamp=" + timestamp + ", replyChannel=" + replyChannel + ", tags=" + tags;
    }
}

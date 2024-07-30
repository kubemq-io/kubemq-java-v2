package io.kubemq.sdk.cq;

import kubemq.Kubemq.Request;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryMessage {

    private String id;
    private String channel;
    private String metadata;
    private byte[] body;
    @Builder.Default
    private Map<String, String> tags = new HashMap<>();
    private int timeoutInSeconds;
    private String cacheKey;
    private int cacheTtlInSeconds;

    public void validate() {
        if (channel == null || channel.isEmpty()) {
            throw new IllegalArgumentException("Query message must have a channel.");
        }

        if ((metadata == null || metadata.isEmpty()) && (body == null || body.length == 0) && (tags == null || tags.isEmpty())) {
            throw new IllegalArgumentException("Query message must have at least one of the following: metadata, body, or tags.");
        }

        if (timeoutInSeconds <= 0) {
            throw new IllegalArgumentException("Query message timeout must be a positive integer.");
        }
    }

    public Request encode(String clientId) {
        Request.Builder pbQueryBuilder = Request.newBuilder();
        tags.put("x-kubemq-client-id", clientId);
        pbQueryBuilder.setRequestID(id != null ? id : UUID.randomUUID().toString())
                .setClientID(clientId)
                .setChannel(channel)
                .setMetadata(metadata != null ? metadata : "")
                .setBody(com.google.protobuf.ByteString.copyFrom(body))
                .setTimeout(timeoutInSeconds * 1000)
                .setRequestTypeData(Request.RequestType.Query)
                .putAllTags(tags)
                .setCacheKey(cacheKey != null ? cacheKey : "")
                .setCacheTTL(cacheTtlInSeconds * 1000);

        return pbQueryBuilder.build();
    }

    @Override
    public String toString() {
        return "QueryMessage: id=" + id + ", channel=" + channel + ", metadata=" + metadata + ", body=" + new String(body) + ", tags=" + tags + ", timeoutInSeconds=" + timeoutInSeconds + ", cacheKey=" + cacheKey + ", cacheTtlInSeconds=" + cacheTtlInSeconds;
    }
}

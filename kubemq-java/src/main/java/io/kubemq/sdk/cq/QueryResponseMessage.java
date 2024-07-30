package io.kubemq.sdk.cq;

import kubemq.Kubemq.Response;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResponseMessage {

    private QueryMessageReceived queryReceived;
    private String clientId;
    private String requestId;
    private boolean isExecuted;
    private LocalDateTime timestamp;
    private String error;
    private String metadata;
    @Builder.Default
    private byte[] body = new byte[0];
    @Builder.Default
    private Map<String, String> tags = new HashMap<>();

    public QueryResponseMessage validate() {
        if (queryReceived == null) {
            throw new IllegalArgumentException("Query response must have a query request.");
        } else if (queryReceived.getReplyChannel().isEmpty()) {
            throw new IllegalArgumentException("Query response must have a reply channel.");
        }
        return this;
    }

    public QueryResponseMessage decode(Response pbResponse) {
        this.clientId = pbResponse.getClientID();
        this.requestId = pbResponse.getRequestID();
        this.isExecuted = pbResponse.getExecuted();
        this.error = pbResponse.getError();
        this.timestamp = LocalDateTime.ofInstant(Instant.ofEpochSecond(pbResponse.getTimestamp() / 1_000_000_000), ZoneId.systemDefault());
        this.metadata = pbResponse.getMetadata();
        this.body = pbResponse.getBody().toByteArray();
        this.tags = pbResponse.getTagsMap();
        return this;
    }

    public Response encode(String clientId) {
        Response.Builder pbResponseBuilder = Response.newBuilder();
        pbResponseBuilder.setClientID(clientId);
        pbResponseBuilder.setRequestID(this.queryReceived.getId());
        pbResponseBuilder.setReplyChannel(this.queryReceived.getReplyChannel());
        pbResponseBuilder.setExecuted(this.isExecuted);
        pbResponseBuilder.setError(this.error !=null ? this.error:"");
        pbResponseBuilder.setTimestamp(this.timestamp != null ? (this.timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() * 1_000_000):Instant.now().toEpochMilli());
        pbResponseBuilder.setMetadata(this.queryReceived.getMetadata());
        pbResponseBuilder.setBody(com.google.protobuf.ByteString.copyFrom(this.body));
        pbResponseBuilder.putAllTags(this.tags);
        return pbResponseBuilder.build();
    }

    @Override
    public String toString() {
        return "QueryResponseMessage: clientId=" + clientId + ", requestId=" + requestId + ", isExecuted=" + isExecuted + ", error=" + error + ", timestamp=" + timestamp;
    }
}

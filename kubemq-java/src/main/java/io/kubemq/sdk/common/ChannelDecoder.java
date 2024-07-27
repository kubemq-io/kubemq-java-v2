package io.kubemq.sdk.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubemq.sdk.cq.CQChannel;
import io.kubemq.sdk.pubsub.PubSubChannel;
import io.kubemq.sdk.queues.QueuesChannel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * ChannelDecoder provides utility methods for decoding channel data from byte arrays.
 * This class uses Jackson's ObjectMapper to convert JSON byte arrays into lists of channel objects.
 */
public class ChannelDecoder {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Decodes a byte array into a list of PubSubChannel objects.
     *
     * @param dataBytes The byte array containing JSON data representing a list of PubSubChannel objects.
     * @return A list of PubSubChannel objects.
     * @throws IOException If there is an error during the decoding process.
     */
    public static List<PubSubChannel> decodePubSubChannelList(byte[] dataBytes) throws IOException {
        String dataStr = new String(dataBytes, StandardCharsets.UTF_8);
        return objectMapper.readValue(dataStr, new TypeReference<List<PubSubChannel>>() {});
    }

    /**
     * Decodes a byte array into a list of QueuesChannel objects.
     *
     * @param dataBytes The byte array containing JSON data representing a list of QueuesChannel objects.
     * @return A list of QueuesChannel objects.
     * @throws IOException If there is an error during the decoding process.
     */
    public static List<QueuesChannel> decodeQueuesChannelList(byte[] dataBytes) throws IOException {
        String dataStr = new String(dataBytes, StandardCharsets.UTF_8);
        return objectMapper.readValue(dataStr, new TypeReference<List<QueuesChannel>>() {});
    }

    /**
     * Decodes a byte array into a list of CQChannel objects.
     *
     * @param dataBytes The byte array containing JSON data representing a list of CQChannel objects.
     * @return A list of CQChannel objects.
     * @throws IOException If there is an error during the decoding process.
     */
    public static List<CQChannel> decodeCqChannelList(byte[] dataBytes) throws IOException {
        String dataStr = new String(dataBytes, StandardCharsets.UTF_8);
        return objectMapper.readValue(dataStr, new TypeReference<List<CQChannel>>() {});
    }
}

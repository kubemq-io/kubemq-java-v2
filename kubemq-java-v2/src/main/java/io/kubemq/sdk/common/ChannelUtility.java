package io.kubemq.sdk.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubemq.sdk.pubsub.PubSubChannel;

import java.io.IOException;
import java.util.List;

/**
 * ChannelUtility provides utility methods for decoding channel data from byte arrays.
 * This class uses Jackson's ObjectMapper to convert JSON byte arrays into lists of channel objects.
 */
public class ChannelUtility {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Decodes a byte array into a list of PubSubChannel objects.
     *
     * @param dataBytes The byte array containing JSON data representing a list of PubSubChannel objects.
     * @return A list of PubSubChannel objects.
     * @throws IOException If there is an error during the decoding process.
     */
    public static List<PubSubChannel> decodePubSubChannelList(byte[] dataBytes) throws IOException {
        String dataStr = new String(dataBytes, "UTF-8");
        List<PubSubChannel> channels = objectMapper.readValue(dataStr, new TypeReference<List<PubSubChannel>>() {});
        return channels;
    }

    /**
     * Decodes a byte array into a list of QueuesChannel objects.
     *
     * @param dataBytes The byte array containing JSON data representing a list of QueuesChannel objects.
     * @return A list of QueuesChannel objects.
     * @throws IOException If there is an error during the decoding process.
     */
    public static List<QueuesChannel> decodeQueuesChannelList(byte[] dataBytes) throws IOException {
        String dataStr = new String(dataBytes, "UTF-8");
        List<QueuesChannel> channels = objectMapper.readValue(dataStr, new TypeReference<List<QueuesChannel>>() {});
        return channels;
    }

    /**
     * Decodes a byte array into a list of CQChannel objects.
     *
     * @param dataBytes The byte array containing JSON data representing a list of CQChannel objects.
     * @return A list of CQChannel objects.
     * @throws IOException If there is an error during the decoding process.
     */
    public static List<CQChannel> decodeCqChannelList(byte[] dataBytes) throws IOException {
        String dataStr = new String(dataBytes, "UTF-8");
        List<CQChannel> channels = objectMapper.readValue(dataStr, new TypeReference<List<CQChannel>>() {});
        return channels;
    }
}

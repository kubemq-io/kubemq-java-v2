package io.kubemq.sdk.unit.common;

import io.kubemq.sdk.common.ChannelDecoder;
import io.kubemq.sdk.cq.CQChannel;
import io.kubemq.sdk.cq.CQStats;
import io.kubemq.sdk.pubsub.PubSubChannel;
import io.kubemq.sdk.pubsub.PubSubStats;
import io.kubemq.sdk.queues.QueuesChannel;
import io.kubemq.sdk.queues.QueuesStats;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ChannelDecoder JSON parsing.
 */
class ChannelDecoderTest {

    @Nested
    class QueuesChannelDecodingTests {

        @Test
        void decodeQueuesChannelList_withValidJson_parsesCorrectly() throws IOException {
            String json = "[{\"name\":\"channel1\",\"type\":\"type1\",\"lastActivity\":1622014799,\"isActive\":true,\"incoming\":{\"messages\":100,\"volume\":200,\"waiting\":10,\"expired\":5,\"delayed\":2},\"outgoing\":{\"messages\":150,\"volume\":300,\"waiting\":8,\"expired\":3,\"delayed\":1}}]";
            byte[] dataBytes = json.getBytes(StandardCharsets.UTF_8);

            List<QueuesChannel> channels = ChannelDecoder.decodeQueuesChannelList(dataBytes);

            assertEquals(1, channels.size());
            assertEquals("channel1", channels.get(0).getName());
            assertEquals("type1", channels.get(0).getType());
            assertEquals(1622014799L, channels.get(0).getLastActivity());
            assertTrue(channels.get(0).getIsActive());
            assertEquals(100, channels.get(0).getIncoming().getMessages());
            assertEquals(150, channels.get(0).getOutgoing().getMessages());
        }

        @Test
        void decodeQueuesChannelList_withEmptyArray_returnsEmptyList() throws IOException {
            String json = "[]";
            byte[] dataBytes = json.getBytes(StandardCharsets.UTF_8);

            List<QueuesChannel> channels = ChannelDecoder.decodeQueuesChannelList(dataBytes);

            assertTrue(channels.isEmpty());
        }

        @Test
        void decodeQueuesChannelList_withMultipleChannels_parsesAll() throws IOException {
            String json = "[{\"name\":\"channel1\",\"type\":\"type1\",\"lastActivity\":1622014799,\"isActive\":true,\"incoming\":{\"messages\":100,\"volume\":200},\"outgoing\":{\"messages\":150,\"volume\":300}},{\"name\":\"channel2\",\"type\":\"type2\",\"lastActivity\":1622014800,\"isActive\":false,\"incoming\":{\"messages\":50,\"volume\":100},\"outgoing\":{\"messages\":75,\"volume\":150}}]";
            byte[] dataBytes = json.getBytes(StandardCharsets.UTF_8);

            List<QueuesChannel> channels = ChannelDecoder.decodeQueuesChannelList(dataBytes);

            assertEquals(2, channels.size());
            assertEquals("channel1", channels.get(0).getName());
            assertEquals("channel2", channels.get(1).getName());
            assertFalse(channels.get(1).getIsActive());
        }

        @Test
        void decodeQueuesChannelList_withMalformedJson_throwsIOException() {
            String malformedJson = "[{\"name\":\"channel1\" invalid json}]";
            byte[] dataBytes = malformedJson.getBytes(StandardCharsets.UTF_8);

            assertThrows(IOException.class, () -> {
                ChannelDecoder.decodeQueuesChannelList(dataBytes);
            });
        }

        @Test
        void decodeQueuesChannelList_withUnknownFields_throwsException() {
            // QueuesChannel class doesn't have @JsonIgnoreProperties(ignoreUnknown = true)
            // so unknown fields will cause an exception
            String json = "[{\"name\":\"channel1\",\"unknownField\":\"value\",\"type\":\"type1\",\"lastActivity\":1622014799,\"isActive\":true}]";
            byte[] dataBytes = json.getBytes(StandardCharsets.UTF_8);

            assertThrows(IOException.class, () -> {
                ChannelDecoder.decodeQueuesChannelList(dataBytes);
            });
        }
    }

    @Nested
    class PubSubChannelDecodingTests {

        @Test
        void decodePubSubChannelList_withValidJson_parsesCorrectly() throws IOException {
            String json = "[{\"name\":\"channel1\",\"type\":\"type1\",\"lastActivity\":1622014799,\"isActive\":true,\"incoming\":{\"messages\":100,\"volume\":200},\"outgoing\":{\"messages\":150,\"volume\":300}}]";
            byte[] dataBytes = json.getBytes(StandardCharsets.UTF_8);

            List<PubSubChannel> channels = ChannelDecoder.decodePubSubChannelList(dataBytes);

            assertEquals(1, channels.size());
            assertEquals("channel1", channels.get(0).getName());
            assertEquals("type1", channels.get(0).getType());
            assertEquals(1622014799L, channels.get(0).getLastActivity());
            assertTrue(channels.get(0).getIsActive());
            assertEquals(100, channels.get(0).getIncoming().getMessages());
            assertEquals(150, channels.get(0).getOutgoing().getMessages());
        }

        @Test
        void decodePubSubChannelList_withEmptyArray_returnsEmptyList() throws IOException {
            String json = "[]";
            byte[] dataBytes = json.getBytes(StandardCharsets.UTF_8);

            List<PubSubChannel> channels = ChannelDecoder.decodePubSubChannelList(dataBytes);

            assertTrue(channels.isEmpty());
        }

        @Test
        void decodePubSubChannelList_withMalformedJson_throwsIOException() {
            String malformedJson = "not valid json";
            byte[] dataBytes = malformedJson.getBytes(StandardCharsets.UTF_8);

            assertThrows(IOException.class, () -> {
                ChannelDecoder.decodePubSubChannelList(dataBytes);
            });
        }

        @Test
        void decodePubSubChannelList_withNullStats_handlesGracefully() throws IOException {
            String json = "[{\"name\":\"channel1\",\"type\":\"type1\",\"lastActivity\":1622014799,\"isActive\":true}]";
            byte[] dataBytes = json.getBytes(StandardCharsets.UTF_8);

            List<PubSubChannel> channels = ChannelDecoder.decodePubSubChannelList(dataBytes);

            assertEquals(1, channels.size());
            assertEquals("channel1", channels.get(0).getName());
            assertNull(channels.get(0).getIncoming());
            assertNull(channels.get(0).getOutgoing());
        }
    }

    @Nested
    class CQChannelDecodingTests {

        @Test
        void decodeCqChannelList_withValidJson_parsesCorrectly() throws IOException {
            String json = "[{\"name\":\"channel1\",\"type\":\"type1\",\"lastActivity\":1622014799,\"isActive\":true,\"incoming\":{\"messages\":100,\"volume\":200},\"outgoing\":{\"messages\":150,\"volume\":300}}]";
            byte[] dataBytes = json.getBytes(StandardCharsets.UTF_8);

            List<CQChannel> channels = ChannelDecoder.decodeCqChannelList(dataBytes);

            assertEquals(1, channels.size());
            assertEquals("channel1", channels.get(0).getName());
            assertEquals("type1", channels.get(0).getType());
            assertEquals(1622014799L, channels.get(0).getLastActivity());
            assertTrue(channels.get(0).getIsActive());
            assertEquals(100, channels.get(0).getIncoming().getMessages());
            assertEquals(150, channels.get(0).getOutgoing().getMessages());
        }

        @Test
        void decodeCqChannelList_withEmptyArray_returnsEmptyList() throws IOException {
            String json = "[]";
            byte[] dataBytes = json.getBytes(StandardCharsets.UTF_8);

            List<CQChannel> channels = ChannelDecoder.decodeCqChannelList(dataBytes);

            assertTrue(channels.isEmpty());
        }

        @Test
        void decodeCqChannelList_withMalformedJson_throwsIOException() {
            String malformedJson = "{not an array}";
            byte[] dataBytes = malformedJson.getBytes(StandardCharsets.UTF_8);

            assertThrows(IOException.class, () -> {
                ChannelDecoder.decodeCqChannelList(dataBytes);
            });
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void decode_withEmptyBytes_throwsIOException() {
            byte[] emptyBytes = new byte[0];

            assertThrows(IOException.class, () -> {
                ChannelDecoder.decodeQueuesChannelList(emptyBytes);
            });
        }

        @Test
        void decode_withWhitespaceOnlyJson_throwsIOException() {
            byte[] whitespaceBytes = "   ".getBytes(StandardCharsets.UTF_8);

            assertThrows(IOException.class, () -> {
                ChannelDecoder.decodeQueuesChannelList(whitespaceBytes);
            });
        }

        @Test
        void decode_withSpecialCharactersInName_parsesCorrectly() throws IOException {
            String json = "[{\"name\":\"channel-with-special_chars.123\",\"type\":\"type1\",\"lastActivity\":1622014799,\"isActive\":true}]";
            byte[] dataBytes = json.getBytes(StandardCharsets.UTF_8);

            List<QueuesChannel> channels = ChannelDecoder.decodeQueuesChannelList(dataBytes);

            assertEquals(1, channels.size());
            assertEquals("channel-with-special_chars.123", channels.get(0).getName());
        }

        @Test
        void decode_withUnicodeCharactersInName_parsesCorrectly() throws IOException {
            String json = "[{\"name\":\"channel-\u00e9\u00e8\u00ea\",\"type\":\"type1\",\"lastActivity\":1622014799,\"isActive\":true}]";
            byte[] dataBytes = json.getBytes(StandardCharsets.UTF_8);

            List<QueuesChannel> channels = ChannelDecoder.decodeQueuesChannelList(dataBytes);

            assertEquals(1, channels.size());
            assertTrue(channels.get(0).getName().contains("\u00e9"));
        }

        @Test
        void decode_withLargeNumbers_parsesCorrectly() throws IOException {
            String json = "[{\"name\":\"channel1\",\"type\":\"type1\",\"lastActivity\":9223372036854775807,\"isActive\":true,\"incoming\":{\"messages\":2147483647,\"volume\":2147483647}}]";
            byte[] dataBytes = json.getBytes(StandardCharsets.UTF_8);

            List<QueuesChannel> channels = ChannelDecoder.decodeQueuesChannelList(dataBytes);

            assertEquals(1, channels.size());
            assertEquals(Long.MAX_VALUE, channels.get(0).getLastActivity());
            assertEquals(Integer.MAX_VALUE, channels.get(0).getIncoming().getMessages());
        }

        @Test
        void decode_withZeroValues_parsesCorrectly() throws IOException {
            String json = "[{\"name\":\"channel1\",\"type\":\"type1\",\"lastActivity\":0,\"isActive\":false,\"incoming\":{\"messages\":0,\"volume\":0},\"outgoing\":{\"messages\":0,\"volume\":0}}]";
            byte[] dataBytes = json.getBytes(StandardCharsets.UTF_8);

            List<QueuesChannel> channels = ChannelDecoder.decodeQueuesChannelList(dataBytes);

            assertEquals(1, channels.size());
            assertEquals(0, channels.get(0).getLastActivity());
            assertFalse(channels.get(0).getIsActive());
            assertEquals(0, channels.get(0).getIncoming().getMessages());
        }
    }
}

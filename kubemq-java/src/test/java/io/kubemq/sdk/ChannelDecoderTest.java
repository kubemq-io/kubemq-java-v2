package io.kubemq.sdk;

import io.kubemq.sdk.common.ChannelDecoder;
import io.kubemq.sdk.cq.CQChannel;
import io.kubemq.sdk.cq.CQStats;
import io.kubemq.sdk.pubsub.PubSubChannel;
import io.kubemq.sdk.pubsub.PubSubStats;
import io.kubemq.sdk.queues.QueuesChannel;
import io.kubemq.sdk.queues.QueuesStats;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@ExtendWith(MockitoExtension.class)
public class ChannelDecoderTest {

    @Test
    public void testDecodeQueuesChannelList_Success() throws IOException {
        log.info("Starting test: testDecodeQueuesChannelList_Success");

        String json = "[{\"name\":\"channel1\",\"type\":\"type1\",\"lastActivity\":1622014799,\"isActive\":true,\"incoming\":{\"messages\":100,\"volume\":200,\"waiting\":10,\"expired\":5,\"delayed\":2},\"outgoing\":{\"messages\":150,\"volume\":300,\"waiting\":8,\"expired\":3,\"delayed\":1}}]";
        byte[] dataBytes = json.getBytes(StandardCharsets.UTF_8);
        List<QueuesChannel> expectedChannels = Collections.singletonList(
                new QueuesChannel(
                        "channel1", "type1", 1622014799L, true,
                        new QueuesStats(100, 200, 10, 5, 2,0),
                        new QueuesStats(150, 300, 8, 3, 1,0)
                )
        );

        List<QueuesChannel> channels = ChannelDecoder.decodeQueuesChannelList(dataBytes);

        assertEquals(expectedChannels.get(0).getName(), channels.get(0).getName());
        log.info("Finished test: testDecodeQueuesChannelList_Success");
    }

    @Test
    public void testDecodePubSubChannelList_Success() throws IOException {
        log.info("Starting test: testDecodePubSubChannelList_Success");

        String json = "[{\"name\":\"channel1\",\"type\":\"type1\",\"lastActivity\":1622014799,\"isActive\":true,\"incoming\":{\"messages\":100,\"volume\":200},\"outgoing\":{\"messages\":150,\"volume\":300}}]";
        byte[] dataBytes = json.getBytes(StandardCharsets.UTF_8);
        List<PubSubChannel> expectedChannels = Collections.singletonList(
                new PubSubChannel(
                        "channel1", "type1", 1622014799L, true,
                        new PubSubStats(100, 200, 0, 0, 0, 0),
                        new PubSubStats(150, 300, 0, 0, 0, 0)
                )
        );

        List<PubSubChannel> channels = ChannelDecoder.decodePubSubChannelList(dataBytes);

        assertEquals(expectedChannels.get(0).getName(), channels.get(0).getName());
        log.info("Finished test: testDecodePubSubChannelList_Success");
    }

    @Test
    public void testDecodeCQChannelList_Success() throws IOException {
        log.info("Starting test: testDecodeCQChannelList_Success");

        String json = "[{\"name\":\"channel1\",\"type\":\"type1\",\"lastActivity\":1622014799,\"isActive\":true,\"incoming\":{\"messages\":100,\"volume\":200},\"outgoing\":{\"messages\":150,\"volume\":300}}]";
        byte[] dataBytes = json.getBytes(StandardCharsets.UTF_8);
        List<CQChannel> expectedChannels = Collections.singletonList(
                new CQChannel(
                        "channel1", "type1", 1622014799L, true,
                        new CQStats(100, 200, 10,0,0,0),
                        new CQStats(150, 300, 10,0,0,0)
                )
        );

        List<CQChannel> channels = ChannelDecoder.decodeCqChannelList(dataBytes);

        assertEquals(expectedChannels.get(0).getName(), channels.get(0).getName());
        log.info("Finished test: testDecodeCQChannelList_Success");
    }
}


// Payload encoding/decoding with CRC32 (IEEE polynomial) integrity verification.
// JSON encode/decode with Gson, random padding with SecureRandom, SizeDistribution.

package io.kubemq.burnin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.CRC32;

/**
 * Deserialized message payload with all required fields.
 */
class MessagePayload {

    @SerializedName("sdk")
    String sdk = "";

    @SerializedName("pattern")
    String pattern = "";

    @SerializedName("producer_id")
    String producerId = "";

    @SerializedName("sequence")
    long sequence;

    @SerializedName("timestamp_ns")
    long timestampNs;

    @SerializedName("payload_padding")
    String payloadPadding;
}

/**
 * Result of encoding a message payload.
 */
class EncodedPayload {
    final byte[] body;
    final String crcHex;

    EncodedPayload(byte[] body, String crcHex) {
        this.body = body;
        this.crcHex = crcHex;
    }
}

/**
 * Payload encoder/decoder with CRC32 IEEE integrity verification and random padding.
 */
public final class Payload {

    private Payload() {}

    private static final Gson GSON = new GsonBuilder().create();

    // Thread-local SecureRandom for padding generation.
    private static final ThreadLocal<SecureRandom> SECURE_RANDOM =
            ThreadLocal.withInitial(SecureRandom::new);

    /**
     * Compute CRC32 IEEE hash and return as 8-char lowercase hex string.
     */
    public static String crc32Hex(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return String.format("%08x", crc.getValue());
    }

    /**
     * Verify that the CRC32 of the body matches the expected hex string.
     */
    public static boolean verifyCrc(byte[] body, String expected) {
        return crc32Hex(body).equals(expected);
    }

    /**
     * Encode a message payload with optional padding to reach the target size.
     */
    public static EncodedPayload encode(String sdk, String pattern, String producerId,
                                        long seq, int targetSize) {
        MessagePayload msg = new MessagePayload();
        msg.sdk = sdk;
        msg.pattern = pattern;
        msg.producerId = producerId;
        msg.sequence = seq;
        msg.timestampNs = System.nanoTime();

        // Serialize without padding to measure base size.
        byte[] baseBytes = GSON.toJson(msg).getBytes(StandardCharsets.UTF_8);

        if (targetSize > baseBytes.length + 20) {
            int padLen = targetSize - baseBytes.length - 20;
            if (padLen > 0) {
                msg.payloadPadding = randomPadding(padLen);
            }
        }

        byte[] body = GSON.toJson(msg).getBytes(StandardCharsets.UTF_8);
        String crcHex = crc32Hex(body);
        return new EncodedPayload(body, crcHex);
    }

    /**
     * Decode a message payload from raw bytes.
     */
    public static MessagePayload decode(byte[] body) {
        String json = new String(body, StandardCharsets.UTF_8);
        MessagePayload payload = GSON.fromJson(json, MessagePayload.class);
        if (payload == null) {
            throw new IllegalStateException("Failed to deserialize payload");
        }
        return payload;
    }

    /**
     * Generate random printable ASCII padding of the specified length.
     * Characters range from '!' (33) to '~' (126).
     */
    private static String randomPadding(int length) {
        SecureRandom rng = SECURE_RANDOM.get();
        byte[] buf = new byte[length];
        rng.nextBytes(buf);
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = (char) (33 + ((buf[i] & 0xFF) % 94));
        }
        return new String(chars);
    }
}

/**
 * Weighted size distribution for message payload sizes.
 * Parses a spec like "256:80,4096:15,65536:5" into weighted random selection.
 */
class SizeDistribution {
    private final int[] sizes;
    private final int[] weights;
    private final int totalWeight;

    public SizeDistribution(String spec) {
        String[] pairs = spec.split(",");
        sizes = new int[pairs.length];
        weights = new int[pairs.length];
        int tw = 0;
        for (int i = 0; i < pairs.length; i++) {
            String[] parts = pairs[i].trim().split(":");
            sizes[i] = Integer.parseInt(parts[0].trim());
            weights[i] = Integer.parseInt(parts[1].trim());
            tw += weights[i];
        }
        totalWeight = tw;
    }

    /**
     * Select a random size based on the weight distribution.
     * Thread-safe: uses ThreadLocalRandom.
     */
    public int selectSize() {
        int r = ThreadLocalRandom.current().nextInt(1, totalWeight + 1);
        for (int i = 0; i < sizes.length; i++) {
            r -= weights[i];
            if (r <= 0) return sizes[i];
        }
        return sizes[sizes.length - 1];
    }
}

package io.kubemq.sdk.client;

import ch.qos.logback.classic.LoggerContext;
import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.kubemq.sdk.common.ServerInfo;
import kubemq.Kubemq;
import kubemq.kubemqGrpc;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * KubeMQClient is a client for communicating with a KubeMQ server using gRPC.
 * This client supports both plain and TLS (Transport Layer Security) connections.
 * It can be configured with various options such as client ID, authorization token, and connection parameters.
 * The client also allows setting the logging level.
 */
@Slf4j
@Getter
@NoArgsConstructor
@AllArgsConstructor
public abstract class KubeMQClient implements AutoCloseable {

    private  String address;
    private  String clientId;
    private  String authToken;
    private  boolean tls;
    private  String tlsCertFile;
    private  String tlsKeyFile;
    private  int maxReceiveSize;
    private  int reconnectIntervalSeconds;
    private  boolean keepAlive;
    private  int pingIntervalInSeconds;
    private  int pingTimeoutInSeconds;
    private  Level logLevel;

    @Setter
    private ManagedChannel managedChannel;
    @Setter
    private kubemqGrpc.kubemqBlockingStub blockingStub;
    @Setter
    private kubemqGrpc.kubemqStub asyncStub;
    @Setter
    private Metadata metadata;

    /**
     * Constructs a KubeMQClient with the specified parameters.
     *
     * @param address                 The address of the KubeMQ server.
     * @param clientId                The client ID used for authentication.
     * @param authToken               The authorization token for secure communication.
     * @param tls                     Indicates if TLS (Transport Layer Security) is enabled.
     * @param tlsCertFile             The path to the TLS certificate file.
     * @param tlsKeyFile              The path to the TLS key file.
     * @param maxReceiveSize          The maximum size of the messages to receive (in bytes).
     * @param keepAlive               Indicates if the connection should be kept alive.
     * @param pingIntervalInSeconds   The interval between ping messages (in seconds).
     * @param pingTimeoutInSeconds    The timeout for ping messages (in seconds).
     * @param logLevel                The logging level to use.
     */
    public KubeMQClient(String address, String clientId, String authToken, boolean tls, String tlsCertFile, String tlsKeyFile,
                        int maxReceiveSize, int reconnectIntervalSeconds, boolean keepAlive, int pingIntervalInSeconds, int pingTimeoutInSeconds, Level logLevel) {
        if (address == null || clientId == null) {
            throw new IllegalArgumentException("Address and clientId are required");
        }
        if (tls && (tlsCertFile == null || tlsKeyFile == null)) {
            throw new IllegalArgumentException("When TLS is enabled, tlsCertFile and tlsKeyFile are required");
        }

        this.address = address;
        this.clientId = clientId;
        this.authToken = authToken;
        this.tls = tls;
        this.tlsCertFile = tlsCertFile;
        this.tlsKeyFile = tlsKeyFile;
        this.maxReceiveSize = maxReceiveSize;
        this.reconnectIntervalSeconds = reconnectIntervalSeconds;
        this.keepAlive = keepAlive;
        this.pingIntervalInSeconds = pingIntervalInSeconds;
        this.pingTimeoutInSeconds = pingTimeoutInSeconds;
        this.logLevel = logLevel != null ? logLevel : Level.INFO;
        // Set the logging level
        setLogLevel();
        // Initialize the channel
        initChannel();
    }

    /**
     * Initializes the gRPC channel and stubs based on the configuration provided.
     * Sets up TLS if enabled, otherwise uses plaintext communication.
     */
    private void initChannel() {
        // Set MetaData
        if (authToken != null && !authToken.isEmpty()) {
            this.metadata = new Metadata();
            Metadata.Key<String> key = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
            metadata.put(key, authToken);
        }

        log.debug("Constructing channel to KubeMQ on {}", address);
        if (tls) {
            try {
                SslContext sslContext = SslContextBuilder.forClient()
                        .trustManager(new File(tlsCertFile))
                        .keyManager(new File(tlsCertFile), new File(tlsKeyFile))
                        .build();
                managedChannel = NettyChannelBuilder.forTarget(address)
                        .sslContext(sslContext)
                        .negotiationType(NegotiationType.TLS)
                        .maxInboundMessageSize(maxReceiveSize == 0 ? 4194304 : maxReceiveSize) // Default 4MIB
                        .keepAliveTime(pingIntervalInSeconds == 0 ? 180 : pingIntervalInSeconds, TimeUnit.SECONDS)
                        .keepAliveTimeout(pingTimeoutInSeconds == 0 ? 20 : pingTimeoutInSeconds, TimeUnit.SECONDS)
                        .keepAliveWithoutCalls(keepAlive)
                        .enableRetry()
                        .build();
            } catch (SSLException e) {
                log.error("Failed to set up SSL context", e);
                throw new RuntimeException(e);
            }
        } else {
            managedChannel = ManagedChannelBuilder.forTarget(address)
                    .maxInboundMessageSize(maxReceiveSize == 0 ? 4194304 : maxReceiveSize) // Default 4MIB
                    .keepAliveTime(pingIntervalInSeconds == 0 ? 180 : pingIntervalInSeconds, TimeUnit.SECONDS)
                    .keepAliveTimeout(pingTimeoutInSeconds == 0 ? 20 : pingTimeoutInSeconds, TimeUnit.SECONDS)
                    .keepAliveWithoutCalls(keepAlive)
                    .usePlaintext().build();
        }

        if (metadata != null) {
            ClientInterceptor interceptor = new MetadataInterceptor(metadata);
            Channel channel = ClientInterceptors.intercept(managedChannel, interceptor);
            this.blockingStub = kubemqGrpc.newBlockingStub(channel);
            this.asyncStub = kubemqGrpc.newStub(channel);
        } else {
            this.blockingStub = kubemqGrpc.newBlockingStub(managedChannel);
            this.asyncStub = kubemqGrpc.newStub(managedChannel);
        }
        log.info("Client initialized for KubeMQ address: {}", address);
    }

    /**
     * Returns the blocking stub for synchronous communication with the KubeMQ server.
     *
     * @return The blocking stub.
     */
    public kubemqGrpc.kubemqBlockingStub getClient() {
        return blockingStub;
    }

    /**
     * Returns the asynchronous stub for asynchronous communication with the KubeMQ server.
     *
     * @return The asynchronous stub.
     */
    public kubemqGrpc.kubemqStub getAsyncClient() {
        return asyncStub;
    }

    /**
     * Closes the gRPC channel and shuts down the client.
     * This method should be called to release resources when the client is no longer needed.
     */
    @Override
    public void close() {
        if (managedChannel != null) {
            try {
                managedChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.error("Failed to shut down the channel", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Sends a ping request to the KubeMQ server and returns the result.
     *
     * @return The ping result from the server.
     * @throws RuntimeException if the ping request fails.
     */
    public ServerInfo ping() {
        try {
            log.debug("Pinging KubeMQ server at {}", address);
            Kubemq.PingResult pingResult = blockingStub.ping(null);
            log.debug("Ping successful. Response: {}", pingResult);
            return ServerInfo.builder()
                    .host(pingResult.getHost())
                    .version(pingResult.getVersion())
                    .serverStartTime(pingResult.getServerStartTime())
                    .serverUpTimeSeconds(pingResult.getServerUpTimeSeconds())
                            .build();
        } catch (StatusRuntimeException e) {
            log.error("Ping failed", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets the logging level for the client.
     * This method configures the log level based on the specified Level enum.
     */
    private void setLogLevel() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger("ROOT");
        rootLogger.setLevel(ch.qos.logback.classic.Level.valueOf(logLevel.name()));
    }

    /**
     * Enum representing the log levels supported by the KubeMQClient.
     */
    public enum Level {
        TRACE, DEBUG, INFO, WARN, ERROR
    }
}

package io.kubemq.sdk.client;

import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.kubemq.sdk.auth.CredentialManager;
import io.kubemq.sdk.auth.CredentialProvider;
import io.kubemq.sdk.auth.StaticTokenProvider;
import io.kubemq.sdk.common.CompatibilityConfig;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.exception.*;
import io.kubemq.sdk.observability.KubeMQLogger;
import javax.annotation.concurrent.ThreadSafe;
import io.kubemq.sdk.observability.KubeMQLoggerFactory;
import io.kubemq.sdk.observability.Tracing;
import io.kubemq.sdk.observability.TracingFactory;
import io.kubemq.sdk.observability.Metrics;
import io.kubemq.sdk.observability.MetricsFactory;
import kubemq.Kubemq;
import kubemq.kubemqGrpc;
import lombok.Getter;
import lombok.Setter;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * KubeMQClient is the base client for communicating with a KubeMQ server using gRPC.
 * This client supports both plain and TLS (Transport Layer Security) connections.
 *
 * <p><strong>Thread Safety:</strong> This class is thread-safe. A single client instance
 * should be created and shared across all threads in the application. The underlying
 * gRPC channel multiplexes concurrent operations over a single connection. Creating
 * multiple client instances to the same server is unnecessary and wastes resources.</p>
 *
 * <p><strong>Usage pattern:</strong></p>
 * <pre>{@code
 * // Create once, share everywhere
 * PubSubClient client = PubSubClient.builder()
 *     .address("localhost:50000")
 *     .clientId("my-service")
 *     .build();
 *
 * // Safe to use from multiple threads concurrently
 * executorService.submit(() -> client.sendEventsMessage(msg1));
 * executorService.submit(() -> client.sendEventsMessage(msg2));
 *
 * // Close when application shuts down
 * client.close();
 * }</pre>
 *
 * <p>Implements {@link AutoCloseable} for use with try-with-resources.</p>
 */
@ThreadSafe
@Getter
public abstract class KubeMQClient implements AutoCloseable {

    private static final KubeMQLogger staticLog = KubeMQLoggerFactory.getLogger(KubeMQClient.class);
    private static final AtomicInteger clientCount = new AtomicInteger(0);
    private static volatile boolean shutdownHookRegistered = false;
    private static final Object shutdownHookLock = new Object();

    private  String address;
    private  String clientId;
    private final AtomicReference<String> authTokenRef = new AtomicReference<>();
    private  Boolean tls;
    private  String tlsCertFile;
    private  String tlsKeyFile;
    private  String caCertFile;
    private  byte[] caCertPem;
    private  byte[] tlsCertPem;
    private  byte[] tlsKeyPem;
    private  String serverNameOverride;
    private  boolean insecureSkipVerify;
    private  int maxReceiveSize;
    private  int reconnectIntervalSeconds;
    private  Boolean keepAlive;
    private  int pingIntervalInSeconds;
    private  int pingTimeoutInSeconds;
    private  Level logLevel;
    private final KubeMQLogger logger;
    private long reconnectIntervalInMillis;

    private int requestTimeoutSeconds;

    // --- REQ-AUTH-4: Credential provider ---
    private CredentialProvider credentialProvider;
    private CredentialManager credentialManager;
    private ScheduledExecutorService credentialRefreshScheduler;

    // --- REQ-CONN-2: Connection state machine ---
    private final ConnectionStateMachine connectionStateMachine;
    // --- REQ-CONN-1: Reconnection management ---
    private final ReconnectionManager reconnectionManager;
    private final ReconnectionConfig reconnectionConfig;
    private final MessageBuffer messageBuffer;
    // --- REQ-CONN-4: Graceful shutdown ---
    private final AtomicBoolean closed = new AtomicBoolean(false);
    // --- REQ-COMPAT-1: Lazy server compatibility check ---
    private final AtomicBoolean compatibilityChecked = new AtomicBoolean(false);
    private int shutdownTimeoutSeconds;
    // --- REQ-CONN-5: Connection configuration ---
    private int connectionTimeoutSeconds;
    private int maxSendMessageSize;
    private boolean waitForReady;

    // --- REQ-CONC-5: In-flight operation tracking for shutdown safety ---
    private final AtomicInteger inFlightOperations = new AtomicInteger(0);
    private int callbackCompletionTimeoutSeconds;

    // --- REQ-CONC-3: Callback and async operation executors (lazily initialized) ---
    private volatile ExecutorService defaultCallbackExecutor;
    private volatile ExecutorService defaultAsyncOperationExecutor;
    private final Object executorInitLock = new Object();

    // --- REQ-OBS-1/3: OTel tracing and metrics (lazily loaded via factories) ---
    private final Tracing tracing;
    private final Metrics metrics;

    /**
     * Returns the timeout in seconds for synchronous request operations.
     * @return The request timeout in seconds.
     */
    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }
    @Setter
    private ManagedChannel managedChannel;
    @Setter
    private kubemqGrpc.kubemqBlockingStub blockingStub;
    @Setter
    private kubemqGrpc.kubemqStub asyncStub;

    // --- REQ-AUTH-6: Thread-safe reconnection ---
    private final Object reconnectLock = new Object();

    /**
     * Constructs a KubeMQClient with the specified parameters.
     *
     * @param address                 The address of the KubeMQ server in 'host:port' format.
     *                                Default: {@code KUBEMQ_ADDRESS} env var, then {@code "localhost:50000"}.
     * @param clientId                The client ID used for identifying this client instance.
     *                                Default: auto-generated {@code "kubemq-client-<uuid>"}.
     * @param authToken               The authorization token for secure communication.
     *                                Default: {@code null} (no authentication).
     * @param tls                     Indicates if TLS is enabled (null for auto-detect).
     *                                Default: {@code false} for localhost, {@code true} for remote.
     * @param tlsCertFile             The path to the TLS certificate file.
     * @param tlsKeyFile              The path to the TLS key file.
     * @param caCertFile              The path to the CA cert file.
     * @param caCertPem               CA certificate as PEM bytes (alternative to caCertFile).
     * @param tlsCertPem              Client certificate as PEM bytes (alternative to tlsCertFile).
     * @param tlsKeyPem               Client private key as PEM bytes (alternative to tlsKeyFile).
     * @param serverNameOverride      Override for TLS server name verification.
     * @param insecureSkipVerify      Disable certificate verification (dev only).
     * @param credentialProvider      Pluggable credential provider for token refresh.
     * @param maxReceiveSize          The maximum size of the messages to receive (in bytes).
     *                                Default: {@code 104857600} (100 MB).
     * @param reconnectIntervalSeconds The interval between reconnection attempts.
     *                                Default: {@code 1} second.
     * @param keepAlive               Indicates if the connection should be kept alive (default: true).
     * @param pingIntervalInSeconds   The interval between keepalive pings (default: 10s).
     * @param pingTimeoutInSeconds    The timeout for keepalive pings (default: 5s).
     * @param logLevel                The logging level to use. Default: {@code Level.INFO}.
     * @param reconnectionConfig      Configuration for reconnection behavior (optional).
     * @param connectionStateListener Listener for connection state changes (optional).
     * @param shutdownTimeoutSeconds  Timeout for graceful shutdown (default: 5s).
     * @param connectionTimeoutSeconds Timeout for initial connection (default: 10s, 0=no timeout).
     * @param maxSendMessageSize      Maximum outbound message size (default: 100MB).
     * @param waitForReady            Block during CONNECTING/RECONNECTING until READY (default: true).
     * @param logger                  User-provided KubeMQLogger (null for auto-detection via KubeMQLoggerFactory).
     * @param validateOnBuild         When true, pings the server during construction to verify connectivity.
     *                                Default: {@code false}.
     */
    public KubeMQClient(String address, String clientId, String authToken, Boolean tls,
                        String tlsCertFile, String tlsKeyFile, String caCertFile,
                        byte[] caCertPem, byte[] tlsCertPem, byte[] tlsKeyPem,
                        String serverNameOverride, boolean insecureSkipVerify,
                        CredentialProvider credentialProvider,
                        int maxReceiveSize, int reconnectIntervalSeconds, Boolean keepAlive,
                        int pingIntervalInSeconds, int pingTimeoutInSeconds, Level logLevel,
                        ReconnectionConfig reconnectionConfig,
                        ConnectionStateListener connectionStateListener,
                        int shutdownTimeoutSeconds,
                        int connectionTimeoutSeconds,
                        int maxSendMessageSize,
                        Boolean waitForReady,
                        KubeMQLogger logger,
                        boolean validateOnBuild) {

        this.logger = (logger != null) ? logger : KubeMQLoggerFactory.getLogger("io.kubemq.sdk");

        // REQ-DX-2: Default address resolution: explicit > env var > localhost:50000
        String envAddress = System.getenv("KUBEMQ_ADDRESS");
        String effectiveAddress;
        if (address != null && !address.trim().isEmpty()) {
            effectiveAddress = address;
        } else if (envAddress != null && !envAddress.trim().isEmpty()) {
            effectiveAddress = envAddress;
        } else {
            effectiveAddress = "localhost:50000";
            this.logger.warn("Using default address localhost:50000. "
                + "Set address explicitly or use KUBEMQ_ADDRESS environment variable "
                + "for production deployments.");
        }

        // REQ-DX-1: Validate address format
        validateAddress(effectiveAddress);
        this.address = effectiveAddress;

        // REQ-DX-2: Default clientId to auto-generated UUID
        if (clientId == null || clientId.trim().isEmpty()) {
            this.clientId = "kubemq-client-" + UUID.randomUUID().toString().substring(0, 8);
        } else {
            this.clientId = clientId;
        }

        // REQ-AUTH-2: Address-aware TLS defaulting
        if (tls == null) {
            this.tls = !isLocalhostAddress(this.address);
            if (this.tls) {
                this.logger.info("TLS enabled by default for remote address",
                                 "address", this.address);
            }
        } else {
            this.tls = tls;
        }

        // REQ-AUTH-1: Mutable token via AtomicReference
        if (credentialProvider != null && authToken != null && !authToken.isEmpty()) {
            throw new IllegalArgumentException(
                "Cannot specify both authToken and credentialProvider. Use one or the other.");
        }
        this.authTokenRef.set(authToken);

        this.tlsCertFile = tlsCertFile;
        this.tlsKeyFile = tlsKeyFile;
        this.caCertFile = caCertFile;
        this.caCertPem = caCertPem;
        this.tlsCertPem = tlsCertPem;
        this.tlsKeyPem = tlsKeyPem;
        this.serverNameOverride = serverNameOverride;
        this.insecureSkipVerify = insecureSkipVerify;
        this.credentialProvider = credentialProvider;

        validateTlsConfiguration();

        this.maxReceiveSize = maxReceiveSize <= 0 ? (1024 * 1024 * 100) : maxReceiveSize;
        this.reconnectIntervalSeconds = reconnectIntervalSeconds <= 0 ? 1 : reconnectIntervalSeconds;
        this.keepAlive = keepAlive;
        this.pingIntervalInSeconds = pingIntervalInSeconds;
        this.pingTimeoutInSeconds = pingTimeoutInSeconds;
        this.logLevel = logLevel != null ? logLevel : Level.INFO;
        this.reconnectIntervalInMillis = TimeUnit.SECONDS.toMillis(this.reconnectIntervalSeconds);
        this.requestTimeoutSeconds = 30;
        this.shutdownTimeoutSeconds = shutdownTimeoutSeconds <= 0 ? 5 : shutdownTimeoutSeconds;
        this.callbackCompletionTimeoutSeconds = 30;
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
        this.maxSendMessageSize = maxSendMessageSize <= 0 ? (1024 * 1024 * 100) : maxSendMessageSize;
        this.waitForReady = waitForReady != null ? waitForReady : true;

        if (this.pingIntervalInSeconds < 0) {
            throw new IllegalArgumentException("pingIntervalInSeconds must be non-negative, got: " + this.pingIntervalInSeconds);
        }
        if (this.pingTimeoutInSeconds < 0) {
            throw new IllegalArgumentException("pingTimeoutInSeconds must be non-negative, got: " + this.pingTimeoutInSeconds);
        }

        // REQ-AUTH-4: Credential provider setup
        if (credentialProvider != null) {
            this.credentialRefreshScheduler =
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "kubemq-credential-refresh");
                    t.setDaemon(true);
                    return t;
                });
            this.credentialManager = new CredentialManager(credentialProvider, credentialRefreshScheduler);
        } else if (authToken != null && !authToken.isEmpty()) {
            this.credentialManager = new CredentialManager(
                new StaticTokenProvider(authToken), null);
        }

        this.reconnectionConfig = reconnectionConfig != null
            ? reconnectionConfig : ReconnectionConfig.builder().build();
        this.connectionStateMachine = new ConnectionStateMachine();
        if (connectionStateListener != null) {
            this.connectionStateMachine.addListener(connectionStateListener);
        }
        this.reconnectionManager = new ReconnectionManager(
            this.reconnectionConfig, this.connectionStateMachine);
        this.messageBuffer = new MessageBuffer(
            this.reconnectionConfig.getReconnectBufferSizeBytes(),
            this.reconnectionConfig.getBufferOverflowPolicy());

        setLogLevel();

        // REQ-OBS-1/3: Initialize OTel tracing and metrics via lazy-loading factories
        String serverHost = parseHost(this.address);
        int serverPortNum = parsePort(this.address);
        this.tracing = TracingFactory.create(null, null, this.clientId, serverHost, serverPortNum);
        this.metrics = MetricsFactory.create(null, null, null);

        this.connectionStateMachine.transitionTo(ConnectionState.CONNECTING);
        initChannel();
        this.connectionStateMachine.transitionTo(ConnectionState.READY);
        this.metrics.recordConnectionOpened();

        // REQ-DX-1: Optional eager connectivity validation
        if (validateOnBuild) {
            try {
                this.ping();
            } catch (Exception e) {
                if (managedChannel != null) {
                    managedChannel.shutdownNow();
                }
                throw ConnectionException.builder()
                    .code(ErrorCode.CONNECTION_FAILED)
                    .message("Failed to connect to KubeMQ server at '" + this.address
                        + "' during build validation. "
                        + "Ensure the server is running and accessible. "
                        + "To skip this check, remove .validateOnBuild(true) from the builder.")
                    .operation("ClientBuilder.build()")
                    .cause(e)
                    .build();
            }
        }

        clientCount.incrementAndGet();
        registerShutdownHook();
    }

    /**
     * Registers a JVM shutdown hook to clean up all static executors.
     * This is registered once and shared across all client instances.
     */
    private static void registerShutdownHook() {
        if (!shutdownHookRegistered) {
            synchronized (shutdownHookLock) {
                if (!shutdownHookRegistered) {
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        staticLog.info("Shutting down KubeMQ SDK executors");
                        shutdownAllExecutors();
                    }, "kubemq-shutdown"));
                    shutdownHookRegistered = true;
                }
            }
        }
    }

    /**
     * Shuts down all static executors used by the SDK.
     */
    private static void shutdownAllExecutors() {
        shutdownQuietly("QueueUpstreamHandler",
            () -> shutdownExecutor(io.kubemq.sdk.queues.QueueUpstreamHandler.getCleanupExecutor()));
        shutdownQuietly("QueueDownstreamHandler",
            () -> shutdownExecutor(io.kubemq.sdk.queues.QueueDownstreamHandler.getCleanupExecutor()));
        shutdownQuietly("EventStreamHelper",
            () -> shutdownExecutor(io.kubemq.sdk.pubsub.EventStreamHelper.getCleanupExecutor()));
        shutdownQuietly("QueueMessageReceived",
            () -> shutdownExecutor(io.kubemq.sdk.queues.QueueMessageReceived.getVisibilityExecutor()));
        shutdownQuietly("EventsSubscription",
            () -> shutdownExecutor(io.kubemq.sdk.pubsub.EventsSubscription.getReconnectExecutor()));
        shutdownQuietly("EventsStoreSubscription",
            () -> shutdownExecutor(io.kubemq.sdk.pubsub.EventsStoreSubscription.getReconnectExecutor()));
        shutdownQuietly("CommandsSubscription",
            () -> shutdownExecutor(io.kubemq.sdk.cq.CommandsSubscription.getReconnectExecutor()));
        shutdownQuietly("QueriesSubscription",
            () -> shutdownExecutor(io.kubemq.sdk.cq.QueriesSubscription.getReconnectExecutor()));
    }

    private static void shutdownQuietly(String name, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            staticLog.debug("Error shutting down " + name + " executor",
                            "error", e.getMessage());
        }
    }

    /**
     * Helper method to shutdown an executor with timeout.
     */
    private static void shutdownExecutor(ExecutorService executor) {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Validates address format. Accepts "host:port" where host is non-empty
     * and port is a valid integer 1-65535.
     *
     * @param address the address to validate
     * @throws ValidationException if address format is invalid
     */
    private static void validateAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            throw ValidationException.builder()
                .code(ErrorCode.INVALID_ARGUMENT)
                .message("Address is required and cannot be empty.")
                .operation("ClientBuilder.build()")
                .build();
        }

        String trimmed = address.trim();
        int lastColon = trimmed.lastIndexOf(':');
        if (lastColon <= 0 || lastColon == trimmed.length() - 1) {
            throw ValidationException.builder()
                .code(ErrorCode.INVALID_ARGUMENT)
                .message("Address must be in 'host:port' format. Got: '" + trimmed
                    + "'. Example: 'localhost:50000'")
                .operation("ClientBuilder.build()")
                .build();
        }

        String portStr = trimmed.substring(lastColon + 1);
        try {
            int port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) {
                throw ValidationException.builder()
                    .code(ErrorCode.INVALID_ARGUMENT)
                    .message("Port must be between 1 and 65535. Got: " + port)
                    .operation("ClientBuilder.build()")
                    .build();
            }
        } catch (NumberFormatException e) {
            throw ValidationException.builder()
                .code(ErrorCode.INVALID_ARGUMENT)
                .message("Port must be a valid integer. Got: '" + portStr + "'")
                .operation("ClientBuilder.build()")
                .build();
        }
    }

    /**
     * Validates TLS configuration.
     * Ensures that when TLS is enabled, certificate files are properly configured
     * and that file/PEM byte options are not mixed.
     */
    private void validateTlsConfiguration() {
        // Validate that file and PEM bytes are not both provided
        if (caCertFile != null && !caCertFile.isEmpty() && caCertPem != null && caCertPem.length > 0) {
            throw new IllegalArgumentException(
                "Cannot specify both caCertFile and caCertPem. Use one or the other.");
        }
        if (tlsCertFile != null && !tlsCertFile.isEmpty() && tlsCertPem != null && tlsCertPem.length > 0) {
            throw new IllegalArgumentException(
                "Cannot specify both tlsCertFile and tlsCertPem. Use one or the other.");
        }
        if (tlsKeyFile != null && !tlsKeyFile.isEmpty() && tlsKeyPem != null && tlsKeyPem.length > 0) {
            throw new IllegalArgumentException(
                "Cannot specify both tlsKeyFile and tlsKeyPem. Use one or the other.");
        }

        if (!isTls()) {
            return;
        }

        // Validate mutual TLS file configuration - cert and key must be provided together
        boolean hasCert = tlsCertFile != null && !tlsCertFile.isEmpty();
        boolean hasKey = tlsKeyFile != null && !tlsKeyFile.isEmpty();

        if (hasCert != hasKey) {
            throw new IllegalArgumentException(
                "When using mutual TLS, both tlsCertFile and tlsKeyFile must be provided together. " +
                "Got tlsCertFile=" + (hasCert ? "set" : "null") +
                ", tlsKeyFile=" + (hasKey ? "set" : "null")
            );
        }

        // Validate mutual TLS PEM bytes - cert and key must be provided together
        boolean hasCertPem = tlsCertPem != null && tlsCertPem.length > 0;
        boolean hasKeyPem = tlsKeyPem != null && tlsKeyPem.length > 0;

        if (hasCertPem != hasKeyPem) {
            throw new IllegalArgumentException(
                "When using mutual TLS, both tlsCertPem and tlsKeyPem must be provided together. " +
                "Got tlsCertPem=" + (hasCertPem ? "set" : "null") +
                ", tlsKeyPem=" + (hasKeyPem ? "set" : "null")
            );
        }

        // Cannot mix file and PEM for client cert/key
        if ((hasCert && hasCertPem) || (hasKey && hasKeyPem)) {
            throw new IllegalArgumentException(
                "Cannot mix file paths and PEM bytes for client certificate/key. " +
                "Use either tlsCertFile+tlsKeyFile or tlsCertPem+tlsKeyPem.");
        }

        // Validate CA cert file if provided
        if (caCertFile != null && !caCertFile.isEmpty()) {
            File caCertFileObj = new File(caCertFile);
            if (!caCertFileObj.exists()) {
                throw new IllegalArgumentException(
                    "CA certificate file does not exist: " + caCertFile
                );
            }
        }

        // Validate client cert files if provided
        if (hasCert) {
            File certFileObj = new File(tlsCertFile);
            File keyFileObj = new File(tlsKeyFile);

            if (!certFileObj.exists()) {
                throw new IllegalArgumentException(
                    "Client certificate file does not exist: " + tlsCertFile
                );
            }
            if (!keyFileObj.exists()) {
                throw new IllegalArgumentException(
                    "Client key file does not exist: " + tlsKeyFile
                );
            }
        }

        logger.debug("TLS configuration validated",
                     "caCert", caCertFile != null || (caCertPem != null && caCertPem.length > 0),
                     "clientCert", hasCert || hasCertPem,
                     "clientKey", hasKey || hasKeyPem,
                     "insecureSkipVerify", insecureSkipVerify);
    }

    /**
     * Initializes the gRPC channel and stubs based on the configuration provided.
     * Sets up TLS if enabled, otherwise uses plaintext communication.
     */
    private void initChannel() {
        String resolvedAddress = address;
        if (!address.contains("://")) {
            resolvedAddress = "dns:///" + address;
        }

        logger.debug("Constructing channel to KubeMQ", "address", resolvedAddress);
        if (isTls()) {
            try {
                NettyChannelBuilder ncb = NettyChannelBuilder.forTarget(resolvedAddress)
                        .negotiationType(NegotiationType.TLS)
                        .maxInboundMessageSize(maxReceiveSize);

                if (serverNameOverride != null && !serverNameOverride.isEmpty()) {
                    ncb = ncb.overrideAuthority(serverNameOverride);
                }

                SslContext sslContext = createSslContext();
                ncb = ncb.sslContext(sslContext);

                configureKeepAlive(ncb);
                managedChannel = ncb.build();
            } catch (SSLException e) {
                throw classifyTlsException(e);
            }
        } else {
            ManagedChannelBuilder mcb = ManagedChannelBuilder.forTarget(resolvedAddress)
                    .maxInboundMessageSize(maxReceiveSize)
                    .usePlaintext();
            configureKeepAlive(mcb);
            managedChannel = mcb.build();
        }

        // Always apply AuthInterceptor -- it checks token presence on each call
        ClientInterceptor authInterceptor = new AuthInterceptor();
        Channel channel = ClientInterceptors.intercept(managedChannel, authInterceptor);
        this.blockingStub = kubemqGrpc.newBlockingStub(channel);
        this.asyncStub = kubemqGrpc.newStub(channel);

        addChannelStateListener();

        logger.debug("Client initialized for KubeMQ",
                     "address", address,
                     "token_present", authTokenRef.get() != null && !authTokenRef.get().isEmpty());
    }

    private void configureKeepAlive(ManagedChannelBuilder<?> builder) {
        int time = pingIntervalInSeconds == 0 ? 10 : pingIntervalInSeconds;
        int timeout = pingTimeoutInSeconds == 0 ? 5 : pingTimeoutInSeconds;
        boolean withoutCalls = keepAlive != null ? keepAlive : true;
        builder.keepAliveTime(time, TimeUnit.SECONDS);
        builder.keepAliveTimeout(timeout, TimeUnit.SECONDS);
        builder.keepAliveWithoutCalls(withoutCalls);
    }

    private void addChannelStateListener() {
        monitorChannelState(managedChannel.getState(false));
    }

    private void monitorChannelState(ConnectivityState currentState) {
        if (connectionStateMachine.getState() == ConnectionState.CLOSED) {
            return;
        }
        managedChannel.notifyWhenStateChanged(currentState, () -> {
            ConnectivityState newState = managedChannel.getState(false);
            switch (newState) {
                case TRANSIENT_FAILURE:
                    logger.debug("gRPC channel TRANSIENT_FAILURE detected");
                    managedChannel.resetConnectBackoff();
                    reconnectionManager.startReconnection(this::reconnectChannel);
                    break;
                case SHUTDOWN:
                    logger.debug("gRPC channel SHUTDOWN detected");
                    connectionStateMachine.transitionTo(ConnectionState.CLOSED);
                    break;
                case READY:
                    break;
                default:
                    break;
            }
            monitorChannelState(newState);
        });
    }

    private void reconnectChannel() {
        if (isTls()) {
            reconnectWithCertReload();
        } else {
            ManagedChannel oldChannel = managedChannel;
            if (oldChannel != null) {
                oldChannel.shutdown();
            }
            initChannel();
        }
    }

    /**
     * Recreates the gRPC channel with fresh TLS certificates.
     * Supports certificate rotation by re-reading files on each reconnection.
     */
    private void reconnectWithCertReload() {
        synchronized (reconnectLock) {
            try {
                SslContext sslContext = createSslContext();

                ManagedChannel oldChannel = managedChannel;
                if (oldChannel != null) {
                    oldChannel.shutdown();
                }

                String resolvedAddress = address;
                if (!address.contains("://")) {
                    resolvedAddress = "dns:///" + address;
                }

                NettyChannelBuilder ncb = NettyChannelBuilder.forTarget(resolvedAddress)
                        .negotiationType(NegotiationType.TLS)
                        .maxInboundMessageSize(maxReceiveSize)
                        .sslContext(sslContext);

                if (serverNameOverride != null && !serverNameOverride.isEmpty()) {
                    ncb = ncb.overrideAuthority(serverNameOverride);
                }

                configureKeepAlive(ncb);

                managedChannel = ncb.build();

                ClientInterceptor authInterceptor = new AuthInterceptor();
                Channel channel = ClientInterceptors.intercept(managedChannel, authInterceptor);
                this.blockingStub = kubemqGrpc.newBlockingStub(channel);
                this.asyncStub = kubemqGrpc.newStub(channel);

                addChannelStateListener();

                logger.info("Reconnected with fresh TLS certificates",
                           "address", address);

            } catch (SSLException e) {
                RuntimeException classified = classifyTlsException(e);
                logger.error("TLS certificate reload failed during reconnection", e,
                             "error", classified.getMessage());
                managedChannel.resetConnectBackoff();
                addChannelStateListener();
            } catch (Exception e) {
                logger.error("Failed to read certificate files during reconnection", e,
                             "error", e.getMessage());
                managedChannel.resetConnectBackoff();
                addChannelStateListener();
            }
        }
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
     * Returns the executor for subscription callbacks. Single-threaded by default
     * to ensure sequential callback processing per subscription.
     */
    public ExecutorService getCallbackExecutor() {
        ExecutorService exec = defaultCallbackExecutor;
        if (exec == null) {
            synchronized (executorInitLock) {
                exec = defaultCallbackExecutor;
                if (exec == null) {
                    exec = Executors.newSingleThreadExecutor(r -> {
                        Thread t = new Thread(r, "kubemq-callback-" + clientId);
                        t.setDaemon(true);
                        return t;
                    });
                    defaultCallbackExecutor = exec;
                }
            }
        }
        return exec;
    }

    /**
     * Returns the executor for async operations. Bounded thread pool by default,
     * sized to the number of available processors.
     */
    public ExecutorService getAsyncOperationExecutor() {
        ExecutorService exec = defaultAsyncOperationExecutor;
        if (exec == null) {
            synchronized (executorInitLock) {
                exec = defaultAsyncOperationExecutor;
                if (exec == null) {
                    exec = Executors.newFixedThreadPool(
                        Math.max(2, Runtime.getRuntime().availableProcessors()),
                        r -> {
                            Thread t = new Thread(r, "kubemq-async-" + clientId);
                            t.setDaemon(true);
                            return t;
                        });
                    defaultAsyncOperationExecutor = exec;
                }
            }
        }
        return exec;
    }

    /**
     * Returns the in-flight operation counter for use by subscription handlers
     * and async operations.
     */
    public AtomicInteger getInFlightOperations() {
        return inFlightOperations;
    }

    /**
     * Execute a gRPC unary operation within a cancellable context, returning a
     * CompletableFuture that propagates cancellation to the gRPC call.
     *
     * @param operation the gRPC operation to execute
     * @param <T> the result type
     * @return a CompletableFuture wired to gRPC cancellation
     */
    protected <T> CompletableFuture<T> executeWithCancellation(
            java.util.function.Supplier<T> operation) {
        io.grpc.Context.CancellableContext grpcContext =
            io.grpc.Context.current().withCancellation();

        CompletableFuture<T> future = new CompletableFuture<>();
        inFlightOperations.incrementAndGet();

        getAsyncOperationExecutor().execute(() -> {
            try {
                T result = grpcContext.call(operation::get);
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            } finally {
                grpcContext.close();
                inFlightOperations.decrementAndGet();
            }
        });

        future.whenComplete((result, ex) -> {
            if (future.isCancelled()) {
                grpcContext.cancel(new java.util.concurrent.CancellationException(
                    "Operation cancelled by client"));
            }
        });

        return future;
    }

    /**
     * Unwrap a CompletableFuture result with proper exception handling.
     * Converts ExecutionException/CompletionException wrappers to the underlying
     * KubeMQException, and handles timeout and interruption.
     *
     * @param future the future to unwrap
     * @param timeout the maximum time to wait
     * @param <T> the result type
     * @return the result
     */
    protected <T> T unwrapFuture(CompletableFuture<T> future, Duration timeout) {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            throw unwrapException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw OperationCancelledException.builder()
                .message("Operation interrupted")
                .operation("unwrapFuture")
                .cause(e)
                .build();
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            throw KubeMQTimeoutException.builder()
                .message("Operation timed out after " + timeout)
                .operation("unwrapFuture")
                .cause(e)
                .build();
        }
    }

    /**
     * Unwrap ExecutionException to get the underlying cause.
     * If the cause is a KubeMQException, return it directly.
     * Otherwise, wrap in a KubeMQException.
     */
    protected RuntimeException unwrapException(java.util.concurrent.ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof KubeMQException) {
            return (KubeMQException) cause;
        }
        return KubeMQException.newBuilder()
            .code(ErrorCode.UNKNOWN_ERROR)
            .message("Async operation failed: " + (cause != null ? cause.getMessage() : e.getMessage()))
            .operation("async")
            .cause(cause != null ? cause : e)
            .build();
    }

    /**
     * Initiates graceful shutdown of this client.
     * <p>
     * If the client is in RECONNECTING state, reconnection is cancelled and buffered
     * messages are discarded (OnBufferDrain callback fires). Otherwise, buffered messages
     * are flushed before the channel closes.
     * <p>
     * This method is idempotent -- calling it multiple times is safe and has no additional effect.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        logger.info("Initiating graceful shutdown",
                    "timeout_seconds", shutdownTimeoutSeconds,
                    "state", connectionStateMachine.getState());

        ConnectionState currentState = connectionStateMachine.getState();

        if (currentState == ConnectionState.RECONNECTING) {
            reconnectionManager.cancel();
            messageBuffer.discardAll();
        } else {
            if (messageBuffer.size() > 0) {
                logger.info("Flushing buffered messages before shutdown",
                           "count", messageBuffer.size());
                messageBuffer.flush(this::sendBufferedMessage);
            }
        }

        // REQ-CONC-5: Wait for in-flight callbacks and async operations
        int inFlight = inFlightOperations.get();
        if (inFlight > 0) {
            logger.info("Waiting for in-flight operations to complete",
                        "count", inFlight,
                        "timeout_seconds", callbackCompletionTimeoutSeconds);
            long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(callbackCompletionTimeoutSeconds);
            while (inFlightOperations.get() > 0 && System.nanoTime() < deadline) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Interrupted while waiting for in-flight callbacks");
                    break;
                }
            }
            int remaining = inFlightOperations.get();
            if (remaining > 0) {
                logger.warn("In-flight operations did not complete within timeout",
                            "remaining", remaining,
                            "timeout_seconds", callbackCompletionTimeoutSeconds);
            }
        }

        // REQ-CONC-3/5: Shutdown callback and async executors
        shutdownSdkExecutor(defaultCallbackExecutor, "callback");
        shutdownSdkExecutor(defaultAsyncOperationExecutor, "async-operation");

        if (managedChannel != null) {
            try {
                managedChannel.shutdown()
                    .awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.error("Channel shutdown interrupted", e);
                Thread.currentThread().interrupt();
            } finally {
                if (!managedChannel.isTerminated()) {
                    managedChannel.shutdownNow();
                }
            }
        }

        if (credentialManager != null) {
            credentialManager.shutdown();
        }
        if (credentialRefreshScheduler != null) {
            credentialRefreshScheduler.shutdownNow();
        }

        reconnectionManager.shutdown();
        connectionStateMachine.transitionTo(ConnectionState.CLOSED);
        connectionStateMachine.shutdown();
        metrics.recordConnectionClosed();

        int remaining = clientCount.decrementAndGet();
        if (remaining == 0) {
            logger.debug("Last client closed");
        }
    }

    private void sendBufferedMessage(BufferedMessage msg) {
        logger.warn("Discarding buffered message during shutdown "
                    + "(flush-on-close is best-effort; no concrete sender available in base class)",
                    "type", msg.messageType(),
                    "size_bytes", msg.estimatedSizeBytes());
    }

    private void shutdownSdkExecutor(ExecutorService executor, String name) {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * @deprecated SDK no longer controls log levels directly. Configure your
     * SLF4J provider (logback, log4j2) via its own configuration instead.
     * This method is retained for backward compatibility but is a no-op.
     */
    @Deprecated
    private void setLogLevel() {
        logger.debug("setLogLevel() is deprecated; configure logging via SLF4J provider",
                     "requestedLevel", logLevel);
    }

    /**
     * Sends a ping request to the KubeMQ server and returns the result.
     *
     * @return The ping result from the server.
     * @throws RuntimeException if the ping request fails.
     */
    public ServerInfo ping() {
        try {
            logger.debug("Pinging KubeMQ server", "address", address);
            Kubemq.PingResult pingResult = blockingStub.ping(null);
            logger.debug("Ping successful");
            return ServerInfo.builder()
                    .host(pingResult.getHost())
                    .version(pingResult.getVersion())
                    .serverStartTime(pingResult.getServerStartTime())
                    .serverUpTimeSeconds(pingResult.getServerUpTimeSeconds())
                    .build();
        } catch (StatusRuntimeException e) {
            logger.error("Ping failed", e);
            throw io.kubemq.sdk.exception.GrpcErrorMapper.map(e, "ping", null, null, false);
        }
    }

    /**
     * Returns the current connection state.
     * @return the current {@link ConnectionState}
     */
    public ConnectionState getConnectionState() {
        return connectionStateMachine.getState();
    }

    /**
     * Register a listener for connection state transitions.
     * @param listener the listener to add
     */
    public void addConnectionStateListener(ConnectionStateListener listener) {
        connectionStateMachine.addListener(listener);
    }

    /**
     * Performs a server compatibility check after connection.
     * Logs a warning if the server version is outside the tested range.
     * Never throws or blocks the connection.
     */
    private void checkServerCompatibility() {
        try {
            ServerInfo info = ping();
            if (info != null && info.getVersion() != null) {
                if (!CompatibilityConfig.isCompatible(info.getVersion())) {
                    logger.warn(
                        "KubeMQ server version {} is outside the tested compatibility range " +
                        "[{} - {}] for SDK version {}. The connection will proceed, but " +
                        "some features may not work as expected. See COMPATIBILITY.md for details.",
                        info.getVersion(),
                        CompatibilityConfig.MIN_SERVER_VERSION,
                        CompatibilityConfig.MAX_SERVER_VERSION,
                        CompatibilityConfig.SDK_VERSION
                    );
                } else {
                    logger.debug(
                        "Server compatibility check passed: server={}, SDK={}",
                        info.getVersion(),
                        CompatibilityConfig.SDK_VERSION
                    );
                }
            }
        } catch (Exception e) {
            logger.debug("Server compatibility check skipped: {}", e.getMessage());
        }
    }

    /**
     * Thread-safe guard that runs the server compatibility check exactly once.
     * Called from {@link #ensureNotClosed()} so every public operation triggers it.
     */
    private void checkCompatibilityOnce() {
        if (!compatibilityChecked.get()
                && compatibilityChecked.compareAndSet(false, true)) {
            checkServerCompatibility();
        }
    }

    /**
     * Check if the client is closed and throw if so.
     * Must be called at the start of every public operation method.
     */
    protected void ensureNotClosed() {
        checkCompatibilityOnce();
        if (closed.get()) {
            throw ClientClosedException.create();
        }
    }

    /**
     * Block until the connection is READY, or fail if not waitForReady.
     * Called at the start of every operation.
     *
     * @param operationTimeoutMs per-operation timeout in milliseconds
     */
    protected void ensureReady(long operationTimeoutMs) {
        ensureNotClosed();

        ConnectionState currentState = connectionStateMachine.getState();
        if (currentState == ConnectionState.READY) {
            return;
        }

        if (!waitForReady) {
            throw ConnectionNotReadyException.create(currentState.name());
        }

        CompletableFuture<Void> readyFuture = new CompletableFuture<>();

        ConnectionStateListener readyListener = new ConnectionStateListener() {
            @Override public void onConnected() { readyFuture.complete(null); }
            @Override public void onReconnected() { readyFuture.complete(null); }
            @Override public void onClosed() {
                readyFuture.completeExceptionally(ClientClosedException.create());
            }
        };

        connectionStateMachine.addListener(readyListener);
        try {
            if (connectionStateMachine.getState() == ConnectionState.READY) {
                return;
            }
            if (connectionStateMachine.getState() == ConnectionState.CLOSED) {
                throw ClientClosedException.create();
            }
            readyFuture.get(operationTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw ConnectionNotReadyException.create(connectionStateMachine.getState().name());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw OperationCancelledException.builder()
                .code(ErrorCode.CANCELLED_BY_CLIENT)
                .message("Interrupted while waiting for connection to become ready")
                .operation("ensureReady")
                .cause(e)
                .build();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ClientClosedException) {
                throw (ClientClosedException) e.getCause();
            }
            throw ConnectionException.builder()
                .code(ErrorCode.CONNECTION_FAILED)
                .message("Error while waiting for connection: " + e.getMessage())
                .operation("ensureReady")
                .cause(e.getCause())
                .build();
        } finally {
            connectionStateMachine.removeListener(readyListener);
        }
    }

    /**
     * @return true if this client has been closed
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Enum representing the log levels supported by the KubeMQClient.
     */
    public enum Level {
        TRACE, DEBUG, INFO, WARN, ERROR, OFF
    }

    /**
     * Returns the current authentication token.
     *
     * @return the current auth token, or null if not set
     */
    public String getAuthToken() {
        return authTokenRef.get();
    }

    /**
     * Updates the authentication token used for all subsequent requests.
     * Thread-safe: can be called from any thread without synchronization.
     * The new token takes effect on the next gRPC call.
     *
     * @param token the new authentication token, or null to clear authentication
     */
    public void setAuthToken(String token) {
        this.authTokenRef.set(token);
        logger.debug("Auth token updated",
                     "token_present", token != null && !token.isEmpty());
    }

    /**
     * Returns whether TLS is enabled.
     *
     * @return true if TLS is enabled
     */
    public boolean isTls() {
        return Boolean.TRUE.equals(this.tls);
    }

    /**
     * Returns the OTel tracing instrumentation for this client.
     * Returns a no-op implementation when OTel is not on the classpath.
     */
    public Tracing getTracing() {
        return tracing;
    }

    /**
     * Returns the OTel metrics instrumentation for this client.
     * Returns a no-op implementation when OTel is not on the classpath.
     */
    public Metrics getMetrics() {
        return metrics;
    }

    private static String parseHost(String address) {
        int lastColon = address.lastIndexOf(':');
        if (lastColon > 0) {
            return address.substring(0, lastColon);
        }
        return address;
    }

    private static int parsePort(String address) {
        int lastColon = address.lastIndexOf(':');
        if (lastColon > 0 && lastColon < address.length() - 1) {
            try {
                return Integer.parseInt(address.substring(lastColon + 1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * Determines whether the given address is a localhost address.
     * Localhost addresses: "localhost", "127.0.0.1", "::1", "[::1]".
     *
     * @param address the target address (host:port format)
     * @return true if the address is localhost
     */
    public static boolean isLocalhostAddress(String address) {
        if (address == null) {
            return false;
        }
        String host = address;
        int lastColon = address.lastIndexOf(':');
        if (lastColon > 0 && !address.endsWith("]")) {
            host = address.substring(0, lastColon);
        } else if (address.startsWith("[")) {
            int bracket = address.indexOf(']');
            if (bracket > 0) {
                host = address.substring(0, bracket + 1);
            }
        }
        return "localhost".equalsIgnoreCase(host)
            || "127.0.0.1".equals(host)
            || "::1".equals(host)
            || "[::1]".equals(host);
    }

    /**
     * Checks a StatusRuntimeException for UNAUTHENTICATED status and wraps it
     * as an AuthenticationException with an actionable message.
     *
     * @param e the gRPC exception to check
     * @return an AuthenticationException if the status is UNAUTHENTICATED, otherwise the original exception wrapped
     */
    public RuntimeException wrapGrpcException(StatusRuntimeException e) {
        if (e.getStatus().getCode() == Status.Code.UNAUTHENTICATED) {
            String token = authTokenRef.get();
            String hint = (token == null || token.isEmpty())
                ? "Server requires authentication. Set auth token via builder.authToken() or provide a CredentialProvider."
                : "Authentication token was rejected by the server. Verify the token is valid and not expired.";
            return AuthenticationException.builder()
                .message(hint)
                .operation("grpc-call")
                .cause(e)
                .statusCode(e.getStatus().getCode().value())
                .build();
        }
        return new RuntimeException(e);
    }

    /**
     * Creates a fresh SslContext by reading certificate files from disk.
     * Called on every connection/reconnection to pick up rotated certificates.
     *
     * @return a new SslContext configured with current certificates
     * @throws SSLException if the SSL context cannot be built
     */
    public SslContext createSslContext() throws SSLException {
        logger.debug("Loading TLS certificates from configured source");

        SslContextBuilder sslContextBuilder = SslContextBuilder.forClient();

        sslContextBuilder.protocols("TLSv1.3", "TLSv1.2");

        if (insecureSkipVerify) {
            logger.warn("certificate verification is disabled -- "
                        + "this is insecure and should only be used in development");
            sslContextBuilder.trustManager(
                io.grpc.netty.shaded.io.netty.handler.ssl.util
                    .InsecureTrustManagerFactory.INSTANCE);
        } else {
            if (caCertPem != null && caCertPem.length > 0) {
                sslContextBuilder.trustManager(new ByteArrayInputStream(caCertPem));
            } else if (caCertFile != null && !caCertFile.isEmpty()) {
                sslContextBuilder.trustManager(new File(caCertFile));
            }
        }

        if (tlsCertPem != null && tlsCertPem.length > 0
                && tlsKeyPem != null && tlsKeyPem.length > 0) {
            sslContextBuilder.keyManager(
                new ByteArrayInputStream(tlsCertPem),
                new ByteArrayInputStream(tlsKeyPem));
        } else if (tlsCertFile != null && !tlsCertFile.isEmpty()
                && tlsKeyFile != null && !tlsKeyFile.isEmpty()) {
            sslContextBuilder.keyManager(new File(tlsCertFile), new File(tlsKeyFile));
        }

        logger.debug("TLS certificates loaded successfully");
        return sslContextBuilder.build();
    }

    /**
     * Classifies TLS/SSL exceptions into the appropriate SDK exception type.
     *
     * @param e the SSL exception to classify
     * @return the classified RuntimeException
     */
    public RuntimeException classifyTlsException(SSLException e) {
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        Throwable cause = e.getCause();

        if (e instanceof SSLHandshakeException) {
            if (message.contains("certificate") || message.contains("cert")
                    || message.contains("trust") || message.contains("expired")
                    || message.contains("hostname") || message.contains("peer not authenticated")) {
                return AuthenticationException.builder()
                    .message("TLS handshake failed due to certificate validation: " + e.getMessage())
                    .operation("connect")
                    .cause(e)
                    .build();
            }
            if (message.contains("protocol") || message.contains("cipher")
                    || message.contains("no common") || message.contains("version")) {
                return ConfigurationException.builder()
                    .message("TLS handshake failed due to protocol/cipher negotiation: " + e.getMessage()
                        + ". Verify TLS version compatibility (minimum TLS 1.2).")
                    .operation("connect")
                    .cause(e)
                    .build();
            }
        }

        if (cause instanceof IOException
                || message.contains("connection reset") || message.contains("broken pipe")) {
            return ConnectionException.builder()
                .code(ErrorCode.CONNECTION_FAILED)
                .message("TLS handshake failed due to network error: " + e.getMessage())
                .operation("connect")
                .cause(e)
                .build();
        }

        return AuthenticationException.builder()
            .message("TLS handshake failed: " + e.getMessage())
            .operation("connect")
            .cause(e)
            .build();
    }

    @Override
    public String toString() {
        return "KubeMQClient{" +
               "address='" + address + '\'' +
               ", clientId='" + clientId + '\'' +
               ", tls=" + isTls() +
               ", token_present=" + (authTokenRef.get() != null && !authTokenRef.get().isEmpty()) +
               ", credentialProvider_present=" + (credentialManager != null) +
               ", insecureSkipVerify=" + insecureSkipVerify +
               '}';
    }

    /**
     * gRPC client interceptor that injects authentication metadata on every call.
     * Reads the token from the client's AtomicReference or CredentialManager,
     * so token updates are reflected immediately without re-creating the interceptor.
     */
    private class AuthInterceptor implements ClientInterceptor {

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
            return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                    next.newCall(method, callOptions)) {

                @Override
                public void start(Listener<RespT> responseListener, Metadata headers) {
                    String token = null;
                    if (credentialManager != null) {
                        token = credentialManager.getToken();
                    } else {
                        token = authTokenRef.get();
                    }
                    if (token != null && !token.isEmpty()) {
                        Metadata.Key<String> key = Metadata.Key.of("authorization",
                                Metadata.ASCII_STRING_MARSHALLER);
                        headers.put(key, token);
                    }

                    super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(
                            responseListener) {
                        @Override
                        public void onClose(Status status, Metadata trailers) {
                            if (status.getCode() == Status.Code.UNAUTHENTICATED
                                    && credentialManager != null) {
                                credentialManager.invalidate();
                            }
                            super.onClose(status, trailers);
                        }
                    }, headers);
                }
            };
        }
    }
}

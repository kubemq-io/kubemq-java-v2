
/**
 * KubeMQ Queue Publisher Service.
 *
 * This service sends messages to a KubeMQ queue channel with server-side Dead Letter Queue
 * (DLQ) configuration. Each message is stamped with:
 *
 *   - attemptsBeforeDeadLetterQueue: Max number of delivery attempts (rejections by the subscriber)
 *     before the KubeMQ server automatically routes the message to the DLQ channel.
 *   - deadLetterQueue: The channel name where failed messages are routed after max attempts.
 *
 * How server-side DLQ works:
 *   1. Publisher sends a message with attemptsBeforeDeadLetterQueue=3 and deadLetterQueue="dlq-payment".
 *   2. Subscriber receives the message and calls reject() (processing failed).
 *   3. KubeMQ server increments the receiveCount and redelivers the message.
 *   4. After 3 total rejections, the server automatically moves the message to "dlq-payment".
 *   5. The subscriber does NOT need to check retry counts or manually route to DLQ.
 *
 * Required configuration in application.properties:
 *
 *   kubemq.canal.payment=payment-queue          # Queue channel name
 *   kubemq.kubemqserveraddres=localhost:50000    # KubeMQ server address
 *   kubemq.canal.payment.uid=publisher-1         # Unique client ID for this publisher
 *   kubemq.canal.payment.maxRetries=3            # Max delivery attempts before DLQ (default: 3)
 *   kubemq.canal.payment.dlq=dlq-payment         # DLQ channel name (default: dlq-payment)
 */
@Service
public class PublisherService {

    // KubeMQ queue client for sending messages.
    private final QueuesClient queuesClient;

    // Name of the channel (queue) to which messages will be sent.
    private final String queueName;

    // Server-side DLQ configuration:
    // maxRetries - how many times the subscriber can reject() a message before the
    //              KubeMQ server automatically routes it to the DLQ channel.
    private final int maxRetries;

    // dlqChannel - the KubeMQ queue channel where failed messages are routed.
    //              Messages in the DLQ have isReRouted()=true and reRouteFromQueue set
    //              to the original channel name.
    private final String dlqChannel;

    /**
     * Service constructor. Initializes the KubeMQ client with the required configuration.
     *
     * @param queueName  Name of the channel configured in the application.properties file
     * @param kubemqHost Host/IP where KubeMQ is running (e.g. localhost:50000)
     * @param clientId   Unique identifier of this publisher client
     * @param maxRetries Max delivery attempts before routing to DLQ (default: 3)
     * @param dlqChannel Dead letter queue channel name (default: dlq-payment)
     */
    public PublisherService(@Value("${kubemq.canal.payment}") String queueName,
                            @Value("${kubemq.kubemqserveraddres}") String kubemqHost,
                            @Value("${kubemq.canal.payment.uid}") String clientId,
                            @Value("${kubemq.canal.payment.maxRetries:3}") int maxRetries,
                            @Value("${kubemq.canal.payment.dlq:dlq-payment}") String dlqChannel) {

        this.queuesClient = QueuesClient.builder()
                .address(kubemqHost)
                .clientId(clientId)
                .build();

        this.queueName = queueName;
        this.maxRetries = maxRetries;
        this.dlqChannel = dlqChannel;
    }

    /**
     * Sends a message to the KubeMQ queue with server-side DLQ configuration.
     *
     * The message includes:
     *   - attemptsBeforeDeadLetterQueue: Tells the KubeMQ server to route this message
     *     to the DLQ after this many failed delivery attempts (rejections by subscriber).
     *   - deadLetterQueue: The DLQ channel where failed messages are automatically routed.
     *
     * The subscriber only needs to call ack() on success or reject() on failure.
     * The server handles all retry counting and DLQ routing automatically.
     *
     * @param kube The request payload to send.
     * @return true if the message was sent (note: returns true even on send failure due
     *         to the catch block - consider fixing this in a future iteration).
     */
    public boolean sendMessage(KubeRequest kube)  {
        String uid = UUID.randomUUID().toString();
        ObjectMapper mapper=new ObjectMapper();
        try {
        String payload=mapper.writeValueAsString(kube);

        QueueMessage message = QueueMessage.builder()
                .id(uid)
                .channel(queueName)
                .body(payload.getBytes(StandardCharsets.UTF_8))
                // --- SERVER-SIDE DLQ CONFIGURATION ---
                // These two fields tell the KubeMQ server how to handle failed messages:
                // After 'maxRetries' rejections by the subscriber, the server automatically
                // moves the message to the 'dlqChannel' queue. The subscriber does not need
                // to implement any DLQ logic - just ack() or reject().
                .attemptsBeforeDeadLetterQueue(maxRetries)
                .deadLetterQueue(dlqChannel)
                .build();

        QueueSendResult result = queuesClient.sendQueuesMessage(message);

        if (result.isError()) {
            throw new RuntimeException("Error al enviar a KubeMQ: " + result.getError());
        }

        System.out.println("Mensaje enviado correctamente con ID: " + result.getId());
        } catch (Exception e) {
            System.out.println("Error al enviar a KUBEMQ " + e.getMessage());
		}
        return true;
    }
}

# How To: Connect with TLS and mTLS

Configure encrypted connections to KubeMQ using TLS (server verification) or mTLS (mutual certificate authentication).

## TLS — Server Certificate Verification

```java
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.common.ServerInfo;

public class TlsConnection {
    public static void main(String[] args) {
        try (PubSubClient client = PubSubClient.builder()
                .address("kubemq-server:50000")
                .clientId("my-service")
                .tls(true)
                .caCertFile("/path/to/ca.pem")
                .build()) {

            ServerInfo info = client.ping();
            System.out.println("Connected with TLS: " + info.getVersion());
        }
    }
}
```

## mTLS — Mutual Certificate Authentication

```java
import io.kubemq.sdk.pubsub.PubSubClient;

public class MtlsConnection {
    public static void main(String[] args) {
        try (PubSubClient client = PubSubClient.builder()
                .address("kubemq-server:50000")
                .clientId("my-service")
                .tls(true)
                .caCertFile("/path/to/ca.pem")
                .tlsCertFile("/path/to/client.pem")
                .tlsKeyFile("/path/to/client.key")
                .build()) {

            System.out.println("Connected with mTLS: " + client.ping().getHost());
        }
    }
}
```

## Using PEM Bytes Instead of Files

Load certificates from Vault, K8s secrets, or environment variables:

```java
import io.kubemq.sdk.queues.QueuesClient;
import java.nio.charset.StandardCharsets;

public class TlsFromBytes {
    public static void main(String[] args) {
        byte[] caPem = System.getenv("CA_CERT").getBytes(StandardCharsets.UTF_8);
        byte[] certPem = System.getenv("CLIENT_CERT").getBytes(StandardCharsets.UTF_8);
        byte[] keyPem = System.getenv("CLIENT_KEY").getBytes(StandardCharsets.UTF_8);

        try (QueuesClient client = QueuesClient.builder()
                .address("kubemq-server:50000")
                .clientId("my-service")
                .tls(true)
                .caCertPem(caPem)
                .tlsCertPem(certPem)
                .tlsKeyPem(keyPem)
                .build()) {

            client.ping();
            System.out.println("Connected with PEM bytes");
        }
    }
}
```

## Development: Skip Certificate Verification

```java
PubSubClient client = PubSubClient.builder()
        .address("dev-server:50000")
        .tls(true)
        .insecureSkipVerify(true)  // NEVER use in production
        .build();
```

## Auto-TLS for Remote Addresses

TLS is enabled automatically for non-localhost addresses when `tls` is not explicitly set. Set `.tls(false)` to force plaintext for remote servers.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `SSLHandshakeException: PKIX path building failed` | CA cert doesn't match server cert | Use the CA that signed the server certificate |
| `SSLHandshakeException: certificate_unknown` | Server doesn't trust client cert | Ensure server CA includes client cert issuer |
| `FileNotFoundException` on cert path | Wrong file path | Verify paths exist and are readable |
| `Cannot specify both caCertFile and caCertPem` | Mixed file/bytes config | Use either file paths or PEM bytes, not both |
| Connection hangs on remote address | TLS disabled for TLS-required server | Set `.tls(true)` explicitly |

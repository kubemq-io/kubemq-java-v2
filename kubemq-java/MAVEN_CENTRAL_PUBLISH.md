# Maven Central Publishing Guide (Mac)

## Prerequisites

### GPG Setup

1. Install GPG via Homebrew (if not already installed):
   ```bash
   brew install gnupg
   ```

2. Verify GPG is installed:
   ```bash
   gpg --version
   ```

3. Generate a GPG key pair (if you don't have one):
   ```bash
   gpg --full-generate-key
   ```
   - Select RSA and RSA (default)
   - Key size: 4096
   - Expiration: 0 (does not expire)
   - Enter your name and email (use `lior.nabat@kubemq.io`)
   - Passphrase: optional (can leave empty for CI/CD)

4. List your GPG keys:
   ```bash
   gpg --list-keys
   ```
   Note your key ID (the long hex string after `rsa4096/` or on the `pub` line).

   Current key: `0F9D4ACC4D55D2A37F2A05C4EF4AC12F3C94AD91`

### Upload GPG Key to Keyservers

Upload your public key to keyservers so Maven Central can verify signatures:

```bash
# Replace KEY_ID with your actual key ID
gpg --keyserver keys.openpgp.org --send-keys KEY_ID
gpg --keyserver pgp.mit.edu --send-keys KEY_ID
gpg --keyserver keyserver.ubuntu.com --send-keys KEY_ID
```

Example:
```bash
gpg --keyserver keys.openpgp.org --send-keys 0F9D4ACC4D55D2A37F2A05C4EF4AC12F3C94AD91
gpg --keyserver pgp.mit.edu --send-keys 0F9D4ACC4D55D2A37F2A05C4EF4AC12F3C94AD91
gpg --keyserver keyserver.ubuntu.com --send-keys 0F9D4ACC4D55D2A37F2A05C4EF4AC12F3C94AD91
```

## Maven Configuration

### ~/.m2/settings.xml

Create or update `~/.m2/settings.xml` with your Central Portal token credentials:

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <servers>
    <server>
      <id>central</id>
      <username>YOUR_TOKEN_USERNAME</username>
      <password>YOUR_TOKEN_PASSWORD</password>
    </server>
  </servers>
  <profiles>
    <profile>
      <id>central</id>
      <activation>
        <activeByDefault>true</activeByDefault>
      </activation>
      <properties>
        <gpg.executable>gpg</gpg.executable>
        <gpg.keyname>YOUR_GPG_KEY_ID</gpg.keyname>
      </properties>
    </profile>
  </profiles>
  <localRepository>${user.home}/.m2/repository</localRepository>
</settings>
```

**To get your token credentials:**
1. Log in to https://central.sonatype.com
2. Go to Account Settings → User Tokens
3. Generate a new token pair

### pom.xml Configuration

Ensure your `pom.xml` has the Central Publishing plugin:

```xml
<!-- Central Publishing Plugin for Maven Central -->
<plugin>
    <groupId>org.sonatype.central</groupId>
    <artifactId>central-publishing-maven-plugin</artifactId>
    <version>0.7.0</version>
    <extensions>true</extensions>
    <configuration>
        <publishingServerId>central</publishingServerId>
        <autoPublish>true</autoPublish>
        <waitUntil>published</waitUntil>
    </configuration>
</plugin>
```

Also ensure you have these plugins for signing and generating sources/javadoc:

```xml
<!-- GPG Signing -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-gpg-plugin</artifactId>
    <version>3.2.4</version>
    <executions>
        <execution>
            <id>sign-artifacts</id>
            <phase>verify</phase>
            <goals>
                <goal>sign</goal>
            </goals>
            <configuration>
                <executable>gpg</executable>
            </configuration>
        </execution>
    </executions>
</plugin>

<!-- Source JAR -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-source-plugin</artifactId>
    <version>3.3.1</version>
    <executions>
        <execution>
            <id>attach-sources</id>
            <goals>
                <goal>jar-no-fork</goal>
            </goals>
        </execution>
    </executions>
</plugin>

<!-- Javadoc JAR -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-javadoc-plugin</artifactId>
    <version>3.6.3</version>
    <configuration>
        <failOnError>false</failOnError>
    </configuration>
    <executions>
        <execution>
            <id>attach-javadocs</id>
            <goals>
                <goal>jar</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

## Deploy

### Java Version Requirement

This project requires Java 21 (due to Lombok compatibility). Set JAVA_HOME before deploying:

```bash
export JAVA_HOME="/opt/homebrew/Cellar/openjdk@21/21.0.9/libexec/openjdk.jdk/Contents/Home"
```

Or run the deploy command with JAVA_HOME inline:

```bash
JAVA_HOME="/opt/homebrew/Cellar/openjdk@21/21.0.9/libexec/openjdk.jdk/Contents/Home" mvn clean deploy -DskipTests
```

### Deploy Command

```bash
mvn clean deploy -DskipTests
```

The Central Publishing plugin will:
1. Build and sign all artifacts
2. Create a bundle
3. Upload to Central Portal
4. Automatically publish (due to `autoPublish=true`)
5. Wait until published (due to `waitUntil=published`)

## Verify Publication

After deployment, verify your artifact is live:

1. **Maven Central Repository:**
   https://repo1.maven.org/maven2/io/kubemq/sdk/kubemq-sdk-Java/

2. **Central Portal:**
   https://central.sonatype.com/artifact/io.kubemq.sdk/kubemq-sdk-Java

## Troubleshooting

### "Component already exists" Error
If you get this error, the version was already published. Either:
- Check the Central Portal to confirm it's published
- Bump the version number in pom.xml

### 401/403 Authentication Errors
- Verify your token credentials in `~/.m2/settings.xml`
- Regenerate tokens from https://central.sonatype.com → Account Settings → User Tokens

### GPG Signing Errors
- Ensure your GPG key ID is correct in settings.xml
- Verify the key is uploaded to keyservers
- Check that GPG is in your PATH

### Java Version Issues
- Lombok 1.18.x requires Java 21 or lower
- Use `java -version` to check current version
- Set JAVA_HOME to Java 21 if needed

## Important Links

- **Central Portal:** https://central.sonatype.com
- **Maven Central:** https://repo1.maven.org/maven2/
- **Artifact Page:** https://central.sonatype.com/artifact/io.kubemq.sdk/kubemq-sdk-Java

## Notes

- The old OSSRH (oss.sonatype.org) was shut down on June 30, 2025
- All publishing now goes through the Central Portal (central.sonatype.com)
- No manual "Close" and "Release" steps needed - the plugin handles everything automatically

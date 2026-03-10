# Implementation Specification: Category 06 -- Documentation

**SDK:** KubeMQ Java v2
**Category:** 06 -- Documentation
**GS Source:** `clients/golden-standard/06-documentation.md`
**Gap Research:** `clients/gap-research/java-gap-research.md` (lines 886-1045)
**Assessment:** `clients/assesments/JAVA_ASSESSMENT_REPORT.md` (Category 10, lines 560-614)
**Review Rounds:** `clients/gap-research/java-review-r1.md` (C-3), `clients/gap-research/java-review-r2.md`
**Current Score:** 3.00 / 5.0 | **Target:** 4.0+
**Priority:** P0 (Tier 1 gate blocker -- 4 fully MISSING requirements; upgraded from P1 in Review R1 C-3)
**Total Estimated Effort:** 14-22 days

---

## Table of Contents

1. [Summary of REQ Items](#1-summary-of-req-items)
2. [Implementation Order](#2-implementation-order)
3. [REQ-DOC-1: Auto-Generated API Reference](#3-req-doc-1-auto-generated-api-reference)
4. [REQ-DOC-2: README](#4-req-doc-2-readme)
5. [REQ-DOC-3: Quick Start (First Message in 5 Minutes)](#5-req-doc-3-quick-start-first-message-in-5-minutes)
6. [REQ-DOC-4: Code Examples / Cookbook](#6-req-doc-4-code-examples--cookbook)
7. [REQ-DOC-5: Troubleshooting Guide](#7-req-doc-5-troubleshooting-guide)
8. [REQ-DOC-6: CHANGELOG](#8-req-doc-6-changelog)
9. [REQ-DOC-7: Migration Guide](#9-req-doc-7-migration-guide)
10. [Cross-Category Dependencies](#10-cross-category-dependencies)
11. [Breaking Changes](#11-breaking-changes)
12. [Open Questions](#12-open-questions)
13. [Acceptance Checklist](#13-acceptance-checklist)

---

## 1. Summary of REQ Items

| REQ | Status | Gap | Effort | Impl Order |
|-----|--------|-----|--------|------------|
| REQ-DOC-1 | MISSING | Zero Javadoc across 50 files | L-XL (5-8 days) | 4 (Phase 3) |
| REQ-DOC-2 | PARTIAL | Missing badges, error handling, troubleshooting sections | M (1-2 days) | 6 (Phase 4) |
| REQ-DOC-3 | PARTIAL | Needs restructure into explicit quick start format | S (< 1 day) | 5 (Phase 3) |
| REQ-DOC-4 | PARTIAL | Missing examples README, CI compile check, inline comments gaps | M (1-2 days) | 3 (Phase 2) |
| REQ-DOC-5 | MISSING | No troubleshooting guide exists | M (1-2 days) | 2 (Phase 2) |
| REQ-DOC-6 | MISSING | No CHANGELOG.md | S (< 1 day) | 1 (Phase 1) |
| REQ-DOC-7 | MISSING | No v1-to-v2 migration guide | M (1-2 days) | 7 (Phase 4) |

---

## 2. Implementation Order

The order reflects dependency chains and value delivery. Documentation items are mostly independent, so ordering prioritizes quick wins and upstream dependencies.

**Phase 1 -- Quick Wins (day 1):**
1. REQ-DOC-6 (CHANGELOG) -- no dependencies, immediate value, required by DOC-2 and DOC-7

**Phase 2 -- New Documents (days 2-4):**
2. REQ-DOC-5 (troubleshooting guide) -- depends on REQ-ERR-1 error types being defined (from `01-error-handling-spec.md`); can use stub error messages if ERR-1 not yet implemented
3. REQ-DOC-4 (examples improvements) -- depends on REQ-TEST-3 for CI compile check (Category 04); examples README and inline comments can proceed independently

**Phase 3 -- API Reference & Quick Start (days 5-12):**
4. REQ-DOC-1 (Javadoc) -- no blocking dependencies; longest single effort item
5. REQ-DOC-3 (quick start restructure) -- can be done in parallel with DOC-1

**Phase 4 -- README & Migration (days 13-16):**
6. REQ-DOC-2 (README restructure) -- depends on DOC-3, DOC-5, DOC-6 content being available
7. REQ-DOC-7 (migration guide) -- depends on DOC-6 for cross-linking

---

## 3. REQ-DOC-1: Auto-Generated API Reference

**Gap Status:** MISSING
**GS Reference:** 06-documentation.md, REQ-DOC-1
**Assessment Evidence:** 10.1.1 (score 2), 10.1.2 (score 1), 10.1.3 (score 1), 10.1.4 (score 1), 10.1.5 (score 1)
**Effort:** L-XL (5-8 days) -- 50 source files, ~200+ public methods, zero existing Javadoc. Revised upward from L in Review R1.

### 3.1 Current State

The SDK has zero Javadoc comments across all 50 source files in `kubemq-java/src/main/java/io/kubemq/sdk/`. The only documentation comment found is on `KubeMQClient.java` (a single class-level comment). Maven Javadoc Plugin is already configured in `pom.xml` (line 201) but generates empty documentation due to missing source comments.

**Source file inventory (50 files across 5 packages):**

| Package | Files | Public Methods (est.) | Current Javadoc |
|---------|-------|-----------------------|-----------------|
| `client/` | 1 (`KubeMQClient.java`) | ~20 | 1 class-level comment only |
| `common/` | 5 (`ServerInfo`, `SubscribeType`, `ChannelDecoder`, `KubeMQUtils`, `RequestType`) | ~25 | 0 |
| `exception/` | 4 (`GRPCException`, `CreateChannelException`, `DeleteChannelException`, `ListChannelsException`) | ~12 | 0 |
| `pubsub/` | 18 (EventMessage, EventStoreMessage, PubSubClient, subscriptions, etc.) | ~80 | 0 |
| `cq/` | 10 (CQClient, CommandMessage, QueryMessage, etc.) | ~45 | 0 |
| `queues/` | 12 (QueuesClient, QueueMessage, QueueDownStreamProcessor, etc.) | ~60 | 0 |

**Lombok complication:** Most message classes use `@Data`, `@Builder`, `@AllArgsConstructor`, `@NoArgsConstructor`. Lombok-generated methods (getters, setters, builders, `toString`, `equals`, `hashCode`) will not carry Javadoc unless handled explicitly.

### 3.2 Target Design

#### 3.2.1 Javadoc Comment Standard

Every public class, method, constructor, and constant must have a Javadoc comment following this template:

```java
/**
 * Brief one-sentence summary (not restating method name).
 *
 * <p>Optional extended description providing context, behavior details,
 * or usage guidance.
 *
 * <p>Example usage:
 * <pre>{@code
 * EventMessage msg = EventMessage.builder()
 *     .channel("notifications")
 *     .body("Hello".getBytes())
 *     .build();
 * }</pre>
 *
 * @param channel the target channel name; must not be null or empty
 * @param body    message payload as byte array; empty array permitted
 * @param metadata optional string metadata; may be null
 * @param tags    optional key-value pairs for filtering; may be null
 * @return the send result containing the message ID and delivery status
 * @throws KubeMQException if the server rejects the message or connection fails
 * @throws ValidationException if required fields (channel, body) are missing
 * @since 2.1.0
 * @see EventSendResult
 */
```

**Rules for summary line:**
- WRONG: "Gets the channel" (restates method name)
- RIGHT: "Returns the destination channel this message will be published to"
- WRONG: "Send event message" (imperative restating)
- RIGHT: "Publishes a fire-and-forget event to all subscribers on the specified channel"

#### 3.2.2 Lombok Javadoc Strategy

Since Lombok generates methods at compile time, use these approaches:

**Review R1 M-8: Build Integration with Delombok**

All message types use `@Data` + `@Builder` + `@NoArgsConstructor` + `@AllArgsConstructor`. Lombok-generated builder methods, getters, setters, equals, hashCode, and toString are not visible in source and cannot have Javadoc added directly. To generate complete Javadoc including Lombok-generated methods:

1. **Add `lombok-maven-plugin` to `pom.xml`** for Javadoc generation:

```xml
<plugin>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok-maven-plugin</artifactId>
    <version>1.18.20.0</version>
    <executions>
        <execution>
            <phase>generate-sources</phase>
            <goals>
                <goal>delombok</goal>
            </goals>
            <configuration>
                <sourceDirectory>${project.basedir}/src/main/java</sourceDirectory>
                <outputDirectory>${project.build.directory}/delombok</outputDirectory>
                <addOutputDirectory>false</addOutputDirectory>
            </configuration>
        </execution>
    </executions>
</plugin>
```

2. **Configure `maven-javadoc-plugin` to use delombok output:**

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-javadoc-plugin</artifactId>
    <configuration>
        <sourcepath>${project.build.directory}/delombok</sourcepath>
    </configuration>
</plugin>
```

3. **For `@Builder` field documentation:** Lombok 1.18.22+ propagates field-level Javadoc to builder setter methods. Ensure the Lombok version in `pom.xml` is >= 1.18.22. If not, upgrade as part of this work.

**Which public methods need manual Javadoc vs delombok coverage:**

| Method Type | Javadoc Source | Notes |
|-------------|---------------|-------|
| Getters/Setters on `@Data` classes | Field-level Javadoc (propagated by Lombok) | Write once on the field |
| `builder()`, `build()` on `@Builder` classes | Class-level Javadoc with usage example | Delombok generates these |
| Per-field builder setters (e.g., `.channel(String)`) | Field-level Javadoc (propagated by Lombok 1.18.22+) | Verify propagation |
| `toString()`, `equals()`, `hashCode()` on `@Data` classes | Not documented (standard behavior) | Delombok generates these |
| Non-Lombok public methods | Standard method-level Javadoc | Written manually |

**For `@Data` / `@Getter` / `@Setter` classes (message types):**
- Add field-level Javadoc. Lombok copies field Javadoc to generated getter/setter if `lombok.config` is configured:

```
# lombok.config (repo root)
lombok.addGeneratedAnnotation = true
config.stopBubbling = true
lombok.copyableAnnotations += io.kubemq.sdk.annotation.Documented
```

- Document each field with its purpose, default, and constraints:

```java
public class EventMessage {
    /**
     * Target channel name for this event.
     * Must not be null or empty. Channels are created automatically
     * on first publish if they do not exist.
     */
    private String channel;

    /**
     * Message payload as raw bytes.
     * The server imposes a maximum size (default 100MB, configurable server-side).
     * An empty byte array is permitted for signal-only events.
     */
    private byte[] body;

    /**
     * Application-defined string metadata.
     * Not indexed or searchable by the server. Use tags for filterable key-value data.
     * May be null.
     */
    private String metadata;

    /**
     * Key-value string pairs for message filtering and routing.
     * Used by subscribers for server-side filtering. May be null or empty.
     */
    private Map<String, String> tags;
}
```

**For `@Builder` classes:**
- Add class-level Javadoc including a builder usage example.
- For builder method parameters, the field-level Javadoc serves as documentation.

**For non-Lombok classes (clients, handlers, processors):**
- Standard method-level Javadoc on every public method.

#### 3.2.3 Priority Order for Javadoc Authoring

Write Javadoc in this order (highest impact first):

1. **Client classes** (3 files) -- `KubeMQClient`, `PubSubClient`, `CQClient`, `QueuesClient` -- these are the primary API entry points developers interact with
2. **Message request types** (6 files) -- `EventMessage`, `EventStoreMessage`, `CommandMessage`, `QueryMessage`, `QueueMessage`, `QueuesPollRequest` -- developers construct these directly
3. **Response/result types** (8 files) -- `EventSendResult`, `CommandResponseMessage`, `QueryResponseMessage`, `QueueSendResult`, `QueuesPollResponse`, `QueueMessageReceived`, etc.
4. **Subscription types** (4 files) -- `EventsSubscription`, `EventsStoreSubscription`, `CommandsSubscription`, `QueriesSubscription`
5. **Exception classes** (4+ files) -- all exception types including new ones from `01-error-handling-spec.md`
6. **Enums and constants** (3 files) -- `SubscribeType`, `EventsStoreType`, `RequestType`
7. **Internal/handler classes** (10 files) -- `EventStreamHelper`, `QueueDownStreamProcessor`, `QueueUpstreamHandler`, `QueueDownstreamHandler`, etc.
8. **Common utilities** (5 files) -- `ServerInfo`, `ChannelDecoder`, `KubeMQUtils`

#### 3.2.4 Checkstyle Javadoc Linter Configuration

Create `checkstyle.xml` at `kubemq-java/checkstyle.xml`:

```xml
<?xml version="1.0"?>
<!DOCTYPE module PUBLIC
    "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
    "https://checkstyle.org/dtds/configuration_1_3.dtd">
<module name="Checker">
  <module name="TreeWalker">
    <!-- Require Javadoc on all public types -->
    <module name="JavadocType">
      <property name="scope" value="public"/>
      <property name="authorFormat" value=""/>
    </module>
    <!-- Require Javadoc on all public methods -->
    <module name="JavadocMethod">
      <property name="accessModifiers" value="public"/>
      <property name="allowMissingParamTags" value="false"/>
      <property name="allowMissingReturnTag" value="false"/>
    </module>
    <!-- Require Javadoc on all public fields -->
    <module name="JavadocVariable">
      <property name="scope" value="public"/>
    </module>
    <!-- Summary line must not be empty -->
    <module name="SummaryJavadocCheck">
      <property name="forbiddenSummaryFragments"
                value="^@return the *|^This method returns *|^A [{]@code [a-zA-Z0-9]+[}](?: is)? *"/>
    </module>
    <!-- Non-empty @param and @return descriptions -->
    <module name="NonEmptyAtclauseDescription"/>
  </module>
</module>
```

Add Maven Checkstyle Plugin to `pom.xml`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-checkstyle-plugin</artifactId>
    <version>3.3.1</version>
    <configuration>
        <configLocation>checkstyle.xml</configLocation>
        <includeTestSourceDirectory>false</includeTestSourceDirectory>
        <consoleOutput>true</consoleOutput>
        <failsOnError>true</failsOnError>
    </configuration>
    <executions>
        <execution>
            <id>validate-javadoc</id>
            <phase>validate</phase>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
    <dependencies>
        <dependency>
            <groupId>com.puppycrawl.tools</groupId>
            <artifactId>checkstyle</artifactId>
            <version>10.12.5</version>
        </dependency>
    </dependencies>
</plugin>
```

#### 3.2.5 Javadoc Generation and Publishing

The Maven Javadoc Plugin is already configured in `pom.xml`. Enhance it:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-javadoc-plugin</artifactId>
    <version>3.6.3</version>
    <configuration>
        <source>11</source>
        <doclint>all,-missing</doclint>
        <show>public</show>
        <nohelp>true</nohelp>
        <header>KubeMQ Java SDK ${project.version}</header>
        <bottom>Copyright &#169; 2024 KubeMQ. All rights reserved.</bottom>
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

**Publishing:** Publishing to Maven Central automatically makes Javadoc available on [javadoc.io](https://javadoc.io/doc/io.kubemq.sdk/kubemq-sdk-Java). The `-javadoc.jar` artifact attached during release is indexed by javadoc.io. No separate GitHub Pages setup needed.

#### 3.2.6 Acceptance Criteria Verification

| Criterion | Implementation | Verification |
|-----------|---------------|--------------|
| 100% of public types/methods have doc comments | Javadoc on all 50 source files | `mvn checkstyle:check` passes with zero violations |
| Doc comment linter in CI | Checkstyle plugin in `validate` phase | CI build fails if Javadoc is missing on any public API |
| API reference published and accessible | Maven Central javadoc.jar + javadoc.io | Visit `https://javadoc.io/doc/io.kubemq.sdk/kubemq-sdk-Java` after release |
| API reference regenerated on every release | `mvn deploy` attaches javadoc.jar | Verify javadoc.io version matches latest release |

---

## 4. REQ-DOC-2: README

**Gap Status:** PARTIAL
**GS Reference:** 06-documentation.md, REQ-DOC-2
**Assessment Evidence:** 10.4.1 (score 5), 10.4.2 (score 5), 10.4.3 (score 4), 10.4.4 (score 4), 10.4.5 (score 1)
**Effort:** M (1-2 days)

### 4.1 Current State

`README.md` at repo root is 1900 lines. It contains:
- Installation (Maven dependency XML)
- SDK Overview
- Client Configuration with parameters table
- Per-pattern documentation (Events, Events Store, Commands, Queries, Queues) with parameter tables and code examples
- License (MIT, in pom.xml, not explicitly linked in README)

**Missing per GS:**
- Badges (CI status, coverage, Maven Central version)
- Description paragraph (2-3 sentences)
- Messaging Pattern Comparison Table
- Configuration options table (consolidated -- currently scattered)
- Error handling section
- Troubleshooting section (top 5)
- Contributing link
- Absolute URLs for all links

### 4.2 Target Structure

Restructure `README.md` to follow the GS-mandated 10-section format. The README must remain the primary landing page, so it should be concise with links to detailed documentation rather than embedding all 1900 lines.

#### 4.2.1 New README Outline

```markdown
# KubeMQ Java SDK

[![Maven Central](https://img.shields.io/maven-central/v/io.kubemq.sdk/kubemq-sdk-Java)](https://central.sonatype.com/artifact/io.kubemq.sdk/kubemq-sdk-Java)
[![CI](https://github.com/kubemq-io/kubemq-java-v2/actions/workflows/ci.yml/badge.svg)](https://github.com/kubemq-io/kubemq-java-v2/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## Description                                               <!-- Section 2 -->

KubeMQ is a message queue and message broker designed for containerized workloads.
The KubeMQ Java SDK provides a type-safe client for all KubeMQ messaging patterns --
Events, Events Store, Commands, Queries, and Queues -- over gRPC transport with
built-in TLS, authentication, and reconnection support.

## Installation                                              <!-- Section 3 -->

### Maven
{maven dependency XML -- existing content}

### Gradle
{gradle dependency -- add this}

### Prerequisites
- Java 11 or higher
- KubeMQ server running (default: localhost:50000)
- Maven 3.6+ or Gradle 7+

## Quick Start                                               <!-- Section 4 -->
{link to per-pattern quick starts in section 5, plus the simplest possible example}

## Messaging Patterns                                        <!-- Section 5 -->
{comparison table + per-pattern quick starts}

## Configuration                                             <!-- Section 6 -->
{consolidated configuration options table}

## Error Handling                                            <!-- Section 7 -->
{how errors work, retry policy defaults, example}

## Troubleshooting                                           <!-- Section 8 -->
{top 5 common issues -- abbreviated, link to TROUBLESHOOTING.md}

## Contributing                                              <!-- Section 9 -->
{link to CONTRIBUTING.md}

## License                                                   <!-- Section 10 -->
{MIT license with link}
```

#### 4.2.2 Section 1: Title and Badges

```markdown
# KubeMQ Java SDK

[![Maven Central](https://img.shields.io/maven-central/v/io.kubemq.sdk/kubemq-sdk-Java)](https://central.sonatype.com/artifact/io.kubemq.sdk/kubemq-sdk-Java)
[![CI](https://github.com/kubemq-io/kubemq-java-v2/actions/workflows/ci.yml/badge.svg)](https://github.com/kubemq-io/kubemq-java-v2/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/kubemq-io/kubemq-java-v2/branch/main/graph/badge.svg)](https://codecov.io/gh/kubemq-io/kubemq-java-v2)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Javadoc](https://javadoc.io/badge2/io.kubemq.sdk/kubemq-sdk-Java/javadoc.svg)](https://javadoc.io/doc/io.kubemq.sdk/kubemq-sdk-Java)
```

**Note:** CI and coverage badges depend on REQ-TEST-3 (CI pipeline). Use placeholder badge URLs until CI is operational.

#### 4.2.3 Section 5: Messaging Pattern Comparison Table

This is a GS-mandated table that must appear verbatim:

```markdown
## Messaging Patterns

| Pattern | Delivery Guarantee | Use When | Example Use Case |
|---------|--------------------|----------|------------------|
| Events | At-most-once | Fire-and-forget broadcasting to multiple subscribers | Real-time notifications, log streaming |
| Events Store | At-least-once (persistent) | Subscribers must not miss messages, even if offline | Audit trails, event sourcing, replay |
| Queues | At-least-once (with ack) | Work must be processed by exactly one consumer with acknowledgment | Job processing, task distribution |
| Commands | At-most-once (request/reply) | You need confirmation that an action was executed | Device control, configuration changes |
| Queries | At-most-once (request/reply) | You need to retrieve data from a responder | Data lookups, service-to-service reads |
```

Below this table, include brief description + quick start link for each pattern.

#### 4.2.4 Section 6: Configuration Options Table

Consolidate currently scattered configuration documentation into one table:

```markdown
## Configuration

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `address` | `String` | `localhost:50000` | KubeMQ server gRPC address (host:port) |
| `clientId` | `String` | (required) | Unique identifier for this client instance |
| `authToken` | `String` | `null` | JWT authentication token for server access |
| `tls` | `boolean` | `false` | Enable TLS encryption for the connection |
| `tlsCertFile` | `String` | `null` | Path to TLS certificate file (PEM format) |
| `tlsKeyFile` | `String` | `null` | Path to TLS private key file (PEM format) |
| `tlsCaCertFile` | `String` | `null` | Path to CA certificate for server verification |
| `maxReceiveSize` | `int` | `104857600` | Maximum inbound message size in bytes (100MB) |
| `reconnectIntervalSeconds` | `int` | `5` | Seconds between reconnection attempts |
| `logLevel` | `Level` | `INFO` | Logging level (TRACE, DEBUG, INFO, WARN, ERROR, OFF) |
```

Include a code example showing builder configuration:

```java
PubSubClient client = PubSubClient.builder()
    .address("kubemq-server:50000")
    .clientId("my-service")
    .authToken("eyJ...")
    .tls(true)
    .tlsCertFile("/certs/client.pem")
    .build();
```

#### 4.2.5 Section 7: Error Handling

This section must cover:
1. How errors are represented (reference `KubeMQException` hierarchy from `01-error-handling-spec.md`)
2. Which errors are retryable vs non-retryable (reference `ErrorCategory` from `01-error-handling-spec.md`)
3. Default retry behavior
4. Code example showing error handling:

```markdown
## Error Handling

The SDK uses a typed exception hierarchy rooted at `KubeMQException`. Exceptions are
classified as retryable or non-retryable:

| Exception | Retryable | When |
|-----------|-----------|------|
| `TransientException` | Yes | Server temporarily unavailable, deadline exceeded |
| `AuthenticationException` | No | Invalid or expired auth token |
| `ValidationException` | No | Invalid request parameters |

```java
try {
    client.sendEventsMessage(message);
} catch (TransientException e) {
    // Automatic retry exhausted; check e.getAttempts()
    log.warn("Send failed after retries: {}", e.getMessage());
} catch (AuthenticationException e) {
    // Non-retryable; fix credentials
    log.error("Auth failed: {}", e.getMessage());
} catch (KubeMQException e) {
    // Catch-all for other SDK errors
    log.error("Unexpected error: {}", e.getMessage());
}
```

**Note:** This section content depends on the error types defined in `01-error-handling-spec.md`. If the error hierarchy is not yet implemented, use the current exception names (`GRPCException`, `RuntimeException`) with a TODO note to update when the hierarchy ships.

#### 4.2.6 Section 8: Troubleshooting (Top 5)

Abbreviated version of the troubleshooting guide. Include the 5 most common issues:

1. Connection refused / timeout
2. Authentication failed
3. No messages received (subscriber not getting messages)
4. Message too large
5. TLS handshake failure

Each entry: one-line symptom, one-line solution, link to full entry in `TROUBLESHOOTING.md`.

#### 4.2.7 Absolute URLs

Convert all relative links to absolute URLs using the base: `https://github.com/kubemq-io/kubemq-java-v2/blob/main/`

Examples:
- `[TROUBLESHOOTING.md](https://github.com/kubemq-io/kubemq-java-v2/blob/main/TROUBLESHOOTING.md)`
- `[CHANGELOG.md](https://github.com/kubemq-io/kubemq-java-v2/blob/main/CHANGELOG.md)`
- `[examples/](https://github.com/kubemq-io/kubemq-java-v2/tree/main/kubemq-java-example/src/main/java/io/kubemq/example)`

#### 4.2.8 README Length Target

The restructured README should be 300-500 lines (down from 1900). Move detailed per-pattern API documentation to a `docs/` directory or rely on the existing examples and Javadoc. The README's role is orientation, not comprehensive API reference.

### 4.3 Acceptance Criteria Verification

| Criterion | Implementation | Verification |
|-----------|---------------|--------------|
| All 10 sections present | Restructured README per 4.2.1 | Manual checklist review |
| Installation instructions work | Maven + Gradle snippets with current version | `mvn dependency:resolve` with snippet |
| All code examples compile/run | Examples tested in CI (REQ-DOC-4) | CI green |
| Links use absolute URLs | All links use `https://github.com/kubemq-io/...` base | Grep for relative `./` or `](` without `https://` |

---

## 5. REQ-DOC-3: Quick Start (First Message in 5 Minutes)

**Gap Status:** PARTIAL
**GS Reference:** 06-documentation.md, REQ-DOC-3
**Assessment Evidence:** 10.4.2 (score 5), 2.2.1 ("Basic publish in ~4 lines"), 2.2.2 ("Default address works")
**Effort:** S (< 1 day -- restructuring existing content)

### 5.1 Current State

The README contains copy-paste-ready code examples for each pattern. These work against `localhost:50000`. However, they are not structured in the explicit "Quick Start" format required by the GS (prerequisites, send code <= 10 lines, receive code <= 10 lines, expected output).

### 5.2 Target Design

Create three dedicated quick start blocks in the README Section 5 (Messaging Patterns), one per core pattern family.

#### 5.2.1 Events Quick Start

```markdown
### Quick Start: Events (Pub/Sub)

**Prerequisites:**
- Java 11+
- KubeMQ server running on `localhost:50000` ([install guide](https://docs.kubemq.io/getting-started/quick-start))
- SDK added to your project (see [Installation](#installation))

**Publish an event:**
```java
PubSubClient client = PubSubClient.builder()
    .address("localhost:50000")
    .clientId("events-sender")
    .build();
EventSendResult result = client.sendEventsMessage(EventMessage.builder()
    .channel("notifications")
    .body("Hello KubeMQ!".getBytes())
    .build());
System.out.println("Event sent: " + result.getId());
client.close();
```

**Subscribe to events:**
```java
PubSubClient client = PubSubClient.builder()
    .address("localhost:50000")
    .clientId("events-receiver")
    .build();
client.subscribeToEvents(EventsSubscription.builder()
    .channel("notifications")
    .onReceiveEventCallback(event ->
        System.out.println("Received: " + new String(event.getBody())))
    .onErrorCallback(err -> System.err.println("Error: " + err.getMessage()))
    .build());
// Keep running to receive messages
Thread.sleep(30000);
client.close();
```

**Expected output (subscriber):**
```
Received: Hello KubeMQ!
```
```

#### 5.2.2 Queues Quick Start

```markdown
### Quick Start: Queues

**Prerequisites:** (same as above)

**Send a queue message:**
```java
QueuesClient client = QueuesClient.builder()
    .address("localhost:50000")
    .clientId("queue-sender")
    .build();
QueueSendResult result = client.sendQueuesMessage(QueueMessage.builder()
    .channel("tasks")
    .body("Process this job".getBytes())
    .build());
System.out.println("Sent, expired: " + result.isExpired());
client.close();
```

**Receive and acknowledge:**
```java
QueuesClient client = QueuesClient.builder()
    .address("localhost:50000")
    .clientId("queue-receiver")
    .build();
QueuesPollResponse response = client.receiveQueuesMessages(QueuesPollRequest.builder()
    .channel("tasks")
    .pollMaxMessages(1)
    .pollWaitTimeoutInSeconds(10)
    .build());
for (QueueMessageReceived msg : response.getMessages()) {
    System.out.println("Processing: " + new String(msg.getBody()));
    msg.ack();
}
client.close();
```

**Expected output (receiver):**
```
Processing: Process this job
```
```

#### 5.2.3 RPC (Commands/Queries) Quick Start

```markdown
### Quick Start: RPC (Commands & Queries)

**Prerequisites:** (same as above)

**Handle a command (responder):**
```java
CQClient client = CQClient.builder()
    .address("localhost:50000")
    .clientId("command-handler")
    .build();
client.subscribeToCommands(CommandsSubscription.builder()
    .channel("device.control")
    .onReceiveCommandCallback(cmd -> {
        System.out.println("Executing: " + new String(cmd.getBody()));
        return CommandResponseMessage.builder()
            .requestId(cmd.getId())
            .isExecuted(true)
            .build();
    })
    .build());
```

**Send a command (caller):**
```java
CQClient client = CQClient.builder()
    .address("localhost:50000")
    .clientId("command-sender")
    .build();
CommandResponseMessage response = client.sendCommandRequest(CommandMessage.builder()
    .channel("device.control")
    .body("restart".getBytes())
    .timeout(5000)
    .build());
System.out.println("Executed: " + response.isExecuted());
client.close();
```

**Expected output (sender):**
```
Executed: true
```
```

### 5.3 Acceptance Criteria Verification

| Criterion | Implementation | Verification |
|-----------|---------------|--------------|
| Works with zero config against localhost:50000 | All examples use `localhost:50000` | Manual test with local KubeMQ |
| Copy-paste ready, no placeholders | No `<REPLACE_ME>` or `YOUR_...` strings | Grep for placeholder patterns |
| Each pattern has own quick start | Events, Queues, RPC quick starts | Section count = 3 |
| Total time from git clone to first message < 5 min | Maven `dependency:resolve` + compile + run | Timed walkthrough |

---

## 6. REQ-DOC-4: Code Examples / Cookbook

**Gap Status:** PARTIAL
**GS Reference:** 06-documentation.md, REQ-DOC-4
**Assessment Evidence:** 10.3.1 (score 5, 46 example files), 10.3.3 (score 4), 10.3.6 (score 4)
**Effort:** M (1-2 days)

### 6.1 Current State

46 example files exist in `kubemq-java-example/src/main/java/io/kubemq/example/` across 6 subdirectories:

| Directory | Files | Coverage |
|-----------|-------|----------|
| `config/` | 3 | TLS, auth token, client configuration |
| `cq/` | 9 | Commands, queries, group subscriptions, timeouts |
| `pubsub/` | 12 | Events, Events Store (all replay modes), group subscription, cancellation |
| `queues/` | 15 | Send/receive, ack/reject, DLQ, delay, expiry, batch, stream, visibility, channel search |
| `errorhandling/` | 3 | Connection errors, graceful shutdown, reconnection |
| `patterns/` | 3 | Fan-out, request-reply, work queue |

**What is missing:**
- No `README.md` in the examples directory
- No systematic inline comments (some examples have them, others do not)
- No CI compile check for examples
- No OpenTelemetry / observability example
- No queue stream upstream/downstream dedicated examples (stream example exists but combines both)
- Cookbook repo (`github.com/kubemq-io/java-sdk-cookbook`) is a stub

### 6.2 Target Design

#### 6.2.1 Examples Directory README

Create `kubemq-java-example/README.md`:

```markdown
# KubeMQ Java SDK Examples

Runnable examples demonstrating all KubeMQ messaging patterns and features.

## Prerequisites

- Java 11+
- KubeMQ server running on `localhost:50000`
- Build the SDK first: `cd kubemq-java && mvn install`

## Running an Example

```bash
cd kubemq-java-example
mvn compile exec:java -Dexec.mainClass="io.kubemq.example.pubsub.SendEventMessageExample"
```

## Examples by Category

### Events (Pub/Sub)
| Example | Description |
|---------|-------------|
| `pubsub/SendEventMessageExample` | Publish a single event |
| `pubsub/SubscribeToEventExample` | Subscribe to events on a channel |
| `pubsub/GroupSubscriptionExample` | Load-balanced subscription using groups |
| `pubsub/SubscriptionCancelExample` | Cancel an active subscription |
| `pubsub/CreateChannelExample` | Create an events channel |
| `pubsub/DeleteChannelExample` | Delete an events channel |
| `pubsub/ListEventsChanneExample` | List available events channels |

### Events Store (Persistent Pub/Sub)
| Example | Description |
|---------|-------------|
| `pubsub/SubscribeToEventStoreExample` | Subscribe to persistent events |
| `pubsub/EventsStoreStartFromFirstExample` | Replay from first stored event |
| `pubsub/EventsStoreStartFromLastExample` | Start from last stored event |
| `pubsub/EventsStoreStartAtSequenceExample` | Replay from specific sequence number |
| `pubsub/EventsStoreStartAtTimeDeltaExample` | Replay from relative time offset |
| `pubsub/EventsStoreStartNewOnlyExample` | Receive only new events |

### Queues
| Example | Description |
|---------|-------------|
| `queues/CreateQueuesChannelExample` | Create a queue channel |
| `queues/Send_ReceiveMessageUsingStreamExample` | Send and receive via stream |
| `queues/SendBatchMessagesExample` | Send multiple messages in a batch |
| `queues/WaitingPullExample` | Long-poll for messages |
| `queues/MessageDelayExample` | Send with delivery delay |
| `queues/MessageExpirationExample` | Send with expiration time |
| `queues/MessageRejectExample` | Reject a message (return to queue) |
| `queues/ReQueueMessageExample` | Re-queue to a different channel |
| `queues/ReceiveMessageDLQ` | Handle dead letter queue messages |
| `queues/ReceiveMessageWithVisibilityExample` | Visibility timeout for processing |
| `queues/AutoAckModeExample` | Automatic acknowledgment mode |
| `queues/ReceiveMessageMultiThreadedExample` | Multi-threaded message processing |
| `queues/ChannelSearchExample` | Search for queue channels |

### Commands & Queries (RPC)
| Example | Description |
|---------|-------------|
| `cq/CommandsExample` | Send and handle commands |
| `cq/CommandWithTimeoutExample` | Commands with custom timeout |
| `cq/QueriesExample` | Send and handle queries |
| `cq/QueryWithDataResponseExample` | Queries returning data payloads |
| `cq/GroupSubscriptionCommandsExample` | Load-balanced command handling |
| `cq/GroupSubscriptionQueriesExample` | Load-balanced query handling |
| `cq/CreateExample` | Create CQ channels |
| `cq/DeleteExample` | Delete CQ channels |
| `cq/ListExample` | List CQ channels |

### Configuration
| Example | Description |
|---------|-------------|
| `config/ClientConfigurationExample` | Client builder configuration options |
| `config/TLSConnectionExample` | TLS-encrypted connection setup |
| `config/AuthTokenExample` | JWT token authentication |

### Error Handling
| Example | Description |
|---------|-------------|
| `errorhandling/ConnectionErrorHandlingExample` | Handle connection failures |
| `errorhandling/GracefulShutdownExample` | Clean client shutdown |
| `errorhandling/ReconnectionHandlerExample` | Automatic reconnection handling |

### Patterns
| Example | Description |
|---------|-------------|
| `patterns/PubSubFanOutExample` | Fan-out to multiple subscribers |
| `patterns/RequestReplyPatternExample` | Request-reply using commands |
| `patterns/WorkQueuePatternExample` | Work distribution via queues |
```

#### 6.2.2 Inline Comments Standard

Every example must have:
1. File-level comment explaining what the example demonstrates
2. Step-by-step inline comments for each logical block
3. Comment explaining expected output

Example of the comment standard applied to an existing file:

```java
/**
 * Demonstrates sending a fire-and-forget event to a KubeMQ channel.
 *
 * Prerequisites:
 * - KubeMQ server running on localhost:50000
 * - No channel pre-creation required (channels are created on first publish)
 */
public class SendEventMessageExample {
    public static void main(String[] args) {
        // Step 1: Create a PubSub client connected to the KubeMQ server
        PubSubClient client = PubSubClient.builder()
            .address("localhost:50000")
            .clientId("events-sender")
            .build();

        // Step 2: Build an event message with channel, body, and metadata
        EventMessage message = EventMessage.builder()
            .channel("notifications")
            .body("Hello from Java SDK".getBytes())
            .metadata("example-metadata")
            .build();

        // Step 3: Send the event (fire-and-forget -- no delivery confirmation)
        EventSendResult result = client.sendEventsMessage(message);
        System.out.println("Event sent successfully, ID: " + result.getId());

        // Step 4: Clean up -- close the client to release gRPC resources
        client.close();
    }
}
```

Audit all 46 existing examples and add missing inline comments. Estimated: 30-40 examples need comment additions.

#### 6.2.3 Missing Examples to Add

| Example | File | Priority |
|---------|------|----------|
| Queue stream upstream (dedicated) | `queues/QueueStreamUpstreamExample.java` | High |
| Queue stream downstream (dedicated) | `queues/QueueStreamDownstreamExample.java` | High |
| mTLS connection | `config/MtlsConnectionExample.java` | Medium |
| Custom timeouts | `config/CustomTimeoutsExample.java` | Medium |
| OpenTelemetry setup | `config/OpenTelemetryExample.java` | Medium (depends on REQ-OBS-1) |

#### 6.2.4 CI Compile Check

Add the examples module to the CI pipeline (depends on REQ-TEST-3). The `kubemq-java-example/` directory is a **standalone Maven project** (not a submodule in the parent POM). It has its own `pom.xml` that declares the SDK as a dependency. In CI, the SDK must be installed to the local Maven repository first, then examples are compiled separately:

```yaml
# In .github/workflows/ci.yml (from 04-testing-spec.md)
- name: Build SDK
  run: cd kubemq-java && mvn install -DskipTests
- name: Compile Examples
  run: cd kubemq-java-example && mvn compile
```

If examples fail to compile, the CI build must fail and block merge.

#### 6.2.5 Cookbook Repo Disposition

The stub repo at `github.com/kubemq-io/java-sdk-cookbook` should be either:
- **Option A (recommended):** Archive the repo and add a redirect notice pointing to the in-repo examples
- **Option B:** Populate it with the same examples (creates maintenance burden of two copies)

### 6.3 Acceptance Criteria Verification

| Criterion | Implementation | Verification |
|-----------|---------------|--------------|
| Every example self-contained and runnable | Existing examples verified; new examples follow same pattern | `mvn compile` + manual run of 3 representative examples |
| Inline comments explaining each step | Comment audit of all 46+ examples | Manual review |
| Examples directory has own README | `kubemq-java-example/README.md` per 6.2.1 | File exists with all examples listed |
| Examples tested in CI (compile check) | CI step per 6.2.4 | CI green |
| Examples compile in main CI, block merge | Same CI step with failure = blocked merge | PR with broken example is rejected |

---

## 7. REQ-DOC-5: Troubleshooting Guide

**Gap Status:** MISSING
**GS Reference:** 06-documentation.md, REQ-DOC-5
**Assessment Evidence:** 10.2.6 (score 1, "No troubleshooting guide. No common error documentation.")
**Effort:** M (1-2 days)

### 7.1 Current State

No troubleshooting guide exists. No common error documentation.

### 7.2 Target Design

Create `TROUBLESHOOTING.md` at the repository root with a minimum of 11 entries covering all GS-required issues. Each entry follows the mandated format.

> **Review R1 m-7:** Error messages in troubleshooting entries depend on REQ-ERR-1 from
> 01-error-handling-spec. Use provisional error messages (marked with `[provisional]`) during
> authoring. Update exact error text once REQ-ERR-1 is implemented. Example:
> `"ConnectionException [provisional]: Failed to connect to KubeMQ server"`

#### 7.2.1 File Location

`/TROUBLESHOOTING.md` (repo root, same level as `README.md`)

#### 7.2.2 Required Entries

Each entry must include: symptom, exact error message, cause, step-by-step solution, and code example where applicable. The error messages referenced below depend on the error hierarchy from `01-error-handling-spec.md`. Where the new error types are not yet implemented, use the current error messages from the SDK.

**Entry 1: Connection Refused / Timeout**

```markdown
## Problem: Cannot connect to KubeMQ server

**Error message:**
```
io.grpc.StatusRuntimeException: UNAVAILABLE: io exception
Channel to 'localhost:50000' not ready
```

**Cause:** The KubeMQ server is not running, the address is incorrect, or a firewall
is blocking the connection.

**Solution:**
1. Verify the KubeMQ server is running: `kubectl get pods -l app=kubemq`
2. Verify the address and port match the server configuration
3. Check for firewall rules blocking port 50000
4. If using Docker: `docker ps | grep kubemq`
5. Test connectivity: `telnet localhost 50000`

**Code example:**
```java
// Verify connection with ping before sending messages
try {
    ServerInfo info = client.ping();
    System.out.println("Connected to: " + info.getHost());
} catch (Exception e) {
    System.err.println("Connection failed: " + e.getMessage());
}
```
```

**Entry 2: Authentication Failed (Invalid Token)**

```markdown
## Problem: Authentication failed

**Error message:**
```
io.grpc.StatusRuntimeException: UNAUTHENTICATED: invalid token
```

**Cause:** The auth token is missing, expired, or invalid.

**Solution:**
1. Verify the token is set in the client builder: `.authToken("your-token")`
2. Check the token has not expired
3. Verify the token matches the server's configured authentication
4. Ensure the token does not contain trailing whitespace or newlines
```

**Entry 3: Authorization Denied (Insufficient Permissions)**

```markdown
## Problem: Authorization denied

**Error message:**
```
io.grpc.StatusRuntimeException: PERMISSION_DENIED: not allowed
```

**Cause:** The authenticated user does not have permission for the requested operation
or channel.

**Solution:**
1. Verify the user/token has access to the target channel
2. Check KubeMQ server ACL configuration
3. Ensure the operation (read/write) is permitted for this client
```

**Entry 4: Channel Not Found**

```markdown
## Problem: Channel not found

**Error message:**
```
io.grpc.StatusRuntimeException: NOT_FOUND: channel not found
```

**Cause:** The target channel does not exist and auto-creation is not applicable for
this operation (e.g., subscribe to a store channel that has never had a publisher).

**Solution:**
1. Verify the channel name is spelled correctly (case-sensitive)
2. Create the channel explicitly before subscribing:
   ```java
   client.createEventsStoreChannel("my-channel");
   ```
3. For Events (non-store), channels are created on first publish automatically
```

**Entry 5: Message Too Large**

```markdown
## Problem: Message exceeds size limit

**Error message:**
```
io.grpc.StatusRuntimeException: RESOURCE_EXHAUSTED: message too large
```

**Cause:** The message body exceeds the server's configured maximum message size
(default: 100MB) or the client's `maxReceiveSize`.

**Solution:**
1. Check message body size before sending
2. Increase server-side limit if needed
3. Increase client-side receive limit:
   ```java
   .maxReceiveSize(209715200) // 200MB
   ```
4. Consider splitting large payloads across multiple messages
```

**Entry 6: Timeout / Deadline Exceeded**

```markdown
## Problem: Operation timed out

**Error message:**
```
io.grpc.StatusRuntimeException: DEADLINE_EXCEEDED: deadline exceeded after Xs
```

**Cause:** The server did not respond within the configured timeout. This can happen
with commands/queries when the handler is slow, or during queue poll with short wait times.

**Solution:**
1. Increase the operation timeout:
   ```java
   CommandMessage.builder()
       .channel("my-channel")
       .body(data)
       .timeout(30000) // 30 seconds
       .build();
   ```
2. For queue polling, increase `pollWaitTimeoutInSeconds`
3. Check server-side processing time
4. Check network latency between client and server
```

**Entry 7: Rate Limiting / Throttling**

```markdown
## Problem: Rate limited by server

**Error message:**
```
io.grpc.StatusRuntimeException: RESOURCE_EXHAUSTED: rate limit exceeded
```

**Cause:** The client is sending messages faster than the server allows.

**Solution:**
1. Reduce send rate using application-level throttling
2. Use batch operations to group messages
3. Check server-side rate limit configuration
4. Distribute load across multiple channels or clients
```

**Entry 8: Internal Server Error**

```markdown
## Problem: Internal server error

**Error message:**
```
io.grpc.StatusRuntimeException: INTERNAL: internal server error
```

**Cause:** An unexpected error occurred on the KubeMQ server.

**Solution:**
1. Check KubeMQ server logs for details
2. Retry the operation -- internal errors are often transient
3. Verify the server version is compatible with the SDK version
4. If persistent, restart the KubeMQ server and file a bug report
```

**Entry 9: TLS Handshake Failure**

```markdown
## Problem: TLS handshake failed

**Error message:**
```
io.netty.handler.ssl.NotSslRecordException: not an SSL/TLS record
javax.net.ssl.SSLHandshakeException: PKIX path building failed
```

**Cause:** TLS is misconfigured. Common causes: certificate file path is wrong,
certificate is expired, CA cert does not match server cert, connecting with TLS
to a non-TLS server (or vice versa).

**Solution:**
1. Verify certificate file paths exist and are readable
2. Check certificate expiration: `openssl x509 -in cert.pem -noout -dates`
3. Verify the CA certificate matches the server's certificate issuer
4. If server does not use TLS, remove `.tls(true)` from client configuration
5. If server uses self-signed certs, provide the CA cert:
   ```java
   .tls(true)
   .tlsCaCertFile("/path/to/ca.pem")
   ```
```

**Entry 10: No Messages Received (Subscriber Not Getting Messages)**

```markdown
## Problem: Subscriber is not receiving messages

**Error message:** No error -- subscriber is silent.

**Cause:** Multiple possible causes: wrong channel name, subscriber started after
publisher, group name mismatch, or subscription callback error.

**Solution:**
1. Verify channel names match exactly between publisher and subscriber (case-sensitive)
2. For Events (non-store): subscriber must be running before publisher sends.
   Events are not persistent -- if no subscriber is active, the event is lost
3. For Events Store: use `StartFromFirst` or `StartAtSequence(1)` to replay
4. Check that the `group` parameter matches if using group subscriptions
5. Verify the `onReceiveEventCallback` is not throwing exceptions silently
6. Add an `onErrorCallback` to catch subscription errors:
   ```java
   .onErrorCallback(err -> System.err.println("Sub error: " + err.getMessage()))
   ```
```

**Entry 11: Queue Message Not Acknowledged**

```markdown
## Problem: Queue messages keep redelivering

**Error message:** No explicit error -- messages reappear in the queue.

**Cause:** Messages received from a queue were not acknowledged within the visibility
timeout, causing them to return to the queue for redelivery.

**Solution:**
1. Call `msg.ack()` after processing each message
2. Increase visibility timeout if processing takes longer:
   ```java
   QueuesPollRequest.builder()
       .channel("tasks")
       .pollMaxMessages(1)
       .pollWaitTimeoutInSeconds(10)
       .autoAckMessages(false)
       .build();
   // Process and acknowledge
   msg.ack();
   ```
3. Use `msg.reject()` to explicitly reject messages you cannot process
4. Check the dead letter queue for repeatedly failed messages
5. If processing is long, extend visibility with `msg.extendVisibility(seconds)`
```

### 7.3 Cross-References

Each troubleshooting entry should link to relevant sections:
- Connection issues link to README Configuration section
- Auth issues link to `config/AuthTokenExample.java` and auth documentation
- TLS issues link to `config/TLSConnectionExample.java`
- Error types link to Javadoc API reference (once published)

### 7.4 Dependency on Error Hierarchy

The exact error messages in the troubleshooting guide depend on the error types from `01-error-handling-spec.md`. Implementation strategy:

1. **Phase 1 (now):** Write entries using current gRPC error messages (`io.grpc.StatusRuntimeException: UNAVAILABLE: ...`)
2. **Phase 2 (after ERR-1 ships):** Update entries with SDK-wrapped error messages (`io.kubemq.sdk.exception.TransientException: Connection to kubemq-server:50000 failed...`)

### 7.5 Acceptance Criteria Verification

| Criterion | Implementation | Verification |
|-----------|---------------|--------------|
| Minimum 11 entries | 11 entries per 7.2.2 | Count entries |
| Each entry includes exact error message | Error message in code block per entry | Manual review |
| Solutions are actionable | Step-by-step numbered solutions | No entry says only "check your configuration" |
| Entries link to relevant sections | Cross-references per 7.3 | Link check |

---

## 8. REQ-DOC-6: CHANGELOG

**Gap Status:** MISSING
**GS Reference:** 06-documentation.md, REQ-DOC-6
**Assessment Evidence:** 10.4.5 (score 1, "No CHANGELOG.md. Changes tracked only in git commit messages.")
**Effort:** S (< 1 day)

### 8.1 Current State

No `CHANGELOG.md` exists. Changes are tracked only in git commit messages. Known releases from git history: v2.0.3, v2.1.0, v2.1.1.

### 8.2 Target Design

Create `CHANGELOG.md` at repo root following [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) format.

#### 8.2.1 File Content

```markdown
# Changelog

All notable changes to the KubeMQ Java SDK are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2.1.1] - 2026-XX-XX

### Changed
- Bumped version to 2.1.1
- Updated Maven Central publishing configuration

## [2.1.0] - 2026-XX-XX

### Added
- Extensive test suite for all messaging patterns
- Examples for TLS connections, authentication tokens, channel search, and event sourcing
- `RequestSender` functional interface for queue requests

### Changed
- Upgraded dependencies and Java version compatibility (Java 11+ minimum)

### Fixed
- Edge cases in queue message handling
- Event store subscription reliability

## [2.0.3] - 2025-XX-XX

### Added
- Initial v2 release
- Full support for Events, Events Store, Commands, Queries, and Queues
- gRPC transport with TLS support
- Token authentication
- Automatic reconnection
- 46 runnable examples

[Unreleased]: https://github.com/kubemq-io/kubemq-java-v2/compare/v2.1.1...HEAD
[2.1.1]: https://github.com/kubemq-io/kubemq-java-v2/compare/v2.1.0...v2.1.1
[2.1.0]: https://github.com/kubemq-io/kubemq-java-v2/compare/v2.0.3...v2.1.0
[2.0.3]: https://github.com/kubemq-io/kubemq-java-v2/releases/tag/v2.0.3
```

**Note:** Exact dates must be filled in from git tag dates. The entries above are reconstructed from commit messages:
- `3fc4fc0` -- "Bump version to 2.1.1 and update Maven Central publishing configuration"
- `ef1f759` -- "Add examples for TLS connections, authentication tokens, channel search, and event sourcing"
- `e1488ef` -- "Upgrade dependencies and Java version compatibility"
- `3c018e7` -- "Version 2.1.0 - Adding extensive testing and fixing some edge cases"
- `32b883a` -- "Add RequestSender functional interface for queue requests"

#### 8.2.2 Going-Forward Process

Every PR that introduces user-visible changes must update the `[Unreleased]` section. Categories to use:
- **Added** -- new features
- **Changed** -- changes in existing functionality
- **Deprecated** -- features that will be removed in future versions
- **Removed** -- features removed in this version
- **Fixed** -- bug fixes
- **Security** -- vulnerability fixes

**Breaking changes** must be prominently marked with a `**BREAKING:**` prefix:

```markdown
### Changed
- **BREAKING:** Renamed `sendEventsMessage` to `publishEvent` (see [Migration Guide](MIGRATION.md))
```

### 8.3 Acceptance Criteria Verification

| Criterion | Implementation | Verification |
|-----------|---------------|--------------|
| CHANGELOG.md exists in repo root | `/CHANGELOG.md` | File exists |
| Entries grouped by version and date | Keep a Changelog format | Manual review |
| Categories: Added/Changed/Deprecated/Removed/Fixed/Security | Per-version sections | Manual review |
| Breaking changes prominently marked | `**BREAKING:**` prefix | Grep for breaking changes |
| Each entry links to PR or commit | Version comparison links at bottom | Link check |

---

## 9. REQ-DOC-7: Migration Guide

**Gap Status:** MISSING
**GS Reference:** 06-documentation.md, REQ-DOC-7
**Assessment Evidence:** 10.2.4 (score 1, "No migration guide from v1 to v2.")
**Effort:** M (1-2 days -- requires understanding v1 API to document differences)

### 9.1 Current State

No migration guide exists. The repository is named `kubemq-java-v2`, implying a v1 existed. The v1 SDK was a separate repository (`kubemq-io/kubemq-Java`). No documentation exists describing the differences or upgrade path.

### 9.2 Target Design

Create `MIGRATION.md` at repo root.

#### 9.2.1 File Structure

```markdown
# Migration Guide

## Migrating from v1 to v2

This guide covers breaking changes and the upgrade procedure for migrating from the
KubeMQ Java SDK v1 (`io.kubemq:kubemq-java-sdk`) to v2 (`io.kubemq.sdk:kubemq-sdk-Java`).

### Breaking Changes Summary

| Area | v1 | v2 | Action Required |
|------|----|----|-----------------|
| Maven Coordinates | `io.kubemq:kubemq-java-sdk` | `io.kubemq.sdk:kubemq-sdk-Java` | Update pom.xml |
| Package Imports | `io.kubemq.sdk.*` (v1 packages) | `io.kubemq.sdk.client.*`, `io.kubemq.sdk.pubsub.*`, `io.kubemq.sdk.queues.*`, `io.kubemq.sdk.cq.*` | Update imports |
| Client Creation | Constructor-based | Builder pattern | Rewrite client init |
| Events API | `Channel.SendEvent()` | `PubSubClient.sendEventsMessage()` | Rename methods |
| Queues API | `Queue.SendQueueMessage()` | `QueuesClient.sendQueuesMessage()` | Rename methods |
| CQ API | `Command.Send()` / `Query.Send()` | `CQClient.sendCommandRequest()` / `CQClient.sendQueryRequest()` | Rename methods |
| Connection | Manual channel management | Auto-reconnection built-in | Remove manual reconnect code |
| Java Version | Java 8+ | Java 11+ | Upgrade JDK if needed |

### Step-by-Step Upgrade Procedure

#### Step 1: Update Maven Dependency

**Before (v1):**
```xml
<dependency>
    <groupId>io.kubemq</groupId>
    <artifactId>kubemq-java-sdk</artifactId>
    <version>1.x.x</version>
</dependency>
```

**After (v2):**
```xml
<dependency>
    <groupId>io.kubemq.sdk</groupId>
    <artifactId>kubemq-sdk-Java</artifactId>
    <version>2.1.1</version>
</dependency>
```

#### Step 2: Update Client Initialization

**Before (v1):**
```java
// v1: Constructor-based, separate client per pattern
io.kubemq.sdk.event.Channel eventChannel = new io.kubemq.sdk.event.Channel(
    "my-channel", "client-id", false, serverAddress, null);
```

**After (v2):**
```java
// v2: Builder pattern, dedicated client classes
PubSubClient client = PubSubClient.builder()
    .address("localhost:50000")
    .clientId("client-id")
    .build();
```

#### Step 3: Update Event Operations

**Before (v1):**
```java
io.kubemq.sdk.event.Event event = new io.kubemq.sdk.event.Event();
event.setBody("Hello".getBytes());
event.setChannel("my-channel");
event.setClientID("client-id");
eventChannel.SendEvent(event);
```

**After (v2):**
```java
client.sendEventsMessage(EventMessage.builder()
    .channel("my-channel")
    .body("Hello".getBytes())
    .build());
```

#### Step 4: Update Queue Operations

{Similar before/after for queue send, receive, ack}

#### Step 5: Update Command/Query Operations

{Similar before/after for CQ operations}

#### Step 6: Update Error Handling

**Before (v1):**
```java
try {
    // v1: Generic ServerException
} catch (ServerException e) {
    // Limited error information
}
```

**After (v2):**
```java
try {
    // v2: Typed exceptions with structured context
} catch (GRPCException e) {
    // gRPC-specific error details
} catch (RuntimeException e) {
    // Other SDK errors
}
```

#### Step 7: Update Subscription Handling

{Before/after for event subscription, showing callback-based approach}

#### Step 8: Remove Manual Reconnection Code

v2 handles reconnection automatically. Remove any custom reconnection logic.

### Removed Features

| Feature | v1 | v2 | Alternative |
|---------|----|----|-------------|
| REST transport | Supported | Removed (gRPC only) | Use gRPC (default) |
| Manual channel management | Required | Automatic | Client handles channels |

### New Features in v2

| Feature | Description |
|---------|-------------|
| Builder pattern | Type-safe configuration with `.builder()` |
| Auto-reconnection | Built-in reconnection with configurable interval |
| TLS support | Native TLS/mTLS support via builder |
| Dead Letter Queue | Built-in DLQ support for queues |
| Delayed messages | Queue messages with delivery delay |
| Visibility timeout | Processing time window for queue messages |
| Group subscriptions | Load-balanced subscription groups |
| Batch operations | Send multiple queue messages at once |
```

**Note:** The exact v1 API details must be verified against the v1 repository (`kubemq-io/kubemq-Java`). The above is based on common patterns in KubeMQ v1 SDKs across languages.

#### 9.2.2 Cross-Links

The migration guide must be linked from:
- `CHANGELOG.md` -- in the v2.0.3 entry: "See [Migration Guide](MIGRATION.md) for upgrading from v1"
- `README.md` -- in a "Migrating from v1?" note near the top

### 9.3 Acceptance Criteria Verification

| Criterion | Implementation | Verification |
|-----------|---------------|--------------|
| Migration guide exists for v1 -> v2 | `/MIGRATION.md` | File exists |
| Every breaking change has before/after code | Breaking Changes Summary table + Step-by-Step sections | Manual review: each row has code examples |
| Linked from CHANGELOG and README | Cross-links per 9.2.2 | Link check |

---

## 10. Cross-Category Dependencies

### 10.1 Inbound (this spec depends on)

| Dependency | From Spec | REQ-DOC Items Affected | Blocking? |
|-----------|-----------|----------------------|-----------|
| REQ-ERR-1 (typed error hierarchy) | `01-error-handling-spec.md` | DOC-2 (error handling section), DOC-5 (troubleshooting error messages) | No -- use current error messages, update later |
| REQ-ERR-2 (error classification) | `01-error-handling-spec.md` | DOC-2 (retryable vs non-retryable table) | No -- use placeholder table, update later |
| REQ-AUTH-1/2/3 (auth & TLS) | `03-auth-security-spec.md` | DOC-2 (configuration table), DOC-5 (TLS/auth troubleshooting) | No -- document current behavior |
| REQ-TEST-3 (CI pipeline) | `04-testing-spec.md` (not yet written) | DOC-2 (CI badge), DOC-4 (examples compile check) | Partially -- CI badge is placeholder; examples compile check deferred until CI exists |
| REQ-OBS-1 (OpenTelemetry) | `05-observability-spec.md` (not yet written) | DOC-4 (OTel example) | Yes for OTel example only -- defer this example |

### 10.2 Outbound (other specs depend on this)

| Dependency | To Spec | Reason |
|-----------|---------|--------|
| REQ-DOC-1 (Javadoc) | All specs | Javadoc on all public APIs documents behavior defined by other specs |
| REQ-DOC-5 (troubleshooting) | User-facing | Documents error messages from `01-error-handling-spec.md` |
| REQ-DOC-6 (CHANGELOG) | `11-packaging-spec.md` | REQ-PKG-2 (SemVer) and REQ-PKG-4 (release notes) reference CHANGELOG |
| REQ-DOC-7 (migration guide) | `12-compatibility-lifecycle-spec.md` | REQ-COMPAT-4 (deprecation) references migration documentation |

### 10.3 Stub Strategy

Since most documentation dependencies are non-blocking (documents can reference current behavior and be updated later), the strategy is:

1. Write all documentation against the **current SDK behavior** (v2.1.1)
2. Mark sections that will change with `<!-- TODO: Update when REQ-ERR-1 ships -->` comments
3. After each dependent spec is implemented, update the affected documentation sections

This avoids blocking documentation work on code changes.

---

## 11. Breaking Changes

Documentation changes introduce **zero breaking changes** to the SDK's public API. All changes are additive (new files) or restructuring (README).

The only user-visible change is the README restructuring (Section 4), which shortens it from 1900 to 300-500 lines. Detailed per-pattern API documentation currently in the README should be preserved in a `docs/` directory or referenced via Javadoc links so users who bookmarked specific sections are not left without documentation.

**Recommendation:** Before restructuring the README, add a note at the top of the old README content (moved to `docs/API_REFERENCE.md` or similar) indicating it has moved, and keep a redirect note in the README for one release cycle.

---

## 12. Open Questions

| # | Question | Impact | Suggested Resolution |
|---|----------|--------|---------------------|
| 1 | Should the 1900-line README content be moved to `docs/` or deleted entirely? | DOC-2 | Move to `docs/API_REFERENCE.md` for one release, then deprecate once Javadoc is published |
| 2 | What are the exact v1 API names for the migration guide? | DOC-7 | Investigate `kubemq-io/kubemq-Java` repository before writing MIGRATION.md |
| 3 | What are the exact release dates for v2.0.3, v2.1.0, v2.1.1? | DOC-6 | Extract from git tags: `git log --tags --simplify-by-decoration --pretty="format:%ai %d"` |
| 4 | Should Checkstyle run only on `src/main` or also on test code? | DOC-1 | `src/main` only -- test code Javadoc is nice-to-have, not required |
| 5 | Should `lombok.config` be added at repo root or module root? | DOC-1 | Module root (`kubemq-java/lombok.config`) -- Lombok looks upward from source files |
| 6 | Is the `java-sdk-cookbook` repo owned by the same team? | DOC-4 | Verify ownership; if yes, archive it |
| 7 | Should the examples module be a multi-module build with the SDK? | DOC-4 | No -- keep separate. The examples module depends on the published SDK artifact, not source. This validates the published artifact works. |

---

## 13. Acceptance Checklist

Final checklist to verify all REQ-DOC items are met before closing this spec:

### REQ-DOC-1: Auto-Generated API Reference
- [ ] 100% of public types, methods, and constants have Javadoc comments
- [ ] `lombok.config` created with field-level Javadoc propagation
- [ ] `checkstyle.xml` created with Javadoc rules
- [ ] Maven Checkstyle Plugin added to `pom.xml` and runs in `validate` phase
- [ ] `mvn checkstyle:check` passes with zero Javadoc violations
- [ ] Maven Javadoc Plugin generates valid HTML documentation
- [ ] Javadoc is accessible at `javadoc.io/doc/io.kubemq.sdk/kubemq-sdk-Java` after release

### REQ-DOC-2: README
- [ ] All 10 required sections present in `README.md`
- [ ] Badges: Maven Central version, CI status, coverage, license, Javadoc
- [ ] Messaging Pattern Comparison Table included
- [ ] Configuration options consolidated into one table
- [ ] Error handling section with exception table and code example
- [ ] Troubleshooting top 5 with links to `TROUBLESHOOTING.md`
- [ ] Contributing link to `CONTRIBUTING.md`
- [ ] License section with link
- [ ] All links use absolute URLs (`https://github.com/kubemq-io/kubemq-java-v2/...`)
- [ ] README length 300-500 lines

### REQ-DOC-3: Quick Start
- [ ] Events quick start (prerequisites, send <= 10 lines, receive <= 10 lines, expected output)
- [ ] Queues quick start (same format)
- [ ] RPC quick start (same format)
- [ ] All quick starts work against `localhost:50000` with zero configuration
- [ ] No placeholder values in any quick start code

### REQ-DOC-4: Code Examples / Cookbook
- [ ] `kubemq-java-example/README.md` exists listing all examples with descriptions
- [ ] All 46+ examples have file-level Javadoc comment
- [ ] All examples have step-by-step inline comments
- [ ] Queue stream upstream dedicated example added
- [ ] Queue stream downstream dedicated example added
- [ ] mTLS example added
- [ ] Examples compile in CI (`mvn compile` in examples module)
- [ ] CI fails if examples do not compile

### REQ-DOC-5: Troubleshooting Guide
- [ ] `TROUBLESHOOTING.md` exists at repo root
- [ ] Minimum 11 entries covering all GS-required issues
- [ ] Each entry has: symptom, exact error message, cause, step-by-step solution
- [ ] Code examples included where applicable
- [ ] Cross-references to README, examples, and API reference

### REQ-DOC-6: CHANGELOG
- [ ] `CHANGELOG.md` exists at repo root
- [ ] Entries for v2.0.3, v2.1.0, v2.1.1
- [ ] Keep a Changelog format (Added/Changed/Deprecated/Removed/Fixed/Security)
- [ ] Breaking changes marked with `**BREAKING:**` prefix
- [ ] Version comparison links at bottom of file
- [ ] `[Unreleased]` section at top for ongoing changes

### REQ-DOC-7: Migration Guide
- [ ] `MIGRATION.md` exists at repo root
- [ ] Breaking changes summary table (v1 vs v2)
- [ ] Before/after code examples for: client creation, events, queues, commands/queries, subscriptions
- [ ] Step-by-step upgrade procedure (8 steps)
- [ ] New features in v2 table
- [ ] Linked from `CHANGELOG.md` (v2.0.3 entry)
- [ ] Linked from `README.md`

---

## Appendix A: Terminology

All documentation must use consistent terminology as defined in GS Appendix A:

| Term | Use | Do NOT Use |
|------|-----|------------|
| Channel | Named message destination | Topic, subject, routing key |
| Event | Fire-and-forget pub/sub message | Notification, signal |
| Event Store | Persistent event with replay | Durable event, persistent event |
| Queue | Pull-based message with ack | Job, task (in API docs) |
| Command | Request/reply with execute confirmation | Fire-and-forget request |
| Query | Request/reply with data response | RPC call, fetch |
| Subscription | Client registration to receive | Listener, consumer |
| Client | SDK instance connected to server | Connection, session |
| Message | Unit of data (channel + body + metadata + tags) | Payload, packet |
| Tags | Key-value string pairs on a message | Headers, attributes, properties |
| Client ID | Unique client identifier | Consumer ID, subscriber ID |
| Group | Load-balancing mechanism for subscribers | Consumer group, partition |
| Visibility Timeout | Duration message is hidden after receive | Lock timeout, processing window |

## Appendix B: File Inventory

Summary of all files created or modified by this spec:

| File | Action | REQ |
|------|--------|-----|
| `CHANGELOG.md` | Create | DOC-6 |
| `TROUBLESHOOTING.md` | Create | DOC-5 |
| `MIGRATION.md` | Create | DOC-7 |
| `README.md` | Restructure (major rewrite) | DOC-2, DOC-3 |
| `kubemq-java-example/README.md` | Create | DOC-4 |
| `kubemq-java/checkstyle.xml` | Create | DOC-1 |
| `kubemq-java/lombok.config` | Create | DOC-1 |
| `kubemq-java/pom.xml` | Modify (add Checkstyle plugin, update Javadoc plugin) | DOC-1 |
| `kubemq-java/src/main/java/io/kubemq/sdk/**/*.java` | Modify (add Javadoc to all 50 files) | DOC-1 |
| `kubemq-java-example/src/main/java/io/kubemq/example/**/*.java` | Modify (add inline comments to ~40 files) | DOC-4 |
| `kubemq-java-example/src/main/java/io/kubemq/example/queues/QueueStreamUpstreamExample.java` | Create | DOC-4 |
| `kubemq-java-example/src/main/java/io/kubemq/example/queues/QueueStreamDownstreamExample.java` | Create | DOC-4 |
| `kubemq-java-example/src/main/java/io/kubemq/example/config/MtlsConnectionExample.java` | Create | DOC-4 |
| `kubemq-java-example/src/main/java/io/kubemq/example/config/CustomTimeoutsExample.java` | Create | DOC-4 |
| `docs/API_REFERENCE.md` | Create (moved from README) | DOC-2 |

# Implementation Specification: Category 12 -- Compatibility, Lifecycle & Supply Chain

**SDK:** KubeMQ Java v2
**Category:** 12 -- Compatibility, Lifecycle & Supply Chain
**GS Source:** `clients/golden-standard/12-compatibility-lifecycle.md`
**Gap Research:** `clients/gap-research/java-gap-research.md` (lines 1587-1683)
**Assessment:** `clients/assesments/JAVA_ASSESSMENT_REPORT.md` (Category 12, lines 667-694)
**Current Score:** 1.80 / 5.0 | **Target:** 4.0+
**Tier:** 2 (Should-have) | **Weight:** 4%
**Priority:** P2-P3
**Total Estimated Effort:** 5-8 days

---

## Table of Contents

1. [Summary of REQ Items](#1-summary-of-req-items)
2. [Implementation Order](#2-implementation-order)
3. [REQ-COMPAT-1: Client-Server Compatibility Matrix](#3-req-compat-1-client-server-compatibility-matrix)
4. [REQ-COMPAT-2: Deprecation Policy](#4-req-compat-2-deprecation-policy)
5. [REQ-COMPAT-3: Language Version Support](#5-req-compat-3-language-version-support)
6. [REQ-COMPAT-4: Supply Chain Security](#6-req-compat-4-supply-chain-security)
7. [REQ-COMPAT-5: End-of-Life Policy](#7-req-compat-5-end-of-life-policy)
8. [Cross-Category Dependencies](#8-cross-category-dependencies)
9. [Breaking Changes](#9-breaking-changes)
10. [Open Questions](#10-open-questions)
11. [Verification Checklist](#11-verification-checklist)

---

## 1. Summary of REQ Items

| REQ | Status | Gap | Effort | Priority | Impl Order |
|-----|--------|-----|--------|----------|------------|
| REQ-COMPAT-1 | MISSING | No compatibility matrix, no server version check | M (2-3 days) | P2 | 1 |
| REQ-COMPAT-2 | MISSING | No deprecation policy, no `@Deprecated` annotations | S (< 1 day) | P3 | 3 |
| REQ-COMPAT-3 | PARTIAL | README says JDK 8 but pom.xml targets 11; no multi-version CI | M (1-2 days) | P2 | 2 |
| REQ-COMPAT-4 | MISSING | No Dependabot, no SBOM, no dependency audit | S-M (1-2 days) | P2 | 4 |
| REQ-COMPAT-5 | MISSING | No EOL policy documented | S (< 0.5 day) | P3 | 5 |

**Assessment Evidence (from JAVA_ASSESSMENT_REPORT.md Category 12):**

| Sub-criterion | Score | Evidence |
|---------------|-------|----------|
| 12.1.1 Server version matrix | 1 | No server compatibility documentation |
| 12.1.2 Runtime support matrix | 2 | README says "JDK 8 or higher"; pom.xml targets Java 11 |
| 12.1.3 Deprecation policy | 1 | No @Deprecated annotations; no deprecation process |
| 12.1.4 Backward compatibility | 3 | API stable across 2.0.3 to 2.1.1; no formal policy |
| 12.2.1 Signed releases | 3 | Maven GPG Plugin configured; no signed git tags |
| 12.2.2 Reproducible builds | 2 | No lock file; protobuf compiler downloaded at build time |
| 12.2.3 Dependency update process | 1 | No Dependabot or Renovate |
| 12.2.4 Security response process | 1 | No SECURITY.md |
| 12.2.5 SBOM | 1 | No CycloneDX or SPDX plugin |
| 12.2.6 Maintainer health | 2 | Single maintainer; no community process |

---

## 2. Implementation Order

```
REQ-COMPAT-3 (fix README, CI matrix) ─── no deps, quick correctness fix
       │
REQ-COMPAT-1 (compatibility matrix + version check) ─── depends on COMPAT-3 (knowing actual min version)
       │
REQ-COMPAT-2 (deprecation policy) ─── no code deps, policy doc
       │
REQ-COMPAT-4 (supply chain) ─── depends on CI pipeline (REQ-TEST-3)
       │
REQ-COMPAT-5 (EOL policy) ─── no deps, pure documentation
```

**Rationale:** Fix the README/pom.xml inconsistency first (COMPAT-3) since the compatibility matrix (COMPAT-1) depends on knowing the actual minimum Java version. Supply chain tooling (COMPAT-4) depends on CI being in place (REQ-TEST-3 from Category 4).

---

## 3. REQ-COMPAT-1: Client-Server Compatibility Matrix

**GS Requirement:** A published matrix showing which SDK versions are compatible with which KubeMQ server versions. SDK validates server version on connection and warns if outside tested range.

**Current State:** MISSING. Assessment 12.1.1 (score 1) confirms no compatibility documentation. `ping()` returns `ServerInfo` with a `version` field but no comparison logic exists.

### 3.1 Acceptance Criteria

| # | Criterion | Current Status | Target |
|---|-----------|---------------|--------|
| 1 | Compatibility matrix maintained in SDK repo or central docs | MISSING | COMPLIANT |
| 2 | Matrix updated when SDK/server versions add features | MISSING | COMPLIANT |
| 3 | SDK validates server version on connection and warns if incompatible | MISSING | COMPLIANT |
| 4 | SDK logs warning if server version outside tested range; connection proceeds | MISSING | COMPLIANT |

### 3.2 Implementation: COMPATIBILITY.md

Create file `COMPATIBILITY.md` at repository root with the following structure:

```markdown
# KubeMQ Java SDK Compatibility Matrix

## SDK Version vs. KubeMQ Server Version

| SDK Version | Server 2.0.x | Server 2.1.x | Server 2.2.x+ | Notes |
|-------------|-------------|-------------|---------------|-------|
| 2.0.x       | Tested      | Tested      | Not tested    | `MAX_SERVER_VERSION` covers up to 2.2.99 for forward-compat |
| 2.1.x       | Tested      | Tested      | Not tested    | Server 2.2.x may work (within version range) but is not CI-tested |

## Java Runtime Compatibility

| SDK Version | Java 11 (LTS) | Java 17 (LTS) | Java 21 (LTS) |
|-------------|---------------|----------------|----------------|
| 2.1.x       | Tested (CI)   | Tested (CI)    | Tested (CI)    |

## gRPC Protocol Compatibility

| SDK Version | gRPC Version | Protobuf Version | Notes |
|-------------|-------------|-----------------|-------|
| 2.1.1       | 1.75.0      | 4.28.2          |       |

## How to Read This Matrix

- **Tested**: This combination is tested in CI and supported.
- **Not tested**: May work but is not part of the CI matrix. Use at your own risk.
- **Incompatible**: Known incompatibility. Do not use this combination.
```

**Location:** `/COMPATIBILITY.md` (repository root)

**README Update:** Add a "Compatibility" section to `README.md` linking to `COMPATIBILITY.md`:

```markdown
## Compatibility

See [COMPATIBILITY.md](COMPATIBILITY.md) for the full compatibility matrix covering:
- SDK version vs. KubeMQ server version
- Supported Java runtime versions
- gRPC/Protobuf protocol versions
```

### 3.3 Implementation: Server Version Check on Connection

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/common/CompatibilityConfig.java` (new)

```java
package io.kubemq.sdk.common;

import io.kubemq.sdk.common.KubeMQVersion;

/**
 * Holds the compatible server version range for this SDK version.
 * Updated when new SDK versions are released.
 *
 * <p><b>Dependency:</b> This class depends on {@link KubeMQVersion} (from PKG-2, spec 11)
 * for the SDK version constant. PKG-2 must be implemented before COMPAT-1.</p>
 */
public final class CompatibilityConfig {

    private CompatibilityConfig() {
        // Utility class
    }

    /**
     * SDK version, delegated to {@link KubeMQVersion#getVersion()} to avoid
     * maintaining a duplicate hardcoded constant. Requires PKG-2 (spec 11)
     * to be implemented first.
     *
     * @see io.kubemq.sdk.common.KubeMQVersion#getVersion()
     */
    public static final String SDK_VERSION = KubeMQVersion.getVersion();

    /** Minimum KubeMQ server version tested with this SDK (inclusive). */
    public static final String MIN_SERVER_VERSION = "2.0.0";

    /** Maximum KubeMQ server version tested with this SDK (inclusive). */
    public static final String MAX_SERVER_VERSION = "2.2.99";

    /**
     * Checks whether the given server version falls within the tested range.
     *
     * @param serverVersion the version string from ServerInfo (e.g., "2.1.0")
     * @return true if the version is within the tested range, false otherwise
     */
    public static boolean isCompatible(String serverVersion) {
        if (serverVersion == null || serverVersion.isEmpty()) {
            return false; // Unknown version -- treat as outside range
        }
        try {
            int[] sv = parseVersion(serverVersion);
            int[] minV = parseVersion(MIN_SERVER_VERSION);
            int[] maxV = parseVersion(MAX_SERVER_VERSION);
            return compareVersions(sv, minV) >= 0 && compareVersions(sv, maxV) <= 0;
        } catch (NumberFormatException e) {
            return false; // Unparseable version -- treat as outside range
        }
    }

    /**
     * Parses a semver-like string "X.Y.Z" into int[3].
     * Tolerates versions with only major or major.minor components.
     *
     * <p><b>Pre-release handling:</b> Pre-release suffixes (e.g., "-beta", "-rc.1")
     * are stripped before parsing. This means pre-release versions are compared
     * by their base version only (e.g., "2.1.0-beta" is treated as "2.1.0").
     * Per SemVer specification, pre-release versions have lower precedence than
     * the associated release, but this method does not enforce that distinction.
     * Users running a pre-release server version will see the same compatibility
     * result as the corresponding release version.</p>
     */
    static int[] parseVersion(String version) {
        // Strip any leading 'v' prefix
        String v = version.startsWith("v") ? version.substring(1) : version;
        // Strip any pre-release suffix (e.g., "-beta")
        int dashIdx = v.indexOf('-');
        if (dashIdx > 0) {
            v = v.substring(0, dashIdx);
        }
        String[] parts = v.split("\\.");
        int[] result = new int[3];
        for (int i = 0; i < Math.min(parts.length, 3); i++) {
            result[i] = Integer.parseInt(parts[i].trim());
        }
        return result;
    }

    /**
     * Compares two parsed version arrays.
     * @return negative if a < b, 0 if equal, positive if a > b
     */
    static int compareVersions(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) {
                return Integer.compare(a[i], b[i]);
            }
        }
        return 0;
    }
}
```

**Integration point:** After successful connection is established in `KubeMQClient`, perform a non-blocking version check. This MUST NOT prevent connection establishment.

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java` -- modify the connection initialization path.

Add a method to `KubeMQClient`:

```java
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
                log.warn(
                    "KubeMQ server version {} is outside the tested compatibility range " +
                    "[{} - {}] for SDK version {}. The connection will proceed, but " +
                    "some features may not work as expected. See COMPATIBILITY.md for details.",
                    info.getVersion(),
                    CompatibilityConfig.MIN_SERVER_VERSION,
                    CompatibilityConfig.MAX_SERVER_VERSION,
                    CompatibilityConfig.SDK_VERSION
                );
            } else {
                log.debug(
                    "Server compatibility check passed: server={}, SDK={}",
                    info.getVersion(),
                    CompatibilityConfig.SDK_VERSION
                );
            }
        }
    } catch (Exception e) {
        log.debug("Server compatibility check skipped: {}", e.getMessage());
        // Do NOT fail connection -- this is informational only
    }
}
```

**Call site:** Invoke `checkServerCompatibility()` after the gRPC channel is constructed, either:
- At the end of the `KubeMQClient` constructor (after channel creation), OR
- On the first operation (lazy check) to avoid slowing down client construction

**Recommended approach:** Lazy check on first operation using `AtomicBoolean` with `compareAndSet` to ensure thread safety when multiple threads call the first operation concurrently:

```java
private final AtomicBoolean compatibilityChecked = new AtomicBoolean(false);

/**
 * Guard method to invoke on first public operation.
 * Thread-safe: only the first thread to call this performs the check.
 */
private void checkCompatibilityOnce() {
    if (!compatibilityChecked.get()
            && compatibilityChecked.compareAndSet(false, true)) {
        checkServerCompatibility();
    }
}
```

Call `checkCompatibilityOnce()` at the beginning of `ping()`, `send*()`, or `subscribe*()` methods. This avoids adding latency to construction and handles cases where the server is not yet available at construction time.

**Integration point recommendation:** Rather than adding the call to every public method individually in `PubSubClient`, `CQClient`, and `QueuesClient`, add it to a shared entry point. If spec 02 is implemented first, place it in `ensureNotClosed()` (from spec 02) since that method is already called at the start of every public operation: `checkCompatibilityOnce(); ensureNotClosed();`. If spec 02 is not yet implemented, add a `preOperationCheck()` method to `KubeMQClient` that subclasses call at the beginning of each public method:

```java
protected void preOperationCheck() {
    checkCompatibilityOnce();
    // TODO: add ensureNotClosed() when spec 02 is implemented
}
```

### 3.4 Tests

**File:** `kubemq-java/src/test/java/io/kubemq/sdk/unit/common/CompatibilityConfigTest.java` (new)

| Test Case | Description |
|-----------|-------------|
| `parseVersion_standardSemver` | "2.1.0" parses to [2,1,0] |
| `parseVersion_withVPrefix` | "v2.1.0" parses to [2,1,0] |
| `parseVersion_withPreRelease` | "2.1.0-beta" parses to [2,1,0] |
| `parseVersion_majorOnly` | "2" parses to [2,0,0] |
| `parseVersion_majorMinorOnly` | "2.1" parses to [2,1,0] |
| `isCompatible_withinRange` | "2.1.0" returns true |
| `isCompatible_atMinBoundary` | MIN_SERVER_VERSION returns true |
| `isCompatible_atMaxBoundary` | MAX_SERVER_VERSION returns true |
| `isCompatible_belowRange` | "1.9.0" returns false |
| `isCompatible_aboveRange` | "3.0.0" returns false |
| `isCompatible_nullVersion` | null returns false |
| `isCompatible_emptyVersion` | "" returns false |
| `isCompatible_unparseable` | "abc" returns false |

### 3.5 Effort & Risk

- **Effort:** M (2-3 days) -- COMPATIBILITY.md creation, CompatibilityConfig class, integration, tests
- **Risk:** Low. The version check is purely informational (warn only). The main risk is parsing server version strings that don't follow semver -- mitigated by the `try/catch` in `isCompatible()`.
- **Maintenance:** `MIN_SERVER_VERSION` and `MAX_SERVER_VERSION` constants must be updated each release. Add to the release checklist.

---

## 4. REQ-COMPAT-2: Deprecation Policy

**GS Requirement:** APIs must be deprecated before removal with minimum notice. Java annotation: `@Deprecated(since="X.Y", forRemoval=true)` + `@deprecated` Javadoc tag naming the replacement.

**Current State:** MISSING. Assessment 12.1.3 (score 1) confirms no `@Deprecated` annotations anywhere in the codebase. No deprecation policy documented. No CHANGELOG.

### 4.1 Acceptance Criteria

| # | Criterion | Current Status | Target |
|---|-----------|---------------|--------|
| 1 | Deprecated APIs have language-appropriate annotations | MISSING | COMPLIANT |
| 2 | Deprecation notice includes replacement API name | MISSING | COMPLIANT |
| 3 | CHANGELOG entries document deprecations | MISSING | COMPLIANT |
| 4 | Removed APIs listed in migration guides | MISSING | COMPLIANT |

### 4.2 Implementation: Deprecation Policy Document

**Canonical owner:** The `CONTRIBUTING.md` file is defined by **spec 11 (REQ-PKG-4, Section 6.2)**. This spec adds the following deprecation policy section to that file:

```markdown
## Deprecation Policy

### Rules

1. **Annotation:** Every deprecated public API must carry:
   ```java
   @Deprecated(since = "X.Y", forRemoval = true)
   ```
   Plus a Javadoc `@deprecated` tag naming the replacement:
   ```java
   /**
    * @deprecated Since 2.2. Use {@link NewClass#newMethod()} instead.
    *             Scheduled for removal in 3.0.
    */
   ```

2. **Notice period:** Minimum 2 minor versions OR 6 months (whichever is longer) before removal.

3. **Functionality:** Deprecated APIs continue to function identically until removal. No behavior changes in deprecated code paths.

4. **CHANGELOG:** Every deprecation must have a CHANGELOG entry under a "Deprecated" section.

5. **Migration guide:** When a deprecated API is removed in a major version, the migration guide (MIGRATION.md) must list:
   - The removed API
   - The replacement API
   - A code example showing the migration

### Deprecation Lifecycle

```
v2.1: API introduced
v2.3: API deprecated (@Deprecated annotation + CHANGELOG entry)
v2.4: Deprecated API still works, warning in Javadoc
v3.0: API removed (listed in MIGRATION.md)
```
```

### 4.3 Implementation: Deprecation Annotation Pattern

When deprecating an API, apply this exact pattern:

```java
/**
 * Sends an event message to the specified channel.
 *
 * @param event the event message to send
 * @return the send result
 * @deprecated Since 2.3. Use {@link PubSubClient#publishEvent(EventMessage)} instead.
 *             Scheduled for removal in 3.0.
 */
@Deprecated(since = "2.3", forRemoval = true)
public EventSendResult sendEventsMessage(EventMessage event) {
    return publishEvent(event); // Delegate to replacement
}
```

**Note:** `@Deprecated(since=..., forRemoval=...)` requires Java 9+. Since the SDK targets Java 11, this is available.

### 4.4 CHANGELOG.md

**Canonical owner:** The `CHANGELOG.md` file is defined by **spec 11 (REQ-PKG-1, Section 3.2)**. This spec does NOT define a separate CHANGELOG. Instead, the following supplementary requirement applies:

**Additive requirement from REQ-COMPAT-2:** The CHANGELOG template must include a "Deprecated" section category for recording deprecations. When recording a deprecation in the CHANGELOG, use this template:

```markdown
### Deprecated
- `PubSubClient.sendEventsMessage()` -- use `PubSubClient.publishEvent()` instead. Removal planned for 3.0.
```

Additionally, the `[Unreleased]` section in the canonical CHANGELOG (spec 11) should include these items from this spec:
- Server compatibility check on connection (COMPAT-1)
- COMPATIBILITY.md with version matrix
- Deprecation policy in CONTRIBUTING.md

### 4.5 Implementation: MIGRATION.md

Create `MIGRATION.md` at repository root for v1-to-v2 migration:

```markdown
# Migration Guide

## Migrating from v1.x to v2.x

### Breaking Changes

| v1.x API | v2.x Replacement | Notes |
|----------|-----------------|-------|
| `io.kubemq.sdk.event.Channel` | `io.kubemq.sdk.pubsub.PubSubClient` | Builder pattern for configuration |
| `io.kubemq.sdk.commandquery.Channel` | `io.kubemq.sdk.cq.CQClient` | Builder pattern for configuration |
| `io.kubemq.sdk.queue.Queue` | `io.kubemq.sdk.queues.QueuesClient` | Stream-based queue API |

### Configuration Changes

v1.x used individual constructor parameters. v2.x uses builder pattern:

```java
// v1.x
Channel channel = new Channel("events.channel", "client-id", false, serverAddress);

// v2.x
PubSubClient client = PubSubClient.builder()
    .address("localhost:50000")
    .clientId("client-id")
    .build();
```
```

**Note:** The v1-to-v2 migration guide content should be expanded based on actual v1 API surface. If v1 source is not available, document only what is known from the v2 codebase and mark the guide as "best effort."

### 4.6 Currently No APIs to Deprecate

As of v2.1.1, there are no APIs identified for deprecation. The policy and infrastructure are established so that future deprecations follow a consistent process. When the gap-close work introduces replacement APIs (e.g., verb alignment from REQ-DX-3), the old method names will be deprecated using this policy.

### 4.7 Effort & Risk

- **Effort:** S (< 1 day) -- Policy document, CHANGELOG backfill, MIGRATION.md skeleton
- **Risk:** Low. This is documentation/process only. No code changes.
- **Dependency:** CONTRIBUTING.md creation (REQ-DOC-6 from `06-documentation-spec.md`). If CONTRIBUTING.md does not yet exist, create it as part of this work with just the deprecation policy section. Other sections will be added by REQ-DOC-6.

---

## 5. REQ-COMPAT-3: Language Version Support

**GS Requirement:** SDK must support Java LTS releases (11, 17, 21). CI tests against all three. Minimum version documented in README. Dropping support is a MAJOR bump.

**Current State:** PARTIAL. Assessment 12.1.2 (score 2) notes README states "JDK 8 or higher" while `pom.xml` targets Java 11 (`maven.compiler.source=11`, `maven.compiler.target=11`). No CI pipeline exists (assessment 9.3.4 score 1).

### 5.1 Acceptance Criteria

| # | Criterion | Current Status | Target |
|---|-----------|---------------|--------|
| 1 | Minimum language version documented in README | PARTIAL (inconsistent) | COMPLIANT |
| 2 | CI tests against Java 11, 17, 21 | MISSING | COMPLIANT |
| 3 | Dropping language version support treated as MAJOR bump | NOT_ASSESSED | COMPLIANT |

### 5.2 Implementation: Fix README

**File:** `README.md` -- change the prerequisites section.

**Current (line 151):**
```markdown
- Java Development Kit (JDK) 8 or higher
```

**Replace with:**
```markdown
- Java Development Kit (JDK) 11 or higher (LTS releases 11, 17, and 21 are tested in CI)
```

This aligns the README with the actual `pom.xml` configuration where `maven.compiler.source` and `maven.compiler.target` are both set to `11`.

### 5.3 CI Pipeline Requirements for Multi-Version Testing

**Canonical CI workflow owner:** The `ci.yml` workflow is owned by **spec 04 (REQ-TEST-3)**. This spec does NOT define a separate `ci.yml`. Instead, the following requirements from COMPAT-3 must be incorporated into the canonical CI workflow:

**Requirements from REQ-COMPAT-3 on the CI workflow:**
1. CI must include a Java version matrix: `java-version: [11, 17, 21]`
2. CI must use `fail-fast: false` so all three versions run even if one fails
3. CI must use `temurin` distribution (Eclipse Adoptium)
4. CI must use `cache: maven` for build caching
5. CI must upload test results as artifacts per Java version

**Key decisions:**
- **Distribution:** Temurin (Eclipse Adoptium) -- the most widely used free JDK distribution for CI
- **`fail-fast: false`:** All three versions run even if one fails, so you see the full compatibility picture
- **Cache:** `cache: maven` in setup-java caches `~/.m2/repository` to speed up builds
- **Java 11:** Minimum supported version, matching `pom.xml` source/target
- **Java 17:** Current enterprise LTS (most production deployments)
- **Java 21:** Latest LTS (September 2023)

### 5.4 Implementation: Version Support Policy

Add to README.md under a "Version Support" section:

```markdown
## Java Version Support

This SDK targets **Java 11** as the minimum supported version. The following Java LTS releases are tested in CI:

| Java Version | Status | Notes |
|-------------|--------|-------|
| Java 11     | Supported (minimum) | Compile target |
| Java 17     | Supported | Tested in CI |
| Java 21     | Supported | Tested in CI |

**Policy:** Dropping support for a Java version is a breaking change that requires a major version bump (e.g., 3.0.0).

The Java version matrix is reviewed annually when new LTS versions are released.
```

### 5.5 pom.xml Validation

The current `pom.xml` is correctly configured:

```xml
<properties>
    <maven.compiler.source>11</maven.compiler.source>
    <maven.compiler.target>11</maven.compiler.target>
</properties>
```

And the compiler plugin:

```xml
<configuration>
    <source>11</source>
    <target>11</target>
</configuration>
```

No `pom.xml` changes needed for COMPAT-3. The inconsistency is solely in the README.

### 5.6 Known Compatibility Considerations

| Java Version | Known Issues | Mitigation |
|-------------|-------------|------------|
| Java 11 | `javax.annotation-api` already in pom.xml as explicit dep | None needed |
| Java 17 | Stronger encapsulation; Lombok 1.18.36 is compatible | Verify Lombok works |
| Java 21 | Virtual threads available but not required | Future consideration |

**Lombok compatibility:** Lombok 1.18.36 (current) supports Java 11-21. No changes needed. If upgrading to a newer JDK in future, verify Lombok compatibility first.

**gRPC compatibility:** gRPC 1.75.0 supports Java 8+. No issues with Java 11-21.

### 5.7 Tests

No new unit tests needed for this requirement. Verification comes from the CI matrix itself -- if the build and all existing tests pass on Java 11, 17, and 21, the requirement is met.

**Integration test note:** The CI pipeline should run unit tests on all three Java versions. Integration tests (which require a running KubeMQ server) can run on a single Java version to reduce CI resource usage.

### 5.8 Effort & Risk

- **Effort:** M (1-2 days) -- README fix (trivial), CI pipeline creation (main effort), version policy documentation
- **Risk:** Medium. Multi-version CI may reveal compatibility issues that don't manifest on the current development JDK. Likely areas: Lombok annotation processing, gRPC reflection access, `javax.annotation` module visibility. These are expected to work but must be verified.
- **Dependency:** REQ-TEST-3 (CI pipeline) is the primary dependency. If the CI pipeline already exists when this is implemented, the effort reduces to adding the version matrix. If not, the CI pipeline must be created first.

---

## 6. REQ-COMPAT-4: Supply Chain Security

**GS Requirement:** Dependencies scanned for vulnerabilities. SBOM recommended. Direct dependencies audited and justified. No critical vulnerabilities at release time.

**Current State:** MISSING. Assessment 12.2.3 (score 1), 12.2.5 (score 1), and 9.3.5 (score 1) confirm no supply chain security measures.

### 6.1 Acceptance Criteria

| # | Criterion | Current Status | Target |
|---|-----------|---------------|--------|
| 1 | Dependencies scanned for vulnerabilities | MISSING | COMPLIANT |
| 2 | SBOM generated in CycloneDX or SPDX format (recommended) | MISSING | COMPLIANT |
| 3 | Direct dependencies audited and justified | MISSING | COMPLIANT |
| 4 | No critical vulnerabilities at release time | NOT_ASSESSED | COMPLIANT |

### 6.2 Implementation: Dependabot Configuration

**File:** `.github/dependabot.yml` (new, at the git repository root -- NOT inside `kubemq-java/`; GitHub only reads `.github/` from the repo root)

```yaml
version: 2
updates:
  - package-ecosystem: "maven"
    directory: "/kubemq-java"
    schedule:
      interval: "weekly"
      day: "monday"
    open-pull-requests-limit: 10
    labels:
      - "dependencies"
    commit-message:
      prefix: "deps"
    groups:
      grpc:
        patterns:
          - "io.grpc:*"
        update-types:
          - "minor"
          - "patch"
      protobuf:
        patterns:
          - "com.google.protobuf:*"
        update-types:
          - "minor"
          - "patch"
      testing:
        patterns:
          - "org.junit*"
          - "org.mockito*"
        update-types:
          - "minor"
          - "patch"

  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
    labels:
      - "dependencies"
      - "ci"
```

**Key decisions:**
- **Weekly schedule (Monday):** Batches updates weekly to reduce noise
- **Grouped updates:** gRPC, Protobuf, and testing dependencies are grouped to reduce PR count
- **GitHub Actions ecosystem:** Also monitors CI action versions for security
- **PR limit 10:** Prevents Dependabot from overwhelming the maintainer

### 6.3 Implementation: SBOM Generation

**File:** `kubemq-java/pom.xml` -- add CycloneDX Maven plugin.

Add to `<build><plugins>`:

```xml
<!-- CycloneDX SBOM Generation -->
<plugin>
    <groupId>org.cyclonedx</groupId>
    <artifactId>cyclonedx-maven-plugin</artifactId>
    <version>2.8.0</version> <!-- Verify latest stable on Maven Central at implementation time -->
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>makeAggregate</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <projectType>library</projectType>
        <schemaVersion>1.5</schemaVersion>
        <includeBomSerialNumber>true</includeBomSerialNumber>
        <includeCompileScope>true</includeCompileScope>
        <includeProvidedScope>true</includeProvidedScope>
        <includeRuntimeScope>true</includeRuntimeScope>
        <includeSystemScope>false</includeSystemScope>
        <includeTestScope>false</includeTestScope>
        <outputName>kubemq-java-sdk-sbom</outputName>
        <outputFormat>json</outputFormat>
    </configuration>
</plugin>
```

**Output:** Running `mvn package` generates `target/kubemq-java-sdk-sbom.json` in CycloneDX format.

**Release integration:** The SBOM should be attached to GitHub Releases. Add to the release workflow (when created):

```yaml
- name: Generate SBOM
  run: mvn -B package -DskipTests -f kubemq-java/pom.xml

- name: Attach SBOM to release
  uses: softprops/action-gh-release@v2
  with:
    files: kubemq-java/target/kubemq-java-sdk-sbom.json
```

### 6.4 Implementation: Dependency Audit

Document the justification for each direct dependency in `pom.xml` as XML comments and in a dedicated section of `COMPATIBILITY.md`.

**pom.xml comments (add above each dependency):**

```xml
<!-- Core gRPC transport -- required for all server communication -->
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-netty-shaded</artifactId>
    <version>${grpc.version}</version>
</dependency>

<!-- gRPC ALTS authentication -- REVIEW: may be unnecessary if only TLS/mTLS
     is used. Consider replacing with grpc-auth if ALTS is not required.
     See https://grpc.io/docs/languages/java/alts/ -->
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-alts</artifactId>
    <version>${grpc.version}</version>
</dependency>

<!-- Protobuf serialization for KubeMQ protocol messages -->
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-protobuf</artifactId>
    <version>${grpc.version}</version>
</dependency>

<!-- gRPC stub generation -- required for client stubs -->
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-stub</artifactId>
    <version>${grpc.version}</version>
</dependency>

<!-- Protobuf runtime for message serialization/deserialization -->
<dependency>
    <groupId>com.google.protobuf</groupId>
    <artifactId>protobuf-java</artifactId>
    <version>${protobuf.version}</version>
</dependency>

<!-- String utilities (StringUtils) -- REVIEW: evaluate if usage is limited
     enough to inline and remove this dependency. Check for CVE exposure. -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-lang3</artifactId>
    <version>3.14.0</version>
</dependency>

<!-- Compile-time annotation processing (no runtime footprint) -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.36</version>
    <scope>provided</scope>
</dependency>

<!-- SLF4J logging backend -->
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.4.12</version>
</dependency>

<!-- javax.annotation for gRPC generated code (removed from JDK 11+) -->
<dependency>
    <groupId>javax.annotation</groupId>
    <artifactId>javax.annotation-api</artifactId>
    <version>1.3.2</version>
</dependency>

<!-- JSON serialization for channel list responses -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.17.0</version>
</dependency>
```

**Action items from audit (per R1 review m-8 and gap research):**

**Done-criteria for dependency audit:** Each dependency listed below has either (a) a justification comment in `pom.xml` explaining why it is needed, or (b) been removed in a separate PR. The audit is complete when all four rows below have a resolution recorded in the "Action" column.

| Dependency | Action | Priority | Rationale |
|-----------|--------|----------|-----------|
| `grpc-alts` | Evaluate removal | P3 | May be unnecessary if only TLS/mTLS is used. ALTS is Google Cloud-specific. Removing it reduces transitive dependency surface. |
| `commons-lang3` | Evaluate inlining | P3 | If only `StringUtils.isBlank()` / `StringUtils.isEmpty()` are used, inline the checks. Jackson has frequent CVEs, but commons-lang3 is low-risk. Still, fewer deps is better. |
| `jackson-databind` | Evaluate replacement | P3 | High CVE surface. If only used for channel list JSON parsing, `protobuf-java-util` (transitive via gRPC) provides `JsonFormat` as alternative. Investigate actual usage. |
| `logback-classic` | Keep (for now) | -- | Per REQ-OBS-5, will be replaced with pluggable logging interface. SLF4J API will remain; logback becomes optional. |

### 6.5 Implementation: Vulnerability Scanning in CI

Add OWASP Dependency-Check to the CI pipeline:

**Option A: Maven Plugin (recommended for simplicity)**

Add to `pom.xml` `<build><plugins>`:

```xml
<!-- OWASP Dependency-Check for vulnerability scanning -->
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>10.0.3</version>
    <configuration>
        <failBuildOnCVSS>7</failBuildOnCVSS> <!-- Fail on High and Critical (CVSS 7+) for release builds -->
        <suppressionFiles>
            <suppressionFile>dependency-check-suppression.xml</suppressionFile>
        </suppressionFiles>
    </configuration>
</plugin>
```

Run with: `mvn dependency-check:check`

**Option B: GitHub Actions workflow step**

```yaml
- name: OWASP Dependency Check
  uses: dependency-check/Dependency-Check_Action@main
  with:
    project: kubemq-java-sdk
    path: kubemq-java
    format: HTML
    args: --failOnCVSS 7
```

**Recommended:** Option A (Maven plugin) integrated into the CI pipeline from REQ-TEST-3. Run dependency check on the `main` branch push only (not on every PR) to avoid NVD rate limiting.

**Suppression file:** Create `kubemq-java/dependency-check-suppression.xml` for known false positives:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<suppressions xmlns="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd">
    <!-- Add false positive suppressions here with justification comments -->
</suppressions>
```

### 6.6 Implementation: Maven BOM for Version Management

Per R1 review recommendation about Maven BOM, update `pom.xml` to use BOM imports for consistent dependency version management:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.grpc</groupId>
            <artifactId>grpc-bom</artifactId>
            <version>${grpc.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Then remove explicit `<version>` tags from individual gRPC dependencies, relying on the BOM:

```xml
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-netty-shaded</artifactId>
    <!-- Version managed by grpc-bom -->
</dependency>
```

This prevents version conflicts between gRPC sub-modules and simplifies upgrades.

**Note:** When REQ-OBS-1 adds OpenTelemetry, also add the OTel BOM:

```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-bom</artifactId>
    <version>${opentelemetry.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

### 6.7 Effort & Risk

- **Effort:** S-M (1-2 days) -- Dependabot config (trivial), CycloneDX plugin (small), dependency audit comments (small), OWASP plugin (small), BOM refactor (small)
- **Risk:** Medium. Dependency scanning may surface existing vulnerabilities that require immediate attention (especially in jackson-databind and transitive gRPC dependencies). This is a feature, not a bug -- but it may generate unexpected follow-up work.
- **Dependency:** CI pipeline (REQ-TEST-3) must exist for Dependabot PRs to be tested automatically and for OWASP checks to run.

---

## 7. REQ-COMPAT-5: End-of-Life Policy

**GS Requirement:** Previous major version receives security patches for 12 months after next major GA. EOL status documented in README and clearly marked in repository.

**Current State:** MISSING. Assessment 12.2.6 (score 2) notes single maintainer. No EOL policy documented. The repository name `kubemq-java-v2` implies a v1 existed; its maintenance status is unknown (per R2 review m-7).

### 7.1 Acceptance Criteria

| # | Criterion | Current Status | Target |
|---|-----------|---------------|--------|
| 1 | EOL policy documented in SDK README | MISSING | COMPLIANT |
| 2 | Previous major versions receive security patches for 12 months | NOT_ASSESSED | COMPLIANT |
| 3 | EOL status clearly marked in repository | MISSING | COMPLIANT |

### 7.2 Implementation: Version Support Section in README

Add to `README.md`:

```markdown
## Version Support Policy

| Version | Status | Support Until |
|---------|--------|--------------|
| v2.x (current) | **Active** | Current major version; receives all updates |
| v1.x | **End-of-Life** | No longer maintained. Migrate to v2.x. |

### Policy

- The **current major version** receives all bug fixes, security patches, and new features.
- When a new major version reaches GA (e.g., v3.0), the previous major version (v2.x) enters **maintenance mode** and receives **security patches only** for **12 months**.
- After the 12-month maintenance window, the previous major version reaches **end-of-life** and receives no further updates.
- EOL status is marked in the repository README and release notes.

### Migration

See [MIGRATION.md](MIGRATION.md) for migration guides between major versions.
```

### 7.3 Implementation: v1 EOL Marking

**Investigation required:** Determine whether a separate `kubemq-java` (v1) repository exists on GitHub.

**If v1 repo exists:**
1. Add a banner to v1's README:
   ```markdown
   > **This SDK version is end-of-life.** No further updates will be released.
   > Please migrate to [kubemq-java-v2](https://github.com/kubemq-io/kubemq-java-v2).
   > See the [Migration Guide](https://github.com/kubemq-io/kubemq-java-v2/blob/main/MIGRATION.md).
   ```
2. Archive the v1 repository (GitHub Settings > Archive)
3. Update the v1 pom.xml description to include "[EOL]"

**If v1 was never a separate repo:**
- Document in the v2 README that v1 was an internal/pre-release version and is superseded by v2.

### 7.4 Effort & Risk

- **Effort:** S (< 0.5 day) -- README additions, investigate v1 repo, optional v1 archival
- **Risk:** Low. This is purely documentation. The only risk is if the 12-month security patch commitment creates an obligation that cannot be met with a single maintainer. The policy should be aspirational but honest -- if the team cannot commit to 12 months, adjust the window and document it.

---

## 8. Cross-Category Dependencies

| This Spec (COMPAT-*) | Depends On | Dependency Type | Notes |
|----------------------|-----------|----------------|-------|
| REQ-COMPAT-1 (version check) | REQ-PKG-2 (`KubeMQVersion`, spec 11) | Hard | `CompatibilityConfig.SDK_VERSION` delegates to `KubeMQVersion.getVersion()`. PKG-2 must be implemented first. |
| REQ-COMPAT-1 (version check) | `ServerInfo.version` field | Exists | `ping()` already returns version string |
| REQ-COMPAT-1 (version check) | Logging infrastructure | Soft | Uses current SLF4J; will adapt when REQ-OBS-5 adds KubeMQLogger |
| REQ-COMPAT-2 (deprecation policy) | REQ-DOC-6 (CONTRIBUTING.md) | Soft | Can create CONTRIBUTING.md stub if DOC-6 not done yet |
| REQ-COMPAT-2 (CHANGELOG) | REQ-PKG-2 / REQ-PKG-4 | Soft | CHANGELOG serves both compatibility and packaging requirements |
| REQ-COMPAT-3 (CI matrix) | REQ-TEST-3 (CI pipeline) | Hard | CI pipeline must exist before multi-version matrix can run |
| REQ-COMPAT-4 (Dependabot) | GitHub repository settings | Hard | Requires push access to `.github/` directory |
| REQ-COMPAT-4 (SBOM) | Release pipeline | Soft | SBOM can be generated locally; CI attachment is optional |
| REQ-COMPAT-4 (OWASP scan) | REQ-TEST-3 (CI pipeline) | Hard | Needs CI to run scans automatically |
| REQ-COMPAT-4 (Maven BOM) | None | -- | Standalone pom.xml refactor |

**Dependencies from other specs on this spec:**

| Other Spec | Depends on COMPAT-* | Notes |
|-----------|---------------------|-------|
| REQ-DX-3 (verb alignment) | REQ-COMPAT-2 (deprecation policy) | Old method names must follow deprecation process |
| REQ-DX-5 (message immutability) | REQ-COMPAT-2 (deprecation policy) | Setter removal is breaking; must follow deprecation path |
| REQ-PKG-2 (SemVer) | REQ-COMPAT-2 (deprecation policy) | Deprecation timeline aligns with SemVer rules |

---

## 9. Breaking Changes

**None.** All changes in this specification are additive:

| Change | Breaking? | Rationale |
|--------|-----------|-----------|
| New `COMPATIBILITY.md` file | No | New file |
| New `CHANGELOG.md` file | No | New file |
| New `MIGRATION.md` file | No | New file |
| New `CompatibilityConfig` class | No | New class, no changes to existing API |
| Server version check on connection | No | Warning only, connection proceeds |
| README content updates | No | Documentation only |
| Dependabot configuration | No | New file in `.github/` |
| CycloneDX plugin in pom.xml | No | New plugin, does not affect existing build |
| OWASP plugin in pom.xml | No | New plugin, opt-in execution |
| Maven BOM in pom.xml | No | Same dependency versions, different declaration style |
| Dependency audit comments in pom.xml | No | XML comments only |

The deprecation policy (COMPAT-2) establishes the *process* for future breaking changes but does not itself introduce any.

---

## 10. Open Questions

| # | Question | Context | Recommended Resolution |
|---|----------|---------|----------------------|
| 1 | What KubeMQ server versions has v2.1.1 actually been tested against? | COMPATIBILITY.md needs real data, not placeholder | Test against available server versions and populate matrix. Start with "2.x" as a range and narrow as testing confirms. |
| 2 | Does the `kubemq-java` (v1) repository exist on GitHub? | REQ-COMPAT-5 v1 EOL marking | Check `github.com/kubemq-io/kubemq-java`. If it exists, archive it with EOL banner. |
| 3 | Is `grpc-alts` actually used in any code path? | Dependency audit action item | Search codebase for `import io.grpc.alts` or `AltsChannelBuilder`. If unused, remove from pom.xml. |
| 4 | What `commons-lang3` classes are used? | Dependency audit action item | Search for `import org.apache.commons.lang3`. If only `StringUtils`, inline and remove. |
| 5 | What `jackson-databind` is used for? | Dependency audit action item | Search for `import com.fasterxml.jackson`. If only for channel list JSON, evaluate `protobuf-java-util` as replacement. |
| 6 | Should the version check be eager (in constructor) or lazy (first operation)? | REQ-COMPAT-1 implementation | Recommended: lazy. Avoids construction latency and handles servers not yet available. |
| 7 | Should OWASP scan run on every PR or only on main branch pushes? | CI resource usage | Recommended: main branch only. NVD rate limiting can cause flaky CI on PRs. |

---

## 11. Verification Checklist

When all items are implemented, verify:

### REQ-COMPAT-1: Client-Server Compatibility Matrix
- [ ] `COMPATIBILITY.md` exists at repository root with SDK-to-server version matrix
- [ ] `COMPATIBILITY.md` includes Java runtime version matrix
- [ ] `COMPATIBILITY.md` includes gRPC/Protobuf version information
- [ ] README links to `COMPATIBILITY.md`
- [ ] `CompatibilityConfig` class exists with `SDK_VERSION`, `MIN_SERVER_VERSION`, `MAX_SERVER_VERSION`
- [ ] `CompatibilityConfig.isCompatible()` correctly parses semver strings
- [ ] SDK logs WARNING when server version is outside tested range
- [ ] SDK does NOT fail/block connection when server version is outside range
- [ ] SDK logs DEBUG when server version is within range
- [ ] Version check handles null/empty/unparseable server versions gracefully
- [ ] Unit tests cover all `CompatibilityConfig` parsing edge cases

### REQ-COMPAT-2: Deprecation Policy
- [ ] Deprecation policy documented in `CONTRIBUTING.md`
- [ ] Policy specifies `@Deprecated(since=..., forRemoval=true)` + Javadoc `@deprecated` tag
- [ ] Policy specifies minimum 2 minor versions / 6 months notice
- [ ] `CHANGELOG.md` exists at repository root following Keep a Changelog format
- [ ] `CHANGELOG.md` has entries for v2.1.0 and v2.1.1
- [ ] `MIGRATION.md` exists with v1-to-v2 migration guidance (at least skeleton)
- [ ] CHANGELOG template includes "Deprecated" section

### REQ-COMPAT-3: Language Version Support
- [ ] README states "Java 11 or higher" (not "JDK 8 or higher")
- [ ] README lists Java 11, 17, 21 as tested versions
- [ ] CI pipeline includes `java-version: [11, 17, 21]` matrix
- [ ] CI uses `temurin` distribution
- [ ] CI uses `cache: maven` for build caching
- [ ] All existing tests pass on Java 11, 17, and 21
- [ ] Version support policy in README states dropping support requires MAJOR bump

### REQ-COMPAT-4: Supply Chain Security
- [ ] `.github/dependabot.yml` exists with Maven ecosystem configuration
- [ ] Dependabot groups gRPC, Protobuf, and testing dependencies
- [ ] Dependabot also monitors GitHub Actions versions
- [ ] `cyclonedx-maven-plugin` added to `pom.xml`
- [ ] `mvn package` generates SBOM in `target/`
- [ ] All direct dependencies in `pom.xml` have justification comments
- [ ] `grpc-alts` necessity evaluated (and either kept with justification or removed)
- [ ] `commons-lang3` usage evaluated (and either kept with justification or inlined)
- [ ] `jackson-databind` usage evaluated (and either kept with justification or replaced)
- [ ] OWASP dependency-check plugin added to `pom.xml` or CI
- [ ] No known critical (CVSS >= 9) vulnerabilities at release time
- [ ] Maven BOM used for gRPC dependency version management

### REQ-COMPAT-5: End-of-Life Policy
- [ ] Version support table in README with current version status
- [ ] EOL policy documented (12 months security patches after next major GA)
- [ ] v1 repository status investigated and documented
- [ ] If v1 repo exists: EOL banner added and repository archived

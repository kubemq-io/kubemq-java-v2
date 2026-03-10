# Implementation Specification: Category 11 -- Packaging & Distribution

**SDK:** KubeMQ Java v2
**Category:** 11 -- Packaging & Distribution
**GS Source:** `clients/golden-standard/11-packaging.md`
**Gap Research:** `clients/gap-research/java-gap-research.md` (lines 1511-1585)
**Current Score:** 3.30 / 5.0 | **Target:** 4.0+
**Priority:** P2-P3 (Tier 2)
**Total Estimated Effort:** 5-8 days

---

## Table of Contents

1. [Summary of REQ Items](#1-summary-of-req-items)
2. [Implementation Order](#2-implementation-order)
3. [REQ-PKG-1: Package Manager Publishing](#3-req-pkg-1-package-manager-publishing)
4. [REQ-PKG-2: Semantic Versioning](#4-req-pkg-2-semantic-versioning)
5. [REQ-PKG-3: Automated Release Pipeline](#5-req-pkg-3-automated-release-pipeline)
6. [REQ-PKG-4: Conventional Commits (Recommended)](#6-req-pkg-4-conventional-commits-recommended)
7. [Cross-Category Dependencies](#7-cross-category-dependencies)
8. [Breaking Changes](#8-breaking-changes)
9. [Test Plan](#9-test-plan)
10. [File Manifest](#10-file-manifest)

---

## 1. Summary of REQ Items

| REQ | Status | Gap | Effort | Impl Order |
|-----|--------|-----|--------|------------|
| REQ-PKG-1 | PARTIAL | CHANGELOG.md missing | S (< 1 day) | 1 |
| REQ-PKG-2 | PARTIAL | No runtime-queryable version constant | S (< 1 day) | 2 |
| REQ-PKG-3 | MISSING | No CI/CD release pipeline | M (2-3 days) | 4 |
| REQ-PKG-4 | MISSING | No CONTRIBUTING.md, no commit linting, no CHANGELOG | S-M (1-2 days) | 3 |

---

## 2. Implementation Order

**Phase 1 -- Quick Wins (days 1-2):**
1. REQ-PKG-1: Create `CHANGELOG.md` (also satisfies REQ-PKG-4 CHANGELOG criterion)
2. REQ-PKG-2: Add runtime version constant via Maven resource filtering
3. REQ-PKG-4: Create `CONTRIBUTING.md` with commit format guidance

**Phase 2 -- Release Pipeline (days 3-6):**
4. REQ-PKG-3: Create `.github/workflows/release.yml` (at git repo root) for automated Maven Central publishing
5. REQ-PKG-3: Add CI requirements to canonical `.github/workflows/ci.yml` from REQ-TEST-3 (spec 04)

**Rationale:** Phase 1 items have zero dependencies and deliver immediate value. Phase 2 (release pipeline) is the highest-impact item but requires GitHub Secrets configuration and coordination with repository administrators.

---

## 3. REQ-PKG-1: Package Manager Publishing

**Gap Status:** PARTIAL (assessment 11.1.1-11.1.3 score 4, 4, 4)
**GS Reference:** 11-packaging.md, REQ-PKG-1
**Assessment Evidence:** Maven Central publishing configured via `central-publishing-maven-plugin` (v0.7.0). GPG signing configured. GroupId `io.kubemq.sdk`, artifactId `kubemq-sdk-Java`. Package metadata (name, description, URL, license, SCM, developer info) present in pom.xml.

### 3.1 Compliant Criteria (No Changes Required)

| Criterion | Status | Evidence |
|-----------|--------|----------|
| Package published and installable via single command | COMPLIANT | Standard Maven dependency XML. `central-publishing-maven-plugin` configured with `autoPublish=true`, `waitUntil=published`. |
| Package metadata complete | COMPLIANT | pom.xml contains name, description, url, MIT license, SCM connection, developer info. |

### 3.2 Gap: CHANGELOG.md Missing

**Current State:** No `CHANGELOG.md` exists in the repository. Assessment 10.4.5 score 1 confirms absence.

**GS Requirement:** "Package includes README, LICENSE, and CHANGELOG."

#### Implementation

Create `kubemq-java/CHANGELOG.md` following the [Keep a Changelog](https://keepachangelog.com/) format. Cover versions retroactively from 2.0.0 through current 2.1.1.

**File: `kubemq-java/CHANGELOG.md`**

```markdown
# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2.1.1] - 2025-XX-XX

### Changed
- Bump version to 2.1.1
- Update Maven Central publishing configuration

## [2.1.0] - 2025-XX-XX

### Added
- Extensive unit and integration test suite (795 tests)
- TLS connection examples
- Authentication token examples
- Channel search examples
- Event sourcing examples
- `RequestSender` functional interface for queue requests

### Changed
- Upgrade dependencies and Java version compatibility
- Upgrade gRPC to 1.75.0, protobuf to 4.28.2

### Fixed
- Edge cases in queue handling
- Various stability improvements

## [2.0.3] - 2025-XX-XX

### Fixed
- Bug fixes and stability improvements

## [2.0.0] - 2025-XX-XX

### Added
- Complete rewrite of KubeMQ Java SDK (v2)
- Events pub/sub support
- Events Store persistent pub/sub support
- Queue stream upstream and downstream
- RPC Commands and Queries
- Builder pattern for all message types
- gRPC-based transport with connection management

[Unreleased]: https://github.com/kubemq-io/kubemq-java-v2/compare/v2.1.1...HEAD
[2.1.1]: https://github.com/kubemq-io/kubemq-java-v2/compare/v2.1.0...v2.1.1
[2.1.0]: https://github.com/kubemq-io/kubemq-java-v2/compare/v2.0.3...v2.1.0
[2.0.3]: https://github.com/kubemq-io/kubemq-java-v2/compare/v2.0.0...v2.0.3
[2.0.0]: https://github.com/kubemq-io/kubemq-java-v2/releases/tag/v2.0.0
```

**Notes:**
- Replace `XX-XX` dates with actual release dates from git tag history (`git log --tags --simplify-by-decoration --pretty="format:%ai %d"`)
- Populate entries from git log between each tag pair
- Future releases will be maintained manually or auto-generated from conventional commits (see REQ-PKG-4)

### 3.3 Minor pom.xml Improvements (Optional)

The current pom.xml `<url>` field is `http://maven.apache.org` which is a Maven template default, not the actual project URL.

**Current:**
```xml
<url>http://maven.apache.org</url>
```

**Change to:**
```xml
<url>https://github.com/kubemq-io/kubemq-java-v2</url>
```

This improves the metadata quality for Maven Central consumers. Assessment 11.1.2 gave score 4, but the URL field is technically incorrect.

---

## 4. REQ-PKG-2: Semantic Versioning

**Gap Status:** PARTIAL (assessment 11.2.1 score 4)
**GS Reference:** 11-packaging.md, REQ-PKG-2
**Assessment Evidence:** Versions 2.0.3, 2.1.0, 2.1.1 follow SemVer. Tags exist. No pre-release versions observed. No runtime-queryable version.

### 4.1 Compliant Criteria (No Changes Required)

| Criterion | Status | Evidence |
|-----------|--------|----------|
| Version numbers follow SemVer | COMPLIANT | v2.0.3, v2.1.0, v2.1.1 |
| Breaking changes only in MAJOR releases | COMPLIANT | No breaking changes in 2.x line |
| Pre-release versions clearly labeled | NOT_ASSESSED | No pre-release versions exist to evaluate |

### 4.2 Gap: No Runtime-Queryable Version

**Current State:** Version exists only in `pom.xml` (`<version>2.1.1</version>`). No `getVersion()` method or version constant exists in the SDK code. Users and observability systems (REQ-OBS-1 instrumentation scope) cannot query the SDK version at runtime.

**GS Requirement:** "Version is embedded in the package (queryable at runtime)."

#### Implementation

Use Maven resource filtering to inject the pom.xml version into a properties file at build time, then expose it via a static constant and method.

**Step 1: Create version properties template**

**File: `kubemq-java/src/main/resources/kubemq-sdk-version.properties`**

```properties
# Auto-generated by Maven resource filtering. Do not edit manually.
sdk.version=${project.version}
sdk.groupId=${project.groupId}
sdk.artifactId=${project.artifactId}
```

**Step 2: Enable Maven resource filtering**

Add to `kubemq-java/pom.xml` inside the `<build>` section, before `<plugins>`:

```xml
<resources>
    <resource>
        <directory>src/main/resources</directory>
        <filtering>true</filtering>
        <includes>
            <include>kubemq-sdk-version.properties</include>
        </includes>
    </resource>
    <resource>
        <directory>src/main/resources</directory>
        <filtering>false</filtering>
        <excludes>
            <exclude>kubemq-sdk-version.properties</exclude>
        </excludes>
    </resource>
</resources>
```

**Step 3: Create version accessor class**

**File: `kubemq-java/src/main/java/io/kubemq/sdk/common/KubeMQVersion.java`**

```java
package io.kubemq.sdk.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Provides the SDK version at runtime.
 *
 * <p>The version is injected from pom.xml at build time via Maven
 * resource filtering. This class reads the generated properties file
 * from the classpath.
 *
 * <p>Usage:
 * <pre>{@code
 * String version = KubeMQVersion.getVersion(); // e.g., "2.1.1"
 * }</pre>
 */
public final class KubeMQVersion {

    private static final String PROPERTIES_FILE = "kubemq-sdk-version.properties";
    private static final String UNKNOWN = "unknown";

    /**
     * The SDK version string, loaded once at class initialization.
     * Returns "unknown" if the version properties file cannot be read
     * (e.g., when running from source without a Maven build).
     */
    public static final String VERSION;

    /**
     * The SDK group ID (e.g., "io.kubemq.sdk").
     */
    public static final String GROUP_ID;

    /**
     * The SDK artifact ID (e.g., "kubemq-sdk-Java").
     */
    public static final String ARTIFACT_ID;

    static {
        Properties props = new Properties();
        try (InputStream is = KubeMQVersion.class.getClassLoader()
                .getResourceAsStream(PROPERTIES_FILE)) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            // Silently fall back to UNKNOWN. This should not happen
            // in a properly built artifact.
        }
        VERSION = props.getProperty("sdk.version", UNKNOWN);
        GROUP_ID = props.getProperty("sdk.groupId", UNKNOWN);
        ARTIFACT_ID = props.getProperty("sdk.artifactId", UNKNOWN);
    }

    private KubeMQVersion() {
        // Utility class, no instantiation.
    }

    /**
     * Returns the SDK version string (e.g., "2.1.1").
     *
     * @return the SDK version, or "unknown" if not available
     */
    public static String getVersion() {
        return VERSION;
    }
}
```

**Design Decisions:**
- **Properties file over hardcoded constant:** Prevents version drift. The properties file is populated at build time from the single source of truth (pom.xml).
- **Static initializer:** Loads once, thread-safe by JLS guarantees. No runtime overhead after first access.
- **Graceful fallback to "unknown":** Avoids exceptions if running from IDE without Maven build. Logged at DEBUG level if observability is configured.
- **Public `VERSION` constant:** Allows use in `switch` statements and annotation values where method calls are not permitted (e.g., `@Deprecated(since = KubeMQVersion.VERSION)` -- though this specific use is not practical since annotations require compile-time constants).

**Cross-Category Dependency (REQ-OBS-1):** The instrumentation scope for OpenTelemetry Tracer and Meter must use this version. When REQ-OBS-1 is implemented:
```java
Tracer tracer = GlobalOpenTelemetry.getTracer(
    "io.kubemq.sdk", KubeMQVersion.getVersion());
Meter meter = GlobalOpenTelemetry.getMeter(
    "io.kubemq.sdk", KubeMQVersion.getVersion());
```

### 4.3 Pre-Release Version Convention

Although no pre-release versions currently exist, document the convention for future use:

| Stage | Version Format | Maven Central? | Example |
|-------|---------------|----------------|---------|
| Alpha | `X.Y.Z-alpha.N` | No (SNAPSHOT or local) | `2.2.0-alpha.1` |
| Beta | `X.Y.Z-beta.N` | Optional | `2.2.0-beta.1` |
| Release Candidate | `X.Y.Z-rc.N` | Yes | `2.2.0-rc.1` |
| Release | `X.Y.Z` | Yes | `2.2.0` |

In Maven, use `-SNAPSHOT` suffix for development versions: `2.2.0-SNAPSHOT`. SNAPSHOT versions are never published to Maven Central (only to Sonatype snapshots repository if needed).

---

## 5. REQ-PKG-3: Automated Release Pipeline

**Gap Status:** MISSING (assessment 9.3.1 score 1)
**GS Reference:** 11-packaging.md, REQ-PKG-3
**Assessment Evidence:** No CI/CD pipeline exists. Maven Central publishing is manual. No GitHub Releases with content (11.2.3 score 1).

### 5.1 All Criteria Are MISSING

| Criterion | Status | Evidence |
|-----------|--------|----------|
| Release triggered by git tag or merge to release branch | MISSING | No CI pipeline |
| Publishing requires no manual steps after tagging | MISSING | Manual Maven Central publishing |
| GitHub Release created automatically with changelog | MISSING | Tags exist but no release descriptions (11.2.3 score 1) |
| Failed releases don't publish partial artifacts | NOT_ASSESSED | No pipeline to evaluate |

### 5.2 GitHub Actions Release Workflow

**File: `.github/workflows/release.yml`** (at the git repository root, NOT inside `kubemq-java/`; GitHub Actions only detects workflows at the repo root `.github/` directory; Maven steps use `working-directory: kubemq-java`)

```yaml
name: Release to Maven Central

on:
  push:
    tags:
      - 'v*'

permissions:
  contents: write  # Required for creating GitHub Releases

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Extract version from tag
        id: version
        run: |
          TAG_VERSION="${GITHUB_REF#refs/tags/v}"
          echo "version=${TAG_VERSION}" >> "$GITHUB_OUTPUT"

      - name: Verify pom.xml version matches tag
        run: |
          POM_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout -f kubemq-java/pom.xml)
          TAG_VERSION="${{ steps.version.outputs.version }}"
          if [ "$POM_VERSION" != "$TAG_VERSION" ]; then
            echo "ERROR: pom.xml version ($POM_VERSION) does not match tag version ($TAG_VERSION)"
            exit 1
          fi

      - name: Verify CHANGELOG entry exists
        run: |
          TAG_VERSION="${{ steps.version.outputs.version }}"
          if ! grep -q "\[${TAG_VERSION}\]" kubemq-java/CHANGELOG.md; then
            echo "ERROR: No CHANGELOG entry found for version ${TAG_VERSION}"
            exit 1
          fi

  build-and-test:
    needs: validate
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 11
        uses: actions/setup-java@v4
        with:
          java-version: '11'
          distribution: 'temurin'
          cache: 'maven'

      - name: Run unit tests
        working-directory: kubemq-java
        run: mvn verify -DskipITs

  publish:
    needs: build-and-test
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 11
        uses: actions/setup-java@v4
        with:
          java-version: '11'
          distribution: 'temurin'
          cache: 'maven'
          server-id: central
          server-username: MAVEN_USERNAME
          server-password: MAVEN_PASSWORD
          gpg-private-key: ${{ secrets.GPG_PRIVATE_KEY }}
          gpg-passphrase: GPG_PASSPHRASE

      - name: Publish to Maven Central
        working-directory: kubemq-java
        env:
          MAVEN_USERNAME: ${{ secrets.OSSRH_USERNAME }}
          MAVEN_PASSWORD: ${{ secrets.OSSRH_TOKEN }}
          GPG_PASSPHRASE: ${{ secrets.GPG_PASSPHRASE }}
        run: mvn deploy -DskipTests

      - name: Extract changelog for this version
        id: changelog
        run: |
          TAG_VERSION="${GITHUB_REF#refs/tags/v}"
          # Extract the section between this version and the previous version header
          CHANGELOG=$(awk "/## \[${TAG_VERSION}\]/{flag=1; next} /## \[/{flag=0} flag" kubemq-java/CHANGELOG.md)
          # Use GitHub Actions multiline output
          echo "body<<EOF" >> "$GITHUB_OUTPUT"
          echo "$CHANGELOG" >> "$GITHUB_OUTPUT"
          echo "EOF" >> "$GITHUB_OUTPUT"

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          name: "Release ${{ github.ref_name }}"
          body: ${{ steps.changelog.outputs.body }}
          draft: false
          prerelease: ${{ contains(github.ref, '-alpha') || contains(github.ref, '-beta') || contains(github.ref, '-rc') }}

  rollback-on-failure:
    needs: publish
    if: failure()
    runs-on: ubuntu-latest
    steps:
      - name: Notify failure
        run: |
          echo "::error::Release pipeline failed. Maven Central publishing may have partially failed."
          echo "::error::Check Sonatype staging repository for orphaned artifacts."
          echo "::error::Manual cleanup may be required at https://central.sonatype.com/"
```

### 5.3 CI Workflow Requirements (PR Validation)

**Canonical CI workflow owner:** The `ci.yml` workflow is owned by **spec 04 (REQ-TEST-3)**. This spec does NOT define a separate `ci.yml`. Instead, the following requirements must be incorporated into the canonical CI workflow from spec 04:

**Requirements from REQ-PKG-3 on the CI workflow:**
1. CI must run on push to `main` and on pull requests to `main`
2. CI must include a Java version matrix: `[11, 17, 21]` (shared requirement with COMPAT-3 from spec 12)
3. CI must use `temurin` distribution
4. CI must use `cache: maven` for build caching
5. CI must upload test results as artifacts (`actions/upload-artifact@v4` with `surefire-reports/`)
6. CI must run `mvn verify -DskipITs` (unit tests only; integration tests run separately)

### 5.4 Required GitHub Secrets

The following secrets must be configured in the repository settings (`Settings > Secrets and variables > Actions`):

| Secret Name | Description | Source |
|-------------|-------------|--------|
| `OSSRH_USERNAME` | Sonatype/Maven Central username | Sonatype account or token |
| `OSSRH_TOKEN` | Sonatype/Maven Central password/token | Sonatype account or token |
| `GPG_PRIVATE_KEY` | ASCII-armored GPG private key for artifact signing | `gpg --armor --export-secret-keys <key-id>` |
| `GPG_PASSPHRASE` | Passphrase for the GPG private key | GPG key passphrase |

**Note:** The current pom.xml uses `central-publishing-maven-plugin` (Sonatype Central Portal, not the legacy OSSRH Nexus). The `setup-java` action's `server-id: central` must match the pom.xml's `<publishingServerId>central</publishingServerId>`. If the repository currently uses the new Sonatype Central Portal, the secret names may differ -- the `OSSRH_USERNAME`/`OSSRH_TOKEN` naming is conventional but the actual values must match the Central Portal credentials.

### 5.5 Release Process (Developer Steps)

After the pipeline is in place, the release process becomes:

1. **Update version in pom.xml:** `mvn versions:set -DnewVersion=2.2.0`
2. **Update CHANGELOG.md:** Add entry under `## [2.2.0] - YYYY-MM-DD` with all changes
3. **Commit:** `git commit -s -S -m "chore: prepare release 2.2.0"`
4. **Tag:** `git tag -s v2.2.0 -m "Release v2.2.0"`
5. **Push:** `git push origin main --tags`
6. **Pipeline runs automatically:** validate -> build -> test -> publish -> GitHub Release

No manual Maven Central interaction required after step 5.

### 5.6 Failed Release Handling

The pipeline uses separate jobs (`validate`, `build-and-test`, `publish`) with `needs` dependencies. If any job fails:

- **Validate fails:** No build or publish occurs. Developer must fix version/CHANGELOG mismatch.
- **Build/test fails:** No publish occurs. Developer must fix the build.
- **Publish fails:** The `rollback-on-failure` job logs a notification. Maven Central's `central-publishing-maven-plugin` with `autoPublish=true` and `waitUntil=published` means the plugin itself will fail if publishing does not complete successfully. Partial artifacts in Sonatype staging can be manually dropped from the Central Portal UI.

**GS Criterion:** "Failed releases don't publish partial artifacts." The `central-publishing-maven-plugin` with `waitUntil=published` provides atomic publish semantics -- the deployment either fully publishes or fails without releasing partial artifacts to the public Maven Central repository.

---

## 6. REQ-PKG-4: Conventional Commits (Recommended)

**Gap Status:** MISSING
**GS Reference:** 11-packaging.md, REQ-PKG-4
**Assessment Evidence:** No CONTRIBUTING.md (11.3.4 score 1). Commit messages are freeform. No CHANGELOG.md.

**GS Note:** "SemVer (REQ-PKG-2) is the hard requirement; how the CHANGELOG is generated is an internal choice." and "Conventional Commits (Recommended)" -- this is a SHOULD, not a MUST.

### 6.1 Gap Analysis

| Criterion | Status | Evidence |
|-----------|--------|----------|
| Commit format documented in CONTRIBUTING.md | MISSING | No CONTRIBUTING.md |
| Commit linting configured | MISSING | No commitlint or equivalent |
| CHANGELOG maintained | MISSING | No CHANGELOG.md (covered by REQ-PKG-1) |

### 6.2 CONTRIBUTING.md

**File: `kubemq-java/CONTRIBUTING.md`**

```markdown
# Contributing to KubeMQ Java SDK

Thank you for your interest in contributing to the KubeMQ Java SDK.

## Development Setup

### Prerequisites
- JDK 11 or later
- Maven 3.8+
- A running KubeMQ server (for integration tests)

### Building
```bash
cd kubemq-java
mvn clean install -DskipITs
```

### Running Tests

Unit tests:
```bash
mvn test
```

Integration tests (requires running KubeMQ server):
```bash
mvn verify -Dkubemq.address=localhost:50000
```

## Commit Message Format

We recommend (but do not enforce) [Conventional Commits](https://www.conventionalcommits.org/) format:

```
type(scope): description

[optional body]

[optional footer(s)]
```

### Types
- `feat` -- New feature (MINOR version bump)
- `fix` -- Bug fix (PATCH version bump)
- `docs` -- Documentation changes
- `test` -- Adding or updating tests
- `chore` -- Build process, dependency updates
- `refactor` -- Code changes that neither fix a bug nor add a feature
- `perf` -- Performance improvements

### Scope (optional)
- `pubsub` -- Events and Events Store
- `queues` -- Queue operations
- `cq` -- Commands and Queries
- `client` -- KubeMQClient core
- `transport` -- gRPC/connection layer

### Examples
```
feat(queues): add batch send support for queue messages
fix(pubsub): handle reconnection during events store subscription
docs: update README with TLS configuration examples
chore: upgrade gRPC to 1.75.0
```

### Breaking Changes
Append `!` after type or include `BREAKING CHANGE:` in the footer:
```
feat(client)!: change KubeMQClient builder API

BREAKING CHANGE: `address()` method renamed to `serverAddress()`
```

## Pull Requests

1. Create a feature branch from `main`
2. Make your changes
3. Ensure all tests pass (`mvn verify -DskipITs`)
4. Submit a pull request with a clear description of changes

## Changelog

All user-visible changes must be recorded in `CHANGELOG.md` following the
[Keep a Changelog](https://keepachangelog.com/) format. Add your entry under the
`[Unreleased]` section.
```

### 6.3 Commit Linting (Optional -- Deferred)

The GS states conventional commits are "recommended, not required." Commit linting via `commitlint` requires Node.js tooling, which adds friction to a Java-only project.

**Recommendation:** Defer commit linting to a future iteration. For now, document the format in CONTRIBUTING.md (done above) and rely on PR review to maintain commit quality.

**If commit linting is desired later**, two approaches:

1. **CI-based (recommended for Java projects):** Add a GitHub Actions step that validates commit messages against the conventional commits regex pattern. No local toolchain dependency.

```yaml
# Add to .github/workflows/ci.yml
- name: Validate commit messages
  if: github.event_name == 'pull_request'
  run: |
    COMMITS=$(git log --format='%s' origin/main..HEAD)
    PATTERN='^(feat|fix|docs|test|chore|refactor|perf)(\(.+\))?!?: .+'
    echo "$COMMITS" | while read -r msg; do
      if ! echo "$msg" | grep -qE "$PATTERN"; then
        echo "::warning::Commit message does not follow conventional commits format: $msg"
      fi
    done
```

2. **Pre-commit hook (optional):** Add a `.githooks/commit-msg` script and document `git config core.hooksPath .githooks` in CONTRIBUTING.md.

---

## 7. Cross-Category Dependencies

| This Spec | Depends On | Nature of Dependency |
|-----------|-----------|---------------------|
| REQ-PKG-2 (runtime version) | REQ-OBS-1 (observability) | OTel instrumentation scope needs `KubeMQVersion.getVersion()` for Tracer/Meter creation. Implement PKG-2 first so OBS-1 can consume it. |
| REQ-PKG-2 (runtime version) | REQ-COMPAT-1 (compatibility, spec 12) | `CompatibilityConfig.SDK_VERSION` delegates to `KubeMQVersion.getVersion()`. PKG-2 must be implemented before COMPAT-1. |
| REQ-PKG-3 (release pipeline) | REQ-TEST-3 (CI pipeline, Category 04) | Spec 04 (REQ-TEST-3) owns the canonical `ci.yml`. This spec adds requirements (Java version matrix, test upload) to that workflow rather than defining a separate one. |
| REQ-PKG-1 (CHANGELOG) | REQ-DOC-6 (Category 06) | REQ-DOC-6 also requires CHANGELOG.md. A single CHANGELOG.md satisfies both requirements. |
| REQ-PKG-4 (CONTRIBUTING.md) | REQ-DOC-5 (Category 06) | REQ-DOC-5 may also require contributor documentation. CONTRIBUTING.md partially satisfies this. |

### Dependency on Other Specs

| Dependency | Impact | Resolution |
|-----------|--------|------------|
| REQ-ERR-1 (error hierarchy) | None. Packaging is independent of error handling. | No blocking dependency. |
| REQ-CQ-1 (layered architecture) | None. Packaging is independent of internal architecture. | No blocking dependency. |
| REQ-TEST-3 (CI) | Spec 04 (REQ-TEST-3) owns the canonical `ci.yml`. This spec specifies requirements only; no separate workflow file. |

---

## 8. Breaking Changes

**None.** All changes in this specification are additive:

- `CHANGELOG.md` -- new file
- `CONTRIBUTING.md` -- new file
- `KubeMQVersion.java` -- new class, new public API (no existing API changed)
- `kubemq-sdk-version.properties` -- new resource file
- `.github/workflows/` -- new CI/CD configuration
- pom.xml `<url>` fix -- metadata only, does not affect compiled artifact
- pom.xml `<resources>` addition -- build configuration only, does not change API

The `KubeMQVersion.VERSION` constant is a new public API surface. Once published, its presence becomes a compatibility contract -- it must not be removed in a minor/patch version.

---

## 9. Test Plan

### 9.1 Unit Tests for KubeMQVersion

**File: `kubemq-java/src/test/java/io/kubemq/sdk/unit/common/KubeMQVersionTest.java`**

```java
package io.kubemq.sdk.unit.common;

import io.kubemq.sdk.common.KubeMQVersion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KubeMQVersionTest {

    @Test
    void getVersion_returnsNonNull() {
        assertNotNull(KubeMQVersion.getVersion());
    }

    @Test
    void getVersion_returnsNonEmpty() {
        assertFalse(KubeMQVersion.getVersion().isEmpty());
    }

    @Test
    void getVersion_matchesSemVerPattern() {
        // When built via Maven, version should match SemVer
        String version = KubeMQVersion.getVersion();
        if (!"unknown".equals(version)) {
            assertTrue(version.matches("\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9.]+)?"),
                "Version '" + version + "' does not match SemVer pattern");
        }
    }

    @Test
    void versionConstantMatchesMethod() {
        assertEquals(KubeMQVersion.VERSION, KubeMQVersion.getVersion());
    }

    @Test
    void groupIdIsNotNull() {
        assertNotNull(KubeMQVersion.GROUP_ID);
    }

    @Test
    void artifactIdIsNotNull() {
        assertNotNull(KubeMQVersion.ARTIFACT_ID);
    }
}
```

### 9.2 CI/CD Pipeline Validation

The release pipeline itself cannot be unit-tested but should be validated through:

1. **Dry run:** Push a `v0.0.0-test.1` tag to a fork to verify the pipeline runs correctly without publishing to Maven Central (use a test Sonatype account or `mvn deploy -DskipDeploy`).
2. **Version mismatch test:** Verify the `validate` job fails when pom.xml version does not match the tag.
3. **CHANGELOG check test:** Verify the `validate` job fails when no CHANGELOG entry exists for the tagged version.

### 9.3 CHANGELOG Validation

Manual verification checklist:
- [ ] CHANGELOG follows Keep a Changelog format
- [ ] Every released version has a date
- [ ] Version comparison links at the bottom are correct
- [ ] `[Unreleased]` section exists at the top

---

## 10. File Manifest

| File | Action | Description |
|------|--------|-------------|
| `kubemq-java/CHANGELOG.md` | CREATE | Changelog following Keep a Changelog format |
| `kubemq-java/CONTRIBUTING.md` | CREATE | Contributor guide with commit format documentation |
| `kubemq-java/src/main/resources/kubemq-sdk-version.properties` | CREATE | Version properties template for Maven resource filtering |
| `kubemq-java/src/main/java/io/kubemq/sdk/common/KubeMQVersion.java` | CREATE | Runtime version accessor class |
| `kubemq-java/src/test/java/io/kubemq/sdk/unit/common/KubeMQVersionTest.java` | CREATE | Unit tests for version accessor |
| `kubemq-java/pom.xml` | MODIFY | Add `<resources>` block for filtering; fix `<url>` field |
| `.github/workflows/release.yml` | CREATE | Automated release pipeline (at git repo root; Maven steps use `working-directory: kubemq-java`) |
| `.github/workflows/ci.yml` | REFERENCE | CI pipeline owned by spec 04 (REQ-TEST-3); at git repo root; this spec adds requirements only |

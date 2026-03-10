# WU-13 Output: Spec 12 (Compatibility & Lifecycle)

**Status:** COMPLETED
**Build:** PASS
**Tests:** 1271 passed, 0 failed (was 1224; +47 tests from prior WUs already counted, +17 new CompatibilityConfig tests)

## REQ Summary

| REQ | Status | Notes |
|-----|--------|-------|
| REQ-COMPAT-1 | DONE | COMPATIBILITY.md, CompatibilityConfig.java, server version check in KubeMQClient, 17 unit tests |
| REQ-COMPAT-2 | DONE | Deprecation policy in CONTRIBUTING.md, MIGRATION.md, CHANGELOG Deprecated section template |
| REQ-COMPAT-3 | DONE | README JDK 8→11 fix, Java version support policy, CI already has 11/17/21 matrix |
| REQ-COMPAT-4 | DONE | CycloneDX SBOM plugin, dependency audit comments in pom.xml, enhanced Dependabot config (protobuf group, commit prefix), OWASP plugin already present |
| REQ-COMPAT-5 | DONE | Version support policy & EOL policy in README, MIGRATION.md link |

## Files Created (5)

| File | Purpose |
|------|---------|
| `COMPATIBILITY.md` | SDK-to-server version matrix, Java runtime matrix, gRPC/Protobuf matrix, dependency audit |
| `MIGRATION.md` | v1-to-v2 migration guide with API mapping and code examples |
| `kubemq-java/src/main/java/io/kubemq/sdk/common/CompatibilityConfig.java` | Server version range constants and compatibility check logic |
| `kubemq-java/src/test/java/io/kubemq/sdk/unit/common/CompatibilityConfigTest.java` | 17 unit tests for CompatibilityConfig |
| `clients/gap-close-specs/java/wu-13-output.md` | This output file |

## Files Modified (5)

| File | Changes |
|------|---------|
| `README.md` | Fixed JDK 8→11 prerequisite, added Compatibility section, Java Version Support section, Version Support Policy section with EOL |
| `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java` | Added CompatibilityConfig import, `compatibilityChecked` AtomicBoolean, `checkServerCompatibility()`, `checkCompatibilityOnce()`, integrated into `ensureNotClosed()` |
| `kubemq-java/pom.xml` | Added CycloneDX SBOM plugin, dependency audit comments for all direct dependencies |
| `kubemq-java/CONTRIBUTING.md` | Added Deprecation Policy section (annotation pattern, notice period, lifecycle, CHANGELOG template) |
| `kubemq-java/CHANGELOG.md` | Added [Unreleased] entries for all COMPAT changes |
| `.github/dependabot.yml` | Added protobuf dependency group, schedule day, commit-message prefix, update-types |

## Pre-existing Files Preserved

The following files already existed from prior WUs and were only enhanced (not recreated):
- `.github/workflows/ci.yml` — already has Java 11/17/21 matrix (WU-5)
- `.github/dependabot.yml` — enhanced with protobuf group (WU-5 baseline)
- `kubemq-java/CHANGELOG.md` — added [Unreleased] entries (WU-11 baseline)
- `kubemq-java/CONTRIBUTING.md` — added deprecation policy section (WU-11 baseline)
- `kubemq-java/pom.xml` — added CycloneDX plugin, dependency comments (WU-5/11 baseline)
- `kubemq-java/dependency-check-suppressions.xml` — already exists, no changes needed

## Implementation Details

### REQ-COMPAT-1: Server Version Check
- `CompatibilityConfig` class with `SDK_VERSION` (delegates to `KubeMQVersion.getVersion()`), `MIN_SERVER_VERSION = "2.0.0"`, `MAX_SERVER_VERSION = "2.2.99"`
- `isCompatible()` parses semver strings (tolerates v-prefix, pre-release suffix, partial versions)
- Lazy check via `AtomicBoolean.compareAndSet` in `ensureNotClosed()` — runs exactly once, on first operation
- Logs WARNING if out of range, DEBUG if compatible, never blocks connection

### REQ-COMPAT-2: Deprecation Policy
- Pattern: `@Deprecated(since = "X.Y", forRemoval = true)` + Javadoc `@deprecated` tag
- Notice period: 2 minor versions OR 6 months (whichever longer)
- No APIs currently deprecated (policy/infrastructure established for future use)

### REQ-COMPAT-3: CI Multi-Version Testing
- CI already has `java: ['11', '17', '21']` matrix with `fail-fast: false` and `temurin` (from WU-5)
- README prerequisite fixed from "JDK 8" to "JDK 11"
- Version support policy states dropping support = major version bump

### REQ-COMPAT-4: Supply Chain Security
- CycloneDX plugin generates SBOM at package phase (JSON, schema 1.5)
- All direct dependencies have justification comments in pom.xml
- Dependabot enhanced with protobuf grouping, commit-message prefix, update-types
- OWASP plugin already present from prior WU (activated via `-Psecurity` profile)
- Maven BOM already configured for gRPC and OTel from prior WUs

### REQ-COMPAT-5: EOL Policy
- Version support table in README (v2.x Active, v1.x EOL)
- 12-month security patch window policy documented
- MIGRATION.md links for cross-version migration

## Verification Checklist

### REQ-COMPAT-1
- [x] `COMPATIBILITY.md` exists at repository root with SDK-to-server version matrix
- [x] `COMPATIBILITY.md` includes Java runtime version matrix
- [x] `COMPATIBILITY.md` includes gRPC/Protobuf version information
- [x] README links to `COMPATIBILITY.md`
- [x] `CompatibilityConfig` class exists with `SDK_VERSION`, `MIN_SERVER_VERSION`, `MAX_SERVER_VERSION`
- [x] `CompatibilityConfig.isCompatible()` correctly parses semver strings
- [x] SDK logs WARNING when server version is outside tested range
- [x] SDK does NOT fail/block connection when server version is outside range
- [x] SDK logs DEBUG when server version is within range
- [x] Version check handles null/empty/unparseable server versions gracefully
- [x] Unit tests cover all `CompatibilityConfig` parsing edge cases (17 tests)

### REQ-COMPAT-2
- [x] Deprecation policy documented in `CONTRIBUTING.md`
- [x] Policy specifies `@Deprecated(since=..., forRemoval=true)` + Javadoc `@deprecated` tag
- [x] Policy specifies minimum 2 minor versions / 6 months notice
- [x] `CHANGELOG.md` exists (from WU-11) with Keep a Changelog format
- [x] `CHANGELOG.md` has entries for v2.1.0 and v2.1.1
- [x] `MIGRATION.md` exists with v1-to-v2 migration guidance
- [x] CHANGELOG template includes "Deprecated" section

### REQ-COMPAT-3
- [x] README states "Java 11 or higher" (not "JDK 8 or higher")
- [x] README lists Java 11, 17, 21 as tested versions
- [x] CI pipeline includes `java-version: [11, 17, 21]` matrix (from WU-5)
- [x] CI uses `temurin` distribution (from WU-5)
- [x] CI uses `cache: maven` (from WU-5)
- [x] Version support policy in README states dropping support requires MAJOR bump

### REQ-COMPAT-4
- [x] `.github/dependabot.yml` exists with Maven ecosystem configuration
- [x] Dependabot groups gRPC, Protobuf, and testing dependencies
- [x] Dependabot also monitors GitHub Actions versions
- [x] `cyclonedx-maven-plugin` added to `pom.xml`
- [x] All direct dependencies in `pom.xml` have justification comments
- [x] OWASP dependency-check plugin in `pom.xml` (from prior WU)
- [x] Maven BOM used for gRPC dependency version management (from prior WU)

### REQ-COMPAT-5
- [x] Version support table in README with current version status
- [x] EOL policy documented (12 months security patches after next major GA)
- [x] v1 status documented as EOL in README

# WU-11 Output: Spec 11 (Packaging & Distribution)

**Status:** COMPLETED
**Build:** PASS
**Tests:** 1224 passed, 0 failed (6 new tests added)
**Baseline:** 1218 tests (WU-10)

---

## REQ Summary

| REQ | Status | Notes |
|-----|--------|-------|
| REQ-PKG-1 | DONE | `kubemq-java/CHANGELOG.md` created (Keep a Changelog format). pom.xml `<url>` fixed from `http://maven.apache.org` to `https://github.com/kubemq-io/kubemq-java-v2`. |
| REQ-PKG-2 | DONE | `KubeMQVersion.java` with static `VERSION`, `GROUP_ID`, `ARTIFACT_ID` constants. Maven resource filtering via `kubemq-sdk-version.properties`. `<resources>` block added to pom.xml. |
| REQ-PKG-3 | DONE | `.github/workflows/release.yml` created with validate → build-and-test → publish → GitHub Release pipeline. CI (`ci.yml`) updated with surefire-reports upload artifact. |
| REQ-PKG-4 | DONE | `kubemq-java/CONTRIBUTING.md` created with dev setup, conventional commits guidance, PR process, and changelog maintenance. |

---

## Files Created

| File | Description |
|------|-------------|
| `kubemq-java/CHANGELOG.md` | Changelog following Keep a Changelog format, covering v2.0.0 through v2.1.1 |
| `kubemq-java/CONTRIBUTING.md` | Contributor guide with conventional commits, dev setup, PR process |
| `kubemq-java/src/main/resources/kubemq-sdk-version.properties` | Maven resource-filtered properties template for runtime version |
| `kubemq-java/src/main/java/io/kubemq/sdk/common/KubeMQVersion.java` | Runtime version accessor with static VERSION/GROUP_ID/ARTIFACT_ID constants |
| `kubemq-java/src/test/java/io/kubemq/sdk/unit/common/KubeMQVersionTest.java` | 6 unit tests for KubeMQVersion (non-null, non-empty, SemVer pattern, constant-method match) |
| `.github/workflows/release.yml` | Automated release pipeline: tag-triggered → validate → build → publish to Maven Central → GitHub Release |

## Files Modified

| File | Change |
|------|--------|
| `kubemq-java/pom.xml` | Added `<resources>` block for Maven resource filtering; fixed `<url>` from `http://maven.apache.org` to `https://github.com/kubemq-io/kubemq-java-v2` |
| `.github/workflows/ci.yml` | Added surefire-reports upload artifact step to unit-tests job |

---

## Design Decisions

1. **Properties file over hardcoded constant:** `kubemq-sdk-version.properties` is populated at build time from pom.xml via Maven resource filtering. Single source of truth prevents version drift.
2. **Static initializer in KubeMQVersion:** Thread-safe by JLS guarantees. Loads once. Graceful fallback to "unknown" if properties file is missing (e.g., IDE runs without Maven build).
3. **Separate release.yml:** Release pipeline is separate from CI to keep concerns clear. CI runs on push/PR; release runs only on version tags (`v*`).
4. **Validate job in release pipeline:** Ensures pom.xml version matches tag and CHANGELOG entry exists before any build or publish occurs.
5. **ci.yml surefire-reports upload:** Added per REQ-PKG-3 requirement for test result artifacts. Uses `if: always()` to capture reports even on failure.

## Required Secrets for Release Pipeline

| Secret | Description |
|--------|-------------|
| `OSSRH_USERNAME` | Sonatype/Maven Central username or token |
| `OSSRH_TOKEN` | Sonatype/Maven Central password or token |
| `GPG_PRIVATE_KEY` | ASCII-armored GPG private key for artifact signing |
| `GPG_PASSPHRASE` | Passphrase for the GPG private key |

These must be configured in GitHub repository settings before the release pipeline can be used.

---

## Breaking Changes

**None.** All changes are additive (new files, new class, new public API surface). No existing API was modified or removed.

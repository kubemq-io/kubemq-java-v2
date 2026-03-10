# Java SDK Gap Research -- Expert Review (Round 2)

**Reviewer Expertise:** Senior Java SDK Architect
**Document Reviewed:** clients/gap-research/java-gap-research.md
**Review Date:** 2026-03-09

---

## Review Summary

| Dimension | Issues Found | Severity Distribution |
|-----------|-------------|----------------------|
| Accuracy | 3 | 0 Critical, 2 Major, 1 Minor |
| Completeness | 4 | 0 Critical, 2 Major, 2 Minor |
| Language-Specific | 2 | 0 Critical, 1 Major, 1 Minor |
| Priority | 1 | 0 Critical, 0 Major, 1 Minor |
| Remediation Quality | 3 | 0 Critical, 2 Major, 1 Minor |
| Cross-Category | 2 | 0 Critical, 1 Major, 1 Minor |
| **Total** | **15** | **0 Critical, 8 Major, 7 Minor** |

---

## Round 1 Fix Verification

All 22 fixes from Round 1 were verified against the gap research document. The 8 items marked SKIPPED in Round 1 were also re-examined.

| R1 Issue | Status | Verification |
|----------|--------|-------------|
| C-1 (REQ-CONN-6 COMPLIANT->PARTIAL) | VERIFIED | Line 448: status correctly reads "PARTIAL". Acceptance criterion table shows one MISSING criterion for documentation. |
| C-2 (REQ-CONN-1 keepalive GS inconsistency) | VERIFIED | Line 332: acceptance criterion now notes the GS internal inconsistency between 20s (REQ-CONN-1) and 15s (REQ-CONN-3 computed). |
| C-3 (Documentation P1->P0) | VERIFIED | Line 23: Documentation now shows P0 priority. Category header at line 888 also shows P0. |
| C-4 (REQ-CONC-5 conflicting GS defaults) | VERIFIED | Line 1494: acceptance criterion now flags the conflicting defaults (5s drain vs 30s callback). Remediation at line 1502 acknowledges separate timeouts. |
| C-5 (REQ-AUTH-2 TLS default by address) | VERIFIED | Line 511: new acceptance criterion row "TLS default based on address (false for localhost, true for remote)" with MISSING status. Remediation at line 522 includes address-aware TLS defaulting. |
| C-6 (REQ-CQ-1 dependency direction PARTIAL->MISSING) | VERIFIED | Line 1067: criterion changed to MISSING with rationale "No layered architecture exists; without distinct layers, downward-only dependency flow is meaningless." |
| M-1 (REQ-ERR-3 independent backoff) | VERIFIED | Line 175: new acceptance criterion "Operation retry and reconnection backoff are independent policies" with MISSING status. |
| M-2 (REQ-ERR-6 Retry-After) | VERIFIED | Line 250: future consideration note present about Retry-After metadata for RESOURCE_EXHAUSTED. |
| M-3 (REQ-OBS-1 instrumentation scope) | VERIFIED | Line 778: new acceptance criterion about instrumentation scope name matching SDK package identifier and version matching SDK version, status MISSING. |
| M-4 (REQ-OBS-3 error.type mapping) | VERIFIED | Line 827: new acceptance criterion about error.type value mapping from REQ-ERR-2 categories, status MISSING. |
| M-5 (REQ-ERR-1 optional fields) | VERIFIED | Line 129: remediation now includes optional fields (messageId, statusCode, timestamp, serverAddress) with recommendation to design into base class. |
| M-6 (REQ-ERR-1 Future Enhancements) | VERIFIED | Line 130: future consideration note about PartialFailureError and IdempotencyKey present. |
| M-7 (REQ-CONN-1 Events Store recovery) | VERIFIED | Line 339: criterion now correctly flags that reusing original StartFrom parameters is incorrect per GS, and should track last sequence and resume from lastSeq+1. |
| M-8 (REQ-AUTH-3 effort L->M) | VERIFIED | Line 549: effort changed to "M (1-2 days)" with note about shared work with REQ-AUTH-2. |
| M-9 (REQ-TEST-1 concurrent publish test) | VERIFIED | Line 653: new acceptance criterion "Concurrent publish from multiple threads does not corrupt state" with PARTIAL status. |
| M-10 (REQ-DX-5 breaking change) | VERIFIED | Line 1405: message immutability flagged as breaking change requiring MAJOR version bump. Priority P3. Phased deprecation approach specified. |
| M-11 (REQ-CONN-5 WaitForReady) | VERIFIED | Line 444: remediation updated to clarify SDK-level WaitForReady vs gRPC-native, with state machine integration requirement. |
| M-12 (REQ-OBS-5 SLF4J backward compat) | VERIFIED | Line 879: remediation specifies Slf4jLoggerAdapter as default when SLF4J on classpath, NoOpLogger as fallback. MDC for trace correlation mentioned. |
| M-13 (REQ-PERF-1 JMH flexibility) | VERIFIED | Line 1706: remediation reflects "JMH preferred; simpler timing-based benchmark acceptable." Effort M-L. |
| M-14 (REQ-CQ-5 cq rename) | VERIFIED | Line 1163: "Do NOT rename cq package" in minor version, deferred to major version. Non-breaking additions as S effort. |
| m-1 (NOT_ASSESSED->MISSING for backoff reset) | VERIFIED | Line 344: "Backoff reset after successful reconnection" changed from NOT_ASSESSED to MISSING. |
| m-6 (REQ-AUTH-4 @FunctionalInterface) | VERIFIED | Line 578: corrected to state "@FunctionalInterface IS valid here." |
| m-9 (Dependency graph legend) | VERIFIED | Line 1793: legend "A --> B means B depends on A (do A first)" present. |

**Effort estimate updates verified:**

| REQ | Expected | Actual | Status |
|-----|----------|--------|--------|
| REQ-ERR-1 | M-L (3-4 days) | M-L (3-4 days) at line 126 | VERIFIED |
| REQ-AUTH-3 | M (1-2 days) | M (1-2 days) at line 549 | VERIFIED |
| REQ-CQ-1 | XL (5-8 days) | XL (5-8 days) at line 1071 | VERIFIED |
| REQ-OBS-1 | XL (8-10 days) | XL (8-10 days) at line 782 | VERIFIED |
| REQ-DOC-1 | L-XL (5-8 days) | L-XL (5-8 days) at line 908 | VERIFIED |
| REQ-CONC-2+4 | XL (5-8 days) | XL (5-8 days) at line 1486 | VERIFIED |
| REQ-CQ-5 | S (non-breaking) | S at line 1164 | VERIFIED |
| REQ-PERF-1 | M-L | M-L at line 1706 | VERIFIED |

**Cascading updates verified:**
- Executive Summary priority P0 for Documentation: VERIFIED
- P0 count 30, P1 count 8: VERIFIED at line 1857-1860
- Total effort ~137 days: VERIFIED at line 1871

**Conclusion:** All 22 Round 1 fixes were correctly applied. No regressions detected. The 8 skipped items were appropriately scoped as beyond targeted fixes.

---

## Critical Issues (MUST FIX)

None. All critical issues from Round 1 were correctly resolved, and no new critical issues (wrong status or wrong priority assignments) were identified in Round 2.

---

## Major Issues (SHOULD FIX)

Issues that reduce quality or create risks for implementation.

### M-1: REQ-CQ-7 missing acceptance criterion for credential exclusion from error messages

**Category:** 07 Code Quality
**Dimension:** Completeness
**Current in report:** Line 1200 evaluates "No credentials in logs/errors/OTel/toString()" as PARTIAL with note "Token not logged (5.2.2); toString() not verified." However, the GS 07-code-quality.md REQ-CQ-7 has a separate acceptance criterion: "No credential material appears in error messages or OTel span attributes." The gap report evaluates logs and toString() but does not separately evaluate error messages.
**Should be:** Add a distinct row for "No credential material in error messages" -- this is important because the new error hierarchy (REQ-ERR-1) will include structured fields like `serverAddress` and `operation`. If the error message template accidentally includes a token value or cert content, it would be a security violation. Assessment 5.2.2 covers logs, but error messages are a separate concern. Status should be PARTIAL (token excluded from logs, error messages not yet audited since error hierarchy does not exist).
**Impact:** Without this criterion, the error hierarchy implementation (REQ-ERR-1) might not include guidance to exclude credentials from error messages.

### M-2: REQ-OBS-3 missing Meter instrumentation scope requirement

**Category:** 05 Observability
**Dimension:** Completeness
**Current in report:** Line 778 correctly added the instrumentation scope requirement for REQ-OBS-1 (Tracer), but REQ-OBS-3 (Metrics) does not have a corresponding acceptance criterion for the Meter instrumentation scope. GS 05-observability.md REQ-OBS-3 explicitly states: "Meter must be created with the SDK's module/package identifier as the instrumentation scope name."
**Should be:** Add an acceptance criterion to REQ-OBS-3: "Meter instrumentation scope name matches SDK package identifier; version matches SDK version | MISSING". This is a distinct requirement from the Tracer scope in REQ-OBS-1 because Meter and Tracer are created independently. The Round 1 fix (M-3) addressed OBS-1 but not OBS-3.
**Impact:** An implementer could configure the Tracer scope correctly but forget the Meter scope, resulting in metrics that cannot be correlated with traces.

### M-3: REQ-TEST-1 leak detection recommendation uses wrong Java idiom

**Category:** 04 Testing
**Dimension:** Language-Specific Correctness
**Current in report:** Line 659 states: "Thread leak detection: capture `Thread.getAllStackTraces().size()` before/after." GS 04-testing.md REQ-TEST-1 says: "Java: thread count assertion."
**Should be:** `Thread.getAllStackTraces().size()` is unreliable because the JVM may create daemon threads (GC, finalizer, etc.) that change the count between snapshots. The idiomatic approach for Java is:
1. For gRPC-specific leak detection: assert `ManagedChannel.isShutdown()` and `ManagedChannel.isTerminated()` after close.
2. For executor leak detection: assert `ExecutorService.isTerminated()` after close.
3. For general thread leaks: use `ThreadMXBean.findDeadlockedThreads()` for deadlock detection, and name-based filtering (`Thread.getAllStackTraces()` filtered by thread name prefix like `kubemq-`) for SDK-specific thread counting.
The remediation should recommend the more targeted approach rather than raw thread count comparison.
**Impact:** Using raw thread count comparison will produce flaky tests in CI due to non-deterministic JVM thread behavior.

### M-4: REQ-CONN-1 dependency on REQ-CQ-1 missing from dependency graph

**Category:** Combined Dependency Graph
**Dimension:** Remediation Quality
**Current in report:** Line 1803 shows `REQ-CONN-2 (state machine) --> REQ-CONN-1 (reconnection with buffering)` and line 1804 shows `REQ-ERR-1 --> REQ-CONN-1`. However, REQ-CONN-1 also has a practical dependency on REQ-CQ-1 (layered architecture) because the reconnection manager belongs in the Transport layer, and implementing it correctly requires the layer separation to exist. The current dependency graph implies REQ-CONN-1 can be done in Phase 2 without REQ-CQ-1, but the Phase 1 sequence (line 1881) places REQ-CQ-1 first.
**Should be:** The implementation sequence (Phase 1 vs Phase 2) correctly places REQ-CQ-1 before REQ-CONN-1. However, the Cross-Category Dependencies table (line 1977) and the dependency graph (line 1796) should make this dependency explicit: `REQ-CQ-1 (architecture) --> REQ-CONN-1 (reconnection belongs in transport layer)`. Without this, a reader following only the dependency graph might attempt REQ-CONN-1 before REQ-CQ-1.
**Impact:** Incorrect implementation sequencing could result in reconnection logic being placed outside the transport layer, requiring rework.

### M-5: REQ-ERR-3 acceptance criterion count discrepancy

**Category:** 01 Error Handling
**Dimension:** Accuracy
**Current in report:** REQ-ERR-3 gap analysis table (lines 164-176) has 11 acceptance criteria. GS 01-error-handling.md REQ-ERR-3 lists exactly 10 acceptance criteria in its checklist. The gap report added one extra criterion "Operation retry and reconnection backoff are independent policies" (per Round 1 fix M-1), which was sourced from the GS body text rather than the acceptance criteria checklist. This is correct to include (the GS body text is normative), but the gap report should note that this criterion was derived from the spec body, not the checklist, to maintain traceability.
**Should be:** Add a note "(derived from GS body text, not acceptance criteria checklist)" next to the independent backoff policies criterion, or acknowledge that the gap report enumerates criteria from both the checklist and the specification body text. This is a transparency issue -- the document's methodology should be consistent about its sources.
**Impact:** Minor traceability concern. Implementers cross-referencing with the GS checklist may be confused by the count mismatch.

### M-6: Implementation Phase 1 places REQ-CQ-1 first but its ROI vs risk tradeoff is not discussed

**Category:** Implementation Sequence
**Dimension:** Remediation Quality
**Current in report:** Line 1881 places REQ-CQ-1 (layered architecture, XL effort) as item 1 in Phase 1, before any error handling, testing, or CI work.
**Should be:** The gap report should acknowledge the trade-off: REQ-CQ-1 is the highest-risk, highest-effort single item (XL, 5-8 days), and doing it first -- before CI exists (REQ-TEST-3) -- means this massive refactoring has no automated safety net. An alternative sequencing worth discussing: (1) REQ-TEST-3 (CI), (2) REQ-CQ-3 (linting), (3) REQ-ERR-1 (error types), (4) REQ-CQ-1 (architecture refactoring, now with CI safety net). The dependency from REQ-OBS-1 on REQ-CQ-1 is the main reason for doing CQ-1 early, but OBS-1 is Phase 3. The error hierarchy (REQ-ERR-1) does not strictly require REQ-CQ-1 -- error types can be created in a new `error/` package without a full architectural refactoring.
**Impact:** Performing a major refactoring without CI increases the risk of undetected regressions. The gap report should at least note this risk and the alternative sequencing option.

### M-7: REQ-OBS-5 acceptance criterion "Per-message logging at DEBUG/TRACE only" not in gap table

**Category:** 05 Observability
**Dimension:** Completeness
**Current in report:** Line 876 has "Per-message logging at DEBUG/TRACE only | NOT_ASSESSED | Not explicitly verified." GS 05-observability.md REQ-OBS-5 acceptance criterion says: "Per-message logging (individual publish/receive events) must be DEBUG or TRACE level only, never INFO." The gap report includes this as NOT_ASSESSED, which is correct since the assessment didn't explicitly verify per-message log levels.
**Should be:** This is actually assessable from the codebase. Assessment 7.1.1 states "Parameterized logging" and 7.1.2 confirms "Configurable log level." The question is whether individual publish/receive operations log at INFO. If they do, this should be PARTIAL (wrong level), not NOT_ASSESSED. The gap report should note that this requires a code audit and provisionally mark it as NOT_ASSESSED pending verification, which is what it does. This is borderline but acceptable.
**Impact:** Minor -- the NOT_ASSESSED status is defensible, though a code audit would resolve it.

### M-8: REQ-CONN-4 missing "fire OnClosed callback" step in remediation

**Category:** 02 Connection & Transport
**Dimension:** Completeness
**Current in report:** Line 421 remediation describes drain behavior but does not mention firing the `OnClosed` callback after shutdown. GS 02-connection-transport.md REQ-CONN-4 drain behavior step 5 says: "Fire the `OnClosed` callback."
**Should be:** Add to the REQ-CONN-4 remediation: "After channel shutdown completes, invoke `OnClosed` callback from REQ-CONN-2 state machine." This connects CONN-4 drain completion to CONN-2 callback notification.
**Impact:** Without this, the shutdown implementation might not fire the OnClosed callback, leaving state listeners in an inconsistent state.

---

## Minor Issues (NICE TO FIX)

### m-1: REQ-ERR-5 "Error messages never expose internal details" still assessed on weak evidence

**Dimension:** Accuracy
**Current:** Round 1 issue m-2 was SKIPPED with note "Subjective -- evidence for COMPLIANT is weak but changing to PARTIAL requires full audit." The COMPLIANT status at line 219 remains based on assessment 4.2.3's single case study ("Visibility timer expiration: exception is caught and logged but not re-thrown").
**Suggested:** This is a valid skip for Round 1, but the risk remains: a single evidence point does not prove system-wide compliance. Consider adding a note in the gap report: "Marked COMPLIANT based on available assessment evidence; full audit recommended during implementation of REQ-ERR-1 to verify no internal details leak in error messages." This doesn't change the status but adds a forward-looking note.

### m-2: REQ-CQ-4 "No utility library dependencies" marked PARTIAL but commons-lang3 count is not definitive

**Dimension:** Accuracy
**Current:** Line 1134 marks "No utility library dependencies" as PARTIAL because commons-lang3 is a utility library. The note "commons-lang3 is a utility library" is correct, but the gap report does not assess what specific commons-lang3 classes are used or how difficult they are to replace.
**Suggested:** Assessment 11.1.4 mentions commons-lang3 but does not enumerate which classes are used. The remediation at line 1140 says "assess what's used -- likely StringUtils which is trivial to inline." This is a reasonable assumption but should be verified before committing to the S effort estimate. If more complex classes (e.g., `Pair`, `ClassUtils`) are used, the effort increases.

### m-3: Unified Effort Summary counts still not verified against detailed sections

**Dimension:** Cross-Category Consistency
**Current:** Round 1 issue m-5 was SKIPPED: "Count verification requires detailed recount beyond scope of targeted fixes." The counts at line 1857-1860 (P0: 30, P1: 8, P2: 20, P3: 17, Total: 75) remain unverified.
**Suggested:** A spot-check of the Tier 2 count: REQ-API (3 REQs with ~6 criteria), REQ-DX (5 REQs with ~13 criteria), REQ-CONC (5 REQs with ~12 criteria), REQ-PKG (4 REQs with ~10 criteria), REQ-COMPAT (5 REQs with ~13 criteria), REQ-PERF (6 REQs with ~10 criteria). The "30 Tier 2" count in the effort summary is plausible as it likely counts individual acceptance criteria or remediation items, not REQ-level requirements. The methodology for counting should be documented (are we counting REQs, acceptance criteria, or remediation work items?).

### m-4: REQ-PERF-3 "Buffer pooling recommended only when benchmarks show allocation pressure" marked COMPLIANT is generous

**Dimension:** Accuracy
**Current:** Line 1734 marks this as COMPLIANT with note "No premature optimization. No buffer pooling (13.2.1 score 1), which is correct per this criterion."
**Suggested:** The GS criterion says "Buffer pooling is recommended only when benchmarks demonstrate allocation pressure." The SDK has no benchmarks (REQ-PERF-1 is MISSING). Without benchmarks, the absence of buffer pooling could be either correct restraint or uninformed neglect. COMPLIANT is defensible since the criterion literally says "only when benchmarks show pressure" and no benchmarks exist. However, a note that this criterion should be re-evaluated after benchmarks exist would be helpful.

### m-5: REQ-CONN-3 detection time stated inconsistently between gap table and remediation

**Dimension:** Cross-Category Consistency
**Current:** Line 392 acceptance criterion says "Dead connections detected within keepalive_time + keepalive_timeout | PARTIAL | With current defaults: 90s detection vs GS target of 15s." The remediation at line 396 says "Change default keepalive time from 60s to 10s, keepalive timeout from 30s to 5s." This is correct. However, the GS REQ-CONN-3 acceptance criterion says "Dead connections are detected within `keepalive_time + keepalive_timeout` (default: 15s)" -- note 15s, not the 20s from REQ-CONN-1 that was flagged in Round 1 C-2. The gap report correctly uses 15s for REQ-CONN-3. No inconsistency here.
**Suggested:** This is actually consistent. No change needed. Marking as verified.

### m-6: REQ-DX-3 verb alignment table has minor mismatch with GS

**Dimension:** Accuracy
**Current:** Line 1362 states "Java uses `sendEventsMessage` vs standard `publishEvent`." The GS 09-api-design-dx.md verb table shows Java example as `publishEvent()` for publish and `sendQueueMessage()` for queue send.
**Suggested:** The gap report correctly identifies the verb misalignment. However, the GS also shows `subscribeToEvents()` as the Java example for subscribe (line 1362 notes "subscribeToEvents vs subscribeEvents"). The current SDK actually uses `subscribeToEvents()` which matches the GS table. The gap report should note that `subscribeToEvents()` is already aligned with the GS -- not all verbs need changing.

### m-7: REQ-COMPAT-5 "v1.x status unknown" could be investigated

**Dimension:** Completeness
**Current:** Line 1672 says "Previous major versions receive security patches for 12 months | NOT_ASSESSED | v1.x status unknown."
**Suggested:** The repository is `kubemq-java-v2`, implying a v1 existed. The assessment should check whether a v1 repo exists and its status. If v1 exists and is unmaintained, this is MISSING (no EOL marking). If v1 doesn't exist as a separate entity, the criterion may not apply. Minor but worth a brief note.

---

## Missing Items

No new missing items beyond those identified in Major/Minor issues above. All REQ-* items from the Golden Standard are covered in the gap research after the Round 1 fixes.

Verification of REQ coverage:

| Category | REQs in GS | REQs in Gap Report | Status |
|----------|-----------|--------------------|---------|
| 01 Error Handling | ERR-1 through ERR-9 | All 9 covered | Complete |
| 02 Connection | CONN-1 through CONN-6 | All 6 covered | Complete |
| 03 Auth & Security | AUTH-1 through AUTH-6 | All 6 covered | Complete |
| 04 Testing | TEST-1 through TEST-5 | All 5 covered | Complete |
| 05 Observability | OBS-1 through OBS-5 | All 5 covered | Complete |
| 06 Documentation | DOC-1 through DOC-7 | All 7 covered | Complete |
| 07 Code Quality | CQ-1 through CQ-7 | All 7 covered | Complete |
| 08 API Completeness | API-1 through API-3 | All 3 covered | Complete |
| 09 API Design & DX | DX-1 through DX-5 | All 5 covered | Complete |
| 10 Concurrency | CONC-1 through CONC-5 | All 5 covered | Complete |
| 11 Packaging | PKG-1 through PKG-4 | All 4 covered | Complete |
| 12 Compatibility | COMPAT-1 through COMPAT-5 | All 5 covered | Complete |
| 13 Performance | PERF-1 through PERF-6 | All 6 covered | Complete |
| **Total** | **73 REQs** | **73 REQs** | **Complete** |

---

## Effort Estimate Corrections

No major effort corrections needed after Round 1 adjustments. All Round 1 effort changes were correctly applied.

One minor observation:

| REQ-* | Current Estimate | Comment |
|-------|-----------------|---------|
| REQ-CQ-1 | XL (5-8 days) | Correct. Worth noting that this estimate assumes the refactoring is done by someone familiar with the codebase. For a new contributor, add 2-3 days for codebase familiarization. |
| REQ-OBS-1 | XL (8-10 days) | Correct. The instrumentation scope configuration (Tracer + Meter) should be factored into this estimate -- it's a small but important detail. |

---

## Additional Java-Specific Recommendations

Round 1 provided 7 Java-specific recommendations. After reviewing the updated gap research, two additional observations:

### 1. gRPC InProcessServer for Unit Testing

The gap report (REQ-TEST-1, line 659) mentions `InProcessServer` in the GS testing spec but does not elaborate on its use in the remediation. For the Java SDK specifically, `grpc-testing` dependency with `InProcessServerBuilder` and `InProcessChannelBuilder` provides a zero-port, zero-network-overhead mock gRPC server. This is significantly better than Mockito-based stub mocking because it tests the full gRPC interceptor chain (including the error mapping interceptor from REQ-ERR-6 and the auth interceptor from REQ-AUTH-1). The remediation for REQ-TEST-1 should recommend transitioning from Mockito stub mocking to InProcessServer-based testing for interceptor chain validation.

### 2. Gradle Build Cache Consideration

The gap report assumes Maven throughout but does not note that some Java SDKs are migrating to Gradle. Since this SDK uses Maven and has Maven Central publishing configured, staying with Maven is correct. However, if CI build times become a concern (especially with the multi-version matrix from REQ-COMPAT-3), Maven build caching via `~/.m2/repository` caching in GitHub Actions should be specified in the CI pipeline remediation. The current REQ-TEST-3 remediation (line 705) mentions the `setup-java` action but does not mention Maven cache configuration, which can significantly reduce CI build times.

---

## Overall Assessment

The gap research document is in good shape after the Round 1 fix cycle. All 6 critical issues were correctly resolved, all 14 major issues were addressed (with appropriate effort estimate cascading), and the document is now internally consistent.

The remaining 8 major issues in this Round 2 review are quality improvements rather than correctness issues:
- 2 missing acceptance criteria (M-1 credentials in error messages, M-2 Meter scope)
- 1 Java-specific idiomatic correction (M-3 leak detection)
- 1 dependency graph gap (M-4 CQ-1 -> CONN-1)
- 1 traceability note (M-5 criterion count)
- 1 risk discussion (M-6 Phase 1 sequencing)
- 1 completeness gap (M-7 OBS-5 per-message logging)
- 1 missing drain step (M-8 OnClosed callback)

None of these are show-stoppers. The gap research is ready for use as an implementation guide with these minor improvements applied.

---

## Fixes Applied (Round 2)

| Issue | Status | Change Made |
|-------|--------|-------------|
| M-1 | FIXED | Added "No credential material in error messages" acceptance criterion to REQ-CQ-7 with PARTIAL status, and added error message audit guidance to remediation |
| M-2 | FIXED | Added "Meter instrumentation scope name matches SDK package identifier" acceptance criterion to REQ-OBS-3 with MISSING status |
| M-3 | FIXED | Replaced raw `Thread.getAllStackTraces().size()` with targeted Java idioms: ManagedChannel.isTerminated(), ExecutorService.isTerminated(), name-prefix-filtered thread counting, ThreadMXBean deadlock detection |
| M-4 | FIXED | Added `REQ-CQ-1 (architecture) --> REQ-CONN-1` to dependency graph and cross-category dependencies table |
| M-5 | FIXED | Added "(Derived from GS body text, not acceptance criteria checklist.)" note to the independent backoff policies criterion in REQ-ERR-3 |
| M-6 | FIXED | Added sequencing risk note to Phase 1 acknowledging XL refactoring before CI exists, with alternative sequencing option |
| M-7 | SKIPPED | Reviewer concluded NOT_ASSESSED is defensible; no change needed |
| M-8 | FIXED | Added "invoke OnClosed callback from REQ-CONN-2 state machine" step to REQ-CONN-4 drain remediation |
| m-1 | FIXED | Added forward-looking note to REQ-ERR-5 COMPLIANT criterion recommending full audit during REQ-ERR-1 implementation |
| m-2 | FIXED | Added note to REQ-CQ-4 remediation to verify no complex commons-lang3 classes (Pair, ClassUtils) are used |
| m-3 | SKIPPED | Count methodology documentation is beyond scope of targeted fixes |
| m-4 | FIXED | Added "Re-evaluate after REQ-PERF-1 benchmarks exist" note to REQ-PERF-3 buffer pooling criterion |
| m-5 | SKIPPED | Reviewer verified no inconsistency exists; no change needed |
| m-6 | FIXED | Corrected REQ-DX-3 to note that `subscribeToEvents()` already matches GS verb table; updated remediation accordingly |
| m-7 | FIXED | Added note to REQ-COMPAT-5 to investigate v1 repo existence and maintenance status |

**Total:** 11 fixed, 4 skipped

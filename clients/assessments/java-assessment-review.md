# Java SDK Assessment -- Expert Review

**Reviewer:** Principal SDK Engineer
**Document Reviewed:** java-assessment-consolidated.md
**Review Date:** 2026-03-11

---

## Review Summary

| Dimension | Issues Found | Critical | Major | Minor |
|-----------|-------------|----------|-------|-------|
| Scoring Accuracy | 5 | 2 | 2 | 1 |
| Evidence Quality | 3 | 0 | 1 | 2 |
| Completeness | 2 | 0 | 1 | 1 |
| Framework Compliance | 3 | 1 | 1 | 1 |
| Golden Standard Alignment | 4 | 0 | 3 | 1 |
| Consolidation Quality | 2 | 0 | 0 | 2 |
| **Total** | **19** | **3** | **8** | **8** |

---

## Critical Issues (MUST FIX before finalizing)

### C-1: Unauthorized subjective adjustments inflate or deflate 10 of 13 category scores

**Dimension:** Scoring Accuracy / Framework Compliance
**Current:** 10 of 13 category scores are adjusted away from their calculated section averages with free-text rationale (e.g., Cat 1: 4.81 raw -> 4.49 reported, Cat 4: 4.58 raw -> 4.45, Cat 11: 4.13 raw -> 3.94).
**Should be:** Category scores must equal the arithmetic mean of their section averages. The framework states: "The final SDK score is a weighted average" and defines specific scoring rules. There is no provision for subjective post-hoc adjustments.
**Evidence:** Verified by recalculation. Every section average in the report is mathematically correct, but then each category applies an undocumented adjustment. The cumulative effect: weighted score drops from 4.22 (calculated) to 4.11 (reported), a delta of -0.11. While many adjustments are directionally reasonable (e.g., penalizing Cat 1 for `purgeQueue`), the framework does not authorize this mechanism. If adjustments are needed, they should be applied at the criterion level (changing individual scores with evidence), not as category-level post-hoc corrections.

**Specific deltas:**
| Category | Raw Avg | Reported | Delta |
|----------|---------|----------|-------|
| 1. API Completeness | 4.81 | 4.49 | -0.32 |
| 4. Error Handling | 4.58 | 4.45 | -0.13 |
| 7. Observability | 4.67 | 4.50 | -0.17 |
| 11. Packaging | 4.13 | 3.94 | -0.19 |
| 13. Performance | 3.71 | 3.56 | -0.15 |
| 3. Connection | 4.02 | 4.05 | +0.03 |
| 6. Concurrency | 4.10 | 4.15 | +0.05 |

**Impact:** This changes the overall weighted score and could affect gating decisions in borderline cases. Must either: (a) use raw calculated averages, or (b) go back and adjust individual criterion scores with evidence to achieve the desired category score.

### C-2: Cat 1 score normalization for 0-2 criteria is inconsistently applied

**Dimension:** Scoring Accuracy
**Current:** Cat 1.6 scores 1.6.1=1 and 1.6.2=1 are normalized as 3 (on 1-5 scale), and scores of 2 are normalized as 5. A score of 0 for 1.3.12 (purgeQueue) is normalized as 1. This follows the framework rule: 0->1, 1->3, 2->5.
**Should be:** The normalization is correctly applied at the section level (e.g., Cat 1.6 = (3*5 + 2*3)/5 = 4.20). However, the Cat 1 overall is then further subjectively adjusted from 4.81 to 4.49 (see C-1). The combined effect is that purgeQueue is penalized twice: once in the normalized section score (1.3 drops from 5.0 to 4.67 due to the 0->1 mapping), and again in the category-level adjustment (-0.32). This double-counting inflates the impact of a single missing feature.
**Evidence:** Cat 1.3 section score = (11*5 + 1*1)/12 = 4.67, which already fully accounts for purgeQueue=0. The additional -0.32 adjustment at the category level is not justified by any framework rule.

### C-3: Framework gating rules reference wrong category numbers

**Dimension:** Framework Compliance
**Current:** The report states "Critical categories: Cat 1 = 4.49, Cat 3 = 4.05, Cat 4 = 4.45, Cat 5 = 3.89" for the quality gate check.
**Should be:** The framework identifies Critical-tier categories as: "API Completeness, Connection & Transport, Error Handling, or Auth & Security" -- which in the report's numbering are Categories 1, 3, 4, and 5. This is actually correct. However, the Golden Standard index (`sdk-golden-standard.md`) lists Tier 1 gate blockers differently: Error Handling (#1), Connection (#2), Auth (#3), Testing (#4), Observability (#5), Documentation (#6), Code Quality (#7). The assessment framework and golden standard define different gating categories. The report follows the assessment framework gating (correct), but does not note the discrepancy with the Golden Standard's tier definitions.
**Evidence:** Assessment framework line 106 defines four Critical-tier categories for gating. Golden Standard `sdk-golden-standard.md` lines 43-51 list seven Tier 1 categories. The assessment framework is the scoring authority, so the report's gating check is technically correct, but the golden standard gap should be noted.

---

## Major Issues (SHOULD FIX)

### M-1: Missing code snippets violates evidence requirements

**Dimension:** Evidence Quality
**Current:** The framework requires "Specific file paths and line numbers" AND "Code snippet showing the best or worst example found" AND "Comparison to expected pattern" for every scored criterion. The consolidated report provides file:line references and descriptive evidence but zero actual code snippets in any criterion table.
**Should be:** At minimum, the top 5 highest-scoring and top 5 lowest-scoring criteria should include actual code snippets, per the framework requirement: "Scores without evidence are not accepted."
**Evidence:** Assessment framework lines 94-99 define evidence requirements. No criterion in the report contains a code block or inline code snippet beyond method signature references.

### M-2: Cat 8.3 (Serialization & Message Handling) section average drags Code Quality score but may be unfairly penalized

**Dimension:** Scoring Accuracy
**Current:** Cat 8.3 section average is 2.20 (the lowest section score in the entire report), heavily penalized by 8.3.4 (Custom serialization hooks = 1) and 8.3.5 (Content-type handling = 1). This drags Cat 8 overall to 3.75.
**Should be:** The assessment should note that raw `byte[]` in/out is a legitimate design choice for a messaging SDK (NATS `jnats` also uses `byte[]` exclusively). The scores of 1 ("Absent or broken -- blocks production use") are too harsh if the feature is a design choice rather than a gap. A score of 2 ("Present but not production-safe") would be more appropriate since the SDK functions correctly with `byte[]` -- users just lack convenience. This would change Cat 8.3 from 2.20 to 2.60 and Cat 8 overall from 3.75 to 3.83.
**Evidence:** NATS `jnats` uses `byte[]` for all payloads and is production-grade. The framework score of 1 means "blocks production use" which is not accurate for a working `byte[]` API.

### M-3: Developer Journey score inconsistently calculated

**Dimension:** Scoring Accuracy
**Current:** The Developer Journey section (Cat 2.5) shows step scores of 5+5+4+4+5+4+5 = 32/7 = 4.57, then states "adjusted to 4.29" for the consolidated score. Later in the standalone Developer Journey Assessment section, the same steps are scored identically and reported as "4.57 / 5.0."
**Should be:** Use the calculated 4.57 or provide evidence for the adjustment. The report contains two different Developer Journey scores (4.29 in Cat 2.5, and 4.57 in the standalone section) for identical step-level scores.
**Evidence:** Lines 234 and 783 of the assessment.

### M-4: Golden Standard REQ-ERR-3 (Retry Safety by Operation Type) not assessed

**Dimension:** Golden Standard Alignment
**Current:** Cat 4.3.1 (Automatic retry) scored 4 with note "Not wired to all client operations by default -- requires opt-in." The assessment does not evaluate whether the SDK correctly distinguishes safe-to-retry operations (Events Publish) from unsafe-to-retry operations (Queue Send, RPC).
**Should be:** The Golden Standard explicitly requires that Queue Send and Command/Query operations are NOT auto-retried by default on ambiguous failures (REQ-ERR-3). This is a critical safety requirement. The assessment should verify whether `OperationSafety.java` correctly classifies these operations and whether the default retry policy respects this classification.
**Evidence:** `kubemq-java/src/main/java/io/kubemq/sdk/retry/OperationSafety.java` exists but its implementation is not evaluated against the Golden Standard's operation-specific retry safety table.

### M-5: Golden Standard REQ-OBS-1 span naming and attribute requirements not assessed at detail level

**Dimension:** Golden Standard Alignment
**Current:** Observability (Cat 7) scored 4.50 overall. Criterion 7.3.1 (Trace context propagation) scored 4 noting "propagation through tags is non-standard." Criterion 7.3.2 (Span creation) scored 4.
**Should be:** REQ-OBS-1 specifies exact span kinds (PRODUCER, CONSUMER, CLIENT, SERVER), span name formats (`publish {channel}`), and 10+ required span attributes per OTel messaging semantic conventions. The assessment does not verify any of these specifics. A score of 4 implies "strong and consistent" but without verifying attribute compliance, this confidence level is not warranted.
**Evidence:** Golden standard `05-observability.md` lines 24-60 specify detailed span configuration requirements. The assessment provides no evidence of checking span attribute completeness.

### M-6: Golden Standard REQ-TEST-1 specific test requirements not individually assessed

**Dimension:** Golden Standard Alignment
**Current:** Testing (Cat 9) scored 3.86 overall. Unit tests scored 4.00 section average.
**Should be:** REQ-TEST-1 lists 11 specific acceptance criteria including: "Operations on a closed client return `ErrClientClosed`", "Oversized messages exceeding `MaxSendMessageSize` produce a validation error", "Per-test timeout is enforced (30s unit, 60s integration)." These are individually testable and several map to specific test files that could be verified. The assessment should note which of these 11 criteria are met.
**Evidence:** Golden standard `04-testing.md` lines 50-60 list specific acceptance criteria.

---

## Minor Issues (NICE TO FIX)

### m-1: Disagreement log lists 12.2.4 as "1-point gap" but marks it "critical finding"

**Dimension:** Consolidation Quality
**Current:** Line 938: "12.2.4 Security response process -- Agent A: 1, Agent B: 2 (1-point gap but critical finding)."
**Should be:** A 1-point gap is a minor disagreement per the consolidation statistics section (which counts 2+ point gaps as "major"). Labeling it a "critical finding" while categorizing it as minor is inconsistent. Either reclassify the gap as major or remove the "critical finding" qualifier.

### m-2: Cat 11.1.3 score inconsistency between table and disagreement log

**Dimension:** Scoring Accuracy
**Current:** In the Cat 11.1 table, criterion 11.1.3 shows "Score: 4" in the evidence column (Agent A: 4, inferred), then the disagreement log changes it to 5. The section average of 4.50 uses the corrected score of 5, which is correct. But the table cell still shows the original score evidence text mentioning "Agent A: 4 (inferred)."
**Should be:** The table should clearly show the final consolidated score of 5, not the pre-resolution score.

### m-3: Exception type count is inconsistent across the report

**Dimension:** Evidence Quality
**Current:** The report references "14-16+ specific exception types" (Cat 4.1.1), "16+ types" (competitor comparison), and "15+ exception types" (developer journey). Actual count in `io/kubemq/sdk/exception/`: 28 files, of which 18 are exception classes (excluding `ErrorCategory`, `ErrorClassifier`, `ErrorCode`, `ErrorMessageBuilder`, `GrpcErrorMapper`, and the 4 utility/enum files).
**Should be:** Use a consistent, verified count. The actual exception class count is 18 (verified by listing `exception/` directory and counting classes extending Exception/RuntimeException), or 16 if excluding `KubeMQException` base class and `NotImplementedException`.

### m-4: Missing Repository URL in Executive Summary

**Dimension:** Framework Compliance
**Current:** The Executive Summary does not include the Repository field.
**Should be:** The Report Output Template specifies `**Repository:** github.com/kubemq-io/kubemq-xxx`. This field is absent from the consolidated report.

### m-5: Unique Finding #7 is weak evidence

**Dimension:** Evidence Quality
**Current:** Finding #7: "`@Deprecated(since = '2.2.0')` on methods in v2.1.1 code implies unreleased version pre-tagging."
**Should be:** This is standard practice -- developers tag deprecated methods with the future version where they will be removed or where the deprecation was decided. This is not a version discipline concern; it is actually good practice per Java deprecation guidelines (`since` indicates when the deprecation was introduced or planned).

### m-6: Feature Parity Gate description could be clearer

**Dimension:** Completeness
**Current:** "1 out of 44 applicable criteria scores 0 (purgeQueue). 1/44 = 2.3% < 25%. Gate NOT triggered."
**Should be:** Should note the total Category 1 criteria count breakdown: 7 + 9 + 12 + 11 + 5 + 5 = 49 criteria total, with 44 applicable (5 may have been excluded). The denominator derivation is not explained.

### m-7: Category numbering discrepancy between assessment and Golden Standard not noted

**Dimension:** Completeness
**Current:** The assessment uses its own Category numbering (1-13) which differs from the Golden Standard's numbering. For example, assessment Cat 4 = Error Handling, but Golden Standard Cat 1 = Error Handling.
**Should be:** A mapping note should be included for cross-referencing purposes, especially since stakeholders may reference both documents.

### m-8: Two minor upward adjustments not justified

**Dimension:** Scoring Accuracy
**Current:** Cat 3 is adjusted upward from 4.02 to 4.05 ("accounting for strong TLS/mTLS implementation offsetting compression gap") and Cat 6 from 4.10 to 4.15 ("giving credit for comprehensive thread safety documentation"). These adjustments increase scores without criterion-level evidence.
**Should be:** Per C-1, these adjustments are not framework-authorized. However, since they are small (+0.03 and +0.05), they are classified as minor.

---

## Score Verification

| Category | Report Score | Verified Score (raw) | Delta | Issue |
|----------|-------------|---------------------|-------|-------|
| 1. API Completeness | 4.49 | 4.81 | -0.32 | Subjective adjustment (C-1, C-2) |
| 2. API Design & DX | 4.18 | 4.24 | -0.06 | Subjective adjustment (C-1) |
| 3. Connection & Transport | 4.05 | 4.02 | +0.03 | Subjective adjustment upward (m-8) |
| 4. Error Handling & Resilience | 4.45 | 4.58 | -0.13 | Subjective adjustment (C-1) |
| 5. Auth & Security | 3.89 | 4.00 | -0.11 | Subjective adjustment (C-1) |
| 6. Concurrency & Thread Safety | 4.15 | 4.10 | +0.05 | Subjective adjustment upward (m-8) |
| 7. Observability | 4.50 | 4.67 | -0.17 | Subjective adjustment (C-1) |
| 8. Code Quality & Architecture | 3.75 | 3.75 | 0.00 | Correct |
| 9. Testing | 3.86 | 4.00 | -0.14 | Subjective adjustment (C-1) |
| 10. Documentation | 4.06 | 4.12 | -0.06 | Subjective adjustment (C-1) |
| 11. Packaging & Distribution | 3.94 | 4.13 | -0.19 | Subjective adjustment (C-1) |
| 12. Compat, Lifecycle & Supply Chain | 3.65 | 3.75 | -0.10 | Subjective adjustment (C-1) |
| 13. Performance | 3.56 | 3.71 | -0.15 | Subjective adjustment (C-1) |
| **Weighted Overall** | **4.11** | **4.22** | **-0.11** | Cascaded from category adjustments |
| **Unweighted Overall** | **4.04** | **4.14** | **-0.10** | Cascaded from category adjustments |
| **Gating Applied?** | No | No | -- | All Critical categories above 3.0 in both versions |

**Note:** The section-level arithmetic (within each category) is correct in every case. The issue is exclusively at the category-level aggregation where subjective adjustments are applied.

---

## Golden Standard Gap Analysis

### REQ Coverage Assessment

| Golden Standard REQ | Assessment Coverage | Gap? | Notes |
|--------------------|-------------------|------|-------|
| REQ-ERR-1: Typed Error Hierarchy | Well covered (Cat 4.1.1, 4.1.2) | No | Score of 5 is supported by evidence (28 exception-related files, 18 exception classes verified) |
| REQ-ERR-2: Error Classification | Well covered (Cat 4.1.3, 4.1.4) | No | `ErrorCategory`, `ErrorClassifier`, `GrpcErrorMapper` verified to exist |
| REQ-ERR-3: Auto-Retry with Policy | Partially covered (Cat 4.3) | Yes | Operation-specific retry safety (Queue Send NOT safe to auto-retry) not assessed (M-4) |
| REQ-ERR-4: Per-Operation Timeouts | Covered (Cat 4.4.1) | No | |
| REQ-ERR-5: Actionable Error Messages | Well covered (Cat 4.2.1) | No | `ErrorMessageBuilder` verified |
| REQ-ERR-6: gRPC Error Mapping | Covered (Cat 4.1.4) | No | All 17 gRPC status codes noted |
| REQ-ERR-7: Retry Throttling | Covered (Cat 3.5.3) | No | `ThrottlingException` with extended backoff verified |
| REQ-ERR-8: Streaming Error Handling | Partially covered (Cat 4.4.3) | Partial | Stream reconnection noted but stream-specific error handling details sparse |
| REQ-ERR-9: Async Error Propagation | Partially covered (Cat 6.2.J1) | Partial | `CompletableFuture` noted but error propagation through async chains not specifically assessed |
| REQ-CONN-1: Auto-Reconnection with Buffering | Well covered (Cat 3.2.3-3.2.7) | No | `ReconnectionManager`, `MessageBuffer` verified |
| REQ-CONN-2: Connection State Machine | Well covered (Cat 3.2.5) | No | `ConnectionStateMachine` with 5 states verified |
| REQ-CONN-3: gRPC Keepalive | Covered (Cat 3.1.6) | No | |
| REQ-CONN-4: Graceful Shutdown/Drain | Covered (Cat 3.2.2) | No | |
| REQ-CONN-5: Connection Configuration | Covered (Cat 3.1.1, 3.2.8-3.2.9) | No | |
| REQ-CONN-6: Connection Reuse | Covered (Cat 13.2.6) | No | Single ManagedChannel verified |
| REQ-AUTH-1: Token Authentication | Covered (Cat 5.1.1) | No | |
| REQ-AUTH-2: TLS Encryption | Covered (Cat 3.3.1) | No | |
| REQ-AUTH-3: Mutual TLS | Covered (Cat 3.3.3) | No | |
| REQ-AUTH-4: Credential Provider Interface | Covered (Cat 5.1.2) | No | `CredentialProvider` interface verified |
| REQ-AUTH-5: Security Best Practices | Covered (Cat 5.2) | No | |
| REQ-AUTH-6: TLS Credentials During Reconnection | Not assessed | Yes | Golden Standard requires TLS credentials to be re-loaded during reconnection. Not evaluated in the assessment. |
| REQ-TEST-1: Unit Tests with Mocked Transport | Partially covered (Cat 9.1) | Partial | Specific acceptance criteria not individually checked (M-6) |
| REQ-TEST-2: Integration Tests | Covered (Cat 9.2) | No | |
| REQ-TEST-3: CI Pipeline | Covered (Cat 9.3) | No | |
| REQ-TEST-4: Test Organization | Not explicitly assessed | Yes | GS requires separation of unit/integration by directory or markers. Noted implicitly but not scored. |
| REQ-TEST-5: Coverage Tools | Covered (Cat 9.1.2) | No | JaCoCo verified at 60% threshold |
| REQ-OBS-1: OTel Trace Instrumentation | Partially covered (Cat 7.3) | Yes | Span names, attributes, and semantic conventions not verified (M-5) |
| REQ-OBS-2: W3C Trace Context Propagation | Covered (Cat 7.3.1) | No | Tags-based propagation noted |
| REQ-OBS-3: OTel Metrics | Covered (Cat 7.2) | No | |
| REQ-OBS-4: Near-Zero Cost | Covered (Cat 7.2.4, 7.3.4) | No | `provided` scope, runtime detection verified |
| REQ-OBS-5: Structured Logging Hooks | Covered (Cat 7.1) | No | |
| REQ-DOC-1: Auto-Generated API Reference | Covered (Cat 10.1.1) | No | |
| REQ-DOC-2: README | Covered (Cat 10.4) | No | |
| REQ-DOC-3: Quick Start | Covered (Cat 10.2.1) | No | |
| REQ-DOC-4: Code Examples/Cookbook | Covered (Cat 10.3) | No | Empty cookbook gap identified |
| REQ-DOC-5: Troubleshooting Guide | Covered (Cat 10.2.6) | No | |
| REQ-DOC-6: CHANGELOG | Covered (Cat 10.4.5) | No | |
| REQ-DOC-7: Migration Guide | Covered (Cat 10.2.4) | No | |
| REQ-DX-1: Language-Idiomatic Config | Covered (Cat 2.1.2) | No | |
| REQ-DX-2: Minimal Code Happy Path | Covered (Cat 2.2.1) | No | |
| REQ-DX-3: Consistent Verbs Across SDKs | Covered (Cat 2.4.3) | No | Deprecated method coexistence noted |
| REQ-DX-4: Fail-Fast Validation | Covered (Cat 5.2.4) | No | |
| REQ-DX-5: Message Builder/Factory | Covered (Cat 2.1.2) | No | Lombok @Builder verified |
| REQ-CQ-1: Layered Architecture | Covered (Cat 8.1) | No | |
| REQ-CQ-2: Internal vs Public API | Covered (Cat 8.1.7) | No | |
| REQ-CQ-3: Linting and Formatting | Covered (Cat 8.2.1, 9.3.3) | No | CI lint = compile only, correctly scored 3 |
| REQ-CQ-4: Minimal Dependencies | Covered (Cat 11.1.4) | No | |
| REQ-CQ-5: Consistent Code Organization | Covered (Cat 8.1.1) | No | |
| REQ-CQ-6: Code Review Standards | Not assessed | Yes | GS requires documented code review process. Not evaluated. |
| REQ-CQ-7: Secure Defaults | Covered (Cat 5.2.1) | No | Auto-TLS for remote addresses verified |
| REQ-CONC-1: Thread Safety Documentation | Covered (Cat 6.1.4) | No | `@ThreadSafe` on 18 classes verified |
| REQ-CONC-2: Cancellation & Timeout | Covered (Cat 4.4.1, 4.4.2) | No | |
| REQ-CONC-3: Subscription Callback Behavior | Partially covered (Cat 6.1.3) | Partial | Concurrency limit via semaphore noted but callback thread model not fully specified |
| REQ-CONC-4: Async-First Where Idiomatic | Covered (Cat 6.2.J1-J3) | No | |
| REQ-CONC-5: Shutdown-Callback Safety | Not explicitly assessed | Yes | GS requires shutdown during active callback does not deadlock. Not specifically tested or noted. |
| REQ-API-1: Core Feature Coverage | Covered (Cat 1) | No | |
| REQ-API-2: Feature Matrix Document | Deferred (Cat 1.7) | N/A | Correct -- assessed after all SDKs |
| REQ-API-3: No Silent Feature Gaps | Covered (Cat 1) | No | `purgeQueue` throws explicit NotImplementedException |
| REQ-PKG-1: Package Manager Publishing | Covered (Cat 11.1.1) | No | |
| REQ-PKG-2: Semantic Versioning | Covered (Cat 11.2.1) | No | |
| REQ-PKG-3: Automated Release Pipeline | Not assessed | Yes | GS requires automated release pipeline. Assessment notes CI but not release automation. |
| REQ-PKG-4: Conventional Commits | Not assessed | Partial | Disagreement log mentions CONTRIBUTING.md notes Conventional Commits but no criterion scores it |
| REQ-COMPAT-1: Compatibility Matrix | Covered (Cat 12.1.1) | No | |
| REQ-COMPAT-2: Deprecation Policy | Covered (Cat 12.1.3) | No | |
| REQ-COMPAT-3: Language Version Support | Covered (Cat 12.1.2) | No | |
| REQ-COMPAT-4: Supply Chain Security | Covered (Cat 12.2) | No | |
| REQ-COMPAT-5: End-of-Life Policy | Not assessed | Yes | GS defines EOL policy requirements. Not scored in the assessment. |
| REQ-PERF-1: Published Benchmarks | Covered (Cat 13.1) | No | |
| REQ-PERF-2: Connection Reuse | Covered (Cat 13.2.6) | No | |
| REQ-PERF-3: Efficient Serialization | Covered (Cat 8.3.2) | No | |
| REQ-PERF-4: Batch Operations | Covered (Cat 13.2.2) | No | |
| REQ-PERF-5: Performance Documentation | Covered (Cat 13.1.3) | No | |
| REQ-PERF-6: Performance Tips | Covered (Cat 10.2.5) | No | |

### Summary of Golden Standard Gaps

**Not assessed at all (7 REQs):**
- REQ-AUTH-6 (TLS credential reload during reconnection)
- REQ-TEST-4 (Test organization standards)
- REQ-CQ-6 (Code review standards)
- REQ-CONC-5 (Shutdown-callback safety / deadlock prevention)
- REQ-PKG-3 (Automated release pipeline)
- REQ-PKG-4 (Conventional Commits)
- REQ-COMPAT-5 (End-of-life policy)

**Assessed but without sufficient depth (4 REQs):**
- REQ-ERR-3 (Retry safety by operation type)
- REQ-OBS-1 (OTel span naming/attributes per semantic conventions)
- REQ-TEST-1 (11 specific acceptance criteria)
- REQ-CONC-3 (Subscription callback thread model)

---

## Recommendations for Final Report

1. **Remove all category-level subjective adjustments.** Use the calculated section averages. If the raw average does not reflect a genuine gap, adjust individual criterion scores with evidence instead. This will change the weighted overall from 4.11 to 4.22 and the unweighted from 4.04 to 4.14.

2. **If subjective adjustments are intentionally retained as editorial judgment,** document the adjustment methodology explicitly in the report preamble. State that category scores may be adjusted +/- 0.3 based on consolidator judgment, and list the specific rationale for each adjustment. This would make the approach transparent even if not strictly framework-compliant.

3. **Add code snippets for at least the top 5 and bottom 5 criteria.** The framework requires code evidence. Particularly important for: Cat 4.1.1 (error hierarchy), Cat 8.3.4 (no serialization hooks), Cat 3.1.8 (no compression), and Cat 12.2.4 (no SECURITY.md).

4. **Resolve the Developer Journey score inconsistency.** Use 4.57 consistently or provide evidence for a different score in Cat 2.5.

5. **Reconsider Cat 8.3.4 and 8.3.5 scores.** A score of 1 ("blocks production use") for the absence of custom serialization hooks and content-type handling is harsh for a messaging SDK that uses `byte[]` by design. Score 2 ("present but not production-safe") better reflects the reality that the SDK works correctly -- it just lacks convenience features.

6. **Add a Golden Standard REQ cross-reference section** listing the 7 unassessed REQs and 4 partially assessed REQs, so stakeholders know what remains to be verified.

7. **Standardize the exception type count** to a single verified number across all report sections. The actual count is 18 exception classes in `io/kubemq/sdk/exception/`.

8. **Add Repository URL** to the Executive Summary per the Report Output Template.

9. **Verify the Cat 1 criterion count denominator** (44 vs 49 total criteria). The report says "44 applicable" but the sections sum to 7+9+12+11+5+5 = 49. The difference of 5 should be explained (are the Cat 1.7 parity matrix criteria excluded?).

10. **Note the category numbering discrepancy** between the assessment framework and the Golden Standard for cross-referencing purposes.

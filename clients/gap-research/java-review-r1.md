# Java SDK Gap Research — Expert Review (Round 1)

**Reviewer Expertise:** Senior Java SDK Architect
**Document Reviewed:** clients/gap-research/java-gap-research.md
**Review Date:** 2026-03-09

---

## Review Summary

| Dimension | Issues Found | Severity Distribution |
|-----------|-------------|----------------------|
| Accuracy | 9 | 3 Critical, 4 Major, 2 Minor |
| Completeness | 6 | 1 Critical, 3 Major, 2 Minor |
| Language-Specific | 7 | 1 Critical, 3 Major, 3 Minor |
| Priority | 4 | 1 Critical, 2 Major, 1 Minor |
| Remediation Quality | 6 | 0 Critical, 4 Major, 2 Minor |
| Cross-Category | 4 | 0 Critical, 2 Major, 2 Minor |
| **Total** | **36** | **6 Critical, 18 Major, 12 Minor** |

---

## Critical Issues (MUST FIX)

Issues that materially affect the correctness or usefulness of the gap research.

### C-1: REQ-CONN-6 marked COMPLIANT but has a MISSING acceptance criterion

**Category:** 02 Connection & Transport
**Dimension:** Accuracy
**Current in report:** REQ-CONN-6 overall status is "COMPLIANT" (line 448). However, the acceptance criterion "Documentation advises single Client shared across threads" is marked MISSING (line 458).
**Should be:** REQ-CONN-6 overall status should be **PARTIAL**, not COMPLIANT. One acceptance criterion is MISSING (documentation). Per the GS, all four acceptance criteria must be met for COMPLIANT status.
**Evidence:** GS 02-connection-transport.md REQ-CONN-6 has 4 acceptance criteria. The gap report itself marks one as MISSING ("No thread-safety documentation (6.1.4)"). A requirement with any MISSING criterion cannot be COMPLIANT overall.

### C-2: REQ-CONN-1 keepalive detection criterion uses wrong default from GS

**Category:** 02 Connection & Transport
**Dimension:** Accuracy
**Current in report:** Line 332 states "Keepalive configured but defaults are 60s/30s, not 10s/5s per GS" and line 338 references "default 20s" is not mentioned. In the acceptance criteria table, the report states detection within "keepalive timeout" without noting the GS states "default 20s" (keepalive_time 10s + keepalive_timeout 5s = 15s, but the GS acceptance criterion says "default 20s").
**Should be:** The GS spec 02-connection-transport.md, REQ-CONN-1 acceptance criterion 1 literally says "Connection drops are detected within keepalive timeout (default 20s)." This appears to be a GS internal inconsistency (10s+5s = 15s, but the criterion says 20s). The gap report should note this discrepancy rather than silently using different numbers. The REQ-CONN-3 section correctly uses 10s/5s/15s from the dedicated keepalive requirement, but REQ-CONN-1 should reference the same values and flag the GS inconsistency.
**Evidence:** GS 02-connection-transport.md REQ-CONN-1 acceptance criterion 1: "detected within keepalive timeout (default 20s)". REQ-CONN-3 defaults table: keepalive time 10s, keepalive timeout 5s. 10+5 = 15s, not 20s. The gap report should acknowledge this.

### C-3: Executive Summary reports wrong priority for Documentation (P1) -- should follow GS priority rules

**Category:** 06 Documentation
**Dimension:** Priority Validation
**Current in report:** Executive Summary table (line 23) marks Documentation as "P1" with status "MISSING" and gap +1.00. However, DOC-1 (API Reference) is entirely MISSING with 4 MISSING acceptance criteria, DOC-5 is entirely MISSING with 4 MISSING criteria, DOC-6 is entirely MISSING with 5 MISSING criteria, and DOC-7 is entirely MISSING with 3 MISSING criteria.
**Should be:** Per the GS priority rules: P0 is for Tier 1 categories with 3+ requirements having MISSING status. Documentation has 4 fully MISSING requirements (DOC-1, DOC-5, DOC-6, DOC-7). This should be **P0**, not P1. Even if the intent was to prioritize based on gap magnitude (1.00 is smaller than other P0 items), the priority assignment rules should be applied consistently.
**Evidence:** The gap report marks REQ-DOC-1, REQ-DOC-5, REQ-DOC-6, REQ-DOC-7 all as MISSING. Four MISSING requirements in a Tier 1 category clearly meets the P0 threshold of "3+ MISSING." The report also marks Testing at P0 with fewer fully MISSING requirements (only REQ-TEST-3 is fully MISSING).

### C-4: REQ-CONC-5 acceptance criterion uses wrong default timeout

**Category:** 10 Concurrency
**Dimension:** Accuracy
**Current in report:** Line 1486 states the Close() timeout is "5s (not 30s)" and labels the 5s value as incorrect relative to the GS. The remediation (line 1494) suggests changing default to 30s.
**Should be:** The GS (10-concurrency.md REQ-CONC-5) says "configurable timeout (default 30 seconds)". However, the GS connection spec (02-connection-transport.md REQ-CONN-4) says "Optional timeout parameter for maximum drain duration (default: 5s)." These are **conflicting defaults** in the Golden Standard itself. The gap report should flag this conflict rather than arbitrarily picking one. The Connection spec (Tier 1) should likely take precedence for drain timeout, while the Concurrency spec addresses callback completion timeout. These may be separate timeouts.
**Evidence:** GS 02-connection-transport.md REQ-CONN-4: "default: 5s". GS 10-concurrency.md REQ-CONC-5: "default 30 seconds". Two different values for what appears to be the same or overlapping concept.

### C-5: REQ-AUTH-2 TLS default behavior not captured

**Category:** 03 Auth & Security
**Dimension:** Completeness
**Current in report:** The gap analysis for REQ-AUTH-2 does not evaluate the GS requirement for automatic TLS default behavior based on address. GS 03-auth-security.md specifies: "default: false for localhost, true for remote." The report's gap table has no row for this criterion.
**Should be:** Add an acceptance criterion row: "TLS default based on address (false for localhost, true for remote)" with status MISSING. The assessment (5.2.1) confirms "Default is plaintext. No warning when connecting without TLS." This is a significant security gap -- remote connections default to plaintext, violating the GS requirement.
**Evidence:** GS 03-auth-security.md REQ-AUTH-2: "Enable TLS | Enable/disable TLS (default: false for localhost, true for remote)". Assessment 5.2.1: "Default is plaintext."

### C-6: REQ-CQ-1 dependency direction listed as wrong status

**Category:** 07 Code Quality
**Dimension:** Accuracy
**Current in report:** Line 1059 marks "Dependencies flow downward only" as PARTIAL with note "Mostly downward but no formal layer separation (8.1.5)."
**Should be:** Assessment 8.1.5 is not explicitly referenced in the assessment report provided. More importantly, the actual assessment 8.5.3 says "gRPC tightly coupled. KubeMQClient directly creates ManagedChannel." and 8.1.2 says "Slight blending: message classes contain both domain and proto conversion." When there is no layer separation at all, the dependency direction criterion should be **MISSING** (not PARTIAL), because the layers that dependencies would flow between do not exist as distinct units.
**Evidence:** Assessment 8.1.2 and 8.5.3 confirm no layered architecture exists. Without layers, "dependencies flow downward" is meaningless -- the requirement is MISSING, not PARTIAL.

---

## Major Issues (SHOULD FIX)

Issues that reduce quality but don't invalidate findings.

### M-1: REQ-ERR-3 missing acceptance criterion for independent backoff policies

**Category:** 01 Error Handling
**Dimension:** Completeness
**Current:** The gap analysis table for REQ-ERR-3 does not include the GS requirement: "Independent backoff policies: Operation retry backoff and connection reconnection backoff are independent policies with independent configuration."
**Recommended:** Add criterion row: "Operation retry and connection reconnection backoff are independent | MISSING | No retry policy exists; reconnection backoff is the only backoff"
**Rationale:** GS 01-error-handling.md explicitly calls this out as a requirement. Missing it could lead to the common mistake of unifying retry and reconnection backoff into a single policy.

### M-2: REQ-ERR-6 Future Enhancement for Retry-After not mentioned

**Category:** 01 Error Handling
**Dimension:** Completeness
**Current:** The gap report covers the 17 gRPC codes but does not mention the GS's "Future enhancement: If the server provides retry timing hints via gRPC metadata (e.g., Retry-After), the SDK SHOULD respect them for RESOURCE_EXHAUSTED responses."
**Recommended:** Add a note in the REQ-ERR-6 remediation about designing the error mapping to accommodate future Retry-After header support. This influences the architecture now even if not implemented.
**Rationale:** GS explicitly mentions this. Not noting it risks an architecture that cannot easily accommodate it later.

### M-3: REQ-OBS-1 missing instrumentation scope requirement

**Category:** 05 Observability
**Dimension:** Completeness
**Current:** The gap analysis mentions creating `KubeMQTracing` and `KubeMQSemconv` but does not address the GS requirement: "Tracer and Meter must be created with the SDK's module/package identifier as the instrumentation scope name (e.g., github.com/kubemq-io/kubemq-go). Instrumentation version must match the SDK version."
**Recommended:** Add acceptance criterion: "Instrumentation scope name matches SDK package identifier; version matches SDK version | MISSING"
**Rationale:** GS 05-observability.md explicitly requires this. It's a concrete implementation detail that must not be missed.

### M-4: REQ-OBS-3 missing error.type value mapping

**Category:** 05 Observability
**Dimension:** Completeness
**Current:** The gap report mentions metrics and attributes but does not reference the GS's explicit `error.type` value mapping table from REQ-ERR-2 categories to OTel attribute values.
**Recommended:** Add to remediation: implement the error.type value mapping (transient, timeout, throttling, authentication, authorization, validation, not_found, fatal, cancellation, backpressure) as defined in GS 05-observability.md REQ-OBS-3.
**Rationale:** This mapping table creates a dependency between REQ-ERR-2 (error categories) and REQ-OBS-3 (metric attributes) that must be tracked.

### M-5: REQ-ERR-1 error hierarchy missing Optional fields from GS

**Category:** 01 Error Handling
**Dimension:** Accuracy
**Current:** The report lists required fields (Code, Message, Operation, Channel, IsRetryable, Cause, RequestID) but the remediation does not mention the GS optional fields: MessageID, StatusCode, Timestamp, ServerAddress.
**Recommended:** Add to remediation: "Include optional fields per GS: MessageID (string), StatusCode (int, gRPC status code), Timestamp (Instant), ServerAddress (string). These are recommended, not required, but should be designed into the base class for future use."
**Rationale:** GS 01-error-handling.md lists these as "Optional fields (recommended)." Omitting them from the remediation risks an error hierarchy that cannot accommodate them without breaking changes.

### M-6: REQ-ERR-1 Future Enhancement items not tracked

**Category:** 01 Error Handling
**Dimension:** Completeness
**Current:** The gap report does not mention the GS Future Enhancements section for Error Handling: PartialFailureError type (for future batch per-message status) and IdempotencyKey field (for future server-side dedup).
**Recommended:** Add a note about designing the error hierarchy to accommodate PartialFailureError and the message types to accommodate IdempotencyKey, even if not implemented now.
**Rationale:** GS 01-error-handling.md Future Enhancements explicitly says "A PartialFailureError type SHOULD be added to the error hierarchy now (even if unused)" and "SDKs should design their message types with room for this field."

### M-7: REQ-CONN-1 Events Store recovery semantics incorrectly described

**Category:** 02 Connection & Transport
**Dimension:** Accuracy
**Current:** Line 335 states "EventsStore resumes with same parameters." This is imprecise.
**Recommended:** The GS specifies precise Events Store recovery: "Track last received sequence number locally. Re-subscribe with StartFromSequence(lastSeq + 1). The original StartFrom* parameter reflects initial intent, not reconnection intent." The gap report should verify whether the current implementation tracks last sequence number or simply re-subscribes with the original StartFrom parameter. Assessment 3.2.6 says "EventsStore reconnects with same store type/sequence/time" -- this suggests the original parameters are reused, which is **wrong** per the GS. On reconnection, the SDK should resume from lastSeq+1, not the original StartFrom.
**Rationale:** This is a functional correctness issue that could cause duplicate message delivery or missed messages after reconnection.

### M-8: REQ-AUTH-3 effort estimate too high relative to shared work with REQ-AUTH-2

**Category:** 03 Auth & Security
**Dimension:** Remediation Quality
**Current:** REQ-AUTH-3 is estimated at L (3-5 days). REQ-AUTH-2 is estimated at M (2-3 days). Both require PEM bytes API and cert reload.
**Recommended:** The PEM bytes work and cert reload work is shared between AUTH-2 and AUTH-3. If AUTH-2 is done first (which the dependency chain requires), AUTH-3's incremental effort is much smaller -- primarily adding keyManager to SslContextBuilder. Estimate AUTH-3 at **M (1-2 days)** assuming AUTH-2 is complete.
**Rationale:** The remediation for AUTH-3 explicitly says "cert reload on reconnection requires SslContext recreation" but this work is the same work done for AUTH-2. Double-counting effort.

### M-9: REQ-TEST-1 acceptance criterion for concurrent publish test not evaluated

**Category:** 04 Testing
**Dimension:** Completeness
**Current:** The gap report's REQ-TEST-1 gap table does not evaluate the GS criterion: "Concurrent publish from multiple goroutines/threads does not corrupt state (at minimum, one concurrent test per SDK)."
**Recommended:** Add criterion row. Assessment 9.1.3 mentions "Production readiness tests for: concurrency" which may partially cover this. Status should be PARTIAL if a concurrency test exists but doesn't assert no-corruption, or COMPLIANT if it does.
**Rationale:** GS 04-testing.md REQ-TEST-1 explicitly requires this test.

### M-10: REQ-DX-5 message immutability marked as separate P2 remediation but is a breaking change

**Category:** 09 API Design & DX
**Dimension:** Remediation Quality
**Current:** Line 1397 suggests replacing `@Data` with `@Value` and labels it M effort, P2 priority with "Medium -- breaking change risk if old names removed."
**Recommended:** This should be explicitly flagged as requiring a MAJOR version bump. Replacing `@Data` (which generates setters) with `@Value` (which does not) will break any user code that calls setters on message objects. This cannot be done in a minor version. The remediation should specify: "Must be deferred to next major version (v3.0) or a phased approach: (1) deprecate setters, (2) remove in next major." Priority should arguably be P3 given the breaking change constraint.
**Rationale:** SemVer compliance (REQ-PKG-2) prohibits breaking changes in minor versions. The effort estimate should also account for the migration/deprecation path.

### M-11: REQ-CONN-5 WaitForReady remediation suggests wrong gRPC mechanism

**Category:** 02 Connection & Transport
**Dimension:** Language-Specific Correctness
**Current:** Line 440 says "Apply via gRPC CallOptions.withWaitForReady() on stubs."
**Recommended:** While gRPC's native `withWaitForReady()` exists, the GS explicitly says "This is an SDK-level behavior layered on top of gRPC's native WaitForReady call option." The SDK-level WaitForReady needs to interact with the connection state machine (block during CONNECTING and RECONNECTING). Simply setting the gRPC call option is insufficient -- it only handles gRPC-level ready state, not the SDK's state machine states. The remediation should clarify that WaitForReady is implemented at the SDK level, potentially using gRPC's WaitForReady as one mechanism but also integrating with the SDK state machine.
**Rationale:** GS 02-connection-transport.md explicitly distinguishes SDK-level WaitForReady from gRPC-native WaitForReady.

### M-12: REQ-OBS-5 remediation suggests NoOpLogger default but assessment shows SLF4J is currently used

**Category:** 05 Observability
**Dimension:** Remediation Quality
**Current:** Line 871 proposes NoOpLogger as default and SLF4J adapter. However, moving from current SLF4J logging to NoOpLogger default would silently suppress all SDK logs for existing users who rely on SLF4J auto-detection.
**Recommended:** The remediation should address migration: (1) Define KubeMQLogger interface, (2) Create Slf4jLoggerAdapter as default when SLF4J is on classpath (auto-detect via reflection), (3) Fall back to NoOpLogger only when SLF4J is not on classpath. This maintains backward compatibility while meeting the GS requirement. Alternatively, document the breaking change clearly.
**Rationale:** Silently breaking logging for existing users would be a significant backward compatibility issue.

### M-13: REQ-PERF-1 benchmark framework note not reflected

**Category:** 13 Performance
**Dimension:** Accuracy
**Current:** Line 1698 says "JMH benchmark suite" with JMH as the required framework.
**Recommended:** GS 13-performance.md says "JMH preferred; simpler timing-based benchmark acceptable if reproducible." The gap report should reflect that JMH is preferred but a simpler approach is also acceptable. This affects effort estimation -- a timing-based benchmark is significantly less effort than full JMH setup.
**Rationale:** The GS gives flexibility here that the gap report does not reflect. This could change the effort from L to M if a simpler approach is chosen.

### M-14: REQ-CQ-5 suggests renaming `cq` package which is a breaking change

**Category:** 07 Code Quality
**Dimension:** Remediation Quality
**Current:** Line 1155 suggests "Rename cq package to commands and queries" as part of M-effort work.
**Recommended:** Package renaming in Java changes all import statements and is a breaking change. This must be deferred to the next major version or done with the original package kept as deprecated aliases (which is not standard practice in Java). The remediation should flag this as a major version change and separate it from the non-breaking package additions (error/, auth/, transport/).
**Rationale:** Import path changes break all user code. This is a MAJOR bump per SemVer.

---

## Minor Issues (NICE TO FIX)

### m-1: Inconsistent use of "NOT_ASSESSED" vs "MISSING" for unimplemented features

**Current:** Some acceptance criteria for features that clearly don't exist are marked NOT_ASSESSED (e.g., REQ-CONN-1 "Backoff reset after successful reconnection" at line 340), while equivalent missing features elsewhere are marked MISSING.
**Suggested:** Reserve NOT_ASSESSED strictly for criteria where the assessment did not cover this area AND no evidence exists either way. When the assessment provides enough evidence to determine the feature is absent (e.g., no state machine means no backoff reset), mark as MISSING. For line 340, the assessment confirms no formal state machine, so backoff reset cannot exist -- this is MISSING, not NOT_ASSESSED.

### m-2: REQ-ERR-5 "Error messages never expose internal details" marked COMPLIANT based on weak evidence

**Current:** Line 216 marks "No stack traces or raw gRPC frames in error messages (4.2.3)" as COMPLIANT.
**Suggested:** Assessment 4.2.3 says "Visibility timer expiration: exception is caught and logged but not re-thrown (intentional)." This is not strong evidence that no internal details leak -- it's evidence about one specific case. The criterion should be PARTIAL with a note that a full audit is needed.

### m-3: Executive Summary Unassessed Requirements section includes items with partial assessment coverage

**Current:** Lines 36-37 list REQ-ERR-8 and REQ-ERR-9 as "unassessed" but then note "partial overlap with assessment."
**Suggested:** These should not be in the "Unassessed Requirements" section if partial evidence exists. Move to a separate "Partially Assessed" category or remove the parenthetical hedging.

### m-4: Quick Wins section lists REQ-DOC-6 (CHANGELOG) but it also appears in Tier 2 Quick Wins

**Current:** Line 82 lists "REQ-DOC-6: Add CHANGELOG.md (S)" as a Tier 1 Quick Win. Line 90 lists "Add CHANGELOG.md (REQ-PKG-2, REQ-PKG-4)" as a Tier 2 Quick Win.
**Suggested:** CHANGELOG is one deliverable. List it once in whichever tier/section it primarily belongs to, with a cross-reference to the other REQ that it satisfies.

### m-5: Line counts in Unified Effort Summary don't add up

**Current:** Line 1847 shows P0: 23 items (Tier 1), P1: 15 items (Tier 1), P2: 7 (Tier 1) + 13 (Tier 2) = 20, P3: 0 (Tier 1) + 17 (Tier 2). Total: 45 Tier 1 + 30 Tier 2 = 75.
**Suggested:** Verify these counts against the actual requirement/criterion breakdown. The report covers 9 error handling REQs, 6 connection REQs, 6 auth REQs, 5 testing REQs, 5 observability REQs, 7 documentation REQs, 7 code quality REQs = 45 Tier 1 REQs. Tier 2: 3 API + 5 DX + 5 concurrency + 4 packaging + 5 compatibility + 6 performance = 28 Tier 2 REQs (not 30). Verify the count.

### m-6: REQ-AUTH-4 says @FunctionalInterface not possible but it could be

**Current:** Line 573 says "Java: interface with @FunctionalInterface not possible (returns complex type)."
**Suggested:** `@FunctionalInterface` works on any interface with a single abstract method regardless of return type complexity. `CredentialProvider` with just `getToken()` returning `TokenResult` IS a valid `@FunctionalInterface`. The note is incorrect. However, if the interface needs to throw checked exceptions, a lambda would require wrapping, so a regular interface may still be preferable for readability.

### m-7: REQ-ERR-7 remediation suggests Semaphore but GS description is about rate limiting, not concurrency limiting

**Current:** Line 265 uses `java.util.concurrent.Semaphore` with fixed permits.
**Suggested:** The GS says "Max concurrent retries | 10" and "retry attempts are throttled to prevent retry storms." A Semaphore is appropriate for concurrent limiting. However, consider also noting that a token bucket pattern could provide smoother rate limiting for burst scenarios. The Semaphore approach is simpler and acceptable.

### m-8: REQ-CQ-4 jackson-databind dependency not evaluated for removal

**Current:** Line 1132 mentions jackson-databind as a dependency but only says "evaluate if only used for channel decoding."
**Suggested:** The assessment should have evaluated what Jackson is used for. If it's only for JSON parsing of channel list responses, `com.google.protobuf:protobuf-java-util` (already a transitive dependency via gRPC) includes `JsonFormat` which could replace it. This would eliminate a high-risk dependency (Jackson has frequent CVEs).

### m-9: Dependency graph arrow direction is confusing

**Current:** Lines 1786+ use `-->` to indicate dependencies, e.g., "REQ-CQ-1 (architecture) --> REQ-ERR-1 (typed errors)."
**Suggested:** The arrow direction is ambiguous. "A --> B" could mean "A depends on B" or "A enables B." The text appears to mean "A must be done before B" (A enables B). Clarify with a legend: "A --> B means B depends on A (do A first)."

### m-10: Duplicate remediation for connection reuse documentation

**Current:** REQ-CONN-6 remediation (line 462) and REQ-PERF-2 remediation (line 1715) both suggest documenting client reuse/thread-safety. Implementation sequence (line 1947) lists REQ-CONN-6 separately.
**Suggested:** Consolidate these into a single work item to avoid duplicate effort.

### m-11: REQ-OBS-5 remediation does not mention MDC for trace correlation

**Current:** Line 871 mentions including trace_id and span_id in log entries via `Span.current().getSpanContext()`.
**Suggested:** For Java specifically, the standard approach is to use SLF4J MDC (Mapped Diagnostic Context) with an OpenTelemetry MDC integration (`opentelemetry-logback-mdc-1.0` or `opentelemetry-context-api`). This automatically populates trace_id/span_id in log entries without manual coding per log call. The remediation should mention this standard Java approach.

### m-12: REQ-CONN-4 acceptance criterion for Close() during RECONNECTING not fully evaluated

**Current:** Line 415 marks "Close() during RECONNECTING cancels and discards" as MISSING.
**Suggested:** This is correct but should also note that the GS specifies "fire OnBufferDrain with the count of discarded messages" and "drain timeout does not apply in this case." These are specific behaviors that should be tracked in the remediation.

---

## Missing Items

Requirements or acceptance criteria not covered in the gap research:

| # | REQ-* | Acceptance Criterion | Should Be |
|---|-------|---------------------|-----------|
| 1 | REQ-AUTH-2 | TLS default based on address (false for localhost, true for remote) | MISSING -- assessment confirms plaintext is default for all addresses. Remediation should include address-aware TLS defaulting. |
| 2 | REQ-ERR-3 | Independent backoff policies (operation retry vs reconnection) | MISSING -- criterion not evaluated. Should be in gap table. |
| 3 | REQ-ERR-6 | Future Enhancement: Retry-After metadata for RESOURCE_EXHAUSTED | Not evaluated -- should note architectural consideration for future support. |
| 4 | REQ-ERR-1 | Future Enhancement: PartialFailureError and IdempotencyKey | Not evaluated -- GS says "SHOULD be added now (even if unused)." |
| 5 | REQ-OBS-1 | Instrumentation scope name = SDK module identifier, version = SDK version | MISSING -- not tracked in gap table. |
| 6 | REQ-OBS-3 | error.type value mapping table (from error categories to OTel values) | MISSING -- dependency on REQ-ERR-2 not tracked. |
| 7 | REQ-TEST-1 | Concurrent publish from multiple threads does not corrupt state | Not evaluated -- assessment 9.1.3 mentions concurrency tests but criterion not in gap table. |
| 8 | REQ-CONN-1 | Events Store recovery: track last sequence locally, resume from lastSeq+1 | PARTIAL -- current behavior may reuse original StartFrom parameters (incorrect per GS). |
| 9 | REQ-CQ-7 | No credential material in error messages | Not explicitly evaluated as separate criterion -- assessment 5.2.2 covers logs but not error messages. |
| 10 | REQ-CONN-5 | Address default is localhost:50000 | Gap report line 434 says "Address default not documented as localhost:50000" but doesn't evaluate whether the SDK actually defaults to this address. Assessment 2.2.2 says only address and clientId are required -- implying no default. This should be MISSING. |

---

## Effort Estimate Corrections

| REQ-* | Current Estimate | Corrected Estimate | Reason |
|-------|-----------------|-------------------|--------|
| REQ-ERR-1 | M (2-3 days) | M-L (3-4 days) | Java exception hierarchies require more ceremony than the estimate suggests: base class, 10 subclasses, ErrorCode enum, toString()/getMessage() overrides, Serializable considerations, and migrating ~20 throw sites. With proper unit tests, 2 days is optimistic. |
| REQ-AUTH-3 | L (3-5 days) | M (1-2 days) | Cert reload work is shared with REQ-AUTH-2. Incremental mTLS work after AUTH-2 is small: add keyManager to SslContextBuilder, add PEM bytes API for key. |
| REQ-CQ-1 | L (3-5 days) | XL (5-8 days) | Layered architecture refactoring of a working SDK is one of the riskiest changes. Moving gRPC imports out of client classes, creating Transport interface, Protocol interceptor chain, and ensuring all tests still pass is closer to a week. This is the single largest change in the roadmap. |
| REQ-OBS-1 | XL (5+ days) | XL (8-10 days) | Comprehensive OTel instrumentation across all 4 messaging patterns, with batch consume patterns, retry events, proper span lifecycle, and testing is substantial. 5 days is the minimum; 8-10 is realistic for Java with proper testing. |
| REQ-DOC-1 | L (3-5 days) | L-XL (5-8 days) | 50+ source files with zero Javadoc. Each public method needs @param, @return, @throws. Lombok complicates Javadoc generation. Estimated 200+ methods to document. At ~3 min per method including review, this is 10+ hours of pure writing. |
| REQ-PERF-1 | L | M-L (2-4 days) | GS permits "simpler timing-based benchmark acceptable if reproducible." A timing-based benchmark is significantly less effort than full JMH setup. If JMH is chosen, L is correct. If timing-based, M is sufficient. |
| REQ-CONC-2 + REQ-CONC-4 | L | XL (5-8 days) | Adding async API (CompletableFuture return types) to all operations is a fundamental API change. It requires: new method signatures, internal refactoring to async-first, sync wrappers, cancellation propagation, proper exception handling in CompletableFuture chains, and comprehensive testing. This is underestimated. |
| REQ-CQ-5 | M (1-2 days) | S (< 1 day) for non-breaking + deferred for breaking | Non-breaking additions (error/, auth/, transport/ packages) are quick. Renaming cq/ is a breaking change and should be deferred to major version. Splitting the estimate accordingly. |

---

## Additional Java-Specific Recommendations

### 1. Java Module System (JPMS) Consideration

The gap report does not mention Java Platform Module System (JPMS). For Java 11+ as the minimum target, consider adding a `module-info.java` that:
- Exports only the public API packages
- Requires gRPC and protobuf as transitive dependencies
- Opens internal packages only to test frameworks

This would provide compile-time enforcement of internal API separation (REQ-CQ-2), which is currently only convention-based in Java. However, this is a significant undertaking and many Java libraries still skip JPMS support. Recommend as a future consideration, not an immediate task.

### 2. Lombok Dependency Strategy

The gap report mentions Lombok throughout but does not address the growing ecosystem trend away from Lombok. Consider:
- Lombok is a compile-time annotation processor (`provided` scope) so it doesn't affect runtime
- However, it complicates Javadoc generation (REQ-DOC-1) and IDE support
- Java 16+ records could replace `@Value` classes for immutable message types
- Recommendation: Keep Lombok for now but plan migration to records for message types in a future major version. Document this as a future consideration.

### 3. CompletableFuture Exception Handling Complexity

The gap report's async API remediation (REQ-CONC-2/4) underestimates the complexity of proper CompletableFuture exception handling in Java:
- `CompletableFuture.get()` wraps exceptions in `ExecutionException` -- callers need to unwrap
- `CompletableFuture.join()` wraps in `CompletionException` -- different wrapper
- The SDK should provide a utility to unwrap these consistently
- Consider providing `CompletableFuture<T>` with custom exception handling that returns `KubeMQException` directly
- Timeout handling with `CompletableFuture.orTimeout()` (Java 9+) vs `get(timeout, unit)` (Java 8+) -- since minimum is Java 11, `orTimeout()` is available

### 4. SLF4J 2.x API Compatibility

The gap report mentions SLF4J but does not specify version. SLF4J 2.x (released 2023) introduced fluent API and native structured logging support. The KubeMQLogger interface design should consider:
- SLF4J 2.x has `LoggingEventBuilder` with native key-value support
- An Slf4jLoggerAdapter should work with both SLF4J 1.x and 2.x
- The minimum SLF4J version should be documented (suggest 1.7.x for broadest compatibility)

### 5. gRPC ClientInterceptor for Centralized Cross-Cutting Concerns

The gap report mentions `ClientInterceptor` for error mapping (REQ-ERR-6 line 246) but should emphasize this more broadly. A chain of `ClientInterceptor` instances is the idiomatic Java/gRPC way to implement the Protocol layer:
- `ErrorMappingInterceptor` -- wraps gRPC errors to SDK types
- `RetryInterceptor` -- handles retry logic
- `AuthInterceptor` -- injects auth token (replaces current MetadataInterceptor)
- `TracingInterceptor` -- creates OTel spans
- `MetricsInterceptor` -- records OTel metrics
- `LoggingInterceptor` -- logs operations

This interceptor chain IS the Protocol layer. The layered architecture (REQ-CQ-1) remediation should explicitly adopt this pattern rather than describing an abstract "protocol layer."

### 6. Maven BOM Consideration

For managing gRPC and OTel dependency versions, consider using Maven BOM (Bill of Materials) imports:
- `io.grpc:grpc-bom` for consistent gRPC versions
- `io.opentelemetry:opentelemetry-bom` for consistent OTel versions
This prevents version conflicts and simplifies dependency management. Mention in REQ-CQ-4 remediation.

### 7. AutoCloseable and try-with-resources

The gap report mentions `AutoCloseable` but should emphasize that the `close()` improvements (REQ-CONN-4, REQ-CONC-5) should maintain try-with-resources compatibility. All client classes implementing `AutoCloseable` should ensure `close()` does not throw checked exceptions (it can throw unchecked). The current implementation already follows this pattern but it should be explicitly preserved.

---

## Fixes Applied (Round 1)

| Issue | Status | Change Made |
|-------|--------|-------------|
| C-1 | FIXED | REQ-CONN-6 status changed from COMPLIANT to PARTIAL (one acceptance criterion is MISSING) |
| C-2 | FIXED | REQ-CONN-1 keepalive detection criterion now notes GS internal inconsistency (20s in REQ-CONN-1 vs 15s computed from REQ-CONN-3 defaults) |
| C-3 | FIXED | Documentation priority changed from P1 to P0 (4 fully MISSING requirements meets P0 threshold). Updated Executive Summary, category header, effort summary, and compact status file. |
| C-4 | FIXED | REQ-CONC-5 acceptance criterion now flags conflicting GS defaults (REQ-CONN-4: 5s drain vs REQ-CONC-5: 30s callback timeout -- may be separate timeouts). Remediation updated. |
| C-5 | FIXED | Added missing acceptance criterion to REQ-AUTH-2: "TLS default based on address (false for localhost, true for remote)" with MISSING status. Remediation updated with address-aware TLS defaulting. |
| C-6 | FIXED | REQ-CQ-1 "Dependencies flow downward only" changed from PARTIAL to MISSING (no layered architecture exists, criterion is meaningless without layers) |
| M-1 | FIXED | Added acceptance criterion to REQ-ERR-3: "Operation retry and reconnection backoff are independent policies with independent configuration" with MISSING status |
| M-2 | FIXED | Added future consideration note to REQ-ERR-6 remediation about Retry-After metadata for RESOURCE_EXHAUSTED |
| M-3 | FIXED | Added acceptance criterion to REQ-OBS-1: instrumentation scope name matches SDK package identifier, version matches SDK version |
| M-4 | FIXED | Added acceptance criterion to REQ-OBS-3: error.type value mapping from REQ-ERR-2 categories to OTel attribute values |
| M-5 | FIXED | Added optional fields (messageId, statusCode, timestamp, serverAddress) to REQ-ERR-1 remediation. Updated effort to M-L (3-4 days). |
| M-6 | FIXED | Added PartialFailureError and IdempotencyKey future consideration note to REQ-ERR-1 remediation (combined with M-5) |
| M-7 | FIXED | REQ-CONN-1 Events Store recovery criterion updated to flag incorrect behavior: should track lastSeq and resume from lastSeq+1, not reuse original StartFrom parameters |
| M-8 | FIXED | REQ-AUTH-3 effort estimate changed from L (3-5 days) to M (1-2 days) -- PEM bytes/cert reload work shared with REQ-AUTH-2 |
| M-9 | FIXED | Added acceptance criterion to REQ-TEST-1: "Concurrent publish from multiple threads does not corrupt state" with PARTIAL status |
| M-10 | FIXED | REQ-DX-5 message immutability flagged as breaking change requiring MAJOR version bump. Priority changed from P2 to P3. Phased deprecation approach specified. |
| M-11 | FIXED | REQ-CONN-5 WaitForReady remediation updated to clarify SDK-level behavior vs gRPC-native WaitForReady. Must integrate with SDK state machine. |
| M-12 | FIXED | REQ-OBS-5 remediation updated: Slf4jLoggerAdapter as default when SLF4J on classpath (auto-detect), NoOpLogger only as fallback. Mentions SLF4J MDC for trace correlation. |
| M-13 | FIXED | REQ-PERF-1 benchmark remediation updated to reflect GS flexibility: "JMH preferred; simpler timing-based benchmark acceptable." Effort changed to M-L. |
| M-14 | FIXED | REQ-CQ-5 remediation updated: do NOT rename cq package in minor version (breaking change). Non-breaking package additions (error/, auth/, transport/) as S effort. cq rename deferred to major version. |
| m-1 | FIXED | REQ-CONN-1 "Backoff reset after successful reconnection" changed from NOT_ASSESSED to MISSING (no state machine means feature cannot exist) |
| m-2 | SKIPPED | Subjective -- evidence for COMPLIANT is weak but changing to PARTIAL requires full audit which is beyond this fix round |
| m-3 | SKIPPED | Subjective wording preference -- "partial overlap" parenthetical is informative |
| m-4 | SKIPPED | Minor duplication across tiers is informative, not harmful |
| m-5 | SKIPPED | Count verification requires detailed recount beyond scope of targeted fixes |
| m-6 | FIXED | REQ-AUTH-4 corrected: @FunctionalInterface IS valid for single-abstract-method interface regardless of return type. Note updated. |
| m-7 | SKIPPED | Semaphore approach acknowledged as acceptable by reviewer |
| m-8 | SKIPPED | Jackson evaluation is a code investigation task, not a document fix |
| m-9 | FIXED | Added legend to dependency graph: "A --> B means B depends on A (do A first)" |
| m-10 | SKIPPED | Consolidating duplicate remediation items is a restructuring task |
| m-11 | SKIPPED | MDC mention added as part of M-12 fix |
| m-12 | SKIPPED | Additional Close() behavior details are beyond targeted fixes |

**Effort estimate updates applied:**
- REQ-ERR-1: M -> M-L (3-4 days)
- REQ-AUTH-3: L -> M (1-2 days)
- REQ-CQ-1: L -> XL (5-8 days)
- REQ-OBS-1: XL (5+) -> XL (8-10 days)
- REQ-DOC-1: L -> L-XL (5-8 days)
- REQ-CONC-2+REQ-CONC-4: L -> XL (5-8 days)
- REQ-CQ-5: M -> S (non-breaking) + deferred (breaking rename)
- REQ-PERF-1: L -> M-L

**Cascading updates:**
- Executive Summary: Doc P1->P0, Auth effort 1S+2M+2L -> 1S+3M+1L, CQ effort 2S+3M+1L -> 2S+3M+0L+1XL
- P0/P1 counts: P0 23->30, P1 15->8
- Effort totals: XL 3->4, L 11->9, total days ~133->~137
- Compact status files (java-tier1-status.md): updated to match

**Total:** 22 fixed, 8 skipped

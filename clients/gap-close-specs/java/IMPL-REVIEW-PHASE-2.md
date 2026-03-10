# Java SDK — Implementation QA Review (Phase 2)

**Date:** 2026-03-10
**Specs Reviewed:** 04-testing-spec.md (P1), 05-observability-spec.md (P1+P2-3), 08-api-completeness-spec.md, 09-api-design-dx-spec.md (P1)
**Code Review Issues:** 3 found, 3 fixed
**Simplification Changes:** 3
**Build Status:** PASS
**Test Status:** 1218 passed, 0 failed

## Issues Found and Fixed

### Important (3)
1. **sendQueuesMessages() used IllegalArgumentException instead of ValidationException** — Inconsistent with REQ-DX-4 mandate. Fixed to use ValidationException.builder().
2. **QueuesPollRequest retained unused @Slf4j annotation** — Left over from WU-8 modifications. Removed.
3. **Test assertions expected IllegalArgumentException after production code changed** — 2 tests in BatchQueueSendTest updated to expect ValidationException.

## Simplification Changes Applied

1. **LogContextProvider**: Eliminated duplicate OTel classpath detection, now uses OTelAvailability.isAvailable().
2. **KubeMQTracing**: Extracted newSpanBuilder() and addProducerLink() helpers to eliminate ~16 lines of duplication.
3. **KubeMQMetrics**: Extracted baseChannelAttrs() and retryAttrs() helpers for shared attribute building.

## Verification Checklist
- OTel lazy loading (J-11): PASS — only KubeMQTracing/KubeMQMetrics import OTel, loaded via Class.forName()
- Subscribe return types: PASS — all 4 methods return subscription handles
- ValidationException consistency: PASS (after fix)
- Logger migration (@Slf4j): PASS — only out-of-scope files retain @Slf4j
- Thread safety in observability: PASS
- Import correctness: PASS

## Issues Deferred
- QueuesClient.waiting() and pull() inline validation still uses IllegalArgumentException (pre-existing, not Phase 2 change)

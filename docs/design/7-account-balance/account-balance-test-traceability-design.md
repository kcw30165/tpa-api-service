# Account Balance Test and Traceability Design

**Feature:** 6.14 Account Balance  
**Baseline:** 27/08/2026  
**Status:** Consolidated design baseline, pending repository investigation  

## Investigation tests to discover/reuse

- Common header validation codes and targets.
- PortalAccessContext injection and Account-Ref ownership handling.
- ReferenceDate outcome mapping.
- APIM executor operation registration and architecture guards.
- RedisCacheKeyFactory and reactive Redis ports.
- Existing single-flight, CAS, set-if-absent, or in-flight coalescing pattern.

## Contract tests

- Currency omitted sends `curr:""`; supplied currency is forwarded.
- All context fields come from protected context.
- `response.data` has exactly one complete object.
- FE JSON uses Display Account and Sub-Display-Account naming.
- Currency selection maps correctly.
- One `asOfDate` only.

## Transformation tests

- Unique joins and orphan rejection.
- Minimum display-account sequence.
- All approved aggregates.
- Both view hierarchies and ordering.
- English, Chinese, and fallback descriptions.
- Hidden overview null behavior and portfolio omission.
- Factsheet blank/malformed omission.

## Metadata tests

- Cache key uses opaque env/trust/policy qualifier.
- Entry embeds `DEFAULT_ALL` or `POLICY_SPECIFIC` resolution.
- Cache hit avoids APIM.
- Cache miss loads and stores for 20 minutes.
- Targeted miss uses the same TAS semantics and updates the same entry.
- Identical duplicates deduplicate; conflicting duplicates fail.
- No startup call.
- No artificial precision maximum.
- Concurrent same-key misses do not corrupt state.

## Error tests

- Exact approved business mappings.
- Existing validation behavior reused.
- All remaining errors return 503, SYSTEM_ERROR, err.system.unavailable.
- Sensitive values are absent from logs.

## Idempotency tests

- A request without `Idempotency-Key` is accepted when all Account Balance headers are valid.
- No idempotency record, replay entry, or idempotency adapter is invoked.
- If repository investigation finds a method-agnostic global validator, capture that as a contradiction requiring architect review rather than changing this contract silently.

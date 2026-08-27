# Account Balance Decisions and Open Questions

**Feature:** 6.14 Account Balance  
**Baseline:** 27/08/2026  
**Status:** Consolidated design baseline, pending repository investigation  

## Confirmed

- Optional FE currency; APIM always receives `curr`, using empty string on initial load.
- One complete Account Balance object in successful `response.data`.
- Display Account and Sub-Display-Account terminology.
- Lazy Redis-first general metadata resolution.
- TAS decides general versus policy-specific precision from the actual request policy.
- Targeted calls use the same semantics.
- Embedded resolution marker in one cache entry.
- Twenty-minute YAML TTL.
- No Java precision maximum.
- Valid factsheet URL returned; malformed URL safely omitted.
- Final business and system error mappings.

## Open pending repository investigation

1. Exact common header validation classes, codes, and targets.
2. Exact RedisCacheKeyFactory API and opaque qualifier convention.
3. Existing reactive Redis serialization/update ports.
4. Existing single-flight or concurrent-miss pattern.
5. APIM operation ledger and architecture-test update points.
6. Existing optional-field JSON omission setup for portfolio fields.
7. Existing public error `type` used for page/global errors.

## Design implication

These are physical implementation questions, not unresolved business rules. They should be closed by repository investigation before freezing class names and implementation subtasks.

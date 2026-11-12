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

## Idempotency decision

- Confirmed: `Idempotency-Key` is not part of the Account Balance FE-BFF contract.
- Confirmed: repeated GET requests are naturally safe and no Account Balance idempotency store is introduced.
- Investigation item only: verify whether any current global filter incorrectly mandates Idempotency-Key for every HTTP method and report it as a repository constraint, not as an approved feature requirement.

## Phase I Repository Ratification

The repository investigation ratifies these physical boundaries:

- `X-Request-Id` is the only header universally rejected when missing by the shared pre-controller mandatory-header filter. Only `X-Request-Id` is propagated to APIM.
- `Accept-Language` is optional. When supplied, the existing shared language resolver selects English or Traditional Chinese TAS descriptions. Missing, blank, or unsupported values resolve to English. Account Balance adds no language parser and does not return `VALIDATION_FAILED` for an absent language.
- `Account-Ref` is required by the existing selected-account context-resolution path, not by an Account Balance controller validator. `PortalAccessContextWebFilter` resolves and publishes `PortalAccessContext` to Reactor Context and exchange attributes; `CurrentPortalAccessContextResolver.current()` is the controller reuse point. `Account-Ref` is neither sent to APIM nor echoed to FE.
- `Idempotency-Key` is absent from the naturally repeatable Account Balance GET contract. No idempotency store, replay record, or idempotency adapter is introduced.
- `MutationResponse` is the target public envelope. `ApiResponse` is obsolete and must not be introduced.
- Technical failures use HTTP 503, `SYSTEM_ERROR`, `err.system.unavailable`, and `errors[].type=SYSTEM`. `PAGE` and `GLOBAL` are not error types.
- Portfolio omission uses scoped Jackson inclusion on Account Balance response DTO fields. Global `ObjectMapper` inclusion must not change. Hidden overview `vestedBalance` and `pendingSwitchIn` remain present as JSON null; hidden `fxRate` and blank or malformed `factsheetUrl` are omitted.
- `ContributionController#getContributionSummary` and `ContributionControllerTest` are the primary physical controller and test patterns. Account Balance preserves its optional `currency`, selected-account context, optional language fallback, both result views, and absence of `Idempotency-Key`; contribution business DTOs and logic are not copied.

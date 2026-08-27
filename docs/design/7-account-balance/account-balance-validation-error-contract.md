# Account Balance Validation and Error Contract

**Feature:** 6.14 Account Balance  
**Baseline:** 27/08/2026  
**Status:** Consolidated design baseline, pending repository investigation  

## Validation

The shared pre-controller mandatory-header filter validates X-Request-Id. Accept-Language is optional, and Account-Ref is handled by selected-account context resolution. Optional currency is validated only when present. Validation failure uses existing `VALIDATION_FAILED` codes and targets and invokes no feature downstream capability.

Only X-Request-Id is propagated to APIM. Accept-Language drives BFF localization. Account-Ref resolves context. Idempotency-Key is not part of the Account Balance GET contract and no feature idempotency store is introduced.

## Business mappings

```text
err.tpa.account.invalid -> BUSINESS_REJECTED / err.ab.account.not.exist
err.tpa.member.not.found -> BUSINESS_REJECTED / err.ab.account.mismatch
```

These exact approved pairings are locked by tests.

## System mapping

All other errors map to HTTP 503, `SYSTEM_ERROR`, `err.system.unavailable`, including TAS required-field errors, ReferenceDate failure, APIM failure, invalid array cardinality, join violations, visible missing/sentinel values, metadata failures, and missing effective precision.

Malformed factsheet URL is the exception: log safely, omit, continue success.

## Account Balance header profile

Required headers are:

```text
X-Request-Id: required by shared mandatory-header filter
Accept-Language: optional, English fallback
Account-Ref: required by selected-account context resolution
Idempotency-Key: absent
```

`Idempotency-Key` is excluded because Account Balance is a read-only GET operation. Phase I investigates the existing GET validation path only to identify the correct reuse point and confirm there is no unavoidable platform-wide constraint.

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

### Ratified business translation

The approved mappings remain `err.tpa.account.invalid` to `BUSINESS_REJECTED / err.ab.account.not.exist` and `err.tpa.member.not.found` to `BUSINESS_REJECTED / err.ab.account.mismatch`. Raw TAS error strings are classified inside the integration boundary and never exposed. All unrecognized or technical failures use the shared SYSTEM response.

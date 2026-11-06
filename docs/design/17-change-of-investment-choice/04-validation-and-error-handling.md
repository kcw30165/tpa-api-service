# 04 Validation and error handling

## 1. Submit order of checks

1. Token gate (existing): 403 on any token problem; token consumed.
2. Body validation (stateless).
3. Snapshot read (miss -> 503).
4. Snapshot validation.
5. Downstream.

All violations from one stage are reported together; later stages do not run.

## 2. Validation (HTTP 422, `VALIDATION_FAILED`, `result: null`)

Rendered by the CIM controller with the same override as `PersonalInformationController`
(`VALIDATION_FAILED` -> `UNPROCESSABLE_CONTENT`, D-30). Every error: `message: null`,
`severity: ERROR`, `source: SERVER`. Targets are request property paths with zero-based indexes (D-28).

| # | Rule | type | code | targets |
|---|---|---|---|---|
| V1 | body is a JSON object with only `switchSources` and `allocations`; correct JSON types | `FORM` | `err.investment-choice.request.invalid` (D-35) | `[]` |
| V2 | `switchSources` non-empty array of nonblank strings | `FIELD` | `err.investment-choice.switch-source.required` | `["switchSources"]` |
| V3 | no duplicate switch source | `FIELD` | `err.investment-choice.switch-source.duplicate` | `["switchSources[i]"]` |
| V4 | `allocations` non-empty array of `{fundCode, percentage}` only | `FIELD` | `err.investment-choice.allocation.required` | `["allocations"]` |
| V5 | `fundCode` nonblank | `FIELD` | `err.investment-choice.fund.required` | `["allocations[i].fundCode"]` |
| V6 | no duplicate `fundCode` | `FIELD` | `err.investment-choice.fund.duplicate` | `["allocations[i].fundCode"]` |
| V7 | `percentage` JSON integer 1..100 | `FIELD` | `err.investment-choice.percentage.invalid` | `["allocations[i].percentage"]` |
| V8 | sum of `percentage` = 100 | `CROSS_FIELD` | `err.investment-choice.percentage.total-invalid` | `["allocations"]` |
| S1 | `percentage % intervalPcnt == 0` | `FIELD` | `err.investment-choice.percentage.interval-invalid` | `["allocations[i].percentage"]` |
| S2 | source exists in snapshot and its `avail-sw` = true (D-21, D-37) | `FIELD` | `err.investment-choice.switch-source.unavailable` | `["switchSources[i]"]` |
| S3 | fund exists in snapshot and its `avail-sw` = true | `FIELD` | `err.investment-choice.fund.unavailable` | `["allocations[i].fundCode"]` |

New constants go to `shared/error/ErrorCodes` (`UPPER_SNAKE_CASE` constant, `err.investment-choice.<reason>` value).

V1-V8 also run at password-validation issuance for `CHANGE_INVESTMENT_CHOICE` (strict), rendered by
the existing issuance path (`err.password-validation.request.invalid`, existing status), so a
frontend bug fails before a token is issued.

## 3. Error ownership (D-36, F1 = A)

Rule: exactly one ERROR-level incident record per CIM incident, owned by CIM code through
`FailureEventPort` (`StructuredFailureEventLogger`, a ratchet-designated owner). CIM production code
contains **no** direct `log.error(` / `atError(` call, so `ErrorLogCallSiteRatchetArchitectureTest`
`KNOWN_REMAINING` is unchanged.

| Who | Does |
|---|---|
| CIM APIM adapters | On any failure of their exchange: classify with `ApimFailureClassifier.classify(throwable)`, map `ApimFailureCategory` -> `FailureOrigin` 1:1 (same names), publish one `FailureEvent`, then map to the feature exception. On nonblank `err-message`: publish origin `DOWNSTREAM_CONTRACT` with `internalCode` = the `err-message` code (D-34). On malformed data detected by the adapter: origin `DOWNSTREAM_INVALID_RESPONSE` |
| CIM use cases | Snapshot miss: publish origin `LOCAL_SYSTEM`. Reconciled / unreconciled: plain WARN (not an incident) |
| `InvestmentChoiceExceptionMappings` | Render the public 503 with `ApiError.system`; log level **below ERROR** (WARN) and no stack trace, so `ApiExceptionHandler` does not create a second ERROR. Sub-task 0.1 confirms a mapping can declare this; otherwise STOP |
| Shared executor | Unchanged. It still logs `apim.exchange.failed` at ERROR on transport/exchange failures and `apim.business.rejected` at WARN on `err-message`. That duplicate on transport failures is shared behaviour owned by exception-handling M3 (3.8), recorded as O-9; CIM does not change it |

`FailureEvent` fields for CIM: `capability` `INVESTMENT_CHOICE`; `operation` `TRPGetCIMDtl` or
`TRPCIMReq` (or `SNAPSHOT`); `publicCode` the 503 code; `httpStatus` 503; `retryable` true for
the GET, false for submit; `safeDimensions` only counts and reasons - never policy, certificate,
user id, account ref, fund codes or percentages.

Event names: `investment-choice.retrieval.downstream_failure`,
`investment-choice.submission.downstream_failure`, `investment-choice.submission.snapshot.missing`.

## 4. Error matrix

| Case | HTTP | status | type / code | Incident (ERROR) | Other logs |
|---|---|---|---|---|---|
| GET: transport / timeout / 5xx / crypto | 503 | `SYSTEM_ERROR` | `SYSTEM` / `err.investment-choice.retrieval.unavailable` | details adapter, classified origin | executor ERROR (O-9) |
| GET: nonblank `err-message` | 503 | `SYSTEM_ERROR` | same | details adapter, `DOWNSTREAM_CONTRACT` | executor WARN |
| GET: malformed (empty data, >1 item, missing required, integrity, strict date) | 503 | `SYSTEM_ERROR` | same | details adapter, `DOWNSTREAM_INVALID_RESPONSE` | - |
| GET: `AccountIdentifierFormatter` rejects policy/cert | 503 | `SYSTEM_ERROR` | same | use case, `LOCAL_SYSTEM` | - |
| GET: snapshot write skipped/failed | 200 | `SUCCESS` | - | none | WARN from cache service (D-31) |
| Submit: token problem | 403 | `BUSINESS_REJECTED` | existing gate | existing | existing |
| Submit: V1-V8, S1-S3 | 422 | `VALIDATION_FAILED` | section 2 | none | INFO |
| Submit: snapshot miss (absent, unreadable, unusable session, account mismatch) | 503 | `SYSTEM_ERROR` | `SYSTEM` / `err.investment-choice.submission.unavailable` | use case, `LOCAL_SYSTEM` | cache service INFO miss reason |
| Submit: nonblank `err-message` | 503 | `SYSTEM_ERROR` | same | instruction adapter, `DOWNSTREAM_CONTRACT` | executor WARN |
| Submit: unknown outcome, reconciled to one pending | 200 | `SUBMITTED` | - | instruction adapter (classified) | WARN reconciled; executor ERROR (O-9) |
| Submit: unknown outcome, not reconciled | 503 | `SYSTEM_ERROR` | same | instruction adapter (classified) | WARN unreconciled; executor ERROR (O-9) |
| Submit: reconciliation call fails | 503 | `SYSTEM_ERROR` | same | instruction adapter + details adapter (two operations) | executor ERRORs (O-9) |

Every failure: `success: false`, `result: null`, `messages: []`, `message: null`. Public responses
never expose `DOWNSTREAM_*`, `err-message` values, policy, certificate or user id.

## 5. Reserved eligibility slot (D-24)

`InvestmentChoiceEligibilityCodes` holds an empty mapping `err-message -> feature code`. When Progress
publishes eligibility codes, each mapped code becomes 422 `BUSINESS_REJECTED`, type `BUSINESS`,
`err.investment-choice.not-eligible.<reason>`, published with origin `DOWNSTREAM_BUSINESS` (WARN).
Filling the slot is a separate reviewed change (O-2).

## 6. Exception mappings

`InvestmentChoiceExceptionMappings`, registered in `ExceptionMappingRegistryConfig`,
`singleWithoutMessage` style (message null), `ApiError.system(...)` for 503s (type `SYSTEM`,
targets `[]`), log level per section 3.

## 7. Logging and privacy

- Register `POST /api/v1/investment-choice/instructions` as a confidential route (password-validation requirement).
- Never log: request or response bodies, token, percentages, fund codes, policy, certificate, user id, account ref.
- Allowed: request id, operation, `err-message` code, origin, reason, counts.

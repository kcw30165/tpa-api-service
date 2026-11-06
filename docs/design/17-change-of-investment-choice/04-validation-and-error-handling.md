# 04 Validation and error handling

Built on the exception-management capability merged with M2b/O-17 (design pack 15). CIM is a new
feature: it uses the shared APIM classifier and has exactly one incident ERROR owner per incident
from its first commit, so it adds nothing to `ErrorLogCallSiteRatchetArchitectureTest.KNOWN_REMAINING`.

## 1. Submit order of checks

1. Token gate (existing): 403 on any token problem; token consumed.
2. Body validation (stateless).
3. Snapshot present.
4. Snapshot validation.
5. Downstream.

All violations from one stage are reported together; later stages do not run.

## 2. Validation (HTTP 400, `VALIDATION_FAILED`, `result: null`)

Every error: `message: null`, `severity: ERROR`, `source: SERVER`. Targets are request property
paths with zero-based indexes (D-28).

| # | Rule | type | code | targets |
|---|---|---|---|---|
| V1 | body is a JSON object with only `switchSources` and `allocations`; correct JSON types | `FORM` | `err.request.invalid` (existing) | `[]` |
| V2 | `switchSources` non-empty array of nonblank strings | `FIELD` | `err.investment-choice.switch-source.required` | `["switchSources"]` |
| V3 | no duplicate switch source | `FIELD` | `err.investment-choice.switch-source.duplicate` | `["switchSources[i]"]` |
| V4 | `allocations` non-empty array of `{fundCode, percentage}` only | `FIELD` | `err.investment-choice.allocation.required` | `["allocations"]` |
| V5 | `fundCode` nonblank | `FIELD` | `err.investment-choice.fund.required` | `["allocations[i].fundCode"]` |
| V6 | no duplicate `fundCode` | `FIELD` | `err.investment-choice.fund.duplicate` | `["allocations[i].fundCode"]` |
| V7 | `percentage` JSON integer 1..100 | `FIELD` | `err.investment-choice.percentage.invalid` | `["allocations[i].percentage"]` |
| V8 | sum of `percentage` = 100 | `CROSS_FIELD` | `err.investment-choice.percentage.total-invalid` | `["allocations"]` |
| S1 | `percentage % intervalPcnt == 0` | `FIELD` | `err.investment-choice.percentage.interval-invalid` | `["allocations[i].percentage"]` |
| S2 | source exists in snapshot and its `avail-sw` = true (D-21, O-1) | `FIELD` | `err.investment-choice.switch-source.unavailable` | `["switchSources[i]"]` |
| S3 | fund exists in snapshot and its `avail-sw` = true | `FIELD` | `err.investment-choice.fund.unavailable` | `["allocations[i].fundCode"]` |

New codes go to the central `ErrorCodes` with the existing naming convention.

V1-V8 also run at password-validation issuance for purpose `CHANGE_INVESTMENT_CHOICE` (strict,
O-12 decided), rendered by the existing issuance error path (`err.password-validation.request.invalid`),
so a frontend bug fails before a token is issued.

## 3. Error matrix

| Case | HTTP | status | type / code | Log (one incident owner) |
|---|---|---|---|---|
| GET: transport / timeout / 5xx | 503 | `SYSTEM_ERROR` | `SYSTEM` / `err.investment-choice.retrieval.unavailable` | shared APIM classifier, ERROR |
| GET: nonblank `err-message` | 503 | `SYSTEM_ERROR` | same | ERROR, `err-message` code logged (a code, not personal data) |
| GET: empty `data`, >1 item, missing required, integrity, strict date | 503 | `SYSTEM_ERROR` | same | ERROR, invalid-response category |
| GET: `AccountIdentifierFormatter` rejects policy/cert | 503 | `SYSTEM_ERROR` | same | ERROR, local system origin |
| Submit: token problem | 403 | `BUSINESS_REJECTED` | existing gate rendering | existing gate event |
| Submit: V1-V8, S1-S3 | 400 | `VALIDATION_FAILED` | section 2 | INFO/WARN, no ERROR |
| Submit: snapshot missing | 503 | `SYSTEM_ERROR` | `SYSTEM` / `err.investment-choice.submission.unavailable` | ERROR `investment-choice.submission.snapshot.missing`, local system origin |
| Submit: snapshot store unreadable | 503 | `SYSTEM_ERROR` | same | ERROR, local system origin |
| Submit: nonblank `err-message` | 503 | `SYSTEM_ERROR` | same | ERROR, no reconciliation |
| Submit: unknown outcome, reconciled to one pending | 200 | `SUBMITTED` | - | WARN `investment-choice.submission.reconciled` |
| Submit: unknown outcome, not reconciled | 503 | `SYSTEM_ERROR` | same | ERROR, single owner (reconciliation step) |

Every failure: `success: false`, `result: null`, `messages: []`, `message: null`. Public responses
never expose `DOWNSTREAM_*`, `err-message` values, policy, certificate or user id.

## 4. Reserved eligibility slot (C2 = b)

`InvestmentChoiceEligibilityCodes` holds an empty mapping `err-message -> feature code`. When Progress
publishes eligibility codes, each mapped code becomes 422 `BUSINESS_REJECTED`, type `BUSINESS`,
`err.investment-choice.not-eligible.<reason>`, logged WARN as an expected business rejection
(422 follows `HttpStatusClassification`). Until then every code is 503. Filling the slot is a
separate reviewed change (O-2).

## 5. Exception mappings

`InvestmentChoiceExceptionMappings`, registered in `ExceptionMappingRegistryConfig`, using the
`singleWithoutMessage` style (message stays null) and `ApiError.system(...)` for 503s, so the type
is `SYSTEM` and targets are `[]` (not the `FORM` type some older 503s still emit).

## 6. Logging and privacy

- Register `POST /api/v1/investment-choice/instructions` as a confidential route
  (password-validation security requirement).
- Never log: request or response bodies, token, percentages, policy, certificate, user id.
- Allowed: request id, operation name, `err-message` code, category, counts.
- Snapshot content is never logged.

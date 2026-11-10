# 04 Validation and error handling

Sections 1-8 record M5 implementation behavior and its evidence. The approved
M6 target below is not implemented or runtime-verified; once separately
implemented, it supersedes the CIM validation location and execution ownership
described earlier. Historical M5 evidence is retained.

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

## 8. Cancel (M4b)

### 8.1 Validation (HTTP 422, `VALIDATION_FAILED`, `result: null`)

Rendered like the submit validation (controller override, D-30). Every error: `message: null`, `severity: ERROR`, `source: SERVER`.

| # | Rule | type | code | targets |
|---|---|---|---|---|
| CV1 | body is a JSON object with only `refNo`; `refNo` is a JSON string | `FORM` | `err.investment-choice.request.invalid` | `[]` |
| CV2 | `refNo` missing or blank | `FIELD` | `err.investment-choice.cancel.ref-required` | `["refNo"]` |
| CS1 | `refNo` not among the snapshot's pending references | `FIELD` | `err.investment-choice.cancel.ref-invalid` | `["refNo"]` |

CV1 takes precedence over CV2. `refNo` has no format rule (C6). CV1 and CV2 also run at password-validation issuance for `CANCEL_INVESTMENT_CHOICE` (strict, same renderer as the other purposes), and a parity test pins that issuance and the controller agree on every body.

### 8.2 Error matrix

| Case | HTTP | status | type / code | Incident (ERROR) | Other logs |
|---|---|---|---|---|---|
| Token problem | 403 | `BUSINESS_REJECTED` | existing gate rendering | existing gate event | existing |
| CV1, CV2, CS1 | 422 | `VALIDATION_FAILED` | section 8.1 | none | INFO |
| Snapshot miss (absent, unreadable, unusable session, account mismatch, stale shape) | 503 | `SYSTEM_ERROR` | `SYSTEM` / `err.investment-choice.cancel.unavailable` | use case, `LOCAL_SYSTEM`, operation `SNAPSHOT`, event `investment-choice.cancel.snapshot.missing` | cache service INFO miss reason |
| `err.request.cannot.be.cancelled` | 422 | `BUSINESS_REJECTED` | `err.investment-choice.cancel.not-cancellable` | none (WARN only): adapter publishes `DOWNSTREAM_BUSINESS` | executor WARN |
| Any other `err-message` | 503 | `SYSTEM_ERROR` | `SYSTEM` / `err.investment-choice.cancel.unavailable` | adapter, `DOWNSTREAM_CONTRACT` | executor WARN |
| Malformed success | 503 | `SYSTEM_ERROR` | same | adapter, `DOWNSTREAM_INVALID_RESPONSE` | - |
| Throwable (transport, crypto, contract, local) | 503 | `SYSTEM_ERROR` | same | adapter, classified origin | executor ERROR for transport (O-9) |
| Success | 200 | `SUCCESS` | - | none | snapshot removal best effort; a skipped write logs one WARN |

Every failure: `success: false`, `result: null`, `messages: []`, `message: null`. Public responses never expose `DOWNSTREAM_*`, `err-message` values, policy, certificate, user id, reference numbers or the token.

### 8.3 Events

Cancel adapter events: capability `INVESTMENT_CHOICE`, operation `TRPCnclCIMReq`, event `investment-choice.cancel.downstream_failure`, `retryable` false, `httpStatus` 503 (422 for the business rejection), `publicCode` `err.investment-choice.cancel.unavailable` (or `...not-cancellable`). Snapshot miss: operation `SNAPSHOT`, event `investment-choice.cancel.snapshot.missing`, origin `LOCAL_SYSTEM`. Exactly one event per failure. `safeDimensions` carry reasons only.

### 8.4 Logging and privacy

Never log: `refNo`, any pending reference number, the request or response body, the token, policy, certificate, user id, account ref, session id. Register `POST /api/v1/investment-choice/instructions/cancel` as a confidential route, with the same logging tests as the submit route.

### 8.5 New `ErrorCodes` constants

`INVESTMENT_CHOICE_CANCEL_UNAVAILABLE`, `INVESTMENT_CHOICE_CANCEL_NOT_CANCELLABLE`, `INVESTMENT_CHOICE_CANCEL_REF_REQUIRED`, `INVESTMENT_CHOICE_CANCEL_REF_INVALID`, with the values given in section 8.1 and 8.2.

## 9. Approved M6 purpose-owned prechecks (not implemented)

### 9.1 Issuance ordering and validation ownership

For `CHANGE_INVESTMENT_CHOICE`, the registered application policy runs V1-V8
and then S1-S3. For `CANCEL_INVESTMENT_CHOICE`, it runs CV1-CV2 and
pending-reference membership. These prechecks run during
`POST /api/v1/password-validation`, before throttle admission and password
verification. Each rule has one implementation in the policy; submit/cancel
execution does not rerun it. Generic envelope parsing/validation remains its
existing 400 behavior. Personal Information issuance and PUT behavior do not
change.

The minimum adapter-decoded representation preserves payload member presence,
JSON kinds, unknown member names and values so application policies can return
detailed violations. Do not introduce a general-purpose JSON framework;
Jackson remains in adapters. The same purpose decoder strictly decodes and
constructs the typed execution command without rerunning business validation.

### 9.2 M6 error and incident ownership

| Issuance outcome | HTTP / response | Incident ownership |
|---|---|---|
| CIM feature payload or snapshot-rule violation | 422 `VALIDATION_FAILED`, existing detailed CIM code and target | Expected violation; no technical incident |
| CIM snapshot miss/unavailable | Feature-specific 503 `SYSTEM_ERROR` (`submission.unavailable` or `cancel.unavailable`) | Exactly one feature-owned `LOCAL_SYSTEM` `FailureEvent`; shared issuance must not publish a duplicate |
| Generic issuance envelope error | Existing 400 contract | Existing shared behavior |
| Wrong password | Existing response unchanged | Existing password-validation behavior |

The feature policy owns snapshot-failure publication and classification. Shared
issuance must propagate this feature-owned failure without adding its own
incident. Existing downstream adapter events and post-success submit
reconciliation remain unchanged.

### 9.3 CIM execution after issuance authorization

The token gate continues to enforce single-use authorization and session,
account, purpose and exact-payload binding. The existing gate binding attribute
is mandatory at each CIM controller boundary and in its command. A
`PayloadDigest` alone does not establish authorization; the trusted fact is the
gate's successful scope/token consumption and canonicalizer-based body-binding
checks before dispatch. Missing binding or rejected gate checks prevent
dispatch. Equality remains the existing canonicalized payload equality, not
raw-byte equality.

Execution retains strict decoding, typed construction, downstream dispatch,
response mapping, submit reconciliation and existing successful snapshot
updates. It does not repeat CIM feature business prechecks.

Cancel execution performs no snapshot precheck and no pending-reference
membership revalidation. Snapshot access retained for post-success cleanup is
best effort only: absent/unreadable snapshot or cleanup failure cannot block
dispatch or change a successful downstream cancellation into failure.
Progress retains its existing current-state enforcement; do not assume Progress
repeats every Java validation rule. Tokens do not reserve business state.

No token scope/lifetime/consumption/canonicalization change, legacy-token
migration, grant versioning, rolling-deployment handling, snapshot concurrency
redesign, CAS refactor or GET fencing is included.

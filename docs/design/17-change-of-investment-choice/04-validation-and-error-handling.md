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

## 2. Validation codes (400 `VALIDATION_FAILED`, `type` `FIELD`)

| # | Rule | code | target |
|---|---|---|---|
| V1 | body is a JSON object with only `switchSources` and `allocations` | `err.investment-choice.request.invalid` | `PAGE` |
| V2 | `switchSources` non-empty array of nonblank strings | `err.investment-choice.switch-source.required` | `switchSources` |
| V3 | no duplicate switch source | `err.investment-choice.switch-source.duplicate` | `switchSources[i]` |
| V4 | `allocations` non-empty array of `{fundCode, percentage}` only | `err.investment-choice.allocation.required` | `allocations` |
| V5 | `fundCode` nonblank | `err.investment-choice.fund.required` | `allocations[i].fundCode` |
| V6 | no duplicate `fundCode` | `err.investment-choice.fund.duplicate` | `allocations[i].fundCode` |
| V7 | `percentage` JSON integer 1..100 | `err.investment-choice.percentage.invalid` | `allocations[i].percentage` |
| V8 | sum of `percentage` = 100 | `err.investment-choice.percentage.total-invalid` | `allocations` |
| S1 | `percentage % intervalPcnt == 0` | `err.investment-choice.percentage.interval-invalid` | `allocations[i].percentage` |
| S2 | source exists in snapshot and its `avail-sw` = true (D-21, B3) | `err.investment-choice.switch-source.unavailable` | `switchSources[i]` |
| S3 | fund exists in snapshot and its `avail-sw` = true | `err.investment-choice.fund.unavailable` | `allocations[i].fundCode` |

Index `i` is the zero-based array position in the request. New codes go to the central `ErrorCodes`.
The 400 status for field validation mirrors the existing token-gate `BODY_INVALID` rendering
(`ApiStatus.VALIDATION_FAILED`); sub-task 0 confirms the Personal Information PUT field-validation
status at HEAD and stops on a difference.

The same V1-V8 structure checks run at password-validation issuance for purpose
`CHANGE_INVESTMENT_CHOICE` (strict, O-12 decided), so a frontend bug fails before a token is issued.

## 3. Error matrix

| Case | HTTP | ApiStatus | code | nextStep | Log (one incident owner) |
|---|---|---|---|---|---|
| GET: transport / timeout / 5xx | 503 | SYSTEM_ERROR | `err.investment-choice.retrieval.unavailable` | RELOAD | shared APIM classifier, ERROR |
| GET: nonblank `err-message` | 503 | SYSTEM_ERROR | same | RELOAD | ERROR, `errMessage` code logged (codes are not personal data) |
| GET: empty `data`, >1 item, missing required, integrity, strict date | 503 | SYSTEM_ERROR | same | RELOAD | ERROR, invalid-response category |
| GET: `AccountIdentifierFormatter` rejects policy/cert | 503 | SYSTEM_ERROR | same | RELOAD | ERROR, `FailureOrigin.LOCAL_SYSTEM` |
| Submit: token problem | 403 | BUSINESS_REJECTED | `PASSWORD_VALIDATION_AUTHORIZATION_INVALID` | - | existing gate event |
| Submit: V1-V8, S1-S3 | 400 | VALIDATION_FAILED | table above | - | INFO/WARN, no ERROR |
| Submit: snapshot missing | 503 | SYSTEM_ERROR | `err.investment-choice.submission.unavailable` | RELOAD | ERROR `investment-choice.submission.snapshot.missing`, `LOCAL_SYSTEM` |
| Submit: snapshot store unreadable | 503 | SYSTEM_ERROR | same | RELOAD | ERROR, `LOCAL_SYSTEM` |
| Submit: nonblank `err-message` | 503 | SYSTEM_ERROR | same | RELOAD | ERROR, no reconciliation |
| Submit: unknown outcome, reconciled to one pending | 200 | SUCCESS | - | - | WARN `investment-choice.submission.reconciled` |
| Submit: unknown outcome, not reconciled | 503 | SYSTEM_ERROR | same | RELOAD | ERROR, single owner (reconciliation step) |

Public responses never expose `DOWNSTREAM_*` statuses, `err-message` values, policy, certificate or user id.

## 4. Reserved eligibility slot (C2 = b)

`InvestmentChoiceEligibilityCodes` holds an empty mapping `err-message -> feature code`. When Progress
publishes eligibility codes, each mapped code becomes 422 `BUSINESS_REJECTED`
`err.investment-choice.not-eligible.<reason>`, logged WARN as an expected business rejection. Until
then every code is 503 as above. Filling the slot is a separate, reviewed change (open item O-2).

## 5. Exception mappings

`InvestmentChoiceExceptionMappings`, registered manually in `ExceptionMappingRegistryConfig`, using the
`singleWithoutMessage` style (messages stay null). One mapping per feature exception type; no wrapping
exceptions only to carry metadata.

## 6. Logging and privacy

- Register `POST /api/v1/investment-choice/instructions` in `PasswordValidationRouteMatcher`'s confidential
  route list (security requirement of design 16 document 11 section 4).
- Never log: request or response bodies, token, percentages per fund, policy, certificate, user id.
- Allowed: request id, operation name, `err-message` code, category, counts (number of sources, funds).
- Snapshot content is never logged.

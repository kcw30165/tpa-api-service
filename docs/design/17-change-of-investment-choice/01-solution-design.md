# 01 Solution design

## 1. Scope

In scope (member portal, `ROLE_USER_MBR` only):

| Capability | BFF endpoint | Downstream |
|---|---|---|
| Show CIM page: account number, allowed step, switch sources, available funds, allocation history, pending instructions | `GET /api/v1/investment-choice` | `TRPGetCIMDtl` |
| Submit a change of investment instruction after password confirmation | `POST /api/v1/investment-choice/instructions` | `TRPCIMReq` |
| Cancel a pending instruction | `POST /api/v1/investment-choice/instructions/cancel` | `TRPCnclCIMReq`; password-confirmed (D-38, D-39) |

Out of scope for the BFF: employer portal, print, leave-page warning, stepper, Account Balance
widget (existing API), post-day-end e-notification, history filters (frontend filters the records
Progress returns), CIM type handling, labels and message texts (frontend owns all labels; Java
returns codes only).

CIM is not a workflow feature: responses carry no `nextStep`, `pageRef` or `actions`.

## 2. Responsibility split

| Concern | Owner |
|---|---|
| Eligibility (terminated O/P/S/T, service break, financial status F01/F13/F17/F22, function on/off, maintenance) | Progress |
| Duplicate / one-pending-per-source / identical-to-current rules | Progress |
| Cut-off and effective date | Progress |
| `interval-pcnt`, risk level, closed-fund flag, switch-source availability | Progress |
| History limited to latest 5 records | Progress |
| Date formatting (`dd/MM/yyyy`) and `InvestMandate.pcnt` string formatting | Progress |
| Password check and lockout | Progress via `TRPPwdValidation` (existing password-validation capability) |
| Account Number `{formatted-policyNo}-{formatted-certNo}` | Java, `AccountIdentifierFormatter.format` |
| Fund sorting, grouping, joining pending details | Java |
| Submit validation against the session snapshot | Java |
| History filter options, labels, messages, dropdown vs manual entry | Frontend |

## 3. Identity

All downstream identity comes from the trusted `PortalAccessContext(ActorContext actor, AccountContext account)`
resolved by `CurrentPortalAccessContextResolver` from the session and the `Account-Ref` header.

| Downstream field | Source |
|---|---|
| `policy-no` | `account.policyNo()` |
| `cert-no` | `account.certNo()` |
| `login-user-id` | `actor.actorUserId()` |
| `login-user-role` | `actor.actorUserRole().toApimValue()` (lowercase, e.g. `role_user_mbr`) |
| `ref-date` (TRPGetCIMDtl only) | `ReferenceDatePort.resolveReferenceDate(account.accountEnv())`, formatted with a local strict `dd/MM/uuuu` formatter |

The frontend never sends policy, certificate, user id or role. The controller requires a nonblank
`accountRef` exactly as `PersonalInformationController.requireAccountRef` does.

## 4. Components (hexagonal placement)

| Layer | Component (design role; exact names confirmed in sub-task 0.1) |
|---|---|
| web in | `InvestmentChoiceController` (GET, POST instructions), request/response DTOs, `InvestmentChoiceWebMapper` (fund ordering, display name, grouping output) |
| application | `GetInvestmentChoiceUseCase`, `SubmitInvestmentChoiceUseCase`, `InvestmentChoiceSubmissionValidator`, `InvestmentChoiceSnapshotCacheService` |
| application port out | `InvestmentChoiceDetailsPort` (TRPGetCIMDtl), `InvestmentChoiceInstructionPort` (TRPCIMReq); existing `FailureEventPort`, `AuthenticatedSessionPort`, `AuthenticatedSessionProtectorPort`, `TransientJsonPort` |
| adapter out apim | `ApimInvestmentChoiceDetailsAdapter`, `ApimInvestmentChoiceInstructionAdapter`, APIM DTOs and mappers |
| web error | `InvestmentChoiceExceptionMappings` registered in `ExceptionMappingRegistryConfig` |
| password validation | new purpose `CHANGE_INVESTMENT_CHOICE` (prerequisite milestone M1) |

Reused as-is: `MutationResponse`, `ApiStatus`, `ApiError` factories, `HttpStatusClassification`,
`AccountIdentifierFormatter`, `FundPriceRiskLevels` (application dto, framework-free),
`ReferenceDatePort`, `ApimExchangeExecutor`, `ApimFailureClassifier`, `FailureEventPort`,
`AuthenticatedSession.withFeatureState`, `AuthenticatedSessionConcurrencyService.updateIfUsable`.

## 5. Flow - GET

1. Resolve `PortalAccessContext` and reference date.
2. Call `TRPGetCIMDtl` (`SAFE_BUSINESS_CALL`).
3. Interpret the envelope per operation (not `ApimResponseValidator`): nonblank `err-message` or
   malformed/empty `data` -> feature exception, one `FailureEvent` (document 04).
4. Assemble the result: account number, interval, switch sources, funds, history, pending.
5. Write the submit snapshot (section 6). Best effort (D-31): a skipped or failed write never
   fails the GET.
6. Web mapper orders and shapes `result.data` (section 7). Return 200 `SUCCESS`.

## 6. Submit snapshot (session featureState, D-29)

Mirrors `PersonalInformationConfigCacheService` exactly; framework-free, not a Spring bean by
annotation.

| Property | Rule |
|---|---|
| Location | `AuthenticatedSession.featureState`, key `investment-choice.snapshot`; other features' entries untouched |
| Content | `accountRef`, `policyNo`, `intervalPcnt`, sources (`switch-src`, `avail-sw`), funds (`fund-code`, `avail-sw`), `pendingRefNos` (the distinct `ref-no` values of `PendingReq`). Nothing else (D-22 as amended by D-40) |
| Encoding | `TransientJsonPort`, private `Wire` record; unknown properties fail decoding = miss |
| Write | Every successful GET (and every successful reconciliation read), via `concurrency.updateIfUsable(sessionId, s -> s.withFeatureState(KEY, encoded), requestId)`; overwrite; never fails the caller; skip logged at WARN |
| Read | Submit only. Plain `sessions.load` + `protector.unprotect`; `isUsable(clock.instant())`; entry must `belongsTo(accountRef, policyNo)`. Every failure is a miss |
| Lifetime | Session lifetime (login idle/absolute TTL, PTTL preserved on update). Logout leaves state but makes the session unusable, so reads miss |
| Miss on submit | 503 `SYSTEM_ERROR`; the use case publishes one `FailureEvent` `investment-choice.submission.snapshot.missing` origin `LOCAL_SYSTEM` (D-23) |
| Logging | request id and reason only; never `accountRef`, `policyNo` or snapshot content |

## 7. Fund ordering (web mapper, D-32)

In `InvestmentChoiceWebMapper`, as Fund Price does in its web layer. Fund Price is not refactored.

1. `FundPriceRiskLevels.sortKey(riskLevel)` ascending, nulls last (null, blank, `"0"` = no risk level = highest).
2. Display name: language resolved with the existing `RequestLanguageResolver`; zh_HK ->
   `chin-fund-name` when nonblank, otherwise `fund-name`; any other language -> `fund-name`.
   English name is mandatory downstream (D-06).
3. Display name case-insensitive (`Locale.ROOT`), then `fund-code`.

Applied to the available-fund list, and the fund rows inside each history group and each pending instruction.

## 8. Flow - submit

```text
FE: POST /api/v1/password-validation {password, purpose: CHANGE_INVESTMENT_CHOICE, operationPayload: <exact submit body>}
    -> 200 result.data {token, expiresInSeconds}
FE: POST /api/v1/investment-choice/instructions  (password-validation token header, same body)
BFF:
 1. token gate consumes the token, binds session + Account-Ref + purpose + exact body (403 on mismatch)
 2. stateless body validation (422)
 3. read snapshot (miss -> 503, one FailureEvent)
 4. snapshot validation: interval multiple, source avail-sw, fund avail-sw (422)
 5. expand to CIMInstr = every source x every allocation
 6. TRPCIMReq (NON_RETRYABLE, never resubmitted)
 7. definite success -> 200 SUBMITTED result.data {referenceNo, submitDate, submitTime}
    err-message      -> 503 (no reconciliation)
    unknown outcome  -> reconciliation (section 9)
```

The token is consumed before validation (existing password-validation rule). Any 422 after step 1
requires the member to confirm the password again. This is accepted behaviour of the shared capability.

No `Idempotency-Key` (D-15): the single-use token prevents resubmission of the same confirmation.

## 9. Unknown-outcome reconciliation (D-17)

Trigger: the TRPCIMReq exchange did not produce a readable definite answer (transport/timeout
after send, downstream 5xx, response parse/crypto/mapping failure). The instruction adapter has
already published the incident `FailureEvent` for that exchange.

1. Call `TRPGetCIMDtl` once for the same account.
2. Candidates: `PendingReq` entries whose `submit-date` equals today (Asia/Hong_Kong) and whose
   joined `PendingReqDtl` rows equal exactly the submitted `(switch-src, fund-code, pcnt)` set.
3. Exactly one candidate -> 200 `SUBMITTED` with that `ref-no`, `submit-date`, `submit-time`;
   WARN log `investment-choice.submission.reconciled`.
4. Zero or several candidates -> 503 `err.investment-choice.submission.unavailable`; WARN log
   `investment-choice.submission.unreconciled` (the incident ERROR already exists). If the
   reconciliation call itself fails, the details adapter publishes its own `FailureEvent` (a
   separate operation incident) and the result is 503.
5. A successful reconciliation read refreshes the snapshot.

## 10. Flow - cancel (M4b, D-38 to D-43)

```text
FE: POST /api/v1/password-validation {password, purpose: CANCEL_INVESTMENT_CHOICE, operationPayload: {"referenceNo": "<ref>"}}
    -> 200 result.data {token, expiresInSeconds}
FE: POST /api/v1/investment-choice/instructions/cancel  (password-validation token header, body {"referenceNo": "<ref>"})
BFF:
 1. token gate consumes the token, binds session + Account-Ref + purpose + exact body (403 on mismatch)
 2. body validation: object with only referenceNo, a nonblank string (422)
 3. read snapshot (miss -> 503, one FailureEvent)
 4. referenceNo must be in the snapshot's pendingRefNos (else 422 ref-invalid; no downstream call, no event)
 5. TRPCnclCIMReq (NON_RETRYABLE, never resubmitted)
 6. success -> 200 SUCCESS result.data {referenceNo = the validated value}; remove the matching pending reference from the snapshot (best effort)
    err.request.cannot.be.cancelled -> 422 BUSINESS_REJECTED not-cancellable
    any other err-message, or any throwable -> 503 cancel.unavailable (no reconciliation)
```

- Progress enforces that the reference belongs to the member (D-43); Java does not call `TRPGetCIMDtl` before cancelling.
- The snapshot check is a Java-side guard: a cancel token can only be used for a reference this session has been shown by a GET.
- No reconciliation (D-41): after a timeout the outcome is unknown, and "no longer pending" cannot tell cancelled from executed. The frontend reloads the GET.
- A reference created by a definite submit success is not in the snapshot until the next GET (O-13).
- Snapshot removal after success is a best-effort rewrite through `updateIfUsable`; a concurrent GET may win, which only costs the member a reload.
- A stored snapshot without `pendingRefNos` (written before this change) fails decoding and is a miss.

## 11. Approved M6 purpose-owned prechecks (not implemented)

This section is the approved target design and supersedes the M5 submit/cancel
validation placement described in sections 8 and 10 only after a separately
authorized implementation. Sections 1-10 and their recorded evidence remain
the current M5 implementation description; this documentation does not claim
the target is implemented or runtime-verified.

### 11.1 Shared issuance composition

`POST /api/v1/password-validation` remains the only issuance HTTP request.
Shared issuance dispatches through registered purpose-specific payload
decoders and framework-free application precheck policies. It contains no
purpose branches in its parser or issuance algorithm. Adding a purpose requires
explicit purpose, protected-route and bean registration, not changes to shared
issuance logic. Composition fails closed if a supported purpose has a missing,
duplicate or mismatched decoder/policy registration.

The adapter decoder returns only the representation required by current
purposes: payload member presence, JSON kinds, unknown member names and decoded
values. This is not a general-purpose JSON framework; Jackson remains in
adapters. The decoder also provides strict typed construction for execution.
Typed construction is not a business-rule precheck.

### 11.2 Issuance order and ownership

For CIM, decoding and the registered feature policy run before username
throttle and password verification. Submit runs V1 structural validation
first; V1 failure returns the existing FORM violation without a snapshot read.
For structurally safe input, it retains V2-V8 violations and reads the
account-owned snapshot exactly once. With an available snapshot, independent
S1-S3 violations are appended after V1-V8 in S1, S2, S3 order, using the
original decoded payload and preserving array indexes. S1 reuses the V7
integer/range predicate; S2/S3 skip missing or blank operands already invalid
under V2/V5. Cancel prechecks CV1-CV2 and pending-reference membership. Each
rule has one implementation in the purpose policy; execution does not repeat
it. A token does not reserve or freeze business state. Progress retains its
existing current-state enforcement; do not assume it repeats every Java
validation rule.

Generic envelope errors retain the current HTTP 400 contract. CIM feature
payload and snapshot-rule violations return HTTP 422 `VALIDATION_FAILED`, with
the detailed codes and targets already defined in section 2 of document 04.
For submit, known V2-V8 violations take precedence over a missing or unreadable
snapshot: return the known 422 errors and publish no feature-owned 503 incident.
If there are no V violations, snapshot unavailability retains the
feature-specific HTTP 503 `SYSTEM_ERROR` and exactly one feature-owned
`LOCAL_SYSTEM` `FailureEvent`. A V1 structural failure remains an immediate
422 without a snapshot read. Snapshot availability permits aggregation of
known V errors followed by safely evaluable S1-S3 errors. Cancel snapshot
misses retain their existing feature-specific 503 and exactly one event.
Expected feature violations produce no technical incident, and shared
issuance must not publish a duplicate event. Wrong-password behavior remains
unchanged.

Personal Information issuance and PUT behavior remain unchanged, including its
existing issuance 400 behavior and PUT-side validation. Progress's existing
enforcement remains unchanged.

### 11.3 Trusted execution handoff

Reuse the existing password-validation gate binding attribute. A
`PayloadDigest` alone is not proof of authorization. Trust comes from successful
gate scope/token consumption and the existing canonicalizer's body-binding
checks before controller dispatch; equality is canonicalized payload equality,
not raw-byte equality. Each CIM controller boundary and command requires the
trusted binding attribute. Missing binding or any rejected gate check prevents
dispatch. A client-supplied flag or alternate unguarded path is not accepted.

After authorization, execution still strictly decodes and constructs its typed
command, then performs downstream dispatch, response mapping and the existing
submit reconciliation/post-success snapshot behavior. It does not rerun
feature business prechecks.

Cancel execution does not read or validate a snapshot before dispatch and does
not rerun pending-reference membership. Retained snapshot access solely for
post-success cleanup is best effort: missing/unreadable snapshot or cleanup
failure cannot block the cancellation or change successful downstream
cancellation into failure. No snapshot concurrency redesign, CAS refactor or
GET fencing is part of this design.

The existing token scope, lifetime, consumption and canonicalization do not
change. No legacy-token migration, grant versioning, rolling-deployment
handling, or token expiry wait is required for this local-development design.

### Scenario-aware GET and account snapshot (implemented in M6-012; not runtime-verified against Progress)

Progress owns selection of `NORMAL`, `FUNCTION_UNAVAILABLE`, or
`MEMBER_INELIGIBLE`. Documents 02 and 03 record the confirmed `scenario` field
and exact wire mappings. Downstream rollout is not yet confirmed, so the
temporary missing-field-to-NORMAL compatibility fallback remains. Runtime
behavior has not been verified against Progress.

The account-owned snapshot stores the resolved scenario. `NORMAL` retains the
existing interval, editable sources, funds, pending references, and ownership
invariants. A restricted GET replaces that account's snapshot with restricted
state containing the scenario and ownership data only; it must not fabricate
an interval, editable sources, funds, or pending references. Submit and cancel
issuance inspect this stored scenario and reject restricted states as specified
in document 04.

Use the existing account-scoped storage and update mechanism. Do not add CAS
redesign, GET fencing, or stronger consistency guarantees. A restricted write
that is applied replaces the prior NORMAL snapshot. As with the existing
best-effort snapshot behavior, a failed or superseded write may leave an older
snapshot; authorization reflects the stored state and this design does not
claim linearizability or guarantee that a concurrent/failed GET immediately
revokes an earlier NORMAL snapshot.

Scenario-aware GET parsing and snapshot replacement also apply when submit
unknown-outcome reconciliation refreshes state: do not turn a restricted
response into fabricated NORMAL authorization data. Cancel's post-success
cleanup must tolerate restricted state without creating editable data or
blocking downstream success. Neither reconciliation nor cleanup adds an
execution-time scenario/business precheck; already-issued token semantics are
unchanged.

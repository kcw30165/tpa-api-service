# 01 Solution design

## 1. Scope

In scope (member portal, `ROLE_USER_MBR` only):

| Capability | BFF endpoint | Downstream |
|---|---|---|
| Show CIM page: account number, allowed step, switch sources, available funds, allocation history, pending instructions | `GET /api/v1/investment-choice` | `TRPGetCIMDtl` |
| Submit a change of investment instruction after password confirmation | `POST /api/v1/investment-choice/instructions` | `TRPCIMReq` |
| Cancel a pending instruction | `POST /api/v1/investment-choice/instructions/{refNo}/cancel` | Progress operation not yet provided - **deferred** (D-19) |

Out of scope for the BFF: employer portal, print, leave-page warning, stepper, Account Balance
widget (existing API), post-day-end e-notification, history filters (frontend filters the records
Progress returns), CIM type handling, labels and message texts (frontend owns all labels; Java
returns codes only).

CIM is not a workflow feature: responses carry no `nextStep`, `pageRef` or `actions`
(evidence 004: `nextStep` exists only in the workflow/auth result types).

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
| Submit validation against the cached GET snapshot | Java |
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
| `ref-date` (TRPGetCIMDtl only) | `ReferenceDatePort.resolveReferenceDate(account.accountEnv())`, formatted `dd/MM/yyyy` |

The frontend never sends policy, certificate, user id or role.

## 4. Components (hexagonal placement)

| Layer | New component (design role; exact names checked in sub-task 0.1) |
|---|---|
| web in | `InvestmentChoiceController` (GET, POST instructions), request/response DTOs, web mapper |
| application | `GetInvestmentChoiceUseCase`, `SubmitInvestmentChoiceUseCase`, `InvestmentChoiceSubmissionValidator`, `InvestmentChoiceFundOrdering` |
| application port out | `InvestmentChoiceDetailsPort` (TRPGetCIMDtl), `InvestmentChoiceInstructionPort` (TRPCIMReq), `InvestmentChoiceSnapshotStorePort` |
| adapter out apim | `ApimInvestmentChoiceDetailsAdapter`, `ApimInvestmentChoiceInstructionAdapter`, APIM DTOs |
| adapter out redis | `RedisInvestmentChoiceSnapshotStore` |
| web error | `InvestmentChoiceExceptionMappings` registered in `ExceptionMappingRegistryConfig` |
| password validation | new purpose `CHANGE_INVESTMENT_CHOICE` (prerequisite milestone M1) |

Reused as-is: `MutationResponse`, `ApiStatus`, `ApiError` factories, `AccountIdentifierFormatter`,
`FundPriceRiskLevels.sortKey`, the Fund Price display-name rule, `ReferenceDatePort`,
`ApimExchangeExecutor`, the shared APIM classifier, `RedisCacheKeyFactory`.

## 5. Flow - GET

1. Resolve `PortalAccessContext` and reference date.
2. Call `TRPGetCIMDtl` (`SAFE_BUSINESS_CALL`).
3. Interpret the envelope per operation (not `ApimResponseValidator`): nonblank `err-message` or
   malformed/empty `data` -> 503 (document 04).
4. Build `result.data`: account number, interval, switch sources sorted by `seq`, funds with
   `avail-sw = true` sorted (section 7), history grouped by `effect-date` latest first, pending
   instructions joined by `ref-no` latest first.
5. Store the submit snapshot (section 6), overwriting any previous one for the same session and account.
6. Return 200 `SUCCESS`.

## 6. Submit snapshot (Redis)

Content, nothing else (D-22): `intervalPcnt`, every `SwitchSrc` (`switch-src`, `avail-sw`), every
`SwitchFund` (`fund-code`, `avail-sw`).

| Property | Rule |
|---|---|
| Key | Built with `RedisCacheKeyFactory`; scoped by session id **and** `accountRef`; feature segment `investment-choice:snapshot` |
| Write | Every successful GET (and every successful reconciliation read), overwrite |
| Read | Submit only |
| Lifetime | Must not outlive the session. TTL mechanism follows the existing session-scoped cache pattern confirmed in sub-task 0.1 (O-8); if none exists, stop and escalate |
| Missing on submit | 503 `SYSTEM_ERROR`, ERROR log `investment-choice.submission.snapshot.missing` (D-23) |
| Content | No names, no history, no personal data |

## 7. Fund ordering

Reuse the Fund Price rule:

1. `FundPriceRiskLevels.sortKey(riskLevel)` ascending, nulls last (null, blank, `"0"` = no risk level = highest).
2. Display name for the request language: zh_HK -> `chin-fund-name`, falling back to `fund-name`
   when blank; otherwise `fund-name`. English name is mandatory downstream (D-06); no new rule.
3. Ties: the existing Fund Price tie-break, confirmed in sub-task 0.1; then `fund-code`.

Applied to the available-fund list, and the fund rows inside each history group and each pending instruction.

## 8. Flow - submit

```text
FE: POST /api/v1/password-validation {password, purpose: CHANGE_INVESTMENT_CHOICE, operationPayload: <exact submit body>}
    -> 200 result.data {token, expiresInSeconds}
FE: POST /api/v1/investment-choice/instructions  (password-validation token header, same body)
BFF:
 1. token gate consumes the token, binds session + Account-Ref + purpose + exact body (403 on mismatch)
 2. stateless body validation (400)
 3. load snapshot (missing -> 503 + ERROR log)
 4. snapshot validation: interval multiple, source avail-sw, fund avail-sw (400)
 5. expand to CIMInstr = every source x every allocation
 6. TRPCIMReq (NON_RETRYABLE, never resubmitted)
 7. definite success -> 200 SUBMITTED result.data {referenceNo, submitDate, submitTime}
    err-message      -> 503 (no reconciliation)
    unknown outcome  -> reconciliation (section 9)
```

The token is consumed before validation (existing password-validation rule). Any 400 after step 1
requires the member to confirm the password again. This is accepted behaviour of the shared capability.

No `Idempotency-Key` (D-15): the single-use token prevents resubmission of the same confirmation.

## 9. Unknown-outcome reconciliation (D-17)

Trigger: the TRPCIMReq exchange did not produce a readable definite answer (transport/timeout
after send, downstream 5xx, response parse/crypto/mapping failure).

1. Call `TRPGetCIMDtl` once for the same account.
2. Candidates: `PendingReq` entries whose `submit-date` equals today (Asia/Hong_Kong) and whose
   joined `PendingReqDtl` rows equal exactly the submitted `(switch-src, fund-code, pcnt)` set.
3. Exactly one candidate -> 200 `SUBMITTED` with that `ref-no`, `submit-date`, `submit-time`;
   WARN log `investment-choice.submission.reconciled`.
4. Zero or several candidates, or the reconciliation call fails -> 503
   `err.investment-choice.submission.unavailable`; one ERROR incident. The frontend reloads the
   GET to see the pending list; no `nextStep` is returned.
5. A successful reconciliation read refreshes the snapshot.

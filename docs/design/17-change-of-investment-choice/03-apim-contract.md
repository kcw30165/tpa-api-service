# 03 APIM contract (BFF <-> Progress via APIM)

Both operations are new entries in `ApimRoutingLedgerCharacterizationTest` (21 -> 23).
Envelope `{ "response": { "err-message": "", "data": [ ... ] } }` through `ApimResponseEnvelope<T>`.
Every response DTO and nested type carries `@JsonIgnoreProperties(ignoreUnknown = true)`.
Each adapter interprets its own envelope; do not use `ApimResponseValidator`.

Date/time parsing (D-33): each mapper declares its own
`private static final DateTimeFormatter ... = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)`
and, where needed, `ofPattern("HH:mm:ss").withResolverStyle(ResolverStyle.STRICT)`, as
`ForgotPasswordTrpSearchMemberMapper` and `TrpResetPasswordApimResultMapper` do. Fund Price beans are not reused.

## 1. TRPGetCIMDtl

`POST /ws/NGTPA/v1/TRPGetCIMDtl` - `ApimRetryEligibility.SAFE_BUSINESS_CALL`, `ApimDatabaseScope.ACCOUNT_ENV`,
credential context `(actorUserRole, accountEnv)` as Fund Fact Sheet.

### Request

```json
{ "policy-no": "<account.policyNo>", "cert-no": "<account.certNo>", "ref-date": "31/08/2026" }
```

### Response `data[0]` (exactly one item required)

| Field | Type | Required | Mapping rule |
|---|---|---|---|
| `interval-pcnt` | integer | yes | 1..100 |
| `SwitchSrc[]` | array | yes, may be empty | |
| `.switch-src` | string | yes, nonblank, unique | code |
| `.src-desc`, `.src-chin-desc` | string | no | `.src-desc` -> `switchSources[].switchSource`; `.src-chin-desc` ignored |
| `.seq` | integer | yes | sort key |
| `.avail-sw` | boolean | yes | passed through; true = editable, false = read-only (D-37) |
| `SwitchFund[]` | array | yes, may be empty | |
| `.fund-code` | string | yes, nonblank, unique | |
| `.fund-name` | string | yes, nonblank (D-06) | |
| `.chin-fund-name` | string | no | blank -> English |
| `.fund-seq` | integer | no | passed through as `funds[].sequence` |
| `.risk-level` | string | no | `FundPriceRiskLevels` rule |
| `.avail-sw` | boolean | yes | false -> excluded from `funds`, kept for history names |
| `InvestMandate[]` | array | yes, may be empty | |
| `.effect-date` | string `dd/MM/uuuu` strict | yes | group key |
| `.switch-src`, `.fund-code` | string | yes | |
| `.pcnt` | string | yes, nonblank | verbatim |
| `PendingReq[]` | array | optional when no pending instructions; omission maps to empty | |
| `.ref-no` | string | yes, unique | join key |
| `.submit-date` | string `dd/MM/uuuu` strict | yes | |
| `.submit-time` | string `HH:mm:ss` strict | yes | |
| `PendingReqDtl[]` | array | optional when no pending instructions; omission maps to empty | |
| `.ref-no` | string | yes, must exist in `PendingReq` | |
| `.switch-src`, `.fund-code` | string | yes | |
| `.pcnt` | integer | yes, 1..100 | |

Progress may omit both pending arrays when there are no pending instructions. An omitted
`PendingReq` or `PendingReqDtl` is treated as an empty array.

Integrity rules (violation = malformed -> 503): duplicate `switch-src` or `fund-code`; duplicate
`ref-no` in `PendingReq`; `PendingReqDtl.ref-no` not in `PendingReq`; `PendingReq` without any
detail; null item, required `SwitchSrc`/`SwitchFund`/`InvestMandate` list, or row; date/time not
strict. Pending records remain subject to all listed row and reference validations.

A history or pending row whose `fund-code` is absent from `SwitchFund` is returned with
`fundName: null` (O-5), not treated as malformed.

### M6 scenario-aware GET contract (implemented; not runtime-verified against Progress)

The requiredness in the preceding table applies to `NORMAL`. The approved internal enum is `NORMAL`, `FUNCTION_UNAVAILABLE`, or `MEMBER_INELIGIBLE`; Progress owns the scenario decision.

The confirmed downstream field is `scenario`, beside `interval-pcnt`, with exact string mappings: `normal` -> `NORMAL`, `scenario1` -> `FUNCTION_UNAVAILABLE`, and `scenario2` -> `MEMBER_INELIGIBLE`. Matching is exact and case-sensitive; do not trim values or accept aliases. No configuration-based mapping is used. Keep field extraction and wire-value translation localized to the APIM DTO and adapter mapper.

Downstream rollout is not yet confirmed. Until rollout is confirmed, an absent `scenario` field temporarily resolves to internal `NORMAL`. Present null, non-string, blank, uppercase aliases, or unknown values fail as malformed downstream responses and never fall back to `NORMAL`. Remove the missing-field fallback after the confirmed mandatory contract is rolled out.

| Internal scenario | Required fields and validation |
|---|---|
| `NORMAL` | `interval-pcnt`, `SwitchSrc`, `SwitchFund`, and `InvestMandate` remain mandatory and retain existing strict validation. Preserve the existing omission-to-empty handling for `PendingReq` and `PendingReqDtl`. |
| `FUNCTION_UNAVAILABLE` / `MEMBER_INELIGIBLE` | `SwitchFund` metadata and `InvestMandate` remain mandatory and strictly validated. `interval-pcnt` and `SwitchSrc` are optional. No new nonempty-list requirement is introduced. |

`InvestMandate` remains the required history input. `SwitchFund` is mandatory in both restricted scenarios and is used internally for history names and the existing fund ordering, but restricted GET responses do not expose `funds`. Preserve the existing `fundName: null` behavior for a history fund absent from `SwitchFund`. If `SwitchSrc` is absent, use the existing deterministic source-code fallback for history ordering. Restricted responses do not expose `switchSources` or pending instructions, whether or not Progress supplies those arrays.

If restricted responses include `SwitchSrc`, it may enrich history internally using the existing source ordering; it is still omitted from the public response.

### err-message (all 503, origin `DOWNSTREAM_CONTRACT`, D-34)

| Value | Meaning (Progress) |
|---|---|
| `err.account.invalid` | member not found by policy/cert (server-side context fault) |
| `err.account.no.invest.mandate` | no data returned |
| any other nonblank | generic |
| future eligibility codes | reserved slot, 04 section 4 |

## 2. TRPCIMReq

`POST /ws/NGTPA/v1/TRPCIMReq` - `ApimRetryEligibility.NON_RETRYABLE`, never resubmitted
(same policy as `TRPPwdValidation`). Configured by
`apim.investment-choice.instruction.path` (default `/ws/NGTPA/v1/TRPCIMReq`). No `ref-date`, no
`package-code`.

### Request

```json
{
  "login-user-id": "<actor.actorUserId>",
  "login-user-role": "role_user_mbr",
  "policy-no": "<account.policyNo>",
  "cert-no": "<account.certNo>",
  "CIMInstr": [ { "switch-src": "EE", "fund-code": "DBSF", "pcnt": 100 } ]
}
```

### Response `data[0]`

| Field | Type | Rule |
|---|---|---|
| `success` | boolean | mapped into the DTO only; no check in this feature (D-14) |
| `submit-date` | string `dd/MM/uuuu` strict | required -> `submitDate` |
| `submit-time` | string `HH:mm:ss` strict | required -> `submitTime` |
| `ref-no` | string nonblank | required -> `referenceNo` |

### err-message (all 503, origin `DOWNSTREAM_CONTRACT`, no reconciliation)

| Value | Meaning |
|---|---|
| `err.account.invalid` | member not found |
| `err.role.not.allow.access` | login user not allowed to submit |
| any other nonblank | generic |

## 3. TRPCnclCIMReq (cancel)

`POST /ws/NGTPA/v1/TRPCnclCIMReq` - `ApimRetryEligibility.NON_RETRYABLE`, never resubmitted (same policy as `TRPCIMReq`); `ApimDatabaseScope.ACCOUNT_ENV`; credential context `(actorUserRole, accountEnv)`. New ledger entry (23 -> 24). New configuration key `apim.investment-choice.cancel.path`, default `/ws/NGTPA/v1/TRPCnclCIMReq`, with fail-fast validation like the other two paths. The environment override is `APIM_INVESTMENT_CHOICE_CANCEL_PATH`.

### Request

```json
{
  "login-user-id": "<actor.actorUserId>",
  "login-user-role": "role_user_mbr",
  "policy-no": "<account.policyNo>",
  "cert-no": "<account.certNo>",
  "ref-no": "<validated referenceNo>"
}
```

All values are strings, including `cert-no` (field encryption already covers `policy-no` and `cert-no`).

### Response `data[0]`

| Field | Type | Rule |
|---|---|---|
| `success` | boolean | mapped into the DTO only; no check in this feature (D-14). `success:false` with an empty `err-message` is documented by Progress as impossible |
| `ref-no` | string | optional; ignored. The downstream team may add it to the response; Java does not depend on it (D-42) |

Exactly one data item is required.

### err-message and outcomes

| Value | Meaning (Progress) | Outcome | Event origin |
|---|---|---|---|
| `err.request.cannot.be.cancelled` | already processed or processing | 422 `BUSINESS_REJECTED` `...cancel.not-cancellable` | `DOWNSTREAM_BUSINESS` (WARN) |
| `err.tpa.cust.id.not.registered` | user not registered | 503 `...cancel.unavailable` | `DOWNSTREAM_CONTRACT`, `internalCode` = the code |
| `err.request.invalid` | request invalid (a Java-side fault) | 503 | `DOWNSTREAM_CONTRACT`, `internalCode` = the code |
| `err.role.not.allow.access` | role not allowed | 503 | `DOWNSTREAM_CONTRACT`, `internalCode` = the code |
| any other nonblank | generic | 503 | `DOWNSTREAM_CONTRACT`, `internalCode` = the code |
| malformed success (empty data, two items) | - | 503 | `DOWNSTREAM_INVALID_RESPONSE` |
| any throwable | transport, crypto, contract, local | 503 | classified by `ApimFailureClassifier`, exhaustive switch |

There is no reconciliation after a timeout or unknown outcome (D-41). Ownership of the reference by the member is enforced by Progress (D-43); Java does not call `TRPGetCIMDtl` first.

## 4. Pending downstream fixes

| Id | Fix | Owner |
|---|---|---|
| O-6 | Sample dates `2019-02-01` / `2026-08-21` become `dd/MM/yyyy` | Progress |

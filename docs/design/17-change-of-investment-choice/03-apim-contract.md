# 03 APIM contract (BFF <-> Progress via APIM)

Both operations are new routing-ledger entries in `ApimRoutingLedgerCharacterizationTest`
(21 entries at 8f84da7a; count re-confirmed in sub-task 0.1). Envelope:
`{ "response": { "err-message": "", "data": [ ... ] } }`, deserialized through the generic
`ApimResponseEnvelope<T>`. Every response DTO and nested type carries
`@JsonIgnoreProperties(ignoreUnknown = true)`. Each adapter interprets its own envelope;
do not use `ApimResponseValidator`.

## 1. TRPGetCIMDtl

`POST /ws/NGTPA/v1/TRPGetCIMDtl` - `ApimRetryEligibility.SAFE_BUSINESS_CALL`.

### Request

```json
{ "policy-no": "<account.policyNo>", "cert-no": "<account.certNo>", "ref-date": "31/08/2026" }
```

`policy-no` and `cert-no` stay textual (configured field encryption may transform them).

### Response `data[0]` (exactly one item required)

| Field | Type | Required | Mapping rule |
|---|---|---|---|
| `interval-pcnt` | integer | yes | 1..100 |
| `SwitchSrc[]` | array | yes, may be empty | |
| `.switch-src` | string | yes, nonblank, unique | code |
| `.src-desc`, `.src-chin-desc` | string | no | ignored (codes only) |
| `.seq` | integer | yes | sort key |
| `.avail-sw` | boolean | yes | passed through; meaning pending O-1 |
| `SwitchFund[]` | array | yes, may be empty | |
| `.fund-code` | string | yes, nonblank, unique | |
| `.fund-name` | string | yes, nonblank (D-06) | |
| `.chin-fund-name` | string | no | blank -> English fallback |
| `.fund-seq` | integer | no | not used |
| `.risk-level` | string | no | `FundPriceRiskLevels` rule |
| `.avail-sw` | boolean | yes | false -> excluded from `funds`, kept for history names |
| `InvestMandate[]` | array | yes, may be empty | |
| `.effect-date` | string `dd/MM/yyyy` strict | yes | group key |
| `.switch-src`, `.fund-code` | string | yes | |
| `.pcnt` | string | yes, nonblank | verbatim (Progress formats) |
| `PendingReq[]` | array | yes, may be empty | |
| `.ref-no` | string | yes, unique | join key |
| `.submit-date` | string `dd/MM/yyyy` strict | yes | |
| `.submit-time` | string `HH:mm:ss` strict | yes | |
| `PendingReqDtl[]` | array | yes, may be empty | |
| `.ref-no` | string | yes, must exist in `PendingReq` | |
| `.switch-src`, `.fund-code` | string | yes | |
| `.pcnt` | integer | yes, 1..100 | |

Integrity rules (violation = malformed -> 503): duplicate `switch-src` or `fund-code`; duplicate
`ref-no` in `PendingReq`; `PendingReqDtl.ref-no` not in `PendingReq`; `PendingReq` without any
detail; date/time not strict.

A history or pending row whose `fund-code` is absent from `SwitchFund` is returned with
`fundName: null` (open item O-5), not treated as malformed.

### err-message

| Value | Meaning (Progress) | BFF |
|---|---|---|
| `err.account.invalid` | member not found by policy/cert (server-side context fault) | 503 retrieval unavailable |
| `err.account.no.invest.mandate` | no data returned | 503 retrieval unavailable |
| any other nonblank | generic | 503 retrieval unavailable |
| future eligibility codes | not yet defined (C2 = b) | reserved slot, 04 section 4 |

## 2. TRPCIMReq

`POST /ws/NGTPA/v1/TRPCIMReq` - `ApimRetryEligibility.NON_RETRYABLE`, never resubmitted
(same policy as `TRPPwdValidation`). No `ref-date`, no `package-code`.

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
| `success` | boolean | mapped into the DTO only. No `success` check in this feature; Nick's separate branch (D-14) |
| `submit-date` | string `dd/MM/yyyy` strict | required -> `submitDate` |
| `submit-time` | string `HH:mm:ss` strict | required -> `submitTime` |
| `ref-no` | string nonblank | required -> `referenceNo` |

### err-message

| Value | Meaning | BFF |
|---|---|---|
| `err.account.invalid` | member not found | 503 submission unavailable, no reconciliation |
| `err.role.not.allow.access` | login user not allowed to submit | 503 submission unavailable, no reconciliation |
| any other nonblank | generic | 503 submission unavailable, no reconciliation |

A nonblank `err-message` is a definite answer; reconciliation runs only for unknown outcomes (01 section 9).

## 3. Cancel (deferred)

Progress operation not published. Open item O-3.

## 4. Pending downstream fixes (tracked, not blocking design)

| Id | Fix | Owner |
|---|---|---|
| O-1 | Final meaning of `SwitchSrc.avail-sw` | Progress |
| O-6 | Sample dates `2019-02-01` / `2026-08-21` become `dd/MM/yyyy` | Progress |

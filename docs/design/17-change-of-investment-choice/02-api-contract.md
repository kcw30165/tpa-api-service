# 02 API contract (BFF <-> frontend)

Common to all endpoints: authenticated session cookie, CSRF as for other mutations,
`Account-Ref` header (selected account), `Accept-Language` (canonical language source),
optional `X-Request-Id`. Envelope, `ApiStatus`, `ApiError(type, code, message, targets, severity, source)`
and `nextStep` follow the existing generic base response; `message` stays null (codes only).
Dates are `dd/MM/yyyy`, times `HH:mm:ss`, as received from Progress.

## 1. GET /api/v1/investment-choice

No query parameters.

### 200

```json
{
  "status": "SUCCESS",
  "data": {
    "accountNumber": "00000012345-0001",
    "intervalPercentage": 5,
    "switchSources": [
      { "switchSource": "BO", "sequence": 10, "available": true }
    ],
    "funds": [
      { "fundCode": "DHKM", "fundName": "Fidelity HKD Money Market Fund", "riskLevel": "1" }
    ],
    "history": [
      {
        "effectiveDate": "01/02/2019",
        "allocations": [
          { "switchSource": "BO", "fundCode": "DGLE", "fundName": "BlackRock Global Equity Fund", "percentage": "100" }
        ]
      }
    ],
    "pendingInstructions": [
      {
        "referenceNo": "260000922",
        "submitDate": "05/11/2026",
        "submitTime": "17:04:21",
        "allocations": [
          { "switchSource": "BO", "fundCode": "DGLE", "fundName": "BlackRock Global Equity Fund", "percentage": 100 }
        ]
      }
    ]
  }
}
```

| Field | Rule |
|---|---|
| `accountNumber` | `AccountIdentifierFormatter.format(policyNo, certNo)` |
| `intervalPercentage` | `interval-pcnt`, integer > 0, verbatim. Frontend decides dropdown vs manual entry |
| `switchSources` | every `SwitchSrc` (Progress omits HIDDEN), sorted by `seq` ascending, ties by code. `available` = `avail-sw` verbatim (meaning pending B3, see 06) |
| `funds` | `SwitchFund` with `avail-sw = true` only, ordered per 01 section 7. `riskLevel` is the raw downstream string |
| `history` | `InvestMandate` grouped by `effect-date`, groups latest first; rows ordered by switch-source `seq`, then fund order. `percentage` is the downstream **string**, verbatim |
| `history[].allocations[].fundName` | resolved from the **full** `SwitchFund` list, including `avail-sw = false` (closed funds stay visible in history) |
| `pendingInstructions` | `PendingReq` joined to `PendingReqDtl` by `ref-no`, latest `submitDate`+`submitTime` first; `percentage` is an **integer** |
| codes only | `src-desc` / `src-chin-desc` are not returned; frontend owns switch-source labels |

`history` and `pendingInstructions` may be empty arrays. Frontend applies the history filter options.

### Errors

| HTTP | ApiStatus | code | When |
|---|---|---|---|
| 401/403 | existing | existing | session, CSRF, Account-Ref authorization (unchanged shared behaviour) |
| 503 | `SYSTEM_ERROR` | `err.investment-choice.retrieval.unavailable` | any downstream failure, any `err-message`, malformed response (04) |

## 2. POST /api/v1/investment-choice/instructions

Headers additionally: `X-Password-Validation-Token` (required), `Content-Type: application/json`.

### Request

```json
{
  "switchSources": ["BO", "EE"],
  "allocations": [
    { "fundCode": "DBLF", "percentage": 60 },
    { "fundCode": "DHKM", "percentage": 40 }
  ]
}
```

The same body, byte-for-byte semantically (member order free; values, types, array order significant),
must have been sent as `operationPayload` to `POST /api/v1/password-validation` with purpose
`CHANGE_INVESTMENT_CHOICE`. Unknown members are rejected (400) at both places.

Expansion to Progress: one `CIMInstr` row per (source, allocation), sources in request order,
allocations in request order. All sources receive the same allocation (D-22 atomic).

### 200

```json
{
  "status": "SUCCESS",
  "data": { "referenceNo": "260001449", "submitDate": "21/08/2026", "submitTime": "09:25:22" }
}
```

Success shape (status code and envelope) mirrors `PUT /api/v1/personal-information` success at the
implementation HEAD; sub-task 0 records it. If it is not 200 + `data`, stop and escalate.

### Errors

| HTTP | ApiStatus | code | targets | When |
|---|---|---|---|---|
| 403 | `BUSINESS_REJECTED` | `PASSWORD_VALIDATION_AUTHORIZATION_INVALID` (existing) | `X-Password-Validation-Token` | token missing, expired, consumed, other scope, payload mismatch |
| 400 | `VALIDATION_FAILED` | see 04 section 2 | field paths | body or snapshot validation |
| 503 | `SYSTEM_ERROR` | `err.investment-choice.submission.unavailable` | `PAGE` | downstream failure, `err-message`, missing snapshot, unreconciled unknown outcome; `nextStep: RELOAD` |

## 3. POST /api/v1/investment-choice/instructions/{refNo}/cancel - deferred

Reserved. No password token (Q9). No body. Contract completed when Progress publishes the cancel
operation (open item O-3). Not implemented in M1-M5.

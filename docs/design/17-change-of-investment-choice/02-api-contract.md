# 02 API contract (BFF <-> frontend)

## 0. Envelope (existing)

Every response is the existing `MutationResponse<T>(success, status, result, messages, errors)`.
Success: `success: true`, `result` set, `errors: []`. Failure: `success: false`, `result: null`,
`messages: []`. CIM returns no `messages[]` items and no `nextStep`.

```json
{ "success": true, "status": "SUCCESS", "result": { "data": {} }, "messages": [], "errors": [] }
```

```json
{
  "success": false,
  "status": "SYSTEM_ERROR",
  "result": null,
  "messages": [],
  "errors": [
    { "type": "SYSTEM", "code": "err.investment-choice.retrieval.unavailable",
      "message": null, "targets": [], "severity": "ERROR", "source": "SERVER" }
  ]
}
```

`ApiError` fields: `type`, `code`, `message` (null; frontend owns text), `targets`, `severity`
(`ERROR`), `source` (`SERVER`). Types used by CIM: `SYSTEM` (`ApiError.system`, `targets: []`),
`FIELD`, `CROSS_FIELD`, `FORM`. `DOWNSTREAM_*` types and statuses are never public.

HTTP status: `SUCCESS`/`SUBMITTED` 200 and `SYSTEM_ERROR` 503 via `HttpStatusClassification`;
`VALIDATION_FAILED` **422** on the submit endpoint (controller override, same as
`PersonalInformationController.withUpdateHttpStatus`, D-30); token-gate 403 unchanged.

Common request headers: session cookie, CSRF as for other mutations, `Account-Ref`,
`Accept-Language`, `X-Request-Id` (mandatory, existing filter).
Dates `dd/MM/yyyy`, times `HH:mm:ss`, as received from Progress.

## 1. GET /api/v1/investment-choice

No query parameters.

### 200 SUCCESS

```json
{
  "success": true,
  "status": "SUCCESS",
  "result": {
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
  },
  "messages": [],
  "errors": []
}
```

| Field | Rule |
|---|---|
| `accountNumber` | `AccountIdentifierFormatter.format(policyNo, certNo)` |
| `intervalPercentage` | `interval-pcnt` verbatim, integer 1..100. Frontend decides dropdown vs manual entry |
| `switchSources` | every `SwitchSrc` (Progress omits HIDDEN), sorted by `seq` ascending then code. `available` = `avail-sw` verbatim (true = editable, false = read-only; D-37) |
| `funds` | `SwitchFund` with `avail-sw = true` only, ordered per 01 section 7. `riskLevel` raw downstream string, null when absent |
| `history` | `InvestMandate` grouped by `effect-date`, groups latest first; rows ordered by switch-source `seq`, then fund order. `percentage` is the downstream **string**, verbatim |
| `history[].allocations[].fundName` | resolved from the **full** `SwitchFund` list including `avail-sw = false` |
| `pendingInstructions` | `PendingReq` joined to `PendingReqDtl` by `ref-no`, latest `submitDate`+`submitTime` first; `percentage` is an **integer** |
| codes only | `src-desc` / `src-chin-desc` are not returned |

`history` and `pendingInstructions` may be empty arrays.

### Errors

| HTTP | status | type | code | targets | When |
|---|---|---|---|---|---|
| existing | existing | existing | existing | existing | session, CSRF, `Account-Ref`, `X-Request-Id` (shared behaviour) |
| 503 | `SYSTEM_ERROR` | `SYSTEM` | `err.investment-choice.retrieval.unavailable` | `[]` | any downstream failure, any `err-message`, malformed response, identifier formatting failure |

A failed snapshot write does not change the GET result (D-31).

## 2. POST /api/v1/investment-choice/instructions

Additional headers: the existing password-validation token header, `Content-Type: application/json`.

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

The same body must have been sent as `operationPayload` to `POST /api/v1/password-validation`
with purpose `CHANGE_INVESTMENT_CHOICE`; the gate compares with the existing strict canonicalizer.
Unknown members are rejected at both places.

Expansion to Progress: one `CIMInstr` row per (source, allocation), sources in request order,
allocations in request order. All sources receive the same allocation (D-22, atomic).

### 200 SUBMITTED

```json
{
  "success": true,
  "status": "SUBMITTED",
  "result": { "data": { "referenceNo": "260001449", "submitDate": "21/08/2026", "submitTime": "09:25:22" } },
  "messages": [],
  "errors": []
}
```

### Errors

| HTTP | status | type | code | targets | When |
|---|---|---|---|---|---|
| 403 | `BUSINESS_REJECTED` | as rendered by the gate | `err.password-validation.authorization.invalid` | as rendered by the gate | token missing, expired, consumed, other scope, payload mismatch (existing gate) |
| 422 | `VALIDATION_FAILED` | see 04 section 2 | see 04 section 2 | see 04 section 2 | body or snapshot validation |
| 503 | `SYSTEM_ERROR` | `SYSTEM` | `err.investment-choice.submission.unavailable` | `[]` | downstream failure, `err-message`, snapshot miss, unreconciled unknown outcome |

## 3. POST /api/v1/investment-choice/instructions/cancel

Cancels a pending instruction. Password-confirmed (D-39): additional header is the existing password-validation token header; `Content-Type: application/json`.

### Request

```json
{ "refNo": "260001449" }
```

The same body must have been sent as `operationPayload` to `POST /api/v1/password-validation` with purpose `CANCEL_INVESTMENT_CHOICE`; the gate compares it with the existing strict canonicalizer. Unknown members are rejected at both places. `refNo` is a string with no format rule (C6); it is validated by membership in the session snapshot's pending references (D-40).

### 200 SUCCESS

```json
{
  "success": true,
  "status": "SUCCESS",
  "result": { "data": { "referenceNo": "260001449" } },
  "messages": [],
  "errors": []
}
```

`referenceNo` is the validated requested value (D-42). The frontend reloads `GET /api/v1/investment-choice` for the updated list.

### Errors

| HTTP | status | type | code | targets | When |
|---|---|---|---|---|---|
| 403 | `BUSINESS_REJECTED` | as rendered by the gate | `err.password-validation.authorization.invalid` | as rendered by the gate | token missing, expired, consumed, other scope, body mismatch (existing gate) |
| 422 | `VALIDATION_FAILED` | `FORM` | `err.investment-choice.request.invalid` | `[]` | body is not an object, unknown member, `refNo` not a string |
| 422 | `VALIDATION_FAILED` | `FIELD` | `err.investment-choice.cancel.ref-required` | `["refNo"]` | `refNo` missing or blank |
| 422 | `VALIDATION_FAILED` | `FIELD` | `err.investment-choice.cancel.ref-invalid` | `["refNo"]` | `refNo` not among the snapshot's pending references (no downstream call) |
| 422 | `BUSINESS_REJECTED` | existing business-rejection type | `err.investment-choice.cancel.not-cancellable` | `[]` | Progress `err.request.cannot.be.cancelled`: already processed or processing |
| 503 | `SYSTEM_ERROR` | `SYSTEM` | `err.investment-choice.cancel.unavailable` | `[]` | downstream failure, any other `err-message`, snapshot miss, malformed response, unknown outcome |

The HTTP status of `BUSINESS_REJECTED` comes from the existing `HttpStatusClassification`; sub-task 4b.3 confirms it and the type of the business error factory before coding.

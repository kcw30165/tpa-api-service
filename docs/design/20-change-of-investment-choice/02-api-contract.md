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

Current M5 implementation: `SUCCESS`/`SUBMITTED` 200 and `SYSTEM_ERROR` 503 via
`HttpStatusClassification`; submit snapshot rules and cancel reference
membership return 422 at execution. Structural CIM payload failures at
issuance use the existing generic 400 contract. Approved M6 target (not
implemented): all CIM feature prechecks move to password-token issuance; see
section 4. CIM execution no longer returns those feature-precheck 422s. Gate
403 remains unchanged.

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
        {
          "switchSourceCode": "BO",
          "switchSource": "Employer and Member Voluntary Contributions",
          "sequence": 10,
          "available": true
        }
      ],
      "funds": [
        {
          "fundCode": "DHKM",
          "fundName": "Fidelity HKD Money Market Fund",
          "sequence": 60,
          "riskLevel": "1"
        }
      ],
      "history": [
        {
          "effectiveDate": "01/02/2019",
          "allocations": [
            { "switchSourceCode": "BO", "switchSource": "Employer and Member Voluntary Contributions", "fundCode": "DGLE", "fundName": "BlackRock Global Equity Fund", "percentage": "100" }
          ]
        }
      ],
      "pendingInstructions": [
        {
          "referenceNo": "260000922",
          "submitDate": "05/11/2026",
          "submitTime": "17:04:21",
          "allocations": [
            { "switchSourceCode": "BO", "switchSource": "Employer and Member Voluntary Contributions", "fundCode": "DGLE", "fundName": "BlackRock Global Equity Fund", "percentage": 100 }
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
| `switchSources` | available `SwitchSrc` entries only (Progress omits HIDDEN), sorted by `seq` ascending then code. `switchSourceCode` = `switch-src`; `switchSource` = `src-desc`; `sequence` = `seq`; `available` = `avail-sw` verbatim (true = editable; false = omitted; D-37). The complete source metadata remains available internally for history and pending allocation enrichment |
| `funds` | `SwitchFund` with `avail-sw = true` only, ordered per 01 section 7. `sequence` = `fund-seq`; `riskLevel` raw downstream string, null when absent |
| `history` | `InvestMandate` grouped by `effect-date`, groups latest first; rows ordered by switch-source `seq`, then fund order. Each allocation returns `switchSourceCode` from `switch-src`, `switchSource` from matching `SwitchSrc.src-desc`, original `fundCode`, resolved `fundName`, and `percentage` as the downstream **string**, verbatim |
| `history[].allocations[].fundName` | resolved from the **full** `SwitchFund` list including `avail-sw = false` |
| `pendingInstructions` | `PendingReq` joined to `PendingReqDtl` by `ref-no`, latest `submitDate`+`submitTime` first; each allocation uses the same source-code/description and fund-code/name mapping as history; `percentage` is an **integer** |
| Missing allocation metadata | Preserve each allocation and its original source/fund codes; missing source description or fund name is `null`. Missing referenced source descriptions and fund names are aggregated into one `investment-choice.retrieval.metadata_integrity` incident per GET (`DOWNSTREAM_INVALID_RESPONSE`, ERROR level); dimensions contain only fixed reason labels and missing-reference counts. The incident is non-fatal and the GET remains HTTP 200 `SUCCESS` |
| switch-source description | `src-desc` is returned as top-level `switchSource` and enriches history/pending `switchSource`; `src-chin-desc` is not returned |

`history` and `pendingInstructions` may be empty arrays.

### Errors

| HTTP | status | type | code | targets | When |
|---|---|---|---|---|---|
| existing | existing | existing | existing | existing | session, CSRF, `Account-Ref`, `X-Request-Id` (shared behaviour) |
| 503 | `SYSTEM_ERROR` | `SYSTEM` | `err.investment-choice.retrieval.unavailable` | `[]` | any downstream failure, any `err-message`, malformed response, identifier formatting failure |

A failed snapshot write does not change the GET result (D-31).

### M6 scenario-aware GET (implemented; not runtime-verified against Progress)

The response above records the M5 NORMAL shape. The approved M6 target adds a normalized `scenario` property to `result.data`. NORMAL returns the current GET fields plus `"scenario": "NORMAL"`. For the two restricted scenarios, `result.data` contains only `accountNumber`, `scenario`, and `history`:

```json
{
  "accountNumber": "<formatted-account-number>",
  "scenario": "FUNCTION_UNAVAILABLE",
  "history": []
}
```

`MEMBER_INELIGIBLE` uses the same restricted shape with `"scenario": "MEMBER_INELIGIBLE"`. Restricted responses omit `intervalPercentage`, `switchSources`, `funds`, and `pendingInstructions`; omitted properties are absent from JSON, never `null` or empty arrays. This applies even when Progress supplies those fields.

The normalized internal and FE scenarios are `NORMAL`, `FUNCTION_UNAVAILABLE`, and `MEMBER_INELIGIBLE`; Progress owns the scenario decision. The confirmed APIM wire mappings are `normal` -> `NORMAL`, `scenario1` -> `FUNCTION_UNAVAILABLE`, and `scenario2` -> `MEMBER_INELIGIBLE`. Wire matching is exact and case-sensitive; no trimming or aliases are accepted.

Downstream rollout is not yet confirmed, so a missing field still maps
temporarily to `NORMAL`. Present null, non-string, blank, uppercase aliases, or
unknown values fail as malformed downstream responses. Remove the missing-field
fallback once the confirmed mandatory contract has been rolled out.

| Internal scenario | Required downstream data | Public `result.data` |
|---|---|---|
| `NORMAL` | Existing mandatory `interval-pcnt`, `SwitchSrc`, `SwitchFund`, and `InvestMandate`; preserve current validation and pending-array omission handling | Existing fields plus `scenario: NORMAL` |
| `FUNCTION_UNAVAILABLE` / `MEMBER_INELIGIBLE` | Mandatory `SwitchFund` metadata and `InvestMandate`; `interval-pcnt` and `SwitchSrc` are optional | `accountNumber`, `scenario`, `history` only |

`InvestMandate` remains the required history source. `SwitchFund` is mandatory in every scenario and is used internally for history names and the existing fund ordering; an unknown history fund retains the existing `fundName: null` behavior. When `SwitchSrc` is absent, history uses the existing deterministic source-code fallback. No new nonempty-list requirement is introduced.

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
| 422 | `BUSINESS_REJECTED` | `BUSINESS` | `err.investment-choice.submission.same-source-pending` | `[]` | Progress `err.tpa.same.src.cim.exist`: a same-source instruction is pending; re-confirm password before submitting again |
| 503 | `SYSTEM_ERROR` | `SYSTEM` | `err.investment-choice.submission.unavailable` | `[]` | downstream failure, `err-message`, unreconciled unknown outcome |

## 3. POST /api/v1/investment-choice/instructions/cancel

Cancels a pending instruction. Password-confirmed (D-39): additional header is the existing password-validation token header; `Content-Type: application/json`.

### Request

```json
{ "referenceNo": "260001449" }
```

The same body must have been sent as `operationPayload` to `POST /api/v1/password-validation` with purpose `CANCEL_INVESTMENT_CHOICE`; the gate compares it with the existing strict canonicalizer. Unknown members are rejected at both places; the former `refNo` member is not an alias. `referenceNo` is a string with no format rule (C6); it is validated by membership in the session snapshot's pending references (D-40).

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
| 422 | `BUSINESS_REJECTED` | existing business-rejection type | `err.investment-choice.cancel.not-cancellable` | `[]` | Progress `err.request.cannot.be.cancelled`: already processed or processing |
| 503 | `SYSTEM_ERROR` | `SYSTEM` | `err.investment-choice.cancel.unavailable` | `[]` | downstream failure, any other `err-message`, malformed response, unknown outcome |

The HTTP status of `BUSINESS_REJECTED` comes from the existing `HttpStatusClassification`; sub-task 4b.3 confirms it and the type of the business error factory before coding.

## 4. Approved M6 issuance precheck contract (not implemented)

The following rows belong to `POST /api/v1/password-validation`, not to either
CIM execution endpoint. Current M5 still has its existing behavior until the
approved target is separately implemented and verified.

| Purpose | Failure | HTTP | status | Public code/targets | Ownership |
|---|---|---:|---|---|---|
| `CHANGE_INVESTMENT_CHOICE` | V1-V8 payload or S1-S3 snapshot rule violation | 422 | `VALIDATION_FAILED` | Existing detailed CIM code and target from 04 section 2 | No technical incident |
| `CHANGE_INVESTMENT_CHOICE` | Snapshot unavailable/miss | 503 | `SYSTEM_ERROR` | `err.investment-choice.submission.unavailable`, `[]` | One feature-owned `LOCAL_SYSTEM` `FailureEvent`; no shared duplicate |
| `CANCEL_INVESTMENT_CHOICE` | CV1-CV2 payload or pending-reference membership violation | 422 | `VALIDATION_FAILED` | Existing detailed CIM code and target from 04 section 8.1 | No technical incident |
| `CANCEL_INVESTMENT_CHOICE` | Snapshot unavailable/miss | 503 | `SYSTEM_ERROR` | `err.investment-choice.cancel.unavailable`, `[]` | One feature-owned `LOCAL_SYSTEM` `FailureEvent`; no shared duplicate |

These are feature-policy errors returned before throttle and password
verification. Generic issuance-envelope errors remain the existing 400
contract; wrong-password behavior is unchanged. CIM execution still returns
the gate's existing 403 and downstream/business/system outcomes, but does not
repeat these feature checks. Personal Information issuance and PUT behavior
remain unchanged.

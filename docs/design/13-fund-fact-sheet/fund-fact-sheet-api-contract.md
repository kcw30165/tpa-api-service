# Fund Fact Sheet FE-BFF API Contract

## Endpoint

```http
GET /api/v1/fund-fact-sheet
```

No request body and no query parameters.

## Request context

| Header / cookie | Purpose |
|---|---|
| `portal_sid` cookie | Authenticated session |
| `Account-Ref` | Selected account; `PortalAccessContextWebFilter` resolves `policyNo`, `certNo`, `schemeType`, `accountEnv` and actor role |
| CSRF header (`RequestHeaderContextKeys.XSRF_TOKEN_HEADER`) | Required by the filter whenever `Account-Ref` is present |
| `X-Request-Id` | Request correlation |
| `Accept-Language` | `en` or `zh_HK`; resolved with `RequestLanguageResolver.resolve(headers, acceptLanguage, "en")` |

FE must not send account metadata, reference date or fund data.

## Success response (HTTP 200)

```json
{
  "success": true,
  "status": "SUCCESS",
  "result": {
    "data": {
      "rows": [
        {
          "seq": 10060,
          "fundName": "Fidelity Balanced Fund",
          "fundNameEn": "Fidelity Balanced Fund",
          "fundNameZh": "富達均衡基金",
          "factsheetUrl": "https://www.bcthk.com/f/PUP/DB/publications/DBA_dblf_ffs.pdf"
        },
        {
          "seq": 10080,
          "fundName": "BlackRock Global Equity Fund",
          "fundNameEn": "BlackRock Global Equity Fund",
          "fundNameZh": null
        }
      ]
    }
  },
  "messages": [],
  "errors": []
}
```

Chinese names above are illustrative only. The second row shows an `en` response for a fund with no Chinese name and no available link.

## Row fields

| Field | Type | Source | Rule |
|---|---|---|---|
| `seq` | integer | `seq` | Unique row id; FE row key |
| `fundName` | string | `name-eng` / `name-chi` | `en`: `name-eng`. `zh_HK`: `name-chi`, falling back to `name-eng` when `name-chi` is null or blank |
| `fundNameEn` | string | `name-eng` | For FE bilingual filtering; passed through as received |
| `fundNameZh` | string or `null` | `name-chi` | For FE bilingual filtering; passed through as received; null serialized as `null` |
| `factsheetUrl` | string, optional | `link-eng` / `link-chi` | `en`: `link-eng`. `zh_HK`: `link-chi`. No cross-language fallback. Omitted when empty or rejected by `UrlValidator`; otherwise returned exactly as received |

`name-eng` is mandatory downstream. A blank or null value is passed through and logged, not rejected.

## Collection rules

- `rows` is always present. When there are no funds (TAS returns an empty `FundFactSheet` list), `rows` is `[]` and FE shows its label.
- Rows from every `data[]` element are flattened into one list.
- Order: `fundName` ascending (lower-cased with `Locale.ROOT`, plain character-code order, no `Collator`), then `seq` ascending.

## Failure responses

| Case | HTTP | `status` | `errors[0].code` |
|---|---|---|---|
| Missing or invalid session, CSRF or Account-Ref | Existing filter and handler behaviour | Existing | Existing |
| Any non-empty `err-message` (including `err.scheme.invalid`); empty `err-message` with missing or empty `data`; null data item, list or row; transport failure; timeout; `policyNo`, `certNo` or `schemeType` null or blank in the account context (logged, APIM not called) | 503 | `SYSTEM_ERROR` | `err.fund-fact-sheet.retrieval.unavailable` |

`errors[0].type` is `SYSTEM`. `DOWNSTREAM_REJECTED` and `DOWNSTREAM_ERROR` are never exposed. FE owns message text.

The 503 error carries a code only: `message` is omitted from the JSON. A missing `Account-Ref` header leaves no `PortalAccessContext`, and the existing handler returns HTTP 400, `BUSINESS_REJECTED`, `err.member.context.invalid`. `Account-Ref` without the `portal_sid` cookie or the CSRF header fails in `PortalAccessContextWebFilter` with its existing error. No route registration or security configuration change is needed (`SecurityConfig` requires authentication for every exchange by default).

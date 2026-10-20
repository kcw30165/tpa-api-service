# Fund Price FE-BFF API Contract

## Common request context

All endpoints are authenticated and selected-account scoped. Existing `portal_sid`, `Account-Ref`, `X-Request-Id`, and `Accept-Language` conventions apply. FE must not supply authoritative account metadata.

## 1. Load mode metadata

```http
GET /api/v1/fund-price/mode
```

Conceptual successful result data:

```json
{
  "mode": "DAILY",
  "availableFundPriceDates": ["18/09/2026", "18/05/2026"],
  "maximumFundPriceHistoryMonths": 36,
  "referenceDate": "18/09/2026"
}
```

The exact outer response wrapper must reuse the repository standard for authenticated read APIs. The mode endpoint initializes or replaces Fund Price metadata for the selected account in authenticated session state.

## 2. Retrieve Fund Price

```http
GET /api/v1/fund-price?date={value}
```

- `date` always follows the configured full-date request format (`dd/MM/yyyy`), for both `DAILY` and `MONTHLY` mode -- there is no separate MONTHLY input format. FE must supply one of the exact `availableFundPriceDates` values Java already returned via the `/mode` endpoint.
- For both `DAILY` and `MONTHLY` mode, the supplied date is used directly as both
`from-date` and `to-date` -- there is no calendar-month range expansion for MONTHLY.
Mode affects only which FE picker (day vs month) is rendered and which cached
available-date list the date is validated against; it has no effect on the downstream
TRPGetFundPrice request shape. (Correction, Nick: an earlier version of this contract
incorrectly specified month-range derivation for MONTHLY.)
- Missing, malformed, unavailable, or out-of-range input is a standard HTTP 400 validation failure and must not call APIM.

Conceptual returned fields per row:

```json
{
  "fundCode": "DUSM",
  "fundName": "Fidelity USD Money Market Fund",
  "priceDate": "05/05/2026",
  "fundCurrency": "HKD",
  "bidUnitPrice": 159.7628,
  "offerUnitPrice": 159.7628,
  "riskLevel": "1.5"
}
```

Java must model supported fields explicitly. It must not expose APIM DTOs or implement generic arbitrary-field passthrough.

When the cached fund-price-type is BIDOFFER PRICE, Java returns bidUnitPrice and
offerUnitPrice as shown above. When it is UNIT PRICE, Java returns a single unitPrice
field sourced from bid-price instead of the two fields; fund-price-type itself is never
returned to FE, which owns display labelling. fundName resolves per Accept-Language;
there is no separate Chinese field in the web (non-export) response.

riskLevel carries the raw downstream risk-level string verbatim (for example "1" or
"1.5"); Java does not reformat or zero-pad it. Emission is result-set scoped: when at
least one fund in the result set carries a risk level, riskLevel is present on every
row with absent values serialized as null; when no fund carries one, the key is absent
from every row.

Rows are ordered by risk level ascending (an empty risk level ranks as the highest risk
and sorts last), then English fund name ascending, then unit price ascending, where
unit price is the downstream bid-price under both fund-price-type values. fund-seq is
not returned and is not a sort key.

maxRiskLevel is a single top-level field on the response envelope, not a per-row
field: Java echoes the cached max-risk-level value (from the mode fetch) only when at
least one fund in that specific response's result set carries a riskLevel; if no fund
in the result set has a riskLevel, maxRiskLevel is omitted from the response envelope
entirely, regardless of what is cached in session state.

## 3. Export Fund Price

```http
GET /api/v1/fund-price/export
```

- No FE date-range parameters.
- Filename: `fund_price_info.xlsx`.
- Media type: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.
- Headers are always bilingual and do not vary by request language; `Accept-Language`
  does not select the workbook header language.
- Risk level is a sort key only for export, per fund-price-excel-export-contract.md: rows are
  not exported with a `riskLevel` field, and the workbook does not carry `maxRiskLevel`. This
  corrects an earlier version of this contract, which incorrectly stated the export carries
  both fields under the same emission rule as the query response.

## Public failures

- Invalid FE date input: project-standard HTTP 400 validation response.
- Missing, stale, malformed, mismatched, or unusable session metadata: HTTP 503 `SYSTEM_ERROR`.
- Any downstream nonblank `err-message`: HTTP 503 `SYSTEM_ERROR`. (Pending change, Fund Comparison CR D6, awaiting downstream confirmation: `err.tpa.invalid.date` from TRPGetFundPrice will map to HTTP 400 `err.fund-price.request.invalid`; TRPGetFundPriceMode is unchanged. See docs/design/13-fund-comparison/09-options-session-cache-change.md.)
- Malformed or inconsistent downstream success data: HTTP 503 `SYSTEM_ERROR`.
- Raw downstream errors must not be exposed.

## Response envelope (query and mode endpoints)

`GET /api/v1/fund-price/mode` and `GET /api/v1/fund-price` both return the repository-standard
`MutationResponse` envelope; the payload sits under `result.data`:

```json
{"success": true, "status": "SUCCESS", "result": {"data": {"rows": []}}, "messages": [], "errors": []}
```

For the query endpoint `result.data` is the rows object described in Section 2 (with the
conditional top-level `maxRiskLevel`); for the mode endpoint it is the mode metadata in
Section 1. Failures use the same envelope with `success: false` and populated `errors`.
`GET /api/v1/fund-price/export` is a binary XLSX download and is not wrapped.

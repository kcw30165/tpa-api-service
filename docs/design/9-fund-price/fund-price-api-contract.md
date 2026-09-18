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

- For `DAILY`, `date` follows the configured full-date request format.
- For `MONTHLY`, `date` follows the configured month request format.
- Missing, malformed, unavailable, or out-of-range input is a standard HTTP 400 validation failure and must not call APIM.

Conceptual returned fields per row:

```json
{
  "fundCode": "DUSM",
  "fundName": "Fidelity USD Money Market Fund",
  "priceDate": "05/05/2026",
  "fundCurrency": "HKD",
  "bidUnitPrice": 159.7628,
  "offerUnitPrice": 159.7628
}
```

Java must model supported fields explicitly. It must not expose APIM DTOs or implement generic arbitrary-field passthrough.

When the cached fund-price-type is BIDOFFER PRICE, Java returns bidUnitPrice and offerUnitPrice as shown above. When it is UNIT PRICE, Java returns a single unitPrice field sourced from bid-price instead of the two fields; fund-price-type itself is never returned to FE, which owns display labelling. fundCode/fundName resolve per Accept-Language; there is no separate Chinese field in the web (non-export) response.

## 3. Export Fund Price

```http
GET /api/v1/fund-price/export
```

- No FE date-range parameters.
- Filename: `fund_price_info.xlsx`.
- Media type: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.
- Uses `Accept-Language` for header localization.

## Public failures

- Invalid FE date input: project-standard HTTP 400 validation response.
- Missing, stale, malformed, mismatched, or unusable session metadata: HTTP 503 `SYSTEM_ERROR`.
- Any downstream nonblank `err-message`: HTTP 503 `SYSTEM_ERROR`.
- Malformed or inconsistent downstream success data: HTTP 503 `SYSTEM_ERROR`.
- Raw downstream errors must not be exposed.

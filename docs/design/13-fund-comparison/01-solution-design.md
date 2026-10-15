# 1. Solution Design

## 1.1 Objective

Allow an authenticated member to retrieve scheme-specific fund comparison options and then retrieve raw date/unit-price points for one to five selected funds over a selected period. The frontend calculates percentage changes and renders the chart.

## 1.2 Context and responsibilities

### Downstream system

- Owns which funds are available for the member's scheme.
- Owns the configured time-range string.
- Supplies localized English and Chinese fund names.
- Supplies historical bid and offer prices through the existing Fund Price operation.

### BFF

- Resolves `policyNo` and `accountEnv` from authenticated account context.
- Retrieves and normalizes Fund Comparison options.
- Selects and sorts fund names using the existing Fund Price language-selection and sorting behavior.
- Validates and converts time-range tokens.
- Resolves `fromDate` and `toDate` using `ReferenceDatePort.resolveReferenceDate(accountEnv)`.
- accountEnv is resolved by the controller via `CurrentPortalAccessContextResolver.current()` (the same source as `policyNo`) and passed into the application layer as a primitive; `PortalAccessContext` is not exposed to the application layer.
- Extends the existing Fund Price downstream request shape with fund-list, via a new dedicated BFF command/port/adapter -- the existing Fund Price command, port, and adapter are not modified
- Maps downstream `bid-price` to public `unitPrice`.
- Groups returned price points by `fundCode`.
- Preserves the shared public response envelope.

### Frontend

- Presents options and enforces the BRD's minimum-two selection rule in the comparison UI.
- Allows funds to be selected incrementally, so the BFF accepts one to five funds.
- Calculates percentage changes relative to the first applicable point.
- Owns rounding, chart axes, sampling/labels, messages, and visual rendering.

## 1.3 Component flow

### Options flow

1. Controller accepts `POST /api/v1/fund-comparison/options`.
2. Controller resolves `PortalAccessContext` via `CurrentPortalAccessContextResolver.current()` and passes `accountEnv` and `policyNo` as primitives to the application service; the application service never receives `PortalAccessContext` itself.
3. Outbound adapter calls `POST /TRPGetFundCmpOpt`.
4. Mapper splits `time-range`, trims tokens, validates tokens, drops invalid tokens with logging, and removes duplicates.
5. If no valid token remains, the use case fails with `err.fund-comparison.retrieval.unavailable`.
6. Mapper selects localized fund names from the downstream fund list using the existing Fund Price language behavior.
7. Funds are ordered using the existing Fund Price fund-name sorting behavior.
8. Default time range is selected using `D < M < Y < YTD`; within a unit the smallest positive integer wins.
9. Controller returns the shared success envelope.

### Prices flow

1. Controller accepts `POST /api/v1/fund-comparison/prices` with `fundCodes` and `timeRange`.
2. Input validation rejects blank/duplicate-invalid inputs and more than five unique fund codes.
3. Application service validates time-range syntax only. It does not persist or re-fetch prior options.
4. Application service resolves the reference date via `ReferenceDatePort.resolveReferenceDate(accountEnv)`.
5. Date range is calculated inclusively.
6. Fund codes are joined with commas and no spaces.
7. The downstream TRPGetFundPrice operation is called (via Fund Comparison's own dedicated adapter, not the existing FundPriceSourcePort/ApimFundPriceSourceAdapter) with policy-no, formatted from-date, formatted to-date, and fund-list
8. Downstream rows are grouped into series by `fund-code`.
9. Each `bid-price` becomes `unitPrice`; `offer-price` is ignored.
10. Public dates use `dd/MM/yyyy`.
11. A valid empty `FundPrice` returns success with `series: []`.

## 1.4 Inclusive date rules

Let `R` be the date returned by `ReferenceDatePort.resolveReferenceDate(accountEnv)`.

- `XD`: `fromDate = R.minusDays(X - 1)`
- `XM`: `fromDate = R.minusMonths(X).plusDays(1)`
- `XY`: `fromDate = R.minusYears(X).plusDays(1)`
- `YTD`: `fromDate = 1 January of R.year`
- Every range: `toDate = R`

`X` must be a positive integer. Java `LocalDate` month/year adjustment is authoritative for month-end and leap-day cases.

Examples for `R = 15/10/2026`:

- `10D`: `06/10/2026` to `15/10/2026`
- `1M`: `16/09/2026` to `15/10/2026`
- `1Y`: `16/10/2025` to `15/10/2026`
- `YTD`: `01/01/2026` to `15/10/2026`

## 1.5 State and caching

The feature is stateless. It does not store options in Redis or another server-side store. The prices endpoint validates the time-range syntax rather than membership in an earlier options response.

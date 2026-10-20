# 5. Test Strategy and Traceability

## 5.1 Unit tests

### Time-range parser

- parses both expected downstream strings;
- trims whitespace;
- rejects zero, negative, decimal, signed, lowercase, unknown-unit, and combined tokens;
- removes duplicates, retaining first occurrence;
- fails with `err.fund-comparison.retrieval.unavailable` when nothing is valid;
- selects default using `D < M < Y < YTD` and smallest integer within a unit.

### Date-range calculator

Using a stubbed `ReferenceDatePort`:

- `10D` is inclusive of the reference date;
- `1M` and `1Y` are inclusive via `plusDays(1)`;
- `YTD` starts on 1 January;
- month-end adjustment follows `LocalDate`;
- leap-year/year subtraction is covered;
- public and downstream dates format as `dd/MM/yyyy` where required.

### Options mapper

- selects English or Chinese display name according to existing Fund Price behavior;
- reuses the existing sorting behavior;
- handles invalid tokens without failing valid options;
- outputs only `fundCode` and `fundName` per fund.

### Prices validation and mapping

- accepts one to five funds;
- rejects more than five unique funds;
- rejects missing/blank fund codes;
- deduplicates repeated fund codes;
- validates time-range syntax, then membership against the session-cached options (superseded 2026-10-20; required cases in 09 section 9.8);
- joins fund codes without spaces;
- maps `bid-price` to `unitPrice`;
- does not expose `offer-price`;
- groups and orders price points;
- maps valid empty FundPrice to empty series.

## 5.2 Regression tests

- Every pre-existing Fund Price caller sends or resolves `fund-list` as empty.
- Existing Fund Price public responses remain unchanged.
- Existing Fund Price localized-name selection and sorting remain unchanged.
- Shared envelopes still include `messages` and `errors` on success.

## 5.3 Adapter/contract tests

- Exact JSON names for `TRPGetFundCmpOpt` request and response.
- Exact JSON names for the extended `TRPGetFundPrice` request.
- Empty `fund-list` serialization for legacy callers.
- Comma-separated selected funds for Fund Comparison.
- Empty downstream FundPrice response maps to HTTP 200.
- Transport, malformed envelope, and non-empty error outcomes follow established technical mapping.

## 5.4 Controller tests

- Both POST routes use `result.data`.
- Success includes top-level empty `messages` and `errors` arrays.
- Options contract contains ranges, nullable default, and localized funds.
- Prices contract contains `fromDate`, `toDate`, and grouped series.
- Invalid frontend requests return HTTP 400, `VALIDATION_FAILED`, and `err.fund-comparison.request.invalid`.
- Retrieval and unusable-options failures return HTTP 503, `SYSTEM_ERROR`, and `err.fund-comparison.retrieval.unavailable`.
- No-valid-time-range never returns a successful empty options response.

## 5.5 BRD traceability

- Maximum five funds: enforced by BFF and frontend.
- Minimum two for graph comparison: frontend responsibility; BFF accepts one for incremental selection.
- Scheme-specific fund names and time ranges: supplied by downstream.
- Alphabetical fund ordering: existing Fund Price localized comparator.
- Comparison graph and percentage change: frontend responsibility.
- Date/unit-price observations: BFF prices response.
- No data: successful empty series.

## 5.6 Exact error-contract regression tests

- missing/blank/invalid request time range uses only `err.fund-comparison.request.invalid`;
- more than five unique fund codes uses only `err.fund-comparison.request.invalid`;
- missing or blank downstream options time range uses only `err.fund-comparison.retrieval.unavailable`;
- a mixture of valid and invalid downstream tokens succeeds with valid tokens;
- all-invalid downstream tokens return HTTP 503;
- raw downstream `err-message` is never exposed;
- legitimate empty `FundPrice` remains HTTP 200 with empty series.


## 5.7 Options session cache and membership (Change Request, 2026-10-20)

Required cases are listed in 09-options-session-cache-change.md section 9.8. The section 5.6
scenario "missing/blank/invalid request time range uses only `err.fund-comparison.request.invalid`"
now also covers syntactically valid but unoffered time ranges and unlisted fund codes.

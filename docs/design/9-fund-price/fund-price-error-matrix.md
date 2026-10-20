# Fund Price Error Matrix

## FE input errors

- Missing `date`: HTTP 400, standard validation status/code, APIM not called.
- Malformed date (either mode -- DAILY and MONTHLY share the same full-date format): HTTP 400, field target `date`, APIM not called.
- Date outside maximum history (`R.minusMonths(M) <= D <= R`, either mode): HTTP 400, field target `date`, APIM not called.
- Date does not exactly match one parsed available date (either mode): HTTP 400, field target `date`, APIM not called.

## Session and contract errors

The following map to HTTP 503, `ApiStatus.SYSTEM_ERROR`, and the repository generic unavailable code:

- Missing Fund Price mode metadata.
- Metadata bound to a different selected account.
- Metadata reference date differs from current authoritative reference date.
- Blank or unsupported mode.
- Invalid maximum history months.
- Invalid available-date value.
- Nonblank risk-level outside the downstream-supported value set.
- Session read/write, encryption, or concurrency failure.

## Downstream errors

Every nonblank `err-message` from either operation maps to HTTP 503 `SYSTEM_ERROR`, including:

- `err.tpa.invalid.date` from TRPGetFundPriceMode (from TRPGetFundPrice: see the note below)
- `err.tpa.no.fund.price`
- unknown or generic downstream errors

Decided 2026-10-20 (Fund Comparison CR D6, takes effect with Fund Comparison Milestone 5 sub-task 5.5): `err.tpa.invalid.date` from TRPGetFundPrice (Fund Price query, Fund Price export, Fund Comparison prices) maps to HTTP 400 `VALIDATION_FAILED`; for Fund Price the code is `err.fund-price.request.invalid`, and Fund Price's other 400s stay `BUSINESS_REJECTED`. TRPGetFundPriceMode is unchanged. See docs/design/13-fund-comparison/09-options-session-cache-change.md.

Transport, timeout, token, certificate, crypto, deserialization, empty data, invalid item count, duplicate/unmatched fund codes, and workbook generation failures also map to HTTP 503 `SYSTEM_ERROR`.

Raw downstream messages and internal exception text are never returned to FE.

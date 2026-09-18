# Fund Price Error Matrix

## FE input errors

- Missing `date`: HTTP 400, standard validation status/code, APIM not called.
- Malformed DAILY date: HTTP 400, field target `date`, APIM not called.
- Malformed MONTHLY value: HTTP 400, field target `date`, APIM not called.
- Date/month outside maximum history: HTTP 400, field target `date`, APIM not called.
- Date unavailable or month contains no available date: HTTP 400, field target `date`, APIM not called.

## Session and contract errors

The following map to HTTP 503, `ApiStatus.SYSTEM_ERROR`, and the repository generic unavailable code:

- Missing Fund Price mode metadata.
- Metadata bound to a different selected account.
- Metadata reference date differs from current authoritative reference date.
- Blank or unsupported mode.
- Invalid maximum history months.
- Invalid available-date value.
- Session read/write, encryption, or concurrency failure.

## Downstream errors

Every nonblank `err-message` from either operation maps to HTTP 503 `SYSTEM_ERROR`, including:

- `err.tpa.invalid.date`
- `err.tpa.no.fund.price`
- unknown or generic downstream errors

Transport, timeout, token, certificate, crypto, deserialization, empty data, invalid item count, duplicate/unmatched fund codes, and workbook generation failures also map to HTTP 503 `SYSTEM_ERROR`.

Raw downstream messages and internal exception text are never returned to FE.

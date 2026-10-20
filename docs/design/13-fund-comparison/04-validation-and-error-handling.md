# 4. Validation and Error Handling

## 4.1 Authoritative public error codes

Fund Comparison follows the existing Fund Price two-code practice:

- `err.fund-comparison.request.invalid`
- `err.fund-comparison.retrieval.unavailable`

Raw downstream messages and internal diagnostic reasons are not exposed as public error codes.

## 4.2 Time-range token grammar

A valid token is:

```regex
^(?:[1-9][0-9]*[DMY]|YTD)$
```

This expresses `XD`, `XM`, `XY`, or `YTD`, where `X` is a positive integer.

### Options normalization

1. Split the downstream value by comma.
2. Trim whitespace.
3. Validate each token.
4. Drop and log invalid tokens without failing the whole response only when at least one valid token remains.
5. Remove duplicates while retaining the first occurrence.
6. Preserve normalized downstream order in `timeRanges`.
7. If no valid token remains, fail with `err.fund-comparison.retrieval.unavailable`.

A null, missing, or blank `time-range` is the same retrieval failure. It must not produce `timeRanges: []` with a null default.

### Default selection

Use fixed unit priority `D < M < Y < YTD`. Within `D`, `M`, or `Y`, choose the smallest integer. This selection is independent of array order.

## 4.3 Invalid frontend request

The following conditions use the existing Fund Price request-validation practice:

- `fundCodes` is missing or null;
- a fund-code entry is null or blank;
- no nonblank fund code remains;
- more than five unique fund codes are supplied;
- `timeRange` is missing, blank, or syntactically invalid;
- the positive integer cannot be parsed safely;
- date-range calculation cannot produce a usable range.

Public mapping:

- HTTP: `400 BAD_REQUEST`
- API status: `VALIDATION_FAILED`
- Code: `err.fund-comparison.request.invalid`
- Error type: `VALIDATION`
- Target/source: `SERVER`
- `result`: null
- `messages`: empty array

The implementation must reuse the existing Fund Price application-exception and shared response mechanism rather than independently constructing the envelope.

Fund codes are trimmed and deduplicated case-insensitively while preserving first occurrence order. Superseded 2026-10-20: after syntax validation the BFF validates the time range and every fund code for membership in the session-cached options, with one refresh-and-recheck. A membership failure against current options is HTTP 400 `VALIDATION_FAILED`, `err.fund-comparison.request.invalid`, and TRPGetFundPrice is not called (09 sections 9.4-9.6).

## 4.4 Retrieval unavailable

The following conditions use the Fund Price retrieval-unavailable practice:

- required authenticated account context, including `policyNo`, is unavailable;
- downstream transport, timeout, decryption, or response mapping fails;
- the downstream envelope is missing or malformed;
- downstream `err-message` is missing or nonblank (exception pending confirmation, D6: TRPGetFundPrice `err.tpa.invalid.date` maps to HTTP 400, 09 section 9.6);
- options `data` is missing, empty, or structurally unusable;
- options `time-range` is missing, null, or blank;
- no valid options time-range token remains after normalization;
- required downstream fund or price content is structurally invalid;
- options retrieval fails while the prices endpoint refreshes the options cache (cache miss or recheck).

Public mapping:

- HTTP: `503 SERVICE_UNAVAILABLE`
- API status: `SYSTEM_ERROR`
- Code: `err.fund-comparison.retrieval.unavailable`
- Error type: `SYSTEM`
- Target/source: `SERVER`
- `result`: null
- `messages`: empty array

Internal logs preserve the root cause and request correlation but do not expose protected values or raw downstream error text.

## 4.5 Legitimate empty price result

The following response is an approved no-data outcome, not a retrieval failure:

```json
{
  "response": {
    "err-message": "",
    "data": [
      {
        "FundPrice": [],
        "FundInfo": []
      }
    ]
  }
}
```

It maps to HTTP 200 with `result.data.series: []`, `messages: []`, and `errors: []`.

## 4.6 Logging

Log without exposing protected values:

- each invalid time-range token and rejection reason;
- duplicate time-range tokens and fund codes;
- the no-valid-token retrieval failure;
- malformed fund or price rows;
- legitimate no-price data at an appropriate non-error level;
- technical failures with request correlation and preserved root causes.

Do not log policy numbers, `accountRef`, full downstream payloads, or other protected account data. Cache and membership events are defined in 09 section 9.7.

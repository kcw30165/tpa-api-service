# Fund Fact Sheet Error Matrix

## Request context

Missing or invalid session, CSRF or Account-Ref: unchanged existing filter/handler behaviour. APIM is not called. A missing `Account-Ref` header gives HTTP 400, `BUSINESS_REJECTED`, `err.member.context.invalid` through `current()`.

## Downstream and transport

All map to `FundFactSheetRetrievalException.systemFailure()`, handled by a dedicated `@ExceptionHandler` in `ApiExceptionHandler` (`adapter.in.web.advice`), which must not name `ApiStatus.DOWNSTREAM_ERROR` or `ApiStatus.DOWNSTREAM_REJECTED`:

- HTTP 503, `ApiStatus.SYSTEM_ERROR`
- `ApiError.system(ErrorCodes.FUND_FACT_SHEET_RETRIEVAL_UNAVAILABLE, null, "SERVER")` (signature: code, message, source; a null message is omitted from the JSON)
- Constant `ErrorCodes.FUND_FACT_SHEET_RETRIEVAL_UNAVAILABLE`

| Cause | Mapping |
|---|---|
| `err-message` = `err.scheme.invalid` | 503 (policy number comes from trusted server state; a miss is a system inconsistency) |
| Any other non-empty `err-message` except `err.tpa.no.fund.fact.sheet` | 503 |
| `response` null, `err-message` null, `data` null | 503 |
| `err-message` empty with `data` `[]` | 503 (TAS signals no funds with `err.tpa.no.fund.fact.sheet`) |
| Null data item, `FundFactSheet` list or row | 503 |
| Timeout, transport, token, certificate, crypto, deserialization | 503 |
| Reference date resolution failure | 503 through the use case's `onErrorMap` (`ReferenceDateAdapter` already falls back to the Hong Kong date on Redis errors, so this is a defensive path) |

Every 503 cause is logged without values: by the executor (`apim.contract.invalid`, `apim.business.rejected`, `apim.exchange.failed`) or, for a null data item, list or row, by the adapter. Raw downstream codes and exception text are never public.

## Not errors

| Case | Result |
|---|---|
| `err-message` = `err.tpa.no.fund.fact.sheet` | 200, `rows: []` (executor logs `apim.business.rejected` at WARN) |
| Empty or rejected link | Row kept, `factsheetUrl` omitted |
| Missing `seq` | Row dropped and logged |
| Duplicate `seq` | First occurrence kept; later ones dropped and logged |
| Blank `name-eng` | Row kept and logged |
| Blank `name-chi` | Row kept; `zh_HK` display name falls back to `name-eng` |

## Message text

No message configuration is added. Public text is FE-owned; the new handler passes a null message and never calls `ErrorMessageResolver` or `resolvePublicMessage`, so the planned resolver removal needs no change here (it will change the `ApiExceptionHandler` constructor, which `ApiExceptionHandlerFundPriceTest` also calls).

## Precedent note

Fund Price's handler uses `ApiError.form(...)`. Fund Fact Sheet uses `ApiError.system(...)` because the failure is a system failure. This difference is deliberate.

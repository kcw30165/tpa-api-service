# Fund Fact Sheet Error Matrix

## Request context

Missing or invalid session, CSRF or Account-Ref: unchanged existing filter/handler behaviour. APIM is not called.

## Downstream and transport

All map to `FundFactSheetRetrievalException.systemFailure()`, handled by a dedicated `@ExceptionHandler` in `ApiExceptionHandler`:

- HTTP 503, `ApiStatus.SYSTEM_ERROR`
- `ApiError.system("err.fund-fact-sheet.retrieval.unavailable", ..., "SERVER")`
- Constant `ErrorCodes.FUND_FACT_SHEET_RETRIEVAL_UNAVAILABLE`

| Cause | Mapping |
|---|---|
| `err-message` = `err.scheme.invalid` | 503 (policy number comes from trusted server state; a miss is a system inconsistency) |
| Any other non-empty `err-message` except `err.tpa.no.fund.fact.sheet` | 503 |
| `response` null, `err-message` null, `data` null | 503 |
| `err-message` empty with `data` `[]` | 503 (TAS signals no funds with `err.tpa.no.fund.fact.sheet`) |
| Null data item, `FundFactSheet` list or row | 503 |
| Timeout, transport, token, certificate, crypto, deserialization | 503 |
| Reference date resolution failure | Existing reference-date behaviour (not re-mapped) |

Every 503 cause is logged by the adapter without values. Raw downstream codes and exception text are never public.

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

No message configuration is added. Public text is FE-owned; the handler's existing message resolution is left as-is until the planned resolver removal.

## Precedent note

Fund Price's handler uses `ApiError.form(...)`. Fund Fact Sheet uses `ApiError.system(...)` because the failure is a system failure. This difference is deliberate.

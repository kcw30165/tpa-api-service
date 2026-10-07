# Fund Fact Sheet Decisions and Open Questions

## Approved decisions

| # | Decision |
|---|---|
| D-1 | Final Draft is the baseline; comment decisions apply. Fund code search, description search, fuzzy search, PDF-only wording and download filename are removed |
| D-2 | Data source: `TRPGetFundFactSheet` via APIM; TAS decides which funds are returned |
| D-3 | Search and bilingual filtering are FE-only; BFF returns the full list |
| D-4 | BFF returns URLs only |
| D-5 | Endpoint `GET /api/v1/fund-fact-sheet`; APIM path `/ws/NGTPA/v1/TRPGetFundFactSheet` |
| D-6 | Row fields `seq`, `fundName`, `fundNameEn`, `fundNameZh`, `factsheetUrl`; collection `rows` under `result.data` |
| D-7 | Display name and link chosen by `Accept-Language`; Chinese name falls back to English; no link fallback |
| D-8 | Sort by display name ascending (Fund Price fund-name comparison), then `seq` ascending |
| D-9 | No funds: TAS returns `err.tpa.no.fund.fact.sheet` without `data` (confirmed by the downstream developer). BFF returns 200 with `rows: []`; FE shows a label. Empty `err-message` with `data: []` is a system failure |
| D-10 | Missing `seq`: drop and log. Duplicate `seq` (guaranteed not to occur): keep first, drop later, log. `name-eng` is mandatory; a blank value is kept and logged. `name-chi` passed through as received (null as `null`); `zh_HK` display name falls back to `name-eng` |
| D-11 | Every non-empty `err-message` except `err.tpa.no.fund.fact.sheet` (including `err.scheme.invalid`), every structural null and every transport failure: 503 `SYSTEM_ERROR`, `err.fund-fact-sheet.retrieval.unavailable`, logged without values |
| D-12 | Java returns error codes only; no message configuration added |
| D-13 | New shared `UrlValidator` used by Fund Fact Sheet; Account Balance untouched; its migration documented as a deferred refactor |
| D-14 | Links passed through verbatim except those omitted by `UrlValidator`; no scheme or host restriction |
| D-15 | CLP exclusion and Config Matrix 6.8.3.2 deferred (tech lead) |
| D-16 | `policy-no` from `CurrentPortalAccessContextResolver`; `ref-date` from `ReferenceDatePort` |
| D-17 | `ref-date` uses a feature-local `DateTimeFormatter.ofPattern("dd/MM/uuuu")` with STRICT resolution; `fundPriceFullDateFormatter` is not reused |
| D-18 | `UrlValidator` lives in `application.service.presentation` and is not a Spring bean; each consumer constructs it inline in its configuration |
| D-19 | Adapter logging: the executor already logs contract-invalid, business-rejected and exchange-failed outcomes. The adapter logs only a null data item, a null `FundFactSheet` list and a null row, value-free, as `event=fund-fact-sheet.<name>` |
| D-20 | The use case maps every failure that is not a `FundFactSheetRetrievalException` to `systemFailure()` (as Gain/Loss and Account Balance do); `GetFundFactSheetCommand` carries no language, because language selection is in the web mapper; the 503 handler passes a null message and never calls the message resolver |
| D-21 | `err.tpa.no.fund.fact.sheet` is matched on the code alone: zero rows whether `data` is absent (confirmed by the downstream developer) or present |

## Pending decisions

None. Former P-1 to P-4 are resolved as D-9, D-10, D-11 and D-17.

## Open (non-blocking)

| # | Question |
|---|---|
| OQ-4 | Important Notes content source (label owned by FE) |

## Deferred

| Item | Reason |
|---|---|
| Account Balance migration to `UrlValidator` | Owner decision; see URL validator design |
| `UrlRule` Specification extension | No second rule yet |
| CLP / 6.8.3.2 gating | Tech lead |
| `TRPGetFundPrice` missing from routing ledger | Separate defect |
| Message resolver removal | Planned refactor; it changes the `ApiExceptionHandler` constructor used by `ApiExceptionHandlerFundPriceTest`; the Fund Fact Sheet handler does not use the resolver |
| Shared date formatting | The application always uses `dd/MM/yyyy`. Replace per-feature `DateTimeFormatter` beans and local patterns (Fund Price, Account Balance, Gain/Loss, Fund Fact Sheet) with one shared, framework-free date-format helper. Separate refactor |

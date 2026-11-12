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
| D-9 | Superseded by C-4: no funds is `err-message` empty with `data: [{"FundFactSheet": []}]`; BFF returns 200 with `rows: []` and FE shows a label. Empty `err-message` with `data: []` is a system failure |
| D-10 | Missing `seq`: drop and log. Duplicate `seq` (guaranteed not to occur): keep first, drop later, log. `name-eng` is mandatory; a blank value is kept and logged. `name-chi` passed through as received (null as `null`); `zh_HK` display name falls back to `name-eng` |
| D-11 | Every non-empty `err-message` (including `err.scheme.invalid`), every structural null and every transport failure: 503 `SYSTEM_ERROR`, `err.fund-fact-sheet.retrieval.unavailable`, logged without values (extended by C-3 and C-7) |
| D-12 | Java returns error codes only; no message configuration added |
| D-13 | New shared `UrlValidator` used by Fund Fact Sheet; Account Balance untouched; its migration documented as a deferred refactor |
| D-14 | Links passed through verbatim except those omitted by `UrlValidator`; no scheme or host restriction |
| D-15 | CLP exclusion and Config Matrix 6.8.3.2 deferred (tech lead) |
| D-16 | Superseded by C-1 and C-2: `policy-no`, `cert-no` and `sch-type` come from `CurrentPortalAccessContextResolver`; there is no `ref-date` |
| D-17 | Superseded by C-2: the feature-local `dd/MM/uuuu` formatter is removed together with `ref-date` |
| D-18 | `UrlValidator` lives in `application.service.presentation` and is not a Spring bean; each consumer constructs it inline in its configuration |
| D-19 | Adapter logging: the executor already logs contract-invalid, business-rejected and exchange-failed outcomes. The adapter logs only a null data item, a null `FundFactSheet` list and a null row, value-free, as `event=fund-fact-sheet.<name>` |
| D-20 | The use case maps every failure that is not a `FundFactSheetRetrievalException` to `systemFailure()` (as Gain/Loss and Account Balance do); `GetFundFactSheetCommand` carries no language, because language selection is in the web mapper; the 503 handler passes a null message and never calls the message resolver |
| D-21 | Superseded by C-3: `err.tpa.no.fund.fact.sheet` no longer exists and gets no special handling |

## Contract change decisions (2026-10-08, owner-confirmed)

The downstream `TRPGetFundFactSheet` contract changed (change notice). These decisions supersede D-9, D-11, D-16, D-17 and D-21.

| # | Decision |
|---|---|
| C-1 | `policy-no`, `cert-no` and `sch-type` come from `CurrentPortalAccessContextResolver.current()`, `PortalAccessContext.account()` (`policyNo()`, `certNo()`, `schemeType()`) |
| C-2 | `ref-date` is removed. `ReferenceDatePort` is no longer used by this feature, and the feature-local formatter (D-17) is removed |
| C-3 | `err.tpa.no.fund.fact.sheet` no longer exists. It gets no special handling: like any non-empty `err-message` it maps to 503 `SYSTEM_ERROR` |
| C-4 | No funds is `err-message` empty with `data: [{"FundFactSheet": []}]`; the BFF returns 200 with `rows: []` through the rule that an empty list inside a non-null item contributes zero rows |
| C-5 | `err-message` empty with `data: []` is malformed downstream data: 503 `SYSTEM_ERROR`, logged without values (unchanged) |
| C-6 | `cert-no` is sent as a JSON String, passed through unchanged from `AccountContext.certNo()`; the owner tested a String value against the downstream API and it is accepted. No numeric conversion |
| C-7 | If `policyNo`, `certNo` or `schemeType` is absent (null or blank), the BFF logs a value-free event and returns 503 `SYSTEM_ERROR`, `err.fund-fact-sheet.retrieval.unavailable`, without calling the downstream API. The use case performs the check |

## Pending decisions

None. Former P-1 to P-4 are resolved as D-9, D-10, D-11 and D-17 (D-9, D-11 and D-17 were later changed by C-2 to C-4).

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
| Shared date formatting | The application always uses `dd/MM/yyyy`. Replace per-feature `DateTimeFormatter` beans and local patterns (Fund Price, Account Balance, Gain/Loss) with one shared, framework-free date-format helper. Separate refactor |

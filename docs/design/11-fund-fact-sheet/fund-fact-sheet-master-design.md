# Fund Fact Sheet Master Design

## Responsibility boundaries

### Frontend
- Calls `GET /api/v1/fund-fact-sheet` on page load with existing authenticated context.
- Filters on `fundNameEn` and `fundNameZh` regardless of language.
- Disables the button when `factsheetUrl` is absent; shows a label when `rows` is empty.
- Owns all labels, messages and Important Notes.

### Java BFF
- Resolves the trusted selected-account context (`policyNo`, `certNo`, `schemeType`, `accountEnv`, actor role). If `policyNo`, `certNo` or `schemeType` is absent, it logs and returns HTTP 503 without calling APIM.
- Calls `TRPGetFundFactSheet` through the canonical executor.
- Treats an empty `FundFactSheet` list as an empty result (no funds).
- Flattens, validates and normalizes rows; validates links with the shared `UrlValidator`.
- Selects display name and link by language; sorts; maps to the public response.
- Maps every other downstream, contract or transport failure to HTTP 503 `SYSTEM_ERROR`.

### APIM/TAS
- Decides which funds are returned (including fund class and IBM/CHUBB shared links).
- Owns names, links and `seq`; guarantees `seq` uniqueness and a non-empty `name-eng`.

## Target flow

```text
FundFactSheetController
  -> GetFundFactSheetUseCase (application port in)
  -> GetFundFactSheetService (requires policyNo, certNo and schemeType; absent: log, 503, no APIM call)
  -> FundFactSheetSourcePort.fetch(FetchFundFactSheetCommand)
  -> ApimFundFactSheetSourceAdapter
  -> ApimExchangeExecutor.postEnvelope(ApimExchangeRequest)
  -> APIM/TAS
  <- RawFundFactSheetData (flattened; empty when every FundFactSheet list is empty)
  -> FundFactSheetNormalizer (row rules + UrlValidator)
  <- FundFactSheetResult
  -> FundFactSheetWebMapper (language selection + sort)
  <- MutationResponse<FundFactSheetResultResponse>
```

## Component placement

Layer placement follows existing repository conventions. Account Gain/Loss is the reference for the controller and use-case port shape; Fund Price is the reference for the APIM adapter, properties and display-name rule.

| Component | Layer | Reference precedent |
|---|---|---|
| `FundFactSheetController` | `adapter.in.web.fundfactsheet` | `AccountGainLossController` |
| Response records | `adapter.in.web.fundfactsheet.response` | `FundPriceQueryResponse` / `FundPriceQueryResultResponse` |
| `FundFactSheetWebMapper` | `adapter.in.web.fundfactsheet.mapper` | `FundPriceWebMapper.displayFundName` |
| `GetFundFactSheetUseCase` | `application.port.in` | `GetAccountGainLossUseCase` |
| `GetFundFactSheetCommand`, `FetchFundFactSheetCommand`, raw and result DTOs | `application.dto.fundfactsheet` | Fund Price DTOs |
| `GetFundFactSheetService`, `FundFactSheetNormalizer` | `application.usecase.fundfactsheet` | Fund Price use cases (framework-free) |
| `FundFactSheetSourcePort`, `FundFactSheetPresentationEventPort` | `application.port.out` | `FundPriceSourcePort`, `AccountBalancePresentationEventPort` |
| `FundFactSheetRetrievalException` | `application.exception` | `FundPriceRetrievalException` |
| `UrlValidator` | `application.service.presentation` (framework-free; not a Spring bean) | See `fund-fact-sheet-url-validator-design.md` |
| `ApimFundFactSheetSourceAdapter`, properties, validation config (wire DTOs in `adapter.out.apim.fundfactsheet.dto`) | `adapter.out.apim.fundfactsheet` | `ApimFundPriceModeSourceAdapter`, `FundPriceModeApimProperties` |
| `FundFactSheetPresentationEventLogger` | `infrastructure.logging` | `AccountBalancePresentationEventLogger` |
| `FundFactSheetApplicationConfig` | `config.fundfactsheet` | `FundPriceModeApplicationConfig` |

`UrlValidator` goes in `application.service.presentation`, beside the generic `DynamicDescriptionResolver` and `ExactPrecisionRenderer` (Stage 0, row 0.2).

## APIM classification

- `ApimDatabaseScope.ACCOUNT_ENV`
- Actor role and account environment from `PortalAccessContext`
- `ApimRetryEligibility.SAFE_BUSINESS_CALL`
- Routing ledger: add `TRPGetFundFactSheet`; cardinality 19 -> 20.

## Session state

None. The feature stores nothing in the authenticated session and does not read the session cookie directly; `PortalAccessContextWebFilter` resolves the selected account.

## Out of scope

CLP exclusion, Config Matrix 6.8.3.2, Important Notes content, Account Balance changes, message configuration, shared date formatting.

# Fund Fact Sheet Sequence Diagrams

## Success

```mermaid
sequenceDiagram
    participant FE
    participant Filter as PortalAccessContextWebFilter
    participant Ctl as FundFactSheetController
    participant UC as GetFundFactSheetService
    participant Ref as ReferenceDatePort
    participant Ad as ApimFundFactSheetSourceAdapter
    participant APIM
    FE->>Filter: GET /api/v1/fund-fact-sheet (cookie, Account-Ref, CSRF, Accept-Language)
    Filter->>Ctl: PortalAccessContext in Reactor Context
    Ctl->>UC: GetFundFactSheetCommand(accountEnv, policyNo, role)
    UC->>Ref: resolveReferenceDate(accountEnv)
    Ref-->>UC: referenceDate
    UC->>Ad: fetch(FetchFundFactSheetCommand)
    Ad->>APIM: TRPGetFundFactSheet(policy-no, ref-date)
    APIM-->>Ad: envelope
    Ad-->>UC: RawFundFactSheetData (flattened)
    UC->>UC: normalize rows, UrlValidator on both links
    UC-->>Ctl: FundFactSheetResult
    Ctl->>Ctl: select language, sort, map
    Ctl-->>FE: 200 MutationResponse(rows)
```

## No funds

```mermaid
sequenceDiagram
    participant FE
    participant Ctl as FundFactSheetController
    participant Ad as ApimFundFactSheetSourceAdapter
    participant APIM
    Ad->>APIM: TRPGetFundFactSheet
    APIM-->>Ad: err-message = err.tpa.no.fund.fact.sheet
    Ad-->>Ctl: RawFundFactSheetData([])
    Ctl-->>FE: 200 MutationResponse(rows = [])
```

## Downstream failure

```mermaid
sequenceDiagram
    participant FE
    participant Ctl as FundFactSheetController
    participant Ad as ApimFundFactSheetSourceAdapter
    participant H as ApiExceptionHandler
    Ad-->>Ctl: FundFactSheetRetrievalException.systemFailure()
    Ctl-->>H: error signal
    H-->>FE: 503 SYSTEM_ERROR err.fund-fact-sheet.retrieval.unavailable
```

# Fund Price Master Design

## Responsibility boundaries

### Frontend

- Calls the mode endpoint when the page loads.
- Renders a day selector for `DAILY`.
- Renders a month selector for `MONTHLY`.
- Sends only the selected date/month and existing authenticated request context.
- Does not submit account environment, policy number, reference date, maximum history, or available-date authority.

### Java BFF

- Resolves trusted selected-account context.
- Resolves the authoritative reference date.
- Calls the two APIM operations through the canonical executor.
- Validates and stores mode metadata in encrypted account-scoped authenticated session state.
- Validates day/month requests against cached metadata.
- Derives downstream ranges.
- Reconciles downstream arrays and shapes FE responses.
- Generates XLSX.
- Normalizes all downstream nonblank errors to public 503 `SYSTEM_ERROR`.

### APIM/TAS

- Owns Fund Price mode and available-date metadata.
- Owns Fund Price and Fund Info data.
- APIM owns downstream `X-Datadomain`; Java does not set that downstream-only header.

## Target flow

```text
Controller
  -> explicit application command with PortalAccessContext
  -> Fund Price use case
  -> focused outbound port/command
  -> Fund Price APIM adapter
  -> ApimExchangeRequest
  -> ApimExchangeExecutor.postEnvelope(...)
  -> APIM/TAS
```

## APIM classification

Both operations are:

- `ApimDatabaseScope.ACCOUNT_ENV`
- account environment from protected selected-account context
- actor role from protected actor context
- `ApimRetryEligibility.SAFE_BUSINESS_CALL`

The authoritative routing ledger and exact-operation guard must be updated from 18 to 20
operations. TRPGetGeneralMetaData is already ledger entry 18 and is reused, not added.

## Unsupported mode

Java has approved interpretation only for `DAILY` and `MONTHLY`. Blank or other mode values are invalid downstream contracts and fail closed as HTTP 503 `SYSTEM_ERROR`. Java must not invent `WEEKLY` behavior.

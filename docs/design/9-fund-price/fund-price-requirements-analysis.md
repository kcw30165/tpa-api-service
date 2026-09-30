# Fund Price Requirements Analysis

## Scope

The Fund Price page requires three authenticated, selected-account-scoped BFF endpoints:

- Load calendar/mode metadata.
- Retrieve Fund Price data for one selected day or month.
- Export Fund Price history to XLSX.

## Functional requirements

- Mode is downstream-owned.
- `DAILY` instructs FE to render a day selector.
- `MONTHLY` instructs FE to render an `MM/yyyy` selector.
- Available dates and maximum history months come from `TRPGetFundPriceMode`.
- Java stores validated mode metadata in account-scoped authenticated session state.
- Price and export requests use stored metadata for validation and range derivation.
- Java joins Fund Price and Fund Info by `fund-code`. Every `FundPrice` record must
  resolve to exactly one `FundInfo` record; a `FundInfo` record with no price row in
  the returned range is not an error outside the single-date DAILY case.
- Page rows sort by risk level ascending (empty last), then English fund name ascending,
  then unit price ascending (unit price being the downstream bid-price).
- Export rows sort by price date descending, then risk level ascending (empty last),
  then English fund name ascending.
- XLSX filename is `fund_price_info.xlsx`.
- Risk level is in scope as a web response field and as a sort key for both web and
  export. It is not an export column. fund-seq is out of scope entirely.

## Non-functional requirements

- Java 21 and Spring Boot WebFlux.
- Clean Architecture and canonical APIM executor path.
- Configurable strict date formats.
- Encrypted authenticated-session storage.
- No protected identifiers, response bodies, workbook bytes, or session data in logs.
- At least 95.00% aggregate JaCoCo instruction coverage for explicitly enumerated new and changed Fund Price production classes.
- Fund class and employer group filtering is owned by the downstream system; Java implements none of it.
- Fund price decimal precision is sourced from TRPGetGeneralMetaData (fund-pri/precision), cached in Redis per the existing Account Balance pattern.

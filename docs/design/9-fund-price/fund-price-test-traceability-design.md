# Fund Price Test and Coverage Design

## Required test layers

- Web controller and response mapping.
- Application date validation and orchestration.
- Authenticated-session metadata compatibility, binding, update, TTL, and concurrency.
- APIM DTO serialization/deserialization.
- APIM adapter request capture and envelope mapping.
- FundPrice/FundInfo reconciliation.
- XLSX workbook contract.
- Configuration validation.
- Architecture, routing-ledger, public-status isolation, and sensitive-logging guards.

## Mandatory scenarios

- Mode success for DAILY and MONTHLY.
- Blank and unsupported modes.
- Malformed/empty mode envelope.
- Invalid maximum months and available dates.
- DAILY lower/upper boundary, unavailable date, malformed input.
- MONTHLY lower/upper boundary, month with/without available date, leap years and month end.
- APIM zero calls for every FE validation failure.
- Price success with exact request mapping.
- Duplicate, missing, unmatched, null, or blank fund-code data.
- BigDecimal precision derived from cached fund-pri metadata (mock TRPGetGeneralMetaData/Redis per the Account Balance precedent).
- fund-price-type-driven field shaping: BIDOFFER PRICE yields bidUnitPrice+offerUnitPrice; UNIT PRICE yields a single unitPrice sourced from bid-price.
- Web default sorting: risk level ascending with empty ranked last, then English fund
  name ascending, then unit price ascending (unit price being the downstream
  `bid-price` under both `fund-price-type` values).
- Export history derivation; three-key sorting (price date descending, risk level
  ascending with empty ranked last, English fund name ascending); the exact 5-column
  UNIT PRICE and 7-column BIDOFFER PRICE header/source sets in declared order;
  concatenated bilingual headers on non-language columns; the always-present
  Fund Name / 基金名稱 column pair (blank Chinese cell when absent, never backfilled);
  NUMERIC price cells with a separate TEXT currency column; fund-curr duplicated across
  both currency columns under BIDOFFER PRICE; absence of any risk level column;
  filename; media type; and binary logging exclusion.
- Risk level ordering: ascending order, empty risk level ranked last in both web and
  export, mixed empty/non-empty result sets, and an all-empty result set.
- Web riskLevel emission: present on every row when any fund carries one (absent values
  null), key absent from every row when none does, and raw-string passthrough that does
  not zero-pad "1" into "1.0".
- Mode/input-shape disagreement: a MONTHLY-shaped value under DAILY and a DAILY-shaped
  value under MONTHLY both return HTTP 400 with zero APIM invocations.
- MONTHLY range derivation to first and last calendar day, including a leap-February.
- fund-seq is not present anywhere in the web response or the workbook.
- All nonblank downstream errors return 503 `SYSTEM_ERROR`.
- Session metadata old-session readability, account mismatch, stale reference date, persistence error, and concurrent update behavior.

## 95% gate

The final full Maven suite must generate JaCoCo evidence for every explicitly enumerated new or changed Fund Price production class:

- covered/missed instructions and instruction percentage;
- covered/missed branches and branch percentage;
- aggregate counters for the exact Fund Price class set.

Acceptance requires at least 95.00% aggregate instruction coverage. Branch coverage is reviewed per class but is not a separate 95% gate unless the live Maven configuration establishes one.

No exclusions, class relocation, denominator manipulation, weakened/disabled tests, or production-only test seams are permitted. If safe meaningful tests cannot reach the target, implementation stops and escalates with exact missed-instruction evidence.

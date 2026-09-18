# Fund Price Authenticated-Session Contract

## Purpose

The mode endpoint stores validated metadata so subsequent price and export requests can validate and derive ranges without recalling the mode operation.

## Scope

Metadata is selected-account scoped, not global to the login session and not part of `ActorContext`.

Conceptual value:

```json
{
  "accountRefBinding": "server-controlled association",
  "accountEnv": "DB",
  "policyNo": "00000008802",
  "referenceDate": "2026-09-18",
  "mode": "DAILY",
  "fundPriceType": "BIDOFFER PRICE",
  "availableFundPriceDates": ["18/09/2026", "18/05/2026"],
  "maximumFundPriceHistoryMonths": 36
}
```

Physical storage must follow the existing encrypted authenticated-session model. Protected identifiers and dates must not be logged.

## Compatibility

Existing authenticated sessions without Fund Price metadata remain readable. FE initializes metadata by calling `/api/v1/fund-price/mode`.

## Validity

Cached metadata is usable only when it matches the selected account, account environment, policy number, and authoritative reference date. A mismatch or malformed value fails closed as HTTP 503 `SYSTEM_ERROR`.

## Mutation safety

Implementation must investigate and prove the smallest safe update mechanism. It must preserve all actor data, accounts, CSRF state, lifecycle timestamps, TTL, and unrelated attributes. It must not reuse a CSRF-specific compare-and-set port for Fund Price. If whole-record update can lose concurrent changes, introduce a typed expected-value update with focused concurrency tests.

## No hidden refresh

Price and export endpoints do not silently call the mode operation. Missing or stale metadata requires FE to reload the page/mode endpoint or receives the approved system failure according to the final web policy.

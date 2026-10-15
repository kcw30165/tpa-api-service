# Design Pack 13: Fund Comparison

## Status

**Clarification gate: Closed.** The design records only decisions explicitly confirmed from the BRD review and clarification discussion. No implementation is authorized by this pack.

## Scope

Fund Comparison exposes two BFF endpoints:

- `POST /api/v1/fund-comparison/options`
- `POST /api/v1/fund-comparison/prices`

It integrates a new downstream options operation, reuses the existing Fund Price operation, and extends the existing Fund Price request contract with an optional `fund-list` value. The frontend owns percentage-change calculation, rounding, and chart rendering.

## Documents

1. [01-solution-design.md](01-solution-design.md)
2. [02-api-contract.md](02-api-contract.md)
3. [03-downstream-integration.md](03-downstream-integration.md)
4. [04-validation-and-error-handling.md](04-validation-and-error-handling.md)
5. [05-test-and-traceability.md](05-test-and-traceability.md)
6. [06-decisions-and-non-goals.md](06-decisions-and-non-goals.md)

## Authoritative decisions

- `ReferenceDatePort.resolveReferenceDate(accountEnv)` supplies the feature's reference date.
- accountEnv for this call is resolved by the controller via `CurrentPortalAccessContextResolver.current()` and passed into the application layer as a primitive `String`; `PortalAccessContext` itself is never exposed to the application layer, mirroring Fund Price's existing controller-level resolution pattern.
- Fund codes are the identifiers.
- The BFF supports at most five unique, nonblank fund codes per price request.
- A selected fund list is sent downstream as a comma-separated string without spaces.
- `bid-price` is exposed to the frontend as `unitPrice`; `offer-price` is not exposed.
- All public response dates use `dd/MM/yyyy`.
- A legitimate empty downstream `FundPrice` array maps to HTTP 200 and `series: []`.
- Invalid frontend requests use `err.fund-comparison.request.invalid`.
- Retrieval, downstream-contract, and unusable-options failures use `err.fund-comparison.retrieval.unavailable`.
- An options response with no valid time-range token is a retrieval failure, not an empty success.
- Successful responses retain the shared envelope and include top-level `messages: []` and `errors: []`.
- No Fund Comparison caching is introduced.

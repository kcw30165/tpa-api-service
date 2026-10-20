# 6. Decisions and Non-goals

## 6.1 Confirmed decisions

1. The final BRD baseline is accepted for design.
2. TAS/downstream owns fund availability and time-range configuration.
3. Fund identifier is `fund-code`.
4. Options use the new `TRPGetFundCmpOpt` operation.
5. Prices reuse `TRPGetFundPrice` with added `fund-list`.
6. Existing callers use empty `fund-list`.
- Fund Comparison uses a new dedicated command type (`FetchFundComparisonPricesCommand`) and a new, minimal outbound port (`FundComparisonPriceSourcePort`, one method) for its TRPGetFundPrice call, avoiding an Interface Segregation Principle violation that a single combined port would introduce. `FetchFundPriceRangeCommand` and `FetchFundPriceCommand` are not modified or reused. `FundPriceSourcePort` is not modified -- still exactly `fetchPrice` + `fetchPriceRange`. The existing `ApimFundPriceSourceAdapter` IS additively extended (not a new adapter class) to also implement `FundComparisonPriceSourcePort`, since its private `exchangeAndReconcile(...)` helper already contains all request-building, executor-invocation, and error-mapping logic shared across all three operations, differing only in the fund-list value passed through. The shared downstream DTOs and the package-private `FundPriceApimContentValidator` are reused as-is. (Corrected 2026-10-16; see 07-implementation-plan.md change log.)
7. The selected list is comma-separated without spaces.
8. The BFF checks at most five unique funds but does not require two.
9. Superseded 2026-10-20: time range and fund codes are validated against session-cached options (09, D1-D5).
10. `ReferenceDatePort.resolveReferenceDate(accountEnv)` is authoritative.
- accountEnv is resolved by the controller via `CurrentPortalAccessContextResolver.current()` and passed into the application layer as a primitive `String`, the same pattern Fund Price uses; `PortalAccessContext` is not exposed to the application layer.
11. All date windows are inclusive of the reference date.
12. The public price is downstream `bid-price`, named `unitPrice`.
13. Public dates use `dd/MM/yyyy`.
14. The frontend owns percentage calculation and rounding.
15. Empty FundPrice is a success with empty series.
16. Successful responses include `messages` and `errors`.
17. Superseded 2026-10-20: a session-scoped options cache is introduced (09, D1).
18. No valid downstream time-range token is a retrieval failure.
19. Public request validation uses `err.fund-comparison.request.invalid` with HTTP 400.
20. Public retrieval failure uses `err.fund-comparison.retrieval.unavailable` with HTTP 503.

## 6.2 Non-goals

- No chart rendering or axis-spacing logic in Java.
- No percentage-change calculation in Java.
- No rounding of calculated performance in Java.
- No implementation of a new price-history downstream operation.
- Superseded 2026-10-20: options are stored in the encrypted authenticated session (09).
- Superseded 2026-10-20: membership in the current options is validated (09, D5).
- No backend enforcement of the UI's minimum-two comparison rule.
- No exposure of downstream `offer-price`.
- No duplication of established Fund Price localization and sorting logic.

## 6.3 Implementation entry criteria

Implementation may begin only after this design pack is committed and reviewed. The implementation investigation must locate and document the exact existing code anchors for:

- Fund Price command and all callers;
- request DTO/mapper and APIM adapter;
- `ReferenceDatePort` usage;
- Accept-Language fund-name selection and comparator;
- shared success/validation/system-error response factories;
- relevant error-code naming conventions.

If the code differs materially from this design's confirmed behavioral model, stop and raise the discrepancy rather than silently adapting the design.


## 6.4 Change Request decisions (2026-10-20)

Decisions D1-D8, open item Q1, and the session feature-state slot decision are recorded in
09-options-session-cache-change.md section 9.3. D6 is pending downstream confirmation.

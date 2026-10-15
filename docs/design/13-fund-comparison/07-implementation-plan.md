## 7. Implementation Plan

### Status

Entry criteria from §6.3 are closed (scripts 001, 005–011 across `docs/design/13-fund-comparison/`). This plan splits implementation into four milestones, each split into sub-tasks. Each sub-task follows the fail-first gate sequence (new/changed tests first, confirmed failing for the right reason, then implementation, then `spotless:apply`/`spotless:check`, then `-DskipTests compile`/`test-compile`, then the targeted `mvn -Dtest=...`). Each milestone ends with the milestone gates (`spotless:check`, full `mvn test`, coverage review, smoke test, SonarQube).

### 7.1 Confirmed naming

- New command: `FetchFundComparisonPricesCommand`
- New outbound port: `FundComparisonPriceSourcePort`
- New adapter: `ApimFundComparisonPriceSourceAdapter`

These are fully separate from `FetchFundPriceRangeCommand`, `FundPriceSourcePort`, and `ApimFundPriceSourceAdapter`, which remain unmodified (confirmed, §6.1/§6.3). The new adapter reuses the shared downstream DTOs (`TrpGetFundPriceApimDataItem`) and `FundPriceApimContentValidator` for response reconciliation only.

### 7.2 Milestone 1 — Shared building blocks

No downstream calls. Pure/application-layer logic only.

- **1.1 Error codes.** Add `ErrorCodes.FUND_COMPARISON_REQUEST_INVALID` ("err.fund-comparison.request.invalid") and `ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE` ("err.fund-comparison.retrieval.unavailable"). Add `FundComparisonRequestInvalidException` and `FundComparisonRetrievalException`, mirroring `FundPriceRequestInvalidException`/`FundPriceRetrievalException` exactly. Wire both into `ApiExceptionHandler` (400/VALIDATION_FAILED and 503/SYSTEM_ERROR respectively), mirroring the existing Fund Price/Fund Fact Sheet handler entries.
- **1.2 Time-range parser.** Validates `^(?:[1-9][0-9]*[DMY]|YTD)$`; trims and splits on comma; drops invalid tokens with logging only when at least one valid token remains; fails closed (no valid token → retrieval-unavailable, as a thrown exception, not a null/empty default) when none remain; deduplicates retaining first occurrence; preserves normalized downstream order; selects the default time range using fixed unit priority D < M < Y < YTD, smallest integer within a unit, independent of array order.
- **1.3 Date-range calculator.** Given `R = ReferenceDatePort.resolveReferenceDate(accountEnv)` and a validated time-range token: 10D → `R.minusDays(X-1)`; XM → `R.minusMonths(X).plusDays(1)`; XY → `R.minusYears(X).plusDays(1)`; YTD → 1 January of `R`'s year; `toDate = R` always. Public/downstream date formatting is dd/MM/yyyy. Covers month-end/leap-year adjustment via `LocalDate`.

### 7.3 Milestone 2 — Options flow (`POST /api/v1/fund-comparison/options`)

New downstream operation `TRPGetFundCmpOpt`.

- **2.1 Downstream adapter.** New DTOs for the `TRPGetFundCmpOpt` request (`policy-no`) and response (`time-range`, `fund-list[]` with `fund-code`/`fund-name`/`chin-fund-name`). New port `FundComparisonOptionsSourcePort` and adapter `ApimFundComparisonOptionsSourceAdapter`, mirroring `ApimFundPriceModeSourceAdapter`'s template exactly: typed `ApimExchangeRequest`, `ApimDatabaseScope.ACCOUNT_ENV`, caller-supplied `ApimCredentialResolutionContext(actorUserRole, accountEnv)`, `ApimRetryEligibility.SAFE_BUSINESS_CALL`. Any nonblank `err-message`, missing/malformed envelope, or structurally unusable `data` maps to the single generic retrieval-unavailable failure.
- **2.2 Options mapper.** Selects English/Chinese display name using the existing Fund Price `displayFundName` behavior; sorts using the existing `FundPriceSortingSupport` comparator; normalizes `time-range` using M1.2; outputs only `fundCode`/`fundName` per fund; a missing/blank/wholly-invalid `time-range` is a retrieval failure, not an empty success (never returns `timeRanges: []` with a null default).
- **2.3 Options use case.** Orchestrates the adapter (2.1) and mapper (2.2); no persistence, no caching (confirmed non-goal).
- **2.4 Options controller + wiring.** `@RestController @RequestMapping("/api/v1")`, `@PostMapping("/fund-comparison/options")`, no request body. Controller resolves `PortalAccessContext` via `CurrentPortalAccessContextResolver.current()` and passes `accountEnv`/`actorUserRole` as primitives to the use case — `PortalAccessContext` is never exposed to the application layer (confirmed, script 011). New `@Configuration` class mirrors `FundPriceModeApplicationConfig`'s pattern (plain `@Bean` methods, use case carries no Spring annotation).

### 7.4 Milestone 3 — Prices flow (`POST /api/v1/fund-comparison/prices`)

Reuses the downstream `TRPGetFundPrice` *operation* only; the existing Fund Price command/port/adapter are untouched (confirmed, scripts 006–011).

- **3.1 New command/port/adapter.** `FetchFundComparisonPricesCommand(policyNo, fromDate, toDate, fundList, accountEnv, actorUserRole)`. New port `FundComparisonPriceSourcePort`. New adapter `ApimFundComparisonPriceSourceAdapter` builds its own `TrpGetFundPriceApimRequest` (reusing the existing request DTO shape, since the wire contract is shared) with the real comma-joined `fund-list`; reuses `TrpGetFundPriceApimDataItem` and `FundPriceApimContentValidator` for response reconciliation only. `ApimFundPriceSourceAdapter.exchangeAndReconcile` is not modified and keeps sending `fund-list=""`.
- **3.2 Validation + response mapping.** Request validation: 1–5 unique nonblank fund codes (reject >5, reject missing/blank, dedupe preserving first-occurrence order); time-range syntax validated via M1.2 (membership against a prior options response is explicitly not checked, confirmed non-goal). Response mapping: `bid-price` → `unitPrice`; `offer-price` never exposed; group rows by `fund-code` into series, ordered by request's first-occurrence order after validation/dedup; price points within a series ordered by date ascending; a structurally valid empty `FundPrice` is success with `series: []`.
- **3.3 Prices use case.** Takes `accountEnv`/`policyNo` as primitives (passed in by the controller, not `PortalAccessContext`); computes `fromDate`/`toDate` via M1.3; joins validated fund codes with commas, no spaces; calls the new port (3.1).
- **3.4 Prices controller + wiring.** `@PostMapping("/fund-comparison/prices")` with `{ fundCodes[], timeRange }` body. Same controller-resolves-context pattern as 2.4. New `@Configuration` class mirrors `FundPriceQueryExportUseCaseConfig`'s pattern.

### 7.5 Milestone 4 — Stabilization & traceability

- **4.1 Regression proof.** Diff-based proof (not just a claim) that `FetchFundPriceRangeCommand`, `FundPriceSourcePort`, `ApimFundPriceSourceAdapter`, and every existing Fund Price public response remain byte-for-byte unchanged on this branch.
- **4.2 BRD traceability pass.** Exact-error-contract tests from the test-and-traceability doc §5.6 (invalid/missing time range, >5 fund codes, missing/blank downstream time range, mixed valid/invalid downstream tokens, all-invalid tokens, raw downstream message never exposed, legitimate empty-series success).
- **4.3 Architecture/boundary guard test — OPEN, not yet decided.** Fund Fact Sheet has a guard pair (`FundFactSheetApplicationBoundaryGuardTest`, `FundFactSheetWebBoundaryGuardTest`); Fund Price itself has none. Flagged as an explicit unknown per the zero-assumption rule — needs a decision before milestone 4 is implemented: add a comparable guard pair for Fund Comparison, or skip it to match Fund Price's own precedent.
- Milestone gates: `spotless:check`, full `mvn test`, JaCoCo coverage review (95% instruction; DTOs/validators/pure functions 95% branch; adapters/use cases/controllers 85–90% branch with one-line justification per uncovered branch), startup smoke test, SonarQube quality gate.

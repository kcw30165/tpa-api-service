## 7. Implementation Plan

### Status

Entry criteria from §6.3 are closed (scripts 001, 005–011 across `docs/design/13-fund-comparison/`). This plan splits implementation into four milestones, each split into sub-tasks. Each sub-task follows the fail-first gate sequence (new/changed tests first, confirmed failing for the right reason, then implementation, then `spotless:apply`/`spotless:check`, then `-DskipTests compile`/`test-compile`, then the targeted `mvn -Dtest=...`). Each milestone ends with the milestone gates (`spotless:check`, full `mvn test`, coverage review, smoke test, SonarQube).

### 7.1 Confirmed naming (corrected; see Change log)

- New command: `FetchFundComparisonPricesCommand`
- New outbound port: `FundComparisonPriceSourcePort` (a single `fetchPrices(FetchFundComparisonPricesCommand)` method)
- No new adapter class.

`FetchFundComparisonPricesCommand` is fully separate from `FetchFundPriceRangeCommand` and `FetchFundPriceCommand`, which remain unmodified. `FundPriceSourcePort` also remains unmodified -- still exactly `fetchPrice` + `fetchPriceRange` -- so Fund Price's use case is unaffected (Interface Segregation Principle). The existing `ApimFundPriceSourceAdapter` is additively extended to implement *both* `FundPriceSourcePort` and the new `FundComparisonPriceSourcePort`: its private `exchangeAndReconcile(...)` helper gains a `fundList` parameter (previously hardcoded to `""`), and a new `fetchPrices(...)` method passes the real, comma-joined fund-code list through the same shared path the two existing methods already use. The shared downstream DTOs (`TrpGetFundPriceApimDataItem`) and the package-private `FundPriceApimContentValidator` are reused as-is, with no visibility change, since the new logic lives in the same class and package.

### 7.2 Milestone 1 — Shared building blocks

No downstream calls. Pure/application-layer logic only.

- **1.1 Error codes.** Add `ErrorCodes.FUND_COMPARISON_REQUEST_INVALID` ("err.fund-comparison.request.invalid") and `ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE` ("err.fund-comparison.retrieval.unavailable"). Add `FundComparisonRequestInvalidException` and `FundComparisonRetrievalException`, mirroring `FundPriceRequestInvalidException`/`FundPriceRetrievalException` exactly. Wire both into `ApiExceptionHandler` (400/VALIDATION_FAILED and 503/SYSTEM_ERROR respectively), mirroring the existing Fund Price/Fund Fact Sheet handler entries.
- **1.2 Time-range parser.** Validates `^(?:[1-9][0-9]*[DMY]|YTD)$`; trims and splits on comma; drops invalid tokens with logging only when at least one valid token remains; fails closed (no valid token → retrieval-unavailable, as a thrown exception, not a null/empty default) when none remain; deduplicates retaining first occurrence; preserves normalized downstream order; selects the default time range using fixed unit priority D < M < Y < YTD, smallest integer within a unit, independent of array order.
- **1.3 Date-range calculator.** Given `R = ReferenceDatePort.resolveReferenceDate(accountEnv)` and a validated time-range token: 10D → `R.minusDays(X-1)`; XM → `R.minusMonths(X).plusDays(1)`; XY → `R.minusYears(X).plusDays(1)`; YTD → 1 January of `R`'s year; `toDate = R` always. Public/downstream date formatting is dd/MM/yyyy. Covers month-end/leap-year adjustment via `LocalDate`.

### 7.3 Milestone 2 — Options flow (`POST /api/v1/fund-comparison/options`)

New downstream operation `TRPGetFundCmpOpt`.

- **2.1 Downstream adapter.** New DTOs for the `TRPGetFundCmpOpt` request (`policy-no`) and response (`time-range`, `fund-list[]` with `fund-code`/`fund-name`/`chin-fund-name`). New port `FundComparisonOptionsSourcePort` and adapter `ApimFundComparisonOptionsSourceAdapter`, mirroring `ApimFundPriceModeSourceAdapter`'s template exactly: typed `ApimExchangeRequest`, `ApimDatabaseScope.ACCOUNT_ENV`, caller-supplied `ApimCredentialResolutionContext(actorUserRole, accountEnv)`, `ApimRetryEligibility.SAFE_BUSINESS_CALL`. Any nonblank `err-message`, missing/malformed envelope, or structurally unusable `data` maps to the single generic retrieval-unavailable failure.
- **2.2 Options mapper.** Selects English/Chinese display name using the existing Fund Price `displayFundName` behavior; sorts using the existing `FundPriceSortingSupport` comparator; normalizes `time-range` using M1.2; outputs only `fundCode`/`fundName` per fund; a missing/blank/wholly-invalid `time-range` is a retrieval failure, not an empty success (never returns `timeRanges: []` with a null default).
- **2.3 Options use case.** Orchestrates the adapter (2.1) and mapper (2.2); no persistence, no caching (confirmed non-goal at the time; superseded by Milestone 5, section 7.6).
- **2.4 Options controller + wiring.** `@RestController @RequestMapping("/api/v1")`, `@PostMapping("/fund-comparison/options")`, no request body. Controller resolves `PortalAccessContext` via `CurrentPortalAccessContextResolver.current()` and passes `accountEnv`/`actorUserRole` as primitives to the use case — `PortalAccessContext` is never exposed to the application layer (confirmed, script 011). New `@Configuration` class mirrors `FundPriceModeApplicationConfig`'s pattern (plain `@Bean` methods, use case carries no Spring annotation).

### 7.4 Milestone 3 — Prices flow (`POST /api/v1/fund-comparison/prices`)

Reuses the downstream `TRPGetFundPrice` *operation* only; the existing Fund Price command/port/adapter are untouched (confirmed, scripts 006–011).

- **3.1 Shared adapter extension (corrected design; see change log below).** No new adapter class. `FetchFundComparisonPricesCommand(policyNo, fromDate, toDate, fundList, accountEnv, actorUserRole)` is unchanged from the original plan. A new, minimal port `FundComparisonPriceSourcePort` (a single `fetchPrices(FetchFundComparisonPricesCommand)` method) is added so Fund Comparison's use case depends only on the one method it actually calls -- `FundPriceSourcePort` remains untouched, still exactly `fetchPrice` + `fetchPriceRange`, so Fund Price's use case is unaffected (Interface Segregation Principle: neither use case depends on a method it never calls). `ApimFundPriceSourceAdapter` (the existing, already-tested class) is additively extended to implement *both* `FundPriceSourcePort` and `FundComparisonPriceSourcePort`. Its private `exchangeAndReconcile(...)` helper -- which already contains 100% of the request-building, executor-invocation, and error-mapping logic shared by `fetchPrice` and `fetchPriceRange` -- gains a `fundList` parameter (previously hardcoded to `""` inline); the two existing methods now pass `""` explicitly at their own call sites (behavior byte-for-byte unchanged), and a new `fetchPrices(...)` method passes the real, comma-joined fund-code string from the new command through the same shared path. `FundPriceApimContentValidator.reconcile(...)` (confirmed package-private) is reused as-is, with zero visibility change, since the new logic lives in the same adapter class and the same package.

**3.2 Validation + response mapping.** Request validation: 1–5 unique nonblank fund codes (reject >5, reject missing/blank, dedupe preserving first-occurrence order); time-range syntax validated via M1.2 (membership was not checked at the time; superseded by Milestone 5, section 7.6). Response mapping: `bid-price` → `unitPrice`; `offer-price` never exposed; group rows by `fund-code` into series, ordered by request's first-occurrence order after validation/dedup; price points within a series ordered by date ascending; a structurally valid empty `FundPrice` is success with `series: []`.
- **3.3 Prices use case.** Takes `accountEnv`/`policyNo` as primitives (passed in by the controller, not `PortalAccessContext`); computes `fromDate`/`toDate` via M1.3; joins validated fund codes with commas, no spaces; calls the new port (3.1).
- **3.4 Prices controller + wiring.** `@PostMapping("/fund-comparison/prices")` with `{ fundCodes[], timeRange }` body. Same controller-resolves-context pattern as 2.4. New `@Configuration` class mirrors `FundPriceQueryExportUseCaseConfig`'s pattern.

### 7.5 Milestone 4 — Stabilization & traceability

- **4.1 Regression proof.** Diff-based proof (not just a claim) that `FetchFundPriceRangeCommand`, `FundPriceSourcePort`, `ApimFundPriceSourceAdapter`, and every existing Fund Price public response remain byte-for-byte unchanged on this branch.
- **4.2 BRD traceability pass.** Exact-error-contract tests from the test-and-traceability doc §5.6 (invalid/missing time range, >5 fund codes, missing/blank downstream time range, mixed valid/invalid downstream tokens, all-invalid tokens, raw downstream message never exposed, legitimate empty-series success).
- **4.3 Architecture/boundary guard test -- RESOLVED (Nick, 2026-10-17): skip.** Investigation confirmed `FundFactSheetApplicationBoundaryGuardTest`/`FundFactSheetWebBoundaryGuardTest` are bespoke, reflection/regex-pinned characterization tests tied to Fund Fact Sheet's specific migration history (e.g. asserting the controller is not in an old flat folder), not a generic reusable pattern -- Fund Comparison has no equivalent migration history requiring the same protection. The Clean Architecture property that actually matters (application-layer independence from inbound web/adapters) is already enforced, generically and repo-wide, by the existing `CleanArchitectureDependencyTest` and `Task0043AuthenticationFoundationArchitectureGuardTest`, which are confirmed to be exactly what caught Milestone 2's real Clean Architecture violation. No dedicated Fund Comparison guard pair is added, matching Fund Price's own confirmed precedent.
- Milestone gates: `spotless:check`, full `mvn test`, JaCoCo coverage review (95% instruction; DTOs/validators/pure functions 95% branch; adapters/use cases/controllers 85–90% branch with one-line justification per uncovered branch), startup smoke test, SonarQube quality gate.


### 7.6 Milestone 5 -- Session-cached options validation (Change Request)

Sub-tasks 5.1-5.6, gates, and Phase 3 E2E scenarios are defined in
09-options-session-cache-change.md sections 9.9 and 9.11. 5.5 (D6) is a separate commit and is
not merged until downstream confirmation.

### Change log

- **2026-10-20 -- End-to-end finding and Change Request.** `timeRange` `10Y` passed syntax
  validation, TRPGetFundPrice returned `err.tpa.invalid.date`, and the BFF reported HTTP 503
  for a client error. Unoffered tokens and unlisted fund codes were not enforced. Superseded:
  README "No Fund Comparison caching is introduced"; 01 section 1.3 prices step 3 and section
  1.5; 04 section 4.3 last paragraph; 05 section 5.1 "validates time-range syntax only";
  06 decisions 9 and 17 and non-goals "No Redis storage of options" and "No validation that a
  syntactically valid period appeared in an earlier options response"; 07 sub-tasks 2.3 and 3.2
  non-goal notes. 04 section 4.4 gains the pending D6 exception. Replacement design:
  09-options-session-cache-change.md.

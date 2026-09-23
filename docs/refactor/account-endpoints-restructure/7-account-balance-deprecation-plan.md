# S7 - Account Balance Endpoint Deprecation Plan

Ratified 21/09/2026. Sequenced **after S3 and S4**, once
`/api/v1/account/summary` is implemented, wired, and proven — not before.

## Confirmed decisions

1. **`/api/v1/account/gain-loss` stays.** Summary duplicates its data for the
   Portfolio Overview UI, but the standalone endpoint is not withdrawn as part
   of this feature. Two live endpoints can return overlapping gain/loss data;
   that is accepted.

2. **`TRPGetMemberAccBal` and its surrounding code are kept, not renamed.**
   The operation name is downstream-owned and accurately describes what it
   calls. No sweep of `accountbalance` package names, class names,
   `apim.account-balance.*` config keys, or `account-balance.*` observability
   events is performed. Only the public HTTP surface changes.

3. **Timing:** S7 is the last stage of this feature, executed once summary is
   implemented (S3), wired (S4), and its own test suite is green. Withdrawing
   the old endpoint before summary is proven would leave no path back.

4. **The e2e pack is renamed.** `scripts/e2e/scenarios/account-balance/`
   becomes `scripts/e2e/scenarios/account-summary/`, and its request path
   moves from `/api/v1/account/balance` to `/api/v1/account/summary`. This is
   the one renaming in scope, because the pack tests a live HTTP contract that
   this plan removes.

## What is removed

| Item | Reason |
| --- | --- |
| `AccountBalanceController` and its `@GetMapping("/account/balance")` route | The public endpoint is withdrawn |
| `AccountBalanceResultResponse` | Result-envelope wrapper unique to the withdrawn endpoint; `AccountSummaryResultResponse` (S2) is its replacement |
| `AccountBalanceControllerTest` | Tests a controller that no longer exists |
| `scripts/e2e/scenarios/account-balance/list-authenticated.sh` | Superseded by the renamed pack |

`AccountBalanceDataResponse` is also removed once nothing constructs it -
`AccountSummaryDataResponse` (S2) already supersedes its role.

## What is moved, not removed

`CurrencySelectionResponse`, `AccountOverviewResponse` (with
`AccountOverviewDisplayAccountResponse`, `AccountOverviewDisplayOptionsResponse`,
`AccountOverviewTotalsResponse`), and `InvestmentPortfolioResponse` (with
`FundLevelDisplayAccountResponse`, `FundLevelFundResponse`,
`FundLevelSubDisplayAccountResponse`, `SubDisplayLevelDisplayAccountResponse`,
`SubDisplayLevelFundResponse`, `SubDisplayLevelSubDisplayAccountResponse`) move
from `adapter/in/web/response/accountbalance/` to
`adapter/in/web/response/accountsummary/`.

These are pure data shapes with no controller dependency. `AccountSummaryDataResponse`
(S2) already imports them from the balance package; S7 only changes which
package they live in, and the import updates accordingly. No field, name,
nullability, or Jackson annotation changes.

## What is kept exactly as-is

`GetAccountBalanceService`, `AccountBalanceSourcePort`,
`ApimAccountBalanceSourceAdapter`, every `TrpGetMemberAccBal*` DTO,
`DefaultAccountBalanceAssembler`, `DefaultAccountBalanceNormalizer`,
`DefaultFundLevelPortfolioBuilder`, `DefaultSubDisplayLevelPortfolioBuilder`,
`DefaultAccountOverviewAggregator`, and
`DefaultAccountBalancePresentationTransformer` (narrowed in S0-A). The
`accountbalance` package name, `apim.account-balance.*` configuration keys,
and every `account-balance.*` observability event are unaffected. Summary's
assembler (S3) is a new caller of this existing machinery, not a
replacement for it.

## Sequencing

S7 cannot start until:

- S3 (assembler) implements the composition and R-5/R-2/R-4 aggregation rules
  against `AccountSummaryDataResponse` (S2).
- S4 (controller) exposes `/api/v1/account/summary` and its own contract
  tests pass.
- The full suite is green with summary live.

Only then does S7 remove the controller, move the shared response records, and
rename the e2e pack. This ordering means the repository is never in a state
where the old endpoint is gone before the new one is proven.

## Explicitly out of scope for S7

- Renaming `TRPGetMemberAccBal`, its adapter, its DTOs, or its config keys.
- Withdrawing `/api/v1/account/gain-loss`.
- Any change to `GetAccountBalanceService` or its collaborators beyond adding
  summary as a second caller.

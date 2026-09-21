# Account Summary Composition Contract

Ratified 21/09/2026. Supersedes the 21/09/2026 draft in which the BFF computed
`% of Portfolio` and ordered holdings by `fund-seq`.

## Upstream calls

One summary request performs, sequentially:

1. `ReferenceDatePort.resolveReferenceDate(accountEnv)` — once, shared.
2. `GeneralMetadataResolver.resolve(...)` — once, for the Account Balance
   precision key set (FUND_PRICE, NET_ASSET_VALUE, UNIT_BALANCE, FX_RATE) plus
   the new AB_PERCENTAGE key.
3. `AccountBalanceSourcePort.fetchRawAccountBalance(...)` — `TRPGetMemberAccBal`.
4. `AccountGainLossSourcePort.fetch(...)` — `TRPGetInvestGL`.

Both source calls receive the same reference date, the same resolved metadata,
and the same `curr` value. Neither existing APIM adapter path nor the routing
ledger cardinality of 18 changes; summary introduces no new APIM operation.

## Metadata addition

`GeneralMetadataName` gains `AB_PERCENTAGE("ab-percentage")`. It is requested
only by the summary use case. The Account Balance and Account Gain/Loss key
sets are unchanged, so neither existing feature's metadata behaviour is
altered, and `/api/v1/account/balance` does not begin returning a percentage.

Note: `DefaultAccountBalancePresentationTransformer.precisions(...)` iterates
`GeneralMetadataName.values()` and calls `orElseThrow()` on each. Adding an
enum constant would make Account Balance demand `ab-percentage` and fail when
it is absent. That method must be narrowed to the keys the caller actually
requested before the enum is extended. This is sequenced as S0-A and is a
required repair, not an optional cleanup.

## Holdings source

`holdings[]` is driven by the `TRPGetInvestGL` fund list. Per Q-5 that list is
a superset of the balance fund list, because a sold fund retains gain/loss
with no current holding.

Join key is the trimmed fund code:

```
TRPGetInvestGL      InvestGL.FundCode   (already trimmed by the adapter)
TRPGetMemberAccBal  fundDtl.fund-code   (not trimmed today)
```

The composer trims both sides rather than relying on either adapter.

A balance holding whose fund code is absent from `TRPGetInvestGL` violates the
Q-5 superset invariant and maps to SYSTEM_FAILURE. A gain/loss fund with no
holding is normal; its `balance` follows R-2.

## Per-fund values

| Field | Source |
| --- | --- |
| `fundCode` | `InvestGL.FundCode`, trimmed |
| `fundName` | Balance `fundDtl` name when the fund is held, else the `InvestGL` name; both via `DynamicDescriptionResolver` with symmetric non-blank fallback |
| `fundSequence` | `InvestGL` `seq` |
| `balance` | Sum of `nav` across every matching holding, all display and sub-display sources; R-2 governs the unheld case |
| `portfolioPercentage` | Balance `fundDtl.percentage`, rendered at `ab-percentage` precision; governed by R-4 when the fund is unheld |
| `gainLoss` | `InvestGL.GL`, rendered at NAV precision |

Sentinel `-1` and null participating NAV values fail through
`AccountBalanceRetrievalException.systemFailure()`, consistent with
`DefaultAccountOverviewAggregator`.

## Totals

`totalValue` equals `accountOverview.accountTotals.totalBalance` — the sum of
all participating `nav` values, recomputed from the normalized holdings rather
than parsed back from the rendered string.

`totalGainLoss` is the exact `BigDecimal` sum of every `InvestGL.GL`, rendered
once at NAV precision. This matches `AccountGainLossAssembler`, so
`/api/v1/account/gain-loss` and `/api/v1/account/summary` return an identical
total for the same account, period, currency, and reference date.

## Ordering

`holdings[]` is ordered by `InvestGL` `seq` ascending, then trimmed `fundCode`
ascending.

## Rendering

All monetary values and the percentage use the existing
`ExactPrecisionRenderer` with `RoundingMode.UNNECESSARY`. Java sums before
rendering and performs no division and no rounding.

## accountNumber derivation

`accountNumber` requires no upstream call. It is composed in the application
layer from the already-resolved `PortalAccessContext`:

```
AccountContext.policyNo + "-" + AccountContext.certNo
```

`GetAccountSummaryCommand` already carries `policyNo` and `certNo`, because
both are needed for the two APIM requests, so no new field crosses the
application boundary and `PortalAccessContext` still does not reach any
adapter.

Composition happens in the summary assembler, not in the controller, keeping
the controller a thin mapping layer consistent with `AccountBalanceController`.

Neither value is trimmed, padded, or reformatted beyond the R-3 decision, and
neither is re-derived from an APIM response. A null or blank `policyNo` or
`certNo` is a technical contract violation and maps to SYSTEM_FAILURE rather
than producing a malformed value such as `-230` or `00000008802-`.

# Account Summary Composition Contract

## Upstream calls

One summary request performs:

1. `ReferenceDatePort.resolveReferenceDate(accountEnv)` — once, shared.
2. `GeneralMetadataResolver.resolve(...)` — once, with the union of the
   Account Balance precision key set (FUND_PRICE, NET_ASSET_VALUE,
   UNIT_BALANCE, FX_RATE) and the Gain/Loss key set (NET_ASSET_VALUE). The
   union equals the Account Balance set, so no additional metadata key is
   introduced.
3. `AccountBalanceSourcePort.fetchRawAccountBalance(...)` — `TRPGetMemberAccBal`.
4. `AccountGainLossSourcePort.fetch(...)` — `TRPGetInvestGL`.

Steps 3 and 4 share the same resolved reference date and the same resolved
metadata set. Neither source port, adapter, DTO, nor APIM path is modified;
`summary` is a new composing use case over the two existing ports.

## Join

The join key is the trimmed fund code.

```
RawAccountBalanceFundDetail.fundCode   (TRPGetMemberAccBal fundDtl.fund-code)
RawFundGainLoss.fundCode               (TRPGetInvestGL InvestGL.FundCode)
```

`ApimAccountGainLossSourceAdapter` already trims `FundCode`. The balance side
is not trimmed today, so the composer must trim both sides before comparison
rather than relying on either adapter. This mirrors the shared
`IdentifierNormalizer` precedent introduced during the idempotency refactor.

## Per-fund balance

`holdings[].balance` is the sum of `nav` across every holding whose
`fundCode` matches, across all display sources and sub-display sources. This
is the same figure the Fund Level View already aggregates per
`(displaySource, fundCode)`, summed one level higher to the fund.

Sentinel `-1` and null participating NAV values fail through
`AccountBalanceRetrievalException.systemFailure()`, consistent with
`DefaultAccountOverviewAggregator`.

## totalValue

`totalValue` equals `accountOverview.accountTotals.totalBalance` — the sum of
all participating `nav` values. It is recomputed from the same normalized
holdings rather than parsed back from the rendered string.

## totalGainLoss

`totalGainLoss` is the exact `BigDecimal` sum of every `RawFundGainLoss.gainLoss`
returned by `TRPGetInvestGL`, rendered once at NAV precision. This is the same
rule `AccountGainLossAssembler` already applies, so
`/api/v1/account/gain-loss` and `/api/v1/account/summary` return an identical
total for the same account, period, and reference date.

The total is summed over the full `TRPGetInvestGL` fund list, independent of
join outcome, so an unjoined fund cannot silently change the total.

## portfolioPercentage

```
portfolioPercentage = (holding balance / totalValue) * 100
```

Computed on exact `BigDecimal` values before rendering. When `totalValue` is
zero, every `portfolioPercentage` is `"0.0"` and no division is attempted.

Scale is open question Q-3. The UI shows one decimal place (35.0%, 25.0%,
22.0%, 12.0%, 6.0%). Percentage is a presentation-derived ratio, not a TAS
monetary value, so it is not covered by an existing precision metadata key and
requires an explicit decision rather than reuse of NAV precision.

## Ordering

`holdings[]` is ordered by `fundSequence` ascending, then trimmed `fundCode`
ascending — identical to `AccountGainLossAssembler`. The UI's descending
balance order in the screenshot is treated as an FE sort affordance (the
Balance and Gain/Loss column headers both carry sort icons), not a BFF
guarantee. See Q-4.

## Fund set reconciliation

Three cases must be handled explicitly:

| Case | Behaviour | Rationale |
| --- | --- | --- |
| Fund in both sources | Full holding entry | Normal |
| Fund in balance only, absent from `InvestGL` | Open question Q-5 | A zero-gain fund and a missing fund are not the same thing |
| Fund in `InvestGL` only, no matching holding | Open question Q-5 | Would mean a gain/loss with no balance |

`AccountGainLossApimContentValidator` already accepts an empty `InvestGL`
array as valid, so the empty-gain/loss case is a real runtime state and must
not be treated as a contract violation.

## Localization

`holdings[].fundName` uses the shared `DynamicDescriptionResolver` with
symmetric non-blank fallback. When both sources supply a fund name, the
Account Balance `fundDtl` name is authoritative, because that is the name
already rendered in `investmentPortfolio` within the same payload and the two
sections must not disagree.

## Rendering

`balance`, `totalValue`, `totalGainLoss`, and `gainLoss` use the existing
`ExactPrecisionRenderer` at NAV precision with `RoundingMode.UNNECESSARY`.
Java sums before rendering and never rounds, preserving the Account Balance and
Account Gain/Loss rules unchanged.

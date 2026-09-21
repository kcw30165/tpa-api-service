# Decisions and Open Questions

## Confirmed by instruction

- `/api/v1/account-balance` becomes `/api/v1/account/balance`.
- `/api/v1/account-gain-loss` becomes `/api/v1/account/gain-loss`.
- `/api/v1/account/summary` is GET, uses the same headers as
  `/api/v1/account/balance`, returns the same response as
  `/api/v1/account/balance`, and adds a gain/loss breakdown section.
- The breakdown represents the supplied Portfolio Overview / Holdings UI.
- The breakdown is composed from `TRPGetMemberAccBal` and `TRPGetInvestGL`.

## Decided in this pack, reversible on review

- Class-level `@RequestMapping("/api/v1")` retained; only method paths change.
- Configuration keys, property prefixes, package names, and observability event
  names are not renamed.
- No dual-routing alias for the old paths.
- `gainLossBreakdown` is added as a sibling of `investmentPortfolio`; no
  existing Account Balance field changes.
- Summary fails whole rather than degrading on partial upstream failure.
- Fund name is taken from the Account Balance side when both sources supply one.

## Open — required before implementation

**Q-1 Transition window.** Is a temporary alias on the old paths required while
the front end is released, or is the cutover atomic?

**Q-2 Period parameter.** Does `/api/v1/account/summary` accept
`?period=SINCE_SCHEME_INCEPTION|YEAR_TO_DATE`? The UI shows a working
Since Scheme Inception / Year to Date toggle, which implies either a parameter
on summary or an FE follow-up call to `/api/v1/account/gain-loss`.

**Q-3 Percentage scale and rounding.** The UI shows one decimal place. Exact
`BigDecimal` division rarely terminates, so `RoundingMode.UNNECESSARY` cannot
be used for percentage. Which scale, and which rounding mode? This is the only
place in the account feature set where Java would round, so it needs explicit
sign-off. Also: should the percentages be forced to sum to 100?

**Q-4 Holdings ordering.** Fund sequence (consistent with
`/api/v1/account/gain-loss`) or descending balance (matching the screenshot)?

**Q-5 Fund set reconciliation.** For a fund present in balance but absent from
`InvestGL`: emit with null `gainLoss`, emit with `"0.00"`, or omit? And for a
fund present in `InvestGL` with no matching holding: omit, or treat as a
technical failure?

**Q-6 Sequential vs concurrent upstream calls.** Sequential gives deterministic
error precedence; concurrent halves latency. Both are non-blocking.

**Q-7 Currency and gain/loss.** `TRPGetMemberAccBal` accepts `curr`, but
`TRPGetInvestGL` has no currency parameter. When the member selects USD, is the
gain/loss section still HKD, is it suppressed, or is the whole currency
selection unavailable on summary?

**Q-8 E2E scenario pack.** Does the correlated release require a new
`account-summary` scenario pack under `scripts/e2e/scenarios/`, or is renaming
the existing `account-balance` pack paths sufficient for this change?

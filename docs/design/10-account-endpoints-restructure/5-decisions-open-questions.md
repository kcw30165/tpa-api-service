# Decisions and Open Questions

## Confirmed by instruction

- `/api/v1/account-balance` becomes `/api/v1/account/balance`.
- `/api/v1/account-gain-loss` becomes `/api/v1/account/gain-loss`.
- `/api/v1/account/summary` is GET, uses the same headers as
  `/api/v1/account/balance`, returns the same response as
  `/api/v1/account/balance`, and adds a gain/loss breakdown section.
- The breakdown represents the supplied Portfolio Overview / Holdings UI.
- The breakdown is composed from `TRPGetMemberAccBal` and `TRPGetInvestGL`.

## Ratified 21/09/2026

**Q-1 Transition window — RESOLVED.** Atomic cutover, no alias. Implemented in
S1 at commit d160cf01.

**Q-2 Period parameter — RESOLVED.** `/api/v1/account/summary` accepts
`?period=SINCE_SCHEME_INCEPTION|YEAR_TO_DATE`, defaulting to
`SINCE_SCHEME_INCEPTION`. The controller reuses the existing
`AccountGainLossPeriod` enum and the same `@RequestParam` shape as
`AccountGainLossController`, so the UI's Since Scheme Inception / Year to Date
toggle is served by one summary call with no FE follow-up request.

**Q-3 Percentage scale and rounding — RESOLVED, ownership moved downstream.**
Java does not compute `% of Portfolio`. TAS supplies it:

- `TRPGetInvestGL` gains a per-fund `percentage` field.
- `TRPGetGeneralMetaData` gains metadata-name `gl-percentage`, whose
  `precision` drives rendering.

The BFF adds `GL_PERCENTAGE("gl-percentage")` to `GeneralMetadataName` and
renders `percentage` through the existing `ExactPrecisionRenderer` with
`RoundingMode.UNNECESSARY`, exactly as every other monetary field. No division
is performed in Java and no rounding is introduced anywhere in the account
feature set. `totalValue` is still aggregated from balance NAV; only the
per-fund percentage is downstream-owned.

**Q-4 Holdings ordering — RESOLVED.** `TRPGetInvestGL` supplies `seq`.
`holdings[]` is ordered by that field ascending, with trimmed `fundCode` as the
tie-breaker. This is the downstream display order and replaces the earlier
proposal to reuse `fund-seq` or to sort by descending balance.

**Q-5 Fund set reconciliation — RESOLVED.** The `TRPGetInvestGL` fund set is a
superset of the `TRPGetMemberAccBal` fund set. A fund may appear in gain/loss
with no current holding because it was sold; the reverse cannot occur.
Therefore:

- `holdings[]` is driven by the `TRPGetInvestGL` fund list, not the balance
  fund list.
- A balance holding with no matching gain/loss fund is a technical contract
  violation and maps to SYSTEM_FAILURE.
- A gain/loss fund with no matching holding is normal and must be emitted.
  See R-2 for the balance value in that case.

**Q-6 Sequential vs concurrent upstream calls — DECIDED, low risk.**
Sequential. Balance first, then gain/loss. This keeps error precedence
deterministic and matches the existing Account Balance orchestration shape.
Revisit only if measured latency justifies it.

**Q-7 Currency — RESOLVED.** `TRPGetInvestGL` gains a `curr` field with the
same semantics as `TRPGetMemberAccBal`: always serialized, empty string on
initial load, selected value on currency change. The summary endpoint passes
one `currency` value to both upstream calls, so the whole payload is
internally consistent in a single currency. Currency selection remains fully
available on summary.

**Q-8 E2E scenario pack — DECIDED, low risk.** Extend the existing
`scripts/e2e/scenarios/account-balance/` pack with a summary scenario rather
than creating a new pack. The bootstrap, login journey, and assertion helpers
are identical.

## Open — blocking implementation

**R-1 Downstream availability.** `TRPGetInvestGL.percentage`,
`TRPGetInvestGL.curr`, and `TRPGetGeneralMetaData` metadata-name
`gl-percentage` are additions, not existing fields. Required before S2:

- the exact JSON property names and casing, noting that `TRPGetInvestGL`
  already mixes conventions (`FundCode`, `GL` versus `fund-name`, `fund-seq`);
- whether `percentage` arrives as a ratio (0.25) or a display percentage
  (25.0);
- the exact `seq` property name;
- whether these fields are already deployed in the SIT APIM environment, or
  whether the BFF must tolerate their absence during a transition.

The BFF cannot be implemented against an unspecified wire contract.

**R-2 Balance value for a sold fund.** For a fund present in `TRPGetInvestGL`
with no current holding, is `holdings[].balance` `"0.00"`, or null/omitted?
Zero asserts a real position of zero; null asserts no position. The UI
screenshot shows no such row, so this is not resolvable from the design.

**R-3 Account number format.** `/api/v1/account/summary` gains
`result.data.accountNumber`, composed as
`AccountContext.policyNo + "-" + AccountContext.certNo`. Two points are
unresolved:

- *Padding.* `auth-workflows.login.account-identifiers` already configures
  `policy-no.width: 11`, `cert-no.width: 4`, and `pad-character: "0"`. Is the
  emitted value the padded form (`00000008802-0230`) or the raw context values
  (`00000008802-230`)? The Account Balance APIM contract shows `policy-no` as
  `"00000008802"` and `cert-no` as `230`, so the two sides already differ in
  padding today.
- *JSON property name.* The requirement was written as `account-number`, but
  every existing field in this response envelope is camelCase (`asOfDate`,
  `currencySelection`, `displayAccountId`). This pack assumes `accountNumber`
  for consistency. Confirm, or state that the hyphenated form is required.

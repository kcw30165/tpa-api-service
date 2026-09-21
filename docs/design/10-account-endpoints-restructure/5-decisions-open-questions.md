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
Corrected 21/09/2026: percentage belongs to Account Balance, not Gain/Loss.
`% of Portfolio` is a fund's share of account balance. Gain/loss can be
negative, and a negative value cannot express a share of a portfolio, so
siting the field on the gain/loss operation was wrong.

Java does not compute the percentage. TAS supplies it:

- `TRPGetMemberAccBal` gains a per-fund `percentage` field.
- `TRPGetGeneralMetaData` gains metadata-name `ab-percentage`, whose
  `precision` drives rendering.

The BFF adds `AB_PERCENTAGE("ab-percentage")` to `GeneralMetadataName` and
renders `percentage` through the existing `ExactPrecisionRenderer` with
`RoundingMode.UNNECESSARY`, exactly as every other precision-governed value.
No division is performed in Java and no rounding is introduced anywhere in
the account feature set. `totalValue` is still aggregated from balance NAV.

Because the field now arrives on the balance operation, it sits alongside
`fund-code` in `fundDtl` and needs no join to be attributed to a fund.

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

**R-1 Downstream contract — RESOLVED 21/09/2026.**

| Addition | Operation | Wire form |
| --- | --- | --- |
| Percentage | `TRPGetMemberAccBal` response | `accBal[].percentage`, e.g. `25.0` |
| Currency | `TRPGetInvestGL` **request** | `curr` |
| Sequence | `TRPGetInvestGL` response | `fund-seq` — already exists, no change |
| Precision | `TRPGetGeneralMetaData` | `metadata-name: "acbal-pcnt"`, `metadata-type: "precision"` |

Four consequences.

*Percentage is display form, not a ratio.* `25.0` means 25%, so the BFF
renders the value as received and never multiplies by 100. The sample metadata
row carries `metadata-value: "1"`, giving one decimal place — matching the
Portfolio Overview UI.

*Percentage sits on the holding row, not the fund row.* It arrives in `accBal`
alongside `disp-src`, `sub-disp-src`, and `fund-code`, which is the display
source granularity. `holdings[]` is per fund, so a fund held across several
display sources returns several percentage values. They are not summed: the
values are shares of one portfolio, and the fund's share is their sum only if
the source rows partition the fund exactly. See R-5.

*`fund-seq` already exists.* Q-4 ordering needs no downstream change, and the
gain/loss DTO gains no sequence field. Note `TRPGetMemberAccBal` also has a
`fund-seq`; ordering uses the `TRPGetInvestGL` one per Q-4.

*The metadata wire name is `acbal-pcnt`.* The BFF constant is
`AB_PERCENTAGE`, mapped in `GeneralMetadataWireValues`. An earlier draft of
this requirement said `ab-pcnt`; `acbal-pcnt` is the later and authoritative
value.

**R-5 Percentage aggregation across display sources — RESOLVED 21/09/2026.**
`accBal[].percentage` is reported at `fund-code` + `disp-src` + `sub-disp-src`
granularity. A fund's `portfolioPercentage` is the **sum** of its row
percentages.

This is the same key `balance` already aggregates on, so the two values
partition identical rows: a fund's percentage share always corresponds to its
balance share, and the column sums to 100 across the portfolio. The assembler
folds percentage in the same traversal that produces `balance` rather than
making a second pass.

Null is not zero. A row whose `percentage` is null inside a fund that has other
populated rows yields a partial sum, which is a technical contract violation and
maps to SYSTEM_FAILURE, consistent with the sentinel rule for `nav`. The
transition state before TAS deploys - where *every* row is null - is the
distinct case of an unreported section, not a zero one, and must not be
rendered as `"0.0"`.

**R-2 Balance for a sold fund — RESOLVED 21/09/2026, follows from R-5.**
A sold fund appears in `TRPGetInvestGL` but has no `accBal` rows at all. Since
`balance` is a sum over those rows, the empty sum gives `"0.00"`. No special
case is required and none should be added: emitting null here would mean
deliberately overriding an arithmetically correct result.

**R-3 Account number format — RESOLVED 21/09/2026.**

`accountNumber` uses the configured width on *both* components:

```
accountNumber = leftPad(policyNo, 11) + "-" + leftPad(certNo, 4)
              = "00000008802-0230"
```

Widths and pad character come from the global `account-identifiers` block
(`policy-no.width: 11`, `cert-no.width: 4`, `pad-character: "0"`), promoted to
root in commit 8dcebaae. Applying the width to `policyNo` but not `certNo`
would contradict the reason that block was made global.

Note the APIM wire form differs: `TRPGetMemberAccBal` sends `cert-no` as the
unpadded `230`. That is a downstream request contract and is unaffected;
`accountNumber` is a presentation value assembled by the BFF.

*JSON property name.* `accountNumber`, camelCase, consistent with every other
field in this envelope (`asOfDate`, `currencySelection`, `displayAccountId`).
The requirement was written as `account-number`; the config key is
`account-identifiers`, but that governs the YAML namespace, not the response
schema.

*Implementation caveat, see R-6.* Whether the assembler pads at render time
depends on the form already stored in `AccountContext.certNo`.

**R-6 Stored form of AccountContext.certNo — RESOLVED 21/09/2026 from evidence.**
`AccountContext` already holds the padded form. The login chain is:

```
LoginValidatedAccount   rawPolicyNo / policyNo, rawCertNo / certNo
LoginSessionAccount     carries both raw and padded
LoginAuthenticatedSessionAccountMapper  passes policyNo() and certNo()
AuthenticatedAccountContextResolver     copies those into AccountContext
```

The mapper selects the padded accessors, never the raw ones, so by the time the
summary use case sees the context the values are already at width. Corroborated
by `PortalAccessContextWebFilterTest`, which uses `"00000008802"` and `"0001"`,
and by the balance APIM adapter sending `certNo` as `"0230"`.

`accountNumber` is therefore a direct join. Padding is retained in the assembler
only as an idempotent normalisation: a value already at width is unchanged, and
a value longer than its width is a contract violation mapping to SYSTEM_FAILURE.
This keeps the rule true regardless of how the context is populated later.

**R-4 Percentage for a sold fund — RESOLVED 21/09/2026, follows from R-5.**
Identical reasoning: zero rows, so `portfolioPercentage` is `"0.0"`. The row
therefore reads `balance: "0.00"`, `portfolioPercentage: "0.0"`, with
`gainLoss` from `InvestGL` - which is the correct presentation, because a sold
fund occupies none of the current portfolio while still carrying realised
gain/loss.

Note the contrast with the null case in R-5: zero rows means a real zero; rows
present with null percentage means unreported.
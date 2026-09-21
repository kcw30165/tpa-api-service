# Account Endpoints Restructure Handover

## Current stage

Design baseline only. No production or test Java source has been modified.

## Branch

`feature/account-endpoints-restructure-and-summary`, cut from
dev @ 19a4b41c061ae58a34cb156232435e3eaf7d6845.

## Investigation findings carried into this pack

- Both account controllers use class-level `@RequestMapping("/api/v1")` with a
  method-level path segment, so the rename is method-scoped.
- `/api/v1` is shared with `ContributionController` and
  `NotificationController`, which must not be disturbed.
- `TRPGetMemberAccBal` exposes fund identity as `fundDtl.fund-code`;
  `TRPGetInvestGL` exposes it as `InvestGL.FundCode`. The gain/loss adapter
  trims; the balance adapter does not. The composer must trim both sides.
- Both features already resolve reference date and general metadata through the
  same shared ports, and the Gain/Loss metadata key set is a subset of the
  Account Balance set, so composition needs no new metadata key.
- `AccountGainLossApimContentValidator` treats an empty `InvestGL` array as
  valid, so an empty gain/loss result is a real runtime state.
- The APIM routing ledger cardinality is 18 and must remain 18: summary
  introduces no new APIM operation.
- No OpenAPI or Swagger artifact exists in the repository, so the design
  documents are the authoritative contract.

## Blocking

Q-1 through Q-8 in `5-decisions-open-questions.md` must be answered before S1
begins. Q-3 is the most consequential: it would introduce the first rounding
operation in the account feature set.

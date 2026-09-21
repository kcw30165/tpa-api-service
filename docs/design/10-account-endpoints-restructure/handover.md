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

## S1 endpoint rename

Both account read endpoints now live under `/api/v1/account`. `AccountBalanceController`
serves `/account/balance` and `AccountGainLossController` serves `/account/gain-loss`;
both retain the shared class-level `@RequestMapping("/api/v1")`, so
`ContributionController` and `NotificationController` are unaffected.

Path literals were updated in the two controllers, `AccountBalanceControllerTest`,
`AccountGainLossPublicContractTest`, three e2e scripts, and the Account Balance and
Account Gain/Loss contract documents. Request headers, query parameters, response
bodies, status codes, and error mappings are unchanged.

Deliberately preserved: `apim.account-balance.path` and `apim.account-gain-loss.path`
plus their environment overrides, both `@ConfigurationProperties` prefixes, the
`accountbalance` and `accountgainloss` package names, the `TRPGetMemberAccBal` and
`TRPGetInvestGL` APIM paths, and every `account-balance.*` / `account-gain-loss.*`
observability event name. E2E script filenames and the `account-balance` scenario-pack
directory are also unchanged.

No compatibility alias was added; the cutover is atomic per Q-1. Formatting remains
deferred to S6. Summary endpoint work stays blocked on Q-2 through Q-8.

### S1 documentation repair

The S1 documentation pass rewrote every old public path occurrence, including
the six that must retain the pre-rename path because they document the rename
itself. Those lines briefly read "`/api/v1/account/balance` becomes
`/api/v1/account/balance`". The change-set items, the path-change table's
Before column, and the confirmed-by-instruction bullets now carry the correct
`/api/v1/account-balance` and `/api/v1/account-gain-loss` originals.

The old paths now appear exactly six times in the repository, all within this
design pack and all as deliberate history. No controller mapping, test, e2e
script, or configuration value was affected; the live routing remains
`/api/v1/account/balance` and `/api/v1/account/gain-loss`.

## Summary contract ratification

Q-1 through Q-8 are closed. The consequential change is Q-3: percentage is no
longer computed by the BFF. TAS supplies `percentage` on `TRPGetInvestGL` and
a `gl-percentage` metadata name on `TRPGetGeneralMetaData`, so the BFF renders
it like any other precision-governed value and the account feature set keeps
its invariant that Java never rounds.

Q-4 and Q-5 similarly moved downstream: ordering uses the `TRPGetInvestGL`
`seq` field, and the gain/loss fund list is authoritative for `holdings[]`
because it is a superset of the balance fund list — a sold fund retains
gain/loss with no current holding. Q-7 adds `curr` to `TRPGetInvestGL`, so a
currency selection applies consistently across the whole summary payload.

Two blockers remain before S0 begins. R-1: the exact wire contract for the
three downstream additions is unspecified, including property naming and
whether `percentage` is a ratio or a display percentage. R-2: the `balance`
value for a sold fund that has gain/loss but no holding.

One internal repair is now mandatory and is sequenced as S0.
`DefaultAccountBalancePresentationTransformer.precisions(...)` iterates
`GeneralMetadataName.values()` and calls `orElseThrow()` per constant, so
adding `GL_PERCENTAGE` would make Account Balance demand a metadata key it
does not use. The method must resolve only requested keys first.

## Account number addition

`/api/v1/account/summary` gains `result.data.accountNumber`, composed as
`policyNo + "-" + certNo` from the selected account's protected context. It is
derived inside the BFF, needs no upstream call, and is scoped to summary only,
so `/api/v1/account/balance` and `/api/v1/account/gain-loss` are untouched.

Two points on record. First, both components are protected identifiers under
`apim.encryption`, so publishing their concatenation is a deliberate disclosure
and the composed value must stay out of logs. Second, R-3 captures the two
unresolved details: whether the value uses the configured 11/4 zero padding,
and whether the JSON property is `accountNumber` (assumed, matching every other
field in this envelope) or the literal `account-number` as written in the
requirement.

## S0-A precision key narrowing

`DefaultAccountBalancePresentationTransformer.precisions(...)` now resolves an
explicit caller-supplied key set instead of iterating every enum constant. A
private constant declares the four keys Account Balance actually renders —
FUND_PRICE, NET_ASSET_VALUE, UNIT_BALANCE, FX_RATE — matching
`GetAccountBalanceService.REQUIRED_METADATA` exactly. The single-argument
overload is retained and delegates, so `DefaultAccountBalanceAssembler` and
`AccountBalancePresentationTransformerTest` are untouched.

Resolved output is unchanged today: those four keys are currently the complete
enum, so the returned map is identical. What changes is the failure mode.
Account Balance no longer demands a precision row for every future metadata
name, so adding `AB_PERCENTAGE` for the summary endpoint will not make it
`orElseThrow()` on a key it does not use.

`GeneralMetadataName` itself is deliberately untouched. Adding `AB_PERCENTAGE`
also requires a wire mapping in `GeneralMetadataWireValues`, whose `NAMES` map
is a fixed `Map.of(...)` of the four current constants — that is S0-B and stays
blocked on the R-1 wire contract.

The first attempt at this subtask failed its own audit rather than its edit:
the check for enum-wide iteration matched the Javadoc sentence describing the
removed behaviour. The comment no longer names the construct literally, and the
audit now inspects executable lines only.

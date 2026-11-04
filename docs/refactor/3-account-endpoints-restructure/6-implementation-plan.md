# Implementation Plan

Implementation does not begin until Q-1 through Q-8 are answered.

## S1 Endpoint rename

Change two `@GetMapping` values, the five test path literals, and the three
e2e script path literals. Update the Account Balance and Account Gain/Loss
contract documents to the new paths. No behaviour change, no new class.

Gate: compile, test-compile, and the existing controller contract tests pass
against the new paths and 404 on the old ones.

## S2 Summary response DTOs

Add `AccountSummaryDataResponse`, `AccountSummaryResultResponse`,
`GainLossBreakdownResponse`, and `GainLossBreakdownHoldingResponse` under
`adapter/in/web/response/accountsummary/`. `AccountSummaryDataResponse`
composes the existing Account Balance response records unchanged.

Gate: fail-first serialization tests lock field names, ordering, and the
inclusion behaviour settled by Q-5.

## S3 Application composition

Add `GetAccountSummaryUseCase`, `GetAccountSummaryCommand`,
`AccountSummaryResult`, and `GainLossBreakdown` application records. Add
`GetAccountSummaryService`, which resolves reference date once, resolves
metadata once, invokes both existing source ports, and delegates to a new
`AccountSummaryAssembler`.

Constraints: framework-free application layer; both existing source ports and
both existing adapters are reused without modification; no new APIM operation
and no routing-ledger change.

Gate: fail-first tests for join, per-fund balance aggregation, total equality
with `/api/v1/account/gain-loss`, percentage computation, ordering, sentinel
rejection, and both failure-precedence paths.

## S4 Controller and wiring

Add `AccountSummaryController` mirroring `AccountBalanceController`: same
header handling, same `CurrentPortalAccessContextResolver` delegation, same
`RequestLanguageResolver` fallback. Add the Spring configuration class under
`config/accountsummary/`.

Gate: controller tests covering missing X-Request-Id, language fallback,
selected-account resolution, optional currency, and both business mappings.

## S5 Architecture guards

Extend the existing guard style: no `PortalAccessContext` below the
application boundary, no blocking Reactor call, no new global Jackson
inclusion change, no Idempotency-Key requirement, and confirmation that the
APIM routing ledger cardinality is unchanged at 18.

## S6 Stabilization

Run `mvn spotless:apply` once, then `mvn -DskipTests compile` and
`mvn -DskipTests test-compile`. Update `handover.md`. Full `mvn test` is run
manually outside the generated scripts.

## Plan revision 21/09/2026

The ratified decisions move percentage computation and holdings ordering
downstream, which removes work from S3 and adds a prerequisite.

### S0 Metadata enum widening — new, precedes S2

Add `AB_PERCENTAGE("ab-percentage")` to `GeneralMetadataName`, and narrow
`DefaultAccountBalancePresentationTransformer.precisions(...)` so it resolves
only the keys its caller requested instead of iterating
`GeneralMetadataName.values()`. Without this narrowing, adding the enum
constant makes Account Balance require `ab-percentage` and fail.

Gate: existing Account Balance and Account Gain/Loss metadata tests still pass
unchanged, proving neither feature's required key set moved.

### S2 revision

`GainLossBreakdownHoldingResponse.portfolioPercentage` carries a
downstream-supplied value. `AccountSummaryDataResponse` is unchanged otherwise.

### S3 revision

No percentage arithmetic. The assembler renders the balance-side
`fundDtl.percentage` at `ab-percentage` precision, orders by `InvestGL` `seq`,
and drives `holdings[]` from the gain/loss fund list. It asserts the Q-5 superset invariant and fails
with SYSTEM_FAILURE when a holding has no gain/loss counterpart.

### APIM DTO revision — blocked on R-1

`AccountBalanceFundDetail` gains `percentage`; `TrpGetInvestGlFund` gains
`seq`; `TrpGetInvestGlApimRequest` gains `curr`. Both
`AccountBalanceApimContentValidator` and
`AccountGainLossApimContentValidator` must accept the new fields without
weakening their existing checks, and `RawAccountBalanceFundDetail` must carry
`percentage` through the raw boundary. Exact property names and the
ratio-versus-percentage question are unresolved; see R-1.

### S2/S3 addendum — accountNumber

`AccountSummaryDataResponse` gains a leading `accountNumber` string, and the
application `AccountSummaryResult` gains the matching field. The summary
assembler composes it from the command's `policyNo` and `certNo`; no upstream
call, request parameter, or adapter change is involved.

Gate: tests asserting the composed value for padded and unpadded inputs per the
R-3 decision, non-null enforcement, SYSTEM_FAILURE on blank identifiers, and a
redaction check proving the composed value is absent from observability output.


### S8 InvestGL curr-required currency handling

See 8-investgl-curr-required-addendum.md for the full decision set. Adds effective-currency derivation for the gain-loss call inside GetAccountSummaryService, an avai-currency cache in AuthenticatedSession.featureState written on first load and validated on subsequent calls, and a new ACCOUNT_SUMMARY_REQUEST_INVALID / 400 error code. Does not touch /api/v1/account/gain-loss, TRPGetMemberAccBal's request contract, or AccountContext.

Gate: fail-first tests for effective-currency derivation (blank vs supplied), the featureState write-then-read round trip, rejection on a non-member currency, rejection on a cache miss with currency supplied, and a zero-diff audit confirming AccountGainLossController and GetAccountBalanceCommand's blank-currency path are untouched.

## S7 Account Balance endpoint deprecation

See `7-account-balance-deprecation-plan.md` for the full plan. Runs after S3
and S4 are complete and summary is proven. Removes `AccountBalanceController`
and `AccountBalanceResultResponse`; moves the shared section response records
into the summary package; renames the e2e scenario pack. Does not touch
`TRPGetMemberAccBal`, its adapter, its DTOs, or its configuration keys, and
does not withdraw `/api/v1/account/gain-loss`.

Gate: full suite green with the old endpoint removed and the e2e pack renamed
and passing against `/api/v1/account/summary`.

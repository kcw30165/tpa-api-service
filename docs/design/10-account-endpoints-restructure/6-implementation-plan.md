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

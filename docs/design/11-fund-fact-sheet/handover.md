# Fund Fact Sheet Design Handover

## Current state

- Branch `feature/impl-fund-fact-sheet` created from `origin/dev` at `c12d2cc4`.
- BRD analysis, APIM contract review and repository investigations (scripts 001-003) completed.
- Design pack committed in `docs/design/11-fund-fact-sheet/`. Stages 1 to 3 are implemented and committed (Stage 3 at `a0551c6b`); Stage 4 is paused for the contract change below; Stage 5 has not started.
- All pending decisions are resolved. OQ-4 (Important Notes content) is non-blocking.
- Stage 0 investigation complete (scripts 008-016); decisions D-18 to D-21 added; no code blocker found.
- Contract change (2026-10-08): the downstream `TRPGetFundFactSheet` contract changed (decisions C-1 to C-7). The request is `policy-no`, `cert-no` and `sch-type` from the account context; there is no reference date; no funds is an empty `FundFactSheet` list; absent identifiers are logged and answered with 503 without calling APIM. The committed Stage 2 and Stage 3 code follows the old contract and is corrected by a separate `fix(fund-fact-sheet)` commit before Stage 4 resumes. Script 026 (the Stage 4 controller) was set aside unrun.

## Implementation stages

The full execution contract is `fund-fact-sheet-implementation-plan.md` (Stage 0 preflight, fail-first tests, files, rules and gates per stage). The table below is a summary. Implementation continues in a new conversation using `fund-fact-sheet-implementer-handover-prompt.md`; Stage 0 (scripts 008-016) is complete and its findings are folded into the pack by the correction commit (script 017); script numbers are never reused (script 026 was set aside unrun and its number stays used).

| Stage | Scope | Commit |
|---|---|---|
| 1 | `UrlValidator` + `UrlValidatorTest` in `application.service.presentation` | `feat(shared): add UrlValidator for downstream URLs` |
| 2 | APIM DTOs, properties + validation config, `ApimFundFactSheetSourceAdapter`, `FundFactSheetSourcePort`, raw DTOs, retrieval exception, routing ledger (20), YAML path | `feat(fund-fact-sheet): add TRPGetFundFactSheet APIM adapter` |
| 3 | Commands, `GetFundFactSheetUseCase`, service, normalizer, event port + logger, application config, `ErrorCodes` constant, `ApiExceptionHandler` handler | `feat(fund-fact-sheet): add fund fact sheet use case` |
| 4 | Controller, response records, web mapper (language + sort), controller and serialization tests | `feat(fund-fact-sheet): expose GET /api/v1/fund-fact-sheet` |
| 5 | Stabilization: `mvn spotless:apply`, compile, full test suite, startup smoke, coverage per tier, Account Balance diff check | `test(fund-fact-sheet): stabilize and record coverage evidence` |

Implementation scripts validate with `mvn -DskipTests compile` and `mvn -DskipTests test-compile` only; full tests and startup smoke run manually at each stage gate.

Status after the contract change: Stages 1 to 3 committed, Stage 4 paused until the code correction lands, Stage 5 not started.

## Documentation commit

```text
docs(fund-fact-sheet): define implementation design baseline
```

Stage 0 correction commit:

```text
docs(fund-fact-sheet): correct design pack after Stage 0 investigation
```

Contract change (2026-10-08) commits:

```text
docs(fund-fact-sheet): align design with updated TRPGetFundFactSheet contract
fix(fund-fact-sheet): align TRPGetFundFactSheet request and no-fund handling with updated APIM contract
```

The first aligns this pack. The second corrects the committed Stage 2 and Stage 3 code and follows it.

## Follow-up after merge

- `refactor/account-balance-url-validator` per `fund-fact-sheet-url-validator-design.md`.
- Shared `dd/MM/yyyy` date-format helper replacing per-feature formatters (decisions doc, Deferred).

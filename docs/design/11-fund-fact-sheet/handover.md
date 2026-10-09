# Fund Fact Sheet Design Handover

## Current state

- Branch `feature/impl-fund-fact-sheet` created from `origin/dev` at `c12d2cc4`.
- BRD analysis, APIM contract review and repository investigations (scripts 001-003) completed.
- Design pack committed in `docs/design/11-fund-fact-sheet/`. Stages 1 to 5 are implemented and committed (Stage 3 at `a0551c6b`, Stage 4 at `04e5e821`, Stage 5 at this commit). The branch is ready for a merge request pending one final manual full test suite run and startup smoke test (plan section 1.5 / 7).
- All pending decisions are resolved. OQ-4 (Important Notes content) is non-blocking.
- Stage 0 investigation complete (scripts 008-016); decisions D-18 to D-21 added; no code blocker found.
- Contract change (2026-10-08): the downstream `TRPGetFundFactSheet` contract changed (decisions C-1 to C-7). The request is `policy-no`, `cert-no` and `sch-type` from the account context; there is no reference date; no funds is an empty `FundFactSheet` list; absent identifiers are logged and answered with 503 without calling APIM. The committed Stage 2 and Stage 3 code follows the old contract and is corrected by a separate `fix(fund-fact-sheet)` commit before Stage 4 resumes. Script 026 (the Stage 4 controller) was set aside unrun.
- Stage 4 resumed on the corrected code and committed at `04e5e821` (`feat(fund-fact-sheet): expose GET /api/v1/fund-fact-sheet`). An unrelated, pre-existing test-naming defect (three test classes compiled under a stale declared class name, discovered while verifying the Stage 4 gate) was fixed in its own commit `41880b92` (`fix(test): align test class names with their file names`), outside this feature's scope.

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

Status: Stages 1 to 5 committed. Every tracked Fund Fact Sheet class (data and orchestration tiers) measures 100% instruction and 100% branch coverage at the Stage 5 commit; no class falls in the 85-90% branch band, so no uncovered-branch justifications are required. The branch is ready for a merge request pending the final manual full test suite run and startup smoke test.

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

## Stage 5 completion evidence

- Formatting: `mvn spotless:apply` run; `mvn spotless:check` reports the tree clean afterward.
- Compile and test-compile: pass after formatting.
- Coverage (JaCoCo, measured at the Stage 5 commit): every tracked Fund Fact Sheet class in both the data tier and the orchestration tier measures 100% instruction and 100% branch coverage.
- Scope: `git diff --name-only origin/dev...HEAD` contains no Account Balance or Account Summary source or test file.
- Log audit: every log call site in the Fund Fact Sheet production classes (`ApimFundFactSheetSourceAdapter`, `FundFactSheetPresentationEventLogger`) passes a single fixed string literal or named constant; no policy number, certificate number, fund name, URL, `seq` or other value is ever logged.
- Full test suite and startup smoke test: run manually per plan section 1.5 after this commit, consistent with every earlier stage gate; not run by this commit script itself.

## Follow-up after merge

- `refactor/account-balance-url-validator` per `fund-fact-sheet-url-validator-design.md`.
- Shared `dd/MM/yyyy` date-format helper replacing per-feature formatters (decisions doc, Deferred).

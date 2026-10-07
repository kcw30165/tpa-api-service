# Fund Fact Sheet Design Handover

## Current state

- Branch `feature/impl-fund-fact-sheet` created from `origin/dev` at `c12d2cc4`.
- BRD analysis, APIM contract review and repository investigations (scripts 001-003) completed.
- Design pack proposed in `docs/design/11-fund-fact-sheet/`; production implementation not started.
- All pending decisions are resolved. OQ-4 (Important Notes content) is non-blocking.
- Stage 0 investigation complete (scripts 008-016); decisions D-18 to D-21 added; no code blocker found.

## Implementation stages

The full execution contract is `fund-fact-sheet-implementation-plan.md` (Stage 0 preflight, fail-first tests, files, rules and gates per stage). The table below is a summary. Implementation continues in a new conversation using `fund-fact-sheet-implementer-handover-prompt.md`; Stage 0 (scripts 008-016) is complete and its findings are folded into the pack by the correction commit (script 017); the next free script number is 018.

| Stage | Scope | Commit |
|---|---|---|
| 1 | `UrlValidator` + `UrlValidatorTest` in `application.service.presentation` | `feat(shared): add UrlValidator for downstream URLs` |
| 2 | APIM DTOs, properties + validation config, `ApimFundFactSheetSourceAdapter`, `FundFactSheetSourcePort`, raw DTOs, retrieval exception, routing ledger (20), YAML path | `feat(fund-fact-sheet): add TRPGetFundFactSheet APIM adapter` |
| 3 | Commands, `GetFundFactSheetUseCase`, service, normalizer, event port + logger, application config, `ErrorCodes` constant, `ApiExceptionHandler` handler | `feat(fund-fact-sheet): add fund fact sheet use case` |
| 4 | Controller, response records, web mapper (language + sort), controller and serialization tests | `feat(fund-fact-sheet): expose GET /api/v1/fund-fact-sheet` |
| 5 | Stabilization: `mvn spotless:apply`, compile, full test suite, startup smoke, coverage per tier, Account Balance diff check | `test(fund-fact-sheet): stabilize and record coverage evidence` |

Implementation scripts validate with `mvn -DskipTests compile` and `mvn -DskipTests test-compile` only; full tests and startup smoke run manually at each stage gate.

## Documentation commit

```text
docs(fund-fact-sheet): define implementation design baseline
```

Stage 0 correction commit:

```text
docs(fund-fact-sheet): correct design pack after Stage 0 investigation
```

## Follow-up after merge

- `refactor/account-balance-url-validator` per `fund-fact-sheet-url-validator-design.md`.
- Shared `dd/MM/yyyy` date-format helper replacing per-feature formatters (decisions doc, Deferred).

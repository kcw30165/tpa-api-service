# Fund Fact Sheet Design Pack Index

**Feature:** Fund Fact Sheet (BRD 6.8)
**Status:** Design baseline, corrected after the Stage 0 repository investigation (scripts 008-016)
**Implementation status:** Stage 0 complete; Stage 1 not started
**Target branch:** `feature/impl-fund-fact-sheet` (created from `origin/dev`)
**Baseline:** `c12d2cc4f23f87f5987252c6c3b55c2439355863`

## Purpose

This pack turns the reviewed BRD, the `TRPGetFundFactSheet` APIM contract, owner decisions and repository evidence into contracts that govern implementation. Production code and fail-first tests must not start until this pack is committed and accepted.

## Documents

1. `fund-fact-sheet-requirements-analysis.md`
2. `fund-fact-sheet-master-design.md`
3. `fund-fact-sheet-api-contract.md`
4. `fund-fact-sheet-apim-contract.md`
5. `fund-fact-sheet-transformation-contract.md`
6. `fund-fact-sheet-url-validator-design.md`
7. `fund-fact-sheet-error-matrix.md`
8. `fund-fact-sheet-sequence-diagrams.md`
9. `fund-fact-sheet-test-traceability-design.md`
10. `fund-fact-sheet-decisions-open-questions.md`
11. `handover.md`
12. `fund-fact-sheet-implementation-plan.md`
13. `fund-fact-sheet-implementer-handover-prompt.md`

## Governance

- The BRD owns business intent; comment decisions in the Final Draft override its body text.
- TAS/APIM owns which funds are returned, names, links and fund-class filtering.
- Java owns authenticated orchestration, row validation, URL validation, language selection, sorting, mapping and public errors.
- FE owns search, bilingual filtering, labels, wording, button enabling and the no-fund label.
- Where repository evidence contradicts this pack, implementation stops and the pack is corrected first.

## Stage 0 result

The repository investigation (scripts 008-016) found no blocker. Findings and the guard constraints are in `fund-fact-sheet-implementation-plan.md` sections 1.7 and 2.1; decisions D-18 to D-21 are in `fund-fact-sheet-decisions-open-questions.md`.

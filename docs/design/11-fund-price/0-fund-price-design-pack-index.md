# Fund Price Design Pack Index

**Feature:** Fund Price Information  
**Status:** Proposed solution-design baseline for repository review and approval  
**Implementation status:** Not started  
**Target branch:** `features/impl-fund-price`  
**Baseline:** `4fb53ffa663222ceeb26646b11d39005ca559c76`

## Purpose

This pack converts the reviewed BRD, confirmed TAS/APIM schemas, owner decisions, and repository architecture into implementation-governing contracts. Production implementation and fail-first tests must not start until this pack is committed and reviewed as the accepted baseline.

## Documents

1. `fund-price-source-inventory.md`
2. `fund-price-requirements-analysis.md`
3. `fund-price-master-design.md`
4. `fund-price-api-contract.md`
5. `fund-price-apim-contract.md`
6. `fund-price-session-contract.md`
7. `fund-price-date-validation-contract.md`
8. `fund-price-error-matrix.md`
9. `fund-price-excel-export-contract.md`
10. `fund-price-sequence-diagrams.md`
11. `fund-price-test-traceability-design.md`
12. `fund-price-decisions-open-questions.md`
13. `handover.md`

## Governance

- The BRD owns business intent.
- TAS/APIM owns downstream operation schemas and mode selection.
- Java owns authenticated orchestration, validation, protected session state, mapping, public errors, and XLSX generation.
- FE owns calendar rendering and presentation using Java-provided metadata.
- Repository `.github` instructions, skills, and the authoritative APIM developer guide govern physical implementation.
- Where repository evidence conflicts with this proposed design, implementation must stop and the design must be corrected before code continues.

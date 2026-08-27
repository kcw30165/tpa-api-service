# Account Balance Design Pack Index

**Feature:** 6.14 Account Balance  
**Baseline:** 27/08/2026  
**Status:** Consolidated design baseline, pending repository investigation  

## Review conclusion

The previous package contained 26 files, including several parallel generations of the same subjects. Twenty-seven documents are not necessary for this feature. This revision consolidates the package into 12 design documents plus a manifest while preserving the current requirements, contracts, transformation rules, validation, observability, tests, decisions, and handover.

## Canonical documents

1. `account-balance-source-and-requirements.md`
2. `account-balance-master-design.md`
3. `account-balance-api-contract.md`
4. `account-balance-apim-contract.md`
5. `account-balance-transformation-contract.md`
6. `general-metadata-cache-resolver-design.md`
7. `account-balance-validation-error-contract.md`
8. `account-balance-configuration-observability.md`
9. `account-balance-sequence-diagrams.md`
10. `account-balance-test-traceability-design.md`
11. `account-balance-decisions-open-questions.md`
12. `account-balance-implementation-plan.md`
13. `handover.md`

## Consolidation map

- Old requirements analysis and source inventory are merged into `account-balance-source-and-requirements.md`.
- Old APIM-only and APIM-plus-transformation files are replaced by separate canonical APIM and transformation contracts.
- Three old precision/cache documents are replaced by `general-metadata-cache-resolver-design.md`.
- Two old validation/error files and the error matrix are replaced by one canonical validation/error contract.
- Three old test/open-question documents are replaced by a test-traceability design plus a decisions/open-questions register.
- Configuration and observability remain together because both are small cross-cutting operational contracts.

## Readiness

Business and logical design are sufficient for FE review and for repository investigation. Physical implementation phases, exact class/package names, existing validator reuse, Redis concurrency mechanics, and APIM registration edits must be finalized only after codebase investigation.

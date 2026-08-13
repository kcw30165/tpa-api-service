# Feature 0061 - Forgot Password Design Pack Index

**Feature:** 0061 - Forgot Password  
**Baseline:** 2026-07-30  
**Gate:** 3  

**Status:** Regenerated cumulative Gate 3 package, ready for formal approval  
**Gate 4:** Not started and not authorized

## Gate status

| Gate | Status |
|---|---|
| Gate 0 | Completed |
| Gate 1 | Approved and subsequently ratified by applicable domains |
| Gate 2 | Conditionally approved and incorporated into Gate 3 |
| Gate 3 | Regenerated after material business/APIM/navigation changes; consistency review passed |
| Gate 4 | Not started |
| Gate 5+ | Not started |

## Authority status

- Business/content approved by BA on 2026-07-30.
- TAS operations/password behavior approved by TAS on 2026-07-30.
- Security and solution-design decisions approved by Nick on 2026-07-30.
- D3-014 through D3-020 are incorporated into this baseline.

## Artifact manifest

| Concern | Artifact |
|---|---|
| Sources/governance | `forgot-password-source-inventory.md` |
| Requirements | `forgot-password-requirements-analysis.md` |
| Decisions | `forgot-password-decisions-open-questions.md` |
| Master solution | `forgot-password-master-design.md` |
| Workflow | `forgot-password-workflow-contract.md` |
| State | `forgot-password-state-contract.md` |
| FE-BFF API | `forgot-password-api-contract.md` |
| Next steps/actions | `forgot-password-next-step-contract.md` |
| APIM/TAS | `forgot-password-apim-contract.md` |
| Data/masking | `forgot-password-data-contract.md` |
| Session/security | `forgot-password-session-security-contract.md` |
| Errors | `forgot-password-error-matrix.md` |
| Configuration | `forgot-password-configuration-contract.md` |
| Observability/audit | `forgot-password-observability-audit-contract.md` |
| Sequences | `forgot-password-sequence-diagrams.md` |
| Traceability/test intent | `forgot-password-traceability-test-design.md` |
| Pre-Gate-4 QA | `forgot-password-pre-gate-4-consistency-review.md` |
| Handover | `handover.md` |

## Scope controls

Post-login Change Password, Username Recovery, captcha, production code, physical repository names, and implementation decomposition are excluded. The deferred ActorContext evolution is not part of current Feature 0061 implementation unless separately approved.

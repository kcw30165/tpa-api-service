# 22 — Basis of Contribution & Vesting Scale (BRD 6.12) — Design Pack Index

| Doc | Purpose |
|---|---|
| 01-solution-design.md | Scope, architecture, component chain, ownership |
| 02-api-contract.md | BFF `GET /api/v1/vesting-scale` contract (FE-facing) |
| 03-apim-contract.md | APIM `POST /ws/NGTPA/v1/TRPGetVestingScale` contract and parsing rules |
| 04-visibility-and-mapping-rules.md | Config visibility, presence semantics, language filtering, field mapping |
| 05-error-handling.md | err-message classification, contract violations, logging, exception registry |
| 06-test-strategy.md | Fail-first test matrix per layer |
| 07-implementation-plan.md | Milestones, sub-tasks, gates, branch/commit plan |
| 08-decision-log.md | Every resolved question with its answer source |

Baseline: origin/dev `6084ba77507c717aa366c7edf58a8fa06dd9ae92` (GC-VS-001 investigation).
Reference feature: `docs/design/20-change-of-investment-choice/` (CIM GET chain).
Status: all clarification questions resolved by Nick (see 08). No open unknowns.

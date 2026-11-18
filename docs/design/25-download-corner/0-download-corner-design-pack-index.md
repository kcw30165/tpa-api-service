# 25 — Download Corner (BRD 6.9, V0.3) — Design Pack Index

| Doc | Purpose |
|---|---|
| 01-solution-design.md | Scope, ownership, architecture and component chain |
| 02-api-contract.md | BFF `GET /api/v1/download-corner` contract |
| 03-apim-contract.md | APIM `TRPGetDownloadCorner` request, response and parsing |
| 04-visibility-and-mapping-rules.md | Language selection, fallback, filtering and ordering |
| 05-error-handling.md | Failure classification, event fields and public mapping |
| 06-test-strategy.md | Fail-first tests and milestone validation |
| 07-implementation-plan.md | Proposed file plan, milestones and MR boundaries |
| 08-decision-log.md | Locked L1–L18 decisions, rationale and open items |

## Status and source baseline

**M0 status: APPROVED by Nick on 2026-11-18 at commit `202fe9bb08ca6a21bf2558aedf8eab8e25945c92`** (design pack `fd04d50f7697dfd41e7b9f5c0473e81672e4be92` plus fix-up `202fe9bb08ca6a21bf2558aedf8eab8e25945c92`). M0 approval clears the design gate for M1 only; M2 and M3 each require an explicit go-ahead at their milestone gate. Existing-code references were checked against `origin/dev` at `1011a9d8e59a18d273fa2304d2e58f58cfc7f9e9`. The two phase reports were used as investigation aids and discrepancies are recorded in [08 — Decision Log](08-decision-log.md#open-items-and-follow-ups).

**New names, paths, YAML keys and codes are explicitly marked `NEW (proposed)`.** Names and wire details not established by the feature decisions or source reports are marked `UNVERIFIED`.

M0 changes documentation only; no Java, test, YAML, script, or build file is changed. No Maven command is part of M0.

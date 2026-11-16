# 07 — Implementation Plan

Branch: `feature/vesting-scale-bff` from origin/dev (record base SHA). One bounded commit per sub-task. Evidence: `target/evidence/gc-vs-<seq>-<slug>/REPORT.md`, unique folder per prompt.

| Milestone | Sub-task | Content | Done when |
|---|---|---|---|
| M0 | M0.1 | Commit this design pack to `docs/design/22-basis-of-contribution-vesting-scale/` | docs-only commit; MR or included in feature MR |
| M1 APIM adapter | M1.1 | Read-only preflight: Jackson null-inclusion config, existing `login-user-id`/`login-user-role` serialization, generic envelope/data-cardinality helper, masking helper | REPORT.md, no edits |
| | M1.2 | Request DTO, YAML path, port interface, presence-aware application DTOs, adapter + strict mapper, err-message classification (3 codes), malformed handling | adapter/contract/mapper/config tests green |
| M2 Application | M2.1 | `GetVestingScaleUseCase`, `GetVestingScaleService`, `VestingScaleVisibilityPolicy`, wiring config | service + policy tests green |
| M3 Web | M3.1 | Response DTOs + presence serialization proof (codec-level contract test) | three-state JSON proven |
| | M3.2 | `VestingScaleWebMapper`, `VestingScaleController`, `VestingScaleExceptionMappings` + registry | controller/mapper/registry tests green; full suite green |
| M4 Review & deploy | M4.1 | Independent read-only review of M1–M3 against this pack | review REPORT.md, no blocking findings |
| | M4.2 | Startup smoke + scripted curl validation against DEV (en, zh-HK); Sonar new issues fixed | evidence recorded; MR to dev |
| M5 E2E | M5.1 | Add vesting-scale scenario to the real login→feature→logout orchestrator | **blocked** until Nick provides the orchestrator guideline |

Stop rules: 3 consecutive failed attempts → escalate; 10 prompts without a commit → escalate.

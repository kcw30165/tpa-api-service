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
| M5 E2E | M5.1 | Add the read-only `vesting-scale` post-login feature pack. Select the shared account with zero-based `E2E_ACCOUNT_INDEX` (default `0`); the original per-feature index proposal was replaced by this shared selector. | Registered two-language GET pack, offline fake-curl contract tests, summary-only evidence, and pre-login absent-field validation are complete. |

Stop rules: 3 consecutive failed attempts → escalate; 10 prompts without a commit → escalate.

## M6 — Post-M5 config-matrix filtering follow-up

Status: planned by the M0 documentation update; no implementation has started. This follow-up does not rewrite the
completed M0–M5 history. M1 and M2 are independent feature milestones; M3 starts after both are complete.

| Milestone | Scope | Fail-first and completion criteria |
|---|---|---|
| M1 Vesting Scale | Filter raw `JsonNode` config entries to normalized UI item-types before DTO deserialization; preserve mapper enum validation, malformed-response behavior, and duplicate-ID rejection for retained entries. | Add fail-first cases in `ApimVestingScaleAdapterTest` and mapper regressions in `TrpGetVestingScaleApimMapperTest` for excluded malformed `config-value`, normalized UI eligibility, retained valid/invalid UI values, and retained duplicates; run targeted regression tests. |
| M2 Personal Information | Filter normalized UI and DATA entries before typed conversion, including the raw-list compatibility path; preserve GET DATA rendering, UI access-state behavior, the PUT cache/refetch path, and last-row-wins for eligible duplicate IDs. | Add fail-first cases in `ApimPersonalInformationAdapterTest` and `GetPersonalInformationServiceTest`; add GET rendering coverage in `PersonalInformationWebMapperTest` and PUT cached-access coverage in `PersonalInformationAccessConfigServiceTest` or `PersonalInformationFieldAccessCheckerTest`; run targeted regression tests. Do not change `TRPUpdMemberInfo` response mapping. |
| M3 Validation | Validate both feature milestones together. | Run full regression, coverage, startup smoke, and SonarQube; record results and any accepted gaps. |

Do not introduce a shared filtering abstraction unless a separate, evidence-based need is established.

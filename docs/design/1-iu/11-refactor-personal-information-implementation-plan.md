# Personal Information Refactor — Implementation Plan

> **Amended 2026-10-22 (requirement change).** Where this document conflicts with the section "Amendment 2026-10-22" at the end, the amendment wins.


Design: `10-refactor-personal-information-design.md`. Branch `refactor/personal-information`.
Each sub-task follows the project gates: fail-first (where behaviour changes), `mvn spotless:apply && mvn spotless:check`,
`mvn -DskipTests compile`, `mvn -DskipTests test-compile`, then `mvn -Dtest=<classes> test` (new or changed tests plus the
existing tests of every class changed). Each milestone ends with the milestone gates: `mvn spotless:check`, full
`mvn test`, coverage (instruction >= 95%; DTOs, records, validators, pure functions branch >= 95%; adapters, use cases,
controllers 85-90% with justified gaps), startup smoke test, SonarQube. Push and MR only after the last milestone passes.
Commits use Conventional Commits.

## Milestone 1 — Response contract and env removal

| Sub-task | Title | Fail-first | Main classes |
|---|---|---|---|
| 1.1 | GET returns `MutationResponse` with `result.data = {page, form}` | yes | `PersonalInformationController`, `PersonalInformationWebMapper`, new `PersonalInformationResultResponse`, `PersonalInformationDataResponse`; update `get-personal-information-response.json` usage |
| 1.2 | Remove `env` from the `TRPGetMemberInfo` request body | yes | `GetMemberInfoApimRequest`, `ApimMemberInfoAdapter` |
| 1.3 | Remove `env` from the `TRPUpdMemberInfo` request body | yes | `UpdateMemberInfoApimRequest`, `UpdateMemberInfoRequest`, `ApimUpdatePersonalInformationAdapter` |
| 1.4 | Fix SonarQube S5841 in two request-body tests (assert the field list is non-empty before `doesNotContain`) | no | `GetMemberInfoApimRequestTest`, `UpdateMemberInfoApimRequestPayloadTest` |
| 1.5 | Delete the unused `UpdateMemberInfoRequest` record and its test | no | `UpdateMemberInfoRequest`, `UpdateMemberInfoRequestTest` |

Notes: routing and credential context keep `accountEnv`; the PUT response items keep `env`.

## Milestone 2 — Partial update and YAML-driven validation

| Sub-task | Title | Fail-first | Main classes |
|---|---|---|---|
| 2.1 | YAML field groups: binding, linter, group definitions (U-1: overseasPhoneExtension standalone); `RuleGroupFields` helper so rule groups bound as index-keyed maps are linted (D-22) | yes | `PageSchemaProperties` and related pageconfig properties, `application-page-personal-information.yml`, `ValidationDslBindingAndLinterTest` |
| 2.2 | Validator semantics: format rules on non-blank values only; `required` only for submitted blank fields; `null` normalised to `""` in the web mapper | yes | `PersonalInformationUpdateYamlValidator`, `PersonalInformationUpdateWebMapper`, command records (no NPE on `null`), rewrite of the presence-semantics guard test |
| 2.3 | Group presence rule and address rules (section 4.3), including `address.atLeastOneRequired` only when both groups are submitted; the validator reads rule groups through `RuleGroupFields` (D-22) | yes | validator, YAML rules, validator tests |
| 2.4 | YAML alignment D1-D6 (regexes, mailing line 1 `minLength`, remove email `minLength`, fax rule, remove AEOI rule) | yes | `application-page-personal-information.yml`, YAML guard tests |

Note: in M2 the group presence rule requires all fields of a group; excluding non-editable fields is added in 3.4.

## Milestone 3 — Session-cached field config

| Sub-task | Title | Fail-first | Main classes |
|---|---|---|---|
| 3.1 | Extract field access resolution (HIDDEN / READONLY / EDITABLE_COM / EDITABLE_OPTION) into one shared pure class; characterization tests first; behaviour-preserving for GET in this step; record the behaviour for item ids absent from the config | characterization | `PersonalInformationWebMapper`, new access resolver |
| 3.1b | Group-wide access normalization (D-19) in the shared resolver: any field of a group not READONLY => all not READONLY; any REQUIRED => all REQUIRED; log ERROR for inconsistent groups; applies to GET and PUT | yes | access resolver, `PersonalInformationWebMapper` |
| 3.2 | Session cache entry and read/write service on `featureState` key `personal-information.config` | yes | new entry record, new cache service, application config |
| 3.3 | GET writes the config to the session after a successful retrieval (never fails the request) | yes | `PersonalInformationController` or use case, session cookie reader |
| 3.4 | PUT resolves field access (cache, else one `TRPGetMemberInfo`; one refetch on a cached rejection; 503 when unavailable) and rejects HIDDEN / READONLY / blank EDITABLE_COM fields; group presence excludes non-editable fields; remove the static `required` of `emailAddress`; record the current VALIDATION_FAILED HTTP mapping | yes | `PersonalInformationController` (or new orchestrating service), `UpdatePersonalInformationService`, validator, `ApiExceptionHandler` mapping, `ErrorCodes` |
| 3.5 | Docs sync: amend `information-update-rules.md` and design docs to the as-built behaviour | no | docs |

## Open items carried

- U-1 `overseasPhoneExtension` group membership (YAML-only change once confirmed).
- BRD section 9 deviation (any of address lines 1-3 counts) to be raised with the stakeholder.
- Label removal and `ApiExceptionHandler` consolidation are later, separate branches.

## Amendment 2026-10-22 — revised Milestone 3

Design: section "Amendment 2026-10-22" of the design document (D-25 to D-32). 3.1 and 3.1b are committed; 3.1b is
reverted by 3.1c with a new commit (no history rewrite).

| Sub-task | Title | Fail-first | Main classes |
|---|---|---|---|
| 3.1c | Remove group-wide access normalization and fieldGroups (D-30): FieldGroupAccessNormalizer and its tests, fieldGroups YAML, binding and linter checks, sync guard | no (removal) | access resolver, PersonalInformationWebMapper, PageSchemaProperties, PageSchemaLinter, application-page-personal-information.yml |
| 3.1d | Remove the group presence rule and the Java country rule (D-27, D-29); delete the four country YAML rules; narrow address.atLeastOneRequired to line 1 and evaluate it only when both line-1 keys are submitted blank (D-28) | yes | PersonalInformationUpdateYamlValidator, YAML, validator and YAML guard tests |
| 3.1e | Conditional-required over submitted fields only (D-31) | yes | PersonalInformationUpdateYamlValidator |
| 3.1f | requiredOverride for line 1 (D-25a) and unknown-value fail-safe (D-32) in the shared access resolver; GET reflects it | yes | access resolver, page YAML binding, PersonalInformationWebMapper |
| 3.2 | Session cache entry and service (unchanged) | yes | as before |
| 3.3 | GET writes config to the session (unchanged) | yes | as before |
| 3.4 | PUT resolves access and applies A.3 steps 1-2 (no group presence exclusion); remove the static required of emailAddress | yes | as before, minus group logic |
| 3.5 | Docs sync to as-built behaviour | no | docs |

Then the Milestone 3 gates.

Open items carried: U-1, U-2 (country rule unconfirmed). The BRD section 9 deviation is withdrawn.
Out of scope: PIN verification (feature/step-up-pin-verification), label removal (separate branch).

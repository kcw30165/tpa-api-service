# Personal Information Refactor — Implementation Plan

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

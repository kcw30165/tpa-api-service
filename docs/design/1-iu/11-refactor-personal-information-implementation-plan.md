# Personal Information Refactor — Implementation Plan

> **Amended 2026-10-27 (Milestone 4).** The section "Amendment 2026-10-27 — Milestone 4" at the end records the latest completion status and wins over earlier gate instructions or milestone notes where they conflict.

> **Amended 2026-10-22 (requirement change).** Where this document conflicts with the section "Amendment 2026-10-22", that amendment wins except where superseded by Milestone 4. The "As-built 2026-10-23 (sub-task 3.5)" section below is a historical Milestone 3 record.


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

## As-built 2026-10-23 (sub-task 3.5)

Sub-tasks 3.1 to 3.4c are committed; the commits are listed in design section B.1. The planned 3.4 was delivered as three
sub-tasks.

| Sub-task | Title | Commit |
|---|---|---|
| 3.4a | PUT field-access validator (new class, not wired) | e053f1a3 |
| 3.4b | access-config lookup service and UI-only field fix | 72a03537 |
| 3.4c | controller wiring, one refetch on a cached rejection, email.required removed | 9fcd0317 |

Differences from the planned 3.4: no group presence exclusion (D-27). The HTTP 503 handler (O-1) and the PUT HTTP
status (O-2) were added afterwards (design B.5).

Targeted test run at 3.4c: 533 tests, 0 failures, 0 errors.

Milestone 3 gates: full mvn test 6476 tests, 0 failures, 0 errors, 10 skipped; startup smoke test passed; SonarQube
reported 14 new-code issues, fixed in 0b1abd50 (a re-run after the next push is pending). Later: O-1 in 3f5a61f8 and
O-2 in d1cd529e. Open items: design B.6 and B.7.

## Amendment 2026-10-27 — Milestone 4

Design: `10-refactor-personal-information-design.md`, section "Milestone 4 as
built". This amendment records the approved PUT result contract, completed
implementation, and gates on the rebased branch. The Milestone 3 as-built notes
above remain historical; their PUT result-array and HTTP-status statements are
superseded.

### Commit record

| Step | Commit | Scope |
|---|---|---|
| ST-0 | `6157bdc9ab0d19910a6195f135b7ad88af81667d` — `refactor(account): share account identifier formatting` | Extract configured shared AccountIdentifierFormatter; Login and Personal Information reuse it. |
| ST-1 | `4a8ab59c2b9613f749615e71071f39b90b4d7711` — `fix(personal-information): forward apply-all to APIM` | Forward FE applyToAllAccounts to APIM apply-all, default false; one downstream request. |
| ST-2 | `3859da5e421d719d59c4ee9152311bef8515ac19` — `fix(personal-information): classify PUT account outcomes` | Pure SS/SF/FS/FF/SYSTEM_ERROR classification; PUT-specific identifier/outcome/transport failure handling; preserve encrypted identifiers as strings. |
| ST-3 | `457fe15aaa3ab8e4b09cf5f499566ed11bf9d3bd` — `fix(personal-information): map PUT account errors` | Map downstream codes/fields to code-only MappedError values and FE field targets. |
| ST-4 | `de21fc522cfb71ee6c0061fee71c39cba7aaf2fc` — `fix(personal-information): shape PUT account outcome response` | Selected-account/OA response shape and downstream outcome HTTP/status contract. |
| ST-5 | `feda005c5482c48b2a71667551ba2b5e3ceee025` — `fix(personal-information): distinguish malformed PUT requests from content rejections` | Structural checks before YAML/access; malformed 400 versus content 422; code-only errors. |
| Milestone-end formatting | `8d5922e09f1c15c84aec971710e679df706c15bc` — `style(personal-information): apply formatting after milestone 4` | Spotless formatting and three same-package import removals. |
| Step 056 gate gaps | `6dce48236fd8bdefcad353c6882f0514d020e0bb` — `test(personal-information): close milestone 4 gate gaps` | Update stale code-only 400 characterization; lower duplicate/handled ERROR logs to WARN; add coverage tests. These fixes were committed separately, not under the formatting commit. |
| Step 057 SonarQube | `eaca1bb960ef9cd85f833100a735c095b06f997e` — `refactor(personal-information): resolve milestone 4 sonar findings` | Resolve 42 new-code findings with behavior-preserving production/test refactors. |

These are the nine Milestone 4 commits in the current branch history. The
step-056 gate fixes have their own commit; they are not part of the formatting
commit.

### Sub-task gates and milestone gates

| Work | Gate/change |
|---|---|
| ST-0 | Fail-first shared formatter/Login tests, compile/test-compile and targeted tests; Spotless deferred. |
| ST-1 | Fail-first APIM apply-all request tests, compile/test-compile and targeted tests; Spotless deferred. |
| ST-2 | Fail-first pure classifier/adapter outcome tests, compile/test-compile and targeted tests; PUT-specific 503 cases included; Spotless deferred. |
| ST-3 | Fail-first downstream code/field mapping tests, compile/test-compile and targeted tests; Spotless deferred. |
| ST-4 | Fail-first SS/SF/FS/FF response and HTTP contract tests, compile/test-compile and targeted tests; Spotless deferred. |
| ST-5 | Fail-first malformed-versus-content rejection tests, compile/test-compile and targeted tests; Spotless deferred. |
| Milestone-end formatting (054) | Ran `spotless:apply`, `spotless:check`, clean compile and test-compile. The initial full suite exposed gate gaps; these were diagnosed before the separate step-056 repair. |
| Step 056 | Clean test-compile, targeted tests and full `mvn clean test`; accepted the controller branch-coverage exception documented below. |
| Step 057 | Spotless, clean test-compile, all touched-class/PUT targeted tests, full clean suite and fresh JaCoCo report. |
| Rebased HEAD `eaca1bb9` | re-run: Tests run: 6816, Failures: 0, Errors: 0, Skipped: 10 |

### Superseded decisions and accepted exception

- D-02's prior response-array contract is replaced by the Milestone 4
  `selectedAccount` / successful OA `rows[]` / failed OA `errors[]` contract.
  Public PUT response items no longer expose `env`; internal account matching
  still includes `accountEnv`.
- O-2 is narrowed: malformed requests remain HTTP 400; Java YAML/access content
  rejections and downstream SA business failures are HTTP 422; success and
  partial success are HTTP 200; specified PUT protocol/transport and classifier
  failures are HTTP 503. O-1 remains unchanged: an unavailable
  `TRPGetMemberInfo` lookup returns 503 `SYSTEM_ERROR` with
  `err.personal-information.retrieval.unavailable`.
- `PersonalInformationController` branch coverage is **73.1%** (19/26). The
  seven uncovered branches are defensive private-helper null-context/null-
  account branches that cannot be reached through the public reactive entry
  points: Reactor cannot emit null, and missing accounts are rejected by
  `requireAccountRef` before those helpers. This coverage exception is accepted;
  no behavior change or private-reflection test was introduced to raise it.

### Follow-ups

| Follow-up | Scope |
|---|---|
| ArchUnit classifier import guard | Add a guard preventing `PersonalInformationUpdateOutcomeClassifier` from acquiring adapter/framework dependencies. |
| Unknown downstream field diagnostics | Consider optional, privacy-safe logging of unknown downstream field names that are currently omitted from response targets. |
| Duplicate structural validation | Consider removing the duplicate `validateStructure` call between controller pre-check and `toCommand`, while preserving the malformed-request-before-YAML/access ordering. |
| YAML email-pattern decision | The current YAML email pattern accepts a leading-dot domain; business decision pending. |
| FE translations | FE translations are required for `personalInformation.email.invalid`, `personalInformation.field.invalidFormat`, `personalInformation.update.rejected`, `personalInformation.update.success`, and `personalInformation.update.partialSuccess`. |

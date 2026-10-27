# Personal Information Refactor — Design

> **Amended 2026-10-27 (Milestone 4).** The section "Milestone 4 as built" at the end is the latest source of truth for PUT result shape, error mapping and HTTP status. It supersedes conflicting earlier D-02/D-14 wording and the Milestone 3 as-built PUT behavior. Earlier sections remain as historical design and milestone records where not superseded.

> **Amended 2026-10-22 (requirement change).** Where this document conflicts with the section "Amendment 2026-10-22", that amendment wins except where superseded by Milestone 4.


Branch: `refactor/personal-information` (rebased base `origin/dev` cff55083). Owner: Nick (solution design approval).
Status: design agreed in conversation on 2026-10-21; this document is the written record for review.

Related: `information-update-rules.md` (BRD extract), `get-personal-information-response.json` (GET sample),
`11-refactor-personal-information-implementation-plan.md` (milestones and sub-tasks).

## 1. Scope

In scope: `GET /api/v1/personal-information` and `PUT /api/v1/personal-information`.

1. GET response becomes `MutationResponse`; `result.data` holds `page` and `form`.
2. `env` is removed from the request payload of `TRPGetMemberInfo` and `TRPUpdMemberInfo`.
3. PUT accepts partial updates with group-completeness rules.
4. Labels: no change in this branch (see 2, D-15).

Out of scope: removal of labels and of the `messages[]` / `errors[]` label text (separate refactor branch later);
central `ApiExceptionHandler` consolidation (deferred workstream);
`mailingAddressSameAsResidential` and `applyToAllMemberAccounts` handling (unchanged).

## 2. Decision register

| ID | Decision | Source |
|---|---|---|
| D-01 | GET returns `MutationResponse<PersonalInformationResultResponse>`: `{success, status: SUCCESS, result: {data: {page, form}}, messages: [], errors: []}`. Mirrors `FundFactSheetResultResponse(data)`. | Nick |
| D-02 | Milestone 4 PUT result contract: resolve the selected account (SA) from Account-Ref; identify its downstream row by policy + certificate + internal `accountEnv`, normalizing leading zeros for the numeric identifiers. Return `result.data` with `selectedAccount` (or null when SA fails), successful other-account rows, and failed other-account errors. Root `errors[]` contains SA or Java validation errors only; never duplicate errors. See "Milestone 4 as built" below for exact fields, codes and status matrix. Identifiers remain strings end-to-end because policy-no/cert-no are field-encrypted. | Nick |
| D-03 | `env` is removed only from the request BODY of `TRPGetMemberInfo` and `TRPUpdMemberInfo`. APIM `ACCOUNT_ENV` routing, `ApimCredentialResolutionContext(role, accountEnv)` and the `accountEnv` used for YAML variant resolution stay. | Nick |
| D-04 | PUT is a partial update. An absent field means unchanged. `null` and `""` mean clear (the downstream accepts `""`); Java normalises `null` to `""` before building the command. Mandatory-ness is checked (D-10). | Nick |
| D-05 | Group rule: if any field of a group is present, ALL editable fields of the group must be present. "Present" means the key exists in `fields`, whatever its value. | Nick |
| D-06 | Groups are defined once in YAML (D-07): residentialAddress, mailingAddress, businessPhone, overseasPhone (see 4.2). | Nick |
| D-07 | Groups live in the page YAML (`bff-pages.pages.personalInformation`) and are referenced by validator and rules; no hard-coded field lists in Java. | Nick (configurable approach) |
| D-08 | Address rules, see 4.3. Each address group is judged on its own. | Nick |
| D-09 | Format rules (`pattern`, `minLength`, `maxLength`, `email`, `blockedAddress`) apply only to non-blank values, so `""` can clear a field. This replaces the semantics pinned by `PersonalInformationFieldPresenceValidationSemanticsGuardTest`. | Nick (Q-A) |
| D-10 | Mandatory source is the `TRPGetMemberInfo` config (HIDDEN / READONLY / EDITABLE_COM / EDITABLE_OPTION), cached by GET and read by PUT. EDITABLE_COM => a submitted value must be non-blank, for non-address fields. Address groups are governed only by D-08. The static `required` rule of `emailAddress` is removed. | Nick |
| D-11 | The config cache is stored in `AuthenticatedSession.featureState` under the key `personal-information.config`, like `/api/v1/fund-comparison/options`. It stores only the item-id => config-value map plus `accountRef` and `policyNo`. No member data. | Nick |
| D-12 | Lifecycle is controlled by the authenticated session (idle and absolute timeout, logout). Each GET overwrites the entry. PUT never evicts it. | Nick |
| D-13 | PUT cache miss (or no session cookie): call `TRPGetMemberInfo` once, refresh the cache, validate. A rejection decided from a cached entry: refetch once and revalidate. If `TRPGetMemberInfo` cannot return the data: HTTP 503 `SYSTEM_ERROR`. | Nick |
| D-14 | A config/access rejection is a content rejection: HTTP 422 `VALIDATION_FAILED` with code-only errors and frontend `targets`. Malformed request structure remains HTTP 400 under the Milestone 4 rules. | Nick (Milestone 4 supersedes original status) |
| D-15 | PUT responses carry codes and `targets` only; FE owns PUT error/success text. GET labels and existing GET `messages[]` / `errors[]` label text are unchanged. | Nick (Milestone 4 clarifies PUT scope) |
| D-16 | YAML is aligned with the BRD: D1 phones keep `minLength: 8` (non-blank only); D2 `mailingAddressLine1.minLength: 5` enabled (non-blank only); D3 email regex allows only letters, digits, `.`, `_`, `-`; D4 the email `minLength` rule (including the JP variant) is removed; D5 fax allows digits, space and `-` only, no maximum length in YAML (field attributes follow the TAS setting); D6 the `aeoiReminder.whenResidentialCountryChanged` rule is removed. | Nick |
| D-17 | The GET change is breaking for the frontend; the frontend is aligned. | Nick |
| D-18 | A group that is HIDDEN or READONLY is simply not sent by FE. Java never rejects a request because a group is absent. Absent group => unchanged. | Nick (Q-O) |
| D-19 | Group-wide access normalization in the shared access resolver (GET and PUT): within a field group, if any field is not READONLY then all fields of the group are treated as not READONLY; if any field is REQUIRED (EDITABLE_COM) then all fields of the group are REQUIRED. A group whose fields disagree is logged at ERROR level. Reason: the config matrix is not designed to provide a group feature; in theory all fields of a group carry the same values. | Nick |
| D-20 | FE contract for the address groups: the FE sends the entire group (4 keys) when the group is editable; when both groups are editable, any change to either group sends both groups (8 keys); a group that is not editable is not sent. Java derives the same set from the session-cached config (D-05, D-18). | Nick |
| D-21 | `fieldGroups` is Java-only YAML under `bff-pages.pages.personalInformation.fieldGroups` (name + fields) and is not serialized to the GET response. `address.atLeastOneRequired` keeps its inline groups because the FE reads them; a guard test keeps both definitions in sync. The linter checks: unique non-blank names, non-empty known fields, no field in two groups. | Nick (Q-S) |
| D-23 | Milestone 2 coverage gate (gate 024, HEAD 53ceed2a) found `PersonalInformationUpdateYamlValidator` at 85.5% instruction / 76.4% branch and `PageSchemaLinter` at 97.3% instruction / 88.9% branch, both below the 95%/95% DTO-validator tier. Of the uncovered branches, 8 lines in the validator were introduced by M2.1-M2.3 (validateFieldGroups, validateAddressGroupCountryRule, resolveGroupMessage, groupSubmitted/groupIsEmpty's null-group handling) and are closed by dedicated tests in this sub-task. The remaining uncovered branches in both files - `instantiateContext`'s reflection fallback, `fallbackVariantCandidates`/`addJoined`/`addSingle`, `isValidEmail`/`isBlockedAddress` detail branches, and `PageSchemaLinter`'s pre-existing null-guard branches - predate this branch and are explicitly OUT of scope: tracked as separate technical debt, not closed here. |  Nick (option 2) |
| D-22 | Rule groups (`when.groups[].fields`) are read through `RuleGroupFields.of(...)`, which accepts a list or an index-keyed map. Probe 011 (Spring Boot binder harness, real YAML, HEAD 081b2d9e) showed the group `fields` bound as `LinkedHashMap`, so `address.atLeastOneRequired` did not fire when all address fields were absent; the existing unit tests build the groups as lists and did not show it. The linter uses the helper in M2.1, the validator in M2.3. Historical M2 consequence: after M2.3 a PUT that submits both address groups blank returns 400 `VALIDATION_FAILED` (behavior change visible to FE). Under Milestone 4, this content rejection returns HTTP 422 `VALIDATION_FAILED`. | Probe 011; Milestone 4 |
| D-24 | Group-wide access normalization (D-19, sub-task 3.1b) and HIDDEN fields: a HIDDEN field remains HIDDEN regardless of its group's normalized READONLY/REQUIRED state (HIDDEN is not overridden by group normalization). A group containing a HIDDEN field alongside differing non-HIDDEN access values among its other fields is logged as an inconsistency at ERROR level (group name and item ids only, no member data), mirroring D-19's handling of READONLY/REQUIRED disagreement. | Nick |

Flagged unknown U-1: whether `overseasPhoneExtension` belongs to the overseasPhone group or is standalone. Stakeholder
confirmation is pending. Because groups are YAML-defined (D-07), the answer is a YAML-only change. Until confirmed it is
treated as standalone (no group rule).

Deviation from BRD wording (to be raised with the stakeholder): BRD section 9 states that "Line 2 or line 3 alone does not
count" as an address. Nick decided that any of address lines 1-3 counts as an address line, with the country mandatory
(D-08). The BRD extract is annotated accordingly.

## 3. API contracts

### 3.1 GET /api/v1/personal-information

```json
{
  "success": true,
  "status": "SUCCESS",
  "result": { "data": { "page": { }, "form": { } } },
  "messages": [],
  "errors": []
}
```

`page` and `form` are the existing `PageResponse` and `FormSchemaResponse` payloads, unchanged. New response records:
`PersonalInformationResultResponse(PersonalInformationDataResponse data)` and
`PersonalInformationDataResponse(PageResponse page, FormSchemaResponse form)`. `FormPageResponse` is not used by
this endpoint any more. Side effect: on success the field config of the selected account is written to the session
(section 5). A failed cache write never fails the request.

### 3.2 PUT /api/v1/personal-information

Request (unchanged envelope): `{ "formVersion": "...", "applyToAllAccounts": false, "fields": { ... } }`.

- Absent key: unchanged. `null` or `""`: clear.
- Unknown keys are handled as today (not changed by this refactor).
- Downstream request no longer contains `env` (D-03). The current response and HTTP contract is
  "Milestone 4 as built" at the end of this document; the former result-array response described in D-02
  and the original failure table below are superseded.

Historical Milestone 3 failure responses (superseded by Milestone 4):

| Case | HTTP | status | errors |
|---|---|---|---|
| Format or group rule fails | 400 | VALIDATION_FAILED | code + targets |
| Submitted HIDDEN / READONLY field | 400 | VALIDATION_FAILED | `personalInformation.field.notEditable`, targets = the fields |
| Blank EDITABLE_COM field | 400 | VALIDATION_FAILED | `personalInformation.field.required`, targets = the fields |
| `TRPGetMemberInfo` unavailable (config cannot be resolved) | 503 | SYSTEM_ERROR | `err.personal-information.retrieval.unavailable` |
| Downstream business rejection / selected account missing in response | unchanged | unchanged | unchanged |

HTTP status mapping for VALIDATION_FAILED follows the existing validation-failure path; sub-task 3.4 records the
current mapping in its evidence before changing anything.

## 4. Validation model

### 4.1 Phases

1. Resolve field access: cached config of this session and account, or one `TRPGetMemberInfo` call (D-13).
2. Validate the request against the YAML (formats, groups, address rules) and against the field access (D-14).
3. Normalise `null` to `""` and call `TRPUpdMemberInfo` with only the submitted fields.

The validator becomes a function of (request, resolved field access, language/variant context). Field access per field
is resolved by one shared pure class, extracted from the logic `PersonalInformationWebMapper` already uses for GET
(HIDDEN, READONLY, EDITABLE_COM), so GET and PUT cannot drift.

### 4.2 Groups (YAML-defined)

| Group | Fields |
|---|---|
| residentialAddress | residentialAddressLine1, residentialAddressLine2, residentialAddressLine3, residentialCountry |
| mailingAddress | mailingAddressLine1, mailingAddressLine2, mailingAddressLine3, mailingCountry |
| businessPhone | hongKongBusinessPhone, hongKongBusinessPhoneExtension |
| overseasPhone | overseasCountryCode, overseasAreaCode, overseasPhoneNumber (U-1: overseasPhoneExtension pending) |

Standalone fields: homeTel, hongKongMobilePhone, faxNo, emailAddress, smsLanguage (and overseasPhoneExtension, U-1).

Group presence rule (D-05): after group normalization (D-19) a group is either editable or not; the required keys of an editable group are all of its fields, and a group that is not editable is never required.
Missing required keys => 400, code `personalInformation.<group>.incomplete`, targets = the missing keys.
A group with no key present is untouched (D-18).

### 4.3 Address rules (D-08)

For each submitted address group (all required keys present):

- If any of lines 1-3 is non-blank, the country must be non-blank.
- If the country is non-blank, at least one of lines 1-3 must be non-blank ("country alone is not an address").
- A group whose lines and country are all blank is allowed (it clears that address).
- `Line1` format rules (`minLength: 5`, `maxLength`, `blockedAddress`) apply when the value is non-blank; the PO-box
  block applies to the residential group only, as today.

Cross-group rule (`address.atLeastOneRequired`): the two address groups cannot both be blank. It is evaluated only when
BOTH address groups are submitted. If only one is submitted, Java does not judge the other (D-18); the downstream
system is the backstop. Existing codes (`personalInformation.address.atLeastOneRequired`,
`personalInformation.residentialAddress.incomplete`, `personalInformation.mailingAddress.incomplete`,
`personalInformation.residentialCountry.required`, `personalInformation.mailingCountry.required`) are reused where they
describe the failure.

### 4.4 Other YAML rules

Existing conditional rules are kept and evaluated only over submitted fields:
`businessPhone.requiredWhenExtensionPresent`, `overseasPhoneNumber.requiredWhenCodePresent`,
`overseasCountryCode.requiredWhenOverseasPhonePresent`. The static `required` of `emailAddress` is removed (D-10).
YAML changes follow D-16.

### 4.5 Config access rules (D-14)

| Field access | Submitted value |
|---|---|
| HIDDEN, READONLY | rejected: `personalInformation.field.notEditable` |
| EDITABLE_COM | must be non-blank, except address fields (D-10): `personalInformation.field.required` |
| EDITABLE_OPTION | optional |

A field whose item id is absent from the config follows the existing GET mapper behaviour; sub-task 3.1 records that
behaviour in a characterization test and the design is amended if it is not the agreed fail-safe.

### 4.6 Group-wide access normalization (D-19, D-20)

The config matrix assigns access per field, but the fields of a group should carry the same values. The shared access resolver therefore normalizes each group (sub-task 3.1b), for GET and PUT alike:

- any field of the group is not READONLY => all fields of the group are not READONLY;
- any field of the group is REQUIRED (EDITABLE_COM) => all fields of the group are REQUIRED;
- a group whose fields disagree is logged at ERROR level (group name and item ids only, no member data).

After normalization a group is either fully editable or fully non-editable, so D-14 (reject submitted READONLY fields) never rejects a field inside an editable group, and the presence rule of 4.2 needs no per-field exception.

Resolved (3.1b, D-24): a HIDDEN field remains HIDDEN regardless of its group's normalized READONLY/REQUIRED state. A group containing a HIDDEN field alongside differing non-HIDDEN access values among its other fields is logged as an inconsistency at ERROR level (group name and item ids only, no member data), mirroring D-19's handling of READONLY/REQUIRED disagreement.

Implementation note (script 040): FieldGroupAccessNormalizer integrates at the fieldStates level in PersonalInformationWebMapper, which is after HIDDEN fields have already been filtered out by applyApimConfigItems / applyLegacyApimConfig. As a result, the HIDDEN-field-vs-non-HIDDEN-disagreement detection described above is NOT implemented -- the normalizer never observes a HIDDEN field's existence within a group. READONLY/REQUIRED disagreement among visible (non-HIDDEN) fields is fully implemented and logged at ERROR per D-19. This is tracked as a known, flagged limitation, not a silent gap; closing it would require restructuring the two config-resolution paths to normalize before filtering HIDDEN fields, which was judged out of proportion to this sub-task's scope.

## 5. Session cache (D-11 to D-13)

- Mechanism: `AuthenticatedSession.featureState` (generic slot added by Fund Comparison M5.1) through
  `AuthenticatedSessionConcurrencyService.updateIfUsable`; the revision-checked write already exists.
- Key: `personal-information.config`. Value: JSON `{accountRef, policyNo, itemConfig: {itemId: configValue}}`.
- Read: plain session load and unprotect (no touch; the portal access web filter already touches the session).
  Absent, unreadable, unusable session or account mismatch is a miss, never an error.
- Write: replaces only this entry; a conflict, missing or unusable session is logged and skipped.
- Logs carry the request id and a reason only; `policyNo` and `accountRef` are never logged.
- The service mirrors `FundComparisonOptionsCacheService` (framework-free application class, wired in a config class).
- Retrieval path used by PUT on a miss: the existing `ApimMemberInfoPort` (`TRPGetMemberInfo`); member data is
  discarded, only the config map is kept.

## 6. Downstream changes (D-03)

| Class | Change |
|---|---|
| `GetMemberInfoApimRequest` | remove the `env` body field |
| `UpdateMemberInfoApimRequest` | remove the `env` body field |
| `UpdateMemberInfoRequest` | deleted: the record had no references (removed in M1.5) |
| `ApimMemberInfoAdapter`, `ApimUpdatePersonalInformationAdapter` | stop populating `.accountEnv(...)` in the body; routing and credential context unchanged |

The adapter's response matching on `policy-no`, `cert-no`, `env` stays (D-02).

## 7. Existing tests affected (to be confirmed per sub-task)

- Semantics rewrite: `PersonalInformationFieldPresenceValidationSemanticsGuardTest`.
- Validator: `PersonalInformationUpdateYamlValidatorTest`, `...PhoneEmailTest` (blank email, JP email variant),
  `...AddressTest`, `UpdatePersonalInformationYamlValidation*Test`.
- YAML guards: `PersonalInformationPhoneEmailValidationYamlTest`, `PersonalInformationAddressValidationYamlTest`,
  `PersonalInformationPatternValidationYamlRuntimeGuardTest`, `ValidationDslBindingAndLinterTest`,
  `PersonalInformationDuplicateValidationRuleContractTest`, `PersonalInformationRequiredRuleRuntimeStateContractTest`.
- GET contract: `PersonalInformationControllerTest`, `PersonalInformationControllerContractTest`,
  `PersonalInformationWebMapperTest`, `PersonalInformationWebMapperTypedResponseTest`,
  `PersonalInformationResponseSerializationContractTest`, `PersonalInformationGetValidationResponseBoundaryGuardTest`.
- Env removal: `UpdateMemberInfoApimRequestTest`, `ApimPersonalInformationAdapterTest`,
  `ApimUpdatePersonalInformationAdapterTest`, `FetchMemberInfoCommandTest`.
- Use cases: `UpdatePersonalInformationServiceTest`, `GetPersonalInformationServiceTest`.
- **Superseded by ST-4:** the legacy PUT result-array guards below are intentionally replaced by structured
  selected-account/successful-OA/failed-OA contract guards:
  `PersonalInformationUpdateResultArrayContractTest`,
  `PersonalInformationUpdateResultArrayArchitectureGuardTest`, `UpdatePersonalInformationControllerResultArrayArchitectureGuardTest`.

## 8. Risks

- The FE contract for GET changes shape (accepted, D-17).
- Email blank-required error code changes from `personalInformation.email.required` to the generic
  `personalInformation.field.required` (config-driven). FE message mapping must follow.
- If fields of one group have different access values, the group rule only requires the editable ones (4.2).
- `overseasPhoneExtension` (U-1) and the BRD section 9 deviation await stakeholder confirmation.

## Amendment 2026-10-22 — requirement change

Trigger: new requirements received on 2026-10-22 at HEAD d8be3562 (M3.1 and M3.1b committed, M3.2-3.5 not started).
FE sends only the changed fields. PIN (step-up) verification is out of scope (separate branch
feature/step-up-pin-verification). Label removal is out of scope (separate branch; D-15 unchanged).

### A.1 Decisions added

| ID | Decision | Source |
|---|---|---|
| D-25 | Required comes from the downstream config. The enum is exactly EDITABLE_COM, EDITABLE_OPTION, READONLY, HIDDEN; Java compares exact values (no suffix matching). EDITABLE_COM => GET `required: true`; PUT rejects a submitted blank value (personalInformation.field.required). An absent key is never checked (partial update). | Nick |
| D-25a | Line-1 exception: residentialAddressLine1 (addr1) and mailingAddressLine1 (corr-addr1) carry a YAML `requiredOverride: false`. The downstream config always returns both as EDITABLE_COM because its database cannot express the cross-address rule. GET returns `required: false` for them; PUT accepts a blank line 1. If the downstream config ever expresses the rule, the override is deleted (YAML only). | Nick |
| D-26 | An address exists when line 1 is non-blank. Line 2 or line 3 alone is accepted (matches the downstream). Line 1 minLength 5 applies only when submitted non-blank (D-09). | Nick / downstream behaviour |
| D-27 | The group presence rule and the "send whole groups" FE contract are removed. FE sends only changed fields. | Nick |
| D-28 | "At least one address has line 1" is enforced by the downstream (it requires line 1 when the other address's line 1 is empty). Java evaluates address.atLeastOneRequired only when both line-1 keys are submitted blank; its groups are narrowed to the line-1 field of each address. A downstream rejection maps to a code as today. The rule stays in the GET response for FE. | Nick |
| D-29 | The country-depends-on-address rule is not confirmed (U-2) and is not applied: validateAddressGroupCountryRule is deleted from Java, and the YAML rules residentialAddress.completeWhenStarted, mailingAddress.completeWhenStarted, residentialCountry.requiredWhenResidentialAddressCaptured and mailingCountry.requiredWhenMailingAddressCaptured are deleted. Re-adding them later is YAML only. Country is still required when its config is EDITABLE_COM (D-25). | Nick |
| D-30 | Group-wide access normalization is removed: FieldGroupAccessNormalizer, the fieldGroups YAML, its binding and linter checks, and the fieldGroups/atLeastOneRequired sync guard. Access is resolved per field. | Nick (Q1) |
| D-31 | Conditional-required rules stay in YAML (e.g. phone country required only when the phone number has a value) and are evaluated over submitted fields only: a rule fails only when its trigger is submitted non-blank AND its target is submitted blank. If the target key is absent, Java treats the stored value as unchanged and skips the rule; the downstream decides. | Nick (Q2) |
| D-32 | A config value outside the four enum values is logged at ERROR (item id and value only) and treated as HIDDEN: the GET omits the field and a PUT containing it is rejected with personalInformation.field.notEditable. | Nick (option B) |
| D-32a | A null or blank access config value is logged at ERROR (item id and value only) and treated as HIDDEN: the GET omits the field and a PUT containing it is rejected with personalInformation.field.notEditable. | Nick |

### A.2 Decisions superseded

| ID | Status |
|---|---|
| D-05 | Superseded by D-27 (no group presence rule). |
| D-06, D-07 | Superseded by D-30 for fieldGroups. YAML stays the single source of rules (no hard-coded field lists in Java). |
| D-08, section 4.3 | Superseded by D-26, D-28, D-29. |
| D-10 | Superseded by D-25 / D-25a (address fields: only line 1 is exempt). |
| D-19, D-24, section 4.6 | Superseded by D-30. |
| D-20 | Superseded by D-27. |
| D-21 | Superseded by D-30 (fieldGroups YAML removed; sync guard removed). |
| D-23 | Its group/country parts (validateFieldGroups, validateAddressGroupCountryRule, resolveGroupMessage, groupSubmitted/groupIsEmpty) are deleted, not covered. |
| BRD section 9 deviation | Withdrawn: line 1 defines an address, as the BRD says. |

D-11 to D-13 (session-cached config) and D-14 stay.

### A.3 PUT validation (replaces 4.1-4.5 where they differ)

1. Resolve per-field access (session cache, else one TRPGetMemberInfo, D-13).
2. For each submitted key: HIDDEN/READONLY (an unknown value, D-32, or a null/blank value, D-32a, is treated as HIDDEN) => personalInformation.field.notEditable;
   EDITABLE_COM without requiredOverride and blank => personalInformation.field.required.
3. Format rules on non-blank submitted values (D-09).
4. Conditional-required rules per D-31; address.atLeastOneRequired per D-28.
5. Normalise null to "" and send only the submitted fields to TRPUpdMemberInfo.

### A.4 Open items

- U-1 overseasPhoneExtension: now only affects conditional-required rules (no groups).
- U-2 country-depends-on-address: unconfirmed; not applied (D-29).
- Flag: the downstream config marks both addr1 and corr-addr1 as EDITABLE_COM; handled by D-25a until the downstream config can express the rule.

## As-built 2026-10-23 (sub-task 3.5)

Status: Milestone 3 sub-tasks 3.1 to 3.4c are committed on refactor/personal-information (HEAD 9fcd0317 when this
section was written). This section records the implemented behaviour. Where it differs from the main text or from the
amendment of 2026-10-22, this section wins. Gate results and the work after 3.5 are recorded in B.7.

### B.1 Sub-task record

| Sub-task | Commit | Result |
|---|---|---|
| 3.1 | 4dd81951 | FieldAccessResolver extracted (characterization tests 8ccbc175 and bf7adf8b came first) |
| 3.1b | d8be3562 | FieldGroupAccessNormalizer added (D-19); removed again by 3.1c |
| 3.1c | 2002b16e | Group normalization, the fieldGroups YAML, the group presence check and the Java country rule removed (D-27, D-29, D-30) |
| 3.1d | a162568b | address.atLeastOneRequired narrowed to line 1 and evaluated only when both line-1 keys are submitted blank; the four country rules removed from the YAML (D-26, D-28, D-29) |
| 3.1e | ec75a744 | conditionalRequired rules evaluated over submitted fields only (D-31) |
| 3.1f | 05829af4 | requiredOverride false on the two line-1 fields (D-25a); an unrecognised, null or blank access value is HIDDEN and logged at ERROR (D-32, D-32a) |
| 3.2 | c4f72153 | session cache entry and service, featureState key personal-information.config (D-11, D-12) |
| 3.3 | bbb6bea4 | GET writes the config to the session; a null config value becomes blank |
| 3.4a | e053f1a3 | PersonalInformationFieldAccessValidator (D-13, D-14) |
| 3.4b | 72a03537 | PersonalInformationAccessConfigService; the validator ignores UI-only fields |
| 3.4c | 9fcd0317 | PUT controller wiring through PersonalInformationFieldAccessChecker; the static email.required rule removed |
| 3.5 | 5b669721 | documentation sync |

The plan delivered 3.4 as the three sub-tasks 3.4a, 3.4b and 3.4c.

### B.2 GET as built

- The controller reads the session cookie and the request id, requires Account-Ref, and calls
  GetPersonalInformationUseCase.executeAndCache(command, sessionId, accountRef, requestId). The command does not carry
  accountRef (GetPersonalInformationCommandBoundaryGuardTest).
- GetPersonalInformationCachingUseCase wraps GetPersonalInformationService. After a successful fetch it writes
  PersonalInformationConfigCacheEntry (accountRef, policyNo, item id to config value) through
  PersonalInformationConfigCacheService. A missing session, an empty config, an invalid entry or a failed write is logged
  with a reason and skipped: the GET never fails because of the cache. Each GET replaces the entry of the session.
- GetPersonalInformationService.extractConfig turns a null config value into an empty string (it used to become the text
  "null").
- Field access is resolved per field by the shared FieldAccessResolver: HIDDEN is omitted; READONLY is readonly true;
  EDITABLE_COM is required true unless the field has requiredOverride false; EDITABLE_OPTION is neither. A non-blank
  value outside these four, and a null or blank value, are treated as HIDDEN and logged at ERROR with item id and value.
- requiredOverride false is set in the YAML on residentialAddressLine1 and mailingAddressLine1 only.

### B.3 PUT as built

1. The controller resolves the portal access context and requires Account-Ref. A blank Account-Ref is rejected with
   PortalAccessContextResolutionException, as in the GET.
2. The YAML validator checks the submitted fields: format rules on non-blank values (D-09), conditional-required rules
   (D-31) and address.atLeastOneRequired (D-28).
3. PersonalInformationFieldAccessChecker always runs, also when step 2 found errors, so one response lists every error.
   A request without submitted fields triggers no lookup.
   - The config comes from PersonalInformationAccessConfigService.resolve: the session cache for this account and policy.
     On a miss, or without a session cookie, there is one TRPGetMemberInfo call through the caching GET use case, which
     also refreshes the cache.
   - PersonalInformationFieldAccessValidator checks each submitted field that has both an apimBinding.data and an
     apimBinding.config in the page YAML. HIDDEN, READONLY, a blank or unrecognised config value, or an item id absent
     from the config gives personalInformation.field.notEditable. EDITABLE_COM (unless requiredOverride is false) with a
     blank value gives personalInformation.field.required. EDITABLE_OPTION, or EDITABLE_COM with a value, is accepted.
     UI-only fields (a config binding but no data binding, for example applyToAllMemberAccounts), fields without a config
     binding and keys unknown to the YAML are ignored by this check.
   - A rejection decided from a cached entry triggers one more TRPGetMemberInfo call and a revalidation (D-13), so a stale
     cache cannot block a member. A rejection decided from a freshly fetched config is final.
4. The YAML errors and the access errors are merged (YAML first, duplicates dropped). If any error remains, the controller
   returns MutationResponse.failure(VALIDATION_FAILED, errors) and nothing is mapped or executed. The HTTP status of
   that response is 400 (step 6).
5. Otherwise requestMapper.toCommand, the update use case and the response mapper run as before (D-02 unchanged).
6. The HTTP status is set from the body status before the response is returned (PersonalInformationController
   withHttpStatus, through HttpStatusClassification.classify): VALIDATION_FAILED is 400, BUSINESS_REJECTED is 422 and
   SYSTEM_ERROR is 503; UPDATED, PARTIAL_SUCCESS and the other success-like statuses stay 200. The downstream always
   answers HTTP 200, so the failure bodies that PersonalInformationUpdateResponseMapper builds from downstream results
   get the same treatment. The body is unchanged, including the per-account result array (O-2).
7. A failed TRPGetMemberInfo lookup (cache miss, no session cookie, or the refetch of step 3) is mapped by
   PersonalInformationAccessConfigService to PersonalInformationRetrievalException. ApiExceptionHandler returns it as
   HTTP 503 with status SYSTEM_ERROR, type SYSTEM and error code err.personal-information.retrieval.unavailable, with no
   message and no downstream detail (O-1).

### B.4 Error codes

| Code | When | Notes |
|---|---|---|
| personalInformation.field.notEditable | a submitted field is HIDDEN, READONLY, blank, unrecognised or absent in the config | code and field target only, message null |
| personalInformation.field.required | an EDITABLE_COM field is submitted blank (not for the two line-1 fields) | code and field target only, message null |
| personalInformation.emailAddress.required | **Superseded by Milestone 4:** downstream `REQUIRED` maps to `personalInformation.field.required`; message text is removed and FE owns the text. | Historical Milestone 3 mapping; do not use for current PUT |
| err.personal-information.retrieval.unavailable | TRPGetMemberInfo cannot be fetched on a PUT cache miss, without a session cookie, or on the refetch | HTTP 503, status SYSTEM_ERROR, type SYSTEM, code only, message null (O-1) |

The static YAML rule email.required (code personalInformation.email.required) no longer exists.

### B.5 Deviations from the design text

None open. Both deviations recorded in the first version of this section were resolved by decisions of 2026-10-23.

1. D-13 and the 503 row of section 3.2 (decision O-1): a failed TRPGetMemberInfo lookup on the PUT returns HTTP 503
   with status SYSTEM_ERROR and error code err.personal-information.retrieval.unavailable, as designed. Implemented in
   commit 3f5a61f8: PersonalInformationRetrievalException, ErrorCodes.PERSONAL_INFORMATION_RETRIEVAL_UNAVAILABLE, one
   ApiExceptionHandler method, and the mapping in PersonalInformationAccessConfigService. When this branch meets
   refactor/exception-handling, the handler method becomes one mapping for PersonalInformationRetrievalException there.
2. D-14 and the section 3.2 table (decision O-2, widened): the downstream always answers HTTP 200, so every failure of
   the PUT is given a non-200 status derived from the body status through HttpStatusClassification: VALIDATION_FAILED
   400, BUSINESS_REJECTED 422, SYSTEM_ERROR 503; PARTIAL_SUCCESS and success stay 200. D-14 named 400 for the rejections
   the controller builds itself; as built the rule also covers the failure bodies built from downstream results. The
   body is unchanged. Implemented in commit d1cd529e: PersonalInformationController.withHttpStatus. The return type
   stays Mono<MutationResponse<List<...>>>, which UpdatePersonalInformationControllerResultArrayArchitectureGuardTest
   pins.

### B.6 Open items

- O-1 Closed 2026-10-23: a failed TRPGetMemberInfo lookup on the PUT returns HTTP 503 SYSTEM_ERROR (B.5, item 1).
- O-2 Closed 2026-10-23: the PUT sets the HTTP status from the body status: 400, 422 or 503; PARTIAL_SUCCESS and success
  stay 200 (B.5, item 2).
- O-3 get-personal-information-response.json is not regenerated. Known stale content: the static email.required rule, the
  two completeWhenStarted rules and the aeoiReminder rule.
- O-4 Closed by Milestone 4: a blank email uses `personalInformation.field.required` for both Java access validation
  and downstream `REQUIRED`; FE no longer handles `personalInformation.emailAddress.required` for the current PUT.
- O-5 The access check and the YAML check both run on every PUT, so a cache miss causes one TRPGetMemberInfo call even when
  the YAML check already rejects the request.
- O-6 The display text of the removed country rules is still in the page YAML until the label-removal branch.
- O-7 U-1 (overseasPhoneExtension) and U-2 (country depends on address) are unchanged: unconfirmed, not applied.
- O-8 Closed: the startup smoke test passed on 2026-10-23 at 5b669721, which proves the wiring of the cache service, the
  access config service and the checker. The later O-1 and O-2 changes add no bean.
- O-9 README.md and docs/refactor/2-controller/controller-refactoring-endpoint-inventory.md were edited for the GET shape in
  Milestone 1 and were not re-read in 3.5.
- O-10 Frontend impact of O-2: where the PUT used to answer 200 with a failure body it now answers 400, 422 or 503 with
  the same body, so the frontend has to read the body of these responses. WebTestClient tests cover the HTTP stack; the
  status was not verified against a running instance.

### B.7 After 3.5 (2026-10-23)

| Step | Commit | Result |
|---|---|---|
| Milestone 3 gate | 5b669721 | full mvn test: 6476 tests, 0 failures, 0 errors, 10 skipped; startup smoke test passed |
| SonarQube new-code fixes | 0b1abd50 | 14 issues (1 bug, 6 in assertion lambdas, 2 matchers, 2 shadowed fields, 3 other rules) fixed; a re-run after the next push is pending |
| O-1: HTTP 503 | 3f5a61f8 | a failed member-info lookup on the PUT returns 503 SYSTEM_ERROR |
| O-2: HTTP status | d1cd529e | the PUT sets 400, 422 or 503 from the body status |

Coverage review (Milestone 3 gate): 356 of 359 coverable changed lines (99.2%) and 187 of 208 changed-line branches
(89.9%) covered. The new PersonalInformationFieldAccessValidator is at 93.2% instruction and 71.0% branch coverage,
mostly defensive null guards; flagged, not blocking.

## Milestone 4 as built — PUT result, errors and HTTP contract (2026-10-27)

This section supersedes the original D-02 and D-14 status wording, the historical
Milestone 3 failure table in section 3.2, and the Milestone 3 as-built PUT result
and status descriptions in B.3-B.6. It records the implemented, approved contract.

### C.1 Selected account and response shape

- The selected account (SA) is resolved by Java from the request's `Account-Ref`.
  Internally, the account identity is policy number + certificate number +
  `accountEnv`; policy/certificate comparison normalizes leading zeros. `env`
  remains an internal/downstream matching value and is not included in the PUT
  response.
- Send one `TRPUpdMemberInfo` request. Do not loop by account in Java.
- `apply-all` is copied from FE `applyToAllAccounts`, defaulting to `false` when
  omitted. The downstream applies the update to all accounts when true. If false
  but OA rows are returned, Java logs their count and drops those rows.
- `result.data` is:
  - `selectedAccount`: the SA's `{accountIdentifier, refNo, submitDate, submitTime}`
    on SA success, otherwise `null`;
  - `rows[]`: successful other-account (OA) rows only, with the same four fields;
  - `errors[]`: failed OA rows only, each
    `{accountIdentifier, errors:[{type, code, targets}]}`.
- Root `errors[]` contains SA errors or Java content-validation errors only.
  OA errors appear only under `result.data.errors[]`; never duplicate an error.
- Public account rows contain no `success`, `selected`, `env`, `policyNo` or
  `certNo` properties. Downstream `refNo`, `submitDate` and `submitTime` pass
  through exactly, including null or blank values. These fields are optional
  and may be blank or null for any row; FE must not rely on them.
- `accountIdentifier` uses the shared formatter and `account-identifiers`
  configuration: policy number padded to width 11, certificate number padded to
  width 4, pad character `0`, joined with `-` (for example
  `00000000118-0002`).
- `policy-no` and `cert-no` remain strings end-to-end, including APIM DTOs:
  these fields are field-encrypted and must remain textual for encryption and
  decryption.
- Response message/error entries are codes only; the FE owns display text.

### C.2 HTTP and ApiStatus matrix

| Case | HTTP | ApiStatus | Public result/errors |
|---|---:|---|---|
| SA succeeds, no OA failure (SS) | 200 | `UPDATED` | Selected account and successful OA rows; root errors empty |
| SA succeeds, at least one OA fails (SF) | 200 | `PARTIAL_SUCCESS` | Selected account and successful OA rows; failed OA rows only in `result.data.errors[]` |
| SA fails, no OA failure (FS) | 422 | `VALIDATION_FAILED` | `selectedAccount:null`; SA mapped errors at root; no OA errors |
| SA fails and an OA fails (FF) | 422 | `VALIDATION_FAILED` | `selectedAccount:null`; SA mapped errors at root and OA failures in `result.data.errors[]` |
| Malformed request: missing/invalid Account-Ref | 400 | Existing Account-Ref path (`BUSINESS_REJECTED`) | Existing error mapping |
| Malformed request: invalid JSON/type, unknown FE field, no submitted/updatable fields | 400 | `VALIDATION_FAILED` | Endpoint request-invalid code; code-only errors |
| Java YAML/access content rejection | 422 | `VALIDATION_FAILED` | `result:null`; code-only root errors with FE targets |
| PUT downstream `err-message`, exhausted transport failure, missing/duplicate SA, SA `success:false` with empty errors, or invalid downstream identifiers | 503 | `SYSTEM_ERROR` | `err.personal-information.update.unavailable`; no result |
| `TRPGetMemberInfo` unavailable on cache miss/refetch (O-1) | 503 | `SYSTEM_ERROR` | `err.personal-information.retrieval.unavailable`; no result |

O-2 is therefore narrowed: malformed requests retain HTTP 400, while content
rejections return 422. PUT-specific downstream protocol/outcome classification
does not change generic APIM handling or other endpoints.

### C.3 Error mapping and validation order

| Downstream code | Java public code |
|---|---|
| `REQUIRED` | `personalInformation.field.required` |
| `INVALID_FORMAT` | The single shared YAML validation code ending in `.invalid` across all resolved targets; otherwise `personalInformation.field.invalidFormat` |
| `AT_LEAST_ONE_REQUIRED` | `personalInformation.address.atLeastOneRequired` |
| Unknown code | `personalInformation.update.rejected` |

- The mapped TAS codes are:

  | TAS code | Java public code |
  |---|---|
  | `MISSING_OVERSEAS_COUNTRY` | `personalInformation.overseasCountryCode.requiredWhenPhonePresent` |
  | `MISSING_OVERSEAS_PHONE` | `personalInformation.overseasPhoneNumber.requiredWhenCodePresent` |
  | `MISSING_BUSSINESS_PHONE` | `personalInformation.businessPhone.requiredWhenExtensionPresent` |
  | `INVALID_EMAIL` | `personalInformation.email.invalid` |
  | `INVALID_FAX` | `personalInformation.faxNo.invalid` |
  | `INVALID_MOBILE_NUMBER` | `personalInformation.mobilePhone.invalid` |
  | `INVALID_HOME_PHONE` | `personalInformation.homeTel.invalid` |
  | `INVALID_ADDRESS` | `personalInformation.residentialAddress.invalid` |
  | `INVALID_CORR_ADDRESS` | `personalInformation.mailingAddress.invalid` |
  | `BLOCKED_PO_ADDRESS` | `personalInformation.address.poBoxBlocked` |

- For the three `MISSING_*` codes, `targets` come from the matching YAML
  `conditionalRequired` rule's `then.targets`, resolved to FE field IDs; TAS
  `fields` are not used for those targets. For the other mapped codes,
  downstream `fields` are resolved to FE field IDs through the existing reverse
  APIM binding. An unknown code falls back to
  `personalInformation.update.rejected` and emits a WARN event
  `event=personal-information-update-unknown-downstream-code`, logging only
  the raw code after removing characters outside `[A-Za-z0-9_.-]` and
  truncating it to 64 characters.

- Split downstream `fields` on `|`, resolve each name through YAML APIM bindings,
  and return FE field IDs in `targets`. Unknown downstream field names are
  omitted from targets (an empty targets array is valid).
- A failed OA row with an empty downstream `errors[]` maps to
  `[{type:null, code:"personalInformation.update.rejected", targets:[]}]`.
- Java checks request structure (empty/missing fields, unknown FE field, wrong
  value type, no updatable fields) before YAML validation and access checking.
  Structural rejections do not trigger `TRPGetMemberInfo`.
- The hard-coded mapper email regex is removed. YAML is the single source of
  content validation rules; configured YAML email validation is applied as
  content validation and returns HTTP 422.

### C.4 Response examples

#### SS — selected account and all updated accounts succeeded (HTTP 200)

```json
{
  "success": true,
  "status": "UPDATED",
  "result": {
    "data": {
      "selectedAccount": {
        "accountIdentifier": "00000000118-0002",
        "refNo": "R-100",
        "submitDate": "2026-10-27",
        "submitTime": "13:00:00"
      },
      "rows": [],
      "errors": []
    }
  },
  "messages": [{"code": "personalInformation.update.success"}],
  "errors": []
}
```

#### SF — selected account succeeded; an OA failed (HTTP 200)

```json
{
  "success": true,
  "status": "PARTIAL_SUCCESS",
  "result": {
    "data": {
      "selectedAccount": {
        "accountIdentifier": "00000000118-0002",
        "refNo": "R-100",
        "submitDate": "2026-10-27",
        "submitTime": "13:00:00"
      },
      "rows": [
        {
          "accountIdentifier": "00000000118-0003",
          "refNo": "R-101",
          "submitDate": "2026-10-27",
          "submitTime": "13:00:01"
        }
      ],
      "errors": [
        {
          "accountIdentifier": "00000000118-0004",
          "errors": [
            {
              "type": "FIELD",
              "code": "personalInformation.email.invalid",
              "targets": ["emailAddress"]
            }
          ]
        }
      ]
    }
  },
  "messages": [{"code": "personalInformation.update.partialSuccess"}],
  "errors": []
}
```

#### FS — selected account failed; OA succeeded (HTTP 422)

```json
{
  "success": false,
  "status": "VALIDATION_FAILED",
  "result": {
    "data": {
      "selectedAccount": null,
      "rows": [
        {
          "accountIdentifier": "00000000118-0003",
          "refNo": "R-101",
          "submitDate": "2026-10-27",
          "submitTime": "13:00:01"
        }
      ],
      "errors": []
    }
  },
  "messages": [],
  "errors": [
    {
      "type": "FIELD",
      "code": "personalInformation.field.required",
      "targets": ["emailAddress"]
    }
  ]
}
```

#### FF — selected account and an OA failed (HTTP 422)

```json
{
  "success": false,
  "status": "VALIDATION_FAILED",
  "result": {
    "data": {
      "selectedAccount": null,
      "rows": [],
      "errors": [
        {
          "accountIdentifier": "00000000118-0004",
          "errors": [
            {
              "type": "FIELD",
              "code": "personalInformation.email.invalid",
              "targets": ["emailAddress"]
            }
          ]
        }
      ]
    }
  },
  "messages": [],
  "errors": [
    {
      "type": "FIELD",
      "code": "personalInformation.field.required",
      "targets": ["emailAddress"]
    }
  ]
}
```

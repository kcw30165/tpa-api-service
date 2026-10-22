# Personal Information Refactor — Design

Branch: `refactor/personal-information` (base `origin/dev` 92849aac). Owner: Nick (solution design approval).
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
central `ApiExceptionHandler` consolidation (deferred workstream); PUT response shape; runtime behaviour of
`applyToAllAccounts`; `mailingAddressSameAsResidential` and `applyToAllMemberAccounts` handling (unchanged).

## 2. Decision register

| ID | Decision | Source |
|---|---|---|
| D-01 | GET returns `MutationResponse<PersonalInformationResultResponse>`: `{success, status: SUCCESS, result: {data: {page, form}}, messages: [], errors: []}`. Mirrors `FundFactSheetResultResponse(data)`. | Nick |
| D-02 | PUT response is already `MutationResponse<List<PersonalInformationUpdateResultResponse>>` and is NOT changed: `result` stays an array, `env` stays in the items, `selected` matching and the 502 `APIM_RESPONSE_INVALID` on no selected item stay as they are. | Nick |
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
| D-14 | A config rejection is HTTP 400 `VALIDATION_FAILED` with codes and `targets` only: a submitted HIDDEN or READONLY field; a blank value for an EDITABLE_COM field. | Nick |
| D-15 | Labels: existing `messages[]` and `errors[]` label text is untouched in this branch. New errors carry codes and `targets` only. | Nick |
| D-16 | YAML is aligned with the BRD: D1 phones keep `minLength: 8` (non-blank only); D2 `mailingAddressLine1.minLength: 5` enabled (non-blank only); D3 email regex allows only letters, digits, `.`, `_`, `-`; D4 the email `minLength` rule (including the JP variant) is removed; D5 fax allows digits, space and `-` only, no maximum length in YAML (field attributes follow the TAS setting); D6 the `aeoiReminder.whenResidentialCountryChanged` rule is removed. | Nick |
| D-17 | The GET change is breaking for the frontend; the frontend is aligned. | Nick |
| D-18 | A group that is HIDDEN or READONLY is simply not sent by FE. Java never rejects a request because a group is absent. Absent group => unchanged. | Nick (Q-O) |
| D-19 | Group-wide access normalization in the shared access resolver (GET and PUT): within a field group, if any field is not READONLY then all fields of the group are treated as not READONLY; if any field is REQUIRED (EDITABLE_COM) then all fields of the group are REQUIRED. A group whose fields disagree is logged at ERROR level. Reason: the config matrix is not designed to provide a group feature; in theory all fields of a group carry the same values. | Nick |
| D-20 | FE contract for the address groups: the FE sends the entire group (4 keys) when the group is editable; when both groups are editable, any change to either group sends both groups (8 keys); a group that is not editable is not sent. Java derives the same set from the session-cached config (D-05, D-18). | Nick |
| D-21 | `fieldGroups` is Java-only YAML under `bff-pages.pages.personalInformation.fieldGroups` (name + fields) and is not serialized to the GET response. `address.atLeastOneRequired` keeps its inline groups because the FE reads them; a guard test keeps both definitions in sync. The linter checks: unique non-blank names, non-empty known fields, no field in two groups. | Nick (Q-S) |
| D-23 | Milestone 2 coverage gate (gate 024, HEAD 53ceed2a) found `PersonalInformationUpdateYamlValidator` at 85.5% instruction / 76.4% branch and `PageSchemaLinter` at 97.3% instruction / 88.9% branch, both below the 95%/95% DTO-validator tier. Of the uncovered branches, 8 lines in the validator were introduced by M2.1-M2.3 (validateFieldGroups, validateAddressGroupCountryRule, resolveGroupMessage, groupSubmitted/groupIsEmpty's null-group handling) and are closed by dedicated tests in this sub-task. The remaining uncovered branches in both files - `instantiateContext`'s reflection fallback, `fallbackVariantCandidates`/`addJoined`/`addSingle`, `isValidEmail`/`isBlockedAddress` detail branches, and `PageSchemaLinter`'s pre-existing null-guard branches - predate this branch and are explicitly OUT of scope: tracked as separate technical debt, not closed here. |  Nick (option 2) |
| D-22 | Rule groups (`when.groups[].fields`) are read through `RuleGroupFields.of(...)`, which accepts a list or an index-keyed map. Probe 011 (Spring Boot binder harness, real YAML, HEAD 081b2d9e) showed the group `fields` bound as `LinkedHashMap`, so `address.atLeastOneRequired` did not fire when all address fields were absent; the existing unit tests build the groups as lists and did not show it. The linter uses the helper in M2.1, the validator in M2.3. Consequence for the MR: after M2.3 a PUT that submits both address groups blank returns 400 `VALIDATION_FAILED` (behavior change visible to FE). | Probe 011 |
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
- Response shape unchanged (D-02). The downstream request no longer contains `env` (D-03).

Failure responses (codes and targets only, D-15):

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
- PUT result guards that must keep passing unchanged: `PersonalInformationUpdateResultArrayContractTest`,
  `PersonalInformationUpdateResultArrayArchitectureGuardTest`, `UpdatePersonalInformationControllerResultArrayArchitectureGuardTest`.

## 8. Risks

- The FE contract for GET changes shape (accepted, D-17).
- Email blank-required error code changes from `personalInformation.email.required` to the generic
  `personalInformation.field.required` (config-driven). FE message mapping must follow.
- If fields of one group have different access values, the group rule only requires the editable ones (4.2).
- `overseasPhoneExtension` (U-1) and the BRD section 9 deviation await stakeholder confirmation.

# Centralized Template Service — Implementation & Migration Plan

Companion to `template-service-solution-design.md` (the "what/why"). This
document is the "how/when": it sequences all remaining work into phases
and stages, so no stage is skipped and migration never begins before the
service it depends on is actually live.

Status key: `DONE`, `PENDING`.

---

## Phase I — Centralized Template Service Implementation (producer side)

Goal: make the Template Service itself real and operational — a live,
Spring-wired component with real content — before any workflow is asked
to depend on it.

| Stage | Work | Status |
|---|---|---|
| I-1 | Core abstractions: `NoticeTemplateDefinition` (record/DTO), `NoticeTemplateRegistryPort` (interface) | DONE (script 010) |
| I-2 | YAML-backed implementation: `NoticeTemplateProperties` (`@ConfigurationProperties`), `YamlNoticeTemplateRegistryAdapter` | DONE (script 014) — classes exist but are **not yet registered as Spring beans** |
| I-3 | APIM DTO extension: `schemeName`/`schemeNameChi` (`sch-name`/`chin-sch-name`) added to `TrpGetAccountListApimDataItem`, `ForgotPasswordTrpSearchMemberDataItem`, `TrpSearchMemberApimDataItem` (usernamerecovery) | DONE (script 018) |
| I-4 | **Spring wiring**: `NoticeTemplateServiceConfig` class with `@EnableConfigurationProperties(NoticeTemplateProperties.class)`, a constructor that calls `properties.validate()` at startup (fail-fast), and a `@Bean NoticeTemplateRegistryPort` factory method. Confirmed precedent via investigation (script 024): mirrors `FundPriceDateFormatValidationConfig` exactly (validate-in-constructor + single `@Bean` producing what the properties back), not merely `TrpGetAccountListAdapterConfig`'s more elaborate multi-bean shape. Covered by a context-loading test using Spring Boot's `ApplicationContextRunner` | DONE |
| I-5 | **Populate `application.yml`** under `notice-templates.definitions.*` with real content for all 4 message types x 2 channels x 2 locales, sourced verbatim from the BRD SMS sheet and the full literal text of the 3 email docx files, with `{ttlValue}`/`{ttlUnit}` placeholders (confirmed: keep dynamic TTL wording for SMS and email, consistent with current production; NOT applied to `username-recovery`, which the BRD defines with no OTP/expiry). Generic Chinese SMS templates for `login-otp`/`forgot-password-otp` and `username-recovery` reconstructed from the BRD's filled-in examples by substituting example values back to placeholders. **Known content gap, transcribed faithfully and flagged for business review, not altered:** the Chinese email subject for `login-otp`/`forgot-password-otp` and `username-recovery` has no trust-code placeholder in the source docx, unlike the English subject. | DONE |
| I-6 | Startup validation / smoke test: `NoticeTemplateRegistryFullMatrixSmokeTest` confirms `YamlNoticeTemplateRegistryAdapter.resolve(...)` returns correct content for all 4x2x2 combinations against the real `application.yml`, including per-type required-placeholder sets, non-blank EMAIL subjects, null SMS subjects, `login-otp`/`forgot-password-otp` byte-for-byte content synchronization (§5), and that `en`/`zh_HK` content genuinely differs (guards against a silent locale-fallback regression) | DONE |

**Gate before Phase II begins:** I-4, I-5, and I-6 are all complete and
validated. **Phase I is now closed.** The Template Service is live,
Spring-wired, populated with real content, and correctness-tested across
the full 4x2x2 matrix. Phase II (workflow migration) may now begin,
starting with Stage II-1 (Username Recovery).

---

## Phase II — Workflow Migration (consumer side)

Goal: switch each of the four workflow adapters from its own scattered
policy lookup to calling the now-live `NoticeTemplateRegistryPort`
directly, per the seam identified in the solution design (§7.2):
`RegistrationMessageDeliveryAdapter`-style classes resolve content, then
still build `MessageDeliveryCommand` and call
`MessageDeliveryPort.deliver(command)` exactly as today.

| Stage | Workflow | Notes | Status |
|---|---|---|---|
| II-1 | Username Recovery | Simplest — single call site in `UsernameRecoveryFeatureConfig` / `UsernameRecoveryRequestProtectedOperation`. First to confirm whether `schemeName`/`schemeNameChi` already flow from the DTO through `TrpSearchMemberApimResultMapper` → `UsernameRecoveryLookupCandidate`, or need additive plumbing. | IN PROGRESS |
| II-2 | Registration | Replace `RegistrationMessageDeliveryAdapter`'s `subject()`/`body()` policy lookup with `NoticeTemplateRegistryPort.resolve(...)`. No scheme-name placeholder needed (§8.2). | PENDING |
| II-3 | Login | Replace `LoginOtpSendPolicy`-based lookup. Requires scheme-name plumbing from `TrpGetAccountListApimDataItem`. | PENDING |
| II-4 | Forgot Password | **Last** — highest risk, since it currently has no live YAML wiring at all. Reconcile content to match `login-otp` verbatim (§5). Requires scheme-name plumbing from `ForgotPasswordTrpSearchMemberDataItem`. | PENDING |

Each stage follows the established investigate → implement → validate
pattern: confirm full current source of the call site before any edit,
implement with full test coverage per the tiered policy, validate with
`mvn -DskipTests compile` + `test-compile` (full `mvn test` + app-startup
smoke test before each stage transition, per standing convention), commit.

---

## Phase III — Cleanup (only after all four workflows are migrated)

| Stage | Work | Status |
|---|---|---|
| III-1 | Remove the four old scattered config blocks (`auth-workflows.registration.otp.*`, `auth-workflows.login.otp.*`, `auth-workflows.username-recovery.template.*`, and the orphaned `auth-workflows.forgot-password.otp.localized-template.*` node) | PENDING |
| III-2 | Remove `ForgotPasswordOtpTemplates` Java class | PENDING |
| III-3 | Remove the duplicated `ttlUnit()` helpers in `ForgotPasswordDetailsOtpProtectedOperation` / `ForgotPasswordOtpResendProtectedOperation` | PENDING |

Cleanup is deliberately deferred until **all four** workflows are
migrated — not performed per-workflow — so that no workflow is left
depending on a config block that has already been removed.

---

## Current Position

Phase I (stages I-1 through I-6) is complete and closed. The next piece
of work is **Phase II, Stage II-1** (Username Recovery migration) -
investigate the full current send path first (per the established
investigate-before-implement pattern), before changing any production
code in that workflow.

## Appendix A: Stage II-1 Sub-Plan (Username Recovery Migration Detail)

Investigation (scripts 031–033) traced the full current send path and
confirmed the following before any implementation:

- **Gap confirmed:** `schemeName`/`schemeNameChi` (added to the three APIM
  DTOs in Phase I, stage I-3) do **not** yet flow through
  `TrpSearchMemberApimResultMapper` into `UsernameRecoveryLookupCandidate`,
  `UsernameRecoveryCandidate` (domain), or `UsernameRecoverySessionCandidate`.
  Additive plumbing is required end-to-end.
- **Confirmed available:** `trustCode` already survives the full chain
  (`UsernameRecoveryCandidate.trustCode()` is accessible at
  `buildDeliveryCommand()` time). `context.language()` is a required,
  non-null field populated once at session-start time from
  `command.language()`, reliably present for every session.
- **Confirmed behavior change (user-approved):** today,
  `UsernameRecoveryFeatureConfig` wires `emailSubject`/`emailBody`/
  `smsBody` as fixed strings **once at Spring startup** from
  `AuthWorkflowProperties.UsernameRecovery.getTemplate()` (a single,
  locale-agnostic YAML value). This migration changes that to **per-request
  resolution** via `NoticeTemplateRegistryPort.resolve(USERNAME_RECOVERY,
  channel, locale)`, using the member's actual `context.language()` at
  request time. This is an intended improvement (matches the BRD's
  per-locale requirement), not an unapproved scope expansion.

Because this migration spans three layers (APIM DTO → domain model →
feature wiring → request-time resolution), it is split into four
sub-stages, each independently compiled, tested, and committed:

| Sub-stage | Change | Files touched |
|---|---|---|
| II-1.a | Add `schemeName`/`schemeNameChi` to `UsernameRecoveryLookupCandidate` (DTO) and `UsernameRecoveryCandidate` (domain), both via compatibility constructors so existing call sites are untouched. Wire `TrpSearchMemberApimResultMapper` to read `item.schemeName()`/`item.schemeNameChi()` from the APIM DTO and populate the new fields. | `UsernameRecoveryLookupCandidate.java`, `UsernameRecoveryCandidate.java`, `TrpSearchMemberApimResultMapper.java` + tests |
| II-1.b | Add `schemeName()`/`schemeNameChi()` accessor methods to `UsernameRecoverySessionCandidate` (delegating to the wrapped `candidate`). Thread the new fields through `UsernameRecoveryStartProtectedOperation.createRevisioned()`'s candidate construction so they survive into the session. **Known landmine to fix in this sub-stage:** `UsernameRecoverySessionCandidate.withState()` currently rebuilds its wrapped `UsernameRecoveryCandidate` via the old 6-arg constructor; once that becomes a compatibility constructor (II-1.a), every state transition (reserve/markRequested/markFailed/markUnknownOutcome) would silently null out `schemeName`/`schemeNameChi`. `withState()` must be updated to use the canonical 8-arg constructor, carrying forward `candidate.schemeName()`/`candidate.schemeNameChi()`. | `UsernameRecoverySessionCandidate.java`, `UsernameRecoveryStartProtectedOperation.java` + tests |
| II-1.c | Replace `UsernameRecoveryFeatureConfig`'s static-string constructor wiring with injecting `NoticeTemplateRegistryPort` directly. Update `UsernameRecoveryRequestProtectedOperation`'s constructor signature accordingly (drops `emailSubject`/`emailBody`/`smsBody` parameters, adds the port). | `UsernameRecoveryFeatureConfig.java`, `UsernameRecoveryRequestProtectedOperation.java` + tests |
| II-1.d | Update `UsernameRecoveryRequestProtectedOperation.buildDeliveryCommand()` to resolve content per request via `NoticeTemplateRegistryPort.resolve(MessageDeliveryType.USERNAME_RECOVERY, channel, context.language().toLanguageTag())`, apply the confirmed business rule (empty `schemeNameChi` falls back to `schemeName`), and populate the `MessageDeliveryCommand` variables map with `username`, `trustCode`, `schemeName`/`schemeNameChi`. | `UsernameRecoveryRequestProtectedOperation.java` + tests |

**Cleanup note:** the old `auth-workflows.username-recovery.template.*`
YAML block remains in place but unused after II-1.c/d completes — per the
Phase III gate, removal is deferred until **all four** workflows are
migrated, not performed per-workflow.

Each sub-stage follows: confirm no further assumptions needed → implement
→ `mvn -DskipTests compile` + `test-compile` → scoped `mvn test` for the
affected classes → commit → proceed to the next sub-stage.

## Appendix A.1: Stage II-1 Progress and Gate Status

| Sub-stage | Status | Reference |
|---|---|---|
| II-1.a schemeName/schemeNameChi through lookup candidate and domain candidate | DONE | b3c1289c |
| II-1.b session candidate accessors, withState() field-loss fix, StartProtectedOperation threading | DONE | dd1aa4fd |
| II-1.b addendum: protected session context serializer | DONE | 8889c0a1 |
| II-1.c NoticeTemplateRegistryPort injected into UsernameRecoveryFeatureConfig | DONE | 8889c0a1 |
| II-1.d per-request resolution in UsernameRecoveryRequestProtectedOperation | DONE | 8889c0a1 |

**Finding added during implementation (not in the original sub-plan).**
`AesGcmUsernameRecoveryAuthFlowContextProtectorAdapter` keeps a private `Candidate` payload
record that serializes fields explicitly and rebuilt the domain candidate through the 6-argument
compatibility constructor. `schemeName`/`schemeNameChi` were therefore dropped whenever the session
context was protected after START and unprotected at REQUEST, so REQUEST never saw them. The
payload now carries both fields and a round-trip test guards it. Any future field added to
`UsernameRecoveryCandidate` must also be added to that payload.

**Carry into II-3 (Login) and II-4 (Forgot Password).** Both workflows keep protected session
contexts. Before changing their send paths, read their context serializers in full and confirm
whether they serialize candidate or account fields explicitly. `schemeName`/`schemeNameChi` must
survive that round trip, the same way they now do for Username Recovery.

**Test-scope finding.** Script 034 changed `UsernameRecoveryCandidate.toString()` to list the new
fields and ran only a hand-picked test list, which missed two existing exact-string guards
(`UsernameRecoveryCandidateTest`, `UsernameRecoverySessionCandidateTest`). The wide pattern run
in script 044 caught them. `toString()` now omits `schemeName`/`schemeNameChi` again, so the
redaction format those tests pin is unchanged and the values are still never disclosed.
Scoped runs for this workstream use wildcard patterns, not named lists.

**Known behaviour, decision pending.** EMAIL templates for `username-recovery` require
`{schemeName}`. If the APIM response has a blank or absent `sch-name`, rendering throws and
`MessageDeliveryAdapter` reports FAILED for that email. SMS content has no scheme placeholder and
is unaffected. The business rule implemented is only: blank `chin-sch-name` falls back to `sch-name`.

**Language finding (scripts 046-049).** Username Recovery START builds `language` with
`Locale.forLanguageTag(Accept-Language)` and REQUEST selects the template from
`context.language().toLanguageTag()`. The registry matched only the exact tag `zh_HK` (after `-`
to `_`), so `zh`, `zh-TW`, `zh-Hant` and similar fell back to English content, and the returned
definition reported the unmatched tag rather than the key used. The shared request pipeline treats
every language starting with `zh` as Traditional Chinese (`RequestHeaderContext`). The registry now
selects from the configured keys (exact, then same language, then `en`) and reports the selected
key; see solution design section 8.4. Fixed in `b9012557`.
Not changed: `UsernameRecoveryController.resolveLanguage` still builds the `Locale` from the raw
header, so a multi-value header becomes `und` and selects English. Carry into II-2 to II-4: pass a
single tag, and read each workflow's language source before changing its send path.

**Stage gate (not yet satisfied, so II-1 is not marked DONE).** The standing convention requires,
before a stage transition, evidence that the full Maven test suite passed and that an application
startup smoke test confirmed the Spring application starts without bean or configuration errors.
Both are run manually. Stage II-1 becomes DONE only after that evidence exists.

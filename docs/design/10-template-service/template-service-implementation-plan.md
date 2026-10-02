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

| Stage | Workflow | Notes |
|---|---|---|
| II-1 | Username Recovery | Simplest — single call site in `UsernameRecoveryFeatureConfig` / `UsernameRecoveryRequestProtectedOperation`. First to confirm whether `schemeName`/`schemeNameChi` already flow from the DTO through `TrpSearchMemberApimResultMapper` → `UsernameRecoveryLookupCandidate`, or need additive plumbing. |
| II-2 | Registration | Replace `RegistrationMessageDeliveryAdapter`'s `subject()`/`body()` policy lookup with `NoticeTemplateRegistryPort.resolve(...)`. No scheme-name placeholder needed (§8.2). |
| II-3 | Login | Replace `LoginOtpSendPolicy`-based lookup. Requires scheme-name plumbing from `TrpGetAccountListApimDataItem`. |
| II-4 | Forgot Password | **Last** — highest risk, since it currently has no live YAML wiring at all. Reconcile content to match `login-otp` verbatim (§5). Requires scheme-name plumbing from `ForgotPasswordTrpSearchMemberDataItem`. |

Each stage follows the established investigate → implement → validate
pattern: confirm full current source of the call site before any edit,
implement with full test coverage per the tiered policy, validate with
`mvn -DskipTests compile` + `test-compile` (full `mvn test` + app-startup
smoke test before each stage transition, per standing convention), commit.

---

## Phase III — Cleanup (only after all four workflows are migrated)

| Stage | Work |
|---|---|
| III-1 | Remove the four old scattered config blocks (`auth-workflows.registration.otp.*`, `auth-workflows.login.otp.*`, `auth-workflows.username-recovery.template.*`, and the orphaned `auth-workflows.forgot-password.otp.localized-template.*` node) |
| III-2 | Remove `ForgotPasswordOtpTemplates` Java class |
| III-3 | Remove the duplicated `ttlUnit()` helpers in `ForgotPasswordDetailsOtpProtectedOperation` / `ForgotPasswordOtpResendProtectedOperation` |

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

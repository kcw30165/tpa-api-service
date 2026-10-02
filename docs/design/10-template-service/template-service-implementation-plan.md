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
| I-4 | **Spring wiring**: new `NoticeTemplateServiceConfig` class with `@EnableConfigurationProperties(NoticeTemplateProperties.class)` and a `@Bean NoticeTemplateRegistryPort` factory method, mirroring the existing `TrpGetAccountListAdapterConfig` precedent (`@Configuration` + `@EnableConfigurationProperties` + `@Bean` per dependency) | PENDING |
| I-5 | **Populate `application.yml`** under `notice-templates.definitions.*` with real, approved content for all 4 message types (`registration-otp`, `login-otp`, `forgot-password-otp`, `username-recovery`) × 2 channels (sms, email) × 2 locales (en, zh_HK), using the BRD SMS sheet and the 3 email docx bodies as source, with `{ttlValue}`/`{ttlUnit}` placeholders per the resolved TTL decision | PENDING — blocked on final business sign-off of exact literal copy for cells not yet quoted verbatim in the design doc |
| I-6 | Startup validation / smoke test: confirm `NoticeTemplateRegistryPort.resolve(...)` returns a correct `NoticeTemplateDefinition` for all 4×2×2 combinations once the app context loads, and that `NoticeTemplateProperties.validate()` fails fast on any gap | PENDING |

**Gate before Phase II begins:** I-4, I-5, and I-6 must all be complete
and validated. No workflow migration starts until the service is
confirmed live end-to-end.

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

As of this document, Phase I stages I-1 through I-3 are complete. The
next piece of work is **Phase I, Stage I-4** (Spring wiring), not any
Phase II migration stage.

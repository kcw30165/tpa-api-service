# Centralized Template Service — Solution Design (Draft v0.1)

Status: DRAFT — pending sign-off on open decisions in §11
Scope: SMS + Email channels only. Designed to be extensible to future
message-transport channels (WhatsApp, WeChat, etc.) without reworking
workflow/use-case logic.
Out of scope: In-app Notice Board (BRD §6.1 / 7 message types with
trigger + expiry fields) — structurally distinct channel, not covered here.

---

## 1. Purpose

`api-service` currently sends notification content (OTP codes, username
recovery messages, etc.) for Registration, Login, Forgot Password, and
Username Recovery from four independent, non-uniform places. This document
defines a centralized Template Service so that:

- All notice content (subject/body per channel/locale) is defined once,
  keyed by a notice code.
- Workflow use-cases stop embedding literal template strings and instead
  ask the Template Service to render content for `(noticeCode, channel,
  locale, variables)`.
- Adding a new channel (WhatsApp, WeChat, ...) in the future requires
  adding a sender adapter and template content — not touching the four
  workflow use-cases.

## 2. Source of Truth

- BRD: `BRD for TPA ORSO Web Revamp_Content Management.xlsx`, sheets
  `SMS` and `Email`.
- Email template docs: `E0001_Registration_OTP.docx`,
  `E0002_Login_ForgotPassword_OTP.docx`, `E0003_UsernameRecovery_Email.docx`.
- Code investigation evidence: scripts 001–003 (branch
  `chore/centralize-notification-template-investigation`,
  baseline `dev`@`632dd2150055fce00244d3f401854e42ef24215d`).

## 3. BRD Notice Inventory

| Notice ID | Function | Channels | Dynamic fields | OTP / Expiry |
|---|---|---|---|---|
| E0001 / S0001 | AccountRegistration | Email, SMS | none (static copy) | OTP, 5 min expiry |
| E0002 / S0002 | Login_ForgotPassword | Email, SMS | `<trust code>`, `<Scheme Name>` (email only) | OTP, 5 min expiry |
| E0003 / S0003 | UsernameRecovery | Email, SMS | `<trust code>`, `<Scheme Name>`, `<Username>` | none |

The BRD treats **Login and Forgot Password as the same notice** (one
template, S0002/E0002), differentiated only by trust code, not by
workflow origin.

## 4. Current-State Findings (as-is wiring)

| Workflow | Template literal location | Config-driven? | Class(es) |
|---|---|---|---|
| Registration | `AuthWorkflowProperties.Registration.Otp` (`sms-template`, `email-template.subject/body`) | Yes — YAML under `auth-workflows.registration.otp.*` | `OtpPolicy`, `OtpEmailPolicy`, `RegistrationAuthWorkflowPolicy`, consumed by `RegistrationMessageDeliveryAdapter` |
| Login | `AuthWorkflowProperties.Login.LoginOtp` (`sms-template`, `email-template.subject/body`) | Yes — YAML under `auth-workflows.login.otp.*` | `LoginOtpSendPolicy` (validates `{code}` placeholder at construction), consumed by `LoginOtpSendService` / `LoginOtpResendService` |
| Forgot Password | **Hardcoded Java static factory**, not YAML | No — `ForgotPasswordOtpTemplates.approved()` returns literal EN/ZH strings; the YAML validation path (`validateForgotPasswordOtpPolicy`) has its sms/email-template checks **commented out**, confirming no live YAML wiring for this workflow's content | `ForgotPasswordOtpTemplates`, consumed by `ForgotPasswordDetailsOtpProtectedOperation` and `ForgotPasswordOtpResendProtectedOperation` |
| Username Recovery | `AuthWorkflowProperties.UsernameRecovery.UsernameRecoveryTemplate` (`email-subject`, `email-body`, `sms-body`) | Yes — YAML under `auth-workflows.username-recovery.template.*`, with placeholder validation (`{username}` required) | `UsernameRecoveryTemplate`, injected as raw strings into `UsernameRecoveryRequestProtectedOperation` via `UsernameRecoveryFeatureConfig` |

Additional duplication found: the `ttlUnit(String language)` helper
(translating "minutes" ↔ "分鐘") is implemented **twice**, identically, in
`ForgotPasswordDetailsOtpProtectedOperation` and
`ForgotPasswordOtpResendProtectedOperation`. This is exactly the kind of
scattering a central service should eliminate.

## 5. Resolved Decision: Login vs. Forgot Password Templates

The BRD defines **one** notice (S0002/E0002, "Login_ForgotPassword") for
both the Login OTP and Forgot Password OTP flows, while the current code
implements them as two independently configured templates with
**different wording** (Login: *"Your BCT login verification code is
{code}..."*; Forgot Password: *"Your BCT forgot password verification
code is {code}..."*).

**Decision (confirmed):** `LOGIN_OTP` and `FORGOT_PASSWORD_OTP` remain
**two separate notice codes with two separate YAML config entries** —
they are not merged into a single config key, since they originate from
different workflows and may need to diverge independently in the future.
However, **their content (subject/body text, per channel/locale) must be
synchronized to be identical**, reconciling the current wording
discrepancy. In practice this means both config entries are populated
with the same literal template strings at rollout, and any future content
change to one must be mirrored to the other unless a future BRD update
explicitly splits them.

## 6. Existing Reusable Building Blocks (do not redesign)

The channel-transport layer is already in good shape and should be reused
as-is (confirmed by full-source review in investigation scripts 006–007,
not just grep snippets):

- `MessageDeliveryChannel` (enum: `SMS`, `EMAIL`)
- `MessageDeliveryType` (enum: `REGISTRATION_OTP`, `LOGIN_OTP`,
  `FORGOT_PASSWORD_OTP`, `USERNAME_RECOVERY`) — **this is reused directly
  as the notice key** (see §7.0); no new enum is introduced
- `MessageDeliveryScenario` (enum: `REGISTRATION`, `LOGIN`,
  `FORGOT_PASSWORD`, `USERNAME_RECOVERY`) — used only by
  `MessageDeliveryRuntimeConfig` for non-prod destination-override
  routing; unrelated to template content and left untouched
- `MessageDeliveryCommand` (record: messageType, scenario, channel,
  recipient, subject, body, templateCode, templateVariables, locale,
  correlationId)
- `MessageDeliveryPort` / `MessageDeliveryAdapter` — routes by channel to
  `SmsSenderPort` / `EmailSenderPort` and already renders `{placeholder}`
  templates in-line via `MessageTemplateRenderer` as part of `deliver()`
- `SmsSenderPort`/`EmailSenderPort` + `SmsSenderAdapter`/
  `SmtpEmailSenderAdapter` — provider-agnostic at the transport level,
  already handle masking, config validation, and safe error logging
- `DefaultMessageTemplateRenderer` — already does `{placeholder}`
  substitution generically and fails fast (`IllegalArgumentException`)
  on a missing variable
- `RegistrationMessageDeliveryAdapter` — confirms the real end-to-end
  pattern: resolve subject/body from policy → build
  `MessageDeliveryCommand` → call `MessageDeliveryPort.deliver(command)`.
  This is the exact seam the Template Service plugs into (§7.3)

**What's missing** is a layer that resolves "which notice (messageType),
which channel, which locale" into the actual subject/body string and
required-variable contract, replacing each workflow's own scattered
policy lookup. That is the gap this design closes — it does **not**
replace or wrap `MessageDeliveryPort` itself.

## 7. Proposed Architecture

### 7.0 Revised After Further Investigation: Reuse `MessageDeliveryType`, No New `NoticeCode`

Investigation (script 007) found that `MessageDeliveryType` **already
exists** in `application.dto.message` with exactly these four values:

```java
public enum MessageDeliveryType {
  REGISTRATION_OTP,
  LOGIN_OTP,
  FORGOT_PASSWORD_OTP,
  USERNAME_RECOVERY
}
```

This is identical, value-for-value, to the `NoticeCode` enum proposed in
the previous draft of this document (§5's resolved decision). Introducing
a second, parallel enum would duplicate an identifier that already flows
through every `MessageDeliveryCommand` today (`RegistrationMessageDeliveryAdapter`
already passes `MessageDeliveryType.REGISTRATION_OTP` when building its
command). **This design therefore drops `NoticeCode` entirely and uses
`MessageDeliveryType` as the notice key.** There is also an existing,
separate `MessageDeliveryScenario` enum (`REGISTRATION`, `LOGIN`,
`FORGOT_PASSWORD`, `USERNAME_RECOVERY`) used by `MessageDeliveryRuntimeConfig`
for non-prod destination-override lookups — this is a different concern
(deployment-environment destination routing) and is unrelated to template
content resolution; it is left untouched.

### 7.1 `NoticeTemplateDefinition`

```java
public record NoticeTemplateDefinition(
    MessageDeliveryType messageType,
    MessageDeliveryChannel channel,
    String locale,
    String subject,          // null for SMS
    String body,
    Set<String> requiredPlaceholders) {}
```

### 7.2 `NoticeTemplateRegistryPort` (single resolution port — no separate resolver layer)

An earlier draft of this design proposed a second port,
`NoticeContentResolverPort`, sitting in front of the registry. On review,
that port added no behavior of its own — it was a pure passthrough with
an identical signature to `NoticeTemplateRegistryPort.resolve(...)`. Per
decision, **this is collapsed into a single port**; workflow adapters
depend on `NoticeTemplateRegistryPort` directly:

```java
public interface NoticeTemplateRegistryPort {
  NoticeTemplateDefinition resolve(MessageDeliveryType messageType,
                                    MessageDeliveryChannel channel,
                                    String locale);
}
```

A YAML-backed implementation (`YamlNoticeTemplateRegistryAdapter`) mirrors
the existing `@ConfigurationProperties` pattern already used throughout
`AuthWorkflowProperties`, but under one new, centralized config root
(see §8) instead of being split per-workflow.

If a future need arises for caching, fallback-locale resolution, or
similar cross-cutting behavior, the standard extension path is a
decorator implementing the same `NoticeTemplateRegistryPort` interface
(e.g. `CachingNoticeTemplateRegistryAdapter` wrapping
`YamlNoticeTemplateRegistryAdapter`) — callers depend on the interface,
not the concrete class, so no workflow adapter changes would be needed.
This is not being built now, since there is no current requirement for
it.

Investigation also found that the four workflow adapters do not call
`MessageDeliveryPort` with blank commands — each already resolves its own
subject/body from scattered policy objects (e.g.
`RegistrationMessageDeliveryAdapter.subject()/body()`) **before**
constructing `MessageDeliveryCommand`, then calls the existing
`MessageDeliveryPort.deliver(command)` directly. The existing
`MessageDeliveryAdapter` already renders `{placeholder}` templates
in-line via `MessageTemplateRenderer` as part of `deliver()`.

Each of the four `*MessageDeliveryAdapter` classes (Registration today;
Login, Forgot Password, Username Recovery once migrated) changes its
internal `subject()`/`body()`-equivalent logic to call
`NoticeTemplateRegistryPort.resolve(...)` instead of reading from its own
`AuthWorkflowProperties` sub-tree, then still builds
`MessageDeliveryCommand` and calls `MessageDeliveryPort.deliver(command)`
exactly as today. **No changes to `MessageDeliveryPort`,
`MessageDeliveryAdapter`, `MessageTemplateRenderer`, `SmsSenderAdapter`,
or `SmtpEmailSenderAdapter` are required.** Required-placeholder
validation happens in `YamlNoticeTemplateRegistryAdapter` at startup
(fail-fast on misconfiguration) rather than per-request, since
`DefaultMessageTemplateRenderer` already throws
`IllegalArgumentException` on a missing variable at render time as a
second line of defense.

### 7.3 Extensibility Path for Future Channels (WhatsApp, WeChat, ...)

Per your explicit requirement, adding a channel must not require reworking
workflow logic. With the design above:

1. Add the new value to `MessageDeliveryChannel` (e.g., `WHATSAPP`).
2. Implement a new `*SenderPort` adapter (mirrors `SmsSenderAdapter`).
3. Add a `case WHATSAPP ->` arm in `MessageDeliveryAdapter` and the five
   other exhaustive channel switches found in investigation (§12 lists
   all six call sites: `MessageDeliveryAdapter`,
   `UsernameRecoveryEffectiveDestinationConfigAdapter`,
   `RegistrationMessageDestinationOverrideAdapter`,
   `Sha256DestinationBucketIdGenerator`, plus two others).
4. Add template content for the new channel under each notice code in
   config (§8).

Steps 1–4 touch **transport/config wiring only** — zero changes to any of
the four workflow adapter classes' delivery logic, because they will
depend only on `NoticeTemplateRegistryPort` with the existing
`MessageDeliveryType` + `MessageDeliveryChannel`, never on template
literals.

## 8. Proposed Config Schema (illustrative, not final)

### 8.0 Key Naming and Locale Scope (revised after investigation)

YAML keys are the kebab-case form of the `MessageDeliveryType` enum
values themselves (not a separate `NoticeCode`-style name), so the
mapping from enum to config key is mechanical and unambiguous:

| `MessageDeliveryType` | YAML key |
|---|---|
| `REGISTRATION_OTP` | `registration-otp` |
| `LOGIN_OTP` | `login-otp` |
| `FORGOT_PASSWORD_OTP` | `forgot-password-otp` |
| `USERNAME_RECOVERY` | `username-recovery` |

**Decision (confirmed):** the new schema supports **per-locale (`en` +
`zh_HK`) content for all four message types**, not just Forgot Password.
Investigation (script 011) found the current state is inconsistent and a
locale gap, not a deliberate design:

- Only `ForgotPasswordOtpTemplates` (hardcoded Java, not YAML) actually
  varies content by locale today (`en`/`zh_HK` maps).
- Registration's `Otp`/`OtpEmail` and Login's `LoginOtp`/`LoginOtpEmail`
  each bind only a **single**, locale-agnostic `sms-template`/
  `subject`/`body` in YAML — no locale split exists.
- `UsernameRecoveryTemplate` likewise binds only a single
  `email-subject`/`email-body`/`sms-body` — no locale split exists.
- `application.yml` additionally contains an **orphaned** YAML block,
  `auth-workflows.forgot-password.otp.localized-template.{en,zh_HK}.*`,
  which does not bind to any field on `AuthWorkflowProperties.Otp` and is
  silently ignored by Spring's relaxed binding. This confirms Forgot
  Password's real locale-varying content comes only from
  `ForgotPasswordOtpTemplates.approved()` today, and that stray YAML node
  will be removed (not migrated) as part of cleanup once the central
  schema is live.

Since the BRD requires both English and Chinese content for all three
BRD notices (and therefore all four `MessageDeliveryType` values once
Login/Forgot Password are split per §5), the central schema closes this
gap uniformly rather than perpetuating it.

```yaml
notice-templates:
  registration-otp:
    sms:
      en: "BCT Pension: Your One-Time Password (OTP) for Account Registration: {code}"
      zh_HK: "BCT 退休金: 有關您的帳戶登記的一次性密碼為: {code}"
    email:
      en:
        subject: "..."
        body: "..."
      zh_HK:
        subject: "..."
        body: "..."
  login-otp:
    sms:
      en: "BCT {trustCode}: Your One-Time Password (OTP) : {code}"
      zh_HK: "BCT - {trustCode} : 您的一次性密碼為: {code}"
    email:
      en:
        subject: "..."
        body: "..."
      zh_HK:
        subject: "..."
        body: "..."
  forgot-password-otp:         # content intentionally identical to login-otp (§5)
    sms:
      en: "BCT {trustCode}: Your One-Time Password (OTP) : {code}"
      zh_HK: "BCT - {trustCode} : 您的一次性密碼為: {code}"
    email:
      en:
        subject: "..."
        body: "..."
      zh_HK:
        subject: "..."
        body: "..."
  username-recovery:
    sms:
      en: "BCT {trustCode}: Your Username is {username}. Please login our Member Portal to view your account details"
      zh_HK: "BCT - {trustCode} : 您的用戶名稱是 {username} 。 請登入會員網站查閱您的賬戶資料。"
    email:
      en:
        subject: "..."
        body: "..."
      zh_HK:
        subject: "..."
        body: "..."
```

This replaces the four scattered `auth-workflows.*.otp.*` /
`auth-workflows.username-recovery.template.*` blocks (plus the hardcoded
`ForgotPasswordOtpTemplates` Java class and the orphaned
`localized-template` YAML node) with one coherent, notice-keyed,
per-locale structure, while `auth-workflows.*` config for non-template
concerns (OTP length, TTL, resend limits, destination overrides) remains
where it is — this design only centralizes **content**, not the entire
`AuthWorkflowProperties` tree.

## 9. Migration Plan (future phases — not in this round's scope)

1. Introduce `NoticeTemplateDefinition`, `NoticeTemplateRegistryPort` +
   `YamlNoticeTemplateRegistryAdapter` (additive, no existing code
   touched).
2. Migrate Username Recovery first (simplest — single call site in
   `UsernameRecoveryFeatureConfig`).
3. Migrate Registration (replace `RegistrationMessageDeliveryAdapter`'s
   `subject()`/`body()` policy lookup with
   `NoticeTemplateRegistryPort.resolve(...)`), then Login.
4. Migrate Forgot Password **last**, since it currently has no live YAML
   wiring at all — this is the highest-risk migration. As part of this
   step, reconcile its content to match `login-otp` verbatim (§5), and
   remove `ForgotPasswordOtpTemplates` plus the orphaned
   `auth-workflows.forgot-password.otp.localized-template.*` YAML node
   (§8.0) rather than migrating it, since it has no live binding today.
5. Remove the four old template config blocks and the duplicated
   `ttlUnit()` helpers once all four call sites are migrated.

Each phase should land as its own branch/MR per your existing workflow
conventions, with its own investigation-first validation before any code
is written.

## 10. Test Coverage Strategy

Per your existing tiered policy:

- `NoticeTemplateDefinition` (record/DTO) — ≥95% instruction and branch
  coverage.
- `YamlNoticeTemplateRegistryAdapter` (orchestration/config-binding,
  including fail-fast required-placeholder validation) — ≥95%
  instruction, 85–90% branch, with one-line justification comments for
  any defensive/unreachable branch.

## 11. Open Decisions Requiring Sign-off

1. ~~Login vs. Forgot Password content~~ — **Resolved** (§5): two
   distinct `NoticeCode` values with synchronized identical content.
2. **Forgot Password YAML wiring**: the commented-out validation in
   `AuthWorkflowProperties.validateForgotPasswordOtpPolicy` suggests this
   was deliberately deferred. Confirm whether centralizing Forgot
   Password content is in-scope for this initiative or a separate
   follow-up.
3. ~~`NoticeCode` closed-enum vs. open-ended~~ — **Moot/resolved**: the
   design now reuses the existing `MessageDeliveryType` enum directly
   instead of introducing a new type (§7.0).

## 12. Appendix: Investigation Inventory

Full file/class inventory, channel-switch call sites, and YAML line
references are preserved in the evidence zips from investigation scripts
001–003 (branch `chore/centralize-notification-template-investigation`).

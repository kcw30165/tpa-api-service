# Centralized Masking Policy - Solution Design

Status: Phase 1 (core service) implemented and committed. Phases 2-5 pending.
Branch: `refactor/centralized-masking-policy`
Related: `docs/capabilities/centralized-masking-policy/implementation-plan.md`

## 1. Problem statement

Masking logic (email, mobile, identity reference, date-of-birth, and generic
sensitive-field log redaction) was independently reimplemented across at
least 8 classes in login, registration, forgot-password, username-recovery,
and infrastructure logging, with inconsistent formats:

| Class | Purpose | Email format | Mobile format |
|---|---|---|---|
| `LoginDestinationMasker` | Login OTP FE display | `m****r@example.com` (fixed 4 stars) | `****4567` (last 4 digits) |
| `ForgotPasswordDestinationMasker` | Forgot-password OTP FE display | delegates to `LoginDestinationMasker` | delegates to `LoginDestinationMasker` |
| `RegistrationContactMasker` | Registration FE display | same fixed-4-star pattern | same last-4-digit pattern |
| `UsernameRecoveryRequestDestinationMasker` | Username recovery FE display | same pattern | same pattern |
| `EmailAddressMasker` | SMTP adapter logging | `m***r@example.com` (3 stars - different) | n/a |
| `SmsSenderAdapter.maskPhone` (private) | SMS adapter logging | n/a | `9123****` (keeps first 4 - opposite convention) |
| `RegistrationLookupLogMasker` | Structured failure-logging | different email/mobile shape, plus identity + DOB redaction | different shape |
| `LoggingSanitizer` | Generic key-based JSON/text field redaction | n/a (key-based, not shape-based) | n/a |

None of these are YAML-configurable; every format is hardcoded in Java.

## 2. Goals

1. One centralized `MaskingService` in `com.bct.ngtpa.apiservice.shared.masking`
   is the single authority for all email, mobile, identity, and
   date-of-birth masking, and supplies the placeholder used by generic
   structured-logging redaction.
2. Business rules are configurable via YAML (`masking.*`), not hardcoded.
3. All existing call sites (destination-display AND adapter/log-redaction)
   converge on the same two rules for email and mobile - no more format
   drift between "what the user sees" and "what appears in logs."
4. No behavior-preserving wrapper classes are left behind; old classes are
   deleted outright once callers are repointed.

## 3. Business rules

- **Email**: before `@`, mask all characters except the first and last
  character of the local-part. Domain is never masked.
  - Example (variable-length, default): `supermember@example.com` ->
    `s*********r@example.com`
  - Example (fixed-length, mask-length=4): `supermember@example.com` ->
    `s****r@example.com`
  - Local-part of length <= 2: fully masked (no char is "safely" revealable
    without effectively disclosing the whole value).
- **Mobile**: mask all digits except the first and last digit of the number.
  - Example (variable-length, default): `12345678` -> `1******8`
  - Example (fixed-length, mask-length=4): `12345678` -> `1****8`
  - Length <= 2: fully masked.
- **Identity** (e.g. policy/cert/trust reference such as
  `TRUST/0001/G12345678`): reveal only the last `/`-delimited segment;
  every other segment becomes a fixed 2-character mask group. Example:
  `TRUST/0001/G12345678` -> `**/**/G12345678`. No revealable `/`-segment ->
  falls back to the global disabled-placeholder.
- **Date of birth**: always fully redacted (fixed placeholder, e.g.
  `<redacted>`). No partial reveal is ever applied to DOB.
- **Generic structured-logging redaction** (`LoggingSanitizer`): unrelated
  to shape-based masking above. Continues to redact by field-name match
  (e.g. `password`, `secret`) but its placeholder value is sourced from
  `MaskingService.loggingPlaceholder()` instead of a private hardcoded
  constant, so there is one place that owns "what a masked/redacted value
  looks like" across the whole codebase.

## 4. Mode semantics (email / mobile only)

Both email and mobile support two mutually exclusive reveal-shape modes,
selected per type via `masking.<type>.mode`:

- **`variable-length`** (default): number of mask characters rendered
  equals the actual number of hidden characters. Length-preserving;
  mirrors the true length of the original value.
- **`fixed-length`**: number of mask characters rendered is always the
  configured `masking.<type>.mask-length`, regardless of input length.

In both modes, only the first and last character of the relevant segment
is ever revealed (email: local-part before `@`; mobile: the full number).

## 5. Disabled behavior (Option B)

When `masking.<type>.enabled=false`, `MaskingService` returns the configured
global `masking.disabled-placeholder` (default `<masked>`) instead of
performing any partial reveal. Raw passthrough is never an option - the
value stays fully hidden either way; disabling only turns off the
first/last-character reveal format, not confidentiality itself.

## 6. YAML schema

```yaml
masking:
  email:
    enabled: true
    mode: variable-length      # variable-length | fixed-length
    mask-length: 4             # used only when mode=fixed-length
    mask-character: "*"
  mobile:
    enabled: true
    mode: variable-length      # variable-length | fixed-length
    mask-length: 4             # used only when mode=fixed-length
    mask-character: "*"
  identity:
    enabled: true
    mask-character: "*"
  date-of-birth:
    enabled: true
    placeholder: "<redacted>"
  logging:
    enabled: true
    mask-character: "*"
    placeholder: "***"
  disabled-placeholder: "<masked>"
```

Configured in `src/main/resources/application.yml`. Follows the existing
`account-identifiers` precedent: a global, top-level, kebab-case YAML key
bound via a single `@ConfigurationProperties(prefix = "masking")` class
(`MaskingProperties`), not scoped under any one feature's
`auth-workflows.*` tree.

## 7. Scope: call sites migrating onto MaskingService

| # | Class | Data type(s) | Current owner package |
|---|---|---|---|
| 1 | `LoginDestinationMasker` | email, mobile | `application.usecase.auth.login.preauth` |
| 2 | `ForgotPasswordDestinationMasker` | email, mobile (delegates to #1) | `application.usecase.auth.forgotpassword.otp` |
| 3 | `RegistrationContactMasker` | email, mobile | `application.service.auth.registration` |
| 4 | `UsernameRecoveryRequestDestinationMasker` | email, mobile | `application.usecase.auth.usernamerecovery` |
| 5 | `EmailAddressMasker` | email (SMTP adapter log) | `adapter.out.email` |
| 6 | `SmsSenderAdapter.maskPhone` (private method) | mobile (SMS adapter log) | `adapter.out.sms` |
| 7 | `RegistrationLookupLogMasker` | email, mobile, identity, date-of-birth (structured failure logging) | `adapter.out.apim.registration` |
| 8 | `LoggingSanitizer` | generic redaction placeholder only (not shape masking) | `infrastructure.logging` |

All 8 route through the single `MaskingService` (`shared.masking` package).
Classes 1-7 are deleted outright once their callers are repointed (see
migration approach below); class 8 keeps its existing key-based redaction
logic but sources its placeholder constant from
`MaskingService.loggingPlaceholder()`.

## 8. Migration approach: direct removal, no wrappers

Decision: delete the 7 old masker classes outright and repoint every call
site directly to `MaskingService`, rather than converting them into
thin backward-compatible wrappers.

Rationale:
- Several of the old classes are instantiated inline (`new
  LoginDestinationMasker()`), not Spring-managed; converting them into
  wrappers would require the same constructor/DI rewiring at every call
  site as direct removal does, so wrapping saves no migration effort.
- Wrappers leave old class names in the codebase, inviting accidental
  reuse of the pre-centralization path.
- Matches existing project precedent (e.g. `TRPSearchUnregMember`
  replaced outright rather than wrapped; obsolete Account Balance code
  removed rather than aliased).

## 9. Open items / explicitly deferred

- `identity` and `date-of-birth` masking currently have a single fixed
  strategy each (no `mode` toggle). If a future requirement needs
  alternate identity/DOB reveal shapes, extend `MaskingProperties`
  analogous to the email/mobile `mode` field at that time.
- `sensitive-tokens` list currently owned by
  `logging-sanitizer.sensitive-tokens` is expected to migrate under
  `masking.logging.sensitive-tokens` during the `LoggingSanitizer`
  integration phase (Phase 3); exact key relocation is confirmed in that
  phase's script, not assumed here.

## 10. Why MaskingService is a concrete class, not an interface

`MaskingService` is deliberately implemented as a single concrete
`@Component`, with no `MaskingServicePort`/interface counterpart, unlike
the ports in `application.port.out` (`EmailSenderPort`, `SmsSenderPort`,
`RegistrationLookupPort`, `FailureEventPort`, `AuthFlowSessionPort`,
etc.). This was raised explicitly during implementation and confirmed as
the correct choice, for the following reasons:

1. **No I/O or external-system boundary.** Every real port in this
   codebase exists because it crosses a boundary to an external system
   (APIM, SMTP, an SMS gateway, Redis). `MaskingService` is pure,
   deterministic string transformation with no I/O of any kind - it does
   not fit the definition of a port.
2. **Architecturally closer to existing concrete pure-logic classes.**
   It sits next to `RegistrationContactDeriver`, `CsrfTokenValidationService`,
   and `OriginRefererValidator` - all concrete, single-implementation,
   pure business-rule classes already in this codebase - not next to a
   port.
3. **Variability is already handled via configuration, not polymorphism.**
   `MaskingProperties`/YAML already covers every axis of variation this
   service needs (per-type `enabled` flags, `variable-length` vs
   `fixed-length` reveal mode, configurable mask characters). An
   interface would be a second, redundant extension mechanism for a
   problem configuration already solves.
4. **No test ever mocks or fakes it.** Across every call site introduced
   in Phases 2-3b (~20+ locations), every test constructs the real
   `MaskingService` with a custom `MaskingProperties` instance - never a
   mock. The primary practical benefit an interface provides (test
   doubles) is unused in practice.
5. **The counter-argument (stylistic consistency with the codebase's
   otherwise-hexagonal port convention) is acknowledged but weighed
   below YAGNI here**, especially since ~20 call sites already depend on
   the concrete type directly; retrofitting an interface later would
   touch all of them for no functional gain, and can still be done then
   if a genuine second implementation or mocking need ever materializes.

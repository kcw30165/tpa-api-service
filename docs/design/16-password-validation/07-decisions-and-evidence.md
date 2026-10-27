# 07 Decisions, evidence and implementation checks

## Approved decision register
D01 One shared password-validation endpoint; Personal Information initially, future investment/fund-switch consumers unsupported now.
D02 Bind to authenticated session, selected account, purpose and exact reviewed changes; FE sends proposed operation payload at confirmation.
D03 TTL 5 minutes; one active token per session/account/purpose; successful confirmation replaces old token; other scopes coexist.
D04 First authenticated submission consumes, including validation failure, payload mismatch and malformed JSON. Token in header; ownership/session/CSRF/account checks precede consumption.
D05 Downstream owns password failure counting and lock threshold; no local five-attempt rule or fail-count exposure.
D06 Wrong password returns code only. Locked account invalidates current session and cookie; cookie always cleared even if invalidation fails, with technical failure reported.
D07 No accountEnv in TRPPwdValidation body/header; business HTTP 200; data exactly one item on empty-error path; unregistered cust-id -> 503.
D08 No automatic password-validation retry. No new mutation result replay. Consumed tokens never authorize another execution.
D09 POST /api/v1/password-validation; X-Password-Validation-Token; token/expiresInSeconds result; configured login password lengths, no trimming/case conversion or registration complexity.
D10 JSON object order irrelevant; arrays/types/null presence significant; no silent value normalization.
D11 Approved HTTP matrix: wrong password 401; locked 423; invalid authorization 403; invalid request 400; technical failures 503; throttle 429.
D12 Username-wide configurable 10 requests/60 seconds across sessions/accounts/purposes, independent of TAS lock counting.

## Evidence register
E1: user-provided 001-password-validation-readonly-investigation.zip.txt; source snapshot origin/dev 49b5ffb4089b04d214444b3e3d7cd33f2d5dbc4c. Archive/source integrity was reported checked during the evidence review. No runtime validation was performed.
E2: downstream request/response examples and clarifications supplied directly by the user in this conversation.
E3: user approvals of proposals 1-5, malformed-JSON consumption, defaults and final HTTP/throttle mapping in this conversation.

E1 source findings used in this design:
- LoginOtpVerifyService constructs ActorContext from normalizedUsername; AuthenticatedSessionAccount has no separate username field.
- CurrentPortalAccessContextResolver reads established request context, not a new Account-Ref-only Redis lookup.
- PersonalInformationController update validates structure/YAML/access before update dispatch; UpdatePersonalInformationRequest has formVersion, applyToAllAccounts, fields.
- PortalAccessContextWebFilter resolves context when Account-Ref is present; missing header can skip that filter's resolution. Protected-route enforcement must explicitly require it.
- RequestLoggingWebFilter has highest-precedence body-buffering behavior when enabled, before account context filter; disabled defaults alone do not prove consume-before-parse.
- AuthenticatedSessionCookieWriter supports clearing; existing LogoutController clears on successful cleanup only and returns 503 on failure.
- Generic session CAS and NON_RETRYABLE APIM classification exist. Their existence is not proof that new grant concurrency requirements are already met.
- Login HTTP mapping has 401 incorrect credentials, 423 locked and 502 credentials unavailable; shared system mapping includes 503. This package uses the explicitly approved feature matrix, not a claim of uniform repository conventions.
E4: scripts 005/006 in this conversation -- full-file reads (not grep snippets) of the actual working-tree source at HEAD a88f80a4 for CAS/session, filter, Personal Information, and rate-limit classes, plus every `.lua` script in the repository. Used to resolve gates G1-G5 at the design level with exact file references (see "Gate resolution status" below). No runtime/Redis/HTTP validation was performed; this remains a source-reading pass only.

## Authored technical specifications, not separate user approvals
New feature error-code spellings; result.data DTO wrapper; strict issuance envelope; canonicalizer numeric/duplicate-key details; random token strength and storage schema; fixed-window admission boundary; Retry-After; capability-specific grant storage preference; proposed milestone structure. These implement approved behavior and remain subject to design/code review. They are not descriptions of already implemented code.

## Implementation/release verification gates
G1 Storage: prove atomic grant replacement/consumption with current-session usability and no session resurrection. Final script/key layout is not guessed from encrypted payload fields.
G2 Ordering: prove actual filter/handler/decoder order, missing-account behavior, malformed/empty body consumption and logging-enabled exclusions with full HTTP tests. Transport/proxy rejection before BFF is outside consumption guarantee.
G3 Limits: verify current login password bounds, decoder/body/depth limits and configuration names. Do not insert invented production values.
G4 Logging: prove new token header, password, cust-id and personal payload cannot leak in request/response/APIM/debug/error/toString paths. Existing sanitizer presence is not sufficient evidence.
G5 APIM: verify NON_ACCOUNT profile/crypto settings, no accountEnv header and no hidden business retry for TRPPwdValidation.
G6 FE/deployment: verify new header through CORS/proxy, response envelope, 401 wrong-password handling without blanket logout, 423 cleanup and timeout no-resubmission behavior.
G7 Apply-all: approved binding covers submitted applyToAllAccounts flag and complete fields, not a frozen downstream-resolved related-account list. If business requires a frozen target set, stop and revise scope before implementation.

These gates do not reopen approved requirements. They prevent unverified implementation/deployment details from being asserted as facts. No tests, coverage, startup, quality gate, merge readiness or regulatory compliance is certified by this document package.
### Gate resolution status (M0 corrective passes, source-grounded -- E4)

This pass read full method bodies and Lua scripts (E4) to resolve gates at the design level with exact source references. No code was changed; these are design decisions, not implementation-complete claims. G1 and G4 below were further corrected in a second corrective pass after the independent reviewer returned CHANGES REQUIRED on commit 5540abac, identifying a blocking session-resurrection race (G1), a major grant-key account-scoping gap (closed in Section 6 of 01-solution-design.md), a major logging-exclusion robustness gap (G4), and a major response-type contradiction (closed in 02-api-contract.md).

- G1 (Storage): PARTIALLY RESOLVED, CONTINGENT ON A CROSS-CUTTING PREREQUISITE (corrected after independent review). Reuse `AuthenticatedSession.featureState` + `AuthenticatedSessionConcurrencyService.updateIfUsable(...)`, keyed per session/account/purpose (see corrected Section 6 of 01-solution-design.md). A genuine session-resurrection race was found in the prior design pass (terminal transitions preserved the session revision, allowing a racing grant-mutation CAS write to silently restore ACTIVE status after logout/revoke/expiry). The fix requires `AuthenticatedSessionLifecycleService`'s terminal-transition path to bump the session revision on every terminal write -- a change to existing, already-shipped session-lifecycle code, explicitly flagged as a prerequisite subtask that must land, with its own tests, before any password-validation grant code is implemented. Still requires the deterministic race tests specified in Section 6, per R06/R08/T06/T08/T19.
- G2 (Ordering): RESOLVED at design level. `RequestLoggingWebFilter` (`@Order(HIGHEST_PRECEDENCE)`) runs before `PortalAccessContextWebFilter` (`@Order(HIGHEST_PRECEDENCE + 1)`); a new `PasswordValidationTokenGateWebFilter` at `@Order(HIGHEST_PRECEDENCE + 2)` structurally precedes the controller's lazy `@RequestBody Mono<T>` subscription (see Observability section of 04-validation-and-error-handling.md). Still requires full HTTP tests proving zero body bytes read on rejection.
- G3 (Limits): PARTIALLY RESOLVED. Login password bounds confirmed: `auth-workflows.login.credential.password-min-length`/`password-max-length` (env `LOGIN_PASSWORD_MIN_LENGTH`/`LOGIN_PASSWORD_MAX_LENGTH`, default 1/128), enforced structurally case-sensitive/no-trim by `LoginCredentialPolicyService`'s constructor guard. Inbound body/decoder size limits were NOT found in the searched config excerpts and remain an open verification item for implementation, not invented here.
- G4 (Logging): PARTIALLY RESOLVED, CORRECTED after independent review. The prior pass's reliance on "never configure an endpoint-logging rule for these two paths" plus a config-scan test was insufficient, because `RequestLoggingWebFilter` matches rules with `AntPathMatcher`, so an unrelated wildcard rule could still match these paths without naming them. The corrected design requires a hard, code-level path denylist inside `RequestLoggingWebFilter` itself that unconditionally overrides any configured rule match for these two exact paths (see corrected Observability section of 04-validation-and-error-handling.md); the config-scan test is now secondary defense-in-depth, not the primary control. Redaction still follows the `CaptchaChallengeResponse`-style `<redacted>` `toString()` precedent. `SecurityAuditMetadataSanitizer` remains confirmed as a separate mechanism from `RequestLoggingWebFilter`'s logging path and must not be assumed to cover it.
- G5 (APIM): PARTIALLY RESOLVED. `ApimRetryEligibility.NON_RETRYABLE` exists (confirmed in the prior pass, script 003). The exact `NON_ACCOUNT`-equivalent constant name on the account-scope enum was not re-verified by name in this pass and remains to be pinned at implementation time against the actual enum.
- G6 (FE/deployment) and G7 (apply-all binding): explicitly NOT addressed by this pass -- these are runtime/deployment-time and cross-team (FE, CORS/gateway, release coordination) concerns that cannot be resolved by reading BFF source alone. They remain open, separately tracked M5-stage gates, not M0 design questions.


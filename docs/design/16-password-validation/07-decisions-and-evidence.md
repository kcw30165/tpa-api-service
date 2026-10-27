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

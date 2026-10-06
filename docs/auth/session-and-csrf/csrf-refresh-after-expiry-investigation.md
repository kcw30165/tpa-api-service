# CSRF Refresh After Expiry: Investigation Record

## 1. Question

When the CSRF token is expired, can the caller call `POST /api/v1/auth/csrf-token/refresh` to get a new token and continue the workflow?

This record is investigation only. It changes no production code, no contract and no existing document.

## 2. Baseline and evidence

| Item | Value |
|---|---|
| Repository HEAD | `9b96910c4e6c1b20addb2e35c633dac37c49d1d8` (`dev` == `origin/dev`) |
| Review branch | `review/csrf-token-refresh-expiry-investigation` |
| Evidence 003 | Full source of the refresh capability, auth-flow session, CSRF and registration classes (128 files) |
| Evidence 004 | Characterization probe over the real classes with an in-memory `AuthFlowSessionPort`; 66 targeted tests, 0 failures. Probe observations are cited below as `S<n>` |
| Trigger | Browser capture: the response body of one selected registration `verify` request is `err.registration.csrf.invalid`, `nextStep=NONE`, target `PAGE`. Other registration requests (`check`, `complete`, `cancel`, further `verify` and `resend`) are marked failed in the network list, but their bodies were not visible |

Probe method: `CsrfTokenRefreshController`, `RefreshCsrfTokenService`, `PreAuthSessionCsrfRefreshService`, `AuthFlowSessionCsrfService` and `DefaultRegistrationProtectedAuthFlowContextLoader` were used unchanged. Only the persistence port, the token generator and the context protector were replaced.

## 3. Answer

**It depends on why the CSRF check failed.** The refresh endpoint is a recovery path for a lost or mismatched token on a live session. It is not a recovery path for an expired session.

| Situation | Can refresh recover? | Evidence |
|---|---|---|
| Session alive, raw token lost or wrong | Yes. HTTP 200, new token returned, the new token is accepted by the registration loader, the old token is rejected | S1b, S2 |
| Session alive, stored CSRF hash absent | Yes. HTTP 200 and the new token is accepted | S6 |
| Session record present but past `expiresAt` | No. HTTP 401, no write | S3a, S3b |
| Session record gone (Redis TTL elapsed) | No. HTTP 401, no write | S4 |
| Session `COMPLETED`, `CANCELLED`, `LOCKED`, `UNKNOWN_OUTCOME_BLOCKED` or `EXPIRED` | No. HTTP 401, no write | S5a, S5b, S13 |
| No `auth_flow_sid` cookie | No. HTTP 401 | S10 |
| Duplicate `auth_flow_sid` cookies | No. HTTP 400 | S11 |
| Origin not allowed, or neither Origin nor Referer present | No. HTTP 403 | S9a, S9b |
| Concurrent mutation during rotation | No. HTTP 409, no token returned, retry is possible | S8 |
| Stale or blank `portal_sid` together with a valid `auth_flow_sid` | No. HTTP 401, no fallback to the pre-auth session | S7a, S7b |

## 4. Findings

### F1. There is no CSRF-specific expiry

The CSRF token is stored only as a hash inside the `AuthFlowSession`. Its lifetime equals the session lifetime. "Expired CSRF" therefore means one of two different things:

1. The session is still alive and the submitted token does not match the stored hash (lost token, replaced token, missing hash).
2. The session itself has expired or is terminal.

### F2. The two cases produce different public errors

For registration OTP verify and OTP resend (`SharedVerifyRegistrationOtpService`, `SharedResendRegistrationOtpService`), the loader status maps as follows. Registration complete and cancel behave differently, see section 5, items 5 and 6:

| Loader status | Public error code | Next step |
|---|---|---|
| `CSRF_INVALID` | `err.registration.csrf.invalid` | `NONE` |
| `MISSING_SESSION`, `EXPIRED_OR_TERMINAL_SESSION`, `WRONG_SCENARIO`, `INVALID_STATUS`, `MISSING_ACTIVE_OTP`, `MISSING_CONTEXT`, `CONTEXT_DECRYPTION_FAILED` | `err.registration.authFlow.invalid` | `RESTART_FLOW` (from `application-registration-errors.yml`) |

The visible `verify` response in the browser capture shows `err.registration.csrf.invalid`. By the code path above, the session was therefore not reported as expired or terminal for that call. Refresh was the applicable recovery action, subject to F5. The bodies of the other failed requests in the capture were not visible, so no conclusion is drawn for them.

The loader also converts any `RuntimeException` raised while validating into `CSRF_INVALID` (`DefaultRegistrationProtectedAuthFlowContextLoader.load`, `onErrorResume`). A persistence fault can therefore surface as `csrf.invalid`. This was not exercised.

### F3. What refresh preserves

After a successful pre-auth refresh (S2): status, active OTP challenge, session data, `createdAt` and `expiresAt` are unchanged. Only the CSRF hash and `updatedAt` change. Refresh does not extend the session. The sliding expiry used by the registration OTP, username check and completion calls is not triggered by refresh.

### F4. Strict rotation

Each successful refresh invalidates all earlier tokens (S12: after two refreshes only the last token is accepted). A second browser tab that refreshes first invalidates the token held by the first tab.

### F5. Preconditions the caller must meet

- `auth_flow_sid` cookie present exactly once. The cookie path is `/api/v1/auth`, which covers the refresh route. The refresh controller writes no cookies.
- `X-Request-Id` header (enforced by `RequiredRequestIdWebFilter`, not exercised by the probe).
- `Origin`, or `Referer` as fallback, matching `CorsProperties.allowedOrigins`.
- The previous `X-XSRF-TOKEN` is not required.
- If a `portal_sid` cookie is present, the authenticated path is selected and never falls back to `auth_flow_sid`.

### F6. Refresh is not restricted by workflow status

Refresh succeeds for every non-terminal `AuthFlowStatus`, including `STARTED`, `OTP_VERIFIED`, `USERNAME_VERIFIED` and `COMPLETING` (S13). It is not scenario-aware.

### F7. The failure response gives the frontend no recovery hint

`err.registration.csrf.invalid` is configured with `next-step: NONE`. Nothing in the response tells the frontend to call refresh. Whether the frontend does so is not determinable from this repository.

## 5. Observations outside the question (not acted on)

These are recorded for later triage and are deliberately not fixed here.

1. **Registration protected calls do not check Origin/Referer.** The probe shows a valid session and token with a foreign Origin is accepted by `DefaultRegistrationProtectedAuthFlowContextLoader` (S1c). `AuthFlowSessionCsrfService.validateForSession` does not use the origin fields, and the loader passes an empty allowed-origin list. `OriginRefererValidator` is applied in Forgot Password and Username Recovery operations and in the refresh endpoint, but no registration class references it. The registration documents state that Origin/Referer is validated.
2. **Stale design statements.** `authflow-session-security-contract.md` section 5.4 and `registration-sequence-diagrams.md` say the frontend must restart registration when the CSRF token is lost. The refresh documents under `docs/auth/session-and-csrf/` describe the refresh endpoint as the recovery path. The two sets of documents disagree.
3. **Refresh during `COMPLETING`** is allowed (F6). Whether a rotation in that state is acceptable has not been assessed.
4. **Loader error mapping** (F2, last paragraph).
5. **Registration complete never emits `err.registration.csrf.invalid`.** `SharedCompleteRegistrationService.authorize` selects the status `CSRF_INVALID` or `INVALID_AUTH_FLOW`, but passes `RegistrationErrorKey.AUTH_FLOW_INVALID` as the error code in both cases. A CSRF mismatch on complete therefore reports `err.registration.authFlow.invalid` (next step `RESTART_FLOW`), unlike verify and resend. This was read from source and not exercised.
6. **Registration cancel succeeds for a missing, expired or terminal session.** `CancelRegistrationService` returns a cleanup success (`ALREADY_TERMINAL`, `SESSION_NOT_FOUND`) before any CSRF check when the session is not usable, so a failed cancel is not an expiry symptom. This was read from source and not exercised.

## 6. Not determinable from this repository

- The server log line for the captured requests (`event=auth.flow.csrf.validation ... result=...`), which would show `TOKEN_MISMATCH` or `TOKEN_HASH_MISSING`.
- Whether the frontend calls the refresh endpoint after `csrf.invalid`.
- Real Redis behavior (TTL eviction, revision compare-and-set). The probe used an in-memory port, so a missing record stood in for an evicted key.
- The WebFlux filter chain and `SecurityConfig` at runtime. The route is covered only by `CsrfTokenRefreshWebArchitectureGuardTest`, which checks that `permitAll` precedes the catch-all rule.

## 7. Open decisions

None are taken in this record. Items 1 to 3 and 5 in section 5 each need an explicit scoping decision before any design or code change.

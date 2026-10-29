# 09 Release and operations

Status of this document: written at the end of milestone M5. It records what was built, what was verified, what still needs a person, and how to release. Items marked **open** have not been verified by anyone yet.

## 1. What is delivered

| Milestone | Scope | Approved commit |
|---|---|---|
| M0 | Design package | 558a18fd |
| M1 | Contracts, payload binding, `TRPPwdValidation` adapter, strict single-submission transport | 86eb775c |
| M1.5 | Shared session conditional writes, absolute-deadline TTL, contention fallback for touches | bfbac598 |
| M2 | Username throttle, session-bound grant store, write-point usability guard | ec7f1617 |
| M3 | `POST /api/v1/password-validation`, route guards, logging protections | 8e870d40 |
| M4 | Token gate on `PUT /api/v1/personal-information` | c6981630 |
| M5 Run A | CORS default, `.env.sample`, gate tests | 96eaacd4 |
| M5 documentation | This document set and the scenario script | the commit that adds this file |

## 2. Release order and rollback

The two sides are not compatible with each other in either order.

- **BFF first:** the current frontend sends no token, so every Personal Information update is refused with 403 until the frontend ships.
- **Frontend first:** the new frontend calls `/api/v1/password-validation`, which does not exist on the old BFF, so confirmation fails.

Release both in one maintenance window, in this order: configuration (secret, CORS), then BFF, then frontend. Verify with the scenario script (section 6) before announcing.

**Rollback warning:** rolling the BFF back to a version without this feature removes the password check. Personal Information updates then succeed without confirmation. Roll the frontend back at the same time and treat the window as unprotected.

## 3. Configuration

| Setting | Required | Notes |
|---|---|---|
| `PASSWORD_VALIDATION_RATE_LIMIT_SECRET` | Yes, per environment | Base64 of at least 32 random bytes. **Identical on every replica.** Never commit a value. If it is missing, the application still starts but every issuance fails closed with 503 |
| `PASSWORD_VALIDATION_RATE_LIMIT_MAX_ATTEMPTS` | No | Default 10 |
| `PASSWORD_VALIDATION_RATE_LIMIT_WINDOW_SECONDS` | No | Default 60 |
| `CORS_ALLOWED_HEADERS` | Only if overridden | Must include `X-Password-Validation-Token` and keep `Content-Type`. The built-in default already does |
| Login password length bounds | Existing | Reused for the password check; no registration-style complexity rules apply |

`.env.sample` carries blank placeholders for the secret and the defaults 10 and 60 for the limits. A blank numeric value would override the built-in default, so the numeric lines must not be left empty.

Recorded inbound limits (the framework defaults; the service sets no override): decoder in-memory limit 256 KiB, JSON nesting limit 1000. A deployment may override either. That has **not** been checked.

Not configurable in this release: the token lifetime is a code constant of 300 seconds. The approved design called the five minutes "configurable". See open item O-4.

## 4. Operations checklist

Before enabling in an environment:

- [ ] Secret generated (for example 32 bytes from a secure random source, Base64 encoded), stored in the secret manager, identical across replicas
- [ ] `CORS_ALLOWED_HEADERS` in the pipeline repository includes `X-Password-Validation-Token`
- [ ] Redis supports the script behaviour the session writes rely on. Verified only against Redis 8.0.5 in isolated tests. Check the deployed version, Sentinel failover behaviour and mTLS settings
- [ ] Redis and application clocks are synchronised (the write-point deadline check uses the Redis clock)
- [ ] The ingress or gateway does not log request bodies or the `X-Password-Validation-Token` header for these two routes
- [ ] APIM profile for `TRPPwdValidation` is the non-account profile, and no `accountEnv` is sent
- [ ] Decoder and JSON depth limits in the deployed configuration match the recorded defaults, or the difference is accepted

Signals to watch after release: sustained 429s for one user (a stuck client), a rise in 503 `err.password-validation.unavailable` (downstream, Redis or a missing secret), a rise in 403 `err.password-validation.authorization.invalid` on the update route (a frontend sending a different body than it confirmed), and 423 lockouts.

## 5. Verification gate register (final)

| Gate | Subject | Status | Owner |
|---|---|---|---|
| G1 | Atomic grant storage and session-usability at the write point | Implemented and proven in isolated Redis (73 cases). **Open:** deployed Redis version, Sentinel failover, clock synchronisation | Ops |
| G2 | Token consumed before the request body is read | Resolved by real-chain HTTP tests, including a body-read probe | Closed |
| G3 | Inbound body and depth limits | Recorded (256 KiB, depth 1000). **Open:** deployed overrides | Ops |
| G4 | No sensitive logging | Resolved in the service by code-level route and header protections with adversarial tests. **Open:** ingress, gateway and external log pipeline | Ops |
| G5 | `TRPPwdValidation` routing, encryption and single submission | Resolved against test doubles and a real local connector. **Open:** first live call on DEV | Nick |
| G6 | Frontend and deployment | **Open:** frontend implementation per document 08, deployed CORS values, `Retry-After` exposure | Frontend, Ops |
| G7 | Binding of "apply to all accounts" | The token binds the submitted flag and fields. It does **not** freeze which related accounts the downstream resolves. **Decision needed** | Nick |

## 6. Manual end-to-end check

`scripts/e2e/password-validation-scenarios.sh` exercises wrong password, missing token, changed body, consumed token, replay and the throttle with `curl`. It prints only scenario names, statuses and public error codes. Read the header comment before running it. It performs a real update only with `CONFIRM_LIVE_UPDATE=yes`, and a wrong-password attempt (which counts toward the downstream lock) only with `CONFIRM_WRONG_PASSWORD_ATTEMPT=yes`. Use a test account.

## 7. Open items

| Id | Item | Owner | Blocks release |
|---|---|---|---|
| O-1 | Frontend implementation and a real-browser run of document 08 | Frontend | Yes |
| O-2 | First live `TRPPwdValidation` call on DEV, via the scenario script | Nick | Yes |
| O-3 | Deployed Redis version, Sentinel failover and clock synchronisation | Ops | Yes |
| O-4 | Token lifetime is fixed at 300 s; approved wording said configurable. Accept, or add a setting in a follow-up | Nick | No |
| O-5 | Decide whether "apply to all accounts" needs a frozen target set (G7) | Nick | Decision |
| O-6 | `Retry-After` and `X-Request-Id` are not exposed to cross-origin JavaScript | Frontend, Ops | No |
| O-7 | Deployed `CORS_ALLOWED_HEADERS` overrides in the pipeline repository | Ops | Yes |
| O-8 | Gateway and log-pipeline check for bodies and the token header | Ops | Yes |
| O-9 | Confirm the Sonar analysis revision and branch used for the final gate | Nick | Yes |
| O-10 | Global handling of CSRF failures is still route-local for these two routes; align with the logout shape later | Nick | No |
| O-11 | Surefire text reports undercount four architecture test classes against the XML reports (7,226 versus 7,234); XML is authoritative | Nick | No |

## 8. Merge request text

**Title:** `feat(password-validation): password confirmation before personal information update`

**Description:**

> Adds a shared password-confirmation step. `POST /api/v1/password-validation` checks the member's password through the downstream `TRPPwdValidation` operation and returns a single-use token, valid for up to 300 seconds, bound to the session, the selected account, the purpose and the exact reviewed payload. `PUT /api/v1/personal-information` now requires that token in `X-Password-Validation-Token`; the gate consumes it before the body is read.
>
> **Notes for review**
> - The downstream owns failed-attempt counting and locking. The service never exposes the count and never retries the password call.
> - Session writes now use revision-checked conditional writes with absolute deadlines (M1.5). Existing consumers are unchanged and covered by regression tests.
> - Passwords, tokens and payloads are never logged; the two routes have hard logging protections.
> - New configuration: `PASSWORD_VALIDATION_RATE_LIMIT_SECRET` (required per environment).
>
> **Deploy:** the frontend and the BFF must be released together; see `docs/design/16-password-validation/09-release-and-operations.md`. Rolling back the BFF removes the password check.
>
> **Verification:** full test suite with the isolated Redis tests enabled, formatting check, startup check, static analysis. Open items are listed in section 7 of the release document.

**Proposed commit messages for the M5 documentation work**

```text
docs(password-validation): add as-built frontend, release and traceability documents
```

```text
test(e2e): add password-validation curl scenario script for manual DEV runs
```

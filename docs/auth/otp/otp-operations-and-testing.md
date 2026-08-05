# OTP Operations, Configuration, and Testing

> **Audience:** Developers, reviewers, testers, support engineers, and platform engineers.  
> **Rule:** Values in this document that describe current settings are configuration, not architectural constants.

## 1. Configuration model

OTP configuration is exposed through `AuthWorkflowProperties` with separate policy sections for Login and Registration.

### 1.1 OTP policy fields

- `length` — generated code length
- `numericOnly` — whether the generator is restricted to digits
- `ttlSeconds` — challenge lifetime
- `resendCooldownSeconds` — minimum time before resend
- `maxVerifyAttempts` — failed verification limit
- `resendLimitEnabled` — enables resend-count enforcement
- `maxResendAttempts` — resend limit when enabled
- `invalidatePreviousOnResend` — prevents an older code from succeeding
- `resetVerifyAttemptsOnResend` — controls attempt-counter behavior after resend
- `smsTemplate` — must contain the code placeholder expected by configuration validation
- `emailTemplate.subject`
- `emailTemplate.body` — must contain the code placeholder

The reviewed configuration indicates six-digit numeric OTPs, a 300-second challenge TTL, a 60-second resend cooldown, and five verification attempts. Registration also enables resend controls and previous-code invalidation. Verify the deployed profile before relying on these values.

### 1.2 Idempotency TTLs

Registration exposes action-specific TTLs for:

- Start
- OTP resend
- OTP verify
- Registration complete

Login exposes TTLs for:

- Start
- OTP send
- OTP resend
- OTP verify
- Cancel

A TTL must be positive and long enough to cover the maximum expected processing/replay interval without retaining pre-auth records unnecessarily.

### 1.3 Session and cookie settings

Pre-auth operations use the auth-flow session cookie, conventionally `auth_flow_sid`, plus an XSRF cookie/header arrangement. Login success writes a separate authenticated session cookie.

Operationally verify:

- Cookie name and path
- `HttpOnly` setting
- `Secure` setting
- `SameSite` value
- Max age
- Auth-flow session TTL
- Absolute and idle authenticated-session timeout
- Terminal-state retention
- Allowed origins

Do not troubleshoot cookie issues by disabling CSRF or weakening production cookie settings.

### 1.4 Redis settings

The reviewed codebase supports reactive Redis and Sentinel configuration, with SSL/mTLS and connection-pool settings externalized. OTP-related adapters use Redis for challenge state, auth-flow sessions, idempotency/replay records, locks, and rate-limit buckets.

Operational checks:

- Sentinel master name and nodes
- Authentication and SSL bundle
- Connection/read timeout
- Pool capacity and wait timeout
- Key prefix per environment
- Time synchronization across application and Redis hosts
- Memory and eviction policy
- Alerts for latency, timeouts, failover, and connection exhaustion

Do not use broad wildcard deletion for OTP/auth keys. Administrative deletion must be capability-scoped and key-factory-aware.

### 1.5 Message delivery

SMS/email adapters require provider-specific endpoints and credentials as well as approved templates.

Verify:

- Provider connectivity and timeout
- Sender identity
- Template placeholder validation
- Email subject/body
- SMS character/length constraints
- Environment-specific destination override rules, if approved
- Secret management
- Destination masking in logs

The sender does not provide a delivery-status callback in the current project context. Treat a successful provider call as accepted for sending, not proof of handset/mailbox delivery.

## 2. HTTP contract summary

### 2.1 Common transport values

The auth endpoints use combinations of:

- `X-Request-Id`
- `Idempotency-Key`
- `X-XSRF-TOKEN`
- `Accept-Language`
- `Origin`
- `Referer`
- `auth_flow_sid` cookie

A global filter requires a client-supplied `X-Request-Id` for `/api/*`. Controllers also echo it in the response.

### 2.2 Login

#### Start

```text
POST /api/v1/auth/login
```

Required in the current controller contract:

- `X-Request-Id`
- `Idempotency-Key`
- Request body containing username and password

On success, the response includes masked destinations and workflow step `SELECT_OTP_DESTINATION`, and the server writes the auth-flow cookie.

#### Send

```text
POST /api/v1/auth/login/otp/send
```

Requires:

- `auth_flow_sid`
- `X-XSRF-TOKEN`
- `Idempotency-Key`
- `X-Request-Id`
- Request body containing `destinationId`

Success moves to `VERIFY_OTP` and returns masked destination, expiry, and cooldown metadata.

#### Resend

```text
POST /api/v1/auth/login/otp/resend
```

Requires the protected flow context, CSRF, idempotency key, and request ID. The response can include `retryAfterSeconds`; when positive, the controller can add a `Retry-After` header.

#### Verify

```text
POST /api/v1/auth/login/otp/verify
```

Requires:

- `auth_flow_sid`
- `X-XSRF-TOKEN`
- `Idempotency-Key`
- Request body containing `code`

On success, the server can write the authenticated session cookie, clear pre-auth cookies, return the authenticated CSRF value, and provide accounts for selection.

### 2.3 Registration

#### Start

```text
POST /api/v1/auth/registration/start
```

Requires `Idempotency-Key`; the global API rule also requires `X-Request-Id`. Registration start performs its scenario checks and sends the initial OTP on success.

#### Resend

```text
POST /api/v1/auth/registration/otp/resend
```

Requires:

- `auth_flow_sid`
- `X-XSRF-TOKEN`
- `Idempotency-Key`
- `X-Request-Id` under the global API rule

The response can expose masked destination, expiry, cooldown, remaining resend attempts, and retry delay.

#### Verify

```text
POST /api/v1/auth/registration/otp/verify
```

Requires the auth-flow cookie, CSRF token, idempotency key, request ID, and body `code`. Successful verification continues to the next Registration step and can return unregistered trusts.

## 3. Error and recovery guide

### Invalid OTP

**Expected behavior:** Field-targeted error on `otpCode`; remaining attempts may be returned.  
**Check:** Challenge association, hash comparison, normalization, attempt counter.  
**User action:** Retry within the remaining attempt/TTL boundary.

### OTP expired

**Expected behavior:** Business failure and a next step that permits resend or restart according to the scenario.  
**Check:** Challenge expiry, Redis TTL, service clock, host time.  
**User action:** Resend or restart; never extend an expired code in place.

### Verification attempts exceeded / locked

**Expected behavior:** `429 Too Many Requests` in the mapped cases and restart guidance.  
**Check:** Atomic attempt increment and exact boundary behavior.  
**User action:** Restart according to policy; support must not unlock by editing Redis manually.

### Cooldown active

**Expected behavior:** Resend rejected, timing metadata returned, and `Retry-After` used where supported.  
**Check:** Last-send time, policy, replay behavior, client double-submit.  
**User action:** Wait for the indicated interval.

### Maximum resend attempts reached

**Expected behavior:** Business failure; flow may require restart.  
**Check:** Resend counter and policy resolution.  
**User action:** Restart if permitted.

### Invalid destination

**Expected behavior:** Reject the public destination ID without revealing raw destination data.  
**Check:** Destination belongs to the current protected context and was not copied from another session.  
**User action:** Use a destination returned by the current flow.

### Delivery failure

**Expected behavior:** Downstream/system-safe response, commonly translated to `502 Bad Gateway`; no OTP in logs.  
**Check:** Provider HTTP result, timeout, credentials, template, network path.  
**Retry safety:** Use idempotency state. Do not manually repeat an uncertain provider request.

### Idempotency request in progress

**Expected behavior:** Conflict/waiting result.  
**Check:** Same action/key/request hash and processing record TTL.  
**User action:** Poll/retry using the same key only if the client contract permits it.

### Idempotency key reused with a different request

**Expected behavior:** Reject the call.  
**Check:** Client key generation and request-hash inputs.  
**User action:** Generate a new key for the new logical action.

### Missing/invalid CSRF or auth-flow session

**Expected behavior:** `403` for CSRF/origin cases or scenario-specific invalid-flow response.  
**Check:** Cookie path/domain/SameSite, XSRF header, origin, session expiry, browser credential mode.  
**User action:** Restart the flow. Do not bypass validation.

### Redis unavailable

**Expected behavior:** Safe system/downstream failure without topology, credentials, or raw key details.  
**Check:** Sentinel health, failover, mTLS, pool exhaustion, timeout, DNS/network.  
**Retry safety:** Determined by idempotency/unknown-outcome state.

### Verification succeeded but account/trust lookup failed

**Expected behavior:** Scenario-specific downstream error.  
**Check:** APIM call and mapping after OTP verification.  
**User action:** Follow the returned next step; do not ask the user to submit the OTP repeatedly if possession was already securely recorded and the approved state machine supports continuation.

## 4. Logging and observability

### Safe fields

- Request/correlation ID
- Scenario and action
- Status/outcome
- Error code
- HTTP status
- Duration
- Challenge/session age bucket, if approved
- Attempt/resend count, if not linkable to raw identity
- Masked destination
- Adapter/provider name

### Forbidden fields

- OTP code or OTP hash
- CSRF token/hash
- Session cookie value
- Idempotency replay payload
- Raw mobile/email
- Password
- Identity value/date of birth
- Redis credentials or certificate material
- Full protected context

Recommended structured events:

```text
otp.challenge.created
otp.delivery.requested
otp.delivery.failed
otp.verify.succeeded
otp.verify.failed
otp.challenge.locked
otp.resend.rejected
auth.flow.invalid
idempotency.replayed
idempotency.key_reused
```

Use correlation IDs, not sensitive identifiers, to connect events.

## 5. Test strategy

### 5.1 Domain and policy tests

Test:

- Valid/invalid policy construction
- Code length and character set
- Positive TTL and attempt values
- Required template placeholder
- Challenge status transitions
- Destination normalization/masking
- Redacted `toString()` output

### 5.2 Application-service tests

Use mocked ports and an injected `Clock`.

Essential send cases:

- Valid destination and successful delivery
- Invalid destination
- Session/scenario/state mismatch
- CSRF/origin failure
- Rate limited
- Delivery failure
- Failure before/after persistence
- Completed idempotency replay
- Duplicate in progress
- Key reused with a different request

Essential resend cases:

- Resend exactly before cooldown expiry
- Resend exactly at cooldown expiry
- Maximum resend boundary
- Previous code invalidated
- Verification attempts preserved/reset according to policy
- Concurrent resend requests
- Unknown delivery outcome

Essential verify cases:

- Correct code
- Incorrect code
- Remaining-attempt decrement
- Lock exactly at maximum attempts
- Expired exactly at the time boundary
- Replaced/invalidated code
- Challenge from another session/scenario
- Concurrent valid verification
- Completed replay
- Post-verification downstream failure

### 5.3 Redis adapter tests

Use an isolated Redis test environment where possible. Verify:

- Key format and prefix isolation
- No PII in keys
- TTL applied to every record
- JSON round trip
- Atomic counter behavior
- Idempotent state transitions
- Lock acquire/release ownership
- Safe failure translation
- Sentinel/failover behavior in integration environments

### 5.4 Web contract tests

Test each controller and mapper for:

- Required headers/cookies/body
- Request normalization
- Response envelope
- Error code/type/target/source
- `nextStep`
- HTTP status
- `Retry-After`
- Cookie set/clear attributes
- `X-Request-Id` echo
- No localized display text returned where the pre-login error contract intentionally returns codes only

### 5.5 Message adapter tests

Verify:

- Correct channel and template
- OTP substituted once
- Timeout/failure mapping
- Destination masking
- Credentials absent from logs/errors
- No retry for a non-idempotent provider action unless explicitly approved

### 5.6 Security tests

Include:

- CSRF mismatch
- Origin/referer mismatch
- Session fixation attempts
- Destination ID copied across sessions
- OTP copied across scenarios
- Brute-force attempt boundary
- Resend abuse boundary
- Idempotency abuse
- Redis key-injection attempts
- Log-redaction assertions
- Response-cache prevention for sensitive endpoints
- Cookie policy verification

### 5.7 End-to-end tests

Minimum flows:

1. Login start → destination select → send → verify → authenticated session/account selection.
2. Login resend → previous code rejected → new code accepted.
3. Registration start → initial OTP → verify → trust/username step.
4. Registration cooldown/rate-limit/lock recovery.
5. Cancel flow → cookies cleared → subsequent OTP call rejected.
6. Delivery provider outage.
7. Redis outage/failover.

## 6. Release evidence

Each OTP change should include:

- Requirement/decision traceability
- Updated architecture/reuse/runbook document where applicable
- Fail-first TDD evidence
- Unit, integration, and contract test results
- Security test results
- Configuration diff by environment
- Redis key/TTL impact
- Message-template approval
- Error-contract and frontend navigation impact
- Rollback plan
- Monitoring/alert changes
- Commit message

## 7. Production readiness checklist

- [ ] Policy values approved and startup-validated
- [ ] Redis Sentinel/mTLS verified
- [ ] All temporary records have TTLs
- [ ] No PII in keys
- [ ] Cookies use approved security attributes
- [ ] CSRF/origin rules enabled
- [ ] Idempotency covers every mutation
- [ ] Unknown outcomes are handled explicitly
- [ ] Rate limits and audit events enabled
- [ ] SMS/email credentials sourced from secret management
- [ ] Templates approved
- [ ] Logs and traces verified for redaction
- [ ] Dashboards and alerts available
- [ ] Runbook tested by someone other than the implementer
- [ ] Frontend handles `nextStep`, errors, and `Retry-After`
- [ ] Operations know when a user must restart rather than retry

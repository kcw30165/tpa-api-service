# Rate Limiting and Abuse-Prevention Integration Guide

## 1. Start with the threat model

Before adding a limiter, document:

- Protected action
- Abuse scenario
- Attacker capabilities
- Side effect or resource at risk
- Dimension to constrain
- Expected legitimate burst and sustained traffic
- Existing controls
- Required privacy behavior
- Fail-open/fail-closed policy
- User recovery and support impact

Do not choose “per IP” only because it is easy.

## 2. Select the control layers

Use the controls that match the threat:

```text
Repeated identical request
  -> idempotency

High request volume
  -> rate limit

Rapid OTP replacement
  -> resend cooldown + resend maximum

Repeated wrong OTP/CAPTCHA
  -> failed-attempt limit + challenge lock

Automated workflow start
  -> rate limit + CAPTCHA according to policy

Parallel workflow contexts
  -> session concurrency policy

Credential/account lock behavior
  -> TAS/APIM-owned policy
```

## 3. Implementation sequence

### Step 1 — Define or reuse a bucket

A bucket should describe action and dimension, for example:

```text
FORGOT_PASSWORD_START_BY_CLIENT
FORGOT_PASSWORD_OTP_SEND_BY_DESTINATION_HASH
FORGOT_PASSWORD_OTP_VERIFY_BY_SESSION
```

Reuse an existing bucket only when the threat, action semantics, policy ownership, and desired shared budget are genuinely the same.

### Step 2 — Define policy

Specify:

- Enabled flag
- Maximum attempts
- Window seconds
- Environment/profile behavior
- Failure mode
- Public error code
- Next step
- Audit event/severity

Validate positive values at startup. Avoid production defaults that silently disable a required security control.

### Step 3 — Derive a safe bucket ID

Use an approved generator:

- Destination → normalized, type-scoped SHA-256 ID
- Session → opaque validated session dimension or approved hash
- Client/IP → trusted source resolution plus one-way bucket identifier
- Subject/account → security-approved pseudonymous identifier

Never use raw input directly as the Redis key part.

### Step 4 — Place the check correctly

The check should occur after enough validation to derive a trustworthy dimension but before the expensive or externally visible side effect.

Example OTP send order:

```text
validate session/CSRF/scenario/state
  -> resolve authorized destination
  -> handle completed idempotency replay
  -> check destination rate limit
  -> create OTP challenge
  -> send message
  -> bind session
  -> complete idempotency
```

Ordering may differ by operation. Document whether invalid requests consume budget and why.

### Step 5 — Call `RateLimitPort`

Construct a validated command with bucket, opaque ID, policy, and correlation ID. Handle every result:

```text
DISABLED
  -> continue only if policy permits

ALLOWED
  -> continue

BLOCKED
  -> stop before side effect
  -> create security audit event
  -> return approved error and retry timing
```

### Step 6 — Map public behavior

Define:

- Stable error code
- Error type/severity/source
- Frontend target, usually `PAGE` or the protected field
- Workflow next step
- HTTP `429`
- Positive `Retry-After`
- Cookie/session cleanup behavior

Avoid revealing the bucket dimension or current counter.

### Step 7 — Audit safely

Publish a sanitized event with scenario, action, reason code, correlation ID, and approved pseudonymous bucket dimensions. Do not include raw inputs or secrets in metadata.

### Step 8 — Define Redis failure behavior

For every operation state explicitly:

```text
Redis unavailable
  -> fail closed / fail open / approved degraded behavior
```

Security-sensitive pre-auth operations should normally fail closed unless the security authority approves otherwise.

### Step 9 — Add tests and monitoring

Test exact boundaries, distributed concurrency, retry timing, privacy, Redis failure, and interplay with adjacent controls.

## 4. Destination bucket guidance

### Email

Normalization commonly includes trimming and case normalization according to the approved email contract. Do not over-normalize local parts unless the business identity rules permit it.

### Mobile

Normalize to the approved canonical country/number representation before hashing. Formatting differences must not create easy bypasses.

### Type separation

The same text in email and mobile channels must not collide. Include the destination type in the hash namespace or generator input.

## 5. IP/client guidance

Source-IP limiting is deployment-sensitive:

1. Identify trusted reverse proxies/load balancers.
2. Define authoritative header/socket source.
3. Reject or ignore untrusted forwarded values.
4. Decide IPv4/IPv6 canonicalization and prefix aggregation policy.
5. Consider NAT/shared-network false positives.
6. Hash or otherwise pseudonymize the approved dimension.
7. Avoid using IP as identity evidence.

The codebase exposes a Registration-start-by-IP bucket concept, but the operational source-IP policy must be explicitly documented and security-approved before relying on it. citeturn12search49

## 6. OTP verification guidance

Use both distributed and challenge-specific controls:

- Rate limit by session or approved subject dimension
- Increment challenge failed attempts on a wrong code
- Lock exactly at the configured maximum
- Audit failures and lock events
- Do not disclose whether the destination/account exists
- Keep resend, send, and verify budgets distinct

## 7. CAPTCHA guidance

Rate limiting can protect CAPTCHA creation/refresh and the Registration start endpoint. CAPTCHA answer failures remain challenge-state transitions. Avoid issuing unlimited fresh challenges to bypass the failed-attempt policy.

## 8. Forgot Password example

A proposed layered policy could be:

```text
Start
  client/IP or browser-derived bucket
  + CAPTCHA according to scenario policy
  + identity-enumeration-resistant response

OTP send
  destination-hash bucket
  + idempotency
  + eligible destination check

OTP resend
  destination bucket
  + challenge cooldown
  + maximum resend count
  + previous-code invalidation

OTP verify
  session bucket
  + challenge failed-attempt limit
  + lockout

Complete reset
  session/authorization validation
  + idempotency
  + TAS-owned password policy behavior
```

Exact limits and dimensions require security and TAS approval. Post-login Change Password is a separate context.

## 9. Policy-change procedure

For a limit/window/bucket change:

1. Identify threat and owner.
2. Compare current traffic and block rates.
3. Assess false positives and denial-of-service risk.
4. Review privacy and enumeration behavior.
5. Update configuration and validation.
6. Review existing Redis keys/window transition.
7. Update error/navigation and support guidance.
8. Test boundary, concurrency, and outage behavior.
9. Monitor after deployment with rollback thresholds.

## 10. Pull-request checklist

- [ ] Threat model documented
- [ ] Correct control selected
- [ ] Bucket meaning and dimension defined
- [ ] Safe bucket-ID generator used
- [ ] No PII/secrets in Redis keys or logs
- [ ] Policy values validated
- [ ] Check occurs before side effect
- [ ] Ordering with idempotency documented
- [ ] Retry-After behavior defined
- [ ] Audit event sanitized
- [ ] Fail-open/fail-closed approved
- [ ] Enumeration/privacy behavior reviewed
- [ ] Distributed concurrency tests added
- [ ] Redis outage tests added
- [ ] Fail-first TDD evidence included
- [ ] Operations/runbook updated
- [ ] Commit message included

Suggested commit message:

```text
docs(security): document rate limiting and abuse prevention
```

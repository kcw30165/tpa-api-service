# Rate Limiting and Abuse-Prevention Architecture

## 1. Architectural intent

Rate limiting is implemented behind an application port so workflow services depend on policy and outcomes rather than Redis commands.

```text
Workflow service
  Login / Registration / OTP / future workflow
        |
Bucket-ID derivation + policy resolution
        |
RateLimitPort
        |
RedisRateLimitAdapter
  key factory + Lua script + Clock
        |
Redis Sentinel deployment
```

## 2. Core contracts

### `RateLimitCommand`

The command carries concepts such as:

- Bucket type
- Opaque bucket ID
- Enabled flag
- Maximum attempts
- Window seconds
- Correlation ID

Validation requires a known bucket, safe bucket ID, and positive limit/window when enabled.

### `RateLimitResult`

The result contains:

- Status: `ALLOWED`, `BLOCKED`, or `DISABLED`
- Bucket
- Limit
- Remaining allowance
- `retryAfterSeconds`
- Reset time

A blocked result requires a positive retry delay. Disabled policy is explicit rather than represented by a missing adapter.

### `RateLimitRulePolicy`

Policy separates configurable security limits from adapter mechanics. It should identify enablement, maximum attempts, and window length for a specific abuse scenario.

## 3. Bucket model

`RateLimitBucket` defines the dimension and purpose of the counter. Known concepts represented in the codebase include:

```text
REGISTRATION_START_BY_IP
OTP_SEND_BY_DESTINATION_HASH
OTP_VERIFY_BY_REGISTRATION_SESSION
```

A bucket name is not merely a Redis namespace. It documents the security assumption: which actions are grouped, which subject/client/destination dimension is used, and what bypasses remain possible.

### Bucket-selection principles

- Use the narrowest dimension that mitigates the threat without creating easy denial of service against unrelated users.
- Combine dimensions only through reviewed policy; do not concatenate raw data.
- Consider shared networks, NAT, proxies, and multiple accounts using one destination.
- Do not use a user-controlled identifier without canonicalization and trust analysis.
- Avoid account-enumeration differences between existing and non-existing identities.

## 4. Opaque bucket IDs

The rate-limit adapter receives an opaque `bucketId`; it does not receive raw PII.

### Destination bucket IDs

`Sha256DestinationBucketIdGenerator` normalizes SMS/email destinations and produces a type-scoped SHA-256-derived identifier, conceptually:

```text
dest_EMAIL_<digest>
dest_MOBILE_<digest>
```

Normalization is part of the security contract. Equivalent destination formats must map consistently, while email and mobile namespaces remain separate.

### Session bucket IDs

Session-scoped verification limits use an opaque auth-flow session identifier or a safe one-way derivative according to the approved key convention. The raw value must not be logged or exposed as an audit dimension.

### IP/client bucket IDs

A Registration-start IP bucket requires an approved source-IP resolution policy. In proxy deployments, only trusted forwarded headers may influence the resolved address. The system should convert the approved client dimension into a safe bucket identifier rather than embedding raw network identifiers in Redis keys or logs.

## 5. Redis and Lua atomicity

`RedisRateLimitAdapter` uses a Lua script to combine increment and expiry behavior atomically. Conceptually:

```text
count = INCR key
if count == 1:
    EXPIRE key window
remainingTtl = TTL key
return count, remainingTtl
```

The actual script/adapter also validates command and result invariants.

Atomicity matters because multiple application instances can receive requests concurrently. A Java read-then-write sequence can lose increments or create counters without TTL.

### Result derivation

```text
count <= limit
  -> ALLOWED
  -> remaining = limit - count

count > limit
  -> BLOCKED
  -> retryAfterSeconds from Redis TTL
```

The reset time is derived consistently from the current clock and remaining TTL.

## 6. Redis key safety

A conceptual key is:

```text
<prefix>:rate-limit:<bucket>:<opaque-bucket-id>
```

Rules:

- Use the shared key factory.
- Reject blank/unsafe bucket IDs.
- Do not embed raw PII, IPs, session values, tokens, or free-form inputs.
- Apply a positive TTL to every bucket.
- Keep bucket names stable during the active window.
- Do not expose arbitrary pattern-based administration.

## 7. OTP abuse controls

Rate limiting works alongside OTP challenge policy.

### OTP send

A destination-scoped bucket can limit sends across sessions or repeated flow starts. After the distributed limit passes, the workflow still applies idempotency, eligible-destination checks, challenge creation, and message-delivery handling.

### OTP resend

Resend additionally enforces:

- Challenge resend cooldown
- Maximum resend attempts
- Previous-code invalidation policy
- Session/workflow state
- Idempotency

A cooldown is challenge-specific; the rate limit is a broader distributed budget. Both can produce retry timing, but their error code and recovery semantics remain distinct.

### OTP verification

Verification protection can combine:

- Session-scoped distributed rate limit
- Challenge-specific failed-attempt counter
- Challenge lock at maximum attempts
- Session expiry
- Security audit event

The distributed limit should not weaken the exact challenge lock boundary.

## 8. CAPTCHA abuse controls

CAPTCHA has its own TTL, failed-attempt count, lock, consume, and refresh lifecycle. Rate limiting may additionally protect challenge creation, refresh, or Registration start.

CAPTCHA does not replace destination/session rate limiting. It raises automation cost but does not guarantee a human user or verified identity.

## 9. Idempotency relationship

```text
Idempotency
  same action + key + request hash
  prevents duplicate processing/replays prior result

Rate limiting
  bucket + window
  constrains aggregate frequency across requests/keys
```

Check ordering must be deliberate. A completed idempotency replay should normally not consume another security budget or repeat side effects. A new logical action should be rate-limited according to policy.

## 10. Session and concurrency relationship

Single-active-flow rules limit concurrent workflow sessions but are not a substitute for rate limiting. Attackers can repeatedly replace sessions. Rate-limit dimensions may therefore include client, destination, subject, and/or session depending on the threat model.

## 11. Security audit model

`SecurityAuditEvent` includes concepts such as:

- Event type
- Outcome
- Severity
- Scenario
- Action
- Reason code
- Correlation ID
- Subject/client/destination bucket IDs
- Safe metadata
- Occurrence time

Relevant event types include rate-limit blocked, OTP verification failed, OTP challenge locked, message delivery failed, and Registration security rejected.

`LoggingSecurityAuditEventAdapter` sanitizes metadata and must not log raw identity, destination, session, OTP, CAPTCHA answer, password, token, or Redis key.

## 12. Public error and HTTP model

Rate-limited outcomes map through the workflow error contract to stable public codes, structured errors, `429 Too Many Requests`, and `Retry-After` where available.

Distinct outcomes should remain distinguishable:

```text
SEND_RATE_LIMITED
COOLDOWN_ACTIVE
MAX_RESEND_ATTEMPTS_REACHED
OTP_LOCKED
OTP_VERIFY_RATE_LIMITED
CAPTCHA_LOCKED
```

Do not map all abuse controls to one generic error if the frontend recovery behavior differs.

## 13. Failure modes

### Fail closed

Appropriate when bypassing the limiter would materially weaken a sensitive control, especially pre-authentication OTP or identity workflows. Redis failure becomes a safe system/unavailable result.

### Fail open

May be considered only for low-risk operations with explicit security approval, compensating controls, observability, and documented outage behavior.

### Degraded/fallback

A local in-memory fallback is not equivalent in a multi-instance deployment and must not be introduced silently. It can create inconsistent enforcement and restart bypasses.

## 14. Privacy and enumeration resistance

- Use identical public behavior where existence should remain private.
- Do not reveal bucket counts, raw limit dimensions, or whether another account consumed the budget.
- Return only approved `retryAfterSeconds` and public error codes.
- Retain bucket state only for the configured window.
- Keep audit dimensions pseudonymous and access-controlled.

## 15. Extension boundaries

### Reuse directly

- `RateLimitPort`
- Redis/Lua adapter
- Key factory and TTL behavior
- Destination bucket generator
- Result model and `Retry-After` mapping pattern
- Security audit port and sanitizer

### Configure or extend

- Bucket enum
- Rule policy
- Bucket-ID generator
- Failure mode
- Public error/navigation
- Audit event and safe dimensions

### Implement per protected operation

- Threat model
- Dimension selection
- Ordering with session, CAPTCHA, idempotency, and side effects
- Recovery behavior
- Security approval and monitoring threshold

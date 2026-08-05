# Rate Limiting and Abuse-Prevention Operations and Testing

## 1. Policy inventory

Maintain an approved inventory:

```text
Bucket
Protected action
Dimension source
Bucket-ID generator
Enabled
Limit
Window
Fail mode
Public error code
Next step
Audit event
Owner
```

Keep rate-limit policy separate from OTP resend cooldown, challenge attempts, CAPTCHA attempts, session TTL, idempotency TTL, and APIM retry settings.

## 2. Configuration reference

Review per environment:

- Bucket enablement
- Maximum attempts
- Window seconds
- Destination normalization rules
- Client/IP resolution policy
- Redis prefix and timeout
- Security audit enablement
- Public error/navigation mapping
- `Retry-After` behavior
- Fail-open/fail-closed mode

OTP policy also contains TTL, resend cooldown, maximum verify attempts, resend limit enablement, maximum resend attempts, previous-code invalidation, and attempt-reset behavior. CAPTCHA policy contains length/alphabet, case sensitivity, TTL, and maximum attempts. These are adjacent controls, not fields of the distributed rate-limit counter. citeturn12search49

## 3. Troubleshooting runbook

### Legitimate users are blocked

Check bucket dimension, normalization, NAT/shared destination behavior, configured limit/window, duplicate frontend calls, idempotency ordering, stale Redis TTL, and recent policy changes. Do not remove the bucket before assessing abuse and side effects.

### Limiter is not blocking

Check policy enablement, bucket enum/mapping, bucket-ID derivation, adapter wiring, Redis key prefix, Lua result mapping, and whether the check occurs before the side effect.

### `Retry-After` is missing or zero

Check Redis TTL, blocked-result validation, workflow result propagation, HTTP response helper, and whether the block came from another control such as challenge lock or maximum resend count.

### Equivalent destinations bypass limits

Check email/mobile canonicalization, country-code handling, whitespace/punctuation removal, case rules, and channel type separation. Avoid logging raw destinations during comparison.

### Different users incorrectly share a bucket

Check dimension design and hash inputs. This can be an expected consequence of a shared destination/IP policy or a collision/normalization defect. Review with security before changing.

### Redis outage disables protection

Verify the approved fail mode and error mapping. Ensure no hidden in-memory fallback or exception-swallowing path allows the side effect.

### Counter has no TTL

Treat as a defect. Check Lua/script deployment and first-write path. A persistent counter can permanently block users.

### Block rate spikes after release

Compare policy/config changes, frontend request patterns, new retries, proxy/IP resolution, destination normalization, Redis TTL, and actual abuse signals. Use correlation and pseudonymous bucket metrics only.

### CAPTCHA or OTP is locked but rate limit allows

This is expected: challenge attempt locks and distributed rate limits are independent controls. The most restrictive applicable outcome should stop the operation with the correct public recovery path.

## 4. Logging and security audit

### Safe fields

- Correlation/request ID
- Scenario and action
- Bucket enum
- Allowed/blocked/disabled status
- Limit and remaining count where approved
- Retry-after/reset bucket
- Pseudonymous bucket reference
- Public reason code
- Duration

### Forbidden fields

- Raw IP, email, mobile, identity, username, account, or session ID
- OTP/CAPTCHA answer or hashes used for verification
- CSRF/idempotency values
- Redis key/value
- Password or TAS credentials

Recommended events:

```text
rate_limit.allowed
rate_limit.blocked
rate_limit.disabled
rate_limit.redis_failed
otp.challenge.locked
otp.verification.failed
captcha.challenge.locked
registration.security.rejected
```

## 5. Metrics and alerts

Track by bucket/action/environment:

- Checks
- Allowed, blocked, and disabled results
- Block rate
- Remaining/retry-after distribution
- Unique pseudonymous buckets where approved
- Redis/script failures and latency
- OTP/CAPTCHA lock rates
- Resend cooldown and maximum-resend outcomes
- Security-audit publication failures

Alert on sudden block spikes, control unexpectedly disabled, Redis/script errors, persistent/no-TTL buckets, unusually high challenge-lock rates, and audit-pipeline failure.

## 6. Test strategy

### 6.1 Contract tests

- Valid enabled command
- Disabled command
- Zero/negative limit/window rejected when enabled
- Safe bucket-ID validation
- Allowed-result invariants
- Blocked result requires positive retry delay
- Remaining and reset-time invariants

### 6.2 Bucket generator tests

- Equivalent normalized destination yields same ID
- Different destination yields different ID
- Email/mobile type separation
- International mobile normalization
- Blank/malformed destination rejected
- No raw destination in ID, exception, or log
- Deterministic SHA-256 output

### 6.3 Redis/Lua adapter tests

Against isolated Redis:

- First increment creates TTL
- Counts up to limit are allowed
- First over-limit request is blocked
- TTL is not reset unexpectedly by later requests
- Retry delay matches remaining TTL
- Window expiry resets budget
- Script result validation
- Redis error becomes safe exception
- No persistent/no-TTL bucket

### 6.4 Concurrency tests

- Many parallel requests around the limit
- Exactly the approved number allowed
- No lost increments
- One stable TTL/window
- Multiple application instances against one Redis
- Concurrent first requests
- Failover during increment in an integration environment

### 6.5 Workflow integration tests

- Registration start checks correct bucket
- OTP send destination bucket blocks before delivery
- OTP verify session bucket blocks before verification side effects
- Completed idempotency replay does not consume/send again
- New request consumes according to policy
- Cooldown and rate-limit codes remain distinct
- Challenge lock remains enforced independently
- `429` and `Retry-After` map correctly
- Security audit emitted on block

### 6.6 CAPTCHA/OTP abuse tests

- CAPTCHA exact failed-attempt boundary
- CAPTCHA refresh/consume cannot bypass policy
- OTP exact verification-lock boundary
- Resend exactly before/at cooldown
- Maximum resend boundary
- Previous code invalid after resend when configured
- Multi-session destination send attempts share intended budget

### 6.7 IP/proxy tests

Where IP limiting is enabled:

- Direct client address
- Trusted proxy forwarded address
- Untrusted spoofed forwarded header ignored/rejected
- IPv4/IPv6 canonicalization
- Shared NAT behavior
- Missing client dimension
- No raw IP in Redis/logs

### 6.8 Failure-mode tests

- Redis unavailable under fail-closed policy
- Approved fail-open behavior, if any
- Audit sink failure
- Mapper/HTTP failure after a block
- Clock skew around reset time
- Corrupt/invalid script result

### 6.9 Privacy and enumeration tests

- Existing and non-existing identity paths expose equivalent allowed/block behavior where required
- No bucket count or dimension leaked publicly
- Error messages do not confirm destination/account existence
- Pseudonymous IDs inaccessible to clients
- Logs/APM contain no raw dimensions

## 7. Policy tuning procedure

1. Gather baseline traffic and blocked outcomes.
2. Separate legitimate retry/duplicate behavior from abuse.
3. Model shared-IP/destination false positives.
4. Propose limit/window and rollback threshold.
5. Obtain security approval.
6. Test with production-like concurrency.
7. Deploy configuration through controlled change.
8. Monitor blocks, failures, and user recovery.
9. Roll back if approved thresholds are exceeded.

Do not tune solely to suppress support tickets; investigate whether clients are producing duplicate calls or bypass patterns.

## 8. Incident response

For suspected abuse:

- Preserve request IDs and sanitized audit events.
- Identify bucket/action patterns without extracting raw PII from keys.
- Confirm Redis health and policy configuration.
- Coordinate with security and TAS owners where account-lock behavior is involved.
- Avoid ad hoc permanent blocks in Redis.
- Use approved configuration/deployment mechanisms.
- Record false positives and recovery actions.

For accidental over-blocking:

- Determine affected bucket scope and remaining TTL.
- Prefer expiry or approved capability-scoped remediation.
- Do not broadly delete unrelated security keys.
- Verify the root cause before changing limits.

## 9. Release evidence

Each change should include:

- Threat model and owner
- Bucket/dimension specification
- Policy values and justification
- Fail-mode approval
- Fail-first TDD evidence
- Bucket generator tests
- Redis/Lua concurrency tests
- Workflow and HTTP contract tests
- Privacy/enumeration review
- Audit/logging evidence
- Production-like load evidence
- Monitoring/rollback thresholds
- Commit message

## 10. Production readiness checklist

- [ ] Threat and protected side effect documented
- [ ] Bucket and dimension approved
- [ ] Safe bucket-ID derivation tested
- [ ] Limit/window validated and justified
- [ ] Check occurs before side effect
- [ ] Ordering with idempotency defined
- [ ] Cooldown/challenge limits remain distinct
- [ ] Redis Lua atomicity tested
- [ ] Every bucket has TTL
- [ ] Fail mode approved and tested
- [ ] `429` and `Retry-After` verified
- [ ] Audit events sanitized
- [ ] Privacy/enumeration tests pass
- [ ] Proxy/IP trust policy tested where applicable
- [ ] Metrics and alerts available
- [ ] Incident and over-blocking runbooks exercised
- [ ] Fail-first TDD evidence attached

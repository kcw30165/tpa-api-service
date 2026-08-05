# CAPTCHA Operations and Testing

## 1. Configuration reference

Review per environment/workflow:

- Enabled/required flag
- Answer length
- Allowed alphabet
- Case sensitivity
- TTL seconds
- Maximum attempts
- Image dimensions and renderer settings where configurable
- Font availability
- Refresh policy
- Creation/refresh/start rate limits
- Redis prefix/timeout
- Error/navigation definitions
- Security-audit behavior

The reviewed Registration policy uses length `7`, alphabet `23456789ABCDEFGHJKLMNPQSTUVWXYZ`, case-insensitive comparison, TTL `300` seconds, and maximum attempts `5`. Confirm the deployed profile. citeturn13search54

## 2. Redis record inventory

Maintain a record specification:

```text
Key namespace
Challenge ID format
JSON schema/version
Status values
Attempt fields
Lifecycle timestamps
TTL source
Administrative policy
Owner
```

CAPTCHA records must be temporary and must not contain raw answers or user PII.

## 3. Troubleshooting runbook

### Image does not render

Check MIME type, Base64 integrity, frontend data-URL handling, response size, image-generation failure, font availability, and content-security policy. Never inspect by logging the answer or full image payload in production logs.

### Users consistently fail valid-looking answers

Check case-sensitivity configuration, normalization parity between creation and verification, ambiguous font rendering, stale challenge ID after refresh, frontend whitespace handling, and clock/TTL behavior.

### Challenge expires too quickly

Compare policy TTL, Redis TTL, application/node clocks, frontend delay, and whether refresh/verification accidentally rewrites expiry. Do not extend TTL by accepting expired challenges.

### Attempts do not lock correctly

Check concurrent verification updates, exact boundary logic, persisted failed-attempt count, maximum-attempt value stored with the challenge, and Redis mutation behavior.

### Old challenge still works after refresh

Treat as a security defect. Verify old challenge status became `REFRESHED`, the frontend replaced the ID, and verification rejects refreshed state.

### Verified challenge cannot start Registration

Check TTL between verify and consume, challenge status, whether it was already consumed, whether refresh replaced it, and `RegistrationCaptchaGateService` outcome mapping.

### Same verified challenge starts Registration twice

Treat as a security defect. Check atomic consumption, concurrent requests, idempotency ordering, and whether downstream work occurs before consumption.

### Redis unavailable

Required CAPTCHA workflows should fail according to the approved security policy. Do not bypass the gate or keep untracked in-memory challenge state across a distributed deployment.

### CAPTCHA is unreadable

Review font, contrast, noise, character spacing, dimensions, and policy alphabet. Security distortion must be balanced against accessibility and legitimate completion rates.

## 4. Logging and audit

### Safe fields

- `X-Request-Id`
- Scenario and operation
- Outcome category
- Challenge status category
- Attempt/remaining-attempt bucket where approved
- Duration
- Error code
- Rate-limit result

### Forbidden fields

- Raw answer or answer hash
- CAPTCHA image/Base64 payload
- Challenge ID if treated as a security token
- Identity/contact/account data
- Session/CSRF/idempotency values
- Full Redis key/value

Recommended events:

```text
captcha.challenge.created
captcha.challenge.refreshed
captcha.verification.failed
captcha.challenge.verified
captcha.challenge.locked
captcha.challenge.consumed
captcha.consume.rejected
captcha.system.failed
```

## 5. Metrics and alerts

Track by scenario/environment without customer identifiers:

- Challenges created
- Refresh rate
- Verification success/failure rate
- Expired challenges
- Locked challenges
- Consume success/rejection rate
- Already-consumed/refreshed outcomes
- Image-generation failures and latency
- Redis failures
- Rate-limit blocks

Alert on render failures, Redis failures, sudden verification failure/lock spikes, repeated consumption defects, and unusual refresh volume.

## 6. Test strategy

### 6.1 Policy tests

- Valid policy
- Empty/alphabet-too-small policy
- Invalid length
- Zero/negative TTL
- Zero/negative maximum attempts
- Case-sensitive versus insensitive behavior
- Restricted alphabet contains only approved characters

### 6.2 Text-generator tests

- Correct length
- Every character belongs to alphabet
- Multiple calls produce varied values
- Invalid policy rejected
- SecureRandom dependency used
- No answer in `toString()` or exceptions

Avoid asserting exact random output.

### 6.3 Image-generator tests

- Valid PNG signature
- Correct MIME type
- Base64 decodes successfully
- Expected dimensions where configured
- Non-empty/randomized rendering
- Unsupported font fallback
- Unicode/config behavior if supported
- Rendering failure maps safely
- No answer/image content in logs

Do not use brittle pixel-perfect assertions for randomized noise.

### 6.4 Hasher tests

- SHA-256 hex shape
- Same normalized answer yields same hash
- Different answer yields different hash
- Case-sensitive distinction
- Case-insensitive match
- Constant-time comparison path
- Blank/invalid answer handling
- No clear answer in errors/logs

### 6.5 Challenge-domain tests

- Initial `CREATED` state
- Timestamp/expiry validation
- Attempt increment
- Exact lock boundary
- Verified transition
- Terminal-state rejection
- Consume outcomes
- Refresh outcomes
- Defensive immutability

### 6.6 Redis adapter tests

- Create with positive TTL
- Load/not found
- Mark verified
- Increment failed attempt
- Mark locked
- Mark consumed
- Mark refreshed
- Delete
- JSON round trip
- Exact TTL boundary
- Corrupt record
- Safe `CacheException`
- No raw answer in value/key

### 6.7 Concurrency tests

- Simultaneous wrong answers preserve all increments
- Correct/wrong race results in one valid terminal transition
- Concurrent verifies do not bypass maximum attempts
- Concurrent consume permits one winner
- Refresh versus verify race
- Refresh versus consume race
- Redis failover during mutation in integration environment

### 6.8 Use-case tests

#### Create

- Successful challenge
- Policy disabled/invalid
- ID collision/reservation failure where supported
- Image generation failure
- Hashing failure
- Redis failure

#### Verify

- Valid
- Invalid with attempts remaining
- Locked at boundary
- Expired
- Not found
- Already verified
- Consumed
- Refreshed
- Redis failure

#### Refresh

- Successful replacement
- Old challenge unusable
- Invalid/expired/locked/consumed input
- New challenge creation failure
- Partial-failure behavior

### 6.9 Registration-gate tests

- Verified challenge consumed and Registration proceeds
- Not verified
- Not found
- Expired
- Locked
- Already consumed
- Refreshed
- Concurrent duplicate Registration starts
- CAPTCHA consumption occurs before APIM/member lookup
- Idempotency replay does not consume/execute twice
- Correct workflow errors and next steps

### 6.10 Web/contract tests

- Required `X-Request-Id`
- Request-body validation
- Challenge ID/answer validation
- Create/refresh/verify response shape
- Image and MIME fields
- Error codes/types/targets/severity/source
- HTTP status
- No answer in any response
- Body logging disabled/excluded for CAPTCHA image payload where configured

### 6.11 Security and accessibility tests

- Brute-force boundary
- Unlimited refresh prevented
- Challenge copied across workflows/sessions according to binding policy
- Image metadata does not contain answer
- Browser/proxy cache-control behavior
- Keyboard/screen-reader labels in frontend contract
- Approved accessible alternative retains equivalent anti-abuse controls
- Logs/APM contain no answer/hash/image/challenge secret

## 7. Policy tuning procedure

1. Measure completion, refresh, expiry, and lock rates.
2. Separate unreadability/usability failures from automation.
3. Review alphabet, rendering difficulty, TTL, and attempts.
4. Assess accessibility impact.
5. Obtain security/content/accessibility approval.
6. Test production-like concurrency and browsers.
7. Deploy as controlled configuration/code change.
8. Monitor and retain rollback thresholds.

Do not weaken CAPTCHA solely to reduce failures without checking whether rendering, frontend state, or clock behavior is defective.

## 8. Incident response

For suspected CAPTCHA bypass:

- Preserve request IDs and sanitized audit events.
- Verify challenge status transitions and consumption ordering.
- Inspect aggregate bucket/status metrics, not raw answers.
- Confirm Redis health and TTL.
- Review recent policy/renderer/deployment changes.
- Coordinate with the security authority.
- Do not dump challenge values or images from Redis into tickets.

For over-locking or unreadability:

- Check exact policy and renderer version.
- Evaluate affected browsers/fonts and refresh rates.
- Use approved configuration rollback.
- Do not manually mark challenges verified.

## 9. Release evidence

Each CAPTCHA change should include:

- Threat model and security owner
- Policy/configuration diff
- Image/content/accessibility review
- Fail-first TDD evidence
- Generator/hasher tests
- Lifecycle and Redis tests
- Concurrency/consume tests
- Registration-gate tests
- Rate-limit and error-contract tests
- Logging/audit redaction evidence
- Browser compatibility evidence
- Rollback and monitoring plan
- Commit message

## 10. Production readiness checklist

- [ ] Policy approved and startup-validated
- [ ] Secure text generation verified
- [ ] Raw answer never returned or persisted
- [ ] Hashing and constant-time comparison tested
- [ ] Image rendering usable and reviewed
- [ ] Redis TTL and lifecycle transitions tested
- [ ] Exact lock boundary tested
- [ ] Refresh invalidates old challenge
- [ ] Consumption is one-time and precedes side effects
- [ ] Rate limits and audit events enabled
- [ ] Required workflows fail closed on Redis failure
- [ ] Errors/navigation and HTTP mapping tested
- [ ] Accessibility alternative/recovery approved
- [ ] Logs/APM contain no answer/hash/image/security token
- [ ] Metrics and alerts available
- [ ] Incident runbook exercised
- [ ] Fail-first TDD evidence attached

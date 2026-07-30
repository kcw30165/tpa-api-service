# CAPTCHA Architecture and Lifecycle

## 1. Architectural intent

The CAPTCHA capability follows ports and adapters. Application services own lifecycle decisions; security adapters generate and hash values; Redis persists challenge state; web adapters expose create, refresh, and verify operations.

```text
Web controller / mapper
        |
CAPTCHA use case
  create / refresh / verify
        |
Application ports
  image generator
  answer hasher
  challenge ID generator
  challenge persistence
        |
Security and Redis adapters
```

Registration consumes the verified challenge through a separate gate service. Verification and workflow consumption are intentionally different operations.

## 2. Core domain model

### `CaptchaPolicy`

Policy contains:

- Answer length
- Allowed alphabet
- Case-sensitivity behavior
- Challenge TTL
- Maximum failed attempts

Policy is resolved through the workflow-policy boundary and validated before use.

### `CaptchaChallengeId`

Challenge IDs are opaque, validated, and use a `cap_`-style prefix with a restricted URL/header-safe character set. They contain no user identity or business data.

### `CaptchaAnswerHash`

Represents a validated SHA-256 hexadecimal hash. The raw answer is never part of the persisted challenge.

### `CaptchaChallenge`

Challenge state includes concepts such as:

- Challenge ID
- Answer hash
- Status
- Failed-attempt count
- Maximum attempts
- Created and expiry timestamps
- Verified timestamp
- Consumed timestamp
- Refreshed timestamp
- Locked timestamp

### Status model

```text
CREATED
VERIFIED
CONSUMED
REFRESHED
EXPIRED
LOCKED
```

A challenge cannot transition from a terminal/unusable status back to `CREATED` or `VERIFIED`.

## 3. Answer generation

`SecureRandomCaptchaTextGenerator` uses the configured alphabet and length with a cryptographically secure random source.

Rules:

- Reject empty/unsafe alphabets.
- Reject invalid lengths.
- Generate every character independently from approved entropy.
- Do not log or expose generated answer text.
- Avoid ambiguous characters through policy, not ad hoc renderer logic.

The reviewed alphabet excludes commonly confused values such as `0`, `1`, `I`, and `O`. citeturn13search54

## 4. Image generation

`JavaCaptchaImageGeneratorAdapter` renders the generated text into a PNG CAPTCHA image. It applies randomized visual elements such as fonts, foreground/background colors, and noise, then returns a `CaptchaImage` containing Base64 image data and MIME type.

### Image rules

- The answer is rendered only into the generated image.
- The raw answer is not returned in the public response.
- Image MIME type is explicit.
- Rendering failure is a system failure, not a blank challenge.
- Image content and Base64 data are sensitive operationally and should not be logged.
- Random visual transformations must not make the challenge unusable for legitimate users.

## 5. Answer hashing

`Sha256CaptchaAnswerHasher`:

1. Applies the configured case-sensitivity normalization.
2. Hashes the normalized answer with SHA-256.
3. Produces a validated hexadecimal `CaptchaAnswerHash`.
4. Compares submitted and stored hashes in constant time.

Case-insensitive mode must use the same normalization during creation and verification. Do not trim or transform answers differently between the two paths unless the policy explicitly defines it.

## 6. Challenge creation

A conceptual creation flow is:

```text
Resolve CAPTCHA policy
  -> generate opaque challenge ID
  -> generate answer text
  -> render image
  -> hash answer
  -> create CREATED challenge with attempts=0 and expiresAt
  -> persist with Redis TTL
  -> return challengeId, imageBase64, mimeType, expiry metadata
```

Persistence must succeed before a usable response is returned. If Redis fails, the client must not receive an image for a challenge the server cannot verify.

## 7. Verification lifecycle

```text
Load challenge by ID
  -> NOT_FOUND if absent
  -> EXPIRED if TTL/time elapsed
  -> reject LOCKED / CONSUMED / REFRESHED
  -> handle already VERIFIED according to approved contract
  -> hash submitted answer
  -> constant-time compare
```

### Correct answer

The challenge transitions from `CREATED` to `VERIFIED`, records `verifiedAt`, and remains available only for the approved consumption window.

### Incorrect answer

The failed-attempt counter increments. If the new count reaches the configured maximum, the challenge transitions to `LOCKED` and records `lockedAt`; otherwise it remains `CREATED`.

### Concurrency

Two concurrent verification attempts must not both observe the same old attempt count and bypass the lock boundary. The persistence adapter/service contract must preserve deterministic attempt accounting.

## 8. Refresh lifecycle

Refresh creates a new challenge/image and makes the previous challenge unusable.

```text
Load old challenge
  -> validate refresh eligibility
  -> mark old challenge REFRESHED
  -> generate and persist new challenge
  -> return new challengeId and image
```

Important behaviors:

- The old answer must no longer verify.
- Refresh must not reset an unlimited automation budget.
- Refresh endpoints can require separate rate limiting.
- Partial failure between invalidating the old challenge and creating the new one must return a safe restart/retry result.
- The frontend must replace both challenge ID and image atomically from its perspective.

## 9. Consumption lifecycle

Verification proves that the submitted answer matched the challenge. Consumption proves that a workflow used that verification once.

`ConsumeCaptchaChallengeResult` distinguishes outcomes such as:

```text
CONSUMED
NOT_FOUND
NOT_VERIFIED
EXPIRED
LOCKED
ALREADY_CONSUMED
REFRESHED
```

Registration starts only after an approved `CONSUMED` result. A verified challenge copied into another request, session, or scenario must not become a reusable bypass.

## 10. Registration gate

`RegistrationCaptchaGateService` consumes the challenge as a precondition for Registration start.

A conceptual sequence is:

```text
Validate Registration input and CAPTCHA challenge ID
  -> apply Registration start abuse controls
  -> verify/require prior CAPTCHA verification according to API contract
  -> consume challenge
  -> perform member/identity lookup
  -> create auth-flow session and CSRF context
  -> create/send OTP
```

The gate maps challenge outcomes into Registration-specific error keys and recovery navigation.

## 11. Redis persistence

`RedisCaptchaChallengeAdapter` uses:

- Capability-specific key factory and `auth:captcha`-style namespace
- JSON mapper/wire record
- Reactive Redis operations
- Positive TTL derived from `expiresAt`
- Lifecycle mutations for verified, failed-attempt, locked, consumed, refreshed, and delete
- Safe `CacheException` translation

No answer, identity, destination, session, or account data belongs in the Redis key.

## 12. Public API and error model

The web capability exposes create, refresh, and verify request/response DTOs under the auth API. Responses use the common mutation/workflow envelope where applicable.

Public error conditions include:

- Required/missing challenge
- Invalid answer
- Not found
- Expired
- Locked
- Already consumed
- Refreshed/replaced
- Refresh failed
- System/Redis failure
- Rate limited, where configured

HTTP and workflow mapping should distinguish malformed input, business/security rejection, rate limiting, conflict, and system unavailability using the shared error contract.

## 13. Security auditing and logging

Relevant security events can include:

- Challenge created
- Verification failed
- Challenge locked
- Challenge consumed
- Refresh rejected
- Rate limit blocked
- Registration security rejected

Audit metadata must be sanitized. Do not record raw answer, hash, challenge ID where it is considered a security token, image data, identity, contact, session, IP, or full Redis key.

## 14. Accessibility boundary

A visual CAPTCHA can create accessibility barriers. The codebase's image challenge should therefore be treated as a security mechanism requiring an approved accessible alternative or assisted recovery path. Any alternative must preserve comparable anti-abuse controls and must not expose the answer through metadata, audio URLs without protection, or predictable content.

## 15. Extension boundaries

### Reuse directly

- Secure text generator
- Challenge ID generator
- Image generator port/adapter
- SHA-256 hasher and constant-time matching
- Challenge port and Redis adapter
- Create/refresh/verify use cases
- Registration gate pattern
- Error/audit patterns

### Configure or extend

- Policy length/alphabet/case/TTL/attempts
- Image rendering parameters
- Workflow error/navigation
- Rate-limit rules
- Accessible alternative

### Implement per workflow

- Whether CAPTCHA is required
- When it is issued and consumed
- Which session/scenario it is bound to
- Recovery after expiry/lock/refresh
- Public workflow next steps

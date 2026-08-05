# CAPTCHA Capability

> **Status:** Documents the CAPTCHA implementation represented by the reviewed codebase snapshot.  
> **Audience:** New team members, backend developers, security reviewers, frontend developers, testers, and support engineers.  
> **Scope:** CAPTCHA generation, image transport, answer hashing, Redis challenge state, verification, refresh, consumption, Registration gating, errors, auditing, and reuse.

## Start here

CAPTCHA is implemented as a reusable, server-side challenge capability. The server generates the answer and image, persists only the answer hash, and manages the challenge through a bounded lifecycle.

```text
Create challenge
  -> generate secure answer text
  -> render PNG image
  -> hash normalized answer
  -> persist challenge in Redis with TTL
  -> return challengeId + Base64 image + MIME type

Verify
  -> load challenge
  -> enforce status, TTL, and attempt limit
  -> hash submitted answer
  -> compare in constant time
  -> mark VERIFIED or increment/lock

Registration start
  -> consume verified challenge once
  -> reject missing, expired, locked, refreshed, or consumed challenge
```

Read the documents in this order:

1. **[Architecture and lifecycle](architecture-and-lifecycle.md)** — understand generators, hashing, images, Redis state, verification, refresh, consumption, and Registration gating.
2. **[Integration and reuse guide](integration-and-reuse-guide.md)** — follow the implementation recipe when adding CAPTCHA to another pre-authentication workflow.
3. **[Operations and testing](operations-and-testing.md)** — configure, troubleshoot, monitor, test, and release CAPTCHA changes safely.

## Main components

- `CreateCaptchaChallengeUseCase`
- `RefreshCaptchaUseCase`
- `VerifyCaptchaUseCase`
- `RegistrationCaptchaGateService`
- `CaptchaChallengePort`
- `CaptchaImageGeneratorPort`
- `CaptchaAnswerHasherPort`
- `CaptchaChallengeIdGenerator`
- `SecureRandomCaptchaTextGenerator`
- `JavaCaptchaImageGeneratorAdapter`
- `Sha256CaptchaAnswerHasher`
- `RedisCaptchaChallengeAdapter`

## Security purpose

CAPTCHA raises the cost of automated abuse. It does **not** prove identity, establish authentication, authorize a business action, replace rate limiting, or replace OTP.

```text
CAPTCHA
  automation-resistance challenge

Rate limit
  constrains aggregate request volume

OTP
  possession verification for an approved destination

Session + CSRF
  binds browser workflow state and protects mutations

Idempotency
  prevents duplicate processing
```

## Current consumer

Registration uses CAPTCHA as its configured pre-start challenge. `RegistrationCaptchaGateService` consumes a successfully verified challenge before Registration proceeds with identity/member lookup, auth-flow session creation, OTP creation, and message delivery. citeturn13search54

## Current configurable policy

The reviewed configuration uses a seven-character, case-insensitive CAPTCHA, a restricted alphabet that excludes easily confused characters, a 300-second TTL, and five attempts. These are environment/policy values rather than architectural constants. citeturn13search54

## Core rules

1. Generate answers using `SecureRandom`.
2. Return the image, never the answer.
3. Persist only the answer hash.
4. Bind policy values to the stored challenge.
5. Verify against server-side challenge state.
6. Use constant-time hash comparison.
7. Increment failed attempts atomically enough for concurrent requests.
8. Lock exactly at the configured boundary.
9. Refresh invalidates the previous challenge for future use.
10. A verified challenge is not sufficient until the workflow consumes it.
11. Consumption is one-time and scenario-bound.
12. CAPTCHA IDs, answers, hashes, and images must not enter logs.
13. Redis failure must not silently bypass a required CAPTCHA gate.
14. Accessibility and retry behavior must be part of the frontend contract.

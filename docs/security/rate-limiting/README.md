# Rate Limiting and Abuse Prevention

> **Status:** Documents the rate-limiting and related abuse-prevention implementation represented by the reviewed codebase snapshot.  
> **Audience:** New team members, backend developers, security reviewers, testers, support engineers, and platform engineers.  
> **Scope:** Distributed rate limiting, bucket design, opaque bucket IDs, policy rules, Redis/Lua atomicity, `Retry-After`, security auditing, OTP/CAPTCHA/session protections, failure modes, and reuse.

## Start here

Rate limiting is one part of a layered abuse-prevention model. It constrains how frequently an action may occur within a defined window, but it does not replace authentication, CSRF protection, CAPTCHA, OTP attempt limits, idempotency, session concurrency, or TAS-owned account lockout.

```text
Request
  -> session / CSRF / workflow validation as applicable
  -> derive approved opaque bucket ID
  -> resolve rate-limit policy
  -> RateLimitPort.checkAndConsume(...)
  -> Redis Lua increment + expiry
  -> ALLOWED / BLOCKED / DISABLED
  -> workflow result + security audit + Retry-After
```

Read the documents in this order:

1. **[Architecture and abuse model](architecture-and-abuse-model.md)** — understand buckets, policies, Redis atomicity, layered controls, audit events, and failure modes.
2. **[Integration and policy guide](integration-and-policy-guide.md)** — follow the implementation recipe when protecting a new operation.
3. **[Operations and testing](operations-and-testing.md)** — configure, test, troubleshoot, monitor, and operate the controls safely.

## Implemented rate-limit model

The shared contracts include:

- `RateLimitPort`
- `RateLimitCommand`
- `RateLimitResult`
- `RateLimitStatus`
- `RateLimitBucket`
- `RateLimitRulePolicy`
- `DestinationBucketIdGenerator`
- Redis-backed atomic rate-limit adapter
- Security audit events

Known bucket concepts include:

- Registration start by client/IP dimension
- OTP send by destination hash
- OTP verification by Registration session

The model supports enabled/disabled policies, maximum attempts, window duration, remaining allowance, reset time, and positive retry delay for blocked requests. citeturn12search49

## Layered controls

```text
Rate limit
  constrains volume per bucket/window

OTP resend cooldown
  constrains replacement frequency for a challenge

OTP/CAPTCHA attempt limit
  locks a challenge after repeated failures

Idempotency
  prevents duplicate execution of the same logical request

Session concurrency
  controls simultaneous auth-flow contexts

CSRF and Origin/Referer
  protect browser-bound mutations

TAS account/password lockout
  remains owned by TAS/APIM policy
```

A workflow often applies several of these controls together.

## Core rules

1. Define the abuse threat before selecting a bucket.
2. Never put raw IP, email, mobile, identity, session, token, or account data in Redis keys.
3. Use opaque or one-way-derived bucket IDs.
4. Normalize destination data before hashing.
5. Use the shared Redis/Lua adapter for atomic counter-plus-expiry behavior.
6. Apply the control before the protected side effect.
7. Return `Retry-After` only from an approved positive retry delay.
8. Record blocked outcomes through sanitized security auditing.
9. Define fail-open or fail-closed behavior explicitly for every protected action.
10. Do not confuse provider/APIM retry policy with inbound abuse limiting.
11. Avoid revealing whether an account or destination exists through bucket behavior.
12. Test distributed concurrency and exact window boundaries.

## Responsibility boundaries

The security authority owns approval of pre-authentication anti-abuse controls. TAS owns its external account/password-lock behavior. The BFF owns correct implementation of approved rate-limit buckets, Redis state, public error mapping, audit events, and workflow recovery behavior.

# Redis and Distributed State

> **Status:** Documents the Redis implementation represented by the reviewed codebase snapshot.  
> **Audience:** New team members, backend developers, reviewers, testers, support engineers, and platform engineers.  
> **Scope:** Redis-backed cache and distributed state, key design, TTL, JSON persistence, atomic operations, locks, Sentinel, mTLS, administration, failure handling, and reuse.

## Start here

Redis is not used only as a cache. It is the distributed state and coordination foundation for:

- Pre-authentication and authenticated sessions
- OTP and CAPTCHA challenges
- Login and Registration idempotency
- Login replay snapshots
- Rate-limit counters
- Auth-flow completion locks
- Reference-date caching and administration

Read the documents in this order:

1. **[Architecture and key model](architecture-and-key-model.md)** — understand ports, adapters, key factories, JSON records, TTL, atomicity, locks, Sentinel, and mTLS.
2. **[Integration and reuse guide](integration-and-reuse-guide.md)** — follow the implementation recipe when adding a Redis-backed capability.
3. **[Operations and testing](operations-and-testing.md)** — configure, test, troubleshoot, monitor, and operate Redis safely.

## High-level architecture

```text
Application service
    -> capability-specific port
    -> Redis adapter
       + key factory
       + JSON mapper
       + TTL/lifecycle rules
       + atomic operation where required
    -> Reactive Redis / Lettuce
    -> Redis Sentinel deployment over TLS/mTLS
```

Application code must not depend on Redis types or construct Redis keys directly.

## Two persistence patterns

### Generic cache

`CachePort` provides string-value get, set-with-TTL, and eviction. `CacheAdminPort` restricts administrative scans and deletion to approved capabilities such as Reference Date.

### Capability-specific state

Dedicated ports and adapters own richer lifecycle behavior for sessions, OTP, CAPTCHA, idempotency, rate limiting, replay records, and locks. Use these instead of treating structured workflow state as an arbitrary cache string.

## Core rules

1. Every key is created by a key factory.
2. Keys contain opaque technical identifiers only—never PII, credentials, tokens, OTPs, or business account data.
3. Every temporary record has a positive TTL.
4. Lifecycle timestamps and Redis TTL must agree.
5. JSON formats are persistence contracts, not internal implementation details.
6. Atomicity is required for reservation, counters, and lock ownership.
7. Redis failures are translated into safe `CacheException`-style errors.
8. Broad wildcard deletion is forbidden; administration is capability-scoped.
9. Sentinel and mTLS are part of the production design, not optional afterthoughts.
10. No workflow may silently bypass Redis when doing so would weaken security or deduplication.

## Current key convention

The shared key factory follows a shape similar to:

```text
<prefix>:<capability>[:<opaque-part>...]
```

The reviewed configuration uses `ngtpa` as the default prefix. Examples of capabilities include authenticated session, auth-flow session, OTP, CAPTCHA, idempotency, rate limit, and `reference-date`. Treat exact prefixes and property values as environment configuration.

## Reuse decision

Use `CachePort` for a simple cache entry whose value can be safely reconstructed. Use a dedicated capability port when the data has status transitions, atomic counters, ownership, replay semantics, encryption, or non-trivial failure behavior.

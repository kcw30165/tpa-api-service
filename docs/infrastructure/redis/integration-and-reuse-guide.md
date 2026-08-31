# Redis Integration and Reuse Guide

## 1. Choose generic cache or dedicated persistence

Use `CachePort` when:

- The value is simple
- It can be reconstructed safely
- Get/set/evict with TTL is sufficient
- There are no complex state transitions

Create a capability-specific port/adapter when:

- The state has lifecycle statuses
- Multiple fields must change consistently
- Atomic reservation/counter/ownership is required
- Replay or unknown-outcome behavior exists
- Sensitive fields need protection
- Domain-specific load/update results matter

## 2. Implementation sequence

### Step 1 — Define data ownership and lifecycle

Document:

- Capability owner
- Record purpose
- Creation and termination events
- Required fields
- Status transitions
- TTL and retention
- Concurrency behavior
- Failure behavior
- Cleanup/administration behavior
- Whether values require encryption

Do not start from a Redis key.

### Step 2 — Define an application port

The port expresses capability operations without Redis types.

Bad boundary:

```text
Mono<String> getRedisJson(String key)
```

Preferred boundary:

```text
Mono<ChallengeLookupResult> findChallenge(ChallengeId id)
```

The exact contract should use repository domain terminology.

### Step 3 — Define a capability namespace

Add a stable key-factory namespace. Use only opaque validated identifiers as parts.

Document the key shape without including real customer examples:

```text
ngtpa:<capability>:<opaque-id>
```

Never concatenate free-form input directly.

### Step 4 — Define the persistence record

Create a Redis wire record distinct from the domain object where useful. Include explicit timestamps and a schema/version field when compatibility risk warrants it.

Decide how old records are handled during rolling deployment.

### Step 5 — Implement a JSON mapper

Test both directions independently:

- Domain to record/JSON
- JSON/record to domain
- Missing/unknown fields
- Unknown enum/status
- Invalid timestamps
- Corrupt protected payload

Deserialization failure must map safely and must not trigger duplicate side effects.

### Step 6 — Define TTL ownership

State whether TTL comes from:

- Domain `expiresAt`
- Scenario policy
- Idempotency action policy
- Cache configuration
- Lock lease

Calculate with an injected `Clock` where time boundaries matter. Reject non-positive values.

### Step 7 — Select the atomic primitive

Ask what happens under two application instances.

- First-writer reservation → set-if-absent
- Counter with fixed window → Lua increment/expiry
- Lock release → compare ownership token and delete atomically
- Mutable state transition → capability-specific atomic/update strategy

Do not implement read-modify-write without reviewing lost-update behavior.

### Step 8 — Map failures

Define behavior for:

- Not found
- Expired
- Malformed record
- Serialization failure
- Connection timeout
- Sentinel failover
- TLS/mTLS failure
- Pool exhaustion
- Partial operation/unknown outcome

Security controls usually fail closed. Cache-oriented data may use an approved fallback.

#### Step 9 — Wire configuration

Reuse `RedisAdapterConfig`, `RedisCacheProperties`, the central `LettuceConnectionFactory`, the single application-owned `ReactiveStringRedisTemplate`, the SSL bundle, and conditional bean patterns.

- Inject `ReactiveStringRedisTemplate` only in outbound adapter or Redis operations code.
- Reuse the bean named `reactiveStringRedisTemplate`.
- Do not create a capability-local template bean.
- Do not inject `ReactiveRedisTemplate<String, String>`.
- Do not add aliases, `@Primary`, or `@Qualifier` to mask duplicate templates.
- Keep Spring Data Redis types out of application ports and use cases.

### Step 10 — Add observability without sensitive data

Use capability, operation, status, duration, and request ID. Avoid full keys and all stored values.

### Step 11 — Test before release

Add domain/mapper/adapter/concurrency/TTL/failover tests and verify the deployment configuration.

## 3. Key-design checklist

- [ ] Stable capability namespace
- [ ] Environment/application prefix used
- [ ] All parts validated
- [ ] No PII or secrets
- [ ] No unbounded free-form values
- [ ] Administrative pattern is capability-scoped
- [ ] Key remains stable for record lifetime
- [ ] Logs do not print full key

## 4. TTL-design checklist

- [ ] Positive TTL always applied
- [ ] TTL source documented
- [ ] Domain expiry and Redis TTL agree
- [ ] Clock-boundary behavior tested
- [ ] Touch/refresh behavior explicit
- [ ] Absolute expiry not exceeded
- [ ] Terminal retention explicit
- [ ] Privacy/retention impact reviewed

## 5. Adding a distributed lock

Use the existing lock abstraction and ownership pattern:

1. Define the protected resource and lock namespace.
2. Generate a unique ownership token.
3. Acquire with set-if-absent and positive lease.
4. Enter the critical section only if acquired.
5. Complete within the lease or define renewal policy.
6. Release with atomic compare-and-delete.
7. Treat lock contention as a business/concurrency outcome, not a Redis deletion opportunity.

A lock does not replace idempotency. Idempotency identifies repeated logical requests; a lock serializes work on a shared resource.

## 6. Adding a rate-limit bucket

1. Define the threat and bucket dimension.
2. Generate an opaque/hashed bucket ID.
3. Configure maximum attempts and window.
4. Use the shared atomic Lua-based adapter.
5. Return remaining/retry/reset metadata according to policy.
6. Audit blocked outcomes without logging identity or destination.
7. Define fail-open/fail-closed behavior for Redis failure.

## 7. Adding an administrative cache operation

Only add a `CacheCapability` for approved administrable cache data. Do not expose arbitrary pattern or key input through HTTP.

The application service should call `CacheAdminPort`, return sorted/safe entries or a deleted count, and map Redis failure through the standard response contract.

## 8. Schema-change procedure

For a Redis record change:

1. Identify maximum old-record lifetime.
2. Decide backward-compatible read or controlled invalidation.
3. Version the record where needed.
4. Deploy readers before writers when rolling compatibility requires it.
5. Test mixed-version fixtures.
6. Ensure corrupt/old idempotency records do not cause side-effect re-execution.
7. Update runbook and rollback instructions.

## 9. Worked example: Forgot Password state

Forgot Password should reuse existing adapters where the domain matches:

- `AuthFlowSessionPort` for workflow session state
- OTP challenge adapter for OTP lifecycle
- Idempotency adapters/pattern for start/send/resend/verify/complete
- Rate-limit adapter for abuse controls
- Completion lock if password reset completion requires serialization

Add only scenario-specific records/key factories when shared models cannot represent required state safely. Do not create one monolithic `forgot-password` Redis JSON containing session, OTP, idempotency, and rate limits.

## 10. Pull-request checklist

- [ ] Data owner and lifecycle documented
- [ ] Correct generic/dedicated port chosen
- [ ] Key factory used
- [ ] No PII/secrets in keys
- [ ] Wire record and compatibility reviewed
- [ ] Positive TTL enforced
- [ ] Atomicity/concurrency reviewed
- [ ] Sensitive value protection applied
- [ ] Failure mode and fallback defined
- [ ] Admin behavior capability-scoped
- [ ] Mapper and mixed-version tests added
- [ ] Redis integration and concurrency tests added
- [ ] Sentinel/mTLS impact reviewed
- [ ] Fail-first TDD evidence included
- [ ] Operations document updated
-  One canonical `ReactiveStringRedisTemplate` reused; no generic or duplicate template bean introduced
- [ ] Commit message included

Suggested commit message:

```text
docs(redis): document distributed state architecture and reuse
```

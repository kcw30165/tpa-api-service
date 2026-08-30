# Redis Architecture and Key Model

## 1. Architectural intent

Redis infrastructure is isolated behind application ports. Each capability owns its lifecycle semantics while sharing connection, key, serialization, and safe-failure infrastructure.

```text
application/port/out
  CachePort / AuthFlowSessionPort / AuthenticatedSessionPort /
  OtpChallengeStatePort / CaptchaChallengePort /
  Idempotency ports / RateLimitPort / Lock ports
        |
adapter/out/redis
  adapter + key factory + JSON mapper + Redis operations
        |
RedisAdapterConfig
  connection factory, one ReactiveStringRedisTemplate, Sentinel, SSL, pool
```

## 2. Shared infrastructure

### `RedisCacheProperties`

Externalizes concepts including:

- Enabled flag
- Key prefix
- Password and timeout
- Sentinel master, nodes, and password
- SSL enabled flag and SSL bundle
- Lettuce pool limits and wait time
- Shutdown timeout

#### Reactive transport

Adapters use the single application-owned `ReactiveStringRedisTemplate` and Reactor `Mono`/`Flux`. The project does not define or inject a separate `ReactiveRedisTemplate<String, String>` bean. Blocking Redis calls must not be introduced into reactive request paths.

`RedisAdapterConfig` owns exactly one reactive Redis template bean:

```java
@Bean
public ReactiveStringRedisTemplate reactiveStringRedisTemplate(
    LettuceConnectionFactory lettuceConnectionFactory) {
  return new ReactiveStringRedisTemplate(lettuceConnectionFactory);
}
```

All string-key/string-value Redis adapters inject this canonical type. Do not add another reactive template bean, an alias named `reactiveRedisTemplate`, `@Primary`, or consumer-specific `@Qualifier` annotations. The bean uses the central `LettuceConnectionFactory`, preserving Sentinel, authentication, timeout, and TLS/mTLS behavior.

### Connection topology

The production-oriented configuration supports Redis Sentinel and an SSL bundle for certificate-based transport. The connection factory is built centrally; capability adapters do not manage hosts, ports, passwords, or certificates.

## 3. Key model

`RedisCacheKeyFactory` and capability-specific factories generate deterministic namespaced keys.

```text
<prefix>:<capability>:<opaque-id-or-qualifier>
```

### Key safety invariants

Keys must not contain:

- Email or mobile number
- Username, identity value, or date of birth
- Policy/certificate number
- Account reference or business account data unless explicitly proven opaque and approved
- OTP, CAPTCHA answer, password, CSRF, access token, or hash derived for authentication
- Free-form user input

Key parts must be validated for allowed characters and delimiter safety. Human-readable business meaning belongs in the serialized value, protected where required—not in the key.

### Capability namespaces

Separate namespaces prevent collision and allow approved capability-level administration. A new capability must receive a stable namespace and must not reuse another capability merely to avoid adding a factory.

## 4. Generic cache contracts

### `CachePort`

Provides generic string cache operations:

- Get
- Set with required positive TTL
- Evict

Callers provide a fully formed, namespaced key. The port rejects null, zero, or negative TTL.

### `CacheAdminPort`

Provides only approved capability-scoped operations:

- Find all entries by `CacheCapability`
- Evict all entries by `CacheCapability`

It intentionally avoids exposing arbitrary Redis patterns to controllers or services.

## 5. Capability-specific adapters

### Auth-flow session

Supports create, load, update, touch, complete, cancel, and delete. It stores scenario, status, CSRF hash, active challenge reference, protected attributes, and lifecycle timestamps.

### Authenticated session

Supports store, load, touch, revoke, and delete. Sensitive actor/account state is protected before storage; lifecycle metadata supports idle, absolute, and terminal-retention behavior.

### OTP challenge

Stores scenario, channel, destination hash, code hash, status, attempts, resend state, and timestamps. Clear OTP values are never persisted.

### CAPTCHA challenge

Supports create, load, verification, failed-attempt increment, lock, consume, refresh, and delete. Only the answer hash is stored.

### Idempotency and replay

Uses atomic reservation and stores action, request hash, status, timestamps, and safe replay snapshots. Status includes processing, completed, failed, and—where supported—unknown outcome.

### Rate limiting

Uses an atomic Lua operation to increment a bucket and establish/read TTL as one distributed operation. Results include allowed/blocked status, remaining allowance, reset time, and retry delay.

### Completion lock

Uses a lock key and unique ownership token. Release is implemented with an atomic Lua compare-and-delete script so one process cannot release a lock it does not own.

### Reference date

Uses the generic cache model and a `reference-date` capability namespace. It supports refresh/update, capability-scoped listing, and cleanup.

## 6. JSON persistence model

Capability-specific JSON mappers isolate wire records from domain models. They provide:

- Explicit field mapping
- ISO-8601 timestamps
- Enum/status mapping
- Validation during deserialization
- Safe handling of malformed or incomplete records
- Protection-envelope mapping where sensitive data is encrypted

### Persistence compatibility

Redis JSON is a deployed persistence contract. Renaming a Java field or enum does not automatically make existing Redis records compatible.

For any schema change, decide:

- Backward-compatible reader
- Versioned record/envelope
- Controlled cache invalidation
- Deployment ordering
- Maximum lifetime of old records

Never fall through from a corrupt security/idempotency record into re-executing a side effect.

## 7. TTL and lifecycle

TTL is required for every temporary record. Adapters typically calculate TTL from `expiresAt` and the current `Clock`, or receive a validated duration.

Rules:

- Reject zero or negative TTL
- Avoid indefinite auth/security state
- Keep Redis TTL consistent with domain expiry
- Do not revive terminal/expired state through touch
- Do not extend authenticated idle expiry past absolute expiry
- Preserve terminal records only for the configured retention period
- Treat TTL changes as behavioral and retention changes

## 8. Atomicity model

Use atomic Redis operations when correctness spans concurrent instances:

- `SET NX`/set-if-absent for reservation and lock acquisition
- Lua increment-plus-expiry for rate limiting
- Lua compare-and-delete for lock release
- Capability-specific state transitions where lost updates are security-relevant

A local Java synchronized block is not distributed coordination and is insufficient in a multi-instance deployment.

## 9. Failure model

Infrastructure failures are translated to safe exceptions such as `CacheException`. Public error handling maps them to the standard system/downstream response without revealing:

- Redis host or Sentinel nodes
- Passwords
- Certificate paths/content
- Full keys
- Stored values
- Internal topology

Capabilities must define whether Redis failure is fail-closed, partial success, or fallback-enabled. Security state, idempotency, and rate limiting should not silently degrade into unsafe behavior.

## 10. Reference-date fallback exception

Reference Date is a cache/fallback-oriented capability. Its resolution chain can use Redis, APIM, and a Hong Kong system-date fallback according to policy. This fallback pattern must not be copied to sessions, OTP, CAPTCHA, locks, rate limits, or idempotency.

## 11. Encryption boundary

Redis transport security and value protection are separate:

```text
TLS/mTLS
  protects data in transit to Redis

AES-GCM/context protection
  protects sensitive session/context data in stored values
```

Do not assume TLS means all Redis values are safe to store in clear text.

## 12. Administration boundary

Administrative listing/deletion is limited to approved capabilities. Controllers remain thin and return counts/results through standard response models. Admin operations must be authenticated/authorized by deployment controls and audited.

## 13. Extension boundaries

#### Reuse directly
- Central `LettuceConnectionFactory`
- Single application-owned `ReactiveStringRedisTemplate`
- Key factory conventions
- Generic `CachePort`
- Safe exception translation
- ISO timestamp/Jackson patterns
- Sentinel/mTLS configuration

### Create a dedicated adapter when

- The record has statuses or transitions
- Atomic compare/update is required
- Replay or ownership semantics exist
- Sensitive value protection is required
- A generic string cache would leak infrastructure logic into services

### Extend shared infrastructure only when

- A new Redis data type/atomic primitive is broadly required
- A topology/security requirement changes
- A common serialization/versioning mechanism is introduced

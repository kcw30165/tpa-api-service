# Redis Operations and Testing

## 1. Configuration reference

Review per environment:

### Core

- Enabled flag
- Key prefix
- Command timeout
- Redis password secret source

### Sentinel

- Master name
- Sentinel nodes
- Sentinel authentication
- Redis data-node authentication
- DNS/network reachability

### SSL/mTLS

- SSL enabled flag
- SSL bundle name
- CA/trust material
- Client certificate and private key
- Certificate validity and rotation
- Hostname/peer-verification policy

### Lettuce pool

- Maximum active connections
- Maximum/minimum idle connections
- Maximum wait
- Shutdown timeout

The reviewed configuration includes Sentinel, SSL bundle `redis-mtls`, default prefix `ngtpa`, a command timeout, and bounded Lettuce pool settings. Confirm the active profile; do not copy local certificate paths into deployment configuration.

## 2. Capability inventory

Maintain an inventory with:

```text
Capability
Key namespace
Record type/version
TTL source
Atomic operations
Contains protected data
Admin operations
Failure policy
Owner
```

At minimum include sessions, OTP, CAPTCHA, idempotency, replay, rate limit, locks, and reference date.

### 2A. Reactive template wiring invariant

The application context contains one application-owned bean named `reactiveStringRedisTemplate`, backed by the central `LettuceConnectionFactory`. Active source must not define or inject `ReactiveRedisTemplate<String, String>`.

If startup reports both template names, remove the duplicate rather than using `@Primary` or `@Qualifier`, then run compile, test-compile, and bounded local-profile startup validation.

```bash
git grep -n -F 'ReactiveRedisTemplate' HEAD -- \
  'src/main/java/**/*.java' \
  'src/test/java/**/*.java'
```

Audit committed source, not another worktree or preserved evidence. The canonical design produces no matches.

## 3. Troubleshooting runbook

### Connection timeout

Check Redis/Sentinel availability, DNS/network, command timeout, pool saturation, TLS handshake time, and node failover. Do not log connection credentials or full topology in public errors.

### Sentinel cannot resolve master

Check master name consistency, Sentinel quorum/state, node definitions, Sentinel authentication, and network access from the application environment.

### TLS/mTLS handshake fails

Check certificate validity, CA chain, client certificate/key pairing, SSL bundle mapping, file/secret permissions, hostname policy, and system clock.

### Pool exhaustion

Check slow Redis commands, blocked event-loop work, leaked/long-held connections, traffic spikes, retry storms, and pool/max-wait settings. Increasing pool size without identifying latency can move the failure downstream.

### Record disappears early

Compare domain `expiresAt`, calculated duration, Redis TTL, clock skew, touch behavior, eviction policy, and whether another capability/admin action deleted it.

### Record does not expire

Check that every write path applies TTL, updates preserve TTL correctly, and no accidental persistent `SET` is used. Indefinite auth/security records are a defect.

### JSON cannot be deserialized

Check deployment-version compatibility, enum/field changes, timestamp format, partial writes, protection key version, and mapper configuration. Do not delete an idempotency/security record then re-execute automatically.

### Lock remains present

Check lease TTL, process crash, release ownership token, Lua result, and clock/command delay. Never manually delete a live lock without understanding the protected operation.

### Rate limit is inconsistent

Check bucket ID canonicalization, window/TTL, Lua-script deployment, Redis cluster/topology assumptions, concurrent calls, and application clocks used for returned reset metadata.

### Reference-date cleanup affects wrong data

Verify cache capability/prefix patterns. Cleanup must target only `reference-date` entries created by the reference-date update adapter, not all similarly named keys.

### Redis unavailable during security operation

Follow the capability's failure policy. Sessions, OTP, idempotency, locks, and security rate limits should not silently bypass Redis. Reference Date may use its separately approved fallback chain.

## 4. Logging and observability

### Safe fields

- Request/correlation ID
- Capability
- Operation
- Outcome category
- Duration
- Exception type
- TTL/record-age bucket
- Sentinel failover or connection-state category
- Number of entries affected by approved admin operation

### Forbidden fields

- Full Redis key where it contains a correlatable identifier
- Stored JSON/value
- Session, OTP, CAPTCHA, CSRF, idempotency, or lock token
- PII/account data
- Password
- Sentinel/Redis credentials
- Client private key/certificate material

Recommended events:

```text
redis.operation.failed
redis.connection.unavailable
redis.sentinel.failover
redis.serialization.failed
redis.ttl.invalid
redis.lock.contended
redis.lock.release_rejected
redis.admin.evicted
```

## 5. Metrics and alerts

Track:

- Command latency and error rate
- Pool active/idle/waiting connections
- Timeout count
- Sentinel failover/master changes
- TLS handshake/certificate failures
- Memory usage and eviction count
- Key count and TTL distribution by capability
- Serialization failures
- Lock contention and lease expiry
- Rate-limit script failures
- Admin deletion counts

Alert on sustained timeout/error rates, pool exhaustion, unexpected evictions, no-TTL security keys, serialization spikes, failover instability, and certificate expiry.

## 6. Test strategy

### 6.1 Key factory tests

- Exact namespace format
- Prefix handling
- Blank/unsafe parts rejected
- Delimiter/wildcard injection rejected
- No PII examples in keys
- Capability pattern scopes only intended keys

### 6.2 JSON mapper tests

- Domain/record/JSON round trip
- ISO timestamps
- Unknown/missing fields
- Unknown enums/statuses
- Corrupt JSON
- Protected payload corruption
- Old/current schema fixtures
- No sensitive `toString()` output

### 6.3 TTL tests

Use an injected `Clock` where applicable:

- Positive TTL
- Exact expiry boundary
- Zero/negative rejection
- Touch/refresh behavior
- Terminal retention
- Idle capped by absolute expiry
- TTL preserved after updates

### 6.4 Adapter integration tests

Against an isolated Redis instance:

- Create/load/update/delete
- Not-found behavior
- Session lifecycle
- OTP/CAPTCHA transitions
- Idempotency reservation/replay
- Rate-limit Lua behavior
- Completion-lock ownership
- Reference-date get/set/evict
- Safe exception translation

### 6.5 Concurrency tests

- Many set-if-absent contenders: one winner
- Idempotency first-writer behavior
- Rate-limit increments under concurrency
- Lock acquire contention
- Non-owner release rejected
- Concurrent session touches/terminal transition
- Lost-update scenarios

### 6.6 Sentinel and mTLS tests

In an integration environment:

- Initial Sentinel discovery
- Master failover
- Reconnection after failover
- Sentinel/data-node authentication
- Valid mTLS connection
- Expired/untrusted client certificate
- Certificate rotation
- Network partition/timeout

### 6.7 Cache-admin tests

- Find only selected capability
- Entry qualifier mapping
- Delete only selected capability
- No arbitrary pattern input
- `deletedCount=0` is successful
- Large-key-set behavior is bounded/non-blocking
- Failure maps safely

### 6.8 Failure and fallback tests

- Redis unavailable for session/OTP/idempotency: fail according to security policy
- Redis unavailable for rate limiting: approved fail mode
- Redis unavailable for reference date: approved APIM/system-date fallback
- Serialization failure does not re-execute side effect
- Pool exhaustion and timeout

## 7. Schema migration and rollback evidence

Every Redis record change should include:

- Old and new JSON fixtures
- Compatibility decision
- Maximum TTL of old records
- Deployment order
- Cleanup/invalidation plan
- Rollback behavior
- Protection-key/version impact
- Fail-first TDD evidence

## 8. Production readiness checklist

- [ ] Capability/key inventory updated
- [ ] Prefix and key safety verified
- [ ] Every temporary write has TTL
- [ ] JSON compatibility/migration reviewed
- [ ] Atomicity and concurrency tests pass
- [ ] Security values protected at rest where required
- [ ] Sentinel discovery/failover tested
- [ ] Redis and Sentinel authentication tested
- [ ] mTLS certificates valid and rotation monitored
- [ ] Pool and timeout sized from load evidence
- [ ] No sensitive values in logs/APM
- [ ] Cache admin is capability-scoped
- [ ] Security capabilities do not fail open unintentionally
- [ ] Reference-date fallback tested separately
- [ ] Dashboards and alerts available
- [ ] Recovery/failover runbook exercised
-  Exactly one application-owned `ReactiveStringRedisTemplate` is present
-  No direct `ReactiveRedisTemplate` references exist in active source
-  Local-profile startup has no template ambiguity
- [ ] Fail-first TDD evidence attached to the change

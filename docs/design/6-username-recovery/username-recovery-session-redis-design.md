# Username Recovery Session and Redis Design

## 1. Captcha gate before session creation

`/captcha/verify` establishes Redis status `VERIFIED`. `/username-recovery/start` receives only the challenge ID and uses an atomic Redis operation to transition:

```text
VERIFIED -> CONSUMED
```

No recovery session is created before this transition and a valid APIM result.

The captcha answer is never included in the start command, recovery idempotency hash, recovery session, or logs.

## 2. Lifecycle

```text
ACTIVE
  -> PARTIALLY_REQUESTED
  -> COMPLETED

ACTIVE or PARTIALLY_REQUESTED
  -> CANCELLED
  -> EXPIRED
```

`COMPLETED` means all registered candidates have delivery state `ACCEPTED`.

Completion check:

```text
registeredCandidates is nonempty
AND every registered candidate is ACCEPTED
```

For a result containing no registered candidate, prefer not to create a recovery session. If framework consistency requires one, create it directly as `COMPLETED`.

## 3. Minimal protected session

Store only data used by later workflow operations.

### Session fields

- auth-flow scenario
- workflow status
- revision
- CSRF hash
- delivery channel
- verified destination
- language
- created/expires timestamps
- candidate list

### Candidate fields

- Scheme-Ref
- account environment
- trust code
- registration flag
- username only when registered
- delivery state: `AVAILABLE`, `RESERVED`, `ACCEPTED`, `FAILED`, or `UNKNOWN_OUTCOME`
- accepted timestamp
- sender/delivery operation reference when available

Do not store policy number, certificate number, scheme type, termination data, DOB, or raw identity value when they are not required after APIM matching.

## 4. Atomic final completion

After sender acceptance:

1. reacquire the session mutation boundary;
2. confirm reservation ownership/idempotency operation;
3. mark selected candidate `ACCEPTED`;
4. count registered candidates and accepted registered candidates;
5. set status to `COMPLETED` if counts match;
6. persist candidate state, session status, revision, and replay snapshot consistently.

If Redis commit fails after sender acceptance, persist or surface `UNKNOWN_OUTCOME` through the idempotency mechanism. Do not send again with a new key.

## 5. Memory model

The APIM adapter may deserialize all contract fields required to validate the response. Immediately after validation, map each item to a minimal application record:

```java
public record UsernameRecoveryCandidate(
        String schemeRef,
        String accountEnv,
        String trustCode,
        boolean registered,
        String userName,
        UsernameRecoveryDeliveryState deliveryState) {
}
```

The DTO boundary can be complete while the domain and Redis model remain minimal. This avoids retaining unused TAS data.

## 6. Cookie and CSRF

```text
Cookie: auth_flow_sid
Path: /api/v1/auth/username-recovery
SameSite: Strict
Secure: true outside local HTTP profile
HttpOnly: true
TTL: 600 seconds
```

CSRF and origin/referer checks are required for `/request` and `/cancel`.

## 7. Non-production destination state

Store the TAS-verified destination in protected context. Resolve the effective destination at delivery time:

- PROD/DR: verified destination
- all other profiles: configured Username Recovery override

Do not overwrite the verified destination with test configuration in Redis.

## 8. Exception safety

Redis adapters must not catch and discard serialization, decryption, timeout, or script errors. Map them to a typed persistence exception/result with operation and correlation context. Session services convert them into workflow `SYSTEM_FAILED` or `UNKNOWN_OUTCOME` as appropriate.

A failed optional cleanup is logged/audited separately and must not cause the service to report successful deletion or completion unless the shared adapter provides a definitive idempotent outcome.

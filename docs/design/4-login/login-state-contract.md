> Status: Design baseline v0.1  
> Date: 20 July 2026  
> Scope: Subsequent member login plus reusable authentication foundations  
> Source baseline: Login BRD V0.3, discovery decisions in 02-brd.md through 10-brd.md, and the supplied current codebase snapshot  
> Open external dependency: detailed `TRPGetAccountList` business failure-code mapping remains TBC.


# Login State Contract

## 1. Protected pre-auth login context

Logical fields before complete-context encryption:

```text
normalizedUsername
custType
userRole (retained only if a confirmed consumer exists)
destinationsById
credentialVerifiedAt
workflowVersion
```

Each destination entry contains an opaque destination ID, channel string (`email` or `sms`), original destination, and masked destination. The original destination is protected at rest and never returned.

The AuthFlowSession additionally owns scenario, status, CSRF hash, active OTP challenge ID, creation/expiry metadata and generic workflow state.

Passwords and raw OTPs are never stored.

## 2. Authenticated session

```text
sessionId
sessionVersion
normalizedUsername
actorContext
accountsByAccountRef
csrfTokenHash
authenticatedAt
lastActivityAt
idleExpiresAt
authenticationStrength = PASSWORD_OTP
otpVerifiedAt
```

### Actor mapping

```text
actorUserId   <- TRPMemberLogin cust-id
actorUserRole <- TRPMemberLogin cust-type
```

APIM `user-role` is not mapped to `ActorContext`.

### Account entry

```text
accountRef
accountEnv
rawPolicyNo
policyNo
rawCertNo
certNo
trustCode
schemeType = null
termStatus
termCompletionDate
```

The complete sensitive context is encrypted before persistence. AccountRef remains opaque and session-scoped.

## 3. Account validation

### Policy number

- Digits only
- Numeric value greater than zero
- Raw length no greater than configured width
- Left-zero-pad to configured width

### Certificate number

- APIM integer
- Greater than zero
- Formatted length no greater than configured width
- Left-zero-pad to configured width

### Termination pair

- `term-status = T` requires a nonblank strictly parsed `dd/MM/yyyy` completion date.
- Non-T status requires blank/absent completion date.
- Malformed records are omitted when at least one valid account remains.

## 4. Redis logical keys

```text
<prefix>:auth:portal-session:<opaque-session-id>
<prefix>:auth:active-login:<opaque-hash-of-normalized-username>
<prefix>:auth:replaced-session:<opaque-session-id>
```

Raw username, policy number, certificate number, destination and AccountRef must not appear in Redis keys.

## 5. Invariants

- AuthFlowSession never grants portal access.
- A portal session must contain at least one valid account.
- Every AccountRef maps to exactly one policy/certificate pair within one session.
- AccountRefs are not persisted outside the encrypted authenticated session.
- Active-login index points only to a durable authenticated session.
- A replaced session cannot refresh its expiry.

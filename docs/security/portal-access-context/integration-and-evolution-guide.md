# Portal Access Context Integration and Evolution Guide

## 1. Protecting a new authenticated endpoint

### Step 1 — Classify endpoint scope

Decide whether the endpoint is:

- Authenticated but not account-scoped
- Authenticated and selected-account-scoped
- Multi-account, with backend resolution from one selected account
- Internal/admin and governed separately

Do not require `Account-Ref` when no account context is needed. Do require it when the operation affects or reads a selected member account.

### Step 2 — Use the established filter

Ensure the route is covered by authenticated-session and portal-context filtering. Do not duplicate cookie, CSRF, session, or account lookup in the controller.

### Step 3 — Resolve current context

Use `CurrentPortalAccessContextResolver` or the established exchange context. Fail if the context is absent; do not construct a fallback context from request fields.

### Step 4 — Build an application command

Pass only required values. Prefer a command that carries the selected account reference or server-derived business fields rather than the complete context if the use case does not need all actor data.

### Step 5 — Map downstream context

In the APIM adapter/mapper:

- Use account fields from the resolved context.
- Use actor fields only when required by the APIM contract.
- Preserve `X-Request-Id`.
- Do not forward session, CSRF, cookies, or arbitrary headers.

### Step 6 — Add authorization rules

If authorization depends on actor role/type, implement an explicit policy/service. Do not scatter string comparison of `actorUserRole` or future `actorUserType` through controllers and adapters.

### Step 7 — Add failure mapping

Define behavior for:

- Missing session
- Expired/logged-out/revoked session
- Invalid CSRF
- Missing account reference
- Account reference not found in session
- Account terminated/ineligible
- Actor not authorized for account
- Missing/invalid Reactor context
- APIM/downstream rejection

Use the common workflow/error contract and avoid revealing another account's existence.

## 2. Planned `ActorContext` change

### Current record

```java
public record ActorContext(
        String actorUserId,
        String actorUserRole
) {}
```

### Planned conceptual record

```java
public record ActorContext(
        String actorUserId,
        String actorUsername,
        String actorUserType,
        String actorUserRole
) {}
```

This is a design target, not a statement that the change is already implemented. Final naming/order and type choices should follow the approved Java/domain convention.

## 3. Field semantics and governance

### `actorUserId`

The TAS identity identifier for the acting user. It may represent HKID, passport number, staff number, or another supported identity identifier.

Rules:

- Treat as sensitive identity data.
- Do not display it by default.
- Do not log or use in metrics.
- Do not use it as account reference or Redis key material.
- Preserve exact TAS contract formatting unless a documented domain normalization applies.
- If identity type is needed to interpret the value, obtain it from approved actor type/identity metadata rather than guessing from format.

### `actorUsername`

Planned login username.

Rules:

- Keep distinct from `actorUserId`.
- Treat as sensitive/pseudonymous account data.
- Define case normalization and canonical form at the authentication boundary.
- Do not use display casing as an authorization key.

### `actorUserType`

Planned actor category/type.

Before implementation, define:

- Allowed values or enum
- TAS/APIM source field
- Member/non-member semantics
- Whether it affects account-list presence
- Whether it participates in authorization
- Unknown/unsupported value behavior
- Backward compatibility with sessions lacking the field

Do not derive it from role unless approved.

### `actorUserRole`

Actor role/authorization classification.

Before broad reuse, define:

- Allowed values or enum
- Whether role is global or account-specific
- TAS/APIM source
- Authorization decisions that consume it
- Unknown-role fail behavior

## 4. Migration impact checklist

Adding fields affects more than the public record:

1. Login/APIM contract mapper that builds actor context
2. `AuthenticatedSession`
3. Session creation command/result
4. AES-GCM sensitive context model
5. Redis wire record and JSON mapper
6. Session replay/test fixtures
7. Temporary portal context properties and adapter
8. Reactor/context consumers
9. APIM credential-resolution context/mappers
10. Equality, copy, and `toString()` behavior
11. Logging sanitizer token list
12. Unit, integration, migration, and contract tests

## 5. Rolling-deployment strategy

Choose one approved approach:

### Backward-compatible reader

New code can read old sessions lacking `actorUsername`/`actorUserType`. Such fields remain absent/unknown until a new Login establishes a refreshed session. Authorization must not assume a missing value means a privileged type.

### Session invalidation

Invalidate all pre-change authenticated sessions and require re-login. This is simpler but affects users and requires operational planning.

### Versioned protected payload

Add a session/payload schema version and support both versions during the maximum old-session lifetime.

Whichever approach is selected must align with absolute session TTL, terminal retention, encryption key/version behavior, and rollback.

## 6. Authorization policy guidance

Avoid raw string checks such as:

```text
if actorUserRole == "X" ...
```

Prefer a policy abstraction that receives actor type, role, operation, and account context and returns an explicit authorization decision.

Consider whether role is:

- Actor-global
- Account-specific
- Operation-specific
- Supplied directly by TAS
- Derived from APIM authorization data

Default unknown type/role handling should be deny/fail-safe for protected operations.

## 7. Temporary adapter update

If local/test configuration must support the planned fields:

- Add optional values to `SessionActor` and legacy profile shapes.
- Keep old fixtures readable during migration where practical.
- Mark defaults as test-only.
- Do not invent production authorization defaults.
- Add test fixtures for member and non-member actor types.

The temporary adapter must not become the canonical definition of TAS actor semantics.

## 8. Frontend contract

Normally the frontend should not need actor identity fields to make authorization decisions. The BFF remains authoritative.

If UI display requires username or actor type later:

- Expose only an approved public projection.
- Do not serialize `PortalAccessContext` directly.
- Do not expose `actorUserId` unless explicitly required and privacy-approved.
- Do not let the frontend choose role/type.

## 9. Worked authenticated request

```text
Frontend sends:
  portal_sid cookie
  X-XSRF-TOKEN for protected mutation
  Account-Ref: acc_opaque
  X-Request-Id
  Accept-Language

BFF resolves:
  ActorContext
    actorUserId      # sensitive TAS identity ID
    actorUsername    # planned
    actorUserType    # planned
    actorUserRole

  AccountContext
    accountRef
    accountEnv
    policyNo
    certNo
    trustCode
    schemeType
    termStatus
    termCompletionDate

Application/APIM receives only required derived values.
```

## 10. Pull-request checklist

- [ ] Endpoint scope classified
- [ ] Established filter/resolver reused
- [ ] Account-Ref matched to current session
- [ ] No actor/account fields trusted from frontend
- [ ] Context propagated through Reactor correctly
- [ ] APIM receives only required context
- [ ] Authorization policy is explicit
- [ ] Sensitive fields redacted
- [ ] Missing/invalid context errors mapped
- [ ] Temporary adapter not used as production authority
- [ ] Actor field semantics approved
- [ ] Session/wire migration strategy approved
- [ ] Old/new session compatibility tested
- [ ] Non-member authorization tested
- [ ] Fail-first TDD evidence included
- [ ] Operations document updated
- [ ] Commit message included

Suggested commit message:

```text
docs(security): document portal access and actor/account context
```

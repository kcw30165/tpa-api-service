# Portal Access and Account Context Architecture

## 1. Architectural intent

The capability converts an authenticated browser session plus an opaque account selection into a trusted per-request context.

```text
Web request
  portal_sid + Account-Ref + request security headers
        |
PortalAccessContextWebFilter
        |
ValidateAuthenticatedSessionUseCase
  load, validate, CSRF-check, and touch session
        |
AuthenticatedAccountContextResolver
  find selected account in session
        |
PortalAccessContext
  ActorContext + AccountContext
        |
Exchange attribute + Reactor Context
        |
CurrentPortalAccessContextResolver
        |
Application use case / APIM adapter
```

## 2. Context hierarchy

### `PortalAccessContext`

The aggregate for one authenticated request:

```text
PortalAccessContext
  actor: ActorContext
  account: AccountContext
```

The account can be optional only for an explicitly non-account-scoped operation. Account-scoped services must require it.

### Current `ActorContext`

```java
public record ActorContext(
        String actorUserId,
        String actorUserRole
) {}
```

Current semantics:

- `actorUserId` identifies the acting TAS user identity.
- `actorUserRole` identifies the role under which the actor is operating.

`actorUserId` may map to an HKID, passport number, staff number, or another approved TAS identity identifier. It is not a username, not an account reference, and not safe diagnostic metadata.

### Planned actor fields

The context is expected to add:

```text
actorUsername
actorUserType
```

Intended separation:

```text
actorUserId
  TAS identity identifier

actorUsername
  login/user-name identifier

actorUserType
  actor category/type

actorUserRole
  actor role/authorization classification
```

`actorUsername` and `actorUserType` are not part of the current public record. The final enum/value vocabulary and APIM/TAS mapping must be approved before implementation.

### `AccountContext`

Represents the selected account:

- Opaque `accountRef`
- Account environment
- Policy and certificate numbers
- Trust code
- Scheme type
- Term status
- Term completion date

These fields are resolved from server-side authorized account state.

## 3. Authenticated-session source

`AuthenticatedSession` contains:

- Authenticated session ID
- `ActorContext`
- Authorized `AuthenticatedSessionAccount` entries
- Session status
- CSRF token hash
- Creation, access, idle-expiry, and absolute-expiry timestamps

The session supports account lookup by opaque account reference and rejects duplicate references. Only active, non-expired sessions are usable. citeturn14search59

## 4. Account-reference model

`Account-Ref` is an opaque reference assigned after Login account retrieval and validation. It allows the frontend to select an account without sending policy number, certificate number, account environment, or other downstream identifiers.

### Required properties

- High entropy
- Restricted safe character format
- Unique within the authenticated session
- No embedded business identity
- No derivation that reveals policy/certificate data
- Valid only when matched against the current session

The reviewed configuration uses an `acc`-style prefix, entropy configuration, and bounded uniqueness attempts. Exact values remain configurable. citeturn14search59

## 5. Login account preparation

Before establishing the authenticated session:

1. Retrieve accessible accounts through APIM/TAS.
2. Validate required account fields and identifier formats.
3. Interpret term status and completion date.
4. Assign a unique opaque account reference to each validated account.
5. Build the actor context.
6. Create the authenticated session with actor and accounts.
7. Protect sensitive session state before Redis persistence.

A malformed account record must not become an authorized portal account merely because APIM returned it.

## 6. Web-filter resolution

`PortalAccessContextWebFilter` obtains the session cookie, account reference, and authenticated CSRF token where required. It validates the authenticated session through the session use case and resolves the selected account.

On success, it places the context in:

- A `ServerWebExchange` attribute
- Reactor Context under the designated context key

On failure, the authenticated portal exception handler maps the error into the common response contract, commonly with `401` for missing/invalid authenticated context or the approved account-context error for an invalid selection. citeturn14search59

## 7. Reactor Context propagation

`ReactorCurrentPortalAccessContextResolver` reads `PortalAccessContext` from Reactor Context. It does not independently reload or reconstruct context from `Account-Ref`.

Rules:

- Resolve at the filter boundary once.
- Propagate through reactive chains using Reactor Context.
- Do not store request context in static or thread-local fields.
- Preserve context across asynchronous operators.
- Fail clearly if a protected service is invoked without context.
- Do not fall back to temporary YAML data when authenticated context is missing.

## 8. Controller and service usage

A controller should request current context through the approved resolver or accept the filter-established context. It should not parse session/account details itself.

Account-scoped use cases receive only the fields they need, ideally through a command or context-aware resolver. For example, Personal Information requires a valid selected account and uses account environment, trust code, and scheme type for downstream/config behavior. citeturn14search59

## 9. APIM boundary

The BFF maps server-derived context into operation-specific APIM requests or credential-resolution context.

Rules:

- Do not forward `portal_sid` or CSRF tokens.
- Do not forward `Account-Ref` as if TAS understands it unless an explicit contract says so.
- Map account environment, policy/certificate number, trust/scheme data from `AccountContext`.
- Map actor data only when the downstream operation requires it.
- Keep `X-Request-Id` as the canonical propagated correlation header.
- Do not forward arbitrary inbound headers.

For multi-account APIs, the frontend supplies only the selected `Account-Ref`; backend/APIM-side logic resolves any additional related accounts.

## 10. Member and non-member actors

The project design distinguishes member and non-member access patterns:

- Member sessions include the authorized account list.
- Non-member access requires separate verification that the actor user ID/role may access the member account.

The actor model must support this distinction without trusting client-supplied actor attributes. The future `actorUserType` can help represent actor category, but its allowed values and authorization meaning must be explicitly designed rather than inferred from `actorUserRole`.

## 11. Temporary adapter boundary

`TemporaryPortalAccessContextAdapter` supports session-shaped and legacy profile-shaped configuration for local/testing/transitional behavior. It resolves configured actors and accounts, parses term status/date, and raises member-context errors for missing or invalid entries. citeturn14search59

It is not the production authorization authority. New endpoints should target authenticated-session resolution, not expand the legacy profile mechanism.

## 12. Context protection and Redis

Authenticated session state contains sensitive actor and account data. The session protector uses AES-GCM before Redis persistence. Transport mTLS and value encryption are separate protections.

When actor fields are added:

- Include them in the protected sensitive payload.
- Update persistence wire models/mappers.
- Review AAD/versioning behavior.
- Support rolling deployment compatibility or controlled session invalidation.
- Ensure logs and exceptions redact the new fields.

## 13. Security invariants

- `actorUserId` is sensitive identity data.
- `actorUsername` will be sensitive when added.
- Actor fields never come from arbitrary frontend input.
- Account reference lookup is session-bound.
- Account business identifiers are never authorization evidence when client-supplied.
- Context is unavailable after logout, revocation, or expiry.
- CSRF validation remains separate from context authorization.
- Reactor Context is request-scoped.
- APIM mapping is operation-specific.
- Unauthorized account selection is rejected and audited safely.

## 14. Extension boundaries

### Reuse directly

- Authenticated-session validation/touch
- `AuthenticatedAccountContextResolver`
- `PortalAccessContextWebFilter`
- Reactor context keys and current-context resolver
- Opaque account-reference assignment
- Authenticated-session protection
- Error and logging patterns

### Extend carefully

- `ActorContext`
- `AccountContext`
- Session wire/protected payload
- APIM credential-resolution context
- Authorization rules for non-member actors

### Do not extend

- Temporary YAML/profile context as a production source
- Client-controlled actor/account fields
- Arbitrary header propagation into APIM

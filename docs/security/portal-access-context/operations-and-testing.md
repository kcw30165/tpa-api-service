# Portal Access and Account Context Operations and Testing

## 1. Context inventory

Maintain an approved field inventory:

```text
Field
Source system/contract
Meaning
Sensitivity
Required/optional
Normalization
Authorization use
Downstream consumers
Logging rule
Persistence location
```

At minimum inventory all current `ActorContext` and `AccountContext` fields plus planned `actorUsername` and `actorUserType`.

## 2. Current and planned actor fields

### Implemented now

```text
actorUserId
actorUserRole
```

### Planned

```text
actorUsername
actorUserType
```

`actorUserId` maps to the TAS user identity identifier, such as HKID, passport number, staff number, or another supported identity identifier. It must be classified and handled as sensitive personal identity data.

Do not operationally assume the planned fields exist until the code, APIM/TAS mappings, persistence schema, and migration are deployed.

## 3. Troubleshooting runbook

### Authentication-required response

Check `portal_sid`, cookie path/domain/SameSite/Secure behavior, session status/expiry, Redis availability, CSRF validation where required, and whether the route is covered by the portal-context filter.

### Account-Ref missing

Confirm the endpoint is account-scoped and that the frontend sends the selected reference returned after Login. Do not substitute policy/certificate number from request input.

### Account-Ref not found

Check whether it belongs to a different/expired session, whether a new Login regenerated references, whether account-list mapping dropped the account, or whether the frontend retained stale selection state.

### Wrong account context reaches APIM

Trace session account list → selected `Account-Ref` → `AccountContext` → operation mapper. Check Reactor context propagation and avoid using temporary/default context.

### Portal context missing inside a reactive service

Check that the filter wrote the correct Reactor key, context was preserved across operators/schedulers, the service did not subscribe manually, and the current-context resolver is invoked within the request chain.

### Session contains actor but no accounts

Determine whether the Login actor is a non-member, APIM Account List failed, all account records were invalid/ineligible, or session establishment occurred too early. Member Login should not establish an authorized account session before account resolution succeeds.

### Actor role/type is unexpected

Check the TAS/APIM source contract and mapper. Do not repair by hardcoding a role/type in the controller or temporary properties. Unknown values should follow fail-safe authorization behavior.

### New actor fields are null after deployment

Determine whether the session was created by an older application version, the payload reader is in compatibility mode, APIM/TAS did not return the value, or the mapper omitted it. Do not infer type from role or identity-ID format.

### Old session cannot be decrypted/deserialized

Check protected-payload version, AES-GCM key version, record schema, deployment order, and rollback compatibility. Follow the approved session invalidation strategy rather than exposing stored payloads.

### Temporary local context differs from production

Check `TemporaryPortalAccessContextProperties` session/profile shape. Treat it as a test fixture; production behavior must come from authenticated session and TAS/APIM contracts.

## 4. Logging, privacy, and audit

### Forbidden fields

- `actorUserId`
- `actorUsername`
- Policy number
- Certificate number
- Raw session ID/cookie
- CSRF token/hash
- Full authenticated session/protected payload
- Raw Account-Ref where considered correlatable
- Identity documents or contact data

### Safe fields

- `X-Request-Id`
- Operation
- Context-resolution outcome category
- Actor type/role category only if approved and non-sensitive
- Account environment/trust/scheme only if approved for operational dimensions
- Session status category
- Error code
- Duration

Recommended events:

```text
portal_context.resolved
portal_context.authentication_required
portal_context.account_ref_missing
portal_context.account_ref_rejected
portal_context.actor_unauthorized
portal_context.reactor_context_missing
portal_context.session_schema_incompatible
```

Security audit should avoid identity values and use approved pseudonymous/categorical dimensions.

## 5. Metrics and alerts

Track:

- Context-resolution success/failure
- Authentication-required rate
- Missing/invalid Account-Ref rate
- Reactor-context missing defects
- Account-list validation rejections
- Unknown actor type/role count
- Session-schema compatibility failures
- APIM operations rejected due to actor/account context
- Temporary-adapter usage outside approved profiles

Alert on authentication/context failure spikes, unknown actor values, schema/decryption errors, and temporary context activation in production-like environments.

## 6. Test strategy

### 6.1 Context record tests

- Required field validation
- Defensive immutability
- Equality/copy behavior
- Term-status/date invariants
- Redacted `toString()`
- Current actor fields
- Planned field behavior when introduced

### 6.2 Account-reference tests

- Format and entropy configuration
- Unique assignment within session
- Collision retry and maximum attempts
- No embedded policy/certificate/account data
- Reference from one session rejected in another
- Stale reference rejected after re-login

### 6.3 Session/account resolver tests

- Valid actor/account resolution
- Missing session ID
- Missing Account-Ref
- Session not found
- Session inactive/expired/revoked/logged out
- Account not found
- Duplicate account references rejected
- Non-member authorization path
- Terminated/ineligible account behavior

### 6.4 Filter tests

- Reads correct session cookie and Account-Ref
- Validates authenticated CSRF as required
- Invokes session validation/touch
- Writes exchange attribute
- Writes Reactor Context
- Maps missing/invalid context to approved response
- Does not trust actor/account request fields
- Request ID preserved

### 6.5 Reactor propagation tests

- Resolver succeeds inside request chain
- Context survives `map`, `flatMap`, and approved scheduler boundaries
- Missing context fails deterministically
- Parallel requests do not leak context
- No thread-local/static leakage
- Manual subscription anti-pattern absent

### 6.6 APIM mapping tests

- Correct account environment/policy/certificate/trust/scheme mapping
- Only `X-Request-Id` propagated as correlation
- No portal cookie/CSRF/Account-Ref forwarded unless explicitly contracted
- Actor fields mapped only for operations that require them
- Unknown role/type fails safely
- Multi-account backend resolution uses selected account correctly

### 6.7 Current `ActorContext` tests

- `actorUserId` and `actorUserRole` required/validated according to current contract
- HKID/passport/staff-number examples use synthetic values only
- No identity value in logs, keys, metrics, or public responses
- Actor user ID is not treated as username

### 6.8 Planned actor migration tests

When `actorUsername` and `actorUserType` are implemented:

- New Login maps all four fields
- Old payload without new fields follows approved compatibility strategy
- New payload round-trips through AES-GCM and Redis mapper
- Rolling old/new application versions behave safely
- Rollback behavior is tested
- Unknown user type/role denied where authorization requires them
- Username normalization is deterministic
- User ID and username remain distinct
- Sanitizer redacts both
- Temporary adapter fixtures support explicit old/new shapes

### 6.9 Temporary adapter tests

- Session-based profile resolution
- Legacy profile resolution
- Missing/default session behavior
- Missing account/profile
- Unknown term status
- Invalid term completion date
- Planned actor fields absent/present according to fixture version
- Adapter disabled outside approved environments

### 6.10 End-to-end tests

- Login OTP success → account list → authenticated session
- Multiple account selection
- Account-scoped Personal Information request
- Contribution/notification request using resolved context
- Account-Ref copied across sessions rejected
- Session logout/revocation invalidates context
- APIM receives correct derived context
- No sensitive actor/account data in logs/APM

## 7. Actor-context migration procedure

1. Approve field semantics and TAS/APIM source mapping.
2. Decide Java types and allowed-value enums.
3. Update domain record and validation.
4. Update Login/APIM mappers.
5. Update authenticated-session creation.
6. Update protected payload and Redis mapper/version.
7. Select compatibility or session-invalidation strategy.
8. Update temporary fixtures without creating production defaults.
9. Update sanitizer, audit, and metrics policy.
10. Run old/new fixture, rolling-deployment, and rollback tests.
11. Coordinate frontend only if a public projection changes.
12. Deploy with schema-error and unknown-value monitoring.

## 8. Release evidence

Each context change should include:

- Field data dictionary and owner
- TAS/APIM contract evidence
- Privacy/security classification
- Authorization policy impact
- Session schema/version decision
- Fail-first TDD evidence
- Resolver/filter/Reactor tests
- APIM mapping tests
- Old/new persistence fixtures
- Rolling deployment and rollback evidence
- Logging-redaction evidence
- Temporary-adapter profile checks
- Commit message

## 9. Production readiness checklist

- [ ] Actor/account field meanings approved
- [ ] Actor identity data classified and redacted
- [ ] Account-Ref is opaque and session-bound
- [ ] Session/CSRF validated before context resolution
- [ ] Reactor context propagation tested
- [ ] APIM mapping uses only server-derived values
- [ ] Non-member authorization path defined
- [ ] Temporary adapter disabled outside approved profiles
- [ ] Planned actor fields clearly distinguished from implemented fields
- [ ] Session persistence migration strategy approved
- [ ] Old/new sessions and rollback tested
- [ ] Unknown type/role fails safely
- [ ] No sensitive values in logs, metrics, keys, or public response
- [ ] Monitoring and runbook available
- [ ] Fail-first TDD evidence attached

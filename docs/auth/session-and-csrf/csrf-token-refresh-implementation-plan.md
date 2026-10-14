# Session-Aware CSRF Token Refresh Endpoint Implementation Plan

## 1. Purpose

Implement a CSRF token refresh endpoint for browser page refresh recovery. The endpoint must support both security contexts already used by the API service:

1. A pre-authentication `AuthFlowSession`, identified by the `auth_flow_sid` cookie.
2. An authenticated `AuthenticatedSession`, identified by the `portal_sid` cookie.

The implementation will preserve the current frontend contract in which the raw CSRF token is returned in the response payload. It will not migrate CSRF transport to an `XSRF-TOKEN` cookie as part of this change.

Because the backend stores only a hash of the CSRF token, the endpoint cannot recover the previous raw token. It must generate a new token, atomically replace the hash bound to the active session, and return the new raw token once.

## 2. Scope

### 2.1 In scope

- Add one public endpoint for CSRF token rotation after FE page refresh.
- Resolve either the authenticated session or pre-auth auth-flow session from existing cookies.
- Give an authenticated-session cookie precedence when both session cookies are present.
- Refuse fallback to the pre-auth session when an authenticated-session cookie is present but invalid.
- Generate a new cryptographically secure CSRF token.
- Persist only the new token hash.
- Return the raw token at `MutationResponse.result.data.csrfToken`.
- Preserve all unrelated session and workflow state.
- Validate request provenance using the existing Origin/Referer policy.
- Require the existing request-correlation contract.
- Add cache-prevention response headers.
- Add fail-first unit, application, web-contract, persistence, concurrency, security, and architecture tests.
- Update repository documentation, including `README.md`, the session and CSRF documents, and the implementation `handover.md`.
- Produce timestamped implementation evidence under `target/evidence`.

### 2.2 Out of scope

- Migrating the current payload-based token transport to Angular's conventional XSRF cookie transport.
- Changing the names or purposes of `auth_flow_sid`, `portal_sid`, or `X-XSRF-TOKEN`.
- Creating a new session when no valid session exists.
- Restarting or reviving an expired, cancelled, completed, logged-out, or revoked session.
- Changing Login, Registration, Forgot Password, or Username Recovery state machines except where a small shared-CSRF extraction is required.
- Changing account authorization or `Account-Ref` behavior.
- Changing framework-level Spring Security CSRF configuration.
- Returning session IDs, CSRF hashes, protected contexts, actor details, or account details.
- Running the full Maven test suite in the implementation or validation script.

## 3. Proposed API Contract

### 3.1 Endpoint

```http
POST /api/v1/auth/csrf-token/refresh
```

`POST` is required because the operation changes server state by replacing the CSRF hash stored in the active session.

### 3.2 Request

The request has no JSON body.

Required transport inputs:

- `X-Request-Id`
- Browser credentials, so the applicable session cookie is sent
- An approved `Origin`, or `Referer` where the existing policy permits fallback
- Zero or one applicable session cookie:
  - `portal_sid` for an authenticated session
  - `auth_flow_sid` for a pre-auth session

The endpoint must not require `X-XSRF-TOKEN`, because the purpose of the operation is to recover from loss of the raw CSRF token in FE memory.

The endpoint must not accept a session ID or CSRF token through:

- Query parameters
- Path parameters
- Request body
- A public session header

### 3.3 Successful authenticated-session response

```json
{
  "success": true,
  "status": "SUCCESS",
  "result": {
    "data": {
      "sessionType": "AUTHENTICATED",
      "csrfToken": "<new-raw-csrf-token>"
    }
  },
  "messages": [],
  "errors": []
}
```

### 3.4 Successful pre-auth response

```json
{
  "success": true,
  "status": "SUCCESS",
  "result": {
    "data": {
      "sessionType": "PRE_AUTH",
      "csrfToken": "<new-raw-csrf-token>"
    }
  },
  "messages": [],
  "errors": []
}
```

### 3.5 Required response headers

```http
Cache-Control: no-store
Pragma: no-cache
X-Request-Id: <resolved-request-id>
```

The response must not set a new XSRF cookie as part of this implementation.

### 3.6 Public session type

Define a public response enum with only these values:

```text
PRE_AUTH
AUTHENTICATED
```

Do not expose internal scenario names, workflow states, session status, or persistence outcomes through this field.

## 4. Session Resolution Policy

Use this deterministic resolution order:

1. If `portal_sid` is present, select the authenticated-session path.
2. If `portal_sid` is absent and `auth_flow_sid` is present, select the pre-auth path.
3. If neither cookie is present, return the approved unauthenticated or session-unavailable response.

### 4.1 No downgrade fallback

If `portal_sid` is present but malformed, unknown, expired, logged out, revoked, corrupt, or unavailable, the service must not fall back to `auth_flow_sid`, even when that cookie is also present.

This prevents a request carrying an unusable higher-trust context from silently selecting a lower-trust pre-auth context.

### 4.2 Cookie ambiguity

If the request contains duplicate values for the selected session-cookie name, use the repository's existing duplicate-cookie handling. If no deterministic existing behavior exists, reject the request rather than choosing one value.

## 5. Security Invariants

The implementation must preserve these invariants:

- The raw CSRF token is generated with the existing secure generator.
- Only the token hash is persisted.
- The raw token is returned once in the success payload.
- The raw token, token hash, and session-cookie values are never logged.
- The new token is bound to exactly one session security context.
- A token generated for a pre-auth session is not valid for an authenticated session.
- A token generated for one session is not valid for another session.
- Expired and terminal sessions cannot be refreshed or revived.
- Origin/Referer validation remains mandatory for this token-rotation exception.
- CORS remains restricted to approved credentialed origins.
- The endpoint does not require the old CSRF token.
- The endpoint does not create, restart, replace, or complete a workflow.
- The endpoint does not extend a session beyond its absolute expiry.
- The endpoint does not expose internal failure distinctions that would reveal token or session-validation details.
- The response is non-cacheable.

## 6. Target Architecture

### 6.1 Inbound web adapter

Suggested package:

```text
adapter/in/web/auth/csrf/
  CsrfTokenRefreshController.java
  CsrfTokenRefreshWebMapper.java
  dto/
    CsrfTokenRefreshData.java
    CsrfSessionTypeResponse.java
```

Responsibilities:

- Read the existing session cookies through shared cookie helpers.
- Read `Origin`, `Referer`, and `X-Request-Id` through existing request-context helpers.
- Perform only transport-level presence and syntax checks.
- Build the application command.
- Delegate to the inbound use-case port.
- Map the application result into `MutationResponse<CsrfTokenRefreshData>`.
- Apply `Cache-Control: no-store` and `Pragma: no-cache`.

The controller must not:

- Generate or hash a token.
- Load or update Redis.
- Decide session expiry or terminal status.
- Hardcode cookie names.
- Validate CSRF hashes.
- Implement Origin/Referer policy.
- Log raw session or CSRF values.

### 6.2 Inbound application port

Suggested contract:

```text
RefreshCsrfTokenUseCase
  refresh(RefreshCsrfTokenCommand)
```

Suggested command fields:

```text
authenticatedSessionCookieValue
preAuthSessionCookieValue
origin
referer
requestId
```

Use existing validated value objects where they are already available. Do not introduce duplicate request-ID or provenance models.

### 6.3 Application orchestration

Suggested package:

```text
application/usecase/auth/csrf/
  RefreshCsrfTokenService.java
  RefreshCsrfTokenCommand.java
  RefreshCsrfTokenResult.java
  RefreshCsrfTokenStatus.java
  CsrfSessionType.java
```

Responsibilities:

1. Select the session family using the fixed precedence rule.
2. Validate Origin/Referer through the existing service.
3. Delegate authenticated rotation to the authenticated-session capability.
4. Delegate pre-auth rotation to the auth-flow capability.
5. Map internal results to the approved public application result.
6. Prevent downgrade fallback.

### 6.4 Session-specific rotation capabilities

Prefer reusing existing ports and services. Add narrowly scoped ports only where current contracts cannot rotate the hash safely.

Possible abstractions:

```text
AuthenticatedSessionCsrfRotationPort
AuthFlowSessionCsrfRotationPort
```

Or, if existing session ports already provide safe compare-and-set updates, keep the rotation logic in application services and reuse those ports directly.

Do not add a generic persistence port that erases important differences between revisioned auth-flow updates and authenticated-session lifecycle updates.

## 7. Characterization Before Modification

Before designing concrete replacements, inspect and document the cumulative current state of:

### 7.1 Token generation and hashing

- Location and interface of `CsrfTokenGenerator`.
- Location and interface of `CsrfTokenHasher`.
- Entropy and output-format assumptions.
- Existing tests for token generation and hashing.

### 7.2 Pre-auth session handling

- `AuthFlowSession` CSRF-hash field.
- Session status and terminal-state rules.
- Revision or optimistic-concurrency mechanism.
- Existing create, update, touch, complete, cancel, and delete operations.
- Existing scenario adapters for Login, Registration, Forgot Password, and Username Recovery.
- Current behavior for expiry refresh.
- Current exception and public error mappings.

### 7.3 Authenticated session handling

- `AuthenticatedSession` CSRF-hash field.
- Session load and validation path.
- Idle and absolute expiry behavior.
- Touch semantics.
- Logout, revoke, and terminal retention behavior.
- Existing atomic mutation or compare-and-set support.
- Encryption/protection boundary for stored session state.
- Existing Login transition that creates and returns the authenticated CSRF token.

### 7.4 Web and security handling

- Existing request-ID filter.
- Existing Origin/Referer validator.
- Existing cookie-name and cookie-path properties.
- Existing CORS configuration.
- Existing CSRF-header exception rules, if any.
- Existing response-cache helpers or filters.
- Existing global exception and `MutationResponse` mappings.

### 7.5 Payload conventions

- Current pre-auth start response fields carrying `csrfToken`.
- Current Login success response field carrying authenticated `csrfToken`.
- Existing token redaction and serialization tests.
- Existing public status names for missing, expired, terminal, conflicting, and unavailable sessions.

Record characterization findings in `handover.md` before applying production changes.

## 8. Detailed Implementation Plan

### Phase 1: Characterization and design lock

1. Inventory the existing classes and tests listed in Section 7.
2. Identify the canonical token generator and hasher.
3. Identify the canonical Origin/Referer validation entry point.
4. Identify the canonical `MutationResponse` builder and public error mapper.
5. Determine whether both session families already support atomic CSRF-hash replacement.
6. Determine whether rotation should touch idle or interaction expiry.
7. Document the final class/file change map in `handover.md`.
8. Preserve all earlier confirmed behavior not explicitly changed by this plan.

Exit criteria:

- No design uncertainty remains around persistence atomicity.
- No duplicate security primitive is planned.
- Public error mappings are identified.
- Tests can be written against stable interfaces.

### Phase 2: Fail-first contract tests

Add failing tests before production implementation.

#### Controller tests

- Endpoint mapping is `POST /api/v1/auth/csrf-token/refresh`.
- No request body is required.
- No `X-XSRF-TOKEN` header is required.
- `X-Request-Id` remains required.
- Origin/Referer inputs are forwarded.
- Successful authenticated response contains `sessionType=AUTHENTICATED` and `csrfToken`.
- Successful pre-auth response contains `sessionType=PRE_AUTH` and `csrfToken`.
- Response includes `Cache-Control: no-store`.
- Response includes `Pragma: no-cache`.
- No XSRF cookie is created by the endpoint.
- No session ID, hash, actor, account, or protected context is serialized.

#### Application tests

- Authenticated session is selected when only `portal_sid` is present.
- Pre-auth session is selected when only `auth_flow_sid` is present.
- Authenticated session takes precedence when both are present.
- Invalid authenticated session does not fall back to pre-auth.
- Missing both cookies returns the approved failure.
- Origin rejection prevents rotation.
- Persistence failure maps to `SYSTEM_ERROR` without leaking internals.
- Conflict maps to the approved public retry or conflict behavior.

Exit criteria:

- Tests fail for the expected missing implementation, not because of unrelated compilation errors.

### Phase 3: Application contract and orchestration

1. Add the inbound use-case port.
2. Add command, result, status, and public session-type models.
3. Add the orchestration service.
4. Implement deterministic session selection.
5. Enforce no downgrade fallback.
6. Reuse the existing provenance validator.
7. Map internal session outcomes to approved public statuses.
8. Keep raw token lifetime limited to the result path.

Exit criteria:

- Application tests for selection, provenance, and mapping pass.
- Application packages do not depend on WebFlux, Redis, or controller DTOs.

### Phase 4: Pre-auth CSRF rotation

1. Load and validate the selected auth-flow session.
2. Reject missing, malformed, expired, terminal, or incompatible state.
3. Generate a new raw CSRF token.
4. Hash it through the canonical hasher.
5. Replace only the stored CSRF hash using the existing revision-aware mutation mechanism.
6. Preserve:
   - Session ID
   - Scenario
   - Workflow status
   - OTP challenge binding
   - Protected context and attributes
   - Creation timestamp
   - Existing expiry policy
   - All unrelated metadata
7. Return the raw token only after persistence succeeds.
8. Do not return a token when persistence outcome is unknown.

Expiry decision:

- Default to preserving existing expiry without extension.
- If the current security foundation treats any successfully validated browser interaction as a sliding-expiry touch, use the canonical touch policy and document that decision.
- Never extend a terminal session or pass an absolute/capped expiry.

Exit criteria:

- Login, Registration, Forgot Password, and Username Recovery auth-flow sessions are supported through the shared session contract.
- Concurrent mutation tests prove that workflow state cannot be overwritten by token rotation.

### Phase 5: Authenticated CSRF rotation

1. Load the authenticated session by the existing port.
2. Validate active status, idle expiry, absolute expiry, and protected state.
3. Generate and hash a new CSRF token.
4. Atomically replace only the authenticated CSRF hash.
5. Preserve:
   - Session ID
   - Actor context
   - Authorized accounts and generated `Account-Ref` values
   - Creation timestamp
   - Absolute expiry
   - Status and protected session state
6. Apply the canonical touch policy only after successful validation.
7. Prevent rotation from overwriting concurrent logout, revoke, expiry, or account/session updates.
8. Return the raw token only after a confirmed successful write.

Exit criteria:

- Authenticated rotation tests pass at idle and absolute expiry boundaries.
- Account and actor state preservation is explicitly asserted.
- Concurrent logout and rotation behavior is deterministic.

### Phase 6: Web adapter and response mapping

1. Add the controller and DTOs.
2. Read cookie values using shared helpers and configured names.
3. Read request provenance and request ID through existing helpers.
4. Delegate exactly once to the use case.
5. Return the standard mutation envelope.
6. Add non-cacheable response headers.
7. Ensure logging and exception handling redact all secret values.
8. Keep the endpoint outside any filter rule that requires an existing CSRF header, while retaining session and Origin/Referer validation inside the application path.

Exit criteria:

- Web contract tests pass.
- The endpoint is callable without the old token but not without a valid selected session and approved provenance.

### Phase 7: Error mapping and security hardening

Confirm or add mappings for:

- Missing session cookies
- Malformed cookie
- Session not found
- Session expired
- Session terminal
- Session integrity/protection failure
- Origin/Referer rejected
- Concurrent update conflict
- Redis or persistence unavailable
- Unknown persistence outcome

Rules:

- Do not expose whether a CSRF hash existed.
- Do not expose the selected session ID.
- Do not expose Redis or encryption details.
- Do not expose `ApiStatus.DOWNSTREAM_REJECTED` or other internal statuses.
- Use the approved public business or system status.
- Clear stale cookies only when consistent with existing endpoint and cookie-cleanup policies.

### Phase 8: Architecture and regression tests

Add or update tests proving:

- Controller depends on inbound application abstractions only.
- Application code has no WebFlux dependency.
- Application code has no Redis dependency.
- Token generation and hashing are not duplicated.
- Cookie names are not hardcoded in the controller.
- Raw CSRF tokens are not stored.
- CSRF hashes are not returned.
- Session identifiers and token values are not logged.
- Existing Login transition still creates a separate authenticated CSRF token.
- Existing protected requests still validate `X-XSRF-TOKEN`.
- Existing workflow start contracts remain payload-based.
- Pre-auth and authenticated tokens remain non-interchangeable.

### Phase 9: Documentation

Update:

- Root `README.md`
  - Add the refresh endpoint to the API inventory.
  - State that it rotates and returns a token in the payload.
  - State that it supports pre-auth and authenticated sessions.
  - State that it does not require the previous CSRF header.
- `docs/auth/session-and-csrf/README.md`
  - Correct any implication that the current implementation universally uses an XSRF cookie.
- `docs/auth/session-and-csrf/architecture-and-lifecycle.md`
  - Add page-refresh token rotation for both session families.
- `docs/auth/session-and-csrf/integration-and-reuse-guide.md`
  - Define the FE refresh sequence.
- `docs/auth/session-and-csrf/operations-and-testing.md`
  - Add troubleshooting, monitoring, and tests for refresh rotation.
- `handover.md`
  - Record characterization, decisions, implemented files, preservation checks, validation commands, evidence paths, known limitations, and follow-up work.

The documentation must clearly distinguish:

- Start or Login responses that initially issue a raw token.
- The refresh endpoint that rotates a lost token.
- Protected mutations that submit the current token through `X-XSRF-TOKEN`.

## 9. Concurrency Policy

### 9.1 Default rule

Use strict rotation:

- A successful refresh replaces the previously stored hash.
- Only the newest successfully persisted token is valid.
- Earlier tokens become invalid immediately.

### 9.2 Pre-auth concurrency

Use revision-aware mutation. If the session changes between load and write:

1. Do not overwrite the newer workflow state.
2. Do not return the newly generated token as successful.
3. Return the approved conflict or retry response.
4. Allow FE to retry according to the public contract.

### 9.3 Authenticated concurrency

Use an atomic mutation that cannot overwrite concurrent:

- Logout
- Revocation
- Expiry transition
- Session touch
- Account/session-state update
- Another CSRF rotation

If no suitable atomic authenticated-session update exists, add the narrowest capability required. Do not emulate atomicity with an unsafe load-modify-save sequence.

### 9.4 Multiple browser tabs

Document that concurrent refreshes may invalidate the token returned to another tab. FE should coordinate page-refresh recovery where practical. Supporting multiple simultaneously valid raw tokens is not part of this implementation.

## 10. Test Matrix

### 10.1 Successful cases

- Valid Login auth-flow session.
- Valid Registration auth-flow session.
- Valid Forgot Password auth-flow session.
- Valid Username Recovery auth-flow session.
- Valid authenticated session.
- Both cookies present with a valid authenticated session.
- Approved `Origin`.
- Approved `Referer` fallback where configured.

### 10.2 Session rejection cases

- No cookies.
- Malformed selected cookie.
- Unknown session.
- Expired pre-auth session.
- Completed pre-auth session.
- Cancelled pre-auth session.
- Replaced pre-auth session.
- Logged-out authenticated session.
- Revoked authenticated session.
- Idle-expired authenticated session.
- Absolute-expired authenticated session.
- Corrupt protected session.
- Invalid `portal_sid` with valid `auth_flow_sid`, proving no fallback.

### 10.3 Provenance cases

- Missing Origin and Referer when policy requires them.
- Rejected Origin.
- Rejected Referer.
- Approved cross-origin credentialed request.
- CORS disallows an unapproved credentialed origin.

### 10.4 Rotation and persistence cases

- New raw token differs from the previous token.
- New hash validates the new raw token.
- New hash rejects the old raw token.
- Raw token is not persisted.
- Raw token is returned only after confirmed persistence.
- Persistence failure returns no token.
- Unknown write outcome returns no token.
- Concurrent auth-flow update preserves workflow state.
- Concurrent authenticated logout wins or conflicts deterministically.
- Concurrent rotations leave exactly one latest valid token.

### 10.5 Response and leakage cases

- Standard success envelope with `sessionType` and `csrfToken` under `result.data`.
- Correct public `sessionType`.
- `Cache-Control: no-store`.
- `Pragma: no-cache`.
- Request ID echoed according to current convention.
- No `Set-Cookie: XSRF-TOKEN` response.
- No session ID in body or headers.
- No token/hash in logs.
- No actor or account details in response.
- No Redis, encryption, or exception details in errors.

## 11. Evidence and Validation Workflow

The implementation script must create one explicit timestamped directory before validation:

```text
target/evidence/csrf-token-refresh-YYYYMMDD-HHMMSS/
```

Capture sequential evidence files for:

1. Pre-state and Git status
2. Characterization inventory
3. Formatting result
4. Production compile command and exit code
5. Test-compile command and exit code
6. Static contract and architecture audits
7. Endpoint and security-preservation checks
8. Documentation-link and README inventory check
9. Final source diff
10. Diff stat
11. Name status
12. Final status
13. Preserved failure diagnostics, only when needed

Do not duplicate full Maven output into evidence text files. Maven build reports are authoritative. Retain only:

- Exact command
- Exit code
- Concise summary
- Report manifest
- Relevant failure diagnostics when a command fails

### 11.1 Formatting

Run the repository's established formatting command before compilation.

### 11.2 Production compilation

```bash
export JAVA_HOME="C:/Java/OpenJDK/jdk-21" && export M2_HOME="/d/Tools/apache-maven-3.9.15" && export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH" && mvn -DskipTests compile
```

Stop immediately if production compilation fails. Repair all compile errors cumulatively before continuing.

### 11.3 Test compilation

```bash
export JAVA_HOME="C:/Java/OpenJDK/jdk-21" && export M2_HOME="/d/Tools/apache-maven-3.9.15" && export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH" && mvn -DskipTests test-compile
```

Stop if test compilation fails. Repair all compile errors cumulatively before completing validation.

### 11.4 Full tests

Do not run `mvn test` in the implementation or validation script. Targeted or full tests may be run manually after implementation when needed.

## 12. Preservation Checks

The implementation must explicitly prove that it preserves:

- Current payload-based CSRF delivery.
- Existing workflow start response contracts.
- Existing Login authenticated-session response contract.
- Existing `X-XSRF-TOKEN` submission contract for protected operations.
- Existing session cookie names and HttpOnly behavior.
- Existing Origin/Referer validation.
- Existing request-ID behavior.
- Existing session expiry and terminal-state rules.
- Existing auth-flow revision protection.
- Existing authenticated actor and account state.
- Existing Redis protection and TTL behavior.
- Existing public error status policy.
- Existing sensitive-data logging restrictions.
- Existing Account-Ref authorization behavior.

## 13. Acceptance Criteria

The implementation is complete only when all the following are true:

- `POST /api/v1/auth/csrf-token/refresh` exists.
- It supports a valid pre-auth session.
- It supports a valid authenticated session.
- It gives `portal_sid` precedence when both cookies exist.
- It does not fall back to pre-auth when `portal_sid` is present but invalid.
- It does not require the previous `X-XSRF-TOKEN` value.
- It requires approved request provenance.
- It rotates rather than recovers the previous token.
- It persists only the new token hash.
- It returns the new raw token at `result.data.csrfToken` in the mutation-result payload.
- It preserves all unrelated session state.
- It rejects expired and terminal sessions.
- It is safe under concurrent session mutation.
- It returns non-cacheable responses.
- It does not create an XSRF cookie.
- It does not expose or log sensitive session or token material.
- Production compilation passes.
- Test compilation passes.
- Architecture, contract, security, and preservation audits pass.
- `README.md`, session/CSRF documentation, and `handover.md` are updated.
- The final evidence directory contains a clear pass/fail status.

## 14. Planned Delivery Sequence

Use separate implementation rounds so that each change remains reviewable:

1. **Characterization and fail-first contracts**
   - Inventory current CSRF/session primitives.
   - Update `handover.md`.
   - Add failing application and controller tests.

2. **Shared application contract and pre-auth rotation**
   - Add inbound use case and result models.
   - Implement revision-safe auth-flow rotation.

3. **Authenticated-session rotation**
   - Add or reuse atomic authenticated-session mutation.
   - Preserve actor, account, status, and expiry state.

4. **Web endpoint and public error mapping**
   - Add controller, DTO, mapper, cache headers, and security exception handling.

5. **Regression, architecture, and documentation**
   - Add preservation tests and architecture rules.
   - Update `README.md`, session/CSRF documents, and `handover.md`.
   - Run formatting, compile, test-compile, and evidence audits.

Each round must build on the cumulative expected state from all earlier confirmed rounds. It must not derive replacements from the initial codebase after prior changes have been applied.

## 15. Risks and Mitigations

### Risk: unsafe authenticated load-modify-save

Mitigation: require an atomic mutation or compare-and-set capability before returning a token.

### Risk: pre-auth workflow state overwritten during rotation

Mitigation: use revision-aware mutation and reject conflicts.

### Risk: multiple tabs invalidate one another's tokens

Mitigation: document strict rotation and require FE coordination or retry behavior.

### Risk: refresh endpoint becomes a CSRF bypass

Mitigation: narrowly exempt only this endpoint from the old-token requirement while enforcing valid session state, Origin/Referer validation, credentialed exact-origin CORS, correlation, non-cacheability, and no request-supplied session identifier.

### Risk: stale authenticated cookie downgrades to pre-auth

Mitigation: prohibit fallback whenever `portal_sid` is present.

### Risk: raw token leakage

Mitigation: payload serialization only after successful persistence, no logging, no URLs, no storage in idempotency records, no token in exception metadata, and explicit redaction tests.

### Risk: documentation and implementation disagree

Mitigation: update all session/CSRF documents in the same implementation round and audit relative links and endpoint inventory.

## 16. Recommended Commit Message

```text
feat(auth): add session-aware CSRF token refresh endpoint
```

## Superseded: authenticated rotation capability (2026-10-14)

Section 6.4's "Possible abstractions: `AuthenticatedSessionCsrfRotationPort`" and the whole-value
compare-and-set it led to have been removed. That mechanism made every CSRF refresh and
committed-login replay fail with a false `CSRF_REFRESH_CONFLICT` whenever the stored session's
encrypted attributes serialized in a different field order than the live JVM's re-serialization.
See `docs/design/12-csrf-token-refresh-conflict/README.md` for the defect and the replacement:
the pre-existing revision-based `AuthenticatedSessionCasUpdatePort` /
`AuthenticatedSessionConcurrencyService`, which compares only the stored `sessionRevision`.

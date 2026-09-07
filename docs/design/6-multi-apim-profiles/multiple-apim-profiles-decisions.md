# Multiple APIM Profiles Decision Record

## Status

- Purpose: close the remaining design choices before producing `multiple-apim-profiles-design.md`.
- Discovery status: complete through Stage 001B.
- Username Recovery: `DEFERRED_EXTERNAL_WORKTREE`.
- Production implementation authorized by this document: no.
- Required action: approve or replace each candidate decision below.

## Already Approved

The following decisions are closed and are not repeated for approval:

1. `actorUserRole` becomes an enum with exactly:
   - `ROLE_INPUT`
   - `ROLE_APPROVE`
   - `ROLE_USER`
   - `ROLE_AGENT`
   - `ROLE_ITADMIN`
   - `ROLE_CCD`
   - `ROLE_SA`
   - `ROLE_BROKER`
   - `ROLE_CCC`
   - `ROLE_OPERATION`
2. Temporary portal-access-context code and configuration are removed.
3. Target `ApimCredentialResolutionContext` contains only `actorUserRole` and `accountEnv`.
4. Operation classification is typed Java code.
5. YAML owns profile definitions and account-environment mappings.
6. `TRPRegMember` and `TRPResetPassword` are account-environment-specific.
7. Required profile resolution returns one valid profile or an error. `Mono.empty()` is invalid.
8. Missing or invalid routing fails before token acquisition and APIM transmission.
9. Default-profile, first-profile, legacy-global, and non-account fallback are forbidden.
10. Mapping failures require sanitized structured `ERROR` logging.

---

## D-001: Logical Name Replacing `masterLog`

### Candidate decision

Use:

```text
Java database scope enum constant: NON_ACCOUNT
Java configured profile reference: nonAccount
Physical backend database name: masterLog
```

### Rationale

- `NON_ACCOUNT` describes the routing category directly.
- It does not imply a default or fallback.
- It keeps the Java logical name separate from the physical database name.

### Alternatives rejected by the candidate

- `DEFAULT`: incorrectly implies fallback.
- `GLOBAL`: may imply a cross-database operation.
- `GENERIC`: does not define the routing boundary clearly.
- `MASTER_LOG`: preserves the current logging-related ambiguity.

### Decision

```text
[ ] APPROVE CANDIDATE
[ ] REPLACE WITH: ______________________________
```

---

## D-002: `accountEnv` to Profile Mapping Shape

### Candidate decision

Use an explicit mapping from normalized `accountEnv` to an independent profile ID.

Conceptual structure:

```yaml
apim:
  routing:
    non-account-profile-id: nonAccount
    account-env-profile-mappings:
      JP: jpAccount
      DB: dbAccount
```

### Rules

- Mapping keys are canonical `accountEnv` values.
- Profile IDs are infrastructure identifiers and need not equal `accountEnv`.
- Mapping is exact after an approved normalization rule.
- Missing, blank, duplicate, or unresolved mappings fail closed.
- FE cannot submit a profile ID.
- No identity-based implicit fallback from `accountEnv` to profile ID.

### Rationale

This avoids coupling a TAS/APIM business environment code to a Java infrastructure credential identifier and permits future credential rotation or profile renaming without changing backend environment values.

### Decision

```text
[ ] APPROVE EXPLICIT MAPPING
[ ] REQUIRE accountEnv == profileId
[ ] OTHER: _____________________________________
```

---

## D-003: Per-Profile and Global Property Scope

### Candidate decision

Require these values per credential profile:

```text
profileId
baseUrl
tokenUri
clientId
clientSecret
scope
apiKey
certificatePath
```

Keep these policies global unless future evidence requires profile-specific behavior:

```text
encryption enabled flag
token renewal skew
certificate cache TTL
certificate renewal skew
business-call retry count
business-call retry backoff
request timeout
```

### Rules

- No fallback from a missing per-profile field to legacy global credentials or connection fields.
- Every referenced profile is validated at startup.
- Secrets remain externally supplied through environment or secret-management configuration.
- Retry and timeout are operational policies, not database identity, so they remain global initially.
- Token and certificate caches remain keyed by `profileId`.

### Rationale

The current profile model already supports the proposed connection and credential fields, while token and certificate caches are profile-ID-scoped. Global retry, timeout, and cache policies avoid unnecessary configuration duplication.

### Decision

```text
[ ] APPROVE CANDIDATE SCOPE
[ ] CHANGE PER-PROFILE FIELDS: __________________
[ ] CHANGE GLOBAL FIELDS: _______________________
```

---

## D-004: Actor-Role Persistence Compatibility

### Verified current state

Stage 001B found no production persisted `actorUserRole` field in the authenticated Redis or protected auth-flow boundaries reviewed. The current profile-resolution context also does not yet contain `actorUserRole`; production construction sites populate no actor role.

### Candidate decision

Use a strict enum in the domain and authenticated request context without adding compatibility aliases for unapproved historical values.

Rules:

```text
ROLE_USER is the canonical member role.
MEMBER, role_user, EMPLOYEE, and other historical strings are not enum aliases.
Unknown values fail deserialization or context construction with a technical/security failure.
Tests and temporary fixtures are migrated to canonical enum values.
No new actor role is added to pre-auth protected contexts solely for profile routing.
Pre-auth member workflows supply ROLE_USER as their declared actor intent when constructing the APIM routing context.
```

### Deployment consideration

If a later exact production persistence audit proves that live authenticated sessions contain historical role strings, deployment must either invalidate those sessions or introduce a separately approved migration. No speculative alias is included now.

### Decision

```text
[ ] APPROVE STRICT ENUM, NO ALIASES
[ ] INVALIDATE EXISTING SESSIONS ON DEPLOYMENT
[ ] ADD TEMPORARY READ COMPATIBILITY FOR: ________
[ ] OTHER: _____________________________________
```

---

## D-005: Current-Worktree APIM Operation Classifications

### Proposed classification rule

```text
NON_ACCOUNT:
  Operation searches, authenticates, or retrieves reference information before one authoritative accountEnv is selected.

ACCOUNT_ENV:
  Operation targets a selected member account or a physical database identified by an authoritative accountEnv.
```

Availability of `env` in a response does not make the current call account-specific. Absence of `env` in a request does not make a call non-account-specific.

### Candidate classifications

#### NON_ACCOUNT

```text
TRPChkCustId
TRPSearchMember
TRPMemberLogin
TRPGetAccountList
```

#### ACCOUNT_ENV

```text
TRPRegMember
TRPResetPassword
TRPGetMemberInfo
TRPGetContSumy
TRPGetWebSysDate
TRPGetMsgBoard
TRPUpdMsgRead
TRPUpdMemberInfo
```

#### NON_ACCOUNT reference operation

```text
TRPGetCountryList
```

### Notes

- `TRPRegMember` and `TRPResetPassword` are already approved as `ACCOUNT_ENV`.
- Registration lineage is complete through protected `UsernameCheckState`.
- Forgot Password is currently missing propagation of `TRPSearchRegedMember.env` into protected state and then into the resolver context for `TRPResetPassword`; this is an implementation gap.
- Authenticated operations use `PortalAccessContext.actor.actorUserRole` and `PortalAccessContext.account.accountEnv`.
- `TRPGetWebSysDate` is classified by its request `env` and account-specific reference-date use, not as a generic reference operation.
- `TRPGetCountryList` has no selected-account requirement under the present contract and uses the non-account profile.

### Decision

```text
[ ] APPROVE ALL CANDIDATE CLASSIFICATIONS
[ ] CHANGES:
    Operation: __________________ Scope: __________
    Operation: __________________ Scope: __________
    Operation: __________________ Scope: __________
```

---

## D-006: Resolution Exception and Logging Ownership

### Candidate decision

Introduce a dedicated technical exception for APIM profile routing or resolution failure, with a stable internal error code mapped to the existing public `SYSTEM_ERROR` contract.

Primary logging ownership:

```text
ApimWebClientFacade
```

### Rules

1. Resolver returns `Mono.just(validProfile)` or `Mono.error(...)`.
2. Facade adds a defensive `switchIfEmpty` that creates the same technical failure.
3. Facade logs one sanitized structured `ERROR` event before propagating.
4. The failure event may include:
   - operation
   - database scope
   - actor role
   - account environment
   - candidate profile ID, if non-sensitive and available
   - failure reason
   - request ID
5. The event must not include secrets, tokens, passwords, certificates, or raw payloads.
6. Token acquisition, certificate retrieval, encryption, and transmission do not occur after resolution failure.
7. Adapters do not duplicate the same profile-resolution `ERROR` log.
8. The HTTP exception handler may log the separate request-failure event, but it must not expose profile topology or raw exception details.
9. Raw APIM error bodies must be sanitized before logging.

### Candidate names

```text
Exception: ApimProfileResolutionException
Internal error code: err.apim.profile.resolution.failed
Public API status: SYSTEM_ERROR
```

The final public error body continues to use the existing safe technical-failure contract.

### Decision

```text
[ ] APPROVE CANDIDATE
[ ] EXCEPTION NAME: _____________________________
[ ] INTERNAL ERROR CODE: ________________________
[ ] PRIMARY LOG OWNER: __________________________
```

---

## Implementation Gaps Already Proven

These do not require additional policy decisions:

1. Forgot Password must map `TRPSearchRegedMember.env` into protected auth-flow state.
2. `TRPResetPassword` must receive `accountEnv` through its application command or routing context even if the wire request continues to omit `env`.
3. Existing explicit `ApimCredentialResolutionContext` construction sites must migrate to the minimal context.
4. Facade overloads that create an implicit context must be redesigned so every outbound operation supplies an explicit typed database scope and routing context.
5. Existing default, first-profile, and legacy fallback tests must be replaced with fail-closed expectations.
6. Temporary portal-context tests and fixtures must be removed or migrated to authenticated-session context tests.

## Approval Result

Complete this block after reviewing D-001 through D-006:

```text
Decision record status: [ ] APPROVED  [ ] CHANGES REQUIRED
Approved by: ______________________________
Approval date: _____________________________
Notes:
________________________________________________
________________________________________________
```

## Next Artifact After Approval

```text
multiple-apim-profiles-design.md
```

The design document will apply the approved decisions to exact classes, configuration properties, session fields, operation classifications, error handling, migration, tests, and staged implementation boundaries.

# Multiple APIM Profiles Technical Design

## 1. Document Control

- **Status:** Approved for design review
- **Decision authority:** Nick
- **Approval date:** 2026-08-10
- **Governing decision record:** `multiple-apim-profiles-decisions.md`
- **Governing implementation map:** `docs/design/6-multi-apim-profiles/implementation-map.md`
- **Discovery basis:** Stage 001, Stage 001A, and superseding Stage 001B evidence recorded in `handover.md`
- **Username Recovery:** `DEFERRED_EXTERNAL_WORKTREE`
- **Implementation authorization:** Begins only after the design-review prompt passes

## 2. Purpose

This design replaces the current misleading APIM credential-profile selection with a strict database-routing model.

Java is the BFF between FE and APIM. APIM routes each request to a physical backend database. The physical targets comprise:

- the non-account database physically named `masterLog`; and
- databases identified by an authoritative `accountEnv`.

Java selects a logical APIM credential profile. Each profile contains the connection and credential material that causes APIM to route to the intended physical database.

The design supports:

1. non-account pre-authentication calls;
2. account-environment pre-authentication calls whose environment is stored in protected auth-flow state;
3. authenticated member calls whose role and selected environment come from `PortalAccessContext`; and
4. future role-sensitive acting-on-behalf routing without defining those future policies now.

## 3. Scope

### 3.1 Included

- introduce the approved `ActorUserRole` enum;
- remove temporary portal-access-context code and configuration;
- introduce typed APIM database scope;
- reduce `ApimCredentialResolutionContext` to `actorUserRole` and `accountEnv`;
- configure a logical `nonAccount` profile;
- configure explicit `accountEnv` to profile-ID mappings;
- require profile-specific APIM connection and credential properties;
- remove default, first-profile, and legacy-global profile fallbacks;
- classify all 13 current-worktree APIM operations;
- propagate authoritative `accountEnv` for registration and Forgot Password;
- use authenticated `PortalAccessContext` for post-login calls;
- fail closed before token acquisition and APIM transmission;
- emit sanitized structured `ERROR` logs for routing failures;
- preserve profile-scoped token and certificate caches;
- add fail-first, focused, cumulative, and full regression tests.

### 3.2 Excluded

- Username Recovery implementation or operation classification in its separate worktree;
- behavior for non-member actors acting on behalf of members;
- FE selection of profile IDs;
- APIM or TAS contract changes unless a verified implementation gap requires one and the approved wire contract permits it;
- database topology disclosure in public API responses;
- compatibility aliases for historical actor-role strings;
- automatic migration of unknown persisted actor-role values.

## 4. Approved Design Decisions

### 4.1 Naming

```text
Java database scope: NON_ACCOUNT
Java profile reference: nonAccount
Physical database name: masterLog
```

`nonAccount` is an explicit profile. It is not a default or fallback.

### 4.2 Mapping

Use an explicit mapping:

```text
accountEnv -> profileId
```

An `accountEnv` value is a business routing identifier. A profile ID is an infrastructure credential identifier. They are independent.

### 4.3 Property scope

Required per profile:

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

Global operational policies:

```text
encryption enabled
token renewal skew
certificate cache TTL
certificate renewal skew
business-call retry count
business-call retry backoff
request timeout
```

### 4.4 Role compatibility

- use the strict approved enum;
- do not support aliases such as `MEMBER`, `role_user`, or `EMPLOYEE`;
- unknown role values fail;
- tests and temporary fixtures migrate to canonical enum values;
- pre-auth member workflows declare `ROLE_USER` as actor intent;
- pre-auth protected contexts do not gain an actor-role field solely for routing.

### 4.5 Resolution failure

- resolver returns one valid profile or an error;
- `Mono.empty()` is invalid;
- `ApimWebClientFacade` is the primary routing-failure logging boundary;
- use `ApimProfileResolutionException` and internal code `err.apim.profile.resolution.failed`;
- public status remains `SYSTEM_ERROR`.

## 5. Current State Summary

The current implementation has these defects:

1. `DefaultApimCredentialProfileResolver` selects a configured default, falls back to the first profile, and constructs a legacy profile from global properties.
2. `TrustBasedApimCredentialProfileResolver` returns `Mono.empty()`.
3. `ApimWebClientFacade` uses `flatMap`, so empty resolution silently completes without transmission or error.
4. The current resolution context contains unrelated fields and omits the approved actor-role field.
5. Explicit production context constructors populate neither role nor environment.
6. Many adapters use a facade overload that creates an implicit context.
7. Forgot Password currently does not propagate `TRPSearchRegedMember.env` into protected context or the `TRPResetPassword` resolution context.
8. Temporary portal-access-context classes and fixtures coexist with the authenticated-session resolver.

Existing foundations to preserve:

- token cache keyed by `profileId`;
- in-flight token acquisition keyed by `profileId`;
- certificate cache keyed by `profileId`;
- in-flight certificate acquisition keyed by `profileId`;
- registration environment persisted in protected `UsernameCheckState`;
- authenticated `PortalAccessContextPort` backed by the authenticated session and selected account.

## 6. Target Domain Model

### 6.1 `ActorUserRole`

Introduce a domain enum in the application or shared domain layer where `ActorContext` is defined:

```java
public enum ActorUserRole {
    ROLE_INPUT,
    ROLE_APPROVE,
    ROLE_USER,
    ROLE_AGENT,
    ROLE_ITADMIN,
    ROLE_CCD,
    ROLE_SA,
    ROLE_BROKER,
    ROLE_CCC,
    ROLE_OPERATION
}
```

`ActorContext.actorUserRole` changes from `String` to `ActorUserRole`.

Requirements:

- no catch-all or `UNKNOWN` member;
- no custom alias deserializer;
- null is rejected wherever authenticated actor context is required;
- pre-auth member calls construct routing context with `ROLE_USER`;
- future roles are representable but have no unapproved routing policy.

### 6.2 `ApimDatabaseScope`

Introduce:

```java
public enum ApimDatabaseScope {
    NON_ACCOUNT,
    ACCOUNT_ENV
}
```

The scope describes the operation's physical database category. It is not inferred at runtime from the request body.

### 6.3 `ApimCredentialResolutionContext`

Replace the existing record with:

```java
public record ApimCredentialResolutionContext(
        ActorUserRole actorUserRole,
        String accountEnv) {
}
```

Normalization rules:

- `actorUserRole` is required for every APIM call;
- `accountEnv` is trimmed;
- blank environment becomes invalid, not null-normalized for fallback;
- `ACCOUNT_ENV` requires `accountEnv`;
- `NON_ACCOUNT` requires no environment for selection; an available environment does not override the declared scope.

### 6.4 Resolution request

The resolver contract must receive both the operation scope and dynamic context. Preferred signature:

```java
Mono<ApimCredentialProfile> resolve(
        ApimDatabaseScope scope,
        ApimCredentialResolutionContext context);
```

If repository conventions favor a request record, use:

```java
public record ApimProfileResolutionRequest(
        ApimDatabaseScope scope,
        ApimCredentialResolutionContext context) {
}
```

The chosen form must not restore operation name or arbitrary metadata as routing inputs.

### 6.5 `ApimCredentialProfile`

Retain profile-specific fields:

```text
profileId
baseUrl
tokenUri
clientId
clientSecret
apiKey
scope
certificatePath
```

All are required for every referenced profile. `scope` may be represented as the repository's existing list type but must not silently fall back to global scope.

## 7. Configuration Design

### 7.1 Conceptual YAML

```yaml
apim:
  routing:
    non-account-profile-id: nonAccount
    account-env-profile-mappings:
      JP: jpAccount
      DB: dbAccount

  credential-profiles:
    profiles:
      - profile-id: nonAccount
        base-url: ${APIM_NON_ACCOUNT_BASE_URL}
        token-uri: ${APIM_NON_ACCOUNT_TOKEN_URI}
        client-id: ${APIM_NON_ACCOUNT_CLIENT_ID}
        client-secret: ${APIM_NON_ACCOUNT_CLIENT_SECRET}
        api-key: ${APIM_NON_ACCOUNT_API_KEY}
        scope:
          - ${APIM_NON_ACCOUNT_SCOPE}
        certificate-path: ${APIM_NON_ACCOUNT_CERTIFICATE_PATH}

      - profile-id: jpAccount
        base-url: ${APIM_JP_BASE_URL}
        token-uri: ${APIM_JP_TOKEN_URI}
        client-id: ${APIM_JP_CLIENT_ID}
        client-secret: ${APIM_JP_CLIENT_SECRET}
        api-key: ${APIM_JP_API_KEY}
        scope:
          - ${APIM_JP_SCOPE}
        certificate-path: ${APIM_JP_CERTIFICATE_PATH}

      - profile-id: dbAccount
        base-url: ${APIM_DB_BASE_URL}
        token-uri: ${APIM_DB_TOKEN_URI}
        client-id: ${APIM_DB_CLIENT_ID}
        client-secret: ${APIM_DB_CLIENT_SECRET}
        api-key: ${APIM_DB_API_KEY}
        scope:
          - ${APIM_DB_SCOPE}
        certificate-path: ${APIM_DB_CERTIFICATE_PATH}
```

Exact binding placement may adapt to existing `ApimProperties`, but these logical sections must remain distinct:

- routing policy references;
- credential profile definitions;
- global operational policy.

### 7.2 Canonical environment keys

`accountEnv` mapping keys use the canonical codes received from APIM and stored in account/session state.

Normalization:

```text
trim
uppercase using Locale.ROOT
```

The same normalization is applied:

- when validating configuration keys;
- when constructing account-specific resolution context;
- when comparing a request `env` with authoritative context.

No profile-ID inference occurs after normalization.

### 7.3 Startup validation

Application startup fails if any of these conditions exists:

- `non-account-profile-id` is blank;
- referenced non-account profile is absent;
- mapping key is blank after normalization;
- duplicate normalized environment keys exist;
- mapped profile ID is blank;
- mapped profile does not exist;
- duplicate profile IDs exist;
- a referenced profile has blank required fields;
- a referenced profile has an empty scope;
- old global credential fallback properties are the only credentials provided;
- an unsupported configuration shape tries to define a default profile.

Validation errors must identify safe property paths but never secret values.

### 7.4 Legacy properties

Legacy global connection and credential properties must not participate in runtime resolution.

Migration approach:

1. add and bind strict routing/profile configuration;
2. update environment configuration;
3. remove legacy fallback code;
4. deprecate or remove legacy properties after all consumers are migrated;
5. tests must prove legacy-only configuration fails startup or resolver validation.

## 8. Profile Resolution Algorithm

### 8.1 Resolver pseudocode

```text
resolve(scope, context):
    require scope
    require context
    require context.actorUserRole

    if scope == NON_ACCOUNT:
        profileId = routing.nonAccountProfileId
        return requireConfiguredProfile(profileId)

    if scope == ACCOUNT_ENV:
        env = normalizeRequired(context.accountEnv)
        profileId = routing.accountEnvProfileMappings[env]
        if profileId missing:
            fail ACCOUNT_ENV_UNMAPPED
        return requireConfiguredProfile(profileId)

    fail SCOPE_UNSUPPORTED
```

### 8.2 Role behavior

Current approved behavior:

- pre-auth member workflow: `ROLE_USER`;
- authenticated member: role taken from `PortalAccessContext.actor.actorUserRole` and expected to be `ROLE_USER` for current member behavior;
- other enum roles remain representable;
- the resolver structure permits future role-sensitive policy;
- no other role receives an implicit profile policy in this feature.

For this release, a future-role request reaching an operation without an approved policy must fail closed rather than reuse `ROLE_USER` behavior.

### 8.3 Failure reasons

Use a typed internal reason enum or equivalent:

```text
SCOPE_MISSING
SCOPE_UNSUPPORTED
CONTEXT_MISSING
ACTOR_ROLE_MISSING
ACTOR_ROLE_UNSUPPORTED
ACCOUNT_ENV_MISSING
ACCOUNT_ENV_UNMAPPED
NON_ACCOUNT_PROFILE_MISSING
MAPPED_PROFILE_MISSING
PROFILE_INVALID
RESOLVER_EMPTY
```

These reasons remain internal and are not returned to FE.

## 9. Facade and Transport Contract

### 9.1 Explicit scope and context

Every outbound APIM call must supply:

```text
operation path
request body
database scope
resolution context
retry eligibility
```

Facade overloads that create a routing context implicitly must be removed or restricted to internal helpers that still require explicit scope and context.

No public adapter-facing overload may invent:

- `NON_ACCOUNT` scope;
- `ROLE_USER`;
- null environment;
- a default profile.

### 9.2 Execution order

Required order:

```text
1. Validate operation scope and context.
2. Resolve exactly one profile.
3. Defensively reject Mono.empty.
4. Validate request env consistency when applicable.
5. Obtain profile-scoped token.
6. Obtain profile-scoped certificate when encryption is enabled.
7. Encrypt payload when required.
8. Build request using profile base URL and API key.
9. Transmit.
10. Apply existing bounded invalid-token or invalid-public-key retry behavior.
```

No token, certificate, encryption, or request work may occur before steps 1 through 4 complete.

### 9.3 Defensive empty handling

The facade adds `switchIfEmpty` after resolver selection. Empty resolution produces `ApimProfileResolutionException` with internal reason `RESOLVER_EMPTY`.

### 9.4 Payload `env` consistency

For an `ACCOUNT_ENV` operation whose request serializes `env`:

```text
normalize(request.env) == normalize(context.accountEnv)
```

A mismatch fails before token acquisition.

The consistency check should use a typed adapter-supplied value or interface, not reflection over serialized JSON.

Preferred forms:

- adapter validates before facade invocation; and
- facade receives an optional declared request environment for a final consistency guard.

The design review should choose the least invasive form consistent with repository style, but runtime reflection is forbidden.

## 10. Operation Classification

The current worktree contains 13 outbound APIM operations.

### 10.1 Classification ledger

#### `NON_ACCOUNT`

```text
TRPChkCustId
TRPSearchMember
TRPMemberLogin
TRPGetAccountList
TRPGetCountryList
```

#### `ACCOUNT_ENV`

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

### 10.2 Pre-auth actor context

These member pre-auth calls use:

```text
actorUserRole = ROLE_USER
```

For `NON_ACCOUNT` pre-auth calls:

```text
accountEnv = null
```

For `ACCOUNT_ENV` pre-auth calls:

```text
accountEnv = authoritative protected auth-flow value
```

### 10.3 Authenticated actor context

Authenticated operations use:

```text
actorUserRole = PortalAccessContext.actor.actorUserRole
accountEnv = PortalAccessContext.account.accountEnv
```

The current member path requires `ROLE_USER`. Other roles fail until later policy is approved.

## 11. Registration Design

### 11.1 Existing authoritative lineage

The existing lineage is complete:

```text
TRPSearchMember.env
-> RegistrationLookupAccount.accountEnv
-> selected RegistrationSelectedTrust
-> validated UsernameCheckState.selectedTrust.accountEnv
-> encrypted by AesGcmRegistrationUsernameCheckProtectorAdapter
-> ciphertext and metadata stored in AuthFlowSession.attributes
-> RedisAuthFlowSessionJsonMapper persists attributes
-> completion reloads and unprotects UsernameCheckState
-> TrpRegMemberApimRequest.env
```

Do not add a duplicate `accountEnv` field to another registration context unless required by an exact implementation constraint.

### 11.2 Routing changes

- `TRPSearchMember`: `NON_ACCOUNT`, `ROLE_USER`, no environment.
- `TRPChkCustId`: `NON_ACCOUNT`, `ROLE_USER`, no environment.
- `TRPRegMember`: `ACCOUNT_ENV`, `ROLE_USER`, environment from unprotected `UsernameCheckState.selectedTrust.accountEnv`.

### 11.3 Required invariant

Before `TRPRegMember`:

```text
request.env == selectedTrust.accountEnv == resolutionContext.accountEnv
```

### 11.4 Replay and continuation

- idempotent replay must not re-resolve from FE input;
- replay uses the protected or persisted authoritative state associated with the original operation;
- additional-trust continuation validates the newly selected trust against eligible protected results and routes using that selected environment;
- unknown or missing environment fails as a technical error with no APIM transmission.

## 12. Forgot Password Design

> **Superseded (2026-10-12).** This section designed adding `accountEnv` to `ForgotPasswordAuthFlowContext` and routing `TRPResetPassword` as `ACCOUNT_ENV`. That design was reversed. In the current code `TRPResetPassword` uses `ApimDatabaseScope.NON_ACCOUNT` with a credential context whose `accountEnv` is `null`, and `accountEnv` does not exist in the Forgot Password pre-auth flow (commit `b77a7ad8`, guarded by `ForgotPasswordPreAuthNoAccountEnvArchitectureTest`). Registration still carries `accountEnv`. Read the text below as the historical design.

### 12.1 Current gap

Current code receives `TRPSearchRegedMember.env` but does not expose it from the APIM mapper into the application result and does not store it in `ForgotPasswordAuthFlowContext`.

`TRPResetPassword` currently sends customer ID and password without an explicit routing context containing environment.

### 12.2 Target protected context

Add normalized `accountEnv` to `ForgotPasswordAuthFlowContext`.

Because the context is encrypted and stored as ciphertext in generic session attributes, the protected payload schema changes. Update:

- record constructor and validation;
- all copy and transition methods;
- AES-GCM serialization and deserialization payload;
- context version if the protector uses explicit versioning;
- test fixtures and round-trip tests.

No role field is added to the protected context.

### 12.3 Capture point

The adapter/mapper for `TRPSearchRegedMember` must expose the selected member's `env` in the application result.

The details-verification or orchestration service must:

1. validate the selected downstream result;
2. normalize the environment;
3. write it into the protected Forgot Password context;
4. persist the updated encrypted context before OTP continuation.

### 12.4 Preservation through phases

All context transitions must retain `accountEnv` through:

- OTP issue;
- resend;
- verify;
- reset authorization creation;
- reset authorization validation;
- completion;
- idempotent replay;
- failed-existing handling;
- unknown-outcome handling.

### 12.5 Reset routing

`TRPResetPassword` uses:

```text
scope = ACCOUNT_ENV
actorUserRole = ROLE_USER
accountEnv = ForgotPasswordAuthFlowContext.accountEnv
```

The wire request may continue to omit `env` if the APIM contract expects only customer ID and password. Database routing is then driven solely by the resolved APIM profile.

The application completion command or outbound port command must carry `accountEnv` explicitly to the adapter. The adapter must not load auth-flow persistence itself if the application service already owns the protected context.

## 13. Authenticated Request Design

### 13.1 Context source

Preserve the authenticated path:

```text
authenticated session cookie
-> session validation
-> Account-Ref validation
-> account lookup in authenticated session
-> ActorContext
-> AccountContext
-> PortalAccessContext
-> Reactor context
-> application command
-> APIM adapter
```

### 13.2 Application command propagation

Application commands for account operations must carry:

- authoritative `accountEnv`;
- `ActorUserRole` where the operation requires actor role;
- existing policy, certificate, user, trust, and scheme data required by the APIM contract.

The APIM adapter constructs `ApimCredentialResolutionContext` from these authoritative command fields.

### 13.3 Current member rule

For current FE member traffic:

```text
actorUserRole == ROLE_USER
```

A different role reaching the member-only path fails closed until future acting-on-behalf policy is approved.

## 14. Temporary Portal Context Removal

Remove:

- `TemporaryPortalAccessContextAdapter`;
- `TemporaryPortalAccessContextProperties`;
- temporary YAML sections;
- demo sessions;
- legacy property shapes;
- obsolete property registration;
- tests and fixtures dedicated to temporary resolution.

Preserve:

- `PortalAccessContext`;
- `PortalAccessContextPort`;
- `AuthenticatedSessionConfig.authenticatedPortalAccessContextPort`;
- `AuthenticatedAccountContextResolver`;
- request filter and Reactor context behavior;
- authenticated-session and selected-account tests.

Add a Spring context test proving exactly the intended production `PortalAccessContextPort` bean is selected after removal.

## 15. Error and Logging Design

### 15.1 Exception

Introduce:

```text
ApimProfileResolutionException
```

Required fields should be safe internal diagnostics:

```text
reason
scope
actorUserRole
normalized accountEnv, if allowed by logging policy
candidate profileId, if available
operation identifier for diagnostics only
```

Operation identifier may be passed to the facade for logging but must not re-enter the resolver's selection context.

### 15.2 Internal and public mapping

```text
internal code: err.apim.profile.resolution.failed
public ApiStatus: SYSTEM_ERROR
```

Use the existing safe technical error response pattern. Do not expose:

- profile ID;
- physical database name;
- account mapping;
- exception message;
- credential property path.

### 15.3 Primary log owner

`ApimWebClientFacade` logs one routing-resolution event at `ERROR`.

Suggested event:

```text
event=apim.profile.resolution.failure
operation=<safe operation name>
scope=<scope>
actorRole=<enum>
accountEnv=<sanitized normalized value or omitted>
profileId=<safe value or omitted>
reason=<typed reason>
requestId=<correlation ID>
```

Adapters must not duplicate this event. Global HTTP handling may log the separate request failure.

### 15.4 Sanitization correction

Raw APIM response bodies must pass through `LoggingSanitizer` before any error log. Existing raw-body logging is a security hardening item in the routing-foundation stage because the facade is already being changed.

## 16. Token, Certificate, and Retry Behavior

Preserve:

- token cache keyed by `profileId`;
- token in-flight map keyed by `profileId`;
- certificate cache keyed by `profileId`;
- certificate in-flight map keyed by `profileId`;
- one bounded invalid-token refresh retry;
- one bounded invalid-public-key refresh retry;
- global retry count and backoff;
- global request timeout.

Add validation that `profileId` is nonblank before any cache access.

A routing failure is not retryable.

## 17. Security Analysis

### 17.1 Wrong-database prevention

- no fallback profile;
- explicit operation scope;
- explicit environment mapping;
- authoritative session/context environment;
- request `env` consistency checks;
- startup validation of all mappings;
- fail before token and transmission.

### 17.2 FE tampering prevention

- FE never supplies profile ID;
- authenticated `accountEnv` comes from selected account in Redis-backed session;
- registration selection is checked against protected eligible results;
- Forgot Password environment comes from APIM response and protected context;
- FE-supplied environment is never the sole routing authority.

### 17.3 Secret protection

- secrets remain externally configured;
- secret values are excluded from validation messages, logs, evidence, and API responses;
- profile and mapping diagnostic logs contain only safe identifiers.

## 18. Migration and Deployment

### 18.1 Configuration prerequisite

Before deploying code:

1. configure `nonAccount` profile;
2. configure every required account profile;
3. configure every known account-environment mapping;
4. validate secrets are available;
5. remove reliance on global legacy credentials.

### 18.2 Session compatibility

The approved enum introduces no compatibility aliases.

The discovery found no production persisted role field in the reviewed session boundaries. If deployment verification finds live persisted roles outside those boundaries, invalidate affected sessions rather than silently accepting historical values.

> Superseded (2026-10-12): see the note under section 12. The Forgot Password context no longer carries `accountEnv`.

Forgot Password protected context changes require version-aware handling:

- active old-format Forgot Password sessions cannot supply `accountEnv`;
- they must fail safely and restart the flow, or be invalidated at deployment;
- no default environment may be added;
- the design-review stage must confirm which existing context-version mechanism is available.

### 18.3 Rollback

Rollback must restore matching code and configuration together. A code rollback that expects legacy global credentials must not occur after those credentials are removed unless the rollback configuration is restored securely.

## 19. Testing Strategy

### 19.1 Actor role tests

- enum contains exactly ten values;
- `ActorContext` accepts canonical values;
- unknown historical values fail;
- authenticated context round-trip preserves enum;
- pre-auth member routing uses `ROLE_USER`;
- future roles do not inherit member routing.

### 19.2 Configuration tests

- valid non-account and environment mappings bind;
- duplicate normalized environment keys fail;
- missing non-account profile fails;
- missing mapped profile fails;
- blank required profile field fails;
- empty scope fails;
- legacy-only configuration fails;
- secret values do not appear in validation output.

### 19.3 Resolver tests

- `NON_ACCOUNT` resolves only `nonAccount`;
- `ACCOUNT_ENV` resolves exact explicit mapping;
- missing environment fails;
- unmapped environment fails;
- unsupported role fails where no policy exists;
- resolver never returns empty;
- no default, first-profile, or legacy fallback remains.

### 19.4 Facade tests

- empty resolver output becomes error;
- resolver error prevents token acquisition;
- resolver error prevents certificate acquisition;
- resolver error prevents transmission;
- request environment mismatch fails before token acquisition;
- one structured `ERROR` is emitted;
- no secrets appear;
- raw APIM error response is sanitized;
- token and certificate retry behavior remains bounded.

### 19.5 Registration tests

- lookup uses `NON_ACCOUNT` and `ROLE_USER`;
- username check uses `NON_ACCOUNT` and `ROLE_USER`;
- selected environment survives protected-state round trip;
- completion uses `ACCOUNT_ENV`;
- request `env` equals routing environment;
- mismatch and missing environment fail before transmission;
- replay and additional-trust flows preserve routing.

### 19.6 Forgot Password tests

- lookup calls use approved scope;
- downstream response environment enters protected context;
- environment survives OTP issue, resend, verify, and reset authorization;
- completion uses `ACCOUNT_ENV` and `ROLE_USER`;
- wire request may omit `env` while routing context includes it;
- old-format context restarts safely;
- missing environment fails before transmission;
- idempotent replay and unknown outcome preserve the original routing environment.

### 19.7 Authenticated operation tests

For each authenticated `ACCOUNT_ENV` operation:

- context comes from `PortalAccessContext`;
- role is `ROLE_USER`;
- selected account environment drives mapping;
- serialized `env`, when present, matches context;
- wrong or unknown account reference is rejected before the adapter.

### 19.8 Temporary context removal tests

- no temporary bean or properties remain;
- production context has the intended primary port bean;
- authenticated selected-account resolution passes;
- demo YAML sessions are absent.

## 20. Implementation Stages

### Stage 002: Actor role enum migration

- add enum;
- update `ActorContext` and actual role-bearing boundaries;
- migrate tests and fixtures;
- enforce strict unknown-value failure;
- prove no alias behavior.

### Stage 003: Temporary portal-context removal

- remove temporary adapter, properties, YAML, and fixtures;
- preserve authenticated bean wiring;
- add context-wiring regression tests.

### Stage 004: Strict routing foundation

- add `ApimDatabaseScope`;
- reduce resolution context;
- add routing properties and explicit mapping;
- enforce startup validation;
- replace resolver behavior;
- remove fallbacks;
- make facade explicit and fail closed;
- add structured logging and raw-body sanitization;
- preserve profile-scoped caches and retries.

### Stage 005: Registration routing

- classify three registration operations;
- route completion from protected `UsernameCheckState`;
- validate request environment consistency;
- cover replay and additional trust.

### Stage 006: Forgot Password routing

- expose downstream environment;
- add it to protected context;
- preserve it through all transitions;
- route reset by environment;
- handle old context safely.

### Stage 007: Login and authenticated routing

- classify login operations;
- route authenticated account operations from `PortalAccessContext`;
- enforce current `ROLE_USER` policy;
- migrate all remaining adapters.

### Stage 008: Audit and regression

- prove every operation is classified;
- prove every explicit or implicit context construction is migrated;
- prove no fallback remains;
- prove temporary context is absent;
- run focused, cumulative, full, compile, serialization, configuration, logging, and static audits;
- update `handover.md`.

## 21. Acceptance Criteria

The feature is complete only when:

1. all 13 current-worktree APIM operations have explicit typed scope;
2. `TRPRegMember` and `TRPResetPassword` use protected authoritative environments;
3. authenticated account calls use role and environment from `PortalAccessContext`;
4. `ApimCredentialResolutionContext` has only two fields;
5. role is the approved strict enum;
6. temporary portal context is removed;
7. explicit mappings resolve every account environment;
8. no profile fallback remains;
9. empty resolution is an error;
10. failures occur before token, certificate, encryption, and transmission;
11. failures log sanitized structured `ERROR` events;
12. tokens and certificates remain profile-isolated;
13. public failures remain safe `SYSTEM_ERROR` responses;
14. Username Recovery remains deferred and cannot inherit an implicit scope when later merged;
15. all focused, cumulative, and full validations pass with evidence.

## 22. Design Review Questions

The GitHub Copilot design-review prompt must verify:

1. exact class/package placement against repository conventions;
2. whether `ForgotPasswordAuthFlowContext` has a version mechanism and how old sessions restart;
3. whether every authenticated application command already carries role and environment or needs extension;
4. whether every operation classification matches its real adapter and contract;
5. whether facade logging plus global HTTP logging creates acceptable distinct events rather than duplicates;
6. whether removing implicit facade overloads requires a staged compatibility bridge;
7. whether startup validation can run through existing `ApimProperties` validation hooks;
8. whether any profile-specific property still silently falls back to global configuration;
9. whether raw response logging is fully sanitized;
10. whether test stages preserve unrelated authentication, OTP, captcha, idempotency, and session behavior.

## 23. Exact Target Package and Symbol Map

The design uses these exact package paths and symbol names for implementation to reduce ambiguity during code changes (stage boundaries reference these exact symbols):

- ActorUserRole: com.bct.ngtpa.apiservice.application.dto.ActorUserRole (new enum alongside ActorContext in application DTO package)
- ApimDatabaseScope: com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope (new enum in the APIM credential package)
- ApimCredentialResolutionContext: com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext (existing record to be reduced to two fields in this package)
- ApimCredentialProfile: com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfile (existing record)
- ApimProfileResolutionFailureReason: com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimProfileResolutionFailureReason (new internal enum)
- ApimProfileResolutionException: com.bct.ngtpa.apiservice.exception.ApimProfileResolutionException (new typed exception in common exception package)
- Routing properties and validator: com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties (extend this class with a Routing nested class and add a dedicated startup validator bean in adapter/out/apim/config)
- Facade owner: com.bct.ngtpa.apiservice.adapter.out.apim.ApimWebClientFacade (primary owner of resolution failure logging)
- Logging sanitizer: com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer (existing)

Implementers must not introduce these types in other packages to avoid dependency direction violations. Package placement aligns with existing `ActorContext` (com.bct.ngtpa.apiservice.application.dto) and existing APIM credential classes (com.bct.ngtpa.apiservice.adapter.out.apim.credential).

## 24. Next Artifact

```text
002-review-multiple-apim-profiles-design.prompt.md
```

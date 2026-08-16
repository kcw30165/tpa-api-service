# Task 0043: Authenticated Context and Generic APIM Execution Implementation Plan

## 1. Document Purpose

This plan governs the architecture correction and subsequent APIM multi-profile rollout for the Java BFF clean architecture project.

The work addresses two related but separate concerns:

1. Introduce a generic, profile-aware APIM execution mechanism that removes repeated profile, certificate, crypto, retry, and envelope-processing orchestration from individual APIM implementations.
2. Correct authenticated context propagation so trusted actor and selected-account data are resolved once at the inbound boundary and then passed explicitly through application and outbound contracts.

The plan intentionally avoids migrating Notifications until the generic APIM execution foundation has been characterized and proven through pre-authentication flows.

## 2. Working Branch

Use one branch for this architecture correction and all later migrations covered by this plan:

```bash
git switch -c feature/authenticated-context-apim-profile
```

The previously stashed adapter migration must remain untouched until the generic executor and context boundaries are established. The stash must not be restored wholesale.

## 3. Architectural Context

The BFF request flow is:

```text
Controller
    -> Application use case/service
    -> Outbound port/adapter
    -> APIM transport
    -> APIM
```

The project has two independent dimensions that must not be conflated:

### 3.1 Flow category

```text
Pre-authentication flow
Authenticated flow
```

### 3.2 Downstream database routing scope

```java
public enum ApimDatabaseScope {
    NON_ACCOUNT,
    ACCOUNT_ENV
}
```

`ApimDatabaseScope` identifies how the downstream database behind APIM is selected. It does not identify whether the BFF request is pre-authenticated or authenticated.

- `NON_ACCOUNT` means downstream database routing does not require a selected account environment for that APIM operation.
- `ACCOUNT_ENV` means downstream database routing requires an authoritative `accountEnv`.

An authenticated operation can require `ACCOUNT_ENV`, and a pre-authentication operation can also require `ACCOUNT_ENV` after an authoritative account environment has been established in protected workflow state.

## 4. Authoritative Routing Inputs

### 4.1 Authenticated flows

For authenticated requests, the authoritative values originate from the authenticated session and selected account:

```text
Authenticated session cookie + opaque Account-Ref header
    -> session validation
    -> Account-Ref matched against session accounts
    -> PortalAccessContext
    -> ActorContext + AccountContext
```

The FE-provided Account-Ref is only an opaque selector. It is not trusted account metadata.

The following values must come from `PortalAccessContext`:

```java
portalAccessContext.actor().actorUserId()
portalAccessContext.actor().actorUserRole()
portalAccessContext.account().accountEnv()
portalAccessContext.account().policyNo()
portalAccessContext.account().certNo()
portalAccessContext.account().trustCode()
```

### 4.2 Pre-authentication flows

Pre-authentication operations do not have `PortalAccessContext`. Their authoritative routing values must come from their own protected workflow state, validated lookup results, or explicit application command contracts.

Examples include:

- login pre-authentication context;
- registration selected trust;
- Forgot Password protected context;
- username recovery protected context;
- validated account records returned during login continuation.

### 4.3 Caller-owned routing decision

Neither `ApimDatabaseScope` nor `ActorUserRole` may be hardcoded inside the generic executor or individual APIM implementation.

Both values must be supplied by the caller of the APIM execution boundary:

```java
new ApimCredentialResolutionContext(
        callerSuppliedActorUserRole,
        callerSuppliedAccountEnv)
```

The caller must also supply the explicit database scope:

```java
ApimDatabaseScope.NON_ACCOUNT
```

or:

```java
ApimDatabaseScope.ACCOUNT_ENV
```

The APIM execution layer validates and applies those values. It does not infer or replace them.

For authenticated flows, the caller-supplied role must be the authenticated actor role from `ActorContext`.

For pre-authentication flows, the caller must supply the role required by the flow's established application contract. The implementation must not silently substitute `ROLE_USER_MBR`.

## 5. Architecture Invariants

### 5.1 Authenticated context invariants

1. Authenticated session and Account-Ref are resolved once at the inbound web boundary.
2. Account-Ref is an opaque account selector, not account metadata.
3. `accountEnv` for an authenticated flow comes only from the matched session `AccountContext`.
4. FE cannot submit `accountEnv`, actor role, actor ID, policy number, certificate number, or trust code as authoritative authenticated context.
5. The inbound web controller receives or accesses the already-resolved `PortalAccessContext`.
6. Application use cases receive authenticated context explicitly.
7. Application services do not resolve ambient current portal context.
8. Outbound adapters do not access HTTP headers, cookies, sessions, or Reactor portal context.
9. Outbound commands carry only operation-required actor and account values.
10. `PortalAccessContext` never enters APIM transport infrastructure.

### 5.2 APIM routing and execution invariants

11. Database scope is supplied explicitly by the caller for every APIM operation.
12. Database scope must not be inferred from whether `accountEnv` is blank.
13. `ACCOUNT_ENV` requires a nonblank authoritative `accountEnv`.
14. `NON_ACCOUNT` and `ROLE_USER_MBR` must not be hardcoded inside the APIM execution implementation.
15. `ActorUserRole` is supplied explicitly by the caller.
16. Authenticated flows use the actual authenticated actor role.
17. Profile resolution remains inside the APIM outbound boundary.
18. A credential profile is resolved once per logical APIM call.
19. The same resolved profile owns base URL, token, API key, certificate, and business call execution.
20. Encryption mode is hidden from feature adapters and transports.
21. When encryption is enabled, request encryption and response verification use the public key associated with the resolved profile and successful attempt.
22. A refreshed public key requires regeneration of the encrypted request.
23. Retry eligibility remains explicit and operation-specific.
24. Business-envelope interpretation remains operation-specific.
25. Crypto, transport, and uncertain-outcome mapping remain operation-specific.
26. No missing routing value may trigger an implicit fallback to another database scope or global APIM configuration.

## 6. Target Layering

### 6.1 Inbound web boundary

The web filter remains responsible for resolving authenticated request context:

```text
Cookie + Account-Ref
    -> validate authenticated session
    -> match selected account
    -> construct PortalAccessContext
    -> store in Reactor Context for inbound delivery
```

The Reactor-backed current-context accessor is an inbound delivery mechanism only.

### 6.2 Controller boundary

The controller obtains the already-resolved `PortalAccessContext` and builds an explicit application command.

The controller must not:

- reload the session;
- resolve account metadata directly;
- create an APIM credential profile;
- create an APIM certificate;
- infer downstream database scope from nullable values.

### 6.3 Application use case/service

The use case receives authenticated context explicitly and applies business orchestration.

It derives a focused outbound command containing only the values required by the downstream operation, for example:

```text
actorUserRole
accountEnv
actorUserId
policyNo
certNo
operation-specific inputs
```

The application service must not inject `CurrentPortalAccessContextResolver` or access Reactor Context.

### 6.4 Feature APIM adapter or transport

Feature-specific APIM code remains responsible for:

- APIM path;
- APIM request DTO construction;
- APIM response data-item type;
- caller-supplied explicit database scope;
- caller-supplied actor role and account environment;
- retry eligibility;
- operation-specific response validation;
- operation-specific application result mapping;
- operation-specific failure normalization.

It must not:

- branch on encryption configuration;
- resolve the current portal context;
- fetch a certificate directly;
- duplicate generic profile and crypto orchestration;
- hardcode database scope or actor role where those belong to the caller contract.

### 6.5 Generic APIM execution layer

Introduce a focused component under a package such as:

```text
adapter/out/apim/execution
```

Conceptual name:

```text
ApimExchangeExecutor
```

It owns:

```text
caller-supplied routing request
    -> resolve credential profile
    -> obtain profile public key when encryption is enabled
    -> prepare encrypted or plain request
    -> execute through ApimWebClientFacade
    -> preserve same-profile token/certificate behavior
    -> decrypt or parse a typed APIM response envelope
```

It does not own:

- `PortalAccessContext`;
- session resolution;
- Account-Ref;
- caller role selection;
- caller database-scope selection;
- application result mapping;
- business-error interpretation;
- unknown-outcome policy;
- feature-specific audit events.

### 6.6 APIM HTTP facade

`ApimWebClientFacade` remains responsible for:

- profile-specific HTTP transport;
- profile-specific base URL and API key;
- OAuth token retrieval and caching;
- application certificate header;
- token eviction and refresh after `invalid_token`;
- certificate eviction and refresh after `invalid_public_key`;
- bounded transient business retry;
- HTTP failure classification;
- sanitized transport logging.

## 7. Proposed Generic Execution Contract

The exact Java API will be finalized during implementation, but the conceptual contract is:

```java
public interface ApimExchangeExecutor {

    <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(
            ApimExchangeRequest<R, D> exchangeRequest);
}
```

The operation request should contain transport-only values:

```java
public record ApimExchangeRequest<R, D>(
        String path,
        R request,
        Class<R> requestType,
        Class<D> responseDataType,
        ApimDatabaseScope databaseScope,
        ApimCredentialResolutionContext credentialContext,
        ApimRetryEligibility retryEligibility
) {
}
```

The generic executor should return the typed APIM envelope:

```java
Mono<ApimResponseEnvelope<D>>
```

It must not return the final application result.

Feature adapters and result mappers continue to interpret the envelope.

## 8. Encryption and Retry Model

### 8.1 Encryption disabled

Even when encryption is disabled, the execution must:

1. use caller-supplied database scope and credential context;
2. resolve the profile once;
3. prepare the request through the crypto service's pass-through behavior;
4. execute using the resolved profile;
5. parse the typed response envelope through the crypto service's plain JSON behavior.

The non-encrypted path must not bypass profile resolution.

### 8.2 Encryption enabled

The execution must:

1. resolve the profile once;
2. obtain that profile's public key;
3. encrypt the request using that key;
4. execute using the same profile;
5. decrypt and verify the response using the key associated with the successful attempt.

### 8.3 Invalid public key recovery

If APIM returns `invalid_public_key`:

```text
Evict certificate for resolved profile
    -> refresh profile certificate
    -> regenerate encrypted request with refreshed key
    -> retry once using the same profile
    -> decrypt successful response with refreshed key
```

The implementation must never retry a stale encrypted payload after refreshing the certificate.

### 8.4 Invalid token recovery

If APIM returns `invalid_token`:

```text
Evict token for resolved profile
    -> refresh token
    -> retry once using the same profile
```

Token recovery and certificate recovery remain bounded and independent from business-call retry.

### 8.5 Business-call retry

Retry classification remains supplied by the caller:

```java
ApimRetryEligibility.SAFE_BUSINESS_CALL
ApimRetryEligibility.IDEMPOTENT_MUTATION
ApimRetryEligibility.NON_RETRYABLE
```

The generic execution layer must not guess retry eligibility from HTTP method, endpoint name, or response type.

## 9. Responsibilities That Must Remain Operation-Specific

The following behavior must not be made globally generic:

- calling `ApimResponseValidator.requireSuccessData(...)`;
- mapping downstream business error codes;
- recognizing no-record success;
- verifying selected-account response identity;
- parsing operation-specific dates and values;
- converting APIM items into domain models;
- deciding `UNAVAILABLE`, `UNKNOWN_OUTCOME`, business rejection, or system failure;
- recording feature-specific audit or failure events;
- applying mutation-specific uncertain-outcome rules.

The executor returns a typed envelope or propagates a transport/crypto failure. The feature adapter decides what that means for the operation.

## 10. First Proving Flows

### 10.1 First pilot: Forgot Password username lookup

Use the Forgot Password username lookup through the shared `TRPChkCustId` transport as the first pre-authentication proving flow.

Current conceptual path:

```text
StartForgotPasswordService
    -> ForgotPasswordUsernameLookupPort
    -> ApimForgotPasswordUsernameLookupAdapter
    -> SharedApimTrpChkCustIdTransport
    -> ApimWebClientFacade
```

The caller must explicitly supply:

```text
Database scope: NON_ACCOUNT
Actor role: caller-supplied pre-auth routing role
Retry eligibility: SAFE_BUSINESS_CALL
```

Nothing in the shared transport or generic executor may hardcode `NON_ACCOUNT` or `ROLE_USER_MBR`.

Required behavior to preserve:

- APIM request DTO;
- typed envelope;
- downstream `err-message` preservation;
- malformed-response checks;
- sanitized logging;
- request correlation;
- Forgot Password result mapping;
- no raw downstream error exposure to FE.

The shared `TRPChkCustId` transport is also used by registration username availability. Its caller-specific routing must remain explicit, including any `ACCOUNT_ENV` use based on protected selected-trust data.

### 10.2 Second pilot: Forgot Password completion

Use Forgot Password completion as the second proving flow because it exercises an idempotent mutation and uncertain-outcome behavior.

The caller must explicitly supply:

- the database scope appropriate to the protected Forgot Password state;
- the authoritative account environment where `ACCOUNT_ENV` applies;
- the caller-defined role;
- `IDEMPOTENT_MUTATION` retry eligibility.

Existing inconsistency between encrypted and non-encrypted database routing must be resolved explicitly. The two encryption modes must use the same caller-supplied routing decision.

Crypto uncertainty must remain operation-specific:

```text
Crypto uncertainty
    -> ForgotPasswordCompletionResult.unknownOutcome()
```

The generic executor must propagate the failure. It must not convert it into a universal system error.

## 11. Authenticated Pilot and Country List

### 11.1 Notifications authenticated pilot

Notifications remains the first authenticated vertical slice after the executor and pre-auth pilots are proven.

Notification retrieval requires:

```text
Database scope: ACCOUNT_ENV
Actor role: PortalAccessContext.actor().actorUserRole()
Account environment: PortalAccessContext.account().accountEnv()
Retry eligibility: SAFE_BUSINESS_CALL
```

Notification read-status update requires:

```text
Database scope: ACCOUNT_ENV
Actor role: PortalAccessContext.actor().actorUserRole()
Account environment: PortalAccessContext.account().accountEnv()
Retry eligibility: IDEMPOTENT_MUTATION
```

Required values are derived from:

```java
portalAccessContext.actor().actorUserId()
portalAccessContext.actor().actorUserRole()
portalAccessContext.account().accountEnv()
portalAccessContext.account().policyNo()
portalAccessContext.account().certNo()
```

### 11.2 Country-list correction

Country-list is an authenticated account-routed operation.

Its target flow is:

```text
Authenticated session + Account-Ref
    -> PortalAccessContext
    -> AccountContext.accountEnv
    -> caller supplies ACCOUNT_ENV and authenticated actor role
    -> APIM profile resolution
```

Country-list must not use `NON_ACCOUNT` and must not hardcode `ROLE_USER_MBR` in its implementation.

It will be migrated after the Notifications authenticated pilot establishes the explicit-context pattern.

## 12. Development Stages

## Stage 001: Baseline and Characterization

### Goal

Lock down existing profile, crypto, and retry behavior before changing production abstractions.

### Production changes

None initially.

### Tests

Characterize:

1. Profile resolution occurs once per logical call.
2. Caller-supplied `NON_ACCOUNT` resolves the configured non-account profile.
3. Caller-supplied `ACCOUNT_ENV` resolves through normalized account-environment mapping.
4. Missing `accountEnv` fails for `ACCOUNT_ENV`.
5. Unknown `accountEnv` does not fall back to the non-account profile.
6. Encryption-disabled execution still uses the resolved profile.
7. Encryption-enabled execution retrieves the certificate for the resolved profile.
8. `invalid_token` refreshes token and retains the same profile.
9. `invalid_public_key` refreshes the profile certificate.
10. The request is re-encrypted after public-key refresh.
11. The response is processed with the public key used by the successful attempt.
12. Safe and idempotent operations follow the configured retry policy.
13. `NON_RETRYABLE` operations do not use transient business retry.
14. Token and certificate recovery are bounded independently.
15. Profile resolution failure does not fall back to global APIM properties.
16. Database scope and actor role are taken from caller input, not implementation constants.

### Exit criteria

- Characterization tests are stable.
- Existing defects are distinguished from intended behavior.
- No feature migration has started.

### Commit message

```text
test(apim): characterize profile-aware encrypted execution
```

## Stage 002: Generic Execution Contract

### Goal

Define the transport-only generic execution contract.

### Work

- Introduce `ApimExchangeExecutor` contract.
- Introduce an operation request model such as `ApimExchangeRequest<R, D>`.
- Require explicit caller-supplied database scope.
- Require explicit caller-supplied credential resolution context.
- Require explicit caller-supplied retry eligibility.
- Return a typed `ApimResponseEnvelope<D>`.

### Exit criteria

- Contract supports encrypted and plain modes.
- Contract supports both database scopes.
- Contract contains no web, session, or `PortalAccessContext` types.
- Contract does not infer scope or actor role.

### Commit message

```text
feat(apim): define generic profile-aware exchange contract
```

## Stage 003: Successful-Attempt Key Consistency

### Goal

Ensure response decryption receives the exact public key associated with the successful attempt.

### Work

- Refine the facade's encrypted execution path.
- Regenerate encrypted request after certificate refresh.
- Preserve same-profile routing through token and certificate recovery.
- Expose the successful attempt's key to typed response processing without moving feature mapping into the facade.

### Exit criteria

- No stale encrypted payload retry occurs after key refresh.
- Response verification uses the successful attempt's key.
- Plain execution requires no public key.
- Existing token and business retry semantics remain intact.

### Commit message

```text
refactor(apim): preserve encryption key across successful exchange attempts
```

## Stage 004: Generic APIM Executor Implementation

### Goal

Centralize profile, encryption, typed envelope, and transport orchestration.

### Work

- Resolve caller-requested profile.
- Conditionally retrieve the profile public key.
- Encrypt or pass through the request.
- Invoke the facade with the resolved profile.
- Parse or decrypt the typed envelope.
- Capture synchronous crypto failures inside the reactive pipeline.

### Exit criteria

- Executor tests cover encrypted and plain modes.
- Both database scopes are covered.
- All retry classifications are supported.
- No feature adapter needs an encryption branch.
- No feature adapter needs direct certificate access for executor-managed calls.

### Commit message

```text
feat(apim): add generic profile-aware exchange executor
```

## Stage 005: Forgot Password Username-Lookup Pilot

### Goal

Prove the executor on a pre-authentication safe business call.

### Target

```text
SharedApimTrpChkCustIdTransport
```

### Work

- Make database scope a caller input.
- Make actor role a caller input.
- Route the raw APIM exchange through the executor.
- Preserve operation-specific envelope and result handling.
- Remove manual encryption and certificate orchestration from the shared transport.
- Avoid application-layer `instanceof` checks against the concrete shared transport.

### Exit criteria

- Forgot Password username lookup uses the executor.
- No encryption branch remains in the feature transport.
- No scope or role is hardcoded in the implementation.
- Existing result, malformed-response, logging, and security behavior is preserved.
- Registration username-availability regressions remain green.

### Commit message

```text
refactor(forgot-password): use generic APIM exchange for username lookup
```

## Stage 006: Forgot Password Completion Pilot

### Goal

Prove the executor on a pre-authentication idempotent mutation with unknown-outcome semantics.

### Work

- Resolve the authoritative routing source from protected Forgot Password state.
- Pass explicit scope, role, account environment, and retry eligibility.
- Use the same routing decision in encrypted and plain modes.
- Route through the executor.
- Keep unknown-outcome mapping in the feature adapter.

### Exit criteria

- Encrypted and plain modes use identical caller-supplied routing.
- Unknown-outcome semantics are preserved.
- No generic component prematurely normalizes the failure.
- Idempotency and retry behavior are explicitly tested.

### Commit message

```text
refactor(forgot-password): use generic APIM exchange for password completion
```

## Stage 007: Authenticated Context Contract Correction

### Goal

Move current portal-context access from application services to the inbound web layer.

### Target flow

```text
PortalAccessContextWebFilter
    -> inbound current-context accessor
    -> controller
    -> explicit authenticated use-case command
    -> application service
```

### Work

- Restrict `CurrentPortalAccessContextResolver` usage to inbound web code.
- Make authenticated context explicit in use-case commands.
- Remove resolver injection from application services.
- Derive focused outbound commands in application services.
- Replace architecture guards that currently enforce service-level ambient context access.

### Exit criteria

- Application services execute without Reactor Context.
- Service tests pass explicit context.
- Outbound contracts receive focused trusted values.
- Architecture tests prevent application and outbound layers from accessing current portal context.

### Commit message

```text
refactor(auth): make portal access context explicit at use-case boundary
```

## Stage 008: Notifications Authenticated Pilot

### Goal

Apply the generic executor and corrected authenticated-context boundary to notification retrieval and read-status update.

### Work

- Controller passes explicit `PortalAccessContext` into the use case.
- Service creates focused outbound commands.
- Retrieval caller supplies `ACCOUNT_ENV`, authenticated actor role, and `SAFE_BUSINESS_CALL`.
- Mutation caller supplies `ACCOUNT_ENV`, authenticated actor role, and `IDEMPOTENT_MUTATION`.
- Adapters use the executor.
- Remove hardcoded `ROLE_USER_MBR` and encryption branches.

### Exit criteria

- Notification services do not access Reactor portal context.
- Notification adapters do not hardcode database scope or actor role where those are caller inputs.
- `accountEnv` comes from `AccountContext`.
- Reference-date, filtering, sorting, validation, and response mapping remain intact.

### Commit message

```text
refactor(notifications): use explicit context and generic APIM exchange
```

## Stage 009: Country-List Authenticated Correction

### Goal

Correct country-list to use authenticated selected-account routing.

### Work

- Make country-list use-case execution explicitly authenticated.
- Pass actor role and account environment from `PortalAccessContext`.
- Caller supplies `ACCOUNT_ENV`.
- Adapter uses the executor.
- Remove current non-account and hardcoded-member assumptions.

### Exit criteria

- Country-list cannot run without selected authenticated account context.
- Missing context fails at the inbound boundary.
- No `NON_ACCOUNT` fallback exists.

### Commit message

```text
refactor(reference-data): route country list by authenticated account
```

## Stage 010: Personal Information Retrieval

### Goal

Migrate `TRPGetMemberInfo` as an authenticated selected-account safe business call.

### Routing contract

```text
Flow category: authenticated
Database scope: ACCOUNT_ENV
Account environment: PortalAccessContext.account().accountEnv()
Actor role: PortalAccessContext.actor().actorUserRole()
Retry eligibility: SAFE_BUSINESS_CALL
Execution boundary: ApimExchangeExecutor
```

### Work
- Preserve the Stage 007 explicit-context boundary.
- Remove inferred scope and make `ACCOUNT_ENV` explicit.
- Route the typed APIM request through the generic executor.
- Remove manual facade, certificate, crypto, profile-resolution, and encryption orchestration.
- Preserve selected-account response identity checks.
- Preserve country-dependent field mapping, `Accept-Language`, frontend field identifiers, no-record handling, malformed-envelope handling, and technical-failure normalization.

### Exit criteria
- The adapter uses command-supplied account environment and authenticated actor role.
- Missing or blank account environment fails before executor invocation.
- No `NON_ACCOUNT` fallback, hardcoded role, or scope inference remains.
- Retrieval behavior and public response contracts remain unchanged.

### Commit message

```text
refactor(personal-information): use authenticated APIM exchange for retrieval
```

## Stage 011: Personal Information Update

### Goal

Migrate `TRPUpdMemberInfo` as an authenticated selected-account idempotent mutation.

### Routing contract

```text
Flow category: authenticated
Database scope: ACCOUNT_ENV
Account environment: PortalAccessContext.account().accountEnv()
Actor role: PortalAccessContext.actor().actorUserRole()
Retry eligibility: IDEMPOTENT_MUTATION
Execution boundary: ApimExchangeExecutor
```

### Work
- Route the typed mutation exchange through the generic executor.
- Preserve APIM validation failure mapping to `MutationResponse/errors`.
- Preserve frontend field IDs in `errors[].targets`.
- Preserve country-specific dependency validation and configured blocked-address rules.
- Preserve recognized business rejection, malformed-envelope, technical-failure, and uncertain-mutation-outcome behavior.
- Do not move mutation-specific policy into the executor.

### Exit criteria
- The update adapter uses explicit authenticated routing.
- Validation and mutation response behavior remain unchanged.
- Unknown or uncertain outcomes retain operation-specific handling.
- No manual encryption orchestration remains in the adapter.

### Commit message

```text
refactor(personal-information): use authenticated APIM exchange for update
```

## Stage 012: Contribution Summary Retrieval and Export

### Goal

Migrate `TRPGetContSumy` using explicit authenticated routing while preserving both retrieval and export use cases.

### Routing contract

The authoritative database scope must be confirmed from repository and routing evidence before production editing. The stage must not retain the current non-account assumption merely for compatibility.

```text
Flow category: authenticated
Database scope: confirmed before implementation
Account environment: PortalAccessContext.account().accountEnv() when ACCOUNT_ENV applies
Actor role: PortalAccessContext.actor().actorUserRole()
Retry eligibility: SAFE_BUSINESS_CALL
Execution boundary: ApimExchangeExecutor
```

### Work
- Prove whether retrieval and export share one APIM invocation path.
- Migrate both in one stage only if export consumes the same application result and does not introduce a separate APIM execution path.
- Preserve date validation, filtering, sorting, pagination, language behavior, summary mapping, and export formatting.
- Keep export file construction outside APIM execution infrastructure.

### Exit criteria
- Routing authority is documented and explicit.
- Retrieval and export behavior remain unchanged.
- No ambient authenticated context or feature-owned encryption orchestration remains.

### Commit message

```text
refactor(contributions): use explicit context and generic APIM exchange
```

## Stage 013: Reference-Date Routing Correction

### Goal

Classify and migrate `TRPGetWebSysDate` without conflating the APIM refresh call with Redis cache or internal administration behavior.

### Required decision gate

Before production editing, confirm:
- flow category;
- authoritative account-environment source;
- required database scope;
- caller role source;
- retry eligibility;
- cache update ownership.

No implementation may infer scope from nullable `accountEnv`.

### Work
- Migrate only the APIM reference-date refresh path after routing classification is approved.
- Preserve Redis cache read/write behavior.
- Preserve `GET` and `DELETE /api/v1/internal/reference-date/all` behavior.
- Preserve cache key scoping and sorting by account environment.

### Exit criteria
- APIM routing is explicit and evidence-backed.
- Cache and internal administration behavior remain unchanged.
- No unrelated reference-data operation is migrated.

### Commit message

```text
refactor(reference-date): use explicit account routing and APIM exchange
```

## Stage 014: Login Authentication Call

### Goal

Migrate `TRPMemberLogin` as a bounded pre-authentication safe business call.

### Work
- Record the authoritative pre-authentication routing contract.
- Route the typed exchange through the generic executor.
- Preserve login request mapping, captcha boundary, credential handling, TAS lock-policy behavior, public failure normalization, and OTP continuation.
- Keep login-specific business-envelope interpretation outside the executor.

### Exit criteria
- Mixed execution ownership is removed for `TRPMemberLogin`.
- Routing, role, and retry eligibility are explicit caller inputs.
- Login behavior remains unchanged.

### Commit message

```text
refactor(login): use generic APIM exchange for authentication
```

## Stage 015: Login Account List

### Goal

Migrate `TRPGetAccountList` as a protected pre-authentication continuation after successful authentication.

### Work
- Identify the authoritative protected-workflow routing source.
- Route the typed exchange through the generic executor.
- Preserve account-list mapping, authorization data, account selection, and generation of Account-Ref mappings.
- Do not introduce authenticated `PortalAccessContext` before the authenticated session boundary exists.

### Exit criteria
- The account-list operation has explicit caller routing and retry classification.
- Account-selection behavior remains unchanged.

### Commit message

```text
refactor(login): use generic APIM exchange for account list
```

## Stage 016: Registration Lookup

### Goal

Migrate `TRPSearchUnregMember` as an independently bounded registration lookup.

### Work
- Record pre-authentication routing authority explicitly.
- Route the typed exchange through the generic executor.
- Preserve eligible trust discovery and additional trust registration behavior.
- Preserve captcha and protected registration workflow boundaries.

### Exit criteria
- Registration lookup no longer owns generic APIM orchestration.
- Result and error behavior remain registration-specific.

### Commit message

```text
refactor(registration): use generic APIM exchange for member lookup
```

## Stage 017: Registration Username Availability

### Goal

Migrate the registration caller of `TRPChkCustId` without changing the already approved Forgot Password caller.

### Work
- Preserve caller-specific routing for the shared operation.
- Supply registration scope, role, account environment, and retry eligibility explicitly.
- Do not collapse registration and Forgot Password into one hardcoded routing policy.
- Preserve username-availability behavior and shared DTO compatibility.

### Exit criteria
- Both callers remain explicit and independently tested.
- No shared transport hardcodes scope or role.
- Forgot Password regressions remain green.

### Commit message

```text
refactor(registration): use generic APIM exchange for username availability
```

## Stage 018: Registration Completion

### Goal

Migrate `TRPRegMember` as a protected pre-authentication idempotent mutation.

### Work
- Confirm and use the authoritative selected-trust account environment.
- Supply explicit `ACCOUNT_ENV` where confirmed, caller role, and `IDEMPOTENT_MUTATION`.
- Preserve registration result mapping, business rejection, uncertain outcome, and return-to-login behavior.
- Keep mutation-specific policy outside the executor.

### Exit criteria
- Registration completion uses the generic executor with explicit routing.
- Idempotency and uncertain-outcome behavior remain intact.

### Commit message

```text
refactor(registration): use generic APIM exchange for completion
```

## Stage 019: Username Recovery

### Goal

Migrate `TRPSearchMember` as a pre-authentication safe business call.

### Work
- Supply explicit routing from the Username Recovery caller contract.
- Route the typed exchange through the generic executor.
- Preserve captcha and identity verification.
- Preserve the approved behavior that Username Recovery does not require OTP.
- Preserve username disclosure and public error behavior.

### Exit criteria
- No outbound Reactor context supplies operation data.
- Routing and role are explicit.
- Username Recovery behavior remains unchanged.

### Commit message

```text
refactor(username-recovery): use generic APIM exchange for member search
```

## Stage 020: Forgot Password Details Lookup

### Goal

Migrate `TRPSearchRegedMember` without reopening the completed username-lookup or password-completion stages.

### Work
- Use the protected Forgot Password workflow as the authoritative caller boundary.
- Supply explicit scope, role, account environment, and `SAFE_BUSINESS_CALL`.
- Route the typed exchange through the generic executor.
- Preserve details verification, malformed-envelope handling, public status mapping, and workflow security controls.

### Exit criteria
- Details lookup uses the executor.
- Stages 005 and 006 remain unchanged and green.

### Commit message

```text
refactor(forgot-password): use generic APIM exchange for details lookup
```

## Stage 021: Final APIM Caller Inventory

### Goal

Prove that every APIM production caller has an explicit routing and execution classification.

### Work
- Compare every APIM production caller against the routing ledger.
- Identify callers still using direct facade, certificate, crypto, profile resolution, encryption branching, inferred scope, or hardcoded role.
- Record every remaining operation explicitly.
- Do not migrate newly discovered operations opportunistically.
- Create a separately bounded follow-up stage for each newly discovered caller.

### Exit criteria
- The ledger and production caller inventory are complete and consistent.
- No operation remains hidden under “other post-login operations” or “remaining adapters”.
- Any residual work has an explicit operation, routing source, retry policy, evidence gate, and commit boundary.

### Commit message

```text
test(apim): audit remaining routing and execution callers
```

## Stage 022: Cleanup and Architecture Enforcement

### Cleanup candidates
- remove obsolete manual encryption branches;
- remove direct certificate dependencies from migrated feature adapters;
- remove redundant facade overloads only after all callers migrate;
- remove unused global WebClient configuration if proven obsolete;
- eliminate reflection-based generic-envelope conversion;
- remove test-only compatibility constructors when production reachability is disproven;
- remove fully qualified transitional class names;
- review global versus operation-specific crypto field configuration;
- move or rename CurrentPortalAccessContextResolver to reflect inbound ownership;
- remove transitional comments and obsolete characterization tests that no longer describe production behavior.

### Architecture guards

Add or update guards enforcing:
- only inbound web code accesses current portal context;
- outbound APIM code cannot depend on PortalAccessContext;
- application services cannot use Reactor Context for portal identity;
- authenticated account-scoped ports receive explicit trusted routing values;
- generic executor and feature implementations do not hardcode NON_ACCOUNT or ROLE_USER_MBR;
- database scope is explicit;
- no scope inference from nullable accountEnv;
- migrated feature adapters do not inject ApimCertificateService;
- migrated feature adapters do not branch on encryption configuration;
- every APIM production caller appears exactly once in the final routing ledger.

### Exit criteria
- All known operation migrations are complete.
- No cleanup removes behavior still required by an unmigrated caller.
- Final architecture and full regression suites pass.

### Commit message

```text
test(architecture): enforce APIM routing and context boundaries
```

## 13. Per-Operation Routing Ledger

Maintain a routing record for every migrated APIM operation.

Use this format:

```text
Operation:
Flow category:
Authoritative database-routing source:
Caller-supplied database scope:
Caller-supplied actor role source:
Retry eligibility:
Crypto failure policy:
Business envelope owner:
```

Example for Notifications retrieval:

```text
Operation: TRPGetMsgBoard
Flow category: authenticated
Authoritative database-routing source: PortalAccessContext.account().accountEnv()
Caller-supplied database scope: ACCOUNT_ENV
Caller-supplied actor role source: PortalAccessContext.actor().actorUserRole()
Retry eligibility: SAFE_BUSINESS_CALL
Crypto failure policy: operation-specific APIM technical failure
Business envelope owner: ApimNoticeMessageAdapter
```

Example for Forgot Password username lookup:

```text
Operation: TRPChkCustId
Flow category: pre-authentication
Authoritative database-routing source: none for the initial username lookup
Caller-supplied database scope: NON_ACCOUNT
Caller-supplied actor role source: Forgot Password caller contract
Retry eligibility: SAFE_BUSINESS_CALL
Crypto failure policy: operation-specific technical failure
Business envelope owner: Forgot Password lookup adapter/result mapper
```

## 14. Development Controls to Prevent Drift

### 14.1 One concern per implementation round

Use this sequence:

```text
characterization
    -> generic contract
    -> successful-attempt key consistency
    -> executor implementation
    -> pre-auth safe-call pilot
    -> pre-auth mutation pilot
    -> authenticated context boundary
    -> Notifications pilot
    -> country-list correction
    -> personal-information retrieval
    -> personal-information update
    -> contribution summary retrieval and export
    -> reference-date classification and migration
    -> login authentication
    -> login account list
    -> registration lookup
    -> registration username availability
    -> registration completion
    -> username recovery
    -> Forgot Password details lookup
    -> final APIM caller inventory
    -> cleanup and guards
```

Do not combine the first generic-execution implementation with the Notifications context migration.

### 14.2 Fail-first contract changes

Each production change begins with focused fail-first tests for the intended invariant.

Example:

```text
Fail first:
The feature transport must not branch on encryption.

Implementation:
Route the call through ApimExchangeExecutor.

Regression:
Existing result and failure mapping remains unchanged.
```

### 14.3 Preserve operation semantics

Every migration must explicitly list behavior that must remain unchanged, including:

- request DTO shape;
- response DTO shape;
- business error mapping;
- retry classification;
- unknown-outcome handling;
- request correlation;
- sanitized logging;
- audit events;
- no-record behavior;
- idempotency behavior.

### 14.4 Do not restore the WIP stash wholesale

After the foundation is complete:

1. Inspect the stash diff.
2. Classify each change as reusable, superseded, or invalid.
3. Reapply only still-valid profile, configuration, certificate, facade, or test work.
4. Discard adapter-specific duplicated orchestration superseded by the executor.
5. Do not restore hardcoded authenticated roles or inferred database scopes.

### 14.5 Cumulative expected state

Every implementation script or patch must be based on the cumulative expected state of all previously confirmed stages, not the original repository state.

Each stage must verify that previously established invariants remain preserved.

## 15. Validation Strategy

Every implementation stage must create a timestamped evidence directory under:

```text
target/evidence/<stage-specific-name>-<timestamp>/
```

Validation should capture sequentially numbered evidence for:

1. Git pre-state.
2. Fail-first test result.
3. Focused implementation tests.
4. Relevant cumulative regressions.
5. Full test suite where required.
6. Compile result.
7. Architecture audits.
8. Diff check.
9. Final status.
10. Diff stat.
11. Name-status.
12. Command exit codes.
13. Preserved failure logs.

Maven commands must use the agreed environment:

```bash
export JAVA_HOME="C:/Java/OpenJDK/jdk-21"
export M2_HOME="/d/Tools/apache-maven-3.9.15"
export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH"
mvn -Dtest=... test
```

Focused validation must run before cumulative and full regression validation.

## 16. Stage Exit Gate

A stage is complete only when:

- its focused tests pass;
- relevant cumulative regression tests pass;
- compile passes;
- architecture guards pass;
- the diff contains only stage-scoped changes;
- the routing ledger is updated where an APIM operation was migrated;
- the implementation plan and `handover.md` are updated with confirmed decisions and achieved state;
- a commit message is provided.

No later stage may begin based only on partial local success.

## 17. Current Status
Architecture invariants: AGREED
Working branch: feature/apim-routing-context-modernization
Production investigation: COMPLETED
Generic APIM execution direction: IMPLEMENTED
Caller-supplied database scope: IMPLEMENTED AND ENFORCED FOR MIGRATED CALLERS
Caller-supplied actor role: IMPLEMENTED AND ENFORCED FOR MIGRATED CALLERS
Stages 001–008: COMPLETED AND ARCHITECT-APPROVED
Stage 009: PREPARED, NOT YET APPROVED
First pre-auth proving flow: Forgot Password username lookup, COMPLETED
Second pre-auth proving flow: Forgot Password completion, COMPLETED
Authenticated context correction: COMPLETED
First authenticated pilot: Notifications, COMPLETED
Country-list classification: authenticated ACCOUNT_ENV
Immediate next stage: Stage 009 Country-List Authenticated Correction

## 18. Immediate Next Action

Begin only **Stage 009: Country-List Authenticated Correction** after the Stage 008 commit is present.

The Stage 009 implementation package must:
- require the existing authenticated selected-account context at the inbound boundary;
- pass `accountEnv` and actor role explicitly through the country-list use-case contract;
- use `ApimDatabaseScope.ACCOUNT_ENV` and `ApimRetryEligibility.SAFE_BUSINESS_CALL`;
- migrate `TRPGetCountryList` to `ApimExchangeExecutor`;
- remove `NON_ACCOUNT`, hardcoded `ROLE_USER_MBR`, scope inference, and manual encryption orchestration from the country-list path;
- preserve country mapping, language behavior, ordering, duplicate handling, no-record behavior, business rejection, malformed-envelope behavior, technical-failure normalization, and the public response contract;
- preserve Stages 007 and 008;
- produce complete timestamped validation evidence;
- not begin Stage 010 before architect approval.

## Current implementation status

- Stages 001 through 011: approved.
- Stage 012: Contribution Summary authenticated transport migration implemented; final validation in progress.
- Stage 013: not started.

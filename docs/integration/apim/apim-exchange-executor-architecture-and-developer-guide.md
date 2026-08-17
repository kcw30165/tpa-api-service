# APIM Exchange Executor Architecture and Developer Guide

## Document Control

| Field | Value |
|---|---|
| Document | APIM Exchange Executor Architecture and Developer Guide |
| Audience | Developers, architects, and reviewers |
| Scope | Architecture, contracts, routing, security, integration, testing, operation ledger, and maintenance |
| Status | Approved reference architecture after Task 0043 closure |
| Last updated | 17 August 2026 |
| Related implementation | Task 0043: Authenticated Context and Generic APIM Execution |

---

## 1. Purpose

This document is the complete technical reference for the generic APIM exchange execution architecture.

The architecture establishes one consistent boundary for calls from the Java BFF to TAS APIs exposed through APIM. It separates reusable transport concerns from feature-specific business behavior and makes routing, credential selection, encryption, retry eligibility, and request correlation explicit.

The architecture was finalized through the staged Task 0043 modernization. The resulting repository state governs 15 APIM operations, each with one canonical executor-backed production path.

This guide explains:

- the role and responsibility of `ApimExchangeExecutor`;
- the contracts used to describe an APIM exchange;
- database-scope and account-environment routing;
- actor-role and credential-profile resolution;
- certificate, token, encryption, and correlation ownership;
- retry classifications and mutation safety;
- operation-specific envelope and error interpretation;
- rules for adding or changing an APIM operation;
- architecture and behavioral testing requirements;
- the final 15-operation routing ledger;
- troubleshooting and maintenance procedures.

---

## 2. Architectural Goals

The architecture is designed to achieve the following goals.

### 2.1 One canonical APIM execution mechanism

Every governed BFF-to-APIM business operation executes through:

```java
ApimExchangeExecutor.postEnvelope(...)
```

A feature must not maintain a parallel WebClient, certificate, crypto, or profile-resolution path.

### 2.2 Explicit routing authority

The caller must explicitly provide:

- database scope;
- actor role;
- account environment when the scope requires one;
- retry eligibility.

The executor must not infer routing from nullable fields, response data, feature names, or ambient business state.

### 2.3 Clear separation of responsibilities

The executor owns reusable APIM transport mechanics.

The feature owns:

- request-domain mapping;
- business-envelope interpretation;
- public status mapping;
- workflow transitions;
- idempotency workflows;
- mutation unknown-outcome decisions;
- contact masking;
- OTP, captcha, session, and Account-Ref behavior.

### 2.4 Safe authenticated and pre-authentication execution

The same executor supports:

- authenticated operations using protected account context;
- pre-authentication operations with no `PortalAccessContext`;
- global operations that intentionally have no account environment;
- account-environment-scoped operations with a validated authoritative environment.

### 2.5 Durable architectural enforcement

Repository tests enforce:

- the exact operation set;
- canonical executor ownership;
- explicit routing and retry classification;
- absence of feature-owned APIM orchestration;
- exclusion of duplicate and legacy execution paths;
- public downstream-status isolation.

---

## 3. High-Level Architecture

```text
Inbound web adapter
    -> application command and use case
    -> focused outbound command
    -> feature outbound port
    -> operation adapter or approved shared transport
    -> ApimExchangeRequest
    -> ApimExchangeExecutor.postEnvelope(...)
    -> generic APIM transport and crypto pipeline
    -> typed successful exchange
    -> operation-specific envelope mapper
    -> application workflow and public response
```

The architecture has three principal layers.

### 3.1 Feature application layer

The application layer determines whether the APIM call is allowed and constructs a focused outbound command.

Typical responsibilities include:

- input and workflow validation;
- protected-context loading;
- CSRF validation;
- captcha and OTP gates;
- idempotency reservation;
- selected-account or selected-trust validation;
- actor-role authority;
- authoritative account-environment selection.

### 3.2 Operation adapter layer

The operation adapter translates the focused command into:

1. the downstream APIM request DTO; and
2. an `ApimExchangeRequest` containing explicit execution metadata.

The adapter then invokes the generic executor and interprets the typed exchange using operation-specific mapping.

### 3.3 Generic execution layer

The generic execution layer resolves credentials, obtains APIM access and cryptographic material, performs plain or encrypted transport as configured, applies approved retry behavior, and returns a typed result.

The generic layer has no knowledge of feature workflow semantics.

---

## 4. Core Contracts

## 4.1 `ApimExchangeExecutor`

`ApimExchangeExecutor` is the canonical application-facing APIM execution abstraction.

Conceptually, the executor accepts:

- a typed exchange request;
- a typed downstream request payload;
- the expected response data-item type;
- transport and routing metadata already chosen by the caller.

The standard execution form is:

```java
apimExchangeExecutor.postEnvelope(exchangeRequest)
```

The exact repository signature must be used when implementing an operation. Do not create a feature-local variation of the executor contract.

## 4.2 `ApimExchangeRequest`

`ApimExchangeRequest` describes one APIM exchange. It must contain or represent the following information:

```text
operation path
APIM request body
request DTO type
response data-item type
database scope
credential-resolution context
retry eligibility
request correlation metadata
```

The request must be fully classified before it reaches the executor.

The executor must reject internally inconsistent combinations, including:

- `ACCOUNT_ENV` with a missing or blank account environment;
- a missing actor role;
- unsupported retry classification;
- missing path or type information.

## 4.3 `ApimCredentialResolutionContext`

The credential-resolution context conveys the explicit credential-routing inputs:

```java
new ApimCredentialResolutionContext(actorUserRole, accountEnv)
```

For a non-account operation:

```java
new ApimCredentialResolutionContext(
    command.actorUserRole(),
    null
)
```

For an account-environment operation:

```java
new ApimCredentialResolutionContext(
    command.actorUserRole(),
    command.accountEnv()
)
```

This metadata is used for credential resolution. It must not automatically become part of the downstream request body.

## 4.4 `ApimSuccessfulExchange`

The executor returns a typed successful exchange rather than a feature-specific business result.

The successful exchange may contain transport-level information such as:

- the typed APIM envelope;
- successful attempt metadata;
- resolved request correlation;
- information required to interpret a successful transport exchange consistently.

The feature adapter remains responsible for deciding whether the downstream envelope represents:

- business success;
- recognized business rejection;
- malformed or contradictory content;
- incomplete data;
- an operation-specific invalid response.

## 4.5 Operation request and response DTOs

Each operation uses a dedicated request DTO and typed response data-item DTO.

Routing metadata must not be added to the body merely because the executor requires the same value for transport routing.

A value may appear in both places only when both of the following are true:

1. the downstream TAS/APIM contract independently requires the value in the request body; and
2. the executor independently requires the value as routing metadata.

For example, registration completion requires `env` in the downstream DTO and also uses the protected account environment for APIM credential routing. Those are two distinct responsibilities backed by the same validated source.

---

## 5. Responsibility Boundary

## 5.1 Executor responsibilities

The generic executor owns:

- APIM transport invocation;
- APIM credential-profile resolution;
- token acquisition and reuse;
- certificate and public-key acquisition;
- request encryption when enabled;
- response decryption when enabled;
- plain-versus-encrypted execution selection;
- request-correlation propagation;
- transport-level response parsing;
- approved transient retries;
- invalid-token recovery according to foundation policy;
- successful-attempt metadata.

## 5.2 Feature adapter responsibilities

The feature adapter owns:

- command-to-request mapping;
- exact operation path selection;
- explicit database scope;
- explicit credential-resolution context;
- explicit retry eligibility;
- response data-item type selection;
- operation-specific envelope interpretation;
- downstream business-code recognition;
- mapper invocation;
- conversion into application results.

## 5.3 Application service responsibilities

The application service owns:

- workflow prerequisites;
- protected context and session loading;
- CSRF, captcha, and OTP gates;
- account, trust, or identity validation;
- authoritative actor-role selection;
- authoritative account-environment selection;
- idempotency and replay;
- mutation unknown-outcome handling;
- public result selection;
- workflow transition and cleanup.

## 5.4 Explicitly prohibited executor responsibilities

The executor must not contain feature knowledge such as:

```text
OTP send, resend, or verification
captcha verification or consumption
registration trust eligibility
username availability
username disclosure
contact comparison or masking
Account-Ref generation
session establishment
password workflow transitions
notification business rules
personal-information validation
public ApiStatus mapping
feature-specific business error codes
```

---

## 6. Database Routing

## 6.1 `NON_ACCOUNT`

Use `ApimDatabaseScope.NON_ACCOUNT` when the call is global or occurs before one authoritative account environment exists.

The credential context must use a null account environment:

```java
new ApimCredentialResolutionContext(
    command.actorUserRole(),
    null
)
```

Typical examples include:

- login authentication;
- login account-list discovery;
- registration candidate lookup;
- username availability;
- Username Recovery member search;
- Forgot Password username and details lookup;
- Forgot Password completion where the accepted TAS contract is global.

`NON_ACCOUNT` must be intentional. It must not mean that the developer forgot to supply an environment.

## 6.2 `ACCOUNT_ENV`

Use `ApimDatabaseScope.ACCOUNT_ENV` when the downstream operation targets one authoritative TAS environment.

The caller must obtain the environment from protected, validated state before invoking the adapter.

Valid authority examples include:

- authenticated selected-account context resolved from session and Account-Ref;
- a protected selected trust established by the registration workflow;
- an internal administrator request validated by the internal API contract.

The executor request must reject blank account environments.

## 6.3 Prohibited routing sources

The following are not valid APIM routing authority unless an explicit business contract validates and protects them first:

- an arbitrary frontend `accountEnv` field;
- a nullable value that happens to be present;
- an environment returned by the same operation being routed;
- a response item from a candidate search;
- a raw trust code without validated environment mapping;
- an Account-Ref that has not been resolved by the server;
- a default global environment used as a silent fallback.

## 6.4 Response-derived routing prohibition

Data returned by an APIM operation must not be used to determine the routing of that same call.

For example:

```text
Correct:
NON_ACCOUNT account-list request
    -> APIM response containing account environments
    -> application validates and stores candidates

Incorrect:
response account environment
    -> used to route the request that produced the response
```

A response environment may become protected business state for a later operation, but it is not retroactive routing authority.

---

## 7. Actor Role and Credential Authority

## 7.1 Actor role is routing metadata

`ActorUserRole` is part of credential-profile selection. It is not automatically an authorization decision.

The application caller must supply the role explicitly.

The adapter must use:

```java
command.actorUserRole()
```

or the exact equivalent accessor defined by the command contract.

## 7.2 Authenticated operations

For authenticated operations, the actor role must originate from protected authenticated context established by the BFF session flow.

The adapter must not reconstruct or override the role.

## 7.3 Pre-authentication operations

For pre-authentication flows, no authenticated role exists yet.

An established compatibility role such as:

```java
ActorUserRole.ROLE_USER_MBR
```

may be supplied at the application boundary when required by APIM credential routing. The role means only:

```text
use the approved pre-authentication member credential profile
```

It must not be interpreted as proof that the caller is authenticated or authorized as a member.

## 7.4 Prohibited role behavior

Do not:

- hardcode the role in the executor;
- hardcode the role in a shared transport;
- choose a role based on a feature-name string;
- silently provide a role through a compatibility constructor;
- infer the role from accountEnv;
- fabricate an actor identifier or authenticated session.

---

## 8. Credential, Token, Certificate, and Crypto Pipeline

## 8.1 Credential-profile resolution

The executor uses the explicit credential-resolution context to resolve the appropriate APIM credential profile.

The profile resolver may consider:

- actor role;
- account environment when `ACCOUNT_ENV` applies;
- configured trust and environment mappings;
- approved global or environment-specific APIM profiles.

Feature adapters must not call the profile resolver directly.

## 8.2 Token handling

The generic foundation owns APIM access-token behavior, including:

- token acquisition;
- token caching;
- expiry handling;
- invalid-token eviction;
- the approved retry after invalid-token recovery.

Feature code must not acquire or cache APIM tokens.

## 8.3 Certificate and public-key handling

The generic foundation owns:

- certificate lookup;
- APIM public-key acquisition;
- certificate header construction;
- key-related transport failures.

Feature code must not retrieve or select certificates or public keys.

## 8.4 Encryption and decryption

The generic executor owns encryption-mode selection and invokes the crypto service when encryption is enabled.

The intended sequence is conceptually:

```text
resolve credentials
    -> obtain token and certificate material
    -> serialize typed request
    -> encrypt configured request fields or payload
    -> invoke APIM
    -> decrypt response envelope
    -> deserialize typed envelope
```

When encryption is disabled, the same `ApimExchangeRequest` routing metadata must still be used. Plain and encrypted execution must not have different feature-level routing behavior.

## 8.5 Feature-owned crypto prohibition

A feature adapter or transport must not import or use:

```text
ApimWebClientFacade
ApimCertificateService
ApimPayloadCryptoService
ApimCredentialProfileResolver
ApimProperties encryption decisions
manual resolveProfile(...)
manual getPublicKey(...)
manual encryptRequest(...)
manual decryptResponseEnvelope(...)
```

These dependencies are permitted only in the approved generic foundation and executor internals.

---

## 9. Request Correlation

`X-Request-Id` is the canonical correlation value propagated to APIM.

The operation path must preserve the request ID through reactive execution. When Reactor context is used, the adapter must not drop the context before executor invocation.

A feature must not propagate arbitrary inbound headers to APIM. Only explicitly approved correlation and contract headers are forwarded.

Correlation metadata must be safe to log. If a request ID is generated by the BFF, that generated value becomes the canonical value for the downstream call and evidence trail.

---

## 10. Retry Classifications

Retry classification is part of the operation contract and must be explicit in every `ApimExchangeRequest`.

## 10.1 `SAFE_BUSINESS_CALL`

Use `SAFE_BUSINESS_CALL` for read-like or otherwise safely repeatable operations where retrying a transient transport failure does not create a duplicate business mutation.

Typical examples include:

- member lookup;
- account-list retrieval;
- country-list retrieval;
- personal-information retrieval;
- contribution-summary retrieval;
- username checks.

The generic retry policy determines which transport failures are transient and how many attempts are allowed.

## 10.2 `IDEMPOTENT_MUTATION`

Use `IDEMPOTENT_MUTATION` only when duplicate mutation is prevented by a proven contract.

Evidence may include:

- a stable idempotency key accepted and enforced downstream;
- an operation contract that is inherently idempotent;
- a repository-approved mutation executor contract with equivalent duplicate protection.

Java-side idempotency alone does not automatically make an APIM mutation safe to retry after an unknown network outcome. The downstream duplicate-prevention boundary must be understood.

## 10.3 `NON_RETRYABLE`

Use `NON_RETRYABLE` when the operation mutates state and retry safety cannot be proven.

Registration completion uses `NON_RETRYABLE` because the server-owned registration idempotency key is not serialized to TAS/APIM and downstream duplicate prevention cannot be proven.

For a non-retryable mutation, the feature layer must retain ownership of unknown-outcome handling.

## 10.4 Retry classification rules

Do not:

- use `SAFE_BUSINESS_CALL` for a mutation merely because the Java method returns a business envelope;
- use `IDEMPOTENT_MUTATION` merely because the BFF has an idempotency record;
- let the WebClient facade choose retry behavior from the path name;
- silently change retry classification while refactoring an adapter;
- collapse timeout-after-send into a known business failure.

---

## 11. Mutation Unknown Outcomes

A mutation may have an unknown outcome when the request may have reached TAS but the BFF did not receive a definitive response.

Examples include:

- connection loss after request transmission;
- timeout after APIM accepted the request;
- response decryption failure after downstream processing;
- infrastructure failure while committing local success state.

The generic executor reports transport facts. The feature application layer decides how the business workflow handles uncertainty.

A feature-specific unknown-outcome strategy may include:

- recording an unknown idempotency outcome;
- blocking automatic replay;
- returning a typed system result;
- allowing later reconciliation;
- loading a previously committed result;
- retaining protected workflow state.

The executor must not report an unknown mutation as known success or known business rejection.

---

## 12. Envelope and Error Handling

## 12.1 Transport success is not business success

A successful HTTP and crypto exchange only means that the executor obtained a typed APIM envelope.

The feature mapper must still validate:

- envelope status;
- business error codes;
- required data-item count;
- required fields;
- contradictory success and error content;
- operation-specific no-record semantics;
- duplicate or ambiguous data.

## 12.2 Recognized business rejection

A known TAS/APIM business rejection must map to an approved public business result.

The BFF must not expose:

```text
ApiStatus.DOWNSTREAM_REJECTED
raw TAS error messages
internal error origins
profile IDs
credentials
keys or certificates
```

## 12.3 Technical failure

Transport, crypto, malformed-envelope, and unrecognized downstream failures must map through the feature's approved technical-error behavior.

For Forgot Password, recognized business rejections map to the approved public business status, while technical failures map to `SYSTEM_ERROR`.

## 12.4 Malformed or contradictory envelopes

Operation adapters must identify and reject conditions such as:

- success status with missing required data;
- simultaneous success and business-error data;
- unexpected item count;
- missing identity or account fields;
- unmapped identity type;
- response structure incompatible with the operation contract.

Do not move these rules into the generic executor unless they are truly universal APIM-envelope invariants.

---

## 13. Security and Sensitive-Data Rules

The following values must not be written to ordinary logs, evidence summaries, exception messages, or public responses:

```text
raw passwords
password confirmation
OTP values
captcha answers
APIM client secrets
access tokens
private keys
certificate material
raw encrypted payloads when sensitive
full protected identity values
unmasked contact details unless explicitly approved
```

Commands containing sensitive data must use redacted `toString()` implementations or avoid automatic record string rendering.

Routing metadata must remain separate from the request body unless independently required by the downstream contract.

Error logs should contain safe details such as:

- operation name;
- request ID;
- normalized failure category;
- HTTP status where safe;
- exception type;
- elapsed time;
- redacted identifiers.

---

## 14. Canonical Implementation Pattern

The following pattern illustrates a normal read-like operation. Use exact repository types and constructors rather than copying the example literally.

```java
public Mono<FeatureLookupResult> execute(FeatureLookupCommand command) {
    DownstreamRequest body = mapper.toRequest(command);

    ApimExchangeRequest<DownstreamRequest, DownstreamDataItem> exchangeRequest =
            new ApimExchangeRequest<>(
                    API_PATH,
                    body,
                    DownstreamRequest.class,
                    DownstreamDataItem.class,
                    ApimDatabaseScope.NON_ACCOUNT,
                    new ApimCredentialResolutionContext(
                            command.actorUserRole(),
                            null
                    ),
                    ApimRetryEligibility.SAFE_BUSINESS_CALL
            );

    return apimExchangeExecutor
            .postEnvelope(exchangeRequest)
            .map(mapper::toFeatureResult);
}
```

For an account-environment-scoped operation:

```java
ApimExchangeRequest<DownstreamRequest, DownstreamDataItem> exchangeRequest =
        new ApimExchangeRequest<>(
                API_PATH,
                body,
                DownstreamRequest.class,
                DownstreamDataItem.class,
                ApimDatabaseScope.ACCOUNT_ENV,
                new ApimCredentialResolutionContext(
                        command.actorUserRole(),
                        command.accountEnv()
                ),
                ApimRetryEligibility.SAFE_BUSINESS_CALL
        );
```

The application boundary must validate `command.accountEnv()` before this code runs, and the exchange contract must reject blank values defensively.

---

## 15. Adding a New APIM Operation

A future operation must follow the process below.

## 15.1 Inventory the current flow

Before editing production code, identify:

- every production caller;
- inbound controller and mapper;
- application use case;
- outbound port;
- current adapter or transport;
- request and response DTOs;
- existing facade, crypto, certificate, and profile ownership;
- current retry behavior;
- business-envelope and public-error ownership.

## 15.2 Classify the flow

Record:

```text
feature
operation and path
flow category
database scope
accountEnv authority
actor-role authority
retry eligibility
unknown-outcome owner
```

Do not implement until each value is evidence-backed.

## 15.3 Design a focused outbound command

The command should contain only:

- downstream-required fields;
- approved correlation metadata;
- actor-role routing metadata;
- account environment when required.

Do not pass an entire session, `PortalAccessContext`, controller DTO, or workflow object to the adapter.

## 15.4 Add fail-first tests

Before production implementation, add tests that fail because the current path does not meet the target boundary.

The fail-first run must be genuine and retained with:

- exact command;
- complete output;
- failing assertion;
- test totals;
- nonzero exit;
- timestamp proving it preceded the writer.

If no genuine defect exists, do not manufacture failure. Escalate for an explicit governance decision.

## 15.5 Implement the typed exchange

The final adapter must explicitly provide:

- path;
- request DTO;
- response item DTO;
- database scope;
- credential context;
- retry eligibility.

Remove the feature-owned manual transport path only after all callers are migrated.

## 15.6 Preserve operation behavior

Test all operation-specific outcomes, including:

- normal success;
- recognized business rejection;
- no record;
- invalid item count;
- missing fields;
- malformed envelope;
- technical failure;
- unknown mutation outcome where applicable.

## 15.7 Prove isolation

Verify:

- related operations remain unchanged;
- generic foundation remains unchanged;
- no duplicate Spring wiring exists;
- no deleted class remains referenced;
- no alternative production path remains.

---

## 16. Testing Strategy

## 16.1 Executor contract tests

Executor tests should cover:

- request validation;
- `ACCOUNT_ENV` blank-environment rejection;
- `NON_ACCOUNT` null-environment acceptance;
- credential-resolution inputs;
- encrypted and plain execution;
- token invalidation and approved retry;
- transient retry classification;
- non-retryable mutation behavior;
- typed envelope deserialization;
- successful-attempt consistency.

## 16.2 Adapter boundary tests

An adapter test should capture the submitted `ApimExchangeRequest` and assert:

- exact path;
- exact request DTO;
- exact response type;
- exact scope;
- exact actor role;
- exact account environment;
- exact retry eligibility;
- one executor invocation;
- no manual transport dependency.

Behavior-based request capture is preferred over source-text assertions.

## 16.3 Application workflow tests

Application tests should prove that invalid prerequisites invoke the executor zero times.

Depending on the feature, test:

- invalid CSRF;
- invalid captcha;
- invalid OTP;
- expired protected context;
- missing selected account or trust;
- missing authoritative accountEnv;
- committed idempotent replay;
- concurrent duplicate request;
- session or workflow transition ordering.

## 16.4 Architecture guards

Repository architecture tests must enforce:

- the exact governed operation set;
- no feature-owned facade, certificate, crypto, or profile dependencies;
- no legacy or duplicate execution paths;
- no alternate production test transports;
- explicit scope and retry classification;
- caller-supplied roles;
- business semantics outside the executor;
- public downstream-status isolation.

## 16.5 Full validation

When production execution, shared wiring, generic foundation, or architecture tests change, run:

```bash
export JAVA_HOME="C:/Java/OpenJDK/jdk-21"
export M2_HOME="/d/Tools/apache-maven-3.9.15"
export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH"

mvn -DskipTests test-compile
mvn -Dtest=<explicit-focused-test-list> test
mvn test
mvn -DskipTests compile
```

Use explicit test lists for focused validation. Do not derive test selection automatically from every dirty file.

---

## 17. Final Operation Routing Ledger

The table below records the finalized Task 0043 operation set.

| Operation | Feature / flow | Scope | Account environment authority | Actor-role source | Retry | Canonical production path |
|---|---|---|---|---|---|---|
| `TRPChkCustId` | Registration username availability and Forgot Password username lookup; pre-authentication | `NON_ACCOUNT` | Intentional `null` | Caller command | `SAFE_BUSINESS_CALL` | `SharedApimTrpChkCustIdTransport` |
| `TRPGetAccountList` | Login account discovery; protected pre-authentication continuation | `NON_ACCOUNT` | Intentional `null`; returned environments are output only | Caller command | `SAFE_BUSINESS_CALL` | `SharedApimTrpGetAccountListTransport` |
| `TRPGetContSumy` | Contribution Summary; authenticated | `ACCOUNT_ENV` | Authenticated selected-account command context | Caller command | `SAFE_BUSINESS_CALL` | `ApimContributionSummaryAdapter` |
| `TRPGetCountryList` | Country List; authenticated | `ACCOUNT_ENV` | Authenticated selected-account command context | Caller command | `SAFE_BUSINESS_CALL` | `ApimReferenceDataCountriesAdapter` |
| `TRPGetMemberInfo` | Personal Information retrieval; authenticated | `ACCOUNT_ENV` | Authenticated selected-account command context | Caller command | `SAFE_BUSINESS_CALL` | `ApimMemberInfoAdapter` |
| `TRPGetMsgBoard` | Notifications retrieval; authenticated | `ACCOUNT_ENV` | Authenticated selected-account command context | Caller command member type | `SAFE_BUSINESS_CALL` | `ApimNoticeMessageAdapter` |
| `TRPGetWebSysDate` | Internal Reference Date refresh | `ACCOUNT_ENV` | Validated internal command environment | Caller command | `SAFE_BUSINESS_CALL` | `ApimReferenceDateRefreshAdapter` |
| `TRPMemberLogin` | Login authentication; pre-authentication | `NON_ACCOUNT` | Intentional `null` | Explicit login command role | `SAFE_BUSINESS_CALL` | `SharedApimTrpMemberLoginTransport` |
| `TRPRegMember` | Registration completion; protected pre-authentication | `ACCOUNT_ENV` | Protected selected trust | Explicit registration command role | `NON_RETRYABLE` | `ApimRegistrationCompletionAdapter` |
| `TRPResetPassword` | Forgot Password completion; protected pre-authentication | `NON_ACCOUNT` | Intentional `null` | Explicit Forgot Password command role | `IDEMPOTENT_MUTATION` | `ApimForgotPasswordCompletionAdapter` |
| `TRPSearchMember` | Username Recovery member search; pre-authentication | `NON_ACCOUNT` | Intentional `null` | Explicit Username Recovery command role | `SAFE_BUSINESS_CALL` | `TrpSearchMemberAdapter` |
| `TRPSearchRegedMember` | Forgot Password details lookup; protected pre-authentication | `NON_ACCOUNT` | Intentional `null`; returned environment is business data only | Explicit Forgot Password command role | `SAFE_BUSINESS_CALL` | `ApimForgotPasswordDetailsLookupAdapter` |
| `TRPSearchUnregMember` | Registration member lookup; pre-authentication | `NON_ACCOUNT` | Intentional `null` | Explicit registration command role | `SAFE_BUSINESS_CALL` | `ApimRegistrationLookupAdapter` |
| `TRPUpdMemberInfo` | Personal Information update; authenticated mutation | `ACCOUNT_ENV` | Authenticated selected-account command context | Caller command | `IDEMPOTENT_MUTATION` | `ApimUpdatePersonalInformationAdapter` |
| `TRPUpdMsgRead` | Notification read-status update; authenticated mutation | `ACCOUNT_ENV` | Authenticated selected-account command context | Caller command member type | `IDEMPOTENT_MUTATION` | `ApimNotificationReadStatusAdapter` |

### 17.1 Ledger invariants

The finalized ledger has:

```text
operation count: 15
unresolved entries: 0
duplicate production paths: 0
feature-owned manual APIM paths: 0
response-derived routing paths: 0
alternate production test transports: 0
```

---

## 18. Operation-Specific Notes

## 18.1 Shared `TRPChkCustId`

`TRPChkCustId` is shared by Registration and Forgot Password, but the business meanings remain distinct.

```text
Registration:
    Is a proposed new username available?

Forgot Password:
    Does a submitted existing username resolve to a valid identity?
```

The shared transport owns execution mechanics only. Each feature owns its business interpretation.

## 18.2 Login account list

`TRPGetAccountList` is invoked before the authenticated session and selected Account-Ref exist.

Returned account environments are validated and later stored as account data. They are not routing inputs for the account-list request.

## 18.3 Registration completion

`TRPRegMember` uses the protected selected trust's environment.

The operation is `NON_RETRYABLE` because downstream duplicate prevention is not proven by the Java-side idempotency key.

## 18.4 Forgot Password completion

`TRPResetPassword` has one canonical executor path and no production test-transport seam.

The operation uses `IDEMPOTENT_MUTATION` according to the accepted completion contract. Feature code retains business mapping, completion workflow, and unknown-outcome behavior.

## 18.5 Username Recovery

Username Recovery is pre-authentication and does not require OTP.

The member search remains `NON_ACCOUNT`, and username disclosure occurs only after the application-owned captcha and identity-verification gates.

---

## 19. Spring Wiring Rules

Each outbound port must resolve to one canonical production bean.

Do not provide:

- a second constructor for injecting a feature test transport;
- a conditional branch that bypasses the executor;
- a duplicate bean for plain versus encrypted execution;
- a compatibility bean using hardcoded routing metadata;
- reflective fallback to a deleted APIM path.

Tests should mock the executor or the outbound port at the proper architecture boundary instead of adding a test-only production constructor.

After deleting an obsolete transport, verify:

```text
imports: 0
constructor references: 0
Spring bean references: 0
reflection or class-name references: 0
production callers: 0
```

---

## 20. Troubleshooting

## 20.1 Credential profile cannot be resolved

Check:

1. the caller supplied the correct actor role;
2. `ACCOUNT_ENV` has a nonblank validated environment;
3. the environment-to-profile configuration exists;
4. the operation did not incorrectly use `ACCOUNT_ENV` for a global lookup;
5. no adapter fallback is hiding missing routing data.

Do not fix the issue by hardcoding a profile ID in the adapter.

## 20.2 Invalid public key or crypto failure

Check:

- resolved credential profile;
- certificate configuration;
- public-key retrieval;
- encryption-enabled configuration;
- request field selection;
- response envelope compatibility.

The feature adapter should receive a normalized executor failure. It should not retry crypto manually.

## 20.3 Unexpected retry

Check the operation's `ApimRetryEligibility` and the generic retry policy.

For a mutation, verify that the classification is supported by downstream duplicate-safety evidence.

Do not suppress duplicate effects by adding feature-local retry counters around the executor.

## 20.4 Missing request ID

Check Reactor context preservation and the canonical `X-Request-Id` key before executor invocation.

Do not propagate all inbound headers as a workaround.

## 20.5 Malformed downstream response

Determine whether the failure is:

- transport-level deserialization;
- generic envelope invalidity;
- operation-specific item-count or required-field failure;
- recognized business rejection;
- unexpected business code.

Keep operation-specific interpretation in the feature mapper.

## 20.6 Architecture guard fails after adding an adapter

Verify that the adapter:

- uses `ApimExchangeExecutor`;
- has one production execution path;
- has no facade, certificate, crypto, or profile dependency;
- carries explicit scope and retry eligibility;
- receives actor role and accountEnv from the caller command;
- appears exactly once in the routing ledger;
- does not duplicate an existing operation path.

---

## 21. Review Checklist

Use this checklist when reviewing a new or changed APIM operation.

### Architecture

- [ ] The operation appears in the routing ledger.
- [ ] The path is exact and unique.
- [ ] One canonical production adapter or approved shared transport exists.
- [ ] The adapter invokes `ApimExchangeExecutor.postEnvelope(...)`.
- [ ] No alternate feature-owned transport path exists.

### Routing

- [ ] Database scope is explicit.
- [ ] Actor role is supplied by the application command.
- [ ] Account environment is authoritative or intentionally null.
- [ ] `ACCOUNT_ENV` rejects missing or blank accountEnv.
- [ ] Response data is not used to route the same request.
- [ ] No default routing fallback exists.

### Retry and mutation safety

- [ ] Retry eligibility is explicit.
- [ ] A mutation is not classified as safe without evidence.
- [ ] Unknown outcomes remain feature-owned.
- [ ] Idempotency and replay behavior are tested.

### Security

- [ ] Routing metadata is not improperly serialized.
- [ ] Sensitive values are redacted.
- [ ] No raw downstream error is exposed.
- [ ] `ApiStatus.DOWNSTREAM_REJECTED` is not exposed to FE.
- [ ] Correlation uses the canonical request ID.

### Testing

- [ ] Genuine fail-first evidence exists, or an approved exception is recorded.
- [ ] The exchange request is captured and asserted.
- [ ] Invalid prerequisites invoke the executor zero times.
- [ ] Success and all relevant failure branches are tested.
- [ ] Related operations and generic foundation are preserved.
- [ ] Full validation is rerun when required.

---

## 22. Maintenance Guidance

### 22.1 Keep the ledger authoritative

Whenever an operation is added, removed, renamed, or rerouted:

1. update the existing routing-ledger source of truth;
2. update the exact operation-set guard;
3. add focused executor-boundary tests;
4. prove no duplicate path exists;
5. update this guide when the architecture changes.

Do not create a second independent ledger.

### 22.2 Preserve generic-foundation isolation

It is valid for the generic executor internals to use:

```text
ApimWebClientFacade
ApimCertificateService
ApimPayloadCryptoService
ApimCredentialProfileResolver
```

It is not valid for feature adapters to use those dependencies directly.

Automated scans must classify foundation occurrences separately from feature occurrences to avoid false positives.

### 22.3 Treat reflection carefully

A generic search for `Class.forName` is not sufficient evidence of an alternate APIM path.

A reflection occurrence is an APIM architecture concern only when the reflected class participates in APIM execution, adapter selection, transport selection, routing, or credential selection.

### 22.4 Treat response access carefully

A controller reading `result.accountEnv()` for response mapping is not automatically response-derived APIM routing.

The architecture violation exists only when response data controls the routing metadata of the same or an unauthorized later APIM call.

### 22.5 Avoid production test seams

Do not add test transports, test-only constructors, or production branches solely to simplify unit tests.

Use:

- executor mocks;
- port mocks;
- captured `ApimExchangeRequest` values;
- test Spring configurations outside production code.

---

## 23. Final Architecture Statement

The APIM modernization architecture is complete when the following statement remains true:

```text
All governed TAS/APIM operations execute through one canonical typed
ApimExchangeExecutor path. Each caller supplies explicit database scope,
actor-role authority, account-environment authority or intentional null,
and retry eligibility. Generic infrastructure owns credentials, tokens,
certificates, crypto, correlation, and transport retries. Feature code owns
business-envelope interpretation, public status, workflow, idempotency,
and unknown mutation outcomes. No feature-owned manual APIM orchestration,
duplicate path, inferred routing, or response-derived routing remains.
```

At Task 0043 closure, this statement applies to all 15 governed operations.

---

## Appendix A. Key Repository Guard Categories

The repository should retain guards in these categories:

```text
exact routing-ledger operation set
executor architecture
execution-request contract
credential-resolution context
retry classification
clean architecture
legacy orchestration exclusion
duplicate-path exclusion
public downstream-status isolation
sensitive-data safety
Spring wiring uniqueness
```

## Appendix B. Standard Evidence Expectations

For a future APIM migration stage, retain:

```text
Git pre-state and prerequisite
caller and call-chain inventory
routing and actor-role classification
request and response DTO inventory
genuine fail-first evidence
writer output
focused tests
feature preservation tests
foundation preservation tests
full suite when required
compile
routing and sensitive-data audits
final changed-file classification
HEAD, index, stash, and conflict fingerprints
completion report
final review bundle
```

## Appendix C. Terminology

| Term | Meaning |
|---|---|
| Account environment | TAS environment selected from protected, validated business state and used for APIM credential routing when scope is `ACCOUNT_ENV`. |
| Actor role | Explicit credential-routing role supplied by the caller. It is not automatically proof of authentication or authorization. |
| Business envelope | The TAS/APIM operation result structure interpreted by an operation-specific mapper. |
| Canonical path | The one approved production execution route for an APIM operation. |
| Feature-owned orchestration | Direct feature management of facade, profile, certificate, crypto, or plain/encrypted transport behavior. Prohibited after migration. |
| Generic foundation | Shared APIM token, credential, certificate, crypto, transport, and retry infrastructure used internally by the executor. |
| Protected context | Server-controlled workflow or authenticated state whose integrity and confidentiality are enforced by the BFF. |
| Routing metadata | Scope, actor role, account environment, retry eligibility, and correlation information used to execute an APIM call. |
| Unknown outcome | A mutation state where the BFF cannot prove whether TAS committed the operation. |

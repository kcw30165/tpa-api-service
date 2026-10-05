# APIM Integration Capability

**Status:** Authoritative onboarding index for the APIM exchange architecture finalized by Task 0043.  
**Audience:** Developers, architects, reviewers, testers, support engineers, and solution designers.  
**Scope:** Java BFF integration with APIM as the gateway and contract facade for TAS and related downstream operations.

## Start Here

All governed APIM business operations use one canonical typed execution path:

```text
web controller
    -> application use case
    -> application outbound port
    -> operation adapter or approved business-neutral shared transport
    -> ApimExchangeRequest
    -> ApimExchangeExecutor.postEnvelope(...)
    -> generic APIM transport and security foundation
    -> APIM
    -> TAS or another downstream service
```

Read the documentation in this order:

1. [**APIM Exchange Executor Architecture and Developer Guide**](apim-exchange-executor-architecture-and-developer-guide.md)  
   The authoritative reference for executor contracts, routing, credential authority, retry classifications, operation-specific ownership, the final 15-operation ledger, integration patterns, architecture guards, and developer review requirements.

2. [**APIM Operations, Testing, and Production Runbook**](operations-and-testing.md)  
   The operational companion for environment configuration, OAuth and certificate troubleshooting, crypto diagnosis, observability, test inventories, release evidence, incident evidence, and production-readiness checks.

The following historical paths are retained only as compatibility redirects:

- [Architecture and contracts](architecture-and-contracts.md)
- [Integration and reuse guide](integration-and-reuse-guide.md)

Do not use historical guidance that instructs a feature adapter to call `ApimWebClientFacade`, certificate, crypto, token, or credential-profile services directly.

Decision records:

- [APIM payload AES-CBC wire contract and SonarQube `java:S5542`](apim-payload-aes-cbc-sonar-s5542-decision.md)

---

## Architectural Model

### Feature and application layers

The feature layers own:

- inbound request validation;
- protected workflow and session loading;
- CSRF, captcha, and OTP gates;
- selected-account or selected-trust validation;
- actor-role authority;
- authoritative account-environment selection;
- idempotency and replay;
- mutation unknown-outcome handling;
- public response and workflow transitions.

### Operation adapter layer

The operation adapter or approved shared transport owns:

- explicit downstream request mapping;
- exact operation path;
- request and response DTO types;
- explicit database scope;
- explicit credential-resolution context;
- explicit retry eligibility;
- operation-specific envelope and business-result interpretation.

### Generic APIM executor foundation

The generic executor foundation owns:

- WebClient transport;
- credential-profile resolution;
- OAuth2 client-credentials token management;
- subscription or API-key headers;
- application certificate handling;
- APIM public-certificate retrieval and caching;
- request encryption and response decryption;
- plain-versus-encrypted execution selection;
- request-ID propagation;
- transport-level envelope handling;
- approved bounded retries;
- APIM exception normalization.

Feature adapters must not duplicate these capabilities.

---

## APIM's Project Role

APIM acts as both:

- an HTTP gateway for TAS and related downstream services; and
- a contract facade that owns frontend-facing API versioning while TAS owns implementation versioning.

The Java BFF remains responsible for:

- workflow orchestration;
- session and protected-context resolution;
- request validation;
- Account-Ref resolution;
- response shaping;
- public error normalization;
- feature-specific interpretation of TAS/APIM outcomes.

APIM and TAS business errors must not leak directly to the frontend. In particular, the BFF must not expose `ApiStatus.DOWNSTREAM_REJECTED`.

---

## Governed Operation Families

The finalized architecture governs 15 APIM operations across these capabilities:

### Authentication and pre-authentication

- Member Login;
- Accessible Account List;
- Registration member lookup;
- Registration username availability;
- Registration completion;
- Username Recovery member search;
- Forgot Password username lookup;
- Forgot Password details lookup;
- Forgot Password completion.

### Authenticated and internal capabilities

- Contribution Summary;
- Country List;
- Personal Information retrieval;
- Personal Information update;
- Notice Message retrieval;
- Notification read-status update;
- Reference Date refresh.

Some feature capabilities share a downstream operation. For example, Registration username availability and Forgot Password username lookup both use `TRPChkCustId`, while retaining separate business interpretation in their feature adapters.

The authoritative operation names, paths, routing classifications, actor-role sources, account-environment authorities, retry classifications, DTOs, and canonical adapters are maintained in the routing ledger within the architecture and developer guide.

Do not maintain a second independent ledger here.

---

## Routing Model

Every operation explicitly declares:

```text
ApimDatabaseScope
ActorUserRole authority
accountEnv authority or intentional null
ApimRetryEligibility
```

### `NON_ACCOUNT`

Use `NON_ACCOUNT` for global or pre-authentication calls made before one authoritative account environment exists.

The credential context intentionally carries a null account environment.

### `ACCOUNT_ENV`

Use `ACCOUNT_ENV` when the call targets one protected and validated TAS account environment.

The environment must come from an authoritative server-controlled source, such as:

- authenticated selected-account context resolved from the session and Account-Ref;
- a protected selected trust in Registration;
- a validated internal command contract.

Do not infer scope from nullability, use response data to route the same call, accept arbitrary frontend account context, or silently fall back to a global environment.

---

## Retry Model

Every operation explicitly uses one supported retry classification:

- `SAFE_BUSINESS_CALL`;
- `IDEMPOTENT_MUTATION`;
- `NON_RETRYABLE`.

Retry classification follows business semantics and downstream duplicate-safety evidence, not the HTTP method.

Java-side idempotency alone does not prove that a downstream mutation is safe to retry after an unknown transport outcome.

Maximum attempts and backoff values are environment configuration. Historical observed values must not be treated as permanent architecture constants.

---

## Non-Negotiable Boundaries

- Controllers and application services do not call WebClient or APIM directly.
- Application ports use application or domain contracts, not APIM HTTP DTOs.
- APIM DTOs remain inside the outbound APIM boundary.
- Every operation has one canonical executor-backed production path.
- Feature adapters do not directly use facade, token, certificate, crypto, or credential-profile services.
- Feature adapters do not contain manual plain-versus-encrypted branches.
- Production test transports and alternate test-only constructors are prohibited.
- Database scope, actor role, account environment, and retry eligibility are explicit.
- Routing metadata is not serialized unless independently required by the downstream contract.
- Response data does not route the same APIM request.
- Contract mapping is explicit and operation-specific.
- Business-envelope interpretation remains outside generic infrastructure.
- Only approved fields are encrypted or decrypted.
- Credentials, access tokens, API keys, certificates, keys, passwords, OTP values, and clear PII are never logged.
- Mutations require explicit idempotency, retry, and unknown-outcome decisions.
- `X-Request-Id` is the canonical correlation value propagated to APIM.
- `Accept-Language` is the canonical language source where required.
- Browser session cookies and CSRF tokens are not propagated to APIM.

---

## Where to Start in the Code

For an existing operation, locate:

```text
inbound controller and web mapper
application use case
application outbound port
focused outbound command
operation adapter or approved shared transport
APIM request DTO
APIM response or data-item DTO
contract and result mappers
executor-boundary tests
workflow and behavior tests
routing-ledger and architecture guards
```

Then trace generic execution through:

```text
ApimExchangeExecutor
ApimExchangeRequest
DefaultApimExchangeExecutor
ApimCredentialResolutionContext
credential-profile resolution
ApimWebClientFacade
OAuth token service
certificate service
payload crypto service
retry policy
request-correlation filters
```

The facade, credential, token, certificate, and crypto classes should be reached through the generic executor foundation, not imported into a feature adapter.

---

## Adding or Changing an Operation

Before implementation:

1. identify every caller and the current call chain;
2. confirm the exact APIM path and DTO contract;
3. classify database scope;
4. prove actor-role authority;
5. prove account-environment authority or intentional null;
6. classify retry and mutation unknown-outcome behavior;
7. define a focused outbound command;
8. retain genuine fail-first evidence, or obtain a governance exception when no honest defect exists;
9. construct a typed `ApimExchangeRequest`;
10. invoke `ApimExchangeExecutor.postEnvelope(...)`;
11. retain operation-specific business mapping outside the executor;
12. update the authoritative routing ledger and durable guards;
13. run focused and full validation when required.

Do not begin by copying a legacy transport or injecting `ApimWebClientFacade` into a new adapter.

---

## Testing and Evidence

Minimum evidence for an APIM behavior change includes:

- DTO serialization and deserialization tests;
- mapper tests;
- captured `ApimExchangeRequest` assertions;
- explicit routing and retry assertions;
- success, recognized rejection, malformed-envelope, and technical-failure tests;
- mutation unknown-outcome tests where applicable;
- logging and redaction tests;
- architecture-guard validation;
- related-feature and generic-foundation preservation;
- full Maven validation when production execution, shared wiring, foundation code, or architecture guards change;
- final worktree, index, stash, and conflict-state evidence.

Do not create a feature test transport or production compatibility constructor for test convenience. Mock the executor or the outbound port at the correct boundary.

---

## Operations and Support

Use [operations-and-testing.md](operations-and-testing.md) for:

- environment connectivity and credential checks;
- OAuth token diagnosis;
- certificate and encryption troubleshooting;
- retry and unknown-outcome diagnosis;
- request-correlation checks;
- safe logging rules;
- DTO, mapper, adapter, foundation, retry, and integration test inventories;
- release evidence;
- production-readiness and incident-evidence checklists.

Never include secrets, key material, raw protected payloads, passwords, OTP values, or unmasked customer data in operational evidence.

---

## Final Architecture Invariant

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

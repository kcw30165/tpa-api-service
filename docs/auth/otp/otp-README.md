# One-Time Password (OTP) Capability

> **Status:** Documents the implementation represented by the reviewed codebase snapshot.  
> **Audience:** New team members, backend developers, reviewers, testers, and operations staff.  
> **Scope:** Pre-authentication OTP used by **Login** and **Registration**, plus guidance for reusing the capability in another pre-authentication workflow.  
> **Out of scope:** Post-login Change Password.

## Start here

This codebase implements OTP as a collection of reusable security and workflow capabilities rather than as one standalone service. Login and Registration use the same architectural building blocks—OTP challenge state, policy, auth-flow sessions, CSRF protection, idempotency, message delivery, Redis persistence, error contracts, and audit/rate-limit ports—but each workflow owns its orchestration and success behavior.

Read the documents in this order:

1. **[OTP architecture](otp-architecture.md)** — understand the components, boundaries, lifecycle, and security model.
2. **[OTP integration guide](otp-integration-guide.md)** — follow the reuse recipe when adding another workflow.
3. **[OTP operations and testing](otp-operations-and-testing.md)** — configure, test, troubleshoot, and support the capability.

## Current OTP consumers

### Login

The Login workflow:

1. Authenticates the username and password.
2. Returns all available OTP destinations, even if only one destination exists.
3. Lets the frontend select a destination.
4. Sends or resends an OTP.
5. Verifies the OTP.
6. Creates the authenticated session and returns accessible-account information.

Relevant HTTP endpoints include:

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/login/otp/send`
- `POST /api/v1/auth/login/otp/resend`
- `POST /api/v1/auth/login/otp/verify`
- `POST /api/v1/auth/login/cancel`

### Registration

The Registration workflow:

1. Performs the registration-start checks, including the configured pre-start challenge.
2. Creates and sends the initial OTP as part of registration start.
3. Allows OTP resend.
4. Verifies the OTP.
5. Continues to eligible-trust selection, username checking, and password setup.

Relevant HTTP endpoints include:

- `POST /api/v1/auth/registration/start`
- `POST /api/v1/auth/registration/otp/resend`
- `POST /api/v1/auth/registration/otp/verify`
- `POST /api/v1/auth/registration/username/check`
- `POST /api/v1/auth/registration/complete`
- `POST /api/v1/auth/registration/cancel`

## High-level architecture

```text
HTTP controller and web mapper
            |
            v
Scenario-specific application service
            |
            +--> Auth-flow session and CSRF validation
            +--> Idempotency coordination
            +--> OTP challenge port
            +--> Message-delivery port
            +--> Rate-limit and audit ports
            |
            v
Redis, SMS, email, APIM, and security adapters
```

The reusable unit is the set of shared contracts and infrastructure behind these ports. A new workflow should not copy a Login or Registration service just because it needs an OTP.

## Shared versus scenario-specific responsibilities

### Reusable capability

- Secure challenge ID and OTP-code generation
- OTP-code hashing and verification
- Challenge state and TTL persistence
- Replacement/invalidation on resend
- Verification-attempt accounting and locking
- Destination normalization, hashing, and masking
- SMS and email delivery abstractions
- Auth-flow session infrastructure
- CSRF, origin, and referer validation
- Idempotency records and completed-response replay
- Rate-limiting and security-audit abstractions
- Standard mutation response and workflow-error concepts

### Scenario-specific orchestration

- How the actor is identified
- How destinations are obtained
- Whether the user must select a destination
- Preconditions for issuing an OTP
- Workflow states before and after verification
- What a successful OTP authorizes
- Downstream calls made after verification
- Public endpoints, DTOs, error codes, and navigation

## Request security context

Depending on the endpoint, an OTP request can require:

- `X-Request-Id`
- `Idempotency-Key`
- `X-XSRF-TOKEN`
- `Accept-Language`
- `Origin`
- `Referer`
- `auth_flow_sid` cookie

Consult the operations document for the endpoint-by-endpoint requirements. Do not assume every endpoint uses the same combination.

## Important implementation rules

1. Never log a raw OTP, CSRF token, session identifier, unmasked destination, or idempotency payload.
2. Never put PII or secrets in Redis keys.
3. Treat policy values as configuration, not constants.
4. Keep controllers thin; orchestration belongs in the application service.
5. Depend on ports from application code, not Redis/SMS/email implementations.
6. Use a new idempotency key for a new logical request; replay requires the same key and the same request hash.
7. Preserve workflow-specific behavior rather than forcing all scenarios into one service.
8. Keep public responses in the standard `MutationResponse`/workflow-result shape.
9. Use injected `Clock` and deterministic test doubles for time-bound behavior.
10. Document a new OTP consumer before calling it reusable.

## Reuse readiness

Before using OTP in another workflow, be able to answer:

- What event permits OTP issuance?
- Who owns the destination data?
- Does the user select the destination?
- What does successful verification authorize?
- How long is that authorization valid?
- What are the attempt, resend, and rate-limit policies?
- Which state is stored in the auth-flow session?
- What is the idempotency boundary for start, send, resend, and verify?
- Which audit events are mandatory?
- What is the restart/recovery behavior for every terminal failure?

If any answer is unclear, define the workflow contract before implementation.

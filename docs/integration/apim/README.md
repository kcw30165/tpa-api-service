# APIM Integration Capability

> **Status:** Documents the APIM implementation represented by the reviewed codebase snapshot.  
> **Audience:** New team members, backend developers, reviewers, testers, support engineers, and solution designers.  
> **Scope:** Java BFF integration with APIM as the gateway and contract facade for TAS and related downstream operations.

## Start here

APIM is not called directly from controllers or application services. The codebase isolates APIM transport, security, DTOs, response validation, retries, and downstream error translation behind outbound ports and adapters.

Read the documents in this order:

1. **[Architecture and contracts](architecture-and-contracts.md)** — understand the layers, transport pipeline, DTO boundaries, OAuth, certificates, payload encryption, retries, and errors.
2. **[Integration and reuse guide](integration-and-reuse-guide.md)** — follow the implementation recipe when adding or changing an APIM operation.
3. **[Operations and testing](operations-and-testing.md)** — configure, test, troubleshoot, monitor, and release APIM integrations safely.

## High-level architecture

```text
Web controller
    -> application use case
    -> application outbound port
    -> operation-specific APIM adapter
    -> contract mapper
    -> operation transport
    -> shared ApimWebClientFacade
    -> OAuth / API key / certificate / encryption pipeline
    -> APIM
    -> TAS or another downstream service
```

The application boundary uses domain commands and results. APIM request/response DTOs remain inside the outbound adapter boundary.

## APIM's project role

APIM acts as both:

- An HTTP gateway for downstream services
- A contract facade that owns the frontend-facing API version while TAS owns its implementation version

The Java BFF remains responsible for workflow orchestration, session/context resolution, request validation, response shaping, and translating APIM outcomes into the common API response contract.

## Implemented integration families

The reviewed codebase contains APIM adapters or transports for areas including:

- Member Login
- Accessible Account List
- Registration lookup
- Registration username availability
- Registration completion
- Member/personal information
- Personal-information update
- Contribution summary
- Notice messages
- Notification read status
- Reference countries
- Reference-date refresh

Each operation can have its own endpoint path, operation name, DTOs, mapping, error rules, and retry eligibility while sharing the common transport/security pipeline.

## Shared APIM capabilities

- Reactive `WebClient` transport
- Credential-profile resolution
- OAuth2 client-credentials access-token management
- Subscription/API-key header
- Application certificate handling
- APIM public-certificate retrieval and caching
- Field-level request encryption and response decryption
- Signed-JWT payload integrity
- Correlation/request-ID propagation
- Envelope validation
- Retry classification and bounded backoff
- APIM exception normalization
- Sensitive-data logging sanitization

## Non-negotiable boundaries

1. Controllers never call `WebClient` or APIM directly.
2. Application ports contain no APIM HTTP DTOs.
3. APIM DTOs do not become web response DTOs.
4. Contract mapping is explicit; do not serialize domain records and hope field names match.
5. Only approved fields are encrypted/decrypted.
6. Credentials, access tokens, API keys, certificates, keys, and clear PII are never logged.
7. Retries require explicit eligibility; mutations are not automatically retryable.
8. APIM business rejection is different from transport/system failure.
9. `X-Request-Id` is the canonical correlation header propagated to APIM.
10. `Accept-Language` and selected account context are passed only when the operation contract requires them.

## Where to start in the code

For an existing operation, locate these elements:

```text
Application outbound port
Operation-specific adapter
APIM request/response DTOs
Contract mapper
Transport interface/implementation
Endpoint properties
Error mappings
Adapter and contract tests
```

Then trace the shared infrastructure through `ApimWebClientFacade`, token/certificate services, payload crypto, response validation, and logging filters.

## Current configuration examples

The reviewed configuration supports a configurable APIM base URL and timeout, OAuth client credentials, API key headers, certificate paths, optional field-level encryption, certificate caching/renewal, and a bounded retry policy. The observed retry configuration uses three maximum attempts and 100 ms backoff, but deployments must treat these as environment-specific configuration rather than architectural constants.

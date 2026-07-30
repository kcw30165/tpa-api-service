# APIM Architecture and Contracts

## 1. Architectural intent

The APIM integration follows ports and adapters. Business use cases depend on application-facing ports; operation adapters translate between domain contracts and APIM contracts; shared infrastructure owns HTTP and security mechanics.

```text
adapter/in/web
  HTTP request/response and public API contract
        |
application
  Use cases, commands, results, business workflow
        |
application/port/out
  Downstream capability contract
        |
adapter/out/apim
  Operation adapter, mapper, APIM DTOs, transport
        |
shared APIM infrastructure
  WebClient, credentials, OAuth, certificates,
  crypto, response validation, retry, logging
        |
APIM contract facade
        |
TAS / downstream implementation
```

## 2. Responsibilities by layer

### Application outbound port

Defines what the application needs, not how APIM exposes it. Port methods use application/domain commands and results and typically return Reactor `Mono` or `Flux`.

### Operation-specific adapter

Implements the outbound port and owns:

- Input validation at the adapter boundary
- Mapping domain commands to APIM requests
- Selecting credential profile and operation metadata
- Invoking the operation transport
- Validating/mapping APIM responses
- Translating APIM business and technical outcomes

### Contract mapper

Maps field-by-field between application and APIM DTOs. It is the primary protection against accidental contract leakage or field-name coupling.

### Operation transport

Expresses the APIM operation call and can be replaced with a test transport. Some flows use shared transport implementations with operation-specific commands and DTOs.

### Shared facade and security services

Own cross-cutting mechanics:

- Base URL and timeout
- OAuth token acquisition/cache
- API-key header
- Certificate header
- Payload encryption/decryption
- Signed JWT handling
- Request correlation
- Retry policy
- HTTP/APIM exception normalization
- Safe request logging

## 3. DTO boundary model

There are three separate contracts:

```text
Public BFF API DTO
    !=
Application command/result
    !=
APIM request/response DTO
```

### Rules

- Public DTOs belong to the inbound web adapter.
- Application DTOs express business intent and are transport-neutral.
- APIM DTOs preserve the exact downstream field names, casing, arrays, envelope, and raw values.
- `@JsonProperty` belongs on APIM DTOs when the wire name differs from Java naming.
- Mappers normalize only where the contract explicitly permits it.
- APIM raw values are not exposed directly to frontend responses.
- A DTO change must be reviewed as a downstream contract change, not a local refactor.

### APIM envelope validation

The shared response validator checks the APIM response envelope, error/message fields, and required data shape. An HTTP 2xx response is not automatically business success. Missing or malformed envelope fields are contract/system failures.

## 4. Outbound request pipeline

A typical operation proceeds as follows:

```text
1. Resolve endpoint and credential profile
2. Obtain/reuse OAuth access token
3. Resolve API key and headers
4. Resolve application certificate/header
5. Obtain/cache APIM public certificate when encryption is enabled
6. Map application command to APIM DTO
7. Encrypt configured request fields
8. Build/sign protected payload where required
9. Send HTTP request with X-Request-Id
10. Receive APIM envelope
11. Validate envelope
12. Verify/decrypt protected response fields
13. Map APIM data or errors to domain result
14. Return to application use case
```

Not every operation uses every step. Security behavior is configuration- and operation-dependent.

## 5. Credential profiles and OAuth

`ApimCredentialProfile` encapsulates values such as:

- Profile identifier
- Base URL
- Client ID and client secret
- Token URI
- OAuth scope
- Subscription/API key
- Certificate path

A credential-profile resolver chooses the profile from approved request context. Trust-specific selection can be implemented behind this boundary rather than scattered across adapters.

`ApimTokenService` implements OAuth2 client-credentials token management. It caches usable access tokens, refreshes near expiry, and coordinates concurrent refresh so multiple business requests do not trigger a token storm.

### OAuth rules

- Never send client credentials to business endpoints.
- Never log client secret or access token.
- Cache by credential profile, not globally across incompatible profiles.
- On an approved `invalid_token` response, evict and retry through the bounded recovery path.
- Token refresh failure is an infrastructure/authentication failure, not a TAS business rejection.

## 6. Certificates and payload protection

### Application certificate

The application certificate service loads or creates the application key/certificate material needed for outbound protection and certificate headers.

### APIM public certificate

`ApimCertificateService` retrieves and caches the APIM-side public certificate used by payload encryption. Cache expiry and renewal skew are configurable. Concurrent callers share in-flight retrieval rather than requesting duplicate certificates.

### Encryption pipeline

The reviewed implementation supports:

- AES/CBC/PKCS5Padding for configured field payloads
- RSA/OAEP with SHA-256 for AES key exchange
- Signed JWT for payload integrity/authenticity
- Configured request and response field lists

Only configured fields should be transformed. Adding a sensitive field to an APIM DTO does not automatically encrypt it; the field configuration and contract tests must also be updated.

### Certificate recovery

An approved invalid-public-key outcome can evict the cached certificate and retry once through the established recovery path. Repeated failures are surfaced as safe crypto/downstream errors.

## 7. Headers and request context

Typical shared headers include:

- OAuth `Authorization`
- APIM subscription/API-key header
- Application `Certificate` header
- `X-Request-Id`
- `Accept: application/json`

Operation DTOs may also carry values derived from authenticated context, such as account environment or selected-account data, but only according to the APIM contract.

Project rule: propagate only `X-Request-Id` as request correlation to APIM. Do not forward arbitrary inbound headers, cookies, session IDs, CSRF tokens, or frontend authorization context.

`Accept-Language` remains the canonical language source where downstream language is required. There is no `lang` query-parameter fallback.

## 8. Retry architecture

`ApimRetryEligibility` classifies operations, for example:

- Safe business call
- Idempotent mutation
- Non-retryable operation

The facade combines this classification with transient-failure detection and bounded retry configuration.

### Retry rules

- Reads may be retried only for approved transient failures.
- A mutation is retryable only where its downstream contract is idempotent or a stable idempotency mechanism exists.
- Validation/business rejections are not retried.
- Crypto/key recovery has its own bounded eviction-and-retry behavior.
- Unknown outcomes must not be converted into blind retries.
- Retry attempts retain the same correlation ID.

## 9. Error taxonomy and mapping

### Transport/system failure

Examples:

- Timeout
- DNS/connection failure
- OAuth failure
- Invalid/missing APIM envelope
- Crypto/certificate failure
- APIM 5xx

These become `ApimException` or a safe crypto exception and are normalized to stable error codes such as service unavailable, timeout, or upstream failure.

### APIM/TAS business rejection

The HTTP call and envelope are valid, but the downstream business operation rejects the request. Endpoint-specific properties map raw codes to domain outcomes or downstream error classifications.

### Field and cross-field validation

For operations such as personal-information update, APIM errors can identify type, code, and affected fields. The adapter maps APIM field names to frontend field IDs through the application/web mapping boundary and returns blocking errors in `MutationResponse.errors[]`.

### Fallback mapping

Every operation requires an approved fallback for unknown downstream codes. Unknown codes must be logged safely with correlation and operation metadata, without exposing PII or raw protected payloads.

## 10. Logging and data protection

The APIM logging/filter layer must sanitize:

- Authorization and access tokens
- Client secret
- Subscription/API key
- Certificate and key material
- Encrypted/clear sensitive fields
- Policy/certificate numbers
- User identifiers
- Passwords
- Email/mobile/contact data

Body logging is not a default debugging technique for APIM operations. Where enabled in non-production, it must remain field-aware, size-bounded, and sanitized.

## 11. Existing operation patterns

### Login and account list

Dedicated contract mappers and transports handle Member Login and Account List. Login maps APIM OTP destination items into application destinations and classifies incorrect username/password, invalid account, account locked, and unavailable outcomes. Account List maps authorized account data and supports the post-OTP session-establishment boundary.

### Registration

Separate adapters/transports handle unregistered-member lookup, username availability, and registration completion. They preserve scenario-specific error handling while sharing APIM infrastructure.

### Portal data operations

Contribution, member information, notice messages, notification updates, countries, reference date, and personal-information update each have operation adapters and strict DTOs behind application ports.

## 12. Extension boundaries

### Reuse directly

- `ApimWebClientFacade`
- Token and certificate services
- Payload crypto
- Credential-profile resolution
- Response envelope validation
- Request correlation and safe logging
- Retry classification infrastructure
- APIM exception model

### Configure or extend

- Endpoint path and operation name
- Credential profile
- Encrypted fields
- Retry eligibility
- Raw downstream error mappings
- Certificate cache TTL/renewal skew

### Implement per operation

- Application outbound port
- APIM request/response DTOs
- Contract mapper
- Transport call
- Adapter result/error mapping
- Operation-specific tests

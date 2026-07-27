# NGTPA API Service

> **Document ownership:** This README is the source of truth for the current implemented service behaviour, local setup, configuration, API contracts, logging behaviour, and developer/operator guidance.

**Login implementation status:** complete — credential authentication, OTP send/resend/verify/cancel, account retrieval, authenticated-session establishment, request-context resolution, and logout are implemented.  
> `architecture_plan.md` is retained as the architecture baseline and decision-history document; it should not duplicate every implementation detail.

Backend-for-Frontend (BFF) service for the **ORSO NGTPA** member portal.  
Acts as the single gateway between the Angular frontend and the APIM layer (which fronts the Progress OpenEdge business logic). No local database — all state lives in Progress via APIM.

The service runs as a reactive Spring Boot application and reads local development settings from the repository root `.env` file via Spring config import.

---

## Tech Stack

| Concern | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0.6 |
| HTTP (inbound) | Spring WebFlux |
| HTTP (outbound) | Spring WebFlux `WebClient` |
| Cross-cutting logging | Spring AOP (`ExecutionLoggingAspect`) — package-pattern pointcut for use cases; `@LogExecution` for adapters/facades |
| Auth (outbound) | Spring Security OAuth2 Client Credentials |
| Encryption | BouncyCastle 1.82 (RSA + AES/CBC) |
| Spreadsheet export | Apache POI OOXML |
| Cache | Redis (Lettuce reactive driver, Sentinel mode) |
| Build | Maven 3.9.x (/d/Tools/apache-maven-3.9.15) |
| JDK | OpenJDK 21 (`C:\Java\OpenJDK\jdk-21`) |

---

## Architecture

Login uses the same ports-and-adapters rules as the rest of the service. The framework-neutral application layer owns credential, OTP, account-list, session-lifecycle, logout, and context-resolution contracts. Spring configuration composes the use cases; APIM, Redis, crypto, mail/SMS, cookies, headers, and WebFlux remain adapter/infrastructure concerns.

The implemented Login runtime adds these main capabilities:

```text
Credential authentication -> protected pre-auth session -> shared OTP lifecycle
-> account retrieval/validation -> protected authenticated session
-> session-scoped PortalAccessContext -> logout/revocation
```


## Logging Stack

The project uses the SLF4J API with Logback as the runtime implementation:

- **API**: SLF4J (`org.slf4j`) is used in application code (for example, `ExecutionLoggingAspect`).
- **Runtime**: Logback (`ch.qos.logback:logback-classic`) is provided via the Spring Boot starters.
- **Bridging**: `log4j-to-slf4j` and `jul-to-slf4j` bridge Log4j and java.util.logging to SLF4J → Logback.
- **Configuration**: Use `logging.level.*` in `src/main/resources/application.yml` for simple overrides. For advanced configuration add `logback-spring.xml` or `logback.xml` to `src/main/resources`.

Clean Architecture / Hexagonal (Ports & Adapters):

```
com.bct.ngtpa.apiservice
├── domain/              # Pure Java — no Spring, no I/O
│   ├── model/           # NoticeMessage, MessageType, MessageStatus, AudienceType, Hyperlink
│   └── exception/       # DomainException
├── application/         # Orchestration — no Spring @Service; wired by UseCaseConfig
│   ├── port/
│   │   ├── in/          # GetNotificationsUseCase, UpdateNotificationsReadStatusUseCase, GetContributionSummaryUseCase, ExportContributionSummaryUseCase, RefreshReferenceDateUseCase, GetAllReferenceDatesUseCase, CleanUpAllReferenceDatesUseCase, GetReferenceDataCountriesUseCase, GetPersonalInformationUseCase, UpdatePersonalInformationUseCase
│   │   └── out/         # ApimNoticeMessagePort, ApimNotificationReadStatusPort, ApimContributionSummaryPort, ApimReferenceDataCountriesPort, ReferenceDatePort, ApimReferenceDateRefreshPort, ReferenceDateCacheUpdatePort, CachePort, CacheAdminPort, ApimMemberInfoPort, ApimUpdatePersonalInformationPort, PortalAccessContextPort, CurrencyDisplayPort
│   ├── usecase/         # GetNotificationsService, UpdateNotificationsReadStatusService, GetContributionSummaryService, ExportContributionSummaryService, RefreshReferenceDateService, GetAllReferenceDatesService, CleanUpAllReferenceDatesService, GetReferenceDataCountriesService, GetPersonalInformationService, UpdatePersonalInformationService
│   ├── dto/             # Notification, contribution summary, reference data, reference-date cache admin, and personal-information commands/results; CurrencyDisplay; CacheCapability; CacheEntry; PortalAccessContext (ActorContext, MemberOwnerContext, AccountContext)
│   └── exception/       # InvalidContributionRequestException, InvalidNotificationRequestException, InvalidPersonalInformationUpdateException, PortalAccessContextResolutionException
├── adapter/
│   ├── in/web/          # Reactive controllers, request/response records
│   │   ├── NotificationController
│   │   ├── ContributionController
│   │   ├── ReferenceDataController
│   │   ├── PersonalInformationController
│   │   ├── UpdatePersonalInformationController
│   │   ├── ContributionSummaryWorkbookExporter
│   │   ├── ApiExceptionHandler (@RestControllerAdvice)
│   │   ├── config/      # Web presentation config
│   │   │   ├── ContributionSummaryProperties     # Binds contribution-summary.* YAML
│   │   │   ├── ContributionWebDisplayConfig      # Neutral record: total labels + XLSX headers
│   │   │   └── ContributionWebDisplayConfigProvider  # Adapts ContributionSummaryProperties → ContributionWebDisplayConfig
│   │   ├── filter/      # Inbound WebFilter infrastructure
│   │   │   ├── RequestLoggingWebFilter           # Correlation ID + global RequestHeaderContext extraction + lifecycle logs
│   │   │   ├── RequestHeaderContextWebFilter     # Thin test wrapper over the live global filter path
│   │   │   └── RequestLoggingProperties          # Binds request-logging.* YAML
│   │   ├── request/     # UpdateNotificationsReadStatusRequest, RefreshReferenceDateRequest
│   │   └── response/    # Notification, contribution summary, reference-date refresh/cache-admin, and mutation response records
│   └── out/
│       ├── apim/            # APIM integration
│       │   ├── config/                        # APIM-specific configuration
│       │   │   ├── ApimProperties             # Binds apim.* YAML (base-url, timeout, oauth, encryption)
│       │   │   └── ApimCryptoConfig           # Registers BouncyCastle JCA provider @Bean
│       │   ├── client/                    # APIM-specific WebClient construction and filters
│       │   │   ├── ApimWebClientConfig    # @Bean apimWebClient (OAuth2, cert header, filters)
│       │   │   ├── ApimRequestIdExchangeFilter    # Propagates X-Request-Id from Reactor Context
│       │   │   └── ApimRequestLoggingExchangeFilter # Logs outbound APIM requests as structured JSON
│       │   ├── oauth/                     # APIM OAuth2 bean registration
│       │   │   └── ApimOAuthClientConfig  # ReactiveClientRegistrationRepository, ReactiveOAuth2AuthorizedClientManager
│       │   ├── certificate/               # APIM certificate header utilities
│       │   │   └── ApimCertificateHeaderProvider  # Thin wrapper over ApimAppCertificateService
│       │   ├── credential/                # APIM credential profile resolution
│       │   ├── crypto/                    # APIM-specific crypto helpers and exceptions
│       │   ├── dto/                       # APIM request/response POJOs
│       │   ├── ApimWebClientFacade        # Pure HTTP transport (OAuth2 token attach)
│       │   ├── ApimNoticeMessageAdapter   # Implements ApimNoticeMessagePort
│       │   ├── ApimNotificationReadStatusAdapter  # Implements ApimNotificationReadStatusPort
│       │   ├── ApimContributionSummaryAdapter     # Implements ApimContributionSummaryPort
│       │   ├── ApimReferenceDataCountriesAdapter  # Implements ApimReferenceDataCountriesPort
│       │   ├── ApimMemberInfoAdapter              # Implements ApimMemberInfoPort
│       │   ├── ApimUpdatePersonalInformationAdapter # Implements ApimUpdatePersonalInformationPort
│       │   ├── ApimCertificateService     # Fetches BCT public key from APIM
│       │   ├── ApimAppCertificateService  # Loads app RSA keys + X509 cert
│       │   └── ApimPayloadCryptoService   # AES/CBC + RSA field encryption/decryption
│       ├── config/          # Config-property-backed adapters
│       │   ├── CurrencyMappingProperties              # Binds currency-mapping.* YAML
│       │   ├── DisplayFormatConfigSource              # Reads display-format.* values behind ConfigSource
│       │   ├── CurrencyMappingConfigSource            # Reads currency-mapping.* values behind ConfigSource
│       │   ├── DefaultConfigVariantResolver           # Global variant resolver for config-backed display/message lookups
│       │   ├── DisplayFormatKeyCandidateStrategy      # Legacy display-format candidate strategy; to be replaced by DefaultConfigKeyCandidateStrategy
│       │   ├── CurrencyMappingKeyCandidateStrategy    # Legacy currency candidate strategy; to be replaced by DefaultConfigKeyCandidateStrategy
│       │   └── ConfigBackedCurrencyDisplayAdapter     # Implements CurrencyDisplayPort
│       ├── configserver/    # ConfigMap / Spring Cloud Config Server YAML-backed adapters (legacy)
│       │   ├── ReferenceDateProperties                # Binds reference-date.* YAML (active accountEnv/cache settings plus legacy override/refresh fields)
│       │   ├── ConfigBackedReferenceDateAdapter       # Retained for reference; not the active ReferenceDatePort bean
│       │   └── ReferenceDateResolver                  # Shared production-like / override resolution (used by legacy adapter only)
│       ├── referencedate/   # Active ReferenceDate outbound adapter
│       │   ├── ReferenceDateAdapter               # PROD/DR => HK system date; nonp => Redis → APIM → HK system date
│       │   ├── ReferenceDateRedisCacheAdapter         # Implements ReferenceDateCacheUpdatePort via CachePort
│       │   └── config/
│       │       └── ReferenceDateAdapterConfig         # @Bean referenceDatePort (wires active read adapter and Redis write-back port)
│       ├── configservice/   # Config Service REST API outbound adapter
│       │   ├── config/
│       │   │   └── ConfigServiceProperties            # Binds config-service.* YAML (base-url, timeout-milliseconds)
│       │   ├── client/
│       │   │   └── ConfigServiceWebClientConfig       # @Bean configServiceWebClient
│       │   ├── dto/
│       │   │   ├── ConfigServiceRequest               # Adapter-private PUT request DTO (must not leak outside adapter)
│       │   │   └── ConfigServiceResponse              # Adapter-private GET/PUT response DTO (must not leak outside adapter)
│       │   └── ConfigServiceWebClientAdapter          # Implements ConfigServicePort — GET, PUT, DELETE
│       ├── redis/           # Redis outbound cache adapter
│       │   ├── config/
│       │   │   ├── RedisCacheProperties               # Binds redis-cache.* YAML (Sentinel, SSL, pool)
│       │   │   └── RedisAdapterConfig                 # @Bean LettuceConnectionFactory, ReactiveRedisTemplate, RedisCacheKeyFactory, CachePort, CacheAdminPort
│       │   ├── RedisCacheKeyFactory                   # Deterministic key builder: \<prefix\>:\<capability\>\[:\<part\>...\]; capability scan pattern builder
│       │   ├── RedisStringCacheAdapter                # Implements CachePort via ReactiveRedisTemplate\<String,String\>
│       │   └── RedisCacheAdminAdapter                 # Implements CacheAdminPort using capability-scoped SCAN/delete
│       └── security/        # Non-APIM security concerns
│           ├── TemporaryPortalAccessContextProperties  # Binds temporary-portal-access-context.profiles.*
│           └── TemporaryPortalAccessContextAdapter     # Implements PortalAccessContextPort
├── config/              # Spring composition only — use case @Bean wiring
│   └── UseCaseConfig    # @Bean definitions for application use case implementations
├── infrastructure/      # Cross-cutting Spring infrastructure
│   ├── config/          # Shared config resolver AOP + bean wiring
│   │   ├── ConfigVariantResolverConfiguration
│   │   └── ResolveConfigAspect
│   ├── logging/         # AOP execution logging and sanitization
│   │   ├── ExecutionLoggingAspect         # AOP around @LogExecution + package-pattern for use cases
│   │   ├── LoggingSanitizer               # Masks sensitive fields in logged values
│   │   └── LoggingSanitizerProperties     # Binds logging-sanitizer.* YAML
│   ├── security/        # Security filter chain and CORS configuration
│   │   ├── SecurityConfig                 # Spring Security WebFlux filter chain
│   │   └── CorsProperties                 # Binds cors.* YAML
│   ├── jackson/         # Jackson ObjectMapper customization
│   │   └── JacksonConfig
│   └── webclient/       # Generic WebClient.Builder bean
│       └── WebClientBaseConfig
├── shared/
│   ├── config/          # Framework-free config resolver contracts and records
│   ├── logging/
│   │   └── LogExecution.java              # Method-level AOP annotation (adapters/facades only)
│   └── web/
│       ├── RequestCorrelation.java        # X-Request-Id header/attribute/context key constants
│       ├── RequestHeaderContext.java      # Account-Ref, requestId, language carrier for inbound Reactor Context
│       └── RequestHeaderContextKeys.java  # Shared header names and RequestHeaderContext attribute/context keys
└── exception/           # ApimException (shared)
```

## Inbound Request and Authenticated Context

`RequestLoggingWebFilter` resolves the global inbound `RequestHeaderContext` once per request and stores it in both Reactor Context and `ServerWebExchange` attributes. The relevant headers are:

- `Account-Ref` — opaque selected-account reference for account-scoped APIs.
- `X-Authenticated-Session-Id` — opaque post-login session transport. Values use the `sid_` prefix.
- `X-Request-Id` — correlation identifier; a UUID is generated when missing or blank and the effective value is returned in the response header.
- `Accept-Language` — inbound language preference; defaults to `en` when missing or blank.

`auth_flow_sid` is a separate **pre-authentication HttpOnly cookie** used by registration and login workflows before portal authentication. It is not accepted as authenticated portal-session transport.

### Selected-account request rules

Selected-account APIs require both:

```http
X-Authenticated-Session-Id: sid_<opaque-value>
Account-Ref: acc_<opaque-value>
```

The runtime flow is:

```text
X-Authenticated-Session-Id
  -> load/decrypt/touch authenticated session in Redis
  -> resolve Account-Ref only within that session
  -> create PortalAccessContext
  -> propagate through exchange attributes and Reactor Context
```

`AuthenticatedAccountContextResolver` is the primary runtime `PortalAccessContextPort`. `TemporaryPortalAccessContextAdapter` is not component-scanned and is retained only for isolated legacy/local tests. There is no fallback to the retired `Session-Id` header or to YAML-backed account profiles.

For an account-scoped request, missing, malformed, expired, revoked, or unknown session state—and unknown or cross-session `Account-Ref` values—fail closed with HTTP **401**, `err.security.authentication.required`, and no session/account existence disclosure.

Only `X-Request-Id` is propagated to APIM. `Account-Ref`, `X-Authenticated-Session-Id`, and `Accept-Language` are not propagated to APIM.

---

## APIM Response Envelope

All APIM endpoints share a single top-level envelope and are modeled once under `adapter/out/apim/dto`:

```json
{
  "response": {
    "err-message": "",
    "data": []
  }
}
```

Use `ApimResponseEnvelope<T>` / `ApimResponseBody<T>` for outbound APIM parsing; do not create endpoint-specific wrapper DTOs for the shared envelope. The APIM adapter must unwrap `response.data`, check `response.err-message`, and map items into domain/application objects.

When APIM request DTOs still require a JSON field named `env`, that remains an adapter-boundary mapping from internal `accountEnv`; application and domain code should continue to use `accountEnv`.

### Dependency Rule

```text
adapter/in/web          →  application  →  domain
```

### SMS Sender Adapter

P2.5 adds the outbound SMS sender adapter behind `SmsSenderPort`.

Configuration root:

```yaml
sms:
  enabled: ${SMS_ENABLED:true}
  base-url: ${SMS_BASE_URL:}
  send-single-path: ${SMS_SEND_SINGLE_PATH:/sms/ext/sendSingleSMS.do}
  username: ${SMS_USERNAME:}
  password: ${SMS_PASSWORD:}
  send-id: ${SMS_SEND_ID:}
  class-code: ${SMS_CLASS_CODE:}
  default-country: ${SMS_DEFAULT_COUNTRY:HK}
  timeout-millis: ${SMS_TIMEOUT_MILLIS:5000}
```

The adapter maps `SmsMessage` to the `/sms/ext/sendSingleSMS.do` provider payload.

Security:

- SMS password is never logged.
- Full phone number is never logged.
- SMS content is never logged.
- Raw OTP values are never logged.

This phase does not implement registration endpoints or SMTP email sending.
adapter/out/apim        →  application  →  domain
adapter/out/config      →  application  →  domain
adapter/out/configserver →  application  →  domain
adapter/out/configservice →  application  →  domain
adapter/out/referencedate →  application  →  domain
adapter/out/security    →  application  →  domain
config                  →  application use case @Bean wiring only (UseCaseConfig)
infrastructure          →  Spring/framework infrastructure only (no application/domain imports)

---

## Prerequisites

- **JDK 21** at `C:\Java\OpenJDK\jdk-21`
- **Maven 3.9.x** at `D:\Tools\apache-maven-3.9.15` (used by the wrapper via `maven-wrapper.properties`)
- Network access to APIM (`trafficorso-sitk8apim.bcthk.info`) and OAuth token endpoint for live calls

---

## Building

```bash
# Compile
export JAVA_HOME="C:/Java/OpenJDK/jdk-21" && export M2_HOME="/d/Tools/apache-maven-3.9.15" && export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH" && mvn -DskipTests compile

# Run tests
export JAVA_HOME="C:/Java/OpenJDK/jdk-21" && export M2_HOME="/d/Tools/apache-maven-3.9.15" && export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH" && mvn test
```

> On Windows without `JAVA_HOME` in PATH, prefix each command with `JAVA_HOME="C:/Java/OpenJDK/jdk-21"` or use the VS Code tasks defined in `.vscode/tasks.json`.

---

## Running Locally

## Current Login Workflow

The complete username/password, OTP, account-selection, authenticated-session, request-context, and logout workflow is active.

### Endpoints

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/login/otp/send`
- `POST /api/v1/auth/login/otp/resend`
- `POST /api/v1/auth/login/otp/verify`
- `POST /api/v1/auth/login/cancel`
- `POST /api/v1/auth/logout`

### Login start

`POST /api/v1/auth/login` validates and normalizes the username, preserves the password exactly for credential authentication, calls `TRPMemberLogin` through the shared APIM transport, and returns all available OTP destinations—even when only one destination exists.

Required request headers:

```text
Idempotency-Key
X-Request-Id
Accept-Language
```

A successful response establishes a protected pre-auth flow:

- writes the HttpOnly `auth_flow_sid` cookie;
- returns the plaintext CSRF token once in endpoint-specific `result.data.csrfToken`;
- stores only the CSRF hash in the server-side session;
- returns opaque destination IDs plus masked destinations;
- returns `nextStep = SELECT_OTP_DESTINATION`.

Credentials, raw destinations, and the plaintext CSRF token are never persisted in the pre-auth session. The encrypted login context contains only the validated workflow state needed by later login operations.

### Login OTP lifecycle

Login OTP operations reuse the shared `OtpChallengePort`, `MessageDeliveryPort`, challenge state, Redis persistence, hashing, expiry, cooldown, verification counters, and invalidation behavior used by registration. There is no login-specific OTP store or OTP channel model.

```yaml
auth-workflows:
  login:
    otp:
      length: 6
      numeric-only: true
      ttl-seconds: 300
      resend-cooldown-seconds: 60
      max-verify-attempts: 5
      resend-limit-enabled: false
```

- OTP send requires `auth_flow_sid`, `X-XSRF-TOKEN`, `Idempotency-Key`, `X-Request-Id`, and a selected opaque `destinationId`.
- Send and resend are Redis-idempotent; completed replay does not create or deliver another OTP.
- Resend observes cooldown, replaces the active challenge, invalidates the previous challenge, and keeps the session in `OTP_SENT`.
- Login has no resend-count limit, but expiry, cooldown, CSRF, origin/referer, idempotency, and verification-attempt protections remain active.
- Invalid OTP remains on `VERIFY_OTP` and returns the remaining verification attempts.
- Verification-attempt exhaustion invalidates/cancels the pre-auth flow and returns `nextStep = RESTART_FLOW` with action code `RESTART_FLOW`.
- `POST /api/v1/auth/login/cancel` invalidates any active challenge, cancels the pre-auth flow, clears pre-auth cookies, and returns `RETURN_TO_LOGIN`.

In non-production-like deployments, physical Login OTP delivery uses the separate Login destination overrides. The original verified destination remains authoritative for masking, workflow state, hashing, and validation.

```yaml
auth-workflows:
  login:
    message:
      destination-override:
        mobile: ${LOGIN_MESSAGE_OVERRIDE_MOBILE:}
        email: ${LOGIN_MESSAGE_OVERRIDE_EMAIL:}
```

### Account retrieval and selection

After successful OTP verification, the BFF calls `TRPGetAccountList` through the shared APIM envelope, encryption, credential, retry, and request-ID infrastructure. Request values come only from the protected pre-auth context.

The BFF validates and formats each returned account:

- policy number: digits only, positive, width `11`, left-zero padded;
- certificate number: positive integer, width `4`, left-zero padded;
- termination pair: blank/blank or `T` plus strict `dd/MM/yyyy`;
- malformed records are omitted when at least one valid record remains;
- zero valid records fail the login continuation.

Each valid account is assigned a cryptographically random, session-scoped `Account-Ref`. Public verification success returns only opaque account references and `nextStep = SELECT_ACCOUNT`; policy, certificate, environment, trust, scheme, and termination details remain inside the protected authenticated-session context.

### Authenticated session

Successful OTP verification creates the authenticated session before deleting the pre-auth flow. If pre-auth cleanup fails, the new authenticated session is revoked and no session identifier is exposed.

Authenticated sessions:

- use opaque values such as `sid_<URL-safe-random-value>`;
- are protected as a complete AES-256-GCM context before Redis persistence;
- store `ActorContext`, ordered account mappings, a CSRF hash, status, and idle/absolute expiry metadata;
- enforce idle and absolute expiry without extending the absolute timeout;
- use Redis Sentinel through the existing `CachePort` infrastructure;
- resolve `Account-Ref` only inside the loaded session.

On successful OTP verification, the BFF returns the session value in the response header:

```http
X-Authenticated-Session-Id: sid_<opaque-value>
```

The authenticated session ID is not included in the JSON response. The successful transition clears `auth_flow_sid` and the pre-auth XSRF cookie.

### Authenticated requests and logout

Account-scoped APIs use:

```http
X-Authenticated-Session-Id: sid_<opaque-value>
Account-Ref: acc_<opaque-value>
```

`POST /api/v1/auth/logout` accepts `X-Authenticated-Session-Id`, revokes the server-side session, returns `Cache-Control: no-store`, and is intentionally idempotent. Missing, malformed, absent, expired, or already-terminated sessions return the same generic success response so session existence is not disclosed. The frontend must discard its stored session value after logout.

### Security and diagnostics

- Raw passwords, OTPs, CSRF tokens, destinations, session IDs, account identifiers, policy numbers, and certificate numbers are not logged.
- Login send/resend/verify diagnostics use stable semantic events and safe statuses only.
- Public errors use stable BFF-owned codes and never expose raw APIM/Progress codes or Redis/crypto details.
- `X-Authenticated-Session-Id` is the only authenticated-session header; the retired `Session-Id` and former `sid_xxx` header names have no compatibility fallback.

---

## Current Registration Workflow

The registration workflow is active and uses the common `MutationResponse` contract. Registration endpoints do not require `Account-Ref`; they use the opaque pre-authentication session transported by the `auth_flow_sid` cookie.

### Endpoints

- `POST /api/v1/auth/captcha/challenge`
- `POST /api/v1/auth/captcha/verify`
- `POST /api/v1/auth/captcha/refresh`
- `POST /api/v1/auth/registration/start`
- `POST /api/v1/auth/registration/otp/resend`
- `POST /api/v1/auth/registration/otp/verify`
- `POST /api/v1/auth/registration/username/check`
- `POST /api/v1/auth/registration/complete`
- `POST /api/v1/auth/registration/cancel`

Protected operations require the applicable combination of `auth_flow_sid`, `X-XSRF-TOKEN`, `Idempotency-Key`, `X-Request-Id`, and `Accept-Language`.

### Registration lookup contract

Registration start and post-OTP trust refresh use `TRPSearchUnregMember`. The normal application outcomes are `ELIGIBLE` and `NOT_ELIGIBLE`. `ELIGIBLE` must contain at least one valid account/trust entry. `err.tpa.member.not.found` maps to `NOT_ELIGIBLE`.

Any other APIM `err-message`, a malformed envelope, blank success with empty data, contact mismatch, or an eligible item missing required account fields is a downstream technical/contract failure—not a business result. Frontend identity type `STAFF_NO` maps to APIM `STAFF` at the adapter boundary.

### OTP lifecycle

```yaml
auth-workflows:
  registration:
    otp:
      length: 6
      numeric-only: true
      ttl-seconds: 300
      resend-cooldown-seconds: 60
      max-verify-attempts: 5
      max-resend-attempts: 5
      invalidate-previous-on-resend: true
```

- The initial OTP does not consume a resend attempt.
- Five resends are permitted, so the maximum is six OTP deliveries: one initial send plus five resends.
- Challenge replacement owns cooldown, resend counting, invalidation of the previous challenge, and creation of the new active challenge.
- Each OTP challenge/send has five verification attempts.
- Registration start has no rate limit.
- There is no destination-hash OTP-send limiter and no registration `rate-limit` policy tree.

Raw OTP values are never persisted. Redis stores only the OTP hash and challenge state under opaque challenge identifiers.

### Completion and trust continuation

After OTP verification, one eligible trust advances to username checking and multiple trusts advance to trust selection. Completion calls `TRPRegMember`. If eligible unregistered trusts remain, the frontend may continue with another trust; otherwise the workflow returns the user to login and expires the auth-flow cookie.

### Error and logging model

Public workflow errors use typed `WorkflowErrorKey` implementations: `RegistrationErrorKey` and `SharedWorkflowErrorKey`. The removed `RegistrationPublicErrorCode`, `RegistrationWorkflowErrorKey`, and `RegistrationWorkflowErrorKeys` types must not be reintroduced.

Lookup contract failures emit masked semantic failure events. Public responses do not expose APIM error codes or internal origins. Completed `4xx` responses use `WARN`/`http.request.failed`; completed `5xx` responses use `ERROR`/`http.request.failed`.


### Registration OTP Destination Override

Registration OTP delivery supports a non-production-only physical destination override so test OTPs do not require database mobile/email changes.

```yaml
auth-workflows:
  registration:
    message:
      destination-override:
        mobile: ${REGISTRATION_MESSAGE_OVERRIDE_MOBILE:}
        email: ${REGISTRATION_MESSAGE_OVERRIDE_EMAIL:}
```

Deployment classification uses `DeploymentEnvironmentPolicy` and the first active Spring profile:

- `PROD` and `DR` are production-like. OTPs use the original verified recipient, and override values are not required.
- Every other value is non-production-like, including local, DEV, SIT, UAT, blank, missing, and unknown profiles. Both override values are mandatory; startup fails when either is blank or missing.
- There is no enable flag and no fallback to the original recipient in a non-production-like deployment.

Delivery rules:

- Existing registration logic first validates recipient availability and selects `MOBILE` or `EMAIL`.
- `MOBILE` uses `REGISTRATION_MESSAGE_OVERRIDE_MOBILE` in non-production; `EMAIL` uses `REGISTRATION_MESSAGE_OVERRIDE_EMAIL`.
- Substitution occurs only at the `RegistrationMessagePort` physical-delivery boundary and applies to both registration start and OTP resend.
- The original database destination remains authoritative for eligibility, channel selection, the masked destination shown to the frontend, OTP challenge subject hashing, and registration session state. The removed destination-hash OTP-send limiter must not be reintroduced.
- Errors and logs must not expose original destinations, override destinations, OTP values, message bodies, or provider credentials.

Non-production local startup requires both variables:

```bash
export REGISTRATION_MESSAGE_OVERRIDE_MOBILE="<test-mobile>"
export REGISTRATION_MESSAGE_OVERRIDE_EMAIL="<test-email>"
```

Do not commit real destination values to YAML or source control. Supply them through environment-specific deployment configuration or a secret/configuration manager.

### OTP Challenge and Resend Lifecycle

`OtpChallengePort` is active for registration start, resend, and verification. It provides hash-only Redis persistence, expiry, verification-attempt tracking, lockout, replacement, and invalidation. The auth-flow session stores the active challenge ID server-side.

The configured maximum is five **resends**, not five total sends. Initial delivery leaves five resends available, so at most six OTPs can be delivered. Each replacement challenge receives a fresh five-attempt verification allowance.

### Via VS Code Debugger (recommended)

Open **Run & Debug** → select **"Debug NgtpaApiServerApplication"** → press F5.

All required environment variables are pre-configured in `.vscode/launch.json`.

### Via Maven task or shell

```bash
export JAVA_HOME="C:/Java/OpenJDK/jdk-21" && export M2_HOME="/d/Tools/apache-maven-3.9.15" && export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH" && mvn spring-boot:run

export JAVA_HOME="C:/Java/OpenJDK/jdk-21" && export M2_HOME="/d/Tools/apache-maven-3.9.15" && export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH" && mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Spring Boot imports the repository root `.env` file automatically via `spring.config.import`, so local `APIM_*` variables do not need to be exported one by one.

Browser access from a local frontend to a deployed API stays closed by default. To allow a local Angular app running at `http://localhost:4200` to call the dev deployment, set `CORS_ALLOWED_ORIGINS=http://localhost:4200` in the dev deployment configuration. Do not set this variable in environments that should remain closed to browser cross-origin calls.

If you hit a stale class problem after refactors, run a clean rebuild first:

```bash
export JAVA_HOME="C:/Java/OpenJDK/jdk-21" && export M2_HOME="/d/Tools/apache-maven-3.9.15" && export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH" && mvn clean compile -DskipTests
```

---

## Page Schema Validation DSL

This repository includes a generic Page Schema Validation DSL bound to the `bff-pages` YAML root. The DSL is exposed as typed configuration and statically linted as part of the test-suite.

- Documentation: `docs/pageconfig/045-page-schema-validation-dsl.md`
- Binding model: `BffPagesProperties` and related classes under `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/pageconfig`
- Linter: `com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageSchemaLinter` (returns `LintResult`)
- Test fixtures: `src/test/resources/pageconfig/` and tests under `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/pageconfig`
- Run tests and linter: `./mvnw test` (binding and linter tests are included in the suite)

To add a page schema, place YAML under `src/main/resources/pageconfig/` or add entries to an existing `bff-pages` YAML. Prefer adding a test fixture under `src/test/resources/pageconfig/` and a focused unit test to verify binding and linting (TDD).

## Personal Information GET

Endpoint: GET /api/v1/personal-information

- Purpose: returns the BFF page schema for the Personal Information UI (fields, sections, confirmation metadata).
- Headers required for selected-account semantics:
  - `Account-Ref`: required for selected-account APIs; resolved from Reactor context via `RequestHeaderContextKeys.CONTEXT_KEY`.
  - `X-Request-Id`: echoed back in responses and propagated to APIM via `ApimRequestIdExchangeFilter`.
  - `Accept-Language`: used for UI text choice and error-message locale resolution; normalized via `RequestLanguageResolver` (defaults to `en`).
- APIM dependency: the GET uses an outbound APIM call to TRPGetMemberInfo (`/ws/NGTPA/v1/TRPGetMemberInfo`) implemented by `ApimMemberInfoAdapter` and exposed to the application layer via `ApimMemberInfoPort`.
- Page YAML and mapping:
  - Page schema for Personal Information is page-specific and bound using the existing `bff-pages` DSL (place YAML under `src/main/resources/pageconfig/` and add fixtures under `src/test/resources/pageconfig/`).
  - The application maps APIM `response.data[0].config[]` (list of `{item-id, config-value}`) into BFF field metadata using the `PersonalInformationFieldMapper` implementation.
  - APIM `config` rules implemented: `HIDDEN` (field omitted), `READONLY` (readonly=true, required=false), `EDITABLE_COM` (readonly=false, required=true), `EDITABLE_OPTION` (readonly=false, required=false).
  - Unmapped APIM config items or missing expected APIM item-ids are logged as errors via `LoggingSanitizer` and are not emitted to the frontend.
  - Country values are not converted by the Java mapping (the raw APIM value is emitted; localization and country display values are handled by the page schema/option sources if needed).
  - The API returns `optionSource` (reference to a front-end option list) from the page schema — it does not return option value lists from the server.
  - Confirmation metadata is included in the response (driven by the page YAML `confirmation` section).

Notes:

- The controller follows the same selected-account pattern as `ReferenceDataController`: it resolves `Account-Ref` from the Reactor Context and throws `PortalAccessContextResolutionException` with `ErrorCodes.MEMBER_CONTEXT_INVALID` when missing or blank.
- The use case is framework-free and wired in `UseCaseConfig` as `GetPersonalInformationService` which resolves the `PortalAccessContext`, calls `ApimMemberInfoPort`, and delegates to `PersonalInformationFieldMapper` to assemble the page payload.

---

## Environment Variables

| Variable | Description | Default |
|---|---|---|
| `SERVER_PORT` | HTTP port | `8888` |
| `SPRING_CLOUD_CONFIG_ENABLED` | Enable Spring Cloud Config | `false` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated browser origins allowed for `/api/**` CORS responses | _(empty / closed)_ |
| `REFERENCE_DATE_ACCOUNT_ENV` | Current configured business/account environment used for reference-date lookup | _(empty)_ |
| `REFERENCE_DATE_OVERRIDE_DATE` | Optional non-production override date in `dd/MM/yyyy` | _(empty)_ |
| `REFERENCE_DATE_OVERRIDE_ZONE_ID` | Optional non-production override zone ID paired with `REFERENCE_DATE_OVERRIDE_DATE` | _(empty)_ |
| `REFERENCE_DATE_CACHE_TTL_SECONDS` | Legacy TTL property from the old Config Service populate flow. The active `ReferenceDateAdapter` does not use it. | `0` |
| `REFERENCE_DATE_REFRESH_CACHE_TTL_SECONDS` | TTL in seconds for the internal refresh Redis write. | `86400` |
| `REFERENCE_DATE_REFRESH_CONFIG_SERVICE_APPLICATION` | Legacy inactive property retained in YAML/properties only; the active refresh flow does not write Config Service. | _(empty)_ |
| `REFERENCE_DATE_REFRESH_CONFIG_SERVICE_PROFILE` | Legacy inactive property retained in YAML/properties only; the active refresh flow does not write Config Service. | _(empty)_ |
| `REFERENCE_DATE_REFRESH_CONFIG_SERVICE_LABEL` | Legacy inactive property retained in YAML/properties only; the active refresh flow does not write Config Service. | _(empty)_ |
| `REGISTRATION_MESSAGE_OVERRIDE_MOBILE` | Physical mobile destination for registration OTP delivery in non-production-like deployments. Required together with the email override outside PROD/DR. | _(empty; mandatory outside PROD/DR)_ |
| `REGISTRATION_MESSAGE_OVERRIDE_EMAIL` | Physical email destination for registration OTP delivery in non-production-like deployments. Required together with the mobile override outside PROD/DR. | _(empty; mandatory outside PROD/DR)_ |
| `LOGIN_PREAUTH_CONTEXT_KEY_VERSION` | Version label for the Login pre-auth AES-GCM key | _(required)_ |
| `LOGIN_PREAUTH_CONTEXT_SECRET` | Base64-encoded 32-byte AES key for protected Login pre-auth context | _(required)_ |
| `AUTH_WORKFLOWS_LOGIN_SESSION_PROTECTION_KEY_VERSION` | Version label for the authenticated-session AES-GCM key | _(required)_ |
| `AUTH_WORKFLOWS_LOGIN_SESSION_PROTECTION_BASE64_KEY` | Base64-encoded 32-byte AES key for protected authenticated sessions; must differ from the pre-auth key | _(required)_ |
| `LOGIN_MESSAGE_OVERRIDE_MOBILE` | Physical mobile destination for Login OTP delivery in non-production-like deployments | _(empty; mandatory outside PROD/DR)_ |
| `LOGIN_MESSAGE_OVERRIDE_EMAIL` | Physical email destination for Login OTP delivery in non-production-like deployments | _(empty; mandatory outside PROD/DR)_ |
| `AUTH_WORKFLOWS_LOGIN_SESSION_TTL_SECONDS` | Absolute authenticated-session lifetime | `900` |
| `AUTH_WORKFLOWS_LOGIN_SESSION_IDLE_TIMEOUT_SECONDS` | Authenticated-session idle timeout | `900` |
| `APIM_BUSINESS_RETRY_MAX_ATTEMPTS` | Total attempts for eligible safe APIM business calls, including the initial attempt | `3` |
| `APIM_BUSINESS_RETRY_BACKOFF_MILLISECONDS` | Backoff between eligible APIM business-call attempts | `100` |
| `APIM_BASE_URL` | APIM base URL (preferred) | _(required)_ |
| `APIM_BASEURL` | APIM base URL (legacy fallback) | _(see `APIM_BASE_URL`)_ |
| `APIM_TIMEOUT_MILLISECONDS` | WebClient timeout in ms (preferred) | `10000` |
| `APIM_TIMEOUTMILLISECONDS` | WebClient timeout in ms (legacy fallback) | _(see `APIM_TIMEOUT_MILLISECONDS`)_ |
| `APIM_CLIENT_REGISTRATION_ID` | OAuth2 client registration id | `apim-client` |
| `APIM_CLIENT_ID` | OAuth2 client ID | `local-dev` |
| `APIM_CLIENT_SECRET` | OAuth2 client secret | `local-dev` |
| `APIM_CLIENT_AUTH_METHOD` | OAuth2 auth method | `client_secret_basic` |
| `APIM_CLIENT_SCOPE` | OAuth2 scope | _(empty)_ |
| `APIM_TOKEN_URI` | OAuth2 token endpoint | `http://localhost/token` |
| `APIM_ENCRYPTION_ENABLED` | Enable RSA/AES payload encryption | `true` |
| `APIM_CERTIFICATE_PATH` | Path to BCT public cert endpoint | `/api/wssupport/v1/encryption/certificate` |
| `APIM_API_KEY` | API key sent as `KeyId` header | _(required if encryption enabled)_ |
| `APIM_PRIVATE_KEY_PEM` | App RSA private key (Base64 PEM) | _(required if encryption enabled)_ |
| `APIM_PUBLIC_KEY_PEM` | App RSA public key (Base64 PEM) | _(required if encryption enabled)_ |
| `TEMP_NOTIF_POLICY_NO` | Temporary notification policy number | `policyNo_for_notifications` |
| `TEMP_NOTIF_CERT_NO` | Temporary notification certificate number | `certNo_for_notifications` |
| `TEMP_NOTIF_USER_ID` | Temporary notification user ID | `userId_for_notifications` |
| `TEMP_NOTIF_TRUST_CODE` | Temporary notification trust code | `trustCode_for_notifications` |
| `TEMP_NOTIF_SCHEME_TYPE` | Temporary notification scheme type | `schemeType_for_notifications` |
| `TEMP_NOTIF_TERM_STATUS` | Temporary notification raw term status (`O`/`P`/`S`/`T` or blank) | _(empty)_ |
| `TEMP_NOTIF_TERM_COMPLETION_DATE` | Temporary notification term completion date (`dd/MM/yyyy`) | _(empty)_ |
| `TEMP_CONT_POLICY_NO` | Temporary contribution policy number | `policyNo_for_contributions` |
| `TEMP_CONT_CERT_NO` | Temporary contribution certificate number | `certNo_for_contributions` |
| `TEMP_CONT_USER_ID` | Temporary contribution user ID | `userId_for_contributions` |
| `TEMP_CONT_TRUST_CODE` | Temporary contribution trust code | `trustCode_for_contributions` |
| `TEMP_CONT_SCHEME_TYPE` | Temporary contribution scheme type | `schemeType_for_contributions` |
| `TEMP_CONT_TERM_STATUS` | Temporary contribution raw term status (`O`/`P`/`S`/`T` or blank) | _(empty)_ |
| `TEMP_CONT_TERM_COMPLETION_DATE` | Temporary contribution term completion date (`dd/MM/yyyy`) | _(empty)_ |
| `API_SECURITY_REQUIRE_AUTHENTICATION` | Enable in-process HTTP Basic auth. Set to `false` when auth is enforced externally by a K8s ingress or API gateway. | `true` |
| `CONFIG_SERVICE_BASE_URL` | Base URL of the internal Config Service REST API | _(empty / disabled)_ |
| `CONFIG_SERVICE_TIMEOUT_MILLISECONDS` | WebClient read timeout in ms for the Config Service | `10000` |
| `CONFIG_SERVICE_USERNAME` | HTTP Basic auth username sent to the Config Service | _(empty)_ |
| `CONFIG_SERVICE_PASSWORD` | HTTP Basic auth password sent to the Config Service | _(empty)_ |

For the dev cluster, the Kubernetes deployment or external config repository must set `CORS_ALLOWED_ORIGINS=http://localhost:4200` before local frontend calls from that origin will succeed. Those deployment manifests are outside this repository.

---

## Authentication and Portal Access Context

### Local and external HTTP authentication

HTTP Basic authentication remains available for local development when `api.security.require-authentication=true`; local credentials are defined in `application-local.yml`. Deployments that authenticate at ingress, gateway, service mesh, or another trusted external layer may set `API_SECURITY_REQUIRE_AUTHENTICATION=false`.

This transport-layer setting is separate from the implemented portal Login workflow documented above.

### Runtime portal context

Portal access context is now resolved from the protected authenticated session—not from the temporary YAML profile configuration.

```text
X-Authenticated-Session-Id + Account-Ref
  -> AuthenticatedAccountContextResolver
  -> active protected session
  -> selected session-owned account
  -> PortalAccessContext(ActorContext, AccountContext)
```

`ActorContext` contains only:

```text
actorUserId
actorUserRole
```

There is no `actorType` field in the authenticated-session model.

`TemporaryPortalAccessContextAdapter` and its profile properties remain in the repository only for isolated legacy/local test compatibility. The adapter is not component-scanned, is not the runtime `PortalAccessContextPort`, and no selected-account request falls back to the temporary YAML data.

Future OAuth2/OIDC resource-server integration may replace the outer authentication mechanism, but it is not required for the current Redis-backed portal-session and account-context flow.

---

## Reference Date Orchestration

The reference date is resolved through the `ReferenceDatePort` outbound port for **all** flows that require it: contribution summary validation, contribution export, and notification flows (`GetNotificationsService`, `UpdateNotificationsReadStatusService`). Neither notification service contains a hardcoded date constant.

The active implementation is `ReferenceDateAdapter` (under `adapter/out/referencedate`), registered by `ReferenceDateAdapterConfig`. Application services depend only on `ReferenceDatePort`; they do not import Redis, APIM, or Spring types.

### Source chain

```
PROD / DR deployment env
1. Hong Kong system date only

non-production-like deployment env
1. Redis cache  — key: ${redis-cache.key-prefix}:reference-date:<accountEnv>  (e.g. ngtpa:reference-date:JP)
2. APIM — fetched through the existing refresh-style APIM reference-date port using the same `accountEnv`
3. Hong Kong system date
```

### Fallback rules

| Scenario | Behaviour |
|---|---|
| PROD / DR deployment env | Return `LocalDate.now(Asia/Hong_Kong)`; do not call Redis or APIM |
| Redis disabled | Skip Redis; read APIM |
| Redis miss | Read APIM |
| Redis read error | Log sanitised WARN (no connection details); read APIM |
| Redis hit | Parse `dd/MM/yyyy` value and return date |
| Redis invalid / blank value | Log sanitised WARN when invalid and fall through to APIM |
| APIM hit | Update Redis using the existing refresh-style Redis write port; return date even when the Redis write partially fails |
| APIM miss / error | Log sanitised WARN; fall back to Hong Kong system date |

### Production-like environments

Production-like behavior is determined from the runtime deployment environment (active Spring profile), not from `accountEnv`. `accountEnv` selects the reference-date keys and the APIM `env` request value; it does not decide whether production-like fallback is active.

### Date format

All dates are stored and parsed in `dd/MM/yyyy` format (for example `25/12/2025`). Strict parsing uses a proleptic-year formatter so valid Redis values do not fall through unexpectedly.

### APIM refresh/update flow

The active APIM write-back flow updates Redis only. Config Service is not part of the active read or refresh path.

### Configuration

```yaml
reference-date:
  cache-ttl-seconds: ${REFERENCE_DATE_CACHE_TTL_SECONDS:0}  # legacy inactive setting retained for the old Config Service path
  refresh:
    cache-ttl-seconds: ${REFERENCE_DATE_REFRESH_CACHE_TTL_SECONDS:86400}
    config-service:
      application: ${REFERENCE_DATE_REFRESH_CONFIG_SERVICE_APPLICATION:}
      profile: ${REFERENCE_DATE_REFRESH_CONFIG_SERVICE_PROFILE:}
      label: ${REFERENCE_DATE_REFRESH_CONFIG_SERVICE_LABEL:}
```

`reference-date.override-date` and `reference-date.override-zone-id` remain only for the legacy config-backed adapter path; the active `ReferenceDateAdapter` does not consult them.

---

## Config Service Adapter

The `ConfigServiceWebClientAdapter` (under `adapter/out/configservice`) provides the BFF with read, write, and delete access to the internal Config Service via three outbound operations:

| HTTP method | Path | Port method |
|---|---|---|
| `GET` | `/api/configs?application=&profile=&label=&configKey=` | `ConfigServicePort.listConfigs(ConfigQuery)` |
| `PUT` | `/api/configs` | `ConfigServicePort.upsertConfig(ConfigUpsertCommand)` |
| `DELETE` | `/api/configs/{application}/{profile}/{label}/{configKey}` | `ConfigServicePort.deleteConfig(String, String, String, String)` |

**Architecture boundaries:**

- Application code depends only on `ConfigServicePort`, `ConfigEntry`, `ConfigQuery`, and `ConfigUpsertCommand` — all framework-free records in `application/port/out/` and `application/dto/`.
- HTTP request/response DTOs (`ConfigServiceRequest`, `ConfigServiceResponse`) are adapter-private to `adapter/out/configservice/dto/` and are enforced by an ArchUnit rule (`configServiceDtoTypesDoNotLeakOutsideAdapter`).
- `ConfigServiceException` maps all Config Service HTTP errors to a safe exception; it does not expose raw Config Service response bodies.

**Configuration:**

```yaml
config-service:
  base-url: ${CONFIG_SERVICE_BASE_URL:}
  timeout-milliseconds: ${CONFIG_SERVICE_TIMEOUT_MILLISECONDS:10000}
  username: ${CONFIG_SERVICE_USERNAME:}
  password: ${CONFIG_SERVICE_PASSWORD:}
```

**Scope of this task:** This task adds the outbound adapter capability only. Redis caching, read-through strategy, APIM write-back, and `ReferenceDate` orchestration are out of scope and will be addressed in later tasks.

---

## Redis Cache Adapter

The `RedisStringCacheAdapter` (under `adapter/out/redis/`) provides an outbound reactive key-value cache using Redis Sentinel with optional mTLS, backed by the Lettuce reactive driver.

### Architecture boundaries

- Application code depends only on CachePort and CacheAdminPort (in application/port/out/) — framework-free reactive contracts.
- CachePort remains limited to single-key get/set/evict operations.
- CacheAdminPort is capability-scoped and accepts CacheCapability values; it must not expose raw Redis pattern deletion to application use cases.
- All Redis types (ReactiveRedisTemplate, Lettuce, Sentinel configuration, ScanOptions) are confined to adapter/out/redis/ and enforced by ArchUnit.
- RedisCacheKeyFactory enforces key format and key safety: raw PII (policy numbers, certificate numbers, user IDs, tokens) must **never** appear as key parts. It also centralizes capability scan pattern construction.
- RedisCacheAdminAdapter implements capability-scoped scan/delete using RedisCacheKeyFactory; for reference dates it targets ${redis-cache.key-prefix}:reference-date:* only.
- CacheException maps all Redis errors to a safe exception; sensitive details (passwords, certificate paths, Sentinel addresses) are never included in the exception message. The raw cause is accessible via getCause() for diagnostic purposes only.
- CacheException is handled by ApiExceptionHandler and returns 503 Service Unavailable with error code SYSTEM\_UNEXPECTED. No stack trace is logged.

### Key format

```
<prefix>:<capability>[:<part>...]
```

Examples:

| Key | Meaning |
|---|---|
| `ngtpa:reference-date:JP` | Reference date for scheme JP |
| `ngtpa:display-format:en:amount` | Display format for locale `en`, field `amount` |

The prefix defaults to `ngtpa` and is set via `REDIS_KEY_PREFIX`.

### Configuration

```yaml
redis-cache:
  enabled: ${REDIS_CACHE_ENABLED:true}
  key-prefix: ${REDIS_KEY_PREFIX:ngtpa}
  password: ${REDIS_PASSWORD:}
  timeout-milliseconds: ${REDIS_TIMEOUT_MILLISECONDS:1000}
  sentinel:
    master: ${REDIS_SENTINEL_MASTER:ngtpaMaster}
    nodes: ${REDIS_SENTINEL_NODES:}
    password: ${REDIS_SENTINEL_PASSWORD:${REDIS_PASSWORD:}}
  ssl:
    enabled: ${REDIS_SSL_ENABLED:true}
    bundle: ${REDIS_SSL_BUNDLE:redis-mtls}
  lettuce:
    pool:
      max-active: ${REDIS_POOL_MAX_ACTIVE:20}
      max-idle: ${REDIS_POOL_MAX_IDLE:10}
      min-idle: ${REDIS_POOL_MIN_IDLE:0}
      max-wait-milliseconds: ${REDIS_POOL_MAX_WAIT_MILLISECONDS:3000}
    shutdown-timeout-milliseconds: ${REDIS_SHUTDOWN_TIMEOUT_MILLISECONDS:5000}
```

| Environment variable | Default | Description |
|---|---|---|
| `REDIS_CACHE_ENABLED` | `true` | Set to `false` to disable the Redis adapter entirely (local dev without Sentinel) |
| `REDIS_KEY_PREFIX` | `ngtpa` | Prefix prepended to every cache key |
| `REDIS_PASSWORD` | _(empty)_ | Redis AUTH password (also used as Sentinel password fallback) |
| `REDIS_TIMEOUT_MILLISECONDS` | `1000` | Command timeout for Redis operations |
| `REDIS_SENTINEL_MASTER` | `ngtpaMaster` | Sentinel master name |
| `REDIS_SENTINEL_NODES` | _(empty)_ | Comma-separated `host:port` list of Sentinel nodes |
| `REDIS_SENTINEL_PASSWORD` | `$REDIS_PASSWORD` | Sentinel-specific password; falls back to `REDIS_PASSWORD` |
| `REDIS_SSL_ENABLED` | `true` | Enable TLS on Redis connections |
| `REDIS_SSL_BUNDLE` | `redis-mtls` | Spring SSL bundle name for mTLS client certificate |
| `REDIS_POOL_MAX_ACTIVE` | `20` | Maximum active connections in the Lettuce pool |
| `REDIS_POOL_MAX_IDLE` | `10` | Maximum idle connections |
| `REDIS_POOL_MIN_IDLE` | `0` | Minimum idle connections |
| `REDIS_POOL_MAX_WAIT_MILLISECONDS` | `3000` | Maximum wait time for a connection from the pool |
| `REDIS_SHUTDOWN_TIMEOUT_MILLISECONDS` | `5000` | Lettuce client shutdown grace period |

### Kubernetes deployment assumptions

- Redis runs as a Sentinel cluster in the **same namespace** as the BFF pod.
- Sentinel nodes are reachable via in-cluster DNS, e.g., `redis-sentinel.<namespace>.svc.cluster.local:26379`.
- Set `REDIS_SENTINEL_NODES` to a comma-separated list of all Sentinel node endpoints.
- mTLS client certificate and key are provided to the pod via a Kubernetes Secret mounted at a path configured in the Spring SSL bundle (`redis-mtls`). The exact mount path is deployment-specific.
- For local development without a Sentinel cluster, set `REDIS_CACHE_ENABLED=false` to skip all Redis bean initialisation.

### Disabled mode

When `REDIS_CACHE_ENABLED=false`, the entire `RedisAdapterConfig` is skipped (via `@ConditionalOnProperty`). No `LettuceConnectionFactory`, `ReactiveRedisTemplate`, `CachePort` or `CacheAdminPort` bean is registered. Any use case that injects `CachePort` or `CacheAdminPort` will fail to start — this is intentional to prevent silent cache-bypass in production.

**Scope of this task:** This task adds the outbound cache capability only. Redis-first `ReferenceDate` strategy, cache warm-up, TTL policy, and Config Service fallback orchestration are addressed in later tasks.

---

## APIM encryption configuration

APIM encryption is configured per APIM base URL (not per endpoint). The list `apim.encryption.request-fields` contains JSON field names that the APIM adapter will encrypt for every outbound request sent to the configured `apim.base-url`.

Example configuration:

```yaml
apim:
  base-url: ${APIM_BASE_URL:${APIM_BASEURL:}}
  timeout-milliseconds: ${APIM_TIMEOUT_MILLISECONDS:${APIM_TIMEOUTMILLISECONDS:10000}}
  encryption:
    enabled: ${APIM_ENCRYPTION_ENABLED:true}
    certificate-path: ${APIM_CERTIFICATE_PATH:/api/wssupport/v1/encryption/certificate}
    api-key: ${APIM_API_KEY:}
    private-key-pem: ${APIM_PRIVATE_KEY_PEM:}
    public-key-pem: ${APIM_PUBLIC_KEY_PEM:}
    request-fields:
      - policy-no
      - cert-no
      - user-id
```

Notes:

- `apim.encryption.request-fields` is global for the `apim.base-url` and applies to every outbound request handled by the APIM client.
- It is not configured per APIM operation (for example `TRPGetMsgBoard`).
- To add encryption for a new field, add it once under `apim.encryption.request-fields`.

## Execution Logging

Global execution logging is implemented as a cross-cutting concern in `infrastructure/logging`.

- Application use case execution is logged automatically via a **package-pattern pointcut** in `ExecutionLoggingAspect`:

  ```
  execution(* com.bct.ngtpa.apiservice.application.usecase..*Service.execute(..))
  ```

  No `@LogExecution` annotation is needed on use case classes — this keeps the application layer free of Spring/framework dependencies.
- Use `@LogExecution` (from `com.bct.ngtpa.apiservice.shared.logging`) on **controller, adapter, and facade methods** that represent entry or orchestration points outside the application layer.
- Annotated synchronous methods log start, success, error, and elapsed time.
- Annotated `Mono` and `Flux` methods stay lazy; the aspect logs on subscription and completion/error without calling `block()` or subscribing internally.
- Logged arguments and results are opt-in through annotation attributes and are sanitized before serialization.
- Sensitive header and payload fields such as `Authorization`, `Certificate`, API keys, tokens, secrets, policy numbers, certificate numbers, user IDs, and key material are masked in logs.
- The sensitive key list is configured through `logging-sanitizer.sensitive-tokens`, using `src/main/resources/application-local.yml` for local development and the deployed `ngtpa-display-config` Spring YAML in Kubernetes.
- All log events are emitted as JSON-structured strings (using `ObjectMapper`) suitable for ingestion by Elasticsearch/Logstash.

Log event names: `method.execution.start`, `method.execution.success`, `method.execution.error`. When a `requestId` is available in Reactor Context (set by `RequestLoggingWebFilter`), it is included in every event.

### X-Request-Id Correlation Constants

The `X-Request-Id` header name and Reactor Context key are centralised in `com.bct.ngtpa.apiservice.shared.web.RequestCorrelation`. All components that read or write the correlation identifier — `RequestLoggingWebFilter`, `ApiExceptionHandler`, `ExecutionLoggingAspect`, `ApimRequestIdExchangeFilter`, `ApimRequestLoggingExchangeFilter` — import constants from this class. This prevents `ApiExceptionHandler` from depending on the filter implementation.

---

## Request Correlation and Structured Logging

### X-Request-Id Header

Every inbound HTTP request is assigned a correlation identifier managed by `RequestLoggingWebFilter`:

| Scenario | Behavior |
|---|---|
| `X-Request-Id` header present and non-blank | Reuse the inbound value |
| `X-Request-Id` header missing or blank | Generate a new UUID |
| All responses | `X-Request-Id` header is always returned in the response |
| Error responses | `X-Request-Id` is in the response **header only** — never in the response body |

Error response body remains:

```json
{
  "errorCode": "...",
  "message": "..."
}
```

---

## Global Exception Handling

### Business Error Code Convention

The BFF owns the `errorCode` values returned in API error responses. These values are business error codes, not HTTP status-code strings, Java exception names, APIM-specific identifiers, or raw upstream payload fragments.

The centralized registry for these stable contract values is `com.bct.ngtpa.apiservice.shared.error.ErrorCodes`.

HTTP status remains in the HTTP response status only. The JSON error response body remains:

```json
{
  "errorCode": "err.some.business.code",
  "message": "Resolved user-facing message"
}
```

`X-Request-Id` remains in the response header only and must not be added to the response body.

All public API errors handled by `ApiExceptionHandler` return exactly these two fields: `errorCode` and `message`.
The public `message` is always resolved through `ErrorMessageResolver` backed by `error-message.*` configuration; raw exception text is used only as sanitized diagnostic input for logs.
Unknown exceptions are mapped to HTTP `500` with business error code `err.system.unexpected` and a resolver-backed safe message.

Purpose of business error codes:

- Provide a stable BFF-owned API contract value that frontend clients can safely use for flow handling, analytics, and localized presentation decisions.
- Decouple client-visible error semantics from transport details such as HTTP status, Java exception class names, APIM internals, and deployment-specific implementation details.

Naming pattern:

- `err.<area>.<scenario>`
- `err.<area>.<sub-area>.<scenario>`
- `err.<area>.<sub-area>.<scenario>.<variant>`

Naming rules:

- Error codes must be stable API contract values.
- Error codes must be safe to expose to frontend clients.
- Error codes must use lowercase dot-separated tokens.
- Error-code naming must be independent from Java exception class names.
- Variant suffixes may be used only when the same scenario needs a product, trust, scheme, channel, locale, or deployment-specific variant.

Safety rules:

- Do not include sensitive identifiers, member IDs, policy numbers, certificate numbers, tokens, secrets, request IDs, or raw upstream error payloads.
- Do not embed HTTP status numbers, stack-trace details, Java class names, APIM error keys, or other implementation-specific internals.

Initial BFF error-code catalog:

- `err.request.invalid`
- `err.request.validation.failed`
- `err.request.body.malformed`
- `err.security.access.denied`
- `err.security.authentication.required`
- `err.member.context.unavailable`
- `err.member.context.invalid`
- `err.apim.upstream.failure`
- `err.apim.service.unavailable`
- `err.apim.timeout`
- `err.apim.response.invalid`
- `err.config.error-message.missing`
- `err.config.resolution.failed`
- `err.contribution.request.invalid`
- `err.notification.request.invalid`
- `err.system.unexpected`

Good error codes:

- `err.request.validation.failed`
- `err.apim.service.unavailable`
- `err.member.context.unavailable`

Bad error codes:

- `400`
- `500`
- `NullPointerException`
- `APIM_ERR_001`
- `err.member.12345678.failed`
- `err.token.expired.raw.jwt.value`

### Outbound APIM Propagation

`ApimRequestIdExchangeFilter` (under `adapter/out/apim/client`) propagates the resolved `X-Request-Id` from Reactor Context to every outbound APIM call as an HTTP header. This allows APIM-side log correlation with BFF-side logs.

### JSON Structured Log Events

All global and method-level log events are serialized as JSON strings. Key event types:

| Event | Source | Key Fields |
|---|---|---|
| `http.request.start` | `RequestLoggingWebFilter` | `requestId`, `method`, `path`, `query`, `headers` |
| `http.request.end` | `RequestLoggingWebFilter` | `requestId`, `method`, `path`, `status`, `elapsedMs` |
| `http.request.error` | `RequestLoggingWebFilter` | `requestId`, `method`, `path`, `elapsedMs`, `exceptionType`, `errorMessage` |
| `http.api.error` | `ApiExceptionHandler` | `requestId`, `httpMethod`, `path`, `httpStatus`, `errorCode`, `exceptionType`, `sanitizedMessage` |
| `apim.request` | `ApimRequestLoggingExchangeFilter` | `requestId`, `method`, `url`, `headers` |
| `method.execution.start` | `ExecutionLoggingAspect` | `requestId`*, `className`, `methodName`, `label`, `args` |
| `method.execution.success` | `ExecutionLoggingAspect` | `requestId`*, `className`, `methodName`, `elapsedMs`, `result` |
| `method.execution.error` | `ExecutionLoggingAspect` | `requestId`*, `className`, `methodName`, `elapsedMs`, `exceptionType`, `errorMessage` |

\* `requestId` is included when available from Reactor Context (Mono/Flux methods) or MDC (synchronous methods).

### Global Exception Logging Policy

`ApiExceptionHandler` logs the final API error mapping once per handled exception using the structured `http.api.error` event.

Severity policy:

- Validation errors (`err.request.validation.failed`, `err.request.body.malformed`) log at `WARN`.
- Business/application exceptions log at `WARN`.
- Authentication and access-denied failures log at `WARN`.
- APIM/integration failures log at `ERROR`.
- Unexpected exceptions log at `ERROR` and include a single stack trace.

Operational rules:

- `requestId` is included when available from `X-Request-Id` correlation.
- `errorCode` is always logged for mapped API errors.
- `sanitizedMessage` uses `LoggingSanitizer`; sensitive tokens such as API keys, tokens, certificates, policy numbers, certificate numbers, member IDs, and user IDs must be masked.
- Unexpected exceptions include the stack trace once from `ApiExceptionHandler`; expected validation/business failures do not emit stack traces by default.
- Request lifecycle logs from `RequestLoggingWebFilter` may still log start/end/status, but the detailed exception mapping is owned by `ApiExceptionHandler` to avoid duplicate exception stack traces.

### Body Logging Configuration

Request/response body logging is **disabled by default** and must be explicitly enabled:

```yaml
request-logging:
  enabled: true          # enables lifecycle logging (start/end/error); default: true
  log-headers: false     # includes allowlisted headers in start log; default: false
  header-allowlist:
    - User-Agent
    - Accept
    - Content-Type
  body-logging:
    enabled: false                      # global body logging gate; default: false
    default-max-body-size-bytes: 4096   # max logged body bytes
    endpoints:
      - method: POST
        path-pattern: /api/v1/some-endpoint
        log-request-body: true
        log-response-body: false
        max-body-size-bytes: 4096       # overrides default for this endpoint
```

Effective rules:

- `shouldLogRequestBody = request-logging.body-logging.enabled AND endpoint.log-request-body`
- `shouldLogResponseBody = request-logging.body-logging.enabled AND endpoint.log-response-body`
- No endpoint match → no body logging.
- Binary response bodies (Excel, PDF, octet-stream) are never logged regardless of configuration.

> **Security warning:** Body logging may expose PII or sensitive business data. Keep `body-logging.enabled=false` in all production and production-like environments. Only enable on specific endpoints in lower non-production environments for debugging.

---

## Presentation Sorting: `@ApplySorts`

The `@ApplySorts` annotation provides opt-in, path-based sorting for JSON API responses and Excel export data. It lives entirely in the web adapter layer (`adapter/in/web/sort`) and does **not** modify application or domain code.

### When to use

Apply `@ApplySorts` to **web-adapter methods** that return a value needing sorted lists before it is mapped to JSON or written to an Excel workbook. Never apply it to application use case `execute()` methods.

### How it works

1. The `ApplySortsAspect` intercepts any method annotated with `@ApplySorts`.
2. For synchronous return types the sorting is applied inline.
3. For `Mono<T>` the sorting is deferred via `.map()` — the aspect never subscribes or blocks internally.
4. Sorting is delegated to `SortEngine`, which navigates the object graph, sorts the target list, and rebuilds any immutable Java records in the path using the canonical constructor.

### Annotation reference

```java
// Container annotation — one or more lists to sort
@ApplySorts({
    @SortList(
        path  = "report.rows",   // dot-separated accessor path to the target List
        by    = {
            @SortBy(
                field       = "dealingDate",       // accessor name (or dot-separated nested path)
                direction   = SortDirection.DESC,  // ASC | DESC
                type        = SortType.DATE,       // STRING | NUMBER | DATE | BOOLEAN
                datePattern = "dd/MM/yyyy",        // required for DATE type
                nullsLast   = true                 // nullsLast=true (default) → nulls sort after values
            )
        }
    )
})
public MyResult sort(MyResult result) { return result; }
```

### Supported `SortType` values

| Type | Comparison | Notes |
|---|---|---|
| `STRING` | Lexicographic via `String.compareTo` | Case-sensitive |
| `NUMBER` | Numeric via `BigDecimal` | Handles `Integer`, `Long`, `Double`, `BigDecimal` |
| `DATE` | Temporal via `LocalDate` parsed with `datePattern` | Blank/null/unparseable → treated as `null` |
| `BOOLEAN` | Natural order (`false < true`) | |

### Null handling

- `nullsLast = true` (default) — null, blank, and unparseable DATE values sort **after** all non-null values.
- `nullsLast = false` — those values sort **before** all non-null values.

### Date pattern behaviour

- `datePattern` must be a valid `DateTimeFormatter` pattern (e.g. `"dd/MM/yyyy"`).
- If the field value cannot be parsed using the pattern it is treated as `null`.
- An empty `datePattern` defaults to ISO 8601 local date format (`yyyy-MM-dd`).

### Path syntax

| Path example | Description |
|---|---|
| `"items"` | Direct `List` field named `items` on the root object |
| `"report.rows"` | Navigate `root.report()` then sort `report.rows()` |
| `"report.sources"` | Navigate `root.report()` then sort `report.sources()` |
| `"data.items"` | Navigate `root.data()` then sort `data.items()` |

> **Note:** Collection traversal paths (e.g. `"items[].breakdown.rows"`) are not yet implemented. Only direct field and nested object paths are supported. This can be added without changing the annotation contract.

### Multi-field sorting

Multiple `@SortBy` rules in the same `@SortList` form a compound comparator applied in declaration order (primary, secondary, tertiary …). If two values are equal on the primary field the secondary rule is used, and so on.

### Limitations

- **Opt-in only.** The annotation does **not** recursively discover and sort all lists. Only the list at the declared `path` is sorted.
- **Records required for nested paths.** When the path includes intermediate segments (e.g. `"report.rows"`), every intermediate object must be a Java record. The engine uses the canonical constructor to rebuild the record immutably.
- **Binary exports.** Do not apply `@ApplySorts` to methods that return `ResponseEntity<byte[]>` or raw Excel bytes. Sorting must happen **before** the workbook exporter writes bytes. See the Contribution Summary example below.
- **Flux support.** `Flux<T>` sorting is not yet implemented. Existing endpoints use `Mono`. Flux support can be added without changing the annotation contract.

### Contribution Summary example

```java
@Component
public class ContributionSortingSupport {

    @ApplySorts({
        @SortList(
            path = "report.rows",
            by = {
                @SortBy(field = "dealingDate", direction = SortDirection.DESC,
                        type = SortType.DATE, datePattern = "dd/MM/yyyy"),
                @SortBy(field = "coverFrom",   direction = SortDirection.DESC,
                        type = SortType.DATE, datePattern = "dd/MM/yyyy"),
                @SortBy(field = "coverTo",     direction = SortDirection.DESC,
                        type = SortType.DATE, datePattern = "dd/MM/yyyy")
            }
        ),
        @SortList(
            path = "report.sources",
            by = {
                @SortBy(field = "sequence", direction = SortDirection.ASC, type = SortType.NUMBER),
                @SortBy(field = "code",     direction = SortDirection.ASC, type = SortType.STRING)
            }
        )
    })
    public ContributionSummaryReportResult sort(ContributionSummaryReportResult result) {
        return result; // AOP intercepts and sorts
    }
}
```

The controller calls `contributionSortingSupport::sort` before mapping to JSON and before writing Excel bytes:

```java
// JSON endpoint
getContributionSummaryUseCase.execute(command)
    .map(contributionSortingSupport::sort)           // sort first
    .map(result -> webMapper.toListResponse(...));   // then map

// Export endpoint
exportContributionSummaryUseCase.execute(command)
    .map(contributionSortingSupport::sort)           // sort first
    .map(workbookExporter::write)                    // then write bytes
    .map(body -> ResponseEntity.ok()...);
```

### Future endpoint example

```java
@ApplySorts({
    @SortList(
        path = "items",
        by = {
            @SortBy(field = "tradeDate", direction = SortDirection.DESC,
                    type = SortType.DATE, datePattern = "dd/MM/yyyy"),
            @SortBy(field = "fundCode",  direction = SortDirection.ASC,
                    type = SortType.STRING)
        }
    )
})
public TradeListResult sort(TradeListResult result) { return result; }
```

---

## Contribution Summary Configuration

Contribution summary labels, currency display mappings, and Excel headers are configured as regular Spring properties rather than environment variables. For now the runtime source is `src/main/resources/application-local.yml` plus the Kubernetes ConfigMap `ngtpa-display-config`. Config Service remains future work.

Current local defaults in `src/main/resources/application.yml`:

```yaml
reference-date:
  override-date: ${REFERENCE_DATE_OVERRIDE_DATE:}
  override-zone-id: ${REFERENCE_DATE_OVERRIDE_ZONE_ID:}

contribution-summary:
  total-label:
    en: Total Contributions
    zh: "供款總額"
  headers:
    dealing-date: Dealing date處理日期
    contribution-period: Contribution Periods供款期
    total-contribution: Total Contributions供款總額

currency-mapping:
  en:
    HKD: HKD
    HKD.TB.HKBU: HKD
  zh_HK:
    HKD: 港元
    HKD.TB.HKBU: 港元
```

`reference-date.account-env` is the business/account environment used for reference-date cache keys and the APIM `env` request on the reference-date flow. Production-like safety is based on the runtime deployment environment, not on `accountEnv`; the active `ReferenceDateAdapter` derives that deployment signal from the active Spring profile. For production-like deployment values, reference-date resolution returns the current Hong Kong date without calling Redis or APIM. For non-production-like deployment values, the active read path is Redis -> APIM -> Hong Kong system date. The legacy override-date pair and legacy Config Service settings are no longer part of the active read path. `deploymentEnv` is not part of `PortalAccessContext`.

These values drive the synthetic total detail row in the JSON response, the first three column headers in the XLSX export, the locale-specific currency display returned in contribution summary JSON, and the effective contribution reference date. Currency, date, and amount lookups now run through the global config variant resolver, which evaluates `env`, `trustCode`, and `schemeType` suffix combinations in a fixed order and then falls back to English when the requested language has no match.

## Auth Workflow Registration Configuration

`auth-workflows.registration` is the active YAML-owned policy for captcha, OTP, username, password, message templates, destination overrides, and idempotency TTLs. Java defines the typed binding schema; YAML owns the values.

There is intentionally no `auth-workflows.registration.rate-limit` block. Registration start is not rate-limited, and the OTP challenge replacement lifecycle owns the five-resend maximum and cooldown. See **Current Registration Workflow** for the canonical OTP configuration.

## API Endpoints

### `POST /api/v1/internal/reference-date/refresh`

Manually refreshes the reference date for a single `accountEnv`.

**Request:**

```json
{ "accountEnv": "JP" }
```

**Success response:**

```json
{ "accountEnv": "JP", "refDate": "31/12/2025", "redisUpdated": true }
```

**Redis partial failure response:**

```json
{ "accountEnv": "JP", "refDate": "31/12/2025", "redisUpdated": false }
```

**Behavior:**

- This is an internal operational endpoint and is authenticated when `api.security.require-authentication=true`.
- Final read path: `PROD/DR => Hong Kong system date only; nonp => Redis -> APIM -> Hong Kong system date`.
- Final refresh/write path: `APIM -> Redis`.
- Scope is one `accountEnv` per request. Scheduler-driven refresh and all-accountEnv refresh are out of scope.
- The APIM fetch uses `POST /ws/NGTPA/v1/TRPGetWebSysDate` with request body `{ "env": "<accountEnv>" }`.
- `accountEnv` is passed to APIM unchanged. Java does not derive or remap it.
- Java does not send `X-Datadomain` for this APIM operation.
- The refreshed date is read from `response.data[0].sys-date`, then written to Redis key `${redis-cache.key-prefix}:reference-date:<accountEnv>`.

### GET /api/v1/internal/reference-date/all
  
Returns all Redis reference-date entries created under the reference-date cache namespace.
**Success response:**{
  "success": true,
  "status": "SUCCESS",
  "result": {
    "referenceDates": [
      { "accountEnv": "HK", "refDate": "01/01/2026" },
      { "accountEnv": "JP", "refDate": "31/12/2025" }
    ]
  },
  "messages": [],
  "errors": []
}
**Behavior:**

- This is an internal operational endpoint and is authenticated when api.security.require-authentication=true.
- Scope is ${redis-cache.key-prefix}:reference-date:* only.
- Output is sorted by accountEnv for deterministic API responses and tests.
- The endpoint uses the generic MutationResponse envelope.

### DELETE /api/v1/internal/reference-date/all
  
Deletes all Redis reference-date entries created under the reference-date cache namespace.
**Success response:**{
  "success": true,
  "status": "UPDATED",
  "result": { "deletedCount": 2 },
  "messages": [],
  "errors": []
}
**No matching keys response:**{
  "success": true,
  "status": "UPDATED",
  "result": { "deletedCount": 0 },
  "messages": [],
  "errors": []
}
**Behavior:**

- This is an internal operational endpoint and is authenticated when api.security.require-authentication=true.
- Scope is ${redis-cache.key-prefix}:reference-date:* only.
- No matching keys is a successful no-op with deletedCount=0.
- The endpoint uses the generic MutationResponse envelope.

#### POST /api/v1/auth/registration/username/check
  
Checks whether a proposed registration username is available for the selected trust and advances the protected registration auth flow to password setup when available.  

Required inputs:
- Cookie: `auth_flow_sid`
- Header: `X-XSRF-TOKEN`
- Header: `X-Request-Id` optional but propagated through the use-case command as `correlationId`
- Header: `Accept-Language` optional; user-facing messages remain resolver/config driven
- Header: `Origin` and `Referer` optional at the controller boundary and passed to the use case
- Body: selectedTrust + username  

Request:
```json
{
  "selectedTrust": {
    "trustCode": "TL",
    "accountEnv": "ENV1"
  },
  "username": "abc1@"
}
```
  
Success response:
```json
{
  "success": true,
  "status": "SUCCESS",
  "result": {
    "nextStep": "PASSWORD_SETUP"
  },
  "messages": [],
  "errors": []
}
```
  
Validation failure response example:
```json
{
  "success": false,
  "status": "VALIDATION_FAILED",
  "result": {
    "nextStep": "USERNAME_CHECK"
  },
  "messages": [],
  "errors": [
    {
      "code": "registration.username.incorrect",
      "target": "PAGE"
    }
  ]
}
```
  
Downstream business rejection response example:
```json
{
  "success": false,
  "status": "DOWNSTREAM_REJECTION",
  "result": {
    "nextStep": "USERNAME_CHECK"
  },
  "messages": [],
  "errors": [
    {
      "code": "registration.username.isregistered",
      "target": "PAGE"
    }
  ]
}
```
  
Behavior and boundaries:
- the controller is thin: it checks missing `auth_flow_sid`, missing `X-XSRF-TOKEN`, and missing body, then delegates to `CheckRegistrationUsernameUseCase` through `RegistrationUsernameCheckWebMapper`;
- the controller does not call APIM/TAS, Redis, session ports, idempotency ports, or downstream classifiers directly;
- username/check is not action-idempotent and does not require `Idempotency-Key`; if the header is present it is ignored by this endpoint;
- selectedTrust contains only `trustCode` and `accountEnv`; `policyNo` and `certNo` are not accepted or returned;
- APIM/TAS business rejection mapping is already classified by the P9.3 use case and P9.0 DSL before web mapping;
- technical downstream failures map to `DOWNSTREAM_ERROR` and do not use page/business fallback codes.

## APIM Certificate, OAuth Token, and Credential Profile Caching

APIM credentials are represented as credential profiles. Today a single default profile is supported for backward compatibility but multiple profiles are supported for future mapping by trust.

- **Credential profiles:** Represent `client_id`, `client_secret`, `api_key`, token endpoint, base URL, and certificate endpoint as a profile object under `adapter/out/apim/credential`.
- **Resolver:** Profile selection is isolated behind `ApimCredentialProfileResolver`. The default implementation returns the configured default profile. Trust-to-profile mapping is TBC and must not be assumed yet.
- **Cache location:** Token and certificate caches live inside the existing APIM outbound infrastructure (`adapter/out/apim`). No generic `infrastructure/cache` layer is introduced.
- **Cache scope:** In-memory per application instance. Distributed cache is optional future work only.
- **Certificate caching:** Certificates are fetched once per selected profile and cached. A certificate is refreshed when TTL/expiry is reached, when local Java encryption fails, or when APIM returns `invalid_public_key`. Certificate cache is keyed by `profileId`.
- **Token caching:** OAuth `client_credentials` tokens are cached per `profileId`, refreshed proactively before expiry using a configurable skew, and evicted only for the affected profile on `invalid_token` responses.
- **API key:** The API key header is taken from the resolved credential profile. The header name is configurable via `apim.apiKeyHeaderName`.
- **Retry behavior:** For recoverable failures the APIM adapter will evict and refresh only the affected token or certificate for the selected profile, and retry the original APIM request once. Retries are limited to avoid infinite loops.
- **Security:** Secrets (client_secret, api_key, tokens, certificate bodies) must be provided via environment variables and are never logged.

### `GET /api/v1/reference-data/countries`

Retrieves country and calling-code options for reference-data lookup.

**Required headers:**

- `Account-Ref`
- `X-Request-Id`
- `Accept-Language`

**Example:**

```http
GET /api/v1/reference-data/countries
Content-Type: application/json
Account-Ref: ACC-123
X-Request-Id: client-request-uuid
Accept-Language: en
```

**Behavior:**

- The BFF calls APIM `POST /TRPGetCountryList` with JSON body `{}`.
- APIM response envelope handling follows the shared pattern (`response`, `err-message`, `data`) and shared upstream error mapping behavior.
- `countries[].value` maps from APIM `country-code`.
- `callingCodes[].value` maps from APIM `calling-code`.
- `countries[].text` and `callingCodes[].text` use the same localized name:
  - English path: APIM `country-name-eng`
  - `zh-HK` path: APIM `country-name-chi`
  - Fallback path: English name if localized Chinese is blank
- APIM order is preserved in the response arrays.
- `alpha-two-code` is not exposed in the BFF response.
- `Account-Ref` and `Accept-Language` are not propagated to APIM; only `X-Request-Id` continues to be propagated.

**Response:**

```json
{
  "countries": [
    { "value": "HKG", "text": "Hong Kong" },
    { "value": "CHN", "text": "China" }
  ],
  "callingCodes": [
    { "value": "852", "text": "Hong Kong" },
    { "value": "86", "text": "China" }
  ]
}
```

**Error response:**

```json
{
  "errorCode": "err.member.context.invalid",
  "message": "Member context is invalid."
}
```

All failures continue to use the standardized API error response envelope.

### `GET /api/v1/notifications`

Retrieves the current notice list for a member context.

**Query parameters:**

- `env` (account environment, required)
- `mbrType` (required)
- `page` (optional; accepted by the BFF but not forwarded to APIM)
- `size` (optional; accepted by the BFF but not forwarded to APIM)
- `dateFormat` (optional; default `dd/MM/yyyy HH:mm`)
- `timezone` (optional; default `Asia/Hong_Kong`)

**Example:**

```http
GET /api/v1/notifications?env=JP&mbrType=MBR&page=1&size=10&dateFormat=dd/MM/yyyy%20HH:mm&timezone=Asia/Hong_Kong
```

**Behavior:**

- Only notifications with `startDateTime <= now` in the requested or default timezone are returned.
- Notifications with missing or unparsable APIM start datetime values are treated as not visible.
- `msgCode` uses APIM `msg-code-long` when present, otherwise falls back to `msg-code`.
- `category` returns the raw APIM `msg-cate` code.
- `msgTitle` is derived from `msg-cate`.
- `isRead` is derived from APIM `msg-status`.

**Response:**

```json
{
  "notifications": [
    {
      "msgCode": "LONG-001",
      "sequence": "1",
      "category": "ACT_REQ",
      "msgTitle": "ACTION REQUIRED",
      "msgContentChi": "中文內容",
      "msgContentEng": "English content",
      "isRead": false,
      "startDateTime": "29/04/2026 14:15"
    }
  ]
}
```

**Error response:**

```json
{
  "errorCode": "err.notification.request.invalid",
  "message": "Invalid notification request."
}
```

APIM or crypto failures still use the same error envelope with `5xx` status codes.

### `PATCH /api/v1/notifications`

Updates the read status for one or more notifications.

**Request body:**

```json
{
  "env": "DEV",
  "mbrType": "MBR",
  "notificationId": ["msgCode1", "msgCode2"]
}
```

**Behavior:**

- `env` and `mbrType` must be non-blank.
- `notificationId` must contain at least one non-blank value.
- Duplicate notification IDs are preserved in request order and forwarded to APIM unchanged.
- Actor and account routing fields are resolved from the authenticated session selected by `X-Authenticated-Session-Id` and `Account-Ref`. The notification flow does not read temporary YAML portal-context profiles at runtime.

**Response:**

```json
{
  "notifications": [
    {
      "msgCode": "msgCode1",
      "isRead": true
    },
    {
      "msgCode": "msgCode2",
      "isRead": false
    }
  ]
}
```

**Error response:**

```json
{
  "errorCode": "err.request.validation.failed",
  "message": "Invalid request payload."
}
```

Top-level APIM failures are translated to the same standardized `5xx` error envelope.

### `GET /api/v1/contributions`

Retrieves grouped contribution summary rows for a member context as JSON.

**Query parameters:**

- `env` (account environment, required by frontend contract; currently forwarded only as application context)
- `mbrType` (required by frontend contract; currently forwarded only as application context)
- `fromDate` (required; `dd/MM/yyyy`)
- `toDate` (required; `dd/MM/yyyy`)
- `lang` (optional temporary fallback when `Accept-Language` is missing; normalized to `en` or `zh_HK`; defaults to `en`)
- `page` (optional; pagination page number, must be > 0; defaults to `1`)
- `pageSize` (optional; number of items per page, must be > 0; defaults to `20`)

**Example:**

```http
GET /api/v1/contributions?env=JP&mbrType=MBR&fromDate=05/04/2026&toDate=05/05/2026&lang=en&page=1&pageSize=20
Accept-Language: zh-HK
```

**Behavior:**

- The BFF calls APIM `POST /ws/NGTPA/v1/TRPGetContSumy`.
- `cover-from` is taken from `fromDate`; `cover-to` is taken from `toDate`.
- `fromDate` and `toDate` must both be within `[ref-date - 36 months, ref-date]`, inclusive.
- `ref-date` is resolved from `PortalAccessContext.account().accountEnv()` via `ReferenceDatePort`, not from a deployment-scoped config property or the request query parameter `env`.
- `accountEnv` selects the reference-date Redis/APIM keying only; production-like behavior is decided by runtime deployment env.
- `page` and `pageSize` must both be greater than 0; HTTP 400 is returned otherwise. No real backend pagination is performed yet — all data is returned from APIM and the pagination fields reflect the full dataset.
- Effective contribution language is resolved in the web adapter with this priority: `Accept-Language` header, then `lang` query parameter, then `en`.
- Contribution language normalization is `en`, `en-US`, `en_HK` -> `en`; `zh-HK`, `zh_HK`, `zh` -> `zh_HK`; blank, missing, and unknown values -> `en`.
- The resolved contribution language drives success-path JSON labels, source labels, currency text, and display formatting.
- Contribution rows are grouped by `deal-date + cover-from + cover-to`.
- Dynamic detail items are joined from `contDtl[*].disp-src` to `dispSrc[*].disp-src` and sorted by `dispSrc.seq` ascending.
- Amount `text` values are formatted using `display-format.amount.*` configuration (language and env-specific). Value `value` is the raw `BigDecimal`.
- Date values carry the query-string text (`fromDate`/`toDate`) and also the ISO date string derived from APIM `cover-from`/`cover-to` date parsing.
- Actor and account routing fields are resolved from the authenticated session selected by `X-Authenticated-Session-Id` and `Account-Ref`. The contribution flow does not read temporary YAML portal-context profiles at runtime.
- `actions.export.enabled` is always `true` (temporary stub via `TemporaryContributionActionPermissionAdapter`).
- `Accept-Language` is not propagated to APIM; only `X-Request-Id` continues to be propagated.

**Response:**

```json
{
  "actions": {
    "export": { "enabled": true }
  },
  "items": [
    {
      "itemId": "CONTRIB-2026-03",
      "itemType": "contribution",
      "period": {
        "fromDate": { "value": "2026-03-01", "text": "01/03/2026" },
        "toDate":   { "value": "2026-03-31", "text": "31/03/2026" }
      },
      "dealingDate": { "value": "2026-03-01", "text": "01/03/2026" },
      "currency": { "value": "HKD", "text": "HKD" },
      "totalContribution": {
        "amount": { "value": 24908.45, "text": "24,908.45" }
      },
      "breakdown": {
        "rows": [
          {
            "label": "Total Contributions",
            "amount": { "value": 24908.45, "text": "24,908.45" }
          },
          {
            "label": "Company",
            "amount": { "value": 17791.75, "text": "17,791.75" }
          },
          {
            "label": "Member",
            "amount": { "value": 7116.70, "text": "7,116.70" }
          }
        ]
      }
    }
  ],
  "pagination": {
    "page": 1,
    "pageSize": 20,
    "totalRecords": 1,
    "hasNextPage": false
  }
}
```

**Error response:**

```json
{
  "errorCode": "err.contribution.request.invalid",
  "message": "Invalid contribution request."
}
```

Range validation failures and pagination validation failures use the same standardized envelope with the same business error code and a resolver-backed safe message.

#### Config-backed Value Lookup Convention

Config-backed display values and user-facing messages use one global candidate ordering convention. Each lookup starts from a logical `code` and appends context suffixes derived from `accountEnv`, `trustCode`, and `schemeType`. Locale selects the language bucket; locale is not appended to the key itself.

Given `code`, `accountEnv`, `trustCode`, `schemeType`, and `locale`, the resolver tries keys in this order:

1. `code.accountEnv.trustCode.schemeType`
2. `code.accountEnv.trustCode`
3. `code.accountEnv.schemeType`
4. `code.accountEnv`
5. `code.schemeType`
6. `code`

Blank dimensions are skipped, compound candidates are emitted only when all participating dimensions are present, duplicates are removed while preserving order, and malformed keys are never emitted. Requested locale candidates are tried first; if no match is found and the requested locale is not English, the same candidate sequence is retried under `en`.

Standard config-backed adapters and resolvers should use `DefaultConfigVariantResolver` with `DefaultConfigKeyCandidateStrategy` unless a feature has an explicitly documented exception to this global lookup order. Domain-specific classes may keep their own `ConfigSource` and business fallback behaviour, but they should not define custom candidate ordering for standard config-backed lookups.

Example flat-key shapes for the target convention:

```yaml
date-display-format:
  en:
    date: dd/MM/yyyy
    date.JP: MM/dd/yyyy
  zh_HK:
    date: dd/MM/yyyy

amount-display-format:
  en:
    amount: "#,##0.00"
    amount.JP: "#,##0.000"
  zh_HK:
    amount: "#,##0.00"
    amount.JP: "#,##0.000"

currency-mapping:
  en:
    AUD: AUD
    EUR: EUR
    EUR.TB.HKBU: EUR
  zh_HK:
    AUD: 澳元
    EUR: 歐羅
    EUR.TB.HKBU: 歐元

error-message:
  en:
    "err.request.invalid": "Invalid request."
    "err.request.invalid.JP": "Your request is invalid."
  zh_HK:
    "err.request.invalid": "請求無效。"
```

### Display Format Configuration

Amount display formatting for the contribution JSON response is currently driven by `display-format.amount`. Date formatting is currently driven by `display-format.date`. Both resolve through the shared config variant resolver. The target convention is to split these into flat-key `date-display-format` and `amount-display-format` groups so date, amount, currency, and error-message all use the same `code.variant` lookup model.

#### Global config variant resolver

The shared resolver accepts a config category, a code, and a lookup context (`env`, `trustCode`, `schemeType`, locale). It generates suffix candidates in this exact order, skipping blank dimensions, removing duplicates, and never emitting malformed keys:

1. `env.trustCode.schemeType`
2. `env.trustCode`
3. `env.schemeType`
4. `trustCode.schemeType`
5. `env`
6. `trustCode`
7. `schemeType`

Requested language keys are tried first. If the requested language is not `en` and has no match, the resolver retries the same candidate sequence under `en`.

The `ERROR_MESSAGE` category uses the same lookup dimensions and candidate order. `ConfiguredErrorMessageResolver` resolves the final public API error message from this category, and `ApiExceptionHandler` returns that resolved value in the response body instead of raw exception text.

#### Error message configuration

User-facing error messages are configured under `error-message.<locale>.<errorCode>`.

- Supported locales: `en`, `zh_HK`
- Incoming locale aliases are normalized to `en` or `zh_HK` (`zh-HK` and `zh_HK` are treated the same); any other locale falls back to English
- Variant-specific overrides use the existing config dimensions: `env`, `trustCode`, `schemeType`
- Full dotted error-code keys and full variant keys should stay quoted in YAML so they bind as single map keys
- Message values must be safe for frontend display and must not contain stack traces, raw upstream payloads, tokens, request IDs, policy numbers, certificate numbers, or other internal details

Example shape:

```yaml
error-message:
  en:
    "err.apim.service.unavailable.JP": "Service is temporarily unavailable in JP environment. Please try again later."
    "err.member.context.unavailable.JP.JPM.OE": "Member context is unavailable for this scheme. Please try again later."
  zh_HK:
    "err.apim.service.unavailable.JP": "JP服務暫時未能提供，請稍後再嘗試。"
    "err.member.context.unavailable.JP.JPM.OE": "此計劃的成員資料暫時未能提供，請稍後再嘗試。"
```

Resolver fallback order:

1. Requested locale + context-specific variant candidates + base key
2. English + context-specific variant candidates + base key
3. English `err.system.unexpected`
4. Hardcoded safe fallback: `Sorry, this service might be interrupted. Please try again later.`

#### Amount format (pattern-based)

Each locale maps variant keys (or a wildcard `*`) to a `DecimalFormat` pattern string:

```yaml
display-format:
  amount:
    en:
      "[*]": "#,##0.00"
      PROD.RM.MPF: "#,##0.000"
      JP: "#,##0.00"
    zh_HK:
      "[*]": "#,##0.00"
      JP: "#,##0.000"
```

**Wildcard `[*]`**: The `[*]` YAML key must stay quoted so Spring Boot binds it as the literal `*` map key. Do not use `default`, and do not reintroduce the old object-based amount-format wrapper fields.

**Fallback order** (tried in sequence until a pattern is found):

1. `display-format.amount.<lang>.<env>.<trustCode>.<schemeType>`
2. `display-format.amount.<lang>.<env>.<trustCode>`
3. `display-format.amount.<lang>.<env>.<schemeType>`
4. `display-format.amount.<lang>.<trustCode>.<schemeType>`
5. `display-format.amount.<lang>.<env>`
6. `display-format.amount.<lang>.<trustCode>`
7. `display-format.amount.<lang>.<schemeType>`
8. `display-format.amount.<lang>.*`
9. Retry steps 1-8 with `en` locale if the requested locale has no match
10. Hardcoded default: `#,##0.00`

**Formatting rules**: `DecimalFormat` is used with English-locale symbols (`,` grouping, `.` decimal), rounding mode `HALF_UP`. The pattern `#,##0.00` always produces exactly two decimal places and never strips trailing zeros.

**Example outputs**:

| Input | Output |
|-------|--------|
| `0` | `0.00` |
| `1` | `1.00` |
| `12.3` | `12.30` |
| `1234.5` | `1,234.50` |
| `1234.567` | `1,234.57` |
| `-1234.5` | `-1,234.50` |

#### Date format

```yaml
display-format:
  date:
    en:
      "[*]": dd/MM/yyyy
      JP: MM/dd/yyyy
    zh_HK:
      "[*]": dd/MM/yyyy
```

**Wildcard `[*]`**: The `[*]` YAML key is the locale wildcard fallback. Keep it quoted, and do not use `default`.

**Fallback order** (tried in sequence until a pattern is found):

1. `display-format.date.<lang>.<env>.<trustCode>.<schemeType>`
2. `display-format.date.<lang>.<env>.<trustCode>`
3. `display-format.date.<lang>.<env>.<schemeType>`
4. `display-format.date.<lang>.<trustCode>.<schemeType>`
5. `display-format.date.<lang>.<env>`
6. `display-format.date.<lang>.<trustCode>`
7. `display-format.date.<lang>.<schemeType>`
8. `display-format.date.<lang>.*`
9. Retry steps 1-8 with `en` locale if the requested locale is missing or has no match
10. Adapter default `dd/MM/yyyy` if no config entry is found at all

Segments that are blank or absent are skipped; compound keys are only emitted when all constituent segments are present.

#### Currency mapping

Currency labels use a flat key schema under each locale. The code itself is the base key, and the suffix candidates are appended after the currency code:

```yaml
currency-mapping:
  zh_HK:
    AUD: 澳元
    EUR: 歐羅
    EUR.TB.HKBU: 歐元
  en:
    AUD: AUD
    EUR: EUR
    EUR.TB.HKBU: EUR
```

**Lookup order** (tried in sequence until a value is found):

1. `currency-mapping.<lang>.<code>.<env>.<trustCode>.<schemeType>`
2. `currency-mapping.<lang>.<code>.<env>.<trustCode>`
3. `currency-mapping.<lang>.<code>.<env>.<schemeType>`
4. `currency-mapping.<lang>.<code>.<trustCode>.<schemeType>`
5. `currency-mapping.<lang>.<code>.<env>`
6. `currency-mapping.<lang>.<code>.<trustCode>`
7. `currency-mapping.<lang>.<code>.<schemeType>`
8. `currency-mapping.<lang>.<code>`
9. Retry steps 1-8 with `en` locale if the requested locale has no match

This preserves existing flat keys such as `EUR.TB.HKBU` and `AUD.OG` while allowing more specific `env`-aware variants. At the adapter level, if no mapping resolves even after language fallback, both the English and Chinese display values fall back to the raw currency code.

### `GET /api/v1/contributions/export`

Exports the contribution summary as an XLSX workbook.

**Query parameters:**

- `env` (account environment, required by frontend contract; currently forwarded only as application context)
- `mbrType` (required by frontend contract; currently forwarded only as application context)
- `lang` (optional temporary fallback when `Accept-Language` is missing; normalized to `en` or `zh_HK`; defaults to `en`)

**Example:**

```http
GET /api/v1/contributions/export?env=JP&mbrType=MBR&lang=en
Accept: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
Accept-Language: zh-HK
```

**Behavior:**

- The BFF resolves `ref-date` through `ReferenceDatePort`.
- `ReferenceDatePort` receives `accountEnv` from `PortalAccessContext.account().accountEnv()` to key Redis and APIM lookup/update operations. Production-like behavior is decided separately from runtime deployment env, not from request query parameter `env`.
- `cover-from` is computed as `ref-date.minusMonths(36)`; `cover-to` is the resolved `ref-date`.
- Actor identity, member ownership, and account routing fields (`actor-user-id`, `policy-no`, `cert-no`, `trustCode`, `schemeType`, etc.) are resolved from externalized `temporary-portal-access-context.profiles.contributions.*` configuration until Auth Server integration is implemented.
- Effective contribution language is resolved in the web adapter with this priority: `Accept-Language` header, then `lang` query parameter, then `en`.
- Contribution language normalization is `en`, `en-US`, `en_HK` -> `en`; `zh-HK`, `zh_HK`, `zh` -> `zh_HK`; blank, missing, and unknown values -> `en`.
- The total and source workbook headers are selected from the resolved contribution language.
- The first two structural headers come from `contribution-summary.headers.*`.
- Dynamic source columns are sorted by `dispSrc.seq` ascending.
- Amount cells are numeric, formatted as `0.00`, and rounded with `HALF_UP`.
- Missing dynamic source amounts are written as blank cells.
- `Accept-Language` is not propagated to APIM; only `X-Request-Id` continues to be propagated.

**Success headers:**

- `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- `Content-Disposition: attachment; filename="Contribution_Summary.xlsx"`

Failures use the same standardized JSON error envelope as the rest of the API.

Error message localization by `Accept-Language` is deferred, and notification response behavior is unchanged.

---

## Project Documents

Registration stabilization evidence: `docs/0040-registration-stabilization-013-otp-lifecycle-evidence.md`.

| Document | Location |
|---|---|
| Functional Requirements | `docs/brd/converted/functional_reqs.md` |
| API Specifications | `docs/brd/converted/api_specs.md` |
| Data Models | `docs/brd/converted/data_models.md` |
| Gap Analysis | `docs/brd/analysis/gap_analysis.md` |
| Architecture Plan | `docs/brd/analysis/architecture_plan.md` |

## Architecture and Implementation Conventions

This section contains the current implementation conventions that were previously mixed into `architecture_plan.md`. Keep these details here because they describe how the repository currently works and how developers should extend it.

### Document Ownership

- Update this README when implementation changes affect build/run steps, endpoint contracts, environment variables, configuration properties, logging, authentication, packaging, or operational behaviour.
- Update `architecture_plan.md` only for architecture direction changes, major design decisions, confirmed TBC integrations, or new/removed architectural constraints.

### Application Layer Rules

Application use case implementations must remain framework-free:

- no `org.springframework.*` imports in `application.*`;
- no adapter imports in `application.*`;
- no `@Service`, `@Component`, or web/security annotations on use-case classes;
- use cases are wired from `config/UseCaseConfig`;
- execution logging for use cases is applied by infrastructure pointcut, not by annotating use cases.

### Web Adapter Mapping Rules

Response DTO records should remain simple data carriers. Mapping belongs in `adapter/in/web/mapper` or focused web-support classes when it involves:

- fallback logic;
- type conversion;
- dynamic formatting;
- display configuration;
- synthetic rows;
- presentation ordering;
- public API contract decisions.

ObjectMapper should be used for mechanical JSON serialization/deserialization only, not to hide business or presentation rules.

### Presentation Sorting Rules

`@ApplySorts` is a web-adapter mechanism. Apply it only to methods that sort presentation/export results before JSON mapping or workbook generation.

Do not apply presentation sorting directly to application use-case `execute(...)` methods. Binary export bytes must not be sorted directly; sort the result object before writing the workbook.

### Exception Boundary Rules

- Adapter-private exceptions should not cross architectural boundaries.
- APIM crypto or transport-specific exceptions should be mapped to the standard APIM/application error path before reaching the application or web layer.
- `ApiExceptionHandler` should not import outbound adapter internals.
- Error responses should follow the standard BFF error envelope documented in the endpoint sections.

### APIM DTO Boundary Rules

- `ApimResponseEnvelope`, `ApimResponseBody`, and endpoint-specific APIM item DTOs are confined to `adapter/out/apim`.
- Outbound ports must not expose APIM DTOs.
- Application use cases, domain models, and web controllers must not return APIM DTOs.
- Endpoint-specific APIM DTOs should model outbound request payloads and `response.data[]` item schemas only; the shared APIM response envelope should be modelled once.

### Configuration Placement Rules

- Global `config/` is composition-only and should contain use-case wiring or equivalent composition classes.
- APIM properties belong under the APIM adapter package.
- Web presentation properties belong under the web adapter package.
- Temporary member-context, display-format, reference-date, and permission adapters should stay near the adapter implementation that consumes them.

### ArchUnit Guardrails

The architecture test suite should continue to enforce these constraints:

- domain does not depend on Spring, adapter, application, or config;
- application does not depend on adapter, config, or Spring Framework;
- application does not depend on shared logging annotations;
- inbound adapters do not depend on outbound adapters;
- global `config/` remains composition-only;
- APIM internal subpackages such as DTO, crypto, credential, OAuth, certificate, and client packages do not leak outside the APIM adapter.

### Personal Information PUT

Endpoint: `PUT /api/v1/personal-information`

Purpose: updates selected personal-information fields for the account selected by `Account-Ref`.

Required headers:

- `Account-Ref`: required; resolved from the inbound `RequestHeaderContext` and used to resolve `PortalAccessContext`.
- `X-Request-Id`: echoed in the BFF response and propagated to APIM.
- `Accept-Language`: available for normal BFF error localization; it is not propagated to APIM.

Request body:

```json
{
  "formVersion": "1.0",
  "applyToAllAccounts": false,
  "fields": {
    "residentialAddressLine2": "Tai Po, New Territories",
    "hongKongMobilePhone": "98765432",
    "emailAddress": "user@example.com"
  }
}
```

`applyToAllAccounts=true` asks APIM to apply the same update to every account owned by the member. The selected account remains the account represented by the inbound `Account-Ref`.

BFF mapping rules:

- The request `fields` object uses BFF field ids from `application-page-personal-information.yml`.
- The web adapter maps each submitted BFF field id to `apimBinding.data` from the page YAML.
- `apimBinding.config` is not used for PUT mapping.
- Fields that exist in YAML but have no `apimBinding.data` are treated as UI-only. They are omitted from `update-fields` and logged as warnings.
- Unknown field ids are rejected with a validation error and logged as errors.
- If, after omitting UI-only fields, no APIM-updatable fields remain, the request is rejected with a validation error.
- `formVersion` is currently accepted but not validated.
- The BFF does not perform APIM editability checks for this phase; the service behind APIM is responsible for editability enforcement.
- The BFF does not process confirmation password fields and does not implement mailing-address copy behaviour; both are frontend concerns for this phase.

Backend validation scope:

- request body and `fields` shape;
- known field id;
- UI-only omission;
- required submitted fields cannot be null or blank;
- simple `maxLength`, `pattern`, `dataType`, and email control validation where declared in YAML.

The BFF does not implement the full page-level/cross-field validation DSL for PUT in this phase.

APIM dependency:

```text
POST /ws/NGTPA/v1/TRPUpdMemberInfo
```

APIM request body is built from `PortalAccessContext` and mapped update fields:

```json
{
  "policy-no": "policy-NO",
  "cert-no": "CERT-NO",
  "env": "JP",
  "user-id": "user-id",
  "apply-all": false,
  "update-fields": {
    "mobile-number": "91234567",
    "email": "user@example.com"
  }
}
```

Context mapping:

- `policy-no` = `PortalAccessContext.account().policyNo()`
- `cert-no` = `PortalAccessContext.account().certNo()`
- `env` = `PortalAccessContext.account().accountEnv()`
- `user-id` = `PortalAccessContext.actor().actorUserId()`

APIM response mapping:

For `applyToAllAccounts=false`, APIM returns one `response.data[]` item. The BFF maps that item to the standard mutation envelope.

For `applyToAllAccounts=true`, APIM may return multiple `response.data[]` items. The BFF identifies the selected account by comparing each APIM item:

- `policy-no` with `PortalAccessContext.account().policyNo()`
- `cert-no` with `PortalAccessContext.account().certNo()`
- `env` with `PortalAccessContext.account().accountEnv()`

Non-selected accounts are not mapped back to `Account-Ref`; user-facing status for those accounts is displayed by policy/certificate identifiers only.

APIM multi-account response example:

```json
{
  "response": {
    "err-message": "",
    "data": [
      {
        "success": true,
        "policy-no": "00000000118",
        "cert-no": 2,
        "env": "DB",
        "ref-no": "260001373",
        "submit-date": "2025-12-31",
        "submit-time": "15:42:52",
        "errors": []
      },
      {
        "success": false,
        "policy-no": "00000000118",
        "cert-no": 3,
        "env": "DB",
        "ref-no": "260001374",
        "submit-date": "2025-12-31",
        "submit-time": "15:42:52",
        "errors": [
          { "type": "FIELD", "fields": "email", "code": "REQUIRED" }
        ]
      }
    ]
  }
}
```

BFF mutation response rules:

- Selected account succeeded and all other accounts succeeded: `success=true`, `status=UPDATED`, `errors=[]`.
- Selected account succeeded but one or more other accounts failed: `success=true`, `status=PARTIAL_SUCCESS`, `errors=[]`, and `messages[]` contains a page-level warning listing other successful accounts and failed accounts with violated rules.
- Selected account failed: `success=false`, `status=VALIDATION_FAILED`, `result=null`, `messages=[]`, and `errors[]` contains only selected-account rule violations so FE can refocus a target field.

Partial-success response example:

```json
{
  "success": true,
  "status": "PARTIAL_SUCCESS",
  "result": {
    "refNo": "260001373",
    "submitDate": "2025-12-31",
    "submitTime": "15:42:52"
  },
  "messages": [
    {
      "type": "WARNING",
      "code": "personalInformation.update.partialSuccess",
      "message": "The update was successful for the selected account and policy 00000000118 certificate 2, but failed for policy 00000000118 certificate 3: email is required.",
      "target": "PAGE"
    }
  ],
  "errors": []
}
```

Selected-account validation failure example:

```json
{
  "success": false,
  "status": "VALIDATION_FAILED",
  "result": null,
  "messages": [],
  "errors": [
    {
      "type": "FIELD",
      "code": "personalInformation.email.required",
      "message": "This field is required.",
      "targets": ["emailAddress"],
      "severity": "ERROR",
      "source": "SERVER"
    }
  ]
}
```

Malformed request, missing `Account-Ref`, APIM transport, crypto, and unexpected system failures continue to use the standard BFF error envelope:

```json
{
  "errorCode": "err.personal-information.update.invalid",
  "message": "Invalid personal information update request."
}
```

Architecture notes:

- YAML reverse mapping lives in the inbound web adapter (`adapter/in/web/mapper`) because page/form/field binding is a presentation concern.
- The application use case receives already mapped APIM update field keys and remains framework-free.
- The update use case depends only on `PortalAccessContextPort` and `ApimUpdatePersonalInformationPort`.
- APIM request/response DTOs stay inside `adapter/out/apim/dto`.
- Multi-item APIM update responses are mapped to application-level per-account outcomes before the web adapter decides the public mutation status.
- `PARTIAL_SUCCESS` is a BFF mutation status used only when the selected account update succeeded but at least one non-selected account failed.
- Only `X-Request-Id` is propagated to APIM.
- Body logging remains disabled by default and should stay disabled for this endpoint because the payload contains personal data.

<!-- CONFIG_LOOKUP_STANDARDIZATION_START -->

## Config Lookup Standardization

The config lookup stack is standardized around `LocalizedConfigProperties`, `LocalizedConfigSource`, `DefaultConfigKeyCandidateStrategy`, and `DefaultConfigVariantResolver`.

Active config categories are:

- `DISPLAY_DATE_FORMAT`
- `DISPLAY_AMOUNT_FORMAT`
- `CURRENCY_MAPPING`
- `ERROR_MESSAGE`

Display date/amount config uses canonical keys such as `date.PROD.RM` and `amount.PROD.RM`; wildcard key `*` and legacy variant-only keys are not supported.

See [`docs/config-lookup-standardization.md`](docs/config-lookup-standardization.md) for details.

Run the regression guard with:

```bash
bash scripts/verify-config-lookup-standardization.sh
```

<!-- CONFIG_LOOKUP_STANDARDIZATION_END -->

### Message Delivery Foundation

The service includes an internal generic message delivery foundation behind `MessageDeliveryPort`.

Implemented in P2:

- generic `MessageDeliveryCommand` / `MessageDeliveryResult`
- framework-free `MessageDeliveryAdapter` dispatching for SMS and email
- SMS sender port and `/sms/ext/sendSingleSMS.do` adapter
- email sender port and SMTP adapter boundary using an adapter-local SMTP abstraction
- generic placeholder rendering
- safe masking/logging guards for SMS/email delivery
- passive `MessageDeliveryTask` DTO for a future task handoff boundary; no queueing, persistence, retry, worker, or broker behavior is implemented

Template rendering:

- supports simple `{variable}` replacement only
- does not load YAML templates
- does not perform TTL conversion
- does not append, pluralize, or translate units
- callers/templates own unit text

Security:

- raw OTP values are never logged
- SMS content and email body are never logged
- full phone numbers and full email addresses are never logged
- SMS password and provider credentials are never logged
- SMTP/provider credentials are never logged

Configuration roots:

```yaml
sms:
  enabled: true
  base-url: ${SMS_BASE_URL:}
  send-single-path: ${SMS_SEND_SINGLE_PATH:/sms/ext/sendSingleSMS.do}
  username: ${SMS_USERNAME:}
  password: ${SMS_PASSWORD:}
  send-id: ${SMS_SEND_ID:}
  class-code: ${SMS_CLASS_CODE:}
  default-country: ${SMS_DEFAULT_COUNTRY:HK}
  timeout-millis: ${SMS_TIMEOUT_MILLIS:5000}

email:
  enabled: true
  smtp:
    host: ${SMTP_HOST:}
    port: ${SMTP_PORT:25}
    from: ${SMTP_FROM:}
    timeout-millis: ${SMTP_TIMEOUT_MILLIS:5000}
```

Not implemented in P2:

- registration endpoints
- OTP resend/verify endpoints
- registration session `activeOtpChallengeId`
- APIM registration adapters
- rate limiting
- captcha
- message queue persistence/worker
- Spring Mail or Jakarta Mail SMTP dependency

### Abuse Prevention and Security Hardening

The generic `RateLimitPort`, Redis rate-limit adapter, CSRF foundation, exact-origin checks, and security audit model remain available for capabilities that require them. Registration OTP resend does not use the former destination-hash limiter.

Current registration rules:

- no registration-start rate limit;
- initial OTP does not consume a resend;
- five resends are enforced by challenge lineage;
- five verification attempts are enforced per challenge;
- raw destinations, CSRF values, cookie values, OTPs, passwords, message bodies, and provider credentials are not logged;
- credentialed registration flows do not allow wildcard origins.

### Session Management Foundation

P4A adds reusable session-management foundation components for future AuthFlowSession and AuthenticatedSession support.

Implemented in P4A:

- `auth-flow-session` configuration binding
- configurable session id policy (`prefix`, `entropyBytes`)
- secure opaque session id generation
- session TTL policy and expiry calculation
- generic cookie policy model
- generic cookie set/clear instruction model
- WebFlux cookie adapter for applying cookie instructions
- XSRF token transport policy and cookie instruction support

Important cookie policies:

- `auth_flow_sid` is HttpOnly, Secure, SameSite=None, Path=/api/v1/auth
- `XSRF-TOKEN` is Secure, SameSite=None, Path=/api/v1/auth, and intentionally not HttpOnly so Angular can read it and send `X-XSRF-TOKEN`

CORS:

- existing `CorsProperties` remains the single source of truth for allowed origins and credentialed CORS behavior
- `auth-flow-session` does not duplicate CORS allowed-origin configuration

Not implemented in P4A:

- AuthFlowSession domain model
- AuthFlowSession Redis persistence
- AuthFlowSession lifecycle service
- CSRF token binding to AuthFlowSession
- registration endpoints
- OTP endpoints
- APIM registration adapters
- captcha

This section documents foundation components only. It does not document session management as active registration runtime behavior.

### AuthFlowSession Foundation

P4B adds the framework-free AuthFlowSession foundation for temporary pre-auth workflow session.

AuthFlowSession supports these scenarios:

- REGISTRATION
- LOGIN
- FORGOT_PASSWORD
- USERNAME_RECOVERY

AuthFlowSession statuses:

- STARTED
- OTP_SENT
- OTP_VERIFIED
- TRUST_SELECTION_READY
- USERNAME_VERIFIED
- COMPLETING
- COMPLETED
- CANCELLED
- EXPIRED

AuthFlowSession is not an authenticated portal session, is not proof of login, and must never grant portal access.

Implemented in P4B:

- AuthFlowSession domain model and port contract
- Redis persistence using `{prefix}:auth:flow-session:{authFlowSessionId}`
- CSRF binding that stores only `csrfTokenHash`
- lifecycle service for create/load/status/destination/OTP/usernameCheck/complete/cancel/delete
- first concurrency policy: SINGLE_ACTIVE_FLOW + REPLACE_EXISTING
- package-info placeholder boundary for future AuthenticatedSession only

Important boundaries:

- `AuthFlowSessionId` is the server-side opaque identifier
- `auth_flow_sid` is the cookie name that later transports `AuthFlowSessionId.value()`
- Redis adapter does not know or write cookies
- raw CSRF tokens, cookie values, OTP codes, password values, raw mobile/email, and session ids must not be logged

Not implemented in P4B:

- registration endpoints
- OTP resend/verify endpoints
- cookie reading or writing composition; P4B does not read or write cookies at runtime
- CSRF web filter/controller enforcement
- Origin/Referer endpoint enforcement
- AuthenticatedSession runtime implementation
- APIM registration adapters
- captcha

Do not claim registration/start or OTP endpoints are implemented.

### Lightweight Workflow Foundation

P5 adds lightweight workflow helper models for future multi-step flows.

Implemented in P5:

- framework-free workflow vocabulary (`WorkflowScenario`, `WorkflowStep`, `WorkflowNextStep`, `WorkflowPageRef`)
- workflow result helpers that map to the existing `MutationResponse` contract
- workflow message/error key conventions
- failure classification metadata for ERROR vs FATAL logging policy
- workflow page selection helpers for bff-pages integration
- stable transition/severity helpers

Design rules:

- workflow helpers are not a workflow engine
- workflow helpers do not implement a graph DSL
- workflow helpers do not store workflow runtime state in a database
- user-facing text must come from YAML/config or bff-pages, not hardcoded Java
- runtime page responses must resolve to one language only
- failures are represented by response errors, idempotency state, and audit/security events, not by generic FAILED / UNKNOWN / ERROR workflow statuses

Not implemented in P5:

- registration/start endpoint
- OTP resend/verify endpoints
- username/check or complete endpoints
- APIM registration adapters
- auth-flow resume
- captcha
- fund-switching business logic

Do not claim any registration endpoint is implemented in P5.

### Registration Runtime Endpoints

The active runtime includes captcha challenge/verify/refresh, registration start, OTP resend/verify, username check, completion, and cancellation. Controllers remain thin and delegate to framework-free application use cases.

Registration start applies the configured captcha gate, calls `TRPSearchUnregMember`, creates the auth-flow session only after eligibility succeeds, binds CSRF state, creates the initial challenge, and sends the OTP. Resend replaces the active challenge; the initial send is not counted and five replacements are allowed. Verification uses the active challenge and refreshes the unregistered trust list.

Captcha endpoints do not require `auth_flow_sid`, `X-XSRF-TOKEN`, `Idempotency-Key`, or `Account-Ref`. Protected registration endpoints enforce the applicable session, CSRF, origin/referer, and idempotency rules.

#### Registration Username Check Foundation
  
P9.0-P9.3 add the registration username-check foundation used by the future endpoint:
POST /api/v1/auth/registration/username/check  

Implemented in P9.0-P9.3:

- page-specific downstream business rejection classification under the existing `bff-pages` DSL;
- framework-free `DownstreamErrorClassificationPort` and config-backed resolver;
- username/check web boundary DTOs for selectedTrust + username;
- selectedTrust shape is limited to `trustCode` and `accountEnv` only;
- framework-free username-check command/result/status models;
- `RegistrationUsernameAvailabilityPort` and TRPChkCustId APIM boundary;
- adapter-only TRPChkCustId request/response DTOs under `adapter/out/apim/registration`;
- APIM/TAS username availability success maps to `AVAILABLE`;
- APIM/TAS business `err-message` maps to `REJECTED` with the raw downstream code preserved for the page-specific classifier;
- technical APIM/TAS failures bypass business fallback classification and remain downstream/system failures;
- `CheckRegistrationUsernameService` application use case;
- username policy validation from `auth-workflows.registration.username`;
- AuthFlowSession usernameCheck storage only after username availability succeeds;
- AuthFlowSession transition to `USERNAME_VERIFIED` only after successful availability check;
- same selectedTrust + same username repeat while `USERNAME_VERIFIED` returns success without another APIM/TAS call;
- failed username replacement does not clear the previous stable usernameCheck state.
  
Username/check request contract prepared by P9.1-P9.3:

```json
{
  "selectedTrust": {
    "trustCode": "TL",
    "accountEnv": "ENV1"
  },
  "username": "abc1@"
}
```
  
Session usernameCheck state shape:

```json
{
  "selectedTrust": {
    "trustCode": "TL",
    "accountEnv": "ENV1"
  },
  "username": "abc1@",
  "checkedAt": "2026-07-10T00:00:00Z",
  "expiresAt": null
}
```
  
Page-specific downstream classification DSL entries are configured under `bff-pages.pages.<pageId>.downstreamErrorClassification`. For username/check, the page id is `registration-username-check`, source is `APIM_TAS`, endpoint is `TRPChkCustId`, and `err.tpa.username.taken` resolves through config to the BFF-facing response code `registration.username.isregistered`. Unknown APIM/TAS business rejection codes use the page fallback or the safe built-in generic downstream business rejection fallback.
  
Important boundaries:

- username/check is not action-idempotent and does not require or process `Idempotency-Key`;
- no `policyNo` or `certNo` appears in username/check selectedTrust DTOs, application models, session usernameCheck state, or TRPChkCustId application port boundary;
- APIM DTOs and raw APIM/TAS `err-message` values stay inside outbound adapter/application boundary handling and are not exposed as FE response codes;
- user-facing messages are not hardcoded in Java and must be resolved later through the existing message resolver using `Accept-Language`;
- controllers must remain thin when the endpoint is exposed in P9.4.
  
Not implemented yet in P9.0-P9.3:

- the public username/check controller route;
- username/check web response mapper;
- registration password setup;
- registration complete / TRPRegMember;
- username/check idempotency;
- captcha or OTP behavior changes.

### Local Registration Context Protection Secret

When running the application with the `local` profile, the registration auth-flow encrypted context protector requires a local development secret. This repository provides a local-only value in:

```text
src/main/resources/application-local.properties
```

For production and shared environments, do not use the local value. Provide a secure 32-byte-minimum secret through environment-specific configuration or a secret manager. The current detected prefix is:

```properties
auth-workflows.registration.context-protection.secret=<secure-32-byte-minimum-secret>
```

<!-- task-0038-registration-complete -->
## Registration completion

The registration workflow completes through:

```http
POST /api/v1/auth/registration/complete
Cookie: auth_flow_sid=<opaque-session-id>
X-XSRF-TOKEN: <csrf-token>
Idempotency-Key: <opaque-idempotency-key>
Accept-Language: en
Content-Type: application/json
```

```json
{
  "password": "<password>",
  "confirmPassword": "<password>"
}
```

The endpoint is owned by `AuthRegistrationController` and returns the generic
`MutationResponse` contract. Password values are transient. The workflow loads
identity/contact data and username/selected-trust data from separately protected
AuthFlowSession attributes, acquires a session-scoped completion lock, invokes
`TRPRegMember`, and then checks whether other eligible trusts remain.

Success-page content and actions come from the existing `registration-success`
page configuration. The `hasRemainingTrusts` variant lets the user continue with
another trust; `noRemainingTrusts` returns the user to login and expires the
auth-flow cookie.

Technical outcomes that may have committed downstream are stored as idempotency
`UNKNOWN_OUTCOME` and are not automatically replayed. See
[`docs/evidence/0038-registration-complete.md`](docs/evidence/0038-registration-complete.md)
for security invariants, test commands, and the merge-request evidence checklist.

### Registration Start

`POST /api/v1/auth/registration/start` begins the protected registration workflow after captcha verification. The request requires `Idempotency-Key`; eligible-member discovery is performed through `TRPSearchUnregMember`. A successful start creates the opaque `auth_flow_sid` pre-authentication session and returns the `csrfToken` required by later protected registration operations.

### Registration OTP Resend and Verify

`POST /api/v1/auth/registration/otp/resend` keeps the workflow at `VERIFY_OTP` while replacing the active OTP challenge under the configured cooldown and resend limits. `POST /api/v1/auth/registration/otp/verify` validates the active challenge and refreshes eligible trusts through `RegistrationLookupPort`. Successful verification advances to username checking for one trust or `SELECT_TRUST` when multiple eligible trusts remain.

The original verified destination remains authoritative for masking, session state, challenge subject protection, and eligibility checks. Physical destination substitution is confined to `RegistrationMessagePort`. Legacy destination hashing and the removed destination-hash OTP-send limiter are not part of the active resend policy and must not be reintroduced.


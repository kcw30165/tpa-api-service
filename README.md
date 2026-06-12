# NGTPA API Service


> **Document ownership:** This README is the source of truth for the current implemented service behaviour, local setup, configuration, API contracts, logging behaviour, and developer/operator guidance.  
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
| Build | Maven 3.9.x (/d/Tools/apache-maven-3.9.15) |
| JDK | OpenJDK 21 (`C:\Java\OpenJDK\jdk-21`) |

---

## Architecture

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
│   │   ├── in/          # GetNotificationsUseCase, UpdateNotificationsReadStatusUseCase, GetContributionSummaryUseCase, ExportContributionSummaryUseCase, GetReferenceDataCountriesUseCase, GetPersonalInformationUseCase, UpdatePersonalInformationUseCase
│   │   └── out/         # ApimNoticeMessagePort, ApimNotificationReadStatusPort, ApimContributionSummaryPort, ApimReferenceDataCountriesPort, ApimMemberInfoPort, ApimUpdatePersonalInformationPort, ReferenceDatePort, PortalAccessContextPort, CurrencyDisplayPort
│   ├── usecase/         # GetNotificationsService, UpdateNotificationsReadStatusService, GetContributionSummaryService, ExportContributionSummaryService, GetReferenceDataCountriesService, GetPersonalInformationService, UpdatePersonalInformationService
│   ├── dto/             # Notification, contribution summary, reference data, and personal-information commands/results; CurrencyDisplay; PortalAccessContext (ActorContext, MemberOwnerContext, AccountContext)
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
│   │   ├── request/     # UpdateNotificationsReadStatusRequest
│   │   └── response/    # Notification and contribution summary response records
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
│       ├── configserver/    # ConfigMap/Config-Server-backed adapters
│       │   ├── ReferenceDateProperties                # Binds reference-date.* YAML
│       │   ├── ConfigBackedReferenceDateAdapter       # Current ConfigMap-backed ReferenceDatePort implementation
│       │   ├── ConfigServiceReferenceDateAdapter      # Planned future API-backed ReferenceDatePort implementation
│       │   └── ReferenceDateResolver                  # Shared production-like / override resolution policy
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

## Inbound Request Header Context

Phase 1 now provides a single global inbound request header context at the WebFlux boundary.

The live owner is `RequestLoggingWebFilter`, which resolves a typed `RequestHeaderContext` once per inbound request and stores it in both Reactor Context and `ServerWebExchange` attributes for web and infrastructure code.

Extracted headers:

- `Account-Ref`
- `X-Request-Id`
- `Accept-Language`

Behavior:

- `Account-Ref` is optional globally and must not be required for login or account-list style flows.
- Selected-account APIs now require `Account-Ref` for:
  - `GET /api/v1/contributions`
  - `GET /api/v1/contributions/export`
  - `GET /api/v1/notifications`
  - `PATCH /api/v1/notifications`
  - `GET /api/v1/reference-data/countries`
  - `GET /api/v1/personal-information`
  - `PUT /api/v1/personal-information`
- Missing or invalid `Account-Ref` on those selected-account APIs returns HTTP `400` with error code `err.member.context.invalid`.
- `X-Request-Id` behavior is unchanged: the filter reuses a non-blank inbound value, generates a UUID when missing, returns the value in the response header, and keeps propagating it through Reactor Context.
- Only `X-Request-Id` is propagated to APIM by `ApimRequestIdExchangeFilter`.
- `Account-Ref` is not propagated to APIM.
- `Accept-Language` is not propagated to APIM.
- `Accept-Language` is extracted from the inbound request and defaults to `en` when missing or blank.
- Contribution success responses now prefer inbound `Accept-Language` for `GET /api/v1/contributions` and `GET /api/v1/contributions/export`, with `lang` kept as a temporary fallback and `en` as the default.
- Reference-data countries response text uses inbound `Accept-Language`: English (`en`) uses `country-name-eng`, and `zh-HK` uses `country-name-chi` with fallback to English when Chinese text is blank.
- Contribution endpoint normalization is `en`, `en-US`, `en_HK` -> `en`; `zh-HK`, `zh_HK`, `zh` -> `zh_HK`; blank, missing, and unknown values -> `en`.
- Error-message localization by `Accept-Language` is still deferred, and notification response behavior is unchanged.
- Login, account-list, OAuth, and Account-Ref generation remain future work outside this phase.

### APIM Response Envelope

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

```
adapter/in/web          →  application  →  domain
adapter/out/apim        →  application  →  domain
adapter/out/config      →  application  →  domain
adapter/out/configserver →  application  →  domain
adapter/out/security    →  application  →  domain
config                  →  application use case @Bean wiring only (UseCaseConfig)
infrastructure          →  Spring/framework infrastructure only (no application/domain imports)
```

---

## Prerequisites

- **JDK 21** at `C:\Java\OpenJDK\jdk-21`
- **Maven 3.9.x** at `D:\Tools\apache-maven-3.9.15` (used by the wrapper via `maven-wrapper.properties`)
- Network access to APIM (`trafficorso-sitk8apim.bcthk.info`) and OAuth token endpoint for live calls

---

## Building

```bash
# Compile
export JAVA_HOME="C:/Java/OpenJDK/jdk-21" && export M2_HOME="/d/Tools/apache-maven-3.9.15" && export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH" && mvn compile -DskipTests

# Run tests
export JAVA_HOME="C:/Java/OpenJDK/jdk-21" && export M2_HOME="/d/Tools/apache-maven-3.9.15" && export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH" && mvn test
```

> On Windows without `JAVA_HOME` in PATH, prefix each command with `JAVA_HOME="C:/Java/OpenJDK/jdk-21"` or use the VS Code tasks defined in `.vscode/tasks.json`.

---

## Running Locally

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

For the dev cluster, the Kubernetes deployment or external config repository must set `CORS_ALLOWED_ORIGINS=http://localhost:4200` before local frontend calls from that origin will succeed. Those deployment manifests are outside this repository.

---

## Authentication

### Local Development

HTTP Basic Auth is auto-configured by Spring Boot when `api.security.require-authentication=true` (the default). Local credentials are defined in `src/main/resources/application-local.yml`:

```yaml
spring:
  security:
    user:
      name: dev
      password: dev-local
```

In Postman, set **Authorization → Basic Auth** with username `dev` and password `dev-local`.

### Kubernetes / External Auth Layer

When authentication is enforced externally (K8s ingress controller, API gateway, or service mesh mTLS), disable in-process auth in the Deployment manifest or ConfigMap:

```yaml
env:
  - name: API_SECURITY_REQUIRE_AUTHENTICATION
    value: "false"
```

When `false`, the application logs a startup `WARN` confirming that the external auth layer is trusted. All traffic reaching the pod is permitted without in-process credential checks.

### Future: OAuth2 / OIDC Resource Server

When an Auth Server is available, set `api.security.require-authentication=true` and configure `SecurityConfig` as a WebFlux OAuth2 resource server (`http.oauth2ResourceServer(...)`). Portal access context will then be extracted from JWT claims instead of the temporary profile configuration.

---

## Temporary Portal Access Context Configuration

Until Auth Server integration is implemented, the actor identity, member ownership, and account routing fields used in APIM calls are sourced from a temporary profile configuration rather than derived from a JWT token.

For the selected-account APIs, `TemporaryPortalAccessContextAdapter` now resolves the context from an account-ref keyed profile entry:

```text
temporary-portal-access-context.profiles[{accountRef}]
```

The older feature-key entries such as `notifications` and `contributions` remain only as a transitional fallback for non-migrated callers and should be removed once all selected-account flows supply real `Account-Ref` values end to end.

The context model is structured into three separate dimensions:

- **Actor** (`actorUserId`, `actorUserType`, `actorUserRole`) — identifies who is acting (the logged-in user)
- **Member owner** (`memberUserId`, `memberType`) — identifies the member whose data is being accessed
- **Account** (`accountEnv`, `policyNo`, `certNo`, `trustCode`, `schemeType`, `termStatus`, `termCompletionDate`) — routing fields and term metadata for the target APIM account


The property class `TemporaryPortalAccessContextProperties` binds `temporary-portal-access-context.profiles.*`. The outbound adapter `TemporaryPortalAccessContextAdapter` (under `adapter/out/security`) implements `PortalAccessContextPort`, resolves the correct profile by account reference key (for example `ACC-123`), retains transitional support for legacy feature-key entries when callers still pass `notifications` or `contributions`, converts raw `term-status` into the framework-free `TermStatus` enum, and parses `term-completion-date` as `dd/MM/yyyy` when present.

For the selected-account APIs, missing or invalid `Account-Ref` values fail fast with `PortalAccessContextResolutionException` mapped to HTTP **400** with error code `err.member.context.invalid`. Genuine context unavailability still maps to HTTP **500** with `err.member.context.unavailable`.

Example YAML (present in `application-local.yml` with blank-safe defaults for the term fields):

```yaml
temporary-portal-access-context:
  profiles:
    ACC-123:
      actor-user-id: ${TEMP_ACC_123_ACTOR_USER_ID:C402400A}
      actor-user-type: ${TEMP_ACC_123_ACTOR_USER_TYPE:MEMBER}
      actor-user-role: ${TEMP_ACC_123_ACTOR_USER_ROLE:SELF}
      member-user-id: ${TEMP_ACC_123_MEMBER_USER_ID:C402400A}
      member-type: ${TEMP_ACC_123_MEMBER_TYPE:MBR}
      account-env: ${TEMP_ACC_123_ACCOUNT_ENV:JP}
      policy-no: ${TEMP_ACC_123_POLICY_NO:00000000217}
      cert-no: ${TEMP_ACC_123_CERT_NO:95}
      trust-code: ${TEMP_ACC_123_TRUST_CODE:JPM}
      scheme-type: ${TEMP_ACC_123_SCHEME_TYPE:OE}
      term-status: ${TEMP_ACC_123_TERM_STATUS:}
      term-completion-date: ${TEMP_ACC_123_TERM_COMPLETION_DATE:}
    notifications:
      actor-user-id: ${TEMP_NOTIF_ACTOR_USER_ID:C402400A}
      actor-user-type: ${TEMP_NOTIF_ACTOR_USER_TYPE:MEMBER}
      actor-user-role: ${TEMP_NOTIF_ACTOR_USER_ROLE:SELF}
      member-user-id: ${TEMP_NOTIF_MEMBER_USER_ID:C402400A}
      member-type: ${TEMP_NOTIF_MEMBER_TYPE:MBR}
      account-env: ${TEMP_NOTIF_ACCOUNT_ENV:JP}
      policy-no: ${TEMP_NOTIF_POLICY_NO:00000000118}
      cert-no: ${TEMP_NOTIF_CERT_NO:2}
      trust-code: ${TEMP_NOTIF_TRUST_CODE:}
      scheme-type: ${TEMP_NOTIF_SCHEME_TYPE:}
      term-status: ${TEMP_NOTIF_TERM_STATUS:}
      term-completion-date: ${TEMP_NOTIF_TERM_COMPLETION_DATE:}
    contributions:
      actor-user-id: ${TEMP_CONT_ACTOR_USER_ID:C402400A}
      actor-user-type: ${TEMP_CONT_ACTOR_USER_TYPE:MEMBER}
      actor-user-role: ${TEMP_CONT_ACTOR_USER_ROLE:SELF}
      member-user-id: ${TEMP_CONT_MEMBER_USER_ID:C402400A}
      member-type: ${TEMP_CONT_MEMBER_TYPE:MBR}
      account-env: ${TEMP_CONT_ACCOUNT_ENV:JP}
      policy-no: ${TEMP_CONT_POLICY_NO:00000000217}
      cert-no: ${TEMP_CONT_CERT_NO:95}
      trust-code: ${TEMP_CONT_TRUST_CODE:JPM}
      scheme-type: ${TEMP_CONT_SCHEME_TYPE:OE}
      term-status: ${TEMP_CONT_TERM_STATUS:}
      term-completion-date: ${TEMP_CONT_TERM_COMPLETION_DATE:}
```

  `ACC-123` demonstrates the preferred selected-account shape. The `notifications` and `contributions` entries are transitional examples kept only to support the legacy fallback during migration.

Blank or missing `term-status` maps to `TermStatus.BLANK` without warning. Unsupported non-blank raw values map to `TermStatus.UNKNOWN` and emit a sanitized warning from the temporary provider boundary only.

  This configuration is **temporary**. It will be replaced once the Auth Server is integrated and portal access context is extracted from JWT access token claims.

---

## Reference Date Configuration (Stage 1.2)

`ref-date` is resolved through the `ReferenceDatePort` outbound port for **all** flows that require it: contribution summary validation, contribution export, and notification flows (`GetNotificationsService`, `UpdateNotificationsReadStatusService`). Neither notification service contains a hardcoded date constant.

The single shared implementation is `ConfigBackedReferenceDateAdapter` (under `adapter/out/configserver`), backed by `ReferenceDateProperties`. Application services depend only on `ReferenceDatePort`; they do not import `ReferenceDateProperties`.

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
  account-env: ${REFERENCE_DATE_ACCOUNT_ENV:}
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

`reference-date.account-env` is the current configured business/account environment used for reference-date lookup. Production-like safety is based on the runtime deployment environment, not on `accountEnv`; the current `ConfigBackedReferenceDateAdapter` derives that deployment signal from the active Spring profile. For production-like deployment values (`PROD`, `PRD`, `PRODUCTION`, `DR`, blank, and null), all use cases that require `ref-date` — contribution summary validation, contribution export, and notification flows — always use the app server timezone and current date. For non-production-like deployment values, `reference-date.override-date` and `reference-date.override-zone-id` may be provided as a pair; when both are absent the app falls back to the server clock, and when only one is present resolver-time validation fails. Today `accountEnv` comes from Spring externalized configuration / ConfigMap through `ConfigBackedReferenceDateAdapter`; in the future, after `Account-Ref` resolution, the request-specific source can become `PortalAccessContext.account().accountEnv()`. `deploymentEnv` is not part of `PortalAccessContext`.

These values drive the synthetic total detail row in the JSON response, the first three column headers in the XLSX export, the locale-specific currency display returned in contribution summary JSON, and the effective contribution reference date. Currency, date, and amount lookups now run through the global config variant resolver, which evaluates `env`, `trustCode`, and `schemeType` suffix combinations in a fixed order and then falls back to English when the requested language has no match.


---

## API Endpoints

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
- Actor identity, member ownership, and account routing fields (`actor-user-id`, `policy-no`, `cert-no`, etc.) are resolved from externalized `temporary-portal-access-context.profiles.notifications.*` configuration (see **Temporary Portal Access Context Configuration** below) until Auth Server integration is implemented. `ref-date` remains temporarily hardcoded.

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
- `ref-date` is resolved through `ReferenceDatePort`.
- The current configured lookup source is `reference-date.account-env`; a future request-specific source can be `PortalAccessContext.account().accountEnv()` after `Account-Ref` resolution.
- Production-like safety is based on runtime deployment environment, not on request query `env` and not on `reference-date.account-env`.
- `page` and `pageSize` must both be greater than 0; HTTP 400 is returned otherwise. No real backend pagination is performed yet — all data is returned from APIM and the pagination fields reflect the full dataset.
- Effective contribution language is resolved in the web adapter with this priority: `Accept-Language` header, then `lang` query parameter, then `en`.
- Contribution language normalization is `en`, `en-US`, `en_HK` -> `en`; `zh-HK`, `zh_HK`, `zh` -> `zh_HK`; blank, missing, and unknown values -> `en`.
- The resolved contribution language drives success-path JSON labels, source labels, currency text, and display formatting.
- Contribution rows are grouped by `deal-date + cover-from + cover-to`.
- Dynamic detail items are joined from `contDtl[*].disp-src` to `dispSrc[*].disp-src` and sorted by `dispSrc.seq` ascending.
- Amount `text` values are formatted using `display-format.amount.*` configuration (language and env-specific). Value `value` is the raw `BigDecimal`.
- Date values carry the query-string text (`fromDate`/`toDate`) and also the ISO date string derived from APIM `cover-from`/`cover-to` date parsing.
- Actor identity, member ownership, and account routing fields (`actor-user-id`, `policy-no`, `cert-no`, `trustCode`, `schemeType`, etc.) are resolved from externalized `temporary-portal-access-context.profiles.contributions.*` configuration (see **Temporary Portal Access Context Configuration** below) until Auth Server integration is implemented.
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
4. `code.trustCode.schemeType`
5. `code.accountEnv`
6. `code.trustCode`
7. `code.schemeType`
8. `code`

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
- The current configured lookup source is `reference-date.account-env`; a future request-specific source can be `PortalAccessContext.account().accountEnv()` after `Account-Ref` resolution.
- Production-like safety is based on runtime deployment environment, not on request query `env` and not on `reference-date.account-env`.
- `deploymentEnv` is not part of `PortalAccessContext`.
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

# NGTPA API Service

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
| Cross-cutting logging | Spring AOP + custom `@LogExecution` |
| Auth (outbound) | Spring Security OAuth2 Client Credentials |
| Encryption | BouncyCastle 1.82 (RSA + AES/CBC) |
| Spreadsheet export | Apache POI OOXML |
| Build | Maven 3.9.x (/d/Tools/apache-maven-3.9.15) |
| JDK | OpenJDK 21 (`C:\Java\OpenJDK\jdk-21`) |

---

## Architecture

Clean Architecture / Hexagonal (Ports & Adapters):

```
com.bct.ngtpa.apiservice
├── domain/              # Pure Java — no Spring, no I/O
│   ├── model/           # NoticeMessage, MessageType, MessageStatus, AudienceType, Hyperlink
│   └── exception/       # DomainException
├── application/         # Orchestration — @Service only
│   ├── port/
│   │   ├── in/          # GetNotificationsUseCase, UpdateNotificationsReadStatusUseCase, GetContributionSummaryUseCase, ExportContributionSummaryUseCase
│   │   └── out/         # ApimNoticeMessagePort, ApimNotificationReadStatusPort, ApimContributionSummaryPort, ReferenceDatePort, MemberContextPort, CurrencyDisplayPort
│   ├── usecase/         # GetNotificationsService, UpdateNotificationsReadStatusService, GetContributionSummaryService, ExportContributionSummaryService
│   ├── dto/             # Notification and contribution summary commands/results; CurrencyDisplay; MemberContext, MemberContextPurpose
│   └── exception/       # InvalidContributionRequestException, InvalidNotificationRequestException, MemberContextResolutionException
├── adapter/
│   ├── in/web/          # Reactive controllers, request/response records
│   │   ├── NotificationController
│   │   ├── ContributionController
│   │   ├── ContributionSummaryWorkbookExporter
│   │   ├── ApiExceptionHandler (@RestControllerAdvice)
│   │   ├── config/      # Web presentation config (Stage 2.2)
│   │   │   ├── ContributionWebDisplayConfig     # Neutral record: total labels + XLSX headers
│   │   │   └── ContributionWebDisplayConfigProvider # Adapts ContributionSummaryProperties → ContributionWebDisplayConfig
│   │   ├── request/     # UpdateNotificationsReadStatusRequest
│   │   └── response/    # Notification and contribution summary response records
│   └── out/
│       ├── apim/            # APIM integration
│       │   ├── ApimWebClientFacade        # Pure HTTP transport (OAuth2 token attach)
│       │   ├── ApimNoticeMessageAdapter   # Implements ApimNoticeMessagePort
│       ├── ApimNotificationReadStatusAdapter # Implements ApimNotificationReadStatusPort
│       ├── ApimContributionSummaryAdapter # Implements ApimContributionSummaryPort
│       │   ├── configserver/ConfigBackedReferenceDateAdapter # Current ConfigMap-backed ReferenceDatePort implementation
│       │   ├── configserver/ConfigServiceReferenceDateAdapter # Planned future API-backed ReferenceDatePort implementation
│       │   ├── configserver/ReferenceDateResolver # Shared production-like / override resolution policy
│       │   ├── ApimCertificateService     # Fetches BCT public key from APIM
│       │   ├── ApimAppCertificateService  # Loads app RSA keys + X509 cert
│       │   ├── ApimPayloadCryptoService   # AES/CBC + RSA field encryption/decryption
│       │   ├── crypto/                    # APIM-specific crypto helpers and exceptions
│       │   └── dto/                       # APIM request/response POJOs
│       ├── config/          # Config-property-backed adapters
│       │   └── ConfigBackedCurrencyDisplayAdapter  # Implements CurrencyDisplayPort; reads CurrencyMappingProperties
│       └── security/        # Non-APIM security concerns
│           └── TemporaryMemberContextAdapter  # Implements MemberContextPort; reads temporary-member-context profiles
├── config/              # Spring configuration beans (unchanged across layers)
│   ├── ApimProperties
│   ├── ContributionSummaryProperties
│   ├── TemporaryMemberContextProperties  # Binds temporary-member-context.profiles.*
│   ├── WebClientConfig
│   ├── SecurityConfig
│   ├── JacksonConfig
│   ├── ApimCryptoConfig
│   └── logging/        # `@LogExecution`, aspect, and log sanitization
└── exception/           # ApimException (shared)
```

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

### Dependency Rule

```
adapter/in/web          →  application  →  domain
adapter/out/apim        →  application  →  domain
adapter/out/config      →  application  →  domain
adapter/out/configserver →  application  →  domain
adapter/out/security    →  application  →  domain
config                  →  framework composition only (must not be imported by application or domain)
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
./mvnw compile -DskipTests

# Run tests
./mvnw test
```

> On Windows without `JAVA_HOME` in PATH, prefix each command with `JAVA_HOME="C:/Java/OpenJDK/jdk-21"` or use the VS Code tasks defined in `.vscode/tasks.json`.

---

## Running Locally

### Via VS Code Debugger (recommended)

Open **Run & Debug** → select **"Debug NgtpaApiServerApplication"** → press F5.

All required environment variables are pre-configured in `.vscode/launch.json`.

### Via Maven task or shell

```bash
export JAVA_HOME="C:/Java/OpenJDK/jdk-21"
./mvnw spring-boot:run
```

Spring Boot imports the repository root `.env` file automatically via `spring.config.import`, so local `APIM_*` variables do not need to be exported one by one.

Browser access from a local frontend to a deployed API stays closed by default. To allow a local Angular app running at `http://localhost:4200` to call the dev deployment, set `CORS_ALLOWED_ORIGINS=http://localhost:4200` in the dev deployment configuration. Do not set this variable in environments that should remain closed to browser cross-origin calls.

If you hit a stale class problem after refactors, run a clean rebuild first:

```bash
export JAVA_HOME="C:/Java/OpenJDK/jdk-21"
./mvnw clean compile -DskipTests
```

---

## Environment Variables

| Variable | Description | Default |
|---|---|---|
| `SERVER_PORT` | HTTP port | `8888` |
| `SPRING_CLOUD_CONFIG_ENABLED` | Enable Spring Cloud Config | `false` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated browser origins allowed for `/api/**` CORS responses | _(empty / closed)_ |
| `DEPLOY_ENV` | Runtime deployment environment used for production-safe reference-date resolution | _(empty / production-safe)_ |
| `REFERENCE_DATE_OVERRIDE_DATE` | Optional non-production override date in `dd/MM/yyyy` | _(empty)_ |
| `REFERENCE_DATE_OVERRIDE_ZONE_ID` | Optional non-production override zone ID paired with `REFERENCE_DATE_OVERRIDE_DATE` | _(empty)_ |
| `APIM_BASEURL` | APIM base URL | _(required)_ |
| `APIM_TIMEOUTMILLISECONDS` | WebClient timeout | `10000` |
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
| `TEMP_CONT_POLICY_NO` | Temporary contribution policy number | `policyNo_for_contributions` |
| `TEMP_CONT_CERT_NO` | Temporary contribution certificate number | `certNo_for_contributions` |
| `TEMP_CONT_USER_ID` | Temporary contribution user ID | `userId_for_contributions` |
| `TEMP_CONT_TRUST_CODE` | Temporary contribution trust code | `trustCode_for_contributions` |
| `TEMP_CONT_SCHEME_TYPE` | Temporary contribution scheme type | `schemeType_for_contributions` |

For the dev cluster, the Kubernetes deployment or external config repository must set `CORS_ALLOWED_ORIGINS=http://localhost:4200` before local frontend calls from that origin will succeed. Those deployment manifests are outside this repository.

---

## Temporary Member Context Configuration

Until Auth Server integration is implemented, the `policy-no`, `cert-no`, `user-id`, `trustCode`, and `schemeType` values used in APIM calls are sourced from a temporary feature-specific profile configuration rather than hardcoded constants.

The property class `TemporaryMemberContextProperties` binds `temporary-member-context.profiles.*`. The outbound adapter `TemporaryMemberContextAdapter` (under `adapter/out/security`) implements `MemberContextPort` and resolves the correct profile by feature purpose (`NOTIFICATIONS` or `CONTRIBUTIONS`).

If a required profile is missing from configuration, the service fails fast with `MemberContextResolutionException`, which maps to HTTP **500** with the standard error body.

Example YAML (already present in `application-local.yml`):

```yaml
temporary-member-context:
  profiles:
    notifications:
      policy-no: ${TEMP_NOTIF_POLICY_NO:policyNo_for_notifications}
      cert-no: ${TEMP_NOTIF_CERT_NO:certNo_for_notifications}
      user-id: ${TEMP_NOTIF_USER_ID:userId_for_notifications}
      trust-code: ${TEMP_NOTIF_TRUST_CODE:trustCode_for_notifications}
      scheme-type: ${TEMP_NOTIF_SCHEME_TYPE:schemeType_for_notifications}
    contributions:
      policy-no: ${TEMP_CONT_POLICY_NO:policyNo_for_contributions}
      cert-no: ${TEMP_CONT_CERT_NO:certNo_for_contributions}
      user-id: ${TEMP_CONT_USER_ID:userId_for_contributions}
      trust-code: ${TEMP_CONT_TRUST_CODE:trustCode_for_contributions}
      scheme-type: ${TEMP_CONT_SCHEME_TYPE:schemeType_for_contributions}
```

This configuration is **temporary**. It will be replaced once the Auth Server is integrated and member context is extracted from the JWT access token claims.

---

## Reference Date Configuration (Stage 1.2)

`ref-date` is resolved through the `ReferenceDatePort` outbound port for **all** flows that require it: contribution summary validation, contribution export, and notification flows (`GetNotificationsService`, `UpdateNotificationsReadStatusService`). Neither notification service contains a hardcoded date constant.

The single shared implementation is `ConfigBackedReferenceDateAdapter` (under `adapter/out/configserver`), backed by `ReferenceDateProperties`. Application services depend only on `ReferenceDatePort`; they do not import `ReferenceDateProperties`.

---

## APIM encryption configuration

APIM encryption is configured per APIM base URL (not per endpoint). The list `apim.encryption.requestFields` contains JSON field names that the APIM adapter will encrypt for every outbound request sent to the configured `apim.baseUrl`.

Example configuration:

```yaml
apim:
  baseUrl: ${APIM_BASEURL:}
  encryption:
    enabled: ${APIM_ENCRYPTION_ENABLED:true}
    certificatePath: ${APIM_CERTIFICATE_PATH:/api/wssupport/v1/encryption/certificate}
    apiKey: ${APIM_API_KEY:}
    privateKeyPem: ${APIM_PRIVATE_KEY_PEM:}
    publicKeyPem: ${APIM_PUBLIC_KEY_PEM:}
    requestFields:
      - policy-no
      - cert-no
      - user-id
```

Notes:

- `requestFields` is global for the `apim.baseUrl` and applies to every outbound request handled by the APIM client.
- It is not configured per APIM operation (for example `TRPGetMsgBoard`).
- To add encryption for a new field, add it once under `apim.encryption.requestFields`.

## Execution Logging

Global execution logging is implemented as a configuration-level cross-cutting concern under `config/logging`.

- Use `@LogExecution` on controller, use-case, and outbound adapter/facade methods that represent entry or orchestration points.
- Annotated synchronous methods log start, success, error, and elapsed time.
- Annotated `Mono` and `Flux` methods stay lazy; the aspect logs on subscription and completion/error without calling `block()` or subscribing internally.
- Logged arguments and results are opt-in through annotation attributes and are sanitized before serialization.
- Sensitive header and payload fields such as `Authorization`, `Certificate`, API keys, tokens, secrets, policy numbers, certificate numbers, user IDs, and key material are masked in logs.
- The sensitive key list is configured through `logging-sanitizer.sensitive-tokens`, using `src/main/resources/application-local.yml` for local development and the deployed `ngtpa-display-config` Spring YAML in Kubernetes.
- All log events are emitted as JSON-structured strings (using `ObjectMapper`) suitable for ingestion by Elasticsearch/Logstash.

Log event names: `method.execution.start`, `method.execution.success`, `method.execution.error`. When a `requestId` is available in Reactor Context (set by `RequestLoggingWebFilter`), it is included in every event.

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

### Outbound APIM Propagation

`WebClientConfig` propagates the resolved `X-Request-Id` to every outbound APIM call as an HTTP header. This allows APIM-side log correlation with BFF-side logs.

### JSON Structured Log Events

All global and method-level log events are serialized as JSON strings. Key event types:

| Event | Source | Key Fields |
|---|---|---|
| `http.request.start` | `RequestLoggingWebFilter` | `requestId`, `method`, `path`, `query`, `headers` |
| `http.request.end` | `RequestLoggingWebFilter` | `requestId`, `method`, `path`, `status`, `elapsedMs` |
| `http.request.error` | `RequestLoggingWebFilter` | `requestId`, `method`, `path`, `elapsedMs`, `exceptionType`, `errorMessage` |
| `apim.request` | `WebClientConfig` | `requestId`, `method`, `url`, `headers` |
| `method.execution.start` | `ExecutionLoggingAspect` | `requestId`*, `className`, `methodName`, `label`, `args` |
| `method.execution.success` | `ExecutionLoggingAspect` | `requestId`*, `className`, `methodName`, `elapsedMs`, `result` |
| `method.execution.error` | `ExecutionLoggingAspect` | `requestId`*, `className`, `methodName`, `elapsedMs`, `exceptionType`, `errorMessage` |

\* `requestId` is included when available from Reactor Context (Mono/Flux methods) or MDC (synchronous methods).

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



## Contribution Summary Configuration

Contribution summary labels, currency display mappings, and Excel headers are configured as regular Spring properties rather than environment variables. For now the runtime source is `src/main/resources/application-local.yml` plus the Kubernetes ConfigMap `ngtpa-display-config`. Config Service remains future work.

Current local defaults in `src/main/resources/application.yml`:

```yaml
reference-date:
  deployment-env: ${DEPLOY_ENV:}
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
    HKD.JP: HKD
  zh_HK:
    HKD: 港元
    HKD.JP: 港元
```

`reference-date.deployment-env` is the runtime deployment environment, separate from the request query `env`. For production-like deployments (`PROD`, `PRD`, `PRODUCTION`, `DR`, blank, and null), all use cases that require `ref-date` — contribution summary validation, contribution export, and notification flows — always use the app server timezone and current date. For non-production-like deployments, `reference-date.override-date` and `reference-date.override-zone-id` may be provided as a pair; when both are absent the app falls back to the server clock, and when only one is present startup-time validation is rejected when the resolver is used. Today these values come from Spring externalized configuration / ConfigMap through `ConfigBackedReferenceDateAdapter`; when the external Config Service API is available, `ConfigServiceReferenceDateAdapter` should become the alternative `ReferenceDatePort` implementation while reusing the same `ReferenceDateResolver` policy.

These values drive the synthetic total detail row in the JSON response, the first three column headers in the XLSX export, the locale-specific currency display returned in contribution summary JSON, and the effective contribution reference date. `trustCode` and `schemeType` stay empty until access-token claim extraction is implemented, so currency lookup currently falls back from `${code}.${env}` to `${code}`.


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


### `GET /api/v1/notifications`

Retrieves the current notice list for a member context.

**Query parameters:**

- `env` (required)
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
  "errorCode": "400",
  "message": "Invalid timezone: Mars/Olympus",
  "timestamp": "2026-04-29T07:42:40.643070200Z"
}
```

APIM or crypto failures still use the same error envelope with `5xx` status codes.

### `PATCH /api/v1/notification`

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
- `policy-no`, `cert-no`, and `user-id` are resolved from externalized `temporary-member-context.profiles.notifications.*` configuration (see **Temporary Member Context Configuration** below) until Auth Server integration is implemented. `ref-date` remains temporarily hardcoded.

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
  "errorCode": "400",
  "message": "notificationId must not be empty",
  "timestamp": "2026-04-29T07:42:40.643070200Z"
}
```

Top-level APIM failures are translated to the same standardized `5xx` error envelope.

### `GET /api/v1/contributions`

Retrieves grouped contribution summary rows for a member context as JSON.

**Query parameters:**

- `env` (required by frontend contract; currently forwarded only as application context)
- `mbrType` (required by frontend contract; currently forwarded only as application context)
- `fromDate` (required; `dd/MM/yyyy`)
- `toDate` (required; `dd/MM/yyyy`)

**Example:**

```http
GET /api/v1/contributions?env=JP&mbrType=MBR&fromDate=05/04/2026&toDate=05/05/2026
```

**Behavior:**

- The BFF calls APIM `POST /ws/NGTPA/v1/TRPGetContSumy`.
- `cover-from` is taken from `fromDate`; `cover-to` is taken from `toDate`.
- `fromDate` and `toDate` must both be within `[ref-date - 36 months, ref-date]`, inclusive.
- `ref-date` is resolved from deployment-scoped reference date config, not from the request query `env`.
- `reference-date.deployment-env` controls whether the paired non-production override may be used.
- Contribution rows are grouped by `deal-date + cover-from + cover-to`.
- Dynamic detail items are joined from `contDtl[*].disp-src` to `dispSrc[*].disp-src` and sorted by `dispSrc.seq` ascending.
- `totalContributionEn` is the sum of the grouped detail amounts using `BigDecimal`.
- The first detail item is synthetic and uses the configured `contribution-summary.total-label.*` values.
- Each detail label exposes only `en` and `zh`.
- Detail `amountEn` and `amountZh` are pre-formatted strings using the resolved currency display.
- `totalContributionEn` and `totalContributionZh` use the same formatting logic with insignificant trailing zeros stripped.
- `policy-no`, `cert-no`, `user-id`, `trustCode`, and `schemeType` are resolved from externalized `temporary-member-context.profiles.contributions.*` configuration (see **Temporary Member Context Configuration** below) until Auth Server integration is implemented.

**Response:**

```json
{
  "contributions": [
    {
      "dealingDate": "01/03/2026",
      "coveringPeriod": "01/03/2026 - 31/03/2026",
      "totalContributionEn": "HKD 24908.45",
      "totalContributionZh": "港元 24908.45",
      "details": [
        {
          "labels": {
            "en": "Total Contributions",
            "zh": "供款總額"
          },
          "amountEn": "HKD 24908.45",
          "amountZh": "港元 24908.45"
        },
        {
          "labels": {
            "en": "Company",
            "zh": ""
          },
          "amountEn": "HKD 17791.75",
          "amountZh": "港元 17791.75"
        },
        {
          "labels": {
            "en": "Member",
            "zh": ""
          },
          "amountEn": "HKD 7116.7",
          "amountZh": "港元 7116.7"
        }
      ]
    }
  ]
}
```

**Error response:**

```json
{
  "errorCode": "400",
  "message": "fromDate must be provided in dd/MM/yyyy format",
  "timestamp": "2026-05-06T11:33:53.000000000Z"
}
```

Range validation failures also use the same envelope with messages such as `fromDate must not be after toDate` and `fromDate and toDate must be within the range from ref-date minus 36 months to ref-date`.

### `GET /api/v1/contributions/export`

Exports the contribution summary as an XLSX workbook.

**Query parameters:**

- `env` (required by frontend contract; currently forwarded only as application context)
- `mbrType` (required by frontend contract; currently forwarded only as application context)

**Example:**

```http
GET /api/v1/contributions/export?env=JP&mbrType=MBR
Accept: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
```

**Behavior:**

- The BFF resolves `ref-date` through `ReferenceDatePort`.
- `ReferenceDatePort` uses deployment environment configuration, not the request query `env`, to decide whether non-production override rules apply.
- `cover-from` is computed as `ref-date.minusMonths(36)`; `cover-to` is the resolved `ref-date`.
- `policy-no`, `cert-no`, `user-id`, `trustCode`, and `schemeType` are resolved from externalized `temporary-member-context.profiles.contributions.*` configuration until Auth Server integration is implemented.
- The first three headers come from `contribution-summary.headers.*`.
- Dynamic source columns are sorted by `dispSrc.seq` ascending.
- Amount cells are numeric, formatted as `0.00`, and rounded with `HALF_UP`.
- Missing dynamic source amounts are written as blank cells.

**Success headers:**

- `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- `Content-Disposition: attachment; filename="Contribution_Summary.xlsx"`

Failures use the same standardized JSON error envelope as the rest of the API.

---

## Project Documents

| Document | Location |
|---|---|
| Functional Requirements | `docs/brd/converted/functional_reqs.md` |
| API Specifications | `docs/brd/converted/api_specs.md` |
| Data Models | `docs/brd/converted/data_models.md` |
| Gap Analysis | `docs/brd/analysis/gap_analysis.md` |
| Architecture Plan | `docs/brd/analysis/architecture_plan.md` |

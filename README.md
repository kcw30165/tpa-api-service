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
| Auth (outbound) | Spring Security OAuth2 Client Credentials |
| Encryption | BouncyCastle 1.82 (RSA + AES/CBC) |
| Spreadsheet export | Apache POI OOXML |
| Build | Maven 3.9.x (wrapper — `./mvnw`) |
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
│   │   └── out/         # ApimNoticeMessagePort, ApimNotificationReadStatusPort, ApimContributionSummaryPort, ReferenceDatePort
│   ├── usecase/         # GetNotificationsService, UpdateNotificationsReadStatusService, GetContributionSummaryService, ExportContributionSummaryService
│   └── dto/             # Notification and contribution summary commands/results
├── adapter/
│   ├── in/web/          # Reactive controllers, request/response records
│   │   ├── NotificationController
│   │   ├── ContributionController
│   │   ├── ContributionSummaryWorkbookExporter
│   │   ├── ApiExceptionHandler (@RestControllerAdvice)
│   │   ├── request/     # UpdateNotificationsReadStatusRequest
│   │   └── response/    # Notification and contribution summary response records
│   └── out/apim/        # APIM integration
│       ├── ApimWebClientFacade        # Pure HTTP transport (OAuth2 token attach)
│       ├── ApimNoticeMessageAdapter   # Implements ApimNoticeMessagePort
│       ├── ApimNotificationReadStatusAdapter # Implements ApimNotificationReadStatusPort
│       ├── ApimContributionSummaryAdapter # Implements ApimContributionSummaryPort
│       ├── configserver/ConfigBackedReferenceDateAdapter # Temporary non-prod reference-date resolver
│       ├── ApimCertificateService     # Fetches BCT public key from APIM
│       ├── ApimAppCertificateService  # Loads app RSA keys + X509 cert
│       ├── ApimPayloadCryptoService   # AES/CBC + RSA field encryption/decryption
│       ├── crypto/                    # APIM-specific crypto helpers and exceptions
│       └── dto/                       # APIM request/response POJOs
├── config/              # Spring configuration beans (unchanged across layers)
│   ├── ApimProperties
│   ├── ContributionSummaryProperties
│   ├── WebClientConfig
│   ├── SecurityConfig
│   ├── JacksonConfig
│   └── ApimCryptoConfig
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
adapter/in/web  →  application  →  domain
adapter/out/apim →  application  →  domain
config          →  framework composition only
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
| `REFERENCE_DATE_ZONE_ID` | Server zone used to resolve contribution reference date | `Asia/Hong_Kong` |
| `REFERENCE_DATE_NON_PROD_OVERRIDE` | Optional non-production override in `dd/MM/yyyy` | _(empty)_ |
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

For the dev cluster, the Kubernetes deployment or external config repository must set `CORS_ALLOWED_ORIGINS=http://localhost:4200` before local frontend calls from that origin will succeed. Those deployment manifests are outside this repository.

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

## Contribution Summary Configuration

Contribution summary labels, currency display mappings, and Excel headers are configured as regular Spring properties rather than environment variables. For now the runtime source is `src/main/resources/application-local.yml` plus the Kubernetes ConfigMap `ngtpa-display-config`. Config Service remains future work.

Current local defaults in `src/main/resources/application.yml`:

```yaml
reference-date:
  zone-id: ${REFERENCE_DATE_ZONE_ID:Asia/Hong_Kong}
  non-prod-override: ${REFERENCE_DATE_NON_PROD_OVERRIDE:}

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

`reference-date.zone-id` controls the server date zone used by contribution summary validation and export. `reference-date.non-prod-override` is applied only when the request `env` is non-production; `PROD`, `PRD`, `PRODUCTION`, blank, and null all stay production-safe and always use the current server date.

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
- The BFF currently hardcodes `cert-no`, `policy-no`, `user-id`, and `ref-date` while auth/config integration is pending.

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
- `ref-date` is the current server date in `reference-date.zone-id`, unless a non-production override is configured.
- Contribution rows are grouped by `deal-date + cover-from + cover-to`.
- Dynamic detail items are joined from `contDtl[*].disp-src` to `dispSrc[*].disp-src` and sorted by `dispSrc.seq` ascending.
- `totalContributionEn` is the sum of the grouped detail amounts using `BigDecimal`.
- The first detail item is synthetic and uses the configured `contribution-summary.total-label.*` values.
- Each detail label exposes only `en` and `zh`.
- Detail `amountEn` and `amountZh` are pre-formatted strings using the resolved currency display.
- `totalContributionEn` and `totalContributionZh` use the same formatting logic with insignificant trailing zeros stripped.
- The BFF currently hardcodes `policy-no`, `cert-no`, and `user-id` while auth/progress integrations are pending.
- The BFF currently hardcodes empty `trustCode` and `schemeType` until access-token claim extraction is implemented.

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
- `cover-from` is computed as `ref-date.minusMonths(36)`; `cover-to` is the resolved `ref-date`.
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

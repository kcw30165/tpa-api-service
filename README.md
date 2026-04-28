# NGTPA API Service

Backend-for-Frontend (BFF) service for the **ORSO NGTPA** member portal.  
Acts as the single gateway between the Angular frontend and the APIM layer (which fronts the Progress OpenEdge business logic). No local database — all state lives in Progress via APIM.

---

## Tech Stack

| Concern | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0.6 |
| HTTP (inbound) | Spring MVC (`spring-boot-starter-webmvc`) |
| HTTP (outbound) | Spring WebFlux `WebClient` |
| Auth (outbound) | Spring Security OAuth2 Client Credentials |
| Encryption | BouncyCastle 1.82 (RSA + AES/CBC) |
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
│   │   ├── in/          # GetMessageBoardUseCase
│   │   └── out/         # ApimNoticeMessagePort
│   ├── usecase/         # GetMessageBoardService
│   └── dto/             # GetMessageBoardCommand, MessageBoardResult
├── adapter/
│   ├── in/web/          # Spring MVC controllers, request/response records
│   │   ├── MessageBoardController
│   │   ├── ApiExceptionHandler (@RestControllerAdvice)
│   │   ├── request/     # GetMessageBoardWebRequest (record)
│   │   └── response/    # MessageBoardWebResponse, MessageWebDto, ApiErrorResponse (records)
│   └── out/apim/        # APIM integration
│       ├── ApimWebClientFacade        # Pure HTTP transport (OAuth2 token attach)
│       ├── ApimNoticeMessageAdapter   # Implements ApimNoticeMessagePort
│       ├── ApimCertificateService     # Fetches BCT public key from APIM
│       ├── ApimAppCertificateService  # Loads app RSA keys + X509 cert
│       ├── ApimPayloadCryptoService   # AES/CBC + RSA field encryption/decryption
│       └── dto/                       # APIM request/response POJOs
├── config/              # Spring configuration beans (unchanged across layers)
│   ├── ApimProperties
│   ├── WebClientConfig
│   ├── SecurityConfig
├── exception/           # ApimException (shared)
└── util/apim/           # ApimCertUtility, JsonFieldCryptoUtil, RsaFieldCryptoUtil
```

### Dependency Rule

```
adapter/in/web  →  application  →  domain
adapter/out/apim →  application  →  domain
config / util   →  (no inward dependency)
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

### Via Maven task

```bash
# In .vscode/tasks.json — label: mvn-spring-boot-run
./mvnw spring-boot:run
```

---

## Environment Variables

| Variable | Description | Default |
|---|---|---|
| `SERVER_PORT` | HTTP port | `8888` |
| `APIM_BASEURL` | APIM base URL | _(required)_ |
| `APIM_TIMEOUTMILLISECONDS` | WebClient timeout | `10000` |
| `APIM_ENCRYPTION_ENABLED` | Enable RSA/AES payload encryption | `false` |
| `APIM_CERTIFICATE_PATH` | Path to BCT public cert endpoint | `/api/wssupport/v1/encryption/certificate` |
| `APIM_API_KEY` | API key sent as `KeyId` header | _(required if encryption enabled)_ |
| `APIM_PRIVATE_KEY_PEM` | App RSA private key (Base64 PEM) | _(required if encryption enabled)_ |
| `APIM_PUBLIC_KEY_PEM` | App RSA public key (Base64 PEM) | _(required if encryption enabled)_ |
| `APIM_CLIENT_ID` | OAuth2 client ID | _(required)_ |
| `APIM_CLIENT_SECRET` | OAuth2 client secret | _(required)_ |
| `APIM_CLIENT_AUTH_METHOD` | OAuth2 auth method | `client_secret_basic` |
| `APIM_CLIENT_SCOPE` | OAuth2 scope | _(empty)_ |
| `APIM_TOKEN_URI` | OAuth2 token endpoint | _(required)_ |
| `SPRING_CLOUD_CONFIG_ENABLED` | Enable Spring Cloud Config | `false` |

---

## API Endpoints

### `POST /api/messages`

Retrieves the notice message board for a member.

**Request body:**
```json
{
  "policy-no": "string",
  "cert-no": "string",
  "user-id": "string",
  "ref-date": "dd/MM/yyyy",
  "env": "string",
  "mbr-type": "string"
}
```

**Response:**
```json
{
  "page": 1,
  "size": 10,
  "messages": [
    {
      "msgCode": "string",
      "msgCodeLong": "string",
      "seq": 1,
      "msgType": "MARKET_UPDATE",
      "msgContentChi": "string",
      "msgContentEng": "string",
      "startDatetime": "28/04/2026 09:00",
      "msgStatus": "UNREAD"
    }
  ]
}
```

**Error response:**
```json
{ "message": "APIM error description" }
```

---

## Project Documents

| Document | Location |
|---|---|
| Functional Requirements | `docs/brd/converted/functional_reqs.md` |
| API Specifications | `docs/brd/converted/api_specs.md` |
| Data Models | `docs/brd/converted/data_models.md` |
| Gap Analysis | `docs/brd/analysis/gap_analysis.md` |
| Architecture Plan | `docs/brd/analysis/architecture_plan.md` |

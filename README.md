# NGTPA API Service

NGTPA API Service is a Java 21 Spring Boot WebFlux backend-for-frontend for member-facing portal flows. It exposes HTTP APIs for authentication, registration, personal information, notifications, contribution summaries, reference data, and internal reference-date cache operations.

The service sits between frontend clients and downstream services such as APIM-backed integrations and Redis-backed runtime/session storage. Current code shows a ports-and-adapters structure, thin WebFlux controllers, and feature-specific response models rather than a single universal REST envelope.

## Tech Stack

| Area                  | Current implementation                                       |
| --------------------- | ------------------------------------------------------------ |
| Language              | Java 21                                                      |
| Application framework | Spring Boot 4.0.6                                            |
| Dependency platform   | Spring Cloud 2025.1.0                                        |
| Inbound HTTP          | Spring WebFlux                                               |
| Security              | Spring Security, Spring OAuth2 Client                        |
| Outbound HTTP         | Spring WebClient-based adapters under APIM outbound packages |
| Runtime state         | Spring Data Redis with Lettuce                               |
| Crypto                | Bouncy Castle 1.82                                           |
| Binary export         | Apache POI OOXML 5.4.1                                       |
| Build                 | Maven                                                        |
| Test stack            | Spring Boot test starters, ArchUnit 1.3.0, JaCoCo 0.8.12     |

## Architecture Overview

The repository follows a clean or hexagonal structure. Current code keeps controllers and web mappers under inbound adapters, orchestration and ports under application packages, pure models under domain packages, external transport concerns under outbound adapters, and Spring wiring under config and infrastructure packages.

APIM transport details stay inside outbound APIM adapters. Redis-backed runtime state, revisioned auth-flow envelopes, authenticated-session state, idempotency records, and cache administration stay inside outbound Redis adapters. Username Recovery keeps its protected workflow context inside a single revisioned auth-flow envelope rather than a separate workflow-state sidecar. Controllers resolve request context and delegate to use cases instead of embedding downstream transport logic.

```text
src/main/java/com/bct/ngtpa/apiservice/
 adapter/
  in/web/
  out/apim/
  out/redis/
  out/configservice/
 application/
 domain/
 config/
 infrastructure/
 shared/
src/main/resources/
docs/
```

## Implemented Capabilities

- Authentication workflows: captcha, login start, login OTP send or resend, OTP verification, login cancel, logout
- Username Recovery: start, username delivery request, cancellation, idempotent replay, and protected single-envelope workflow state
- Registration workflows: start, username check, OTP resend or verify, complete, cancel
- Selected-account business APIs: contribution summaries, contribution export, notifications, personal information
- Reference data: countries
- Internal operations: reference-date refresh, inspection, and cleanup

## API Overview

Base path: `/api/v1`

### Authentication and Session

| Method | Path                        | Purpose                                              | Requirement                    | Selected account |
| ------ | --------------------------- | ---------------------------------------------------- | ------------------------------ | ---------------- |
| POST   | `/auth/captcha/challenge` | Create a captcha challenge                           | Pre-auth                       | No               |
| POST   | `/auth/captcha/verify`    | Verify captcha input                                 | Pre-auth                       | No               |
| POST   | `/auth/captcha/refresh`   | Refresh the active captcha                           | Pre-auth                       | No               |
| POST   | `/auth/login`             | Start login and create auth-flow state               | Pre-auth                       | No               |
| POST   | `/auth/login/otp/send`    | Send login OTP                                       | Auth-flow cookie and CSRF      | No               |
| POST   | `/auth/login/otp/resend`  | Resend login OTP                                     | Auth-flow cookie and CSRF      | No               |
| POST   | `/auth/login/otp/verify`  | Verify login OTP and establish authenticated session | Auth-flow cookie and CSRF      | No               |
| POST   | `/auth/login/cancel`      | Cancel the current login flow                        | Auth-flow cookie and CSRF      | No               |
| POST   | `/auth/logout`            | End the authenticated session                        | Authenticated session and CSRF | No               |

Detailed design packs: [docs/design/login/finalized/0-login-design-pack-index.md](docs/design/login/finalized/0-login-design-pack-index.md), [docs/design/login/finalized/login-api-contract.md](docs/design/login/finalized/login-api-contract.md)


## Username Recovery

Username Recovery uses one protected, revisioned auth-flow envelope. The encrypted `UsernameRecoveryAuthFlowContext` stores the verified destination, language, candidate projections, delivery states, and workflow status. START creates the envelope, Request uses revision-aware envelope updates for reservation and delivery outcomes, and Cancel changes the envelope lifecycle to `CANCELLED` while retaining the protected context and original expiry. There is no separate Username Recovery workflow-state Redis sidecar.

| Method | Path | Purpose | Requirement | Selected account |
|---|---|---|---|---|
| POST | `/auth/username-recovery/start` | Verify recovery prerequisites and create the protected auth-flow envelope | Pre-auth, captcha, idempotency | No |
| POST | `/auth/username-recovery/request` | Request delivery of a selected registered username | Auth-flow cookie, CSRF, idempotency | No |
| POST | `/auth/username-recovery/cancel` | Cancel the current Username Recovery flow | Auth-flow cookie, CSRF, idempotency | No |

Key runtime properties:

- `auth-workflows.username-recovery.context-protection.enabled`
- `auth-workflows.username-recovery.context-protection.key-version`
- `auth-workflows.username-recovery.context-protection.secret`
- `auth-workflows.username-recovery.session.ttl-seconds`
- `USERNAME_RECOVERY_COOKIE_SECURE`

The context-protection secret must be environment-specific and contain at least 32 characters. Do not commit a real secret.


### Registration

| Method | Path                                  | Purpose                                       | Requirement                         | Selected account |
| ------ | ------------------------------------- | --------------------------------------------- | ----------------------------------- | ---------------- |
| POST   | `/auth/registration/start`          | Start registration and create auth-flow state | Pre-auth                            | No               |
| POST   | `/auth/registration/username/check` | Check username availability                   | Auth-flow cookie and CSRF           | No               |
| POST   | `/auth/registration/otp/resend`     | Resend registration OTP                       | Auth-flow cookie and CSRF           | No               |
| POST   | `/auth/registration/otp/verify`     | Verify registration OTP                       | Auth-flow cookie and CSRF           | No               |
| POST   | `/auth/registration/complete`       | Complete registration                         | Auth-flow cookie, CSRF, idempotency | No               |
| POST   | `/auth/registration/cancel`         | Cancel the current registration flow          | Auth-flow cookie and CSRF           | No               |

Detailed contract: [docs/design/registration/finalized/registration-api-contract.md](docs/design/registration/finalized/registration-api-contract.md)

### Contributions

| Method | Path                      | Purpose                               | Requirement                                 | Selected account |
| ------ | ------------------------- | ------------------------------------- | ------------------------------------------- | ---------------- |
| GET    | `/contributions`        | Return contribution summaries         | Authenticated session, CSRF,`Account-Ref` | Yes              |
| GET    | `/contributions/export` | Export contribution summaries as XLSX | Authenticated session, CSRF,`Account-Ref` | Yes              |

### Notifications

| Method | Path               | Purpose                         | Requirement                                 | Selected account |
| ------ | ------------------ | ------------------------------- | ------------------------------------------- | ---------------- |
| GET    | `/notifications` | Return notifications            | Authenticated session, CSRF,`Account-Ref` | Yes              |
| PATCH  | `/notifications` | Update notification read status | Authenticated session, CSRF,`Account-Ref` | Yes              |

### Personal Information

| Method | Path                      | Purpose                                      | Requirement                                 | Selected account |
| ------ | ------------------------- | -------------------------------------------- | ------------------------------------------- | ---------------- |
| GET    | `/personal-information` | Return page-backed personal information data | Authenticated session, CSRF,`Account-Ref` | Yes              |
| PUT    | `/personal-information` | Update personal information                  | Authenticated session, CSRF,`Account-Ref` | Yes              |

### Reference Data

| Method | Path                          | Purpose                                        | Requirement | Selected account |
| ------ | ----------------------------- | ---------------------------------------------- | ----------- | ---------------- |
| GET    | `/reference-data/countries` | Return country and calling-code reference data | Pre-auth    | No               |

### Internal Operations

| Method | Path                                 | Purpose                              | Requirement                                                      | Selected account |
| ------ | ------------------------------------ | ------------------------------------ | ---------------------------------------------------------------- | ---------------- |
| POST   | `/internal/reference-date/refresh` | Refresh one reference-date entry     | Internal endpoint; authenticated when in-process auth is enabled | No               |
| GET    | `/internal/reference-date/all`     | List cached reference-date entries   | Internal endpoint; authenticated when in-process auth is enabled | No               |
| DELETE | `/internal/reference-date/all`     | Delete cached reference-date entries | Internal endpoint; authenticated when in-process auth is enabled | No               |

Detailed contract: [docs/design/reference-date/reference-date-cache-admin.md](docs/design/reference-date/reference-date-cache-admin.md)

## Common API Conventions

- Versioning uses `/api/v1` controller mappings.
- JSON is the default content type for API responses. The contribution export endpoint returns `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.
- `/api/**` requests currently require a client-supplied `X-Request-Id`. The service echoes `X-Request-Id` in responses and propagates the resolved value through Reactor context and outbound APIM filters.
- `Accept-Language` is consumed by controllers and request-context helpers. Current normalization resolves `en*` to `en`, `zh` or `zh-HK` to `zh_HK`, and defaults to `en` when missing or unsupported.
- Pre-auth follow-up endpoints read the `auth_flow_sid` cookie and `X-XSRF-TOKEN` header. Auth-flow configuration also defines an `XSRF-TOKEN` cookie scoped to `/api/v1/auth`.
- Selected-account endpoints use the authenticated session cookie named `portal_sid`, the `X-XSRF-TOKEN` header, and `Account-Ref`. The authenticated session ID value itself is generated with a `sid_` prefix.
- `Account-Ref` is not required globally. Current selected-account APIs require it.
- Mutation and workflow endpoints commonly return `MutationResponse<T>`:

```json
{
 "success": true,
 "status": "SUCCESS",
 "result": {},
 "messages": [],
 "errors": []
}
```

- Page-backed personal-information reads return `FormPageResponse<T>` with `page` and `form` fields instead of `result`:

```json
{
 "success": true,
 "status": "SUCCESS",
 "page": {},
 "form": {},
 "messages": [],
 "errors": []
}
```

- Some read endpoints return feature-specific JSON models directly, for example contributions, notifications, reference data, and the internal reference-date refresh response.
- Transport and system failures handled by the global exception advice are returned as `MutationResponse<Void>` with statuses such as `VALIDATION_FAILED`, `BUSINESS_REJECTED`, `SYSTEM_ERROR`, and `DOWNSTREAM_ERROR`.
- The common wrappers expose both `messages[]` and `errors[]`. Current blocking failures are returned through `errors[]`.

## Prerequisites

### Build and test

- JDK 21
- Apache Maven installed and available as `mvn`

### Full runtime integration

- Redis Sentinel connectivity unless `redis-cache.enabled=false`
- APIM connectivity and credentials for APIM-backed flows
- Startup crypto settings for login, authenticated session, and registration context protection
- Any downstream message-delivery configuration required by the flows you exercise

## Build and Test

Ensure `JAVA_HOME` points to a JDK 21 installation. Maven builder parallelism uses `-T 1C`; JUnit classes and methods should remain sequential unless a test suite is explicitly proven parallel-safe.

```bash
mvn -T 1C clean compile -DskipTests
mvn -T 1C -Djunit.jupiter.execution.parallel.enabled=false test
mvn -T 1C -Djunit.jupiter.execution.parallel.enabled=false -Dtest=ContributionControllerTest test
mvn -T 1C -Djunit.jupiter.execution.parallel.enabled=false clean verify
```

## Run Locally

1. Activate the local profile from [src/main/resources/application-local.yml](src/main/resources/application-local.yml), for example with `SPRING_PROFILES_ACTIVE=local`.
2. Provide local configuration through environment variables or a repository-root `.env` or `.env.properties` file. The base application configuration imports `optional:file:./.env[.properties]`.
3. At minimum, configure the current startup-critical protection settings:

- `auth-workflows.login.context-protection.key-version`
- `auth-workflows.login.context-protection.secret`
- `auth-workflows.login.session.protection.base64-key`
- `auth-workflows.registration.context-protection.secret`
- `auth-workflows.username-recovery.context-protection.key-version`
- `auth-workflows.username-recovery.context-protection.secret`

1. Start the application:

```bash
mvn -T 1C -DskipTests -Dspring-boot.run.profiles=local spring-boot:run
```

Current default port is `8888`.

Useful local behavior from current configuration:

- `application-local.yml` sets `api.security.require-authentication=false`
- `spring.cloud.config.enabled` defaults to `false`
- Redis can be disabled locally with `redis-cache.enabled=false`

The attached startup log in this repository workspace confirms that leaving `auth-workflows.login.context-protection.key-version` blank prevents startup.

## Configuration

| Prefix                                                | Purpose                                                                               | Secret management | Notes                                                                      |
| ----------------------------------------------------- | ------------------------------------------------------------------------------------- | ----------------- | -------------------------------------------------------------------------- |
| `apim`                                              | APIM base URL, OAuth client, encryption, retry, credential profiles                   | Yes               | Required for APIM-backed calls                                             |
| `redis-cache`                                       | Redis Sentinel, SSL bundle, timeout, key prefix                                       | Usually           | Enabled by default; disable explicitly for no-Redis local runs             |
| `auth-workflows`                                    | Login, registration, and forgot-password rules, OTP behavior, session cookie settings | Partly            | Includes authenticated session cookie name`portal_sid`                   |
| `auth-flow-session`                                 | Auth-flow cookie, CSRF, TTL, scenario settings                                        | No                | Drives`auth_flow_sid` and `XSRF-TOKEN` behavior                        |
| `auth-workflows.login.context-protection`           | Login pre-auth encryption settings                                                    | Yes               | Required at startup                                                        |
| `auth-workflows.login.session.protection`           | Authenticated-session payload protection                                              | Yes               | Required at startup                                                        |
| `auth-workflows.registration.context-protection`    | Registration context and username-check protection                                    | Yes               | Required at startup                                                        |
| `auth-workflows.username-recovery.context-protection` | Username Recovery protected auth-flow context | Yes | Required for Username Recovery runtime composition; workflow state is stored only in the auth-flow envelope |
| `auth-workflows.forgot-password.context-protection` | Forgot-password continuation-context encryption settings                              | Yes               | Declared for later workflow wiring; not startup-critical in Subtask 002    |
| `config-service`                                    | Config service HTTP client settings                                                   | Usually           | Startup integration settings                                               |
| `sms`                                               | SMS delivery settings for workflow messaging                                          | Usually           | Used by current auth workflows                                             |
| `cors`                                              | Allowed origins, headers, and methods                                                 | No                | Applied when origins are configured                                        |
| `request-logging`                                   | Structured request logging and body-logging rules                                     | No                | Body logging is opt-in                                                     |
| `logging-sanitizer`                                 | Sensitive-field masking configuration                                                 | No                | Masks tokens, secrets, identifiers, and key material                       |
| `reference-date`                                    | Reference-date cache TTL and refresh behavior                                         | No                | Used by internal reference-date operations                                 |
| `bff-pages`                                         | Page schema and validation metadata                                                   | No                | See page-schema DSL doc                                                    |

Page-schema reference: [docs/pageconfig/045-page-schema-validation-dsl.md](docs/pageconfig/045-page-schema-validation-dsl.md)

## Security and Sensitive Data

- Do not commit real secrets, credentials, cookies, tokens, keys, account identifiers, or personal data.
- Treat login secrets, authenticated-session protection keys, registration protection secrets, APIM credentials, Redis credentials, and message-delivery credentials as environment-specific sensitive configuration.
- Keep sample requests and examples synthetic. Do not paste real `portal_sid`, `auth_flow_sid`, `X-XSRF-TOKEN`, `Account-Ref`, user identifiers, or downstream credentials into docs or logs.
- Current logging code sanitizes configured sensitive fields, but developers should still avoid adding sensitive values to logs, tests, or markdown examples.

## Observability

- HTTP request logging is implemented as a global WebFlux filter.
- Request correlation is based on `X-Request-Id`, which is stored in response headers, exchange attributes, and Reactor context.
- Execution logging is implemented through an aspect in the infrastructure logging package.
- Request and response bodies are not logged by default. Body logging must be enabled explicitly and then opted into per endpoint.
- Binary responses such as XLSX exports are passed through without response-body logging.

## Project Documentation

- [docs/design/login/finalized/0-login-design-pack-index.md](docs/design/login/finalized/0-login-design-pack-index.md) — index of current login design and contract documents
- [docs/design/login/finalized/login-api-contract.md](docs/design/login/finalized/login-api-contract.md) — detailed login workflow contract baseline
- [docs/design/registration/finalized/registration-api-contract.md](docs/design/registration/finalized/registration-api-contract.md) — detailed registration workflow contract baseline
- [docs/design/reference-date/reference-date-cache-admin.md](docs/design/reference-date/reference-date-cache-admin.md) — internal reference-date cache admin contract
- [docs/implementation/username-recovery-single-envelope-migration.md](docs/implementation/username-recovery-single-envelope-migration.md) — Username Recovery single-envelope migration and sidecar-removal record
- [docs/pageconfig/045-page-schema-validation-dsl.md](docs/pageconfig/045-page-schema-validation-dsl.md) — page schema and validation DSL reference

## Documentation Maintenance

Keep this README as a concise repository entry point. Update it when prerequisites, build or run procedures, major capability areas, public endpoint inventory, common API conventions, or key documentation links change.

Detailed endpoint contracts, architecture decisions, error matrices, implementation evidence, and historical records belong under `docs/`, not in the root README.

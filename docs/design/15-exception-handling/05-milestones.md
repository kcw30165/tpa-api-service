# 05 - Milestones and Sub-tasks

Gates follow the project workflow: sub-task gates (fail-first where behaviour changes, spotless,
compile, test-compile, targeted tests) and milestone gates (spotless check, full `mvn test`,
coverage, smoke test, SonarQube). Brand-new types use compile-ready stubs so fail-first tests fail on
assertions, not on compilation. Pin-only tests (no behaviour change) have no fail-first step.

## M1 - Central exception handling (no public behaviour change)

| # | Commit type | Work | Main tests |
|---|---|---|---|
| 1.0 | - | Read-only capture of the full `ApiExceptionHandler` and `AuthenticatedPortalContextWebExceptionHandler` and their tests | - |
| 1.1 | test | Characterization pack for every current handler (status, body, header, log event, level, stack trace) | `ApiExceptionHandlerCharacterizationTest`, `AuthenticatedPortalContextWebExceptionHandlerCharacterizationTest` |
| 1.2 | feat | `ErrorDescriptor`, `LogLevel`, `ErrorContext`, `ExceptionMapping`, `ExceptionMappingRegistry` | `ExceptionMappingRegistryTest` |
| 1.3 | refactor | `ErrorResponseRenderer`, `ErrorEventLogger` (logging moved verbatim) | `ErrorResponseRendererTest`, `ErrorEventLoggerTest` + characterization |
| 1.4 | refactor | Move all mappings into `*ExceptionMappings`; `ApiExceptionHandler` delegates; rewrite source-text tests that read it | characterization + `ExceptionMappingRegistryArchitectureTest` |
| 1.5 | refactor | `AuthenticatedPortalContextWebExceptionHandler` on registry/renderer/logger | characterization |
| 1.6 | fix | Account gain/loss through renderer/logger (adds `X-Request-Id` + log) | fail-first `AccountGainLossExceptionMappingsTest` |

## M2 - Logging foundation

| # | Commit type | Work | Main tests |
|---|---|---|---|
| 2.1 | - | Read-only: Spring Boot version, `mvn dependency:tree` for logging and `context-propagation`, current console pattern | - |
| 2.2 | feat | Structured JSON console output for non-local profiles | config test + smoke log check |
| 2.3 | feat | Request id (and trust code, user role) in MDC across Reactor threads | `RequestContextMdcPropagationTest` |
| 2.4 | feat | `StructuredFailureEventLogger` level by `FailureOrigin`; `record(event, cause)` | fail-first `StructuredFailureEventLoggerTest` |
| 2.5 | test | `ErrorLogCallSiteRatchetArchitectureTest` with current `KNOWN_REMAINING` | itself |

## M3 - Call-site migration (one ERROR per incident)

Each sub-task: read-only classification table first (INCIDENT / DIAGNOSTIC), Nick approves, then
implementation, and `KNOWN_REMAINING` shrinks.

| # | Commit type | Scope |
|---|---|---|
| 3.1 | refactor | Login (controller, OTP verify/resend services, session establishment) |
| 3.2 | refactor | Forgot Password (start, details/OTP, resend, username lookup mapper/adapter) |
| 3.3 | refactor | Registration (start mapper, username check service) |
| 3.4 | refactor | Authenticated session lifecycle and validation services |
| 3.5 | refactor | Personal Information mappers |
| 3.6 | refactor | Fund Fact Sheet, Fund Comparison, Config Service adapter, APIM facade profile-resolution |
| 3.7 | - | Read-only audit: every swallowing path that returns a system failure records a FailureEvent |
| 3.8 | refactor | Observer downgrades to WARN (aspect, filter 5xx, APIM facade, APIM outcome logger); update APIM error-logging docs; `KNOWN_REMAINING` empty |

## M4 - Error alerting

| # | Commit type | Work | Main tests |
|---|---|---|---|
| 4.1 | feat | `ErrorAlertProperties` + validation | `ErrorAlertPropertiesTest` |
| 4.2 | feat | `ErrorAlertFingerprint`, `ErrorAlertThrottle` (both `deduplicate` modes) | `ErrorAlertFingerprintTest`, `ErrorAlertThrottleTest` |
| 4.3 | feat | `ErrorAlert`, `ErrorAlertAppender` (level filter, exclusions, loop guard, queue) | `ErrorAlertAppenderTest` |
| 4.4 | feat | `ErrorAlertDispatcher`, `ErrorAlertNotificationPort`, `EmailErrorAlertAdapter`, email formatter | `ErrorAlertDispatcherTest`, `EmailErrorAlertAdapterTest` |
| 4.5 | feat | Installer/lifecycle wiring, `application.yml` `error-alert.*` | `ErrorAlertWiringTest` (enabled/disabled contexts, fake `SmtpClient`) |
| 4.6 | docs | Runbook: enable, change recipients, tune dedup, DEV verification | - |

## Coverage

Project targets apply: instruction >= 95%; branch >= 95% for records, validators and pure functions
(`ErrorDescriptor`, registry resolution, fingerprint, throttle, properties validation); 85-90% for
handlers, appender, dispatcher and adapters, each uncovered branch justified in one line.

## Push and MR

After M4 milestone gates pass. Jenkins/runtime values for PROD/DR (`ERROR_ALERT_ENABLED=true`,
recipients) are a separate pipeline change.

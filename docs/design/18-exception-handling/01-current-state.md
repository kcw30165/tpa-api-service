# 01 - Current State (origin/dev 92849aac)

Labels: **confirmed** = read in source on `origin/dev`; **suspected** = inferred, to be verified
by the sub-task that depends on it.

## 1. Exception handling

- **Confirmed.** Two global handlers exist:
  - `adapter.in.web.advice.ApiExceptionHandler` (`@RestControllerAdvice`) with 21
    `@ExceptionHandler` methods, including a catch-all `Exception` -> 500 `SYSTEM_ERROR`.
  - `adapter.in.web.filter.AuthenticatedPortalContextWebExceptionHandler` (`WebExceptionHandler`)
    for `AuthenticatedPortalContextResolutionException` -> 401.
- **Confirmed.** Most handlers use `buildMutationErrorResponse(...)`, which builds a
  `MutationResponse<Void>`, sets `X-Request-Id` and writes one `http.api.error` event. Log level and
  stack trace are chosen per handler through `logAtError` / `includeStackTrace` flags.
- **Confirmed.** `handleAccountGainLossRetrievalException` bypasses that path: no log line and no
  `X-Request-Id` response header.
- **Confirmed.** `ApimException` returns `ex.getStatusCode()` as the public HTTP status.
- **Confirmed.** Controllers for Login, Registration, Forgot Password and Captcha catch
  `RuntimeException` locally and build error responses themselves; 102 local error-response
  builder sites exist across 41 files.
- **Confirmed.** `SYSTEM_ERROR` maps to different HTTP statuses by feature:
  `LoginHttpResponses` 502 (500 for `SYSTEM_FAILED`), `RegistrationHttpResponses` 502 (its second
  `SYSTEM_ERROR` -> 503 branch is unreachable), `ForgotPasswordHttpResponses` 503,
  `HttpStatusClassification` 503, catch-all 500. Out of scope (D5).
- **Confirmed.** `RequiredRequestIdWebFilter` writes its own 400 body.

## 2. Logging

- **Confirmed.** Logback is the logging backend (tests use `ch.qos.logback.classic`). There is no
  `logback*.xml` and no structured-logging dependency in `pom.xml`.
- **Confirmed.** 54 `log.error` calls in 27 files on `origin/dev`.
- **Confirmed.** One unexpected exception thrown from a `@LogExecution` use case is logged at ERROR
  by `ExecutionLoggingAspect` (with stack trace), by `ApiExceptionHandler` (with stack trace) and by
  `RequestLoggingWebFilter` (`http.request.failed`, because status >= 500).
- **Confirmed.** An APIM HTTP failure is logged at ERROR by `ApimWebClientFacade` and by
  `ApimExchangeOutcomeLogger` (`apim.exchange.failed`) before reaching any handler.
- **Confirmed.** `StructuredFailureEventLogger` logs every `FailureEvent` at ERROR, including
  expected business rejections (registration lookup `err.tpa.member.not.found`, HTTP 422).
  `FailureEventPort` has one production caller (`ApimRegistrationLookupAdapter`).
- **Confirmed.** Auth flows swallow failures with `doOnError(log.error)` + `onErrorReturn(...)` or
  `onErrorResume(...)`; these never reach the central handler.
  Examples: `LoginOtpVerifyService`, `EstablishAuthenticatedLoginSessionService`,
  `AuthenticatedSessionLifecycleService`, `ForgotPasswordStartProtectedOperation`,
  `CheckRegistrationUsernameService` (`severity=FATAL`).
- **Confirmed.** Some controller `onErrorResume` paths return `SYSTEM_ERROR` with no log at all
  (for example `LoginController.startLogin`, `ForgotPasswordController.startForgotPassword`).
- **Confirmed.** Raw `getMessage()` is logged without `LoggingSanitizer` in `LoginController`,
  `LoginOtpVerifyService`, `EstablishAuthenticatedLoginSessionService` and
  `AuthenticatedSessionLifecycleService`.
- **Confirmed.** `PersonalInformationWebMapper` logs configuration mismatches at ERROR on every
  request that hits them.
- **Confirmed.** Two formats are mixed: JSON payloads (`log.x("{}", json)`) and `event=... key=...`.
- **Confirmed.** `requestId` lives in the Reactor Context (`RequestCorrelation.REQUEST_ID_CONTEXT_KEY`)
  and in exchange attributes; MDC is not populated.

## 3. Notification building blocks

- **Confirmed.** `EmailSenderPort` -> `SmtpEmailSenderAdapter` -> `PlainSocketSmtpClient`
  (enabled by `email.enabled`). `MessageDeliveryRuntimeConfig` wraps `MessageDeliveryPort` with a
  non-production destination override for member messages; calling `EmailSenderPort` directly does
  not apply that override.
- **Confirmed.** `DeploymentEnvironmentPolicy` treats PROD and DR as production-like.
- **Confirmed.** `ConfigServiceEnvironmentPostProcessor` runs before beans exist.

## 4. Tests that pin source text

- **Confirmed.** These tests assert literal strings in production sources and must be rewritten as
  behaviour tests when the code moves:
  `ApiResponseExceptionHandlingBoundaryTest`, `PortalAccessContextArchitectureGuardTest`,
  `RegistrationSessionSecurityAndReactiveCorrectnessTest`, `RegistrationRuntimeSemanticRegressionTest`,
  `RegistrationStructuredFailureLoggingContractTest`,
  `RegistrationLoggingLookupStabilizationIntegrationTest` (near-duplicate of the previous one),
  `RegistrationDownstreamRejectionLoggingContractTest`,
  `Task0043AuthenticatedRequestFailureHttpArchitectureTest`.

## 5. Prior decisions this refactor fulfils

- `docs/capabilities/apim-error-logging/README.md` defers "project-wide centralized logging" and a
  typed event vocabulary to this refactor.
- `PublicDownstreamStatusIsolationGuardTest` already forbids `ApiStatus.DOWNSTREAM_*` in
  `adapter.in.web`; it stays.

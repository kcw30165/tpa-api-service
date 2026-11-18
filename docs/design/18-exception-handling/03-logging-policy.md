# 03 - Central Logging (M2 foundation, M3 migration)

## 1. Level policy

| Situation | Level | Owner |
|---|---|---|
| Request access log (`http.request.start`, `http.request.end`) | INFO | `RequestLoggingWebFilter` |
| Completed request with status >= 400 (`http.request.failed`) | WARN (5xx moves from ERROR to WARN in M3) | `RequestLoggingWebFilter` |
| Client/validation error, expected business rejection | WARN | `ErrorEventLogger` / `StructuredFailureEventLogger` |
| Downstream business rejection (`apim.business.rejected`) | WARN (unchanged) | `ApimExchangeOutcomeLogger` |
| Observer of a failure that is decided elsewhere (aspect, APIM facade, APIM outcome logger) | WARN, with full diagnostics (moves from ERROR in M3) | the observer |
| **Terminal technical failure (an incident)** | **ERROR, exactly once** | `ErrorEventLogger` (propagated exceptions) or `StructuredFailureEventLogger` (swallowed failures) |
| Exception that escaped every handler (`http.request.error`) | ERROR | `RequestLoggingWebFilter` |

**Rule: one ERROR per incident.** An incident ERROR is written only where the failure is terminally
decided:

- the exception propagates to the web boundary -> `ErrorEventLogger`, or
- the code turns the failure into a result (`SYSTEM_FAILED`, `onErrorReturn`, `onErrorResume`) ->
  it must call `FailureEventPort.publish(event, cause)` with a technical `FailureOrigin`
  (`src/main/java/com/bct/ngtpa/apiservice/application/port/out/FailureEventPort.java:5-10`).

Every other observer logs at WARN. This is what makes D1 meaningful: an ERROR means "someone must act".

### Central technical ERROR payload

For `ErrorEventLogger` events with `logAtError=true`, the incident payload is metadata-only:
`event=http.api.error`, request ID when present, method, path, HTTP status, canonical error code,
exception type, and approved value-free mapping fields. Do not evaluate a mapping diagnostic or
`Throwable.getMessage()`, emit `sanitizedMessage`, or attach a Throwable/stack trace. This policy
overrides `ErrorDescriptor.includeStackTrace` for central technical ERROR events without changing
the descriptor or public response rendering.

The rule is specific to the central `http.api.error` technical ERROR path. It does not change WARN
diagnostic/optional-stack behavior, `StructuredFailureEventLogger`, `ExecutionLoggingAspect`, or
other logger sources. Do not infer a repository-wide logging privacy guarantee from this policy.

## 2. FailureEvent changes

- `StructuredFailureEventLogger` level by `FailureOrigin`:
  - WARN: `LOCAL_VALIDATION`, `LOCAL_BUSINESS`, `DOWNSTREAM_BUSINESS`
  - ERROR: `DOWNSTREAM_CONTRACT`, `DOWNSTREAM_INVALID_RESPONSE`, `DOWNSTREAM_TRANSPORT`, `LOCAL_SYSTEM`
- `FailureEventPort` exposes `void publish(FailureEvent event)` and
  `default void publish(FailureEvent event, Throwable cause)`; `StructuredFailureEventLogger`
  implements both (`src/main/java/com/bct/ngtpa/apiservice/application/port/out/FailureEventPort.java:5-10`;
  `src/main/java/com/bct/ngtpa/apiservice/infrastructure/logging/StructuredFailureEventLogger.java:28-35`). The cause-bearing overload adds
  `exceptionType`, `rootCauseType`, and `sanitizedMessage`; the latter is populated directly from
  `cause.getMessage()`. The logger passes the cause to ERROR logging, attaching its stack trace
  (`src/main/java/com/bct/ngtpa/apiservice/infrastructure/logging/StructuredFailureEventLogger.java:55,67-75,58-64`).
- Typed event names (`LogEvent` enum or constants class in `infrastructure.logging`) were proposed
  but are not implemented on `origin/dev`: `FailureEvent.event` is a `String`, and event labels are
  supplied as strings at producer sites. For example, `ApimInvestmentChoiceCancelAdapter` defines
  `String` constants for event, capability, and operation
  (`src/main/java/com/bct/ngtpa/apiservice/application/dto/observability/FailureEvent.java:6-11`;
  `src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/investmentchoice/ApimInvestmentChoiceCancelAdapter.java:33-35,156-167`).

## 3. Structured output (D7)

- Console output is JSON in non-local profiles; the `local` profile keeps human-readable text.
- Preferred: Spring Boot built-in structured logging (`logging.structured.format.console`). This needs
  Spring Boot 3.4 or later; sub-task 2.1 verifies the version. If the version is older, the fallback
  is `logstash-logback-encoder` (new dependency, open item O-1).
- Existing JSON payload messages stay as the `message` field. No message format rewrite in this
  refactor.

## 4. Request id in MDC

- `requestId` (and, when present, `trustCode` and `userRole` already in the Reactor Context) are
  copied to MDC across Reactor thread hops using Reactor automatic context propagation
  (`Hooks.enableAutomaticContextPropagation()`) with `io.micrometer:context-propagation`
  `ThreadLocalAccessor`s. This is a new direct dependency (open item O-2).
- Effect: every line, including third-party library lines, carries `requestId` in the JSON output and
  the alert appender can read it from MDC.

## 5. Call-site migration (M3)

- A ratchet guard, `ErrorLogCallSiteRatchetArchitectureTest`, scans `src/main/java` for ERROR-level
  logger calls. Allowed owners: `ErrorEventLogger`, `StructuredFailureEventLogger`,
  `RequestLoggingWebFilter` (`http.request.error` only). All other current files are listed in
  `KNOWN_REMAINING`, which may only shrink. It must be empty at the end of M3.
- Each migration sub-task starts with a read-only classification of its call sites into
  **INCIDENT** (-> `FailureEventPort.publish(event, cause)`) or **DIAGNOSTIC** (-> WARN). Nick approves
  the table before the implementation script runs.
- The same sub-tasks replace raw `getMessage()` in log arguments with `LoggingSanitizer` output.
- Silent `onErrorResume` paths that return `SYSTEM_ERROR` without logging gain a FailureEvent, so
  they are no longer invisible.
- Observer downgrades (aspect, filter 5xx, APIM facade, APIM outcome logger) run last, only after a
  read-only audit proves that every swallowing path records a FailureEvent. This order prevents an
  incident from losing its only ERROR line.
- `docs/capabilities/apim-error-logging/architecture-and-design.md` is updated in the same sub-task
  that changes APIM levels.

### Implementation note (verified against origin/dev 1011a9d8e59a18d273fa2304d2e58f58cfc7f9e9)

`FailureEventPort` declares `publish(FailureEvent)` and a default cause-bearing
`publish(FailureEvent, Throwable)` overload (`src/main/java/com/bct/ngtpa/apiservice/application/port/out/FailureEventPort.java:5-10`).
`FailureEvent` carries `event`, `requestId`, `capability`, `operation`, `origin`, `internalCode`,
`publicCode`, `httpStatus`, `retryable`, and `safeDimensions`; the logger serializes these as
`event`, `requestId`, `capability`, `operation`, `failureOrigin`, `internalCode`, `publicCode`,
`httpStatus`, `retryable`, and `dimensions` (`src/main/java/com/bct/ngtpa/apiservice/application/dto/observability/FailureEvent.java:6-20`;
`src/main/java/com/bct/ngtpa/apiservice/infrastructure/logging/StructuredFailureEventLogger.java:42-55`).

Publishers include APIM adapters, application services, and web components
(`src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/investmentchoice/ApimInvestmentChoiceCancelAdapter.java:33-35,156-167`;
`src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/registration/ApimRegistrationLookupAdapter.java:152-163,184-195`;
`src/main/java/com/bct/ngtpa/apiservice/application/usecase/investmentchoice/CancelInvestmentChoiceService.java:83-101`;
`src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/passwordvalidation/PasswordValidationController.java:113-122`).
The APIM investment-choice adapter defines event, capability, and operation as local string constants;
the registration adapter also supplies event, capability, and operation as string values at the call
site (the citations above).
`StructuredFailureEventLogger` implements the port and serializes the payload through
`LoggingSanitizer.toSafeString` before logging (`src/main/java/com/bct/ngtpa/apiservice/infrastructure/logging/StructuredFailureEventLogger.java:18,28-64`).

Structured sanitization is field-name based: map keys are normalized and compared with configured
sensitive-name tokens (`src/main/java/com/bct/ngtpa/apiservice/infrastructure/logging/LoggingSanitizer.java:108-115,165-174`;
`src/main/java/com/bct/ngtpa/apiservice/shared/masking/MaskingProperties.java:70-86`). Neither
`internalCode` nor `sanitizedMessage` matches the configured tokens, so those fields are not masked
by the field-name rule; `sanitizedMessage` receives `cause.getMessage()` directly
(`StructuredFailureEventLogger.java:67-75`).

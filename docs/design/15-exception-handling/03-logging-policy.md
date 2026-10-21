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
  it must call `FailureEventPort.record(event, cause)` with a technical `FailureOrigin`.

Every other observer logs at WARN. This is what makes D1 meaningful: an ERROR means "someone must act".

## 2. FailureEvent changes

- `StructuredFailureEventLogger` level by `FailureOrigin`:
  - WARN: `LOCAL_VALIDATION`, `LOCAL_BUSINESS`, `DOWNSTREAM_BUSINESS`
  - ERROR: `DOWNSTREAM_CONTRACT`, `DOWNSTREAM_INVALID_RESPONSE`, `DOWNSTREAM_TRANSPORT`, `LOCAL_SYSTEM`
- `FailureEventPort` gains `default void record(FailureEvent event, Throwable cause)`. The logger adds
  `exceptionType`, `rootCauseType` and the sanitized message, and attaches the stack trace at ERROR.
  The existing `record(FailureEvent)` stays.
- Event names become typed constants (`LogEvent` enum or constants class in
  `infrastructure.logging`), fulfilling the deferral in `docs/capabilities/apim-error-logging`.

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
  **INCIDENT** (-> `FailureEventPort.record(event, cause)`) or **DIAGNOSTIC** (-> WARN). Nick approves
  the table before the implementation script runs.
- The same sub-tasks replace raw `getMessage()` in log arguments with `LoggingSanitizer` output.
- Silent `onErrorResume` paths that return `SYSTEM_ERROR` without logging gain a FailureEvent, so
  they are no longer invisible.
- Observer downgrades (aspect, filter 5xx, APIM facade, APIM outcome logger) run last, only after a
  read-only audit proves that every swallowing path records a FailureEvent. This order prevents an
  incident from losing its only ERROR line.
- `docs/capabilities/apim-error-logging/architecture-and-design.md` is updated in the same sub-task
  that changes APIM levels.

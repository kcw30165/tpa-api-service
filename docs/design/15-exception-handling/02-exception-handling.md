
## 02 - Central Exception Handling (M1)

### 1. Principles
- One place decides how an exception becomes a public response: the **exception mapping registry**.
- One place renders the response: the **error response renderer**.
- One place writes the boundary log event: the **error event logger**.
- Public behaviour does not change (D5). Every current mapping is pinned by characterization tests
before any code moves.
- Java returns codes only; message text stays with the frontend or downstream (existing rule).

### 2. Components
  
All in com.bct.ngtpa.apiservice.adapter.in.web.error unless stated.
<table>
<tr>
<th>  
Component
</th>
<th>  
Kind
</th>
<th>  
Responsibility
</th>
</tr>
<tr>
<td>  
ErrorDescriptor
</td>
<td>  
record
</td>
<td>  
HttpStatus httpStatus, ApiStatus apiStatus, List\<ErrorEntry\> errors (one or more public
errors; see section 2a), String logEventName, LogLevel logLevel, boolean includeStackTrace,
Map\<String, Object\> extraLogFields (see section 2a)
</td>
</tr>
<tr>
<td>  
ErrorEntry
</td>
<td>  
record
</td>
<td>  
String type, String code, List\<String\> targets, MessagePolicy policy (see section 2a)
</td>
</tr>
<tr>
<td>  
MessagePolicy
</td>
<td>  
enum
</td>
<td>  
RESOLVE, NONE (see section 2a)
</td>
</tr>
<tr>
<td>  
LogLevel
</td>
<td>  
enum
</td>
<td>  
WARN, ERROR (the only levels the boundary uses)
</td>
</tr>
<tr>
<td>  
ExceptionMapping<T extends Throwable>
</td>
<td>  
interface
</td>
<td>  
Class<T> exceptionType(); ErrorDescriptor describe(T exception, ErrorContext context); default
String diagnosticMessage(T exception)
</td>
</tr>
<tr>
<td>  
ErrorContext
</td>
<td>  
record
</td>
<td>  
request method, request path, request id (read from exchange attributes, never resolved, never
blocking)
</td>
</tr>
<tr>
<td>  
ExceptionMappingRegistry
</td>
<td>  
component
</td>
<td>  
Collects all ExceptionMapping beans. Resolves by nearest superclass distance. Fails startup on two mappings for the same type. Falls back to the catch-all mapping.
</td>
</tr>
<tr>
<td>  
ErrorResponseRenderer
</td>
<td>  
bean
</td>
<td>  
Builds ResponseEntity<MutationResponse<Void>> (controller advice) or writes JSON to ServerHttpResponse (WebExceptionHandler) with X-Request-Id.
</td>
</tr>
<tr>
<td>  
ErrorEventLogger
</td>
<td>  
bean
</td>
<td>  
Writes the single http.api.error event at the descriptor level, with sanitized message, exception type, error code, request id, and any ErrorLogDirective.extraFields() (see section 2a); stack trace only when includeStackTrace. Moved verbatim from ApiExceptionHandler.buildMutationErrorResponse.
</td>
</tr>
<tr>
<td>  
ErrorLogDirective
</td>
<td>  
record
</td>
<td>  
HttpStatusCode status, String errorCode, boolean logAtError, boolean includeStackTrace,
Map\<String, Object\> extraFields (5th component added in sub-task 1.7; a 4-argument
constructor defaulting extraFields to an empty map is kept for every pre-1.7 call site)
</td>
</tr>
<tr>
<td>  
PublicMessageResolver
</td>
<td>  
component (bean, sub-task 1.8)
</td>
<td>  
Resolves the public message for one RESOLVE-policy ErrorEntry, given its error code, the
exception and the exchange: the pre-login path check, the Accept-Language locale lookup, the
portal-context account/trust/scheme fields, and the reflective lang lookup on a bound request
target. No logic change from ApiExceptionHandler's current resolvePublicMessage code; only its
location changes.
</td>
</tr>
<tr>
<td>  
*ExceptionMappings
</td>
<td>  
final classes with static factories, one per capability; registered by hand in ExceptionMappingRegistryConfig (not @Component, not auto-collected; see O-11)
</td>
<td>  
Hold the mappings now inside ApiExceptionHandler (security, request validation, APIM, cache, account balance, account gain/loss, fund price, fund comparison, fund fact sheet, registration lookup, personal information, notifications, contribution, portal context, catch-all).
</td>
</tr>
</table>

  
Through sub-task 1.7, ApiExceptionHandler stays the @RestControllerAdvice with its existing
handleXxx methods as one-line delegations (registry -> renderer -> logger), so tests that call
them by name keep compiling. From sub-task 1.8 (A1), ApiExceptionHandler collapses to a single
@ExceptionHandler(Exception.class) method; all per-type routing happens inside
ExceptionMappingRegistry. AuthenticatedPortalContextWebExceptionHandler uses the same registry,
renderer, logger and PublicMessageResolver throughout.

### 2a. ErrorEntry, MessagePolicy and extraLogFields (sub-task 1.7, M1 closure, Option A)

Before sub-task 1.7, ErrorDescriptor carried exactly one errorType/errorCode/targets triple,
always with its message resolved. Three confirmed exceptions never resolve a message
(AccountBalanceRetrievalException, AccountGainLossRetrievalException,
FundFactSheetRetrievalException -- see O-12) and one produces more than one public error
(WebExchangeBindException, one per field error), so none of the four could be expressed by the
single-error shape without either changing their confirmed public contract or leaving them inline
in ApiExceptionHandler indefinitely.

ErrorDescriptor.errors() is now a List\<ErrorEntry\>. Each ErrorEntry carries its own
MessagePolicy:
- RESOLVE: the renderer calls the public message resolver for this entry's error code (every
mapping before this sub-task, and WebExchangeBindException's field/form entries).
- NONE: the message is always null, even outside a pre-login request (AccountBalance,
AccountGainLoss, FundFactSheet).

ErrorDescriptor.single(...) and ErrorDescriptor.singleWithoutMessage(...) are convenience
factories reproducing the exact pre-1.7 single-error constructor shape (one RESOLVE or one NONE
entry respectively), so every mapping that was already correct changes by one line: the
constructor call becomes a call to one of these factories, same arguments, same order.

extraLogFields (Map\<String, Object\>, default empty) lets a mapping contribute extra, value-free
key/value pairs that ErrorEventLogger merges into the single http.api.error event it already
writes. This is how A2 (below) is implemented without ErrorEventLogger needing any
exception-type-specific knowledge.

### 3. Flow
exception
  -> ApiExceptionHandler.handle(ex, exchange)              (controller path, single method from 1.8)
  -> AuthenticatedPortalContextWebExceptionHandler          (filter path)
       -> ErrorContext.from(exchange)
       -> ExceptionMappingRegistry.resolve(ex, ctx) -> ErrorDescriptor
       -> ErrorEventLogger.log(descriptor, ex, ctx)
       -> PublicMessageResolver.resolve(entry, ex, exchange)   (per RESOLVE-policy ErrorEntry)
       -> ErrorResponseRenderer.render(descriptor, messages, ctx)

### 4. Behaviour pinned by characterization tests
  
For every current handler: HTTP status, ApiStatus, error type/code/targets/severity/source,
X-Request-Id header, public message (null or resolved, per O-12), log event name, log level and
whether a stack trace is attached.  
Intentional internal-only changes in M1 (no public HTTP status, ApiStatus, error code/type/
targets/severity/source, message null/resolved-ness, or header change in any case):
- AccountGainLossRetrievalException goes through the renderer and logger (sub-task 1.6), so it
gains the X-Request-Id response header and an http.api.error log event (WARN for business
rejection, ERROR for system failure). Status and body are unchanged.
- AccountBalance normalization failures (A2, sub-task 1.7) log a single http.api.error event
carrying the extra value-free fields (collection, rowIndex, field, reason) instead of a separate
account.balance.normalization.failed event followed by the http.api.error event. HTTP status and
body are unchanged; only the log event count (2 -> 1) and shape change, and the pinned log
assertion in the characterization test is updated to match.

### 5. Guards
- PublicDownstreamStatusIsolationGuardTest stays, unchanged, through M1 closure.
- ExceptionMappingRegistryArchitectureTest: every exception type handled before the refactor has a
mapping; no @ExceptionHandler method in adapter.in.web outside ApiExceptionHandler. From sub-task
1.8, ApiExceptionHandler itself has exactly one @ExceptionHandler method.
- Source-text tests listed in 01-current-state.md section 4 that read ApiExceptionHandler.java are
rewritten to assert behaviour through the registry and renderer in the sub-task that moves the
code (done by 1.4; the 4 remaining inline handlers were moved by 1.7).

### 6. Not changed in M1
- Feature *HttpResponses status tables (Login, Registration, Forgot Password, Captcha).
- RequiredRequestIdWebFilter response.
- Spring default handling of exceptions raised by filters other than the portal-context filter
(open item O-6).
- AuthenticatedPortalContextWebExceptionHandler's X-Request-Id header absence and its own log
format (open item O-10): both are confirmed, pinned by characterization tests, and out of scope
for M1 because fixing either is a public response-header or log-format change.
- The mismatch between the project's codes-only rule and the fact that errorMessageResolver does
resolve label text for most public errors, while AccountBalance/AccountGainLoss/FundFactSheet
return null instead (open item O-12): confirmed by sub-task 1.6b, preserved unchanged by sub-task
1.7, resolution deferred to Nick and FE.

### 7. M1 closure decisions (approved by Nick, 2026-10-22)
- **A1.** ApiExceptionHandler collapses to a single
`@ExceptionHandler(Exception.class) handle(Exception ex, ServerWebExchange exchange)`. All
per-type routing is done by ExceptionMappingRegistry (nearest-superclass resolution); no other
`@ExceptionHandler` method remains on the class. Tests that previously called
`handler.handleXxx(...)` are updated to call `handler.handle(...)` instead; only the call site
changes, never the assertion.
- **A2.** The AccountBalance normalization-failure event is merged into the single
`http.api.error` event: `collection`, `rowIndex`, `field` and `reason` become extra fields on that
one event instead of a separate `account.balance.normalization.failed` log line emitted before it.
HTTP status and response body are unchanged. Implemented in sub-task 1.7 via
`ErrorDescriptor.extraLogFields()` / `ErrorLogDirective.extraFields()` (section 2a).
- Everything else in exception-handling-implementation-prompt.md still stands: no public HTTP
status, ApiStatus, error type/code/targets/severity/source or response-header change anywhere in
M1.

### 8. Target shape (sub-task 1.8)
```java
@RestControllerAdvice
@RequiredArgsConstructor
public class ApiExceptionHandler {
  private final ExceptionMappingRegistry registry;
  private final PublicMessageResolver messages;
  private final ErrorEventLogger logger;
  private final ErrorResponseRenderer renderer;

  @ExceptionHandler(Exception.class)
  public ResponseEntity<MutationResponse<Void>> handle(Exception ex, ServerWebExchange exchange) {
    ErrorContext ctx = ErrorContext.from(exchange);
    ErrorDescriptor d = registry.resolve(ex, ctx);
    logger.log(d, ex, ctx);
    return renderer.render(d, messages.resolve(d, ex, exchange), ctx);
  }
}
```
ErrorEventLogger and ErrorResponseRenderer become Spring beans (no static entry points left).
AuthenticatedPortalContextWebExceptionHandler uses the same four collaborators. Keep the existing
return type of the current handler methods; do not introduce Mono where it is not used today.

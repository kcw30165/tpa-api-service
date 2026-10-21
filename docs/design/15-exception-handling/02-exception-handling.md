# 02 - Central Exception Handling (M1)

## 1. Principles

- One place decides how an exception becomes a public response: the **exception mapping registry**.
- One place renders the response: the **error response renderer**.
- One place writes the boundary log event: the **error event logger**.
- Public behaviour does not change (D5). Every current mapping is pinned by characterization tests
  before any code moves.
- Java returns codes only; message text stays with the frontend or downstream (existing rule).

## 2. Components

All in `com.bct.ngtpa.apiservice.adapter.in.web.error` unless stated.

| Component | Kind | Responsibility |
|---|---|---|
| `ErrorDescriptor` | record | `HttpStatus httpStatus`, `ApiStatus apiStatus`, `List<ApiError> errors`, `LogLevel logLevel`, `boolean includeStackTrace`, `String event` |
| `LogLevel` | enum | `WARN`, `ERROR` (the only levels the boundary uses) |
| `ExceptionMapping<T extends Throwable>` | interface | `Class<T> type()`; `ErrorDescriptor map(T exception, ErrorContext context)` |
| `ErrorContext` | record | request path, request id, resolved locale, optional `PortalAccessContext` (read from exchange attributes, never resolved, never blocking) |
| `ExceptionMappingRegistry` | component | Collects all `ExceptionMapping` beans. Resolves by nearest superclass distance. Fails startup on two mappings for the same type. Falls back to the catch-all mapping. |
| `ErrorResponseRenderer` | component | Builds `ResponseEntity<MutationResponse<Void>>` (controller advice) or writes JSON to `ServerHttpResponse` (WebExceptionHandler) with `X-Request-Id`. |
| `ErrorEventLogger` | component | Writes the single `http.api.error` event at the descriptor level, with sanitized message, exception type, error code, request id; stack trace only when `includeStackTrace`. Moved verbatim from `ApiExceptionHandler.buildMutationErrorResponse`. |
| `*ExceptionMappings` | `@Component` classes, one per capability | Hold the mappings now inside `ApiExceptionHandler` (security, request validation, APIM, cache, account balance, account gain/loss, fund price, fund comparison, fund fact sheet, registration lookup, personal information, notifications, contribution, portal context, catch-all). |

`ApiExceptionHandler` stays the `@RestControllerAdvice`. Its existing `handleXxx` methods remain as
one-line delegations (`registry -> renderer -> logger`) so the current tests that call them by name
keep compiling. `AuthenticatedPortalContextWebExceptionHandler` uses the same registry, renderer and
logger.

## 3. Flow

```
exception
  -> ApiExceptionHandler.handleXxx(ex, exchange)        (controller path)
  -> AuthenticatedPortalContextWebExceptionHandler      (filter path)
       -> ErrorContext.from(exchange)
       -> ExceptionMappingRegistry.resolve(ex).map(ex, ctx) -> ErrorDescriptor
       -> ErrorEventLogger.log(descriptor, ex, ctx)
       -> ErrorResponseRenderer.render(descriptor, ctx)
```

## 4. Behaviour pinned by characterization tests

For every current handler: HTTP status, `ApiStatus`, error `type`/`code`/`targets`/`severity`/`source`,
`X-Request-Id` header, log event name, log level and whether a stack trace is attached.

Exactly one intentional change in M1, internal only:

- `AccountGainLossRetrievalException` goes through the renderer and logger, so it gains the
  `X-Request-Id` response header and an `http.api.error` log event (WARN for business rejection,
  ERROR for system failure). Status and body are unchanged.

## 5. Guards

- `PublicDownstreamStatusIsolationGuardTest` stays.
- New `ExceptionMappingRegistryArchitectureTest`: every exception type handled before the refactor
  has a mapping; no `@ExceptionHandler` method in `adapter.in.web` outside `ApiExceptionHandler`.
- Source-text tests listed in `01-current-state.md` section 4 that read `ApiExceptionHandler.java`
  are rewritten to assert behaviour through the registry and renderer in the sub-task that moves the
  code.

## 6. Not changed in M1

- Feature `*HttpResponses` status tables (Login, Registration, Forgot Password, Captcha).
- `RequiredRequestIdWebFilter` response.
- Spring default handling of exceptions raised by filters other than the portal-context filter
  (open item O-6).

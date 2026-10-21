# 06 - Open Items, Risks, Follow-ups

## Resolution rule for the implementer

- **Fact questions** (what the code, build or runtime does today) may be resolved by the implementer
  from evidence. Record the answer, the evidence file and the commit in this table.
- **Stop and escalate to Nick** before any change that:
  - adds or upgrades a dependency,
  - changes public behaviour (HTTP status, `ApiStatus`, error code/type/targets, response headers),
  - touches security, privacy, audit or the content/transport of alert emails.
- Never close a row owned by Nick, operations, FE or the security authority on their behalf.

## Items

| # | Item | Resolver | Status / resolution path |
|---|---|---|---|
| O-1 | Spring Boot version supports built-in structured logging (3.4+)? Otherwise `logstash-logback-encoder` is needed. | Implementer (fact) -> Nick (dependency) | Open. Sub-task 2.1 records the version and dependency tree; a new dependency waits for Nick's approval. |
| O-2 | `io.micrometer:context-propagation` as a direct dependency for MDC propagation. | Implementer (fact) -> Nick (dependency) | Open. Sub-task 2.1 checks whether it is already on the classpath; declaring it directly waits for Nick's approval. |
| O-3 | Group mailbox to replace `deng.nick@bcthk.com`. | Nick / operations | Open. Configuration change only, no code. |
| O-4 | Alert emails travel over the plain SMTP relay. Content is sanitized, but stack traces and logger names are internal details. | Security authority | Open. Sign-off required before alerting is enabled in PROD. |
| O-5 | Unify public HTTP status for `SYSTEM_ERROR` (500/502/503) and the `FORM` error type on some system 503s. | Nick + FE | Deferred (D5). Separate follow-up after this refactor. |
| O-6 | Exceptions raised in web filters other than the portal-context filter are handled by Spring's default error handler (suspected; may also log at ERROR). | Implementer (fact) -> Nick (change) | **Closed (fact) 2026-10-21:** Confirmed by sub-task 1.0 (script 005 evidence, commit to follow). Only one `WebExceptionHandler` bean exists (`AuthenticatedPortalContextWebExceptionHandler`); it handles only `AuthenticatedPortalContextResolutionException` and passes every other throwable through via `Mono.error(failure)`. No bean overrides Spring's default `ErrorWebExceptionHandler`/`DefaultErrorAttributes`/`ErrorWebFluxAutoConfiguration` anywhere in src/main/java (confirmed empty). Any exception raised in `RequestLoggingWebFilter` or `PortalAccessContextWebFilter` other than the explicit `AuthenticatedPortalContextResolutionException` case reaches Spring Boot's default WebFlux error handler. `RequestLoggingWebFilter.doOnError` independently logs an `http.request.error` ERROR event (sanitized message, no stack trace) as an observer before the exception propagates; it does not swallow it. `RequiredRequestIdWebFilter` never throws; it writes its own 400 body directly. Whether to change this behaviour remains Nick's decision (not closed). |
| O-7 | Failures before the Spring context creates beans (for example Config Service bootstrap) are not alerted. | Nick | **Closed 2026-10-21: accepted.** Visible as pod restart. |
| O-8 | Per-pod dedup: up to one email per pod per window for the same incident. | Nick | **Closed 2026-10-21: accepted** (follows D3). |
| O-9 | Characterization and ratchet tests replace the source-text tests; the near-duplicate Registration logging tests are merged. | Implementer | Open. Done inside 1.4, 2.5 and the M3 sub-tasks. |

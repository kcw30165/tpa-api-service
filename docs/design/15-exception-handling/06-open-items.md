
## 06 - Open Items, Risks, Follow-ups

### Resolution rule for the implementer
- **Fact questions** (what the code, build or runtime does today) may be resolved by the implementer
from evidence. Record the answer, the evidence file and the commit in this table.
- **Stop and escalate to Nick** before any change that:
- adds or upgrades a dependency,
- changes public behaviour (HTTP status, ApiStatus, error code/type/targets, response headers),
- touches security, privacy, audit or the content/transport of alert emails.
- Never close a row owned by Nick, operations, FE or the security authority on their behalf.

### Items

<table>
<tr>
<th>  
#
</th>
<th>  
Item
</th>
<th>  
Resolver
</th>
<th>  
Status / resolution path
</th>
</tr>
<tr>
<td>  
O-1
</td>
<td>  
Spring Boot version supports built-in structured logging (3.4+)? Otherwise logstash-logback-encoder is needed.
</td>
<td>  
Implementer (fact) -> Nick (dependency)
</td>
<td>  
Resolved: Spring Boot built-in structured logging, ECS format, non-local profiles only (9198bc68).
</td>
</tr>
<tr>
<td>  
O-2
</td>
<td>  
io.micrometer:context-propagation as a direct dependency for MDC propagation.
</td>
<td>  
Implementer (fact) -> Nick (dependency)
</td>
<td>  
Resolved: io.micrometer:context-propagation declared directly, Boot-managed version (1dff744e). Hooks.enableAutomaticContextPropagation() is called explicitly in ReactorMdcPropagationConfig rather than via a Spring Boot auto-configuration property, because the three MDC bridges (requestId, trustCode, userRole) are each a custom ThreadLocalAccessor registered by code via ContextRegistry; no property can express that per-field mapping, so the same @PostConstruct method that must already exist to register the accessors also enables the hook.
</td>
</tr>
<tr>
<td>  
O-3
</td>
<td>  
Group mailbox to replace deng.nick@bcthk.com.
</td>
<td>  
Nick / operations
</td>
<td>  
Open. Configuration change only, no code.
</td>
</tr>
<tr>
<td>  
O-4
</td>
<td>  
Alert emails travel over the plain SMTP relay. Content is sanitized, but stack traces and logger names are internal details.
</td>
<td>  
Security authority
</td>
<td>  
Open. Sign-off required before alerting is enabled in PROD.
</td>
</tr>
<tr>
<td>  
O-5
</td>
<td>  
Unify public HTTP status for SYSTEM_ERROR (500/502/503) and the FORM error type on some system 503s.
</td>
<td>  
Nick + FE
</td>
<td>  
Deferred (D5). Separate follow-up after this refactor.
</td>
</tr>
<tr>
<td>  
O-6
</td>
<td>  
Exceptions raised in web filters other than the portal-context filter are handled by Spring's default error handler (suspected; may also log at ERROR).
</td>
<td>  
Implementer (fact) -> Nick (change)
</td>
<td>  
**Open (fact confirmed; change decision pending Nick).** Confirmed by sub-task 1.0 (script 005
evidence, commit a13cad00): only one WebExceptionHandler bean exists
(AuthenticatedPortalContextWebExceptionHandler); it handles only
AuthenticatedPortalContextResolutionException and passes every other throwable through via
Mono.error(failure). No bean overrides Spring's default
ErrorWebExceptionHandler/DefaultErrorAttributes/ErrorWebFluxAutoConfiguration anywhere in
src/main/java (confirmed empty). Any exception raised in RequestLoggingWebFilter or
PortalAccessContextWebFilter other than the explicit AuthenticatedPortalContextResolutionException
case reaches Spring Boot's default WebFlux error handler. RequestLoggingWebFilter.doOnError
independently logs an http.request.error ERROR event (sanitized message, no stack trace) as an
observer before the exception propagates; it does not swallow it. RequiredRequestIdWebFilter never
throws; it writes its own 400 body directly. The fact is closed; whether to change this behaviour
is Nick's decision and stays open.
</td>
</tr>
<tr>
<td>  
O-7
</td>
<td>  
Failures before the Spring context creates beans (for example Config Service bootstrap) are not alerted.
</td>
<td>  
Nick
</td>
<td>  
**Closed 2026-10-21: accepted.** Visible as pod restart.
</td>
</tr>
<tr>
<td>  
O-8
</td>
<td>  
Per-pod dedup: up to one email per pod per window for the same incident.
</td>
<td>  
Nick
</td>
<td>  
**Closed 2026-10-21: accepted** (follows D3).
</td>
</tr>
<tr>
<td>  
O-9
</td>
<td>  
Characterization and ratchet tests replace the source-text tests; the near-duplicate Registration logging tests are merged.
</td>
<td>  
Implementer
</td>
<td>  
Open. Done inside 1.4, 2.5 and the M3 sub-tasks.
</td>
</tr>
<tr>
<td>  
O-10
</td>
<td>  
AuthenticatedPortalContextWebExceptionHandler (filter path) does not set the X-Request-Id response
header and logs its own plain-text format instead of the shared http.api.error JSON event.
</td>
<td>  
Nick
</td>
<td>  
Open. Confirmed by the sub-task 1.0/1.1/1.5 characterization tests. Fixing either part is a public
response-header or log-format change, so it is not changed in M1; Nick decides whether and when to
align it with every other handler.
</td>
</tr>
<tr>
<td>  
O-11
</td>
<td>  
Exception-to-mapping registration is manual: every *ExceptionMappings bean must be listed by hand
in ExceptionMappingRegistryConfig, so adding a new handled exception type requires an edit there
rather than being auto-discovered.
</td>
<td>  
Nick
</td>
<td>  
Accepted unless Nick decides otherwise.
</td>
</tr>
<tr>
<td>  
O-12
</td>
<td>  
Java resolves message text into errors[].message for most errors (via ApiExceptionHandler's
resolvePublicMessage / PublicMessageResolver from sub-task 1.8), which conflicts with the
project's codes-only rule (FE/downstream owns label text). Some features
(AccountBalanceRetrievalException, AccountGainLossRetrievalException,
FundFactSheetRetrievalException) already return errors[].message = null instead, confirmed by the
sub-task 1.6b characterization pack and preserved unchanged by sub-task 1.7's
MessagePolicy.NONE.
</td>
<td>  
Nick + FE
</td>
<td>  
Open. Not changed in M1 (D5): the pre-existing inconsistency between the two groups of errors is
preserved exactly as it exists today, not fixed or extended to every error type. Resolving the
inconsistency (either making every error null, or making the three null ones resolve) is a
separate, broader decision for Nick and FE.
</td>
</tr>


<tr>
<td>
O-13
</td>
<td>
The JaCoCo report has no rows for anonymous classes or *Config classes, so coverage of the
*ExceptionMappings logic is not measured. Evidence: script 072, 10-coverage-m1-classes.txt.
</td>
<td>
Nick
</td>
<td>
Open. Confirmed: pom.xml's JaCoCo exclude block (<exclude>**/*Config.class</exclude>,
<exclude>**/*Config$*.class</exclude>, <exclude>**/*Properties.class</exclude>,
<exclude>**/*Properties$*.class</exclude>) also excludes ReactorMdcPropagationConfig and
ExceptionMappingRegistryConfig from the coverage report, for the same reason -- neither
class's instructions or branches are measured.
</td>
</tr>
<tr>
<td>
O-14
</td>
<td>
(a) ExceptionMappingRegistry superclass walk: confirm whether "candidate != null" can be false;
delete the guard or test it, target 95% branch. (b) Fix the stale Javadoc in
FundComparisonRequestInvalidException and FundPriceRequestInvalidException that still describes
per-type @ExceptionHandler methods in ApiExceptionHandler.
</td>
<td>
Implementer (first commit of M2)
</td>
<td>
**Closed.** (a) Unreachable null guard removed in ExceptionMappingRegistry's superclass walk
(6fddd4e0). (b) Stale Javadoc in FundComparisonRequestInvalidException and
FundPriceRequestInvalidException corrected to describe the real current
ExceptionMappingRegistry/*ExceptionMappings mapping path instead of the removed per-type
@ExceptionHandler methods (2d29948a).
</td>
</tr>


<tr>
<td>
O-15
</td>
<td>
StructuredFailureEventLogger uses Set.of(...).contains(origin); a null origin throws
NullPointerException from the logger (Set.of(...).contains(null) always throws, per the JDK
contract for the immutable collections returned by Set.of). Logging must never throw, so a null
origin should be treated as ERROR instead. Separately, LoggingSanitizer.toSafeString's masking
of the sanitizedMessage field is unverified against the real sanitizer: the existing
StructuredFailureEventLoggerTest mocks LoggingSanitizer entirely, so no test exercises whether a
sensitive token embedded in cause.getMessage() (the raw, unsanitized value placed under the
sanitizedMessage key) is actually masked once the whole event map is passed through the real
toSafeString. Both need a test against the real LoggingSanitizer, not a mock.
</td>
<td>
Implementer (first production commit of M2b)
</td>
<td>
**Closed.** StructuredFailureEventLogger.publish now null-guards failure.origin() before
Set.of(...).contains(...), so a null origin logs at ERROR instead of throwing. A
characterization test confirms this. A second test against the real LoggingSanitizer (not a
mock) confirms toSafeString does not mask a sensitive token embedded in cause.getMessage()
under the sanitizedMessage key -- documented as a known, pre-existing gap, not fixed in this
commit (6b0d6962).
</td>
</tr>
</table>

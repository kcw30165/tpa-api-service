
## 05 - Milestones and Sub-tasks
  
Gates follow the project workflow: sub-task gates (fail-first where behaviour changes, spotless,
compile, test-compile, targeted tests) and milestone gates (spotless check, full mvn test,
coverage, smoke test, SonarQube). Brand-new types use compile-ready stubs so fail-first tests fail on
assertions, not on compilation. Pin-only tests (no behaviour change) have no fail-first step.

### M1 - Central exception handling (no public behaviour change)

**Status: merged to `dev` via merge commit `62029df45a85b31657be220a591c5adfe4eca51b`.**

<table>
<tr>
<th>  
#
</th>
<th>  
Commit type
</th>
<th>  
Work
</th>
<th>  
Main tests
</th>
</tr>
<tr>
<td>  
1.0
</td>
<td>  
-
</td>
<td>  
Read-only capture of the full ApiExceptionHandler and AuthenticatedPortalContextWebExceptionHandler and their tests
</td>
<td>  
-
</td>
</tr>
<tr>
<td>  
1.1
</td>
<td>  
test
</td>
<td>  
Characterization pack for every current handler (status, body, header, log event, level, stack trace)
</td>
<td>  
ApiExceptionHandlerCharacterizationTest, AuthenticatedPortalContextWebExceptionHandlerCharacterizationTest
</td>
</tr>
<tr>
<td>  
1.2
</td>
<td>  
feat
</td>
<td>  
ErrorDescriptor, LogLevel, ErrorContext, ExceptionMapping, ExceptionMappingRegistry
</td>
<td>  
ExceptionMappingRegistryTest
</td>
</tr>
<tr>
<td>  
1.3
</td>
<td>  
refactor
</td>
<td>  
ErrorResponseRenderer, ErrorEventLogger (logging moved verbatim)
</td>
<td>  
ErrorResponseRendererTest, ErrorEventLoggerTest + characterization
</td>
</tr>
<tr>
<td>  
1.4
</td>
<td>  
refactor
</td>
<td>  
Move all mappings into *ExceptionMappings; ApiExceptionHandler delegates; rewrite source-text tests that read it
</td>
<td>  
characterization + ExceptionMappingRegistryArchitectureTest
</td>
</tr>
<tr>
<td>  
1.5
</td>
<td>  
refactor
</td>
<td>  
AuthenticatedPortalContextWebExceptionHandler on registry/renderer/logger
</td>
<td>  
characterization
</td>
</tr>
<tr>
<td>  
1.6
</td>
<td>  
fix
</td>
<td>  
Account gain/loss through renderer/logger (adds X-Request-Id + log)
</td>
<td>  
fail-first AccountGainLossExceptionMappingsTest
</td>
</tr>
<tr>
<td>  
1.7a
</td>
<td>  
docs
</td>
<td>  
M1 closure design update: record A1/A2, the collapsed-advice target shape, PublicMessageResolver,
ErrorDescriptor.errors as a list, fix the O-6 status, add O-10 and O-11, set README status to
Approved, close 04-error-alerting.md O-7 cross-reference
</td>
<td>  
-
</td>
</tr>
<tr>
<td>  
1.7
</td>
<td>  
refactor
</td>
<td>  
Move the 4 remaining inline handlers (AccountBalance with A2 event fields, AccountGainLoss,
WebExchangeBind, FundFactSheet) into exception mappings; handleXxx methods become one-line
delegations (still named at this point)
</td>
<td>  
characterization (only the A2 log assertion may change) + AccountGainLossExceptionMappingsTest
</td>
</tr>
<tr>
<td>  
1.8
</td>
<td>  
refactor
</td>
<td>  
Extract PublicMessageResolver (no logic change); ErrorEventLogger and ErrorResponseRenderer become
Spring beans; collapse ApiExceptionHandler to the single handle(...) method (A1);
AuthenticatedPortalContextWebExceptionHandler uses the same beans; remove sub-task history notes
from production Javadoc
</td>
<td>  
characterization (call sites and constructor wiring only) + ExceptionMappingRegistryArchitectureTest
</td>
</tr>
<tr>
<td>  
1.9
</td>
<td>  
test
</td>
<td>  
Coverage closure for ApiExceptionHandler, PublicMessageResolver, the registry, renderer, logger and
every *ExceptionMappings class against the M1 coverage targets; every remaining uncovered branch
justified in one line
</td>
<td>  
-
</td>
</tr>
<tr>
<td>  
1.10
</td>
<td>  
-
</td>
<td>  
M1 milestone gate: mvn spotless:check, full mvn test, coverage review; then ask Nick to run the
startup smoke test and SonarQube
</td>
<td>  
-
</td>
</tr>


<tr>
<td>
1.11
</td>
<td>
refactor/test/docs
</td>
<td>
M1 closure cleanup (6 items, approved by Nick 2026-10-22): 1) remove ErrorEventLogger's redundant
static log method (9d4cbdbe); 2) move ApiError assembly into ErrorResponseRenderer with a
context-&gt;resolve-&gt;messages-&gt;log-&gt;render pipeline in ApiExceptionHandler (80fee328); 3)
AuthenticatedPortalContextExceptionMappings uses singleWithoutMessage instead of single, since the
handler never resolves a public message for that entry (0ca6e56b); 4) remove stale
sub-task/M1-closure Javadoc history notes across all ~21 files in scope (4306a03a; this cleanup
was incomplete -- the remaining stale references were removed in sub-task 1.12, item 1); 5) close
coverage gaps to target or documented justification for ApimExceptionMappings,
RequestValidationExceptionMappings, WebExchangeBindExceptionMappings, ErrorLogDirective and
CatchAllExceptionMapping (107e3ed7); 6) add a Spring ApplicationContextRunner wiring test proving
ApiExceptionHandler, AuthenticatedPortalContextWebExceptionHandler, ExceptionMappingRegistry,
PublicMessageResolver, ErrorEventLogger and ErrorResponseRenderer all load as real beans
(d037ca10).
</td>
<td>
ErrorEventLoggerTest, ErrorResponseRendererTest, ExceptionMappingsTest,
ExceptionMappingRegistryTest, ExceptionHandlingWiringArchitectureTest
</td>
</tr>


<tr>
<td>
1.12
</td>
<td>
docs/test/refactor
</td>
<td>
2026-10-23 review follow-up (approved by Nick 2026-10-23), 5 items reviewed, 4 produced commits
(item 5 was a coverage report only, no commit): 1) remove the remaining stale
sub-task/M1-closure/Option-A/1.6b/pre-sub-task-1.7 Javadoc references across the files the review
flagged, completing item 4 of sub-task 1.11 (19dae4fe); 2) fix
AuthenticatedPortalContextExceptionMappings' false Javadoc claim (it had described
ErrorDescriptor#single/RESOLVE and ApiExceptionHandler.delegate(...), neither true) to describe
its actual singleWithoutMessage/NONE behaviour (882441cc); 3) add the blank-reason
diagnosticMessage test for RequestValidationExceptionMappings and remove its deferred-work
Javadoc paragraph (7f105bbb); 4) delete ApimExceptionMappings' two provably-unreachable
null/blank guards instead of testing them (c8283e49); 6) correct 05-milestones.md and
02-exception-handling.md: document sub-task 1.11, state ErrorResponseRenderer/ErrorEventLogger
are beans (not "static helper"), and describe *ExceptionMappings as final classes with static
factories registered by hand, not @Component (b43fbb2b).
</td>
<td>
ExceptionMappingsTest, ExceptionMappingRegistryTest
</td>
</tr>
</table>

  
Sub-tasks 1.7a-1.10 (M1 closure) are defined in exception-handling-m1-closure-addendum.md,
approved by Nick on 2026-10-22, following the 2026-10-22 review at e65b87eb that found
ApiExceptionHandler still carried inline response/log logic for 4 exception types and a
single-error ErrorDescriptor shape.

### M2 - Logging foundation

**Status: merged to `dev` via merge commit `fb765111c614edd2eb0232e5360a88ce090159b2`.**

<table>
<tr>
<th>  
#
</th>
<th>  
Commit type
</th>
<th>  
Work
</th>
<th>  
Main tests
</th>
</tr>
<tr>
<td>  
2.1
</td>
<td>  
-
</td>
<td>  
Read-only: Spring Boot version, mvn dependency:tree for logging and context-propagation, current console pattern
</td>
<td>  
-
</td>
</tr>
<tr>
<td>  
2.2
</td>
<td>  
feat
</td>
<td>  
Structured JSON console output for non-local profiles (9198bc68)
</td>
<td>  
config test + smoke log check
</td>
</tr>
<tr>
<td>  
2.3
</td>
<td>  
feat
</td>
<td>  
Request id (and trust code, user role) in MDC across Reactor threads (1dff744e)
</td>
<td>  
RequestContextMdcPropagationTest
</td>
</tr>
<tr>
<td>  
2.4
</td>
<td>  
feat
</td>
<td>  
StructuredFailureEventLogger level by FailureOrigin; record(event, cause) (75c8c37c)
</td>
<td>  
fail-first StructuredFailureEventLoggerTest
</td>
</tr>
<tr>
<td>  
2.5
</td>
<td>  
test
</td>
<td>  
ErrorLogCallSiteRatchetArchitectureTest with current KNOWN_REMAINING (original scalar form, 3f845e29)
</td>
<td>  
itself
</td>
</tr>


<tr>
<td>
2.6
</td>
<td>
test
</td>
<td>
Make the error log ratchet match 03-logging-policy.md section 5: per-file KNOWN_REMAINING map
(path -> exact ERROR-call count), excluding ErrorEventLogger and StructuredFailureEventLogger
entirely, RequestLoggingWebFilter allowed exactly one ERROR call. Fails on an unlisted file, a
risen count, or a fallen count left uncorrected (beaaf227).
</td>
<td>
ErrorLogCallSiteRatchetArchitectureTest
</td>
</tr>
</table>


### M2b - Downstream (APIM) error classification (no public behaviour change)

Added 2026-10-26, approved by Nick. M2b runs on its own branch from `origin/dev` after the M2 MR is
merged, and is merged before M3 starts. M3 chooses WARN or ERROR from `FailureOrigin`; M2b first makes
every APIM failure carry one consistent classification, so M3 does not decide feature by feature and a
downstream business rejection can never raise an ERROR alert.

Rules:

- No public HTTP status, `ApiStatus`, error type/code/targets, message policy or response header change.
- `ApiStatus.DOWNSTREAM_REJECTED` and `ApiStatus.DOWNSTREAM_ERROR` never appear in a public response.
- Java returns codes only; downstream message text is never copied into a public response.
- Feature code tables (which downstream `err-message` code becomes which public code) stay owned by the
  feature. Only the classification and its shape are shared.
- Result-based workflows (Login, Registration, Forgot Password, Username Recovery) keep their results
  and idempotency replay snapshots (D6).
- Log levels do not change in M2b. It only attaches the classification; level changes happen in M3.

| # | Commit type | Work | Main tests |
|---|---|---|---|
| 2b.0 | - | Read-only inventory: generic APIM handling (`ApimException`, `ApimExceptionMappings`, the APIM facade and execution layer, the exchange outcome logger, the crypto failure classifier) and every place a feature turns an APIM `err-message` or response into a domain exception or result. For each: downstream condition, domain exception or result, public status, `ApiStatus`, code, message policy, log event and level, and `FailureOrigin` if any | - |
| 2b.1 | docs | `07-apim-error-classification.md`: categories and their `FailureOrigin` (business rejection -> `DOWNSTREAM_BUSINESS`; envelope or contract violation -> `DOWNSTREAM_CONTRACT`; unparsable or undecryptable response -> `DOWNSTREAM_INVALID_RESPONSE`; timeout, connection failure or HTTP 5xx -> `DOWNSTREAM_TRANSPORT`), the 2b.0 inventory, the shared versus feature-owned split, and open questions. Nick approves before 2b.2 | - |
| 2b.2 | test | Characterization pack for every APIM outcome found in 2b.0 (public status, body, message, log event, level) on unchanged production code | APIM outcome characterization tests |
| 2b.3 | feat | Shared, pure classification type and classifier (names fixed in 2b.1), not yet used | classifier unit tests, branch >= 95% |
| 2b.4..2b.n | refactor | One commit per feature: attach the shared classification to its APIM failures; feature code tables unchanged | characterization unchanged |
| 2b.gate | - | Milestone gate: `mvn spotless:check`, full `mvn test`, per-class instruction and branch coverage including anonymous classes; then Nick runs the startup smoke test and SonarQube | - |

### M3 - Call-site migration (one ERROR per incident)

**Depends on M2b:** M3 starts only after M2b is merged; its INCIDENT/DIAGNOSTIC classification of APIM
call sites uses the M2b `FailureOrigin` classification.
  
Each sub-task: read-only classification table first (INCIDENT / DIAGNOSTIC), Nick approves, then
implementation, and KNOWN_REMAINING shrinks.
<table>
<tr>
<th>  
#
</th>
<th>  
Commit type
</th>
<th>  
Scope
</th>
</tr>
<tr>
<td>  
3.1
</td>
<td>  
refactor
</td>
<td>  
Login (controller, OTP verify/resend services, session establishment)
</td>
</tr>
<tr>
<td>  
3.2
</td>
<td>  
refactor
</td>
<td>  
Forgot Password (start, details/OTP, resend, username lookup mapper/adapter)
</td>
</tr>
<tr>
<td>  
3.3
</td>
<td>  
refactor
</td>
<td>  
Registration (start mapper, username check service)
</td>
</tr>
<tr>
<td>  
3.4
</td>
<td>  
refactor
</td>
<td>  
Authenticated session lifecycle and validation services
</td>
</tr>
<tr>
<td>  
3.5
</td>
<td>  
refactor
</td>
<td>  
Personal Information mappers
</td>
</tr>
<tr>
<td>  
3.6
</td>
<td>  
refactor
</td>
<td>  
Fund Fact Sheet, Fund Comparison, Config Service adapter, APIM facade profile-resolution
</td>
</tr>
<tr>
<td>  
3.7
</td>
<td>  
-
</td>
<td>  
Read-only audit: every swallowing path that returns a system failure records a FailureEvent
</td>
</tr>
<tr>
<td>  
3.8
</td>
<td>  
refactor
</td>
<td>  
Observer downgrades to WARN (aspect, filter 5xx, APIM facade, APIM outcome logger); update APIM error-logging docs; KNOWN_REMAINING empty
</td>
</tr>
</table>


### M4 - Error alerting

<table>
<tr>
<th>  
#
</th>
<th>  
Commit type
</th>
<th>  
Work
</th>
<th>  
Main tests
</th>
</tr>
<tr>
<td>  
4.1
</td>
<td>  
feat
</td>
<td>  
ErrorAlertProperties + validation
</td>
<td>  
ErrorAlertPropertiesTest
</td>
</tr>
<tr>
<td>  
4.2
</td>
<td>  
feat
</td>
<td>  
ErrorAlertFingerprint, ErrorAlertThrottle (both deduplicate modes)
</td>
<td>  
ErrorAlertFingerprintTest, ErrorAlertThrottleTest
</td>
</tr>
<tr>
<td>  
4.3
</td>
<td>  
feat
</td>
<td>  
ErrorAlert, ErrorAlertAppender (level filter, exclusions, loop guard, queue)
</td>
<td>  
ErrorAlertAppenderTest
</td>
</tr>
<tr>
<td>  
4.4
</td>
<td>  
feat
</td>
<td>  
ErrorAlertDispatcher, ErrorAlertNotificationPort, EmailErrorAlertAdapter, email formatter
</td>
<td>  
ErrorAlertDispatcherTest, EmailErrorAlertAdapterTest
</td>
</tr>
<tr>
<td>  
4.5
</td>
<td>  
feat
</td>
<td>  
Installer/lifecycle wiring, application.yml error-alert.*
</td>
<td>  
ErrorAlertWiringTest (enabled/disabled contexts, fake SmtpClient)
</td>
</tr>
<tr>
<td>  
4.6
</td>
<td>  
docs
</td>
<td>  
Runbook: enable, change recipients, tune dedup, DEV verification
</td>
<td>  
-
</td>
</tr>
</table>


### Coverage
  
Project targets apply: instruction >= 95%; branch >= 95% for records, validators and pure functions
(ErrorDescriptor, registry resolution, fingerprint, throttle, properties validation); 85-90% for
handlers, appender, dispatcher and adapters, each uncovered branch justified in one line.

### Push and MR

Use one new branch and one MR per milestone. Open that milestone's MR only after its gate passes; merge it with a merge commit (no squash), then start the next milestone from the updated `origin/dev`. Jenkins/runtime
values for PROD/DR (ERROR_ALERT_ENABLED=true, recipients) are a separate pipeline change.

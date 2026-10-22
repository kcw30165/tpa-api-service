
## 05 - Milestones and Sub-tasks
  
Gates follow the project workflow: sub-task gates (fail-first where behaviour changes, spotless,
compile, test-compile, targeted tests) and milestone gates (spotless check, full mvn test,
coverage, smoke test, SonarQube). Brand-new types use compile-ready stubs so fail-first tests fail on
assertions, not on compilation. Pin-only tests (no behaviour change) have no fail-first step.

### M1 - Central exception handling (no public behaviour change)

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
</table>

  
Sub-tasks 1.7a-1.10 (M1 closure) are defined in exception-handling-m1-closure-addendum.md,
approved by Nick on 2026-10-22, following the 2026-10-22 review at e65b87eb that found
ApiExceptionHandler still carried inline response/log logic for 4 exception types and a
single-error ErrorDescriptor shape.

### M2 - Logging foundation

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
Structured JSON console output for non-local profiles
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
Request id (and trust code, user role) in MDC across Reactor threads
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
StructuredFailureEventLogger level by FailureOrigin; record(event, cause)
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
ErrorLogCallSiteRatchetArchitectureTest with current KNOWN_REMAINING
</td>
<td>  
itself
</td>
</tr>
</table>


### M3 - Call-site migration (one ERROR per incident)
  
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
  
After M4 milestone gates pass. Jenkins/runtime values for PROD/DR (ERROR_ALERT_ENABLED=true,
recipients) are a separate pipeline change.

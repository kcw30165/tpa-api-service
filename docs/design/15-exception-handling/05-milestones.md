
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


### M2b - Downstream (APIM) error classification (approved reduced scope)

Added 2026-10-26, approved by Nick. The reduced-scope completion addendum below supersedes the
original full-integration objective and the original 2b.4..2b.n schedule. M2b delivers E1, E2 and the
shared pure technical classifier; it does not attach classification to every feature APIM failure.
M3 starts after M2b merges and combines the deferred feature integrations with incident-log migration.
This document does not assert that the M2b merge has already occurred.

Rules:

- No public HTTP status, `ApiStatus`, error type/code/targets, message policy or response header change.
- `ApiStatus.DOWNSTREAM_REJECTED` and `ApiStatus.DOWNSTREAM_ERROR` never appear in a public response.
- Java returns codes only; downstream message text is never copied into a public response.
- Feature code tables (which downstream `err-message` code becomes which public code) stay owned by the
  feature. Only the classification and its shape are shared.
- Result-based workflows (Login, Registration, Forgot Password, Username Recovery) keep their results
  and idempotency replay snapshots (D6).
- Except for approved E1, log levels do not change in M2b. Remaining level changes happen in M3.

| # | Commit type | Work | Main tests |
|---|---|---|---|
| 2b.0 | - | Read-only inventory: generic APIM handling (`ApimException`, `ApimExceptionMappings`, the APIM facade and execution layer, the exchange outcome logger, the crypto failure classifier) and every place a feature turns an APIM `err-message` or response into a domain exception or result. For each: downstream condition, domain exception or result, public status, `ApiStatus`, code, message policy, log event and level, and `FailureOrigin` if any | - |
| 2b.1 | docs | `07-apim-error-classification.md`: categories and their `FailureOrigin` (business rejection -> `DOWNSTREAM_BUSINESS`; envelope or contract violation -> `DOWNSTREAM_CONTRACT`; unparsable or undecryptable response -> `DOWNSTREAM_INVALID_RESPONSE`; timeout, connection failure or HTTP 5xx -> `DOWNSTREAM_TRANSPORT`), the 2b.0 inventory, the shared versus feature-owned split, and open questions. Nick approves before 2b.2 | - |
| 2b.2 | test | Characterization pack for every APIM outcome found in 2b.0 (public status, body, message, log event, level) on unchanged production code | APIM outcome characterization tests |
| 2b.3 | feat | Shared, pure classification type and classifier (names fixed in 2b.1), not yet used | classifier unit tests, branch >= 95% |
| 2b.4..2b.n | deferred | Not delivered by reduced-scope M2b. Integrate each feature during M3 using the deferred-work schedule below; feature code tables remain feature-owned | existing characterization plus targeted tests for changed paths |
| 2b.gate | - | Milestone gate: `mvn spotless:check`, full `mvn test`, per-class instruction and branch coverage including anonymous classes; then Nick runs the startup smoke test and SonarQube | - |


**Sub-task 2b.1 addendum (docs(exception-handling): add APIM error classification design):** see docs/design/15-exception-handling/07-apim-error-classification.md for the full APIM error classification design. Two approved exceptions to the M2b "no change" rules were recorded there:
- E1 (log level): Registration `err.tpa.member.not.found` moves from `FailureOrigin.DOWNSTREAM_CONTRACT` to `FailureOrigin.DOWNSTREAM_BUSINESS` (ERROR -> WARN), delivered in reduced-scope M2b with fail-first characterization; remaining Registration integration is deferred to M3. No other log level changes in M2b.
- E2 (public contract): no `DOWNSTREAM_*` value may reach the frontend; enforced by new sub-task **2b.2a** (runs after 2b.2, before 2b.3). FE confirmed by Nick: no dependence on any `DOWNSTREAM_*` value; E2 approved.

### M3 - Call-site migration (one ERROR per incident)

**Depends on M2b:** start M3 from updated `origin/dev` only after the approved reduced-scope M2b
branch is merged. For each feature, attach the shared APIM classification and migrate incident logging
in the same bounded feature slice. The classifier produces `ApimFailureCategory`; map it to the
existing `FailureOrigin` at an appropriate adapter/observability boundary. Do not introduce an
application-to-adapter dependency or change exception identity/public routing merely to carry metadata.

#### M3a - Registration username availability check only (TRPChkCustId)

M3a covers the Registration username-availability check only: typed APIM ownership plumbing, the
`CheckRegistrationUsernameService` terminal incident owner, the completed-HTTP observation marker,
and connected proof. Implementation is complete. Per Nick's attestations on this HEAD, these gates
passed: `mvn clean test`, startup smoke and the SonarQube quality gate. The earlier full-suite and
cold unscoped Spotless gates are accepted. The MR and merge are pending. M3 as a whole remains
incomplete.

The terminal-origin labels identify failures owned by `CheckRegistrationUsernameService`:

- `APIM_TECHNICAL`: the service owns the terminal ERROR for a technical APIM failure.
- `LOCAL_SYSTEM`: the service owns the terminal ERROR for a local-system failure classified at the APIM adapter boundary.
- `LOCAL_STATE_FAILURE`: the service owns the terminal ERROR for a local username-state failure.

Known M3a limitations:
- The encrypted APIM path is covered by shared-plumbing unit tests, not by a connected request test.
- Unmigrated callers retain their existing ERROR ownership; the unmigrated Forgot Password route still logs five ERRORs from three loggers.
- `ApimWebClientFacade` still logs ERROR with a throwable for `APIM_OBSERVER` callers when structured serialization fails.
- Request filters do not validate `X-Request-Id`; the username-check terminal ERROR sanitizes its own copy (see O-18).
- Sonar new-code coverage was evaluated by the quality gate only; no separate changed-line coverage figure was produced.

**Execution order:** Registration (3.3), Forgot Password (3.2), Login (3.1), Username Recovery
(3.9), then the remaining account/fund and existing M3 scopes. Existing sub-task IDs are retained;
table numbering is not execution order. The deferred-work schedule below is authoritative.
  
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

Use one new branch based on updated `origin/dev` and one MR per feature slice. Open a slice's MR only
after its gate passes; merge it with a merge commit (no squash), then start the next feature slice
from the updated `origin/dev`. Jenkins/runtime values for PROD/DR (ERROR_ALERT_ENABLED=true,
recipients) are a separate pipeline change.

### M2b reduced-scope completion addendum (approved 2026-11-04)

M2b completion is intentionally reduced to the following delivered scope:
- E1: Registration `err.tpa.member.not.found` is `DOWNSTREAM_BUSINESS` and WARN.
- E2: no public workflow error `type` renders a `DOWNSTREAM_*` value.
- The shared pure APIM classifier distinguishes `DOWNSTREAM_CONTRACT`,
  `DOWNSTREAM_INVALID_RESPONSE`, `DOWNSTREAM_TRANSPORT`, and `LOCAL_SYSTEM`.
- The final M2b milestone gates pass.

The original 2b.4..2b.n goal to attach classification to every feature APIM failure is deferred to
follow-up work. M2b must not be reported as completing the original "every APIM failure classified"
goal. No Registration, Forgot Password, Login, Username Recovery, or other feature migration is part
of this reduced completion scope.

### M3 deferred-work schedule and closure criteria (approved 2026-11-04)

Deferred means scheduled into the next milestone, not indefinitely postponed. This schedule assigns
follow-up work without reopening M2b or claiming that any feature integration is already complete.
Nick owns sequencing and approval; the implementer delivers each slice and the independent reviewer
checks its evidence. All rows below are pending until separately implemented and validated.

| Order / ID | Scope and destination | Required outcome | Status |
|---|---|---|---|
| First remaining / M3b / 3.3 | Registration: lookup, start, completion and continuation; the username-availability check is M3a | Integrate technical classification at the adapter boundary and consolidate incident logging. Preserve M3a behavior, E1 business/WARN behavior, both captcha paths, public responses and replay semantics. Use bounded internal steps; one coherent feature commit when green. | Pending |
| Second / 3.2 | Forgot Password: start, details/OTP, resend and completion | Integrate classification and logging. Revisit the deferred Details/OTP SYSTEM_FAILED seam and Completion response/replay proof only where required to establish changed-path behavior. Do not redesign the public contract. | Pending |
| Third / 3.1 | Login: start, OTP verify/resend, account-list retrieval and session establishment | Integrate classification and incident ownership while preserving BFF source, public statuses/codes and replay behavior. | Pending |
| Fourth / 3.9 | Username Recovery | Integrate classification and logging; preserve feature-owned business rejections, public outcomes and no-ERROR behavior for expected member-not-found. | Pending |
| Remaining / 3.10 | Account Summary, Account Balance, Account Gain/Loss and other account/fund adapters not covered by existing M3 rows | Migrate one feature at a time; reuse current adapter/mapping tests and add only changed-path gaps. | Pending |
| Existing / 3.4 | Authenticated session lifecycle and validation services | Complete existing incident/diagnostic migration; classifier integration applies only to actual APIM paths. | Pending |
| Existing / 3.5 | Personal Information | Refresh stale inventory/citations against current retrieval/update adapters during this slice, then integrate classification/logging without changing response contracts. | Pending |
| Existing / 3.6 | Fund Price, Fund Fact Sheet, Fund Comparison, Config Service adapter and APIM facade profile-resolution | Cover the remaining feature/technical paths; keep business-code tables local to each feature. Avoid duplicating work completed under 3.10. | Pending |
| Final / 3.7 and 3.8 | Swallowed-failure audit and observer downgrades | Establish incident ownership for swallowed system failures; downgrade duplicate observers only after ownership is proven; shrink the existing ERROR-call ratchet accurately. | Pending |

#### Per-feature acceptance criteria

- Reuse existing characterization; do not restart an exhaustive inventory or require a full-chain test
  for every row. First identify only the changed paths and their existing behavioral tests.
- Use the shared pure classifier for technical APIM failures. Recognized business-code tables stay
  feature-owned. Preserve DOWNSTREAM_INVALID_RESPONSE for crypto/response-mapping failures.
- Keep internal classification internal. Preserve E2 public BUSINESS/SYSTEM types, normalized public
  ApiStatus, BFF Login source and approved lowercase message keys.
- Preserve HTTP status, codes, targets, messages, headers, captcha behavior and idempotency/replay
  snapshots. Any desired public-contract change requires separate approval and scope.
- Assign one incident ERROR owner for technical incidents; expected business rejections do not become
  technical ERROR alerts. Prove changed event levels, stack-trace policy and duplicate-observer behavior.
- Keep adapter dependencies out of application code. Do not wrap exceptions or mutate replay/result
  records solely to attach classification unless routing preservation is proven and separately approved.
- Use fail-first tests for behavior changes, compile, test-compile and targeted tests for changed classes.
  Do not run full-suite/formatting/Sonar/smoke gates after every internal step. Run the established full
  milestone gates once at M3 completion; run formatting apply only at the agreed stabilization stage.
- Each completed feature records its commit, tests, incident owner and remaining gaps. The existing
  KNOWN_REMAINING ratchet may shrink but must not hide newly introduced ERROR call sites.
- Stop and escalate after three consecutive failed attempts or ten scripts without a commit.

#### Other deferred items and explicit destinations

| Item | Destination and timing | Closure condition |
|---|---|---|
| Forgot Password Details/OTP SYSTEM_FAILED routing | Forgot Password M3 slice, only to the extent needed for changed logging/classification | Behavioral evidence for the changed path; any remaining unrelated seam stays explicitly tracked. |
| Forgot Password Completion public-response/replay Test B | Forgot Password M3 slice when its changes require this proof; otherwise separate follow-up | Do not claim replay/public routing covered by adapter-only tests. |
| Personal Information stale rows/citations | Personal Information M3 slice | Trace current source and update affected rows only. |
| O-17: outbound request-body logging/privacy | Separate early security/privacy fix after M2b merge; do not wait for all feature migrations | Nick/security authority agree scope; remove or sanitize sensitive body content and prove it with focused tests. |
| RegistrationHttpResponses duplicate/dead branches and cross-feature HTTP-status inconsistencies | Separate cleanup/contract work, not classifier-integration prerequisites | Preserve current behavior for cleanup; obtain explicit approval for status changes. |
| Technical username-check failure rendered as BUSINESS | Separate public-contract decision owned by Nick | Decide intended public type and implement only under approved scope. |
| Original every-APIM-failure-classified objective | Aggregate completion of the deferred feature integrations, not reduced M2b | Every in-scope feature has implementation and evidence; remaining gaps are explicit. |

#### M3 completion and M4 activation boundary

M3 closes only when its agreed feature integrations, incident ownership, swallowed-failure audit,
observer migration and ratchet gates are complete, followed by the established milestone validation.
Do not silently transfer unfinished M3 features into a claim of full completion.

M4 alerting may be developed separately after its prerequisites are agreed, but alert activation for
an environment must wait until the included paths have validated classification and incident
ownership. Expected business rejections must not generate technical ERROR alerts. Existing security,
recipient, deployment and operational approvals still apply; this schedule does not grant them.

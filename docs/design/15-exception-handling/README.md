
## Exception Handling, Central Logging and Error Alerting

<table>
<tr>
<th>  
Field
</th>
<th>  
Value
</th>
</tr>
<tr>
<td>  
Status
</td>
<td>  
Approved
</td>
</tr>
<tr>
<td>  
Branch
</td>
<td>  
refactor/exception-handling
</td>
</tr>
<tr>
<td>  
Base
</td>
<td>  
origin/dev at 92849aac9a867830a975020809b4290ca15287dc
</td>
</tr>
<tr>
<td>  
Owner
</td>
<td>  
Nick (solution design, implementation, CI/CD)
</td>
</tr>
<tr>
<td>  
Evidence
</td>
<td>  
scripts 001 (inventory) and 002 (deep inventory), read-only on origin/dev
</td>
</tr>
</table>


### Goals
- **Central exception handling**: one mapping from exception to public response, shared by the
controller advice and the WebFlux exception handlers.
- **Central logging**: one logging policy; one ERROR line per incident; structured JSON output.
- **Alerting**: send an email when a log event at a configured level is written.

### Resolved decisions (2026-10-21)

<table>
<tr>
<th>  
#
</th>
<th>  
Decision
</th>
</tr>
<tr>
<td>  
D1
</td>
<td>  
Alerts are driven by a Logback appender. The levels that trigger an alert are configurable (error-alert.levels, default ERROR; WARN and INFO may be added).
</td>
</tr>
<tr>
<td>  
D2
</td>
<td>  
Channel is email through the existing SMTP sender. Initial recipient deng.nick@bcthk.com; to be replaced by a group mailbox through configuration only.
</td>
</tr>
<tr>
<td>  
D3
</td>
<td>  
Deduplication is controlled by a flag (error-alert.deduplicate). true: per-pod in-memory dedup window plus hourly cap. false: every alert is sent. Redis is not used for dedup (see 04-error-alerting.md section 6).
</td>
</tr>
<tr>
<td>  
D4
</td>
<td>  
Alerting is enabled in PROD and DR; disabled by default elsewhere, and can be switched on at runtime in DEV/SIT for testing.
</td>
</tr>
<tr>
<td>  
D5
</td>
<td>  
No public HTTP status or response body changes in this refactor. Current behaviour is pinned by characterization tests. Unifying 500/502/503 for SYSTEM_ERROR is a separate follow-up.
</td>
</tr>
<tr>
<td>  
D6
</td>
<td>  
Result-based auth workflows (Login, Registration, Forgot Password, Username Recovery) keep their result/idempotency-replay design. Only their logging is centralized (through FailureEventPort).
</td>
</tr>
<tr>
<td>  
D7
</td>
<td>  
Logs move to structured JSON. The log platform parses JSON from stdout.
</td>
</tr>
</table>


### M1 closure decisions (2026-10-22)

<table>
<tr>
<th>  
#
</th>
<th>  
Decision
</th>
</tr>
<tr>
<td>  
A1
</td>
<td>  
ApiExceptionHandler collapses to a single @ExceptionHandler(Exception.class)
handle(Exception, ServerWebExchange). Per-type routing is done only by ExceptionMappingRegistry
(nearest-superclass resolution). Tests that call handler.handleXxx(...) change only the call
site, never the assertions.
</td>
</tr>
<tr>
<td>  
A2
</td>
<td>  
The AccountBalance normalization event is merged into the single http.api.error event. Its
value-free fields (collection, rowIndex, field, reason) become extra fields on that one event.
HTTP status and body are unchanged.
</td>
</tr>
</table>

  
See exception-handling-m1-closure-addendum.md for the full M1 closure scope (sub-tasks 1.7-1.10),
approved by Nick on 2026-10-22 following the review at e65b87eb.

### Documents

<table>
<tr>
<th>  
File
</th>
<th>  
Content
</th>
</tr>
<tr>
<td>  
01-current-state.md
</td>
<td>  
Confirmed findings from scripts 001 and 002
</td>
</tr>
<tr>
<td>  
02-exception-handling.md
</td>
<td>  
Target design for central exception handling (M1), including the M1 closure target shape
</td>
</tr>
<tr>
<td>  
03-logging-policy.md
</td>
<td>  
Logging policy, structured logging, call-site migration (M2, M3)
</td>
</tr>
<tr>
<td>  
04-error-alerting.md
</td>
<td>  
Alert appender, configuration, dedup, email content, failure modes (M4)
</td>
</tr>
<tr>
<td>  
05-milestones.md
</td>
<td>  
Milestones, sub-tasks, tests, gates (includes M1 closure sub-tasks 1.7a-1.10)
</td>
</tr>
<tr>
<td>  
06-open-items.md
</td>
<td>  
Open questions, risks, deferred follow-ups (includes O-10, O-11 from M1 closure)
</td>
</tr>
<tr>
<td>  
exception-handling-m1-closure-addendum.md
</td>
<td>  
M1 closure scope: why, approved decisions, target shape, sub-tasks 1.7a-1.10, done-when criteria
</td>
</tr>
</table>


### Non-goals
- Changing any public HTTP status, ApiStatus, error code or error type (D5).
- Converting result-based workflows to exceptions (D6).
- Alerting on failures that happen before the Spring context creates beans
(for example ConfigServiceEnvironmentPostProcessor); see 06-open-items.md.
- Log platform side configuration.
- APIM-specific downstream error code handling and any public status/header change (O-5, O-10)
remain out of scope for M1 closure.


## 04 - Error Alerting (M4)

### 1. Overview
any logger.x(...)  -> Logback root
                         -> console appender (unchanged)
                         -> ErrorAlertAppender (installed only when error-alert.enabled=true)
                              level in error-alert.levels?  logger/thread excluded?
                              -> ErrorAlert (sanitized, truncated)
                              -> bounded queue (non-blocking offer, never throws)
                                   -> ErrorAlertDispatcher (single daemon thread)
                                        -> ErrorAlertThrottle (deduplicate=true only)
                                        -> ErrorAlertNotificationPort
                                             -> EmailErrorAlertAdapter -> EmailSenderPort (SMTP)  
Package: com.bct.ngtpa.apiservice.infrastructure.logging.alert (appender, dispatcher, throttle,
fingerprint, properties); port application.port.out.ErrorAlertNotificationPort; adapter
adapter.out.email.alert.EmailErrorAlertAdapter.

### 2. Configuration
error-alert:
  enabled: ${ERROR_ALERT_ENABLED:false}            # D4: true in PROD/DR via runtime env
  levels: ${ERROR_ALERT_LEVELS:ERROR}              # D1: comma list, e.g. ERROR,WARN
  recipients: ${ERROR_ALERT_RECIPIENTS:deng.nick@bcthk.com}   # D2: later a group mailbox
  subject-prefix: "[NGTPA api-service]"
  deduplicate: ${ERROR_ALERT_DEDUPLICATE:true}     # D3
  dedup-window: ${ERROR_ALERT_DEDUP_WINDOW:PT10M}  # used only when deduplicate=true
  max-alerts-per-hour: ${ERROR_ALERT_MAX_PER_HOUR:20}   # used only when deduplicate=true
  queue-capacity: 500
  stack-trace-max-lines: 30
  message-max-length: 2000
  exclude-loggers: []                               # extra logger-name prefixes to ignore  
Startup validation (ErrorAlertProperties, fail fast when enabled=true):
- levels is non-empty and every value is one of ERROR, WARN, INFO. DEBUG and TRACE are
rejected to prevent mail floods.
- recipients is non-empty and every entry is a syntactically valid address.
- dedup-window, max-alerts-per-hour, queue-capacity are positive.
- email.enabled is true and email.smtp.host/from are set.  
When enabled=false the appender is not installed: zero runtime cost.

### 3. Appender rules
- Accept an event when its level is in levels, its logger name does not start with the alert
package or an exclude-loggers prefix, and it was not written by the dispatcher thread
(loop guard: an SMTP failure inside alerting can never trigger another alert).
- Build ErrorAlert: timestamp, level, logger, thread, requestId (MDC, else the requestId /
correlationId key parsed from the message), message (re-sanitized with LoggingSanitizer,
truncated), exception class, root cause class, first stack-trace-max-lines frames, fingerprint.
- offer to the bounded queue. On a full queue, increment a dropped counter; the next email reports
it. append never throws and never blocks the logging thread.

### 4. Deduplication (D3)
  
ErrorAlertFingerprint = level + logger + event name + root exception class. The event name comes
from the JSON event field or the event= prefix; without one, a normalized message prefix is used
(digits, UUIDs and request ids replaced).  
ErrorAlertThrottle (pure, injected Clock):
<table>
<tr>
<th>  
deduplicate
</th>
<th>  
Behaviour
</th>
</tr>
<tr>
<td>  
true
</td>
<td>  
The first alert of a fingerprint is sent; repeats within dedup-window are counted, not sent. When the window ends with a non-zero count, one summary email is sent ("N more since HH:mm"). Across all fingerprints at most max-alerts-per-hour emails per pod; above the cap, alerts are counted and reported in the next email.
</td>
</tr>
<tr>
<td>  
false
</td>
<td>  
Every alert that fits in the queue is sent. Queue overflow is still counted and reported (memory protection, not dedup).
</td>
</tr>
</table>

  
Dedup state is per pod and in memory. With N pods the same incident can produce up to N emails per
window.

### 5. Email content
- Subject: [NGTPA api-service][<ENV>] <LEVEL> <event or exception> in <logger simple name>
- Body (plain text): environment, pod (HOSTNAME), application name, time (ISO-8601 with offset),
level, logger, thread, request id, fingerprint, suppressed/dropped counts, sanitized message,
exception and root cause, truncated stack trace.
- Never included: request or response bodies, cookies, tokens, OTPs, unmasked identifiers. Messages
are already sanitized at source and are sanitized again before sending.
- Recipients are the configured operations addresses. The member destination override in
MessageDeliveryRuntimeConfig does not apply (it is for member messages); the adapter calls
EmailSenderPort directly, one message per recipient.

### 6. Why dedup does not use Redis, and how a Redis outage is still reported
- The alert path has no Redis dependency, so it keeps working when Redis is down.
- A Redis outage produces ERROR lines on its own: CacheException reaches ApiExceptionHandler
(ERROR), and session store failures are recorded as technical FailureEvents
(AuthenticatedSessionLifecycleService, idempotency stores) after M3. Those lines go through the
appender and are emailed like any other ERROR.
- If dedup state lived in Redis, the dedup store would fail exactly during that outage, and the
failure of the dedup store would itself generate errors that could not be deduplicated.
- In-memory dedup is the right trade-off; the cost is up to one email per pod per window.

### 7. Failure modes

<table>
<tr>
<th>  
Failure
</th>
<th>  
Result
</th>
</tr>
<tr>
<td>  
SMTP unreachable
</td>
<td>  
Logged at WARN by the alert logger (excluded from alerting); alert dropped. Platform/pod monitoring is the backstop.
</td>
</tr>
<tr>
<td>  
Alert storm
</td>
<td>  
deduplicate=true: window + cap. deduplicate=false: bounded queue; overflow counted.
</td>
</tr>
<tr>
<td>  
Exception inside appender or dispatcher
</td>
<td>  
Caught, counted, logged at WARN by the alert logger; logging continues.
</td>
</tr>
<tr>
<td>  
Error before beans exist / application fails to start
</td>
<td>  
Not alerted (appender not installed yet); visible as pod restart. Accepted (O-7, closed
2026-10-21).
</td>
</tr>
<tr>
<td>  
Shutdown
</td>
<td>  
Dispatcher drains for up to 5 s, then stops.
</td>
</tr>
</table>


### 8. Verification in DEV/SIT
  
Set ERROR_ALERT_ENABLED=true and, temporarily, ERROR_ALERT_LEVELS=ERROR,WARN, then send a
request that produces a WARN http.api.error (for example an invalid notification request). One
email arrives; repeats within 10 minutes are suppressed and summarized. Revert the levels afterwards.

# Message Service Operations, Configuration, and Testing

## 1. Configuration reference

### 1.1 Templates and policy

Review per workflow and channel:

- SMS template
- Email subject and body
- Required placeholders
- OTP TTL variables and wording
- Language variants
- Content owner/approval
- Maximum SMS length/encoding assumptions

The reviewed OTP policy validates that SMS/email bodies contain `{code}`. Templates can also use TTL variables such as `{ttlValue}` and `{ttlUnit}`.

### 1.2 SMS provider

Review:

- Enabled flag
- Base URL
- Send path
- Username/password secret source
- Sender ID
- Class code
- Default country code
- Timeout
- Proxy/TLS settings
- Provider acceptance/error contract

### 1.3 Email provider

Review:

- Enabled flag
- SMTP/API host and port
- From address
- Timeout
- Authentication secret source
- TLS/relay requirements
- Allowed sender/domain policy

Do not assume a local unauthenticated relay configuration is appropriate for production.

### 1.4 Environment destination override

Where non-production override is enabled, verify:

- Deployment-environment detection
- Approved override mobile/email
- Startup validation for non-blank values
- Production-like profiles cannot silently redirect
- Logs identify use of override mode without revealing the destination

### 1.5 Workflow controls

Message configuration is incomplete without:

- OTP TTL
- Resend cooldown
- Maximum resend attempts
- Verification attempts
- Rate-limit policy
- Idempotency TTL
- Session TTL

These are owned by the calling workflow, not by the provider adapter.

## 2. Troubleshooting runbook

### Template rendering fails

Check template placeholder names, required variables, selected scenario/channel/language, email subject/body configuration, and startup validation. Never log the rendered OTP body while investigating.

### SMS provider rejects request

Check provider credentials, sender ID, class code, phone normalization/country code, multipart field names, enabled flag, and sanitized response status. Verify against a non-production provider account.

### SMS timeout or connection failure

Check provider availability, DNS, proxy/TLS, configured timeout, and network path. Determine whether the request may have reached the provider; do not blindly retry an uncertain OTP send.

### Email rejected

Check recipient format, from address, relay policy, authentication/TLS, SMTP response category, header formatting, and timeout. Do not expose SMTP diagnostic text publicly.

### API reports delivery failure but customer receives two messages

Investigate idempotency reservation/completion, automatic provider retries, client duplicate requests, timeout unknown outcomes, and user-driven resend. Correlate by request ID and safe provider reference only.

### Message accepted but user did not receive it

The service records provider acceptance, not final delivery. Check destination correctness through approved masked support procedures, provider queues, spam filtering, carrier/mailbox delays, and whether delivery callbacks exist. Do not mark the OTP verified or delivered based on provider acceptance.

### Wrong destination used in non-production

Check destination-override configuration and deployment-environment policy. Treat accidental delivery to a real customer as a security/privacy incident.

### Masked destination looks incorrect

Check channel-specific normalization and masker tests. Avoid “fixing” support output by exposing more recipient characters.

## 3. Logging and observability

### Safe fields

- Correlation/request ID
- Scenario
- Channel
- Provider/adapter name
- Accepted/failed status
- Safe failure code
- Duration
- Retry/unknown-outcome category
- Masked destination or suffix only when approved

### Forbidden fields

- OTP or OTP hash
- Raw destination
- Rendered subject/body containing customer data
- Provider username/password, token, or API key
- Full provider response body
- Session ID, CSRF token, or idempotency key
- Identity/account data

Recommended events:

```text
message.render.failed
message.delivery.started
message.delivery.accepted
message.delivery.failed
message.delivery.unknown
message.destination.override.active
sms.provider.failed
email.provider.failed
```

Use correlation IDs rather than recipient data to join events.

## 4. Metrics and alerts

Track by scenario/channel/provider without customer identifiers:

- Delivery attempts
- Acceptance rate
- Failure rate by safe category
- Timeout/unknown-outcome rate
- Rendering failures
- Provider latency percentiles
- Destination-override usage in non-production
- Workflow resend and cooldown rates

Alert on sustained provider failure, sudden acceptance-rate change, timeout spikes, credential/config failure, and production override activation.

## 5. Test strategy

### 5.1 Template renderer tests

- Single and multiple placeholder replacement
- `{code}`, `{ttlValue}`, and `{ttlUnit}`
- Missing required variable
- Unknown placeholder
- Empty/null template/variables
- Repeated placeholder behavior
- Unicode and Chinese content
- No rendered OTP in exception/toString/log output

### 5.2 Delivery-adapter tests

With fake sender ports:

- SMS accepted
- Email accepted
- Sender failed
- Sender returned null/malformed result
- Unsupported channel
- Invalid command
- Correct template and variable selection
- Safe result mapping
- Correlation ID preservation

### 5.3 SMS adapter tests

- Configuration validation
- Correct path, method, multipart fields, and timeout
- Phone/country normalization
- Provider acceptance
- Provider business rejection
- 4xx/5xx
- Timeout/connection error
- Malformed/large provider response
- Safe response/error sanitization
- Credentials, recipient, and content absent from logs

### 5.4 Email adapter tests

- Address validation
- Subject/body mapping
- From address
- SMTP/API acceptance and rejection
- Timeout
- TLS/authentication failure
- Unicode subject/body
- Header injection attempts
- Recipient/content absent from logs

### 5.5 Masking tests

- Short/long mobile numbers
- International prefixes
- Normal and unusual email forms
- Null/blank values
- Masked output cannot reconstruct raw value
- DTO `toString()` redaction
- Sanitizer catches recipient/code/template fields

### 5.6 Workflow integration tests

- Login OTP send accepted and challenge bound
- Login delivery failure does not create inconsistent session state
- Registration start delivery accepted
- Registration resend accepted
- Cooldown/rate-limit rejection occurs before provider call
- Completed idempotency replay does not resend
- Same idempotency key with different request rejected
- Previous OTP invalidation on resend
- Unknown provider outcome follows approved recovery

### 5.7 Environment override tests

- Override active only in approved environment
- Mobile and email override selected correctly
- Missing override fails startup/use safely
- Production-like environment cannot activate override
- Original destination never logged while overridden

### 5.8 End-to-end tests

Against provider sandbox/stub:

- SMS Login OTP
- Email Login OTP
- Registration initial OTP
- Registration resend
- Provider outage
- Timeout/unknown outcome
- Unicode template
- Correlation ID trace
- No sensitive output in logs/APM

## 6. Failure classification matrix

Maintain a source-controlled matrix:

```text
Provider/adapter outcome
MessageDeliveryStatus
Workflow result status
Public error code
API/HTTP status
Retryable
Idempotency action
OTP/challenge action
Log severity
User next step
```

Include unknown-outcome and null/malformed sender-result behavior.

## 7. Release evidence

Each delivery change should include:

- Template/content approval
- Provider contract/version
- Configuration diff by environment
- Fail-first TDD evidence
- Template-renderer tests
- Adapter/provider tests
- Workflow idempotency and failure tests
- Masking/redaction evidence
- Sandbox delivery evidence using approved test destinations
- Timeout and duplicate-delivery assessment
- Rollback plan
- Monitoring/alert updates
- Commit message

## 8. Production readiness checklist

- [ ] Template content and variables approved
- [ ] SMS/email provider configuration verified
- [ ] Secrets sourced from secret management
- [ ] TLS/authentication requirements verified
- [ ] Timeouts configured
- [ ] Destination normalization and masking tested
- [ ] Non-production override cannot activate in production
- [ ] Idempotency prevents duplicate delivery
- [ ] Rate limits and resend policy applied before delivery
- [ ] Unknown outcomes do not trigger blind retries
- [ ] Provider acceptance is not described as final delivery
- [ ] No OTP, raw recipient, content, or credentials in logs/APM
- [ ] Alerts cover failure, timeout, rendering, and override activation
- [ ] Provider outage runbook exercised
- [ ] Fail-first TDD evidence attached to the change

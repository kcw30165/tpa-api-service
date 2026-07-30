# Message Service Architecture and Delivery Lifecycle

## 1. Architectural intent

The Message Service separates workflow orchestration from transport-specific delivery. The application issues a message-delivery command; the central adapter renders content and selects a sender; provider adapters own HTTP/SMTP mechanics.

```text
application workflow
  Login OTP / Registration OTP / future scenario
        |
application/port/out
  MessageDeliveryPort
        |
message application adapter
  MessageDeliveryAdapter
  MessageTemplateRenderer
        |
provider ports
  SmsSenderPort / EmailSenderPort
        |
provider adapters
  SmsSenderAdapter / SMTP email adapter
        |
external SMS or email service
```

## 2. Core contracts

### `MessageDeliveryCommand`

The channel-neutral request carries the information required to render and send a message, including concepts such as:

- Delivery scenario
- Delivery channel
- Recipient/destination
- Message template or template code
- Email subject where applicable
- Template variables
- Language/locale where applicable
- Correlation ID

The command is an application contract, not an SMS-provider or SMTP wire DTO.

### `MessageDeliveryResult`

Normalizes delivery into a small application-facing result, including:

- Delivery status such as `ACCEPTED` or `FAILED`
- Safe provider/message reference where approved
- Safe failure code
- Safe failure description

A null or malformed sender result becomes a controlled failure rather than an assumed success.

### Sender contracts

`SmsSenderPort` and `EmailSenderPort` accept channel-specific DTOs such as `SmsMessage` and `EmailMessage`, and return `SenderResult` with a normalized sender status.

## 3. Central delivery adapter

`MessageDeliveryAdapter` implements the channel-neutral port. Its responsibilities are:

1. Validate the delivery command.
2. Select the applicable template and variables.
3. Render the content using `MessageTemplateRenderer`.
4. Build the channel-specific sender DTO.
5. Delegate to `SmsSenderPort` or `EmailSenderPort`.
6. Normalize `SenderStatus.ACCEPTED` into `MessageDeliveryStatus.ACCEPTED`.
7. Normalize missing, failed, or exceptional sender outcomes into `FAILED`.
8. Return only safe result metadata.

It must not:

- Resolve whether the recipient is eligible
- Generate OTPs
- Update OTP/session state
- Enforce resend limits
- Decide frontend workflow navigation
- Persist rendered content

## 4. Template rendering

`MessageTemplateRenderer` separates content substitution from provider transport. `DefaultMessageTemplateRenderer` replaces named placeholders using the variables supplied in the render command.

OTP templates use variables such as:

```text
{code}
{ttlValue}
{ttlUnit}
```

### Template rules

- Required variables must be present.
- Missing variables fail before provider invocation.
- OTP policy validation requires `{code}` in the applicable SMS/email body.
- Email subject and body are configured separately.
- Templates are configuration/content, not controller literals.
- Rendered bodies are sensitive because they contain OTPs.
- Template lookup must preserve the requested scenario/channel/language contract.

## 5. SMS adapter

The SMS sender adapter uses a provider-specific `WebClient` and multipart/form-data request. Configuration concepts include:

- Enabled flag
- Base URL and send path
- Username and password
- Sender ID
- Class code
- Default country code
- Timeout

The adapter:

- Validates configuration at startup or construction
- Normalizes the destination according to the approved phone contract
- Sends provider-specific fields
- Maps provider acceptance to `SenderResult`
- Maps HTTP, timeout, serialization, and network failures safely
- Logs only masked phone metadata and safe provider outcome

Provider credentials and message content must not be logged.

## 6. Email adapter

The email path is abstracted by `EmailSenderPort`. The reviewed codebase includes an SMTP-based sender/client pattern with configuration such as:

- Enabled flag
- SMTP host and port
- From address
- Timeout

The sender builds an `EmailMessage` containing recipient, subject, body, and correlation information, then maps SMTP/client success or failure into `SenderResult`.

Deployment must verify the actual SMTP relay's TLS/authentication requirements. A local/plain relay configuration must not be assumed suitable for production.

## 7. Destination handling

Destination values are sensitive. The capability uses distinct representations for distinct purposes:

```text
Raw destination
  used only at the delivery boundary

Destination ID
  opaque public selection identifier where the workflow supports selection

Destination hash
  irreversible value used for rate-limit or challenge binding

Masked destination
  safe display value returned to frontend or stored in session workflow data
```

Email/mobile maskers expose only approved partial information. No raw destination belongs in Redis keys, logs, public errors, or replay snapshots.

## 8. Login delivery lifecycle

A typical Login OTP send flow is:

```text
Credential authentication returns eligible destinations
  -> frontend selects destination ID
  -> Login service validates auth-flow session, CSRF, state, and idempotency
  -> OTP challenge generates code and stores hash/state
  -> MessageDeliveryPort sends through SMS or email
  -> on acceptance, bind challenge to session
  -> complete idempotency replay snapshot
  -> return masked destination and timing metadata
```

On delivery failure, the Login service maps the result to `OTP_DELIVERY_FAILED` or the approved system outcome and must not leave a usable challenge/session binding that could create an inconsistent flow.

## 9. Registration delivery lifecycle

Registration start generally determines the approved channel/destination as part of identity/member verification, creates the OTP, sends it through `RegistrationMessagePort`/the shared delivery capability, and stores only masked/public workflow values.

Registration resend validates session, CSRF, state, idempotency, rate limits, cooldown, and resend limits before requesting a replacement delivery.

Environment-specific destination override is implemented as a controlled adapter/configuration concern. It must be validated and restricted to approved non-production behavior; production-like environments must not silently redirect customer communication.

## 10. Failure and retry semantics

### Delivery accepted

The provider accepted the send request. This is not proof of final delivery.

### Delivery failed

The provider rejected the request or the adapter observed a known failure. The workflow maps it to a downstream delivery error.

### Unknown outcome

A timeout or connection loss after request transmission may leave delivery uncertain. Blind retry can send duplicates. The workflow's idempotency and unknown-outcome policy must control recovery.

### Retry ownership

Provider retry is not a generic responsibility of `MessageDeliveryAdapter`. Retry must be explicitly designed using:

- Provider semantics
- Workflow idempotency key
- Challenge/replacement behavior
- Duplicate-message tolerance
- User-facing resend policy

Usually, user-driven resend is safer than hidden automatic resend for OTP messages.

## 11. Logging and auditing

Safe logging fields include:

- Correlation/request ID
- Scenario
- Channel
- Adapter/provider name
- Accepted/failed status
- Safe failure code
- Elapsed time
- Masked destination or non-identifying suffix only where approved

Forbidden fields include:

- OTP code or code hash
- Rendered subject/body where it contains customer data
- Raw email/mobile
- Provider username/password
- Access token/API key
- Session/CSRF/idempotency secret
- Full provider response when it may echo submitted content

Security-audit metadata passes through a sanitizer that rejects or redacts forbidden keys and values.

## 12. Scope boundary: Notice Messages

`ApimNoticeMessageAdapter` and notification-read operations belong to the portal notification capability. They retrieve/update messages through APIM. They do not implement SMS/email delivery and should not be placed behind `MessageDeliveryPort`.

## 13. Extension boundaries

### Reuse directly

- `MessageDeliveryPort`
- `MessageDeliveryAdapter`
- Template renderer
- Channel/scenario/status DTOs
- Sender-result normalization
- Email/mobile masking
- Correlation and safe logging patterns

### Configure or extend

- Scenario/channel policy
- SMS/email templates
- Sender/provider properties
- Approved locale variants
- Non-production destination override
- Safe failure-code mapping

### Implement per provider or workflow

- New provider adapter behind an existing sender port
- Provider-specific wire DTOs
- Workflow-specific eligibility and destination resolution
- Workflow idempotency/rate limits
- Delivery-failure effects on OTP/session state
- Public error and navigation behavior

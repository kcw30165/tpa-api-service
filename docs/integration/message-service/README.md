# Message Service Capability

> **Status:** Documents the outbound message-delivery implementation represented by the reviewed codebase snapshot.  
> **Audience:** New team members, backend developers, reviewers, testers, support engineers, and solution designers.  
> **Scope:** Application-triggered SMS and email delivery, template rendering, destination handling, provider adapters, failure semantics, masking, and reuse by Login and Registration OTP workflows.

## Start here

The Message Service is an outbound delivery capability. Application workflows submit a channel-neutral delivery command; the central adapter renders the configured template and delegates to an SMS or email sender port.

```text
Login / Registration / future workflow
        |
        v
MessageDeliveryPort
        |
        v
MessageDeliveryAdapter
   + template rendering
   + channel routing
   + sender-result normalization
        |
        +--> SmsSenderPort   -> SMS provider
        |
        +--> EmailSenderPort -> SMTP/email provider
```

Read the documents in this order:

1. **[Architecture and delivery lifecycle](architecture-and-delivery-lifecycle.md)** — understand ports, DTOs, rendering, channel routing, provider adapters, statuses, and security boundaries.
2. **[Integration and reuse guide](integration-and-reuse-guide.md)** — follow the implementation recipe when a workflow needs SMS or email delivery.
3. **[Operations and testing](operations-and-testing.md)** — configure, test, troubleshoot, monitor, and release message-delivery changes safely.

## Important scope distinction

This pack describes **outbound transactional delivery**, especially OTP messages. It is not the same as the portal Notice Message/notification-board capability retrieved through APIM.

```text
Outbound Message Service
  sends SMS/email initiated by a workflow

Notice Message capability
  reads or updates portal messages through APIM
```

Do not combine their ports, DTOs, lifecycle, or error semantics simply because both use the word “message.”

## Current consumers

The reviewed implementation is used by:

- Login OTP send and resend
- Registration start OTP delivery
- Registration OTP resend

The shared model supports `SMS` and `EMAIL` channels and scenarios such as `LOGIN` and `REGISTRATION`. A future workflow such as Forgot Password should reuse the capability through the same port while owning its policy, approved destinations, templates, idempotency, and workflow outcomes.

## Shared responsibilities

- Channel-neutral delivery command
- SMS/email routing
- Template variable rendering
- Provider-specific sender ports
- Sender-result normalization
- Correlation ID propagation
- Destination masking in logs and public responses
- Safe failure codes/messages
- Conditional environment/configuration wiring

## Workflow responsibilities

The calling workflow remains responsible for:

- Establishing that the destination is eligible
- Selecting or resolving the destination
- Generating an OTP or other business value
- Enforcing rate limits, cooldown, and resend limits
- Applying idempotency
- Invalidating an OTP after delivery failure where required
- Mapping delivery failure into workflow status, API errors, and navigation
- Storing only masked destination data in public/session state

The Message Service does not decide whether a user is allowed to receive a message.

## Delivery result semantics

A sender/provider success is represented as an accepted delivery request. It does not prove that a handset or mailbox received the message. The current project context has no delivery-status callback from the SMS/email sender.

## Reuse rules

1. Call `MessageDeliveryPort`; do not call the SMS or email implementation from workflow services.
2. Resolve destination eligibility before delivery.
3. Use configured templates and explicit variables; never concatenate OTP text in controllers.
4. Never log recipient, OTP, rendered body, credentials, or provider secrets.
5. Treat `ACCEPTED` as provider acceptance, not end-user delivery confirmation.
6. Do not add automatic provider retries without an approved duplicate-delivery and idempotency design.
7. Preserve one correlation ID across workflow, delivery adapter, and provider call.
8. Return only masked destinations to clients.
9. Keep Login/Registration failure behavior scenario-specific.
10. Test template variables, redaction, timeout, provider failure, and unknown outcomes.

## Current configuration concepts

The reviewed codebase externalizes OTP SMS templates, email subject/body, sender/provider settings, timeouts, credentials, sender ID/class code, default country handling, and environment-specific destination overrides. Template validation requires the OTP code placeholder where applicable. Confirm the active deployment profile before changing or diagnosing delivery behavior.

# Username Recovery Non-Production Destination Override Design

## 1. Decision

For every non-production-like deployment, Username Recovery message delivery must replace the TAS-matched member destination with a configured test destination before invoking the email or SMS sender.

Production-like deployments must use the verified member destination without override.

The existing `DeploymentEnvironmentPolicy` defines only `PROD` and `DR` as production-like. Missing, blank, local, DEV, SIT, UAT, QA, and unknown profiles are non-production-like and therefore require the destination override.

## 2. Configuration

Add Username Recovery configuration parallel to Login and Forgot Password:

```yaml
auth-workflows:
  username-recovery:
    message:
      destination-override:
        mobile: ${USERNAME_RECOVERY_MESSAGE_OVERRIDE_MOBILE:}
        email: ${USERNAME_RECOVERY_MESSAGE_OVERRIDE_EMAIL:}
```

Environment variables:

```text
USERNAME_RECOVERY_MESSAGE_OVERRIDE_MOBILE
USERNAME_RECOVERY_MESSAGE_OVERRIDE_EMAIL
```

Both override values must be nonblank in every non-production-like deployment. Production-like deployments do not require either override and must ignore them if supplied.

## 3. Runtime behavior

### Production-like deployment

```text
PROD or DR
verified member destination -> MessageDeliveryPort -> sender
```

### Non-production-like deployment

```text
any profile other than PROD or DR
verified member destination -> destination override policy -> configured test destination -> MessageDeliveryPort -> sender
```

The override must be applied centrally in the shared `MessageDeliveryPort` decorator used by Login and Forgot Password. Username Recovery use cases must not contain environment-specific destination replacement logic.

## 4. Required shared-model changes

Extend `AuthWorkflowProperties` with Username Recovery message configuration using the existing `Message` and `DestinationOverride` classes.

Extend `MessageDeliveryScenario` with `USERNAME_RECOVERY` if it is not already present.

Extend `MessageDeliveryRuntimeConfig` with:

```java
private static final String USERNAME_RECOVERY_OVERRIDE_PREFIX =
        "auth-workflows.username-recovery.message.destination-override";
```

The scenario switch must resolve Username Recovery to its own override configuration. It must not reuse the Login override values.

Conceptually:

```java
AuthWorkflowProperties.DestinationOverride override = switch (command.scenario()) {
    case LOGIN -> requiredLoginOverride(properties);
    case FORGOT_PASSWORD -> requiredForgotPasswordOverride(properties);
    case USERNAME_RECOVERY -> requiredUsernameRecoveryOverride(properties);
    default -> null;
};
```

The final implementation must follow the existing code style and exhaustive enum handling.

## 5. Destination state

The Username Recovery session must preserve the TAS-verified destination because it is part of identity verification. It must not replace that verified value at session creation.

Recommended separation:

```text
verifiedDestination
- returned by or accepted through TAS matching
- retained in protected session context
- proves which contact participated in verification

effectiveDeliveryDestination
- resolved immediately before delivery
- equals verifiedDestination in PROD and DR
- equals configured override in every other environment
```

Do not persist the non-production override as if it were the member's registered destination.

## 6. Masked destination response

After sender acceptance, `/request` must return a masked representation of the effective delivery destination:

- PROD/DR: mask the verified member destination.
- Non-production: mask the configured override destination.

This allows testers to identify where the notification was actually submitted and avoids falsely indicating that a real member received the message.

The public response must never return either destination in full.

## 7. Template and content behavior

Destination override changes only the recipient. It must not change:

- selected language;
- template code;
- template variables;
- username being delivered;
- correlation ID;
- message type or scenario.

Username Recovery continues to select localized templates from `Accept-Language` through its own configuration.

## 8. Security requirements

- Never fall back to the verified member destination in a non-production-like deployment when an override is missing.
- Missing or blank applicable override configuration must fail closed.
- Prefer startup validation for both Username Recovery override values in non-production profiles. Runtime validation remains defense in depth.
- Do not log the verified destination, override destination, or username.
- Audit only the fact that an override was applied, the channel, deployment classification, and sanitized correlation metadata.
- Do not expose environment-variable values in startup errors beyond the property names.

## 9. Failure behavior

If a non-production override is unavailable or invalid:

```text
HTTP: 503 Service Unavailable
API status: SYSTEM_ERROR
nextStep: SELECT_ACCOUNT
error code: err.system.unavailable
message delivery: not attempted
candidate state: not ACCEPTED
```

The operation must remain safely retryable after configuration is corrected, subject to existing idempotency and unknown-outcome rules.

If the sender has already been called, later persistence failures follow the existing unknown-outcome policy and must not trigger blind resend.

## 10. Test requirements

Add tests proving:

1. PROD uses the verified email destination.
2. PROD uses the verified mobile destination.
3. DR uses the verified destination.
4. Local, DEV, SIT, UAT, QA, blank, and unknown profiles use the configured override.
5. Username Recovery never uses Login or Forgot Password override values.
6. Missing non-production email override fails closed for email delivery.
7. Missing non-production mobile override fails closed for SMS delivery.
8. Startup validation rejects missing required non-production values if startup validation is implemented.
9. The delivery command preserves scenario, language, template, variables, username, and correlation ID.
10. The public masked destination represents the effective override in non-production.
11. Logs and audit events contain neither original nor override destination.
12. Idempotent replay does not invoke the sender a second time.

## 11. Changes to the main design baseline

This document supplements and amends these sections:

- `username-recovery-solution-design.md`: Message delivery, configuration, security, and observability.
- `username-recovery-api-contract-design.md`: Successful `/request` masked-destination semantics and system failure behavior.
- `username-recovery-session-redis-design.md`: Separation of verified and effective destinations.
- `username-recovery-test-traceability-design.md`: Environment override tests and regression coverage.
- `sequence-diagrams.md`: Destination resolution before `MessageDeliveryPort` submission.
- `handover.md`: Canonical decisions and remaining implementation work.

Where the earlier documents describe delivery to the stored destination without an environment qualifier, this addendum takes precedence.

# Forgot Password State Contract

**Feature:** 0061 - Forgot Password  
**Baseline:** 2026-07-30  
**Gate:** 3  

## Conceptual states

`USERNAME_VERIFIED`, `IDENTITY_VERIFIED`, `OTP_SENT`, `RESET_AUTHORIZED`, `COMPLETED`, `CANCELLED`, `LOCKED`, `EXPIRED`, `UNKNOWN_OUTCOME_BLOCKED`, terminal `FAILED` where required.

```text
no session -> USERNAME_VERIFIED
USERNAME_VERIFIED -> IDENTITY_VERIFIED | CANCELLED | EXPIRED
IDENTITY_VERIFIED -> OTP_SENT | CANCELLED | EXPIRED
OTP_SENT -> OTP_SENT (successful resend)
OTP_SENT -> RESET_AUTHORIZED (OTP success)
OTP_SENT -> LOCKED | CANCELLED | EXPIRED
RESET_AUTHORIZED -> RESET_AUTHORIZED (approved correctable rejection)
RESET_AUTHORIZED -> COMPLETED (confirmed reset success)
RESET_AUTHORIZED -> UNKNOWN_OUTCOME_BLOCKED (ambiguous reset)
```

Terminal/blocked states cannot authorize. `RESTART_FLOW` is a navigation result, not a server-state transition by itself; Java first makes the current context unusable.

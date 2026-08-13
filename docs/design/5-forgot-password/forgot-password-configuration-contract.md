# Forgot Password Configuration Contract

**Feature:** 0061 - Forgot Password  
**Baseline:** 2026-07-30  
**Gate:** 3  

Logical groups: auth-flow TTL/concurrency/cookie/lock; CSRF/provenance; OTP policy; idempotency actions/TTL/unknown policy; rate limits; APIM path/error/encryption/retry; messages/templates; workflow errors/next steps/actions; generic masking.

OTP values are configurable, with approved values six digits, 300 seconds, 60-second cooldown, five attempts, five resends and previous-code invalidation.

Navigation configuration must support the approved page next steps and action transition, without `pageRef`, `RETURN_TO_LOGIN`, or `CALL_HOTLINE` next steps. `RESTART_FLOW` plus configured error drives Hotline presentation.

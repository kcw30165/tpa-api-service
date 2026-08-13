# Forgot Password Observability and Audit Contract

**Feature:** 0061 - Forgot Password  
**Baseline:** 2026-07-30  
**Gate:** 3  

Safe fields: request ID, scenario, operation, stable status/error, nextStep/action ID, HTTP/API status, retry outcome, idempotency category, duration, safe operation identifier.

Forbidden: password, OTP/hash, CSRF/hash, cookies/session IDs, full username/identity/DOB/contact, protected context, Redis values, replay body, payloads and credentials.

Identity mismatch logs use category/code without `user-id` or submitted value. Reset unknown outcome records safe correlation and never triggers a retry.

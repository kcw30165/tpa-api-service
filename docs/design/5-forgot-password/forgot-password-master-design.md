# Forgot Password Master Design

**Feature:** 0061 - Forgot Password  
**Baseline:** 2026-07-30  
**Gate:** 3  

Angular renders the workflow. Java BFF owns validation, protected state, orchestration and public response mapping. APIM is the shared contract/transport facade. TAS owns username existence/identity context, member matching, password policy and reset.

Existing AuthFlowSession, CSRF, OTP, idempotency, rate-limit, message-delivery, APIM retry, completion lock, common response and logging capabilities are reused.

## Responsibility boundaries

- FE sends public inputs, renders `nextStep`, and exposes only returned `actions`.
- Java validates every request, maps TAS identity types explicitly, protects TAS identity context, enforces states and action preconditions.
- APIM uses shared OAuth/certificate/encryption/envelope/retry infrastructure.
- TAS remains authoritative for external business outcomes.

## Deferred shared change

The future `ActorContext(actorUserName, actorUserRole, actorUserType)` change is recorded but excluded from current implementation scope pending separate compatibility assessment.

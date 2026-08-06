# Forgot Password Session and Security Contract

**Feature:** 0061 - Forgot Password  
**Baseline:** 2026-07-30  
**Gate:** 3  

Every username success creates `AuthFlowSession`, issues HttpOnly `auth_flow_sid`, and returns raw CSRF in body. FE sends it in `X-XSRF-TOKEN`; only hash is persisted. Origin/Referer policy applies.

Protected context contains the server-trusted identity inputs required for the new identity step. FE cannot self-assert identity verification. Successful identity check moves to `IDENTITY_VERIFIED`.

OTP controls remain configurable. Fifth verification failure and sixth resend first make session/challenge terminal, then return `RESTART_FLOW`. `returnToLogin` is the only terminal-page action.

Reset completion reuses idempotency and session lock. Unknown outcome blocks authorization and exposes no reset action.

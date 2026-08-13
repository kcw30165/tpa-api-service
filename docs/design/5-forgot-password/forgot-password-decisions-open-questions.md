# Forgot Password Decisions and Open Questions

**Feature:** 0061 - Forgot Password  
**Baseline:** 2026-07-30  
**Gate:** 3  

## Effective decisions

- D3-001: `auth_flow_sid` plus response-body CSRF later sent as `X-XSRF-TOKEN`; no XSRF cookie.
- D3-002: OTP sends immediately after details/contact match.
- D3-003: Reuse `ApimRetryEligibility`.
- D3-004: Generic masking preserves Login behavior.
- D3-005: English and `zh_HK` templates approved.
- D3-006: APIM POST paths, envelope, errors and encryption fields approved.
- D3-007: Password is not field-level encrypted/decrypted.
- D3-008/D3-009/D3-012: Business, TAS and security ratifications recorded.
- D3-010: Post-transmission reset ambiguity is internal `UNKNOWN_OUTCOME`, public `SYSTEM_ERROR`; no automatic repeat.
- D3-011: Explicit one-time reset authorization.
- D3-013: Future ActorContext evolution is deferred.
- D3-014: Add `ENTER_ID` identity verification using `identityType` and `identityValue`.
- D3-015: Expanded `TRPChkCustId`; identity check uses protected `user-id` and `id-type`, not `scheme-code`.
- D3-016: Align `nextStep`, `actions`, `messages`, and `errors`; `RESEND_OTP` is action-only.
- D3-017: Success page is `PASSWORD_RESET_SUCCESS`; `returnToLogin` is an action.
- D3-018: No `pageRef` in Feature 0061 result data.
- D3-019: Hotline outcomes use `RESTART_FLOW`; no `CALL_HOTLINE` next step.
- D3-020: Successful resend returns `VERIFY_OTP`.

## Open questions

No blocking behavioral question remains. Physical shared action serialization, exact existing enum/page mapping, and repository implementation symbols remain Gate 5/6 evidence work.

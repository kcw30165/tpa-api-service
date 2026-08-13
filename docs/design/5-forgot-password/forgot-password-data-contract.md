# Forgot Password Data Contract

**Feature:** 0061 - Forgot Password  
**Baseline:** 2026-07-30  
**Gate:** 3  

- Protected username context stores only data required for continuation, including TAS `user-id` and mapped `id-type`; `scheme-code` is not used for identity verification.
- FE sends `identityType` and trimmed `identityValue`; server-held protected context is authoritative.
- Raw identity, DOB and contact never enter Redis keys or logs.
- OTP clear value is never persisted/logged; challenge state stores hashes/counters/timestamps.
- Password/confirmation are never logged, session-persisted, replayed or returned.
- Generic masking preserves Login output behavior.
- Reset authorization is short-lived, one-time and session/member/scenario-bound.

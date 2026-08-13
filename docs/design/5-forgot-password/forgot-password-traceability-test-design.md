# Forgot Password Traceability and Test Design

**Feature:** 0061 - Forgot Password  
**Baseline:** 2026-07-30  
**Gate:** 3  

| Decision | Coverage | Future test intent |
|---|---|---|
| D3-014 identity step | Workflow/API/state/error | Allowed enum mapping, trim-only value, both comparisons, targets/result-null |
| D3-015 APIM identity context | APIM/data/security | Expanded response, protected storage, no scheme-code use |
| D3-016 actions model | API/navigation/config | Unique IDs, unavailable actions omitted, command response authoritative |
| D3-017 success page | Navigation/error/sequence | Success ends at page with return action |
| D3-018 no pageRef | API/config/contracts | Serialization omits pageRef |
| D3-019 Hotline via restart | Navigation/state/error | Terminal context before response, restart nextStep, only return action |
| D3-020 resend action | Navigation/workflow | Action invokes API, success returns VERIFY_OTP |

Also test previous OTP/security/idempotency/APIM/unknown-outcome/redaction requirements. Implementation evidence columns remain empty until Gate 6.

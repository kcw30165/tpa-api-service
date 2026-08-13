# Forgot Password Error Matrix

**Feature:** 0061 - Forgot Password  
**Baseline:** 2026-07-30  
**Gate:** 3  

| Outcome | Status | Error/target | Result/navigation | State/recovery |
|---|---|---|---|---|
| Invalid identity type | `VALIDATION_FAILED` | FORM `err.forgot-password.incorrect.idType`; `identityType` | `result:null`; remain `ENTER_ID` | Identity unverified |
| Identity mismatch | `BUSINESS_REJECTED` | FORM `err.forgot-password.incorrect.id`; `identityValue` | `result:null`; remain `ENTER_ID` | Identity unverified |
| Details failure | Validation/business rejected | FE fields/PAGE | Remain `ENTER_DETAILS` | Keep safe identity context |
| OTP wrong, attempts remain | Approved validation/business status | `otpCode` | `VERIFY_OTP` | Retry allowed |
| Fifth OTP failure | Terminal rejection | PAGE/configured hotline error | `RESTART_FLOW`; unsuccessful/Hotline presentation | Terminal; only `returnToLogin` action |
| Resend selected | Action intent | None | `RESEND_OTP` action | Execute on OTP page |
| Resend success | `SUCCESS` | None | `VERIFY_OTP` | Replacement challenge |
| Sixth resend | Terminal rejection | PAGE/configured hotline error | `RESTART_FLOW`; Hotline presentation | Terminal; only `returnToLogin` action |
| Password policy rejected | `DOWNSTREAM_REJECTED` | password/PAGE | `PASSWORD_SETUP` | Authorization remains if valid |
| Reset success | `SUCCESS` | None | `PASSWORD_RESET_SUCCESS` | Completed; only `returnToLogin` action |
| Reset possible post-transmission loss | `SYSTEM_ERROR` | PAGE | Safe terminal/restart result; no reset action | `UNKNOWN_OUTCOME_BLOCKED`; no TAS repeat |

No `pageRef`. `RETURN_TO_LOGIN`/`CALL_HOTLINE` are not next steps. Blocking failures may use `result:null`.

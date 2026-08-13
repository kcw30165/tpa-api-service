# Forgot Password Workflow Contract

**Feature:** 0061 - Forgot Password  
**Baseline:** 2026-07-30  
**Gate:** 3  

## WF-001 Username

Validate username and call `TRPChkCustId`. On success, protect `cust-id`, `user-id`, mapped `id-type` and other required context; create the auth-flow session; issue cookie and CSRF; return `ENTER_ID`. `scheme-code` is not used by WF-002.

## WF-002 Identity

Accept `identityType` and `identityValue`. Map public `STAFF_NO` to Java `STAFFNO` and TAS `STAFF`. Compare mapped type and trimmed identity value against protected TAS context. Success returns `ENTER_DETAILS`. Invalid type and mismatched value use the approved FORM errors and `result:null`.

## WF-003 Details and OTP issue

Validate DOB and exactly one contact. Call `TRPSearchRegedMember`. On match, immediately create/send OTP. Accepted delivery binds the challenge and returns `VERIFY_OTP`. Confirmed failure creates no usable challenge and does not increment successful resend count. Indeterminate delivery uses existing unknown-outcome behavior.

## WF-004 OTP verify/resend

Wrong OTP within allowance returns/stays `VERIFY_OTP`. The fifth failure terminates the context and returns `RESTART_FLOW`, with the error resolving to the unsuccessful OTP/Hotline presentation and only `returnToLogin` action.

Resend is action transition `VERIFY_OTP -> RESEND_OTP`; successful API response returns `VERIFY_OTP`. Five resends are allowed; sixth terminates and returns `RESTART_FLOW`.

## WF-005 Password setup

OTP success creates `RESET_AUTHORIZED` and returns `PASSWORD_SETUP`. Correctable password validation/TAS policy failure remains at `PASSWORD_SETUP` while authorization is valid.

Confirmed reset consumes authorization, marks Completed, clears recovery context, and returns `PASSWORD_RESET_SUCCESS` with `returnToLogin` action.

Post-transmission ambiguity blocks authorization, records `UNKNOWN_OUTCOME`, returns `SYSTEM_ERROR`, exposes no reset action, and never repeats TAS automatically.

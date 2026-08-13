# Forgot Password Next-Step and Action Contract

**Feature:** 0061 - Forgot Password  
**Baseline:** 2026-07-30  
**Gate:** 3  

## Semantics

`nextStep` identifies the resulting page/state. `actions[]` identifies clickable items. `messages[]` is non-blocking; `errors[]` is blocking. Command actions call the API first; its response is authoritative. No `pageRef` is used.

## Page next steps

`ENTER_USERNAME`, `ENTER_ID`, `ENTER_DETAILS`, `VERIFY_OTP`, `PASSWORD_SETUP`, `PASSWORD_RESET_SUCCESS`, `RESTART_FLOW`.

`RETURN_TO_LOGIN` and `CALL_HOTLINE` are not next steps.

## Action transition

`RESEND_OTP` is action-only:

```text
VERIFY_OTP -> RESEND_OTP -> VERIFY_OTP
```

## Canonical paths

```text
Happy:
ENTER_USERNAME -> ENTER_ID -> ENTER_DETAILS -> VERIFY_OTP
-> PASSWORD_SETUP -> PASSWORD_RESET_SUCCESS

Username failure:
ENTER_USERNAME -> ENTER_USERNAME

Identity failure:
ENTER_USERNAME -> ENTER_ID -> ENTER_ID

Details failure:
ENTER_USERNAME -> ENTER_ID -> ENTER_DETAILS -> ENTER_DETAILS

OTP failure within allowance:
ENTER_USERNAME -> ENTER_ID -> ENTER_DETAILS -> VERIFY_OTP -> VERIFY_OTP

Fifth OTP failure:
ENTER_USERNAME -> ENTER_ID -> ENTER_DETAILS -> VERIFY_OTP -> RESTART_FLOW

Resend success:
ENTER_USERNAME -> ENTER_ID -> ENTER_DETAILS -> VERIFY_OTP
-> RESEND_OTP -> VERIFY_OTP

Sixth resend:
ENTER_USERNAME -> ENTER_ID -> ENTER_DETAILS -> VERIFY_OTP
-> RESEND_OTP -> RESTART_FLOW

Password-policy rejection:
ENTER_USERNAME -> ENTER_ID -> ENTER_DETAILS -> VERIFY_OTP
-> PASSWORD_SETUP -> PASSWORD_SETUP
```

## Return-to-Login action availability

Only on `VERIFY_OTP`, the `UNSUCCESSFUL_OTP_Verfication` presentation resolved from `RESTART_FLOW`, and `PASSWORD_RESET_SUCCESS`.

Terminal OTP outcomes first make context unusable, then return `RESTART_FLOW`; the associated error drives Call Member Hotline presentation and only `returnToLogin` action.

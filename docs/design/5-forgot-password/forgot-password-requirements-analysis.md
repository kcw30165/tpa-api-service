# Forgot Password Requirements Baseline

**Feature:** 0061 - Forgot Password  
**Baseline:** 2026-07-30  
**Gate:** 3  

## Scope

Forgot Password is a pre-login recovery journey. Username maps one-to-one to a trust. Post-login Change Password, Username Recovery and captcha are excluded.

## Updated happy path

```text
ENTER_USERNAME -> ENTER_ID -> ENTER_DETAILS -> VERIFY_OTP
-> PASSWORD_SETUP -> PASSWORD_RESET_SUCCESS
```

1. Validate username through `TRPChkCustId`.
2. Create `AuthFlowSession`, issue `auth_flow_sid`, and return the raw CSRF token in the response body.
3. FE submits `identityType` and `identityValue`; BFF checks both against protected TAS `id-type` and `user-id`.
4. FE submits DOB and exactly one email/HK mobile; TAS matches through `TRPSearchRegedMember`.
5. On match, OTP is sent immediately to the submitted contact.
6. OTP success creates one-time reset authorization.
7. Password equality is validated by FE/Java; TAS applies policy and reset.
8. Confirmed success consumes authorization, marks Completed and returns `PASSWORD_RESET_SUCCESS` with `returnToLogin` action.

## Identity contract

Public identity types are `GPN`, `GPFS`, `STAFF_NO`, `ID`; Java uses `STAFFNO`. TAS mapping is `GPN->GPN`, `GPFS->GPFS`, `STAFF->STAFF_NO`, `ID->ID`. BFF uses explicit mapping and trims only `identityValue`. `scheme-code` is not used in identity checking.

## OTP baseline

Configuration-driven: six digits, 300-second TTL, 60-second resend cooldown, five verification attempts per issued OTP, five allowed resends, sixth rejected, cumulative resend count, latest-code-only. `RESEND_OTP` is an action transition, not a page.

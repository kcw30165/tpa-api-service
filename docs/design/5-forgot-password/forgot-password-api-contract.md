# Forgot Password FE-BFF Logical API Contract

**Feature:** 0061 - Forgot Password  
**Baseline:** 2026-07-30  
**Gate:** 3  

## Common model

Use the shared response with `success`, `status`, `result.nextStep`, `result.data.actions`, `messages`, and `errors`. `pageRef` is excluded. Blocking failures may return `result:null`; FE remains on the current operation page.

## Check identity request

```json
{"identityType":"STAFF_NO","identityValue":"3010008"}
```

Allowed values: `GPN`, `GPFS`, `STAFF_NO`, `ID`.

## Check identity success

```json
{"success":true,"status":"SUCCESS","result":{"nextStep":"ENTER_DETAILS","data":{}},"messages":[],"errors":[]}
```

## Error summary

- Invalid type: `VALIDATION_FAILED`, FORM, `err.forgot-password.incorrect.idType`, target `identityType`, `result:null`.
- Mismatch: `BUSINESS_REJECTED`, FORM, `err.forgot-password.incorrect.id`, target `identityValue`, `result:null`.

## Success completion

Confirmed reset returns `nextStep:PASSWORD_RESET_SUCCESS` and a unique `returnToLogin` action. `RETURN_TO_LOGIN` is not a next step.

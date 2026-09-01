# Forgot Password APIM/TAS Contract

**Feature:** 0061 - Forgot Password  
**Baseline:** 2026-07-30  
**Gate:** 3  

## Shared rules

POST through existing APIM facade; reuse OAuth/certificates/encryption/envelope/logging/retry. Propagate only `X-Request-Id`.

## Paths

- `/ws/NGTPA/v1/TRPChkCustId`
- `/ws/NGTPA/v1/TRPSearchMember`
- `/ws/NGTPA/v1/TRPResetPassword`

## TRPChkCustId success

```json
{"response":{"err-message":"","data":[{"cust-id":"db01","isExist":true,"scheme-code":"DBA","user-id":"3010008","id-type":"STAFF"}]}}
```

`user-id` and explicitly mapped `id-type` are protected for identity verification. `scheme-code` is not used by check identity.

## Error and retry

Recognized search/reset business errors follow approved mapping. Other non-empty `err-message` uses service-unavailable fallback and is not a network failure. Reset post-transmission timeout/connection loss is `UNKNOWN_OUTCOME`, public `SYSTEM_ERROR`, no automatic repeat. Password is not field-level encrypted/decrypted.

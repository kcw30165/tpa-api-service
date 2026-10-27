# 03 Downstream contract

## User-supplied authoritative business contract
POST /ws/NGTPA/v1/TRPPwdValidation

```json
{"cust-id":"<server-resolved login username>","password":"<unchanged entered password>"}
```

No accountEnv in request body or account-environment header. Business outcomes use HTTP 200. Reuse existing APIM profile, authentication, encryption and correlation mechanisms; use NON_ACCOUNT scope with no accountEnv and NON_RETRYABLE business-call classification. Do not add a second retry in the use case. Verify lower-level transport/profile retry behavior in tests: one password-validation business attempt must not be silently repeated.

## Successful password
```json
{"response":{"err-message":"","data":[{"success":true,"fail-count":0}]}}
```

## Incorrect password
```json
{"response":{"err-message":"","data":[{"success":false,"fail-count":0}]}}
```

For empty err-message, data is contractually exactly one non-null item. success must be an explicitly present boolean, not a primitive default that turns missing into false. fail-count is downstream-owned; do not compare it with five, derive remaining attempts, expose it to FE or use it to invalidate a session. Downstream owns counting and locking.

## Locked account
```json
{"response":{"err-message":"err.tpa.account.locked"}}
```
Clear the browser session cookie and attempt invalidation of the current authenticated session. No token. Cleanup success -> 423 BUSINESS_REJECTED; cleanup failure -> 503 SYSTEM_ERROR. Do not claim all sessions for this username are invalidated; cross-session revocation is outside the agreed scope.

## Unregistered cust-id
```json
{"response":{"err-message":"err.tpa.cust.id.not.registered"}}
```
Map to 503 SYSTEM_ERROR, not a user-correctable username rejection: cust-id came from authenticated server context. Do not expose this internal inconsistency as an account-existence oracle.

## Parsing and failure rules (authored defensive specification)
Interpret recognized non-empty err-message before requiring data, since the supplied error forms omit it. Unknown non-empty err-message is technical failure, not a new public business status. Missing/null response, missing/null err-message, invalid JSON, empty publisher, absent/null/empty/multiple data for empty err-message, null row, or absent/non-boolean success -> 503 and no token.

Only the empty string is the specified success-path err-message; do not silently trim an unknown code into success. Treat contradictory non-empty err-message plus data as error, never token success. fail-count is diagnostic-only: missing/null/unknown values do not become a lock rule or override success; if DTO decoding cannot parse the envelope, classify it as technical failure. This tolerance is an authored design choice, not an additional downstream guarantee.

APIM response DTOs follow the project's ignore-unknown-fields boundary convention without changing request DTOs or global ObjectMapper. Preserve exact downstream code matching; do not relay raw unknown text. HTTP non-200, timeout, crypto/DTO/envelope failure and transport failure -> 503 SYSTEM_ERROR. Password correctness is unknown on technical failure. Never issue a token from a partial response or infer success from fail-count.

No changes to TRPUpdateMemberInfo or existing Personal Information downstream routing are introduced. The new grant is consumed entirely inside BFF and never forwarded downstream.

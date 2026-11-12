# 03 Downstream contract

## User-supplied authoritative business contract
POST /ws/NGTPA/v1/TRPPwdValidation

```json
{"cust-id":"<server-resolved login username>","password":"<unchanged entered password>"}
```

No accountEnv in request body or account-environment header. Business outcomes use HTTP 200. Reuse existing APIM profile, authentication, encryption and correlation mechanisms; use NON_ACCOUNT scope with no accountEnv and NON_RETRYABLE business-call classification. Do not add a second retry in the use case. Verify lower-level transport/profile retry behavior in tests: one password-validation business attempt must not be silently repeated.

### M1 authorized transport adjustment (2026-10-28)

Source verification at `558a18fd9264047e62e7e273619a199ec0daf347` found that
`NON_RETRYABLE` only disables transient-business retries. The facade separately
resubmits after `invalid_token` and, for encrypted exchanges, `invalid_public_key`.
Existing tests require that recovery for existing operations.

The reviewer-authorized adjustment is an explicit `ApimResubmissionPolicy`
enum in `adapter/out/apim/execution`, separate from `ApimRetryEligibility`:
`ALLOW_RECOVERY` preserves existing behavior; `NEVER` prohibits all resubmission.
`ApimExchangeRequest` gains this eighth component. Its existing seven-argument
constructor defaults to `ALLOW_RECOVERY`, preserving real existing callers.
The canonical constructor rejects a null policy and rejects `NEVER` combined
with business-retry eligibility other than `NON_RETRYABLE`.

The executor propagates `NEVER` to new policy-bearing plain/encrypted
`postSuccessfulExchange` / `postEncryptedSuccessfulExchange` facade overloads;
the existing overloads and default executor path preserve existing behavior.
Private attempt and recovery contexts carry the policy explicitly. Both
token and certificate recovery must consult it before eviction/refresh or
resubmission. Transient-business retry remains separately controlled, with
`NEVER` also prohibiting retry defensively. There are no endpoint-string
checks, global switches, thread-locals or implicit Reactor-context policy.

Only the new `ApimPasswordValidationAdapter` selects `NEVER`, together with
`NON_ACCOUNT`, null accountEnv, and `NON_RETRYABLE`. Acquisition of credentials
or certificates before the first password POST remains permitted. Count
password POSTs independently from those acquisition calls. A recovery rejection
or uncertain outcome is technical failure, not evidence of password correctness.
This is at-most-one client submission per invocation, not exactly-once execution.
Tests must traverse the actual shared executor and facade in plain and encrypted
modes, including both recovery codes, HTTP/transport/timeout/crypto failures and
successful profile/key propagation. Existing recovery/business-retry tests stay
unchanged. No endpoint, session mutation or grant behavior is introduced in M1.

Connector and confidentiality details: the facade's cloned builder for `NEVER`
uses the standard shared Reactor connector with `HttpClient.disableRetry(true)`
and the configured APIM response timeout, preventing connector-level automatic
TCP resubmission as well as application retries. Existing builders/operations
are unchanged. Routing, filters, request encryption and response-key verification
remain in the shared path. A loopback transport test must exercise that connector,
not just an ExchangeFunction. Strict exchanges suppress request-body and raw
response/error-text logging (including executor outcome logs), accept only HTTP
200, and never leak downstream text through the application outcome. These
conservative strict-path behaviors do not change existing operations.

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

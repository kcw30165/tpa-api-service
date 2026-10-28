# 02 API contract

## Shared issuance endpoint
POST /api/v1/password-validation
Content-Type: application/json
Required: Account-Ref, X-XSRF-TOKEN, authenticated browser cookie. Preserve existing X-Request-Id requirements and correlation behavior. Accept-Language follows existing conventions; Java returns codes, not translated message text. No new Idempotency-Key or response replay contract is introduced.

```json
{
  "password": "<entered password>",
  "purpose": "PERSONAL_INFORMATION_UPDATE",
  "operationPayload": {
    "formVersion": "<current form version>",
    "applyToAllAccounts": false,
    "fields": {"<existing field id>": "<reviewed value>"}
  }
}
```

Examples use placeholders, not real credentials or asserted production form versions. password is a required JSON string preserved exactly and checked against configured login length bounds. purpose is exactly PERSONAL_INFORMATION_UPDATE; reject unknown or future purposes before APIM. operationPayload is an object conforming to the existing Personal Information request structure. No cust-id, accountEnv or username is accepted as an identity override. Unknown top-level issuance members are rejected (authored strict-envelope specification). The existing fields map is not narrowed by this feature.

## Success
HTTP 200; Cache-Control: no-store. Generic MutationResponse with a feature result.data wrapper (authored DTO shape):
```json
{
  "success": true,
  "status": "SUCCESS",
  "result": {"data": {"token": "<opaque token>", "expiresInSeconds": 300}},
  "messages": [],
  "errors": []
}
```
No password, username, fail-count or reviewed changes are echoed. Session expiration may make a grant unusable before its five-minute expiry. A response lost after successful issuance is not replayed; another successful confirmation replaces the grant.
`expiresInSeconds` is the remaining whole lifetime at acknowledgement, capped at
300; it can be 299 after storage latency. It never renews the stored expiry.
(Corrected, source-grounded: the JSON example above nests the token fields under `result.data`, matching the project's established `result.data` wrapper convention (e.g. `PersonalInformationUpdateResultResponse`). The concrete type is therefore `MutationResponse<PasswordValidationResult>` where `PasswordValidationResult` is a new record `PasswordValidationResult(Data data)` and `Data` is a new nested record `Data(String token, Integer expiresInSeconds)` -- not `{token, expiresInSeconds}` placed directly as `T`, which would omit the `data` nesting shown in the example and contradict it. `MutationResponse<T>` itself (`adapter.in.web.response.MutationResponse`) is the actual existing generic envelope class, used unmodified; `ApiStatus.SUCCESS`, `VALIDATION_FAILED`, `BUSINESS_REJECTED` and `SYSTEM_ERROR` are existing enum constants on `adapter.in.web.response.ApiStatus`; no new status enum is introduced by this feature.)

## Protected submission
PUT /api/v1/personal-information
Same authenticated transport plus exactly one X-Password-Validation-Token header. The body is the exact operationPayload object reviewed at issuance, not the issuance envelope. Do not send password again.

The header is mandatory; missing, malformed, duplicate, expired, consumed or scope-invalid token returns 403 BUSINESS_REJECTED without update dispatch. A valid current token is consumed before body decoding. Payload mismatch returns the same authorization-rejected code after consumption. Session/CSRF/account failures take precedence and preserve existing authentication mappings. Do not globally alter those mappings to fit this feature.

Success and existing business validation/update results retain the current Personal Information response contract. The new authorization gate must not redesign its account result rows or errors.

## Feature error vocabulary
These names are newly authored API design identifiers, not claims of existing ErrorCodes constants. Register them centrally and pin them with contract tests before implementation merge.

| Condition | HTTP | status | errors[].code | target |
|---|---:|---|---|---|
| Issuance request invalid | 400 | VALIDATION_FAILED | err.password-validation.request.invalid | offending public field |
| Wrong password | 401 | BUSINESS_REJECTED | err.password-validation.password.invalid | password |
| Account locked, cleanup succeeded | 423 | BUSINESS_REJECTED | err.password-validation.account.locked | PAGE |
| Token rejected / payload mismatch | 403 | BUSINESS_REJECTED | err.password-validation.authorization.invalid | X-Password-Validation-Token |
| Throttled | 429 | BUSINESS_REJECTED | err.password-validation.rate-limited | PAGE |
| Technical failure / invalidation failed | 503 | SYSTEM_ERROR | err.password-validation.unavailable | PAGE |

M3 reuses authentication-required 401 / err.security.authentication.required
when the session disappears or becomes unusable before the grant write. Missing
Account-Ref retains 400 BUSINESS_REJECTED / err.member.context.invalid. Missing
CSRF/session or an unowned account is authentication-required 401; incorrect
CSRF is the route-local existing access-denied 403 BUSINESS_REJECTED /
err.security.access.denied rendering (D17). All are code-only and no-store.
Only the existing contract vocabulary is reused; no global mapping is changed.

Use the existing ApiError structure, message null, severity ERROR, source SERVER. Existing authentication and Personal Information errors retain their current codes and targets. Malformed mutation JSON is 400 VALIDATION_FAILED using existing request-validation rendering; it consumes a valid grant first. Do not expose DOWNSTREAM_REJECTED or DOWNSTREAM_ERROR publicly. No fail-count in errors or result. For 429 return Retry-After in whole seconds until the limiter window resets (authored response detail).

## FE integration
1. Freeze the reviewed payload and serialize one confirmation request per scope.
2. Send password/purpose/frozen payload; retain token only in memory.
3. Submit that frozen payload with the token header once.
4. Discard the token regardless of submission outcome. On 403 request confirmation again; on 400 correct changes and confirm again.
5. On wrong-password 401 retain the session and show the password error. On locked 423 clear local authenticated state and navigate to login. On cleanup-failure 503 the cookie is still cleared; do not treat the session as usable.
6. Do not automatically retry password validation or mutation after a network timeout. Reconfirming does not prove the previous mutation failed.

CORS/proxy acceptance of the new header is a release check, not assumed existing configuration. Do not propagate the authorization token or FE password-validation purpose to APIM.

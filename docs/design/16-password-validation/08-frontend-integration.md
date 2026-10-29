# 08 Frontend integration

Audience: the frontend team. Scope: Personal Information update, the only supported purpose in this release (`PERSONAL_INFORMATION_UPDATE`). Behaviour below is taken from the approved design and the tests listed in [10 As-built traceability](10-as-built-traceability.md). Nothing in this document has been exercised from a real browser yet; see the open items in [09 Release and operations](09-release-and-operations.md).

## 1. Flow

1. The user reviews their changes on the confirmation screen. **Freeze the reviewed payload now.** It is the object `{ "formVersion": ..., "applyToAllAccounts": ..., "fields": { ... } }` that will later be sent to `PUT /api/v1/personal-information`.
2. The user types their password. Call `POST /api/v1/password-validation` with the password, the purpose and the frozen payload.
3. On HTTP 200, keep `result.data.token` in memory only.
4. Call `PUT /api/v1/personal-information` with the header `X-Password-Validation-Token: <token>`. The body is **exactly the frozen payload** (not the issuance envelope, and no password).
5. Discard the token after the PUT, whatever the result.

Every call uses the existing authenticated browser cookie, the `X-XSRF-TOKEN` header and the `Account-Ref` header, and should carry an `X-Request-Id`, as for all other BFF calls.

## 2. Issuance: POST /api/v1/password-validation

Request body (JSON):

```json
{
  "password": "<entered password>",
  "purpose": "PERSONAL_INFORMATION_UPDATE",
  "operationPayload": { "formVersion": "...", "applyToAllAccounts": false, "fields": { } }
}
```

- Only these three members are accepted. Any other member is rejected with 400. Do not send a username or customer id; the server takes identity from the session.
- The password is used exactly as typed: no trimming, no case change. Its length must be within the login password bounds configured on the server.
- A duplicate member anywhere in the JSON, including inside `operationPayload`, is rejected with 400.

Success (HTTP 200):

```json
{ "success": true, "status": "SUCCESS", "result": { "data": { "token": "<opaque>", "expiresInSeconds": 300 } }, "messages": [], "errors": [] }
```

`expiresInSeconds` is the lifetime the server will honour (300 in this release). The server conservatively counts from when it stored the token, so use it as an upper bound.

## 3. The token

- Opaque, single use, valid for at most 300 seconds.
- Bound to this session, this `Account-Ref`, this purpose and **this exact payload**.
- One active token per session, account and purpose. Issuing a new token replaces the previous one.
- The first PUT that presents a valid token **consumes it**, even if the body is malformed, differs from the reviewed payload, or later fails validation. The user must then confirm their password again.
- Send it only in the `X-Password-Validation-Token` header, exactly once. Never put it in a URL, never store it, never log it.

### What "the same payload" means

| Compared | Rule |
|---|---|
| Object member order | Ignored, at every depth |
| Numbers | Compared by exact decimal value; `1` equals `1.0`, but `1` does not equal `"1"` |
| Strings | Exact, including case, spaces and Unicode form |
| Array order | Significant |
| `null` versus a missing member | Different |
| `formVersion`, `applyToAllAccounts` | Included; changing either needs a new confirmation |
| Unknown members | Included; do not add, remove or rename anything between issuance and PUT |

The server compares the raw JSON text, before any mapping, so a field the form ignores still counts.

## 4. Statuses to handle

Branch on `errors[0].code`, **not on the HTTP status alone**. Two different failures both use 401.

### Issuance

| HTTP | `errors[0].code` | Meaning | Frontend action |
|---|---|---|---|
| 200 | none | Password correct, token issued | Continue to the PUT |
| 400 | `err.password-validation.request.invalid` | Malformed envelope, wrong purpose or password length out of bounds | Fix the request; this should not happen in normal use |
| 400 | `err.member.context.invalid` | `Account-Ref` missing | Existing behaviour |
| 401 | `err.password-validation.password.invalid` | Wrong password | Show the wrong-password message and keep the session. The downstream system counts failures and can lock the account |
| 401 | `err.security.authentication.required` | Session missing, expired or invalid | Send the user to login |
| 403 | `err.security.access.denied` | Invalid CSRF token | Refresh the CSRF token as usual. On these two routes this code is returned by a route-specific mapping, which differs from the logout endpoint's 403 shape |
| 423 | `err.password-validation.account.locked` | Account locked | The server has cleared the session cookie. Clear local authenticated state and send the user to login |
| 429 | `err.password-validation.rate-limited` | Too many confirmations (10 per 60 seconds per user by default, shared across sessions) | Wait and let the user retry. See the `Retry-After` note below |
| 503 | `err.password-validation.unavailable` | Technical failure (downstream, storage, configuration) | Show a generic error; offer a manual retry |

### Update: PUT /api/v1/personal-information

| HTTP | `errors[0].code` | Meaning | Frontend action |
|---|---|---|---|
| 403 | `err.password-validation.authorization.invalid` | Token missing, wrong, expired, already used, or the body differs from the reviewed payload | Ask for the password again and start a new confirmation |
| 400 | `err.request.invalid` | The body is not valid JSON (the token has been consumed) | Correct the request and confirm the password again |
| 503 | `err.password-validation.unavailable` | The token check failed technically; nothing was updated | Confirm the password again and retry manually |
| other | existing codes | Session, CSRF, validation and update outcomes | Unchanged from today |

When the token is valid and the body matches, the response is exactly the existing Personal Information response.

## 5. Rules for the frontend

- **No automatic retries**, of either call. The password is sent once per user action, and the downstream system counts failed attempts.
- A **timeout on the PUT does not prove the update failed.** Read the current data before offering the user another attempt. A new attempt needs a new password confirmation, because the token is consumed.
- Allow **one confirmation request in flight** per account and purpose. Ignore a response that belongs to a superseded request, and use the token from the newest successful one.
- The server returns **codes, not message text.** The frontend owns all wording.
- Do not keep the password after the call returns.

## 5.1 Browser and network requirements

- `X-Password-Validation-Token` must be allowed by CORS. The default list includes it. If a deployment overrides `CORS_ALLOWED_HEADERS`, that value must include it or the browser will block every update.
- The service does not set `Access-Control-Expose-Headers`. For a cross-origin frontend, JavaScript cannot read `Retry-After` or `X-Request-Id`. Either serve the frontend same-origin, ask for those headers to be exposed, or use a fixed back-off of about 60 seconds for a 429.

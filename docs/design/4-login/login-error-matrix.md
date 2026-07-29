> Status: Design baseline v0.1
> Date: 20 July 2026
> Scope: Subsequent member login plus reusable authentication foundations
> Source baseline: Login BRD V0.3, discovery decisions in 02-brd.md through 10-brd.md, and the supplied current codebase snapshot
> Open external dependency: detailed `TRPGetAccountList` business failure-code mapping remains TBC.

# Login Error Matrix

Java returns codes only. FE owns displayed text and actions.

| Scenario                                                                          | HTTP | Status            | Code                                     | APIM Code                           | Next step                                     | Session action                                   |
| --------------------------------------------------------------------------------- | ---: | ----------------- | ---------------------------------------- | ----------------------------------- | --------------------------------------------- | ------------------------------------------------ |
| Username/password request invalid                                                 |  400 | VALIDATION_FAILED | `err.login.credentials.invalid`        |                                     | LOGIN_CREDENTIALS                             | None                                             |
| Empty`TRPMemberLogin` envelope                                                  |  503 | SYSTEM_ERROR      | `err.login.credentials.unavailable`    |                                     | LOGIN_CREDENTIALS                             | None                                             |
| Empty`TRPGetAccountList` envelope                                               |  503 | SYSTEM_ERROR      | `err.login.accountList.unavailable`    |                                     | LOGIN_CREDENTIALS                             | None                                             |
| `TRPMemberLogin`<br />Invalid password                                          |  401 | BUSINESS_REJECTED | `err.login.incorrect.password`         | `err.tpa.wrong.password`          | LOGIN_CREDENTIALS                             | None                                             |
| `TRPMemberLogin`<br />Invalid username                                          | 401  | BUSINESS_REJECTED | `err.login.incorrect.username`         | `err.tpa.no.valid.account`        | LOGIN_CREDENTIALS                             | None                                             |
| `TRPMemberLogin`<br />Account locked by TAS                                     |  423 | BUSINESS_REJECTED | `err.login.locked.account`             | `err.tpa.account.locked`          | LOGIN_CREDENTIALS                             | None                                             |
| `TRPMemberLogin`<br />No valid account                                          |  423 | BUSINESS_REJECTED | `err.login.invalid.account`            | `eer.tpa.cust.id.not.registered` | LOGIN_CREDENTIALS                             | None                                             |
| Missing pre-auth cookie                                                           |  401 | BUSINESS_REJECTED | Existing shared session code             |                                     | LOGIN_CREDENTIALS                             | None                                             |
| Missing/invalid CSRF                                                              |  403 | BUSINESS_REJECTED | Existing shared CSRF code                |                                     | Current safe step                             | None                                             |
| Invalid destination ID                                                            |  400 | VALIDATION_FAILED | `err.login.otp.destination.invalid`    |                                     | SELECT_OTP_DESTINATION                        | None                                             |
| OTP verification attempts exhausted                                               |  429 | BUSINESS_REJECTED | `err.login.otp.verifyAttemptsExceeded` |                                     | Resend using existing OTP next-step semantics | Retain flow; invalidate challenge                |
| OTP resend attempts exhausted                                                     |  429 | BUSINESS_REJECTED | `err.login.otp.resendAttemptsExceeded` |                                     | LOGIN_CREDENTIALS                             | Terminate pre-auth flow                          |
| `TRPGetAccountList`<br />Account-list empty/unavailable after internal attempts |  503 | SYSTEM_ERROR      | `err.login.accountList.unavailable`    |                                     | LOGIN_CREDENTIALS                             | Terminate pre-auth flow                          |
| `TRPGetAccountList`<br />Account records returned but zero valid                |  502 | SYSTEM_ERROR      | `err.login.accountList.unavailable`    |                                     | LOGIN_CREDENTIALS                             | Terminate pre-auth flow                          |
| `TRPGetAccountList`<br />returns no valid account for the login member          |  401 | SYSTEM_ERROR      | `err.login.accountList.unavailable`    | `err.tpa.no.active.account`       | LOGIN_CREDENTIALS                             | Terminate pre-auth flow                          |
| `TRPGetAccountList`<br />returns the username is not existed                    |  401 | SYSTEM_ERROR      | `err.login.accountList.unavailable`    | `err.tpa.no.valid.login.ac`       | LOGIN_CREDENTIALS                             | Terminate pre-auth flow                          |
| Authenticated-session persistence fails                                           |  503 | SYSTEM_ERROR      | `err.login.session.unavailable`        |                                     | LOGIN_CREDENTIALS                             | Keep old active session; terminate pre-auth flow |
| Missing`portal_sid`                                                             |  401 | BUSINESS_REJECTED | Authentication-required code             |                                     | LOGIN_CREDENTIALS                             | None                                             |
| Expired`portal_sid`                                                             |  401 | BUSINESS_REJECTED | Session-expired code                     |                                     | LOGIN_CREDENTIALS                             | Clear portal transport                           |
| Session replaced                                                                  |  401 | BUSINESS_REJECTED | `err.login.session.replaced`           |                                     | LOGIN_CREDENTIALS                             | Clear portal transport                           |
| Missing AccountRef on scoped endpoint                                             |  400 | VALIDATION_FAILED | `err.member.context.invalid`           |                                     | NONE                                          | None                                             |
| Foreign AccountRef                                                                |  403 | BUSINESS_REJECTED | Access-denied/account-context code       |                                     | NONE                                          | None                                             |
| Logout                                                                            |  200 | SUCCESS           | None                                     | LOGIN_CREDENTIALS                   | Revoke and clear transport                    |                                                  |

## Logging

- 4xx outcomes: WARN according to existing HTTP logging policy.
- APIM, contract, crypto, Redis and unexpected failures: ERROR.
- Malformed account records are logged at ERROR without username, policy number, certificate number, AccountRef, destination, session ID, or raw APIM response.
- Detailed Progress codes remain internal and are mapped through configuration.

## TRPMemberLogin envelope compatibility rules

- A known business `err-message` with no data is mapped to its BFF-owned business outcome.
- `err.tpa.wrong.password` may include exactly one data item with `success=false`; Java retains `fail-count` internally and returns `err.login.incorrect.password`.
- A known error combined with incompatible data, including `success=true`, a missing success flag, multiple items, or non-password error data, is contradictory and maps to the safe unavailable outcome.
- Unknown downstream codes, malformed envelopes, invalid cardinality, and indeterminate success flags map to the safe unavailable outcome.
- Raw `err.tpa.*` values never cross the public API boundary.

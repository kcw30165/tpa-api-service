# Account Endpoints -- Implementation Plan

## 1. Shared component design (avoids duplication)

Both endpoints share one adapter and port, since both are variations on
"resolve accounts from the current authenticated session":

- `AccountLookupPort` (outbound port, `application/port/out`):
  - `Mono<List<AccountContext>> listAccounts(String sessionId)`
  - `Mono<AccountContext> getAccount(String targetAccountRef, String sessionId)`
- `AuthenticatedAccountLookupAdapter` implements both methods against the
  same `LoadAndTouchAuthenticatedSessionUseCase` session load used by the
  prior design -- `listAccounts` maps every entry in `session.accounts()`
  with no filter; `getAccount` keeps the existing `session.findAccount(ref)`
  logic. No session-load logic is duplicated between the two methods.

Two use cases consume this one port:

- `GetAccountsUseCase` / `GetAccountsService` -- list all (naming matches the
  existing `GetLoginAccountsUseCase` convention already used elsewhere).
- `GetAccountUseCase` / `GetAccountService` -- get one (naming matches the
  existing `GetAccountSummaryUseCase` convention already used elsewhere).

One controller, `AccountController`, exposes both endpoints:

- `POST /api/v1/accounts` -> `GetAccountsUseCase`
- `POST /api/v1/account` -> `GetAccountUseCase`

Both actions share the same cookie-read -> CSRF-check -> session-validation
sequencing already proven by the prior design (mirroring `LogoutController`).

## 2. Response DTOs (avoiding the existing `AccountSummary` name collision)

`AccountSummary` is already used by the Account Balance and Account Gain Loss
features for a different, larger shape -- a new, distinct name is required
here:

- `AccountDetailsResponse(accountRef, policyNo, certNo, accountEnv, trustCode, schemeType)`
  -- the per-account shape, shared by both endpoints.
- `AccountsDataResponse(List<AccountDetailsResponse> accounts)` + wrapper
  `AccountsResultResponse(AccountsDataResponse data)` -- for the list endpoint.
- `AccountDataResponse` is `AccountDetailsResponse` itself, wrapped directly
  as `AccountResultResponse(AccountDetailsResponse data)` -- for the
  single-get endpoint (no separate inner data record needed since there is
  only one account, not a nested collection).

Both wrapper records mirror the existing
`AccountSummaryResultResponse(AccountSummaryDataResponse data)` convention
exactly, so `MutationResponse<T>.result` produces `result.data.*` correctly
for both endpoints.

## 3. Full rename mapping (old -> new)

| Old (accountswitch) | New (account) |
|---|---|
| `adapter/in/web/accountswitch/AccountSwitchController.java` | `adapter/in/web/account/AccountController.java` |
| `adapter/in/web/accountswitch/AccountSwitchErrorKey.java` | `adapter/in/web/account/AccountErrorKey.java` |
| `adapter/in/web/accountswitch/AccountSwitchRequest.java` | `adapter/in/web/account/AccountRequest.java` |
| `adapter/in/web/accountswitch/AccountSwitchResponse.java` | `adapter/in/web/account/AccountDetailsResponse.java` (+ new `AccountsDataResponse`, `AccountResultResponse`, `AccountsResultResponse`) |
| `adapter/in/web/accountswitch/AccountSwitchWebMapper.java` | `adapter/in/web/account/AccountWebMapper.java` |
| `application/port/out/AccountSwitchLookupPort.java` | `application/port/out/AccountLookupPort.java` |
| `application/usecase/accountswitch/AccountNotInSessionException.java` | `application/usecase/account/AccountNotInSessionException.java` (name unchanged, package moved) |
| `application/usecase/accountswitch/AuthenticatedAccountSwitchLookupAdapter.java` | `application/usecase/account/AuthenticatedAccountLookupAdapter.java` |
| `application/usecase/accountswitch/SwitchAccountCommand.java` | `application/usecase/account/GetAccountCommand.java` (+ new `GetAccountsCommand`, if needed) |
| `application/usecase/accountswitch/SwitchAccountService.java` | `application/usecase/account/GetAccountService.java` (+ new `GetAccountsService`) |
| `application/usecase/accountswitch/SwitchAccountUseCase.java` | `application/usecase/account/GetAccountUseCase.java` (+ new `GetAccountsUseCase`) |
| `config/accountswitch/AccountSwitchConfig.java` | `config/account/AccountConfig.java` |
| `test/.../accountswitch/AccountSwitchControllerTest.java` | `test/.../account/AccountControllerTest.java` |
| `test/.../accountswitch/AccountSwitchWebMapperTest.java` | `test/.../account/AccountWebMapperTest.java` |
| `test/.../accountswitch/SwitchAccountServiceTest.java` | `test/.../account/GetAccountServiceTest.java` (+ new `GetAccountsServiceTest`) |

All prior `accountswitch` packages and files are deleted entirely -- nothing
retains the "switch" name.

## 4. Sequencing for the next script

1. Delete all 14 old `accountswitch`-named files (listed above) and the
   associated Spring config.
2. Create the new `account` package structure with both use cases, the
   shared port/adapter, the new DTOs, and `AccountController`.
3. Fail-first or direct-implementation tests per component, following the
   same gated workflow as before (compile, targeted tests, full `mvn test`,
   startup smoke test with `-Dspring-boot.run.profiles=local`,
   `mvn spotless:apply` before the final commit).

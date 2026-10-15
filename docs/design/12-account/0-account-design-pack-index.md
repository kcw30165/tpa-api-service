# Design Pack 12: Account Endpoints (list + get one)

## Status

Supersedes the earlier "Account Switch" design (previously numbered pack
12-account-switch). That pack described a single `POST /api/v1/account/switch`
endpoint. Following further discussion of the real frontend need, the design
has changed to two endpoints with no "switch" semantics at all, since no
server-side state transition ever occurs.

## Documents in this pack

1. `account-api-contract.md` -- request/response schemas for both endpoints.
2. `account-error-matrix.md` -- status/HTTP code mapping for every failure mode.
3. `account-implementation-plan.md` -- architecture, shared-component design,
   and the full file rename mapping from the old `AccountSwitch*`/`SwitchAccount*`
   naming to the new `Account*`/`GetAccount*` naming.

## Why this changed

The original design assumed the frontend always retains the full account list
(with `policyNo`/`certNo`/etc.) from the post-login OTP-verify response, and
only needed a way to select a *different* already-known account. In practice,
if the frontend does not persist that list, it has no data to render an
account-switch picker UI at all. The real need is:

- A way to **re-fetch the full account list** at any point during the
  session (not just once, right after login).
- A way to **look up a single account's display details** by `accountRef`.

Neither operation changes any server-side state -- the authenticated session
already resolves whichever account the `Account-Ref` header specifies on each
call, as it always has. These are pure, stateless lookups.

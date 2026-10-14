# Account Switch Design Pack Index

**Feature:** Account Switch
**Status:** Implemented (as-built documentation)
**Implementation status:** Complete. Three commits landed on `feature/account-switch-endpoint`.
**Target branch:** `feature/account-switch-endpoint` (created from `origin/dev`)

## Purpose

After `POST /api/v1/auth/login/otp/verify` succeeds, Java returns the full list of accounts
accessible to the authenticated actor. FE stores only the opaque `accountRef` for each account and
had no in-portal mechanism to let the user pick a different account and retrieve its display
metadata. This pack documents the stateless `POST /api/v1/account/switch` endpoint built to close
that gap, along with the architecture conflict encountered during implementation and its
resolution.

This pack is written **after** implementation, documenting the as-built design rather than
governing an unimplemented plan. It exists so the final architecture, API contract, and error
behavior are recorded accurately for future reference and MR review.

## Documents

1. `account-switch-api-contract.md`
2. `account-switch-error-matrix.md`
3. `account-switch-implementation-plan.md`

## Governance

- No session mutation occurs; this feature is strictly stateless. FE continues to send the
  `Account-Ref` header on every subsequent authenticated call as it does today.
- `PortalAccessContextPort` remains exclusively reserved for `PortalAccessContextWebFilter`, per
  pre-existing architecture guards (`PortalAccessContextResponsibilityBoundaryGuardTest`,
  `PortalAccessContextFinalMigrationEvidenceTest`). This feature introduces a dedicated
  `AccountSwitchLookupPort` instead of extending or bypassing that boundary.
- Session-invalid and account-not-in-session are deliberately distinguished as separate failure
  modes (401 vs. 422 respectively) — see `account-switch-error-matrix.md`.

## Implementation commits

1. `87304bf6` — foundation layer: `AuthenticatedSessionCookieReader`, `AccountSwitchLookupPort`,
   `AuthenticatedAccountSwitchLookupAdapter`, `SwitchAccountUseCase`/`SwitchAccountService`.
2. `3487a434` — HTTP layer: `AccountSwitchController`, request/response DTOs, mapper,
   `AccountSwitchConfig` Spring wiring.
3. `dde04073` — session-invalid (401) vs. account-not-in-session (422) failure-mode split.

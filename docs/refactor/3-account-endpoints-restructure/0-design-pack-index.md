# Account Endpoints Restructure and Account Summary Design Pack

**Scope owner:** Nick Deng
**Branch:** feature/account-endpoints-restructure-and-summary
**Base:** dev @ 19a4b41c061ae58a34cb156232435e3eaf7d6845

## Change set

1. Rename `GET /api/v1/account-balance` to `GET /api/v1/account/balance`.
2. Rename `GET /api/v1/account-gain-loss` to `GET /api/v1/account/gain-loss`.
3. Add `GET /api/v1/account/summary`, which returns the Account Balance payload
   plus one additional gain/loss breakdown section representing the Portfolio
   Overview / Holdings UI, composed from both `TRPGetMemberAccBal` and
   `TRPGetInvestGL`.

## Documents

- 1-endpoint-rename-contract.md
- 2-account-summary-api-contract.md
- 3-account-summary-composition-contract.md
- 4-account-summary-error-contract.md
- 5-decisions-open-questions.md
- 6-implementation-plan.md
- 7-account-balance-deprecation-plan.md
- handover.md

## Preserved baselines

This pack does not supersede the Account Balance pack in
`docs/design/7-account-balance/` or the Account Gain/Loss pack in
`docs/design/8-account-gain-loss/`. Both remain authoritative for their own
response bodies, transformation rules, and error mappings. This pack changes
only the public path segments and adds one composed read endpoint.

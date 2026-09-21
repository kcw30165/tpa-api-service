# Endpoint Rename Contract

## Path changes

| Before | After | Method |
| --- | --- | --- |
| /api/v1/account-balance | /api/v1/account/balance | GET |
| /api/v1/account-gain-loss | /api/v1/account/gain-loss | GET |

Request headers, query parameters, response bodies, status codes, and error
mappings are unchanged. Only the URL path segment changes.

## Controller impact

`AccountBalanceController` and `AccountGainLossController` both use
`@RequestMapping("/api/v1")` with a `@GetMapping` path segment. The class-level
mapping is left as `/api/v1`; only the method-level values change to
`/account/balance` and `/account/gain-loss`. This keeps both controllers
consistent with the existing repository pattern and avoids touching
`ContributionController` or `NotificationController`, which share `/api/v1`.

## Non-path identifiers explicitly NOT renamed

The following remain unchanged because they are configuration and observability
identifiers, not public URL paths. Renaming them would be unrelated churn and
would break existing environment overrides and log-based dashboards.

- `apim.account-balance.path` / `APIM_ACCOUNT_BALANCE_PATH`
- `apim.account-gain-loss.path` / `APIM_ACCOUNT_GAIN_LOSS_PATH`
- `AccountBalanceApimProperties` prefix `apim.account-balance`
- `AccountGainLossApimProperties` prefix `apim.account-gain-loss`
- Observability event names `account-balance.completed`,
  `account-balance.failed`, `account-balance.presentation.factsheet.omitted`,
  `account-gain-loss.downstream-rejected`
- Java package names `accountbalance` and `accountgainloss`
- The Redis capability name `general-metadata`

## Call sites requiring update

Public path literals occur in:

- `AccountBalanceController` (`/account-balance`)
- `AccountGainLossController` (`/account-gain-loss`)
- `AccountBalanceControllerTest` (four `.uri(...)` literals)
- `AccountGainLossPublicContractTest` (one path literal)
- `scripts/e2e/run-account-balance-local-stubbed.sh`
- `scripts/e2e/scenarios/account-balance/list-authenticated.sh`
- `scripts/e2e/run-account-balance-regression.sh` (marker list)
- `docs/design/7-account-balance/*` and `docs/design/8-account-gain-loss/*`
  contract text

E2E script *filenames* and scenario-pack directory names are not renamed.

## Compatibility decision

No dual-routing alias is provided. The old paths are removed, not deprecated.
The BFF is consumed by the ORSO front end only, and the front end is changed in
the same correlated release.

Ratified as Q-1 on 21/09/2026: the cutover is atomic. The front-end release and
this backend release must be deployed together, because the old paths return 404
the moment this change is live.

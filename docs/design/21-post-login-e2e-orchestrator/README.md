# Post-login E2E orchestration foundation

## Status

Approved design for a curl/Bash E2E orchestration layer. It does not change Java production behavior or API contracts.

## Confirmed requirements

- Validate, login once, run every selected feature pack in order, logout once, and summarize.
- `E2E_FEATURE_PACKS` is an explicit ordered allow-list. `investment-choice` maps to `scripts/e2e/investment-choice-scenarios.sh`; read-only `vesting-scale` maps to `scripts/e2e/vesting-scale-scenarios.sh`.
- Continue after feature failures. Login failure prevents features. Logout failure fails the run.
- Choose the first email destination, fail if none exists, prompt securely for OTP, and choose an account from the verified login response.
- Select the account once for the whole run with zero-based `E2E_ACCOUNT_INDEX` (default `0`), or retain exact-match selection with `E2E_ACCOUNT_REF`; reject malformed indices and non-empty selector conflicts before login, and fail closed for an out-of-range index.
- The `vesting-scale` pack sends exactly two GETs with explicit English and Traditional Chinese language headers, validates the documented response invariants, and writes summary-only evidence. `VESTING_SCALE_EXPECT_ABSENT` accepts only the documented configurable BFF fields and rejects duplicates or unknown names before login.
- Keep authentication private and run-scoped, supplying `portal_sid`, `X-XSRF-TOKEN`, and `Account-Ref` through the existing HTTP client.
- Keep OTP acquisition replaceable for later real-email integration. Mailbox access is out of scope.
- Preserve anonymous and standalone provided-session modes.

See `architecture.md` and `implementation-plan.md`.

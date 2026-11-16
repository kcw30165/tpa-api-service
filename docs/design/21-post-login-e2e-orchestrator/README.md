# Post-login E2E orchestration foundation

## Status

Approved design for a curl/Bash E2E orchestration layer. It does not change Java production behavior or API contracts.

## Confirmed requirements

- Validate, login once, run every selected feature pack in order, logout once, and summarize.
- `E2E_FEATURE_PACKS` is an explicit ordered allow-list. Initially, `investment-choice` maps to `scripts/e2e/investment-choice-scenarios.sh`.
- Continue after feature failures. Login failure prevents features. Logout failure fails the run.
- Choose the first email destination, fail if none exists, prompt securely for OTP, and choose the first account in API order.
- Keep authentication private and run-scoped, supplying `portal_sid`, `X-XSRF-TOKEN`, and `Account-Ref` through the existing HTTP client.
- Keep OTP acquisition replaceable for later real-email integration. Mailbox access is out of scope.
- Preserve anonymous and standalone provided-session modes.

See `architecture.md` and `implementation-plan.md`.

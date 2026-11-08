# BFF Scripted End-to-End Framework

This repository-owned curl framework replaces Postman as the authoritative local functional-validation mechanism. It is feature-neutral and supports scenario packs.

## Safe self-test

```bash
E2E_SCENARIO_PACK=platform E2E_SCENARIO_ID=controlled-assertion-self-test bash scripts/e2e/run.sh
```

## Readiness request

```bash
BFF_BASE_URL=http://localhost:8080 E2E_SCENARIO_PACK=platform E2E_SCENARIO_ID=readiness bash scripts/e2e/run.sh
```

Use runtime environment variables for sessions and account context. Never put real values in tracked files or command history. Mutations, OTP/SMS delivery, CAPTCHA automation, and other external effects are not implemented by the core framework.

## Change of Investment Choice (CIM)

Run the standalone scripted checks against a test environment:

```bash
BASE_URL=https://host bash scripts/e2e/investment-choice-scenarios.sh
```

The script uses a previously authenticated member session, matching the provided-session inputs used by `password-validation-scenarios.sh`. Supply `SESSION_COOKIE`, `CSRF_TOKEN`, and `ACCOUNT_REF` through the environment or enter them at silent prompts; `CIM_PASSWORD` is needed only for the password opt-in. Do not put secrets in command arguments or tracked files. `ACCEPT_LANGUAGE` is optional and defaults to `en`. Use a test member with an editable switch source and at least two available funds. `CIM_DRY_RUN=yes` prints masked plans without making requests.

The default scenarios are S1–S9: details contract/authentication, missing and invalid token gates, malformed issuance/submit ordering, and cancel authorization/issuance validation. `CIM_USE_PASSWORD=yes` adds P1–P3 and performs **3 password verifications**; set `CIM_PASSWORD` or enter it at the silent prompt. These scenarios check validation and token binding/reuse without changing member data. Every issuance verifies the password and counts toward the member's password-validation throttle.

`CIM_LIVE_SUBMIT=yes` additionally requires `CIM_USE_PASSWORD=yes` and `CIM_CONFIRM_LIVE_SUBMIT=I-UNDERSTAND-THIS-CHANGES-REAL-DATA`. It adds **3 more password verifications** (6 total) and performs a real submit followed by a cancel. The script refuses to start that block if the initial details response has no editable source or already has a pending instruction. If cancellation is rejected because processing has begun, the reference number and manual-handling warning are printed; any later failure after submission also prints a cleanup hint. No live block is run unless both opt-in settings are exact.

P1 uses a snapshot-dependent validation failure (interval-invalid when the snapshot allows it, otherwise unavailable source) so token issuance can succeed. Issuance itself rejects a 99%-total allocation before password verification; that malformed case is covered by S5/S6.

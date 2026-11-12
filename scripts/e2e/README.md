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

The public cancel body is `{"referenceNo":"<pending-reference>"}`. Send the same JSON object as `operationPayload` when issuing a `CANCEL_INVESTMENT_CHOICE` password-validation token.

The default scenarios are S1–S9: details contract/authentication, missing and invalid token gates, malformed issuance/submit ordering, and cancel authorization/issuance validation. S2 sends Account-Ref and CSRF but omits the session cookie to exercise the documented authentication error. `CIM_USE_PASSWORD=yes` adds P1–P3 and performs **3 password verifications** when P1 is available; set `CIM_PASSWORD` or enter it at the silent prompt. P1 is reported as `SKIP` (and counted separately) when the S1 snapshot cannot supply an isolated snapshot-validation case. These scenarios check validation and token binding/reuse without changing member data. Every issuance verifies the password and counts toward the member's password-validation throttle.

`CIM_LIVE_SUBMIT=yes` additionally requires `CIM_USE_PASSWORD=yes` and `CIM_CONFIRM_LIVE_SUBMIT=I-UNDERSTAND-THIS-CHANGES-REAL-DATA`. It adds **3 more password verifications** (up to 6 total, or 5 if P1 is skipped) and performs a real submit followed by a cancel. The script refuses to start that block if the initial details response has no editable source or already has a pending instruction. If cancellation is rejected because processing has begun, the reference number and manual-handling warning are printed; any later failure after submission also prints a cleanup hint. No live block is run unless both opt-in settings are exact.

P1 uses a snapshot-dependent validation failure: interval-invalid when the snapshot has the required interval/source/funds, otherwise an unavailable source when 100% is interval-valid. It skips if neither case can be formed from S1. A token cannot be issued for bodies failing V1–V8 because password-validation issuance rejects them first; only snapshot-dependent rules can be tested with a real token. The 99%-total malformed case is covered by S5/S6.

## Authenticated post-login orchestration

Run an allow-listed set of post-login feature packs through one login and one logout:

```bash
BFF_BASE_URL=https://host LOGIN_TEST_USERNAME=user LOGIN_TEST_PASSWORD=password E2E_ALLOW_EXTERNAL_EFFECTS=true E2E_ALLOW_OTP_VERIFICATION=true E2E_FEATURE_PACKS="investment-choice" bash scripts/e2e/run-post-login.sh
```

The registry initially supports `investment-choice`. The complete ordered list is validated before login; empty, duplicate, or unknown names fail preflight. Login selects the first email OTP destination and fails without SMS fallback when no email is available. OTP is currently entered silently through `/dev/tty`; `acquire_login_otp` is the replacement boundary for future real-email retrieval. After OTP verification, the first account in API response order is selected. The run-scoped private state supplies the portal cookie, CSRF token, and Account-Ref to each feature pack.

All selected packs run in order even after a pack fails. Logout then runs once and verifies HTTP 200, public `SUCCESS`, and clearing of `portal_sid`; logout failure fails the overall run. The final summary includes login, every feature, logout, overall status, and evidence location without secret values. Defensive traps remove private state after interruption; the runner never automatically re-authenticates after HTTP 401 or 403.

Pre-login scenarios remain under `scripts/e2e/run.sh`. Standalone provided-session execution, including `investment-choice-scenarios.sh`, remains supported. Runtime credentials, cookies, CSRF values, OTPs, and account references must never be committed or written into evidence.

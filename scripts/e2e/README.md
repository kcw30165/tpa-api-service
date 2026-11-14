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

The script uses a previously authenticated member session, matching the provided-session inputs used by `password-validation-scenarios.sh`. Supply `SESSION_COOKIE`, `CSRF_TOKEN`, and `ACCOUNT_REF` through the environment or enter them at silent prompts; `CIM_PASSWORD` is needed only for the password opt-in and follows `CIM_PASSWORD` > `LOGIN_TEST_PASSWORD` > silent `/dev/tty` prompt precedence. Do not put secrets in command arguments or tracked files. `ACCEPT_LANGUAGE` is optional and defaults to `en`. `CIM_DRY_RUN=yes` prints masked plans without making requests.

The public cancel body is `{"referenceNo":"<pending-reference>"}`. Send the same JSON object as `operationPayload` when issuing a `CANCEL_INVESTMENT_CHOICE` password-validation token.

The default scenarios are S1–S9: details contract/authentication, missing and invalid token gates, malformed issuance/submit ordering, and cancel authorization/issuance validation. S2 sends Account-Ref and CSRF but omits the session cookie to exercise the documented authentication error. `CIM_USE_PASSWORD=yes` adds P0–P3; password prompts remain silent. P0 generates a valid instruction from the S1 GET snapshot and verifies password-validation issuance without submitting it. P1 and P2 assert M6 snapshot precheck rejection during issuance, so they do not expect or use a token afterward. P3 uses an existing pending reference to verify operation-payload binding and does not send a matching cancel request.

The positive generator accepts only a `NORMAL` snapshot with a valid integer `intervalPercentage`, at least one source with `available=true`, at least two distinct funds, and no existing pending instruction. The public GET field `switchSourceCode` supplies request source codes; `switchSource` is display text. The GET `funds` list contains eligible funds and supplies `fundCode`. The generator randomly selects one eligible source and two or three distinct funds, preferring three when possible. It partitions integer units of `intervalPercentage` so every percentage is positive, divisible by the interval, and totals exactly 100. `CIM_RANDOM_SEED` optionally accepts an integer from 0 through 32767; the selected seed, interval, fund count, and percentages are reported without source/fund codes or authentication values. Reusing the seed with the same Bash version and snapshot reproduces the selection.

P0 reports an explicit `SKIP` reason when the snapshot is restricted, interval data is missing/invalid, no eligible source exists, fewer than two distinct eligible funds exist, pending instructions exist, or the interval cannot form at least two positive allocations totaling 100. It never fabricates valid source or fund codes. P1 separately checks an interval-invalid percentage when an eligible fund set permits it, otherwise an unavailable source when a 100% allocation is interval-valid; it skips when neither isolated precheck can be formed. P2 checks an unavailable pending reference at issuance. M6 rejections occur before password verification; only successful issuance attempts count toward the member's password-validation throttle. Password-check counts vary with snapshot eligibility, pending references, and live opt-in.

`CIM_LIVE_SUBMIT=yes` requires both `CIM_USE_PASSWORD=yes` and the exact `CIM_CONFIRM_LIVE_SUBMIT=I-UNDERSTAND-THIS-CHANGES-REAL-DATA` value. Without both settings the harness does not send an authorized CIM submission or cancellation. With both, it submits the exact P0 operationPayload using the P0 token, then cancels only the newly submitted reference. The initial pending-instruction preflight remains mandatory; an existing instruction is never canceled to make an account eligible. If cancellation is rejected because processing has begun, the reference number and manual-handling warning are printed; any later failure after submission also prints a cleanup hint. A successful password-validation token alone never authorizes mutation.

Snapshot validation is performed during password-validation issuance under M6. Negative issuance scenarios assert their 422 code and target before any submit/cancel attempt; a rejected precheck has no token and does not count as a password verification. The malformed 99%-total case remains covered by S5/S6.

## Authenticated post-login orchestration

Run an allow-listed set of post-login feature packs through one login and one logout:

```bash
BFF_BASE_URL=https://host LOGIN_TEST_USERNAME=user LOGIN_TEST_PASSWORD=password E2E_ALLOW_EXTERNAL_EFFECTS=true E2E_ALLOW_OTP_VERIFICATION=true E2E_FEATURE_PACKS="investment-choice" bash scripts/e2e/run-post-login.sh
```

The registry initially supports `investment-choice`. The complete ordered list is validated before login; empty, duplicate, or unknown names fail preflight. Login selects the first email OTP destination and fails without SMS fallback when no email is available. OTP is currently entered silently through `/dev/tty`; `acquire_login_otp` is the replacement boundary for future real-email retrieval.

After OTP verification, `E2E_ACCOUNT_REF` may optionally select a test account from that authenticated response's `accounts[].accountRef` list. When unset, the first account in API response order is selected, preserving the default behavior. When set, the value must exactly match one and only one returned account; no match, duplicate matches, or a malformed account list fail closed without falling back to the first account. The selector is input only and is kept separate from `ACCOUNT_REF`, the runtime value published to feature requests. It is cleared from the environment after being captured, and account references are never printed or written to evidence. Account selection is local to the login response: the runner does not sort accounts, infer CIM eligibility, query accounts one by one, or call a separate selection endpoint.

OTP verification establishes the authenticated session before the account list is resolved. If selection then fails, no feature pack runs; the runner attempts authenticated logout exactly once, reports selection and logout outcomes separately, and cleans owned private state. Selection failure remains a failed login and failed overall run even when logout succeeds. The selector does not bypass CIM snapshot or pending-instruction eligibility, enable a rejected password-validation issuance, or authorize mutation. Live submit/cancel still requires both `CIM_LIVE_SUBMIT=yes` and the exact `CIM_CONFIRM_LIVE_SUBMIT=I-UNDERSTAND-THIS-CHANGES-REAL-DATA` opt-in; pending instructions are never canceled to make an account eligible.

Treat `E2E_ACCOUNT_REF` as sensitive runtime input. Supply it through a protected environment mechanism rather than a command argument or shell-history entry. Do not copy raw account references into logs, evidence, tracked files, or test output. The run-scoped private state supplies the portal cookie, CSRF token, and selected `Account-Ref` to each feature pack.

All selected packs run in order even after a pack fails. Logout then runs once and verifies HTTP 200, public `SUCCESS`, and clearing of `portal_sid`; logout failure fails the overall run. The final summary includes login, every feature, logout, overall status, and evidence location without secret values. Defensive traps remove private state after interruption; the runner never automatically re-authenticates after HTTP 401 or 403.

Pre-login scenarios remain under `scripts/e2e/run.sh`. Standalone provided-session execution, including `investment-choice-scenarios.sh`, remains supported. Runtime credentials, cookies, CSRF values, OTPs, and account references must never be committed or written into evidence.

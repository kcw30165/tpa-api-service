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

The positive generator accepts a `NORMAL` snapshot with a valid integer `intervalPercentage`, at least one source with `available=true`, and at least two distinct funds. Existing pending instructions do not prevent payload generation; Progress owns duplicate and pending-instruction eligibility. The public GET field `switchSourceCode` supplies request source codes; `switchSource` is display text. The GET `funds` list contains eligible funds and supplies `fundCode`. The generator randomly selects one eligible source and two or three distinct funds, preferring three when possible. It partitions integer units of `intervalPercentage` so every percentage is positive, divisible by the interval, and totals exactly 100. `CIM_RANDOM_SEED` optionally accepts an integer from 0 through 32767; the selected seed, interval, fund count, and percentages are reported without source/fund codes or authentication values. Reusing the seed with the same Bash version and snapshot reproduces the selection.

P0 reports an explicit `SKIP` reason when the snapshot is restricted, interval data is missing/invalid, no eligible source exists, fewer than two distinct eligible funds exist, or the interval cannot form at least two positive allocations totaling 100. Existing pending instructions alone are not a P0 skip reason. It never fabricates valid source or fund codes. P1 separately checks an interval-invalid percentage when an eligible fund set permits it, otherwise an unavailable source when a 100% allocation is interval-valid; it skips when neither isolated precheck can be formed. P2 checks an unavailable pending reference at issuance. M6 rejections occur before password verification; only successful issuance attempts count toward the member's password-validation throttle. Password-check counts vary with snapshot eligibility, pending references, and live opt-in.

`CIM_LIVE_SUBMIT=yes` requires `CIM_USE_PASSWORD=yes`, the exact `CIM_CONFIRM_LIVE_SUBMIT=I-UNDERSTAND-THIS-CHANGES-REAL-DATA` value, and `CIM_RECOVERY_DIR` naming an existing directory owned by the current user with mode `0700`. The selected GET snapshot must be `NORMAL`, have at least three eligible funds, and an interval that supports three positive allocations totaling 100. Existing pending instructions do not block the submit attempt; Progress owns pending/duplicate eligibility, and a rejection ends the run without retry or cancellation. The generated P0 payload contains three distinct eligible funds; every percentage is positive and interval-compliant, totals exactly 100, and the exact operationPayload is submitted once. The sequence is initial GET and P0 issuance; submit; GET again to refresh the snapshot and require the newly returned reference exactly once in `pendingInstructions`; issue a new `CANCEL_INVESTMENT_CHOICE` token bound to that exact reference; cancel it once; then GET a final time and validate the documented GET response shape. Cancellation is verified by the documented cancel `200 SUCCESS` response echoing the same reference. The final GET checks the actual response and treats the run-created reference still listed as pending as incomplete cleanup. Its absence is recorded only as an observed state, not an independent API guarantee that cancellation removes the instruction. Failure to obtain a valid final GET also leaves cleanup incomplete. Existing pending references are never selected for cancellation in this lifecycle.

To cancel a disposable existing test instruction as a separately authorized operation, use `CIM_LIVE_CANCEL_PENDING=yes`, `CIM_USE_PASSWORD=yes`, `CIM_CONFIRM_LIVE_PENDING_CANCEL=I-UNDERSTAND-THIS-CANCELS-AN-EXISTING-INSTRUCTION`, and the same protected `CIM_RECOVERY_DIR`. This option is mutually exclusive with `CIM_LIVE_SUBMIT`; run it separately before the new-instruction lifecycle. It uses only the first instruction reference in the initial authenticated GET snapshot, obtains a fresh cancellation token bound to that reference, makes one cancel attempt, then GETs again and requires the reference to be absent from `pendingInstructions`. It never chooses another pending reference after a rejection. The GET contract exposes no separate `cancellable` flag, so the harness cannot determine cancellation eligibility without the mutation endpoint; if the API reports `cancel.not-cancellable`, it stops and records the selected reference in protected recovery state rather than trying another.

There is no client-side zero-pending eligibility preflight; submit mode never cancels an existing instruction to make the account eligible. Submit and cancel are never retried automatically. After either live mutation selection/attempt without verified cleanup, a JSON recovery record is preserved under `CIM_RECOVERY_DIR` with mode `0600`; it contains the relevant reference only when known, safely JSON-encoded, and no credentials, cookies, CSRF values, tokens, or passwords. Console output contains only the recovery-record path, never the reference. Keep this directory private and inspect recovery records only through secure local access. A successful password-validation token alone never authorizes mutation; each live mode requires its own separate opt-in and exact confirmation.

Snapshot validation is performed during password-validation issuance under M6. Negative issuance scenarios assert their 422 code and target before any submit/cancel attempt; a rejected precheck has no token and does not count as a password verification. The malformed 99%-total case remains covered by S5/S6.

## Authenticated post-login orchestration

Run an allow-listed set of post-login feature packs through one login and one logout:

```bash
BFF_BASE_URL=https://host LOGIN_TEST_USERNAME=user LOGIN_TEST_PASSWORD=password E2E_ALLOW_EXTERNAL_EFFECTS=true E2E_ALLOW_OTP_VERIFICATION=true E2E_FEATURE_PACKS="investment-choice" bash scripts/e2e/run-post-login.sh
```

The registry initially supports `investment-choice`. The complete ordered list is validated before login; empty, duplicate, or unknown names fail preflight. Login selects the first email OTP destination and fails without SMS fallback when no email is available. OTP is currently entered silently through `/dev/tty`; `acquire_login_otp` is the replacement boundary for future real-email retrieval.

After OTP verification, shared `E2E_ACCOUNT_INDEX` may select an account by zero-based position in that authenticated response's `accounts[]` list. It defaults to `0`, so an unset index preserves selecting the first account in API order. It applies to the single login shared by every selected pack. `E2E_ACCOUNT_INDEX` must contain decimal digits; an out-of-range index fails closed, runs no feature pack, and follows the existing authenticated logout and cleanup policy. `E2E_ACCOUNT_REF` continues to select exactly one matching account by reference and cannot be supplied together with a non-empty `E2E_ACCOUNT_INDEX`; the conflict and malformed index are rejected before login. When `E2E_ACCOUNT_REF` is used, no match, duplicate matches, or a malformed account list fail closed without falling back to the first account. Both selector inputs are captured and removed from the environment before child packs run. Account references are never printed or written to evidence; the numeric selected index may be recorded. Account selection is local to the login response: the runner does not sort accounts, infer CIM eligibility, query accounts one by one, or call a separate selection endpoint.

OTP verification establishes the authenticated session before the account list is resolved. If selection then fails, no feature pack runs; the runner attempts authenticated logout exactly once, reports selection and logout outcomes separately, and cleans owned private state. Selection failure remains a failed login and failed overall run even when logout succeeds. The selector does not bypass CIM snapshot or pending-instruction eligibility, enable a rejected password-validation issuance, or authorize mutation. Live submit/cancel still requires both `CIM_LIVE_SUBMIT=yes` and the exact `CIM_CONFIRM_LIVE_SUBMIT=I-UNDERSTAND-THIS-CHANGES-REAL-DATA` opt-in; pending instructions are never canceled to make an account eligible.

Treat `E2E_ACCOUNT_REF` as sensitive runtime input. Supply it through a protected environment mechanism rather than a command argument or shell-history entry. Do not copy raw account references into logs, evidence, tracked files, or test output. The run-scoped private state supplies the portal cookie, CSRF token, and selected `Account-Ref` to each feature pack.

All selected packs run in order even after a pack fails. Logout then runs once and verifies HTTP 200, public `SUCCESS`, and clearing of `portal_sid`; logout failure fails the overall run. The final summary includes login, every feature, logout, overall status, and evidence location without secret values. Defensive traps remove private state after interruption; the runner never automatically re-authenticates after HTTP 401 or 403.

Pre-login scenarios remain under `scripts/e2e/run.sh`. Standalone provided-session execution, including `investment-choice-scenarios.sh`, remains supported. Runtime credentials, cookies, CSRF values, OTPs, and account references must never be committed or written into evidence.

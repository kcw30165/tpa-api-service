# Architecture

## Components

1. The post-login orchestrator validates all configuration and feature names before network activity.
2. The existing authenticated login journey remains the only login protocol implementation. Narrow helpers select the first email, acquire OTP, select the first account, and publish state.
3. The existing HTTP client remains the only authenticated request mechanism.
4. A fixed registry maps trusted names to repository scripts; user input is never evaluated as shell or a path.
5. Explicit logout verifies the established response and cookie-clearing contract, then clears local state.
6. A consolidated summary reports lifecycle and feature outcomes without secrets.

## Lifecycle

- `PREFLIGHT`: reject empty, duplicate, or unknown `E2E_FEATURE_PACKS` entries before login.
- `LOGIN`: select first email only, acquire and verify OTP, select the account at zero-based `E2E_ACCOUNT_INDEX` (default `0`) or the exact `E2E_ACCOUNT_REF` match, and publish state. A malformed index or non-empty selector conflict is rejected before login. An out-of-range index fails selection closed; features do not run, and the existing post-authentication logout and cleanup behavior is preserved.
- `FEATURES`: invoke every resolved pack sequentially and record each result. Never re-login on 401 or 403.
- `LOGOUT`: run once after all feature attempts. Failure contributes to overall failure.
- `SUMMARY`: PASS only when login, every feature, and logout pass.
- `CLEANUP`: remove private state. Traps are defensive and do not replace normal logout.

## State and security

Each run creates a new mode-0700 private directory and restrictive secret files. It never loads stale state. Selector inputs are captured and removed from the environment before login; the selected Account-Ref remains in private state and is never logged or written to evidence. Cookies, CSRF, OTP, password, and complete authenticated bodies are excluded from evidence or redacted by existing helpers. Cleanup removes the private directory on normal and abnormal exits.

## OTP extension boundary

`acquire_login_otp` initially reads silently from `/dev/tty`. A future mailbox implementation can replace only this function without changing destination selection, verification, account selection, state publication, features, or logout.

## Feature contract

Registry entries are trusted paths. Packs return reliable process status. Investment-choice retains standalone provided-session execution and all password/live-mutation safeguards. Orchestrated packs do not log out independently.

The optional live CIM lifecycle is non-retrying: GET #1, change-token issuance,
submit once, GET #2 to refresh and prove the exact new reference is pending,
cancel-token issuance for that same reference, cancel once, and GET #3 for a
contract-validated final read. Cancellation is established by the cancel
endpoint's successful response; absence from the GET pending list is not
documented as an independent cancellation guarantee. Incomplete cleanup
preserves a private recovery record under an explicitly configured mode-0700
directory. The mode-0600 recovery file is JSON so arbitrary reference strings
remain unambiguous; neither the reference nor authentication values appear in
logs or evidence.
If the run-created reference remains listed as pending in GET #3, the harness
conservatively marks cleanup incomplete.

## Logout guard

Implementation must establish the exact endpoint, API result, CSRF/cookie usage, and cookie-clear behavior from full repository source and tests. If evidence is insufficient, implementation stops rather than inventing a contract.

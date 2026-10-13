# Forgot Password E2E Scenario Pack

Scripted curl checks for the HTTP 429 answers of `POST /api/v1/auth/forgot-password/otp/resend`. The pack follows the Login pack: guarded
external effects, private runtime state, redacted evidence and no automatic retry.

| Scenario | What it sends | Expected |
|---|---|---|
| `resend-cooldown` | start, identity check, details check (one real message), an immediate resend, then the same `Idempotency-Key` again | the resend and its replay answer `429`, `BUSINESS_REJECTED`, `VERIFY_OTP`, `err.forgot-password.otp.resend.cooldown` |
| `resend-limit` | the same journey, then the allowed number of accepted resends (five, each after the cooldown), then one resend beyond the limit and its replay | the five resends answer `200`; the extra resend and its replay answer `429`, `BUSINESS_REJECTED`, `err.forgot-password.otp.resend.limitReached` |

The next step of the limit answer is recorded in the summary but not asserted: the design documents and the current response differ for the
sixth resend, and this pack does not decide which is right.

## Run

```bash
bash scripts/e2e/run-forgot-password-dev.sh resend-cooldown
bash scripts/e2e/run-forgot-password-dev.sh resend-limit
```

The dispatcher prompts for the runtime inputs (hidden where personal), sets only the guards the scenario needs and delegates to
`scripts/e2e/run.sh`. `resend-limit` announces the number of real messages and the duration and waits for `SEND`.

Guards, all required: `E2E_ALLOW_EXTERNAL_EFFECTS=true`, `E2E_ALLOW_OTP_RESEND=true`, and for `resend-limit` also
`E2E_ALLOW_OTP_RESEND_LIMIT=true`. A missing guard blocks the scenario before any request (exit 3, 7 or 8).

## Runtime inputs

| Variable | Meaning |
|---|---|
| `FORGOT_PASSWORD_TEST_USERNAME` | username for `/start` |
| `FORGOT_PASSWORD_TEST_IDENTITY_VALUE` | identity value for `/identity-check` |
| `FORGOT_PASSWORD_TEST_DATE_OF_BIRTH` | `dd/MM/yyyy` |
| `FORGOT_PASSWORD_TEST_EMAIL` or `FORGOT_PASSWORD_TEST_MOBILE` | exactly one; the contact the details check sends the OTP to |
| `FORGOT_PASSWORD_TEST_IDENTITY_TYPE` | optional; `GPN`, `GPFS`, `STAFF_NO` or `ID`. When unset, the identity type returned by `/start` is used |
| `FORGOT_PASSWORD_E2E_ORIGIN` | optional; default `http://localhost:4200`. It must be one of the BFF's allowed origins: the resend validates `Origin` |
| `FORGOT_PASSWORD_RESEND_COOLDOWN_SECONDS` | optional; used only when the details check returns no cooldown |
| `E2E_FORGOT_PASSWORD_MAX_RESENDS` | optional; default 5, the configured number of accepted resends |
| `E2E_FORGOT_PASSWORD_COOLDOWN_MARGIN_SECONDS` | optional; default 2, added to the cooldown before every resend |

Messages go to the destination the BFF is configured to use for the environment, which for a local run is normally the override address
in its `.env`. `resend-cooldown` requests one real message. `resend-limit` requests six and takes about seven minutes: it waits for the
cooldown before every resend, including the last one, so the limit, and not the cooldown, is the only possible reason for the rejection.

## Evidence

Every run writes `target/evidence/<timestamp>-bff-e2e-forgot-password-<scenario>/` with the usual files plus, per request, a request
summary (presence flags only), the redacted response headers and the redacted response body, and `030-summary.txt`.

- The framework redacts every JSON key named `code`, which includes `errors[].code`. The assertions therefore read the raw response, which
  exists only in the private runtime state directory, and the summary records the error code.
- The username, identity value, date of birth, e-mail, mobile, the auth-flow cookie, the CSRF token and the idempotency keys are never
  written to the evidence. Personal values are passed to curl as arguments, as the Login scenarios do with credentials.
- `040-progress.txt` lists the waits of `resend-limit`; the same lines are printed on the terminal when one is available.

## What a pass does and does not show

A pass shows that the running BFF answers the cooldown and the limit with 429 on the first request and on a same-key replay. A replay
returns the status and the error code with an empty payload, because only the status is stored for a rejected resend. The pack does not
cover OTP verification, the password reset or the cancel call.

# Captcha Verification Lifecycle Contract

## Approved lifecycle

Only a challenge in `CREATED` is eligible for answer verification. A correct answer performs `CREATED -> VERIFIED`. The later `VERIFIED -> CONSUMED` transition belongs exclusively to a protected consumer such as Registration or Username Recovery and is never performed by the captcha verify endpoint.

## Re-verification of `VERIFIED`

A new verify request against a stored `VERIFIED` challenge is rejected for every submitted answer:

- HTTP status: `422 Unprocessable Content`
- API status: `VALIDATION_FAILED`
- `success`: `false`
- public error code: `err.captcha.verification.invalid`
- error target: exactly `captchaAnswer`
- next step: `NONE`

The request does not compare or hash the submitted answer, does not consume the challenge, and does not change status, failed-attempt count, or expiry/TTL. The public response must not reveal the precise stored lifecycle status or internal persistence details.

## Idempotency distinction

A challenge being in `VERIFIED` state is not transport idempotency. No request key or request hash replay contract exists for captcha verification. Repeating the HTTP request is therefore a new ineligible verification request, not a successful replay.

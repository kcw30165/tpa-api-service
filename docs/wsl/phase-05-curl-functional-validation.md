# Phase 05: Curl Functional Validation

## Objective

Replace routine Postman execution with versioned, fast, repeatable, and evidence-producing curl scripts.

## Test levels

### Level 1: Technical health

- Call `/actuator/health`.
- Assert HTTP 200.
- Assert expected health status.

### Level 2: API contract smoke tests

- Verify required headers.
- Verify public response schema.
- Verify validation failures.
- Avoid irreversible business side effects.

### Level 3: Login and OTP flow

- Start login.
- Assert destination information is returned.
- Preserve workflow identifiers only in protected temporary state.
- Select a mobile destination.
- Request OTP delivery.
- Assert expected public response.
- Require manual `CONFIRMED_BY_NICK` evidence for physical SMS receipt.

## Curl implementation rules

- Use `curl --fail-with-body` where appropriate.
- Capture response body, response headers, HTTP status, duration, and curl exit code separately.
- Use `jq` or an equivalent JSON assertion tool.
- Do not use `curl -v` in normal evidence.
- Do not preserve Authorization headers, cookies, OTP values, full mobile numbers, credentials, or API keys.
- Use redacted request summaries rather than raw secret-bearing commands.

## Evidence

```text
target/evidence/<timestamp>-local-functional/
├── 001-preflight.txt
├── 002-health-summary.txt
├── 003-health-response.json
├── 004-login-start-summary.txt
├── 005-login-start-response.json
├── 006-destination-summary.txt
├── 007-destination-response.json
├── 008-otp-send-summary.txt
├── 009-otp-send-response.json
├── 010-assertions.txt
├── 011-runtime-log-extract.txt
├── 012-manual-observation.txt
├── 013-exit-codes.txt
└── 014-validation-status.txt
```

## Acceptance criteria

- Health and selected API flows run without Postman.
- Assertions fail clearly and preserve diagnostics.
- Evidence is sanitized.
- SMS delivery is not claimed automatically.
- Scripts can run against direct local runtime and local Docker runtime.

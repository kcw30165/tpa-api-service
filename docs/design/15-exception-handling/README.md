# Exception Handling, Central Logging and Error Alerting

| Field | Value |
|---|---|
| Status | Draft for review |
| Branch | `refactor/exception-handling` |
| Base | `origin/dev` at `92849aac9a867830a975020809b4290ca15287dc` |
| Owner | Nick (solution design, implementation, CI/CD) |
| Evidence | scripts 001 (inventory) and 002 (deep inventory), read-only on `origin/dev` |

## Goals

1. **Central exception handling**: one mapping from exception to public response, shared by the
   controller advice and the WebFlux exception handlers.
2. **Central logging**: one logging policy; one ERROR line per incident; structured JSON output.
3. **Alerting**: send an email when a log event at a configured level is written.

## Resolved decisions (2026-10-21)

| # | Decision |
|---|---|
| D1 | Alerts are driven by a Logback appender. The levels that trigger an alert are configurable (`error-alert.levels`, default `ERROR`; `WARN` and `INFO` may be added). |
| D2 | Channel is email through the existing SMTP sender. Initial recipient `deng.nick@bcthk.com`; to be replaced by a group mailbox through configuration only. |
| D3 | Deduplication is controlled by a flag (`error-alert.deduplicate`). `true`: per-pod in-memory dedup window plus hourly cap. `false`: every alert is sent. Redis is not used for dedup (see `04-error-alerting.md` section 6). |
| D4 | Alerting is enabled in PROD and DR; disabled by default elsewhere, and can be switched on at runtime in DEV/SIT for testing. |
| D5 | No public HTTP status or response body changes in this refactor. Current behaviour is pinned by characterization tests. Unifying 500/502/503 for `SYSTEM_ERROR` is a separate follow-up. |
| D6 | Result-based auth workflows (Login, Registration, Forgot Password, Username Recovery) keep their result/idempotency-replay design. Only their logging is centralized (through `FailureEventPort`). |
| D7 | Logs move to structured JSON. The log platform parses JSON from stdout. |

## Documents

| File | Content |
|---|---|
| `01-current-state.md` | Confirmed findings from scripts 001 and 002 |
| `02-exception-handling.md` | Target design for central exception handling (M1) |
| `03-logging-policy.md` | Logging policy, structured logging, call-site migration (M2, M3) |
| `04-error-alerting.md` | Alert appender, configuration, dedup, email content, failure modes (M4) |
| `05-milestones.md` | Milestones, sub-tasks, tests, gates |
| `06-open-items.md` | Open questions, risks, deferred follow-ups |

## Non-goals

- Changing any public HTTP status, `ApiStatus`, error code or error type (D5).
- Converting result-based workflows to exceptions (D6).
- Alerting on failures that happen before the Spring context creates beans
  (for example `ConfigServiceEnvironmentPostProcessor`); see `06-open-items.md`.
- Log platform side configuration.

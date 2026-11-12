# Implementation plan

## Milestone 1: reusable authenticated bootstrap

### M1.1 Selection and OTP boundary
Add fail-first shell tests for first-email selection, no-email failure, first-account selection, and replaceable OTP acquisition. Make only the required login-journey refactor.

### M1.2 State publication
Add fail-first tests proving portal cookie, CSRF, and Account-Ref load into existing HTTP-client variables and private state is cleaned. Preserve redaction and standalone behavior.

## Milestone 2: registry and orchestration

### M2.1 Preflight registry
Add fail-first tests for ordered resolution, empty selection, duplicates, unknown names, and rejection before login. Register only `investment-choice`.

### M2.2 Lifecycle aggregation
Add fail-first fake-driven tests proving continued ordered execution after feature failures, logout after feature failures, logout contribution to overall status, login short-circuiting, successful aggregation, secret-free summaries, and cleanup.

### M2.3 Investment-choice compatibility
Prove it receives orchestrated authentication. Make only compatibility changes, preserving standalone provided-session mode and mutation guards.

## Milestone 3: logout, documentation, and validation

### M3.1 Logout contract
Confirm endpoint and cookie-clear semantics from full source/tests, then add non-live fail-first tests and explicit result handling. Stop if the contract remains unverified.

### M3.2 Documentation
Document the command, feature syntax/registry, selection behavior, OTP extension boundary, continuation, logout, summary, security, pre-login separation, and standalone mode.

### M3.3 Gates
Run shell syntax/static tests, fake lifecycle tests, Spotless, Maven tests, coverage audit, local startup smoke, and SonarQube in the prescribed order. Automated tests invoke no live login, OTP, mutation, cancel, or logout.

## Evidence

Implementation evidence goes to `target/evidence/gc-post-login-e2e-foundation-implementation/REPORT.md`, including repository anchors, fail-first proof, selected tests, gates, redaction audit, fake lifecycle proof, diff, limitations, and commit status.

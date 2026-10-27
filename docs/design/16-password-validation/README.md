# Password Validation and Transaction Authorization: design package

Version 1.3. Requirements baseline: decisions approved in this conversation on 2026-10-27; corrected three times the same day after independent review (CHANGES REQUIRED each time), addressing a session-resurrection race (a deeper loadAndTouch/port.touch overwrite gap was found on the third pass, beyond the initial terminal-transition revision fix), grant-key account scoping, logging-exclusion robustness (body-logging and, separately, header-logging paths), and a response-type contradiction.
Status: final requirements/design baseline for review; no implementation or deployment approval is implied.

The member confirms a password before submitting reviewed Personal Information changes. One shared BFF endpoint validates that password through TAS/APIM and issues a five-minute, single-use authorization bound to the authenticated session, selected account, purpose, and exact submitted changes. This is password confirmation, not a PIN or OTP feature.

## Reading order
1. [Solution design](01-solution-design.md)
2. [API contract](02-api-contract.md)
3. [Downstream contract](03-downstream-contract.md)
4. [Validation and error handling](04-validation-and-error-handling.md)
5. [Test and traceability](05-test-and-traceability.md)
6. [Implementation plan](06-implementation-plan.md)
7. [Decisions, evidence and release checks](07-decisions-and-evidence.md)

## Authority and boundaries
Approved requirements are explicitly identified. Detailed technical specifications and new error-code names in this package are authored design choices, not claims about existing code or separate stakeholder approvals. Implementation verification gates are not hidden assumptions and must not be represented as completed.

Source baseline E1: user-supplied 001-password-validation-readonly-investigation.zip.txt, immutable origin/dev commit 49b5ffb4089b04d214444b3e3d7cd33f2d5dbc4c. Evidence was reviewed in this conversation. Source selection was keyword/import/type based, not an exhaustive architecture audit. No tests, startup, formatting or SonarQube gates were run by the investigation.

No production source changes, Git branch changes, commits, or repository installation are performed by this package. The package is Markdown for repository review; choose the repository documentation location during implementation planning rather than assuming an existing directory number.

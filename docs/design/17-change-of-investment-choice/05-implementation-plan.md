# 05 Implementation plan

Workflow per sub-task: read-only check -> fail-first tests (where behaviour changes) -> minimal change
-> `spotless:check`, compile, test-compile, targeted tests -> one commit -> evidence archive.
Milestone gate: full `mvn clean test`, eager startup smoke test, SonarQube gate (owner-reported).
Stop rules: 3 consecutive failed scripts, or 10 scripts without a commit -> escalate.

Branch: `feature/change-of-investment-choice` from `origin/dev`, in its own worktree.

| Milestone | Sub-task | Content | Depends on |
|---|---|---|---|
| M0 | 0.1 | Read-only anchor check at HEAD: every fact marked "sub-task 0" in 01-04 (session-scoped cache pattern and TTL, PI PUT success shape and field-validation status, shared classifier entry point, ledger count, `ErrorCodes` location, purpose registry shape). Output a fact table. No changes | - |
| M1 password validation 2nd purpose | 1.1 | Purpose `CHANGE_INVESTMENT_CHOICE` in `PasswordValidationPurpose` + parse; Personal Information unchanged (regression tests first) | M0 |
| | 1.2 | Issuance structure check selected by purpose; strict V1-V8 for CIM (O-12 decided strict) | 1.1 |
| | 1.3 | Token gate registration by route+method -> purpose (generic), CIM route `POST /api/v1/investment-choice/instructions`; path-variant tests | 1.2 |
| | 1.4 | Confidential-route registration + logging tests for the CIM route | 1.3 |
| M2 TRPGetCIMDtl | 2.1 | APIM request/response DTOs, `ignoreUnknown`, strict date/time parsing | M0 |
| | 2.2 | `ApimInvestmentChoiceDetailsAdapter`: envelope interpretation, integrity rules, err-message -> feature exception, empty eligibility slot, shared classifier; ledger +1 | 2.1 |
| M3 GET | 3.1 | Fund ordering (reuse `FundPriceRiskLevels`, Fund Price display-name rule) | 2.2 |
| | 3.2 | `GetInvestmentChoiceUseCase` + assembler (grouping, join, account number) | 3.1 |
| | 3.3 | Snapshot store port + Redis adapter (key, lifetime, overwrite) | 3.2 |
| | 3.4 | Controller GET, web DTOs, `InvestmentChoiceExceptionMappings`, contract tests for every row of 02 section 1 | 3.3 |
| M4 submit | 4.1 | TRPCIMReq DTOs + `ApimInvestmentChoiceInstructionAdapter` (`NON_RETRYABLE`, never resubmitted), outcome = success / err-message / unknown; ledger +1 | M2 |
| | 4.2 | Body + snapshot validator (V1-V8, S1-S3) | 3.3 |
| | 4.3 | `SubmitInvestmentChoiceUseCase`: expansion, outcome mapping, reconciliation | 4.1, 4.2 |
| | 4.4 | Controller POST behind the token gate, contract tests for every row of 02 section 2 and 04 section 3 | 1.4, 4.3 |
| M5 close | 5.1 | Curl scenario script (local build vs dev downstream, test member, explicit live-submit opt-in) | M4 |
| | 5.2 | As-built notes and traceability in this pack; milestone gate; MR | 5.1 |
| Deferred | D.1 | Cancel endpoint, after Progress publishes the operation (O-3) | Progress |
| Deferred | D.2 | Eligibility codes -> 422 slot (O-2) | Progress |

M1 may merge separately (it changes the shared password-validation capability); the CIM route stays
unreachable until M4.4 because no controller exists before it.

## Release

Frontend and BFF release together (password-validation precedent). New configuration: none expected;
if sub-task 3.3 needs a snapshot TTL property, it is added with a default and documented here.

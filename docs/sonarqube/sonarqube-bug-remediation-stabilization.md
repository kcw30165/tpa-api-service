# SonarQube Bug Remediation — Stabilization Plan

This document defines the stabilization gates, commands, evidence artifacts, and stop conditions for the `fix/sonarqube-bug-remediation` branch after Units 1–7 have been committed locally.

Status summary (Units 1–7 are COMMITTED):

- Unit 1 (ReferenceDate S1872): commit c0b467bff4a3ff66c13b4aa14be73ed022fc035c
- Unit 2 (ApimAdapterReflection S1751): commit 328a6754050d9c3753b86f8cbd50ce70d6802282
- Unit 3 (ForgotPassword S3923): commit 22af9b02e26091f330ae15993bcf824554ef377e
- Unit 4 (ApimGeneralMetadata S3655): commit 59195c7e1a5174c0752e361cb48248effe5a2c2b
- Unit 5 (WorkflowKey S5998): commit 440379cf6c5ff95c703f3c946f1104e7e1fb2f3b
- Unit 6 (TrpGetAccountList S3923): commit 8c0257caf673d85c77af6aafd8bcdfc4a085600a
- Unit 7 (ExecutionLoggingAspect S6218): commit 2728632c82c3871a80c1e9c8c7f95c501042afea

<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 8b25627e (docs(sonarqube): record gates 0 through 3 stabilization)
Stabilization-status (current):
- Gate 0: PASSED
- full compile: PASSED
- full test-compile: PASSED
- full mvn test: PASSED
- Surefire inspection: PASSED
<<<<<<< HEAD
<<<<<<< HEAD
- Spring startup smoke test: PASSED (Attempt 4)
=======
Stabilization-status (initial):
- full compile: NOT_STARTED
- full test-compile: NOT_STARTED
- full mvn test: NOT_STARTED
- Surefire inspection: NOT_STARTED
=======
>>>>>>> 8b25627e (docs(sonarqube): record gates 0 through 3 stabilization)
- Spring startup smoke test: BLOCKED
>>>>>>> 730a3fa8 (docs(sonarqube): define bug remediation stabilization gates)
=======
- Spring startup smoke test: PASSED (Attempt 4)
>>>>>>> dc79bc42 (docs(sonarqube): record successful application startup smoke)
- SonarQube rescan: BLOCKED
- scoped BUG verification: BLOCKED
- completed vulnerability regression check: BLOCKED
- CODE_SMELL comparison-only check: BLOCKED
- branch push: BLOCKED
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 8b25627e (docs(sonarqube): record gates 0 through 3 stabilization)
- workstream closure: BLOCKED


Completed Gate 0–3 evidence

Evidence directory:
`target/evidence/sonarqube-bug-remediation-stabilization-20261006T083115Z`

Gate results:
- Gate 0: PASSED
- Gate 1 compile: PASSED, exit code 0, elapsed 25 seconds
- Gate 2 test-compile: PASSED, exit code 0, elapsed 53 seconds
- Gate 3 full mvn test: PASSED, exit code 0, elapsed 462 seconds
- Surefire inspection: PASSED

Surefire aggregate:
- Tests run: 5443
- Failures: 0
- Errors: 0
- Skipped: 10
- Test classes: 1128

Skipped-test classification:
- EXPECTED_PRE_EXISTING: 2
- EXPECTED_ENVIRONMENTAL: 8
- UNEXPECTED: 0
- UNRESOLVED: 0

Governance conclusion:
- every skipped test is enumerated in `06-surefire-manifest.txt`;
- every skipped test has source-backed classification evidence;
- Gate 3 satisfies the documented pass criteria;
<<<<<<< HEAD
<<<<<<< HEAD
- Gates 5-6 remain BLOCKED;
- the workstream is not closed.

=======
>>>>>>> 730a3fa8 (docs(sonarqube): define bug remediation stabilization gates)
=======
- Gates 4–6 remain BLOCKED;
=======
- Gate 4: PASSED; Gates 5–6 remain BLOCKED;
>>>>>>> dc79bc42 (docs(sonarqube): record successful application startup smoke)
- the workstream is not closed.

>>>>>>> 8b25627e (docs(sonarqube): record gates 0 through 3 stabilization)

Canonical repository-approved commands (do not run here):

- Gate 1 (compile):
  - Command (proven): mvn -DskipTests compile
- Gate 2 (test-compile):
  - Command (proven): mvn -DskipTests test-compile
- Gate 3 (full test suite) — MANDATORY:
  - Command (proven): mvn test
- Targeted test invocation examples (do not re-run unless required for failure diagnosis):
  - mvn -Dtest=ReferenceDateUseCaseBoundaryTest,RefreshReferenceDateServiceTest test
  - mvn -Dtest=ApimAdapterReflectionBranchProbeTest test
  - mvn -Dtest=ForgotPasswordCompleteWebMapperTest test
  - mvn -Dtest=Task0043TrpGetAccountListAdapterArchitectureTest,TrpGetAccountListAdapterTest test
  - mvn -Dtest=ExecutionLoggingAspectInvocationContextTest test
- Surefire reports inspection locations (evidence):
<<<<<<< HEAD
<<<<<<< HEAD
  - `target/surefire-reports/*.txt`
  - `target/surefire-reports/TEST-*.xml`
=======
  - target/surefire-reports/*.txt
  - target/surefire-reports/TEST-*.xml
>>>>>>> 730a3fa8 (docs(sonarqube): define bug remediation stabilization gates)
=======
  - `target/surefire-reports/*.txt`
  - `target/surefire-reports/TEST-*.xml`
>>>>>>> 8b25627e (docs(sonarqube): record gates 0 through 3 stabilization)
- JaCoCo report artifacts (coverage evidence):
  - target/site/jacoco/jacoco.xml

Repository guidance and CI helper scripts (proven):
- scripts/ci/validate.sh — repository-owned CI validation script. It runs spotless:check, mvn -DskipTests compile, mvn -DskipTests test-compile, and mvn test jacoco:report and validates Surefire/JaCoCo outputs. Use this script as the canonical CI-side validation entrypoint when running in CI.

Startup procedure (repository-evidenced)

- Approved local startup command (documented):
  - mvn spring-boot:run -Dspring-boot.run.profiles=local
  - Alternate documented invocation: SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
- Required Spring profile (documented): `local` (see docs/wsl/phase-04-direct-spring-runtime.md and README.md)
- Required environment/property names (documented; DO NOT include values):
  - SPRING_PROFILES_ACTIVE (profile selection)
  - auth-workflows.login.context-protection.key-version
  - auth-workflows.login.context-protection.secret
  - auth-workflows.login.session.protection.base64-key
  - auth-workflows.registration.context-protection.secret
  - auth-workflows.forgot-password.context-protection.key-version
  - auth-workflows.forgot-password.context-protection.secret
  - (Other application properties referenced in src/main/resources/application-local.yml may be necessary for particular flows; inspect application-local.yml for additional property names.)
- External runtime dependencies (documented):
  - Redis (Spring Data Redis / Sentinel if enabled)
  - APIM downstream connectivity and credentials
  - Crypto/config secrets required for context protection and APIM payloads
- Positive readiness signal (documented):
  - Actuator health endpoint: http://localhost:8090/actuator/health (HTTP 200 indicates readiness per docs/wsl/phase-04-direct-spring-runtime.md)
<<<<<<< HEAD
- Startup timeout: not defined by the repository; 120 seconds was approved and used for the local run.
- Shutdown: no repository-provided command exists; the approved policy is SIGTERM, a 30 second graceful wait, then SIGKILL only if the tracked process tree is still alive. It was executed by external script 009-gate4-attempt4-startup-smoke.
=======
- Startup timeout: NOT_DEFINED in repository documentation. No numeric startup timeout value is specified in repository scripts or docs. This missing fact BLOCKS Gate 4: an agreed numeric startup timeout must be recorded before running the startup gate locally; alternatively, run Gate 4 in a CI job that enforces a timeout and record that timeout in the evidence.
- Safe shutdown: repository runtime controller responsibilities describe stopping the tracked process and child processes; there is no single repository-provided shutdown command. This missing fact BLOCKS Gate 4: a concrete safe shutdown procedure for the chosen execution environment must be provided before running the startup gate locally.
>>>>>>> 730a3fa8 (docs(sonarqube): define bug remediation stabilization gates)

Evidence records and evidence directory (timestamped)

- Evidence directory (create at run time):
  - target/evidence/sonarqube-bug-remediation-stabilization-YYYYMMDDTHHMMSSZ

- Sequential evidence files (exact names):
  01-run-context.txt
  02-git-status-before.txt
  03-compile-summary.txt
  04-test-compile-summary.txt
  05-full-test-summary.txt
  06-surefire-manifest.txt
  07-surefire-aggregate.txt
  08-startup-command.txt
  09-startup-summary.txt
  10-readiness-response.txt
  11-shutdown-summary.txt
  12-sonar-analysis-summary.txt
  13-sonar-scoped-bugs.json
  14-sonar-vulnerability-check.json
  15-sonar-code-smell-comparison.json
  16-final-git-status.txt
  17-final-summary.txt
  99-exit-code.txt

- Evidence record contents guidance:
  - include: exact command, start and end timestamps, elapsed time, exit code, concise summary, and a manifest of report files produced.
  - exclude: credentials, tokens, `.env` contents, private keys, or entire raw Maven output logs. Extract and include only concise failure diagnostics on failure.

Strict success criteria (must be met for a gate to pass)

- Compile exit code must be 0.
- Test-compile exit code must be 0.
- Full `mvn test` exit code must be 0.
- Total Surefire failures: 0.
- Total Surefire errors: 0.
- Skipped tests must be enumerated in 06-surefire-manifest.txt and classified as expected/pre-existing.
<<<<<<< HEAD
- Startup must reach actuator health HTTP 200 with status UP within 120 seconds on the worktree-specific port (8890 for this worktree).
=======
- Startup context must reach the documented readiness signal (actuator health HTTP 200) within an agreed timeout (repository does not define one — see Gate 4 blocked note).
>>>>>>> 730a3fa8 (docs(sonarqube): define bug remediation stabilization gates)
- Sonar analysis must complete successfully and the 10 scoped BUG keys must not be present in the open unresolved BUG query results.
- No regression detected in the completed vulnerability workstream.

Gate-level definition and artifacts

Gate 0: documentation and environment readiness
- Prerequisites: repository at the intended branch HEAD (documented below); required tools installed (Java 21, Maven 3.9.x); evidence directory path available and writable.
- Command (proven): none — checks are local directory and tooling inspections (evidence: 01-run-context.txt, 02-git-status-before.txt).
- Evidence artifacts: 01-run-context.txt, 02-git-status-before.txt.
- Pass criteria: files present, correct branch HEAD recorded, tools available.
- Stop conditions: missing tooling or unwritable evidence directory; fix environment before proceeding.
- Rollback/recovery: fix environment, re-run Gate 0.

Gate 1: compile
- Prerequisites: Gate 0 pass.
- Command (proven): mvn -DskipTests compile
- Evidence artifacts: 03-compile-summary.txt, 99-exit-code.txt
- Pass criteria: compile exit code 0.
- Stop conditions: non-zero exit code.
- Recovery: address compilation errors; do not proceed until resolved.

Gate 2: test-compile
- Prerequisites: Gate 1 pass.
- Command (proven): mvn -DskipTests test-compile
- Evidence artifacts: 04-test-compile-summary.txt, 99-exit-code.txt
- Pass criteria: test-compile exit code 0.
- Stop conditions: non-zero exit code.
- Recovery: fix test-compile issues; do not proceed until resolved.

Gate 3: full test suite — MANDATORY
- Prerequisites: Gate 2 pass.
- Command (proven): mvn test
- Evidence artifacts: 05-full-test-summary.txt, 06-surefire-manifest.txt, 07-surefire-aggregate.txt, 99-exit-code.txt
- Pass criteria:
  - mvn test exit code 0
  - total Surefire failures == 0
  - total Surefire errors == 0
  - skipped tests enumerated and classified in 06-surefire-manifest.txt
- Stop conditions: any failures or errors.
- Recovery/rollback: examine 07-surefire-aggregate.txt for failing tests; only re-run focused per-unit targeted tests if required for diagnosis; do not re-run the full-suite unless diagnosing and resolving failures.

Gate 4: repository-approved application startup smoke
- Prerequisites: Gate 3 pass.
- Command (proven): mvn spring-boot:run -Dspring-boot.run.profiles=local (documented in docs/wsl/phase-04-direct-spring-runtime.md and README.md)
- Evidence artifacts: 08-startup-command.txt, 09-startup-summary.txt, 10-readiness-response.txt, 11-shutdown-summary.txt
- Pass criteria:
  - application process starts successfully
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> dc79bc42 (docs(sonarqube): record successful application startup smoke)
  - Actuator readiness endpoint (http://localhost:8890/actuator/health) returns HTTP 200 within an agreed timeout
- Startup timeout: 120 seconds (approved for the local run).
- Attempt history and results (preserved as evidence):
  - Attempt 1: FAILED — missing ignored Redis TLS prerequisite (.local/redis-tls/tls.crt). Evidence: 09-startup-summary.txt, 10-readiness-response.txt, runtime.log.
  - Attempt 2: FAILED — application served /actuator/health on port 8888 only; documented readiness endpoint 8090 was not proven. Automated shutdown tracking failed and operator manual cleanup was required (Maven PID 1763567, Java PID 1763964). Evidence: runtime-attempt2.log, attempt2_health_body.tmp, 11-shutdown-summary.txt.
  - Attempt 3: FAILED — readiness observed on port 8890 (HTTP 200, sanitized health UP) but the evidence-capture automation could not complete programmatic shutdown in this execution environment; manual cleanup was required (Maven PID 1809703, Java PID 1810026). Evidence: runtime-attempt3.log, attempt3_health_body.tmp, 11-shutdown-summary.txt.
  - Attempt 4: PASSED — Script `009-gate4-attempt4-startup-smoke` (external to the repository) was used by the operator to perform a run with an explicit per-run port override (SERVER_PORT=8890). Observed results: readiness achieved in 44 seconds (HTTP 200, sanitized health UP); graceful shutdown completed in 3 seconds; no SIGKILL required; port 8890 released; no tracked Maven/Java processes remained. Evidence: runtime-attempt4.log, attempt4_health_body.tmp, 09-startup-summary.txt, 10-readiness-response.txt, 11-shutdown-summary.txt.
- Notes:
  - The repository's application.yml resolves the port from SERVER_PORT (application.yml: port: ${SERVER_PORT:8888}); the documentation references 8090 while local runs commonly default to 8888 unless an explicit per-run SERVER_PORT override is used. No tracked file was modified to set 8890 — the per-run env override or an untracked local mechanism was used for Attempt 4. No change to tracked configuration is in scope for this stabilization step.
  - Script `009-gate4-attempt4-startup-smoke` resides outside the repository and was executed by the operator to produce Attempt 4 evidence; its invocation and artifacts are recorded in the evidence directory but the script itself is not committed to this repository.
- Gate 4 final decision: PASSED.
<<<<<<< HEAD
- Stop conditions: application fails to start, readiness not reached within agreed timeout.
- Recovery: collect logs and re-run Gate 4 as needed; do not modify tracked configuration to force a pass.
=======
  - Actuator readiness endpoint (http://localhost:8090/actuator/health) returns HTTP 200 within an agreed timeout
- Startup timeout: NOT_DEFINED in repository docs — this missing fact blocks Gate 4 until an agreed timeout is recorded. If executed in CI, use the CI job's enforced timeout and record it in 08-startup-command.txt.
- Stop conditions: application fails to start, readiness not reached within agreed timeout.
- Recovery: collect logs, fix configuration or infrastructure, re-run Gate 4 once underlying issues are resolved.
>>>>>>> 730a3fa8 (docs(sonarqube): define bug remediation stabilization gates)
=======
- Stop conditions: application fails to start, readiness not reached within agreed timeout.
- Recovery: collect logs and re-run Gate 4 as needed; do not modify tracked configuration to force a pass.
>>>>>>> dc79bc42 (docs(sonarqube): record successful application startup smoke)

Gate 5: branch push / CI / Sonar (ORDERED)
- Prerequisites: Gates 0–4 pass.
- Action: run the repository/organization CI pipeline or approved Jenkins job to perform CI validation and Sonar analysis. The repository contains scripts/ci/validate.sh which is the canonical CI-side validation script; prefer invoking it from CI.
- Evidence artifacts: 12-sonar-analysis-summary.txt, 13-sonar-scoped-bugs.json, 14-sonar-vulnerability-check.json, 15-sonar-code-smell-comparison.json
- Pass criteria:
  - CI job completes successfully (exit code 0)
  - Sonar analysis completes successfully
  - the 10 scoped BUG keys do not appear in the unresolved BUG query
  - no regression in the completed vulnerability workstream
- Blocked facts (repository evidence missing):
  - exact non-production Jenkins job name and its parameters (not present in repository)
  - whether the branch must be pushed before the job runs (not recorded in repository)
  - exact Jenkins credential IDs or credential variable names used for Sonar authentication (not recorded in repository)
  - whether Sonar analyses feature branches or only `dev` (not recorded in repository)
  - exact Sonar API query or project key usage for extracting the 10 scoped issue keys (not recorded in repository)

<<<<<<< HEAD
<<<<<<< HEAD

Because the above facts are not present in the repository, Gate 5 is BLOCKED for Sonar execution until the missing CI/Jenkins job details and authentication method are provided by CI/ops.
=======
  Because the above facts are not present in the repository, Gate 5 is BLOCKED for Sonar execution until the missing CI/Jenkins job details and authentication method are provided by CI/ops.
>>>>>>> 730a3fa8 (docs(sonarqube): define bug remediation stabilization gates)
=======

Because the above facts are not present in the repository, Gate 5 is BLOCKED for Sonar execution until the missing CI/Jenkins job details and authentication method are provided by CI/ops.
>>>>>>> 8b25627e (docs(sonarqube): record gates 0 through 3 stabilization)

Gate 6: exact issue-key verification and final documentation
- Prerequisites: Gate 5 pass.
- Command (evidence capture): export Sonar query results to 13-sonar-scoped-bugs.json and run the comparison.
- Evidence artifacts: 13-sonar-scoped-bugs.json, 14-sonar-vulnerability-check.json, 15-sonar-code-smell-comparison.json, 16-final-git-status.txt, 17-final-summary.txt
- Pass criteria: all ten scoped Sonar BUG issue keys absent from the open unresolved BUG query; no regression in vulnerability workstream; CODE_SMELL counts recorded for comparison only.
- Stop conditions: any scoped issue key still present, or vulnerability regression detected.
- Recovery: revert or fix changes, retest, and resubmit to CI/Sonar.

Notes (repository-proven or externally missing facts)
- Proven facts:
  - scripts/ci/validate.sh exists and validates compile/test/test+jacoco output in CI.
  - local startup command mvn spring-boot:run -Dspring-boot.run.profiles=local is documented and the Actuator health endpoint is the documented readiness signal (http://localhost:8090/actuator/health).
- Missing facts (must be provided by CI/ops):
  - non-production Jenkins job name and parameters that will run Sonar for this branch
  - whether the branch must be pushed to run the CI job
  - exact Sonar API query and credentials variable names used to extract and verify the 10 scoped BUG keys

Execution independence and evidence validity

- Gates 1–3 (compile, test-compile, full test) may execute after Gate 0 passes and produce valid evidence independently of Gate 4/5/6.
<<<<<<< HEAD
- Gates 1-4 (compile, test-compile, full test, startup smoke) are PASSED and their evidence remains valid.
- Gates 5–6 (CI/Sonar and issue verification) are independently BLOCKED by missing CI/Jenkins/Sonar invocation facts described in Gate 5.
- Successful completion of Gates 1–3 remains valid evidence while external blockers for Gates 4–6 are resolved; the workstream cannot be considered closed until Gates 5-6 pass.
=======
- Gate 4 (startup) is independently BLOCKED by the missing startup timeout and safe-shutdown procedures described above.
- Gates 5–6 (CI/Sonar and issue verification) are independently BLOCKED by missing CI/Jenkins/Sonar invocation facts described in Gate 5.
- Successful completion of Gates 1–3 remains valid evidence while external blockers for Gates 4–6 are resolved; the workstream cannot be considered closed until Gates 4–6 pass.
>>>>>>> 730a3fa8 (docs(sonarqube): define bug remediation stabilization gates)

After the stabilization run completes (or is blocked), collect and store the evidence directory contents and update docs/sonarqube/sonarqube-bug-remediation-plan.md with final statuses.

---
Generated by the SonarQube BUG remediation workflow. Do not run any commands without explicit authorization.
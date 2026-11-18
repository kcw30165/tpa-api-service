# 07 — Implementation Plan

## 1. Proposed file plan

All paths/classes marked `NEW (proposed)` are names for the future implementation, not existing code. Paths marked `MODIFIED` are existing files verified on `origin/dev`. Every Java counterpart is mapped to the matching Vesting Scale class; shell-only changes have no Java class counterpart.

| Status | Proposed path | Vesting Scale counterpart / purpose |
|---|---|---|
| NEW (proposed) | `src/main/java/com/bct/ngtpa/apiservice/application/port/out/DownloadCornerPort.java` | `VestingScalePort.java` |
| NEW (proposed) | `src/main/java/com/bct/ngtpa/apiservice/application/port/in/GetDownloadCornerUseCase.java` | `GetVestingScaleUseCase.java` |
| NEW (proposed) | `src/main/java/com/bct/ngtpa/apiservice/application/dto/downloadcorner/GetDownloadCornerCommand.java` | `GetVestingScaleCommand.java` |
| NEW (proposed) | `src/main/java/com/bct/ngtpa/apiservice/application/dto/downloadcorner/DownloadCornerCommand.java` | `VestingScaleCommand.java` |
| NEW (proposed) | `src/main/java/com/bct/ngtpa/apiservice/application/dto/downloadcorner/DownloadCornerResult.java` | `VestingScaleResult.java` |
| NEW (proposed) | `src/main/java/com/bct/ngtpa/apiservice/application/dto/downloadcorner/DownloadCornerData.java` | `VestingScaleData.java` |
| NEW (proposed) | `src/main/java/com/bct/ngtpa/apiservice/application/dto/downloadcorner/DownloadCornerCategory.java` | `VestingScalePlan.java` / category model |
| NEW (proposed) | `src/main/java/com/bct/ngtpa/apiservice/application/dto/downloadcorner/DownloadCornerItem.java` | `VestingScaleRow.java` / item model |
| NEW (proposed) | `src/main/java/com/bct/ngtpa/apiservice/application/usecase/downloadcorner/GetDownloadCornerService.java` | `GetVestingScaleService.java` |
| NEW (proposed) | `src/main/java/com/bct/ngtpa/apiservice/application/exception/DownloadCornerRetrievalException.java` | `VestingScaleRetrievalException.java` |
| NEW (proposed) | `src/main/java/com/bct/ngtpa/apiservice/config/downloadcorner/DownloadCornerApplicationConfig.java` | `VestingScaleApplicationConfig.java` |
| NEW (proposed) | `src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/downloadcorner/ApimDownloadCornerAdapter.java` | `ApimVestingScaleAdapter.java` |
| NEW (proposed) | `src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/downloadcorner/TrpGetDownloadCornerApimMapper.java` | `TrpGetVestingScaleApimMapper.java` |
| NEW (proposed) | `src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/downloadcorner/DownloadCornerApimProperties.java` | `VestingScaleApimProperties.java` |
| NEW (proposed) | `src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/downloadcorner/DownloadCornerApimValidationConfig.java` | `VestingScaleApimValidationConfig.java` |
| NEW (proposed) | `src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/downloadcorner/DownloadCornerFailureReason.java` | `VestingScaleFailureReason.java`; reuse only the verified semantics |
| NEW (proposed) | `src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/downloadcorner/DownloadCornerMalformedResponseException.java` | `VestingScaleMalformedResponseException.java` |
| NEW (proposed) | `src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/downloadcorner/dto/TrpGetDownloadCornerApimRequest.java` | `TrpGetVestingScaleApimRequest.java` |
| NEW (proposed) | `src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/downloadcorner/dto/TrpGetDownloadCornerApimDataItem.java` | `TrpGetVestingScaleApimDataItem.java`; nested category/item DTOs represent the locked response |
| NEW (proposed) | `src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/downloadcorner/dto/ApimField.java` | `vestingscale/dto/ApimField.java`, if presence-aware parsing is needed |
| NEW (proposed) | `src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/downloadcorner/dto/ApimFieldDeserializer.java` | `vestingscale/dto/ApimFieldDeserializer.java`, if presence-aware parsing is needed |
| NEW (proposed) | `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/error/DownloadCornerExceptionMappings.java` | `VestingScaleExceptionMappings.java` |
| NEW (proposed) | `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/downloadcorner/DownloadCornerController.java` | `VestingScaleController.java` |
| NEW (proposed) | `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/downloadcorner/DownloadCornerWebMapper.java` | `VestingScaleWebMapper.java` |
| NEW (proposed) | `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/downloadcorner/response/DownloadCornerResultResponse.java` | `VestingScaleResultResponse.java` |
| NEW (proposed) | `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/downloadcorner/response/DownloadCornerDataResponse.java` | `VestingScaleDataResponse.java` |
| NEW (proposed) | `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/downloadcorner/response/DownloadCornerCategoryResponse.java` | `VestingScaleSectionResponse.java` / category element |
| NEW (proposed) | `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/downloadcorner/response/DownloadCornerItemResponse.java` | `VestingScaleRowResponse.java` / item element |
| MODIFIED | `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/error/ExceptionMappingRegistryConfig.java` | Existing registry; explicitly register Download Corner mapping |
| MODIFIED | `src/main/java/com/bct/ngtpa/apiservice/shared/error/ErrorCodes.java` | Existing public error constants; add proposed retrieval code |
| MODIFIED | `src/main/resources/application.yml` | Vesting `apim.*.path` convention; add proposed Download Corner path |
| MODIFIED | `src/test/java/com/bct/ngtpa/apiservice/architecture/M3R1aOwnershipActivationGuardTest.java` | Existing guard; include Download Corner terminal-caller adapter |
| NEW (proposed) | `src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/downloadcorner/ApimDownloadCornerAdapterTest.java` | `ApimVestingScaleAdapterTest.java` |
| NEW (proposed) | `src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/downloadcorner/TrpGetDownloadCornerApimDtoContractTest.java` | `TrpGetVestingScaleApimDtoContractTest.java` |
| NEW (proposed) | `src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/downloadcorner/TrpGetDownloadCornerApimMapperTest.java` | `TrpGetVestingScaleApimMapperTest.java` |
| NEW (proposed) | `src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/downloadcorner/DownloadCornerApimConfigurationTest.java` | `VestingScaleApimConfigurationTest.java` |
| NEW (proposed) | `src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/downloadcorner/dto/ApimFieldDeserializerTest.java` | `vestingscale/dto/ApimFieldDeserializerTest.java`, if helper is mirrored |
| NEW (proposed) | `src/test/java/com/bct/ngtpa/apiservice/application/usecase/downloadcorner/GetDownloadCornerServiceTest.java` | `GetVestingScaleServiceTest.java` |
| NEW (proposed) | `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/downloadcorner/DownloadCornerControllerTest.java` | `VestingScaleControllerTest.java` |
| NEW (proposed) | `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/downloadcorner/DownloadCornerWebMapperTest.java` | `VestingScaleWebMapperTest.java` |
| NEW (proposed) | `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/downloadcorner/DownloadCornerResponseSerializationContractTest.java` | `VestingScaleResponseSerializationContractTest.java` |
| NEW (proposed) | `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/error/DownloadCornerExceptionMappingRegistrationTest.java` | Feature-specific registry assertion; no existing Download Corner counterpart |
| NEW (proposed) | `scripts/e2e/download-corner-scenarios.sh` | `scripts/e2e/vesting-scale-scenarios.sh`; read-only two-language pack |
| MODIFIED | `scripts/e2e/lib/feature-pack-registry.sh` | Register the Download Corner scenario pack; this is the live registry path, not the L16 path lacking `lib/` |
| NEW (proposed) | `scripts/e2e/tests/download-corner-scenarios-test.sh` | Shell-level contract test for the new pack; exact test harness/path is UNVERIFIED |

The response DTO plan includes only the wrapper/data/category/item structures required by Download Corner. Vesting-specific overview, plan, and presence-section response types have no Download Corner equivalent.

## 2. M0 — Design pack

**Status: APPROVED by Nick on 2026-11-18 at commit `202fe9bb08ca6a21bf2558aedf8eab8e25945c92`** (design pack `fd04d50f7697dfd41e7b9f5c0473e81672e4be92` plus fix-up `202fe9bb08ca6a21bf2558aedf8eab8e25945c92`). M0 is documentation-only; no TDD evidence was required. M0 approval clears the design gate for M1 only.

## 3. M1 — Outbound (same MR as M2)

**Status: READY TO START (not started).**

Implement typed APIM request/response mapping, path properties/YAML, executor read exchange, strict response checks, failure events, and the ownership-guard update. Add and run failing adapter/DTO/mapper/config/guard tests first, then implement and rerun them.

Done when the APIM boundary, raw non-empty error-code event field, malformed-field event dimensions, failure mapping, and targeted tests are green.

## 4. M2 — Inbound/use case (same MR as M1)

M2 still requires an explicit go-ahead at its milestone gate, notwithstanding M0 approval.

Implement application input/output ports and commands, use case, application wiring, language-aware web mapping, feature controller and response DTOs, exception mapping and explicit registry entry. Add the registration test and other inbound/contract tests fail-first.

Done when mapper, service, controller, response serialization, and mapping-registration tests are green and the full Java milestone gates pass.

## 5. M3 — Read-only E2E pack (separate MR)

M3 still requires an explicit go-ahead at its milestone gate, notwithstanding M0 approval.

Add the Download Corner scenario script, register it, and invoke it only through `bash scripts/e2e/run-post-login.sh`. Use the shared account selector exactly as it exists on `origin/dev`; the current selector rejects simultaneous `E2E_ACCOUNT_REF` and a non-empty `E2E_ACCOUNT_INDEX` (`scripts/e2e/lib/authenticated-login-journey.sh:78-97`). Do not modify the selector or add selector rejection tests/scenarios. Do not introduce any live mutation or file fetch.

Done when EN/ZH response checks, ordering/shape checks, and shell-level fail-first scenario-pack evidence pass. M3 has no Java gates.

## 6. MR and validation boundaries

- **MR 1:** M1 + M2 together.
- **MR 2:** M3 separately.
- M1/M2 Java gates G1–G4 are described in §7. M0 approval authorizes M1 only; M2 still requires its own explicit go-ahead. Record evidence without secret values.
- M3 is shell-only; use the relevant shell/scenario checks and report no Java gates.
- Each milestone records test-first failure and passing evidence. No Vesting Scale code or behavior is changed.

## 7. Milestone gate commands

These are documentation of commands and repository evidence, not commands run for M0. The toolchain exports below use the validated WSL paths; the repository also checks for Java 21 and Maven 3.9.x (`docs/coverage/code-coverage-95-plan.md:209-211`; `scripts/ci/validate.sh:14-24`). The corresponding `JAVA_HOME`, `M2_HOME`, and `PATH` setup is recorded in the source documentation (`docs/capabilities/apim-error-logging/architecture-and-design.md:110-118`).

### G1 — Full regression (CONFIRMED)

Run the full Maven `test` phase, with JUnit parallel execution disabled as documented (`README.md:222-230`). This runs the repository's `src/test/java` suite, including any unit and integration tests bound to that phase. No separate `src/integration-test` source set or Failsafe goal is present in the origin/dev CI test stage; `scripts/ci/validate.sh:37-38` labels the stage `unit_tests_and_coverage` and invokes `test jacoco:report`.

```bash
export JAVA_HOME="/home/nd2887/tools/jdk-21.0.12.1+1"
export M2_HOME="/home/nd2887/tools/apache-maven"
export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH"
mvn -T 1C -Djunit.jupiter.execution.parallel.enabled=false test
```

**Pass/fail and counts:** require exit code 0 / Maven `BUILD SUCCESS`; a non-zero exit or test failures/errors is a failed gate. Read the Maven test summary and retain `target/surefire-reports/TEST-*.xml`; `scripts/ci/validate.sh` verifies that Surefire XML reports exist and reports their file count (`scripts/ci/validate.sh:37-59`). Use each XML report's test, failure, error, and skipped counts for the suite totals; the CI script's `surefire_xml_count` is the number of report files, not the number of tests. No separate Failsafe report path is evidenced.

**Record in `REPORT.md`:** exact command/toolchain versions, exit status, Maven test summary, aggregate test/failure/error/skipped counts, and the Surefire report path/count. Do not copy secret values or `.env` contents.

### G2 — JaCoCo (CONFIRMED)

The repo CI command runs tests and explicitly invokes JaCoCo's report goal (`scripts/ci/validate.sh:26,37-59`):

```bash
mvn -B -ntp -Dstyle.color=never test jacoco:report
```

JaCoCo 0.8.12 is configured in `pom.xml`. The POM configures `prepare-agent` without an explicit phase and binds `report` to `test`; it does not bind `jacoco:check` or define JaCoCo check rules (`pom.xml:191-220`). The CI script requires a non-empty `target/site/jacoco/jacoco.xml` (`scripts/ci/validate.sh:40-53`). The configured exclusions are `*Config`, `*Properties`, and `package-info` classes (`pom.xml:195-204`).

There is **no enforced coverage threshold** in this gate. The coverage initiative records an interim target of about 90% instruction coverage, with branch coverage tracked but not gated, and recommends deferring the JaCoCo check threshold (`docs/coverage/code-coverage-95-plan.md:69-73`). For Download Corner, inspect the generated XML's package/class entries whose implemented production package is `downloadcorner`, and record their instruction/line counters; the repository config does not define a separate package-only report command or threshold.

**Record in `REPORT.md`:** exact command, exit status, report path and non-empty check, exclusions, and Download Corner package/class coverage counters. Do not report a threshold pass because none is enforced.

### G3 — Startup smoke (UNVERIFIED overall; local/deployed evidence differs)

**Local startup command (CONFIRMED):** the README documents the local profile and startup-critical settings (`README.md:233-260`). The profile can also be selected with `SPRING_PROFILES_ACTIVE=local` (`README.md:235`); the command below selects it through the Spring Boot Maven property. Required startup-critical environment-variable names are:

- `LOGIN_PREAUTH_CONTEXT_KEY_VERSION`, `LOGIN_PREAUTH_CONTEXT_SECRET` (`src/main/resources/application.yml:185-188`)
- `AUTH_WORKFLOWS_LOGIN_SESSION_PROTECTION_BASE64_KEY` (`src/main/resources/application.yml:224-226`)
- `AUTH_WORKFLOWS_REGISTRATION_SECRET` (`src/main/resources/application.properties:1-4`; specifically the registration secret mapping)
- `FORGOT_PASSWORD_CONTEXT_KEY_VERSION`, `FORGOT_PASSWORD_CONTEXT_SECRET` (`src/main/resources/application.yml:128-131`)

The application defaults `SERVER_PORT` to `8888` (`src/main/resources/application.yml:1-2`). Redis Sentinel connectivity is also a local runtime prerequisite unless Redis is disabled, and the WSL smoke notes call for Redis forwarded ports and TLS files (`README.md:215-220,254-260`; `docs/wsl/phase-04-direct-spring-runtime.md:19-29`). No secret values or `.local` contents belong in evidence.

```bash
export JAVA_HOME="/home/nd2887/tools/jdk-21.0.12.1+1"
export M2_HOME="/home/nd2887/tools/apache-maven"
export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH"
mvn -T 1C -DskipTests -Dspring-boot.run.profiles=local spring-boot:run
```

The documented local smoke pass criteria are successful startup, Redis TLS/Sentinel initialization, HTTP 200 from `/actuator/health`, a complete file log, and clean shutdown (`docs/wsl/phase-04-direct-spring-runtime.md:54-60`). However, the same smoke note polls port `8090` (`docs/wsl/phase-04-direct-spring-runtime.md:24-30`), while the live application configuration and README specify default port `8888` (`src/main/resources/application.yml:1-2`; `README.md:248-252`). The WSL note says a runtime controller will wrap the command, but no controller path is identified there (`docs/wsl/phase-04-direct-spring-runtime.md:15-30`). Therefore the exact local health-probe command/port and smoke-wrapper path remain **UNVERIFIED**; do not invent a `curl` command until that discrepancy is resolved.

**DEV-deployed smoke (script confirmed):** the repo's post-deployment check uses `.devsecops/healthcheck.sh` (`docs/wsl/phase-08-jenkins-quality-correction.md:33-40`). It requires `APPLICATION_BASE_URL`, accepts the environment name as its positional argument, and optionally accepts `HEALTHCHECK_TIMEOUT_SECONDS` (default 10); success is HTTP 200 and JSON status `UP` (`.devsecops/healthcheck.sh:4-35`).

```bash
APPLICATION_BASE_URL=<approved-dev-base-url> .devsecops/healthcheck.sh dev
```

The approved DEV base URL and the Jenkins deployment invocation are not recorded in the repo; provide the approved non-secret URL at execution time. Do not put an access token or secret in this command.

**Record in `REPORT.md`:** local active profile, toolchain versions, names of required environment variables (never values), startup exit result, sanitized log path, health endpoint/port and HTTP status, Redis startup result, and clean-shutdown result. For DEV, record commit/deployment identity, environment, healthcheck output, HTTP status, and `UP` result.

### G4 — Sonar (UNVERIFIED)

No Sonar Maven plugin/property, scanner config, project key, server URL property name, or token environment-variable name was found in the repository's build/CI configuration. The Sonar remediation note points to an approved CI/Jenkins job but explicitly leaves the job, credential variable, branch-analysis behavior, project-key/API query unrecorded (`docs/sonarqube/sonarqube-bug-remediation-stabilization.md:290-304`). `scripts/ci/validate.sh` is a compile/test/JaCoCo script, not a Sonar invocation (`scripts/ci/validate.sh:28-59`).

```text
UNVERIFIED — no repository-evidenced Sonar command or exact CI/Jenkins job invocation.
```

The repo does not document how a developer checks NEW issues for changed files, nor an exact Sonar quality-gate criterion for this feature. Do not invent a scanner command, server property, project key, token variable, UI route, or query. Confirmation requires CI/ops evidence for the job/stage and invocation, Sonar server URL property, project key source, token **variable name only**, analyzed branch/commit behavior, and the approved changed-file/New Code issue check. The documented broad CI gate is owned by Jenkins (`docs/wsl/copilot-evidence-gate-operating-model.md:25-31`; `docs/wsl/phase-08-jenkins-quality-correction.md:33-40`).

**Record in `REPORT.md` once confirmed:** exact job or command and source citation, analyzed commit/branch, Sonar analysis and quality-gate result, changed-file/New Code issue result, and evidence link/path. Never record the token value.

### Evidence convention

Each implementor prompt writes its evidence to its own unique `target/evidence/<prompt-specific-folder>/REPORT.md`.

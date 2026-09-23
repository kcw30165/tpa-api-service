# CLAUDE.md

> Converted from the audited `.github/copilot-instructions.md` (guidance repo
> HEAD `3320553`, branch `feature/copilot-instructions`). Global, always-on
> guardrails equivalent to Copilot's top-level instructions file. Path-scoped
> rules live under `.claude/rules/`; reusable workflows live under
> `.claude/skills/`.

This repository is a Java 21 / Spring Boot WebFlux BFF following Clean Architecture / Hexagonal Architecture.

## Repository Topology

The working directory contains the `api-service` code repository. A separate
guidance corpus (originally `.github/`, now converted to this `CLAUDE.md` +
`.claude/` structure) documents Copilot/Claude-facing conventions. See your
team's decision on whether this `.claude/` directory is tracked in this repo
or kept as a separate history -- do not assume either way without checking
`.gitignore` first.

## Current active workstream (as of 2026-09-17)

The code repository has an active, parallel, **test-only** coverage
initiative on branch `feature/increase-code-coverage-95`. This workstream
never modifies production code. If the current task is coverage-related,
`.claude/rules/coverage-workstream.md` applies and is stricter than the
general TDD guidance below -- read it first. Because the workstream is
parallel, do not infer what this branch does or does not contain from other
workstreams; verify with `git log`/`git merge-base`.

## Source Of Truth Order

- `.claude/rules/current-project-direction.md` is the canonical current-direction source of truth (loads unconditionally, same as this file).
- Path-scoped files under `.claude/rules/` refine the rules for matching files.
- Skills under `.claude/skills/` provide reusable workflows only; they must not override current direction.
- Repository source code and tests are evidence of the current implementation pattern -- trust `ApimRoutingLedgerCharacterizationTest` (a *characterization* test: it records current behavior and does not itself forbid change) and `SharedIdempotencyNoFeatureLocalLifecycleArchitectureTest` (a hard guard) over any historical prompt or memory.
- The detailed APIM architecture is documented in `docs/integration/apim/apim-exchange-executor-architecture-and-developer-guide.md`.
- Historical prompts describing completed, superseded migration stages should not be resumed.

If any older guidance conflicts with current project direction, current project direction wins.

## Operating Rules

Before changing production code:
- read this file and all matching `.claude/rules/*.md` files;
- **first establish whether the task is a behavior change or a coverage task** -- the two have different, partly contradictory rules (see the coverage workstream note above);
- use the project-direction-review skill when the task is non-trivial or architecture-sensitive;
- identify the expected observable behavior and the correct test level;
- add or update a failing test first unless the task is documentation-only or explicitly impossible to test first;
- avoid implementation until requirements, boundaries, response contract, and test strategy are clear.

Do not claim tests passed unless they were actually executed.

## Canonical Project Decisions

### Context And Session
- Use PortalAccessContext, PortalAccessContextPort, CurrentPortalAccessContextResolver, and TemporaryPortalAccessContext terminology.
- Confirmed shapes: `PortalAccessContext(ActorContext actor, AccountContext account)`; `ActorContext(String actorUserId, ActorUserRole actorUserRole)`. Do not assume additional `ActorContext` fields exist or are planned.
- Do not introduce or reintroduce MemberContext, MemberContextPort, TemporaryMemberContext, MemberOwnerContext, or compatibility bridges.
- `sid_xxx` is the session transport header name; do not use `session-id` in new code.

### Identity Naming -- scoped
- The `username`/`identityValue`/`identityType` unification (commits `2e1bdbaa`, `59eea925`) is scoped specifically to `ForgotPasswordAuthFlowContext`.
- **`customerId` and `normalizedUsername` remain correct and actively used everywhere else** -- Login (`CredentialAuthenticationResult`, `LoginPreAuthContext`, `GetLoginAccountsCommand`), Registration (`RegistrationCompletionCommand`), Forgot Password's other commands (`ForgotPasswordCompletionCommand`, `ForgotPasswordDetailsLookupCommand`), and all APIM DTOs bound to `cust-id`. There is no repo-wide unification rule.

### Account-Ref
- Optional at global header extraction; required only for selected-account/member APIs after login/account resolution.
- Login, account-list, registration, OTP, captcha, username recovery, and sub-account resolution must not require it.
- For multi-account APIs, FE sends only the selected Account-Ref.

### Environment Naming
- Use `accountEnv` internally; use APIM `env` only at the outbound boundary under `adapter/out/apim`.
- Do not use `environment` or `deploymentEnv` for business context.

### Language
- Accept-Language is canonical. No `lang` query parameter or fallback. No bilingual messages in a single response.

### Request Correlation
- Use `shared.web.RequestCorrelation` constants; reuse inbound `X-Request-Id` or generate when missing; return it in response headers and propagate through Reactor Context.
- Only `X-Request-Id` is approved for APIM propagation.
- Do not put `requestId` in response bodies or error objects, and do not conflate it with `Idempotency-Key`.

### Response Schema
```json
{ "success": true, "status": "SUCCESS", "result": {}, "messages": [], "errors": [] }
```
- Blocking errors in `errors[]`; non-blocking notifications in `messages[]`.
- Mutation endpoints follow the standardized mutation/base response pattern with shared contract tests.
- Legacy unwrapped read endpoints may remain only for explicit frontend compatibility.
- **Login credential-mismatch collapse (Login only):** `LoginErrorKey.CREDENTIALS_INVALID` (`err.login.credentials.invalid`) is the single public code for both wrong-username and wrong-password. Internal statuses keep the distinction legitimately; the collapse happens in `LoginStartWebMapper`, guarded by `LoginCredentialExposureGuardTest` (commit `a70bd5a6`). No evidence this extends to Forgot Password -- verify separately before assuming.

### APIM Boundary
- All APIM calls go through `ApimExchangeExecutor.postEnvelope(ApimExchangeRequest<R,D>)`. See `.claude/rules/apim-adapter.md` for the confirmed 18-operation ledger and record shapes.
- `ApimExchangeRequest` rejects a blank path, a mismatched request/requestType pair, and a blank `accountEnv` under `ACCOUNT_ENV` scope.
- `ApimDatabaseScope`: `NON_ACCOUNT` | `ACCOUNT_ENV`. `ApimRetryEligibility`: `SAFE_BUSINESS_CALL` | `IDEMPOTENT_MUTATION` | `NON_RETRYABLE`.
- `ActorUserRole` (11 values): `ROLE_INPUT, ROLE_APPROVE, ROLE_USER_MBR, ROLE_USER_EPR, ROLE_AGENT, ROLE_ITADMIN, ROLE_CCD, ROLE_SA, ROLE_BROKER, ROLE_CCC, ROLE_OPERATION`.
- `DefaultApimExchangeExecutor` wires `ApimWebClientFacade`, `ApimPayloadCryptoService`, `ApimProperties`. Do not reference these (or `ApimCertificateService`/`ApimAppCertificateService`/credential resolvers) from a feature adapter.

### Shared Idempotency Capability
- Registration, Forgot Password, and Username Recovery mutation endpoints share one capability under `application/idempotency/` -- implemented and merged to `dev` (`857f2f9a`). See `.claude/rules/idempotency.md`.
- `SharedIdempotencyNoFeatureLocalLifecycleArchitectureTest` fails the build if a new feature-qualified idempotency lifecycle type is introduced.

### Logging And Security
- Never log secrets, tokens, API keys, certificates, keys, policy/certificate numbers, user IDs, passwords, or client secrets.
- Body logging disabled by default, endpoint-level opt-in only; binary responses never body-logged.

## Strict TDD Mode (behavior-change tasks)

Identify observable behavior; choose the test level; add the failing test first; run and record the expected failure; implement the smallest production change; re-run; run broader validation; refactor only after green. Production code must not change before a failing test exists, except for documentation-only or purely mechanical work.

**This section does not apply to coverage tasks** -- see `.claude/rules/coverage-workstream.md`, where production code must not change at all.

## Clean Architecture Boundaries

```
adapter/in/web -> application/port/in -> application/usecase -> application/port/out -> domain
adapter/out/*  -> application/port/out -> domain
config         -> Spring composition and property binding only
infrastructure -> Spring/framework mechanisms only
shared         -> framework-free shared contracts/constants where appropriate
```

Domain is pure Java. Application is framework-free. Controllers stay thin. Web response shaping lives in `adapter/in/web` mappers. APIM transport lives under `adapter/out/apim`. Global `config/` is composition/property binding only.

**Controller layout:** controllers are **feature-colocated** under `adapter/in/web/{feature}/` (e.g. `adapter/in/web/accountbalance/`, `adapter/in/web/auth/login/`). There is **no centralized `adapter/in/web/controller/` package** and none is wanted -- place a new controller in its feature's package alongside that feature's web mappers, request/response DTOs, and validators. `docs/refactor/controller/` tracks the classification/refactor effort over this layout.

## Documentation locations (confirmed to exist)
- `docs/capabilities/idempotency/` (12 files) -- update whenever idempotency behavior changes.
- `docs/integration/apim/` (6 files) -- canonical APIM architecture reference.
- `docs/refactor/controller/` (10 files, confirmed 2026-09-18) -- controller classification/refactor tracking.
- `docs/coverage/code-coverage-95-plan.md` -- coverage plan, targets (D1/D2), and the defect/refactor backlog used by the coverage workstream.
- `docs/design/captcha/` **does not exist** -- do not reference it.

## Validation Commands

Confirmed WSL toolchain: Java 21.0.12.1 (Temurin) at `/home/nd2887/tools/jdk-21.0.12.1+1`, Maven 3.9.15 at `/home/nd2887/tools/apache-maven`.
```bash
export JAVA_HOME="/home/nd2887/tools/jdk-21.0.12.1+1"
export M2_HOME="/home/nd2887/tools/apache-maven"
export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH"
mvn -Dtest=<TargetTestClassName> test
mvn test
mvn clean verify
mvn -DskipTests compile
mvn -DskipTests test-compile
mvn spring-boot:run -Dspring-boot.run.profiles=local
```
Prefer `mvn -DskipTests compile`/`test-compile` during implementation loops; run full `mvn test` at stage-gate boundaries. Defer `mvn spotless:apply` to stabilization.

### Test Result Inspection

After every Maven test execution, inspect `target/surefire-reports/` (and `target/failsafe-reports/`) before concluding pass/fail -- console output may be truncated.
```bash
find target/surefire-reports -maxdepth 1 -type f -print
cat target/surefire-reports/*.txt
```

## Final Response / PR Evidence

For behavior changes: failing test added first; expected failure reason; targeted test command; targeted failure result; production change made; targeted pass result; broader validation result; tests not run and why.

For coverage changes: targets attempted; coverage before/after; tests added; **explicit confirmation that no `src/main/java` file was modified**; any targets deferred as refactor candidates and where they were recorded; any defects discovered and where they were logged.

For documentation-only changes: state that TDD does not apply because no production behavior changed.

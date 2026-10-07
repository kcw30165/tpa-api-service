# Fund Fact Sheet Implementation Plan

**Branch:** `feature/impl-fund-fact-sheet`
**Design baseline:** commit `333418217bc24747ae21ff3b4de3bae8348ecc25` (adds `docs/design/11-fund-fact-sheet/`) plus the Stage 0 correction commit (record the correction commit's hash in Stage 1 evidence)
**Code baseline:** `origin/dev` at `c12d2cc4f23f87f5987252c6c3b55c2439355863`

This plan is the execution contract for the design pack. Where it and another pack document disagree, stop and correct the pack before writing code.

---

## 1. Ground rules

### 1.1 Scope guardrails

- Account Balance and Account Summary production and test sources are **not modified**. Stage 5 proves this with a diff check.
- `AccountBalanceFactsheetResolver` stays as it is; `UrlValidator` is used only by Fund Fact Sheet.
- No message configuration is added. Java returns error codes only.
- No change to `ApimExchangeExecutor`, `ApimExchangeOutcomeLogger`, `ApimEnvelopeSchemaClassifier` or `ApimResponseValidator`.
- No values in logs: no URLs, fund names, `seq`, policy number, certificate number or account identifiers.
- Feature code calls APIM only through `ApimExchangeExecutor.postEnvelope(...)`.

### 1.2 Package rule

Every package that was marked for confirmation was resolved by the Stage 0 investigation (section 2.1); where a package differed from the inference, the tables below are already corrected. Packages marked **(evidenced)** were seen in investigation evidence at `c12d2cc4`.

### 1.3 Script and evidence conventions

- Scripts live in `/mnt/d/Projects/ORSO/llm-scripts/api-service/` and are named `fund-fact-sheet-<NNN>-<purpose>.sh`.
- Script numbers increase on every new script, including fixes and reruns, and are never reused. Scripts 001-016 are used by the investigation and Stage 0 (008-016); the Stage 0 correction commit is script 017; Stage 1 starts at the next free number.
- Every script creates `target/evidence/<script-name>-<timestamp>/` with sequentially numbered files: pre-state, each stage, audits, `git diff --check`, final status, diff stat, name-status, exit codes and preserved failure logs.
- Every script packages its evidence as `target/evidence/<script-name>.zip.txt` and copies it to `/mnt/d/Projects/ORSO/llm-scripts/api-service/`.
- Run command format: `run_notify "Script <filename>" bash /mnt/d/Projects/ORSO/llm-scripts/api-service/<filename>`.
- Use `python`, not `python3`.
- Commit-message checks use Bash substring matching on captured subjects, not `grep`.
- Evidence dumps prefix every line with `Lnnnn| ` and write `<`, `>` and `${` as `[LT]`, `[GT]` and `[DLR]{`. Never emit `cat -n` output or lines that start with whitespace: the evidence upload conversion drops them (scripts 008 and 009).

### 1.4 Validation inside scripts

- Toolchain: Maven at `/home/nd2887/tools/apache-maven`, JDK at `/home/nd2887/tools/jdk-21.0.12.1+1`.
- Scripts run only `mvn -DskipTests compile` and `mvn -DskipTests test-compile`, immediately after the file changes.
- Scripts do not run `mvn test`. Targeted tests and the full suite are run manually.
- Maven output is not copied into evidence; record the command, exit code, a short summary and failure diagnostics only.
- `mvn spotless:apply` is deferred to Stage 5. Every earlier script writes an evidence file stating formatting was intentionally deferred.
- On compile failure, stop and repair all compile errors in the next script.

### 1.5 Stage gates

Before moving from one stage to the next:

1. A targeted run of the architecture guards listed in section 1.7 passes (run manually; it exposes guard failures before the long run).
2. The full Maven test suite passes (run manually): `mvn test`. The repository CI wrapper `scripts/ci/validate.sh` runs `spotless:check`, compile, test-compile and `mvn test jacoco:report`.
3. A Spring application startup smoke test passes with no bean or configuration errors: `mvn spring-boot:run -Dspring-boot.run.profiles=local`. Readiness is `/actuator/health` returning HTTP 200 (`docs/wsl/phase-04-direct-spring-runtime.md` documents port 8090, the README documents default port 8888: use the port the local run actually binds). Startup timeout 120 seconds; shutdown is SIGTERM, a 30 second wait, and SIGKILL only if the tracked process is still alive. Check beforehand that the configured server port is free: three worktrees share the default `SERVER_PORT`.
4. The stage commit exists on the branch.

CI runs `spotless:check` first. Stage commits 1-4 are therefore not pushed on their own; `mvn spotless:apply` runs in Stage 5 before the branch is pushed.

### 1.6 Escalation

- Three consecutive failed scripts: stop and explain failures, likely root cause, repository state, risks and recovery options before generating another script.
- Ten scripts without reaching a commit: stop and escalate the same way.

### 1.7 Guard constraints from the Stage 0 investigation

These architecture and text-scanning guards apply to the new files. Several scan source text, so a comment counts.

| Guard | Rule | Consequence |
|---|---|---|
| `AuthenticatedContextExplicitUseCaseBoundaryTest` | No file under `adapter/out` may contain `PortalAccessContext`, `CurrentPortalAccessContextResolver`, `PortalAccessContextKeys`, `ACTOR_CONTEXT` or `ACCOUNT_CONTEXT` (whitespace ignored) | Nothing under `adapter.out.apim.fundfactsheet` mentions them, even in comments |
| `Stage021LegacyApimOrchestrationClosureFailFirstTest` | Files under `adapter/out/apim` outside the foundation folders must not contain `ApimWebClientFacade`, `ApimCertificateService`, `ApimPayloadCryptoService`, `ApimCredentialProfileResolver`, `resolveProfile(`, `getPublicKey(`, `encryptRequest(`, `decryptResponseEnvelope(` or `getEncryption().isEnabled()`, and must not infer the scope from `accountEnv` | Adapter code and comments avoid these names |
| `ApimExecutionContractArchitectureTest` | `adapter/out/apim/execution` holds exactly its 7 current files | Add nothing there |
| `PublicDownstreamStatusIsolationGuardTest` | No file under `adapter/in/web` may contain `ApiStatus.DOWNSTREAM_ERROR` or `ApiStatus.DOWNSTREAM_REJECTED` | The handler, controller and mapper never name them |
| `CleanArchitectureDependencyTest` | Application classes must not depend on `adapter`, `config`, Spring or `shared.logging`; application port names must not match the web, page, view, form, response, presenter or mapper name pattern; APIM `dto`, `crypto`, `credential`, `oauth`, `certificate` and `client` packages must not be used outside `..adapter.out.apim..`; only the root `config` package is restricted to `*Config` and `*Properties` names | `FundFactSheetPresentationEventPort` passes ("Presentation" does not contain "Presenter"); `config.fundfactsheet` is exempt from the naming rule |
| `InboundWebControllerArchitectureTest` | Lists 11 controllers and asserts 36 routes; the old flat `adapter/in/web/controller` folder holds no `.java` | The new controller is not in its list; do not place it in that folder |
| `FrontendOwnedLocalization*` tests | Registration mappers, filters and page responses return codes only | Not applicable to Fund Fact Sheet; consistent with returning codes only |

About 45 other source-scanning guards were listed but not read in full. They are covered by the targeted guard run and the full suite at each stage gate.

---

## 2. Stage 0 — Pre-implementation investigation (read-only)

**Scripts:** `fund-fact-sheet-008` to `fund-fact-sheet-016` (read-only, no commit). **Status: complete.** Results are in section 2.1; the pack corrections are applied by the Stage 0 correction commit (script 017).

Confirm, with full source dumps where relevant:

| # | Question | Why |
|---|---|---|
| 0.1 | Design pack committed; HEAD, branch, clean tree | Baseline |
| 0.2 | Existing framework-free shared application packages (e.g. under `application`) | Home for `UrlValidator` |
| 0.3 | Location and shape of `FundPriceRetrievalException` and its handler in `ApiExceptionHandler` | Exception and handler pattern |
| 0.4 | `ApiError.system(...)` signature and an existing caller | Handler mapping |
| 0.5 | `FundPriceModeApimProperties`, `FundPriceModeApimValidationConfig`, their YAML binding and tests | Properties pattern |
| 0.6 | `ApimFundPriceModeSourceAdapter` and its test | Adapter pattern, including `ApimCredentialResolutionContext` construction |
| 0.7 | `ReferenceDatePort` package and an existing caller | Use-case wiring |
| 0.8 | `FundPriceModeApplicationConfig` or the nearest application config | Bean wiring |
| 0.9 | Account Gain/Loss use-case port, command and service | Use-case shape |
| 0.10 | `CleanArchitectureDependencyTest` and other architecture guards that scan by package or name | Guard impact |
| 0.11 | `MutationResponse.success` and `ApiStatus` | Response mapping |
| 0.12 | Startup and full-suite commands used by previous stage gates | Gate procedure |

**Exit (met):** every package that was marked for confirmation is resolved in the evidence; where one differed, this plan is already updated.

### 2.1 Stage 0 results

| # | Result |
|---|---|
| 0.1 | HEAD `33341821`, branch `feature/impl-fund-fact-sheet`, clean tree; the 14 pack files were byte-identical across scripts 008-016; baseline compile and test-compile green |
| 0.2 | `UrlValidator` goes in `application.service.presentation` (the framework-free `DynamicDescriptionResolver` and `ExactPrecisionRenderer` already live there). `AccountBalanceFactsheetResolver` behaves exactly as the design says. `DynamicDescriptionResolver` is not reusable: for `en` it falls back to the Chinese name, and it throws when both names are blank |
| 0.3 | `FundPriceRetrievalException` is a final class in `application.exception` with a private constructor and `systemFailure()`; its handler is in `adapter.in.web.advice.ApiExceptionHandler` |
| 0.4 | `ApiError.system(String code, String message, String source)` gives type `SYSTEM`; "SERVER" is the source. Several callers pass a null message |
| 0.5 | Properties use `validate()` with a trim, called from a validation-config constructor; registered by `@ConfigurationPropertiesScan` and `@EnableConfigurationProperties`; test precedent `AccountBalanceApimConfigurationTest` |
| 0.6 | Adapter: `@Component`, public `@Autowired` constructor delegating to a package-private one, `ApimExchangeRequest` with `ACCOUNT_ENV`, `ApimCredentialResolutionContext(actorUserRole, accountEnv)` and `SAFE_BUSINESS_CALL`; wire DTOs in a `dto` subpackage |
| 0.7 | `Mono<LocalDate> resolveReferenceDate(String accountEnv)` in `application.port.out`; `ReferenceDateAdapter` falls back to the Hong Kong date on Redis errors |
| 0.8 | `config.fundprice`, `config.accountbalance` and `config.accountgainloss` hold the application configs; a source port needs no `@Bean` because the adapter is a `@Component` |
| 0.9 | `GetAccountGainLossUseCase` is `Mono<Result> get(Command)`; the service resolves the reference date inside `Mono.defer` and maps every non-domain error to the retrieval exception |
| 0.10 | See section 1.7. No blocker; one pack change (`UrlValidator` is not a bean) |
| 0.11 | `MutationResponse.success(ApiStatus, T, List<ApiMessage>)`; `ApiStatus`, `ApiError` and `MutationResponse` are in `adapter.in.web.response` |
| 0.12 | Gate commands are recorded in section 1.5 |
| extra | The executor returns business-rejected and contract-invalid envelopes untouched and logs them; the no-fund envelope (`err-message` set, no `data`) reaches the adapter; payload crypto accepts it; no route registration is needed; there is no global Jackson inclusion setting; controller tests use `WebTestClient.bindToController` |

---

## 3. Stage 1 — Shared `UrlValidator`

**Commit:** `feat(shared): add UrlValidator for downstream URLs`

### Fail-first tests

`UrlValidatorTest` (same package as the validator), covering every `UrlValidator` scenario in `fund-fact-sheet-test-traceability-design.md`.

### Production

| File | Package |
|---|---|
| `UrlValidator.java` | `application.service.presentation` (resolved in 0.2) |

Behaviour exactly as in `fund-fact-sheet-url-validator-design.md`: `Runnable onRejected`, `validate(String)` returning `Optional<String>`, no trimming, no scheme or host check, no Spring annotations, and not a bean (each consumer's configuration constructs it inline).

### Exit

- Compile and test-compile pass.
- Gate (1.5) passes.
- No other production file changed.

---

## 4. Stage 2 — APIM adapter

**Commit:** `feat(fund-fact-sheet): add TRPGetFundFactSheet APIM adapter`

### Fail-first tests

- `ApimFundFactSheetSourceAdapterTest`: all APIM adapter scenarios in the test design, including `err.tpa.no.fund.fact.sheet` → zero rows, `data: []` → system failure, every structural null → system failure, exact `ApimExchangeRequest`, `ref-date` formatting.
- Properties test: blank path fails startup; delegating constructor.
- DTO contract test: wire names deserialize; unknown properties ignored; `seq` null preserved.
- `ApimRoutingLedgerCharacterizationTest`: add the `TRPGetFundFactSheet` entry, change `.hasSize(19)` to `.hasSize(20)`, and add "TRPGetFundFactSheet" to the `containsExactlyInAnyOrder` list. The test also reads the adapter source and requires the literals `command.actorUserRole()`, `SAFE_BUSINESS_CALL` and `ApimDatabaseScope.ACCOUNT_ENV`, so the adapter's parameter is named `command`.

### Production

| File | Package |
|---|---|
| `TrpGetFundFactSheetApimRequest` | `adapter.out.apim.fundfactsheet.dto` |
| `TrpGetFundFactSheetApimDataItem` (`FundFactSheet` list) | `adapter.out.apim.fundfactsheet.dto` |
| `TrpGetFundFactSheetApimRow` (`seq` as `Integer`) | `adapter.out.apim.fundfactsheet.dto` |
| `FundFactSheetApimProperties` | `adapter.out.apim.fundfactsheet` |
| `FundFactSheetApimValidationConfig` | `adapter.out.apim.fundfactsheet` |
| `ApimFundFactSheetSourceAdapter` | `adapter.out.apim.fundfactsheet` |
| `FundFactSheetSourcePort` | `application.port.out` (resolved) |
| `FetchFundFactSheetCommand`, `RawFundFactSheetData`, `RawFundFactSheetRow` | `application.dto.fundfactsheet` (resolved) |
| `FundFactSheetRetrievalException` | `application.exception` (resolved; the package of `FundPriceRetrievalException`) |
| `application.yml` | add (under the existing top-level `apim:` block of `application.yml` only; not `application.properties`) `apim.fund-fact-sheet.path: ${APIM_FUND_FACT_SHEET_PATH:/ws/NGTPA/v1/TRPGetFundFactSheet}` |

DTO names follow the `TrpGetInvestGlApimDataItem` / `TrpGetFundPriceApimDataItem` pattern, and the wire DTOs live in a `dto` subpackage like every other feature (`accountbalance/dto`, `accountgainloss/dto`, `fundprice/dto`). Properties, validation config and adapter stay in `adapter.out.apim.fundfactsheet`.

### Rules

- `ApimDatabaseScope.ACCOUNT_ENV`, `ApimCredentialResolutionContext(actorUserRole, accountEnv)`, `ApimRetryEligibility.SAFE_BUSINESS_CALL`.
- `ref-date`: feature-local `DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)`.
- Envelope handling exactly as the table in `fund-fact-sheet-apim-contract.md`.
- `err.tpa.no.fund.fact.sheet` compared with an exact, case-sensitive match; the code alone decides (zero rows whether `data` is absent or present).
- The adapter builds its `ApimExchangeRequest` inside `Mono.defer`, so any synchronous failure also maps to `systemFailure()`, and names its parameter `command`.
- Constructors: a public `@Autowired` constructor taking the executor and `FundFactSheetApimProperties`, delegating to a package-private constructor taking the executor and the path string (rejects a blank path). The `dd/MM/uuuu` STRICT formatter is a private static constant of the adapter.
- The adapter logs only a null data item, a null `FundFactSheet` list and a null row (value-free, `event=fund-fact-sheet.<name>`, static SLF4J logger as in `ApimAccountGainLossSourceAdapter`). The executor already logs every other outcome.
- Neither code nor comments in this package may name the types listed in section 1.7.

### Exit

Compile, test-compile and gate pass. Ledger size is 20.

---

## 5. Stage 3 — Use case, normalizer and error mapping

**Commit:** `feat(fund-fact-sheet): add fund fact sheet use case`

### Fail-first tests

- `FundFactSheetNormalizerTest`: all normalizer scenarios.
- `GetFundFactSheetServiceTest`: reference date resolved with `accountEnv`; the fetch command carries `policyNo`, `referenceDate`, `accountEnv` and `actorUserRole`; a `FundFactSheetRetrievalException` passes through unchanged; any other failure (source, reference date, normalizer) maps to `FundFactSheetRetrievalException.systemFailure()`; no-fund result is empty.
- `FundFactSheetPresentationEventLoggerTest`: event names; no values logged.
- Handler test: `FundFactSheetRetrievalException` → 503, `SYSTEM_ERROR`, `err.fund-fact-sheet.retrieval.unavailable`, type `SYSTEM`.
- The handler test also asserts that the error has no `message` field and that no resolver is called.
- Architecture guard: `FundFactSheetPresentationEventPort` methods take no parameters.

### Production

| File | Package |
|---|---|
| `GetFundFactSheetUseCase` | `application.port.in` **(evidenced)** |
| `GetFundFactSheetCommand` (`accountEnv`, `policyNo`, `actorUserRole`; no language), `FundFactSheetResult`, `FundFactSheetRowResult` | `application.dto.fundfactsheet` (resolved) |
| `GetFundFactSheetService`, `FundFactSheetNormalizer` | `application.usecase.fundfactsheet` (resolved) |
| `FundFactSheetPresentationEventPort` (`missingSeq()`, `duplicateSeq()`, `blankEnglishName()`, `malformedFactsheet()`) | `application.port.out` **(evidenced)** |
| `FundFactSheetPresentationEventLogger` | `infrastructure.logging` **(evidenced)** |
| `FundFactSheetApplicationConfig` | `config.fundfactsheet` (resolved; the `UrlValidator` is constructed inline inside the normalizer `@Bean` method) |
| `ErrorCodes.FUND_FACT_SHEET_RETRIEVAL_UNAVAILABLE = "err.fund-fact-sheet.retrieval.unavailable"` | `shared.error` **(evidenced)** |
| `ApiExceptionHandler` (`adapter.in.web.advice`) | add one `@ExceptionHandler` returning `ApiError.system(ErrorCodes.FUND_FACT_SHEET_RETRIEVAL_UNAVAILABLE, null, "SERVER")`; it never calls the message resolver |

### Rules

- Application classes are framework-free; Spring wiring only in `FundFactSheetApplicationConfig`.
- `UrlValidator` is constructed with `events::malformedFactsheet` and applied to both links.
- Duplicate `seq`: keep first occurrence in TAS order.
- `GetFundFactSheetService` builds the fetch command and calls `ReferenceDatePort`, the source port and the normalizer inside one `Mono.defer` chain; `onErrorMap` turns every failure that is not a `FundFactSheetRetrievalException` into `systemFailure()`, as `GetAccountGainLossService` does.
- `FundFactSheetPresentationEventPort` is a plain four-method interface (not `@FunctionalInterface`); its logger writes `event=fund-fact-sheet.<name>` lines with no values, and its test uses the logback `ListAppender` technique of `ApiExceptionHandlerLoggingTest`.

### Exit

Compile, test-compile and gate pass.

---

## 6. Stage 4 — Controller and web mapping

**Commit:** `feat(fund-fact-sheet): expose GET /api/v1/fund-fact-sheet`

### Fail-first tests

- `FundFactSheetWebMapperTest`: language selection, fallbacks, sort, omitted link, null `fundNameZh`.
- `FundFactSheetControllerTest`: route, language resolution, context delegation, envelope shape, empty rows, 503 path. Use `WebTestClient.bindToController(controller)` with the real `ApiExceptionHandler` as controller advice for the 503 path; the repository has no `@WebFluxTest` usage, and missing-`X-Request-Id` rejection is covered by the shared filter's own tests.
- Serialization test: `factsheetUrl` omitted when null; `fundNameZh` null serialized as `null`.

### Production

| File | Package |
|---|---|
| `FundFactSheetController` | `adapter.in.web.fundfactsheet` (pattern: `AccountGainLossController`; the controller stays flat in the feature package, as for Gain/Loss, Account Summary and Contribution) |
| `FundFactSheetResultResponse`, `FundFactSheetResponse`, `FundFactSheetRowResponse` | `adapter.in.web.fundfactsheet.response` (resolved: Fund Price uses `.response`) |
| `FundFactSheetWebMapper` | `adapter.in.web.fundfactsheet.mapper` (resolved: Fund Price uses `.mapper`) |

### Rules

- `RequestLanguageResolver.resolve(headers, acceptLanguage, "en")`, then `CurrentPortalAccessContextResolver.current()`. Read the headers context with `Mono.deferContextual` and `RequestHeaderContextKeys.CONTEXT_KEY` as `AccountGainLossController` does; the third argument of `resolve` is ignored (a missing header gives `en`), and `zh` and `zh-HK` normalize to `zh_HK`.
- Sort: `fundName` lower-cased with `Locale.ROOT` (null as `""`), then `seq`. No `Collator`.
- `@JsonInclude(NON_NULL)` on the `factsheetUrl` record component only. Do not copy the class-level `NON_NULL` of `FundPriceRowResponse`: it would drop `fundNameZh: null`. There is no global Jackson inclusion setting (`JacksonConfig` is a plain `ObjectMapper`), so a null `fundNameZh` serializes as `null`.

### Exit

Compile, test-compile and gate pass. Manual curl smoke against a running instance returns 200 for a valid account.

---

## 7. Stage 5 — Stabilization

**Commit:** `test(fund-fact-sheet): stabilize and record coverage evidence`

1. `mvn spotless:apply`, then compile and test-compile.
2. Full test suite and startup smoke (manual).
3. JaCoCo per-class coverage against the tiers in the test design; one-line justification for every uncovered adapter, use-case or controller branch. The JaCoCo configuration already excludes `*Config` and `*Properties`, so the properties, validation config and application config are not in the report.
4. `git diff --name-only origin/dev...HEAD` contains no Account Balance or Account Summary source or test file.
5. Feature log statements contain no values (audit).
6. Update `handover.md` with stage commits, evidence locations and coverage results.

### Exit

All of the above pass; branch ready for MR.

---

## 8. Stage summary

| Stage | Script range starts | Commit |
|---|---|---|
| 0 | 008-016 | none |
| 0 (correction) | 017 | `docs(fund-fact-sheet): correct design pack after Stage 0 investigation` |
| 1 | next free | `feat(shared): add UrlValidator for downstream URLs` |
| 2 | next free | `feat(fund-fact-sheet): add TRPGetFundFactSheet APIM adapter` |
| 3 | next free | `feat(fund-fact-sheet): add fund fact sheet use case` |
| 4 | next free | `feat(fund-fact-sheet): expose GET /api/v1/fund-fact-sheet` |
| 5 | next free | `test(fund-fact-sheet): stabilize and record coverage evidence` |

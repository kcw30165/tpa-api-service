# Fund Fact Sheet Implementation Plan

**Branch:** `feature/impl-fund-fact-sheet`
**Design baseline:** the commit that adds `docs/design/11-fund-fact-sheet/` (record its hash in Stage 1 evidence)
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

Packages marked **(confirm)** below are inferred from precedent and must be confirmed by the Stage 0 investigation before use. Packages marked **(evidenced)** were seen in investigation evidence at `c12d2cc4`.

### 1.3 Script and evidence conventions

- Scripts live in `/mnt/d/Projects/ORSO/llm-scripts/api-service/` and are named `fund-fact-sheet-<NNN>-<purpose>.sh`.
- Script numbers increase on every new script, including fixes and reruns, and are never reused. Scripts 001-007 are used; implementation starts at **008**.
- Every script creates `target/evidence/<script-name>-<timestamp>/` with sequentially numbered files: pre-state, each stage, audits, `git diff --check`, final status, diff stat, name-status, exit codes and preserved failure logs.
- Every script packages its evidence as `target/evidence/<script-name>.zip.txt` and copies it to `/mnt/d/Projects/ORSO/llm-scripts/api-service/`.
- Run command format: `run_notify "Script <filename>" bash /mnt/d/Projects/ORSO/llm-scripts/api-service/<filename>`.
- Use `python`, not `python3`.
- Commit-message checks use Bash substring matching on captured subjects, not `grep`.

### 1.4 Validation inside scripts

- Toolchain: Maven at `/home/nd2887/tools/apache-maven`, JDK at `/home/nd2887/tools/jdk-21.0.12.1+1`.
- Scripts run only `mvn -DskipTests compile` and `mvn -DskipTests test-compile`, immediately after the file changes.
- Scripts do not run `mvn test`. Targeted tests and the full suite are run manually.
- Maven output is not copied into evidence; record the command, exit code, a short summary and failure diagnostics only.
- `mvn spotless:apply` is deferred to Stage 5. Every earlier script writes an evidence file stating formatting was intentionally deferred.
- On compile failure, stop and repair all compile errors in the next script.

### 1.5 Stage gates

Before moving from one stage to the next:

1. The full Maven test suite passes (run manually).
2. A Spring application startup smoke test passes with no bean or configuration errors.
3. The stage commit exists on the branch.

### 1.6 Escalation

- Three consecutive failed scripts: stop and explain failures, likely root cause, repository state, risks and recovery options before generating another script.
- Ten scripts without reaching a commit: stop and escalate the same way.

---

## 2. Stage 0 — Pre-implementation investigation (read-only)

**Script:** `fund-fact-sheet-008-implementation-preflight.sh` (no commit)

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

**Exit:** every **(confirm)** package below is resolved in the evidence. If any differs, update this plan before Stage 1.

---

## 3. Stage 1 — Shared `UrlValidator`

**Commit:** `feat(shared): add UrlValidator for downstream URLs`

### Fail-first tests

`UrlValidatorTest` (same package as the validator), covering every `UrlValidator` scenario in `fund-fact-sheet-test-traceability-design.md`.

### Production

| File | Package |
|---|---|
| `UrlValidator.java` | shared framework-free application package **(confirm in 0.2)** |

Behaviour exactly as in `fund-fact-sheet-url-validator-design.md`: `Runnable onRejected`, `validate(String)` returning `Optional<String>`, no trimming, no scheme or host check, no Spring annotations.

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
- `ApimRoutingLedgerCharacterizationTest`: add the `TRPGetFundFactSheet` entry and change the expected size from 19 to 20.

### Production

| File | Package |
|---|---|
| `TrpGetFundFactSheetApimRequest` | `adapter.out.apim.fundfactsheet` |
| `TrpGetFundFactSheetApimDataItem` (`FundFactSheet` list) | `adapter.out.apim.fundfactsheet` |
| `TrpGetFundFactSheetApimRow` (`seq` as `Integer`) | `adapter.out.apim.fundfactsheet` |
| `FundFactSheetApimProperties` | `adapter.out.apim.fundfactsheet` |
| `FundFactSheetApimValidationConfig` | `adapter.out.apim.fundfactsheet` |
| `ApimFundFactSheetSourceAdapter` | `adapter.out.apim.fundfactsheet` |
| `FundFactSheetSourcePort` | `application.port.out` **(evidenced package)** |
| `FetchFundFactSheetCommand`, `RawFundFactSheetData`, `RawFundFactSheetRow` | `application.dto.fundfactsheet` **(confirm; `application.dto.accountgainloss` evidenced)** |
| `FundFactSheetRetrievalException` | same package as `FundPriceRetrievalException` **(confirm in 0.3)** |
| `application.yml` | add `apim.fund-fact-sheet.path: ${APIM_FUND_FACT_SHEET_PATH:/ws/NGTPA/v1/TRPGetFundFactSheet}` |

DTO names above follow the `TrpGetInvestGlApimDataItem` / `TrpGetFundPriceApimDataItem` pattern; adjust only if Stage 0 shows a different convention.

### Rules

- `ApimDatabaseScope.ACCOUNT_ENV`, `ApimCredentialResolutionContext(actorUserRole, accountEnv)`, `ApimRetryEligibility.SAFE_BUSINESS_CALL`.
- `ref-date`: feature-local `DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)`.
- Envelope handling exactly as the table in `fund-fact-sheet-apim-contract.md`.
- `err.tpa.no.fund.fact.sheet` compared with an exact, case-sensitive match.

### Exit

Compile, test-compile and gate pass. Ledger size is 20.

---

## 5. Stage 3 — Use case, normalizer and error mapping

**Commit:** `feat(fund-fact-sheet): add fund fact sheet use case`

### Fail-first tests

- `FundFactSheetNormalizerTest`: all normalizer scenarios.
- `GetFundFactSheetServiceTest`: reference date resolved with `accountEnv`; command fields passed through; source failure propagates; no-fund result is empty.
- `FundFactSheetPresentationEventLoggerTest`: event names; no values logged.
- Handler test: `FundFactSheetRetrievalException` → 503, `SYSTEM_ERROR`, `err.fund-fact-sheet.retrieval.unavailable`, type `SYSTEM`.
- Architecture guard: `FundFactSheetPresentationEventPort` methods take no parameters.

### Production

| File | Package |
|---|---|
| `GetFundFactSheetUseCase` | `application.port.in` **(evidenced)** |
| `GetFundFactSheetCommand`, `FundFactSheetResult`, `FundFactSheetRowResult` | `application.dto.fundfactsheet` **(confirm)** |
| `GetFundFactSheetService`, `FundFactSheetNormalizer` | `application.usecase.fundfactsheet` **(confirm; `application.usecase.fundprice` evidenced)** |
| `FundFactSheetPresentationEventPort` (`missingSeq()`, `duplicateSeq()`, `blankEnglishName()`, `malformedFactsheet()`) | `application.port.out` **(evidenced)** |
| `FundFactSheetPresentationEventLogger` | `infrastructure.logging` **(evidenced)** |
| `FundFactSheetApplicationConfig` | `config.fundfactsheet` **(confirm; `config.accountbalance` evidenced)** |
| `ErrorCodes.FUND_FACT_SHEET_RETRIEVAL_UNAVAILABLE = "err.fund-fact-sheet.retrieval.unavailable"` | `shared.error` **(evidenced)** |
| `ApiExceptionHandler` | add one `@ExceptionHandler` using `ApiError.system(...)` **(confirm signature in 0.4)** |

### Rules

- Application classes are framework-free; Spring wiring only in `FundFactSheetApplicationConfig`.
- `UrlValidator` is constructed with `events::malformedFactsheet` and applied to both links.
- Duplicate `seq`: keep first occurrence in TAS order.

### Exit

Compile, test-compile and gate pass.

---

## 6. Stage 4 — Controller and web mapping

**Commit:** `feat(fund-fact-sheet): expose GET /api/v1/fund-fact-sheet`

### Fail-first tests

- `FundFactSheetWebMapperTest`: language selection, fallbacks, sort, omitted link, null `fundNameZh`.
- `FundFactSheetControllerTest`: route, language resolution, context delegation, envelope shape, empty rows, 503 path.
- Serialization test: `factsheetUrl` omitted when null; `fundNameZh` null serialized as `null`.

### Production

| File | Package |
|---|---|
| `FundFactSheetController` | `adapter.in.web.fundfactsheet` (pattern: `AccountGainLossController`, **evidenced**) |
| `FundFactSheetResultResponse`, `FundFactSheetResponse`, `FundFactSheetRowResponse` | `adapter.in.web.fundfactsheet.response` **(confirm against Fund Price response packages)** |
| `FundFactSheetWebMapper` | `adapter.in.web.fundfactsheet.mapper` **(confirm)** |

### Rules

- `RequestLanguageResolver.resolve(headers, acceptLanguage, "en")`, then `CurrentPortalAccessContextResolver.current()`.
- Sort: `fundName` lower-cased with `Locale.ROOT` (null as `""`), then `seq`. No `Collator`.
- `@JsonInclude(NON_NULL)` on `factsheetUrl` only.

### Exit

Compile, test-compile and gate pass. Manual curl smoke against a running instance returns 200 for a valid account.

---

## 7. Stage 5 — Stabilization

**Commit:** `test(fund-fact-sheet): stabilize and record coverage evidence`

1. `mvn spotless:apply`, then compile and test-compile.
2. Full test suite and startup smoke (manual).
3. JaCoCo per-class coverage against the tiers in the test design; one-line justification for every uncovered adapter, use-case or controller branch.
4. `git diff --name-only origin/dev...HEAD` contains no Account Balance or Account Summary source or test file.
5. Feature log statements contain no values (audit).
6. Update `handover.md` with stage commits, evidence locations and coverage results.

### Exit

All of the above pass; branch ready for MR.

---

## 8. Stage summary

| Stage | Script range starts | Commit |
|---|---|---|
| 0 | 008 | none |
| 1 | next free | `feat(shared): add UrlValidator for downstream URLs` |
| 2 | next free | `feat(fund-fact-sheet): add TRPGetFundFactSheet APIM adapter` |
| 3 | next free | `feat(fund-fact-sheet): add fund fact sheet use case` |
| 4 | next free | `feat(fund-fact-sheet): expose GET /api/v1/fund-fact-sheet` |
| 5 | next free | `test(fund-fact-sheet): stabilize and record coverage evidence` |

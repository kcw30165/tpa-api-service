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
| MODIFIED | `scripts/e2e/lib/authenticated-login-journey.sh` | Strictly reject both selector variables by presence before login; no Java counterpart |
| NEW (proposed) | `scripts/e2e/tests/download-corner-scenarios-test.sh` | Shell-level contract/regression test for the new pack and selector rejection; exact test harness/path is UNVERIFIED |

The response DTO plan includes only the wrapper/data/category/item structures required by Download Corner. Vesting-specific overview, plan, and presence-section response types have no Download Corner equivalent.

## 2. M0 — Design pack

Create this directory and its nine design documents only. No TDD evidence is required for this docs-only milestone. Commit only `docs/design/25-download-corner/`; keep the evidence report under ignored `target/evidence/`.

## 3. M1 — Outbound (same MR as M2)

Implement typed APIM request/response mapping, path properties/YAML, executor read exchange, strict response checks, failure events, and the ownership-guard update. Add and run failing adapter/DTO/mapper/config/guard tests first, then implement and rerun them.

Done when the APIM boundary, raw non-empty error-code event field, malformed-field event dimensions, failure mapping, and targeted tests are green.

## 4. M2 — Inbound/use case (same MR as M1)

Implement application input/output ports and commands, use case, application wiring, language-aware web mapping, feature controller and response DTOs, exception mapping and explicit registry entry. Add the registration test and other inbound/contract tests fail-first.

Done when mapper, service, controller, response serialization, and mapping-registration tests are green and the full Java milestone gates pass.

## 5. M3 — Read-only E2E pack (separate MR)

Add the Download Corner scenario script, register it, invoke it only through `bash scripts/e2e/run-post-login.sh`, and use the shared `E2E_ACCOUNT_INDEX` (default `0`). Add a pre-login rejection test for simultaneous `E2E_ACCOUNT_REF` and `E2E_ACCOUNT_INDEX`; update shared selector validation only as needed to enforce rejection by variable presence. Do not introduce any live mutation or file fetch.

Done when EN/ZH response checks, ordering/shape checks, selector rejection before login, and shell-level fail-first evidence pass. M3 has no Java gates.

## 6. MR and validation boundaries

- **MR 1:** M1 + M2 together.
- **MR 2:** M3 separately.
- M1/M2 gates: full `mvn test`, JaCoCo, startup smoke, Sonar. Exact JaCoCo invocation and Sonar task invocation are **UNVERIFIED** in this design pack; use the repository/CI-approved commands and record them.
- M3 is shell-only; use the relevant shell/scenario checks and report no Java gates.
- Each milestone records test-first failure and passing evidence. No Vesting Scale code or behavior is changed.

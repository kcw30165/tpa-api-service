# 06 — Test Strategy (fail-first)

All behavior tests for M1, M2, and M3 are written and run before their corresponding implementation. Record the targeted command, expected red result, and green result for each milestone. M0 changes documentation only; TDD does not apply and no production behavior changes.

## M1 — Outbound APIM adapter

Proposed new tests:

| Test | Required cases |
|---|---|
| `NEW (proposed) TrpGetDownloadCornerApimDtoContractTest` | Exact request wire keys and values; response wire field names; nested categories/items |
| `NEW (proposed) TrpGetDownloadCornerApimMapperTest` | Map one accepted response data item; ignore `env`; retain both language fields and seq values for the web mapper |
| `NEW (proposed) ApimDownloadCornerAdapterTest` | Correct path; `ACCOUNT_ENV`; `SAFE_BUSINESS_CALL`; same read recovery/retry and terminal-caller ownership; exactly one data item; known and unknown errors; malformed response and field diagnostics; `FailureEventPort` event values; raw `internalCode` on every non-empty `err-message`; null internal code for malformed cases |
| `NEW (proposed) DownloadCornerApimConfigurationTest` | Proposed YAML path binding and blank-path failure |
| `NEW (proposed) DownloadCornerApimFieldDeserializerTest` | Presence/shape behavior if the Vesting `ApimField` utility is mirrored |

Also update `M3R1aOwnershipActivationGuardTest` to include the Download Corner adapter as a terminal caller, with `FailureEventPort` and `publishFailure` assertions. The guard currently enumerates exact terminal callers and checks Vesting's event ownership (`M3R1aOwnershipActivationGuardTest.java:9-61`).

**Fail-first evidence:** tests for adapter behavior, wire names, config, failure event values, and ownership are committed/run first and shown failing for the missing Download Corner implementation. Then implement the smallest outbound slice and rerun. Record the exact test selector and failure/pass results in the M1 MR.

## M2 — Inbound/use-case and public contract

Proposed new tests:

| Test | Required cases |
|---|---|
| `NEW (proposed) GetDownloadCornerServiceTest` | One use-case invocation maps selected account and actor data to the port command; no cache or Redis dependency |
| `NEW (proposed) DownloadCornerWebMapperTest` | English; Chinese; independent blank-Chinese fallback for category/item/link/filename; blank resolved link drops only that item; stable category and item ordering; empty category retained; seq/env absent from output |
| `NEW (proposed) DownloadCornerControllerTest` | `GET /api/v1/download-corner`; session and selected Account-Ref path; language resolution; base response wrapper; no body/query contract; retrieval failure mapped to expected 503 |
| `NEW (proposed) DownloadCornerResponseSerializationContractTest` | Exact camelCase JSON fields and wrapper; no seq/env; empty category serializes as `items: []` |
| `NEW (proposed) DownloadCornerExceptionMappingRegistrationTest` | Explicitly assert the proposed retrieval exception resolves through `ExceptionMappingRegistryConfig` to the Download Corner mapping, 503, and public code |

The serialization test mirrors the explicit Vesting response contract test (`VestingScaleResponseSerializationContractTest.java:49-69`). The registry test is intentionally feature-specific; merely constructing the global registry is not a direct assertion that this feature is registered.

**Fail-first evidence:** add/run each affected web, mapper, service, serialization, and registration test before its production counterpart. Record targeted red and green runs in the M2 MR.

## M3 — Read-only post-login E2E

**`NEW (proposed) scripts/e2e/download-corner-scenarios.sh`** must run only authenticated GETs and use the shared runner's session, CSRF token, Account-Ref, and zero-based `E2E_ACCOUNT_INDEX` (default `0`). It performs no login/logout itself, mutation, direct APIM call, file fetch, or remote file-existence check. Register the pack in `scripts/e2e/lib/feature-pack-registry.sh` and execute it through `bash scripts/e2e/run-post-login.sh`.

Scenarios/assertions:

1. English GET succeeds and validates the wrapper plus `result.data.categories[].categoryName` and `items[].{itemName,itemLink,fileName}`.
2. Chinese GET succeeds; validate the Chinese response and expected field-level English fallback where fixture/DS data provides a blank Chinese value.
3. Assert category/item ascending order, stable ties where the selected account has tied sequences, empty-category preservation, and omission of seq/env.
4. Assert the pack makes read-only GET requests; it does not probe returned links.
5. Before login, supplying both `E2E_ACCOUNT_REF` and `E2E_ACCOUNT_INDEX` is rejected. Include a set-but-empty index case: the current shared selector checks the conflict only when the index value is non-empty (`authenticated-login-journey.sh:78-97`), so M3 must first add a failing regression and then close this presence-semantics gap without changing account authorization. Verify the rejection occurs before any login HTTP request.

**Fail-first evidence:** add/run a shell-level selector regression and scenario-pack contract test with stubbed HTTP/login so the new pack and strict conflict case fail before implementation. Record red/green results and confirm no live account mutation. M3 is shell-only and has no Java gates.

## Milestone gates

For the combined M1+M2 MR: full `mvn test`, JaCoCo, startup smoke, and Sonar. Record only commands actually run and inspect Surefire/Failsafe reports after Maven runs. M3 is a separate shell-only MR, with shell/scenario tests and no Java gates. Do not run Maven for M0.

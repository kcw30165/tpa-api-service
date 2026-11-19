# 06 — Test Strategy (fail-first)

All behavior tests for M1, M2, and M3 are written and run before their corresponding implementation. Record the targeted command, expected red result, and green result for each milestone. M0 changes documentation only; TDD does not apply and no production behavior changes.

## M1 — Outbound APIM adapter

Proposed new tests:

| Test | Required cases |
|---|---|
| `NEW (proposed) TrpGetDownloadCornerApimDtoContractTest` | Exact request wire keys and values; response wire field names; nested categories/items |
| `NEW (proposed) TrpGetDownloadCornerApimMapperTest` | Map one accepted response data item; ignore `env`; retain both language fields and seq values for the web mapper |
| `NEW (proposed) ApimDownloadCornerAdapterTest` | Correct path; `ACCOUNT_ENV`; `SAFE_BUSINESS_CALL`; same read recovery/retry and terminal-caller ownership; exactly one data item; known and unknown errors; malformed response and field diagnostics; `FailureEventPort` event values; raw `internalCode` on every non-empty `err-message`; null internal code for malformed cases |
| `NEW (proposed) ApimDownloadCornerAdapterTest` — E2 field validation | Fail first for missing/null `categories`; missing/null nested `items`; missing/null/non-numeric `category-seq`; missing/null/non-numeric `item-seq`; and missing/null/blank English category name. Assert 503 mapping, `MALFORMED_RESPONSE`, null `internalCode`, and the exact `malformedField` path for each case. |
| `NEW (proposed) ApimDownloadCornerAdapterTest` — valid/blank cases | Fail first for valid empty `categories` and `items` arrays, preserving an empty category; missing/null English item name/link/file name treated as blank; and missing/null Chinese category/item/link/file-name fields treated as blank for per-field English fallback. Verify English blank item links still use L6 filtering. |
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

`scripts/e2e/download-corner-scenarios.sh` is implemented as a read-only post-login pack. It makes exactly two authenticated GETs, first `Accept-Language: en` and then `Accept-Language: zh-HK`, using the shared runner's session, CSRF token, and selected account unchanged. It performs no login/logout itself, mutation, direct APIM call, file fetch, or remote file-existence check. The `download-corner` name is registered in `scripts/e2e/lib/feature-pack-registry.sh` and is invoked through `bash scripts/e2e/run-post-login.sh`.

Live checks:

1. For each language, `http-status` requires 200; `envelope` requires `result.data.categories` to be an array; `category-shape` requires each category to have a non-blank `categoryName` and an `items` array; `item-shape` requires non-blank `itemName`, `itemLink`, and `fileName` on every item; `no-seq-env` rejects keys `seq`, `categorySeq`, `itemSeq`, and `env` anywhere under `result.data`.
2. Cross-language checks compare category counts and item counts at each category position.
3. If a language request is not HTTP 200, that language's later checks are recorded as `SKIP`; dependent cross-language checks are also skipped.
4. Live checks deliberately do not assert ordering, tie order, fallback behavior, or empty-category preservation; Java unit tests cover those rules.

The per-language result IDs are `<language>.{http-status,envelope,category-shape,item-shape,no-seq-env}` for `en` and `zh-HK`. Cross-language IDs are `language-category-count` and `language-item-count`. Journal hooks use the shared result library without changing terminal output, request order, or exit status; only per-language records carry valid actual HTTP status, and only `<language>.http-status` carries expected status 200.

The shared selector's existing conflict rejection applies when `E2E_ACCOUNT_REF` and a non-empty `E2E_ACCOUNT_INDEX` are both supplied (`scripts/e2e/lib/authenticated-login-journey.sh:78-97`). M3 does not change the selector, `run-post-login.sh`, or `result-records.sh`; the pack consumes the shared runner's selection unchanged.

**Fail-first evidence:** `scripts/e2e/tests/download-corner-scenario-test.sh` and `scripts/e2e/tests/download-corner-result-hooks-test.sh` use a fake `curl`; both were run before the pack existed and failed on the missing pack, then passed after implementation. M3 is shell-only and has no Java gates. Local live validation remains pending; Nick runs it.

## Milestone gates

For the combined M1+M2 Java gates G1–G4, use the evidence-backed commands and pass criteria in [07 — Implementation Plan, §7 Milestone gate commands](07-implementation-plan.md#7-milestone-gate-commands). Record the command, exit/result, report paths, and gate status in that milestone's evidence report. M3 is shell-only and has no Java gates; local live validation is pending Nick. Do not run Maven for M0 or M3.

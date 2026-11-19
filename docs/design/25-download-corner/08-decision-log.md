# 08 — Decision Log (locked for Download Corner V0.3)

## Approval record

- **M0: APPROVED by Nick on 2026-11-18** at commit `202fe9bb08ca6a21bf2558aedf8eab8e25945c92` (design pack `fd04d50f7697dfd41e7b9f5c0473e81672e4be92` plus fix-up `202fe9bb08ca6a21bf2558aedf8eab8e25945c92`).
- This approval clears the design gate for **M1 only**. **M2 and M3 each still require an explicit go-ahead at their own milestone gate.**

| # | Decision | Rationale |
|---|---|---|
| L1 | `GET /api/v1/download-corner`; session plus `Account-Ref`; no query or body. | Matches the existing feature GET shape `/api/v1/vesting-scale`; controller convention is class `/api/v1` plus feature mapping (`VestingScaleController.java:33-35,54`). The proposed route has no naming discrepancy. |
| L2 | POST `/ws/NGTPA/v1/TRPGetDownloadCorner` with `policy-no`, `cert-no`, `login-user-id`, `login-user-role`. Account fields come from the selected account; login fields from actor context. Use the existing `ApimExchangeExecutor` read path, `ACCOUNT_ENV`, same retry/recovery policy, and a YAML path. | Reuses the established typed APIM boundary and avoids feature-owned transport/authentication behavior (`ApimVestingScaleAdapter.java:77-103`). |
| L3 | Response is `response.{err-message,data:[{env,categories:[{category-seq,category-name-eng,category-name-chi,items:[{item-seq,item-name-eng,item-name-chi,item-link-eng,item-link-chi,file-name-eng,file-name-chi}]}]}]}`. Require exactly one `data` item. Ignore `env`. Apply the E2 field presence/type rules in this decision log and [03 — APIM Contract](03-apim-contract.md). | The selected account's `accountEnv` is already available in session context; response does not override it. One response item gives the feature one unambiguous account result. |
| L4 | Resolve one language from `Accept-Language` with the existing resolver, exactly as other endpoints do. | Keeps language normalization and defaults consistent (`RequestLanguageResolver.java:12-42`). Java forwards DS/FE labels and adds none. |
| L5 | For Chinese, independently fall back from blank Chinese category name, item name, item link, and file name to that field's English value. | A missing translation for one field must not blank an otherwise usable category or item. |
| L6 | After fallback, drop an item only if its resolved link is blank. Never probe remote file existence and never validate/rewrite URLs. | Keeps retrieval limited to listing metadata and forwards DS-owned links unchanged. |
| L7 | Sort categories by category sequence ascending, and items within each category by item sequence ascending. Preserve DS order on ties. | Gives deterministic presentation while avoiding arbitrary reordering of tied records. |
| L8 | Preserve categories with no retained items; return an empty `items` list. | FE owns empty-state wording and may still need the category shell. |
| L9 | Return `result.data.categories[]` with `categoryName` and `items[].itemName`, `itemLink`, `fileName`, using the existing camelCase JSON style and base `MutationResponse`. Expose neither sequence values nor `env`. | Matches existing response record names and wrapper (`VestingScaleResultResponse.java:1-3`; `MutationResponse.java:5-20`) while keeping DS ordering metadata private. |
| L10 | All feature retrieval errors return HTTP 503 and `NEW (proposed) err.download-corner.retrieval.unavailable`. The three named context codes classify as Vesting account-context mismatch; any other non-empty code is unmapped. Missing response, empty error with missing/empty data, non-unit cardinality, missing/null `categories` or nested `items`, missing/null/fractional/out-of-`int`-range `category-seq` or `item-seq`, missing/null/blank English category name, or another DTO/shape parse failure is malformed. Exact-integer decimals are accepted. Malformed events identify the field using Vesting’s dimensions; sequence failures use reason `MALFORMED_RESPONSE` and null `internalCode`. | Maintains feature-owned public errors and existing failure taxonomy (`ApimVestingScaleAdapter.java:113-138,177-203,221-229`; `VestingScaleExceptionMappings.java:15-34`). |
| L11 | Emit through `FailureEventPort` from the APIM adapter. Event `NEW (proposed) download-corner.retrieval.downstream_failure`, capability `NEW (proposed) DOWNLOAD_CORNER`, operation `NEW (proposed) TRPGetDownloadCorner`. For every non-empty `err-message`, `internalCode` is the exact raw value; malformed cases follow Vesting and use null. | Preserves structured failure ownership. Raw-code behavior follows CIM (`ApimInvestmentChoiceCancelAdapter.java:130-167`) and deliberately differs from Vesting (`ApimVestingScaleAdapter.java:205-218`). |
| L12 | Add feature-scoped `NEW (proposed) DownloadCornerExceptionMappings`, explicitly register it in `ExceptionMappingRegistryConfig`, add a test asserting registry resolution, and add the adapter to `M3R1aOwnershipActivationGuardTest`. | Prevents a feature mapping from existing without wiring and retains the terminal incident owner invariant (`ExceptionMappingRegistryConfig.java:25-63`; `M3R1aOwnershipActivationGuardTest.java:9-61`). |
| L13 | Keep existing post-login authorization unchanged, including the shared APIM credential-profile allow-list. | Feature work must not broaden platform role access (`DefaultApimCredentialProfileResolver.java:27-56`). |
| L14 | No caching and no Redis writes. | This endpoint reads current document metadata; no cache lifecycle or invalidation contract is approved. |
| L15 | Mirror the Vesting Scale feature classes one-to-one across the listed architecture layers in `07-implementation-plan.md`, adapting only structures required by this contract. | Reuses verified repository layering and test patterns without importing Vesting behavior or response fields. |
| L16 | M0 is this pack. M1 outbound; M2 inbound; one MR for M1+M2. M3 is a separate read-only post-login E2E pack at the proposed scenario path, registered at `scripts/e2e/lib/feature-pack-registry.sh` and run via the post-login runner using the shared selector unchanged from `origin/dev`. The existing selector rejects `E2E_ACCOUNT_REF` plus a non-empty `E2E_ACCOUNT_INDEX` (`scripts/e2e/lib/authenticated-login-journey.sh:78-97`). Java gates are full Maven test, JaCoCo, startup smoke, Sonar for M1/M2 only; M3 has no Java gates. | Keeps the Java feature and shell-only E2E work independently reviewable and preserves shared selector ownership. |
| L17 | Write tests fail-first and record failing-then-passing evidence for each milestone. | Establishes evidence for each new behavior and contract. M0 is documentation-only, so TDD does not apply. |
| L18 | Exclude frontend search, BRD security settings, file-existence checks, and all Vesting Scale changes. | Preserves the bounded feature scope and avoids coupling independent behavior changes. |
| E1 | The shared E2E account selector is out of scope and must not be changed on this branch. M3 consumes its `origin/dev` behavior unchanged: the mutual-exclusion check rejects `E2E_ACCOUNT_REF` together with a non-empty `E2E_ACCOUNT_INDEX` (`scripts/e2e/lib/authenticated-login-journey.sh:78-97`). Use the live registry path `scripts/e2e/lib/feature-pack-registry.sh`. | Keeps shared test infrastructure changes separate from the Download Corner E2E pack. |
| E2 | For `categories` and nested `items`, missing or null is malformed; empty arrays are valid. Sequence values are signed 32-bit `int`; exact-integer decimal representations are accepted, while missing/null, fractional, and out-of-range values are malformed. Missing, null, or blank English category name is malformed. Missing/null English item name, link, or file name is blank, not malformed; apply per-field English fallback (L5) and resolved-link filtering (L6). Missing/null Chinese category name, item name, link, or file name is blank and falls back field-by-field to English (L5). Every malformed case returns 503 `err.download-corner.retrieval.unavailable` and logs the malformed field path using Vesting's mechanism. Sequence failures use `MALFORMED_RESPONSE` and null `internalCode`. | Defines strict structure needed for reliable ordering and category mapping while allowing absent translations and optional item display fields to follow the locked fallback/filter rules. |

## M1/M2 follow-up decisions

| # | Decision | Rationale |
|---|---|---|
| F1 | Add an APIM routing-ledger entry for `TRPGetDownloadCorner`, modeled on `TRPGetVestingScale`. | Records the new account-environment safe read without changing Vesting Scale. |
| F2 | `category-seq` and `item-seq` are signed 32-bit `int` values. An exact-integer decimal such as `10010.0` is accepted. A fractional or out-of-`int`-range value is malformed: return 503 `err.download-corner.retrieval.unavailable`, log the malformed field path, use reason `MALFORMED_RESPONSE`, and set `internalCode` to null. | Allows numeric JSON encoding with a decimal scale when its mathematical value is still an exact `int`, while rejecting lossy or overflowing values. |
| F3 | `ApimField` and `ApimFieldDeserializer` were copied into the Download Corner DTO package for M1. | Keeps this feature's M1 implementation bounded; shared extraction is not part of M1/M2. |

## Shared-test and M1/M2 implementation decisions

| # | Decision | Rationale |
|---|---|---|
| H1 | The shared inbound route architecture test increased its expected route count from 36 to 37 and includes `GET /api/v1/download-corner`. | Records the new endpoint without removing existing route coverage. |
| H2 | Add the Download Corner retrieval error constant to `ErrorCodes`, mirroring the existing Vesting Scale constant pattern. | Keeps the public error code centralized and consistent with the neighboring feature. |
| H3 | Extend `ExceptionMappingsTest` from 33 to 34 feature mappings for Download Corner. | Retains the shared exception-mapping regression checks while adding the feature mapping. |
| H4 | Shared tests are additive-only: retain existing assertions and add Download Corner coverage without replacing or weakening prior coverage. | Prevents a feature addition from silently reducing repository-wide contract coverage. |
| I1 | Chain the route-count assertion and expected-route assertion into one AssertJ chain. | Resolves java:S5853 without changing the route count or expected route list. |
| I2 | Verify the Download Corner routing-ledger entry against production adapter source literals, using the existing Vesting-style source assertion; mutate retry eligibility temporarily to prove the test fails. | Resolves java:S3415 and demonstrates the assertion detects a production-routing regression. |

## Open items and follow-ups

1. Follow up separately on preserving the raw non-empty DS `err-message` as Vesting Scale's `internalCode`.
2. Extract `ApimField` and `ApimFieldDeserializer` into a shared implementation if Sonar flags duplication.
3. Consolidate `DOWNLOAD_CORNER_LEDGER` into the main routing ledger; its current total is 26.
4. Fix the shared E2E selector's empty-index handling in separate shared-infrastructure work; do not change it in M3.
5. Correct `.github/instructions` references to the nonexistent `docs/design/15-exception-handling/`; the exception-handling documents are under `docs/design/18-exception-handling/`.
6. Investigate and address the unmasked cause message in `StructuredFailureEventLogger`.
7. Reconcile the 10-test difference between Maven and Surefire report totals.
8. Confirm the live DS schema before release.
9. Account for the worktree tool configuring an `origin/dev` upstream on newly created branches; verify the branch configuration when creating the M3 worktree.

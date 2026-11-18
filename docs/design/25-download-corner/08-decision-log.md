# 08 — Decision Log (locked for Download Corner V0.3)

| # | Decision | Rationale |
|---|---|---|
| L1 | `GET /api/v1/download-corner`; session plus `Account-Ref`; no query or body. | Matches the existing feature GET shape `/api/v1/vesting-scale`; controller convention is class `/api/v1` plus feature mapping (`VestingScaleController.java:33-35,54`). The proposed route has no naming discrepancy. |
| L2 | POST `/ws/NGTPA/v1/TRPGetDownloadCorner` with `policy-no`, `cert-no`, `login-user-id`, `login-user-role`. Account fields come from the selected account; login fields from actor context. Use the existing `ApimExchangeExecutor` read path, `ACCOUNT_ENV`, same retry/recovery policy, and a YAML path. | Reuses the established typed APIM boundary and avoids feature-owned transport/authentication behavior (`ApimVestingScaleAdapter.java:77-103`). |
| L3 | Response is `response.{err-message,data:[{env,categories:[{category-seq,category-name-eng,category-name-chi,items:[{item-seq,item-name-eng,item-name-chi,item-link-eng,item-link-chi,file-name-eng,file-name-chi}]}]}]}`. Require exactly one `data` item. Ignore `env`. | The selected account's `accountEnv` is already available in session context; response does not override it. One response item gives the feature one unambiguous account result. |
| L4 | Resolve one language from `Accept-Language` with the existing resolver, exactly as other endpoints do. | Keeps language normalization and defaults consistent (`RequestLanguageResolver.java:12-42`). Java forwards DS/FE labels and adds none. |
| L5 | For Chinese, independently fall back from blank Chinese category name, item name, item link, and file name to that field's English value. | A missing translation for one field must not blank an otherwise usable category or item. |
| L6 | After fallback, drop an item only if its resolved link is blank. Never probe remote file existence and never validate/rewrite URLs. | Keeps retrieval limited to listing metadata and forwards DS-owned links unchanged. |
| L7 | Sort categories by category sequence ascending, and items within each category by item sequence ascending. Preserve DS order on ties. | Gives deterministic presentation while avoiding arbitrary reordering of tied records. |
| L8 | Preserve categories with no retained items; return an empty `items` list. | FE owns empty-state wording and may still need the category shell. |
| L9 | Return `result.data.categories[]` with `categoryName` and `items[].itemName`, `itemLink`, `fileName`, using the existing camelCase JSON style and base `MutationResponse`. Expose neither sequence values nor `env`. | Matches existing response record names and wrapper (`VestingScaleResultResponse.java:1-3`; `MutationResponse.java:5-20`) while keeping DS ordering metadata private. |
| L10 | All feature retrieval errors return HTTP 503 and `NEW (proposed) err.download-corner.retrieval.unavailable`. The three named context codes classify as Vesting account-context mismatch; any other non-empty code is unmapped; missing response, empty error with missing/empty data, non-unit cardinality, or DTO/shape parse failure is malformed. Malformed events identify the field using Vesting’s dimensions. | Maintains feature-owned public errors and existing failure taxonomy (`ApimVestingScaleAdapter.java:113-138,177-203,221-229`; `VestingScaleExceptionMappings.java:15-34`). |
| L11 | Emit through `FailureEventPort` from the APIM adapter. Event `NEW (proposed) download-corner.retrieval.downstream_failure`, capability `NEW (proposed) DOWNLOAD_CORNER`, operation `NEW (proposed) TRPGetDownloadCorner`. For every non-empty `err-message`, `internalCode` is the exact raw value; malformed cases follow Vesting and use null. | Preserves structured failure ownership. Raw-code behavior follows CIM (`ApimInvestmentChoiceCancelAdapter.java:130-167`) and deliberately differs from Vesting (`ApimVestingScaleAdapter.java:205-218`). |
| L12 | Add feature-scoped `NEW (proposed) DownloadCornerExceptionMappings`, explicitly register it in `ExceptionMappingRegistryConfig`, add a test asserting registry resolution, and add the adapter to `M3R1aOwnershipActivationGuardTest`. | Prevents a feature mapping from existing without wiring and retains the terminal incident owner invariant (`ExceptionMappingRegistryConfig.java:25-63`; `M3R1aOwnershipActivationGuardTest.java:9-61`). |
| L13 | Keep existing post-login authorization unchanged, including the shared APIM credential-profile allow-list. | Feature work must not broaden platform role access (`DefaultApimCredentialProfileResolver.java:27-56`). |
| L14 | No caching and no Redis writes. | This endpoint reads current document metadata; no cache lifecycle or invalidation contract is approved. |
| L15 | Mirror the Vesting Scale feature classes one-to-one across the listed architecture layers in `07-implementation-plan.md`, adapting only structures required by this contract. | Reuses verified repository layering and test patterns without importing Vesting behavior or response fields. |
| L16 | M0 is this pack. M1 outbound; M2 inbound; one MR for M1+M2. M3 is a separate read-only post-login E2E pack at the proposed scenario path, registered in the existing feature-pack registry and run via the post-login runner with shared `E2E_ACCOUNT_INDEX` default `0`. Reject both Account-Ref and index before login. Java gates are full Maven test, JaCoCo, startup smoke, Sonar; M3 has no Java gates. | Keeps the Java feature and shell-only E2E work independently reviewable; rejection must happen before login and no mutation is in scope. |
| L17 | Write tests fail-first and record failing-then-passing evidence for each milestone. | Establishes evidence for each new behavior and contract. M0 is documentation-only, so TDD does not apply. |
| L18 | Exclude frontend search, BRD security settings, file-existence checks, and all Vesting Scale changes. Track the Vesting raw-`err-message` `internalCode` improvement as a separate follow-up, not this branch. | Preserves the bounded feature scope and avoids coupling independent behavior changes. |

## Open items and UNVERIFIED evidence

### Contract and implementation details

- **UNVERIFIED:** The BRD 6.9 source artifact and any detail beyond the supplied V0.3 scope/locked decisions were not found in the input reports. Confirm against the authoritative BRD before implementation if additional requirements are expected.
- **UNVERIFIED:** DS scalar types and nullability for sequence/text fields, whether categories/items can be null, and exact coercion rules. The locked response names the fields but the reports found no live Download Corner contract. M1 must confirm the wire schema; parse/shape failures remain malformed per L10.
- **UNVERIFIED:** The precise E2E test harness location/name and the approved JaCoCo/Sonar command invocations. The file names in the plan are proposed; use existing CI conventions when implementing.
- **UNVERIFIED:** Whether runtime APIM behavior exactly matches the supplied response/fallback contract. The source reports found no Download Corner implementation or endpoint on `origin/dev`; this design treats the user's locked decisions as authoritative.

### Prior report unknowns retained for completeness

- **UNVERIFIED (phase 0a):** The full production Redis session adapter call chain and external identity-to-actor-role mapping for internal/call-centre users were not established. This feature reuses the already-resolved context and does not need to change those flows.
- **UNVERIFIED (phase 0a):** Whether every internal/call-centre role reaches the shared resolver as `ROLE_INPUT` or a blocked role. No role behavior is changed here.
- **UNVERIFIED (phase 0a/0b):** Whether an external API contract registry exists outside the repository. No OpenAPI/Swagger/API-spec artifact was found in the reports' origin/dev tree search; the BFF serialization test remains the local contract proof.
- **UNVERIFIED (phase 0b):** Whether the deployed logging encoder adds Reactor/MDC values as top-level JSON fields. No logger/config change is proposed.
- **UNVERIFIED (phase 0b):** Whether Vesting Scale is fully migrated under the exception-handling milestone schedule. This feature relies on observed live source behavior only and makes no Vesting change.
- **UNVERIFIED (phase 0b):** The exact DS contract for Download Corner (including Chinese fallback requirements) was not established by source evidence; the user-provided locked decisions settle this design's behavior, not independent DS verification.

### E2E selector gap found during live verification

The phase reports describe the existing selector conflict as requiring a non-empty index. Live `authenticated-login-journey.sh` confirms the implementation checks `-n "$account_index"` while separately defaulting an empty index to `0` (`scripts/e2e/lib/authenticated-login-journey.sh:78-97`). Therefore two variables that are both set but where the index is explicitly empty are not rejected by the current conflict branch. L16 is stricter: M3 must add a fail-first shell regression and make the common pre-login selector reject simultaneous variable presence, including this empty-index case.

### Numbering and report reconciliation

- Phase 0a describes `23` as the next number after the highest numbered folder on `origin/dev`; phase 0b notes `23` appears on another ref and that global numbering differs by ref. These statements use different ref scopes and are not a source conflict: `origin/dev` contains the Vesting pack as `22`, while `23` exists on an alternate ref.
- The task explicitly fixes this pack path at `docs/design/25-download-corner/`; the gap between `22` and `25` is intentional for this task. The requested path was absent at the M0 precondition check.
- L16 names `scripts/e2e/feature-pack-registry.sh`, but both reports and live `origin/dev` identify the registry at `scripts/e2e/lib/feature-pack-registry.sh` (`scripts/e2e/lib/feature-pack-registry.sh:4-10`). The implementation plan follows the existing registry file rather than proposing a parallel registry at the non-existent path.
- No mismatch was found between the phase reports and live `origin/dev` for the verified Vesting route convention, APIM read path, response wrapper, failure-event ownership, raw CIM error-code provenance, or shared E2E selector behavior. No Download Corner source or docs existed to contradict the locked feature decisions.

## Follow-up (not this branch)

Update Vesting Scale in a separate follow-up to preserve the raw non-empty DS `err-message` in its failure event's `internalCode`, matching the existing CIM behavior. No Vesting code, tests, configuration, or documentation is changed by this pack.

# Account Balance Design Handover

**Feature:** 6.14 Account Balance
**Baseline:** 27/08/2026
**Status:** Phase II implemented, pending architect approval

## Consolidation result

The uploaded package contained multiple versions of the same design areas. It has been consolidated without discarding the still-valid source inventory, requirements, APIM rules, transformation logic, validation, observability, test traceability, decisions, and handover content.

## Current cache decision

Use one lazy policy-scoped Redis entry:

```text
<prefix>:general-metadata:<opaque-env-trust-policy>
```

The entry embeds whether TAS resolved `policy-no=all` or the specific policy. This avoids separate-marker lifecycle management and avoids unsafe reuse of a default for an unseen policy.

## Next step

Run Phase I repository investigation first. Then revise the physical implementation plan with exact packages, classes, validators, Redis ports, concurrency mechanisms, APIM ledger locations, and test names before generating implementation artifacts.

## Header contract correction

The Account Balance GET endpoint requires `X-Request-Id`; `Accept-Language` is optional; and `Account-Ref` is required by selected-account context resolution. `Idempotency-Key` has been removed from the approved contract. Any repository-wide enforcement discovered during Phase I must be escalated to the architect as a contradiction.

## Phase I Repository Ratification

The repository investigation ratifies these physical boundaries:

- `X-Request-Id` is the only header universally rejected when missing by the shared pre-controller mandatory-header filter. Only `X-Request-Id` is propagated to APIM.
- `Accept-Language` is optional. When supplied, the existing shared language resolver selects English or Traditional Chinese TAS descriptions. Missing, blank, or unsupported values resolve to English. Account Balance adds no language parser and does not return `VALIDATION_FAILED` for an absent language.
- `Account-Ref` is required by the existing selected-account context-resolution path, not by an Account Balance controller validator. `PortalAccessContextWebFilter` resolves and publishes `PortalAccessContext` to Reactor Context and exchange attributes; `CurrentPortalAccessContextResolver.current()` is the controller reuse point. `Account-Ref` is neither sent to APIM nor echoed to FE.
- `Idempotency-Key` is absent from the naturally repeatable Account Balance GET contract. No idempotency store, replay record, or idempotency adapter is introduced.
- `MutationResponse` is the target public envelope. `ApiResponse` is obsolete and must not be introduced.
- Technical failures use HTTP 503, `SYSTEM_ERROR`, `err.system.unavailable`, and `errors[].type=SYSTEM`. `PAGE` and `GLOBAL` are not error types.
- Portfolio omission uses scoped Jackson inclusion on Account Balance response DTO fields. Global `ObjectMapper` inclusion must not change. Hidden overview `vestedBalance` and `pendingSwitchIn` remain present as JSON null; hidden `fxRate` and blank or malformed `factsheetUrl` are omitted.
- `ContributionController#getContributionSummary` and `ContributionControllerTest` are the primary physical controller and test patterns. Account Balance preserves its optional `currency`, selected-account context, optional language fallback, both result views, and absence of `Idempotency-Key`; contribution business DTOs and logic are not copied.

### Implementation handover

Phase I investigation is ratified. Implementation must preserve the exact physical boundaries and evidence-backed reuse points above. The next work starts with fail-first shared general-metadata domain and port contracts, followed by executor-backed APIM and Redis adapters, then the Account Balance controller and application flow. Update this `handover.md` during every implementation stage to preserve agreed context.

## Phase II-001 implementation handover

Shared general-metadata domain and port contracts now define downstream-independent metadata names and types, explicit TAS wire mapping, policy-scoped resolution, duplicate normalization, unbounded nonnegative precision parsing through `BigInteger`, one-call required-key resolution, and reactive source/cache boundaries. No APIM DTO, Redis implementation, YAML, controller, Account Balance mapper, blocking call, or rounding policy is included. Phase II-002 must build on these contracts without changing their public semantics unless architect review records a replacement.

### Phase II-001 v003 contract repair

`FetchGeneralMetadataCommand` now carries the trusted `ActorUserRole` unchanged from the protected application boundary. The future APIM metadata adapter must use this role with the command scope's account environment for credential resolution. It must not hardcode a member role or read ambient `PortalAccessContext`. Focused fail-first evidence covers null rejection and preservation of every supported role.

## Phase II-002 implementation handover

The `TRPGetGeneralMetaData` APIM contract now has exact request/row DTOs and operation-content normalization. Unfiltered requests always serialize blank `metadata-name` and `metadata-type`; targeted requests use the explicit shared wire mapping. Response normalization accepts `policy-no=all` or the requested policy, rejects mixed/foreign policy rows, deduplicates exact rows, rejects conflicts and unknown wire values, validates precision, and permits empty data only as a targeted-fallback transport outcome. Redis, resolver orchestration, final executor adapter, Account Balance DTOs, and controller logic remain excluded.

### Phase II-002 v002 contract repair

The exact APIM response envelope is now represented, including `response`, `err-message`, and `data`, with forward-compatible unknown-property handling and structural validation. Empty `data` remains available for targeted fallback but is represented as unresolved through `Optional.empty()` rather than inventing a policy resolution source. Non-empty normalized results always carry the source proven by their rows.

### Phase II-002 v004 canonical envelope ownership repair

Generic APIM envelope parsing and validation are owned exclusively by `ApimResponseEnvelope<T>` and `ApimResponseValidator.requireSuccessData(...)`. The metadata-owned response body and envelope were removed. General metadata retains only its operation-specific row DTO and content normalizer. Valid empty canonical data remains an unresolved `FetchGeneralMetadataResult`; malformed or rejected generic envelopes remain standard `ApimException` outcomes. Phase II-003 must invoke the canonical executor and validator before passing rows to the metadata normalizer.

### Phase II-002 v007 account environment naming repair

Java-side metadata contracts use `accountEnv` consistently to distinguish the account database environment from development or deployment environments. The APIM JSON property remains exactly `env` through `@JsonProperty("env")`. No Java accessor or domain/application field named only `env` is introduced by the metadata capability.

### Phase II-002 v008 complete account environment naming repair

All Java identifiers in the general metadata capability now use `accountEnv` for the account database environment, including record components, factory parameters, local references, and tests. The only retained standalone `env` text is the required APIM JSON wire property in `@JsonProperty("env")` and serialized fixtures. This prevents confusion with development and deployment environments.

## Phase II-003 implementation handover

`TRPGetGeneralMetaData` now has an authenticated `ACCOUNT_ENV` outbound adapter backed exclusively by `ApimExchangeExecutor.postEnvelope(...)`. The adapter uses trusted `command.actorUserRole()`, `command.scope().accountEnv()`, `SAFE_BUSINESS_CALL`, the canonical generic envelope validator, and the Phase II-002 operation-content normalizer. Correlation propagation remains executor/transport infrastructure; no request DTO field or feature-owned propagator was added. The routing ledger and shared response-validation boundary include the new adapter.

## Phase II-004 domain source correction handover

- Decision authority: Nick selected Option B on 28 August 2026.
- Canonical policy-specific resolution source is `POLICY_SPECIFIC`.
- The former requested-policy enum name and all cumulative references were replaced deterministically.
- `DEFAULT_ALL` remains unchanged.
- This correction changes naming only. It does not add Redis persistence, resolver orchestration, targeted fallback, preload, stale fallback, or invalidation behavior.
- Phase II-004 Redis adapter implementation must consume `DEFAULT_ALL` and `POLICY_SPECIFIC`.
- Account Balance feature-level formatting remains deferred until stabilization.

## Phase II-004 Redis cache adapter handover

General metadata now has one lazy policy-scoped Redis document per account environment and opaque trust/policy digest. Keys use `RedisCacheKeyFactory` with capability `general-metadata`; raw trust codes and policy numbers are excluded. Values retain scope, entries, and `DEFAULT_ALL` or `POLICY_SPECIFIC` resolution. Writes use the externally bindable `redis-cache.general-metadata.ttl` property with `PT20M` default. Corrupt documents are evicted and treated as misses; Redis infrastructure failures remain failures. Resolver orchestration, targeted fallback, preload, stale fallback, and invalidation endpoints remain excluded.

## Phase II-005 resolver-query actor-role correction

`ResolveGeneralMetadataQuery` now carries the trusted `ActorUserRole` required by `FetchGeneralMetadataCommand`. The resolver must pass this value unchanged on an unfiltered cache miss. It must not hardcode a role, read ambient `PortalAccessContext`, or pass null. This correction changes only the resolver input contract and focused contract test; cache-hit and full-miss orchestration remain Phase II-005 implementation work.

## Phase II-005 resolver cache-flow handover

`GeneralMetadataResolverService` now implements the reactive cache-hit and full unfiltered cache-miss path. A complete policy-scoped cache hit returns without APIM. A miss or incomplete entry performs exactly one unfiltered source request using the trusted query `ActorUserRole`, requires every requested key, writes only a complete `DEFAULT_ALL` or `POLICY_SPECIFIC` set, and returns it. Empty or incomplete unfiltered data raises `MissingRequiredGeneralMetadataException` without a cache write so Phase II-006 can add targeted fallback. Redis and APIM failures remain failures. No blocking call, startup loader, role hardcoding, targeted request, stale fallback, or Account Balance endpoint is included.

## Phase II-006 targeted fallback handover

When cache data and the full unfiltered metadata response still omit required keys, the resolver now submits one `FetchGeneralMetadataCommand` containing the complete missing-key set. The committed APIM adapter applies the ratified batching strategy by issuing one deterministic targeted request per distinct key. Validated targeted rows are merged with existing unfiltered rows, preserving established keys and requiring one consistent `DEFAULT_ALL` or `POLICY_SPECIFIC` source. Conflicting sources or values fail without a cache write. The cache TTL resets only after the merged result is complete and successfully written. One incomplete targeted attempt raises `MissingRequiredGeneralMetadataException`; there is no polling or unbounded retry.

## Phase II-007 atomic metadata cache replacement contract

`GeneralMetadataCachePort` now exposes capability-scoped expected-value replacement with `APPLIED`, `CONFLICT`, and `MISSING` outcomes. The Redis implementation compares the exact existing serialized metadata document and replaces it atomically with `KEEPTTL`; it does not reset the 20-minute TTL. Unsupported or empty Lua results and Redis failures fail closed with sanitized cache errors. The shared `CachePort` remains unchanged. Resolver conflict recovery and optional in-process coalescing remain deferred to the final Phase II-007 hardening round.

## Phase II-007 resolver concurrency hardening

Same-policy resolver misses now coalesce within one JVM through a policy-scoped in-flight `Mono` using the repository `ConcurrentHashMap`, `computeIfAbsent`, `Mono.cache()`, and conditional `doFinally` removal pattern. Different policy scopes remain independent. Correctness does not depend on coalescing: complete unfiltered loads remain idempotent replacements, while targeted read-modify-write uses Redis expected-value replacement. `APPLIED` returns the replacement; `CONFLICT` or `MISSING` performs exactly one cache reread and returns only a complete competing value, otherwise a sanitized technical concurrency failure is raised. There is no polling, repeated targeted request, repeated CAS attempt, stale fallback, startup load, blocking call, or detached subscription.

## Phase II-007 resolver concurrency correctness repair

In-flight coalescing now uses the immutable pair of policy scope and required-key set, preventing a broader query from receiving a result complete only for a narrower concurrent query. Targeted completion distinguishes initial cache absence from mutation of an existing document: an initially absent cache receives one final complete write and never CAS against an unpublished intermediate value; an existing partial document uses one expected-value replacement and the existing bounded reread on conflict or missing state. No incomplete intermediate document is written.

## Phase II-008 targeted APIM cardinality ratification

Decision authority: Nick Deng on 28 August 2026. `TRPGetGeneralMetaData` accepts exactly one explicit `metadata-name` and one explicit `metadata-type` pair per targeted request. It does not support wildcard filters or multiple explicit key pairs in one request. Supplying both fields as blank requests the full metadata set.

The application boundary remains one `FetchGeneralMetadataCommand` containing the complete deduplicated missing-key set. The physical APIM adapter is explicitly ratified to expand that bounded set into one targeted exchange per distinct key, in deterministic key order, using sequential reactive `concatMap`. Fan-out is bounded by the immutable `query.requiredKeys()` set size. Exact duplicate keys are eliminated by the set contract. A failure, malformed response, conflicting source, or conflicting value in any exchange fails the combined operation; no partial metadata result is returned or cached. Each exchange retains the existing `SAFE_BUSINESS_CALL` executor policy and shared correlation and observability infrastructure.

The earlier phrase “one-call required-key resolution” means one application-port invocation, not one physical APIM exchange. The earlier phrase “one targeted batch” means one bounded application command with deterministic per-key physical fan-out. This ratification resolves the Phase II architect-review cardinality finding without changing production behavior.

## Phase II runtime-gate repair

Focused runtime execution exposed stale test assertions and fixtures plus one production cancellation-lifecycle defect. Tests now assert direct reactive exceptions rather than artificial nested causes, compare Optional resolution sources correctly, return a valid full-set row for blank-filter APIM fixtures, create an actual DEFAULT_ALL versus POLICY_SPECIFIC conflict, and guard cumulative CAS structure without depending on a local variable name. Resolver coalescing now removes the exact in-flight mapping when a downstream subscriber cancels, while retaining terminal cleanup, immutable scope-plus-required-key identity, shared cached execution for active subscribers, and all existing cache/CAS behavior. The ratified blank-filter full-set request and deterministic sequential per-key targeted fan-out remain unchanged.

## Phase II final contract-guard repair

The post-repair focused runtime gate passed 66 of 67 tests. The sole remaining failure was a formatting-sensitive source-text assertion requiring `cache.read(query.scope())` as one contiguous string, while Spotless correctly formats the bounded recovery read across lines. The guard now verifies `recoverAfterConflict(query)` and `.read(query.scope())` independently. Production code and runtime behavior are unchanged.

## Phase II bounded-recovery guard repair

The final focused gate passed 66 of 67 tests. The remaining source-level guard incorrectly scanned the entire resolver for `for (` and therefore rejected legitimate finite loops in deterministic metadata merge logic. The guard now extracts only `recoverAfterConflict(...)` and verifies that the bounded recovery method contains one cache read and the sanitized concurrency failure, with no `while`, `for`, `retryWhen`, or `repeatWhen`. Production code and runtime behavior are unchanged.

## Phase II configuration correction

The TRPGetGeneralMetaData operation path is now bound from the established APIM runtime configuration seam at `apim.general-metadata.path`, explicitly declared in application YAML with environment override support, validated as required and non-blank during adapter bean construction, and supplied to `ApimExchangeRequest`. The adapter no longer owns the URL literal.

Shared metadata cache TTL is now owned by the feature-neutral Redis capability namespace `redis-cache.general-metadata.ttl`, explicitly configured as `PT20M` and runtime-overridable. Null retains the repository-approved defensive `PT20M` default; zero and negative durations fail validation. Redis `PTTL` plus `KEEPTTL`, targeted APIM cardinality, resolver concurrency, and all other accepted Phase II behavior are preserved. Phase III remains blocked pending refreshed architect approval.


## Phase II architecture regression repair

The governed APIM routing inventory now includes `TRPGetGeneralMetaData` as the sixteenth operation. `GeneralMetadataResolverService` remains framework-free in the application layer; Spring runtime composition is owned by outer configuration in `GeneralMetadataRuntimeConfig`. The configuration test import is restored without weakening missing/blank path startup validation. Phase III remains blocked pending refreshed validation and architect approval.

## Phase II configuration-test wiring repair

The configuration startup test now declares its nested `CacheConfig`, enabling `GeneralMetadataCacheProperties` alongside the production `GeneralMetadataApimValidationConfig`. This repairs test compilation without weakening missing/blank path or invalid-TTL startup checks. The routing-ledger and clean-architecture repairs remain preserved. Phase III remains blocked pending refreshed validation and architect approval.

## Phase II routing-ledger cardinality repair

The governed APIM operation inventory contains `TRPGetGeneralMetaData` exactly once and now asserts the corresponding total cardinality of 16 rather than the pre-feature total of 15. This corrects the stale characterization expectation without changing runtime behavior. Phase III remains blocked pending cumulative validation and architect approval.

## Phase III-001 FE contract and controller boundary

Implemented `GET /api/v1/account/balance` with optional `currency`, shared `X-Request-Id` enforcement, optional `Accept-Language` with English fallback, and selected-account delegation through `CurrentPortalAccessContextResolver`. The success envelope uses `MutationResponse` with Account Balance data under `result.data`. Account Overview nullable fields remain stable, portfolio omission is property-scoped, and public terminology uses Display Account and Sub-Display-Account. APIM retrieval and final transformation remain excluded from this subphase.

## Phase III-002 application context and reference-date orchestration

Implemented a framework-free `GetAccountBalanceService` that resolves `ReferenceDatePort.resolveReferenceDate(accountEnv)` exactly once before invoking the focused `AccountBalanceDataPort`. The explicit downstream command preserves actor role, actor user ID, policy number, certificate number, account environment, trust code, scheme type, optional currency, and `LocalDate` reference date. Policy and certificate values remain strings, `Account-Ref` and `PortalAccessContext` do not cross the application boundary, and empty or failed reference-date resolution short-circuits the downstream call. The TRPGetMemberAccBal adapter, wire-level role conversion, certificate conversion, date formatting, and metadata aggregation remain deferred.

## Phase III-003 TRPGetMemberAccBal APIM DTO contract

Implemented exact adapter-only request and response DTOs for `TRPGetMemberAccBal`. The request preserves `policy-no` as a string, uses numeric `cert-no`, formats `ref-date` at the future adapter boundary, and always serializes `curr`, using an empty string for initial load. The response models `displayOptions`, `curr`, `dispSrc`, `fundDtl`, and `accBal`; financial values use `BigDecimal`, bilingual descriptions and factsheet strings remain raw, and exactly one non-null complete `response.data` item is required. Executor invocation, business-error classification, FE grouping, joining, localization, rendering, and factsheet validation remain deferred.

## Phase III-004B typed retrieval error boundary

Added a feature-owned `AccountBalanceRetrievalException` with typed `ACCOUNT_NOT_FOUND`, `ACCOUNT_MISMATCH`, and `SYSTEM_FAILURE` reasons. The application exception is web-neutral and redacts raw TAS details. The inbound response policy maps the two approved business reasons to HTTP 422 `BUSINESS_REJECTED` with `err.ab.account.not.exist` and `err.ab.account.mismatch`, and maps system failure to HTTP 503 `SYSTEM_ERROR` with `err.system.unavailable`. The executor-backed APIM adapter remains deferred to Phase III-004C.

## Phase III-004C1 raw Account Balance source boundary

Introduced an application-owned `AccountBalanceSourcePort` returning transport-neutral `RawAccountBalanceData`. The raw contract mirrors the validated TAS structures and preserves `BigDecimal` values, nullable balances, bilingual descriptions, and raw factsheet strings without Jackson or APIM dependencies. The existing `AccountBalanceDataPort` remains the later aggregation boundary returning `AccountBalanceResult`, so Phase III-002 orchestration and the public controller continue to compile unchanged. The executor-backed APIM adapter will implement `AccountBalanceSourcePort`; a later application transformation stage will consume it and implement `AccountBalanceDataPort`.

## Phase III-004C2 executor-backed Account Balance APIM adapter

Implemented `ApimAccountBalanceSourceAdapter` against the application-owned raw source port through the canonical APIM executor. The adapter uses runtime-validated `apim.account-balance.path`, `ACCOUNT_ENV`, trusted actor role and account environment, and `SAFE_BUSINESS_CALL`; converts certificate and reference date at the boundary; classifies only the two approved TAS business errors; sanitizes every other failure; validates the canonical envelope and exactly-one data item; and preserves raw decimal, bilingual, nullable, sentinel, ordering, and factsheet values without frontend transformation. The routing ledger now registers `TRPGetMemberAccBal` as its seventeenth unique executor-owned operation.

## Phase III-005 Account Balance boundary integration

The application use case now composes one reference-date resolution, one policy-scoped shared general-metadata resolution using all eight Phase IV precision and rounding keys, one raw Account Balance source call, and one application-owned assembler call. Failures short-circuit subsequent stages. The assembler seam is intentionally provisional: its production bean returns the approved sanitized system failure until Phase IV supplies the real transformation, while tests may inject controlled fixture assemblers. No financial values, hierarchy, localization, rendering, or factsheet behavior is fabricated in Phase III. The duplicate Phase III-003 handover section was removed.

## Phase III-006A phase review requirements contract

Added the committed `account-balance-phase-review-requirements.md` governance contract. It fixes the timestamped `phase-review` directory, twelve required review files, formatter and compilation gates, cumulative Git boundaries, architecture and preservation audits, provisional-assembler blocker disclosure, machine-readable review status, and the architect decision gate. This subphase defines review evidence only and does not begin Phase IV transformation.

## Phase III-006B stabilization and architect review package

Ran the deferred repository formatter once at the Phase III feature-level stabilization point, then reran production compilation and test compilation. Generated the committed-contract twelve-file architect-review package from the immutable Phase III base through the pre-review HEAD. Cumulative audits cover the public header contract, selected-account ownership, technical error shape, APIM request and cardinality contracts, executor ownership, reactive non-blocking behavior, preservation of all approved Phase III boundaries, and absence of premature presentation transformation. The provisional `AccountBalanceAssembler` production bean remains the explicit Phase IV blocker: it returns a sanitized system failure and does not fabricate financial output. Phase IV remains blocked pending the architect decision.

## Phase IV-001 normalized holding model and joins

Added an application-owned synchronous Account Balance normalizer with immutable insertion-ordered indexes for display-source, fund, and holding keys. Null or blank identifiers, duplicate keys including exact duplicate payloads, null records, and holding orphans fail through the sanitized `AccountBalanceRetrievalException.systemFailure()` boundary. Empty collections and unreferenced display-source or fund parents remain valid. Raw decimal values, nullable and sentinel values, bilingual descriptions, ordering fields, and factsheet strings are preserved unchanged. APIM DTOs, final response hierarchies, aggregation, localization, number rendering, and factsheet validation remain excluded. The provisional production assembler remains unchanged for later Phase IV integration.

## Phase IV-002 Account Overview aggregation

Added a synchronous application-owned Account Overview aggregator over the Phase IV-001 normalized indexes. It groups exact raw NAV and visible vested balances by Display Account, derives the minimum source sequence, orders by sequence then technical Display Account ID, and computes total balance plus visible Pending Switch-in. Hidden values remain null and are not inspected. Visible null or numeric -1 participating values fail through the sanitized system-failure boundary, while zero remains valid. Empty holdings produce no Display Accounts and zero totals without fabricating rows from unreferenced parents. Localization, labels, rendering, portfolios, currency conversion, factsheet handling, and production assembler replacement remain deferred.

## Phase IV-003 Fund Level View

Added the application-owned raw `Display Account -> Fund -> Sub-Display Account` hierarchy. Only held nodes are emitted. Unit and NAV values are aggregated by `(displaySource, fundCode)` using raw `BigDecimal`; each leaf retains the unique holding values. Ordering uses held minimum source sequence and technical display ID, fund sequence and code, then source sequence and technical sub-display ID. Null or numeric `-1` participating unit/NAV values and missing participating sequences fail through sanitized `SYSTEM_FAILURE`. Localization, formatting, JSON omission, currency conversion, factsheet handling, final assembly, and replacement of the provisional production assembler remain deferred.

## Phase IV-004 Sub-Display-Account Level View

Added the application-owned raw `Display Account -> Sub-Display Account -> Fund` hierarchy. It is generated independently from the same normalized holdings used by the Fund Level View, and every fund leaf preserves its unique holding unit and NAV values. Ordering uses held minimum source sequence and technical display ID, source sequence and technical Sub-Display Account ID, then fund sequence and code. Cross-view tests prove identical canonical holding projections without reconstructing either hierarchy from the other. Null or numeric `-1` participating unit/NAV values and missing participating sequences fail through sanitized `SYSTEM_FAILURE`. Localization, rendering, scoped JSON omission, currency conversion, factsheet handling, final assembly, and production assembler replacement remain deferred.

## Phase IV-005 localization and precision rendering

Added a framework-free application presentation boundary for dynamic English/Traditional Chinese description selection with symmetric nonblank fallback, plus metadata-driven precision extraction and exact grouped decimal rendering. Rendering uses `RoundingMode.UNNECESSARY`, preserves trailing zeros, and fails through sanitized `SYSTEM_FAILURE` when exact representation is impossible. All four precision categories are required without a business maximum. Phase IV-001 through IV-004 raw normalization, aggregation, and both portfolio views remain unchanged. Currency selection, factsheet handling, final result composition, scoped web omission, and replacement of the provisional production assembler remain deferred.

## Phase IV-006 currency, visibility, and factsheet presentation

Added strict currency-selection mapping that preserves TAS order and rejects blank, duplicate, or inconsistent selected currencies. Added graceful factsheet resolution that retains valid absolute URLs, omits null or blank values, and reports malformed or relative values through a zero-argument event port so the full URL cannot enter logs. Existing Account Balance response DTOs retain property-scoped Jackson behavior: overview hidden values serialize as null, while null portfolio FX rate and factsheet URL are omitted. No global ObjectMapper change or fundFactsheetEnabled field is introduced. The provisional assembler remains unchanged because the current flattened public result cannot represent both approved portfolio hierarchies; final contract ratification and result composition remain required.

## Phase IV-007 Full Assembler Integration

- Ratified full response contract added.
- The provisional assembler is replaced by complete normalization, aggregation, portfolio, localization, precision, currency, and factsheet composition.
- Both portfolio views are generated independently from one normalized model.
- Spotless and runtime tests remain deferred to Phase IV-008.

### Phase IV-008 stabilization and architect review

Repaired the stale visibility architecture guard after the ratified dual-view response replaced the flattened portfolio DTO. The feature-level formatter is executed exactly once in this subphase, followed by production compilation, test compilation, cumulative static audits, and diff checking. The required twelve-file Phase IV architect-review package is generated from the immutable Phase IV base through the pre-review HEAD. Runtime Maven tests are not executed by the generated validation workflow. Phase V remains blocked pending the architect decision.

### Phase V-001 public error contract hardening

Hardened the Account Balance application boundary so every unexpected ReferenceDate, metadata/cache, source, assembler, join, sentinel, precision, and transformation failure becomes a sanitized `SYSTEM_FAILURE`, while existing typed `ACCOUNT_NOT_FOUND`, `ACCOUNT_MISMATCH`, and `SYSTEM_FAILURE` exceptions are preserved unchanged. Arbitrary upstream causes are deliberately not retained, preventing raw downstream messages, financial details, metadata content, or internal exception text from remaining reachable through the public failure. The two approved TAS business mappings remain unchanged; every other non-validation failure continues to map to HTTP 503, `SYSTEM_ERROR`, `err.system.unavailable`, and type `SYSTEM`. Missing language remains valid, Account-Ref remains selected-context-owned, and only X-Request-Id remains universally mandatory.

### Phase V-002 observability, redaction, and security hardening

Introduced separate Account Balance and shared General Metadata observability ports so shared metadata remains feature-neutral. Both contracts accept only request correlation, normalized enums, nonnegative counts, booleans, and durations. Their SLF4J adapters emit structured fixed-field events and cannot receive commands, scopes, DTOs, cache keys, URLs, metadata values, APIM bodies, financial values, identity values, or exceptions. Existing request correlation, sanitizer, APIM header-only logging, SHA-256 metadata key opacity, URL-free malformed-factsheet telemetry, error normalization, and reactive concurrency behavior remain unchanged. No second logging framework or metrics framework was introduced.

### Phase V-003 architecture, configuration, and operation guards

Added cumulative source-level guards for both runtime-configured APIM paths, the existing `PT20M` Duration-bound General Metadata TTL, absence of startup metadata loading, canonical `ApimExchangeExecutor` ownership, `ACCOUNT_ENV` and `SAFE_BUSINESS_CALL` classification, narrow request-correlation propagation, non-blocking Reactor usage, absence of global Jackson-inclusion mutation, absence of an Account Balance idempotency-key requirement, and shared General Metadata domain neutrality. Existing operation-ledger and architecture allowlist coverage is preserved and audited rather than duplicated. No production runtime behavior changed in this test-only subphase.

### Phase V-004 contract and regression stabilization

Confirmed the complete Account Balance regression matrix through focused controller, serialization, APIM, application-service, presentation, metadata resolver, Redis cache, and architecture tests. No approved runtime behavior or production contract required change. This feature-level stabilization executes the repository formatter exactly once, then production compilation and test compilation, cumulative static and preservation audits, diff checking, and the governed twelve-file Phase V architect-review package. Maven runtime tests are not executed by the generated workflow. The next phase remains blocked pending architect decision.

#### Phase V-005 scripted local functional validation assets

Added a curl-based local Account Balance validation package with environment-supplied authenticated context, safe scenario selection, strict public response assertions, sanitized business/system fixtures, and documented local APIM/Redis stub prerequisites. Direct scenarios cover initial load, explicit currency, request-ID validation, language fallback, and shared selected-account behavior. Hook-driven scenarios cover approved business mappings, metadata cache and resolution paths, malformed cardinality, and graceful factsheet omission without coupling the repository to a specific stub product. No real identifiers, credentials, financial values, or protected context are committed. The functional suite is not executed by the deterministic writer because the local BFF, authenticated session, Redis, and APIM stubs are external prerequisites. Formatting remains deferred to Phase V-006.

### Phase V-006 final stabilization and architect review

Spotless executed exactly once.

Compile PASS.
Test compile PASS.

Architect review package generated.

STATUS=READY_FOR_ARCHITECT_REVIEW
NEXT_PHASE_BLOCKED_PENDING_ARCHITECT_DECISION=YES

## Production response contract correction

Corrected the public Account Balance response to preserve the approved structured `accountOverview` object containing `displayOptions`, `displayAccounts[]`, and `accountTotals`. The existing raw overview aggregation, visibility flags, deterministic Display Account ordering, localized labels, and totals are now carried through the application and web response boundaries instead of being flattened. `factsheetUrl` was also removed from Fund Level View nested Sub-Display-Account leaves while remaining on both legitimate Fund node shapes. Controller, serialization, assembler integration, architecture guards, the FE-BFF API example, and the Phase IV-007 ratification now lock the same contract. No APIM transport, currency, authentication, selected-account, or error behavior changed.

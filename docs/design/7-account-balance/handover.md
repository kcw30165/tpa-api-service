# Account Balance Design Handover

**Feature:** 6.14 Account Balance  
**Baseline:** 27/08/2026  
**Status:** Consolidated design baseline, pending repository investigation  

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

General metadata now has one lazy policy-scoped Redis document per account environment and opaque trust/policy digest. Keys use `RedisCacheKeyFactory` with capability `general-metadata`; raw trust codes and policy numbers are excluded. Values retain scope, entries, and `DEFAULT_ALL` or `POLICY_SPECIFIC` resolution. Writes use the externally bindable `account-balance.general-metadata.cache.ttl` property with `PT20M` default. Corrupt documents are evicted and treated as misses; Redis infrastructure failures remain failures. Resolver orchestration, targeted fallback, preload, stale fallback, and invalidation endpoints remain excluded.

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

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

# Account Balance Implementation Plan

**Feature:** 6.14 Account Balance  
**Baseline:** 27/08/2026  
**Status:** Consolidated design baseline, pending repository investigation  

## Planning strategy

The previous seven stages were too broad. This revision separates a mandatory investigation gate from small implementation subtasks. The logical sequence is usable now, while exact files/classes and effort must be updated immediately after investigation.

## Phase I: repository investigation, no production changes

### I-001 Inbound and response conventions

Find controller patterns, generic response builders, optional query validation, and exact validation codes/targets for X-Request-Id, Accept-Language, and Account-Ref.

Deliverable: investigation evidence and exact reuse map.

### I-002 Portal context and ReferenceDate

Trace Account-Ref to `PortalAccessContext`, ActorUserRole downstream mapping, certificate type conversion, and ReferenceDate failure outcomes.

Deliverable: exact command fields and orchestration mapping.

### I-003 APIM integration conventions

Trace `ApimExchangeExecutor`, operation path configuration, Account Environment scope, retry classification, envelope parser, business rejection mapping, routing ledger, and architecture guards.

Deliverable: exact adapter and test pattern.

### I-004 Redis and concurrency conventions

Trace `RedisCacheKeyFactory`, Redis Sentinel adapters, JSON serialization, TTL configuration, CAS/set-if-absent/locking, and any in-flight `Mono` coalescing.

Deliverable: selected one-key cache update and same-key miss strategy.

### I-005 Investigation decision update

Update master design, metadata design, test design, implementation plan, and handover with confirmed physical names and remove assumptions.

Deliverable: investigation-ratified implementation baseline.

## Phase II: shared general metadata capability

### II-001 Fail-first domain and port contracts

Add enums, scope, key, resolved-entry model, resolver port, and precision access semantics without APIM or Redis implementation.

### II-002 TRPGetGeneralMetaData APIM DTO contract

Add exact hyphenated request/response DTOs, blank optional filters, enum wire mapping, duplicate validation, and APIM classification tests.

### II-003 Executor-backed metadata adapter

Implement the focused APIM adapter through the canonical executor and existing generic envelope handling.

### II-004 Redis entry and key adapter

Implement one opaque env/trust/policy key, embedded resolution, 20-minute TTL, serialization, corrupt-entry handling, and no sensitive key material.

### II-005 Resolver cache-hit and cache-miss flow

Implement Redis hit, full unfiltered miss, response validation, storage, and returned effective metadata.

### II-006 Targeted metadata fallback

Implement missing-item targeted call, same semantics, same-entry update, and missing-after-reload technical failure.

### II-007 Concurrency hardening

Apply the investigated project pattern for same-key concurrent misses and prove no corrupt/lost cache state.

## Phase III: Account Balance data boundary

### III-001 Fail-first FE API contract

Lock endpoint, optional currency, required Account Balance headers, Display Account names, currency selection, visibility, and generic response shape.

### III-002 Account Balance application query and context mapping

Define the focused use-case query and map actor/account/reference-date values without passing `PortalAccessContext` into infrastructure.

### III-003 TRPGetMemberAccBal DTO contract

Add exact request DTO, `curr:""` behavior, array response DTOs, exactly-one cardinality, and operation-content validation.

### III-004 Executor-backed Account Balance adapter

Implement the focused adapter through canonical executor behavior and approved business-error classification.

## Phase IV: transformation and presentation

### IV-001 Normalized indexes and join validation

Implement display-source, fund, and holding indexes; duplicate/orphan failure behavior.

### IV-002 Account Overview aggregation

Implement Display Account order/balance/vested aggregation and account total/Pending Switch-in behavior.

### IV-003 Fund Level View

Implement Display Account to Fund to Sub-Display-Account hierarchy and fund totals.

### IV-004 Sub-Display-Account Level View

Implement Display Account to Sub-Display-Account to Fund hierarchy.

### IV-005 Localization

Implement existing Accept-Language reuse, English/Chinese selection, and fallback behavior.

### IV-006 Number rendering

Integrate shared precision, grouped strings, trailing zeros, and no invented rounding.

### IV-007 Currency selection and factsheet

Map available/selected currency and add valid-URL pass-through with safe malformed omission.

## Phase V: errors, integration, and evidence

### V-001 Public error mapping

Lock both BUSINESS_REJECTED mappings and universal 503 SYSTEM_ERROR fallback.

### V-002 Controller and end-to-end integration

Wire the controller, orchestration, shared resolver, data adapter, and generic response mapper.

### V-003 Architecture and security guards

Update operation ledger, architecture tests, no-blocking rules, header propagation checks, and logging redaction.

### V-004 Local functional validation

Add scripted curl checks for initial load, currency selection, business errors, cache hit/miss, and malformed factsheet degradation.

### V-005 Evidence and handover

Generate sequential evidence under `target/evidence`, update `handover.md`, and provide the commit message.

## Validation approach

Each implementation subtask should have fail-first tests, production change, formatting, `mvn -DskipTests compile`, `mvn -DskipTests test-compile`, focused contract tests where explicitly run, static audits, and evidence. Full tests remain manual when needed under the existing project workflow.

## Commit message

```text
feat(account-balance): add account balance and shared metadata design baseline
```

## Architect review gate

Phase I investigation scripts produce evidence only and end with `REVIEW_REQUIRED`. They do not alter production code or design documents and do not commit. Copilot reviews every subphase report. Physical implementation subtasks are revised after investigation and before implementation begins.

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

### Ratified physical implementation plan

#### Phase III-001: FE contract and controller pattern

Create `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/accountbalance/AccountBalanceController.java` following `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/contribution/ContributionController.java`, specifically `ContributionController#getContributionSummary`. Create `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AccountBalanceControllerTest.java` following `ContributionControllerTest`. Tests cover shared-filter rejection of missing or malformed `X-Request-Id`; successful missing/unsupported `Accept-Language` with English fallback; selected-account context behavior for missing or invalid `Account-Ref`; absence of `Idempotency-Key`; optional currency; and generic success beneath `result.data`.

#### Phase III-002: context and application query

Use `CurrentPortalAccessContextResolver.current()` and map trusted actor/account fields into the Account Balance query. Do not validate or carry raw `Account-Ref` in the controller, command, service, or adapter. Resolve ReferenceDate before the balance APIM call.

#### Phase IV visibility and serialization

Use scoped Jackson inclusion for portfolio `fxRate` and `factsheetUrl` omission while retaining explicit null overview fields. Add serialization contract tests asserting JSON property presence and absence. Do not modify global `ObjectMapper` inclusion.

#### Phase V error mapping

Use `MutationResponse`; recognized outcomes map to `BUSINESS_REJECTED`, and every other technical failure maps through the shared HTTP 503 `SYSTEM_ERROR`, `err.system.unavailable`, `type=SYSTEM` response. Remove `PAGE` and `GLOBAL` terminology as error types.

# Account Balance Implementation Plan

**Feature:** 6.14 Account Balance  
**Baseline:** 27/08/2026  
**Status:** Consolidated design baseline, pending repository investigation  

## Planning strategy

The previous seven stages were too broad. This revision separates a mandatory investigation gate from small implementation subtasks. The logical sequence is usable now, while exact files/classes and effort must be updated immediately after investigation.

## Phase I: repository investigation, no production changes

### I-001 Inbound and response conventions

Find controller patterns, generic response builders, optional query validation, and exact validation codes/targets for X-Request-Id, Accept-Language, Idempotency-Key, and Account-Ref.

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

Lock endpoint, optional currency, required shared headers, Display Account names, currency selection, visibility, and generic response shape.

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

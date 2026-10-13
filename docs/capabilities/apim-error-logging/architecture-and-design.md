## APIM Error Logging — Architecture and Design

### Document Control

| Field | Value |
|---|---|
| Document | APIM Error Logging — Architecture and Design |
| Status | Draft — pending review before implementation |
| Scope | Log-only observability for APIM outbound exchange outcomes |
| Related | [APIM Exchange Executor Architecture and Developer Guide](../../integration/apim/apim-exchange-executor-architecture-and-developer-guide.md) |

### 1. Purpose

Log every APIM outbound exchange outcome that represents a network/transport failure, a non-empty `err-message` (business rejection), or a malformed response schema — without changing the behavior, return values, or thrown-exception types of any existing adapter, mapper, or business use case.

This closes a real observability gap confirmed by investigation (scripts 002–005): today, when an adapter's envelope check fails or a downstream exception is thrown, nothing logs the event before it propagates. The only existing structured APIM logging lives inside `ApimWebClientFacade` (HTTP-status failures, token/certificate retry) and whatever eventually reaches `ApiExceptionHandler` if the exception is uncaught all the way to the controller boundary.

### 2. Investigation Findings (Summary)

- All 15 governed APIM operations, and the additional pre-existing operations outside that ledger (Account Balance, Account Gain Loss, General Metadata, Reference Date Refresh, plus the 6 identified as out-of-scope for this capability), call `ApimExchangeExecutor.postEnvelope(...)`. A repository-wide grep confirms **zero** production callers of `ApimWebClientFacade` outside the facade itself and `DefaultApimExchangeExecutor`. This makes `DefaultApimExchangeExecutor.postEnvelope` the single confirmed choke point for 100% of outbound APIM calls.
- `docs/integration/apim/operations-and-testing.md` already names an aspirational logging event vocabulary — including `apim.contract.invalid` and `apim.business.rejected` — that is not implemented anywhere in the codebase today. This capability implements those two named events for the first time, rather than inventing new naming.
- A generic schema validator, `ApimResponseValidator.requireSuccessData(envelope)`, already exists and is used by 9 adapters (the ones explicitly out of scope for this capability). It throws `ApimException(BAD_GATEWAY, APIM_RESPONSE_INVALID, errMessage)` unconditionally whenever `err-message` is non-empty — which is confirmed incorrect (see §7) — and is not used by any of the 9+ newer per-operation adapters/transports.
- Every newer per-operation adapter/transport (e.g. `SharedApimTrpChkCustIdTransport`, `TrpMemberLoginAdapter`, `ApimRegistrationLookupAdapter`, `TrpSearchMemberAdapter`, `ApimForgotPasswordDetailsLookupAdapter`, `ApimForgotPasswordCompletionAdapter`) already inspects `err-message` itself to distinguish specific recognized business codes (e.g. `err.tpa.wrong.password`, `err.tpa.member.not.found`, `err.tpa.account.locked`) from technical failure, and none of them currently log anything when this happens.

### 3. In Scope

- A new, pure, side-effect-free schema classifier implementing the confirmed malformed-schema definition (§5).
- A new logging component wrapping the `Mono` returned by `DefaultApimExchangeExecutor.postEnvelope(...)`, applied identically to every operation, that:
  - logs `apim.contract.invalid` (ERROR) when the classifier detects malformed schema;
  - logs `apim.business.rejected` (WARN) when the envelope is well-formed but `err-message` is non-empty;
  - logs `apim.exchange.failed` (ERROR) when the `Mono` completes with any error signal (network failure, timeout, crypto failure, profile resolution failure, or any other unexpected exception), regardless of cause type.
- A well-formed, non-rejected success response remains silent at INFO/WARN/ERROR (no new log line); it is already covered by the existing `ApimRequestLoggingExchangeFilter` outbound request log.
- Reuse of the existing `LoggingSanitizer` for any dynamic values placed in the log payload (consistent with every other structured log site in the codebase).
- Documentation of the confirmed `ApimResponseValidator` defect as a tracked, deferred issue (§7).

### 4. Out of Scope

- Migrating, refactoring, or touching any of the 6 pre-existing `ApimResponseValidator`-based adapters (Contribution Summary, Member Info, Notice Message, Notification Read-Status, Reference Data Countries, Update Personal Information). They keep their current behavior, including the current defect, unchanged.
- Fixing the `ApimResponseValidator.requireSuccessData` unconditional throw on non-empty `err-message` (§7). Removing it now — before the 6 adapters are migrated to per-operation interpretation — would silently change their behavior from "throw on any business rejection" to "treat every business rejection as success," since none of those 6 adapters independently inspect `err-message` today. This is a behavior change, not a logging change, and is deliberately deferred.
- Any change to routing, retry, credential resolution, encryption, or business-envelope interpretation. This capability never throws a new exception and never suppresses or replaces an existing one.
- Project-wide centralized logging spanning `ExecutionLoggingAspect`, `RequestLoggingWebFilter`, `ApiExceptionHandler`, or other non-APIM logging call sites. Deferred to the planned entire-project error-mechanism refactor.

### 5. Malformed-Schema Definition

The expected response scaffold is:

```json
{
    "response": {
        "err-message": "",
        "data": []
    }
}
```

A deserialized `ApimResponseEnvelope<D>` is classified as **malformed** if any of the following is true:

1. `response` is absent (`envelope.getResponse() == null`).
2. `response.err-message` is absent (`null`).
3. `response.data` is absent (`null`) **or present as an empty array** (`[]`) **and** `response.err-message` is empty (`""`).

> **Corrected 2026-09-25** (see section 12 below): an earlier version of this document treated a present-but-empty `data` array as a valid "no records" success when `err-message` is empty. A production incident on `TRPMemberLogin` confirmed this is wrong: APIM returned `{"response":{"err-message":"","data":[]}}`, which the caller could not interpret as a usable result and which surfaced as an unhandled `SYSTEM_ERROR`/502 with no APIM-layer log at all. This shape is now classified as malformed, matching the project's existing Postman contract-test suite, which applies this rule generically across all TRP operations.

`response.data` being absent **or empty** while `response.err-message` is **non-empty** is **not** malformed - a business rejection is not required to carry a non-empty `data` array, consistent with how every current adapter (e.g. `SharedApimTrpChkCustIdTransport.toEnvelope`) already treats this case.

Type-mismatch cases (`err-message` present but not a JSON string, `data` present but not a JSON array) cannot be observed at this classification point: Jackson deserialization fails before an `ApimResponseEnvelope` object exists, surfacing instead as a deserialization/crypto exception inside `ApimPayloadCryptoService.decryptResponseEnvelope`. These cases are covered by the generic `apim.exchange.failed` error-signal logging (§3), not by the schema classifier.

### 6. Integration Point

```
DefaultApimExchangeExecutor.postEnvelope(ApimExchangeRequest<R, D> request)
    -> Mono.defer(() -> encryption enabled ? postEncrypted(request) : postPlain(request))
    -> [NEW] outcome-logging wrapper (doOnNext / doOnError, non-mutating)
    -> returned to caller exactly as today
```

The wrapper attaches only `doOnNext` and `doOnError` side-effect hooks to the existing `Mono` chain. It does not alter emissions, does not catch or replace exceptions, and does not introduce a new exception type. Every existing adapter, mapper, and business use case continues to receive exactly the same envelope or exception it receives today.

Log payload fields (via the existing `LinkedHashMap` -> `LoggingSanitizer` -> JSON-string -> `log.x("{}", json)` convention used throughout the codebase):
- `event` (one of the three names in §3)
- `path` (`request.path()`)
- `databaseScope` (`request.databaseScope()`)
- `retryEligibility` (`request.retryEligibility()`)
- `requestId` (from Reactor context, `RequestCorrelation.REQUEST_ID_CONTEXT_KEY`, consistent with every other filter/aspect)
- for `apim.business.rejected`: sanitized `err-message` value
- for `apim.exchange.failed`: `exceptionType`, sanitized exception message
- never: raw request/response bodies, credentials, tokens, certificates, or unmasked PII (consistent with the forbidden-fields list already documented in `operations-and-testing.md` §5.2)

### 7. Known Related Defect (Tracked, Not Fixed Here)

`ApimResponseValidator.requireSuccessData` contains:

```java
if (!errMessage.isEmpty()) {
  throw new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, errMessage);
}
```

This unconditionally treats **every** non-empty `err-message` as a technical APIM failure, which is confirmed incorrect — a non-empty `err-message` is frequently a legitimate, recognized business rejection (as demonstrated by every newer per-operation mapper). This defect currently affects the 6 out-of-scope adapters only. It should be fixed when those adapters are migrated to per-operation business-code interpretation, not as part of this logging capability.

### 8. Testing Strategy and Coverage Tiers

Per the project's tiered coverage policy:

- **Schema classifier** (pure function, no Spring dependencies): >=95% instruction, >=95% branch. Test every branch of §5 individually: missing `response`, missing `err-message`, missing `data` with empty `err-message`, missing `data` with non-empty `err-message` (must be well-formed), present empty `data` array (must be well-formed), fully populated well-formed envelope.
- **Executor-level logging wrapper** (orchestration, wraps a reactive `Mono`): >=95% instruction, 85-90% branch, gated. Test: well-formed success (no new log line), malformed schema (asserts `apim.contract.invalid` at ERROR), non-empty `err-message` on a well-formed envelope (asserts `apim.business.rejected` at WARN), and an error signal from `postEnvelope` for at least one representative cause per category — `WebClientRequestException`/`TimeoutException` (network), `ApimCryptoException` (crypto), `ApimProfileResolutionException` (profile), and a generic `RuntimeException` (unexpected) — each asserting `apim.exchange.failed` at ERROR with the correct `exceptionType`.
- **No regression tests**: existing adapter, mapper, and executor tests (e.g. `ApimResponseValidatorTest`, `ApimAdapterEnvelopeContractTest`) must remain green unmodified, proving no behavior change.
- Mutation testing is recommended but not gated for the new classifier and wrapper, consistent with the project's stated policy for high-risk/pure classes.

### 9. Validation Commands

```
export JAVA_HOME="/home/nd2887/tools/jdk-21.0.12.1+1"
export M2_HOME="/home/nd2887/tools/apache-maven"
export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH"
mvn -DskipTests compile
mvn -DskipTests test-compile
mvn -Dtest=<explicit-focused-test-list> test
mvn spotless:apply
mvn test
```

Full-suite validation is required before merge, consistent with the architecture guide's rule that shared foundation changes (this wrapper touches `DefaultApimExchangeExecutor`, used by all 15+ operations) require full-suite evidence, not just focused tests.

### 10. Approved Decisions

1. New class names: `com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimEnvelopeSchemaClassifier` (pure classifier) and `com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeOutcomeLogger` (logging wrapper). Approved.
2. A well-formed, non-rejected success response remains silent (no new log line). Approved.
3. Log level for `apim.business.rejected` is `WARN`. Approved.

### 11. Implementation Notes (Post-Approval Design Addition)

During pre-implementation wiring verification (script 008), it was confirmed that
`DefaultApimExchangeExecutorTest` constructs `DefaultApimExchangeExecutor` directly via its
original 3-argument constructor in 10 test methods. Adding a 4th constructor parameter
(`ApimExchangeOutcomeLogger`) as a single replacement constructor would have broken test
compilation for that entire pre-existing file.

**Resolution:** `DefaultApimExchangeExecutor` retains its original 3-argument constructor
unmodified in behavior, and gains a new `@Autowired` 4-argument constructor used by Spring in
production. The retained 3-argument constructor delegates to the 4-argument one, constructing a
real (not no-op) `ApimExchangeOutcomeLogger` with a fresh `ObjectMapper`/`LoggingSanitizer` -
mirroring the exact compatibility-constructor convention already established by
`ApimWebClientFacade` elsewhere in this codebase. This required zero changes to the pre-existing
`DefaultApimExchangeExecutorTest`.

The outcome-logging hook itself is attached via a single `doOnEach` operator on the `Mono` chain
already returned by `postEnvelope` - a non-mutating "peek" operator that cannot alter the emitted
envelope or propagated exception, verified by a new companion test class,
`DefaultApimExchangeExecutorOutcomeLoggingTest`.

**Formatting note:** `mvn spotless:apply` was intentionally NOT run as part of this
implementation script. `origin/dev` at the time of this branch's creation already contains
pre-existing spotless-formatting drift unrelated to this feature (confirmed directly: an earlier
attempt to run `spotless:apply` reformatted 70 unrelated test files repository-wide and had to be
reverted). Running the mutating formatter is deferred to the feature-level stabilization script,
consistent with the project's established convention for other features, so that any repository-
wide formatting drift is handled as its own separate, explicit decision rather than silently
riding along with this feature's diff.


### 12. Correction: Empty Data Array Is Malformed (Production Incident)

**Date:** 2026-09-25
**Trigger:** A production login attempt (`TRPMemberLogin`, request ID `9381f5fd-8c87-4b73-95b7-16ff21c4f451`) received `{"response":{"err-message":"","data":[]}}` from APIM. This was classified as `SUCCESS` by the original classifier (section 5, pre-correction) and therefore intentionally logged nothing. The request subsequently failed with HTTP 502 / `SYSTEM_ERROR` inside downstream business-envelope interpretation, with **no APIM-layer log line at all** to explain why - exactly the observability gap this capability exists to close.

**Root cause of the original classifier's error:** section 5 (pre-correction) treated `data: []` (present, empty) as equivalent to a legitimate "no records found" success response, based on a Javadoc comment in the legacy `ApimResponseValidator` (a class already confirmed to contain an unrelated defect, section 7). That assumption was never independently verified against TAS's real contract.

**Correcting evidence:** the project's existing Postman collection contains a contract-test suite applied generically and identically across all TRP operations. Its logic requires that `err-message` and `data` (when `data` is present) must not both be empty, enforced regardless of operation - login, notifications, contribution history, etc.

This confirms the "empty `data` + empty `err-message`" shape is a downstream (APIM/TAS) contract violation on every operation, not a valid representation of "no records" for any operation. Genuinely empty result sets are expected to be represented by TAS some other way (e.g. a specific business `err-message` code), not by an empty `data` array with an empty `err-message`.

**Resolution:** `ApimEnvelopeSchemaClassifier` now classifies `err-message: ""` combined with `data` being either absent (`null`) or present-but-empty (`[]`) as `MALFORMED` (logged as `apim.contract.invalid` at ERROR), collapsing what were previously two separate branches (section 5, pre-correction) into one. `err-message` non-empty combined with an empty or absent `data` array remains `BUSINESS_REJECTED` (logged at WARN) - unchanged, since a business rejection is still not required to carry a `data` array.

This is a **classification/logging-only change**: it does not alter what any adapter, mapper, or business use case receives from `postEnvelope`. It only changes what gets logged and at what level for this specific envelope shape.

### 13. Response mapping failures (2026-10-13)

`ApimPayloadCryptoService` used to report every failure while reading a response as `ApimCryptoException("Failed to decrypt APIM response payload.")`, even when nothing was decrypted. A JSON-to-DTO mapping failure, such as the then-unmapped `TRPGetMemberAccBal` field `fundDtl.chin-fund-fact-sheet`, therefore looked like a crypto failure, and `apim.exchange.failed` did not show the cause.

Each step now reports its own failure:

| Step | Exception | Message |
|---|---|---|
| Read the JSON | `ApimCryptoException` | `Failed to parse APIM response payload.` |
| Decrypt configured fields | `ApimCryptoException` | `Failed to decrypt APIM response payload.` |
| Map the JSON to the response type | `ApimResponseMappingException` | `Failed to map APIM response payload.` |

With encryption disabled, a mapping failure is also `ApimResponseMappingException`; malformed or empty JSON stays a parse failure.

`apim.exchange.failed` adds two fields:

- `rootCauseType`: simple class name of the innermost cause, when it differs from the top-level exception.
- `jsonPath`: field names and array indexes of the first Jackson mapping failure in the cause chain, for example `response.data[0].fundDtl[0].chin-fund-fact-sheet`. It is built from the exception's path references, never from its message, because Jackson messages can contain response values.

This supersedes the section 5 remark that type mismatches surface as a deserialization/crypto exception, and extends the section 6 field list. `ApimCryptoFailureClassifier` treats `ApimResponseMappingException` like `ApimCryptoException`, so callers that turn a crypto failure into an unknown outcome keep the same result. No HTTP status, public error code or retry behaviour changes.

# 07 - APIM Error Classification Design (Final)

Sub-task 2b.1, final correction pass. Docs only (`docs/design/15-exception-handling/07-apim-error-classification.md`), no production or test change. This replaces the version committed at `6494b53a01871f027217062f1c0b6f8e6d216e5e` (new commit, not an amend). Every row is assigned exactly one `FailureOrigin`; no row is counted in more than one category, and no "mixed"/"classify by cause" category is used -- every catch-all condition is split into one sub-row per distinguishable cause, each with its own single origin and the **same** public status/body as the undivided catch-all (per section 3: "the public exception, status and body stay unchanged").

## Corrections applied in this final pass

1. **`ApiError.downstreamSystem()` callers (resolved, not UNVERIFIED):** an unfiltered grep across `src/main/java` and `src/test/java` found exactly two matches, both in test files -- `ErrorResponseRendererTest.java:89` and `FrontendOwnedLocalizationMessageFreeErrorsTest.java:42`. **Zero production callers.** The factory is genuinely unused in production code today.
2. **`RegistrationLookupDownstreamException.downstreamErrorCode()` (resolved):** a simple accessor returning the trimmed downstream error code, or `null` if blank/absent (`RegistrationLookupDownstreamException.java:25-27`).
3. **`ApimEnvelopeSchemaClassifier`'s exact rule (resolved):** a pure, side-effect-free classifier (`ApimEnvelopeSchemaClassifier.java`) used only to decide what `ApimExchangeOutcomeLogger` logs, never to change adapter/business behavior. Rule: `response.err-message` missing -> malformed; non-empty `err-message` -> business-rejected; empty `err-message` with missing/empty `data` -> malformed (this exact empty/empty combination was confirmed by a cited production incident on TRPMemberLogin, per the class Javadoc); otherwise -> success.
4. **`@LogExecution`/`ExecutionLoggingAspect` (resolved):** two Spring AOP pointcuts exist -- one for methods explicitly annotated `@LogExecution`, and a second, broader pointcut on **every** `com.bct.ngtpa.apiservice.application.usecase..*Service.execute(..)` method, applied automatically with no annotation required. On failure both log `method.execution.error` at **ERROR with the full stack trace** (`ExecutionLoggingAspect.java:169`, `log.error("{}", toJson(event), throwable)`). Ten classes are confirmed `@LogExecution`-annotated, including several adapters already in this inventory (`ApimRegistrationLookupAdapter`, `ApimRegistrationCompletionAdapter`, `ApimForgotPasswordCompletionAdapter`, `ApimUpdatePersonalInformationAdapter`, and others). This is a previously-undocumented extra ERROR-with-stack-trace log line for every row whose adapter class is in that confirmed list; each such row is annotated accordingly below. Whether the specific annotated method matches the exact method documented in each row was not confirmed line-by-line.
5. **`WorkflowErrorType.DOWNSTREAM_SYSTEM`'s 4 call sites (partially resolved):** full source of all four call sites was read. `RegistrationStartWebMapper.java:207` and `RegistrationOtpResendWebMapper.java:142` both map `MESSAGE_DELIVERY_FAILED` status to `WorkflowErrorType.DOWNSTREAM_SYSTEM`; `RegistrationOtpVerifyWebMapper.java:144` maps `TRUST_LOOKUP_FAILED`; `CheckRegistrationUsernameService.java:306` constructs it directly for a `DOWNSTREAM_FAILED` status from the username-availability port. **Still UNVERIFIED:** the renderer that converts `WorkflowErrorDescriptor`/`WorkflowErrorType` into the final public JSON (`WorkflowMutationResponseMapper`/`RegistrationWorkflowMutationResponseMapper`) was not read this pass, so whether `DOWNSTREAM_SYSTEM` literally appears in the rendered JSON `type` field, or is translated to something else first, is not confirmed.
6. **`TrpMemberLoginAdapter`'s and `TrpGetAccountListAdapter`'s prior-log status (resolved):** both adapters' transports (`SharedApimTrpMemberLoginTransport`, `SharedApimTrpGetAccountListTransport`) call `executor.postEnvelope(exchange)` directly -- the identical executor path used by every other adapter in this inventory. Both swallow sites are therefore confirmed to have `apim.exchange.failed` ERROR logged before the swallow, by the same structural guarantee established for the Forgot Password crypto-failure and Username Recovery rows.
7. **`IdempotencyExecutor`'s handling of `RegistrationCompletionPreDispatchException` (resolved):** `DefaultIdempotencyExecutor.classifyProtectedFailureThenError` always re-throws the original failure after idempotency-state bookkeeping (`persistence.fail(...)` or `markUnknownThenError`), regardless of classification result. The exception is never swallowed; it propagates out of `IdempotencyExecutor.execute()` unchanged. One fact remains genuinely unconfirmed: `CatchAllExceptionMapping`'s exact public HTTP status/`ApiStatus` was not read this pass.
8. **`ForgotPasswordDetailsOtpProtectedOperation` (corrected, not the consumer):** its full source was read this pass and it never references `ForgotPasswordDetailsDownstreamException`. The true catch site is most likely `ForgotPasswordUnderLockDetailsVerifier` (a collaborator field on this class), which was not read this pass -- a concrete, scoped UNVERIFIED reason, not a deferred guess.
9. **`ApimExceptionMappings.java:61`, the main public DOWNSTREAM exposure site (section 6):** confirmed as the literal `"DOWNSTREAM_SYSTEM"` string passed into `ErrorDescriptor.single` for `upstream()`, affecting every row in this table whose public error code/type cites `ApimExceptionMappings.java:49-69` -- the Forgot Password username-lookup, completion (missing-envelope and transport sub-causes), Personal Information, both Reference Date rows, and the shared 8-adapter validator group. That is the majority of generic-`ApimException` rows in this table and the single highest-traffic exposure path.
10. **Catch-all rows split (no "mixed"/"classify by cause" category remains):** every `systemFailure()` catch-all (Account Balance, Account Gain/Loss, Fund Price, Fund Comparison, and **Fund Fact Sheet**, which was missing from the prior pass's catch-all list) is split into per-cause sub-rows below, each with exactly one `FailureOrigin` and the same public status/body as the undivided catch-all. `TrpGetAccountListAdapter`'s `onErrorReturn` and `ApimForgotPasswordCompletionAdapter`'s `safeFailure` are similarly split.
11. **Registration `NOT_ELIGIBLE` is `DOWNSTREAM_BUSINESS` only** (not double-counted with `DOWNSTREAM_CONTRACT`); the prior pass's per-category row counts were computed by a grep pattern that could cross-match prose in other sections. This pass computes category counts directly from the row data structure used to generate the table, not from any post-hoc text search.

## 1. Inventory table (37 rows)

One row per downstream condition (or, for catch-all handlers, one row per distinguishable cause) per adapter. Each row states: Condition / Detection site / Handling / Message policy / Ordered log lines (event, level, stack trace, file:line) / Idempotency replay / Origin tag today / Proposed `FailureOrigin` (exactly one, per section 3) / Public HTTP status / Public `ApiStatus` / Public error code or type / Citation.

### Registration -- ApimRegistrationLookupAdapter (TRPSearchMember)

- **Condition:** `err.tpa.member.not.found` -> `NOT_ELIGIBLE`
- **Detection site:** `registration/mapper/TrpSearchMemberApimResultMapper.toResult`, lines 22-24
- **Handling:** Returned as a normal `RegistrationLookupResult(NOT_ELIGIBLE, ...)`; `ApimRegistrationLookupAdapter.withFailureLogging`'s `doOnEach` calls `recordDownstreamRejection` (`ApimRegistrationLookupAdapter.java:101`), publishing a `FailureEvent` (`ApimRegistrationLookupAdapter.java:147`)
- **Message policy:** N/A -- successful application result, never an `ExceptionMapping`
- **Ordered log lines:** (0) possible earlier classifier log, UNVERIFIED (see corrections); (1) `FailureEvent` published, logged ERROR by `StructuredFailureEventLogger` today (origin not in `WARN_ORIGINS`) -- E1 moves this to WARN. ExecutionLoggingAspect also fires on this class (confirmed present via `@LogExecution` grep, `ExecutionLoggingAspect.java`): on failure it logs `method.execution.error` at ERROR **with the full stack trace** (`log.error("{}", toJson(event), throwable)`, `ExecutionLoggingAspect.java:169`). Whether the specific annotated method is the exact one in this row was not confirmed line-by-line this pass -- only that the class file contains at least one `@LogExecution`-annotated method. (class confirmed `@LogExecution`-annotated: `ApimRegistrationLookupAdapter.java`)
- **Idempotency replay:** No -- lookup is called directly, not itself idempotency-wrapped
- **Origin tag today:** `FailureOrigin.DOWNSTREAM_CONTRACT` (`ApimRegistrationLookupAdapter.java:121`)
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_BUSINESS` (E1 names this exact row for the ERROR-to-WARN exception; this row is `DOWNSTREAM_BUSINESS` only, never `DOWNSTREAM_CONTRACT`)
- **Public HTTP status:** 422 (FailureEvent's own logging-only field, `ApimRegistrationLookupAdapter.java:156`)
- **Public ApiStatus:** n/a at this layer (normal 200 application result)
- **Public error code/type:** `err.tpa.member.not.found` / `RegistrationErrorKey.START_VERIFICATION_FAILED`
- **Citation:** `ApimRegistrationLookupAdapter.java:144-159`

### Registration -- ApimRegistrationLookupAdapter (TRPSearchMember)

- **Condition:** `EMPTY_DATA` / `INVALID_ACCOUNT_DATA` / `UNEXPECTED_ERROR` reasons
- **Detection site:** `registration/mapper/TrpSearchMemberApimResultMapper.toResult`, lines 25-28, 30-33, 39-42
- **Handling:** Throws `RegistrationLookupDownstreamException`, mapped by `RegistrationExceptionMappings.lookupDownstream()` (`RegistrationExceptionMappings.java:18-44`)
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** (0) possible earlier classifier log, UNVERIFIED; (1) `FailureEvent`, origin `DOWNSTREAM_CONTRACT`, ERROR; (2) `http.api.error` ERROR, no stack trace (`RegistrationExceptionMappings.java:35`, final arg `false`). ExecutionLoggingAspect also fires on this class (confirmed present via `@LogExecution` grep, `ExecutionLoggingAspect.java`): on failure it logs `method.execution.error` at ERROR **with the full stack trace** (`log.error("{}", toJson(event), throwable)`, `ExecutionLoggingAspect.java:169`). Whether the specific annotated method is the exact one in this row was not confirmed line-by-line this pass -- only that the class file contains at least one `@LogExecution`-annotated method.
- **Idempotency replay:** No
- **Origin tag today:** `FailureOrigin.DOWNSTREAM_CONTRACT` (`ApimRegistrationLookupAdapter.java:121`)
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value()` (`RegistrationExceptionMappings.java:31-32`)
- **Citation:** `RegistrationExceptionMappings.java:18-44`

### Registration -- ApimRegistrationCompletionAdapter (TRPRegMember)

- **Condition:** `ApimProfileResolutionException` during completion (our own profile-routing configuration)
- **Detection site:** `ApimRegistrationCompletionAdapter.java:54-55`
- **Handling:** `.onErrorMap(ApimProfileResolutionException.class, RegistrationCompletionPreDispatchException::new)`. **Resolved this pass (was UNVERIFIED):** `DefaultIdempotencyExecutor.classifyProtectedFailureThenError` classifies the failure via `command.failureClassifier().classify(failure)` (`SharedCompleteRegistrationService.java:163-166`, `PROVEN_NO_SIDE_EFFECT` for this exact type). Regardless of classification result, `DefaultIdempotencyExecutor` **always re-throws the original failure** after idempotency-state bookkeeping -- `persistence.fail(...).then(Mono.error(failure))` for `PROVEN_NO_SIDE_EFFECT`/other-non-null classifications, or `markUnknownThenError` otherwise (`DefaultIdempotencyExecutor.java:104-125`). The exception is **not swallowed**; it propagates out of `IdempotencyExecutor.execute()` unchanged. Since `RegistrationCompletionPreDispatchException` is absent from `ExceptionMappingRegistryConfig`'s dedicated list, it resolves via `CatchAllExceptionMapping` if nothing else intercepts it between `SharedCompleteRegistrationService` and the web layer.
- **Message policy:** N/A until it reaches `CatchAllExceptionMapping`
- **Ordered log lines:** Re-thrown from `DefaultIdempotencyExecutor` unchanged; no additional log line confirmed at that layer this pass. ExecutionLoggingAspect also fires on this class (confirmed present via `@LogExecution` grep, `ExecutionLoggingAspect.java`): on failure it logs `method.execution.error` at ERROR **with the full stack trace** (`log.error("{}", toJson(event), throwable)`, `ExecutionLoggingAspect.java:169`). Whether the specific annotated method is the exact one in this row was not confirmed line-by-line this pass -- only that the class file contains at least one `@LogExecution`-annotated method. (class confirmed `@LogExecution`-annotated: `ApimRegistrationCompletionAdapter.java`)
- **Idempotency replay:** Yes -- Registration complete is idempotency-protected
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.LOCAL_SYSTEM`
- **Public HTTP status:** **UNVERIFIED (one remaining fact):** `CatchAllExceptionMapping`'s exact HTTP status/`ApiStatus`/code was not read this pass, so the final public shape this resolves to is not confirmed, though the propagation path up to that point is now fully confirmed (not swallowed).
- **Public ApiStatus:** n/a (see above)
- **Public error code/type:** n/a (see above)
- **Citation:** `ApimRegistrationCompletionAdapter.java:54-55`; `DefaultIdempotencyExecutor.java:104-144`; `SharedCompleteRegistrationService.java:162-166`

### Forgot Password -- ApimForgotPasswordUsernameLookupAdapter / SharedApimTrpChkCustIdTransport (TRPChkCustId)

- **Condition:** empty response / malformed data items
- **Detection site:** direct `throw new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, ...)`
- **Handling:** mapped via `ApimExceptionMappings.upstream()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.exchange.failed` ERROR if raised downstream of `postEnvelope`; `http.api.error` ERROR, no stack trace
- **Idempotency replay:** Yes -- participates in Forgot Password start
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 502 (`exception.getStatusCode()`, `ApimExceptionMappings.java:59`)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` (`ApimExceptionMappings.java:61`) / `APIM_RESPONSE_INVALID`
- **Citation:** `ApimExceptionMappings.java:49-69`

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)

- **Condition:** missing response envelope
- **Detection site:** `ApimForgotPasswordCompletionAdapter.java:64-68`
- **Handling:** `throw new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, ...)`, mapped via `ApimExceptionMappings.upstream()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.exchange.failed` ERROR; `http.api.error` ERROR, no stack trace. ExecutionLoggingAspect also fires on this class (confirmed present via `@LogExecution` grep, `ExecutionLoggingAspect.java`): on failure it logs `method.execution.error` at ERROR **with the full stack trace** (`log.error("{}", toJson(event), throwable)`, `ExecutionLoggingAspect.java:169`). Whether the specific annotated method is the exact one in this row was not confirmed line-by-line this pass -- only that the class file contains at least one `@LogExecution`-annotated method. (class confirmed `@LogExecution`-annotated: `ApimForgotPasswordCompletionAdapter.java`)
- **Idempotency replay:** Yes -- Forgot Password complete
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 502
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `APIM_RESPONSE_INVALID`
- **Citation:** `ApimForgotPasswordCompletionAdapter.java:62-72`

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)

- **Condition:** crypto failure (`ApimCryptoException`/`ApimResponseMappingException`, `isCryptoFailure` true)
- **Detection site:** `ApimPayloadCryptoService.decryptResponseFields`, line 164; classifier `ApimCryptoFailureClassifier.java:15-16`. Confirmed sole caller of `isCryptoFailure` (`ApimForgotPasswordCompletionAdapter.java:57`)
- **Handling:** `.onErrorResume(ApimCryptoFailureClassifier::isCryptoFailure, e -> Mono.just(unknownOutcome()))`
- **Message policy:** N/A -- swallowed before any `ExceptionMapping`
- **Ordered log lines:** `apim.exchange.failed` ERROR fires via `doOnEach` on the upstream `postEnvelope()` Mono **before** this downstream `onErrorResume` swallows it (confirmed ordering: `DefaultApimExchangeExecutor.java:60-69`). ExecutionLoggingAspect also fires on this class (confirmed present via `@LogExecution` grep, `ExecutionLoggingAspect.java`): on failure it logs `method.execution.error` at ERROR **with the full stack trace** (`log.error("{}", toJson(event), throwable)`, `ExecutionLoggingAspect.java:169`). Whether the specific annotated method is the exact one in this row was not confirmed line-by-line this pass -- only that the class file contains at least one `@LogExecution`-annotated method.
- **Idempotency replay:** Yes (Forgot Password complete)
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- **Public HTTP status:** n/a -- swallowed to success-shaped `unknownOutcome()`
- **Public ApiStatus:** n/a
- **Public error code/type:** n/a
- **Citation:** `ApimPayloadCryptoService.java:158-166`; `ApimCryptoFailureClassifier.java:11-18`; `ApimForgotPasswordCompletionAdapter.java:52-58`

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)

- **Condition:** `safeFailure` catch-all, sub-cause: transport failure (`WebClientRequestException`/timeout/5xx pattern, after retries exhausted per `ApimWebClientFacade.isTransientBusinessFailure`)
- **Detection site:** `ApimForgotPasswordCompletionAdapter.java:74-81`
- **Handling:** `.onErrorMap(this::safeFailure)` -> `ApimException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.SYSTEM_UNEXPECTED, ..., t)`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.exchange.failed` ERROR; `http.api.error` ERROR, no stack trace. ExecutionLoggingAspect also fires on this class (confirmed present via `@LogExecution` grep, `ExecutionLoggingAspect.java`): on failure it logs `method.execution.error` at ERROR **with the full stack trace** (`log.error("{}", toJson(event), throwable)`, `ExecutionLoggingAspect.java:169`). Whether the specific annotated method is the exact one in this row was not confirmed line-by-line this pass -- only that the class file contains at least one `@LogExecution`-annotated method.
- **Idempotency replay:** Yes
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** **500, not 502** (`ApimExceptionMappings.java:59` forwards `getStatusCode()`; this constructor used `HttpStatus.INTERNAL_SERVER_ERROR`)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `SYSTEM_UNEXPECTED`
- **Citation:** `ApimForgotPasswordCompletionAdapter.java:74-81`

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)

- **Condition:** `safeFailure` catch-all, sub-cause: any other non-`ApimException` runtime failure not matching a known transient-transport pattern (a genuine local defect in our own code, not a downstream fault)
- **Detection site:** `ApimForgotPasswordCompletionAdapter.java:74-81`
- **Handling:** same `.onErrorMap(this::safeFailure)` path as the row above; this sub-cause is not structurally distinguished from the transport sub-cause by the current code (both fall into the same catch)
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.exchange.failed` ERROR (if it passed through `postEnvelope`) or none (if it is a purely local exception never touching the executor); `http.api.error` ERROR, no stack trace
- **Idempotency replay:** Yes
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.LOCAL_SYSTEM`
- **Public HTTP status:** 500
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `SYSTEM_UNEXPECTED`
- **Citation:** `ApimForgotPasswordCompletionAdapter.java:74-81` (same code path as the transport sub-cause; the current implementation does not distinguish the two causes at the catch site -- see section 8)

### Forgot Password -- ApimForgotPasswordDetailsLookupAdapter (TRPSearchMember)

- **Condition:** missing envelope
- **Detection site:** `ForgotPasswordTrpSearchMemberMapper.java:46`; `ApimForgotPasswordDetailsLookupAdapter.java:51-52`
- **Handling:** `throw new ForgotPasswordDetailsDownstreamException(Reason.UNEXPECTED_ERROR)`. **Corrected this pass:** the consumer is **not** `ForgotPasswordDetailsOtpProtectedOperation` (its full source was read this pass and it never references `ForgotPasswordDetailsDownstreamException` at all -- it only verifies details already resolved by a separate `ForgotPasswordUnderLockDetailsVerifier` collaborator). The true catch site is most likely inside `ForgotPasswordUnderLockDetailsVerifier` or the adapter's own lookup caller, neither of which was read this pass.
- **Message policy:** N/A (not yet confirmed to reach an `ExceptionMapping`)
- **Ordered log lines:** **UNVERIFIED, concrete reason:** `ForgotPasswordUnderLockDetailsVerifier`'s source (the actual collaborator that would call the lookup adapter and classify its exceptions) was not read this pass; static reading could resolve this with that one additional file.
- **Idempotency replay:** Yes -- Forgot Password details/OTP is idempotency-protected
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** UNVERIFIED (same reason as above)
- **Public ApiStatus:** UNVERIFIED
- **Public error code/type:** UNVERIFIED
- **Citation:** `ForgotPasswordTrpSearchMemberMapper.java:46`; `ApimForgotPasswordDetailsLookupAdapter.java:51-52`; `ForgotPasswordDetailsOtpProtectedOperation.java:1-376` (confirmed NOT the consumer)

### Username Recovery -- TrpSearchMemberAdapter (TRPSearchMember)

- **Condition:** `TrpSearchMemberApimDownstreamException` (unrecognized non-blank err-message)
- **Detection site:** `usernamerecovery/mapper/TrpSearchMemberApimResultMapper.java:47-50`
- **Handling:** `.onErrorResume(TrpSearchMemberApimDownstreamException.class, e -> Mono.just(unknownOutcome()))` (`TrpSearchMemberAdapter.java:54-55`)
- **Message policy:** N/A -- swallowed; absent from `ExceptionMappingRegistryConfig`
- **Ordered log lines:** `apim.exchange.failed` ERROR fires before the swallow (same `doOnEach`-before-`onErrorResume` ordering)
- **Idempotency replay:** Yes -- Username Recovery request
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** n/a -- swallowed to success-shaped `unknownOutcome()`
- **Public ApiStatus:** n/a
- **Public error code/type:** n/a
- **Citation:** `usernamerecovery/mapper/TrpSearchMemberApimResultMapper.java:39-56`; `TrpSearchMemberAdapter.java:48-56`

### Username Recovery -- TrpSearchMemberAdapter (TRPSearchMember)

- **Condition:** any `ApimException` (this adapter does not call `ApimResponseValidator`, so this branch catches only facade-raised, HTTP-error-response `ApimException`s)
- **Detection site:** generic catch
- **Handling:** `.onErrorResume(ApimException.class, e -> Mono.just(unavailable()))` (`TrpSearchMemberAdapter.java:56`)
- **Message policy:** N/A -- swallowed
- **Ordered log lines:** `ApimWebClientFacade.handleErrorBody`'s `log.error` (`ApimWebClientFacade.java:409`, gated by `statusCode.isError()` line 386) fires for the underlying HTTP-error response; `apim.exchange.failed` ERROR also fires before the swallow
- **Idempotency replay:** Yes
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** n/a -- swallowed to success-shaped `unavailable()`
- **Public ApiStatus:** n/a
- **Public error code/type:** n/a
- **Citation:** `TrpSearchMemberAdapter.java:48-56`; `ApimWebClientFacade.java:380-416`

### Login -- TrpMemberLoginAdapter (TRPMemberLogin)

- **Condition:** `IllegalStateException` (envelope path)
- **Detection site:** `TrpMemberLoginAdapter.java:37-39`. **Resolved this pass (was UNVERIFIED):** `SharedApimTrpMemberLoginTransport.post` is literally `return executor.postEnvelope(exchange);` (`SharedApimTrpMemberLoginTransport.java:44`) -- the exact same executor path as every other adapter. `TrpMemberLoginAdapter.mapEnvelope` itself never throws (it returns `mapper.failure(UNAVAILABLE)` directly for a null envelope/response, `TrpMemberLoginAdapter.java:44-46`), so this `IllegalStateException` must originate inside `executor.postEnvelope()`'s own Mono chain. Its exact throw statement was not pinned down this pass, but since `transport.post() == executor.postEnvelope(exchange)`, the structural guarantee established for every other swallow site applies here too: any error in that Mono chain is observed by the outcome logger's `doOnEach` before this downstream `.onErrorResume` can swallow it.
- **Handling:** `.onErrorResume(IllegalStateException.class, ignored -> Mono.just(mapper.failure(UNAVAILABLE)))` (`TrpMemberLoginAdapter.java:37-39`)
- **Message policy:** N/A -- swallowed
- **Ordered log lines:** `apim.exchange.failed` ERROR (or `apim.contract.invalid`, depending on `ApimEnvelopeSchemaClassifier`'s result) fires before the swallow, confirmed by the structural guarantee above
- **Idempotency replay:** Yes -- Login start
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** n/a -- swallowed to `UNAVAILABLE`, handled by `LoginStartWebMapper`
- **Public ApiStatus:** n/a
- **Public error code/type:** n/a
- **Citation:** `TrpMemberLoginAdapter.java:30-40`; `SharedApimTrpMemberLoginTransport.java:27-45`

### Login -- TrpGetAccountListAdapter (TRPGetAccountList)

- **Condition:** any error, sub-cause: facade-raised HTTP-error `ApimException`
- **Detection site:** `TrpGetAccountListAdapter.java:29-32`. **Resolved this pass (was UNVERIFIED):** `SharedApimTrpGetAccountListTransport.post` is literally `return executor.postEnvelope(exchange);` (`SharedApimTrpGetAccountListTransport.java:47`) -- same executor path.
- **Handling:** `.onErrorReturn(LoginAccountAuthorizationResult.unavailable())` (`TrpGetAccountListAdapter.java:32`)
- **Message policy:** N/A -- `onErrorReturn` provides no access to the throwable, so no log can be written at this exact statement
- **Ordered log lines:** `ApimWebClientFacade.handleErrorBody`'s `log.error` fires for the HTTP-error response; `apim.exchange.failed` ERROR also fires before the swallow
- **Idempotency replay:** No -- not found under any idempotency package
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** n/a -- swallowed to `unavailable()`, handled by `LoginHttpResponses`
- **Public ApiStatus:** n/a
- **Public error code/type:** n/a
- **Citation:** `TrpGetAccountListAdapter.java:26-33`; `SharedApimTrpGetAccountListTransport.java:16-48`

### Login -- TrpGetAccountListAdapter (TRPGetAccountList)

- **Condition:** any error, sub-cause: crypto/mapping failure (`ApimCryptoException`/`ApimResponseMappingException`)
- **Detection site:** same `onErrorReturn` catch-all; same confirmed executor path as the row above
- **Handling:** `.onErrorReturn(LoginAccountAuthorizationResult.unavailable())`
- **Message policy:** N/A
- **Ordered log lines:** `apim.exchange.failed` ERROR fires before the swallow (same structural guarantee)
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- **Public HTTP status:** n/a -- swallowed to `unavailable()`
- **Public ApiStatus:** n/a
- **Public error code/type:** n/a
- **Citation:** `TrpGetAccountListAdapter.java:26-33`; `SharedApimTrpGetAccountListTransport.java:16-48`

### Account Balance -- ApimAccountBalanceSourceAdapter (TRPGetMemberAccBal)

- **Condition:** `err.tpa.account.invalid` -> `ACCOUNT_NOT_FOUND`
- **Detection site:** `ApimAccountBalanceSourceAdapter.java:54-55`
- **Handling:** `AccountBalanceRetrievalException.accountNotFound()`, mapped via `AccountBalanceExceptionMappings.retrievalFailure()` delegating to `AccountBalanceRetrievalResponsePolicy.map()` (`AccountBalanceExceptionMappings.java:40`)
- **Message policy:** `MessagePolicy.NONE` (`AccountBalanceExceptionMappings.java:47`, forced for every reason)
- **Ordered log lines:** `logAtError = exception.reason() == SYSTEM_FAILURE` (`AccountBalanceExceptionMappings.java:49-50`) -- WARN for this reason (not `SYSTEM_FAILURE`); `http.api.error`, no stack trace
- **Idempotency replay:** No -- read-only query
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_BUSINESS`
- **Public HTTP status:** 422 (`AccountBalanceRetrievalResponsePolicy.java:26-32`, `HttpStatus.UNPROCESSABLE_ENTITY`)
- **Public ApiStatus:** `BUSINESS_REJECTED`
- **Public error code/type:** `ApiError.business("err.ab.account.not.exist", null, [], "SERVER")` (`AccountBalanceRetrievalResponsePolicy.java:15,26-31`)
- **Citation:** `AccountBalanceRetrievalResponsePolicy.java:13-24`; `ApimAccountBalanceSourceAdapter.java:54-55`

### Account Balance -- ApimAccountBalanceSourceAdapter (TRPGetMemberAccBal)

- **Condition:** `err.tpa.member.not.found` -> `ACCOUNT_MISMATCH`
- **Detection site:** `ApimAccountBalanceSourceAdapter.java:56-57`
- **Handling:** `AccountBalanceRetrievalException.accountMismatch()`, same policy/mapping path as above
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** WARN (`reason != SYSTEM_FAILURE`); `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_BUSINESS`
- **Public HTTP status:** 422
- **Public ApiStatus:** `BUSINESS_REJECTED`
- **Public error code/type:** `ApiError.business("err.ab.account.mismatch", null, [], "SERVER")` (`AccountBalanceRetrievalResponsePolicy.java:16,26-31`)
- **Citation:** `AccountBalanceRetrievalResponsePolicy.java:13-24`; `ApimAccountBalanceSourceAdapter.java:56-57`

### Account Balance -- ApimAccountBalanceSourceAdapter (TRPGetMemberAccBal)

- **Condition:** catch-all `systemFailure()`, sub-cause: unrecognized non-blank error (not `err.tpa.account.invalid`/`err.tpa.member.not.found`)
- **Detection site:** `ApimAccountBalanceSourceAdapter.java:58-67` (inner) / lines 69-73 (outer `onErrorMap`)
- **Handling:** `AccountBalanceRetrievalException.systemFailure()` (same public exception, status and body for every sub-cause of this catch-all; classified here by cause per section 3 -- unrecognized non-blank err-message rule applies)
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `logAtError = true` (`reason == SYSTEM_FAILURE`) -> ERROR; `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503 (`AccountBalanceRetrievalResponsePolicy.java:17-22`, `HttpStatus.SERVICE_UNAVAILABLE`)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, "SERVER")` (`AccountBalanceRetrievalResponsePolicy.java:21`) -- uses the `"SYSTEM"` `ApiError.type` literal, not `"DOWNSTREAM_SYSTEM"`
- **Citation:** `AccountBalanceRetrievalResponsePolicy.java:13-24`; `ApimAccountBalanceSourceAdapter.java:58-73`

### Account Balance -- ApimAccountBalanceSourceAdapter (TRPGetMemberAccBal)

- **Condition:** catch-all `systemFailure()`, sub-cause: `RuntimeException` from the inner try/catch around `requireSingleData(requireSuccessData(...))` (missing/empty data on an otherwise successful exchange)
- **Detection site:** `ApimAccountBalanceSourceAdapter.java:58-67` (inner) / lines 69-73 (outer `onErrorMap`)
- **Handling:** `AccountBalanceRetrievalException.systemFailure()` (same public exception, status and body for every sub-cause of this catch-all; classified here by cause per section 3 -- `ApimResponseValidator`-style callers are always `DOWNSTREAM_CONTRACT` per the explicit rule)
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `logAtError = true` (`reason == SYSTEM_FAILURE`) -> ERROR; `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503 (`AccountBalanceRetrievalResponsePolicy.java:17-22`, `HttpStatus.SERVICE_UNAVAILABLE`)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, "SERVER")` (`AccountBalanceRetrievalResponsePolicy.java:21`) -- uses the `"SYSTEM"` `ApiError.type` literal, not `"DOWNSTREAM_SYSTEM"`
- **Citation:** `AccountBalanceRetrievalResponsePolicy.java:13-24`; `ApimAccountBalanceSourceAdapter.java:58-73`

### Account Balance -- ApimAccountBalanceSourceAdapter (TRPGetMemberAccBal)

- **Condition:** catch-all `systemFailure()`, sub-cause: transport-level failure bypassing the inner try/catch entirely, reaching the outer `.onErrorMap` catch-all (lines 69-73)
- **Detection site:** `ApimAccountBalanceSourceAdapter.java:58-67` (inner) / lines 69-73 (outer `onErrorMap`)
- **Handling:** `AccountBalanceRetrievalException.systemFailure()` (same public exception, status and body for every sub-cause of this catch-all; classified here by cause per section 3 -- HTTP 5xx/timeout/`WebClientRequestException` rule applies)
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `logAtError = true` (`reason == SYSTEM_FAILURE`) -> ERROR; `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** 503 (`AccountBalanceRetrievalResponsePolicy.java:17-22`, `HttpStatus.SERVICE_UNAVAILABLE`)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, "SERVER")` (`AccountBalanceRetrievalResponsePolicy.java:21`) -- uses the `"SYSTEM"` `ApiError.type` literal, not `"DOWNSTREAM_SYSTEM"`
- **Citation:** `AccountBalanceRetrievalResponsePolicy.java:13-24`; `ApimAccountBalanceSourceAdapter.java:58-73`

### Account Gain/Loss -- ApimAccountGainLossSourceAdapter (TRPGetInvestGL)

- **Condition:** `err.tpa.fund.switching.in.progress`
- **Detection site:** adapter line ~74 (reference-confirmed)
- **Handling:** `AccountGainLossRetrievalException.fundSwitchingInProgress()`, mapped via `AccountGainLossExceptionMappings.retrievalFailure()` delegating to `AccountGainLossRetrievalResponsePolicy.map()`
- **Message policy:** `MessagePolicy.NONE` (`AccountGainLossExceptionMappings.java:40`)
- **Ordered log lines:** `logAtError = reason == SYSTEM_FAILURE` (`AccountGainLossExceptionMappings.java:42-43`) -- WARN for this reason; `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_BUSINESS`
- **Public HTTP status:** 422 (`AccountGainLossRetrievalResponsePolicy.java:16-23`)
- **Public ApiStatus:** `BUSINESS_REJECTED`
- **Public error code/type:** `ApiError.business("err.fund.switching.in.progress", null, [], "SERVER")` (`AccountGainLossRetrievalResponsePolicy.java:21-22`)
- **Citation:** `AccountGainLossRetrievalResponsePolicy.java:13-31`

### Account Gain/Loss -- ApimAccountGainLossSourceAdapter (TRPGetInvestGL)

- **Condition:** catch-all `systemFailure()`, sub-cause: unrecognized non-blank error
- **Detection site:** adapter's `try`/`catch` around `requireSingleData(requireSuccessData(...))`, line 73-77 (reference-confirmed)
- **Handling:** `systemFailure()` (public exception, status, body unchanged across sub-causes)
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** ERROR (`reason == SYSTEM_FAILURE`); `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, "SERVER")` (`AccountGainLossRetrievalResponsePolicy.java:28`)
- **Citation:** `AccountGainLossRetrievalResponsePolicy.java:13-31`

### Account Gain/Loss -- ApimAccountGainLossSourceAdapter (TRPGetInvestGL)

- **Condition:** catch-all `systemFailure()`, sub-cause: missing/empty data on success (inner try/catch)
- **Detection site:** adapter's `try`/`catch` around `requireSingleData(requireSuccessData(...))`, line 73-77 (reference-confirmed)
- **Handling:** `systemFailure()` (public exception, status, body unchanged across sub-causes)
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** ERROR (`reason == SYSTEM_FAILURE`); `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, "SERVER")` (`AccountGainLossRetrievalResponsePolicy.java:28`)
- **Citation:** `AccountGainLossRetrievalResponsePolicy.java:13-31`

### Account Gain/Loss -- ApimAccountGainLossSourceAdapter (TRPGetInvestGL)

- **Condition:** catch-all `systemFailure()`, sub-cause: transport-level failure reaching the outer catch-all
- **Detection site:** adapter's `try`/`catch` around `requireSingleData(requireSuccessData(...))`, line 73-77 (reference-confirmed)
- **Handling:** `systemFailure()` (public exception, status, body unchanged across sub-causes)
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** ERROR (`reason == SYSTEM_FAILURE`); `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, "SERVER")` (`AccountGainLossRetrievalResponsePolicy.java:28`)
- **Citation:** `AccountGainLossRetrievalResponsePolicy.java:13-31`

### Fund Price -- ApimFundPriceSourceAdapter / ApimFundPriceModeSourceAdapter (TRPGetFundPrice / TRPGetFundPriceMode)

- **Condition:** `err.tpa.invalid.date`
- **Detection site:** adapter-level check (reference-confirmed)
- **Handling:** `FundPriceInvalidDateException.forInvalidDate()`, mapped via `FundPriceExceptionMappings.invalidDate()`
- **Message policy:** `MessagePolicy.RESOLVE` (`FundPriceExceptionMappings.java:40-61`)
- **Ordered log lines:** `LogLevel.WARN` (`FundPriceExceptionMappings.java:57`), no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_BUSINESS`
- **Public HTTP status:** 400 (`FundPriceExceptionMappings.java:51`)
- **Public ApiStatus:** `VALIDATION_FAILED`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_PRICE_REQUEST_INVALID`
- **Citation:** `FundPriceExceptionMappings.java:40-61`

### Fund Price -- ApimFundPriceSourceAdapter / ApimFundPriceModeSourceAdapter (TRPGetFundPrice / TRPGetFundPriceMode)

- **Condition:** catch-all `systemFailure()`, sub-cause: unrecognized non-blank error
- **Detection site:** adapter's validator/catch-all handling (reference-confirmed)
- **Handling:** `FundPriceRetrievalException.systemFailure()`, mapped via `FundPriceExceptionMappings.retrievalUnavailable()` (public exception, status, body unchanged across sub-causes)
- **Message policy:** `MessagePolicy.RESOLVE` (`FundPriceExceptionMappings.java:18-38`)
- **Ordered log lines:** `LogLevel.ERROR` (`FundPriceExceptionMappings.java:34`), no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503 (`FundPriceExceptionMappings.java:28`)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_PRICE_RETRIEVAL_UNAVAILABLE`
- **Citation:** `FundPriceExceptionMappings.java:18-38`

### Fund Price -- ApimFundPriceSourceAdapter / ApimFundPriceModeSourceAdapter (TRPGetFundPrice / TRPGetFundPriceMode)

- **Condition:** catch-all `systemFailure()`, sub-cause: missing/empty data on success
- **Detection site:** adapter's validator/catch-all handling (reference-confirmed)
- **Handling:** `FundPriceRetrievalException.systemFailure()`, mapped via `FundPriceExceptionMappings.retrievalUnavailable()` (public exception, status, body unchanged across sub-causes)
- **Message policy:** `MessagePolicy.RESOLVE` (`FundPriceExceptionMappings.java:18-38`)
- **Ordered log lines:** `LogLevel.ERROR` (`FundPriceExceptionMappings.java:34`), no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503 (`FundPriceExceptionMappings.java:28`)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_PRICE_RETRIEVAL_UNAVAILABLE`
- **Citation:** `FundPriceExceptionMappings.java:18-38`

### Fund Price -- ApimFundPriceSourceAdapter / ApimFundPriceModeSourceAdapter (TRPGetFundPrice / TRPGetFundPriceMode)

- **Condition:** catch-all `systemFailure()`, sub-cause: transport-level failure
- **Detection site:** adapter's validator/catch-all handling (reference-confirmed)
- **Handling:** `FundPriceRetrievalException.systemFailure()`, mapped via `FundPriceExceptionMappings.retrievalUnavailable()` (public exception, status, body unchanged across sub-causes)
- **Message policy:** `MessagePolicy.RESOLVE` (`FundPriceExceptionMappings.java:18-38`)
- **Ordered log lines:** `LogLevel.ERROR` (`FundPriceExceptionMappings.java:34`), no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** 503 (`FundPriceExceptionMappings.java:28`)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_PRICE_RETRIEVAL_UNAVAILABLE`
- **Citation:** `FundPriceExceptionMappings.java:18-38`

### Fund Comparison -- ApimFundComparisonOptionsSourceAdapter (TRPGetFundCmpOpt)

- **Condition:** catch-all `systemFailure()`, sub-cause: unrecognized non-blank error
- **Detection site:** adapter's validator/catch-all handling (reference-confirmed)
- **Handling:** `FundComparisonRetrievalException.systemFailure()`, mapped via `FundComparisonExceptionMappings.retrievalUnavailable()` (public exception, status, body unchanged)
- **Message policy:** `MessagePolicy.RESOLVE` (`FundComparisonExceptionMappings.java:19-40`)
- **Ordered log lines:** `LogLevel.ERROR` (`FundComparisonExceptionMappings.java:36`), no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503 (`FundComparisonExceptionMappings.java:30`)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE`
- **Citation:** `FundComparisonExceptionMappings.java:15-41`

### Fund Comparison -- ApimFundComparisonOptionsSourceAdapter (TRPGetFundCmpOpt)

- **Condition:** catch-all `systemFailure()`, sub-cause: missing/empty data on success
- **Detection site:** adapter's validator/catch-all handling (reference-confirmed)
- **Handling:** `FundComparisonRetrievalException.systemFailure()`, mapped via `FundComparisonExceptionMappings.retrievalUnavailable()` (public exception, status, body unchanged)
- **Message policy:** `MessagePolicy.RESOLVE` (`FundComparisonExceptionMappings.java:19-40`)
- **Ordered log lines:** `LogLevel.ERROR` (`FundComparisonExceptionMappings.java:36`), no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503 (`FundComparisonExceptionMappings.java:30`)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE`
- **Citation:** `FundComparisonExceptionMappings.java:15-41`

### Fund Comparison -- ApimFundComparisonOptionsSourceAdapter (TRPGetFundCmpOpt)

- **Condition:** catch-all `systemFailure()`, sub-cause: transport-level failure
- **Detection site:** adapter's validator/catch-all handling (reference-confirmed)
- **Handling:** `FundComparisonRetrievalException.systemFailure()`, mapped via `FundComparisonExceptionMappings.retrievalUnavailable()` (public exception, status, body unchanged)
- **Message policy:** `MessagePolicy.RESOLVE` (`FundComparisonExceptionMappings.java:19-40`)
- **Ordered log lines:** `LogLevel.ERROR` (`FundComparisonExceptionMappings.java:36`), no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** 503 (`FundComparisonExceptionMappings.java:30`)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE`
- **Citation:** `FundComparisonExceptionMappings.java:15-41`

### Fund Fact Sheet -- ApimFundFactSheetSourceAdapter (TRPGetFundFactSheet)

- **Condition:** null item/list/row on an otherwise successful exchange (adapter's own `LOG.error`)
- **Detection site:** adapter-level null check (reference-confirmed)
- **Handling:** `FundFactSheetRetrievalException.systemFailure()`, mapped via `FundFactSheetExceptionMappings.retrievalUnavailable()`
- **Message policy:** `MessagePolicy.NONE` (`ErrorDescriptor#singleWithoutMessage`, `FundFactSheetExceptionMappings.java:28-37`, O-12: message always null for this exception)
- **Ordered log lines:** feature-level `LOG.error` in the adapter itself (prior to throwing); `LogLevel.ERROR` at the mapping (`FundFactSheetExceptionMappings.java:35`), no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503 (`FundFactSheetExceptionMappings.java:29`)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"SYSTEM"` / `ErrorCodes.FUND_FACT_SHEET_RETRIEVAL_UNAVAILABLE`
- **Citation:** `FundFactSheetExceptionMappings.java:14-39`

### Fund Fact Sheet -- ApimFundFactSheetSourceAdapter (TRPGetFundFactSheet)

- **Condition:** catch-all `systemFailure()`, sub-cause: unrecognized non-blank error
- **Detection site:** adapter's validator/catch-all handling (reference-confirmed)
- **Handling:** `FundFactSheetRetrievalException.systemFailure()` (public exception, status, body unchanged)
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `LogLevel.ERROR`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"SYSTEM"` / `ErrorCodes.FUND_FACT_SHEET_RETRIEVAL_UNAVAILABLE`
- **Citation:** `FundFactSheetExceptionMappings.java:14-39`

### Fund Fact Sheet -- ApimFundFactSheetSourceAdapter (TRPGetFundFactSheet)

- **Condition:** catch-all `systemFailure()`, sub-cause: transport-level failure
- **Detection site:** adapter's validator/catch-all handling (reference-confirmed)
- **Handling:** `FundFactSheetRetrievalException.systemFailure()` (public exception, status, body unchanged)
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `LogLevel.ERROR`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"SYSTEM"` / `ErrorCodes.FUND_FACT_SHEET_RETRIEVAL_UNAVAILABLE`
- **Citation:** `FundFactSheetExceptionMappings.java:14-39`

### Personal Information -- ApimUpdatePersonalInformationAdapter (TRPUpdMemberInfo)

- **Condition:** empty/all-null data items, or no item matches `selected`
- **Detection site:** reference-confirmed construction site
- **Handling:** `throw new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, ...)`, mapped via `ApimExceptionMappings.upstream()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.exchange.failed` ERROR; `http.api.error` ERROR, no stack trace. ExecutionLoggingAspect also fires on this class (confirmed present via `@LogExecution` grep, `ExecutionLoggingAspect.java`): on failure it logs `method.execution.error` at ERROR **with the full stack trace** (`log.error("{}", toJson(event), throwable)`, `ExecutionLoggingAspect.java:169`). Whether the specific annotated method is the exact one in this row was not confirmed line-by-line this pass -- only that the class file contains at least one `@LogExecution`-annotated method. (class confirmed `@LogExecution`-annotated: `ApimUpdatePersonalInformationAdapter.java`)
- **Idempotency replay:** No -- not found under any idempotency package
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 502
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `APIM_RESPONSE_INVALID`
- **Citation:** reference-confirmed; `ApimExceptionMappings.java:49-69`

### Reference Date -- ApimReferenceDateRefreshAdapter (TRPGetWebSysDate)

- **Condition:** missing payload / blank err-message field / missing data
- **Detection site:** reference-confirmed construction site
- **Handling:** `throw new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, ...)`, mapped via `ApimExceptionMappings.upstream()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.exchange.failed` ERROR; `http.api.error` ERROR, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 502
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `APIM_RESPONSE_INVALID`
- **Citation:** reference-confirmed; `ApimExceptionMappings.java:49-69`

### Reference Date -- ApimReferenceDateRefreshAdapter (TRPGetWebSysDate)

- **Condition:** unparsable date (a present value that fails to parse on our side)
- **Detection site:** reference-confirmed construction site (same `ApimException` construction as the row above)
- **Handling:** same `throw new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, ...)`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.exchange.failed` ERROR; `http.api.error` ERROR, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- **Public HTTP status:** 502
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `APIM_RESPONSE_INVALID`
- **Citation:** reference-confirmed; `ApimExceptionMappings.java:49-69` (this document separates it from the row above by cause per section 3's unparsable-body rule, even though both currently share one construction site and public shape)

### Shared `ApimResponseValidator`-reliant adapters (Contribution Summary, Member Info, Notice Message, Notification Read Status, Reference Data Countries, General Metadata, Account List transport, Member Login transport)

- **Condition:** any non-empty `err-message` on an otherwise successful HTTP exchange
- **Detection site:** `ApimResponseValidator.java:36-38` -- raw downstream `err-message` passed verbatim as the exception's internal diagnostic message only; public message resolved separately by code via `MessagePolicy.RESOLVE`
- **Handling:** mapped via `ApimExceptionMappings.upstream()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.exchange.failed` ERROR; `http.api.error` ERROR, no stack trace
- **Idempotency replay:** No -- all eight are read-only or non-mutating queries
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 502
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `APIM_RESPONSE_INVALID`
- **Citation:** `ApimResponseValidator.java:27-45`; 8 call sites: `ApimNotificationReadStatusAdapter.java:67`, `ApimMemberInfoAdapter.java:59`, `ApimReferenceDataCountriesAdapter.java:50`, `ApimGeneralMetadataSourceAdapter.java:88`, `ApimContributionSummaryAdapter.java:54`, `ApimUpdatePersonalInformationAdapter.java:61` (documented separately above), `ApimNoticeMessageAdapter.java:72`, `ApimAccountGainLossSourceAdapter.java:76` (documented separately above as part of Account Gain/Loss's catch-all split)

## 2. Terms

- `FailureOrigin.DOWNSTREAM_*`: internal log classification only. Never serialized into a response.
- `ApiStatus.DOWNSTREAM_REJECTED` / `ApiStatus.DOWNSTREAM_ERROR`: internal workflow values only. `WorkflowPublicApiStatusNormalizer` maps `DOWNSTREAM_REJECTED` to `BUSINESS_REJECTED` and `DOWNSTREAM_ERROR` to `SYSTEM_ERROR` before rendering.
- Public response: no field may contain any `DOWNSTREAM_*` value (see section 6).

## 3. Classification rules (approved by Nick)

- Recognised err-message code -> `FailureOrigin.DOWNSTREAM_BUSINESS`
- Unrecognised non-blank err-message, including all `ApimResponseValidator` callers -> `FailureOrigin.DOWNSTREAM_CONTRACT`
- Missing response / err-message, or missing or empty data on success -> `FailureOrigin.DOWNSTREAM_CONTRACT`
- `ApimCryptoException` / `ApimResponseMappingException` / unparsable body -> `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- HTTP 5xx / timeout / `WebClientRequestException` -> `FailureOrigin.DOWNSTREAM_TRANSPORT`
- `ApimProfileResolutionException` (our own configuration) -> `FailureOrigin.LOCAL_SYSTEM`
- Specific rows -> `FailureOrigin.DOWNSTREAM_BUSINESS`: Registration `err.tpa.member.not.found`, Account Balance `NOT_FOUND` / `MISMATCH`, Account Gain/Loss `err.tpa.fund.switching.in.progress`, Fund Price `err.tpa.invalid.date` -- **5 rows total** (Account Balance contributes 2 of the 5).
- Catch-all `systemFailure()` (Fund Price, Fund Comparison, Fund Fact Sheet, Account Balance, Account Gain/Loss), plus `TrpGetAccountListAdapter`'s `onErrorReturn` and `ApimForgotPasswordCompletionAdapter`'s `safeFailure`: classified by cause using the rules above, split into one sub-row per cause in section 1; the public exception, status and body stay unchanged across every sub-cause of the same catch-all.

## 4. Approved exceptions to the M2b "no change" rules

- **E1 (log level):** Registration `err.tpa.member.not.found` moves from `FailureOrigin.DOWNSTREAM_CONTRACT` to `FailureOrigin.DOWNSTREAM_BUSINESS` (ERROR -> WARN). Pinned fail-first in the Registration feature commit (sub-task 2b.4..2b.n). No other log level may change in M2b.
- **E2 (public contract):** no `DOWNSTREAM_*` value may reach the frontend (section 6, sub-task 2b.2a).

## 5. Incident ERROR per category (input for M3 3.8 -- describe only, no level change now)

- **DOWNSTREAM_BUSINESS:** proposed incident line is the feature-level log closest to the business decision (Registration's `recordDownstreamRejection`; Account Balance's/Account Gain/Loss's WARN-level mapping log, since these are already WARN today; Fund Price's WARN-level mapping log).
- **DOWNSTREAM_CONTRACT:** proposed incident line is `ApimExchangeOutcomeLogger`'s `apim.contract.invalid` (`ApimExchangeOutcomeLogger.java:70-72`).
- **DOWNSTREAM_INVALID_RESPONSE:** proposed incident line is `apim.exchange.failed` (`ApimExchangeOutcomeLogger.java:52-65`), confirmed to actually fire today for every row in this category.
- **DOWNSTREAM_TRANSPORT:** proposed incident line is `ApimWebClientFacade`'s `log.error` (`ApimWebClientFacade.java:409`, confirmed gated by `statusCode.isError()` at line 386).
- **LOCAL_SYSTEM:** proposed incident line is wherever the local defect/configuration failure is first caught (`ApimRegistrationCompletionAdapter.java:54-55` for the one confirmed `ApimProfileResolutionException` case).
- **Note:** rows whose adapter class is confirmed `@LogExecution`-annotated additionally produce a `method.execution.error` ERROR line with a full stack trace (`ExecutionLoggingAspect.java:169`); M3 3.8 must decide whether that line, rather than the ones above, becomes the canonical incident log for those specific rows, since it is the only one of the candidates that carries a stack trace.

## 6. Public DOWNSTREAM exposure (decision by Nick: no DOWNSTREAM_* in any public field)

Three distinct code paths can place a `DOWNSTREAM`-containing token into a public field:

**(a) `ApiError.downstreamSystem(...)` factory** (`ApiError.java:54-56`). **Resolved:** zero production callers; its only two references in the whole codebase are test usages (`ErrorResponseRendererTest.java:89`, `FrontendOwnedLocalizationMessageFreeErrorsTest.java:42`).

**(b) `WorkflowErrorType.DOWNSTREAM_SYSTEM` enum constant** (`WorkflowErrorType.java:10`), with four confirmed call sites, each tied to a specific status and endpoint:
- `RegistrationStartWebMapper.java:207` -- `MESSAGE_DELIVERY_FAILED` status, Registration Start endpoint
- `RegistrationOtpResendWebMapper.java:142` -- `MESSAGE_DELIVERY_FAILED` status, Registration OTP Resend endpoint
- `RegistrationOtpVerifyWebMapper.java:144` -- `TRUST_LOOKUP_FAILED` status, Registration OTP Verify endpoint
- `CheckRegistrationUsernameService.java:306` -- `DOWNSTREAM_FAILED` status, paired with `ERROR_DOWNSTREAM_UNAVAILABLE`, Registration username-check endpoint

Whether this enum constant is itself rendered verbatim into the public JSON `type` field, or translated by an intermediate renderer first, remains UNVERIFIED (the `WorkflowMutationResponseMapper`/`RegistrationWorkflowMutationResponseMapper` rendering classes were not read this pass).

**(c) The literal string `"DOWNSTREAM_SYSTEM"` passed directly into `ErrorDescriptor.single(...)`, bypassing `ApiError` and `WorkflowErrorType` both** -- this is the **main exposure site**:
- `ApimExceptionMappings.java:61` (`upstream()`, the generic `ApimException` mapping). This single mapping is reached by every one of this table's generic-`ApimException` rows: Forgot Password username-lookup (TRPChkCustId), Forgot Password completion's missing-envelope and transport-sub-cause rows (TRPResetPassword), Personal Information (TRPUpdMemberInfo), both Reference Date rows (TRPGetWebSysDate), and the shared 8-adapter `ApimResponseValidator` group (TRPSearchMember/TRPReqNoticeMessage/etc., via Contribution Summary, Member Info, Notice Message, Notification Read Status, Reference Data Countries, General Metadata, Account List transport, Member Login transport). Every one of these endpoints can return this literal string in its public response `type` field today.
- `RegistrationExceptionMappings.java:31` (`lookupDownstream()`), affecting Registration's lookup `EMPTY_DATA`/`INVALID_ACCOUNT_DATA`/`UNEXPECTED_ERROR` row (TRPSearchMember, Registration Lookup).

**Confirmed non-exposure:** `ApiStatus.DOWNSTREAM_REJECTED`/`ApiStatus.DOWNSTREAM_ERROR` are normalized away by `WorkflowPublicApiStatusNormalizer` before rendering, so these two enum values do not themselves reach the client.

**Existing guard test:** `PublicDownstreamStatusIsolationGuardTest` (characterized in an earlier pass as scanning only for the literal tokens `ApiStatus.DOWNSTREAM_ERROR`/`ApiStatus.DOWNSTREAM_REJECTED` in `adapter/in/web` source) was not re-dumped this pass to re-confirm its exact scan list.

### Replacement

The existing non-downstream system type is `ApiError.type = "SYSTEM"` (`ApiError.system(...)`, `ApiError.java:45-47`), confirmed verbatim from source: `return new ApiError("SYSTEM", code, message, List.of(), DEFAULT_SEVERITY, source);`. This is the exact replacement string for path (c)'s literal `"DOWNSTREAM_SYSTEM"` usages. HTTP status, `ApiStatus`, code and message policy stay unchanged; only the `DOWNSTREAM_SYSTEM` string literal is replaced with `SYSTEM`. Path (b)'s `WorkflowErrorType.DOWNSTREAM_SYSTEM` would need its own replacement enum constant or rendering-path change, not yet designed, since `WorkflowErrorType` is a separate enum from `ApiError.type`.

### Sub-task 2b.2a (runs after 2b.2, before 2b.3)

- Fail-first: change the characterization rows that expect a `DOWNSTREAM_SYSTEM` value (both path (b) and path (c) occurrences, including every endpoint listed in path (c) above) to their replacement; list every row (built from 2b.2's exhaustive characterization pack).
- Replace every public `DOWNSTREAM_*` value; delete `ApiError.downstreamSystem()` (now confirmed production-unused) and resolve `WorkflowErrorType.DOWNSTREAM_SYSTEM`'s four call sites.
- Extend `PublicDownstreamStatusIsolationGuardTest`: keep the existing source check; add a behaviour check that renders every exception mapping and every workflow error path and asserts no rendered JSON contains `"DOWNSTREAM"`.
- Commit: `fix(exception-handling): stop exposing downstream error types to clients`

**2b.2a waits for Nick to confirm FE does not depend on any `DOWNSTREAM_*` value.**

## 7. Sub-task plan

- **2b.2:** characterization pack (public status, body, message, log event, level for every inventory row above) on unchanged code. Must also resolve this document's remaining UNVERIFIED items: `ForgotPasswordUnderLockDetailsVerifier`'s exact catch/classification of `ForgotPasswordDetailsDownstreamException`; `CatchAllExceptionMapping`'s exact HTTP status/`ApiStatus` (for `RegistrationCompletionPreDispatchException`'s final public shape); and `WorkflowMutationResponseMapper`/`RegistrationWorkflowMutationResponseMapper`'s exact JSON rendering of `WorkflowErrorType.DOWNSTREAM_SYSTEM`.
- **2b.2a:** as described in section 6, after Nick confirms FE independence from `DOWNSTREAM_*` values.
- **2b.3:** shared pure classifier (names chosen in this document's section 3 rules), branch coverage >= 95%.
- **2b.4..2b.n:** one commit per feature, attaching the classification; feature code and tables unchanged; gate after each.

## 8. Open questions (listed, not decided)

- Public status 502 vs 500 for `ApimException` (`ApimForgotPasswordCompletionAdapter.safeFailure` uses 500; most other sites use 502) -- whether this divergence is intentional or should be unified is a decision, not a fact, and stays open.
- Whether the swallow sites in section 1 (Forgot Password crypto-failure, Username Recovery x2, Login x3 across its two adapters) should publish a `FailureEvent` (M3 3.7).
- `ApimForgotPasswordCompletionAdapter.safeFailure`'s transport-vs-local-defect split (section 1) is not structurally distinguished by the current code at the catch site; both sub-causes share one statement.


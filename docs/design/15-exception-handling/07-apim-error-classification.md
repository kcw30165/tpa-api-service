# 07 - APIM Error Classification Design

## 1. Inventory table (48 rows)

### Registration -- ApimRegistrationLookupAdapter (TRPSearchMember)

- **Condition:** `err.tpa.member.not.found` -> `NOT_ELIGIBLE`
- **Detection site:** `registration/mapper/TrpSearchMemberApimResultMapper.toResult`, lines 22-24 (confirmed: this is the `adapter/out/apim/registration/mapper` package, a separate file from the `usernamerecovery/mapper` package's same-named class)
- **Handling:** Returned as normal `RegistrationLookupResult(NOT_ELIGIBLE, List.of(), null)`; `ApimRegistrationLookupAdapter.withFailureLogging`'s `doOnEach` (lines 94-128) detects `status()==NOT_ELIGIBLE` on the onNext signal and calls `recordDownstreamRejection` (line 101), publishing a `FailureEvent` (lines 144-159)
- **Message policy:** N/A -- successful application result, never an `ExceptionMapping`
- **Ordered log lines:** (1) `apim.business.rejected` WARN (executor classification; seen in DEV on 2026-10-28 for a TRPSearchMember member-not-found)ier (non-empty err-message), firing on the already-resolved onNext envelope before this mapper runs; (2) `FailureEvent` `event.registration.lookup.downstream_rejection` published, origin `DOWNSTREAM_CONTRACT` (line 121), logged ERROR today by `StructuredFailureEventLogger` -- E1 moves this to WARN. (3) `ApimRegistrationLookupAdapter` is `@LogExecution`-annotated, but this condition is returned as a normal result, so `method.execution.error` (`ExecutionLoggingAspect.java:161-170`) is not logged for it.
- **Idempotency replay:** No -- `searchMember` is called directly from `RegistrationStartProtectedOperation.lookup` (confirmed, not itself idempotency-wrapped; the surrounding Registration Start flow IS idempotency-protected)
- **Origin tag today:** `FailureOrigin.DOWNSTREAM_CONTRACT` (`ApimRegistrationLookupAdapter.java:121`)
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_BUSINESS` only, per E1 (section 4)
- **Public HTTP status:** 422. `RegistrationStartProtectedOperation.handleLookup` (lines 175-184) checks `found.status()==NOT_ELIGIBLE` and returns `failure(VERIFICATION_FAILED, RegistrationErrorKey.START_VERIFICATION_FAILED, "identityValue")`. `RegistrationStartWebMapper.toWorkflowErrorType` (lines 201-213) returns `WorkflowErrorType.FIELD` for this result because its target is "identityValue" (`WorkflowErrorType.BUSINESS` applies only when the target is PAGE). `toWorkflowStatus` (lines 215-224) maps it to `WorkflowResultStatus.BUSINESS_FAILED`. `WorkflowMutationResponseMapper.toApiStatus` maps `BUSINESS_FAILED` -> `ApiStatus.BUSINESS_REJECTED`, which resolves to 422 via `RegistrationHttpResponses`'s standard `BUSINESS_REJECTED`->`UNPROCESSABLE_ENTITY` branch (same pattern confirmed in sibling `*HttpResponses` classes).
- **Public ApiStatus:** `BUSINESS_REJECTED`
- **Public error code/type:** `RegistrationErrorKey.START_VERIFICATION_FAILED.value()` (public workflow error code, type `FIELD`: `RegistrationStartProtectedOperation.java:184` passes target "identityValue" and `RegistrationStartWebMapper.java:203-205` returns `WorkflowErrorType.FIELD` for any non-PAGE target)
- **Citation:** `ApimRegistrationLookupAdapter.java:144-159`; `RegistrationStartProtectedOperation.java:175-184`; `RegistrationStartWebMapper.java:201-224`

### Registration -- ApimRegistrationLookupAdapter (TRPSearchMember)

- **Condition:** `EMPTY_DATA`/`INVALID_ACCOUNT_DATA`/`UNEXPECTED_ERROR` reasons
- **Detection site:** `registration/mapper/TrpSearchMemberApimResultMapper.toResult`, lines 25-28 (unrecognized non-blank err-message -> `UNEXPECTED_ERROR`), 30-33 (empty items -> `EMPTY_DATA`), `toAccount` lines 39-42 (missing env/trustCode -> `INVALID_ACCOUNT_DATA`)
- **Handling:** Throws `RegistrationLookupDownstreamException`, mapped by `RegistrationExceptionMappings.lookupDownstream()` (`RegistrationExceptionMappings.java:18-44`). Also caught by `ApimRegistrationLookupAdapter.withFailureLogging`'s `doOnEach` (lines 108-127), which publishes a `FailureEvent` with the exception's own `downstreamErrorCode()` and `SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value()` (lines 115-126).
- **Message policy:** Configured as MessagePolicy.RESOLVE in RegistrationExceptionMappings, but the CONFIRMED ACTUAL RUNTIME RESULT (real passing test evidence, RegistrationLookupFailuresCharacterizationTest's disabled-path scenarios, all asserting `error.message()).isNull()`) is that `message()` is always null for this condition in practice: `PublicMessageResolver.isPreLoginAuthRequest(...)` short-circuits to null for any `/api/v1/auth/registration` path BEFORE RESOLVE's actual resolution logic ever runs - same pre-login short-circuit mechanism already documented for the Forgot Password/Username Recovery rows above.
- **Ordered log lines:** (1) `apim.contract.invalid` ERROR from the classifier (missing err-message for `EMPTY_DATA`); for `UNEXPECTED_ERROR` (unrecognized non-blank err-message), `apim.business.rejected` WARN instead (the classifier only distinguishes blank-vs-non-blank err-message, not recognized-vs-unrecognized); (2) `FailureEvent`, origin `DOWNSTREAM_CONTRACT`, ERROR (`ApimRegistrationLookupAdapter.java:121`); (3) `http.api.error` ERROR, no stack trace (`RegistrationExceptionMappings.java:35`, final arg `false`). ApimRegistrationLookupAdapter is confirmed `@LogExecution`-annotated (unfiltered grep, 10-class list). A failure here also produces `method.execution.error` ERROR with full stack trace (`ExecutionLoggingAspect.java:161-170`). **Ordered log lines (captcha ENABLED path - NEVER reaches ApiExceptionHandler, confirmed by real passing test evidence):** (1) same classifier line as above (apim.contract.invalid or apim.business.rejected, per reason); (2) FailureEvent, origin DOWNSTREAM_CONTRACT, ERROR - STILL fires, since ApimRegistrationLookupAdapter's `doOnEach(...)` sees the onError signal regardless of what wraps around it further up the chain; (3) NO http.api.error line (the exception is caught and discarded by onErrorResume before the handler layer); instead, `RegistrationStartWebMapper.logFatalIfNeeded` fires its own plain SLF4J ERROR line on its own logger: `event=registration.start.failure severity=FATAL status=SYSTEM_FAILED errorCodes=[err.workflow.system.unavailable]` - this line was previously undocumented here.
- **Idempotency replay:** No
- **Origin tag today:** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** CAPTCHA-STATE DEPENDENT for the SAME exception/reasons (confirmed by real passing tests, RegistrationLookupFailuresCharacterizationTest, 6/6 scenarios green): captcha DISABLED -> 503 (RegistrationExceptionMappings.java:29, reaches ApiExceptionHandler via the normal exception-mapping registry). captcha ENABLED -> **502**, and this exception NEVER reaches ApiExceptionHandler at all - RegistrationStartProtectedOperation's enabled-path `.onErrorResume(ignored -> ...)` catches and DISCARDS the exception (the lambda parameter is literally named `ignored` and the specific reason is never inspected) before the controller/handler ever sees it, producing a generic `failure(SYSTEM_FAILED, SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE, "PAGE")` result instead. All 3 reasons (EMPTY_DATA/INVALID_ACCOUNT_DATA/UNEXPECTED_ERROR) produce this IDENTICAL enabled-path result - only the underlying FailureEvent's dimensions.reason differs.
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value()` (`RegistrationExceptionMappings.java:31-32`)
- **Citation:** `RegistrationExceptionMappings.java:18-44`

### Registration -- ApimRegistrationCompletionAdapter (TRPRegMember)

- **Condition:** `ApimProfileResolutionException` during completion (our own profile-routing config failure)
- **Detection site:** `ApimRegistrationCompletionAdapter.java:54-55`
- **Handling:** `.onErrorMap(ApimProfileResolutionException.class, RegistrationCompletionPreDispatchException::new)` (line 54-55). Absent from `ExceptionMappingRegistryConfig`'s list, resolves via `CatchAllExceptionMapping`. **Routing confirmed:** `RegistrationController.completeRegistration` (lines 134-161) wraps only the synchronous call-construction in `try/catch(RuntimeException)`; a reactive Mono error from `completeRegistrationUseCase.complete(...)` is NOT caught, confirming it propagates to `CatchAllExceptionMapping`.
- **Message policy:** N/A -- resolves via the mandatory catch-all
- **Ordered log lines:** No executor-layer log (local config failure, not a downstream envelope issue); `http.api.error` ERROR with stack trace (`CatchAllExceptionMapping.java:38`, final arg `true`). ApimRegistrationCompletionAdapter is confirmed `@LogExecution`-annotated (unfiltered grep, 10-class list). A failure here also produces `method.execution.error` ERROR with full stack trace (`ExecutionLoggingAspect.java:161-170`).
- **Idempotency replay:** Yes -- Registration complete is idempotency-protected
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.LOCAL_SYSTEM`
- **Public HTTP status:** 500 (`CatchAllExceptionMapping.java:32`)
- **Public ApiStatus:** `SYSTEM_ERROR` (`CatchAllExceptionMapping.java:33`)
- **Public error code/type:** `"SYSTEM"` / `ErrorCodes.SYSTEM_UNEXPECTED` (`CatchAllExceptionMapping.java:34-35`)
- **Citation:** `ApimRegistrationCompletionAdapter.java:54-55`; `RegistrationController.java:134-161`; `CatchAllExceptionMapping.java:18-40`

### Forgot Password -- ApimForgotPasswordUsernameLookupAdapter (TRPChkCustId)

- **Condition:** missing envelope/response
- **Detection site:** `ApimForgotPasswordUsernameLookupAdapter.java:79-81` (`toTransportEnvelope`). Confirmed: this adapter performs its own `postEnvelope` call and conversion; `SharedApimTrpChkCustIdTransport` is referenced only for its `API_NAME` constant (line 51), not as a co-handler.
- **Handling:** `throw new IllegalStateException("TRPChkCustId response envelope is missing response")` (line 80). Propagates past `.map()`, observed by `.doOnError` (lines 68-74, logs `event=forgot-password.username-lookup.failed` ERROR with full exception). `ForgotPasswordStartProtectedOperation.execute`'s `.onErrorResume` (lines 100-108) swallows it into `systemFailed()` -- a result value, not an exception.
- **Message policy:** N/A at this point -- swallowed to a result value before any `ExceptionMapping`
- **Ordered log lines:** `event=forgot-password.username-lookup.failed` ERROR with full exception (`ApimForgotPasswordUsernameLookupAdapter.java:68-74`); `event=forgot-password.start.failed requestId={} stage=username-lookup exceptionType={}` ERROR at the swallow site (`ForgotPasswordStartProtectedOperation.java:100-107`)
- **Idempotency replay:** Yes -- Forgot Password start
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503. `IdempotentStartForgotPasswordService.executeAcquired` (lines 58-67) re-converts a `SYSTEM_FAILED` result BACK into a thrown `IllegalStateException`. `ForgotPasswordController.startForgotPassword` (lines 93-116) has a reactive `.onErrorResume(exception -> Mono.just(ForgotPasswordHttpResponses.toResponse(startMapper.systemUnavailable())))` (lines 109-112), which catches this re-thrown exception. `ForgotPasswordHttpResponses.status()` resolves `ApiStatus.SYSTEM_ERROR` -> `HttpStatus.SERVICE_UNAVAILABLE` (lines 46-48, `ForgotPasswordHttpResponses.java:46-47`). Confirmed: this entirely bypasses `ApimExceptionMappings`/`CatchAllExceptionMapping`.
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value()` (`ForgotPasswordStartWebMapper.systemUnavailable()`, lines 119-127, routes through the shared `failure(...)` helper, lines 129-140, which calls `ApiError.form(error.code(), null, sanitizeTargets(error), "BFF")` -- source is `"BFF"`, not `"DOWNSTREAM"`)
- **Citation:** `ApimForgotPasswordUsernameLookupAdapter.java:77-91`; `ForgotPasswordStartProtectedOperation.java:86-109`; `IdempotentStartForgotPasswordService.java:58-67`; `ForgotPasswordController.java:93-116`; `ForgotPasswordHttpResponses.java:21-50`

### Forgot Password -- ApimForgotPasswordUsernameLookupAdapter (TRPChkCustId)

- **Condition:** no err-message AND no data, or data present but every item malformed
- **Detection site:** `ApimForgotPasswordUsernameLookupAdapter.java:86-100`
- **Handling:** `throw new ApimException(BAD_GATEWAY, APIM_RESPONSE_INVALID, ...)` (lines 87-90 or 96-99). Same swallow chain as the row above: observed by the adapter's `.doOnError`, then swallowed by `ForgotPasswordStartProtectedOperation`'s `.onErrorResume` into `systemFailed()`.
- **Message policy:** N/A -- swallowed before any `ExceptionMapping`
- **Ordered log lines:** `apim.contract.invalid` ERROR from the classifier (matches its malformed rule); adapter's `event=forgot-password.username-lookup.failed` ERROR with exception; `event=forgot-password.start.failed` ERROR at the swallow site
- **Idempotency replay:** Yes
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503 via the same confirmed routing as the row above
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value()` (`ForgotPasswordStartWebMapper.systemUnavailable()`, lines 119-127)
- **Citation:** `ApimForgotPasswordUsernameLookupAdapter.java:86-100`; `ForgotPasswordStartProtectedOperation.java:86-109`; `ForgotPasswordController.java:93-116`

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)

- **Condition:** missing response envelope
- **Detection site:** `ApimForgotPasswordCompletionAdapter.java:64-68` (`toEnvelope`)
- **Handling:** `throw new ApimException(BAD_GATEWAY, APIM_RESPONSE_INVALID, ...)` (lines 65-68). **Routing confirmed:** this propagates past `.map(this::toEnvelope)` (line 54) to the chain's `.onErrorResume(ApimCryptoFailureClassifier::isCryptoFailure, ...)` (lines 56-58, does NOT match -- `ApimException` is not a crypto failure) then `.onErrorMap(this::safeFailure)` (line 59, re-wraps as `ApimException(INTERNAL_SERVER_ERROR, ...)` only if not already an `ApimException` -- here it IS already an `ApimException`, so `safeFailure` returns it unchanged per line 75: `if (t instanceof ApimException) return t;`). Reaches `ForgotPasswordCompleteProtectedOperation.invoke`'s own `.onErrorResume(ignored -> blockUnknown(session, now))` (line 135), which swallows ANY throwable into `unknownOutcome()` -- a result value.
- **Message policy:** N/A -- swallowed to a result value by `ForgotPasswordCompleteProtectedOperation`
- **Ordered log lines:** `apim.contract.invalid` ERROR from the classifier (missing response matches malformed rule); no adapter-level named log line for this specific condition (unlike the username-lookup adapter, this adapter has no `.doOnError` of its own at this throw site). `event=forgot-password.details-otp.failed` does NOT apply here (that is the Details/OTP flow, not Completion). ApimForgotPasswordCompletionAdapter is confirmed `@LogExecution`-annotated (unfiltered grep, 10-class list). Both ERROR lines fire in order: (1) apim.contract.invalid ERROR from DefaultApimExchangeExecutor's own onNext classification (missing response matches the malformed rule; this fires before the adapter's own throw, since toEnvelope()'s check runs inside .map(...) downstream of the executor's postEnvelope(...)); (2) method.execution.error ERROR with full stack trace (ExecutionLoggingAspect.java:161-170) -- the @LogExecution-annotated complete(...) method's own Mono emits onError for this condition (the swallow to unknownOutcome() happens one layer up, in ForgotPasswordCompleteProtectedOperation.invoke, not inside complete() itself). Pinned by ApimForgotPasswordCompletionAdapterLogExecutionAspectCharacterizationTest.missingEnvelopeLogsStartThenErrorWithStackTrace.
- **Idempotency replay:** Yes -- Forgot Password complete
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** not characterized (Test B deferred): the public response and replay for this condition are not pinned by a test; `ForgotPasswordCompleteProtectedOperation` and `IdempotentCompleteForgotPasswordService` are not exercised end to end.
- **Public ApiStatus:** not characterized (Test B deferred): the public response and replay for this condition are not pinned by a test; `ForgotPasswordCompleteProtectedOperation` and `IdempotentCompleteForgotPasswordService` are not exercised end to end.
- **Public error code/type:** not characterized (Test B deferred): the public response and replay for this condition are not pinned by a test; `ForgotPasswordCompleteProtectedOperation` and `IdempotentCompleteForgotPasswordService` are not exercised end to end.
- **Citation:** `ApimForgotPasswordCompletionAdapter.java:62-72`; `ForgotPasswordCompleteProtectedOperation.java:127-135,191-197`; `ForgotPasswordController.java:274-296`

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)
- **Condition:** present response, err-message empty, data empty
- **Detection site:** ApimForgotPasswordCompletionAdapter.java (toEnvelope passes the envelope through unchanged since its own response is non-null; resultMapper.toResult(...) maps empty data to a TECHNICAL_FAILURE result rather than throwing)
- **Handling:** returned as ForgotPasswordCompletionResult(status=TECHNICAL_FAILURE, ...); no exception is thrown
- **Message policy:** N/A -- returned as a normal result value
- **Ordered log lines:** apim.contract.invalid ERROR (DefaultApimExchangeExecutor's own onNext classification of the empty-data envelope); method.execution.start then method.execution.success INFO (the @LogExecution aspect, since complete(...) completes normally with a result value rather than an error). Pinned by ApimForgotPasswordCompletionAdapterBatch2aCharacterizationTest.complete_emptyButPresentResponse_returnsTechnicalFailureResult.
- **Idempotency replay:** Yes -- Forgot Password complete
- **Origin tag today:** none
- **Proposed FailureOrigin (section 3):** FailureOrigin.DOWNSTREAM_CONTRACT
- **Public HTTP status:** not characterized (deferred, pending Nick's Test B decision) 
- **Public ApiStatus:** not characterized (Test B deferred): the public response and replay for this condition are not pinned by a test; `ForgotPasswordCompleteProtectedOperation` and `IdempotentCompleteForgotPasswordService` are not exercised end to end.
- **Public error code/type:** not characterized (Test B deferred): the public response and replay for this condition are not pinned by a test; `ForgotPasswordCompleteProtectedOperation` and `IdempotentCompleteForgotPasswordService` are not exercised end to end.
- **Citation:** ApimForgotPasswordCompletionAdapter.java:52-60; ApimForgotPasswordCompletionAdapterBatch2aCharacterizationTest.complete_emptyButPresentResponse_returnsTechnicalFailureResult

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)

- **Condition:** crypto failure (`ApimCryptoException`/`ApimResponseMappingException`)
- **Detection site:** `ApimPayloadCryptoService.java:164` (`throw new ApimCryptoException("Failed to decrypt APIM response payload.", ex)`); classifier `ApimCryptoFailureClassifier.java:15-16`; confirmed sole caller `ApimForgotPasswordCompletionAdapter.java:57`
- **Handling:** `.onErrorResume(ApimCryptoFailureClassifier::isCryptoFailure, e -> Mono.just(ForgotPasswordCompletionResult.unknownOutcome()))` (lines 56-58). This swallow happens INSIDE `complete()`'s own chain (line 52-59), one layer below `ForgotPasswordCompleteProtectedOperation.invoke`'s further `.onErrorResume(ignored -> blockUnknown(...))` (line 135) -- the adapter's own swallow fires first, so `blockUnknown`'s broader catch never sees this specific failure.
- **Message policy:** N/A -- swallowed
- **Ordered log lines:** `apim.exchange.failed` ERROR (genuine Mono onError signal from the executor's decryption step, fires via `doOnEach` before this adapter-level swallow -- confirmed structural guarantee). ApimForgotPasswordCompletionAdapter is confirmed `@LogExecution`-annotated (unfiltered grep, 10-class list). The adapter swallows this failure into ForgotPasswordCompletionResult.unknownOutcome() (lines 56-58), so the call completes normally: the @LogExecution aspect logs method.execution.start and method.execution.success (INFO) and no method.execution.error (ExecutionLoggingAspect.java:103-104). Pinned by ApimForgotPasswordCompletionAdapterLogExecutionAspectCharacterizationTest.
- **Idempotency replay:** Yes
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- **Public HTTP status:** not characterized (Test B deferred): the public response and replay for this condition are not pinned by a test; `ForgotPasswordCompleteProtectedOperation` and `IdempotentCompleteForgotPasswordService` are not exercised end to end.
- **Public ApiStatus:** not characterized (Test B deferred): the public response and replay for this condition are not pinned by a test; `ForgotPasswordCompleteProtectedOperation` and `IdempotentCompleteForgotPasswordService` are not exercised end to end.
- **Public error code/type:** not characterized (Test B deferred): the public response and replay for this condition are not pinned by a test; `ForgotPasswordCompleteProtectedOperation` and `IdempotentCompleteForgotPasswordService` are not exercised end to end.
- **Citation:** `ApimPayloadCryptoService.java:158-166`; `ApimForgotPasswordCompletionAdapter.java:52-58`

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)

- **Condition:** `safeFailure` catch-all, sub-cause: HTTP-error-status response (facade)
- **Detection site:** `ApimForgotPasswordCompletionAdapter.java:74-81`
- **Handling:** `.onErrorMap(this::safeFailure)` (line 59) -- but confirmed this is reached only when the upstream throwable is NOT already an `ApimException` (line 75's `if (t instanceof ApimException) return t;` guard means a facade-raised HTTP-error `ApimException` is returned UNCHANGED by `safeFailure`, not re-wrapped). Either way (unchanged facade `ApimException`, or a freshly-wrapped `ApimException(INTERNAL_SERVER_ERROR, ...)` for a non-`ApimException` cause), the result still reaches `ForgotPasswordCompleteProtectedOperation.invoke`'s `.onErrorResume(ignored -> blockUnknown(...))` (line 135) and is swallowed into `unknownOutcome()`.
- **Message policy:** N/A -- ultimately swallowed by `ForgotPasswordCompleteProtectedOperation`
- **Ordered log lines:** `apim.exchange.failed` ERROR (genuine Mono error via the facade's HTTP-error path). ApimForgotPasswordCompletionAdapter is confirmed `@LogExecution`-annotated (unfiltered grep, 10-class list). .onErrorMap(this::safeFailure) rethrows (does not swallow) an ApimException inside complete()'s own chain, so complete()'s Mono emits onError and the aspect logs method.execution.error ERROR with full stack trace (ExecutionLoggingAspect.java:161-170) -- confirmed by ApimForgotPasswordCompletionAdapterLogExecutionAspectCharacterizationTest.unexpectedFailureLogsStartThenErrorWithStackTrace (representative non-crypto, unexpected-failure scenario). NOTE: this specific sub-path - safeFailure returning a facade-raised ApimException UNCHANGED (line 75's guard) rather than freshly wrapping a non-ApimException cause - is NOT independently pinned by a dedicated test; the cited test is representative/non-specific to this exact sub-case.
- **Idempotency replay:** Yes
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** not characterized (Test B deferred): the public response and replay for this condition are not pinned by a test; `ForgotPasswordCompleteProtectedOperation` and `IdempotentCompleteForgotPasswordService` are not exercised end to end.
- **Public ApiStatus:** not characterized (Test B deferred): the public response and replay for this condition are not pinned by a test; `ForgotPasswordCompleteProtectedOperation` and `IdempotentCompleteForgotPasswordService` are not exercised end to end.
- **Public error code/type:** not characterized (Test B deferred): the public response and replay for this condition are not pinned by a test; `ForgotPasswordCompleteProtectedOperation` and `IdempotentCompleteForgotPasswordService` are not exercised end to end.
- **Citation:** `ApimForgotPasswordCompletionAdapter.java:74-81`; `ForgotPasswordCompleteProtectedOperation.java:127-135,191-197`

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)

- **Condition:** `safeFailure` catch-all, sub-cause: connection-level failure (`WebClientRequestException`/`TimeoutException`, retries exhausted)
- **Detection site:** `ApimForgotPasswordCompletionAdapter.java:74-81`
- **Handling:** Same `safeFailure` catch-all (`t instanceof ApimException ? t : new ApimException(INTERNAL_SERVER_ERROR, ...)`). Connection-level failures are re-raised unconverted by `ApimWebClientFacade.sendBusinessAttempt`'s `onErrorResume` once retries exhaust (`ApimWebClientFacade.java:219-225`), so `safeFailure` wraps them fresh as `ApimException(INTERNAL_SERVER_ERROR, SYSTEM_UNEXPECTED, ...)`. Same eventual swallow as the row above via `ForgotPasswordCompleteProtectedOperation.blockUnknown`.
- **Message policy:** N/A -- ultimately swallowed
- **Ordered log lines:** `apim.exchange.failed` ERROR (genuine Mono error). Same safeFailure re-wrap as the row above (not swallowed inside complete()), so complete()'s own Mono emits onError and the aspect logs method.execution.error ERROR with full stack trace (ExecutionLoggingAspect.java:161-170) -- confirmed by ApimForgotPasswordCompletionAdapterLogExecutionAspectCharacterizationTest.unexpectedFailureLogsStartThenErrorWithStackTrace.
- **Idempotency replay:** Yes
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** not characterized (Test B deferred): the public response and replay for this condition are not pinned by a test; `ForgotPasswordCompleteProtectedOperation` and `IdempotentCompleteForgotPasswordService` are not exercised end to end.
- **Public ApiStatus:** not characterized (Test B deferred): the public response and replay for this condition are not pinned by a test; `ForgotPasswordCompleteProtectedOperation` and `IdempotentCompleteForgotPasswordService` are not exercised end to end.
- **Public error code/type:** not characterized (Test B deferred): the public response and replay for this condition are not pinned by a test; `ForgotPasswordCompleteProtectedOperation` and `IdempotentCompleteForgotPasswordService` are not exercised end to end.
- **Citation:** `ApimForgotPasswordCompletionAdapter.java:74-81`; `ApimWebClientFacade.java:212-241`; `ForgotPasswordCompleteProtectedOperation.java:127-135,191-197`

### Forgot Password -- ApimForgotPasswordDetailsLookupAdapter (TRPSearchMember)

- **Condition:** missing envelope
- **Detection site:** `ForgotPasswordTrpSearchMemberMapper.java:46`; `ApimForgotPasswordDetailsLookupAdapter.java:51-52`
- **Handling:** `throw new ForgotPasswordDetailsDownstreamException(Reason.UNEXPECTED_ERROR)`. The real consumer is `ForgotPasswordUnderLockDetailsVerifier.verify()` (lines 34-58), which wraps the lookup-port call. `.doOnError(cause -> logFailure(requestId, cause))` (line 55) logs `event=forgot-password.details-otp.failed requestId={} stage=lookup exceptionType={}` ERROR (exception class name only, no stack trace, lines 61-66), then `.onErrorReturn(ForgotPasswordUnderLockDetailsVerification.of(SYSTEM_FAILED))` (lines 56-58) swallows it to a result value. This result flows up through `ForgotPasswordDetailsOtpProtectedOperation.locked` (lines 136-144), which returns `BUSINESS_REJECTED` only if `status()==DETAILS_VERIFIED`; any other status (including `SYSTEM_FAILED`) falls through that same `locked` method's flow unchanged as a `ForgotPasswordUnderLockDetailsVerification` with `SYSTEM_FAILED`.
- **Message policy:** N/A -- swallowed before any `ExceptionMapping`
- **Ordered log lines:** `event=forgot-password.details-otp.failed stage=lookup` ERROR, exception class name only, no stack trace (`ForgotPasswordUnderLockDetailsVerifier.java:61-66`)
- **Idempotency replay:** Yes -- Forgot Password details/OTP is idempotency-protected
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** **UNVERIFIED, narrowly scoped:** `ForgotPasswordDetailsOtpProtectedOperation.locked`'s exact handling of a `SYSTEM_FAILED` verification result (as opposed to `DETAILS_VERIFIED`/`BUSINESS_REJECTED`, both confirmed) was not traced to its final `ForgotPasswordDetailsOtpResult.Status` -- the relevant method body section was not in 's dump range
- **Public ApiStatus:** UNVERIFIED
- **Public error code/type:** UNVERIFIED
- **Citation:** `ForgotPasswordUnderLockDetailsVerifier.java:1-67`; `ApimForgotPasswordDetailsLookupAdapter.java:51-52`; `ForgotPasswordDetailsOtpProtectedOperation.java:110-174` (confirmed read; `SYSTEM_FAILED` sub-branch not conclusively isolated)

### Username Recovery -- TrpSearchMemberAdapter (TRPSearchMember)

- **Condition:** `TrpSearchMemberApimDownstreamException` (unrecognized non-blank err-message)
- **Detection site:** `usernamerecovery/mapper/TrpSearchMemberApimResultMapper.java:47-50` (confirmed: this is the separate `usernamerecovery/mapper` package file, distinct from `registration/mapper`'s same-named class)
- **Handling:** `.onErrorResume(TrpSearchMemberApimDownstreamException.class, e -> Mono.just(unknownOutcome()))` (`TrpSearchMemberAdapter.java:54-55`). **Routing confirmed:** `UsernameRecoveryStartProtectedOperation.lookup` (lines 101-128) calls `lookupPort.search(lookupCommand).onErrorMap(Exception.class, ProtectedExecutionException::new)` (lines 113-115). The success result's status switch (lines 117-127) groups `CONTRACT_INVALID, UNAVAILABLE, UNKNOWN_OUTCOME ->` together (line 125), all mapping uniformly to `Mono.error(UsernameRecoveryStartException.systemFailure())`. `UsernameRecoveryController.start` (lines 100-128) has `.onErrorResume(UsernameRecoveryStartException.class, exception -> Mono.just(startPolicy.toPolicy(exception)))` (lines 123-125), fully typed-exception-routed through `UsernameRecoveryStartResponsePolicy.toPolicy(UsernameRecoveryStartException)`'s `SYSTEM_FAILURE -> systemFailure(WorkflowNextStep.NONE)` branch (line 122), which builds `ApiStatus.SYSTEM_ERROR` with `SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value()` (lines 162-171).
- **Message policy:** N/A -- fully typed-exception-routed through `UsernameRecoveryStartResponsePolicy`
- **Ordered log lines:** `apim.business.rejected` WARN from the classifier (non-empty err-message) fires on the onNext envelope before this mapper throws
- **Idempotency replay:** Yes -- Username Recovery start is idempotency-protected (`IdempotentStartUsernameRecoveryService`)
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503 (CONFIRMED by real source and real passing test: `HttpStatusClassification.classify(ApiStatus.SYSTEM_ERROR)` returns `HttpStatus.SERVICE_UNAVAILABLE` directly in its switch expression; `WorkflowHttpResponses.toResponse` delegates to this classifier unchanged - it is a thin wrapper, not a competing mechanism. `AuthUsernameRecoveryControllerTest.startSystemFailureReturns503WithoutAnyCookie` asserts `HttpStatus.SERVICE_UNAVAILABLE` exactly for this condition.)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value()`
- **Citation:** `usernamerecovery/mapper/TrpSearchMemberApimResultMapper.java:39-56`; `TrpSearchMemberAdapter.java:48-56`; `UsernameRecoveryStartProtectedOperation.java:101-128`; `UsernameRecoveryController.java:100-128`; `UsernameRecoveryStartResponsePolicy.java:67-124,162-171`

### Username Recovery -- TrpSearchMemberAdapter (TRPSearchMember)
- **Condition:** member not found (two distinct mapper entry points both return `Status.NOT_FOUND`: (1) recognized `err.tpa.member.not.found`; (2) empty data items with no err-message at all - NOT the unrecognized-error exception path of the row above)
- **Detection site:** usernamerecovery/mapper/TrpSearchMemberApimResultMapper.java lines 43-46 (recognized `err.tpa.member.not.found` -> `Status.NOT_FOUND`) and lines 51-54 (empty items, no err-message -> `Status.NOT_FOUND`); routed by UsernameRecoveryStartProtectedOperation.java lines 121-124 (`case NOT_FOUND -> ... Outcome.MEMBER_NOT_FOUND`); rendered by UsernameRecoveryStartResponsePolicy.java lines 44-53 (`case MEMBER_NOT_FOUND -> MutationResponse.failure(ApiStatus.BUSINESS_REJECTED, ..., formError(UsernameRecoveryErrorKey.VERIFICATION_FAILED.value(), ...))`)
- **Handling:** returned as a normal lookup result (`Status.NOT_FOUND`), never an exception; no FailureEvent is published anywhere in this chain; no `method.execution.*` line fires (none of TrpSearchMemberAdapter, UsernameRecoveryStartProtectedOperation, or UsernameRecoveryStartResponsePolicy is `@LogExecution`-annotated or matches the broad `*Service.execute(..)` pointcut). **Confirmed by real DEV evidence, 2026-10-28, requestId 35eeecac-5b0b-4e08-b5da-fb51023be1ad**, not merely traced from source.
- **Message policy:** N/A -- normal result value, not an ExceptionMapping
- **Ordered log lines (confirmed from real DEV evidence, exact sequence and levels):** (1) http.request.start INFO (RequestLoggingWebFilter); (2) apim.business.rejected WARN (ApimExchangeOutcomeLogger, errMessage=err.tpa.member.not.found, path=/ws/NGTPA/v1/TRPSearchMember) -- this WARN line fires only for the recognized-err-message entry point, since it originates in the executor's own envelope classifier before the mapper runs; the empty-items entry point would not produce this line, since an empty-but-successful envelope is not a business rejection at the executor's classifier level; (3) http.request.failed WARN (RequestLoggingWebFilter, status=422). No ERROR line, no FailureEvent, no method.execution.* line at any point in this chain for either entry point. These 3 log lines (INFO/WARN/WARN, no ERROR) are DEV-evidenced only (requestId 35eeecac-5b0b-4e08-b5da-fb51023be1ad) - NOT pinned by any automated test.
- **Idempotency replay:** Yes -- Username Recovery start is idempotency-protected (IdempotentStartUsernameRecoveryService)
- **Origin tag today:** none
- **Proposed FailureOrigin (section 3):** FailureOrigin.DOWNSTREAM_BUSINESS
- **Public HTTP status:** 422 (CONFIRMED by real DEV evidence and by real passing tests: HttpStatusClassificationTest confirms HttpStatusClassification.classify(ApiStatus.BUSINESS_REJECTED) -> HttpStatus.UNPROCESSABLE_ENTITY; AuthUsernameRecoveryControllerTest.startMemberNotFoundReturns422WithoutAnyCookie asserts 422 exactly for this condition.)
- **Public ApiStatus:** BUSINESS_REJECTED (CONFIRMED: UsernameRecoveryStartResponsePolicy.java line 47)
- **Public error code/type:** UsernameRecoveryErrorKey.VERIFICATION_FAILED.value(), via UsernameRecoveryWorkflowMapping.formError(...) (UsernameRecoveryStartResponsePolicy.java lines 50-53). The public ApiError.type string is CONFIRMED "FORM" (UsernameRecoveryWorkflowMappingTest.formErrorProducesFormTypeErrorWithDefaultSeverity).
- **Citation:** usernamerecovery/mapper/TrpSearchMemberApimResultMapper.java:34-56; UsernameRecoveryStartProtectedOperation.java:101-128; UsernameRecoveryStartResponsePolicy.java:31-65; DEV log evidence 2026-10-28 requestId 35eeecac-5b0b-4e08-b5da-fb51023be1ad


### Username Recovery -- TrpSearchMemberAdapter (TRPSearchMember)

- **Condition:** any `ApimException` (facade-raised HTTP-error)
- **Detection site:** generic catch, `TrpSearchMemberAdapter.java:56`
- **Handling:** Same routing as the row above: `.onErrorResume(ApimException.class, e -> Mono.just(unavailable()))` (line 56) -> `UNAVAILABLE` status -> grouped with `CONTRACT_INVALID`/`UNKNOWN_OUTCOME` at `UsernameRecoveryStartProtectedOperation.java:125` -> `UsernameRecoveryStartException.systemFailure()` -> same typed-policy routing as above
- **Message policy:** N/A
- **Ordered log lines:** `ApimWebClientFacade.handleErrorBody`'s `log.error` (`ApimWebClientFacade.java:409` (`log.error`), gated by `statusCode.isError()` (line 386)); `apim.exchange.failed` ERROR
- **Idempotency replay:** Yes
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** 503. `UsernameRecoveryStartProtectedOperation.java:125` groups CONTRACT_INVALID, UNAVAILABLE and UNKNOWN_OUTCOME into `UsernameRecoveryStartException.systemFailure()`; `HttpStatusClassification` maps `SYSTEM_ERROR` to 503. Pinned by `AuthUsernameRecoveryControllerTest.startSystemFailureReturns503WithoutAnyCookie`.
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value()`
- **Citation:** `TrpSearchMemberAdapter.java:48-56`; `UsernameRecoveryStartProtectedOperation.java:101-128`

### Username Recovery -- TrpSearchMemberAdapter (TRPSearchMember)

- **Condition:** crypto/mapping failure (`ApimCryptoException`/`ApimResponseMappingException`), uncaught by either typed `onErrorResume`
- **Detection site:** same mechanism as other crypto rows (`DefaultApimExchangeExecutor`'s decryption step)
- **Handling:** Propagates past both type-specific catches in `TrpSearchMemberAdapter` (neither `TrpSearchMemberApimDownstreamException` nor `ApimException` matches a plain `RuntimeException` subtype). Caught by `UsernameRecoveryStartProtectedOperation.lookup`'s own catch-all `.onErrorMap(Exception.class, ProtectedExecutionException::new)` (line 115, confirmed a genuine `Exception`-wide filter), then the outer `execute`'s `.onErrorResume(ProtectedExecutionException.class, error -> Mono.error(UsernameRecoveryStartException.systemFailure(error)))` (lines 96-98) -- same typed-policy routing as the rows above.
- **Message policy:** N/A
- **Ordered log lines:** `apim.exchange.failed` ERROR (genuine Mono error)
- **Idempotency replay:** Yes
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- **Public HTTP status:** 503 (CONFIRMED - same routing as the unrecognized-err-message row above: this condition is grouped with CONTRACT_INVALID/UNKNOWN_OUTCOME at UsernameRecoveryStartProtectedOperation.java:125, all mapping uniformly through UsernameRecoveryStartException.systemFailure() to the SAME ApiStatus.SYSTEM_ERROR -> HttpStatusClassification.classify(...) -> HttpStatus.SERVICE_UNAVAILABLE routing. Cited: AuthUsernameRecoveryControllerTest.startSystemFailureReturns503WithoutAnyCookie; UsernameRecoveryStartProtectedOperation.java:125.)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value()`
- **Citation:** `TrpSearchMemberAdapter.java:48-56`; `UsernameRecoveryStartProtectedOperation.java:71-98`

### Login -- TrpMemberLoginAdapter (TRPMemberLogin)

- **Condition:** `IllegalStateException` (confirmed origin: `DefaultApimExchangeExecutor`'s own structural checks, inside the `postEnvelope`-wrapped chain)
- **Detection site:** `TrpMemberLoginAdapter.java:37-39`; `SharedApimTrpMemberLoginTransport.post` confirmed a plain `executor.postEnvelope(exchange)` passthrough (line 44, no `IllegalStateException` of its own)
- **Handling:** `.onErrorResume(IllegalStateException.class, ignored -> Mono.just(mapper.failure(CredentialAuthenticationStatus.UNAVAILABLE)))` (lines 37-39). **Routing confirmed:** `LoginController.startLogin` (lines 65-79) has its own reactive `.onErrorResume(x -> Mono.just(LoginHttpResponses.toResponse(loginStartMapper.systemUnavailable())))` (lines 75-76) as a further safety net, though this specific condition is already swallowed one layer below at the adapter.
- **Message policy:** N/A -- swallowed to `UNAVAILABLE` at the adapter level
- **Ordered log lines:** `apim.exchange.failed` ERROR fires before the swallow (confirmed structural guarantee, since the only `IllegalStateException` sources are inside the executor's own `doOnEach`-wrapped chain)
- **Idempotency replay:** Yes -- Login start is idempotency-protected
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 502. `mapper.failure(UNAVAILABLE)` -> `LoginStartWebMapper.toMutationResponse` `case UNAVAILABLE ->` (lines 61-63): `failure(ApiStatus.SYSTEM_ERROR, LoginErrorKey.CREDENTIALS_UNAVAILABLE.value(), "DOWNSTREAM")`. LoginHttpResponses.statusForCode has an EXPLICIT branch for CREDENTIALS_UNAVAILABLE (CONFIRMED, LoginHttpResponses.java:43: `if (code.equals(LoginErrorKey.CREDENTIALS_UNAVAILABLE.value())) return HttpStatus.BAD_GATEWAY;`), checked before the generic ApiStatus.SYSTEM_ERROR -> HttpStatus.BAD_GATEWAY fallback (line 22) ever runs - the FINAL public status (502) is unchanged, but it is reached via this explicit, code-specific branch, not the generic fallback (corrected mechanism, earlier text was wrong on this point).
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `LoginErrorKey.CREDENTIALS_UNAVAILABLE.value()` (`LoginStartWebMapper.java:61-63`)
- **Citation:** `TrpMemberLoginAdapter.java:30-40`; `SharedApimTrpMemberLoginTransport.java:27-45`; `LoginController.java:49-80`; `LoginStartWebMapper.java:10-65`; `LoginHttpResponses.java:11-55`

### Login -- TrpGetAccountListAdapter (TRPGetAccountList)

- **Condition:** any error, sub-cause: HTTP-error `ApimException`
- **Detection site:** `TrpGetAccountListAdapter.java:29-32`; `SharedApimTrpGetAccountListTransport.post` confirmed plain `executor.postEnvelope(exchange)` passthrough (line 47)
- **Handling:** `.onErrorReturn(LoginAccountAuthorizationResult.unavailable())` (line 32) -- no exception-type filter at all, catches every throwable indiscriminately
- **Message policy:** N/A -- `onErrorReturn` provides no access to the throwable
- **Ordered log lines:** `ApimWebClientFacade.handleErrorBody`'s `log.error`; `apim.exchange.failed` ERROR
- **Idempotency replay:** Yes. The replay codec stores the 502 (`LoginOtpVerifyReplayCodecHttpStatusTest.mapsAccountListUnavailableTo502`).
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** 502. `LoginOtpVerifyService.java:312-325` (`continueToAccounts`) turns `LoginAccountAuthorizationStatus.UNAVAILABLE` into `VerifyLoginOtpStatus.ACCOUNT_LIST_UNAVAILABLE`; `LoginHttpResponses.java:22` maps `SYSTEM_ERROR` to `BAD_GATEWAY` (no code-specific branch). Pinned for the replay by `LoginOtpVerifyReplayCodecHttpStatusTest.mapsAccountListUnavailableTo502`.
- **Public ApiStatus:** `ApiStatus.SYSTEM_ERROR` (`LoginOtpVerifyWebMapper.java:33`).
- **Public error code/type:** type `FORM`, source `BFF`, code `LoginErrorKey.ACCOUNT_LIST_UNAVAILABLE.value()` (`LoginOtpVerifyWebMapper.java:46-49,69-70`). Pinned by `LoginOtpVerifyWebMapperTest.accountListUnavailableMapsToSystemErrorWithAccountListUnavailableCode`.
- **Citation:** `TrpGetAccountListAdapter.java:26-33`; `SharedApimTrpGetAccountListTransport.java:16-48`

### Login -- TrpGetAccountListAdapter (TRPGetAccountList)

- **Condition:** any error, sub-cause: crypto/mapping failure
- **Detection site:** same unfiltered catch-all
- **Handling:** `.onErrorReturn(LoginAccountAuthorizationResult.unavailable())`
- **Message policy:** N/A
- **Ordered log lines:** `apim.exchange.failed` ERROR
- **Idempotency replay:** Yes. The replay codec stores the 502 (`LoginOtpVerifyReplayCodecHttpStatusTest.mapsAccountListUnavailableTo502`).
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- **Public HTTP status:** 502. `LoginOtpVerifyService.java:312-325` (`continueToAccounts`) turns `LoginAccountAuthorizationStatus.UNAVAILABLE` into `VerifyLoginOtpStatus.ACCOUNT_LIST_UNAVAILABLE`; `LoginHttpResponses.java:22` maps `SYSTEM_ERROR` to `BAD_GATEWAY` (no code-specific branch). Pinned for the replay by `LoginOtpVerifyReplayCodecHttpStatusTest.mapsAccountListUnavailableTo502`.
- **Public ApiStatus:** `ApiStatus.SYSTEM_ERROR` (`LoginOtpVerifyWebMapper.java:33`).
- **Public error code/type:** type `FORM`, source `BFF`, code `LoginErrorKey.ACCOUNT_LIST_UNAVAILABLE.value()` (`LoginOtpVerifyWebMapper.java:46-49,69-70`). Pinned by `LoginOtpVerifyWebMapperTest.accountListUnavailableMapsToSystemErrorWithAccountListUnavailableCode`.
- **Citation:** `TrpGetAccountListAdapter.java:26-33`; `SharedApimTrpGetAccountListTransport.java:16-48`

### Login -- TrpGetAccountListAdapter (TRPGetAccountList)

- **Condition:** any error, sub-cause: connection-level failure
- **Detection site:** same unfiltered catch-all
- **Handling:** `.onErrorReturn(LoginAccountAuthorizationResult.unavailable())`
- **Message policy:** N/A
- **Ordered log lines:** `apim.exchange.failed` ERROR
- **Idempotency replay:** Yes. The replay codec stores the 502 (`LoginOtpVerifyReplayCodecHttpStatusTest.mapsAccountListUnavailableTo502`).
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** 502. `LoginOtpVerifyService.java:312-325` (`continueToAccounts`) turns `LoginAccountAuthorizationStatus.UNAVAILABLE` into `VerifyLoginOtpStatus.ACCOUNT_LIST_UNAVAILABLE`; `LoginHttpResponses.java:22` maps `SYSTEM_ERROR` to `BAD_GATEWAY` (no code-specific branch). Pinned for the replay by `LoginOtpVerifyReplayCodecHttpStatusTest.mapsAccountListUnavailableTo502`.
- **Public ApiStatus:** `ApiStatus.SYSTEM_ERROR` (`LoginOtpVerifyWebMapper.java:33`).
- **Public error code/type:** "FORM" / LoginErrorKey.ACCOUNT_LIST_UNAVAILABLE.value() = "err.login.accountList.unavailable" (source "BFF")
- **Citation:** `TrpGetAccountListAdapter.java:26-33`; `ApimWebClientFacade.java:212-241`

### Account Balance -- ApimAccountBalanceSourceAdapter (TRPGetMemberAccBal)

- **Condition:** `err.tpa.account.invalid` -> `ACCOUNT_NOT_FOUND`
- **Detection site:** `ApimAccountBalanceSourceAdapter.java:54-55`
- **Handling:** `AccountBalanceRetrievalException.accountNotFound()`, mapped via `AccountBalanceExceptionMappings.retrievalFailure()` -> `AccountBalanceRetrievalResponsePolicy.map()`
- **Message policy:** `MessagePolicy.NONE` (forced for every reason)
- **Ordered log lines:** `apim.business.rejected` WARN from classifier; WARN at mapping (`reason != SYSTEM_FAILURE`). `GetAccountBalanceService.get` (line 26) -- neither `@LogExecution`-annotated nor matching the broad pointcut (method named `get`, not `execute`). ApimAccountBalanceSourceAdapter is NOT `@LogExecution`-annotated (confirmed absent from the 10-class list). Its use-case entry point (`GetAccountBalanceService`/`GetAccountGainLossService`, confirmed by direct source read) uses a method named `get(...)`, not `execute(...)`, so the broad `*Service.execute(..)` pointcut does not match either. Neither `ExecutionLoggingAspect` pointcut fires for this row.
- **Idempotency replay:** No -- read-only query
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_BUSINESS`
- **Public HTTP status:** 422 (`AccountBalanceRetrievalResponsePolicy.java:22`, `HttpStatus.UNPROCESSABLE_ENTITY`)
- **Public ApiStatus:** `BUSINESS_REJECTED`
- **Public error code/type:** `ApiError.business("err.ab.account.not.exist", null, SOURCE_SERVER)` (`AccountBalanceRetrievalResponsePolicy.java:15`)
- **Citation:** `AccountBalanceRetrievalResponsePolicy.java:13-24`; `ApimAccountBalanceSourceAdapter.java:54-55`; `GetAccountBalanceService.java:26`

### Account Balance -- ApimAccountBalanceSourceAdapter (TRPGetMemberAccBal)

- **Condition:** `err.tpa.member.not.found` -> `ACCOUNT_MISMATCH`
- **Detection site:** `ApimAccountBalanceSourceAdapter.java:56-57`
- **Handling:** `AccountBalanceRetrievalException.accountMismatch()`, same policy path
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** WARN classifier; WARN mapping. ApimAccountBalanceSourceAdapter is NOT `@LogExecution`-annotated (confirmed absent from the 10-class list). Its use-case entry point (`GetAccountBalanceService`/`GetAccountGainLossService`, confirmed by direct source read) uses a method named `get(...)`, not `execute(...)`, so the broad `*Service.execute(..)` pointcut does not match either. Neither `ExecutionLoggingAspect` pointcut fires for this row.
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_BUSINESS`
- **Public HTTP status:** 422
- **Public ApiStatus:** `BUSINESS_REJECTED`
- **Public error code/type:** `ApiError.business("err.ab.account.mismatch", null, SOURCE_SERVER)` (`AccountBalanceRetrievalResponsePolicy.java:16`)
- **Citation:** `AccountBalanceRetrievalResponsePolicy.java:13-24`; `ApimAccountBalanceSourceAdapter.java:56-57`

### Account Balance -- ApimAccountBalanceSourceAdapter (TRPGetMemberAccBal)

- **Condition:** catch-all, sub-cause: unrecognized non-blank error
- **Detection site:** `ApimAccountBalanceSourceAdapter.java:58-59`
- **Handling:** `AccountBalanceRetrievalException.systemFailure()`. `ApimResponseValidator.requireSuccessData` (`ApimResponseValidator.java:27-45`) treats an empty `data` array as VALID when `err-message` is empty (line 39-43 only rejects `data == null`, not empty). `ApimEnvelopeSchemaClassifier` (logging only, confirmed never influences adapter behavior) instead treats empty `data` + empty `err-message` as malformed (`apim.contract.invalid` ERROR, confirmed production incident on TRPMemberLogin, `ApimEnvelopeSchemaClassifier.java:15-21`). The two deliberately disagree on this exact edge case: the validator's job is correctness of business behavior; the classifier's job is operational alerting.
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `apim.business.rejected` WARN from classifier; ERROR at mapping (`reason == SYSTEM_FAILURE`)
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503 (`AccountBalanceRetrievalResponsePolicy.java:17-22`)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, SOURCE_SERVER)` (`AccountBalanceRetrievalResponsePolicy.java:21`)
- **Citation:** `AccountBalanceRetrievalResponsePolicy.java:13-24`; `ApimAccountBalanceSourceAdapter.java:58-59`

### Account Balance -- ApimAccountBalanceSourceAdapter (TRPGetMemberAccBal)

- **Condition:** catch-all, sub-cause: missing/empty data (inner `try{requireSingleData(requireSuccessData(...))}catch`)
- **Detection site:** `ApimAccountBalanceSourceAdapter.java:60-67`
- **Handling:** `AccountBalanceRetrievalException.systemFailure()`
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `apim.contract.invalid` ERROR from classifier; ERROR at mapping
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, SOURCE_SERVER)`
- **Citation:** `ApimAccountBalanceSourceAdapter.java:60-67`

### Account Balance -- ApimAccountBalanceSourceAdapter (TRPGetMemberAccBal)

- **Condition:** catch-all, sub-cause: crypto/mapping failure (confirmed account-summary-500 incident mechanism)
- **Detection site:** `ApimAccountBalanceSourceAdapter.java:69-73` (outer `.onErrorMap`)
- **Handling:** `e instanceof AccountBalanceRetrievalException ? e : systemFailure()`
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `apim.exchange.failed` ERROR (genuine Mono error); ERROR at mapping
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, SOURCE_SERVER)`
- **Citation:** `ApimAccountBalanceSourceAdapter.java:69-73`; `ApimPayloadCryptoService.java:158-166`

### Account Balance -- ApimAccountBalanceSourceAdapter (TRPGetMemberAccBal)

- **Condition:** catch-all, sub-cause: connection-level failure
- **Detection site:** `ApimAccountBalanceSourceAdapter.java:69-73`
- **Handling:** same outer `.onErrorMap`
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `apim.exchange.failed` ERROR; ERROR at mapping
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, SOURCE_SERVER)`
- **Citation:** `ApimAccountBalanceSourceAdapter.java:69-73`

### Account Gain/Loss -- ApimAccountGainLossSourceAdapter (TRPGetInvestGL)

- **Condition:** `err.tpa.fund.switching.in.progress`
- **Detection site:** `ApimAccountGainLossSourceAdapter.java:63-65`
- **Handling:** `AccountGainLossRetrievalException.fundSwitchingInProgress()`
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `apim.business.rejected` WARN classifier; WARN at mapping (`reason==SYSTEM_FAILURE` false). `GetAccountGainLossService.get` (line 32) -- neither `@LogExecution`-annotated nor matching the broad pointcut (method named `get`, not `execute`). ApimAccountGainLossSourceAdapter is NOT `@LogExecution`-annotated (confirmed absent from the 10-class list). Its use-case entry point (`GetAccountBalanceService`/`GetAccountGainLossService`, confirmed by direct source read) uses a method named `get(...)`, not `execute(...)`, so the broad `*Service.execute(..)` pointcut does not match either. Neither `ExecutionLoggingAspect` pointcut fires for this row.
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_BUSINESS`
- **Public HTTP status:** 422 (`AccountGainLossRetrievalResponsePolicy.java:17-23`)
- **Public ApiStatus:** `BUSINESS_REJECTED`
- **Public error code/type:** `ApiError.business("err.fund.switching.in.progress", null, List.of(), SOURCE_SERVER)` (`AccountGainLossRetrievalResponsePolicy.java:21-22`)
- **Citation:** `AccountGainLossRetrievalResponsePolicy.java:13-31`; `ApimAccountGainLossSourceAdapter.java:63-65`; `GetAccountGainLossService.java:32`

### Account Gain/Loss -- ApimAccountGainLossSourceAdapter (TRPGetInvestGL)

- **Condition:** catch-all, sub-cause: unrecognized non-blank error (adapter's own `LOG.warn("event=account-gain-loss.downstream-rejected"...)`)
- **Detection site:** `ApimAccountGainLossSourceAdapter.java:66-71`
- **Handling:** `AccountGainLossRetrievalException.systemFailure()`. `ApimResponseValidator.requireSuccessData` (`ApimResponseValidator.java:27-45`) treats an empty `data` array as VALID when `err-message` is empty (line 39-43 only rejects `data == null`, not empty). `ApimEnvelopeSchemaClassifier` (logging only, confirmed never influences adapter behavior) instead treats empty `data` + empty `err-message` as malformed (`apim.contract.invalid` ERROR, confirmed production incident on TRPMemberLogin, `ApimEnvelopeSchemaClassifier.java:15-21`). The two deliberately disagree on this exact edge case: the validator's job is correctness of business behavior; the classifier's job is operational alerting.
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `apim.business.rejected` WARN classifier; adapter's own `event=account-gain-loss.downstream-rejected operation=TRPGetInvestGL errorCode={}` WARN (sanitized error code only, lines 67-70); ERROR at mapping
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, SOURCE_SERVER)` (`AccountGainLossRetrievalResponsePolicy.java:28`)
- **Citation:** `ApimAccountGainLossSourceAdapter.java:66-71`

### Account Gain/Loss -- ApimAccountGainLossSourceAdapter (TRPGetInvestGL)

- **Condition:** catch-all, sub-cause: missing/empty data
- **Detection site:** `ApimAccountGainLossSourceAdapter.java:73-92`
- **Handling:** `AccountGainLossRetrievalException.systemFailure()`
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `apim.contract.invalid` ERROR classifier; ERROR at mapping
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, SOURCE_SERVER)`
- **Citation:** `ApimAccountGainLossSourceAdapter.java:73-92`

### Account Gain/Loss -- ApimAccountGainLossSourceAdapter (TRPGetInvestGL)

- **Condition:** catch-all, sub-cause: crypto/mapping failure
- **Detection site:** `ApimAccountGainLossSourceAdapter.java:94-98` (outer `.onErrorMap`)
- **Handling:** `exception instanceof AccountGainLossRetrievalException ? exception : systemFailure()`
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `apim.exchange.failed` ERROR; ERROR at mapping
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, SOURCE_SERVER)`
- **Citation:** `ApimAccountGainLossSourceAdapter.java:94-98`

### Account Gain/Loss -- ApimAccountGainLossSourceAdapter (TRPGetInvestGL)

- **Condition:** catch-all, sub-cause: connection-level failure
- **Detection site:** `ApimAccountGainLossSourceAdapter.java:94-101` (outer `.onErrorMap`/outer try-catch)
- **Handling:** same outer catch-all
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `apim.exchange.failed` ERROR; ERROR at mapping
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, SOURCE_SERVER)`
- **Citation:** `ApimAccountGainLossSourceAdapter.java:94-101`

### Fund Price -- ApimFundPriceSourceAdapter

- **Condition:** `err.tpa.invalid.date`
- **Detection site:** `ApimFundPriceSourceAdapter.java:164-166`
- **Handling:** `FundPriceInvalidDateException.forInvalidDate()`, mapped via `FundPriceExceptionMappings.invalidDate()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.business.rejected` WARN classifier; `LogLevel.WARN` at mapping (`FundPriceExceptionMappings.java:57`). ApimFundPriceSourceAdapter is NOT `@LogExecution`-annotated (confirmed absent from the 10-class list). Its use-case entry point (`GetAccountBalanceService`/`GetAccountGainLossService`, confirmed by direct source read) uses a method named `get(...)`, not `execute(...)`, so the broad `*Service.execute(..)` pointcut does not match either. Neither `ExecutionLoggingAspect` pointcut fires for this row.
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_BUSINESS`
- **Public HTTP status:** 400 (`FundPriceExceptionMappings.java:51`)
- **Public ApiStatus:** `VALIDATION_FAILED`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_PRICE_REQUEST_INVALID`
- **Citation:** `FundPriceExceptionMappings.java:40-61`; `ApimFundPriceSourceAdapter.java:164-166`

### Fund Price -- ApimFundPriceSourceAdapter

- **Condition:** catch-all, sub-cause: missing response / unrecognized non-blank error
- **Detection site:** `ApimFundPriceSourceAdapter.java:159-169`
- **Handling:** `FundPriceRetrievalException.systemFailure()`. `ApimResponseValidator.requireSuccessData` (`ApimResponseValidator.java:27-45`) treats an empty `data` array as VALID when `err-message` is empty (line 39-43 only rejects `data == null`, not empty). `ApimEnvelopeSchemaClassifier` (logging only, confirmed never influences adapter behavior) instead treats empty `data` + empty `err-message` as malformed (`apim.contract.invalid` ERROR, confirmed production incident on TRPMemberLogin, `ApimEnvelopeSchemaClassifier.java:15-21`). The two deliberately disagree on this exact edge case: the validator's job is correctness of business behavior; the classifier's job is operational alerting.
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** classifier event depending on sub-condition; `LogLevel.ERROR` at mapping
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503 (`FundPriceExceptionMappings.java:28`)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_PRICE_RETRIEVAL_UNAVAILABLE`
- **Citation:** `FundPriceExceptionMappings.java:18-38`; `ApimFundPriceSourceAdapter.java:159-169`

### Fund Price -- ApimFundPriceSourceAdapter

- **Condition:** catch-all, sub-cause: malformed item count
- **Detection site:** `ApimFundPriceSourceAdapter.java:170-178`
- **Handling:** `FundPriceRetrievalException.systemFailure()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.contract.invalid` ERROR classifier; ERROR at mapping
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_PRICE_RETRIEVAL_UNAVAILABLE`
- **Citation:** `ApimFundPriceSourceAdapter.java:170-178`

### Fund Price -- ApimFundPriceSourceAdapter

- **Condition:** catch-all, sub-cause: crypto/mapping or connection-level failure (outer `.onErrorMap`)
- **Detection site:** `ApimFundPriceSourceAdapter.java:180-185`
- **Handling:** `exception instanceof FundPriceRetrievalException||FundPriceInvalidDateException ? exception : systemFailure()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.exchange.failed` ERROR; ERROR at mapping
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_PRICE_RETRIEVAL_UNAVAILABLE`
- **Citation:** `ApimFundPriceSourceAdapter.java:180-185` (both crypto and transport causes share one undifferentiated sub-row since the adapter does not distinguish them structurally)

### Fund Price -- ApimFundPriceModeSourceAdapter (TRPGetFundPriceMode)

- **Condition:** catch-all, sub-cause: missing response / any non-blank error (no invalid-date branch; confirmed every non-blank error maps to the single generic exception, no text-specific branching)
- **Detection site:** `ApimFundPriceModeSourceAdapter.java:72-83`
- **Handling:** `FundPriceRetrievalException.systemFailure()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** classifier event depending on sub-condition; ERROR at mapping. ApimFundPriceModeSourceAdapter is NOT `@LogExecution`-annotated (confirmed absent from the 10-class list). Its use-case entry point (`GetAccountBalanceService`/`GetAccountGainLossService`, confirmed by direct source read) uses a method named `get(...)`, not `execute(...)`, so the broad `*Service.execute(..)` pointcut does not match either. Neither `ExecutionLoggingAspect` pointcut fires for this row.
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_PRICE_RETRIEVAL_UNAVAILABLE`
- **Citation:** `ApimFundPriceModeSourceAdapter.java:72-83`

### Fund Price -- ApimFundPriceModeSourceAdapter

- **Condition:** catch-all, sub-cause: malformed single-item data
- **Detection site:** `ApimFundPriceModeSourceAdapter.java:84-89`
- **Handling:** `FundPriceRetrievalException.systemFailure()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.contract.invalid` ERROR; ERROR at mapping
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_PRICE_RETRIEVAL_UNAVAILABLE`
- **Citation:** `ApimFundPriceModeSourceAdapter.java:84-89`

### Fund Price -- ApimFundPriceModeSourceAdapter

- **Condition:** catch-all, sub-cause: crypto/mapping or connection-level failure (outer `.onErrorMap`)
- **Detection site:** `ApimFundPriceModeSourceAdapter.java:91-95`
- **Handling:** `exception instanceof FundPriceRetrievalException ? exception : systemFailure()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.exchange.failed` ERROR; ERROR at mapping
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_PRICE_RETRIEVAL_UNAVAILABLE`
- **Citation:** `ApimFundPriceModeSourceAdapter.java:91-95` (both crypto and transport causes share one undifferentiated sub-row)

### Fund Comparison -- ApimFundComparisonOptionsSourceAdapter (TRPGetFundCmpOpt)

- **Condition:** catch-all, sub-cause: missing response / non-blank error
- **Detection site:** `ApimFundComparisonOptionsSourceAdapter.java:73-83`
- **Handling:** `FundComparisonRetrievalException.systemFailure()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** classifier event depending on sub-condition; ERROR at mapping. ApimFundComparisonOptionsSourceAdapter is NOT `@LogExecution`-annotated (confirmed absent from the 10-class list). Its use-case entry point (`GetAccountBalanceService`/`GetAccountGainLossService`, confirmed by direct source read) uses a method named `get(...)`, not `execute(...)`, so the broad `*Service.execute(..)` pointcut does not match either. Neither `ExecutionLoggingAspect` pointcut fires for this row.
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE`
- **Citation:** `ApimFundComparisonOptionsSourceAdapter.java:73-83`

### Fund Comparison -- ApimFundComparisonOptionsSourceAdapter

- **Condition:** catch-all, sub-cause: malformed fund-list data (`normalize`'s own `IllegalStateException` for null/non-singleton data or empty fund-list)
- **Detection site:** `ApimFundComparisonOptionsSourceAdapter.java:85-89,98-115`
- **Handling:** `FundComparisonRetrievalException.systemFailure()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.contract.invalid` ERROR; ERROR at mapping
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE`
- **Citation:** `ApimFundComparisonOptionsSourceAdapter.java:85-89,98-115`

### Fund Comparison -- ApimFundComparisonOptionsSourceAdapter

- **Condition:** catch-all, sub-cause: crypto/mapping or connection-level failure (outer `.onErrorMap`)
- **Detection site:** `ApimFundComparisonOptionsSourceAdapter.java:91-95`
- **Handling:** `exception instanceof FundComparisonRetrievalException ? exception : systemFailure()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.exchange.failed` ERROR; ERROR at mapping
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE`
- **Citation:** `ApimFundComparisonOptionsSourceAdapter.java:91-95` (both crypto and transport causes share one undifferentiated sub-row)

### Fund Fact Sheet -- ApimFundFactSheetSourceAdapter (TRPGetFundFactSheet)

- **Condition:** missing response, OR non-empty error, OR empty `data` array (confirmed by design doc C-4/C-5: no-funds is `data: [{"FundFactSheet": []}]`, a non-empty data list whose single item has an empty FundFactSheet list -- an actually EMPTY `data` array is distinct and is malformed, matching the code's own handling)
- **Detection site:** `ApimFundFactSheetSourceAdapter.java:95-105` (confirmed: `response==null` line 95-97; `errMessage==null||!errMessage.isEmpty()` line 99-101; `items==null||items.isEmpty()` line 103-105)
- **Handling:** `FundFactSheetRetrievalException.systemFailure()` for every sub-condition above
- **Message policy:** `MessagePolicy.NONE` (`FundFactSheetExceptionMappings.java`, O-12: message always null)
- **Ordered log lines:** classifier event depending on sub-condition (missing response/empty data -> `apim.contract.invalid` ERROR; non-empty error -> `apim.business.rejected` WARN); `LogLevel.ERROR` at mapping
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503 (`FundFactSheetExceptionMappings.java:29`)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"SYSTEM"` / `ErrorCodes.FUND_FACT_SHEET_RETRIEVAL_UNAVAILABLE`
- **Citation:** `ApimFundFactSheetSourceAdapter.java:95-105`; `docs/design/11-fund-fact-sheet/fund-fact-sheet-apim-contract.md` (C-4/C-5, lines 94-99: confirms empty `data` is malformed, not a valid no-funds case, resolving the 's incorrectly flagged Javadoc contradiction)

### Fund Fact Sheet -- ApimFundFactSheetSourceAdapter

- **Condition:** null item/list/row (adapter's own `LOG.error`, confirmed events `EVENT_NULL_ITEM`/`EVENT_NULL_LIST`/`EVENT_NULL_ROW`)
- **Detection site:** `ApimFundFactSheetSourceAdapter.java:106-126`
- **Handling:** `FundFactSheetRetrievalException.systemFailure()`
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** feature-level `LOG.error` (one of the three named events, no value logged, lines 109/114/119); ERROR at mapping. Per design doc D-19: "the executor already logs contract-invalid, business-rejected and exchange-failed outcomes" -- these three adapter-level logs cover a gap the executor's classifier cannot see (structural nulls inside an otherwise well-formed, non-empty data list).
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"SYSTEM"` / `ErrorCodes.FUND_FACT_SHEET_RETRIEVAL_UNAVAILABLE`
- **Citation:** `ApimFundFactSheetSourceAdapter.java:106-126`; `docs/design/11-fund-fact-sheet/fund-fact-sheet-apim-contract.md` (D-19)

### Fund Fact Sheet -- ApimFundFactSheetSourceAdapter

- **Condition:** catch-all, sub-cause: crypto/mapping or connection-level failure (outer `.onErrorMap`)
- **Detection site:** `ApimFundFactSheetSourceAdapter.java:85-90`
- **Handling:** `exception instanceof FundFactSheetRetrievalException ? exception : systemFailure()`
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `apim.exchange.failed` ERROR; ERROR at mapping
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"SYSTEM"` / `ErrorCodes.FUND_FACT_SHEET_RETRIEVAL_UNAVAILABLE`
- **Citation:** `ApimFundFactSheetSourceAdapter.java:85-90` (both crypto and transport causes share one undifferentiated sub-row)

### Personal Information -- ApimUpdatePersonalInformationAdapter (TRPUpdMemberInfo)

- **Condition:** empty/all-null data items
- **Detection site:** `ApimUpdatePersonalInformationAdapter.java:61-67` (`toResults`, called via `.map()` on `executor.postEnvelope(request)`, line 44; this adapter also calls `ApimResponseValidator.requireSuccessData` at line 61, making it a member of the validator pattern for its own missing/empty-data sub-case)
- **Handling:** `throw new ApimException(BAD_GATEWAY, APIM_RESPONSE_INVALID, ...)` (lines 63-67)
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.contract.invalid` ERROR classifier; `http.api.error` ERROR, no stack trace. Confirmed `@LogExecution(value="apim.updatePersonalInformation", logArgs=true)` directly on this method (lines 32-33) -- ApimUpdatePersonalInformationAdapter is confirmed `@LogExecution`-annotated (unfiltered grep, 10-class list). A failure here also produces `method.execution.error` ERROR with full stack trace (`ExecutionLoggingAspect.java:161-170`).
- **Idempotency replay:** No -- not found under any idempotency package
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 502
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `APIM_RESPONSE_INVALID`
- **Citation:** `ApimUpdatePersonalInformationAdapter.java:31-44,58-67`

### Personal Information -- ApimUpdatePersonalInformationAdapter

- **Condition:** no item matches the `selected` account
- **Detection site:** `ApimUpdatePersonalInformationAdapter.java:70-75`
- **Handling:** `throw new ApimException(BAD_GATEWAY, APIM_RESPONSE_INVALID, ...)`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** no classifier-level flag expected (raw data structurally fine); `http.api.error` ERROR, no stack trace. ApimUpdatePersonalInformationAdapter is confirmed `@LogExecution`-annotated (unfiltered grep, 10-class list). A failure here also produces `method.execution.error` ERROR with full stack trace (`ExecutionLoggingAspect.java:161-170`).
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 502
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `APIM_RESPONSE_INVALID`
- **Citation:** `ApimUpdatePersonalInformationAdapter.java:70-75`

### Reference Date -- ApimReferenceDateRefreshAdapter (TRPGetWebSysDate)

- **Condition:** missing payload / **non-empty** err-message field / missing data (corrected: `StringUtils.hasText(getErrMessage())` is true for a NON-blank, non-empty err-message, not a blank one; the of "blank err-message field" was backwards)
- **Detection site:** `ApimReferenceDateRefreshAdapter.java:47-54` (confirmed: `payload==null` line 49; `StringUtils.hasText(payload.getErrMessage())` line 50 throws on a genuinely NON-empty err-message; `CollectionUtils.isEmpty(payload.getData())` line 51 for missing/empty data)
- **Handling:** `throw invalid(...)` -> `ApimException(BAD_GATEWAY, APIM_RESPONSE_INVALID, message)`, called via `.map(this::toReferenceDate)` on `apimExchangeExecutor.postEnvelope(request)` (line 44)
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.contract.invalid` ERROR classifier for missing-payload/missing-data sub-cases; `apim.business.rejected` WARN for the non-empty-err-message sub-case; `http.api.error` ERROR, no stack trace. `RefreshReferenceDateService.execute(RefreshReferenceDateCommand)` (line 26) IS the use-case entry point and matches the broad pointcut by method name. ApimReferenceDateRefreshAdapter is NOT `@LogExecution`-annotated (confirmed absent from the 10-class list). Its use-case entry point, `RefreshReferenceDateService.execute(...)`, is confirmed by direct source read to match the broad pointcut (`execution(public * ...usecase..*Service.execute(..))`, `ExecutionLoggingAspect.java:52-56`), so `method.execution.error` ERROR with full stack trace also fires there.
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 502
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `APIM_RESPONSE_INVALID`
- **Citation:** `ApimReferenceDateRefreshAdapter.java:47-54`; `RefreshReferenceDateService.java:25-31`

### Reference Date -- ApimReferenceDateRefreshAdapter

- **Condition:** unparsable date (`DateTimeParseException` on a present, well-formed-looking field)
- **Detection site:** `ApimReferenceDateRefreshAdapter.java:55-63` (confirmed: catches `DateTimeParseException` from `LocalDate.parse(sysDate.trim(), DATE_FORMATTER)`, wraps as a new `ApimException` preserving `ex` as cause)
- **Handling:** `throw new ApimException(BAD_GATEWAY, APIM_RESPONSE_INVALID, "APIM sys-date must use dd/MM/yyyy format.", ex)`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** no classifier-level flag expected (field was present, non-blank, structurally fine from the classifier's perspective); `http.api.error` ERROR, no stack trace. ApimReferenceDateRefreshAdapter is NOT `@LogExecution`-annotated (confirmed absent from the 10-class list). Its use-case entry point, `RefreshReferenceDateService.execute(...)`, is confirmed by direct source read to match the broad pointcut (`execution(public * ...usecase..*Service.execute(..))`, `ExecutionLoggingAspect.java:52-56`), so `method.execution.error` ERROR with full stack trace also fires there.
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- **Public HTTP status:** 502
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `APIM_RESPONSE_INVALID`
- **Citation:** `ApimReferenceDateRefreshAdapter.java:55-63`

### Shared `ApimResponseValidator`-reliant adapters (Contribution Summary, Member Info, Notice Message, Notification Read Status, Reference Data Countries, General Metadata -- exactly these 6 confirmed by direct source read of every call site)

- **Condition:** any non-empty `err-message` on an otherwise successful HTTP exchange
- **Detection site:** `ApimResponseValidator.java:36-38`. `ApimResponseValidator.requireSuccessData` (`ApimResponseValidator.java:27-45`) treats an empty `data` array as VALID when `err-message` is empty (line 39-43 only rejects `data == null`, not empty). `ApimEnvelopeSchemaClassifier` (logging only, confirmed never influences adapter behavior) instead treats empty `data` + empty `err-message` as malformed (`apim.contract.invalid` ERROR, confirmed production incident on TRPMemberLogin, `ApimEnvelopeSchemaClassifier.java:15-21`). The two deliberately disagree on this exact edge case: the validator's job is correctness of business behavior; the classifier's job is operational alerting.
- **Handling:** mapped via `ApimExceptionMappings.upstream()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.contract.invalid` ERROR classifier (per section 3's explicit rule: every `ApimResponseValidator` caller's non-empty err-message is `DOWNSTREAM_CONTRACT` regardless of recognition); `http.api.error` ERROR, no stack trace. 4 of these 6 ARE `@LogExecution`-annotated (`ApimNotificationReadStatusAdapter`, `ApimReferenceDataCountriesAdapter`, `ApimContributionSummaryAdapter`, `ApimNoticeMessageAdapter`, all confirmed in the 10-class list), so those 4 also produce `method.execution.error` ERROR with full stack trace. The remaining 2 (`ApimMemberInfoAdapter`, `ApimGeneralMetadataSourceAdapter`) are confirmed NOT annotated and their use-case entry points were not read to confirm the broad pointcut.
- **Idempotency replay:** No -- all 6 are read-only or non-mutating queries
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 502
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `APIM_RESPONSE_INVALID`
- **Citation:** `ApimResponseValidator.java:27-45`; confirmed sites: `ApimContributionSummaryAdapter.java:54` (`@LogExecution` line 26), `ApimMemberInfoAdapter.java:59` (not annotated), `ApimNoticeMessageAdapter.java:72` (`@LogExecution` line 43), `ApimNotificationReadStatusAdapter.java:67` (`@LogExecution` line 32), `ApimReferenceDataCountriesAdapter.java:50` (`@LogExecution` line 28), `ApimGeneralMetadataSourceAdapter.java:88` (not annotated)

## 2. Terms

- `FailureOrigin.DOWNSTREAM_*`: internal log classification only. Never serialized into a response.
- `ApiStatus.DOWNSTREAM_REJECTED`/`ApiStatus.DOWNSTREAM_ERROR`: internal workflow values only; `WorkflowPublicApiStatusNormalizer` maps them to `BUSINESS_REJECTED`/`SYSTEM_ERROR` before rendering.
- Public response: no field may contain any `DOWNSTREAM_*` value (section 6).

## 3. Classification rules (approved by Nick)

- Recognised err-message code -> `DOWNSTREAM_BUSINESS`
- Unrecognised non-blank err-message (incl. all `ApimResponseValidator` callers) -> `DOWNSTREAM_CONTRACT`
- Missing response/err-message, or missing/empty data on success -> `DOWNSTREAM_CONTRACT`
- `ApimCryptoException`/`ApimResponseMappingException`/unparsable body -> `DOWNSTREAM_INVALID_RESPONSE`
- HTTP 5xx / timeout / `WebClientRequestException` -> `DOWNSTREAM_TRANSPORT`
- `ApimProfileResolutionException` (our own config) -> `LOCAL_SYSTEM`
- Specific `DOWNSTREAM_BUSINESS` rows (6 total): Registration `err.tpa.member.not.found`, Username Recovery member-not-found, Account Balance `NOT_FOUND`/`MISMATCH` (2), Account Gain/Loss fund-switching, Fund Price invalid-date
- Catch-all `systemFailure()`/`onErrorReturn`/`safeFailure`: classified by cause, split into per-cause sub-rows; public exception/status/body unchanged across sub-causes.

## 4. Approved exceptions to the M2b "no change" rules

- **E1:** Registration `err.tpa.member.not.found` DOWNSTREAM_CONTRACT -> DOWNSTREAM_BUSINESS (ERROR->WARN), pinned fail-first in the Registration feature commit.
- **E2:** no `DOWNSTREAM_*` value may reach the frontend (section 6, sub-task 2b.2a).

## 5. Incident ERROR per category

- **DOWNSTREAM_BUSINESS:** feature-level WARN log closest to the business decision.
- **DOWNSTREAM_CONTRACT:** `apim.contract.invalid` (`ApimExchangeOutcomeLogger.java:68-73`).
- **DOWNSTREAM_INVALID_RESPONSE:** `apim.exchange.failed` (`ApimExchangeOutcomeLogger.java:52-66`).
- **DOWNSTREAM_TRANSPORT:** `ApimWebClientFacade.java:409` for HTTP-error responses; `apim.exchange.failed` for connection-level failures (facade never reached).
- **LOCAL_SYSTEM:** `CatchAllExceptionMapping`'s own `http.api.error` line (already carries a stack trace, confirmed `CatchAllExceptionMapping.java:38`).

## 6. Public DOWNSTREAM exposure

(a) `ApiError.downstreamSystem()` -- zero production callers (two test-only references). (b) `WorkflowErrorType.DOWNSTREAM_SYSTEM` -- confirmed 4 call sites: `RegistrationStartWebMapper.java:207` (`MESSAGE_DELIVERY_FAILED`), `RegistrationOtpResendWebMapper.java:142` (`MESSAGE_DELIVERY_FAILED`), `RegistrationOtpVerifyWebMapper.java:144` (`TRUST_LOOKUP_FAILED`), and -- confirmed by direct source read -- `CheckRegistrationUsernameService.java:306` constructs a `WorkflowErrorDescriptor` with `WorkflowErrorType.DOWNSTREAM_SYSTEM` directly for its `downstreamFailed()` result (`DOWNSTREAM_FAILED` status, `ERROR_DOWNSTREAM_UNAVAILABLE` code), which an application-layer use case (not a web mapper) constructs directly -- the only such case in this inventory where a `WorkflowErrorType.DOWNSTREAM_SYSTEM` value originates below the web layer. **CORRECTED (2026-10-28, confirmed by real passing test evidence, CheckRegistrationUsernameServiceCharacterizationTest.downstreamFailureRendersAsBusinessTypeWithDownstreamErrorCode): the claim above that this value is "rendered verbatim via WorkflowMutationResponseMapper" is FALSE for this specific site.** RegistrationUsernameCheckWebMapper.apiError() hardcodes the literal type "BUSINESS" and never calls WorkflowMutationResponseMapper at all for this adapter's rendering path - the WorkflowErrorType.DOWNSTREAM_SYSTEM value constructed at :306 is discarded entirely before reaching the client. **CheckRegistrationUsernameService.java:306 is therefore NOT a public DOWNSTREAM exposure site** - it is an internal-only value, confirmed never rendered to the client. The error CODE (SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE), unlike the type, IS preserved through to the client. (c) literal `"DOWNSTREAM_SYSTEM"` in `ApimExceptionMappings.java:61` and `RegistrationExceptionMappings.java:31` -- the main exposure path by traffic volume. Replacement: `ApiError.type = "SYSTEM"` (`ApiError.java:45-47`, confirmed verbatim: `return new ApiError("SYSTEM", code, message, List.of(), DEFAULT_SEVERITY, source);`).

### Sub-task 2b.2a

Fail-first characterization update, replace every `DOWNSTREAM_SYSTEM` occurrence (confirmed 2 code paths beyond the pure web mappers: `CheckRegistrationUsernameService`'s direct construction, and the 3 web-mapper call sites), extend `PublicDownstreamStatusIsolationGuardTest` with a rendered-JSON behavior check. FE confirmed by Nick: no dependence on `DOWNSTREAM_*` values; E2 approved.

### Additional public exposure sites (checked against the source lines cited)

- `ApiError.java:49` declares the `downstreamBusiness` factory.
- `WorkflowErrorType.java:9` defines `DOWNSTREAM_BUSINESS`.
- `WorkflowMutationResponseMapper.java:50-58` renders `descriptor.type().name()` into the public error type, so every `WorkflowErrorType` constant is public.
- `RegistrationStartWebMapper.java:207` returns `WorkflowErrorType.DOWNSTREAM_SYSTEM` for `MESSAGE_DELIVERY_FAILED`.
- `RegistrationStartWebMapper.java:209` returns `WorkflowErrorType.DOWNSTREAM_BUSINESS` for `DOWNSTREAM_REJECTED`; it is missing from the list of `DOWNSTREAM_SYSTEM` sites above.
- `LoginStartWebMapper.java:63` passes the literal source "DOWNSTREAM" into the error it builds (the public error `source` field). The 2b.2a check "no rendered JSON contains DOWNSTREAM" would flag it; the replacement value is for Nick and the frontend to decide.
- `RegistrationExceptionMappings.java:31-32` emits the type "DOWNSTREAM_SYSTEM" and the code `SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE`.
- `SharedWorkflowErrorKey.java:8-11` defines public message keys containing "downstream" (for example `err.workflow.downstream.unavailable`). They are frontend message keys; renaming them is a contract change for Nick to decide before the 2b.2a guard is written.

## 7. Sub-task plan

2b.2 characterization pack (must also resolve: `ForgotPasswordDetailsOtpProtectedOperation.locked`'s exact `SYSTEM_FAILED`-sub-branch routing - deferred to Batch 2b, as already agreed; `UsernameRecoveryStartResponsePolicy`'s HTTP code and `TrpGetAccountListAdapter`'s consumer trace are both now RESOLVED above); 2b.2a (section 6); 2b.3 shared classifier; 2b.4..2b.n per-feature commits.

## 8. Open questions

- **Open question for Nick (added 2026-10-28, Priority 1 item 4 task 4):** technical downstream failure rendered as BUSINESS by RegistrationUsernameCheckWebMapper.apiError() - CheckRegistrationUsernameService.java:306 constructs a WorkflowErrorDescriptor with WorkflowErrorType.DOWNSTREAM_SYSTEM, but the real web mapper discards this value and always renders type="BUSINESS" to the client instead (confirmed by real passing test evidence). Is this intentional, or should the mapper be corrected to render the real WorkflowErrorType like every other adapter in this inventory does?
Every confirmed `new ApimException(` construction site (`ApimWebClientFacade.java:411`, `ApimForgotPasswordCompletionAdapter.java:65`, `ApimForgotPasswordUsernameLookupAdapter.java:87,96`, `ApimResponseValidator.java:37,57`, `ApimUpdatePersonalInformationAdapter.java:63,71`, `ApimReferenceDateRefreshAdapter.java:58,67`, `SharedApimTrpChkCustIdTransport.java:45,55,64`, `TrpRegMemberApimResultMapper.java:26`) uses `HttpStatus.BAD_GATEWAY` (502), with exactly one exception: `ApimForgotPasswordCompletionAdapter.java:76`'s `safeFailure` fallback, which constructs `HttpStatus.INTERNAL_SERVER_ERROR` (500). That specific 500 construction is confirmed (section 1, Forgot Password completion `safeFailure` rows) to never reach a public response -- `ForgotPasswordCompleteProtectedOperation.invoke`'s own `.onErrorResume` swallows it into `unknownOutcome()` before any `ExceptionMapping` dispatch occurs. Separately, `ApimWebClientFacade.handleErrorBody` (lines 391-416) always constructs `HttpStatus.BAD_GATEWAY` regardless of the actual upstream HTTP status; `mapUpstreamErrorCode` (lines 485-491) varies only the public error *code* (`APIM_SERVICE_UNAVAILABLE` for upstream 503, `APIM_TIMEOUT` for upstream 504, `APIM_UPSTREAM_FAILURE` otherwise), never the HTTP status. There is no row in this inventory where a public status other than 502 is actually returned for a generic `ApimException`, and no remaining decision: differentiating the public status by upstream 503/504 would itself be a public-behaviour change, which M2b's own rules place out of scope.

- `RegistrationHttpResponses.status()`'s duplicate `SYSTEM_ERROR`/`BUSINESS_REJECTED` branches (confirmed dead code in an ); no change proposed in M2b.
- 4xx vs 5xx downstream status both resolve identically via the facade today; whether a 4xx should classify differently is open.
- `TrpGetAccountListAdapter`'s swallowed result (`LoginAccountAuthorizationStatus.UNAVAILABLE`) **IS consumed by a dedicated controller response path - the earlier claim above was WRONG, now corrected with the full confirmed real chain:** `LoginOtpVerifyService.continueToAccounts` (line 325) maps it to the distinct, named `VerifyLoginOtpStatus.ACCOUNT_LIST_UNAVAILABLE`; `LoginOtpVerifyWebMapper` renders it as `ApiStatus.SYSTEM_ERROR`, type `"FORM"` (NOT `"SYSTEM"` - only `SYSTEM_FAILED` gets the special-cased `"SYSTEM"` type), code `LoginErrorKey.ACCOUNT_LIST_UNAVAILABLE.value()` = `"err.login.accountList.unavailable"`, target `["PAGE"]`, source `"BFF"`; `LoginController` consumes it; `LoginHttpResponses.status()` (Login's OWN dedicated HTTP-status class - does NOT use `HttpStatusClassification` at all) renders it as **HTTP 502** via the generic `ApiStatus.SYSTEM_ERROR -> HttpStatus.BAD_GATEWAY` fallback, since this code does not match any of `statusForCode`'s specific code-based overrides.
- Whether confirmed swallow sites should publish a `FailureEvent` (M3 3.7).


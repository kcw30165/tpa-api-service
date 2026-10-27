# 07 - APIM Error Classification Design (Final, routing-corrected)

Sub-task 2b.1. Docs only, no production/test change. This is a final correction pass incorporating a major structural finding: most auth-flow (Forgot Password/Login/Username Recovery/Registration) downstream failures are **swallowed at the controller or protected-operation layer into a feature-specific result/response**, never reaching `ApimExceptionMappings`/`CatchAllExceptionMapping` at all, except at specific, now-identified gaps (Forgot Password `/details-check`; Login `cancel`/`resend`/`verify`) where no such protective catch exists.

## Corrections in this pass

1. **Auth-flow routing re-traced end to end.** Forgot Password Start/Complete and Login Start/Send are protected by a controller-level `.onErrorResume(...)` into a feature-specific `*HttpResponses` dispatcher (`ForgotPasswordHttpResponses`, `LoginHttpResponses`); failures resolve to that feature's own status/code, not the generic APIM mapping path. Two confirmed gaps: Forgot Password's `/details-check` endpoint has only a synchronous `try/catch` (no reactive `.onErrorResume`), and Login's `cancel`/`resend`/`verify` endpoints likewise lack the reactive catch-all present on `startLogin`/`send` -- in both gap cases an uncaught internal failure genuinely reaches `CatchAllExceptionMapping`.
2. **`ExecutionLoggingAspect` corrected per adapter**, not assumed. The 10 confirmed `@LogExecution`-annotated classes do NOT include any of: Account Balance, Account Gain/Loss, Fund Price, Fund Price Mode, Fund Comparison, Fund Fact Sheet, or Reference Date adapters. `GetAccountBalanceService`/`GetAccountGainLossService` use a method named `get`, not `execute`, so the broad `*Service.execute(..)` pointcut does not match them either -- neither aspect fires for those two features at all. `RefreshReferenceDateService.execute(...)` DOES match the broad pointcut by name, so Reference Date rows are corrected to note that aspect firing.
3. **Every row carries exactly one `**Proposed `FailureOrigin` (section 3):**`** line (enforced by a hard assertion in the generator against the canonical five values).
4. **Validator-vs-classifier empty-data distinction** stated explicitly wherever relevant: `ApimResponseValidator` treats empty `data` as a valid no-records success; `ApimEnvelopeSchemaClassifier` (logging only) treats empty `data` + empty `err-message` as a contract violation worth flagging. Not a bug -- different jobs.
5. **`RegistrationHttpResponses.status()` dead-code duplicate branches** (confirmed lines 60 and 63 both check `ApiStatus.SYSTEM_ERROR`; line 67's `BUSINESS_REJECTED` check is similarly duplicated at line 66) moved to section 8 as an open question; no change proposed in M2b.
6. **Exact adapter line numbers** now cited for every remaining row (Fund Price, Fund Price Mode, Fund Comparison, Fund Fact Sheet, Personal Information, Reference Date, Forgot Password username-lookup), replacing every earlier "reference-confirmed" placeholder.
7. **Fund Fact Sheet's empty-data handling contradicts its own class Javadoc** (Javadoc says no-funds is a non-empty list of items with empty row lists; the code instead errors on a genuinely empty `data` array) -- flagged as its own row and an open question, not silently reconciled.

## 1. Inventory table (48 rows)

### Registration -- ApimRegistrationLookupAdapter (TRPSearchMember)

- **Condition:** `err.tpa.member.not.found` -> `NOT_ELIGIBLE`
- **Detection site:** `registration/mapper/TrpSearchMemberApimResultMapper.toResult`, lines 22-24
- **Handling:** Returned as normal `RegistrationLookupResult(NOT_ELIGIBLE,...)`; `withFailureLogging`'s `doOnEach` calls `recordDownstreamRejection` (`ApimRegistrationLookupAdapter.java:101,147`)
- **Message policy:** N/A -- successful application result, never an `ExceptionMapping`
- **Ordered log lines:** (1) `apim.business.rejected` WARN from the envelope-content classifier (non-empty err-message), firing on the already-resolved onNext envelope before this mapper code runs; (2) `FailureEvent` published, logged ERROR today (origin not in `WARN_ORIGINS`) -- E1 moves this to WARN. (3) ApimRegistrationLookupAdapter is confirmed `@LogExecution`-annotated (unfiltered grep, 10-class list). A failure here also produces `method.execution.error` ERROR with full stack trace (`ExecutionLoggingAspect.java:161-170`).
- **Idempotency replay:** No -- lookup is called directly, not idempotency-wrapped
- **Origin tag today:** `FailureOrigin.DOWNSTREAM_CONTRACT` (`ApimRegistrationLookupAdapter.java:121`)
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_BUSINESS` only, per E1 (section 4)
- **Public HTTP status:** **UNVERIFIED:** no file under `adapter/in/web` references `RegistrationLookupStatus.NOT_ELIGIBLE` by name (confirmed empty grep); this lookup may have no dedicated public HTTP endpoint and be consumed only internally (e.g. by `RegistrationCompleteProtectedOperation.refreshTrusts`)
- **Public ApiStatus:** n/a (see above)
- **Public error code/type:** n/a (see above)
- **Citation:** `err.tpa.member.not.found` / `RegistrationErrorKey.START_VERIFICATION_FAILED` (FailureEvent logging key); `ApimRegistrationLookupAdapter.java:144-159`

### Registration -- ApimRegistrationLookupAdapter (TRPSearchMember)

- **Condition:** `EMPTY_DATA`/`INVALID_ACCOUNT_DATA`/`UNEXPECTED_ERROR` reasons
- **Detection site:** `registration/mapper/TrpSearchMemberApimResultMapper.toResult`, lines 25-28,30-33,39-42
- **Handling:** Throws `RegistrationLookupDownstreamException`, mapped by `RegistrationExceptionMappings.lookupDownstream()` (`RegistrationExceptionMappings.java:18-44`)
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** (1) `apim.contract.invalid` ERROR from the classifier (missing err-message, or missing/empty data); (2) `FailureEvent`, origin `DOWNSTREAM_CONTRACT`, ERROR; (3) `http.api.error` ERROR, no stack trace. ApimRegistrationLookupAdapter is confirmed `@LogExecution`-annotated (unfiltered grep, 10-class list). A failure here also produces `method.execution.error` ERROR with full stack trace (`ExecutionLoggingAspect.java:161-170`).
- **Idempotency replay:** No
- **Origin tag today:** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value()`
- **Citation:** `RegistrationExceptionMappings.java:18-44`

### Registration -- ApimRegistrationCompletionAdapter (TRPRegMember)

- **Condition:** `ApimProfileResolutionException` during completion (our own config failure)
- **Detection site:** `ApimRegistrationCompletionAdapter.java:54-55`
- **Handling:** `.onErrorMap(ApimProfileResolutionException.class, RegistrationCompletionPreDispatchException::new)`. `DefaultIdempotencyExecutor` always re-throws after idempotency bookkeeping (confirmed, prior investigation). Absent from `ExceptionMappingRegistryConfig`'s list, resolves via `CatchAllExceptionMapping`. **Routing correction:** `RegistrationController.completeRegistration` wraps `completeRegistrationUseCase.complete(...)` in a `try/catch(RuntimeException)` at the SYNCHRONOUS call-construction layer only (`RegistrationController.java:134-161`) -- not a reactive `.onErrorResume`. A reactive error from inside the returned Mono is NOT caught by this try/catch and propagates to the central handler, confirming `CatchAllExceptionMapping` is the real destination.
- **Message policy:** N/A -- resolves via the mandatory catch-all
- **Ordered log lines:** No executor-layer log (local configuration failure, not a downstream envelope issue); `http.api.error` ERROR with stack trace (`CatchAllExceptionMapping.java:38`, final arg `true`). ApimRegistrationCompletionAdapter is confirmed `@LogExecution`-annotated (unfiltered grep, 10-class list). A failure here also produces `method.execution.error` ERROR with full stack trace (`ExecutionLoggingAspect.java:161-170`).
- **Idempotency replay:** Yes -- Registration complete is idempotency-protected
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.LOCAL_SYSTEM`
- **Public HTTP status:** 500 (`CatchAllExceptionMapping.java:32`)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"SYSTEM"` / `ErrorCodes.SYSTEM_UNEXPECTED`
- **Citation:** `ApimRegistrationCompletionAdapter.java:54-55`; `RegistrationController.java:134-161`; `CatchAllExceptionMapping.java:18-40`

### Forgot Password -- ApimForgotPasswordUsernameLookupAdapter (TRPChkCustId)

- **Condition:** missing envelope/response
- **Detection site:** `ApimForgotPasswordUsernameLookupAdapter.java:79-81` (`toTransportEnvelope`). **Correction:** this adapter does its OWN `postEnvelope` call and conversion directly; `SharedApimTrpChkCustIdTransport` is referenced only for its `API_NAME` constant (`ApimForgotPasswordUsernameLookupAdapter.java:51`), not as a co-handler.
- **Handling:** `throw new IllegalStateException("TRPChkCustId response envelope is missing response")`. Propagates past `.map()`, observed by `.doOnError` (line 68-74, logs ERROR with full exception) before continuing up to `ForgotPasswordStartProtectedOperation.execute`'s own `.onErrorResume` (lines 100-108), which SWALLOWS it into `StartForgotPasswordResult.systemFailed()` -- a result value, not an exception.
- **Message policy:** N/A at this point -- swallowed to a result value before any `ExceptionMapping` is reached
- **Ordered log lines:** (1) possible `apim.contract.invalid` ERROR from the classifier; (2) adapter's own `event=forgot-password.username-lookup.failed` ERROR with full exception (`ApimForgotPasswordUsernameLookupAdapter.java:68-74`); (3) `event=forgot-password.start.failed ... stage=username-lookup` ERROR (`ForgotPasswordStartProtectedOperation.java:100-107`) at the swallow site itself
- **Idempotency replay:** Yes -- Forgot Password start
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** **Corrected routing:** `IdempotentStartForgotPasswordService.executeAcquired` re-converts a `SYSTEM_FAILED` result BACK into a thrown `IllegalStateException` (lines 58-67) with no `.onErrorMap` on `start()`'s own return (lines 53-56), so it propagates to `ForgotPasswordController.startForgotPassword`, which DOES have a reactive `.onErrorResume(... -> startMapper.systemUnavailable())` (lines 109-112), resolving via `ForgotPasswordHttpResponses`'s generic `ApiStatus.SYSTEM_ERROR` branch -> **503** (`ForgotPasswordHttpResponses.java:46-47`), not `ApimExceptionMappings`/`CatchAllExceptionMapping` at all.
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** whatever code `startMapper.systemUnavailable()` sets (mapper source not read this pass)
- **Citation:** `ApimForgotPasswordUsernameLookupAdapter.java:77-91`; `ForgotPasswordStartProtectedOperation.java:86-109`; `IdempotentStartForgotPasswordService.java:58-67`; `ForgotPasswordController.java:93-116`; `ForgotPasswordHttpResponses.java:21-50`

### Forgot Password -- ApimForgotPasswordUsernameLookupAdapter (TRPChkCustId)

- **Condition:** no err-message AND no data, or data present but every item malformed
- **Detection site:** `ApimForgotPasswordUsernameLookupAdapter.java:86-100`
- **Handling:** `throw new ApimException(BAD_GATEWAY, APIM_RESPONSE_INVALID, ...)`. Same swallow chain as the row above: observed by the adapter's `.doOnError`, then swallowed by `ForgotPasswordStartProtectedOperation`'s `.onErrorResume` into `systemFailed()`.
- **Message policy:** N/A -- swallowed before any `ExceptionMapping`
- **Ordered log lines:** `apim.contract.invalid` ERROR from the classifier (this condition matches its malformed rule); adapter's `event=forgot-password.username-lookup.failed` ERROR with exception; `event=forgot-password.start.failed` ERROR at the swallow site
- **Idempotency replay:** Yes
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503 via the same corrected routing as the row above
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** whatever code `startMapper.systemUnavailable()` sets
- **Citation:** `ApimForgotPasswordUsernameLookupAdapter.java:86-100`; `ForgotPasswordStartProtectedOperation.java:86-109`; `ForgotPasswordController.java:93-116`

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)

- **Condition:** missing response envelope
- **Detection site:** `ApimForgotPasswordCompletionAdapter.java:64-68`
- **Handling:** `throw new ApimException(BAD_GATEWAY, APIM_RESPONSE_INVALID, ...)`. **Routing correction:** this is called from `ForgotPasswordCompleteProtectedOperation.invoke` via `completion.complete(...).flatMap(...).onErrorResume(ignored -> blockUnknown(session, now))` (`ForgotPasswordCompleteProtectedOperation.java:127-135`), which SWALLOWS any error into `unknownOutcome()`, not a thrown exception.
- **Message policy:** N/A -- swallowed to a result value
- **Ordered log lines:** `apim.contract.invalid` ERROR from the classifier; no adapter-level named log line confirmed at this exact site this pass. ApimForgotPasswordCompletionAdapter is confirmed `@LogExecution`-annotated (unfiltered grep, 10-class list). A failure here also produces `method.execution.error` ERROR with full stack trace (`ExecutionLoggingAspect.java:161-170`).
- **Idempotency replay:** Yes -- Forgot Password complete
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** n/a -- swallowed to `unknownOutcome()`, which `ForgotPasswordController.complete`'s reactive chain (no further onErrorResume needed since it's already a success-shaped value) maps through `completeMapper.toResponse(result)` -> `ForgotPasswordHttpResponses`
- **Public ApiStatus:** n/a
- **Public error code/type:** n/a
- **Citation:** `ApimForgotPasswordCompletionAdapter.java:62-72,127-135`; `ForgotPasswordController.java:274-296`

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)

- **Condition:** crypto failure (`ApimCryptoException`/`ApimResponseMappingException`)
- **Detection site:** `ApimPayloadCryptoService.java:164`; classifier `ApimCryptoFailureClassifier.java:15-16`; sole confirmed caller `ApimForgotPasswordCompletionAdapter.java:57`
- **Handling:** `.onErrorResume(ApimCryptoFailureClassifier::isCryptoFailure, e -> unknownOutcome())`. This is INSIDE `invoke()`'s own chain (line 127-135 area), which itself sits inside `ForgotPasswordCompleteProtectedOperation.route`'s `Mono.usingWhen` lock-release wrapper.
- **Message policy:** N/A -- swallowed
- **Ordered log lines:** `apim.exchange.failed` ERROR (genuine Mono onError signal, fires via `doOnEach` before this swallow -- confirmed structural guarantee). ApimForgotPasswordCompletionAdapter is confirmed `@LogExecution`-annotated (unfiltered grep, 10-class list). A failure here also produces `method.execution.error` ERROR with full stack trace (`ExecutionLoggingAspect.java:161-170`).
- **Idempotency replay:** Yes
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- **Public HTTP status:** n/a -- swallowed to `unknownOutcome()`
- **Public ApiStatus:** n/a
- **Public error code/type:** n/a
- **Citation:** `ApimPayloadCryptoService.java:158-166`; `ApimForgotPasswordCompletionAdapter.java:52-58`

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)

- **Condition:** `safeFailure` catch-all, sub-cause: HTTP-error-status response (facade)
- **Detection site:** `ApimForgotPasswordCompletionAdapter.java:74-81`
- **Handling:** `.onErrorMap(this::safeFailure)` -> `ApimException(INTERNAL_SERVER_ERROR, SYSTEM_UNEXPECTED, ..., t)`. Also swallowed into the same `blockUnknown`/`unknownOutcome()` path one level up -- this `onErrorMap` only renames the exception type; the surrounding `invoke()` chain's own `.onErrorResume` further upstream still catches it before it becomes a thrown web-layer exception.
- **Message policy:** N/A -- ultimately swallowed
- **Ordered log lines:** `apim.exchange.failed` ERROR. ApimForgotPasswordCompletionAdapter is confirmed `@LogExecution`-annotated (unfiltered grep, 10-class list). A failure here also produces `method.execution.error` ERROR with full stack trace (`ExecutionLoggingAspect.java:161-170`).
- **Idempotency replay:** Yes
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** n/a -- swallowed (see handling)
- **Public ApiStatus:** n/a
- **Public error code/type:** n/a
- **Citation:** `ApimForgotPasswordCompletionAdapter.java:74-81,127-135`

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)

- **Condition:** `safeFailure` catch-all, sub-cause: connection-level failure
- **Detection site:** `ApimForgotPasswordCompletionAdapter.java:74-81`
- **Handling:** Same `safeFailure` catch-all; same eventual swallow as the row above
- **Message policy:** N/A
- **Ordered log lines:** `apim.exchange.failed` ERROR
- **Idempotency replay:** Yes
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** n/a -- swallowed
- **Public ApiStatus:** n/a
- **Public error code/type:** n/a
- **Citation:** `ApimForgotPasswordCompletionAdapter.java:74-81,127-135`; `ApimWebClientFacade.java:212-241`

### Forgot Password -- ApimForgotPasswordDetailsLookupAdapter (TRPSearchMember)

- **Condition:** missing envelope
- **Detection site:** `ForgotPasswordTrpSearchMemberMapper.java:46`; `ApimForgotPasswordDetailsLookupAdapter.java:51-52`
- **Handling:** `throw new ForgotPasswordDetailsDownstreamException(UNEXPECTED_ERROR)`. **Confirmed this pass:** the real consumer is `ForgotPasswordUnderLockDetailsVerifier.verify()` (`ForgotPasswordUnderLockDetailsVerifier.java:34-58`), which wraps the lookup-port call with `.doOnError(cause -> logFailure(...))` (line 55, logging `event=forgot-password.details-otp.failed stage=lookup`, exception class name only, ERROR) then `.onErrorReturn(ForgotPasswordUnderLockDetailsVerification.of(SYSTEM_FAILED))` (56-58). **Routing gap confirmed:** unlike Start/Complete/Resend/Verify, `ForgotPasswordController.details` (`/details-check`) has only a SYNCHRONOUS `try/catch(RuntimeException)` around command construction (lines 175-194), NOT a reactive `.onErrorResume` on the returned Mono. If the eventual Mono still errors after swallowing SYSTEM_FAILED up through `IdempotentForgotPasswordDetailsOtpService.executeAcquired`'s re-thrown `IllegalStateException` (lines 83-89 of that file, same re-throw pattern as the Start flow), this would propagate uncaught to the central handler -> `CatchAllExceptionMapping`.
- **Message policy:** N/A at the verifier swallow point; `MessagePolicy.RESOLVE` if it ultimately reaches `CatchAllExceptionMapping`
- **Ordered log lines:** `event=forgot-password.details-otp.failed stage=lookup` ERROR, exception class name only, no stack trace (`ForgotPasswordUnderLockDetailsVerifier.java:61-66`); if it propagates further, `http.api.error` ERROR with stack trace via `CatchAllExceptionMapping`
- **Idempotency replay:** Yes -- Forgot Password details/OTP is idempotency-protected
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** **UNVERIFIED, narrowed:** whether this specific `SYSTEM_FAILED` path reaches `IdempotentForgotPasswordDetailsOtpService`'s re-throw (confirmed structurally similar to Start, but the precise `ForgotPasswordDetailsOtpProtectedOperation.locked`->`issue`/business-rejected routing was not traced field-by-field for this exact lookup-failure sub-path this pass) -- if it does, 500/`SYSTEM_ERROR`/`"SYSTEM"`/`SYSTEM_UNEXPECTED` via `CatchAllExceptionMapping`
- **Public ApiStatus:** UNVERIFIED (see above)
- **Public error code/type:** UNVERIFIED (see above)
- **Citation:** `ForgotPasswordUnderLockDetailsVerifier.java:1-67`; `ForgotPasswordController.java:153-195`; `IdempotentForgotPasswordDetailsOtpService.java:44-97`

### Username Recovery -- TrpSearchMemberAdapter (TRPSearchMember)

- **Condition:** `TrpSearchMemberApimDownstreamException` (unrecognized non-blank err-message)
- **Detection site:** `usernamerecovery/mapper/TrpSearchMemberApimResultMapper.java:47-50`
- **Handling:** `.onErrorResume(TrpSearchMemberApimDownstreamException.class, e -> unknownOutcome())` (`TrpSearchMemberAdapter.java:54-55`). **Routing confirmed:** `UsernameRecoveryStartProtectedOperation.lookup` wraps this port call with `.onErrorMap(Exception.class, ProtectedExecutionException::new)` (line 115), then the outer `execute` method's `.onErrorResume(ProtectedExecutionException.class, error -> Mono.error(UsernameRecoveryStartException.systemFailure(error)))` (lines 96-98) converts it to a typed `UsernameRecoveryStartException`. `UsernameRecoveryController.start` DOES have a reactive `.onErrorResume(UsernameRecoveryStartException.class, exception -> startPolicy.toPolicy(exception))` (lines 123-125), so this is fully and correctly handled by the typed policy class, never reaching the central handler.
- **Message policy:** N/A -- fully typed-exception-routed through `UsernameRecoveryStartResponsePolicy`
- **Ordered log lines:** `apim.exchange.failed` ERROR fires before the first swallow
- **Idempotency replay:** Yes -- Username Recovery start is idempotency-protected (confirmed via `IdempotentStartUsernameRecoveryService`)
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** **UNVERIFIED:** `UsernameRecoveryStartResponsePolicy`'s exact status/code mapping for `UsernameRecoveryStartException.systemFailure(...)` was not read this pass
- **Public ApiStatus:** UNVERIFIED
- **Public error code/type:** UNVERIFIED
- **Citation:** `usernamerecovery/mapper/TrpSearchMemberApimResultMapper.java:39-56`; `UsernameRecoveryStartProtectedOperation.java:82-98`; `UsernameRecoveryController.java:100-128`

### Username Recovery -- TrpSearchMemberAdapter (TRPSearchMember)

- **Condition:** any `ApimException` (facade-raised HTTP-error)
- **Detection site:** generic catch, `TrpSearchMemberAdapter.java:56`
- **Handling:** Same swallow/convert chain as the row above: `.onErrorResume(ApimException.class, ...)` -> `ProtectedExecutionException` -> `UsernameRecoveryStartException.systemFailure(error)` -> typed-exception policy at the controller
- **Message policy:** N/A
- **Ordered log lines:** `ApimWebClientFacade.handleErrorBody`'s `log.error`; `apim.exchange.failed` ERROR
- **Idempotency replay:** Yes
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** UNVERIFIED (same reason as above)
- **Public ApiStatus:** UNVERIFIED
- **Public error code/type:** UNVERIFIED
- **Citation:** `TrpSearchMemberAdapter.java:48-56`; `UsernameRecoveryStartProtectedOperation.java:82-98`

### Username Recovery -- TrpSearchMemberAdapter (TRPSearchMember)

- **Condition:** crypto/mapping failure, uncaught by either `onErrorResume`
- **Detection site:** same mechanism as other crypto rows
- **Handling:** propagates past both type-specific catches in `TrpSearchMemberAdapter`, caught by `UsernameRecoveryStartProtectedOperation`'s `.onErrorMap(Exception.class, ProtectedExecutionException::new)` (line 84) -- this one IS a catch-all `Exception` filter, so it IS caught here, then routed identically to the rows above
- **Message policy:** N/A
- **Ordered log lines:** `apim.exchange.failed` ERROR
- **Idempotency replay:** Yes
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- **Public HTTP status:** UNVERIFIED (same reason)
- **Public ApiStatus:** UNVERIFIED
- **Public error code/type:** UNVERIFIED
- **Citation:** `TrpSearchMemberAdapter.java:48-56`; `UsernameRecoveryStartProtectedOperation.java:82-98`

### Login -- TrpMemberLoginAdapter (TRPMemberLogin)

- **Condition:** `IllegalStateException` (confirmed origin: `DefaultApimExchangeExecutor`'s own `switchIfEmpty`/`validateCommon`, inside the `postEnvelope`-wrapped chain)
- **Detection site:** `TrpMemberLoginAdapter.java:37-39`; `SharedApimTrpMemberLoginTransport.post` is a plain `executor.postEnvelope(exchange)` passthrough (line 44)
- **Handling:** `.onErrorResume(IllegalStateException.class, ignored -> mapper.failure(UNAVAILABLE))`. **Routing confirmed:** `LoginController.startLogin` has its own reactive `.onErrorResume(x -> loginStartMapper.systemUnavailable())` (lines 75-76), so even if this swallow point were bypassed, the controller itself would catch any remaining error.
- **Message policy:** N/A -- swallowed to `UNAVAILABLE`, then handled by the controller's own further catch-all if needed
- **Ordered log lines:** `apim.exchange.failed` ERROR fires before the swallow
- **Idempotency replay:** Yes -- Login start is idempotency-protected
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** n/a -- swallowed to `UNAVAILABLE`, mapped by `LoginStartWebMapper`
- **Public ApiStatus:** n/a
- **Public error code/type:** n/a
- **Citation:** `TrpMemberLoginAdapter.java:30-40`; `SharedApimTrpMemberLoginTransport.java:27-45`; `LoginController.java:49-80`

### Login -- TrpGetAccountListAdapter (TRPGetAccountList)

- **Condition:** any error, sub-cause: HTTP-error `ApimException`
- **Detection site:** `TrpGetAccountListAdapter.java:29-32`; `SharedApimTrpGetAccountListTransport.post` confirmed plain `executor.postEnvelope(exchange)` passthrough (line 47)
- **Handling:** `.onErrorReturn(LoginAccountAuthorizationResult.unavailable())` -- no type filter, catches everything
- **Message policy:** N/A -- `onErrorReturn` provides no access to the throwable
- **Ordered log lines:** `ApimWebClientFacade.handleErrorBody`'s `log.error`; `apim.exchange.failed` ERROR
- **Idempotency replay:** No -- not found under any idempotency package
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** n/a -- swallowed to `unavailable()`, handled by `LoginHttpResponses`
- **Public ApiStatus:** n/a
- **Public error code/type:** n/a
- **Citation:** `TrpGetAccountListAdapter.java:26-33`; `SharedApimTrpGetAccountListTransport.java:16-48`

### Login -- TrpGetAccountListAdapter (TRPGetAccountList)

- **Condition:** any error, sub-cause: crypto/mapping failure
- **Detection site:** same unfiltered catch-all
- **Handling:** `.onErrorReturn(LoginAccountAuthorizationResult.unavailable())`
- **Message policy:** N/A
- **Ordered log lines:** `apim.exchange.failed` ERROR
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- **Public HTTP status:** n/a -- swallowed
- **Public ApiStatus:** n/a
- **Public error code/type:** n/a
- **Citation:** `TrpGetAccountListAdapter.java:26-33`; `SharedApimTrpGetAccountListTransport.java:16-48`

### Login -- TrpGetAccountListAdapter (TRPGetAccountList)

- **Condition:** any error, sub-cause: connection-level failure
- **Detection site:** same unfiltered catch-all
- **Handling:** `.onErrorReturn(LoginAccountAuthorizationResult.unavailable())`
- **Message policy:** N/A
- **Ordered log lines:** `apim.exchange.failed` ERROR
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** n/a -- swallowed
- **Public ApiStatus:** n/a
- **Public error code/type:** n/a
- **Citation:** `TrpGetAccountListAdapter.java:26-33`; `ApimWebClientFacade.java:212-241`

### Account Balance -- ApimAccountBalanceSourceAdapter (TRPGetMemberAccBal)

- **Condition:** `err.tpa.account.invalid` -> `ACCOUNT_NOT_FOUND`
- **Detection site:** `ApimAccountBalanceSourceAdapter.java:54-55`
- **Handling:** `AccountBalanceRetrievalException.accountNotFound()`, mapped via `AccountBalanceExceptionMappings.retrievalFailure()` delegating to `AccountBalanceRetrievalResponsePolicy.map()`
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `apim.business.rejected` WARN from classifier; WARN at mapping (`reason != SYSTEM_FAILURE`); `http.api.error`, no stack trace. ApimAccountBalanceSourceAdapter is NOT `@LogExecution`-annotated. Its use-case entry point (`GetAccountBalanceService`) was confirmed by direct source read to use a method named differently than `execute` (e.g. `get`), so the broad `*Service.execute(..)` pointcut does NOT match either. Neither `ExecutionLoggingAspect` pointcut fires for this row.
- **Idempotency replay:** No -- read-only query
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_BUSINESS`
- **Public HTTP status:** 422
- **Public ApiStatus:** `BUSINESS_REJECTED`
- **Public error code/type:** `ApiError.business("err.ab.account.not.exist", null, [], "SERVER")`
- **Citation:** `AccountBalanceRetrievalResponsePolicy.java:13-24`; `ApimAccountBalanceSourceAdapter.java:54-55`

### Account Balance -- ApimAccountBalanceSourceAdapter (TRPGetMemberAccBal)

- **Condition:** `err.tpa.member.not.found` -> `ACCOUNT_MISMATCH`
- **Detection site:** `ApimAccountBalanceSourceAdapter.java:56-57`
- **Handling:** `AccountBalanceRetrievalException.accountMismatch()`, same policy path
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** WARN classifier; WARN mapping; `http.api.error`, no stack trace. ApimAccountBalanceSourceAdapter is NOT `@LogExecution`-annotated. Its use-case entry point (`GetAccountBalanceService`) was confirmed by direct source read to use a method named differently than `execute` (e.g. `get`), so the broad `*Service.execute(..)` pointcut does NOT match either. Neither `ExecutionLoggingAspect` pointcut fires for this row.
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_BUSINESS`
- **Public HTTP status:** 422
- **Public ApiStatus:** `BUSINESS_REJECTED`
- **Public error code/type:** `ApiError.business("err.ab.account.mismatch", null, [], "SERVER")`
- **Citation:** `AccountBalanceRetrievalResponsePolicy.java:13-24`; `ApimAccountBalanceSourceAdapter.java:56-57`

### Account Balance -- ApimAccountBalanceSourceAdapter (TRPGetMemberAccBal)

- **Condition:** catch-all, sub-cause: unrecognized non-blank error
- **Detection site:** `ApimAccountBalanceSourceAdapter.java:58-59`
- **Handling:** `AccountBalanceRetrievalException.systemFailure()`. Note the validator/classifier distinction: `ApimResponseValidator.requireSuccessData` (`ApimResponseValidator.java:27-45`) treats an empty `data` array as VALID when `err-message` is empty (a legitimate "no records" success, line 39-43 only rejects `data == null`, not empty). `ApimEnvelopeSchemaClassifier` (used only for logging, never for adapter behavior) instead treats empty `data` + empty `err-message` as a malformed/contract-violation condition (confirmed production incident on TRPMemberLogin, `ApimEnvelopeSchemaClassifier.java:15-21`). The two components disagree on this exact edge case by design: the validator's job is correctness of business behavior (empty is a valid, if unusual, outcome), while the classifier's job is operational alerting (empty+empty is worth flagging as suspicious) -- this is not a bug, but is worth naming explicitly.
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `apim.business.rejected` WARN from classifier; ERROR at mapping (`reason == SYSTEM_FAILURE`); `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, "SERVER")`
- **Citation:** `AccountBalanceRetrievalResponsePolicy.java:13-24`; `ApimAccountBalanceSourceAdapter.java:58-59`

### Account Balance -- ApimAccountBalanceSourceAdapter (TRPGetMemberAccBal)

- **Condition:** catch-all, sub-cause: missing/empty data (inner `try{requireSingleData(requireSuccessData(...))}catch`)
- **Detection site:** `ApimAccountBalanceSourceAdapter.java:60-67`
- **Handling:** `AccountBalanceRetrievalException.systemFailure()`
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `apim.contract.invalid` ERROR from classifier; ERROR at mapping; `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, "SERVER")`
- **Citation:** `ApimAccountBalanceSourceAdapter.java:60-67`

### Account Balance -- ApimAccountBalanceSourceAdapter (TRPGetMemberAccBal)

- **Condition:** catch-all, sub-cause: crypto/mapping failure (confirmed account-summary-500 incident mechanism)
- **Detection site:** `ApimAccountBalanceSourceAdapter.java:69-73` (outer `.onErrorMap`)
- **Handling:** `e instanceof AccountBalanceRetrievalException ? e : systemFailure()`
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `apim.exchange.failed` ERROR (genuine Mono error); ERROR at mapping; `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, "SERVER")`
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
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, "SERVER")`
- **Citation:** `ApimAccountBalanceSourceAdapter.java:69-73`

### Account Gain/Loss -- ApimAccountGainLossSourceAdapter (TRPGetInvestGL)

- **Condition:** `err.tpa.fund.switching.in.progress`
- **Detection site:** `ApimAccountGainLossSourceAdapter.java:63-65`
- **Handling:** `AccountGainLossRetrievalException.fundSwitchingInProgress()`
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `apim.business.rejected` WARN classifier; WARN at mapping; `http.api.error`, no stack trace. ApimAccountGainLossSourceAdapter is NOT `@LogExecution`-annotated. Its use-case entry point (`GetAccountGainLossService`) was confirmed by direct source read to use a method named differently than `execute` (e.g. `get`), so the broad `*Service.execute(..)` pointcut does NOT match either. Neither `ExecutionLoggingAspect` pointcut fires for this row.
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_BUSINESS`
- **Public HTTP status:** 422
- **Public ApiStatus:** `BUSINESS_REJECTED`
- **Public error code/type:** `ApiError.business("err.fund.switching.in.progress", null, [], "SERVER")`
- **Citation:** `AccountGainLossRetrievalResponsePolicy.java:13-31`; `ApimAccountGainLossSourceAdapter.java:63-65`

### Account Gain/Loss -- ApimAccountGainLossSourceAdapter (TRPGetInvestGL)

- **Condition:** catch-all, sub-cause: unrecognized non-blank error (adapter's own `LOG.warn("event=account-gain-loss.downstream-rejected"...)`)
- **Detection site:** `ApimAccountGainLossSourceAdapter.java:66-71`
- **Handling:** `AccountGainLossRetrievalException.systemFailure()`. Note the validator/classifier distinction: `ApimResponseValidator.requireSuccessData` (`ApimResponseValidator.java:27-45`) treats an empty `data` array as VALID when `err-message` is empty (a legitimate "no records" success, line 39-43 only rejects `data == null`, not empty). `ApimEnvelopeSchemaClassifier` (used only for logging, never for adapter behavior) instead treats empty `data` + empty `err-message` as a malformed/contract-violation condition (confirmed production incident on TRPMemberLogin, `ApimEnvelopeSchemaClassifier.java:15-21`). The two components disagree on this exact edge case by design: the validator's job is correctness of business behavior (empty is a valid, if unusual, outcome), while the classifier's job is operational alerting (empty+empty is worth flagging as suspicious) -- this is not a bug, but is worth naming explicitly.
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `apim.business.rejected` WARN classifier; adapter's own `LOG.warn` (sanitized error code only, line 67-70); ERROR at mapping; `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, "SERVER")`
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
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, "SERVER")`
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
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, "SERVER")`
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
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, "SERVER")`
- **Citation:** `ApimAccountGainLossSourceAdapter.java:94-101`

### Fund Price -- ApimFundPriceSourceAdapter (shared by TRPGetFundPrice/mode-agnostic via `exchangeAndReconcile`, and reused by Fund Comparison's price-fetch path)

- **Condition:** `err.tpa.invalid.date`
- **Detection site:** `ApimFundPriceSourceAdapter.java:164-166`
- **Handling:** `FundPriceInvalidDateException.forInvalidDate()`, mapped via `FundPriceExceptionMappings.invalidDate()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.business.rejected` WARN classifier; `LogLevel.WARN` at mapping. ApimFundPriceSourceAdapter is NOT `@LogExecution`-annotated (confirmed absent from the 10-class list), and its use-case package uses `*UseCase`-named classes, not `*Service`, so the broad pointcut does not match either. Neither `ExecutionLoggingAspect` pointcut fires for this row.
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_BUSINESS`
- **Public HTTP status:** 400
- **Public ApiStatus:** `VALIDATION_FAILED`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_PRICE_REQUEST_INVALID`
- **Citation:** `FundPriceExceptionMappings.java:40-61`; `ApimFundPriceSourceAdapter.java:164-166`

### Fund Price -- ApimFundPriceSourceAdapter

- **Condition:** catch-all, sub-cause: missing response / unrecognized non-blank error
- **Detection site:** `ApimFundPriceSourceAdapter.java:159-169`
- **Handling:** `FundPriceRetrievalException.systemFailure()`. Note the validator/classifier distinction: `ApimResponseValidator.requireSuccessData` (`ApimResponseValidator.java:27-45`) treats an empty `data` array as VALID when `err-message` is empty (a legitimate "no records" success, line 39-43 only rejects `data == null`, not empty). `ApimEnvelopeSchemaClassifier` (used only for logging, never for adapter behavior) instead treats empty `data` + empty `err-message` as a malformed/contract-violation condition (confirmed production incident on TRPMemberLogin, `ApimEnvelopeSchemaClassifier.java:15-21`). The two components disagree on this exact edge case by design: the validator's job is correctness of business behavior (empty is a valid, if unusual, outcome), while the classifier's job is operational alerting (empty+empty is worth flagging as suspicious) -- this is not a bug, but is worth naming explicitly.
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.contract.invalid` ERROR (missing response) or `apim.business.rejected` WARN (unrecognized error), depending on sub-condition; `LogLevel.ERROR` at mapping
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_PRICE_RETRIEVAL_UNAVAILABLE`
- **Citation:** `FundPriceExceptionMappings.java:18-38`; `ApimFundPriceSourceAdapter.java:159-169`

### Fund Price -- ApimFundPriceSourceAdapter

- **Condition:** catch-all, sub-cause: malformed item count (confirmed: `items==null||items.size()!=1||items.get(0)==null`)
- **Detection site:** `ApimFundPriceSourceAdapter.java:170-178`
- **Handling:** `FundPriceRetrievalException.systemFailure()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.contract.invalid` ERROR; ERROR at mapping
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_PRICE_RETRIEVAL_UNAVAILABLE`
- **Citation:** `ApimFundPriceSourceAdapter.java:170-178`

### Fund Price -- ApimFundPriceSourceAdapter

- **Condition:** catch-all, sub-cause: crypto/mapping failure
- **Detection site:** `ApimFundPriceSourceAdapter.java:180-185` (outer `.onErrorMap`)
- **Handling:** `exception instanceof FundPriceRetrievalException||FundPriceInvalidDateException ? exception : systemFailure()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.exchange.failed` ERROR; ERROR at mapping
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_PRICE_RETRIEVAL_UNAVAILABLE`
- **Citation:** `ApimFundPriceSourceAdapter.java:180-185`

### Fund Price -- ApimFundPriceSourceAdapter

- **Condition:** catch-all, sub-cause: connection-level failure
- **Detection site:** `ApimFundPriceSourceAdapter.java:180-185`
- **Handling:** same outer `.onErrorMap`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.exchange.failed` ERROR; ERROR at mapping
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_PRICE_RETRIEVAL_UNAVAILABLE`
- **Citation:** `ApimFundPriceSourceAdapter.java:180-185`

### Fund Price -- ApimFundPriceModeSourceAdapter (TRPGetFundPriceMode -- separate adapter class; confirmed no invalid-date branch, every non-blank error maps to the single generic exception)

- **Condition:** catch-all, sub-cause: missing response / any non-blank error
- **Detection site:** `ApimFundPriceModeSourceAdapter.java:72-83`
- **Handling:** `FundPriceRetrievalException.systemFailure()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.contract.invalid`/`apim.business.rejected` from classifier (depending on sub-condition); ERROR at mapping. ApimFundPriceModeSourceAdapter is NOT `@LogExecution`-annotated (confirmed absent from the 10-class list), and its use-case package uses `*UseCase`-named classes, not `*Service`, so the broad pointcut does not match either. Neither `ExecutionLoggingAspect` pointcut fires for this row.
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
- **Citation:** `ApimFundPriceModeSourceAdapter.java:91-95` (both crypto and transport causes share this one undifferentiated sub-row since the adapter does not distinguish them structurally)

### Fund Comparison -- ApimFundComparisonOptionsSourceAdapter (TRPGetFundCmpOpt)

- **Condition:** catch-all, sub-cause: missing response / non-blank error
- **Detection site:** `ApimFundComparisonOptionsSourceAdapter.java:73-83`
- **Handling:** `FundComparisonRetrievalException.systemFailure()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** classifier event depending on sub-condition; ERROR at mapping. ApimFundComparisonOptionsSourceAdapter is NOT `@LogExecution`-annotated (confirmed absent from the 10-class list), and its use-case package uses `*UseCase`-named classes, not `*Service`, so the broad pointcut does not match either. Neither `ExecutionLoggingAspect` pointcut fires for this row.
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
- **Citation:** `ApimFundComparisonOptionsSourceAdapter.java:91-95` (both crypto and transport causes share one undifferentiated sub-row, same reason as Fund Price Mode)

### Fund Fact Sheet -- ApimFundFactSheetSourceAdapter (TRPGetFundFactSheet)

- **Condition:** missing response / non-empty error
- **Detection site:** `ApimFundFactSheetSourceAdapter.java:95-101` (`toRawData`, confirmed condition: `errMessage==null||!errMessage.isEmpty()`)
- **Handling:** `FundFactSheetRetrievalException.systemFailure()`
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** classifier event depending on sub-condition; `LogLevel.ERROR` at mapping
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"SYSTEM"` / `ErrorCodes.FUND_FACT_SHEET_RETRIEVAL_UNAVAILABLE`
- **Citation:** `ApimFundFactSheetSourceAdapter.java:95-101`

### Fund Fact Sheet -- ApimFundFactSheetSourceAdapter

- **Condition:** null item/list/row (adapter's own `LOG.error`, confirmed events `EVENT_NULL_ITEM`/`EVENT_NULL_LIST`/`EVENT_NULL_ROW`)
- **Detection site:** `ApimFundFactSheetSourceAdapter.java:106-126`
- **Handling:** `FundFactSheetRetrievalException.systemFailure()`
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** feature-level `LOG.error` (one of the three named events, no value logged); ERROR at mapping
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"SYSTEM"` / `ErrorCodes.FUND_FACT_SHEET_RETRIEVAL_UNAVAILABLE`
- **Citation:** `ApimFundFactSheetSourceAdapter.java:106-126`

### Fund Fact Sheet -- ApimFundFactSheetSourceAdapter

- **Condition:** no data items (empty, valid per no-funds contract) -- NOT an error
- **Detection site:** `ApimFundFactSheetSourceAdapter.java:102-105`
- **Handling:** **Correction:** `items==null||items.isEmpty()` -> `Mono.error(systemFailure())` (confirmed, lines 103-105) -- despite the class Javadoc stating "no funds is a non-empty data list whose items carry empty row lists", an actually-EMPTY `data` array is treated as an ERROR here, not a valid empty result. This may be a latent inconsistency between the adapter's stated no-funds contract and its own empty-data handling -- not resolved this pass; flagged as an open question.
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `apim.contract.invalid` ERROR classifier; ERROR at mapping
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"SYSTEM"` / `ErrorCodes.FUND_FACT_SHEET_RETRIEVAL_UNAVAILABLE`
- **Citation:** `ApimFundFactSheetSourceAdapter.java:102-105` (contradicts class Javadoc lines 32-33)

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
- **Detection site:** `ApimUpdatePersonalInformationAdapter.java:61-67` (`toResults`, called via `.map()` on `executor.postEnvelope(request)`, line 44 -- a thrown exception inside `.map()`'s mapper function is converted to a Reactor onError signal, same effect as `.flatMap` + `Mono.error`)
- **Handling:** `throw new ApimException(BAD_GATEWAY, APIM_RESPONSE_INVALID, ...)`, mapped via `ApimExceptionMappings.upstream()`. This also calls `ApimResponseValidator.requireSuccessData` (line 61), making it a member of the shared-validator pattern for its own missing/empty-data sub-case.
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.contract.invalid` ERROR classifier; `http.api.error` ERROR, no stack trace. Confirmed `@LogExecution(value="apim.updatePersonalInformation", logArgs=true)` directly on this method (line 32-33) -- ApimUpdatePersonalInformationAdapter is confirmed `@LogExecution`-annotated (unfiltered grep, 10-class list). A failure here also produces `method.execution.error` ERROR with full stack trace (`ExecutionLoggingAspect.java:161-170`).
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
- **Ordered log lines:** no classifier-level flag expected (raw data was structurally fine); `http.api.error` ERROR, no stack trace. ApimUpdatePersonalInformationAdapter is confirmed `@LogExecution`-annotated (unfiltered grep, 10-class list). A failure here also produces `method.execution.error` ERROR with full stack trace (`ExecutionLoggingAspect.java:161-170`).
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 502
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `APIM_RESPONSE_INVALID`
- **Citation:** `ApimUpdatePersonalInformationAdapter.java:70-75`

### Reference Date -- ApimReferenceDateRefreshAdapter (TRPGetWebSysDate)

- **Condition:** missing payload / blank err-message field / missing data (confirmed: NOT via `ApimResponseValidator` -- this adapter's own inline checks in `toReferenceDate`)
- **Detection site:** `ApimReferenceDateRefreshAdapter.java:47-54`
- **Handling:** `throw invalid(...)` -> `ApimException(BAD_GATEWAY, APIM_RESPONSE_INVALID, message)`, called via `.map(this::toReferenceDate)` on `apimExchangeExecutor.postEnvelope(request)` (line 44)
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** classifier event depending on sub-condition; `http.api.error` ERROR, no stack trace. **Correction:** `RefreshReferenceDateService.execute(RefreshReferenceDateCommand)` (confirmed method name via direct source read) IS the use-case entry point for this adapter's feature, and it matches the broad `*Service.execute(..)` pointcut. ApimReferenceDateRefreshAdapter is NOT `@LogExecution`-annotated (confirmed absent from the 10-class list). If this exception propagates through `RefreshReferenceDateService.execute(...)` (confirmed method name via direct source read), the broad package-wide pointcut (`execution(public * ...usecase..*Service.execute(..))`, `ExecutionLoggingAspect.java:52-56`) also fires, logging `method.execution.error` ERROR with full stack trace.
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 502
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `APIM_RESPONSE_INVALID`
- **Citation:** `ApimReferenceDateRefreshAdapter.java:47-54`; `RefreshReferenceDateService.java:26`

### Reference Date -- ApimReferenceDateRefreshAdapter

- **Condition:** unparsable date (`DateTimeParseException` on a present, well-formed-looking field)
- **Detection site:** `ApimReferenceDateRefreshAdapter.java:55-62`
- **Handling:** `throw new ApimException(BAD_GATEWAY, APIM_RESPONSE_INVALID, ..., ex)` -- confirmed this preserves the `DateTimeParseException` as the cause
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** no classifier-level flag expected (field was present, non-blank); `http.api.error` ERROR, no stack trace. ApimReferenceDateRefreshAdapter is NOT `@LogExecution`-annotated (confirmed absent from the 10-class list). If this exception propagates through `RefreshReferenceDateService.execute(...)` (confirmed method name via direct source read), the broad package-wide pointcut (`execution(public * ...usecase..*Service.execute(..))`, `ExecutionLoggingAspect.java:52-56`) also fires, logging `method.execution.error` ERROR with full stack trace.
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- **Public HTTP status:** 502
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `APIM_RESPONSE_INVALID`
- **Citation:** `ApimReferenceDateRefreshAdapter.java:55-62`

### Shared `ApimResponseValidator`-reliant adapters (Contribution Summary, Member Info, Notice Message, Notification Read Status, Reference Data Countries, General Metadata -- exactly these 6)

- **Condition:** any non-empty `err-message` on an otherwise successful HTTP exchange
- **Detection site:** `ApimResponseValidator.java:36-38`. Note the validator/classifier distinction: `ApimResponseValidator.requireSuccessData` (`ApimResponseValidator.java:27-45`) treats an empty `data` array as VALID when `err-message` is empty (a legitimate "no records" success, line 39-43 only rejects `data == null`, not empty). `ApimEnvelopeSchemaClassifier` (used only for logging, never for adapter behavior) instead treats empty `data` + empty `err-message` as a malformed/contract-violation condition (confirmed production incident on TRPMemberLogin, `ApimEnvelopeSchemaClassifier.java:15-21`). The two components disagree on this exact edge case by design: the validator's job is correctness of business behavior (empty is a valid, if unusual, outcome), while the classifier's job is operational alerting (empty+empty is worth flagging as suspicious) -- this is not a bug, but is worth naming explicitly.
- **Handling:** mapped via `ApimExceptionMappings.upstream()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.contract.invalid` ERROR classifier; `http.api.error` ERROR, no stack trace
- **Idempotency replay:** No -- all 6 are read-only or non-mutating queries
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 502
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `APIM_RESPONSE_INVALID`
- **Citation:** `ApimResponseValidator.java:27-45`; confirmed sites: `ApimContributionSummaryAdapter.java:54`, `ApimMemberInfoAdapter.java:59`, `ApimNoticeMessageAdapter.java:72`, `ApimNotificationReadStatusAdapter.java:67`, `ApimReferenceDataCountriesAdapter.java:50`, `ApimGeneralMetadataSourceAdapter.java:88`

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
- Specific `DOWNSTREAM_BUSINESS` rows (5 total): Registration `err.tpa.member.not.found`, Account Balance `NOT_FOUND`/`MISMATCH` (2), Account Gain/Loss fund-switching, Fund Price invalid-date
- Catch-all `systemFailure()`/`onErrorReturn`/`safeFailure`: classified by cause, split into per-cause sub-rows; public exception/status/body unchanged across sub-causes.

## 4. Approved exceptions to the M2b "no change" rules

- **E1:** Registration `err.tpa.member.not.found` DOWNSTREAM_CONTRACT -> DOWNSTREAM_BUSINESS (ERROR->WARN), pinned fail-first in the Registration feature commit.
- **E2:** no `DOWNSTREAM_*` value may reach the frontend (section 6, sub-task 2b.2a).

## 5. Incident ERROR per category

- **DOWNSTREAM_BUSINESS:** feature-level WARN log closest to the business decision.
- **DOWNSTREAM_CONTRACT:** `apim.contract.invalid` (`ApimExchangeOutcomeLogger.java:70-72`).
- **DOWNSTREAM_INVALID_RESPONSE:** `apim.exchange.failed` (`ApimExchangeOutcomeLogger.java:52-65`).
- **DOWNSTREAM_TRANSPORT:** `ApimWebClientFacade.java:409` for HTTP-error responses; `apim.exchange.failed` for connection-level failures (facade never reached).
- **LOCAL_SYSTEM:** `CatchAllExceptionMapping`'s own `http.api.error` line (richest available -- already carries a stack trace).

## 6. Public DOWNSTREAM exposure

(a) `ApiError.downstreamSystem()` -- zero production callers (two test-only references). (b) `WorkflowErrorType.DOWNSTREAM_SYSTEM` -- 4 Registration call sites, confirmed rendered verbatim into public JSON via `WorkflowMutationResponseMapper.toApiError`'s `descriptor.type().name()`. (c) literal `"DOWNSTREAM_SYSTEM"` in `ApimExceptionMappings.java:61` and `RegistrationExceptionMappings.java:31` -- the main exposure path by traffic volume. Replacement: `ApiError.type = "SYSTEM"` (`ApiError.java:45-47`, confirmed verbatim).

### Sub-task 2b.2a

Fail-first characterization update, replace every `DOWNSTREAM_SYSTEM` occurrence, extend `PublicDownstreamStatusIsolationGuardTest` with a rendered-JSON behavior check. Waits for Nick to confirm FE independence from `DOWNSTREAM_*` values.

## 7. Sub-task plan

2b.2 characterization pack (must also resolve: `UsernameRecoveryStartResponsePolicy`'s exact status/code mapping; the three ungrounded Login endpoints' exact propagated exception types; Forgot Password details-check's exact downstream re-throw path); 2b.2a (section 6); 2b.3 shared classifier; 2b.4..2b.n per-feature commits.

## 8. Open questions

- `RegistrationHttpResponses.status()`'s duplicate `SYSTEM_ERROR`/`BUSINESS_REJECTED` branches (lines 60/63, 66/67) make the 503 and the second `BUSINESS_REJECTED` check unreachable dead code; no change proposed in M2b, flagged for a future cleanup.
- Public status 502 vs 500 divergence for generic `ApimException` remains a decision, not a fact.
- 4xx vs 5xx downstream status both resolve identically via the facade today; whether a 4xx should classify differently (business vs transport) is open.
- Fund Fact Sheet's empty-`data` handling appears to contradict its own class Javadoc's stated no-funds contract; not reconciled this pass.
- **Confirmed structural gap, not yet a table row (each endpoint would need its own feature-specific exception trace to assign one `FailureOrigin`):** `LoginController.cancel`/`resend`/`verify` (`LoginController.java:82-115,164-213,215-279`) and `ForgotPasswordController.details` (`ForgotPasswordController.java:153-195`) each wrap only the SYNCHRONOUS command-construction step in `try/catch(RuntimeException)`, unlike their sibling endpoints (`startLogin`/`send`, `ForgotPasswordController.start`/`complete`/`resend`/`verify`), which all have a reactive `.onErrorResume(...)` on the returned Mono. An uncaught async failure on these four endpoints propagates to the central handler (`CatchAllExceptionMapping` or a registered mapping), unlike their siblings which resolve via the feature's own `*HttpResponses` dispatcher. Whether this is intentional or a gap to close, and what each endpoint's actual propagated exception type/status would be, is open and requires per-endpoint tracing in 2b.2.
- Whether confirmed swallow sites should publish a `FailureEvent` (M3 3.7).


# 07 - APIM Error Classification Design (Final, corrected)

Sub-task 2b.1. Docs only (`docs/design/15-exception-handling/07-apim-error-classification.md`), no production or test change. This replaces the prior version, incorporating Nick's review corrections.

## Corrections applied in this pass

1. **Categories assigned from the row data structure, not grep.** Every row carries exactly one canonical `FailureOrigin`, enforced by a hard assertion in the generator. Fixed: Registration `NOT_ELIGIBLE` is `DOWNSTREAM_BUSINESS` only; Reference Date split into two sub-rows by condition; Fund Fact Sheet's catch-all added to the split list; the business-rule row count is corrected to 5 (Account Balance contributes 2 of the 5).
2. **No "mixed"/"classify by cause" category remains.** Every catch-all condition (Account Balance, Account Gain/Loss, Fund Price, Fund Comparison, Fund Fact Sheet `systemFailure()`; `ApimForgotPasswordCompletionAdapter.safeFailure`) is split into one sub-row per cause (business / contract / invalid-response / transport / local), each with exactly one `FailureOrigin` and the same public status/body as the undivided catch-all.
3. **Ordered log lines corrected for exceptions raised after `postEnvelope`'s onNext.** `DefaultApimExchangeExecutor.postEnvelope`'s `doOnEach` (`DefaultApimExchangeExecutor.java:60-69`) only observes the single terminal signal (onNext or onError) of the Mono it returns. A failure thrown by an adapter, validator, or mapper AFTER that Mono has already emitted onNext is invisible to `doOnEach` and never logs `apim.exchange.failed`. For these rows, the executor's envelope-content classifier (`ApimEnvelopeSchemaClassifier`, invoked inside `logOutcome`'s onNext branch) is the relevant earlier log instead: `apim.contract.invalid` ERROR for missing/malformed content, `apim.business.rejected` WARN for a non-empty err-message. `apim.exchange.failed` applies only to genuine Mono onError signals: facade HTTP-error responses, and decrypt/mapping failures raised inside the executor's own `.flatMap` chain. Affected rows: Forgot Password completion's missing-envelope row, Forgot Password username lookup (both conditions), Personal Information (both conditions), Reference Date (both rows), the 6-adapter shared validator group, Username Recovery's `TrpSearchMemberApimDownstreamException` row, and Registration's `EMPTY_DATA` row.
4. **Registration `NOT_ELIGIBLE`/`EMPTY_DATA` rows' log lines are now concrete, not UNVERIFIED,** citing the specific classifier-driven event (`apim.business.rejected` WARN or `apim.contract.invalid` ERROR) now that the classifier's rule is confirmed.
5. **`ExecutionLoggingAspect` traced per row**, not asserted as a blanket note. Two pointcuts exist: `@annotation(logExecution)` (10 confirmed classes) and the broader `execution(public * ...usecase..*Service.execute(..))`, which fires on ANY class whose simple name ends in `Service` under `application.usecase`, regardless of annotation. `GetAccountBalanceService` and `GetAccountGainLossService` match this pattern by name (their exact method signatures were not independently read this pass). Fund Price's own use-case classes are named `*UseCase`, not `*Service` -- confirmed by directory listing that neither aspect pointcut applies there.
6. **Crypto/mapping-failure sub-rows added to every catch-all split** (Account Balance, Account Gain/Loss, Fund Price, Fund Comparison, Fund Fact Sheet): `ApimCryptoException`/`ApimResponseMappingException` are both plain `RuntimeException` subtypes (not `ApimException`), raised inside `DefaultApimExchangeExecutor`'s own decryption step, bypassing each adapter's inner try/catch entirely and resolving only via the outer `.onErrorMap`/`.onErrorMap` catch-all -- this is the exact mechanism behind the confirmed account-summary-500 incident (an unknown field in a TRPGetMemberAccBal response caused a mapping failure and a 503).
7. **4xx vs 5xx distinguished where it matters; added as an open question where the code does not distinguish it.** `ApimWebClientFacade.java:386` gates `handleErrorBody` on `statusCode.isError()`, which covers both 4xx and 5xx -- any HTTP-error-status response (not only 5xx) reaches `handleErrorBody` and is wrapped into `ApimException(BAD_GATEWAY, ...)`. Separately, connection-level failures (`WebClientRequestException`/`TimeoutException`) are retried via `isTransientBusinessFailure` and, once exhausted, **re-raised unconverted** (`ApimWebClientFacade.java:225`) -- they never become an `ApimException` at all, and since neither type is registered in `ExceptionMappingRegistryConfig`, they resolve via `CatchAllExceptionMapping` (500), not 502. Every `DOWNSTREAM_TRANSPORT` row below is corrected to reflect this split. Section 3 has no explicit rule distinguishing a 4xx-status `ApimException` from a 5xx-status one (both currently land in `DOWNSTREAM_TRANSPORT` via the same facade path) -- this is listed as an open question in section 8.
8. **Shared validator group membership corrected to exactly 6 adapters**, with their precise confirmed line numbers (`ApimContributionSummaryAdapter.java:54`, `ApimMemberInfoAdapter.java:59`, `ApimNoticeMessageAdapter.java:72`, `ApimNotificationReadStatusAdapter.java:67`, `ApimReferenceDataCountriesAdapter.java:50`, `ApimGeneralMetadataSourceAdapter.java:88`). `SharedApimTrpGetAccountListTransport` and `SharedApimTrpMemberLoginTransport` do not call `ApimResponseValidator` at all and are documented only in their own dedicated Login rows.
9. **`TrpMemberLoginAdapter`'s `IllegalStateException` throw site is now confirmed** (inside `DefaultApimExchangeExecutor`'s own `switchIfEmpty`/`validateCommon`, lines 97 and 149-154 -- both genuinely inside the `doOnEach`-wrapped chain). Two new rows added for both `TrpMemberLoginAdapter` and `TrpSearchMemberAdapter` (Username Recovery): since both adapters only filter specific exception types in their `onErrorResume` chains, a facade-raised `ApimException` or a crypto/mapping exception that doesn't match their filters propagates past the adapter entirely to `ApimExceptionMappings` (502) or `CatchAllExceptionMapping` (500) respectively.
10. **Three previously-UNVERIFIED items resolved by reading source directly:**
    - `CatchAllExceptionMapping`: HTTP 500, `ApiStatus.SYSTEM_ERROR`, type `"SYSTEM"`, `ErrorCodes.SYSTEM_UNEXPECTED`, `LogLevel.ERROR` **with stack trace** (the only mapping in this inventory that logs a stack trace via the handler itself) -- used to resolve Registration completion's pre-dispatch row, Forgot Password username-lookup's missing-envelope row, and both new Login/Username-Recovery crypto-propagation rows.
    - `ForgotPasswordUnderLockDetailsVerifier` IS the real consumer of `ForgotPasswordDetailsDownstreamException` (confirmed directly; `ForgotPasswordDetailsOtpProtectedOperation` itself never references it). It logs `event=forgot-password.details-otp.failed` ERROR (exception class name only, no stack trace) then swallows to `ForgotPasswordDetailsStatus.SYSTEM_FAILED`. The further mapping of `SYSTEM_FAILED` to a public HTTP status is a narrower, still-open gap (one layer further up), not the whole chain.
    - `WorkflowMutationResponseMapper.toApiError` confirms `WorkflowErrorType.DOWNSTREAM_SYSTEM` **does** reach the rendered public JSON verbatim via `descriptor.type().name()` -- this is no longer an open question; it is a confirmed exposure path, documented in section 6.
    - `PublicDownstreamStatusIsolationGuardTest`'s exact scan re-confirmed directly: it only scans source for the literal tokens `"ApiStatus.DOWNSTREAM_ERROR"`/`"ApiStatus.DOWNSTREAM_REJECTED"` in `adapter/in/web`; it does not scan for `"DOWNSTREAM_SYSTEM"` and does not render any JSON.
11. **Registration `NOT_ELIGIBLE`'s public HTTP status corrected to UNVERIFIED with a precise reason:** no file under `adapter/in/web` references `RegistrationLookupStatus.NOT_ELIGIBLE` by name (confirmed by an empty grep). It is also plausible this lookup has no dedicated public HTTP endpoint at all and is consumed only internally. The earlier "422 / normal 200" claim is removed as unsupported.
12. **Section 5's `DOWNSTREAM_TRANSPORT` incident-line description corrected** to distinguish the HTTP-error-status case (`ApimWebClientFacade.java:409`) from the connection-failure case (which never reaches that line at all, per correction 7).
13. **Section 8's catch-all-split question removed** (decided: classify by cause, now applied directly in every row above); the 4xx-vs-5xx question is added in its place.

## 1. Inventory table (49 rows)

One row per downstream condition (or, for catch-all handlers, one row per distinguishable cause) per adapter. Each row states: Condition / Detection site / Handling / Message policy / Ordered log lines / Idempotency replay / Origin tag today / Proposed `FailureOrigin` (exactly one) / Public HTTP status / Public `ApiStatus` / Public error code or type / Citation.

### Registration -- ApimRegistrationLookupAdapter (TRPSearchMember)

- **Condition:** `err.tpa.member.not.found` -> `NOT_ELIGIBLE`
- **Detection site:** `registration/mapper/TrpSearchMemberApimResultMapper.toResult`, lines 22-24
- **Handling:** Returned as a normal `RegistrationLookupResult(NOT_ELIGIBLE, ...)`; `ApimRegistrationLookupAdapter.withFailureLogging`'s `doOnEach` calls `recordDownstreamRejection` (`ApimRegistrationLookupAdapter.java:101`), publishing a `FailureEvent` (`ApimRegistrationLookupAdapter.java:147`)
- **Message policy:** N/A -- successful application result, never an `ExceptionMapping`
- **Ordered log lines:** (1) `apim.business.rejected` WARN from `ApimExchangeOutcomeLogger`'s envelope-content classifier (`ApimEnvelopeSchemaClassifier`: non-empty err-message -> business-rejected), since this envelope resolves successfully (onNext) and the classifier runs on its content before any feature code inspects it, per `DefaultApimExchangeExecutor.logOutcome`'s onNext branch (`DefaultApimExchangeExecutor.java:71-82`). (2) `FailureEvent` published, logged ERROR today by `StructuredFailureEventLogger` (origin not in `WARN_ORIGINS`) -- E1 moves this to WARN. (3) ApimRegistrationLookupAdapter is confirmed `@LogExecution`-annotated (ExecutionLoggingAspect.java:40-45 pointcut), so a failure here also produces `method.execution.error` at ERROR with the full stack trace (`ExecutionLoggingAspect.java:161-170`).
- **Idempotency replay:** No -- lookup is called directly, not itself idempotency-wrapped
- **Origin tag today:** `FailureOrigin.DOWNSTREAM_CONTRACT` (`ApimRegistrationLookupAdapter.java:121`)
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_BUSINESS` only, per E1 (section 4)
- **Public HTTP status:** **UNVERIFIED:** no file under `adapter/in/web` references `RegistrationLookupStatus.NOT_ELIGIBLE` by name (confirmed by an empty grep result across the whole web package). `RegistrationHttpResponses.java` was found and dumped, but it maps a generic `ApiStatus` (not `RegistrationLookupStatus` directly) to an HTTP status, so there is an intermediate mapping step not located this pass. It is also plausible this lookup has no dedicated public HTTP endpoint at all and is only consumed internally (e.g. by `RegistrationCompleteProtectedOperation.refreshTrusts`), in which case 'public status' may not directly apply to this condition -- not confirmed either way.
- **Public ApiStatus:** n/a (see above)
- **Public error code/type:** `err.tpa.member.not.found` (downstream code) / `RegistrationErrorKey.START_VERIFICATION_FAILED` (FailureEvent's public key, logging-only; not confirmed to be the HTTP-response public code)
- **Citation:** `ApimRegistrationLookupAdapter.java:144-159`; `TrpSearchMemberApimResultMapper.java:22-24` (registration package)

### Registration -- ApimRegistrationLookupAdapter (TRPSearchMember)

- **Condition:** `EMPTY_DATA` / `INVALID_ACCOUNT_DATA` / `UNEXPECTED_ERROR` reasons
- **Detection site:** `registration/mapper/TrpSearchMemberApimResultMapper.toResult`, lines 25-28, 30-33, 39-42
- **Handling:** Throws `RegistrationLookupDownstreamException`, mapped by `RegistrationExceptionMappings.lookupDownstream()` (`RegistrationExceptionMappings.java:18-44`)
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** (1) `apim.contract.invalid` ERROR from `ApimExchangeOutcomeLogger`'s envelope-content classifier (`ApimEnvelopeSchemaClassifier`: missing err-message, or empty err-message with missing/empty data -> malformed), running on the onNext signal before this exception is even thrown (`DefaultApimExchangeExecutor.java:71-82`); (2) `FailureEvent`, origin `DOWNSTREAM_CONTRACT`, ERROR; (3) `http.api.error` ERROR, no stack trace (`RegistrationExceptionMappings.java:35`, final arg `false`). ApimRegistrationLookupAdapter is confirmed `@LogExecution`-annotated (ExecutionLoggingAspect.java:40-45 pointcut), so a failure here also produces `method.execution.error` at ERROR with the full stack trace (`ExecutionLoggingAspect.java:161-170`).
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
- **Handling:** `.onErrorMap(ApimProfileResolutionException.class, RegistrationCompletionPreDispatchException::new)`. `DefaultIdempotencyExecutor.classifyProtectedFailureThenError` classifies the failure via `command.failureClassifier().classify(failure)` (`SharedCompleteRegistrationService.java:163-166`, `PROVEN_NO_SIDE_EFFECT`), then **always re-throws the original failure unchanged** after idempotency-state bookkeeping. `RegistrationCompletionPreDispatchException` is absent from `ExceptionMappingRegistryConfig`'s 20-entry list, so it resolves via `CatchAllExceptionMapping` (now confirmed, `CatchAllExceptionMapping.java:30-40`).
- **Message policy:** N/A -- resolves via the mandatory catch-all, not a registered mapping
- **Ordered log lines:** No executor-layer log (this is a local configuration failure, not a downstream envelope issue); `http.api.error` ERROR **with stack trace** (`CatchAllExceptionMapping.java:38`, final arg `true` -- the only mapping in this inventory that logs a stack trace via the handler itself). ApimRegistrationCompletionAdapter is confirmed `@LogExecution`-annotated (ExecutionLoggingAspect.java:40-45 pointcut), so a failure here also produces `method.execution.error` at ERROR with the full stack trace (`ExecutionLoggingAspect.java:161-170`).
- **Idempotency replay:** Yes -- Registration complete is idempotency-protected
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.LOCAL_SYSTEM`
- **Public HTTP status:** 500 (`CatchAllExceptionMapping.java:32`, `HttpStatus.INTERNAL_SERVER_ERROR`)
- **Public ApiStatus:** `SYSTEM_ERROR` (`CatchAllExceptionMapping.java:33`)
- **Public error code/type:** `"SYSTEM"` / `ErrorCodes.SYSTEM_UNEXPECTED` (`CatchAllExceptionMapping.java:34-35`)
- **Citation:** `ApimRegistrationCompletionAdapter.java:54-55`; `DefaultIdempotencyExecutor.java` (re-throw confirmed in the prior investigation pass); `CatchAllExceptionMapping.java:18-40`

### Forgot Password -- ApimForgotPasswordUsernameLookupAdapter (TRPChkCustId)

- **Condition:** missing envelope/response (`envelope == null || envelope.getResponse() == null`)
- **Detection site:** `ApimForgotPasswordUsernameLookupAdapter.java:79-81` (`toTransportEnvelope`, this adapter's own method -- NOT `SharedApimTrpChkCustIdTransport`, which is a separate, parallel implementation of near-identical logic not confirmed to be used by this call path)
- **Handling:** `throw new IllegalStateException("TRPChkCustId response envelope is missing response")` (`ApimForgotPasswordUsernameLookupAdapter.java:80`). `IllegalStateException` is absent from `ExceptionMappingRegistryConfig`'s list, so it resolves via `CatchAllExceptionMapping`.
- **Message policy:** N/A -- resolves via the mandatory catch-all
- **Ordered log lines:** (1) possibly `apim.contract.invalid` ERROR from the envelope-content classifier if the raw envelope already looked malformed to it (not confirmed for this exact null-response edge case); (2) `doOnError` at `ApimForgotPasswordUsernameLookupAdapter.java:68-74` logs `event=forgot-password.username-lookup.failed` at ERROR with the exception class name and full stack trace (the `error` object itself is passed as the final `log.error` argument, line 74); (3) `http.api.error` ERROR with stack trace via `CatchAllExceptionMapping`
- **Idempotency replay:** Yes -- participates in Forgot Password start
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 500 (`CatchAllExceptionMapping.java:32`)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"SYSTEM"` / `SYSTEM_UNEXPECTED`
- **Citation:** `ApimForgotPasswordUsernameLookupAdapter.java:77-91`; `CatchAllExceptionMapping.java:18-40`

### Forgot Password -- ApimForgotPasswordUsernameLookupAdapter (TRPChkCustId)

- **Condition:** no err-message AND no data, or data present but every item malformed
- **Detection site:** `ApimForgotPasswordUsernameLookupAdapter.java:84-91` (own method, same caveat re: `SharedApimTrpChkCustIdTransport` as the row above)
- **Handling:** `throw new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, ...)` (`ApimForgotPasswordUsernameLookupAdapter.java:87-90` or `:96-99`), mapped via `ApimExceptionMappings.upstream()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** (1) `apim.contract.invalid` ERROR from the envelope-content classifier (this condition matches the classifier's "empty err-message with missing/empty data" malformed rule, running on the already-resolved onNext envelope before this adapter code executes); (2) `doOnError` logs `event=forgot-password.username-lookup.failed` ERROR with stack trace (`ApimForgotPasswordUsernameLookupAdapter.java:68-74`); (3) `http.api.error` ERROR, no stack trace (`ApimExceptionMappings.java`, final arg `false`)
- **Idempotency replay:** Yes
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 502
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `APIM_RESPONSE_INVALID`
- **Citation:** `ApimForgotPasswordUsernameLookupAdapter.java:77-102`

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)

- **Condition:** missing response envelope
- **Detection site:** `ApimForgotPasswordCompletionAdapter.java:64-68`
- **Handling:** `throw new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, ...)`, mapped via `ApimExceptionMappings.upstream()`. This check runs on the envelope AFTER `executor.postEnvelope()` already resolved it successfully (onNext) -- the facade/executor layer never sees this as an error signal.
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** (1) `apim.contract.invalid` ERROR from the envelope-content classifier if the raw envelope's err-message/data already looked malformed at that point (plausible for a missing-response condition, but the classifier's exact handling of a null-`response`-within-non-null-`envelope` edge case was not independently confirmed this pass); NOT `apim.exchange.failed` -- that event is reserved for genuine `Mono` `onError` signals (facade HTTP errors, decrypt/mapping failures), not for content inspected after a successful onNext; (2) `http.api.error` ERROR, no stack trace. ApimForgotPasswordCompletionAdapter is confirmed `@LogExecution`-annotated (ExecutionLoggingAspect.java:40-45 pointcut), so a failure here also produces `method.execution.error` at ERROR with the full stack trace (`ExecutionLoggingAspect.java:161-170`).
- **Idempotency replay:** Yes -- Forgot Password complete
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 502
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `APIM_RESPONSE_INVALID`
- **Citation:** `ApimForgotPasswordCompletionAdapter.java:62-72`

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)

- **Condition:** crypto failure (`ApimCryptoException`/`ApimResponseMappingException`, `isCryptoFailure` true)
- **Detection site:** `ApimPayloadCryptoService.decryptResponseFields`, line 164 (`ApimCryptoException`); `ApimResponseMappingException` wraps Jackson mapping failures similarly. Both extend `RuntimeException` directly (not `ApimException`). Classifier at `ApimCryptoFailureClassifier.java:15-16`. Confirmed sole caller of `isCryptoFailure` (`ApimForgotPasswordCompletionAdapter.java:57`)
- **Handling:** `.onErrorResume(ApimCryptoFailureClassifier::isCryptoFailure, e -> Mono.just(unknownOutcome()))` (`ApimForgotPasswordCompletionAdapter.java:56-58`)
- **Message policy:** N/A -- swallowed before any `ExceptionMapping`
- **Ordered log lines:** Decryption happens inside `DefaultApimExchangeExecutor.postPlain`/`postEncrypted`'s own `.flatMap`, which IS wrapped by `postEnvelope()`'s `doOnEach` (`DefaultApimExchangeExecutor.java:60-69`). This is a genuine `Mono` `onError` signal (not an onNext-then-later-exception case), so `apim.exchange.failed` ERROR fires via `logOutcome`'s onError branch (`DefaultApimExchangeExecutor.java:77-81`) **before** this adapter's downstream `onErrorResume` swallows it. ApimForgotPasswordCompletionAdapter is confirmed `@LogExecution`-annotated (ExecutionLoggingAspect.java:40-45 pointcut), so a failure here also produces `method.execution.error` at ERROR with the full stack trace (`ExecutionLoggingAspect.java:161-170`).
- **Idempotency replay:** Yes (Forgot Password complete)
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- **Public HTTP status:** n/a -- swallowed to success-shaped `unknownOutcome()`
- **Public ApiStatus:** n/a
- **Public error code/type:** n/a
- **Citation:** `ApimPayloadCryptoService.java:158-166`; `ApimCryptoFailureClassifier.java:11-18`; `ApimForgotPasswordCompletionAdapter.java:52-58`; `DefaultApimExchangeExecutor.java:60-82`

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)

- **Condition:** `safeFailure` catch-all, sub-cause: HTTP 5xx-status response (via facade `handleErrorBody`)
- **Detection site:** `ApimForgotPasswordCompletionAdapter.java:74-81`
- **Handling:** `.onErrorMap(this::safeFailure)` -> `ApimException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.SYSTEM_UNEXPECTED, ..., t)`. Note: regardless of this adapter's own choice of `INTERNAL_SERVER_ERROR` here, `ApimExceptionMappings.upstream()` forwards whatever `exception.getStatusCode()` the `ApimException` instance actually carries -- the cause's own status, if it was itself a facade-raised `ApimException`, is NOT re-read; this adapter always constructs a fresh `ApimException(INTERNAL_SERVER_ERROR, ...)` wrapping the cause, so the final status is always 500 for this specific catch-all, regardless of the original cause's nature.
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.exchange.failed` ERROR (genuine onError signal from the facade); `http.api.error` ERROR, no stack trace. ApimForgotPasswordCompletionAdapter is confirmed `@LogExecution`-annotated (ExecutionLoggingAspect.java:40-45 pointcut), so a failure here also produces `method.execution.error` at ERROR with the full stack trace (`ExecutionLoggingAspect.java:161-170`).
- **Idempotency replay:** Yes
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** **500** (this adapter's own `safeFailure` always constructs `INTERNAL_SERVER_ERROR`, not 502 -- distinct from the facade's own default of `BAD_GATEWAY`, since the facade's own HTTP-error `ApimException` is the CAUSE here, not the exception actually mapped)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `SYSTEM_UNEXPECTED`
- **Citation:** `ApimForgotPasswordCompletionAdapter.java:74-81`

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)

- **Condition:** `safeFailure` catch-all, sub-cause: connection-level failure (`WebClientRequestException`/`TimeoutException`, retries exhausted)
- **Detection site:** `ApimForgotPasswordCompletionAdapter.java:74-81`
- **Handling:** Same `.onErrorMap(this::safeFailure)` path; `safeFailure` wraps ANY throwable into `ApimException(INTERNAL_SERVER_ERROR, SYSTEM_UNEXPECTED, ..., t)` uniformly, regardless of whether the cause was itself an `ApimException`, a raw `WebClientRequestException`, or anything else -- so this sub-cause and the HTTP-5xx sub-cause above share the identical 500 public shape here (this adapter's `safeFailure` is a true universal catch-all, unlike the 5 Fund/Account catch-alls which re-throw the triggering business exception unchanged and only convert unrecognized types).
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.exchange.failed` ERROR; `http.api.error` ERROR, no stack trace
- **Idempotency replay:** Yes
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** 500
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `SYSTEM_UNEXPECTED`
- **Citation:** `ApimForgotPasswordCompletionAdapter.java:74-81`; `ApimWebClientFacade.java:212-241` (retry/re-raise path)

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)

- **Condition:** `safeFailure` catch-all, sub-cause: any other non-`ApimException` runtime failure (a genuine local defect in our own code, not caused by the downstream call at all)
- **Detection site:** `ApimForgotPasswordCompletionAdapter.java:74-81`
- **Handling:** Same code path as the two rows above; the current implementation does not structurally distinguish this sub-cause from the transport sub-cause at the catch site (both are caught by the same universal `.onErrorMap(this::safeFailure)`)
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** May or may not pass through `apim.exchange.failed`, depending on whether the local defect occurs inside or outside the `postEnvelope()`-wrapped Mono chain; `http.api.error` ERROR, no stack trace
- **Idempotency replay:** Yes
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.LOCAL_SYSTEM`
- **Public HTTP status:** 500
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `SYSTEM_UNEXPECTED`
- **Citation:** `ApimForgotPasswordCompletionAdapter.java:74-81` (same code path as the transport sub-cause; see section 8 for the open question about distinguishing these two causes)

### Forgot Password -- ApimForgotPasswordDetailsLookupAdapter (TRPSearchMember)

- **Condition:** missing envelope
- **Detection site:** `ForgotPasswordTrpSearchMemberMapper.java:46`; `ApimForgotPasswordDetailsLookupAdapter.java:51-52` (reference-confirmed in an earlier pass, not re-dumped this round)
- **Handling:** `throw new ForgotPasswordDetailsDownstreamException(Reason.UNEXPECTED_ERROR)`. **Resolved this pass:** the real consumer is `ForgotPasswordUnderLockDetailsVerifier.verify()` (`ForgotPasswordUnderLockDetailsVerifier.java:34-59`), which wraps the call to the `ForgotPasswordDetailsLookupPort` (implemented by this adapter). Any exception raised by the port, including this one, is observed by `.doOnError(cause -> logFailure(requestId, cause))` (line 55, logging `event=forgot-password.details-otp.failed ... exceptionType={}` at ERROR, exception CLASS NAME only, no message or stack trace -- line 61-66) and then swallowed by `.onErrorReturn(ForgotPasswordUnderLockDetailsVerification.of(SYSTEM_FAILED))` (line 56-58). `ForgotPasswordDetailsOtpProtectedOperation` was confirmed NOT to reference this exception type directly -- it is this Verifier, one layer below, that actually catches it.
- **Message policy:** N/A -- swallowed before any `ExceptionMapping`
- **Ordered log lines:** `event=forgot-password.details-otp.failed` ERROR, exception class name only, no stack trace (`ForgotPasswordUnderLockDetailsVerifier.java:61-66`)
- **Idempotency replay:** Yes -- Forgot Password details/OTP is idempotency-protected
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** **UNVERIFIED, narrower than before:** this swallows to `ForgotPasswordDetailsStatus.SYSTEM_FAILED`, a result value, not an HTTP exception. The further mapping from `SYSTEM_FAILED` to a public HTTP status/`ApiStatus` happens at least one more layer up (likely inside `ForgotPasswordDetailsOtpProtectedOperation`'s own result handling or a web mapper), which was not read this pass.
- **Public ApiStatus:** n/a (see above)
- **Public error code/type:** n/a (see above)
- **Citation:** `ForgotPasswordUnderLockDetailsVerifier.java:1-67` (full source now confirmed); `ApimForgotPasswordDetailsLookupAdapter.java:51-52`

### Username Recovery -- TrpSearchMemberAdapter (TRPSearchMember)

- **Condition:** `TrpSearchMemberApimDownstreamException` (unrecognized non-blank err-message)
- **Detection site:** `usernamerecovery/mapper/TrpSearchMemberApimResultMapper.java:47-50`
- **Handling:** `.onErrorResume(TrpSearchMemberApimDownstreamException.class, e -> Mono.just(unknownOutcome()))` (`TrpSearchMemberAdapter.java:54-55`). This check runs inside `.map(this::toResult)` (`TrpSearchMemberAdapter.java:53`), AFTER `executor.postEnvelope(request)` already resolved successfully (onNext) -- this is not a genuine `Mono` `onError` signal from the executor's own perspective.
- **Message policy:** N/A -- swallowed; this exception type is absent from `ExceptionMappingRegistryConfig`
- **Ordered log lines:** `apim.business.rejected` WARN from the envelope-content classifier (non-empty err-message -> business-rejected), which runs on the onNext signal before `.map(this::toResult)` throws this exception; NOT `apim.exchange.failed` (corrected -- that event only applies to genuine Mono onError signals, not post-onNext content-driven exceptions)
- **Idempotency replay:** Yes -- Username Recovery request
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** n/a -- swallowed to success-shaped `unknownOutcome()`
- **Public ApiStatus:** n/a
- **Public error code/type:** n/a
- **Citation:** `usernamerecovery/mapper/TrpSearchMemberApimResultMapper.java:39-56`; `TrpSearchMemberAdapter.java:48-56`

### Username Recovery -- TrpSearchMemberAdapter (TRPSearchMember)

- **Condition:** any `ApimException` (facade-raised; this adapter does not call `ApimResponseValidator`)
- **Detection site:** generic catch, `TrpSearchMemberAdapter.java:56`
- **Handling:** `.onErrorResume(ApimException.class, e -> Mono.just(unavailable()))`. This filter is specific to `ApimException.class` -- it does NOT catch raw `WebClientRequestException`/`TimeoutException` that propagate unconverted after retries are exhausted (see `TRANSPORT_SPLIT_NOTE`-equivalent reasoning), nor crypto exceptions (`ApimCryptoException`/`ApimResponseMappingException`, which are not `ApimException` subtypes). Both of those would propagate past this adapter entirely.
- **Message policy:** N/A -- swallowed
- **Ordered log lines:** `ApimWebClientFacade.handleErrorBody`'s `log.error` (`ApimWebClientFacade.java:409`, gated by `statusCode.isError()` line 386, i.e. an actual 4xx/5xx HTTP response from APIM) fires for the underlying HTTP-error response; `apim.exchange.failed` ERROR also fires via the executor's onError branch before the swallow
- **Idempotency replay:** Yes
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** n/a -- swallowed to success-shaped `unavailable()` for the `ApimException` case. Note: if APIM itself returned a 4xx (not 5xx) status, `statusCode.isError()` (which covers both 4xx and 5xx) still routes through this same `handleErrorBody`/`ApimException` path and is swallowed the same way here -- section 3 has no distinct rule for a 4xx-vs-5xx split, listed as an open question in section 8.
- **Public ApiStatus:** n/a
- **Public error code/type:** n/a
- **Citation:** `TrpSearchMemberAdapter.java:48-56`; `ApimWebClientFacade.java:380-416`

### Username Recovery -- TrpSearchMemberAdapter (TRPSearchMember)

- **Condition:** crypto failure (`ApimCryptoException`/`ApimResponseMappingException`) -- NOT caught by either `onErrorResume`
- **Detection site:** `DefaultApimExchangeExecutor.postPlain`/`postEncrypted`'s decryption step, same as the Forgot Password crypto row
- **Handling:** Neither `.onErrorResume(TrpSearchMemberApimDownstreamException.class, ...)` nor `.onErrorResume(ApimException.class, ...)` matches a raw `ApimCryptoException`/`ApimResponseMappingException` (both extend `RuntimeException` directly, not `ApimException`). The error propagates past `TrpSearchMemberAdapter.search()` entirely, reaching the use-case layer and ultimately `CatchAllExceptionMapping` (neither crypto exception type is registered in `ExceptionMappingRegistryConfig`).
- **Message policy:** N/A -- resolves via the mandatory catch-all
- **Ordered log lines:** `apim.exchange.failed` ERROR fires via the executor's onError branch (genuine Mono error, same structural guarantee as the Forgot Password crypto row); `http.api.error` ERROR with stack trace via `CatchAllExceptionMapping`
- **Idempotency replay:** Yes
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- **Public HTTP status:** 500
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"SYSTEM"` / `SYSTEM_UNEXPECTED`
- **Citation:** `TrpSearchMemberAdapter.java:48-56` (absence of a matching catch confirmed by reading the exact two `onErrorResume` type filters); `CatchAllExceptionMapping.java:18-40`

### Login -- TrpMemberLoginAdapter (TRPMemberLogin)

- **Condition:** `IllegalStateException` (APIM exchange structurally incomplete)
- **Detection site:** Confirmed this pass: `SharedApimTrpMemberLoginTransport.post` is a plain `return executor.postEnvelope(exchange);` (`SharedApimTrpMemberLoginTransport.java:44`) with no `IllegalStateException` of its own. The ONLY sources of `IllegalStateException` in this entire call chain are inside `DefaultApimExchangeExecutor` itself: `switchIfEmpty` ("APIM successful exchange was empty", line 97), `validateCommon` ("... metadata was null"/"... profile was missing", lines 149-154), or the verification-key mismatch checks (lines 104-107, 134-137). All of these occur INSIDE `postPlain`/`postEncrypted`'s own chain, which IS wrapped by `postEnvelope()`'s `doOnEach` -- this is a genuine Mono onError signal.
- **Handling:** `.onErrorResume(IllegalStateException.class, ignored -> Mono.just(mapper.failure(UNAVAILABLE)))` (`TrpMemberLoginAdapter.java:37-39`)
- **Message policy:** N/A -- swallowed
- **Ordered log lines:** `apim.exchange.failed` ERROR fires via the executor's onError branch before the swallow (confirmed structural guarantee, since the throw sites are inside the executor's own doOnEach-wrapped chain)
- **Idempotency replay:** Yes -- Login start
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** n/a -- swallowed to `UNAVAILABLE`, handled by `LoginStartWebMapper`
- **Public ApiStatus:** n/a
- **Public error code/type:** n/a
- **Citation:** `TrpMemberLoginAdapter.java:30-40`; `SharedApimTrpMemberLoginTransport.java:27-45`; `DefaultApimExchangeExecutor.java:84-154`

### Login -- TrpMemberLoginAdapter (TRPMemberLogin)

- **Condition:** `ApimException` (facade-raised HTTP-error response) -- NOT caught by this adapter
- **Detection site:** generic
- **Handling:** `TrpMemberLoginAdapter.authenticate` only catches `IllegalStateException.class` (`TrpMemberLoginAdapter.java:37-39`); a genuine `ApimException` from `handleErrorBody` is not caught here and propagates to the use-case layer, where it is mapped via `ApimExceptionMappings.upstream()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `ApimWebClientFacade.handleErrorBody`'s `log.error`; `apim.exchange.failed` ERROR; `http.api.error` ERROR, no stack trace
- **Idempotency replay:** Yes
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** 502 (for a genuine HTTP-error response) -- see section 8 for the 4xx/5xx open question
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `APIM_UPSTREAM_FAILURE`/`APIM_SERVICE_UNAVAILABLE`/`APIM_TIMEOUT` depending on downstream status (`ApimWebClientFacade.mapUpstreamErrorCode`)
- **Citation:** `TrpMemberLoginAdapter.java:30-40`; `ApimWebClientFacade.java:391-416`

### Login -- TrpMemberLoginAdapter (TRPMemberLogin)

- **Condition:** crypto failure (`ApimCryptoException`/`ApimResponseMappingException`) -- NOT caught by this adapter
- **Detection site:** `DefaultApimExchangeExecutor`'s decryption step, same mechanism as the other crypto rows
- **Handling:** Not caught by `TrpMemberLoginAdapter`'s sole `IllegalStateException`-typed `onErrorResume`; propagates to `CatchAllExceptionMapping` (neither crypto exception type is `ApimException` or separately registered)
- **Message policy:** N/A -- resolves via the mandatory catch-all
- **Ordered log lines:** `apim.exchange.failed` ERROR (genuine Mono error); `http.api.error` ERROR with stack trace
- **Idempotency replay:** Yes
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- **Public HTTP status:** 500
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"SYSTEM"` / `SYSTEM_UNEXPECTED`
- **Citation:** `TrpMemberLoginAdapter.java:30-40`; `CatchAllExceptionMapping.java:18-40`

### Login -- TrpGetAccountListAdapter (TRPGetAccountList)

- **Condition:** any error, sub-cause: facade-raised HTTP-error `ApimException`
- **Detection site:** `TrpGetAccountListAdapter.java:29-32`. Confirmed this pass (prior investigation): `SharedApimTrpGetAccountListTransport.post` is also a plain `return executor.postEnvelope(exchange);` (`SharedApimTrpGetAccountListTransport.java:47`).
- **Handling:** `.onErrorReturn(LoginAccountAuthorizationResult.unavailable())` (`TrpGetAccountListAdapter.java:32`) -- this has NO exception-type filter at all; it catches every throwable indiscriminately, unlike Username Recovery's and Login's other adapter, which filter by specific type.
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
- **Detection site:** same unfiltered `onErrorReturn` catch-all; same confirmed executor path as the row above
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

### Login -- TrpGetAccountListAdapter (TRPGetAccountList)

- **Condition:** any error, sub-cause: connection-level failure (`WebClientRequestException`/`TimeoutException`)
- **Detection site:** same unfiltered `onErrorReturn` catch-all
- **Handling:** `.onErrorReturn(LoginAccountAuthorizationResult.unavailable())` -- catches this too, since it has no type filter at all
- **Message policy:** N/A
- **Ordered log lines:** `apim.exchange.failed` ERROR fires before the swallow
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** n/a -- swallowed to `unavailable()`
- **Public ApiStatus:** n/a
- **Public error code/type:** n/a
- **Citation:** `TrpGetAccountListAdapter.java:26-33`; `ApimWebClientFacade.java:212-302` (retry/re-raise path)

### Account Balance -- ApimAccountBalanceSourceAdapter (TRPGetMemberAccBal)

- **Condition:** `err.tpa.account.invalid` -> `ACCOUNT_NOT_FOUND`
- **Detection site:** `ApimAccountBalanceSourceAdapter.java:54-55`
- **Handling:** `AccountBalanceRetrievalException.accountNotFound()`, mapped via `AccountBalanceExceptionMappings.retrievalFailure()` delegating to `AccountBalanceRetrievalResponsePolicy.map()` (`AccountBalanceExceptionMappings.java:40`)
- **Message policy:** `MessagePolicy.NONE` (`AccountBalanceExceptionMappings.java:47`, forced for every reason)
- **Ordered log lines:** `apim.business.rejected` WARN from the envelope-content classifier (non-empty err-message), before this adapter's own code branches on the specific error text; `logAtError = reason == SYSTEM_FAILURE` (`AccountBalanceExceptionMappings.java:49-50`) -- WARN for this reason at the mapping too; `http.api.error`, no stack trace. ApimAccountBalanceSourceAdapter itself is not `@LogExecution`-annotated. If this exception propagates up through `GetAccountBalanceService.execute(...)`, the broad package-wide pointcut (`execution(public * com.bct.ngtpa.apiservice.application.usecase..*Service.execute(..))`, `ExecutionLoggingAspect.java:52-56`) also logs `method.execution.error` at ERROR with the full stack trace (`ExecutionLoggingAspect.java:161-170`) -- its exact method signature (whether it declares `public ... execute(...)`) was not read this pass; the class name alone matches the pointcut's `*Service` suffix requirement (confirmed present under `application/usecase`, `ExecutionLoggingAspect.java:52-56`), which is this codebase's near-universal use-case entry-point convention, but this is not independently file-confirmed for this specific class.
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
- **Ordered log lines:** `apim.business.rejected` WARN from the envelope-content classifier; WARN at the mapping (`reason != SYSTEM_FAILURE`); `http.api.error`, no stack trace. ApimAccountBalanceSourceAdapter itself is not `@LogExecution`-annotated. If this exception propagates up through `GetAccountBalanceService.execute(...)`, the broad package-wide pointcut (`execution(public * com.bct.ngtpa.apiservice.application.usecase..*Service.execute(..))`, `ExecutionLoggingAspect.java:52-56`) also logs `method.execution.error` at ERROR with the full stack trace (`ExecutionLoggingAspect.java:161-170`) -- its exact method signature (whether it declares `public ... execute(...)`) was not read this pass; the class name alone matches the pointcut's `*Service` suffix requirement (confirmed present under `application/usecase`, `ExecutionLoggingAspect.java:52-56`), which is this codebase's near-universal use-case entry-point convention, but this is not independently file-confirmed for this specific class.
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_BUSINESS`
- **Public HTTP status:** 422
- **Public ApiStatus:** `BUSINESS_REJECTED`
- **Public error code/type:** `ApiError.business("err.ab.account.mismatch", null, [], "SERVER")` (`AccountBalanceRetrievalResponsePolicy.java:16,26-31`)
- **Citation:** `AccountBalanceRetrievalResponsePolicy.java:13-24`; `ApimAccountBalanceSourceAdapter.java:56-57`

### Account Balance -- ApimAccountBalanceSourceAdapter (TRPGetMemberAccBal)

- **Condition:** catch-all `systemFailure()`, sub-cause: unrecognized non-blank error
- **Detection site:** `ApimAccountBalanceSourceAdapter.java:58-59` (error branch before the inner try)
- **Handling:** `AccountBalanceRetrievalException.systemFailure()` (public exception, status, body unchanged across every sub-cause of this catch-all)
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `apim.business.rejected` WARN from the classifier (non-empty err-message, even though unrecognized by this feature); `logAtError = true` (`reason == SYSTEM_FAILURE`) -> ERROR at the mapping; `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503 (`AccountBalanceRetrievalResponsePolicy.java:17-22`)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, "SERVER")` (`AccountBalanceRetrievalResponsePolicy.java:21`)
- **Citation:** `AccountBalanceRetrievalResponsePolicy.java:13-24`; `ApimAccountBalanceSourceAdapter.java:58-59`

### Account Balance -- ApimAccountBalanceSourceAdapter (TRPGetMemberAccBal)

- **Condition:** catch-all `systemFailure()`, sub-cause: missing/empty data on an otherwise successful exchange (`RuntimeException` from the inner `requireSingleData(requireSuccessData(...))` try/catch)
- **Detection site:** `ApimAccountBalanceSourceAdapter.java:64-67`
- **Handling:** `AccountBalanceRetrievalException.systemFailure()`
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `apim.contract.invalid` ERROR from the classifier (missing/empty data on success); ERROR at the mapping; `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, "SERVER")`
- **Citation:** `AccountBalanceRetrievalResponsePolicy.java:13-24`; `ApimAccountBalanceSourceAdapter.java:64-67`

### Account Balance -- ApimAccountBalanceSourceAdapter (TRPGetMemberAccBal)

- **Condition:** catch-all `systemFailure()`, sub-cause: crypto/mapping failure (`ApimCryptoException`/`ApimResponseMappingException`) raised inside `executor.postEnvelope`, bypassing the inner try/catch entirely and reaching the outer `.onErrorMap` (confirmed real-world incident: an unknown field in a TRPGetMemberAccBal response caused exactly this path, per the account-summary-500 investigation)
- **Detection site:** `ApimAccountBalanceSourceAdapter.java:69-73` (outer `.onErrorMap`)
- **Handling:** `e instanceof AccountBalanceRetrievalException ? e : AccountBalanceRetrievalException.systemFailure()`. Since a crypto/mapping exception resolves BEFORE any envelope is ever handed to the adapter's own flatMap lambda, it never enters the inner try/catch at all -- it is caught only by this outer onErrorMap.
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `apim.exchange.failed` ERROR from the executor's onError branch (genuine Mono error, not content classification); ERROR at the mapping; `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, "SERVER")`
- **Citation:** `ApimAccountBalanceSourceAdapter.java:69-73`; `ApimPayloadCryptoService.java:158-166`

### Account Balance -- ApimAccountBalanceSourceAdapter (TRPGetMemberAccBal)

- **Condition:** catch-all `systemFailure()`, sub-cause: connection-level failure (`WebClientRequestException`/`TimeoutException`, retries exhausted) reaching the outer `.onErrorMap`
- **Detection site:** `ApimAccountBalanceSourceAdapter.java:69-73` and the outer `try { ... } catch (RuntimeException exception)` wrapper (lines 38, 99-101)
- **Handling:** Same outer `.onErrorMap` / outer try-catch; the raw connection exception is converted to `AccountBalanceRetrievalException.systemFailure()` here, unlike the generic `ApimException` path, which would NOT apply (since this adapter never calls `ApimExceptionMappings` directly)
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `apim.exchange.failed` ERROR; ERROR at the mapping; `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, "SERVER")`
- **Citation:** `ApimAccountBalanceSourceAdapter.java:69-73,99-101`

### Account Gain/Loss -- ApimAccountGainLossSourceAdapter (TRPGetInvestGL)

- **Condition:** `err.tpa.fund.switching.in.progress`
- **Detection site:** `ApimAccountGainLossSourceAdapter.java:63-65`
- **Handling:** `AccountGainLossRetrievalException.fundSwitchingInProgress()`, mapped via `AccountGainLossExceptionMappings.retrievalFailure()` delegating to `AccountGainLossRetrievalResponsePolicy.map()`
- **Message policy:** `MessagePolicy.NONE` (`AccountGainLossExceptionMappings.java:40`)
- **Ordered log lines:** `apim.business.rejected` WARN from the classifier; WARN at the mapping (`reason == SYSTEM_FAILURE` false, `AccountGainLossExceptionMappings.java:42-43`); `http.api.error`, no stack trace. ApimAccountGainLossSourceAdapter itself is not `@LogExecution`-annotated. If this exception propagates up through `GetAccountGainLossService.execute(...)`, the broad package-wide pointcut (`execution(public * com.bct.ngtpa.apiservice.application.usecase..*Service.execute(..))`, `ExecutionLoggingAspect.java:52-56`) also logs `method.execution.error` at ERROR with the full stack trace (`ExecutionLoggingAspect.java:161-170`) -- its exact method signature (whether it declares `public ... execute(...)`) was not read this pass; the class name alone matches the pointcut's `*Service` suffix requirement (confirmed present under `application/usecase`, `ExecutionLoggingAspect.java:52-56`), which is this codebase's near-universal use-case entry-point convention, but this is not independently file-confirmed for this specific class.
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_BUSINESS`
- **Public HTTP status:** 422 (`AccountGainLossRetrievalResponsePolicy.java:16-23`)
- **Public ApiStatus:** `BUSINESS_REJECTED`
- **Public error code/type:** `ApiError.business("err.fund.switching.in.progress", null, [], "SERVER")` (`AccountGainLossRetrievalResponsePolicy.java:21-22`)
- **Citation:** `AccountGainLossRetrievalResponsePolicy.java:13-31`; `ApimAccountGainLossSourceAdapter.java:63-65`

### Account Gain/Loss -- ApimAccountGainLossSourceAdapter (TRPGetInvestGL)

- **Condition:** catch-all `systemFailure()`, sub-cause: unrecognized non-blank error (with `LOG.warn("event=account-gain-loss.downstream-rejected ...")` already logged by the adapter itself, `ApimAccountGainLossSourceAdapter.java:67-70`)
- **Detection site:** `ApimAccountGainLossSourceAdapter.java:66-71`
- **Handling:** `AccountGainLossRetrievalException.systemFailure()`
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `apim.business.rejected` WARN from the classifier (non-empty err-message); adapter's own `LOG.warn` (`event=account-gain-loss.downstream-rejected`, sanitized error code only); ERROR at the mapping (`reason == SYSTEM_FAILURE`); `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, "SERVER")` (`AccountGainLossRetrievalResponsePolicy.java:28`)
- **Citation:** `ApimAccountGainLossSourceAdapter.java:66-71`

### Account Gain/Loss -- ApimAccountGainLossSourceAdapter (TRPGetInvestGL)

- **Condition:** catch-all `systemFailure()`, sub-cause: missing/empty data on success (inner `try { requireSingleData(requireSuccessData(...)) } catch (RuntimeException)`)
- **Detection site:** `ApimAccountGainLossSourceAdapter.java:73-92`
- **Handling:** `AccountGainLossRetrievalException.systemFailure()`
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `apim.contract.invalid` ERROR from the classifier; ERROR at the mapping; `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, "SERVER")`
- **Citation:** `ApimAccountGainLossSourceAdapter.java:73-92`

### Account Gain/Loss -- ApimAccountGainLossSourceAdapter (TRPGetInvestGL)

- **Condition:** catch-all `systemFailure()`, sub-cause: crypto/mapping failure, reaching the outer `.onErrorMap` (same mechanism as Account Balance's confirmed account-summary-500 incident)
- **Detection site:** `ApimAccountGainLossSourceAdapter.java:94-98` (outer `.onErrorMap`)
- **Handling:** `exception instanceof AccountGainLossRetrievalException ? exception : AccountGainLossRetrievalException.systemFailure()`
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `apim.exchange.failed` ERROR (genuine Mono error); ERROR at the mapping; `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, "SERVER")`
- **Citation:** `ApimAccountGainLossSourceAdapter.java:94-98`

### Account Gain/Loss -- ApimAccountGainLossSourceAdapter (TRPGetInvestGL)

- **Condition:** catch-all `systemFailure()`, sub-cause: connection-level failure, reaching the outer try/catch (`ApimAccountGainLossSourceAdapter.java:38,99-101`) or the outer `.onErrorMap`
- **Detection site:** `ApimAccountGainLossSourceAdapter.java:94-101`
- **Handling:** Same outer catch-all paths
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `apim.exchange.failed` ERROR; ERROR at the mapping; `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, "SERVER")`
- **Citation:** `ApimAccountGainLossSourceAdapter.java:94-101`

### Fund Price -- ApimFundPriceSourceAdapter / ApimFundPriceModeSourceAdapter (TRPGetFundPrice / TRPGetFundPriceMode)

- **Condition:** `err.tpa.invalid.date`
- **Detection site:** `ApimFundPriceSourceAdapter.java:163-166` (`ApimFundPriceModeSourceAdapter` has no equivalent -- per its own Javadoc at lines 26-30, every nonblank error maps to the single generic exception, no branch on specific text)
- **Handling:** `FundPriceInvalidDateException.forInvalidDate()`, mapped via `FundPriceExceptionMappings.invalidDate()`
- **Message policy:** `MessagePolicy.RESOLVE` (`FundPriceExceptionMappings.java:40-61`)
- **Ordered log lines:** `apim.business.rejected` WARN from the classifier (non-empty err-message); `LogLevel.WARN` (`FundPriceExceptionMappings.java:57`), no stack trace. ApimFundPriceSourceAdapter is not `@LogExecution`-annotated, and no `*Service`-named class matching the broad `application.usecase..*Service.execute(..)` pointcut was found in its use-case package (the classes there are named `*UseCase`, not `*Service`, confirmed by directory listing). Neither `ExecutionLoggingAspect` pointcut applies to this row.
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_BUSINESS`
- **Public HTTP status:** 400 (`FundPriceExceptionMappings.java:51`)
- **Public ApiStatus:** `VALIDATION_FAILED`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_PRICE_REQUEST_INVALID`
- **Citation:** `FundPriceExceptionMappings.java:40-61`; `ApimFundPriceSourceAdapter.java:163-166`

### Fund Price -- ApimFundPriceSourceAdapter / ApimFundPriceModeSourceAdapter (TRPGetFundPrice / TRPGetFundPriceMode)

- **Condition:** catch-all `systemFailure()`, sub-cause: missing response / unrecognized non-blank error
- **Detection site:** `ApimFundPriceSourceAdapter.java:159-169`; `ApimFundPriceModeSourceAdapter.java:76-83`
- **Handling:** `FundPriceRetrievalException.systemFailure()`
- **Message policy:** `MessagePolicy.RESOLVE` (`FundPriceExceptionMappings.java:18-38`)
- **Ordered log lines:** `apim.contract.invalid` ERROR (missing response) or `apim.business.rejected` WARN (unrecognized non-blank error) from the classifier, depending on which sub-condition fired; `LogLevel.ERROR` at the mapping (`FundPriceExceptionMappings.java:34`), no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503 (`FundPriceExceptionMappings.java:28`)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_PRICE_RETRIEVAL_UNAVAILABLE`
- **Citation:** `FundPriceExceptionMappings.java:18-38`; `ApimFundPriceSourceAdapter.java:159-169`

### Fund Price -- ApimFundPriceSourceAdapter / ApimFundPriceModeSourceAdapter (TRPGetFundPrice / TRPGetFundPriceMode)

- **Condition:** catch-all `systemFailure()`, sub-cause: malformed data items (inner try/catch around content validation/reconciliation)
- **Detection site:** `ApimFundPriceSourceAdapter.java:170-178`; `ApimFundPriceModeSourceAdapter.java:84-89`
- **Handling:** `FundPriceRetrievalException.systemFailure()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.contract.invalid` ERROR from the classifier if the raw structure already looked malformed, or no classifier-level flag if the raw structure looked fine but a deeper field-level validation failed; ERROR at the mapping; `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_PRICE_RETRIEVAL_UNAVAILABLE`
- **Citation:** `ApimFundPriceSourceAdapter.java:170-178`

### Fund Price -- ApimFundPriceSourceAdapter / ApimFundPriceModeSourceAdapter (TRPGetFundPrice / TRPGetFundPriceMode)

- **Condition:** catch-all `systemFailure()`, sub-cause: crypto/mapping failure, reaching the outer `.onErrorMap`
- **Detection site:** `ApimFundPriceSourceAdapter.java:180-185`; `ApimFundPriceModeSourceAdapter.java:91-95`
- **Handling:** `exception instanceof FundPriceRetrievalException || exception instanceof FundPriceInvalidDateException ? exception : FundPriceRetrievalException.systemFailure()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.exchange.failed` ERROR (genuine Mono error); ERROR at the mapping; `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_PRICE_RETRIEVAL_UNAVAILABLE`
- **Citation:** `ApimFundPriceSourceAdapter.java:180-185`

### Fund Price -- ApimFundPriceSourceAdapter / ApimFundPriceModeSourceAdapter (TRPGetFundPrice / TRPGetFundPriceMode)

- **Condition:** catch-all `systemFailure()`, sub-cause: connection-level failure, reaching the outer `.onErrorMap`
- **Detection site:** same outer `.onErrorMap` as the row above
- **Handling:** same conversion to `FundPriceRetrievalException.systemFailure()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.exchange.failed` ERROR; ERROR at the mapping; `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_PRICE_RETRIEVAL_UNAVAILABLE`
- **Citation:** `ApimFundPriceSourceAdapter.java:180-185`

### Fund Comparison -- ApimFundComparisonOptionsSourceAdapter (TRPGetFundCmpOpt)

- **Condition:** catch-all `systemFailure()`, sub-cause: missing response / unrecognized non-blank error
- **Detection site:** `ApimFundComparisonOptionsSourceAdapter.java:77-83`
- **Handling:** `FundComparisonRetrievalException.systemFailure()`
- **Message policy:** `MessagePolicy.RESOLVE` (`FundComparisonExceptionMappings.java:19-40`)
- **Ordered log lines:** `apim.contract.invalid` ERROR or `apim.business.rejected` WARN from the classifier, depending on the sub-condition; `LogLevel.ERROR` at the mapping (`FundComparisonExceptionMappings.java:36`), no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503 (`FundComparisonExceptionMappings.java:30`)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE`
- **Citation:** `FundComparisonExceptionMappings.java:15-41`; `ApimFundComparisonOptionsSourceAdapter.java:77-83`

### Fund Comparison -- ApimFundComparisonOptionsSourceAdapter (TRPGetFundCmpOpt)

- **Condition:** catch-all `systemFailure()`, sub-cause: malformed fund-list data (`normalize`'s own `IllegalStateException` for a null/non-singleton data list or an empty fund-list, `ApimFundComparisonOptionsSourceAdapter.java:99-107`)
- **Detection site:** `ApimFundComparisonOptionsSourceAdapter.java:85-89` (inner try/catch around `normalize(...)`)
- **Handling:** `FundComparisonRetrievalException.systemFailure()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** ERROR at the mapping; `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE`
- **Citation:** `ApimFundComparisonOptionsSourceAdapter.java:85-89,98-115`

### Fund Comparison -- ApimFundComparisonOptionsSourceAdapter (TRPGetFundCmpOpt)

- **Condition:** catch-all `systemFailure()`, sub-cause: crypto/mapping failure, reaching the outer `.onErrorMap`
- **Detection site:** `ApimFundComparisonOptionsSourceAdapter.java:91-95`
- **Handling:** `exception instanceof FundComparisonRetrievalException ? exception : FundComparisonRetrievalException.systemFailure()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.exchange.failed` ERROR; ERROR at the mapping; `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE`
- **Citation:** `ApimFundComparisonOptionsSourceAdapter.java:91-95`

### Fund Comparison -- ApimFundComparisonOptionsSourceAdapter (TRPGetFundCmpOpt)

- **Condition:** catch-all `systemFailure()`, sub-cause: connection-level failure, reaching the outer `.onErrorMap`
- **Detection site:** same outer `.onErrorMap` as the row above
- **Handling:** same conversion
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.exchange.failed` ERROR; ERROR at the mapping; `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE`
- **Citation:** `ApimFundComparisonOptionsSourceAdapter.java:91-95`

### Fund Fact Sheet -- ApimFundFactSheetSourceAdapter (TRPGetFundFactSheet)

- **Condition:** missing response on an otherwise successful exchange
- **Detection site:** `ApimFundFactSheetSourceAdapter.java:94-97`
- **Handling:** `FundFactSheetRetrievalException.systemFailure()`, mapped via `FundFactSheetExceptionMappings.retrievalUnavailable()`
- **Message policy:** `MessagePolicy.NONE` (`ErrorDescriptor#singleWithoutMessage`, `FundFactSheetExceptionMappings.java:28-37`, O-12: message always null for this exception)
- **Ordered log lines:** `apim.contract.invalid` ERROR from the classifier; `LogLevel.ERROR` at the mapping (`FundFactSheetExceptionMappings.java:35`), no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503 (`FundFactSheetExceptionMappings.java:29`)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"SYSTEM"` / `ErrorCodes.FUND_FACT_SHEET_RETRIEVAL_UNAVAILABLE`
- **Citation:** `ApimFundFactSheetSourceAdapter.java:94-97`

### Fund Fact Sheet -- ApimFundFactSheetSourceAdapter (TRPGetFundFactSheet)

- **Condition:** non-empty error message, or null item/list/row (adapter's own `LOG.error`, `EVENT_NULL_ITEM`/`EVENT_NULL_LIST`/`EVENT_NULL_ROW`, `ApimFundFactSheetSourceAdapter.java:108-121`)
- **Detection site:** `ApimFundFactSheetSourceAdapter.java:98-121`
- **Handling:** `FundFactSheetRetrievalException.systemFailure()`
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `apim.business.rejected` WARN (non-empty error) or `apim.contract.invalid` ERROR from the classifier (depending on condition); feature-level `LOG.error` for the null-item/list/row sub-cases specifically (`ApimFundFactSheetSourceAdapter.java:109,114,119`); ERROR at the mapping; `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"SYSTEM"` / `ErrorCodes.FUND_FACT_SHEET_RETRIEVAL_UNAVAILABLE`
- **Citation:** `ApimFundFactSheetSourceAdapter.java:98-121`

### Fund Fact Sheet -- ApimFundFactSheetSourceAdapter (TRPGetFundFactSheet)

- **Condition:** catch-all, sub-cause: crypto/mapping failure, reaching the outer `.onErrorMap`
- **Detection site:** `ApimFundFactSheetSourceAdapter.java:85-89`
- **Handling:** `exception instanceof FundFactSheetRetrievalException ? exception : FundFactSheetRetrievalException.systemFailure()`
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `apim.exchange.failed` ERROR; ERROR at the mapping; `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"SYSTEM"` / `ErrorCodes.FUND_FACT_SHEET_RETRIEVAL_UNAVAILABLE`
- **Citation:** `ApimFundFactSheetSourceAdapter.java:85-89`

### Fund Fact Sheet -- ApimFundFactSheetSourceAdapter (TRPGetFundFactSheet)

- **Condition:** catch-all, sub-cause: connection-level failure, reaching the outer `.onErrorMap`
- **Detection site:** same outer `.onErrorMap` as the row above
- **Handling:** same conversion
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `apim.exchange.failed` ERROR; ERROR at the mapping; `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_TRANSPORT`
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"SYSTEM"` / `ErrorCodes.FUND_FACT_SHEET_RETRIEVAL_UNAVAILABLE`
- **Citation:** `ApimFundFactSheetSourceAdapter.java:85-89`

### Personal Information -- ApimUpdatePersonalInformationAdapter (TRPUpdMemberInfo)

- **Condition:** empty/all-null data items
- **Detection site:** `ApimUpdatePersonalInformationAdapter.java:61-67`
- **Handling:** `throw new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, "...response data is missing.")`, mapped via `ApimExceptionMappings.upstream()`. This runs AFTER `executor.postEnvelope(request)` already resolved onNext.
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** Possible `apim.contract.invalid` ERROR from the classifier if the raw envelope already looked malformed (the all-null-items case is a deeper structural check the adapter performs after `ApimResponseValidator.requireSuccessData` already succeeded, so whether the classifier's simpler content check -- which inspects data presence, not per-item nullness -- also flags this specific case is not confirmed); NOT `apim.exchange.failed`; `http.api.error` ERROR, no stack trace. ApimUpdatePersonalInformationAdapter is confirmed `@LogExecution`-annotated (ExecutionLoggingAspect.java:40-45 pointcut), so a failure here also produces `method.execution.error` at ERROR with the full stack trace (`ExecutionLoggingAspect.java:161-170`).
- **Idempotency replay:** No -- not found under any idempotency package
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 502
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `APIM_RESPONSE_INVALID`
- **Citation:** `ApimUpdatePersonalInformationAdapter.java:58-67`

### Personal Information -- ApimUpdatePersonalInformationAdapter (TRPUpdMemberInfo)

- **Condition:** no item matches the `selected` account
- **Detection site:** `ApimUpdatePersonalInformationAdapter.java:70-75`
- **Handling:** `throw new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, "...does not contain the selected account.")`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** No classifier-level flag expected (the raw response had valid, non-empty data; this is a deeper business-identity check the adapter performs on data the classifier already saw as structurally fine); `http.api.error` ERROR, no stack trace. ApimUpdatePersonalInformationAdapter is confirmed `@LogExecution`-annotated (ExecutionLoggingAspect.java:40-45 pointcut), so a failure here also produces `method.execution.error` at ERROR with the full stack trace (`ExecutionLoggingAspect.java:161-170`).
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 502
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `APIM_RESPONSE_INVALID`
- **Citation:** `ApimUpdatePersonalInformationAdapter.java:70-75`

### Reference Date -- ApimReferenceDateRefreshAdapter (TRPGetWebSysDate)

- **Condition:** missing payload / blank err-message field / missing data
- **Detection site:** `ApimReferenceDateRefreshAdapter.java:49-51`
- **Handling:** `throw invalid(...)` -> `new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, message)`, mapped via `ApimExceptionMappings.upstream()`. These checks run on the already-resolved envelope (`payload == null`, `StringUtils.hasText(getErrMessage())`, `CollectionUtils.isEmpty(getData())`).
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.contract.invalid` ERROR from the classifier for the missing-payload/missing-data sub-cases (matches its malformed rule); `apim.business.rejected` WARN if the sub-case is instead a non-empty, recognized-shaped err-message; NOT `apim.exchange.failed`; `http.api.error` ERROR, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 502
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `APIM_RESPONSE_INVALID`
- **Citation:** `ApimReferenceDateRefreshAdapter.java:47-54`

### Reference Date -- ApimReferenceDateRefreshAdapter (TRPGetWebSysDate)

- **Condition:** unparsable date (`DateTimeParseException` on an otherwise present, well-formed-looking field)
- **Detection site:** `ApimReferenceDateRefreshAdapter.java:55-63`
- **Handling:** `throw new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, "APIM sys-date must use dd/MM/yyyy format.", ex)`. This is a value present in the envelope that fails OUR OWN parsing, not a structurally empty/missing field the classifier's rule targets.
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** No classifier-level flag expected (the field was present and non-blank from the classifier's structural perspective; the parse failure is detected only by this adapter's own `LocalDate.parse` call); `http.api.error` ERROR, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_INVALID_RESPONSE`
- **Public HTTP status:** 502
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `APIM_RESPONSE_INVALID`
- **Citation:** `ApimReferenceDateRefreshAdapter.java:55-63`

### Shared `ApimResponseValidator`-reliant adapters (Contribution Summary, Member Info, Notice Message, Notification Read Status, Reference Data Countries, General Metadata -- exactly these 6; corrected membership, see note below)

- **Condition:** any non-empty `err-message` on an otherwise successful HTTP exchange
- **Detection site:** `ApimResponseValidator.java:36-38` -- raw downstream `err-message` passed verbatim as the exception's internal diagnostic message only; public message resolved separately by code via `MessagePolicy.RESOLVE`. **Membership correction:** `SharedApimTrpGetAccountListTransport` and `SharedApimTrpMemberLoginTransport` do NOT call `ApimResponseValidator` at all (both are confirmed plain `return executor.postEnvelope(exchange);` passthroughs, documented in their own dedicated Login rows above with their own, different public shapes); they were wrongly included in this group's heading in an earlier pass while the citation list already (correctly) excluded them -- removed here for consistency.
- **Handling:** mapped via `ApimExceptionMappings.upstream()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.contract.invalid` ERROR from the classifier (unrecognized non-blank err-message, per section 3's rule that every `ApimResponseValidator` caller is `DOWNSTREAM_CONTRACT`); NOT `apim.exchange.failed` (this is a post-onNext content check, not a genuine Mono error); `http.api.error` ERROR, no stack trace
- **Idempotency replay:** No -- all 6 are read-only or non-mutating queries
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 502
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `APIM_RESPONSE_INVALID`
- **Citation:** `ApimResponseValidator.java:27-45`; 6 confirmed call sites: `ApimContributionSummaryAdapter.java:54`, `ApimMemberInfoAdapter.java:59`, `ApimNoticeMessageAdapter.java:72`, `ApimNotificationReadStatusAdapter.java:67`, `ApimReferenceDataCountriesAdapter.java:50`, `ApimGeneralMetadataSourceAdapter.java:88`

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
- Specific rows -> `FailureOrigin.DOWNSTREAM_BUSINESS`: Registration `err.tpa.member.not.found`, Account Balance `NOT_FOUND` / `MISMATCH`, Account Gain/Loss `err.tpa.fund.switching.in.progress`, Fund Price `err.tpa.invalid.date` -- 5 rows total (Account Balance contributes 2).
- Catch-all `systemFailure()` (Fund Price, Fund Comparison, Fund Fact Sheet, Account Balance, Account Gain/Loss), plus `TrpGetAccountListAdapter`'s `onErrorReturn` and `ApimForgotPasswordCompletionAdapter`'s `safeFailure`: classified by cause, split into one sub-row per cause in section 1; the public exception, status and body stay unchanged across every sub-cause of the same catch-all.

## 4. Approved exceptions to the M2b "no change" rules

- **E1 (log level):** Registration `err.tpa.member.not.found` moves from `FailureOrigin.DOWNSTREAM_CONTRACT` to `FailureOrigin.DOWNSTREAM_BUSINESS` (ERROR -> WARN). Pinned fail-first in the Registration feature commit (sub-task 2b.4..2b.n). No other log level may change in M2b.
- **E2 (public contract):** no `DOWNSTREAM_*` value may reach the frontend (section 6, sub-task 2b.2a).

## 5. Incident ERROR per category (input for M3 3.8 -- describe only, no level change now)

- **DOWNSTREAM_BUSINESS:** proposed incident line is the feature-level log closest to the business decision (Registration's `recordDownstreamRejection`; Account Balance/Account Gain-Loss/Fund Price's WARN-level mapping log, since these are already WARN today).
- **DOWNSTREAM_CONTRACT:** proposed incident line is `ApimExchangeOutcomeLogger`'s `apim.contract.invalid` (`ApimExchangeOutcomeLogger.java:70-72`).
- **DOWNSTREAM_INVALID_RESPONSE:** proposed incident line is `apim.exchange.failed` (`ApimExchangeOutcomeLogger.java:52-65`), confirmed to fire for every row in this category.
- **DOWNSTREAM_TRANSPORT:** two distinct incident lines apply depending on the actual failure: `ApimWebClientFacade.handleErrorBody`'s `log.error` (`ApimWebClientFacade.java:409`) for an actual HTTP-error-status response; for a connection-level failure (`WebClientRequestException`/`TimeoutException`), `handleErrorBody` is never reached at all -- the `apim.exchange.failed` ERROR line from `ApimExchangeOutcomeLogger` (same as `DOWNSTREAM_INVALID_RESPONSE`) is the only executor-layer line available for this sub-cause. M3 3.8 needs to pick one incident line per sub-cause, not a single line for the whole category.
- **LOCAL_SYSTEM:** proposed incident line is `CatchAllExceptionMapping`'s own `http.api.error` line, which already logs ERROR with a full stack trace (`CatchAllExceptionMapping.java:38`) -- the only category where the terminal handler log, not an earlier executor/feature log, is the richest available line.

## 6. Public DOWNSTREAM exposure (decision by Nick: no DOWNSTREAM_* in any public field)

Three distinct code paths place a `DOWNSTREAM`-containing token into a public field:

**(a) `ApiError.downstreamSystem(...)` factory** (`ApiError.java:54-56`). Zero production callers confirmed via unfiltered grep; its only two references anywhere are test usages.

**(b) `WorkflowErrorType.DOWNSTREAM_SYSTEM` enum constant** (`WorkflowErrorType.java:10`), with four confirmed call sites: `RegistrationStartWebMapper.java:207` (`MESSAGE_DELIVERY_FAILED`, Registration Start endpoint), `RegistrationOtpResendWebMapper.java:142` (same status, Registration OTP Resend endpoint), `RegistrationOtpVerifyWebMapper.java:144` (`TRUST_LOOKUP_FAILED`, Registration OTP Verify endpoint), `CheckRegistrationUsernameService.java:306` (`DOWNSTREAM_FAILED`, Registration username-check endpoint). **Now confirmed (was previously an open question):** `WorkflowMutationResponseMapper.toApiError` builds `new ApiError(descriptor.type().name(), ...)` (`WorkflowMutationResponseMapper.java:51-58`) -- calling `.name()` on a `WorkflowErrorType` enum constant returns its literal constant name, so `WorkflowErrorType.DOWNSTREAM_SYSTEM` renders **verbatim as the public JSON `type` field value `"DOWNSTREAM_SYSTEM"`** at all four endpoints above. `RegistrationWorkflowMutationResponseMapper` only wraps the payload shape, not `errors`, so this applies unchanged to Registration's own response rendering too.

**(c) The literal string `"DOWNSTREAM_SYSTEM"` passed directly into `ErrorDescriptor.single(...)`, bypassing `ApiError` and `WorkflowErrorType` both** -- the main exposure site by traffic volume: `ApimExceptionMappings.java:61` (`upstream()`, the generic `ApimException` mapping), reached by every generic-`ApimException` row in this table: Forgot Password username-lookup, Personal Information (both conditions), both Reference Date rows, the 6-adapter shared validator group, and Login's/Username Recovery's facade-raised-`ApimException` rows. Also `RegistrationExceptionMappings.java:31` (`lookupDownstream()`), affecting Registration lookup's `EMPTY_DATA`/`INVALID_ACCOUNT_DATA`/`UNEXPECTED_ERROR` row.

**Confirmed non-exposure:** `ApiStatus.DOWNSTREAM_REJECTED`/`ApiStatus.DOWNSTREAM_ERROR` are normalized away by `WorkflowPublicApiStatusNormalizer` before rendering.

**Existing guard test, re-confirmed by direct full-source read:** `PublicDownstreamStatusIsolationGuardTest` (`src/test/java/.../architecture/PublicDownstreamStatusIsolationGuardTest.java`) has exactly 3 tests -- two confirm the `WorkflowPublicApiStatusNormalizer` mappings directly, and a third walks every `.java` file under `adapter/in/web` and fails if any file's raw source text contains the literal strings `"ApiStatus.DOWNSTREAM_ERROR"` or `"ApiStatus.DOWNSTREAM_REJECTED"`. It does **not** scan for `"DOWNSTREAM_SYSTEM"`, and it does not render or inspect any actual JSON output -- it is a pure source-text guard, confirmed not a behavior guard.

### Replacement

The existing non-downstream system type is `ApiError.type = "SYSTEM"` (`ApiError.system(...)`, `ApiError.java:45-47`), confirmed verbatim: `return new ApiError("SYSTEM", code, message, List.of(), DEFAULT_SEVERITY, source);`. This is the exact replacement for path (c)'s literal `"DOWNSTREAM_SYSTEM"` usages. HTTP status, `ApiStatus`, code and message policy stay unchanged; only the string literal is replaced. Path (b)'s `WorkflowErrorType.DOWNSTREAM_SYSTEM` needs its own replacement enum constant, since `WorkflowErrorType` is a separate enum from `ApiError.type`.

### Sub-task 2b.2a (runs after 2b.2, before 2b.3)

- Fail-first: change the characterization rows that expect a `DOWNSTREAM_SYSTEM` value (both path (b) and path (c) occurrences, including every endpoint listed above) to their replacement.
- Replace every public `DOWNSTREAM_*` value; delete `ApiError.downstreamSystem()` (confirmed production-unused) and resolve `WorkflowErrorType.DOWNSTREAM_SYSTEM`'s four call sites.
- Extend `PublicDownstreamStatusIsolationGuardTest`: keep the existing source check; add a behaviour check that renders every exception mapping and every workflow error path and asserts no rendered JSON contains `"DOWNSTREAM"`.
- Commit: `fix(exception-handling): stop exposing downstream error types to clients`

**2b.2a waits for Nick to confirm FE does not depend on any `DOWNSTREAM_*` value.**

## 7. Sub-task plan

- **2b.2:** characterization pack (public status, body, message, log event, level for every inventory row above) on unchanged code. Must also resolve the document's remaining genuinely UNVERIFIED items: Registration Lookup's actual public endpoint (if any) for the `NOT_ELIGIBLE` condition; `ForgotPasswordDetailsOtpProtectedOperation`'s mapping from `ForgotPasswordDetailsStatus.SYSTEM_FAILED` to a public HTTP shape; and the exact signatures of `GetAccountBalanceService`/`GetAccountGainLossService` to confirm the broad `ExecutionLoggingAspect` pointcut genuinely applies.
- **2b.2a:** as described in section 6, after Nick confirms FE independence from `DOWNSTREAM_*` values.
- **2b.3:** shared pure classifier (names chosen in this document's section 3 rules), branch coverage >= 95%.
- **2b.4..2b.n:** one commit per feature, attaching the classification; feature code and tables unchanged; gate after each.

## 8. Open questions (listed, not decided)

- Public status 502 vs 500 for `ApimException` (`ApimForgotPasswordCompletionAdapter.safeFailure` always uses 500; most other `ApimException` construction sites use 502) -- whether this divergence is intentional or should be unified.
- A 4xx-status downstream response and a 5xx-status downstream response both currently reach the same `ApimWebClientFacade.handleErrorBody` path and resolve to the identical public shape (502, `DOWNSTREAM_SYSTEM`); section 3 has no rule distinguishing them, and they are currently both folded into `DOWNSTREAM_TRANSPORT`. Whether a 4xx downstream status should instead classify as `DOWNSTREAM_BUSINESS` or `DOWNSTREAM_CONTRACT` (since a 4xx is arguably closer to a rejection than an infrastructure/transport fault) is open.
- Whether the swallow sites in section 1 (Forgot Password crypto-failure, Username Recovery x2, Login x2 across its two adapters' additional unfiltered-catch rows) should publish a `FailureEvent` (M3 3.7).
- `ApimForgotPasswordCompletionAdapter.safeFailure`'s transport-vs-local-defect sub-causes are not structurally distinguished by the current code at the single shared catch site.


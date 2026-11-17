# 07 - APIM Error Classification Design

## 1. Inventory table (48 rows)

### Registration -- ApimRegistrationLookupAdapter (TRPSearchMember)

- **Condition:** `err.tpa.member.not.found` -> `NOT_ELIGIBLE`
- **Detection site:** `registration/mapper/TrpSearchMemberApimResultMapper.toResult`, lines 22-24 (confirmed: this is the `adapter/out/apim/registration/mapper` package, a separate file from the `usernamerecovery/mapper` package's same-named class)
- **Handling:** Returned as normal `RegistrationLookupResult(NOT_ELIGIBLE, List.of(), null)`; `ApimRegistrationLookupAdapter.withFailureLogging`'s `doOnEach` (lines 94-128) detects `status()==NOT_ELIGIBLE` on the onNext signal and calls `recordDownstreamRejection` (line 101), publishing a `FailureEvent` (lines 144-159)
- **Message policy:** N/A -- successful application result, never an `ExceptionMapping`
- **Ordered log lines:** (1) `apim.business.rejected` WARN (executor classification of a non-empty err-message; seen in DEV on 2026-10-28 for a TRPSearchMember member-not-found), firing on the already-resolved onNext envelope before this mapper runs; (2) `FailureEvent` `event.registration.lookup.downstream_rejection` published, origin `DOWNSTREAM_CONTRACT` (line 121), logged ERROR today by `StructuredFailureEventLogger` -- E1 moves this to WARN. (3) `ApimRegistrationLookupAdapter` is `@LogExecution`-annotated, but this condition is returned as a normal result, so `method.execution.error` (`ExecutionLoggingAspect.java:161-170`) is not logged for it.
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
- **Ordered log lines:** (1) `apim.contract.invalid` ERROR from the classifier (missing err-message for `EMPTY_DATA`); for `UNEXPECTED_ERROR` (unrecognized non-blank err-message), `apim.business.rejected` WARN instead (the classifier only distinguishes blank-vs-non-blank err-message, not recognized-vs-unrecognized); (2) `FailureEvent`, origin `DOWNSTREAM_CONTRACT`, ERROR (`ApimRegistrationLookupAdapter.java:121`); (3) `http.api.error` ERROR, no stack trace (`RegistrationExceptionMappings.java:35`, final arg `false`). ApimRegistrationLookupAdapter is confirmed `@LogExecution`-annotated (unfiltered grep, 10-class list). A failure here also produces `method.execution.error` ERROR with full stack trace (`ExecutionLoggingAspect.java:161-170`). **Ordered log lines (captcha ENABLED path - NEVER reaches ApiExceptionHandler, confirmed by real passing test evidence):** (1) same classifier line as above (apim.contract.invalid or apim.business.rejected, per reason); (2) FailureEvent, origin DOWNSTREAM_CONTRACT, ERROR - STILL fires, since ApimRegistrationLookupAdapter's `doOnEach(...)` sees the onError signal regardless of what wraps around it further up the chain; (3) NO http.api.error line (the exception is caught and discarded by onErrorResume before the handler layer); instead, `RegistrationStartWebMapper.logFatalIfNeeded` fires its own plain SLF4J ERROR line on its own logger: `event=registration.start.failure severity=FATAL status=SYSTEM_FAILED errorCodes=[err.workflow.system.unavailable]`.
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
- **Detection site:** `ApimForgotPasswordUsernameLookupAdapter.toTransportEnvelope` detects a missing response envelope.
- **Handling:** R1 sets `ApimIncidentOwnership.TERMINAL_CALLER`. The adapter classifies the exception and returns `SYSTEM_FAILED` with its `FailureOrigin`; `ForgotPasswordStartProtectedOperation` carries that origin into the acquired application flow, which publishes the terminal `FailureEvent` without attaching the original Throwable.
- **Message policy:** The technical result is rendered by the Forgot Password start mapper; no generic `ExceptionMapping` owns this result path.
- **Ordered log lines:** The APIM observer and adapter/method diagnostic observations are WARN-only for this invocation. The acquired application boundary publishes the single terminal incident ERROR through `FailureEventPort`; its event is cause-free and contains no raw downstream message.
- **Idempotency replay:** Yes -- Forgot Password start
- **Failure origin:** `DOWNSTREAM_CONTRACT` for a missing envelope; the adapter preserves the classifier's technical origin in the result.
- **Public HTTP status:** 503, `ApiStatus.SYSTEM_ERROR`; the existing start characterization preserves `err.system.unavailable`. R1 changes incident ownership/diagnostics, not the public response.
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Expected business behavior:** Recognized `err.tpa.cust.id.not.registered` and a valid `isExist=false` result remain `NOT_FOUND`, not technical incidents; their existing response behavior is unchanged.
- **Idempotency/replay:** Start is idempotency-protected. Only the acquired execution publishes an incident; completed replay returns its stored response without republishing the original incident.
- **Citation:** `ApimForgotPasswordUsernameLookupAdapter.java`; `ForgotPasswordStartProtectedOperation.java`; `IdempotentStartForgotPasswordService.java`; `ApimForgotPasswordUsernameLookupAdapterBatch2aCharacterizationTest`; `ForgotPasswordBatch2aStartCharacterizationTest`.

### Forgot Password -- ApimForgotPasswordUsernameLookupAdapter (TRPChkCustId)

- **Condition:** no err-message AND no data, or data present but every item malformed
- **Detection site:** `ApimForgotPasswordUsernameLookupAdapter.java:86-100`
- **Handling:** The adapter retains malformed-response detection, classifies the failure, and returns `SYSTEM_FAILED` with its technical origin to the R1 terminal caller.
- **Message policy:** Rendered through the existing Forgot Password start system-error mapping.
- **Ordered log lines:** Lower APIM observations are WARN-only; the acquired application boundary publishes the one cause-free terminal `FailureEvent` at ERROR. No raw downstream diagnostic is included.
- **Idempotency/replay:** Start is idempotency-protected; a completed replay does not repeat the lookup or republish the original incident.
- **Failure origin:** The adapter retains the classified technical origin in `SYSTEM_FAILED`; the characterization tests assert `DOWNSTREAM_CONTRACT` for missing-envelope and malformed-envelope exceptions.
- **Public behavior:** Existing characterization preserves HTTP 503, `ApiStatus.SYSTEM_ERROR`, and `err.system.unavailable`; no public contract change is part of R1.
- **Citation:** `ApimForgotPasswordUsernameLookupAdapterBatch2aCharacterizationTest`; `ApimForgotPasswordUsernameLookupAdapterTest`; `ForgotPasswordBatch2aStartCharacterizationTest`.

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)

- **Condition:** missing response envelope
- **Detection site:** `ApimForgotPasswordCompletionAdapter.java:64-68` (`toEnvelope`)
- **Handling and ownership:** The adapter converts the missing response into a cause-free `ForgotPasswordCompletionTechnicalFailure` with `DOWNSTREAM_CONTRACT`; the invocation selects `TERMINAL_CALLER`. The protected operation records a completion incident, and the acquired idempotent application service publishes the terminal `FailureEvent` without the original Throwable.
- **Message policy:** `classifiedFailure` converts the intermediate adapter exception to a cause-free technical result before it leaves the adapter; the existing completion system-error mapping remains public.
- **Ordered log lines:** APIM envelope observation and `@LogExecution` observation are WARN-only for this terminal-caller invocation and carry no Throwable. The application boundary publishes the single terminal incident ERROR. `ApimForgotPasswordCompletionAdapterLogExecutionAspectCharacterizationTest.missingEnvelopeLogsStartThenWarningWithoutThrowable` characterizes the adapter observer; the connected application test characterizes the terminal event and public response separately.
- **Idempotency replay:** Yes -- Forgot Password complete
- **Failure origin:** `DOWNSTREAM_CONTRACT`; original Throwable/message is not attached to the terminal failure event.
- **Public/replay behavior:** `ForgotPasswordCompleteIncidentOwnershipConnectedTest` characterizes the acquired technical-failure response as HTTP 503 / `SYSTEM_ERROR` / `err.system.unavailable`, and separately verifies that an unknown-outcome replay does not publish another incident or ownership marker. The missing-envelope adapter is not composed through protected execution and replay in one test; retain this layered-evidence limit (independent review finding F2).
- **Citation:** `ApimForgotPasswordCompletionAdapterBatch2aCharacterizationTest`; `ApimForgotPasswordCompletionAdapterLogExecutionAspectCharacterizationTest`; `ForgotPasswordCompleteIncidentOwnershipConnectedTest`; `ForgotPasswordCompleteProtectedOperation.java`; `IdempotentCompleteForgotPasswordService.java`.

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)
- **Condition:** present response, err-message empty, data empty
- **Detection site:** ApimForgotPasswordCompletionAdapter.java (toEnvelope passes the envelope through unchanged since its own response is non-null; resultMapper.toResult(...) maps empty data to a TECHNICAL_FAILURE result rather than throwing)
- **Handling:** returned as ForgotPasswordCompletionResult(status=TECHNICAL_FAILURE, ...); no exception is thrown
- **Message policy:** N/A -- returned as a normal result value
- **Ordered log lines:** The APIM observer is diagnostic WARN for `TERMINAL_CALLER`; the adapter returns normally, so the aspect records start/success at INFO. The acquired application boundary treats `TECHNICAL_FAILURE` as a terminal incident and publishes one cause-free `FailureEvent`.
- **Idempotency replay:** Yes -- Forgot Password complete
- **Failure origin:** The result mapper marks the empty-data response as `TECHNICAL_FAILURE` with `DOWNSTREAM_CONTRACT`; the public/incident routing is performed by the protected operation and application service.
- **Public/replay behavior:** The existing technical-failure and replay characterization described above remains applicable at the service/controller layer; it does not prove a single end-to-end envelope-to-replay path for this specific adapter response.
- **Citation:** `ApimForgotPasswordCompletionAdapterBatch2aCharacterizationTest.complete_emptyButPresentResponse_returnsTechnicalFailureResult`; `ForgotPasswordCompleteIncidentOwnershipConnectedTest`.

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)

- **Condition:** crypto failure (`ApimCryptoException`/`ApimResponseMappingException`)
- **Detection site:** `ApimPayloadCryptoService.java:164` (`throw new ApimCryptoException("Failed to decrypt APIM response payload.", ex)`); classifier `ApimCryptoFailureClassifier.java:15-16`; confirmed sole caller `ApimForgotPasswordCompletionAdapter.java:57`
- **Handling:** The adapter's `classifiedFailure` maps crypto/response-mapping failures to a cause-free `ForgotPasswordCompletionTechnicalFailure` with `DOWNSTREAM_INVALID_RESPONSE`; it no longer converts them to an adapter-level `unknownOutcome()` result. The protected operation owns incident classification/publication.
- **Message policy:** N/A -- swallowed
- **Ordered log lines:** The APIM observer and `@LogExecution` observer are WARN-only without a Throwable for `TERMINAL_CALLER`; the application boundary emits the single cause-free terminal incident ERROR. `ApimForgotPasswordCompletionAdapterLogExecutionAspectCharacterizationTest.cryptoFailureLogsWarningWithoutThrowable` covers the adapter-level observation.
- **Idempotency replay:** Yes
- **Failure origin:** `DOWNSTREAM_INVALID_RESPONSE`.
- **Public/replay behavior:** Existing technical-failure/public-response and unknown-outcome replay tests characterize the service/controller behavior separately; the crypto adapter path is not composed with those assertions in one end-to-end replay test (F2).
- **Citation:** `ApimForgotPasswordCompletionAdapter.java`; `ApimForgotPasswordCompletionAdapterLogExecutionAspectCharacterizationTest`; `ForgotPasswordCompleteIncidentOwnershipConnectedTest`.

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)

- **Condition:** HTTP non-2xx response
- **Detection site:** `ApimWebClientFacade` / the shared executor returns a technical error to the completion adapter.
- **Handling:** `classifiedFailure` preserves the classified technical origin in a cause-free `ForgotPasswordCompletionTechnicalFailure`; R6 assigns `TERMINAL_CALLER`. The terminal application boundary publishes the incident.
- **Message policy:** Existing completion system-error response; arbitrary downstream text is not public.
- **Ordered log lines:** Lower APIM and method-execution observations are WARN-only without Throwable details; one cause-free terminal `FailureEvent` is published at ERROR by the application boundary.
- **Idempotency replay:** Yes
- **Failure origin:** Normally `DOWNSTREAM_TRANSPORT` for an APIM HTTP-error response, as determined by `ApimFailureClassifier`.
- **Public/replay behavior:** The connected technical-failure response and unknown-outcome replay are characterized separately as described above; this exact facade path is not composed through the adapter and replay in one test (F2).
- **Citation:** `ApimForgotPasswordCompletionAdapter.java`; `ApimFailureClassifier`; `ForgotPasswordCompleteIncidentOwnershipConnectedTest`.

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)

- **Condition:** connection/timeout failure during the `NON_RETRYABLE` completion exchange
- **Detection site:** APIM transport/executor failure propagated to `ApimForgotPasswordCompletionAdapter.complete`.
- **Handling:** `classifiedFailure` maps it to a cause-free technical failure, normally `DOWNSTREAM_TRANSPORT`; the protected operation and application boundary retain terminal ownership.
- **Message policy:** Existing completion system-error response; no Throwable text is exposed.
- **Ordered log lines:** Lower APIM and method-execution observations are WARN-only; one cause-free terminal `FailureEvent` is emitted at ERROR.
- **Idempotency replay:** Yes
- **Failure origin:** `DOWNSTREAM_TRANSPORT` for classified connection/timeout failures.
- **Public/replay behavior:** Existing system-error and unknown-outcome replay characterization remains unchanged; the single real-transport-to-public-replay composition is an acknowledged test-evidence gap (F2).
- **Retry/resubmission:** `TRPResetPassword` remains `NON_RETRYABLE` / `NEVER`; no automatic APIM retry, reset resubmission, or recovery resubmission is authorized. This invocation-level policy does not close O-19's different-key/crash/persistence uncertainty.
- **Citation:** `ApimForgotPasswordCompletionAdapter`; `ApimForgotPasswordCompletionNoResubmissionTransportTest`; `ForgotPasswordCompletionExecutorArchitectureTest`; `ForgotPasswordCompleteIncidentOwnershipConnectedTest`.

### Forgot Password -- ApimForgotPasswordDetailsLookupAdapter (TRPSearchMember)

- **Condition:** missing envelope, malformed response, or other technical lookup failure (M3c R3)
- **Detection site:** `ApimForgotPasswordDetailsLookupAdapter.verify` and `ForgotPasswordTrpSearchMemberMapper`; the adapter classifies the operation failure, and `ForgotPasswordUnderLockDetailsVerifier` distinguishes technical origins from expected business outcomes.
- **Handling and ownership:** The invocation uses `TERMINAL_CALLER`. The verifier carries a technical `FailureOrigin` into the protected operation; `IdempotentForgotPasswordDetailsOtpService` publishes one cause-free terminal `FailureEvent` for the acquired execution. APIM lower observations are WARN-only; no raw downstream message or Throwable is attached to the terminal event.
- **Expected business behavior:** Recognized member-not-found and empty data remain `NOT_FOUND`/business rejection, not technical incidents. Adapter/mapper tests preserve empty-data as `NOT_FOUND`; the controller test preserves HTTP 422 / `BUSINESS_REJECTED` / `err.forgot-password.member.notFound` for member mismatch. Empty data is an expected result, not a technical exception.
- **Message policy:** Technical results use the existing Details/OTP system-error response; expected identity mismatch remains the existing business response.
- **Ordered log lines:** A technical incident produces the lower APIM WARN observation and one application-owned terminal ERROR. Expected business outcomes produce no technical incident ERROR; any recognized APIM rejection observation remains WARN.
- **Idempotency replay:** Yes -- Forgot Password details/OTP is idempotency-protected
- **Failure origin:** `DOWNSTREAM_CONTRACT`, `DOWNSTREAM_INVALID_RESPONSE`, `DOWNSTREAM_TRANSPORT`, or `LOCAL_SYSTEM` according to the classified failure. Expected `DOWNSTREAM_BUSINESS` remains unowned.
- **Public behavior/replay:** Existing controller and connected tests pin the system-error and business-rejection responses; replay remains owned by the shared idempotency service and does not republish a completed incident.
- **Citation:** `ApimForgotPasswordDetailsLookupAdapter.java`; `ForgotPasswordUnderLockDetailsVerifier.java`; `ForgotPasswordDetailsOtpProtectedOperation.java`; `IdempotentForgotPasswordDetailsOtpService.java`; `ApimForgotPasswordDetailsLookupAdapterTest`; `ForgotPasswordDetailsOtpIncidentOwnershipConnectedTest`; `AuthForgotPasswordDetailsControllerTest`.

**Completion-wide preservation:** The APIM request remains `NON_RETRYABLE` / `NEVER` with `TERMINAL_CALLER`. Recognized reset business rejections (including password-invalid/reused outcomes) retain feature-owned result mapping and do not become technical incidents. The adapter-level missing-envelope/empty-data cases and the connected public/replay cases are characterized in separate tests; no single test composes the actual adapter failure through protected execution, response rendering, and completed replay (review finding F2). O-19 remains separate: `NEVER` prevents resubmission within an invocation but does not close different-key, crash, or persistence-failure uncertainty.

### M3c implementation references for R2, R4, R5, and R7

The existing R1, R3, and R6 entries above retain their detailed classification,
ownership, and public/replay descriptions. The remaining M3c implementation
references are recorded here to complete the R1-R7 inventory without
re-specifying those approved behaviors. These commits are included in the M3c
merge history; the connected test sources are cited as evidence locations, not
as claims that a final full-suite or startup gate passed.

| Requirement | Scope | Implementation reference | Connected-test source |
|---|---|---|---|
| R2 | Forgot Password identity check | `f99cc00a1` | `src/test/java/com/bct/ngtpa/apiservice/integration/forgotpassword/ForgotPasswordIdentityIncidentOwnershipConnectedTest.java` |
| R4 | Forgot Password OTP resend | `51f97fd68` | `src/test/java/com/bct/ngtpa/apiservice/integration/forgotpassword/ForgotPasswordOtpResendIncidentOwnershipConnectedTest.java` |
| R5 | Forgot Password OTP verify | `634d6e69e` | `src/test/java/com/bct/ngtpa/apiservice/integration/forgotpassword/ForgotPasswordOtpVerifyIncidentOwnershipConnectedTest.java` |
| R7 | Forgot Password cancellation | `b3b8a8de4` | `src/test/java/com/bct/ngtpa/apiservice/integration/forgotpassword/ForgotPasswordCancelIncidentOwnershipConnectedTest.java` |

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
- **Public HTTP status:** 502. `mapper.failure(UNAVAILABLE)` -> `LoginStartWebMapper.toMutationResponse` `case UNAVAILABLE ->` (lines 61-63): `failure(ApiStatus.SYSTEM_ERROR, LoginErrorKey.CREDENTIALS_UNAVAILABLE.value(), "DOWNSTREAM")`. LoginHttpResponses.statusForCode has an EXPLICIT branch for CREDENTIALS_UNAVAILABLE (CONFIRMED, LoginHttpResponses.java:43: `if (code.equals(LoginErrorKey.CREDENTIALS_UNAVAILABLE.value())) return HttpStatus.BAD_GATEWAY;`), checked before the generic ApiStatus.SYSTEM_ERROR -> HttpStatus.BAD_GATEWAY fallback (line 22) ever runs - the FINAL public status (502) is unchanged, but it is reached via this explicit, code-specific branch, not the generic fallback.
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
- **Public error code/type:** type `FORM`, source `BFF`, code `LoginErrorKey.ACCOUNT_LIST_UNAVAILABLE.value()` (`LoginOtpVerifyWebMapper.java:46-49,69-70`). Pinned by `LoginOtpVerifyWebMapperTest.accountListUnavailableMapsToSystemErrorWithAccountListUnavailableCode`.
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
- **Ordered log lines:** classifier events (missing response -> `apim.contract.invalid` ERROR; non-blank err-message -> `apim.business.rejected` WARN); `LogLevel.ERROR` at mapping
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
- **Ordered log lines:** classifier events (missing response -> `apim.contract.invalid` ERROR; non-blank err-message -> `apim.business.rejected` WARN); ERROR at mapping. ApimFundPriceModeSourceAdapter is NOT `@LogExecution`-annotated (confirmed absent from the 10-class list). Its use-case entry point (`GetAccountBalanceService`/`GetAccountGainLossService`, confirmed by direct source read) uses a method named `get(...)`, not `execute(...)`, so the broad `*Service.execute(..)` pointcut does not match either. Neither `ExecutionLoggingAspect` pointcut fires for this row.
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
- **Ordered log lines:** classifier events (missing response -> `apim.contract.invalid` ERROR; non-blank err-message -> `apim.business.rejected` WARN); ERROR at mapping. ApimFundComparisonOptionsSourceAdapter is NOT `@LogExecution`-annotated (confirmed absent from the 10-class list). Its use-case entry point (`GetAccountBalanceService`/`GetAccountGainLossService`, confirmed by direct source read) uses a method named `get(...)`, not `execute(...)`, so the broad `*Service.execute(..)` pointcut does not match either. Neither `ExecutionLoggingAspect` pointcut fires for this row.
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
- **Ordered log lines:** classifier events (missing response/empty data -> `apim.contract.invalid` ERROR; non-empty error -> `apim.business.rejected` WARN); `LogLevel.ERROR` at mapping
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `FailureOrigin.DOWNSTREAM_CONTRACT`
- **Public HTTP status:** 503 (`FundFactSheetExceptionMappings.java:29`)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"SYSTEM"` / `ErrorCodes.FUND_FACT_SHEET_RETRIEVAL_UNAVAILABLE`
- **Citation:** `ApimFundFactSheetSourceAdapter.java:95-105`; `docs/design/11-fund-fact-sheet/fund-fact-sheet-apim-contract.md` (C-4/C-5, lines 94-99: confirms empty `data` is malformed, not a valid no-funds case)

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

- **Condition:** missing payload / **non-empty** err-message field / missing data (`StringUtils.hasText(getErrMessage())` is true for a NON-blank, non-empty err-message, not a blank one)
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

(a) `ApiError.downstreamSystem()` -- zero production callers (two test-only references). (b) `WorkflowErrorType.DOWNSTREAM_SYSTEM` -- confirmed 4 call sites: `RegistrationStartWebMapper.java:207` (`MESSAGE_DELIVERY_FAILED`), `RegistrationOtpResendWebMapper.java:142` (`MESSAGE_DELIVERY_FAILED`), `RegistrationOtpVerifyWebMapper.java:144` (`TRUST_LOOKUP_FAILED`), and -- confirmed by direct source read -- `CheckRegistrationUsernameService.java:306` constructs a `WorkflowErrorDescriptor` with `WorkflowErrorType.DOWNSTREAM_SYSTEM` directly for its `downstreamFailed()` result (`DOWNSTREAM_FAILED` status, `ERROR_DOWNSTREAM_UNAVAILABLE` code), which an application-layer use case (not a web mapper) constructs directly -- the only such case in this inventory where a `WorkflowErrorType.DOWNSTREAM_SYSTEM` value originates below the web layer. **Confirmed by `CheckRegistrationUsernameServiceCharacterizationTest.downstreamFailureRendersAsBusinessTypeWithDownstreamErrorCode`:** `RegistrationUsernameCheckWebMapper.apiError()` hardcodes the literal type "BUSINESS" and does not call `WorkflowMutationResponseMapper` on this rendering path, so the `WorkflowErrorType.DOWNSTREAM_SYSTEM` value constructed at :306 never reaches the client. **`CheckRegistrationUsernameService.java:306` is therefore not a public DOWNSTREAM exposure site.** The error CODE (SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE), unlike the type, IS preserved through to the client. (c) literal `"DOWNSTREAM_SYSTEM"` in `ApimExceptionMappings.java:61` and `RegistrationExceptionMappings.java:31` -- the main exposure path by traffic volume. Replacement: `ApiError.type = "SYSTEM"` (`ApiError.java:45-47`, confirmed verbatim: `return new ApiError("SYSTEM", code, message, List.of(), DEFAULT_SEVERITY, source);`).

### Sub-task 2b.2a

Fail-first characterization update, replace every `DOWNSTREAM_SYSTEM` occurrence (the public sites listed above; `CheckRegistrationUsernameService.java:306` is internal-only and is not a public exposure site), extend `PublicDownstreamStatusIsolationGuardTest` with a rendered-JSON behavior check. FE confirmed by Nick: no dependence on `DOWNSTREAM_*` values; E2 approved.

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

The earlier 2b.2 characterization-pack deferrals for Details/OTP `SYSTEM_FAILED` routing and Forgot Password completion response/replay are superseded by M3c implementation and tests. Current source and evidence are summarized in the Forgot Password rows above. Completion evidence remains layered: adapter-level failure characterization and service/controller/replay characterization exist, but no single test composes the actual adapter failure through protected execution, response rendering, and completed replay (review finding F2).

## 8. Open questions

- **Open question for Nick:** technical downstream failure rendered as BUSINESS by RegistrationUsernameCheckWebMapper.apiError() - CheckRegistrationUsernameService.java:306 constructs a WorkflowErrorDescriptor with WorkflowErrorType.DOWNSTREAM_SYSTEM, but the real web mapper discards this value and always renders type="BUSINESS" to the client instead (confirmed by real passing test evidence). Is this intentional, or should the mapper be corrected to render the real WorkflowErrorType like every other adapter in this inventory does?
The previously inventoried `ApimForgotPasswordCompletionAdapter.safeFailure` fallback no longer exists after M3c. The adapter now classifies APIM, crypto, mapping, and local failures into cause-free `ForgotPasswordCompletionTechnicalFailure` values with a `FailureOrigin`; the protected operation/application boundary owns incident publication. The remaining `ApimException` construction sites must be checked against current source before drawing conclusions about public status. Separately, `ApimWebClientFacade.handleErrorBody` maps upstream HTTP errors to `BAD_GATEWAY`; its public error code may vary by upstream status. Completion's public response is governed by Forgot Password's feature-owned mapping, not by exposing the adapter exception directly.

- `RegistrationHttpResponses.status()`'s duplicate `SYSTEM_ERROR`/`BUSINESS_REJECTED` branches (the second `ApiStatus.SYSTEM_ERROR` check, which would return `SERVICE_UNAVAILABLE`, is unreachable because the first check always returns `BAD_GATEWAY`; the repeated `BUSINESS_REJECTED` condition is harmless); no change proposed in M2b.
- 4xx vs 5xx downstream status both resolve identically via the facade today; whether a 4xx should classify differently is open.
- Whether confirmed swallow sites should publish a `FailureEvent` (M3 3.7).

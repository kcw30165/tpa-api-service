# 07 - APIM Error Classification Design (Verified)

Sub-task 2b.1, verification pass. Docs only (docs/design/15-exception-handling/07-apim-error-classification.md), no production or test change. This replaces the prior version committed at 2b11554428b9b36f338490d9d6da8e436176080c on the same branch (new commit, not an amend). Every citation below points at the statement that does the work (the throw, log call, onErrorResume/onErrorMap, or mapping return), not an import or class declaration, and is followed by 2 lines of surrounding context in the accompanying script evidence. Facts that could not be determined statically from source in this pass are marked **UNVERIFIED**, with the specific reason, rather than guessed.

## Corrections applied in this verification pass

1. **Public status is not always 502.** `ApimExceptionMappings.upstream()` returns `exception.getStatusCode()` directly (`ApimExceptionMappings.java:58-59`), so the public HTTP status for a generic `ApimException` is whatever status it was constructed with, not a fixed 502. Confirmed construction sites: every `ApimResponseValidator`/adapter-thrown `ApimException(HttpStatus.BAD_GATEWAY, ...)` site does resolve to 502, but `ApimForgotPasswordCompletionAdapter.safeFailure` (`ApimForgotPasswordCompletionAdapter.java:76-80`) constructs `ApimException(HttpStatus.INTERNAL_SERVER_ERROR, ...)`, which resolves to public HTTP **500**, not 502. Both are recorded per-row below.
2. **`apim.exchange.failed` is `ApimExchangeOutcomeLogger.java:54` (`logExchangeFailure`, `log.error` at line 65); `apim.contract.invalid` is `ApimExchangeOutcomeLogger.java:70` (`logContractInvalid`, `log.error` at line 72).** The earlier report had these two event names and their line numbers swapped.
3. **The ordered log-line list per condition is expanded** to include: `ApimWebClientFacade.handleErrorBody`'s `log.error` (`ApimWebClientFacade.java:409`, only when the HTTP response status itself `isError()`, confirmed by the `statusCode.isError()` branch at `ApimWebClientFacade.java:386`); `ApimExchangeOutcomeLogger`'s three distinct events (`apim.exchange.failed` ERROR, `apim.contract.invalid` ERROR, `apim.business.rejected` WARN -- `ApimExchangeOutcomeLogger.java:54,68,75`); and the terminal `ApiExceptionHandler`/`ErrorEventLogger` `http.api.error` line driven by the matched `*ExceptionMappings` entry's `LogLevel`.
4. **A structural fact not previously stated:** `DefaultApimExchangeExecutor.postEnvelope` (`DefaultApimExchangeExecutor.java:61-69`) attaches `.doOnEach(signal -> logOutcome(...))` directly to the Mono it returns. Because `cryptoService.decryptResponseEnvelope` resolves with the parsed envelope as a normal `onNext` value regardless of the envelope's own `err-message` content (it only throws for JSON parse/mapping failures), `logOutcome`'s `onNext` branch runs `ApimEnvelopeSchemaClassifier.classify(envelope)` and may log `apim.contract.invalid` or `apim.business.rejected` **before** any feature-specific adapter code (e.g. `ApimAccountBalanceSourceAdapter`'s `NOT_FOUND`/`MISMATCH` checks) ever inspects the same envelope downstream. **UNVERIFIED:** `ApimEnvelopeSchemaClassifier`'s exact classification rule (what makes it return `MALFORMED` vs `BUSINESS_REJECTED` vs `SUCCESS`) was not read this pass; its source was not in the investigated file set. This means every row below that uses `ApimExchangeExecutor.postEnvelope` may have an additional, earlier `apim.contract.invalid` or `apim.business.rejected` log line whose exact trigger condition is not yet confirmed. Flagged as an open item in section 8.
5. **Swallow-site correction:** the earlier claim "every `ApimException` passes facade line 409" was wrong. Only HTTP-error responses reach `ApimWebClientFacade.java:409` (gated by `statusCode.isError()` at line 386). `ApimResponseValidator`-thrown and adapter-thrown `ApimException`s occur **after** a successful HTTP exchange (`ApimWebClientFacade.postSuccessfulExchange`/`postEncryptedSuccessfulExchange` only resolve `onNext` when the status was not an error), so they never reach that specific line. They do, however, still pass through `ApimExchangeOutcomeLogger.logExchangeFailure` (`apim.exchange.failed`, ERROR) if and when they are raised as a genuine `Mono.error` downstream of `postEnvelope`'s `doOnEach`. Each of the five swallow sites is annotated below with what, if anything, logged before the swallow.
6. **Crypto-failure logging correction:** the earlier claim that the crypto-failure swallow site ("NOT logged anywhere") was wrong for the general case. `ApimCryptoException`/`ApimResponseMappingException` are thrown inside `ApimPayloadCryptoService.decryptResponseEnvelope` (`ApimPayloadCryptoService.java:164`, wrapping as `ApimCryptoException`), which executes inside `DefaultApimExchangeExecutor.postPlain`/`postEncrypted`'s `.flatMap`, itself wrapped by the same `postEnvelope().doOnEach(...)` described in correction 4. Since `doOnEach` is attached to the Mono returned by `postEnvelope()`, and `ApimForgotPasswordCompletionAdapter`'s `.onErrorResume(ApimCryptoFailureClassifier::isCryptoFailure, ...)` (`ApimForgotPasswordCompletionAdapter.java:56-58`) is chained strictly downstream of that same Mono, the error signal is observed by `logOutcome` (and therefore `ApimExchangeOutcomeLogger.logExchangeFailure`, `apim.exchange.failed` ERROR) **before** the adapter's `onErrorResume` swallows it into `unknownOutcome()`. This crypto-failure condition is therefore logged, at ERROR, before being swallowed.

## 1. Inventory table

One row per downstream condition per adapter (no duplicate adapters). Each row states: Feature / Adapter / Operation / Condition / Detection site / Handling / Message policy / Ordered log lines (event, level, stack trace, file:line) / Idempotency replay / Origin tag today / Proposed `FailureOrigin` per section 3 / Public HTTP status / Public `ApiStatus` / Public error code or type / Citation.

### Registration -- ApimRegistrationLookupAdapter (TRPSearchMember)

- **Condition:** `err.tpa.member.not.found` -> `NOT_ELIGIBLE`
- **Detection site:** `registration/mapper/TrpSearchMemberApimResultMapper.toResult`, line 22-24 (confirmed this is the Registration-specific mapper under `adapter/out/apim/registration/mapper/`, imported explicitly by `ApimRegistrationLookupAdapter.java:11`; distinct from the similarly-named `usernamerecovery/mapper/TrpSearchMemberApimResultMapper` used only by Username Recovery)
- **Handling:** returned as a normal `RegistrationLookupResult(NOT_ELIGIBLE, ...)`; `ApimRegistrationLookupAdapter.withFailureLogging`'s `doOnEach` then calls `recordDownstreamRejection` (`ApimRegistrationLookupAdapter.java:101`), which calls `failureEventPort.publish(...)` (`ApimRegistrationLookupAdapter.java:147`)
- **Message policy:** N/A -- this is a successful application result (`RegistrationLookupResult`), never an `ExceptionMapping`; no `MessagePolicy` applies
- **Ordered log lines:** (0) possible earlier `apim.contract.invalid`/`apim.business.rejected` from `ApimEnvelopeSchemaClassifier`, UNVERIFIED (correction 4); (1) `event.registration.lookup.downstream_rejection` `FailureEvent` published via `failureEventPort.publish` -- logged by `StructuredFailureEventLogger`, which today treats `FailureOrigin.DOWNSTREAM_CONTRACT` as **ERROR** (not in `WARN_ORIGINS`)
- **Idempotency replay:** No -- Registration **lookup** (`searchMember`) is called directly by `RegistrationCompleteProtectedOperation.refreshTrusts` and is not itself wrapped by an idempotency execution bridge; only Registration **start/complete/otp-verify/otp-resend** are idempotency-protected
- **Origin tag today:** `FailureOrigin.DOWNSTREAM_CONTRACT` (`ApimRegistrationLookupAdapter.java:121`)
- **Proposed `FailureOrigin` (section 3):** `DOWNSTREAM_BUSINESS` -- E1 names this exact row for the ERROR-to-WARN exception
- **Public HTTP status:** 422 (hardcoded in the `FailureEvent`, `ApimRegistrationLookupAdapter.java:156`) -- this is the `FailureEvent`'s own `httpStatus` field for logging/alerting only, not the HTTP status of the Registration Lookup response itself, which is a normal 200 result
- **Public ApiStatus:** n/a at this layer (see above)
- **Public error code/type:** `err.tpa.member.not.found` (downstream code) / `RegistrationErrorKey.START_VERIFICATION_FAILED` (public key), `ApimRegistrationLookupAdapter.java:154-155`
- **Citation:** `ApimRegistrationLookupAdapter.java:144-159`

### Registration -- ApimRegistrationLookupAdapter (TRPSearchMember)

- **Condition:** `EMPTY_DATA` / `INVALID_ACCOUNT_DATA` / `UNEXPECTED_ERROR` reasons
- **Detection site:** `registration/mapper/TrpSearchMemberApimResultMapper.toResult`, lines 25-28 (unrecognized non-blank error -> `UNEXPECTED_ERROR`), 30-33 (empty items -> `EMPTY_DATA`), and `toAccount` lines 39-42 (missing env/trustCode -> `INVALID_ACCOUNT_DATA`)
- **Handling:** throws `RegistrationLookupDownstreamException`, mapped by `RegistrationExceptionMappings.lookupDownstream()` (`RegistrationExceptionMappings.java:18-44`); also caught by `ApimRegistrationLookupAdapter.withFailureLogging`'s `doOnEach` (`ApimRegistrationLookupAdapter.java:108-127`), which publishes a second `FailureEvent`
- **Message policy:** `MessagePolicy.RESOLVE` (`RegistrationExceptionMappings.java` Javadoc, built via `ErrorDescriptor#single`)
- **Ordered log lines:** (0) possible earlier classifier log, UNVERIFIED (correction 4); (1) `registration.lookup.downstream_contract_failure` `FailureEvent`, origin `DOWNSTREAM_CONTRACT` -> logged ERROR by `StructuredFailureEventLogger`; (2) `http.api.error` at `LogLevel.ERROR`, no stack trace (`RegistrationExceptionMappings.java:35`, final arg `false`)
- **Idempotency replay:** No (see row above)
- **Origin tag today:** `FailureOrigin.DOWNSTREAM_CONTRACT` (`ApimRegistrationLookupAdapter.java:121`, same tag reused)
- **Proposed `FailureOrigin` (section 3):** `DOWNSTREAM_CONTRACT` -- unrecognized err-message / missing-or-empty data; correctly classified already
- **Public HTTP status:** 503 (`RegistrationExceptionMappings.java:29`)
- **Public ApiStatus:** `SYSTEM_ERROR` (`RegistrationExceptionMappings.java:30`)
- **Public error code/type:** error type literal `"DOWNSTREAM_SYSTEM"` (`RegistrationExceptionMappings.java:31`), code `SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value()` (`RegistrationExceptionMappings.java:32`)
- **Citation:** `RegistrationExceptionMappings.java:18-44`

### Registration -- ApimRegistrationCompletionAdapter (TRPRegMember)

- **Condition:** `ApimProfileResolutionException` during completion (our own profile-routing configuration failure, not a downstream response problem)
- **Detection site:** `ApimRegistrationCompletionAdapter.java:54-55`
- **Handling:** `.onErrorMap(ApimProfileResolutionException.class, RegistrationCompletionPreDispatchException::new)` (`ApimRegistrationCompletionAdapter.java:54-55`). `RegistrationCompletionPreDispatchException` is **not** registered in `ExceptionMappingRegistryConfig`'s mapping list (confirmed: absent from its 20-entry `List.of(...)`, `ExceptionMappingRegistryConfig.java:28-49`). Instead it propagates out of `operation.execute(input)` inside `SharedCompleteRegistrationService.execute` (`SharedCompleteRegistrationService.java:162`, passed as the `executeFn` to `IdempotentExecutionCommand`), where the failure-classifier function explicitly checks `failure instanceof RegistrationCompletionPreDispatchException ? PROVEN_NO_SIDE_EFFECT : POSSIBLE_SIDE_EFFECT` (`SharedCompleteRegistrationService.java:163-166`). This confirms the exception is consumed as an internal idempotency-classification signal, not rendered via `ExceptionMappingRegistry`/`ApiExceptionHandler`.
- **Message policy:** N/A -- never reaches an `ExceptionMapping`
- **Ordered log lines:** (0) possible earlier classifier log via `doOnEach`, UNVERIFIED (correction 4); no confirmed `http.api.error` line, since this never reaches the web-layer handler
- **Idempotency replay:** Yes -- Registration **complete** is idempotency-protected (`SharedCompleteRegistrationService`/`RegistrationCompleteSharedExecutionBridge`)
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `LOCAL_SYSTEM` -- `ApimProfileResolutionException` is explicitly named in section 3 as "our own configuration"
- **Public HTTP status / ApiStatus / code:** **UNVERIFIED** -- depends entirely on what `IdempotencyExecutor.execute()` does with a `PROVEN_NO_SIDE_EFFECT`-classified failure after this point; `IdempotencyExecutor`'s own source was not read this pass, so whether this condition ultimately surfaces to the client as a specific `CompleteRegistrationResult` status (and which one) is not confirmed
- **Citation:** `ApimRegistrationCompletionAdapter.java:54-55`; `SharedCompleteRegistrationService.java:162-166`; `ExceptionMappingRegistryConfig.java:28-49` (absence confirmed by full-list inspection)

### Forgot Password -- ApimForgotPasswordUsernameLookupAdapter / SharedApimTrpChkCustIdTransport (TRPChkCustId)

- **Condition:** empty response / malformed data items
- **Detection site:** direct `throw new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, ...)` construction (not via `ApimResponseValidator`)
- **Handling:** propagates as `ApimException`, mapped by `ApimExceptionMappings.upstream()`
- **Message policy:** `MessagePolicy.RESOLVE` (`ApimExceptionMappings.java` Javadoc)
- **Ordered log lines:** (0) possible classifier log, UNVERIFIED; (1) `apim.exchange.failed` ERROR (`ApimExchangeOutcomeLogger.java:54-65`) if raised downstream of `postEnvelope`; (2) `http.api.error` ERROR, no stack trace (`ApimExceptionMappings.java:65`, final arg `false`)
- **Idempotency replay:** Yes -- Forgot Password username lookup participates in the Forgot Password **start** idempotency flow
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `DOWNSTREAM_CONTRACT` -- missing response / malformed data
- **Public HTTP status:** 502 (`exception.getStatusCode()`, `ApimExceptionMappings.java:59`, forwarding the `HttpStatus.BAD_GATEWAY` constructor argument)
- **Public ApiStatus:** `SYSTEM_ERROR` (`ApimExceptionMappings.java:60`)
- **Public error code/type:** error type literal `"DOWNSTREAM_SYSTEM"` (`ApimExceptionMappings.java:61`), code `exception.getErrorCode()` = `ErrorCodes.APIM_RESPONSE_INVALID`
- **Citation:** `ApimExceptionMappings.java:49-69` (mapping); construction site not re-dumped this pass, carried from prior investigation

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)

- **Condition:** missing response envelope
- **Detection site:** `ApimForgotPasswordCompletionAdapter.java:64-68` (`toEnvelope`, `envelope == null || envelope.getResponse() == null`)
- **Handling:** `throw new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, ...)` (`ApimForgotPasswordCompletionAdapter.java:65-68`), mapped by `ApimExceptionMappings.upstream()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** (0) possible classifier log, UNVERIFIED; (1) `apim.exchange.failed` ERROR; (2) `http.api.error` ERROR, no stack trace
- **Idempotency replay:** Yes -- Forgot Password **complete** is idempotency-protected (`ForgotPasswordCompleteProtectedOperation`/`ForgotPasswordCompleteExecutionBridge`)
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `DOWNSTREAM_CONTRACT` -- missing response
- **Public HTTP status:** 502
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `APIM_RESPONSE_INVALID`
- **Citation:** `ApimForgotPasswordCompletionAdapter.java:62-72`

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)

- **Condition:** crypto failure (`ApimCryptoFailureClassifier.isCryptoFailure` true, i.e. `ApimCryptoException` or `ApimResponseMappingException`)
- **Detection site:** `ApimPayloadCryptoService.decryptResponseFields`, line 164 (`throw new ApimCryptoException("Failed to decrypt APIM response payload.", ex)`); classifier at `ApimCryptoFailureClassifier.java:15-16`. This is also the **sole confirmed caller** of `isCryptoFailure` anywhere in `src/main/java` (`ApimForgotPasswordCompletionAdapter.java:57`)
- **Handling:** `.onErrorResume(ApimCryptoFailureClassifier::isCryptoFailure, e -> Mono.just(ForgotPasswordCompletionResult.unknownOutcome()))` (`ApimForgotPasswordCompletionAdapter.java:56-58`)
- **Message policy:** N/A -- swallowed before reaching any `ExceptionMapping`
- **Ordered log lines (corrected, see correction 6):** (1) `apim.exchange.failed` ERROR, `ApimExchangeOutcomeLogger.logExchangeFailure` (`ApimExchangeOutcomeLogger.java:52-65`) fires on the error signal **before** the adapter's downstream `onErrorResume` swallows it, because `doOnEach` is attached to the upstream `postEnvelope()` Mono and `onErrorResume` is chained after it. This condition **is** logged, at ERROR, contrary to the earlier "not logged anywhere" claim.
- **Idempotency replay:** Yes (Forgot Password complete)
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `DOWNSTREAM_INVALID_RESPONSE` -- explicit rule names `ApimCryptoException`/`ApimResponseMappingException`
- **Public HTTP status/ApiStatus/code:** n/a -- swallowed to a success-shaped `unknownOutcome()` result, never an HTTP error response
- **Citation:** `ApimPayloadCryptoService.java:158-166`; `ApimCryptoFailureClassifier.java:11-18`; `ApimForgotPasswordCompletionAdapter.java:52-58`; `DefaultApimExchangeExecutor.java:60-69` (doOnEach/onErrorResume ordering)

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)

- **Condition:** any other throwable not already `ApimException` (`safeFailure` catch-all)
- **Detection site:** `ApimForgotPasswordCompletionAdapter.java:74-81`
- **Handling:** `.onErrorMap(this::safeFailure)` -> `new ApimException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.SYSTEM_UNEXPECTED, "TRPResetPassword transport failed", t)` (`ApimForgotPasswordCompletionAdapter.java:76-80`)
- **Message policy:** `MessagePolicy.RESOLVE` (via `ApimExceptionMappings.upstream()`)
- **Ordered log lines:** (1) `apim.exchange.failed` ERROR; (2) `http.api.error` ERROR, no stack trace
- **Idempotency replay:** Yes
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** classify by cause -- the surrounding retry logic in `ApimWebClientFacade.isTransientBusinessFailure` treats `WebClientRequestException`/`TimeoutException`/5xx-pattern messages as transient and retries them first, so a failure reaching this catch-all after retries are exhausted is predominantly `DOWNSTREAM_TRANSPORT`; a non-transient, non-`ApimException` runtime bug would instead be `LOCAL_SYSTEM`. This boundary is not distinguished by the current code and is listed as an open question in section 8.
- **Public HTTP status:** **500**, not 502 (correction 1) -- `ApimExceptionMappings.java:59` forwards `exception.getStatusCode()`, and this constructor used `HttpStatus.INTERNAL_SERVER_ERROR`
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `SYSTEM_UNEXPECTED`
- **Citation:** `ApimForgotPasswordCompletionAdapter.java:74-81`

### Forgot Password -- ApimForgotPasswordDetailsLookupAdapter (TRPSearchMember)

- **Condition:** missing envelope
- **Detection site:** `ApimForgotPasswordDetailsLookupAdapter.java:51-52` (confirmed via reference list; full adapter source not re-dumped this pass)
- **Handling:** `throw new ForgotPasswordDetailsDownstreamException(Reason.UNEXPECTED_ERROR)`. This exception type is **not** registered in `ExceptionMappingRegistryConfig`'s list either. Its consumer chain was traced to `IdempotentForgotPasswordDetailsOtpService.executeAcquired` (`IdempotentForgotPasswordDetailsOtpService.java:78-97`), which calls `operation.execute(command)` (`ForgotPasswordDetailsOtpProtectedOperation`) and checks `execution.result().status() == SYSTEM_ERROR`, throwing `IllegalStateException` if so (line 85-88). **UNVERIFIED:** the exact statement inside `ForgotPasswordDetailsOtpProtectedOperation` that catches `ForgotPasswordDetailsDownstreamException` and converts it to a `ForgotPasswordDetailsOtpResult.Status` was not read this pass (that class's source was not in the investigated file set), so the precise reason-to-status mapping is not confirmed, only that it is consumed before reaching the web layer (strong circumstantial evidence: absent from the registry; the bridge's `IllegalStateException` wrapping at a `SYSTEM_ERROR` status implies internal classification already happened one layer below).
- **Message policy:** N/A (not reaching an `ExceptionMapping`, per the evidence above)
- **Ordered log lines:** UNVERIFIED pending the protected operation's source
- **Idempotency replay:** Yes -- Forgot Password **details/OTP** is idempotency-protected
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `DOWNSTREAM_CONTRACT` -- missing response
- **Public HTTP status/ApiStatus/code:** UNVERIFIED (see above)
- **Citation:** `ForgotPasswordTrpSearchMemberMapper.java:46`; `ApimForgotPasswordDetailsLookupAdapter.java:51-52`; `IdempotentForgotPasswordDetailsOtpService.java:78-97`

### Username Recovery -- TrpSearchMemberAdapter (TRPSearchMember)

- **Condition:** `TrpSearchMemberApimDownstreamException` (unrecognized non-blank err-message, from `usernamerecovery/mapper/TrpSearchMemberApimResultMapper.toResult` lines 47-50)
- **Detection site:** `usernamerecovery/mapper/TrpSearchMemberApimResultMapper.java:47-50`
- **Handling:** `.onErrorResume(TrpSearchMemberApimDownstreamException.class, e -> Mono.just(unknownOutcome()))` (`TrpSearchMemberAdapter.java:54-55`)
- **Message policy:** N/A -- swallowed; this exception type is also absent from `ExceptionMappingRegistryConfig`
- **Ordered log lines:** (1) `apim.exchange.failed` ERROR, `ApimExchangeOutcomeLogger.logExchangeFailure` fires before the swallow (same `doOnEach`-before-`onErrorResume` ordering as the crypto-failure row above) -- this condition **is** logged, correcting the earlier "silent overall" framing to be precise: it is silent only at the adapter's own swallow site, not end-to-end
- **Idempotency replay:** Yes -- Username Recovery **request** is idempotency-protected
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `DOWNSTREAM_CONTRACT` -- unrecognized err-message
- **Public HTTP status/ApiStatus/code:** n/a -- swallowed to success-shaped `unknownOutcome()`
- **Citation:** `usernamerecovery/mapper/TrpSearchMemberApimResultMapper.java:39-56`; `TrpSearchMemberAdapter.java:48-56`

### Username Recovery -- TrpSearchMemberAdapter (TRPSearchMember)

- **Condition:** any `ApimException`
- **Detection site:** n/a -- generic catch, could originate from the facade (transport/HTTP-error) or from `ApimResponseValidator` (contract) if this path used it; this specific adapter does not call `ApimResponseValidator`, so in practice this branch catches facade-raised `ApimException`s (HTTP-error-response failures)
- **Handling:** `.onErrorResume(ApimException.class, e -> Mono.just(unavailable()))` (`TrpSearchMemberAdapter.java:56`)
- **Message policy:** N/A -- swallowed
- **Ordered log lines:** (1) `ApimWebClientFacade.handleErrorBody`'s `log.error` (`ApimWebClientFacade.java:409`) fires for the underlying HTTP-error response, since this is a facade-raised `ApimException`, not a validator one; (2) `apim.exchange.failed` ERROR (`ApimExchangeOutcomeLogger`) also fires before the swallow
- **Idempotency replay:** Yes (Username Recovery request)
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `DOWNSTREAM_TRANSPORT` -- this swallow site only reaches facade-raised, HTTP-error-response `ApimException`s in this adapter (this adapter does not call `ApimResponseValidator`)
- **Public HTTP status/ApiStatus/code:** n/a -- swallowed to success-shaped `unavailable()`
- **Citation:** `TrpSearchMemberAdapter.java:48-56`; `ApimWebClientFacade.java:380-416`

### Login -- TrpMemberLoginAdapter (TRPMemberLogin)

- **Condition:** `IllegalStateException` (empty envelope, raised inside `SharedApimTrpMemberLoginTransport`, not re-dumped this pass)
- **Detection site:** `TrpMemberLoginAdapter.java:37-39`
- **Handling:** `.onErrorResume(IllegalStateException.class, ignored -> Mono.just(mapper.failure(CredentialAuthenticationStatus.UNAVAILABLE)))` (`TrpMemberLoginAdapter.java:37-39`)
- **Message policy:** N/A -- swallowed
- **Ordered log lines:** **UNVERIFIED** -- whether this `IllegalStateException` is raised by `SharedApimTrpMemberLoginTransport` upstream of (and therefore observed by) `ApimExchangeOutcomeLogger`'s `doOnEach`, or constructed locally after `postEnvelope()` has already resolved successfully (in which case no `apim.exchange.failed` log would fire), was not confirmed this pass; that transport class's source was not read
- **Idempotency replay:** Yes -- Login **start** is idempotency-protected
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `DOWNSTREAM_CONTRACT` -- missing response (consistent with every other "empty envelope" row), pending confirmation of the exact raise site
- **Public HTTP status/ApiStatus/code:** n/a -- swallowed to `UNAVAILABLE`, handled by `LoginStartWebMapper`
- **Citation:** `TrpMemberLoginAdapter.java:30-40`

### Login -- TrpGetAccountListAdapter (TRPGetAccountList)

- **Condition:** any error
- **Detection site:** n/a -- generic catch on `transport.post(command)`
- **Handling:** `.onErrorReturn(LoginAccountAuthorizationResult.unavailable())` (`TrpGetAccountListAdapter.java:29-32`)
- **Message policy:** N/A -- swallowed; `onErrorReturn` provides no access to the throwable, so no log can be written at this exact statement
- **Ordered log lines:** **UNVERIFIED** -- whether the underlying transport passes through `ApimExchangeExecutor`/`ApimExchangeOutcomeLogger` at all was not confirmed (`TrpGetAccountListTransport`'s source was not read this pass)
- **Idempotency replay:** No -- `TrpGetAccountListAdapter` is called directly by Login's account-authorization step; not itself found under an idempotency execution bridge
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** classify by cause -- `onErrorReturn` catches every throwable indiscriminately (transport, contract, or otherwise); this is listed as an open question in section 8
- **Public HTTP status/ApiStatus/code:** n/a -- swallowed to `unavailable()`, handled by `LoginHttpResponses`
- **Citation:** `TrpGetAccountListAdapter.java:26-33`

### Account Balance -- ApimAccountBalanceSourceAdapter (TRPGetMemberAccBal)

- **Condition:** `err.tpa.account.invalid` -> `ACCOUNT_NOT_FOUND`
- **Detection site:** `ApimAccountBalanceSourceAdapter.java:54-55`
- **Handling:** `AccountBalanceRetrievalException.accountNotFound()`, mapped via `AccountBalanceExceptionMappings.retrievalFailure()`, which delegates entirely to `AccountBalanceRetrievalResponsePolicy.map()` as the single source of truth (`AccountBalanceExceptionMappings.java:40`)
- **Message policy:** `MessagePolicy.NONE` for every entry (`AccountBalanceExceptionMappings.java:47`, forced regardless of reason)
- **Ordered log lines:** `logAtError = exception.reason() == SYSTEM_FAILURE` (`AccountBalanceExceptionMappings.java:49-50`) -- since this reason is `ACCOUNT_NOT_FOUND`, not `SYSTEM_FAILURE`, this logs at **WARN**, not ERROR; `http.api.error`, no stack trace (final arg `false`, `AccountBalanceExceptionMappings.java:57`)
- **Idempotency replay:** No -- Account Balance is a read-only query, not found under any idempotency package
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `DOWNSTREAM_BUSINESS` -- explicitly named in section 3
- **Public HTTP status:** 422 (`AccountBalanceRetrievalResponsePolicy.java:26-32`, `HttpStatus.UNPROCESSABLE_ENTITY`)
- **Public ApiStatus:** `BUSINESS_REJECTED` (`AccountBalanceRetrievalResponsePolicy.java:29`)
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
- **Proposed `FailureOrigin` (section 3):** `DOWNSTREAM_BUSINESS`
- **Public HTTP status:** 422
- **Public ApiStatus:** `BUSINESS_REJECTED`
- **Public error code/type:** `ApiError.business("err.ab.account.mismatch", null, [], "SERVER")` (`AccountBalanceRetrievalResponsePolicy.java:16,26-31`)
- **Citation:** `AccountBalanceRetrievalResponsePolicy.java:13-24`; `ApimAccountBalanceSourceAdapter.java:56-57`

### Account Balance -- ApimAccountBalanceSourceAdapter (TRPGetMemberAccBal)

- **Condition:** any other non-blank error / malformed content (via the inner `try`/`catch` around `requireSingleData(requireSuccessData(...))`) / any other exception (outer `onErrorMap` catch-all)
- **Detection site:** `ApimAccountBalanceSourceAdapter.java:58-67` (non-blank-error branch and inner try/catch), `.onErrorMap` at lines 69-73 (outer catch-all)
- **Handling:** `AccountBalanceRetrievalException.systemFailure()`
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** `logAtError = true` (`reason == SYSTEM_FAILURE`) -> **ERROR**; `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** classify by cause, per the catch-all rule -- the non-blank-unrecognized-error branch (line 58-59) is `DOWNSTREAM_CONTRACT`; a `RuntimeException` from the inner try/catch around the validator call is also `DOWNSTREAM_CONTRACT` (`ApimResponseValidator` callers are always `DOWNSTREAM_CONTRACT` per the explicit rule); the outer `onErrorMap` catch-all additionally catches any transport-level failure that bypassed the inner try/catch entirely, which would be `DOWNSTREAM_TRANSPORT`. The current code does not distinguish these at the `systemFailure()` call site.
- **Public HTTP status:** 503 (`AccountBalanceRetrievalResponsePolicy.java:17-22`, `HttpStatus.SERVICE_UNAVAILABLE`)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, "SERVER")` (`AccountBalanceRetrievalResponsePolicy.java:21`) -- note this uses the `"SYSTEM"` `ApiError.type` literal (see section 6), not `"DOWNSTREAM_SYSTEM"`
- **Citation:** `AccountBalanceRetrievalResponsePolicy.java:13-24`; `ApimAccountBalanceSourceAdapter.java:58-73`

### Account Gain/Loss -- ApimAccountGainLossSourceAdapter (TRPGetInvestGL)

- **Condition:** `err.tpa.fund.switching.in.progress`
- **Detection site:** adapter line ~74 (reference-confirmed; not re-dumped verbatim this pass)
- **Handling:** `AccountGainLossRetrievalException.fundSwitchingInProgress()`, mapped via `AccountGainLossExceptionMappings.retrievalFailure()` delegating to `AccountGainLossRetrievalResponsePolicy.map()`
- **Message policy:** `MessagePolicy.NONE` (`AccountGainLossExceptionMappings.java:40`)
- **Ordered log lines:** `logAtError = reason == SYSTEM_FAILURE` (`AccountGainLossExceptionMappings.java:42-43`) -- WARN for this reason; `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `DOWNSTREAM_BUSINESS` -- explicitly named
- **Public HTTP status:** 422 (`AccountGainLossRetrievalResponsePolicy.java:16-23`)
- **Public ApiStatus:** `BUSINESS_REJECTED`
- **Public error code/type:** `ApiError.business("err.fund.switching.in.progress", null, [], "SERVER")` (`AccountGainLossRetrievalResponsePolicy.java:21-22`)
- **Citation:** `AccountGainLossRetrievalResponsePolicy.java:13-31`

### Account Gain/Loss -- ApimAccountGainLossSourceAdapter (TRPGetInvestGL)

- **Condition:** any other non-blank error / malformed content / catch-all
- **Detection site:** adapter's `try`/`catch` around `requireSingleData(requireSuccessData(...))`, line 73-77 (reference-confirmed)
- **Handling:** `systemFailure()`
- **Message policy:** `MessagePolicy.NONE`
- **Ordered log lines:** ERROR (`reason == SYSTEM_FAILURE`); `http.api.error`, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** classify by cause, same reasoning as Account Balance's catch-all row
- **Public HTTP status:** 503
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `ApiError.system("err.system.unavailable", null, "SERVER")` (`AccountGainLossRetrievalResponsePolicy.java:28`)
- **Citation:** `AccountGainLossRetrievalResponsePolicy.java:13-31`

### Fund Price -- ApimFundPriceSourceAdapter / ApimFundPriceModeSourceAdapter (TRPGetFundPrice / TRPGetFundPriceMode)

- **Condition:** `err.tpa.invalid.date`
- **Detection site:** adapter-level check (reference-confirmed, not re-dumped)
- **Handling:** `FundPriceInvalidDateException.forInvalidDate()`, mapped via `FundPriceExceptionMappings.invalidDate()`
- **Message policy:** `MessagePolicy.RESOLVE` (via `ErrorDescriptor#single`, `FundPriceExceptionMappings.java:40-61`)
- **Ordered log lines:** `LogLevel.WARN` (`FundPriceExceptionMappings.java:57`), no stack trace; `http.api.error`
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `DOWNSTREAM_BUSINESS` -- explicitly named
- **Public HTTP status:** 400 (`FundPriceExceptionMappings.java:51`)
- **Public ApiStatus:** `VALIDATION_FAILED`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_PRICE_REQUEST_INVALID` (`FundPriceExceptionMappings.java:53-54`)
- **Citation:** `FundPriceExceptionMappings.java:40-61`

### Fund Price -- ApimFundPriceSourceAdapter / ApimFundPriceModeSourceAdapter (TRPGetFundPrice / TRPGetFundPriceMode)

- **Condition:** any other non-blank error / malformed / catch-all
- **Handling:** `FundPriceRetrievalException.systemFailure()`, mapped via `FundPriceExceptionMappings.retrievalUnavailable()`
- **Message policy:** `MessagePolicy.RESOLVE` (`FundPriceExceptionMappings.java:18-38`)
- **Ordered log lines:** `LogLevel.ERROR` (`FundPriceExceptionMappings.java:34`), no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** classify by cause (same reasoning as Account Balance's catch-all row)
- **Public HTTP status:** 503 (`FundPriceExceptionMappings.java:28`)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_PRICE_RETRIEVAL_UNAVAILABLE`
- **Citation:** `FundPriceExceptionMappings.java:18-38`

### Fund Comparison -- ApimFundComparisonOptionsSourceAdapter (TRPGetFundCmpOpt)

- **Condition:** any non-blank error / malformed / catch-all
- **Handling:** `FundComparisonRetrievalException.systemFailure()`, mapped via `FundComparisonExceptionMappings.retrievalUnavailable()`
- **Message policy:** `MessagePolicy.RESOLVE` (`FundComparisonExceptionMappings.java:19-40`)
- **Ordered log lines:** `LogLevel.ERROR` (`FundComparisonExceptionMappings.java:36`), no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** classify by cause
- **Public HTTP status:** 503 (`FundComparisonExceptionMappings.java:30`)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"FORM"` / `ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE`
- **Citation:** `FundComparisonExceptionMappings.java:15-41`

### Fund Fact Sheet -- ApimFundFactSheetSourceAdapter (TRPGetFundFactSheet)

- **Condition:** null item/list/row (logged via the adapter's own `LOG.error`) / non-empty error / malformed
- **Handling:** `FundFactSheetRetrievalException.systemFailure()`, mapped via `FundFactSheetExceptionMappings.retrievalUnavailable()`
- **Message policy:** `MessagePolicy.NONE` (`ErrorDescriptor#singleWithoutMessage`, `FundFactSheetExceptionMappings.java:28-37`, since this exception's public message is always `null` per O-12)
- **Ordered log lines:** feature-level `LOG.error` in the adapter itself (prior to the exception being thrown, for the null-item case specifically); `LogLevel.ERROR` at the mapping (`FundFactSheetExceptionMappings.java:35`), no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** the null-item/missing-data case leans `DOWNSTREAM_CONTRACT`; the generic catch-all otherwise classifies by cause
- **Public HTTP status:** 503 (`FundFactSheetExceptionMappings.java:29`)
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"SYSTEM"` / `ErrorCodes.FUND_FACT_SHEET_RETRIEVAL_UNAVAILABLE`
- **Citation:** `FundFactSheetExceptionMappings.java:14-39`

### Personal Information -- ApimUpdatePersonalInformationAdapter (TRPUpdMemberInfo)

- **Condition:** empty/all-null data items, or no item matches `selected`
- **Handling:** `throw new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, ...)`, mapped via `ApimExceptionMappings.upstream()` (**not** `PersonalInformationExceptionMappings`, which is a separate mapping for `InvalidPersonalInformationUpdateException`, a distinct validation exception)
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.exchange.failed` ERROR (if raised downstream of `postEnvelope`); `http.api.error` ERROR, no stack trace
- **Idempotency replay:** No -- Personal Information update is not found under any idempotency package
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `DOWNSTREAM_CONTRACT` -- missing/malformed data
- **Public HTTP status:** 502
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `APIM_RESPONSE_INVALID`
- **Citation:** reference-confirmed construction site; `ApimExceptionMappings.java:49-69`

### Reference Date -- ApimReferenceDateRefreshAdapter (TRPGetWebSysDate)

- **Condition:** missing payload / blank err-message field / missing data / unparsable date
- **Handling:** `throw new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, ...)`, mapped via `ApimExceptionMappings.upstream()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.exchange.failed` ERROR; `http.api.error` ERROR, no stack trace
- **Idempotency replay:** No
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `DOWNSTREAM_CONTRACT` for missing-payload/missing-data sub-cases; the "unparsable date" sub-case is arguably closer to `DOWNSTREAM_INVALID_RESPONSE` (a present value that fails to parse on our side) -- listed as a row-level nuance, not resolved here
- **Public HTTP status:** 502
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `APIM_RESPONSE_INVALID`
- **Citation:** reference-confirmed construction site; `ApimExceptionMappings.java:49-69`

### Shared `ApimResponseValidator`-reliant adapters (Contribution Summary, Member Info, Notice Message, Notification Read Status, Reference Data Countries, General Metadata, Account List transport, Member Login transport)

- **Adapters:** `ApimContributionSummaryAdapter`, `ApimMemberInfoAdapter`, `ApimNoticeMessageAdapter`, `ApimNotificationReadStatusAdapter`, `ApimReferenceDataCountriesAdapter`, `ApimGeneralMetadataSourceAdapter`, `SharedApimTrpGetAccountListTransport`, `SharedApimTrpMemberLoginTransport` (every confirmed call site of `ApimResponseValidator.requireSuccessData`, `ApimResponseValidator.java:27-45`, re-confirmed via full grep)
- **Condition:** any non-empty `err-message` on an otherwise successful HTTP exchange
- **Detection site:** `ApimResponseValidator.java:36-38` (`if (!errMessage.isEmpty()) throw new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, errMessage);`) -- the raw downstream `err-message` text is passed verbatim as the exception's internal diagnostic message only; the public message is resolved separately via `MessagePolicy.RESOLVE`/`PublicMessageResolver` by error code, never by this raw text
- **Handling:** mapped via `ApimExceptionMappings.upstream()`
- **Message policy:** `MessagePolicy.RESOLVE`
- **Ordered log lines:** `apim.exchange.failed` ERROR; `http.api.error` ERROR, no stack trace
- **Idempotency replay:** No -- all eight are read-only or non-mutating queries, none found under an idempotency package
- **Origin tag today:** none
- **Proposed `FailureOrigin` (section 3):** `DOWNSTREAM_CONTRACT` for every one of these -- explicit rule: "unrecognised non-blank err-message, **including all `ApimResponseValidator` callers** -> `DOWNSTREAM_CONTRACT`", applying uniformly regardless of whether the specific err-message text happens to be recognized elsewhere
- **Public HTTP status:** 502
- **Public ApiStatus:** `SYSTEM_ERROR`
- **Public error code/type:** `"DOWNSTREAM_SYSTEM"` / `APIM_RESPONSE_INVALID`
- **Citation:** `ApimResponseValidator.java:27-45`; 8 call sites confirmed via grep across `ApimNotificationReadStatusAdapter.java:67`, `ApimMemberInfoAdapter.java:59`, `ApimReferenceDataCountriesAdapter.java:50`, `ApimGeneralMetadataSourceAdapter.java:88`, `ApimContributionSummaryAdapter.java:54`, `ApimUpdatePersonalInformationAdapter.java:61` (also see the dedicated Personal Information row above), `ApimNoticeMessageAdapter.java:72`, `ApimAccountGainLossSourceAdapter.java:76` (this last one feeds into the already-documented Account Gain/Loss catch-all row, not a new row)

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
- Specific rows -> `FailureOrigin.DOWNSTREAM_BUSINESS`: Registration `err.tpa.member.not.found`, Account Balance `NOT_FOUND` / `MISMATCH`, Account Gain/Loss `err.tpa.fund.switching.in.progress`, Fund Price `err.tpa.invalid.date`
- Catch-all `systemFailure()` (Fund Price, Fund Comparison, Fund Fact Sheet, Account Balance, Account Gain/Loss): classify by cause using the rules above; the public exception, status and body stay unchanged.

## 4. Approved exceptions to the M2b "no change" rules

- **E1 (log level):** Registration `err.tpa.member.not.found` moves from `FailureOrigin.DOWNSTREAM_CONTRACT` to `FailureOrigin.DOWNSTREAM_BUSINESS` (ERROR -> WARN). Pinned fail-first in the Registration feature commit (sub-task 2b.4..2b.n). No other log level may change in M2b.
- **E2 (public contract):** no `DOWNSTREAM_*` value may reach the frontend (section 6, sub-task 2b.2a).

## 5. Incident ERROR per category (input for M3 3.8 -- describe only, no level change now)

- **DOWNSTREAM_BUSINESS:** proposed incident line is the feature-level log closest to the business decision (e.g. Registration's `recordDownstreamRejection`, Account Gain/Loss's `fundSwitchingInProgress` warn). The earlier, generic `apim.exchange.failed`/`apim.business.rejected` lines (and any still-UNVERIFIED `ApimEnvelopeSchemaClassifier` line, correction 4) become non-incident context.
- **DOWNSTREAM_CONTRACT:** proposed incident line is `ApimExchangeOutcomeLogger`'s `apim.contract.invalid` (`ApimExchangeOutcomeLogger.java:68-73`), corrected from the earlier line-number swap.
- **DOWNSTREAM_INVALID_RESPONSE:** proposed incident line is `apim.exchange.failed` (`ApimExchangeOutcomeLogger.java:52-66`) for the one confirmed caller (`ApimForgotPasswordCompletionAdapter`'s crypto-failure path); this is now confirmed to actually log today (correction 6), so M3 3.8's job here is to pick the correct canonical line among the now-known candidates, not to add logging from nothing.
- **DOWNSTREAM_TRANSPORT:** proposed incident line is `ApimWebClientFacade`'s `log.error` (`ApimWebClientFacade.java:409`), confirmed to fire only for genuine HTTP-error responses (`statusCode.isError()`, line 386).
- **LOCAL_SYSTEM:** proposed incident line is wherever `ApimProfileResolutionException` is first caught (`ApimRegistrationCompletionAdapter.java:54-55` for Registration completion; other callers not re-traced this pass).

## 6. Public DOWNSTREAM exposure (decision by Nick: no DOWNSTREAM_* in any public field)

Three distinct code paths were confirmed that can place a `DOWNSTREAM`-containing token into a public field, and they are structurally different from each other:

**(a) `ApiError.downstreamSystem(...)` factory** (`ApiError.java:54-56`), which sets `ApiError.type = "DOWNSTREAM_BUSINESS"`... no -- sets `type = "DOWNSTREAM_SYSTEM"`. **No confirmed caller was found anywhere in `src/main/java`** in this pass's grep for `downstreamSystem` (the only matches were the factory's own declaration and its sibling `downstreamBusiness` factory, `ApiError.java:49-56`). This factory method appears to be unused today; confirming that with certainty (rather than an absence-of-evidence inference) is listed as an open item for 2b.2.

**(b) `WorkflowErrorType.DOWNSTREAM_SYSTEM` enum constant** (`WorkflowErrorType.java:10`), with four confirmed call sites, each tied to a specific status and endpoint:
- `RegistrationStartWebMapper.java:207` -- `MESSAGE_DELIVERY_FAILED` status on the Registration Start endpoint
- `RegistrationOtpResendWebMapper.java:142` -- `MESSAGE_DELIVERY_FAILED` status on the Registration OTP Resend endpoint
- `RegistrationOtpVerifyWebMapper.java:144` -- `TRUST_LOOKUP_FAILED` status on the Registration OTP Verify endpoint
- `CheckRegistrationUsernameService.java:306` -- `DOWNSTREAM_FAILED` status, paired with `ERROR_DOWNSTREAM_UNAVAILABLE`, on the Registration username-check path

These are the Registration call sites referred to only abstractly in the prior version of this document; they are now identified precisely. **UNVERIFIED:** whether `WorkflowErrorType.DOWNSTREAM_SYSTEM` is ultimately rendered into the actual public JSON `type` field, and by which renderer/mapper class, was not traced this pass (that rendering path is separate from `ApiError` entirely, since `WorkflowErrorType` is a distinct enum from `ApiError.type`'s raw `String`).

**(c) The literal string `"DOWNSTREAM_SYSTEM"` passed directly as the `errorType` argument to `ErrorDescriptor.single(...)`**, bypassing `ApiError` and `WorkflowErrorType` both:
- `RegistrationExceptionMappings.java:31` (the `RegistrationLookupDownstreamException` mapping, documented in section 1 above)
- `ApimExceptionMappings.java:61` (the generic `ApimException` mapping, `ApimExceptionMappings.upstream()`, used by the majority of rows in section 1)

This third path is the one actually exercised by most of the rows in section 1's inventory table (every generic `ApimException` row), making it the highest-traffic of the three exposure mechanisms, not (a) or (b).

**Confirmed non-exposure:** `ApiStatus.DOWNSTREAM_REJECTED`/`ApiStatus.DOWNSTREAM_ERROR` are normalized away by `WorkflowPublicApiStatusNormalizer` before rendering (confirmed in the prior pass), so these two enum values do not themselves reach the client.

**Existing guard test:** `PublicDownstreamStatusIsolationGuardTest` (characterized in the prior pass as scanning only for the literal tokens `ApiStatus.DOWNSTREAM_ERROR`/`ApiStatus.DOWNSTREAM_REJECTED` in `adapter/in/web` source) was not re-dumped this pass to re-confirm its exact scan list; carried over from the earlier investigation, not independently re-verified here.

### Replacement

The existing non-downstream system type is `ApiError.type = "SYSTEM"` (`ApiError.system(...)`, `ApiError.java:45-47`) -- confirmed verbatim from source: `return new ApiError("SYSTEM", code, message, List.of(), DEFAULT_SEVERITY, source);`. This is the exact replacement string for path (c)'s literal `"DOWNSTREAM_SYSTEM"` usages. HTTP status, `ApiStatus`, code and message policy stay unchanged; only the `DOWNSTREAM_SYSTEM` string literal is replaced with `SYSTEM`. Path (b)'s `WorkflowErrorType.DOWNSTREAM_SYSTEM` would need its own replacement enum constant or rendering-path change, not yet designed, since `WorkflowErrorType` is a separate enum from `ApiError.type`.

### Sub-task 2b.2a (runs after 2b.2, before 2b.3)

- Fail-first: change the characterization rows that expect a `DOWNSTREAM_SYSTEM` value (both path (b) and path (c) occurrences) to their replacement; list every row (built from 2b.2's exhaustive characterization pack).
- Replace every public `DOWNSTREAM_*` value; delete `ApiError.downstreamSystem()` if 2b.2 confirms it is genuinely unused, and resolve `WorkflowErrorType.DOWNSTREAM_SYSTEM`'s four call sites.
- Extend `PublicDownstreamStatusIsolationGuardTest`: keep the existing source check; add a behaviour check that renders every exception mapping and every workflow error path and asserts no rendered JSON contains `"DOWNSTREAM"`.
- Commit: `fix(exception-handling): stop exposing downstream error types to clients`

**2b.2a waits for Nick to confirm FE does not depend on any `DOWNSTREAM_*` value.**

## 7. Sub-task plan

- **2b.2:** characterization pack (public status, body, message, log event, level for every inventory row above) on unchanged code. Must also resolve this document's remaining UNVERIFIED items (`ApimEnvelopeSchemaClassifier`'s rule, `ForgotPasswordDetailsOtpProtectedOperation`'s exact catch site, `IdempotencyExecutor`'s handling of `RegistrationCompletionPreDispatchException` after classification, `downstreamErrorCode()`'s implementation, `ApiError.downstreamSystem()`'s true caller count, and whether `WorkflowErrorType.DOWNSTREAM_SYSTEM` reaches the rendered JSON).
- **2b.2a:** as described in section 6, after Nick confirms FE independence from `DOWNSTREAM_*` values.
- **2b.3:** shared pure classifier (names chosen in this document's section 3 rules), branch coverage >= 95%.
- **2b.4..2b.n:** one commit per feature, attaching the classification; feature code and tables unchanged; gate after each.

## 8. Open questions (listed, not decided)

- Public status 502 vs 500 for `ApimException` (`ApimForgotPasswordCompletionAdapter.safeFailure` uses 500; most other sites use 502) -- whether this divergence is intentional or should be unified is open.
- Whether the swallow sites identified in section 1 (Forgot Password crypto-failure, Username Recovery x2, Login x2) should publish a `FailureEvent` (M3 3.7).
- `ApimEnvelopeSchemaClassifier`'s exact classification rule, and therefore which rows get an additional, earlier `apim.contract.invalid`/`apim.business.rejected` log line via `DefaultApimExchangeExecutor`'s `doOnEach`, before any feature-specific adapter logic runs (correction 4) -- genuinely not traced this pass.
- The two swallow sites whose `onErrorResume`/`onErrorReturn` catch broad types indiscriminately (Username Recovery's `ApimException` catch, Login's `TrpGetAccountListAdapter.onErrorReturn`) mix transport and contract failures without distinguishing them; whether to split this at the swallow site or leave it to the shared classifier (2b.3) is open.
- The "unparsable date" sub-case in Reference Date's catch-all (`DOWNSTREAM_CONTRACT` vs `DOWNSTREAM_INVALID_RESPONSE`) is a row-level nuance not resolved here.
- Whether `ApiError.downstreamSystem()` is genuinely unused (zero callers) or whether this pass's grep missed a caller (e.g. via reflection or a name not matching the literal search term) is open; 2b.2 should re-confirm with a broader search.
- `RegistrationLookupDownstreamException.downstreamErrorCode()`'s exact implementation (referenced at `ApimRegistrationLookupAdapter.java:122`) was not read this pass.

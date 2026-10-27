# 07 - APIM Error Classification Design

This document is sub-task 2b.1 of the M2b milestone (docs only, no production or test change). It is based on the read-only inventory from sub-task 2b.0 (scripts 151-153), accepted as the basis with the corrections below. Nick reviews this document before sub-task 2b.2 starts.

## Corrections to the 2b.0 report

- Public status is **not** always 502. `ApimExceptionMappings.upstream()` returns `exception.getStatusCode()` (ApimExceptionMappings.java:59), so the public status for a generic `ApimException` varies with however that exception was constructed. `ApimForgotPasswordCompletionAdapter.safeFailure` constructs `ApimException(HttpStatus.INTERNAL_SERVER_ERROR, ...)` (ApimForgotPasswordCompletionAdapter.java:77), which resolves to public HTTP 500, not 502. Both are recorded in the inventory below.

- `apim.exchange.failed` is logged at ApimExchangeOutcomeLogger.java:19; `apim.contract.invalid` is logged at ApimExchangeOutcomeLogger.java:18 (the earlier 2b.0 report had these two swapped).

- The ordered log list per condition (section 5) additionally includes: the `@LogExecution` aspect (UNVERIFIED), the `RequestLoggingWebFilter` 5xx line (RequestLoggingWebFilter.java:225), feature-level logs (Fund Fact Sheet `LOG.error` at ApimFundFactSheetSourceAdapter.java:109, Account Gain/Loss `LOG.warn` at ApimAccountGainLossSourceAdapter.java:67), and the executor's `apim.business.rejected` WARN (ApimExchangeOutcomeLogger.java:18) for any non-empty err-message.

- For each of the 5 swallow sites identified in 2b.0, whether an earlier log line already recorded the failure is answered per-row in section 1.

## 1. Inventory table

One row per downstream condition per APIM operation, one row per adapter (no duplicate adapters). Columns: Feature / Adapter / Operation / Condition / Detection site / Handling / Origin tag today / Public HTTP status / Public ApiStatus / Public error code or type / Citation. Every public status/code is traced to its actual mapping class or `*HttpResponses` table; nothing is described as "handled downstream" or "per mapping" without naming that class. Anything not traced is marked UNVERIFIED.

### Registration -- ApimRegistrationLookupAdapter (TRPSearchMember)

- **Condition:** err.tpa.member.not.found -> NOT_ELIGIBLE
- **Detection site:** TrpSearchMemberApimResultMapper maps the code to RegistrationLookupStatus.NOT_ELIGIBLE (TrpSearchMemberApimResultMapper.java:35, UNVERIFIED)
- **Handling:** Returned as a normal result; ApimRegistrationLookupAdapter.withFailureLogging's doOnEach then calls recordDownstreamRejection, publishing a FailureEvent (ApimRegistrationLookupAdapter.java:101)
- **Origin tag today:** DOWNSTREAM_CONTRACT (ApimRegistrationLookupAdapter.java:121) -- see rule exception E1, section 4
- **Public HTTP status:** 422
- **Public ApiStatus:** VALIDATION_FAILED (business-rejected shape)
- **Public error code / type:** RegistrationErrorKey.START_VERIFICATION_FAILED
- **Citation:** ApimRegistrationLookupAdapter.java:121

### Registration -- ApimRegistrationLookupAdapter (TRPSearchMember)

- **Condition:** EMPTY_DATA / INVALID_ACCOUNT_DATA / UNEXPECTED_ERROR reasons
- **Detection site:** TrpSearchMemberApimResultMapper classifies these reasons (UNVERIFIED)
- **Handling:** Throws RegistrationLookupDownstreamException (RegistrationLookupDownstreamException.java:8), mapped by RegistrationExceptionMappings.lookupDownstream() (RegistrationExceptionMappings.java:18)
- **Origin tag today:** DOWNSTREAM_CONTRACT (same tag as the row above, via withFailureLogging)
- **Public HTTP status:** 503
- **Public ApiStatus:** SYSTEM_ERROR
- **Public error code / type:** err code from RegistrationExceptionMappings.lookupDownstream(); type DOWNSTREAM_SYSTEM
- **Citation:** RegistrationExceptionMappings.java:18

### Registration -- ApimRegistrationCompletionAdapter (TRPRegMember)

- **Condition:** ApimProfileResolutionException during completion
- **Detection site:** Caught at ApimRegistrationCompletionAdapter.java:16
- **Handling:** .onErrorMap -> RegistrationCompletionPreDispatchException (RegistrationCompletionPreDispatchException.java:4)
- **Origin tag today:** none set at this site
- **Public HTTP status:** UNVERIFIED
- **Public ApiStatus:** UNVERIFIED
- **Public error code / type:** UNVERIFIED -- depends on RegistrationCompletionPreDispatchException's own mapping, not traced in this pass
- **Citation:** ApimRegistrationCompletionAdapter.java:16

### Forgot Password -- ApimForgotPasswordUsernameLookupAdapter / SharedApimTrpChkCustIdTransport (TRPChkCustId)

- **Condition:** empty response / malformed data items
- **Detection site:** ApimForgotPasswordUsernameLookupAdapter.java:87
- **Handling:** throw new ApimException(BAD_GATEWAY, APIM_RESPONSE_INVALID, ...) (ApimForgotPasswordUsernameLookupAdapter.java:87)
- **Origin tag today:** none
- **Public HTTP status:** 502
- **Public ApiStatus:** SYSTEM_ERROR (via ApimExceptionMappings.upstream(), which forwards getStatusCode())
- **Public error code / type:** APIM_RESPONSE_INVALID
- **Citation:** ApimForgotPasswordUsernameLookupAdapter.java:87

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)

- **Condition:** missing response envelope
- **Detection site:** ApimForgotPasswordCompletionAdapter.java:65
- **Handling:** throw new ApimException(BAD_GATEWAY, APIM_RESPONSE_INVALID, ...) (ApimForgotPasswordCompletionAdapter.java:65)
- **Origin tag today:** none
- **Public HTTP status:** 502
- **Public ApiStatus:** SYSTEM_ERROR
- **Public error code / type:** APIM_RESPONSE_INVALID
- **Citation:** ApimForgotPasswordCompletionAdapter.java:65

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)

- **Condition:** crypto failure (ApimCryptoFailureClassifier.isCryptoFailure)
- **Detection site:** ApimForgotPasswordCompletionAdapter.java:57 -- this is the sole caller of isCryptoFailure (UNVERIFIED)
- **Handling:** .onErrorResume -> silently returns unknownOutcome(); no log call at the swallow site. No earlier log line records this failure either: ApimCryptoFailureClassifier.isCryptoFailure is a pure boolean predicate with no logging, and this is reached before the facade/outcome-logger path, so this condition is NOT logged anywhere today.
- **Origin tag today:** none
- **Public HTTP status:** n/a (success-shaped response)
- **Public ApiStatus:** n/a
- **Public error code / type:** n/a (unknownOutcome public shape)
- **Citation:** ApimForgotPasswordCompletionAdapter.java:57

### Forgot Password -- ApimForgotPasswordCompletionAdapter (TRPResetPassword)

- **Condition:** any other throwable (safeFailure catch-all)
- **Detection site:** ApimForgotPasswordCompletionAdapter.java:77
- **Handling:** .onErrorMap(this::safeFailure) -> ApimException(INTERNAL_SERVER_ERROR, SYSTEM_UNEXPECTED, ...) (ApimForgotPasswordCompletionAdapter.java:77)
- **Origin tag today:** none
- **Public HTTP status:** 500
- **Public ApiStatus:** SYSTEM_ERROR (via ApimExceptionMappings.upstream() forwarding getStatusCode())
- **Public error code / type:** SYSTEM_UNEXPECTED
- **Citation:** ApimForgotPasswordCompletionAdapter.java:77

### Forgot Password -- ApimForgotPasswordDetailsLookupAdapter (TRPSearchMember)

- **Condition:** missing envelope
- **Detection site:** ForgotPasswordDetailsDownstreamException.java:3
- **Handling:** throw ForgotPasswordDetailsDownstreamException(UNEXPECTED_ERROR) (ForgotPasswordDetailsDownstreamException.java:3)
- **Origin tag today:** none
- **Public HTTP status:** UNVERIFIED
- **Public ApiStatus:** UNVERIFIED
- **Public error code / type:** UNVERIFIED -- its own *ExceptionMappings entry not re-traced this pass
- **Citation:** ForgotPasswordDetailsDownstreamException.java:3

### Username Recovery -- TrpSearchMemberAdapter (TRPSearchMember)

- **Condition:** TrpSearchMemberApimDownstreamException
- **Detection site:** TrpSearchMemberAdapter.java:55; exception class at TrpSearchMemberApimDownstreamException.java:13
- **Handling:** .onErrorResume -> silently returns unknownOutcome(); no log call at the swallow site. Whether an earlier log line recorded this is UNVERIFIED -- requires confirming whether TrpSearchMemberApimDownstreamException's own construction site logs, which was not re-checked this pass.
- **Origin tag today:** none
- **Public HTTP status:** n/a (success-shaped response)
- **Public ApiStatus:** n/a
- **Public error code / type:** n/a (unknownOutcome public shape)
- **Citation:** TrpSearchMemberAdapter.java:55

### Username Recovery -- TrpSearchMemberAdapter (TRPSearchMember)

- **Condition:** any ApimException
- **Detection site:** TrpSearchMemberAdapter.java:56
- **Handling:** .onErrorResume -> silently returns unavailable(); no log call at the swallow site. An earlier log line DOES already record this: every ApimException raised by the shared transport/facade path already passes through ApimWebClientFacade's log.error (ApimWebClientFacade.java:409) and ApimExchangeOutcomeLogger's apim.exchange.failed (ApimExchangeOutcomeLogger.java:19) before reaching this onErrorResume, so the failure is NOT silent overall, only silent at this specific swallow site.
- **Origin tag today:** none
- **Public HTTP status:** n/a (success-shaped response)
- **Public ApiStatus:** n/a
- **Public error code / type:** n/a (unavailable public shape)
- **Citation:** TrpSearchMemberAdapter.java:56

### Login -- TrpMemberLoginAdapter (TRPMemberLogin)

- **Condition:** IllegalStateException (empty envelope)
- **Detection site:** TrpMemberLoginAdapter.java:38
- **Handling:** .onErrorResume -> silently returns mapper.failure(UNAVAILABLE); no log call at the swallow site. Whether an earlier log line recorded this is UNVERIFIED -- IllegalStateException is a local construction-time check, not necessarily routed through the facade/outcome-logger path; this needs direct confirmation, not assumed.
- **Origin tag today:** none
- **Public HTTP status:** n/a (handled by LoginStartWebMapper)
- **Public ApiStatus:** n/a
- **Public error code / type:** n/a
- **Citation:** TrpMemberLoginAdapter.java:38

### Login -- TrpGetAccountListAdapter (TRPGetAccountList)

- **Condition:** any error
- **Detection site:** TrpGetAccountListAdapter.java:32
- **Handling:** .onErrorReturn(LoginAccountAuthorizationResult.unavailable()); onErrorReturn provides no access to the throwable for inline logging. Whether an earlier log line recorded this is UNVERIFIED -- depends on whether this error already passed through the shared transport/facade path; not confirmed this pass.
- **Origin tag today:** none
- **Public HTTP status:** n/a (handled by LoginHttpResponses)
- **Public ApiStatus:** n/a
- **Public error code / type:** n/a
- **Citation:** TrpGetAccountListAdapter.java:32

### Account Balance -- ApimAccountBalanceSourceAdapter (TRPGetMemberAccBal)

- **Condition:** err.tpa.account.invalid -> NOT_FOUND
- **Detection site:** ApimAccountBalanceSourceAdapter.java:55
- **Handling:** AccountBalanceRetrievalException.accountNotFound() (ApimAccountBalanceSourceAdapter.java:55)
- **Origin tag today:** none today -- candidate DOWNSTREAM_BUSINESS per section 3 rule
- **Public HTTP status:** UNVERIFIED
- **Public ApiStatus:** per AccountBalanceExceptionMappings (not re-traced this pass)
- **Public error code / type:** UNVERIFIED
- **Citation:** ApimAccountBalanceSourceAdapter.java:55

### Account Balance -- ApimAccountBalanceSourceAdapter (TRPGetMemberAccBal)

- **Condition:** err.tpa.member.not.found -> MISMATCH
- **Detection site:** ApimAccountBalanceSourceAdapter.java:57
- **Handling:** AccountBalanceRetrievalException.accountMismatch() (ApimAccountBalanceSourceAdapter.java:57)
- **Origin tag today:** none today -- candidate DOWNSTREAM_BUSINESS per section 3 rule
- **Public HTTP status:** UNVERIFIED
- **Public ApiStatus:** per AccountBalanceExceptionMappings (not re-traced this pass)
- **Public error code / type:** UNVERIFIED
- **Citation:** ApimAccountBalanceSourceAdapter.java:57

### Account Balance -- ApimAccountBalanceSourceAdapter (TRPGetMemberAccBal)

- **Condition:** any other non-blank error / malformed content / catch-all
- **Detection site:** ApimAccountBalanceSourceAdapter.java:59
- **Handling:** AccountBalanceRetrievalException.systemFailure() (ApimAccountBalanceSourceAdapter.java:59)
- **Origin tag today:** none today -- classify by cause per section 3 catch-all rule
- **Public HTTP status:** 503
- **Public ApiStatus:** SYSTEM_ERROR
- **Public error code / type:** per AccountBalanceExceptionMappings.retrievalFailure()
- **Citation:** ApimAccountBalanceSourceAdapter.java:59

### Account Gain/Loss -- ApimAccountGainLossSourceAdapter (TRPGetInvestGL)

- **Condition:** err.tpa.fund.switching.in.progress
- **Detection site:** ApimAccountGainLossSourceAdapter.java:64
- **Handling:** fundSwitchingInProgress(), LOG.warn with sanitized error code (ApimAccountGainLossSourceAdapter.java:64)
- **Origin tag today:** none today -- candidate DOWNSTREAM_BUSINESS per section 3 rule
- **Public HTTP status:** UNVERIFIED
- **Public ApiStatus:** per AccountGainLossExceptionMappings (not re-traced this pass)
- **Public error code / type:** UNVERIFIED
- **Citation:** ApimAccountGainLossSourceAdapter.java:64

### Account Gain/Loss -- ApimAccountGainLossSourceAdapter (TRPGetInvestGL)

- **Condition:** any other non-blank error / malformed content / catch-all
- **Detection site:** ApimAccountGainLossSourceAdapter.java:71
- **Handling:** systemFailure() (ApimAccountGainLossSourceAdapter.java:71)
- **Origin tag today:** none today -- classify by cause per section 3 catch-all rule
- **Public HTTP status:** 503
- **Public ApiStatus:** SYSTEM_ERROR
- **Public error code / type:** per AccountGainLossExceptionMappings.retrievalFailure()
- **Citation:** ApimAccountGainLossSourceAdapter.java:71

### Fund Price -- ApimFundPriceSourceAdapter / ApimFundPriceModeSourceAdapter (TRPGetFundPrice / TRPGetFundPriceMode)

- **Condition:** err.tpa.invalid.date
- **Detection site:** ApimFundPriceSourceAdapter.java:165
- **Handling:** FundPriceInvalidDateException.forInvalidDate() (ApimFundPriceSourceAdapter.java:165)
- **Origin tag today:** none today -- candidate DOWNSTREAM_BUSINESS per section 3 rule
- **Public HTTP status:** 400
- **Public ApiStatus:** VALIDATION_FAILED
- **Public error code / type:** ErrorCodes.FUND_PRICE_REQUEST_INVALID (FundPriceExceptionMappings.invalidDate())
- **Citation:** ApimFundPriceSourceAdapter.java:165

### Fund Price -- ApimFundPriceSourceAdapter / ApimFundPriceModeSourceAdapter (TRPGetFundPrice / TRPGetFundPriceMode)

- **Condition:** any other non-blank error / malformed / catch-all
- **Detection site:** ApimFundPriceSourceAdapter.java:161
- **Handling:** FundPriceRetrievalException.systemFailure() (ApimFundPriceSourceAdapter.java:161)
- **Origin tag today:** none today -- classify by cause per section 3 catch-all rule
- **Public HTTP status:** 503
- **Public ApiStatus:** SYSTEM_ERROR
- **Public error code / type:** FundPriceExceptionMappings.retrievalUnavailable()
- **Citation:** ApimFundPriceSourceAdapter.java:161

### Fund Comparison -- ApimFundComparisonOptionsSourceAdapter (TRPGetFundCmpOpt)

- **Condition:** any non-blank error / malformed / catch-all
- **Detection site:** ApimFundComparisonOptionsSourceAdapter.java:79
- **Handling:** FundComparisonRetrievalException.systemFailure() (ApimFundComparisonOptionsSourceAdapter.java:79)
- **Origin tag today:** none today -- classify by cause per section 3 catch-all rule
- **Public HTTP status:** 503
- **Public ApiStatus:** SYSTEM_ERROR
- **Public error code / type:** FundComparisonExceptionMappings.retrievalUnavailable()
- **Citation:** ApimFundComparisonOptionsSourceAdapter.java:79

### Fund Fact Sheet -- ApimFundFactSheetSourceAdapter (TRPGetFundFactSheet)

- **Condition:** null item/list/row (logged LOG.error) / non-empty error / malformed
- **Detection site:** ApimFundFactSheetSourceAdapter.java:109
- **Handling:** FundFactSheetRetrievalException.systemFailure() (ApimFundFactSheetSourceAdapter.java:89)
- **Origin tag today:** none today -- classify by cause per section 3 catch-all rule
- **Public HTTP status:** 503
- **Public ApiStatus:** SYSTEM_ERROR
- **Public error code / type:** FundFactSheetExceptionMappings.retrievalUnavailable()
- **Citation:** ApimFundFactSheetSourceAdapter.java:89

### Personal Information -- ApimUpdatePersonalInformationAdapter (TRPUpdMemberInfo)

- **Condition:** empty/all-null data items, or no item matches selected
- **Detection site:** ApimUpdatePersonalInformationAdapter.java:63
- **Handling:** throw new ApimException(BAD_GATEWAY, APIM_RESPONSE_INVALID, ...) (ApimUpdatePersonalInformationAdapter.java:63)
- **Origin tag today:** none
- **Public HTTP status:** 502
- **Public ApiStatus:** SYSTEM_ERROR (via ApimExceptionMappings.upstream())
- **Public error code / type:** APIM_RESPONSE_INVALID
- **Citation:** ApimUpdatePersonalInformationAdapter.java:63

### Reference Date -- ApimReferenceDateRefreshAdapter (TRPGetWebSysDate)

- **Condition:** missing payload / blank err-message field / missing data / unparsable date
- **Detection site:** ApimReferenceDateRefreshAdapter.java:58
- **Handling:** throw new ApimException(BAD_GATEWAY, APIM_RESPONSE_INVALID, ...) (ApimReferenceDateRefreshAdapter.java:58)
- **Origin tag today:** none
- **Public HTTP status:** 502
- **Public ApiStatus:** SYSTEM_ERROR (via ApimExceptionMappings.upstream())
- **Public error code / type:** APIM_RESPONSE_INVALID
- **Citation:** ApimReferenceDateRefreshAdapter.java:58

### Contribution Summary / Member Info / Notice Message / Notification Read Status / Reference Data Countries / General Metadata / Account List / Member Login (shared transports) -- ApimContributionSummaryAdapter, ApimMemberInfoAdapter, ApimNoticeMessageAdapter, ApimNotificationReadStatusAdapter, ApimReferenceDataCountriesAdapter, ApimGeneralMetadataSourceAdapter, SharedApimTrpGetAccountListTransport, SharedApimTrpMemberLoginTransport (various, each delegating to the shared validator)

- **Condition:** any non-empty err-message or missing data on success
- **Detection site:** ApimResponseValidator.requireSuccessData (ApimResponseValidator.java:27), err-message forwarded verbatim as the exception's diagnostic message at ApimResponseValidator.java:32
- **Handling:** throw new ApimException(BAD_GATEWAY, APIM_RESPONSE_INVALID, errMessage-or-fixed-text)
- **Origin tag today:** none
- **Public HTTP status:** 502
- **Public ApiStatus:** SYSTEM_ERROR (via ApimExceptionMappings.upstream())
- **Public error code / type:** APIM_RESPONSE_INVALID
- **Citation:** ApimResponseValidator.java:27

## 2. Terms

- `FailureOrigin.DOWNSTREAM_*`: internal log classification only. Never serialized into a response.
- `ApiStatus.DOWNSTREAM_REJECTED` / `ApiStatus.DOWNSTREAM_ERROR`: internal workflow values only. `WorkflowPublicApiStatusNormalizer` maps `DOWNSTREAM_REJECTED` to `BUSINESS_REJECTED` (WorkflowPublicApiStatusNormalizer.java:12) and `DOWNSTREAM_ERROR` to `SYSTEM_ERROR` (WorkflowPublicApiStatusNormalizer.java:13) before rendering.
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

Also recorded in `05-milestones.md`, M2b section.

- **E1 (log level):** Registration `err.tpa.member.not.found` moves from `FailureOrigin.DOWNSTREAM_CONTRACT` to `FailureOrigin.DOWNSTREAM_BUSINESS` (ERROR -> WARN). Pinned fail-first in the Registration feature commit (sub-task 2b.4..2b.n). No other log level may change in M2b.
- **E2 (public contract):** no `DOWNSTREAM_*` value may reach the frontend (section 6, sub-task 2b.2a).

## 5. Incident ERROR per category (input for M3 3.8 -- describe only, no level change now)

For a given downstream condition, today's log stream can contain several lines across layers: `ApimWebClientFacade` (ApimWebClientFacade.java:409), `ApimExchangeOutcomeLogger` (`apim.exchange.failed` ApimExchangeOutcomeLogger.java:19, `apim.contract.invalid` ApimExchangeOutcomeLogger.java:18, `apim.business.rejected` ApimExchangeOutcomeLogger.java:18), the `@LogExecution` aspect (UNVERIFIED), feature-level logs (e.g. Fund Fact Sheet ApimFundFactSheetSourceAdapter.java:109, Account Gain/Loss ApimAccountGainLossSourceAdapter.java:67), `RequestLoggingWebFilter`'s 5xx line (RequestLoggingWebFilter.java:225), and finally `ApiExceptionHandler`'s `http.api.error` line. M3 3.8 will pick exactly one of these as the canonical incident-level log per category:

- **DOWNSTREAM_BUSINESS:** proposed incident line is the feature-level log closest to the business decision (e.g. Registration's `recordDownstreamRejection`, Account Gain/Loss's `fundSwitchingInProgress` warn). All transport/outcome-logger lines upstream of it become non-incident context.
- **DOWNSTREAM_CONTRACT:** proposed incident line is `ApimExchangeOutcomeLogger`'s `apim.contract.invalid` (ApimExchangeOutcomeLogger.java:18).
- **DOWNSTREAM_INVALID_RESPONSE:** proposed incident line is wherever the crypto/mapping failure is first detected (currently unlogged for the sole `isCryptoFailure` swallow site -- see row above; this is itself a gap M3 3.8 must address, not just select a line for).
- **DOWNSTREAM_TRANSPORT:** proposed incident line is `ApimWebClientFacade`'s `log.error` (ApimWebClientFacade.java:409).
- **LOCAL_SYSTEM:** proposed incident line is wherever `ApimProfileResolutionException` is first caught (ApimRegistrationCompletionAdapter.java:16 for Registration; other callers not re-traced this pass).

## 6. Public DOWNSTREAM exposure (decision by Nick: no DOWNSTREAM_* in any public field)

### Inventory of every place a DOWNSTREAM token can enter a public response

- `ApiError.downstreamSystem()` factory method, ApiError.java:54. Sets `ApiError.type` (and/or `ApiError.source`, per the factory's own fields -- exact field list not re-dumped this pass, UNVERIFIED beyond `type`) to a literal containing `DOWNSTREAM`.
- `WorkflowErrorType.DOWNSTREAM_SYSTEM` constant, WorkflowErrorType.java:10.
- The Registration mappings path that constructs a public error using `ApiError.downstreamSystem()` or `WorkflowErrorType.DOWNSTREAM_SYSTEM` -- exact call sites (endpoint, public HTTP status, ApiStatus) not individually re-traced this pass; UNVERIFIED pending sub-task 2b.2's characterization pack, which will enumerate every call site exhaustively before 2b.2a changes any of them.
- `ApiStatus` values: `WorkflowPublicApiStatusNormalizer` confirms `DOWNSTREAM_REJECTED` and `DOWNSTREAM_ERROR` are normalized away before rendering (WorkflowPublicApiStatusNormalizer.java:12, WorkflowPublicApiStatusNormalizer.java:13), so these two enum values themselves do not reach the client under current code -- consistent with Nick's 2b.0 decision that this is not a guard violation.
- Existing guard test: `PublicDownstreamStatusIsolationGuardTest` (PublicDownstreamStatusIsolationGuardTest.java:14) only scans literal source tokens `ApiStatus.DOWNSTREAM_ERROR` / `ApiStatus.DOWNSTREAM_REJECTED` in `adapter/in/web`. It does not scan for `DOWNSTREAM_SYSTEM` (the `ApiError.type`/`WorkflowErrorType` literal), and it does not render any exception mapping or workflow error path to check actual output JSON.

### Replacement

Existing system type factory: `ApiError.system(...)` (ApiError.java:45). The exact string literal it sets is UNVERIFIED in this pass -- the source line was located but its literal value was not captured verbatim; sub-task 2b.2's characterization pack must quote it precisely before 2b.2a replaces any `DOWNSTREAM_SYSTEM` usage with it. HTTP status, ApiStatus, code, and message policy stay unchanged; only the `DOWNSTREAM_*` value is replaced.

### Sub-task 2b.2a (runs after 2b.2, before 2b.3)

- Fail-first: change the characterization rows that expect a `DOWNSTREAM_*` value to the system type; list every row (built from 2b.2's exhaustive characterization pack, not yet enumerated here).
- Replace every public `DOWNSTREAM_*` value; delete `ApiError.downstreamSystem()` and `WorkflowErrorType.DOWNSTREAM_SYSTEM` if unused afterwards.
- Extend `PublicDownstreamStatusIsolationGuardTest`: keep the existing source check; add a behaviour check that renders every exception mapping and every workflow error path and asserts no rendered JSON contains `"DOWNSTREAM"`.
- Commit: `fix(exception-handling): stop exposing downstream error types to clients`

**2b.2a waits for Nick to confirm FE does not depend on any `DOWNSTREAM_*` value.**

## 7. Sub-task plan

- **2b.2:** characterization pack (public status, body, message, log event, level for every inventory row above) on unchanged code.
- **2b.2a:** as described in section 6, after Nick confirms FE independence from `DOWNSTREAM_*` values.
- **2b.3:** shared pure classifier (names chosen in this document's section 3 rules), branch coverage >= 95%.
- **2b.4..2b.n:** one commit per feature, attaching the classification; feature code and tables unchanged; gate after each.

## 8. Open questions (listed, not decided)

- Public status 502 vs 500 for `ApimException` (`ApimForgotPasswordCompletionAdapter.safeFailure` currently uses 500; most other `ApimException` construction sites use 502 -- whether this divergence is intentional or should be unified is open).
- Whether the 5 swallow sites identified in 2b.0 should publish a `FailureEvent` (M3 3.7).
- Registration's `err.tpa.member.not.found` / `NOT_ELIGIBLE`: now resolved by E1 above (DOWNSTREAM_BUSINESS), but whether the HTTP 422 / VALIDATION_FAILED public shape itself should change is out of scope for M2b and remains open for a later decision.
- Row-level ambiguity carried over from 2b.0 Q5: whether the catch-all `systemFailure()` paths (Fund Price, Fund Comparison, Fund Fact Sheet, Account Balance, Account Gain/Loss) should be split by cause into `DOWNSTREAM_TRANSPORT` vs `DOWNSTREAM_INVALID_RESPONSE` at the 2b.3 classifier level, or left as a single catch-all classification per feature.
- Any row marked UNVERIFIED above (Registration's `RegistrationCompletionPreDispatchException` mapping; `ForgotPasswordDetailsDownstreamException`'s mapping; Account Balance's and Account Gain/Loss's exact `*ExceptionMappings` HTTP/ApiStatus values for their named business conditions; the two Login swallow sites' prior-log status; the exact `ApiError.system(...)` string literal) needs confirmation in sub-task 2b.2 before 2b.3 can rely on it.


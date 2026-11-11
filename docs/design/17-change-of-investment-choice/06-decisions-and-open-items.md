# 06 Decisions and open items

## Decisions (confirmed by Nick)

| Id | Decision |
|---|---|
| D-01 | Design against BRD V0.3; unresolved BRD threads are open items |
| D-02 | Account Number = `{formatted-policyNo}-{formatted-certNo}` via `AccountIdentifierFormatter` (Java) |
| D-03 | Member only; role from `ActorContext.actorUserRole` (`toApimValue()`); employer portal out |
| D-04 | Print, leave warning, stepper, Account Balance widget, e-notification: frontend / out of BFF |
| D-05 | Progress returns latest 5 records; frontend handles filter options; "Latest" = latest effective-date group |
| D-06 | Dates `dd/MM/yyyy` end to end; English fund name mandatory and always present (downstream confirmed 2026-11-06), no new fallback rule |
| D-07 | Pending items not in history; returned separately |
| D-08 | CIM type not handled by the BFF |
| D-09 | Eligibility, termination, duplicate/pending rules, cut-off: Progress |
| D-10 | Java returns codes only; frontend owns labels and messages |
| D-11 | One GET endpoint (details, funds, history, pending) from TRPGetCIMDtl |
| D-12 | Java sorts funds: risk ascending (blank highest), then display name per Accept-Language |
| D-13 | `interval-pcnt` from Progress; `InvestMandate.pcnt` string, `PendingReqDtl.pcnt` integer |
| D-14 | `success:false` handling is not part of this branch; Nick's separate branch |
| D-15 | No `Idempotency-Key`; the single-use password token prevents resubmission |
| D-16 | Password via `POST /api/v1/password-validation`, then the token header on submit; no OTP; strict issuance validation |
| D-17 | Unknown outcome: one TRPGetCIMDtl call, match today's pending by exact detail set |
| D-18 | All TRPGetCIMDtl / TRPCIMReq `err-message` values -> 503 |
| D-19 | Superseded by D-38 to D-43 (Nick, 2026-11-07): the cancel endpoint is implemented now and needs a password token |
| D-20 | Errors via the exception-management capability, per feature |
| D-21 | Submit validates against the snapshot: interval multiple, source `avail-sw` true, fund `avail-sw` true |
| D-22 | One atomic TRPCIMReq for all selected sources; the snapshot holds interval, sources, funds and (D-40) the pending reference numbers, plus ownership keys |
| D-23 | Snapshot miss on submit -> 503 + one incident record |
| D-24 | Eligibility codes: reserved 422 slot, empty until Progress defines codes |
| D-25 | TRPCIMReq has no `ref-date`, no `package-code`; PendingReq carries `submit-date` + `submit-time` |
| D-26 | Public envelope is the existing `MutationResponse`; `ApiError` `message: null`, `severity: ERROR`, `source: SERVER`; system errors `SYSTEM`, `targets: []`; no `nextStep` |
| D-27 | Submit success status is `SUBMITTED` |
| D-28 | Validation targets are request property paths; types `FIELD`, `CROSS_FIELD` (total 100), `FORM` (malformed body) |
| D-29 | Snapshot stored in session `featureState` key `investment-choice.snapshot`, mirroring `PersonalInformationConfigCacheService` (E1) |
| D-30 | Submit validation failures return HTTP 422 via controller override, as Personal Information (E2) |
| D-31 | Snapshot write is best effort; a failed write never fails the GET (F3 = a) |
| D-32 | Fund ordering and display name in CIM's web mapper; reuse `FundPriceRiskLevels`; Fund Price not refactored (E4) |
| D-33 | Local `private static final` strict `dd/MM/uuuu` and `HH:mm:ss` formatters in CIM mappers (E5) |
| D-34 | Nonblank `err-message` is published with origin `DOWNSTREAM_CONTRACT` (ERROR) (F2 = a) |
| D-35 | Malformed submit body code `err.investment-choice.request.invalid` (F4 = a; supersedes the generic code in the earlier D-28 text) |
| D-36 | CIM uses `ApimFailureClassifier` at its adapters and owns one incident record per failure through `FailureEventPort`; its exception mappings log below ERROR; no direct ERROR calls (E3, F1 = A). CIM has no exception-handling M3 slice |
| D-37 | `SwitchSrc`: hidden sources are omitted by Progress; `avail-sw` Boolean, true = editable, false = read-only; downstream confirmed 2026-11-06, closes O-1 |
| D-38 | Cancel operation `TRPCnclCIMReq` with `login-user-id`, `login-user-role`, `policy-no`, `cert-no`, `ref-no`; response `data[0].success` mapped, not inspected (D-14); NON_RETRYABLE, never resubmitted; config key `apim.investment-choice.cancel.path` |
| D-39 | Cancel endpoint `POST /api/v1/investment-choice/instructions/cancel` with body `{"refNo": "..."}`; password-confirmed with purpose `CANCEL_INVESTMENT_CHOICE`, the body is the `operationPayload` (token bound to the reference); no path variable |
| D-40 | The GET snapshot also stores the pending `ref-no` values; cancel validates `refNo` against them. Snapshot miss -> 503; reference absent -> 422 `...cancel.ref-invalid` with no downstream call; after a successful cancel the reference is removed best effort |
| D-41 | Cancel outcomes: `err.request.cannot.be.cancelled` -> 422 `BUSINESS_REJECTED` `...cancel.not-cancellable` (WARN, `DOWNSTREAM_BUSINESS`); every other `err-message` and every throwable -> 503 `...cancel.unavailable` (`DOWNSTREAM_CONTRACT` or classified); no reconciliation |
| D-42 | Cancel success returns the validated requested reference in `result.data.referenceNo`; any `ref-no` in the downstream response is ignored |
| D-43 | Progress enforces that the reference belongs to the member; Java does not call `TRPGetCIMDtl` before cancelling. The snapshot membership check is an additional Java-side guard |
| D-44 | Progress owns scenario selection. The internal scenarios are `NORMAL`, `FUNCTION_UNAVAILABLE`, and `MEMBER_INELIGIBLE`. The provisional downstream field is `scenario` beside `interval-pcnt`; its actual name and wire values are unconfirmed. No configuration mapping; absent temporarily means NORMAL. The exact string literals accepted by the implementation are synthetic provisional values, not confirmed Progress values. Present null, non-string, blank, or unknown values fail as malformed; isolate translation in the APIM mapper and remove the missing-field fallback after the mandatory contract is confirmed and rolled out |
| D-45 | NORMAL retains existing mandatory fields and response behavior, adding `scenario`. Restricted scenarios require `SwitchFund` metadata and `InvestMandate`, permit absent `interval-pcnt`/`SwitchSrc`, and expose only `scenario`, `accountNumber`, and `history`; the other GET properties are absent, even if supplied downstream. Preserve history enrichment, ordering, unknown-fund behavior, and no new nonempty-list requirements |
| D-46 | Store scenario with account ownership. NORMAL retains existing authorization data; restricted state replaces the prior account snapshot without fabricated editable data. Keep existing storage/concurrency behavior; no CAS redesign or GET fencing, and no stronger consistency claim |
| D-47 | Submit and cancel issuance inspect stored scenario and reject restricted scenarios before throttle/password/grant: `FUNCTION_UNAVAILABLE` -> 422 `BUSINESS_REJECTED`, type `BUSINESS`, `err.investment-choice.function-unavailable`; `MEMBER_INELIGIBLE` -> corresponding `err.investment-choice.member-ineligible`. Empty targets, null message, existing severity/source; no technical incident. Ordinary snapshot misses retain the existing feature-owned 503. No execution-time scenario check or change to issued-token semantics |
| D-48 | Submit precedence: V1 remains immediate without snapshot read; structurally safe V violations win over a restricted scenario and suppress normal-only S rules. With no V violations, restricted state rejects; NORMAL retains V/S aggregation and snapshot-miss precedence. Cancel CV1/CV2 remain early; a valid cancel payload sees restricted-state rejection before pending-reference membership |

## Open items

| Id | Item | Owner | Blocks |
|---|---|---|---|
| O-2 | Progress scenario-selection contract: actual downstream field name and wire values, including any `err-message` mapping | Progress / BA | No |
| O-4 | BRD vs Config Matrix 6.5.14: BRD blocks F01/F13/F17/F22, matrix defaults F01 not blocking and only F17 always blocks. BFF unaffected | BA / Progress | No |
| O-5 | History or pending fund code absent from `SwitchFund` -> `fundName: null`. Confirm Progress always includes closed funds | Progress | No |
| O-6 | Progress sample dates still ISO | Progress | M2 live test |
| O-7 | Pending items have no effective date after the downstream change; confirm the BRD screen needs none | BA | No |
| O-9 | Shared executor still logs `apim.exchange.failed` at ERROR, so a CIM transport failure has two ERROR lines until exception-handling M3 3.8 | Exception-handling M3 | No |
| O-10 | Non-matched path variants (trailing slash, case) render 404 as 500 `err.system.unexpected` via the shared exception handler. Shared web behaviour, not CIM-specific; re-check at 4.4 with the real controller | Nick / shared web | No |
| O-11 | A non-empty body with a missing or `text/plain` Content-Type is rejected before the use case and rendered as HTTP 500 (same behaviour as Personal Information). Shared web behaviour | Nick / shared web | No |
| O-12 | At controller level, duplicate JSON member names collapse to the last member; the token gate (strict canonicalizer, 400 `err.request.invalid`) is the authority on body integrity, so production requests never reach the controller with duplicates | Nick | No |
| O-13 | A reference created by a definite submit success is not in the snapshot until the next GET (only a reconciled success refreshes it), so cancelling it needs a GET first. Decide whether a definite submit success should also refresh the snapshot | Nick | No |
| O-14 | Confirm the provisional downstream scenario field name and wire values before removing the missing-field-to-NORMAL compatibility fallback | Progress / BA | No |

## M6 purpose-owned prechecks (approved design; implemented, not runtime-verified against Progress)

The following decisions are approved target behavior. They do not rewrite the
M5 implementation evidence or imply runtime verification.

| Decision | Approved target |
|---|---|
| Purpose dispatch | No additional feature-validation HTTP request. Shared password-token issuance delegates through registered purpose-specific payload decoders and framework-free application precheck policies. Adding a purpose requires explicit purpose/route/bean registration, not a branch in the shared parser or issuance algorithm. |
| Composition | Missing, duplicate or mismatched decoder/policy registration fails composition. The minimal representation preserves member presence, JSON kinds, unknown member names and values; it is not a general JSON framework. Jackson remains in adapters. |
| Precheck timing | CIM submit V1-V8/S1-S3 and cancel CV1-CV2/pending-reference membership run once during issuance, before throttle/password verification. Feature rules are not repeated at execution. |
| Issuance errors | CIM feature-payload/snapshot-rule violations return HTTP 422 `VALIDATION_FAILED` with detailed existing CIM codes/targets. Generic envelope errors retain 400; wrong-password response is unchanged. |
| Snapshot failure ownership | CIM snapshot miss returns feature-specific 503 `SYSTEM_ERROR` and exactly one feature-owned `LOCAL_SYSTEM` `FailureEvent`. Expected violations produce no technical incident; shared issuance must not publish a duplicate event. |
| Trusted execution handoff | Reuse the existing gate binding attribute. A `PayloadDigest` alone is not authorization. Trust comes from successful gate scope/token consumption and canonicalizer-based body-binding checks before dispatch. CIM controllers and commands require the attribute; missing binding/rejected gate prevents dispatch. |
| Execution and current state | Preserve strict decoding, typed construction, single-use authorization, and session/account/purpose/exact-payload binding. Submit reconciliation and post-success updates remain unchanged. Progress retains existing current-state enforcement; do not assume it repeats every Java rule. A token does not reserve business state. |
| Cancel cleanup | No snapshot precheck or membership revalidation at cancel execution. Snapshot access for post-success cleanup is best effort; missing/unreadable state or cleanup failure cannot block cancellation or turn downstream success into failure. |
| Compatibility/scope | Personal Information issuance and PUT behavior remain unchanged. Token scope, lifetime, consumption and canonicalization remain unchanged. No legacy-token migration, grant versioning, rolling-deployment handling, expiry waiting, snapshot concurrency redesign, CAS refactor or GET fencing. |

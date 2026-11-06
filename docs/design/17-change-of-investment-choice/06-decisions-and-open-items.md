# 06 Decisions and open items

## Decisions (confirmed by Nick)

| Id | Decision |
|---|---|
| D-01 | Design against BRD V0.3; unresolved BRD threads are open items |
| D-02 | Account Number = `{formatted-policyNo}-{formatted-certNo}` via `AccountIdentifierFormatter` (Java) |
| D-03 | Member only; role from `ActorContext.actorUserRole` (`toApimValue()`); employer portal out |
| D-04 | Print, leave warning, stepper, Account Balance widget, e-notification: frontend / out of BFF |
| D-05 | Progress returns latest 5 records; frontend handles filter options; "Latest" = latest effective-date group |
| D-06 | Dates `dd/MM/yyyy` end to end; English fund name mandatory, no new fallback rule |
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
| D-19 | Cancel endpoint designed now, implemented after Progress publishes it; no password |
| D-20 | Errors via the exception-management capability, per feature |
| D-21 | Submit validates against the snapshot: interval multiple, source `avail-sw` true, fund `avail-sw` true |
| D-22 | One atomic TRPCIMReq for all selected sources; snapshot holds interval, sources, funds only (plus ownership keys) |
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

## Open items

| Id | Item | Owner | Blocks |
|---|---|---|---|
| O-1 | `SwitchSrc.avail-sw` meaning. R4 note reads "true -> disable in FE"; C1 rule reads "true -> may submit". Design follows C1 (true = selectable). One mapping test pins it | Progress / Nick | 4.2 merge |
| O-2 | Eligibility `err-message` codes for terminated, Scenario 1/2, function off, maintenance | Progress / BA | No (slot reserved) |
| O-3 | Cancel operation contract | Progress | D.1 only |
| O-4 | BRD vs Config Matrix 6.5.14: BRD blocks F01/F13/F17/F22, matrix defaults F01 not blocking and only F17 always blocks. BFF unaffected | BA / Progress | No |
| O-5 | History or pending fund code absent from `SwitchFund` -> `fundName: null`. Confirm Progress always includes closed funds | Progress | No |
| O-6 | Progress sample dates still ISO | Progress | M2 live test |
| O-7 | Pending items have no effective date after the downstream change; confirm the BRD screen needs none | BA | No |
| O-9 | Shared executor still logs `apim.exchange.failed` at ERROR, so a CIM transport failure has two ERROR lines until exception-handling M3 3.8 | Exception-handling M3 | No |

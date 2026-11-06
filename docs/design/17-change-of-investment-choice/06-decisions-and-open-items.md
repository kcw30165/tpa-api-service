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
| D-12 | Java sorts funds: risk ascending (blank highest), then display name per Accept-Language (Fund Price rule) |
| D-13 | `interval-pcnt` from Progress; `InvestMandate.pcnt` string, `PendingReqDtl.pcnt` integer |
| D-14 | `success:false` handling is not part of this branch; Nick's separate branch |
| D-15 | No `Idempotency-Key`; single-use password token prevents resubmission (B2 option a) |
| D-16 | Password via `POST /api/v1/password-validation` then token header on submit; no OTP; strict issuance validation (O-12) |
| D-17 | Unknown outcome: one TRPGetCIMDtl call, match today's pending by exact detail set |
| D-18 | All TRPGetCIMDtl / TRPCIMReq `err-message` values -> 503 |
| D-19 | Cancel endpoint designed now, implemented after Progress publishes it; no password |
| D-20 | Errors via the new exception-management capability, per feature |
| D-21 | Submit validates against the GET snapshot: interval multiple, source `avail-sw` true, fund `avail-sw` true |
| D-22 | One atomic TRPCIMReq for all selected sources; snapshot holds interval, sources, funds only |
| D-23 | Missing snapshot on submit -> 503 + ERROR log |
| D-24 | Eligibility codes: reserved 422 slot, empty until Progress defines codes (C2 = b) |
| D-25 | TRPCIMReq has no `ref-date`, no `package-code`; PendingReq carries `submit-date` + `submit-time` |

## Open items

| Id | Item | Owner | Blocks |
|---|---|---|---|
| O-1 (B3) | `SwitchSrc.avail-sw` meaning. Nick's R4 note reads "true -> disable in FE"; C1 rule reads "true -> may submit". Design follows C1 (true = selectable). One mapping test pins it; flip only that rule if Progress decides otherwise | Progress / Nick | M4.2 merge |
| O-2 | Eligibility `err-message` codes for terminated, Scenario 1/2, function off, maintenance | Progress / BA | No (slot reserved) |
| O-3 | Cancel operation contract | Progress | D.1 only |
| O-4 | BRD vs Config Matrix 6.5.14: BRD blocks F01/F13/F17/F22, matrix defaults F01 not blocking and only F17 always blocks. BFF unaffected (Progress owns eligibility) | BA / Progress | No |
| O-5 | History or pending fund code absent from `SwitchFund`: returned with `fundName: null`. Confirm Progress always includes closed funds with `avail-sw=false` | Progress | No |
| O-6 | Progress sample dates still ISO (R1) | Progress | M2 live test |
| O-7 | Pending items have no effective date after the downstream change; confirm the BRD screen needs none | BA | No |
| O-8 | Session-scoped cache pattern and TTL for the snapshot, confirmed in sub-task 0 | Nick | M3.3 |

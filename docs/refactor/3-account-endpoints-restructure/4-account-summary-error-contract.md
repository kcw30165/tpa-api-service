# Account Summary Error Contract

## Inherited mappings

| Downstream condition | Public outcome |
| --- | --- |
| `err.tpa.account.invalid` (balance) | 422 BUSINESS_REJECTED / `err.ab.account.not.exist` |
| `err.tpa.member.not.found` (balance) | 422 BUSINESS_REJECTED / `err.ab.account.mismatch` |
| `err.tpa.fund.switching.in.progress` (gain/loss) | 422 BUSINESS_REJECTED / `err.fund.switching.in.progress` |
| Everything else | 503 SYSTEM_ERROR / `err.system.unavailable`, type SYSTEM |

No new error code is introduced. Both existing typed exceptions,
`AccountBalanceRetrievalException` and `AccountGainLossRetrievalException`,
already reach the correct response through `ApiExceptionHandler` and their
respective response policies.

## Partial-failure decision

The summary endpoint does not degrade. If either upstream call fails, the whole
response fails. A summary body containing balances but a silently missing or
zeroed gain/loss section would misrepresent member financial position.

Precedence when both could fail: the balance failure is reported, because the
balance call is the structural base of the payload. Composition is sequential
rather than parallel so this precedence is deterministic. See Q-6 on whether
concurrent execution is wanted for latency.

## Join-failure classification

Duplicate fund codes within either source, and any reconciliation outcome
rejected by the Q-5 decision, are technical contract violations and map to
SYSTEM_FAILURE. They are never surfaced as business rejections.

## Redaction

The composer adds no new log fields. Existing Account Balance and Account
Gain/Loss observability events remain unchanged, and no financial value, fund
code, policy number, or certificate number is logged.

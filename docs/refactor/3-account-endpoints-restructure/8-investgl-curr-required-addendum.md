## InvestGL Currency-Required Addendum

Addendum to the Account Endpoints Restructure and Account Summary design pack.
Does not rewrite 5-decisions-open-questions.md Q-7 or 6-implementation-plan.md;
both carry a forward pointer to this document. This addendum is itself ratified
and supersedes Q-7's "empty string on initial load" assumption for the
TRPGetInvestGL call only.

### Trigger

The downstream TRPGetInvestGL operation now validates curr as required and
rejects an empty value. Q-7 (ratified 21/09/2026) assumed TRPGetInvestGL would
accept curr with the same semantics as TRPGetMemberAccBal: always serialized,
empty string on initial load. That assumption is no longer correct for
TRPGetInvestGL. TRPGetMemberAccBal is unaffected; it still accepts an absent or
empty curr on initial load.

### Scope

/api/v1/account/summary only. /api/v1/account/gain-loss (AccountGainLossController)
has no currency query parameter and always forwards curr="" via
GetAccountGainLossCommand's nine-argument compatibility constructor (R-7). It
will be rejected by the new downstream validation. Per instruction, this is
accepted: /api/v1/account/gain-loss is treated as effectively obsolete and is
explicitly out of scope for this addendum. If it is ever revived, the
downstream team must relax the validation for that call path, since the
endpoint has no mechanism to receive a currency from the front end before the
account summary page has loaded for the first time.

### Decision D-1: effective currency for the TRPGetInvestGL call

GetAccountSummaryService.fetchGainLossAndAssemble currently builds the
gain-loss command from command.currency() verbatim, identically to the balance
call. This addendum changes only that one call site:

- command.currency() blank → use balanceResult.currencySelection().selectedCurrency()
  (this is response.data[0].curr[0].selected from the TRPGetMemberAccBal call
  that already executed first in this same request, already parsed by
  AccountBalanceCurrencySelectionMapper into CurrencySelection.selectedCurrency()).
- command.currency() non-blank → the validated value from D-2.

The balance call is unchanged: GetAccountBalanceCommand /
FetchAccountBalanceCommand continue to receive command.currency() verbatim,
blank or not. Only the value fed into FetchAccountGainLossCommand changes.

### Decision D-2: avai-currency cache and validation

**Storage.** AuthenticatedSession.featureState (Map<String,String>), the
existing generic per-session extension point already used by Fund Comparison
(M5.2), via AuthenticatedSession.withFeatureState(key, value). No new Redis
adapter, cache entry, or TTL is introduced; the avai list inherits the
authenticated session's existing idle/absolute expiry lifecycle.

**Key.** "account-summary.currency.avai:" + accountRef, scoped per account
because a session may hold multiple accounts (AuthenticatedSessionAccount).

**Write path.** When command.currency() is blank and the balance call
succeeds, persist balanceResult.currencySelection().availableCurrencies() to
the above key via AuthenticatedSessionConcurrencyService.updateIfUsable(
sessionId, session -> session.withFeatureState(key, serializedAvaiList),
correlationId) — the same CAS primitive, reload-on-conflict with retry, used
elsewhere for single-field session updates.

**Read / validate path.** When command.currency() is non-blank, load the
session and read featureState.get(key) before calling either downstream
operation:
- present, member of the list → proceed; this value becomes the effective
  currency for both the balance call (unchanged, forwarded as-is) and the
  gain-loss call (D-1).
- present, not a member → reject, see D-3.
- absent (cache miss) → reject, see D-3. No fallback re-fetch of
  TRPGetMemberAccBal is performed to revalidate. Per the ratified FE contract,
  currency is only ever sent on a non-first call, where a cache entry should
  already exist; a miss in that state is an anomaly (expired/evicted session
  state, or a non-conforming caller), not a normal flow, so it fails closed
  rather than silently reconstructing trust it no longer has.

### Decision D-3: error contract

New error code ACCOUNT_SUMMARY_REQUEST_INVALID / err.account-summary.request.invalid,
mapped to HTTP 400, added to ErrorCodes. Modeled on
FundPriceRequestInvalidException's shape (plain ApplicationException subclass,
auto-mapped to 400 by the existing generic exception handler; no dedicated
handler method required) rather than FundComparisonRequestInvalidException's
shape, because the two failure modes are structurally identical: a
caller-supplied value absent from a cached available-values list, with no
further distinguishing cases requiring a dedicated handler.

This is additive to 4-account-summary-error-contract.md, not a replacement:
the existing inherited downstream-failure mappings (err.tpa.account.invalid,
err.tpa.member.not.found, err.tpa.fund.switching.in.progress, and the 503
catch-all) are unchanged and continue to apply to downstream rejections. The
new code applies only to the pre-downstream currency-membership check in D-2.

### Non-goals

No change to TRPGetMemberAccBal's request contract, to AccountContext's shape,
or to any Account Balance behaviour. No change to /api/v1/account/gain-loss.
No new Redis adapter or configuration key.

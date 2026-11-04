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

### D-2 correction (key scoping and naming, 04/11/2026)

D-2 above is corrected as follows. The per-account key scheme ("account-summary.currency.avai:" + accountRef) was proposed before FundComparisonOptionsCacheService and PersonalInformationConfigCacheService had been read in full; it does not match the established convention and is superseded by this section. "avai" was also downstream TRPGetMemberAccBal wire terminology and is replaced here with meaningful Java-side naming; it continues to refer only to response.data[0].curr[0].avai/selected when describing the downstream contract itself.

**Key.** A single constant FEATURE_KEY = "account-summary.currency", not a per-account key, matching FundComparisonOptionsCacheService.FEATURE_KEY ("fund-comparison.options") and PersonalInformationConfigCacheService.FEATURE_KEY ("personal-information.config"). Account identity is embedded inside the cached value itself, not the key.

**Cache entry.** A new record AccountSummaryCurrencyCacheEntry(accountRef, policyNo, List<String> availableCurrencies), mirroring FundComparisonOptionsCacheEntry's shape exactly: constructor validation rejects blank accountRef/policyNo and an empty availableCurrencies list; a belongsTo(accountRef, policyNo) method is the sole account-identity check; toString() hides both identifiers. A private nested Wire record governs exactly what is serialized, the same least-privilege pattern used by both precedents.

**Cache service.** A new class AccountSummaryCurrencyCacheService, structurally identical to FundComparisonOptionsCacheService and PersonalInformationConfigCacheService: read(sessionId, accountRef, policyNo, requestId) returns Mono<Optional<AccountSummaryCurrencyCacheEntry>> and never errors -- session missing, undecryptable, not usable, key absent, unparseable, an unknown shape, failed entry validation, and an accountRef/policyNo mismatch (entry.belongsTo(...) returns false) all collapse uniformly to a logged miss with a reason tag, exactly as both precedents do. write(sessionId, entry, requestId) returns Mono<Void> and also never errors; it reuses the existing AuthenticatedSessionConcurrencyService via session.withFeatureState(FEATURE_KEY, encoded) -- no new CAS mechanism, no new Redis adapter.

**Consequence for the read/validate path.** The prior description of a cache miss ("absent") is unchanged in outcome -- still rejects as ACCOUNT_SUMMARY_REQUEST_INVALID/400 before calling either downstream operation -- but a stored entry belonging to a *different* account than the one currently selected is now also correctly treated as a miss via belongsTo(...), rather than being silently matched by an accountRef-scoped key that would have made a cross-account check unnecessary by construction. This is a strictly safer outcome than the superseded per-account-key design, not a behavior change requiring new sign-off.

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

### Decision D-4: controller / use-case wiring

Confirmed from FundComparisonOptionsController and
FundComparisonOptionsUseCase.getOptionsAndCache's real content, the direct
precedent for "read cache before the business call, write cache after it
succeeds, keyed by a cookie-derived session id": the cache read/write is not
performed by the controller directly. The controller reads
AuthenticatedSessionId via the existing AuthenticatedSessionCookieReader (a
missing or malformed cookie yields Optional.empty() and only skips caching; it
never fails the request), and passes sessionId, accountRef, and requestId as
additional parameters into the use case method itself. The use case performs
the cache read/write internally, alongside its existing business logic.

This changes GetAccountSummaryUseCase's public method shape:

- Current: Mono<AccountSummaryResult> get(GetAccountSummaryCommand command)
- New: Mono<AccountSummaryResult> get(GetAccountSummaryCommand command,
  Optional<AuthenticatedSessionId> sessionId, String accountRef, String
  requestId)

AccountSummaryController supplies accountRef from
PortalAccessContext.account().accountRef() (already resolved today, just not
currently passed to the use case) and requestId from the existing
RequestHeaderContext, matching FundComparisonOptionsController's exact
pattern. sessionId comes from AuthenticatedSessionCookieReader.read(exchange),
newly injected into AccountSummaryController the same way it already is into
FundComparisonOptionsController and PersonalInformationController.

Inside GetAccountSummaryService.get: when command.currency() is non-blank,
read the currency cache first (D-2's AccountSummaryCurrencyCacheService) and
reject via AccountSummaryRequestInvalidException on any miss (D-3) before
calling referenceDatePort or either downstream operation. When
command.currency() is blank, proceed as today; after the balance call
succeeds (inside fetchAndAssemble), write the currency cache from
balanceResult.currencySelection() before continuing to
fetchGainLossAndAssemble. A missing sessionId (cookie absent/malformed) only
skips the write in the blank-currency path; it does not fail the request,
matching D-2's "write never errors" behaviour and
FundComparisonOptionsController's "missing cookie only skips caching" comment
verbatim.

This is an additive, backward-incompatible signature change to
GetAccountSummaryUseCase (not an overload): the interface has exactly one
production implementation (GetAccountSummaryService) and one production
caller (AccountSummaryController), both updated together in the same change,
so no compatibility constructor or overload is introduced, mirroring how
FundComparisonOptionsUseCase itself has no legacy no-cache overload either.

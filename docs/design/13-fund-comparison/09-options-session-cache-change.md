# 9. Change Request: Validate Prices Requests Against Session-Cached Options

## 9.0 Status

- Phase 1 design. Decisions D1-D8 confirmed by Nick on 2026-10-20.
- D6 confirmed by Nick on 2026-10-20: `err.tpa.invalid.date` from `TRPGetFundPrice` is HTTP 400 `VALIDATION_FAILED` for every `TRPGetFundPrice` caller. Built as a normal sub-task (5.5), not held.
- Q1 resolved by Nick on 2026-10-20: a membership failure is HTTP 503 `SYSTEM_ERROR` (D8 revised).
- Implementation (Milestone 5) starts only after Nick approves this design.

## 9.1 Background (end-to-end finding)

`POST /api/v1/fund-comparison/prices` validated only the syntax of `fundCodes` and `timeRange`. It never checked them against the options returned by `POST /api/v1/fund-comparison/options` for the same account. As a result:

- `timeRange` `10Y` passes syntax validation. Downstream `TRPGetFundPrice` rejects it with `err.tpa.invalid.date` because the computed from-date is earlier than the config-matrix limit. The BFF mapped every nonblank `err-message` to HTTP 503 `err.fund-comparison.retrieval.unavailable`, reporting a client error as a system fault.
- Tokens inside the config-matrix limit but not offered (for example `2D`, `5M`) are not enforced at all.
- Fund codes not in the options fund list are silently dropped downstream, or returned if downstream does not scope them to the policy.

## 9.2 Confirmed downstream TRPGetFundPrice behavior (new)

1. All fund codes invalid: empty `FundInfo` and empty `FundPrice`.
2. Valid fund code with no prices in the range: non-empty `FundInfo`, empty `FundPrice`.
3. `from-date` earlier than the config-matrix limit: `err-message` `err.tpa.invalid.date`.
4. One invalid fund code mixed with valid ones: normal response; the invalid code is dropped.

## 9.3 Decisions

| ID | Decision |
|---|---|
| D1 | Add a session-scoped options cache. Options are effectively static configuration, the authenticated session has an 8-hour absolute timeout, and the page always calls options on load before any prices call. |
| D2 | One cache slot per session. A cached entry matches only when **both** `accountRef` and `policyNo` equal the current account context. Anything else is a miss, never a match. |
| D3 | Store only the raw downstream `time-range` string and the downstream fund codes. No fund names, no default time range. |
| D4 | The options endpoint always calls `TRPGetFundCmpOpt` and overwrites the cache entry through the existing generic CAS service (`AuthenticatedSessionConcurrencyService`, unchanged, including its retry on revision conflict). No feature-specific port. A failed or skipped write is logged and never fails the options response. |
| D5 | Prices: syntax validation, cache read-through, membership validation with a single refresh-and-recheck (see 9.4). |
| D6 | **Confirmed 2026-10-20.** `TRPGetFundPrice` `err-message` exactly equal to `err.tpa.invalid.date` becomes HTTP 400 `VALIDATION_FAILED` for every `TRPGetFundPrice` caller (Fund Comparison prices: `err.fund-comparison.request.invalid`; Fund Price query and export: `err.fund-price.request.invalid`). `TRPGetFundPriceMode` is excluded (it has no time range) and keeps HTTP 503. Every other nonblank `err-message` stays HTTP 503. |
| D7 | The cache never replaces a downstream check. Downstream remains the owner of fund availability and the config matrix. |
| D8 | **Revised 2026-10-20 (Q1).** A membership failure against current options is HTTP 503 `SYSTEM_ERROR`, `err.fund-comparison.retrieval.unavailable`, logged at ERROR. The FE only offers what the options endpoint returned, and the check runs against freshly fetched options, so a remaining mismatch indicates a Java or downstream fault, not a user error. Syntax failures stay HTTP 400 `VALIDATION_FAILED`. No 422, 403, 409, or 404. |

Resolved details (Nick, 2026-10-20):

- **Key:** hierarchical match on `accountRef` then `policyNo`; one slot, always overwritten.
- **Prices uses the cache:** the options endpoint never serves from cache; prices always reads it.
- **Normalization:** trim and lower-case both sides when comparing (9.5.4).
- **Write conflicts:** reuse the concurrency service's existing retry; an exhausted conflict is logged and skipped.
- **Logging:** use `requestId`; never log `policyNo` or `accountRef` (9.7).
- **Session storage (Option 2):** add a generic feature-state slot to `AuthenticatedSession` instead of another positional field (9.5.1).
- **D6 status per feature:** Fund Comparison returns `err.fund-comparison.request.invalid` (HTTP 400, `VALIDATION_FAILED`, existing handler). Fund Price query and export return `err.fund-price.request.invalid` with HTTP 400 `VALIDATION_FAILED` through a dedicated handler for this case only; Fund Price's other 400s (for example an invalid date input) stay `BUSINESS_REJECTED`.
- **D6 rationale (recorded):** every date that reaches `TRPGetFundPrice` is already calculated or validated by Java, so a rejection points to inconsistent downstream configuration. Nick chose HTTP 400 so the response states that downstream rejected the date range.
- **Rolling deploy:** only DEV exists today, so mixed old/new pods are not handled in this change (future item in 9.10).

## 9.4 Prices flow

1. **Syntax validation** (unchanged except 9.5.4): 1 to 5 unique nonblank fund codes, `timeRange` matches `^(?:[1-9][0-9]*[DMY]|YTD)$` after trim and upper-case. Failure: 400 `VALIDATION_FAILED`, `err.fund-comparison.request.invalid`.
2. **Cache read.** Read the session cache entry. Miss when: no entry, `accountRef` or `policyNo` differs, the entry cannot be decoded, or no readable session ID is available. On a miss, fetch `TRPGetFundCmpOpt` through `FundComparisonOptionsSourcePort`, normalize it with `FundComparisonTimeRangeParser`, write the cache (non-fatal), and continue with the **fresh** options. A miss is never an error by itself.
3. **Membership validation**, with at most one options fetch per prices request:
   - `timeRange` is in the options' time ranges **and** every requested fund code is in the options' fund codes: continue to step 5.
   - Membership fails against a **cached** entry (not fetched in step 2 of this request): fetch `TRPGetFundCmpOpt` once, write the refreshed entry (non-fatal), and validate again.
   - Membership fails against **fresh** options (from step 2 or from the recheck): 503 `SYSTEM_ERROR`, `err.fund-comparison.retrieval.unavailable`, logged at ERROR. `TRPGetFundPrice` is **not** called.
   - Never loop: one fetch at most.
   - Reason: the FE only offers what the options endpoint returned, so a selection that fails against current options means Java or downstream has a fault. Checking fresh options first also means a config change during the session never rejects a selection the user legitimately picked from the page.
4. **Options retrieval failure** during step 2 or the recheck: 503 `SYSTEM_ERROR`, `err.fund-comparison.retrieval.unavailable`, the same as the options endpoint.
5. **Price fetch.** Calculate dates from the cached spelling of the time-range token and call `TRPGetFundPrice` with the cached spelling of each fund code (9.5.4).

## 9.5 Design details

### 9.5.1 Generic feature-state slot on `AuthenticatedSession` (Option 2)

Problem: `AuthenticatedSession` is a positional record. Every class that rebuilds a session calls the full constructor, so each new per-session field touches every rebuild site. A missed site silently drops data. This violates the Open/Closed Principle.

Change:

- Add one field, `featureState`: an immutable `Map<String, String>`, feature key to opaque encoded value. `null` becomes an empty map. Excluded from `toString()`.
- Stored inside the encrypted `SensitiveContext`. An absent value on read is an empty map.
- One-time pass-through migration of every rebuild site, after which none of them changes again for a new feature:
  1. `AuthenticatedSessionConcurrencyService` (replacement before CAS)
  2. `AuthenticatedSessionLifecycleService` (`create` with an empty map; `copy` passes it through)
  3. `AuthenticatedSessionCsrfRefreshService.withCsrfHash`
  4. `RecoverCommittedLoginService.withCsrfHash`
  5. `FundPriceModeUseCase.stampMetadata`
  6. `AesGcmAuthenticatedSessionProtector.SensitiveContext` (`from` and `toDomain`)
- A feature reads and writes only its own key. Writes go through `AuthenticatedSessionConcurrencyService.updateIfUsable` with a mutator that replaces one map entry.
- `fundPriceModeMetadata` is **not** migrated in this change (future item, 9.10).

### 9.5.2 Fund Comparison cache entry

Key: `fund-comparison.options`.

```json
{
  "accountRef": "acc_...",
  "policyNo": "00000008802",
  "rawTimeRange": "10D,1M,3M,6M,1Y,YTD,3Y",
  "fundCodes": ["FAEU", "FBAU"]
}
```

- `rawTimeRange` and `fundCodes` are exactly as returned by `TRPGetFundCmpOpt`.
- Readers parse `rawTimeRange` with `FundComparisonTimeRangeParser` on every read, so cached and fresh options always use the same grammar.
- Encoding: an application-layer codec using the existing `TransientJsonPort` precedent. A decode failure is treated as a cache miss.
- The entry lives inside the encrypted session payload, which already holds `policyNo` for every account.

### 9.5.3 Session access

- **Session ID:** both Fund Comparison controllers obtain it through `AuthenticatedSessionCookieReader`. If none is available, the cache is not read or written, and prices validates against freshly fetched options.
- **Read (revised 2026-10-20, script 086):** a plain `AuthenticatedSessionPort.load` plus `AuthenticatedSessionProtectorPort.unprotect`, not `loadAndTouch`. `PortalAccessContextWebFilter` already calls `loadAndTouch` twice on every authenticated request, so another touch would add a Redis write and widen the overwrite race in 9.10. A missing, unreadable, or unusable session is a cache miss.
- **Write:** `AuthenticatedSessionConcurrencyService.updateIfUsable`. Non-`APPLIED` outcomes are logged with a reason and skipped.

### 9.5.4 Normalization and matching

- Request fund codes are trimmed and de-duplicated case-insensitively, keeping the first occurrence.
- `timeRange` is trimmed. The syntax check runs on its upper-cased form, so `3y` is accepted. **Consequence:** the existing test case that rejects `10d` changes to accepted.
- Membership compares the lower-cased request values with the lower-cased option values.
- After a match, the **cached spelling** of each fund code and of the time-range token is used for date calculation, the downstream `fund-list`, and the response `series[].fundCode`. Without this, a request for `faeu` would have its series silently dropped by exact-match grouping.

## 9.6 Error mapping

| Condition | HTTP | API status | Code |
|---|---|---|---|
| Syntax invalid | 400 | `VALIDATION_FAILED` | `err.fund-comparison.request.invalid` |
| Membership fails against fresh options | 503 | `SYSTEM_ERROR` | `err.fund-comparison.retrieval.unavailable` (Q1, ERROR log) |
| Options retrieval fails during prices (miss or recheck) | 503 | `SYSTEM_ERROR` | `err.fund-comparison.retrieval.unavailable` |
| Cache read failure or unreadable entry | - | - | treated as a miss |
| Cache write failure, conflict, or missing session | - | - | logged and skipped |
| `TRPGetFundPrice` `err.tpa.invalid.date` (D6), Fund Comparison | 400 | `VALIDATION_FAILED` | `err.fund-comparison.request.invalid` |
| Same, Fund Price query/export (D6) | 400 | `VALIDATION_FAILED` | `err.fund-price.request.invalid` |
| Any other nonblank `TRPGetFundPrice` `err-message` | 503 | `SYSTEM_ERROR` | unchanged per feature |
| `TRPGetFundPriceMode` any nonblank `err-message` | 503 | `SYSTEM_ERROR` | unchanged |

## 9.7 Logging

Every event carries `requestId`. Never log `policyNo`, `accountRef`, session IDs, or downstream payloads. Fund codes and time-range tokens are not personal data and may be logged.

| Event | Fields |
|---|---|
| `event=fund-comparison.options-cache.hit` | `requestId` |
| `event=fund-comparison.options-cache.miss` | `requestId`, `reason=session-missing\|session-unreadable\|session-unusable\|absent\|unreadable\|account-mismatch\|failed` |
| `event=fund-comparison.options-cache.write.skipped` | `requestId`, `reason=conflict\|session-missing\|session-not-usable\|failed\|no-session` |
| `event=fund-comparison.options-cache.write.applied` | `requestId` |
| `event=fund-comparison.options-cache.stale-refresh` | `requestId`, `timeRange`, `unlistedFundCodes` |
| `event=fund-comparison.membership.rejected` (ERROR) | `requestId`, `reason=time-range-not-offered\|fund-code-not-offered\|no-valid-time-range`, `timeRange`, `unlistedFundCodes`, `source=fresh` |
| `event=fund-price.downstream.invalid-date` (D6) | `requestId`, `operation` |

## 9.8 Test strategy (required cases)

- Cache hit: no options call.
- Cache miss: fetch and store.
- Different `accountRef` or `policyNo`: treated as a miss.
- Unreadable entry: treated as a miss.
- Membership passes on cache: zero options calls.
- Membership fails on cache, passes after refresh: exactly one options call, cache updated, `TRPGetFundPrice` called.
- Membership fails on cache and after refresh: exactly one options call, 503, ERROR log.
- Membership fails on options fetched after a miss: no second fetch, 503.
- Unoffered `timeRange` (`10Y`, `2D`) or unlisted fund code after the refresh: 503 `err.fund-comparison.retrieval.unavailable`. Syntax-invalid input: 400.
- Options failure during the miss fetch or the recheck: 503.
- `TRPGetFundPrice` never invoked when membership validation fails.
- Options endpoint succeeds when the cache write fails, conflicts, or has no session.
- Refresh-and-recheck succeeds even when the refreshed cache write fails.
- Case and whitespace: `" faeu "` matches `FAEU`; downstream and response use `FAEU`.
- D6: `err.tpa.invalid.date` gives 400 `VALIDATION_FAILED` for comparison (`err.fund-comparison.request.invalid`) and for Fund Price query and export (`err.fund-price.request.invalid`); Fund Price's other 400s stay `BUSINESS_REJECTED`; any other nonblank `err-message` gives 503; mode is unchanged.
- Feature-state slot: each of the six rebuild sites preserves `featureState`; protector round-trip; absent value reads as empty.
- Existing Fund Price callers and responses unchanged, apart from D6.

## 9.9 Milestone 5 (sub-tasks)

Each sub-task is fail-first. Sub-task gate: failing test evidence, implementation, `mvn -DskipTests compile`, `mvn -DskipTests test-compile`, then `mvn -Dtest=<new/changed test classes plus existing tests of every changed class> test`. Compile failures stop the run and are fixed cumulatively.

- **5.1 Feature-state slot.** Add `featureState` to `AuthenticatedSession` and `SensitiveContext`; migrate the six rebuild sites. Pure refactor, no behavior change. Regression tests per rebuild site.
- **5.2 Options cache model and access.** Cache entry record, codec, application service: read (hit, miss, account mismatch, unreadable) and write (via the concurrency service, non-fatal, reason-logged).
- **5.3 Options endpoint writes the cache.** Session ID through `AuthenticatedSessionCookieReader`; write failure non-fatal.
- **5.4 Prices membership validation.** Normalization (9.5.4) in FundComparisonPricesWebMapper; a pure FundComparisonSelectionMatcher; FundComparisonPricesUseCase gains FundComparisonOptionsUseCase and FundComparisonOptionsCacheService, reads the cache, refreshes at most once through FundComparisonOptionsUseCase.getOptionsAndCache, and fetches prices with the offered spelling; wiring. Implemented 2026-10-20 (script 091; merged into the prices use case by script 095, after script 094 landed the formatting commit).
- **5.5 D6 (confirmed).** A dedicated application exception thrown only on the `TRPGetFundPrice` adapter path for the exact `err.tpa.invalid.date`. Fund Price: a dedicated `ApiExceptionHandler` method returns HTTP 400 `VALIDATION_FAILED` `err.fund-price.request.invalid`. Fund Comparison: the prices use case translates it to `FundComparisonRequestInvalidException` (its current error mapping wraps every non-retrieval exception as 503, so this needs an explicit branch). Mode unchanged. Fund Price docs move from "decided" to implemented.
- **5.6 Old-behavior tests.** Update or replace tests that assert the old behavior (including the `10d` syntax case and the BRD traceability suite).

Milestone-end gate (once, after all sub-tasks are committed): full `mvn test`; `mvn spotless:apply` then `spotless:check`, then compile and test-compile again; tiered coverage review; startup smoke test; SonarQube quality gate.

## 9.10 Open items, future revamps, known limitations

Resolved (Nick, 2026-10-20):

- **Q1:** membership failure is HTTP 503 `SYSTEM_ERROR`, `err.fund-comparison.retrieval.unavailable` (D8 revised). No distinct code.
- **D6:** confirmed; HTTP 400 `VALIDATION_FAILED` (D6 row in 9.3).

Future revamps (not in this change):

- Migrate `fundPriceModeMetadata` into the `featureState` slot. It changes Fund Price's stored session format.
- Make `SensitiveContext` ignore unknown properties before any environment runs mixed old/new pods. Today only DEV exists.
- Move `FundPriceController` session-ID resolution to `AuthenticatedSessionCookieReader` (deferred in that reader's own Javadoc).
- Remove the backend-resolved `message` field from error responses; FE owns labels (Nick, future response-schema revamp).

Known limitations:

- `loadAndTouch` rewrites the whole session without a revision check. A concurrent request can overwrite a just-written cache entry. Effect: a later cache miss and refetch, never a wrong answer.
- Fund Price's existing 400s use `BUSINESS_REJECTED`, while D6 and every Fund Comparison 400 use `VALIDATION_FAILED`.
- Accepted trade-off (Q1): a request built outside the page with an unoffered value (for example `10Y` via Postman) gets HTTP 503, not 400. `TRPGetFundPrice` is not called.
- Existing doc discrepancies, not changed by this CR: 01 sections 1.2/1.3 and 03 section 3.2 still describe a separate dedicated Fund Comparison price adapter (the existing adapter was extended instead); 02's error examples show `targets: ["SERVER"]` and `type: SYSTEM`, while the code emits `targets: []`, `type: FORM` for retrieval failures, and a `message` field.

## 9.11 Phase 3 local E2E scenarios

Curl scenarios against the local instance:

1. Options, then prices with an offered selection: 200.
2. Unoffered `timeRange` (`10Y`, `2D`): 503 `err.fund-comparison.retrieval.unavailable`, `TRPGetFundPrice` not called.
3. Unlisted fund code: 503.
4. Prices without a prior options call (cache miss): fetched, then validated.
5. Account switch (different `Account-Ref`): treated as a miss.
6. Stale cache corrected by the recheck: alter the cached entry in Redis, then call prices with a currently offered value: 200, one options call.
7. The four confirmed downstream behaviors in 9.2.
8. D6: an offered time range that downstream's config matrix rejects: 400 `VALIDATION_FAILED`, `err.fund-comparison.request.invalid`.

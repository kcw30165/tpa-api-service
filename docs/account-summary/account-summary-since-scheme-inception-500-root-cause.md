# Account Summary (SINCE_SCHEME_INCEPTION) 500 SYSTEM_ERROR — Root Cause & Fix Design

## Status
Root cause confirmed. Fix not yet implemented (this document precedes implementation per
workstream documentation policy).

## Symptom
`GET /api/v1/account/summary?period=SINCE_SCHEME_INCEPTION` returns HTTP 500 with
`status: SYSTEM_ERROR` / `code: err.system.unexpected` for at least one real account
(login-user-id `jpjpm02`, policy-no `00000000118`, cert-no `0070`, JPM trust).

Server log shows:
```
AccountBalanceRetrievalException[reason=SYSTEM_FAILURE]
  at AccountBalanceRetrievalException.systemFailure(:36)
  at DefaultAccountBalanceNormalizer.systemFailure(:53)
  at DefaultAccountBalanceNormalizer.required(:48)
  at DefaultAccountBalanceNormalizer.normalize(:21)
  at DefaultAccountBalanceAssembler.build(:80)
  at DefaultAccountBalanceAssembler.lambda$assemble$0(:65)
```
APIM `TRPGetMemberAccBal` itself responded HTTP 200 with `err-message: ""` (no downstream
business error) and a seemingly complete `data[0]` payload. The failure is entirely
BFF-side, inside the normalization step.

## Confirmed root cause
`DefaultAccountBalanceNormalizer.normalize()` (line 21) builds the display-source index key as:

```java
var key =
    new DisplaySourceKey(
        required(source.displaySource()), required(source.subDisplaySource()));
```

`required()` is defined as:

```java
private static String required(String value) {
  if (value == null || value.trim().isEmpty()) throw systemFailure();
  return value;
}
```

It throws `AccountBalanceRetrievalException.systemFailure()` whenever the value is `null`
**or blank**. This same `required()` helper is reused, unchanged, for both:
- `displaySource` (the primary grouping key, e.g. `EE` / `ER`) — correctly mandatory.
- `subDisplaySource` (the secondary grouping key) — incorrectly held to the same
  non-blank mandate.

The actual `TRPGetMemberAccBal` response captured for this account has:

```json
"dispSrc": [
  { "disp-src": "EE", ..., "sub-disp-src": "" },
  { "disp-src": "ER", ..., "sub-disp-src": "" }
],
"accBal": [
  { "disp-src": "EE", "sub-disp-src": "", "fund-code": "JGGU", ... },
  { "disp-src": "EE", "sub-disp-src": "", "fund-code": "JPSU", ... },
  { "disp-src": "ER", "sub-disp-src": "", "fund-code": "JGGU", ... },
  { "disp-src": "ER", "sub-disp-src": "", "fund-code": "JPSU", ... }
]
```

Every `sub-disp-src` value for this trust is an empty string, for **both** display
sources and **all four** holding rows. This is a legitimate downstream shape: this
trust has no further sub-display-source grouping under EE/ER, and TAS/APIM represents
"no sub-grouping" as an empty string rather than omitting the field. It is not
malformed or incomplete data.

`required()` rejects this legitimate shape as a technical failure, which is exactly
the line that threw in production:

```
normalize():21  ->  required(source.subDisplaySource())  ->  "" is blank  ->  systemFailure()
```

### Why this was not caught earlier
Every existing fixture that exercises `DefaultAccountBalanceNormalizer` — in
`DefaultAccountBalanceNormalizerTest`, `DefaultAccountBalanceAssemblerIntegrationTest`,
`DefaultAccountOverviewAggregatorTest`, and the account-summary assembler/service
tests — uses a non-blank `subDisplaySource` value (`"OE"`, `"1"`, `"2"`, etc.). None
of them represent an account/trust with no sub-display grouping at all, so the blank
value path was never exercised before this went to an environment with real JPM
trust data.

## Scope check (confirmed by source, not assumed)
The same `required()` helper, with the same blanket non-blank rule, is also applied to
`subDisplaySource` inside the holdings loop of `normalize()`:

```java
String displaySource = required(holding.displaySource());
String subDisplaySource = required(holding.subDisplaySource());
```

So the same fix must be applied consistently to both the `displaySources` loop and the
`holdings` loop — not just the first occurrence — or the holdings loop will still throw
even after the `dispSrc` loop is fixed.

`fundCode` (`FundKey`) is a separate required value unaffected by this issue; production
data for `fund-code` is always non-blank in the captured payload, and there is no reason
to relax that check.

## Proposed fix design (for review before implementation)
Introduce a distinction between the two kinds of "required" semantics currently
collapsed into one `required()` helper:

1. **Primary display source** (`displaySource` on both `RawAccountBalanceDisplaySource`
   and `RawAccountBalanceHolding`) remains mandatory non-blank — unchanged behavior.
2. **Sub display source** (`subDisplaySource` on both types) is relaxed to treat an
   empty/blank string as a valid "no sub-grouping" value, while still treating `null`
   as an unexpected/invalid shape (fail closed on `null`, since APIM has never been
   observed to omit the field entirely — only to send it as `""`).

Concretely, this means adding a second helper (name TBD during implementation review,
e.g. `requiredAllowingBlank()` or `optionalSubSource()`) used only for
`subDisplaySource` in both the `displaySources` loop and the `holdings` loop, while
`required()` continues to be used for `displaySource` and `fundCode` exactly as today.

### Downstream impact to verify during implementation
- `DisplaySourceKey` and `HoldingKey` already accept `String subDisplaySource` with no
  additional constraint, so a key of `("EE", "")` is structurally valid and distinct
  from `("EE", "OE")` — no key-collision risk introduced.
- `DefaultAccountBalanceAssembler.subName()` looks up
  `n.displaySources().get(new DisplaySourceKey(d, s))`; when `s` is `""` this will
  resolve correctly against the relaxed normalizer output, using the sub-source
  description fields from the same (now-accepted) `""`-keyed display source record.
- `SubDisplayLevelPortfolioBuilder` / `FundLevelPortfolioBuilder` (not yet inspected
  line-by-line) must be confirmed during implementation to render a blank
  `subDisplayAccountId` sensibly rather than producing an empty/confusing label in the
  response; this is an implementation-time verification item, not assumed here.

### Testing plan
- Extend `DefaultAccountBalanceNormalizerTest` with cases for:
  - A display source whose `subDisplaySource` is `""` (not blank-trimmed-to-null)
    normalizes successfully.
  - A holding whose `subDisplaySource` is `""` normalizes successfully and joins
    correctly against the corresponding blank-keyed display source.
  - `displaySource` itself remaining blank/null must still fail (regression guard that
    the relaxation is scoped to sub-display-source only).
  - `subDisplaySource` as `null` (if ever produced) still fails closed, per the
    fail-closed decision above.
- Add/extend an assembler or controller-level test reproducing the exact
  `policy-no 00000000118` / `cert-no 0070` shape (all-blank sub-display-source across
  both display sources and all holdings) and assert a successful (non-500) summary
  response.

### Code coverage
`DefaultAccountBalanceNormalizer` is a pure validation/orchestration function in the
application service tier. Per the tiering policy for this workstream, the new
branches introduced by this fix (blank-vs-non-blank sub-display-source) must reach the
applicable tiered coverage targets; exact classification (validator-tier 95% branch vs.
orchestration-tier 85-90%) to be confirmed against the already-agreed tiering rules at
implementation time.

## Out of scope
- No other APIM operations (`TRPGetInvestGL`, `TRPGetContSumy`, etc.) are affected;
  this is specific to `TRPGetMemberAccBal` consumption inside
  `DefaultAccountBalanceNormalizer`.
- No change to the public API contract of `/api/v1/account/summary` or
  `/api/v1/account/balance`; the fix is purely in normalization-layer validation
  strictness.

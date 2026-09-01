# Phase IV-007 Full Assembler Contract Ratification

## Status

```text
PHASE_IV_007_CONTRACT=RATIFIED
PHASE_IV_007_IMPLEMENTATION_AUTHORIZED=YES
```

## Purpose

This document ratifies the final Account Balance assembler output contract required by:

```text
407-phase-iv-007-full-assembler-integration.prompt.md
```

Its purpose is to remove implementation ambiguity regarding:

- Display Account hierarchy
- Fund Level View hierarchy
- Sub-Display-Account Level View hierarchy
- Placement of financial fields
- Placement of factsheet URL
- Placement of FX rate
- Ownership of aggregated and leaf values

This document does not introduce new business decisions. It consolidates already-approved design decisions from the ratified Account Balance design, Phase I investigation, Phase II shared metadata implementation, and Phase III approved application/APIM boundaries.

---

# Root Response Contract

Phase III ratified the public response structure:

```text
result.data
 ├─ asOfDate
 ├─ currencySelection
 ├─ accountOverview
 └─ investmentPortfolio
```

The root application aggregate remains:

```java
AccountBalanceResult
```

Phase IV must populate this structure and must not introduce alternative top-level hierarchies.

---

# Currency Selection

## Ratified shape

```text
currencySelection
 ├─ availableCurrencies[]
 └─ selectedCurrency
```

Example:

```json
{
  "availableCurrencies": [
    "HKD",
    "USD"
  ],
  "selectedCurrency": "HKD"
}
```

## Ownership

```text
curr.avai
    -> availableCurrencies

curr.selected
    -> selectedCurrency
```

No additional currency hierarchy is permitted.

---

# Account Overview

## Ratified shape

```text
accountOverview
 ├─ displayOptions
 │    ├─ vestedBalanceVisible
 │    └─ pendingSwitchInVisible
 ├─ displayAccounts[]
 │    ├─ displayAccountId
 │    ├─ displayAccountLabel
 │    ├─ displaySequence
 │    ├─ accountBalance
 │    └─ vestedBalance
 └─ accountTotals
      ├─ totalBalance
      └─ pendingSwitchIn
```

## Visibility behavior

`displayOptions` is authoritative. Hidden `vestedBalance` values remain present as null on each Display Account. Hidden `pendingSwitchIn` remains present as null under `accountTotals`.

Display Accounts are ordered by `displaySequence` and the stable technical ID tie-breaker. Labels use the approved request-language description resolution and fallback rules.

---

# Investment Portfolio Root

## Ratified shape

```text
investmentPortfolio
 ├─ fundLevelView[]
 └─ subDisplayAccountLevelView[]
```

Both views must be generated from the same normalized holdings model.

Neither view may be derived from the other FE DTO.

---

# Fund Level View

## Hierarchy

```text
Display Account
    └─ Fund
          └─ Sub-Display Account
```

## Ratified Contract

```text
fundLevelView[]
 └─ displayAccount
      ├─ displayAccountName
      ├─ displayAccountId
      ├─ displaySequence
      └─ funds[]
             ├─ fundCode
             ├─ fundName
             ├─ fundPrice
             ├─ unitBalance
             ├─ accountBalance
             ├─ factsheetUrl
             ├─ fxRate
             └─ subDisplayAccounts[]
                    ├─ subDisplayAccountName
                    ├─ subDisplayAccountId
                    ├─ unitBalance
                    ├─ accountBalance
                    ├─ fundPrice
                    └─ fxRate
```

## Placement Rules

The following belong on the Fund node:

```text
fundPrice
unitBalance
accountBalance
factsheetUrl
fxRate
```

because that node represents fund-level aggregation.

The Sub-Display-Account leaf retains holding balances, fundPrice, and fxRate, but does not repeat factsheetUrl because factsheet ownership remains exclusively on a Fund node.

No separate totals object is allowed.

No separate metrics object is allowed.

---

# Sub-Display-Account Level View

## Hierarchy

```text
Display Account
    └─ Sub-Display Account
           └─ Fund
```

## Ratified Contract

```text
subDisplayAccountLevelView[]
 └─ displayAccount
      ├─ displayAccountName
      ├─ displayAccountId
      ├─ displaySequence
      └─ subDisplayAccounts[]
             ├─ subDisplayAccountName
             ├─ subDisplayAccountId
             └─ funds[]
                    ├─ fundCode
                    ├─ fundName
                    ├─ fundPrice
                    ├─ unitBalance
                    ├─ accountBalance
                    ├─ factsheetUrl
                    └─ fxRate
```

## Placement Rules

The following belong only on the Fund leaf:

```text
fundPrice
unitBalance
accountBalance
factsheetUrl
fxRate
```

No aggregate balances are placed directly on the Sub-Display-Account node.

---

# Canonical Ownership Rules

## Fund Level View

```text
Display Account
    -> metadata only

Fund
    -> aggregated balances
    -> fundPrice
    -> fxRate
    -> factsheetUrl

Sub-Display Account leaf
    -> raw holding balances
    -> fundPrice
    -> fxRate
```

## Sub-Display-Account Level View

```text
Display Account
    -> metadata only

Sub-Display Account
    -> metadata only

Fund leaf
    -> raw holding balances
    -> fundPrice
    -> fxRate
    -> factsheetUrl
```

---

# Factsheet Ownership

Factsheet belongs to the fund.

Therefore it appears in:

```text
Fund Level View
    Fund node only, not its nested Sub-Display-Account leaves

Sub-Display-Account View
    Fund leaf node
```

Factsheet must not appear on:

```text
Display Account
Sub-Display Account
Account Overview
Currency Selection
```

---

# FX Rate Ownership

FX rate belongs to the underlying fund holding.

Therefore:

```text
Fund Level View
    Fund node
    Sub-Display Account leaf

Sub-Display Account View
    Fund leaf
```

When:

```text
fxRateVisible=false
```

The property is omitted everywhere.

---

# Phase IV-007 Implementation Authority

With this ratification, Phase IV-007 no longer requires additional FE contract clarification.

The following are now explicitly approved:

```text
Display Account node shape
Fund node shape
Sub-Display Account node shape
Fund Level View hierarchy
Sub-Display Account View hierarchy
fundPrice placement
unitBalance placement
accountBalance placement
fxRate placement
factsheetUrl placement
```

---

# Architect Comment

I do not agree that Phase IV-007 should be blocked pending additional FE design clarification.

The repository did not contain a single finalized hierarchy document, but the required structure can be derived from already-ratified decisions made during Phase I, Phase II, and Phase III.

The missing information was a consolidation problem, not a business-decision problem.

The implementor should treat this document as the authoritative assembler-output contract for Phase IV-007.

No further FE hierarchy approval is required unless a business owner requests a public contract change.

---

# Architect Decision

```text
PHASE_IV_007_CONTRACT=RATIFIED

DISPLAY_ACCOUNT_HIERARCHY=APPROVED
FUND_LEVEL_VIEW=APPROVED
SUB_DISPLAY_ACCOUNT_VIEW=APPROVED
FIELD_PLACEMENT=APPROVED

PHASE_IV_007_IMPLEMENTATION_AUTHORIZED=YES
```

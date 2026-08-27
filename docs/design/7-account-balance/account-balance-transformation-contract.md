# Account Balance Transformation Contract

**Feature:** 6.14 Account Balance  
**Baseline:** 27/08/2026  
**Status:** Consolidated design baseline, pending repository investigation  

## Invariants

```text
accBal -> dispSrc by (disp-src, sub-disp-src)
accBal -> fundDtl by fund-code
```

`(disp-src, sub-disp-src, fund-code)` is unique. Duplicate or orphan records are technical failures.

## Overview

```text
displayAccount.accountBalance = sum(nav) by disp-src
displayAccount.vestedBalance = sum(vested-bal) by disp-src when visible
displayAccount.displaySequence = minimum dispSrc.seq by disp-src
accountTotals.totalBalance = sum(nav)
accountTotals.pendingSwitchIn = sum(transit-amt) when visible
```

Hidden overview values are not aggregated and return `null`.

## Views

Fund Level View:

```text
Display Account -> Fund -> Sub-Display Account
```

Fund unit and NAV sum by `(disp-src, fund-code)`; leaves use their unique holding.

Sub-Display-Account Level View:

```text
Display Account -> Sub-Display Account -> Fund
```

Fund leaves use their unique holding.

Ordering: display accounts by minimum source sequence, sub-display accounts by source sequence, funds by fund sequence, with stable technical-ID tie-breakers.

## Localization

Select TAS English or Chinese descriptions using the existing `Accept-Language` resolver. Use the other nonblank language as fallback. Both blank is invalid for displayed data.

## Number rendering

TAS already rounds returned numbers. Java aggregates exact `BigDecimal` values and uses resolved precision for grouping and trailing-zero display. Java does not invent a rounding method. If an aggregate cannot be represented at the supplied precision without rounding, treat it as a technical contract inconsistency.

## Factsheet

A nonblank syntactically valid absolute URL is returned. A blank or malformed value is omitted; malformed values are safely logged without the full URL.

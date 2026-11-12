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

`factsheetUrl` is chosen per fund from `TRPGetMemberAccBal` `fundDtl`. When the request language resolves to `zh_HK` (`Accept-Language` `zh`, `zh-HK` or `zh_HK`), `chin-fund-fact-sheet` is used if it is a nonblank, syntactically valid absolute URL; otherwise (absent, blank or malformed) `fund-fact-sheet` is used. Every other language uses `fund-fact-sheet` only and never falls back to the Chinese link. Each candidate is validated by `AccountBalanceFactsheetResolver`: a blank value is omitted silently, a malformed value is omitted and logged without the URL. When no candidate is valid, `factsheetUrl` is omitted. The same rule applies to the Fund Level View and the Sub-Display-Account Level View.

## Phase I Repository Ratification

The repository investigation ratifies these physical boundaries:

- `X-Request-Id` is the only header universally rejected when missing by the shared pre-controller mandatory-header filter. Only `X-Request-Id` is propagated to APIM.
- `Accept-Language` is optional. When supplied, the existing shared language resolver selects English or Traditional Chinese TAS descriptions. Missing, blank, or unsupported values resolve to English. Account Balance adds no language parser and does not return `VALIDATION_FAILED` for an absent language.
- `Account-Ref` is required by the existing selected-account context-resolution path, not by an Account Balance controller validator. `PortalAccessContextWebFilter` resolves and publishes `PortalAccessContext` to Reactor Context and exchange attributes; `CurrentPortalAccessContextResolver.current()` is the controller reuse point. `Account-Ref` is neither sent to APIM nor echoed to FE.
- `Idempotency-Key` is absent from the naturally repeatable Account Balance GET contract. No idempotency store, replay record, or idempotency adapter is introduced.
- `MutationResponse` is the target public envelope. `ApiResponse` is obsolete and must not be introduced.
- Technical failures use HTTP 503, `SYSTEM_ERROR`, `err.system.unavailable`, and `errors[].type=SYSTEM`. `PAGE` and `GLOBAL` are not error types.
- Portfolio omission uses scoped Jackson inclusion on Account Balance response DTO fields. Global `ObjectMapper` inclusion must not change. Hidden overview `vestedBalance` and `pendingSwitchIn` remain present as JSON null; hidden `fxRate` and blank or malformed `factsheetUrl` are omitted.
- `ContributionController#getContributionSummary` and `ContributionControllerTest` are the primary physical controller and test patterns. Account Balance preserves its optional `currency`, selected-account context, optional language fallback, both result views, and absence of `Idempotency-Key`; contribution business DTOs and logic are not copied.

# Account Balance Source and Requirements

**Feature:** 6.14 Account Balance  
**Baseline:** 27/08/2026  
**Status:** Consolidated design baseline, pending repository investigation  

## Authority

- BRD first Account Balance page defines the included display scope.
- TAS/APIM owns source facts, display-source hierarchy, fund metadata, display options, currencies, and metadata values.
- Java owns authenticated context resolution, ReferenceDate resolution, APIM orchestration, joins, aggregation, localization, number rendering, and public response normalization.
- FE owns fixed labels, currency presentation, Important Note, print, and switching between the two supplied portfolio views.

## Scope

Included: Account Overview, Display Account balances, vested balances, Total Balance, Pending Switch-in, Fund Level View, Sub-Display-Account Level View, currency selection, FX rate when visible, fund price, unit, NAV/account balance, and factsheet URL.

Excluded: gain/loss, Current Portfolio Gauge, total funds, top holdings, Important Note transport, and server-side print.

## Public request

```http
GET /api/v1/account-balance
GET /api/v1/account-balance?currency=USD
```

Request header profile:

```text
X-Request-Id: required by shared mandatory-header filter
Accept-Language: optional, English fallback
Account-Ref: required by selected-account context resolution
Idempotency-Key: absent
```

Currency is optional. Omission represents initial load; a supplied value represents a dropdown change.

## Context-derived APIM inputs

```text
login-user-id   <- PortalAccessContext.actor.actorUserId
login-user-role <- PortalAccessContext.actor.actorUserRole
policy-no       <- PortalAccessContext.account.policyNo
cert-no         <- PortalAccessContext.account.certNo
accountEnv      <- PortalAccessContext.account.accountEnv
trust-code      <- PortalAccessContext.account.trustCode
ref-date        <- ReferenceDatePort.resolveReferenceDate(accountEnv)
```

FE cannot override protected context fields.

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

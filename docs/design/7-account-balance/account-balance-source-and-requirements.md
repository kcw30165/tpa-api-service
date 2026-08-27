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

Required shared headers:

```text
X-Request-Id
Accept-Language
Idempotency-Key
Account-Ref
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

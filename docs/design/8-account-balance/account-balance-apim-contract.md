# Account Balance APIM Contract

**Feature:** 6.14 Account Balance  
**Baseline:** 27/08/2026  
**Status:** Consolidated design baseline, pending repository investigation  

## TRPGetMemberAccBal

Method: `POST`.

Initial request:

```json
{
  "login-user-id": "db230",
  "login-user-role": "role_user_mbr",
  "policy-no": "00000008802",
  "cert-no": 230,
  "ref-date": "31/08/2026",
  "curr": ""
}
```

Currency change sends the selected value in `curr`. The property is always present and is never null.

Successful `response.data` contains exactly one complete object with `displayOptions`, `curr`, `dispSrc`, `fundDtl`, and `accBal`. Null, empty, or multiple objects are technical failures.

## TRPGetGeneralMetaData

Method: `POST`.

Unfiltered request:

```json
{
  "env": "DB",
  "trust-code": "DBA",
  "policy-no": "00000008802",
  "metadata-name": "",
  "metadata-type": ""
}
```

Targeted request supplies Java-mapped downstream values such as `fund-pri` and `precision`.

Response rows contain:

```json
{
  "policy-no": "all",
  "metadata-name": "nav",
  "metadata-type": "precision",
  "metadata-value": "2"
}
```

TAS filters using request environment, trust, and policy. It decides whether returned rows use `policy-no=all` or the specific input policy. A response is not assumed to contain both forms.

## Java naming

```java
FUND_PRICE("fund-pri")
NET_ASSET_VALUE("nav")
UNIT_BALANCE("unit")
FX_RATE("fx-rate")

PRECISION("precision")
ROUNDING_METHOD("round-method")
```

Account Balance consumes precision only. Generic APIM transport/envelope behavior remains existing shared behavior.

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

### Ratified APIM and context details

`login-user-id` comes from `PortalAccessContext.actor().actorUserId()`, and `login-user-role` uses `ActorUserRole.toApimValue()`. Protected `certNo` remains a string through the application boundary, then `TRPGetMemberAccBal` performs explicit checked numeric conversion at its DTO boundary. Both APIM operations use runtime-configured, startup-validated paths, `ACCOUNT_ENV`, `SAFE_BUSINESS_CALL`, and executor-owned transport security. `TRPGetMemberAccBal` requires exactly one non-null top-level data item.

## fundDtl.chin-fund-fact-sheet (2026-10-13)

TAS added `chin-fund-fact-sheet` (String, Chinese fact sheet URL) to every `fundDtl` item. `AccountBalanceFundDetail` maps it as `chineseFundFactsheet`, and the adapter carries it into `RawAccountBalanceFundDetail.chineseFundFactsheet` unchanged. An absent value is `null`.

Before it was mapped, the field broke envelope parsing: the nested `fundDtl` DTO does not ignore unknown properties and the production `ObjectMapper` keeps `FAIL_ON_UNKNOWN_PROPERTIES` enabled. The failure surfaced as `ApimCryptoException: Failed to decrypt APIM response payload.` and HTTP 503 on `/api/v1/account/summary`, although no field was decrypted. Selection rules are in `account-balance-transformation-contract.md` (Factsheet).

## Unknown response properties (2026-10-13)

The nested `TRPGetMemberAccBal` DTOs (`AccountBalanceCurrency`, `AccountBalanceDisplayOptions`, `AccountBalanceDisplaySource`, `AccountBalanceFundDetail`, `AccountBalanceHolding`) now ignore unknown properties, so the failure described above can no longer be caused by an additive field. See `docs/capabilities/apim-error-logging/architecture-and-design.md` section 14.

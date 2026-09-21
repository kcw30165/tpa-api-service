# Account Balance FE-BFF API Contract

**Feature:** 6.14 Account Balance  
**Baseline:** 27/08/2026  
**Status:** Consolidated design baseline, pending repository investigation  

## Response

```json
{
  "success": true,
  "status": "SUCCESS",
  "result": {
    "data": {
      "asOfDate": "31/08/2026",
      "currencySelection": {
        "availableCurrencies": ["HKD"],
        "selectedCurrency": "HKD"
      },
      "accountOverview": {
        "displayOptions": {
          "vestedBalanceVisible": true,
          "pendingSwitchInVisible": false
        },
        "displayAccounts": [
          {
            "displayAccountId": "EE",
            "displayAccountLabel": "Member Account",
            "displaySequence": 50,
            "accountBalance": "3,881,858.53",
            "vestedBalance": "1,467,156.68"
          }
        ],
        "accountTotals": {
          "totalBalance": "11,326,994.90",
          "pendingSwitchIn": null
        }
      },
      "investmentPortfolio": {
        "displayOptions": {"fxRateVisible": true},
        "fundLevelView": {"displayAccounts": []},
        "subDisplayAccountLevelView": {"displayAccounts": []}
      }
    }
  },
  "messages": [],
  "errors": []
}
```

## Naming

```text
main account -> display account
sub account -> sub-display account
```

Use `displayAccounts`, `displayAccountId`, `displayAccountLabel`, `subDisplayAccounts`, `subDisplayAccountId`, `subDisplayAccountLabel`, and `subDisplayAccountLevelView`.

## Currency

`curr.avai` maps to `availableCurrencies`; `curr.selected` maps to `selectedCurrency`. Preserve order and require the selected item to exist in the available list.

## Visibility

- Hidden overview values stay present as `null`; `displayOptions` is authoritative.
- Hidden portfolio properties are omitted from both views.
- No `fundFactsheetEnabled` field exists.
- Blank factsheet is omitted. Malformed factsheet is safely logged and omitted.

## Exclusions

Do not echo Account-Ref, request currency, account currency, snapshot ID, fixed labels, Important Note, or price dates. One `asOfDate` applies to the page.

## Idempotency decision

`Idempotency-Key` is not required for `GET /api/v1/account/balance`. The operation is read-only and safe to repeat. The BFF does not create an idempotency record or replay store for this endpoint.

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

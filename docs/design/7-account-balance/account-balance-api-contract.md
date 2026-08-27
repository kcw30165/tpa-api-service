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

`Idempotency-Key` is not required for `GET /api/v1/account-balance`. The operation is read-only and safe to repeat. The BFF does not create an idempotency record or replay store for this endpoint.

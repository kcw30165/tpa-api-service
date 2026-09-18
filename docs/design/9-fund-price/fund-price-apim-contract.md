# Fund Price APIM/TAS Contract

## Operation 1: TRPGetFundPriceMode

### Path

```text
/ws/NGTPA/v1/TRPGetFundPriceMode
```

### Request

```json
{
  "policy-no": "00000008802",
  "ref-date": "18/09/2026"
}
```

Sources:

- `policy-no`: `PortalAccessContext.account().policyNo()`.
- `ref-date`: `ReferenceDatePort.resolveReferenceDate(accountEnv)`, formatted with configured full-date request format.

### Response item

```json
{
  "mode": "DAILY",
  "fund-price-type": "BIDOFFER PRICE",
  "available-fund-price-dates": ["18/09/2026", "18/05/2026"],
  "maximum-fund-price-history-months": 36
}
```

Validation requires one data item, a supported nonblank mode, a supported nonblank fund-price-type (BIDOFFER PRICE or UNIT PRICE), positive maximum months, and strictly parseable available dates. Available dates use the same dd/MM/yyyy full-date format as the FE/downstream request format; it is not an independently configured pattern.

## Operation 2: TRPGetFundPrice

### Path

```text
/ws/NGTPA/v1/TRPGetFundPrice
```

### Request

```json
{
  "policy-no": "00000008802",
  "from-date": "05/05/2026",
  "to-date": "05/05/2026"
}
```

### Response

The response contains one data item with `FundPrice` and `FundInfo` arrays. APIM DTOs retain exact downstream wire names. The adapter maps them into application contracts. 
`risk-level`: string representing the fund risk level. Valid values are "0.5" to "5.0" in increments of "0.5".

```json
{
    "response": {
        "err-message": "",
        "data": [
            {
                "FundPrice": [
                    {
                        "fund-code": "DUSM",
                        "price-date": "2026-05-05",
                        "bid-price": 159.7628,
                        "offer-price": 159.7628
                    },
                    {
                        "fund-code": "DSGF",
                        "price-date": "2026-05-05",
                        "bid-price": 472.9417,
                        "offer-price": 472.9417
                    },
                    {
                        "fund-code": "DHKM",
                        "price-date": "2026-05-05",
                        "bid-price": 78.82,
                        "offer-price": 78.82
                    },
                    {
                        "fund-code": "DGWF",
                        "price-date": "2026-05-05",
                        "bid-price": 631.4509,
                        "offer-price": 631.4509
                    },
                    {
                        "fund-code": "DGLE",
                        "price-date": "2026-05-05",
                        "bid-price": 363.3879,
                        "offer-price": 363.6778
                    },
                    {
                        "fund-code": "DGLB",
                        "price-date": "2026-05-05",
                        "bid-price": 283.5613,
                        "offer-price": 283.5613
                    },
                    {
                        "fund-code": "DCSF",
                        "price-date": "2026-05-05",
                        "bid-price": 323.7567,
                        "offer-price": 323.7567
                    },
                    {
                        "fund-code": "DBLF",
                        "price-date": "2026-05-05",
                        "bid-price": 522.0694,
                        "offer-price": 522.0694
                    }
                ],
                "FundInfo": [
                    {
                        "fund-code": "DBLF",
                        "fund-name": "Fidelity Balanced Fund",
                        "chin-fund-name": "Fidelity Balanced Fund",
                        "fund-seq": 60,
                        "fund-curr": "HKD",
                        "risk-level": "0.5"
                    },
                    {
                        "fund-code": "DCSF",
                        "fund-name": "Fidelity Capital Stable Fund",
                        "chin-fund-name": "Fidelity Capital Stable Fund",
                        "fund-seq": 40,
                        "fund-curr": "HKD",
                        "risk-level": "1"
                    },
                    {
                        "fund-code": "DGLB",
                        "fund-name": "PIMCO Global Bond Fund",
                        "chin-fund-name": "PIMCO Global Bond Fund",
                        "fund-seq": 30,
                        "fund-curr": "HKD",
                        "risk-level": "1.5"
                    },
                    {
                        "fund-code": "DGLE",
                        "fund-name": "BlackRock Global Equity Fund",
                        "chin-fund-name": "BlackRock Global Equity Fund",
                        "fund-seq": 80,
                        "fund-curr": "HKD",
                        "risk-level": "2"
                    },
                    {
                        "fund-code": "DGWF",
                        "fund-name": "Fidelity Growth Fund",
                        "chin-fund-name": "Fidelity Growth Fund",
                        "fund-seq": 70,
                        "fund-curr": "HKD",
                        "risk-level": "2.5"
                    },
                    {
                        "fund-code": "DHKM",
                        "fund-name": "Fidelity HKD Money Market Fund",
                        "chin-fund-name": "Fidelity HKD Money Market Fund",
                        "fund-seq": 10,
                        "fund-curr": "HKD",
                        "risk-level": "3"
                    },
                    {
                        "fund-code": "DSGF",
                        "fund-name": "Fidelity Stable Growth Fund",
                        "chin-fund-name": "Fidelity Stable Growth Fund",
                        "fund-seq": 50,
                        "fund-curr": "HKD",
                        "risk-level": "3.5"
                    },
                    {
                        "fund-code": "DUSM",
                        "fund-name": "Fidelity USD Money Market Fund",
                        "chin-fund-name": "Fidelity USD Money Market Fund",
                        "fund-seq": 20,
                        "fund-curr": "HKD",
                        "risk-level": "5"
                    }
                ]
            }
        ]
    }
}
```

Use TRPGetGeneralMetaData to obtain precision for fund price.

```json
{"policy-no":"all","metadata-name":"fund-pri","metadata-type":"precision","metadata-value":"4"}
```

## Reconciliation invariants

- Each `fund-code` appears at most once in each array.
- Every Fund Price record has exactly one Fund Info record.
- Every Fund Info record has exactly one Fund Price record in the returned range where the contract expects one-to-one data.
- Null, blank, duplicate, missing, or unmatched required values are invalid downstream contracts.
- Prices remain `BigDecimal`; Java does not invent rounding.

## Error rule

Every nonblank downstream `err-message`, including `err.tpa.invalid.date` and `err.tpa.no.fund.price`, maps to HTTP 503 `SYSTEM_ERROR` with the repository generic unavailable code. Raw text is never public.

## Routing and transport

Both operations use typed `ApimExchangeRequest`, `ACCOUNT_ENV`, caller-supplied actor role and account environment, and `SAFE_BUSINESS_CALL`. Feature code must not directly access facade, token, certificate, crypto, or profile services.

### Operation 3: TRPGetGeneralMetaData (fund price precision)

#### Purpose

Resolves fund price decimal precision. Confirmed request/response shape (env-scoped, policy-no "all"):

```
{
  "policy-no": "all",
  "metadata-name": "fund-pri",
  "metadata-type": "precision",
  "metadata-value": "4"
}
```

Precision varies by environment, trust-code, and policy-no and must be cached in Redis following the existing Account Balance acbal-pcnt pattern; implementation must confirm the exact key shape and TTL from that precedent before coding. fund-price-type governs which price fields are returned and precision governs decimal formatting; both are internal-only and are never returned to FE.

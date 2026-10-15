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
  "maximum-fund-price-history-months": 36,
  "max-risk-level": "5",
  "available-fund-price-dates": ["18/09/2026", "18/05/2026"]
}
```

`max-risk-level` is a string holding a floating-point value (e.g. `"5"`, `"0.5"`), a sibling field of `mode`/`fund-price-type`/`maximum-fund-price-history-months`/`available-fund-price-dates` on the same data item. It is deliberately NOT validated: a blank or absent value is legitimate and does not fail the mode fetch, unlike the other four fields. Java caches it verbatim in the authenticated session at mode-fetch time. On a later `GET ?date=` response, Java echoes the cached `max-risk-level` to FE only when that specific response's fund array carries a `risk-level` field on at least one fund; if no fund in that response has a risk level, `max-risk-level` is omitted from that response regardless of what is cached.

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
  "to-date": "05/05/2026",
  "fund-list": ""
}
```

`fund-list`: a comma-joined string of fund codes, added to support a later fund-comparison feature. For all current (non-comparison) usages it is always sent as an empty string `""`. This field is populated solely inside `ApimFundPriceSourceAdapter`; it is not threaded through `FetchFundPriceCommand`, `FetchFundPriceRangeCommand`, the fund-price use cases, or the controller -- those layers have no fund-code concept today. Fund comparison is out of scope for this change and may introduce its own command shape rather than reusing these two commands.

### Response

The response contains one data item with `FundPrice` and `FundInfo` arrays. APIM DTOs retain exact downstream wire names. The adapter maps them into application contracts. 
`risk-level`: string representing the fund risk level. The downstream system emits the
literal strings `"0.5"`, `"1"`, `"1.5"`, `"2"`, `"2.5"`, `"3"`, `"3.5"`, `"4"`,
`"4.5"`, `"5"` -- whole numbers are NOT zero-padded to one decimal place (`"1"`, not
`"1.0"`). An empty string is a legitimate value meaning "no risk level assigned" and
is not an error. Java parses the value to `BigDecimal` for ordering only, and echoes
the raw downstream string verbatim to FE without reformatting. A nonblank value
outside this set is an invalid downstream contract and fails closed as HTTP 503
`SYSTEM_ERROR`.

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

- `fund-code` is unique within the `FundInfo` array.
- The pair (`fund-code`, `price-date`) is unique within the `FundPrice` array. A
  `fund-code` legitimately appears once per price date across a MONTHLY or export
  range.
- Every `FundPrice` record resolves to exactly one `FundInfo` record by `fund-code`.
- A `FundInfo` record with no `FundPrice` record in the returned range is NOT an
  error; the stricter one-to-one expectation applies only to the single-date DAILY
  case.
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

## Response size limit

APIM response bodies are buffered in memory before decryption and parsing. The limit is
`apim.max-response-size` (env `APIM_MAX_RESPONSE_SIZE`, default `16MB`), applied to APIM
business calls in `ApimWebClientFacade`. Spring's 256 KB codec default is too small for the
36-month `TRPGetFundPrice` export range, which previously failed with
`DataBufferLimitException` and surfaced as HTTP 503 `SYSTEM_ERROR`. A response larger than the
configured limit fails the same way and is logged as `apim.exchange.failed`.

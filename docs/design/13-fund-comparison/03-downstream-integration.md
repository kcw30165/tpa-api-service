# 3. Downstream Integration

## 3.1 New Fund Comparison options operation

### Downstream endpoint

```http
POST /TRPGetFundCmpOpt
```

### Request

```json
{
  "policy-no": "$policy-no"
}
```

### Response shape

```json
{
  "response": {
    "err-message": "",
    "data": [
      {
        "time-range": "10D,1M,3M,6M,1Y,YTD,3Y",
        "fund-list": [
          {
            "fund-code": "FAEU",
            "fund-name": "Fidelity Advantage Portfolio Fund - Asia Pacific Equity Fund",
            "chin-fund-name": "富達優越投資組合 - 亞太股票基金"
          }
        ]
      }
    ]
  }
}
```

Expected configured time-range families are:

- `10D,1M,3M,6M,1Y,YTD,3Y`
- `3M,6M,1Y,YTD,3Y`

The BFF remains defensive and normalizes any received string according to the validation rules.

The options operation is unusable and maps to `err.fund-comparison.retrieval.unavailable` when any of the following applies:

- the downstream envelope is missing or malformed;
- `err-message` is missing or nonblank;
- options `data` is missing, empty, or structurally unusable;
- `time-range` is missing, null, or blank;
- no valid time-range token remains after normalization.

Invalid tokens may be dropped only when at least one valid token remains.

## 3.2 Existing Fund Price operation

### Downstream endpoint

```http
POST /TRPGetFundPrice
```

### Extended request

```json
{
  "policy-no": "00000008802",
  "from-date": "06/10/2026",
  "to-date": "15/10/2026",
  "fund-list": "FAEU,FBAU"
}
```

`fund-list` is a comma-separated string without spaces. Existing Fund Price callers use an empty value:

```json
{
  "fund-list": ""
}
```

The extension must preserve all current callers. Confirmed (Nick): Fund Comparison introduces its own dedicated command type (not an extension of FetchFundPriceRangeCommand), plus its own outbound port and adapter. The existing FetchFundPriceRangeCommand, FundPriceSourcePort, and ApimFundPriceSourceAdapter remain completely unmodified and continue to send fund-list="" exactly as today; Fund Comparison's new adapter builds its own TrpGetFundPriceApimRequest with the comma-joined selected fund codes, reusing only the shared downstream DTOs and FundPriceApimContentValidator for response reconciliation.

### Downstream response

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
          }
        ]
      }
    ]
  }
}
```

### Mapping

- `fund-code` -> `fundCode`
- parsed `price-date` -> `priceDate` formatted `dd/MM/yyyy`
- `bid-price` -> `unitPrice`
- `offer-price` -> not exposed

The adapter should reuse the existing Fund Price envelope parsing and localized fund-name behavior rather than creating parallel logic.

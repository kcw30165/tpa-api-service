# Fund Price Excel Export Contract

## Endpoint

```http
GET /api/v1/fund-price/export
```

## File

- Name: `fund_price_info.xlsx`
- Format: Office Open XML workbook
- Media type: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`

## Range

Java derives:

```text
from-date = referenceDate.minusMonths(maximumFundPriceHistoryMonths)
to-date   = referenceDate
```

Both are formatted with the configured downstream full-date format. Java must not hardcode 36 months. No batching or pagination is invented without downstream evidence.

## Rows and sorting

- First sort: price date descending.
- Second sort: English fund name ascending.
- Fields and values align with the web response contract.
- Price cells are TEXT values combining fund-curr and the price value (e.g. "HKD 103.87"), matching the confirmed sample workbook -- not numeric cells.
- Downstream decimal precision is preserved, formatted using the precision resolved from TRPGetGeneralMetaData (metadata-name=fund-pri, metadata-type=precision), cached per the existing Account Balance pattern.
- Java does not force two decimal places; format using the precision resolved from TRPGetGeneralMetaData (fund-pri/precision), which is the approved rule for decimal places.

## Localization

`Accept-Language` selects one configured header language. No `lang` query parameter is added.

## Safety

Workbook bytes, downstream response bodies, account identifiers, and session metadata must not be logged. Export failure returns the standard public 503 system response where the web stack can negotiate the error response.

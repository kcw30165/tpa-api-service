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

- First sort: price date descending (latest first).
- Second sort: risk level ascending (lowest risk first). An empty risk level ranks as
  the highest risk and therefore sorts last.
- Third sort: English fund name ascending.
- Risk level is a sort key only. There is no risk level column in the workbook.
- Fields and values align with the web response contract.
- Price cells are NUMERIC cells. Currency is carried in its own separate TEXT column
  sourced from fund-curr. The earlier combined-text treatment is superseded.
- Downstream decimal precision is preserved, formatted using the precision resolved from TRPGetGeneralMetaData (metadata-name=fund-pri, metadata-type=precision), cached per the existing Account Balance pattern.
- Java does not force two decimal places; format using the precision resolved from TRPGetGeneralMetaData (fund-pri/precision), which is the approved rule for decimal places.

## Columns

The column set is selected by the cached fund-price-type. Header cells that carry no
language-bearing content concatenate the English and Chinese label directly, with no
separator. Fund Name and the Chinese fund name remain two distinct columns and are
never concatenated.

### UNIT PRICE -- 5 columns, in order

| # | Header | Source |
|---|--------|--------|
| 1 | Fund Price Date基金價格日期 | FundPrice[i].price-date |
| 2 | Fund Name | FundInfo.fund-name |
| 3 | 基金名稱 | FundInfo.chin-fund-name |
| 4 | Unit Price Currency 單位貨幣 | FundInfo.fund-curr |
| 5 | Unit Price單位價格 | FundPrice[i].bid-price |

### BIDOFFER PRICE -- 7 columns, in order

| # | Header | Source |
|---|--------|--------|
| 1 | Fund Price Date基金價格日期 | FundPrice[i].price-date |
| 2 | Fund Name | FundInfo.fund-name |
| 3 | 基金名稱 | FundInfo.chin-fund-name |
| 4 | Bid Price Currency買入單位貨幣 | FundInfo.fund-curr |
| 5 | Bid Unit Price單位價格 | FundPrice[i].bid-price |
| 6 | Offer Price Currency賣出單位貨幣 | FundInfo.fund-curr |
| 7 | Offer Price單位價格 | FundPrice[i].offer-price |

fund-curr is intentionally emitted twice under BIDOFFER PRICE, once per price column
pair. The Chinese fund name cell is left blank when chin-fund-name is absent and is
never backfilled from the English name.

## Localization

The export is always bilingual and its column set does not vary by request language:
both Fund Name and 基金名稱 are always present, and every other header carries its
English and Chinese label concatenated. Accept-Language therefore does not select the
workbook header language. No `lang` query parameter is added.

## Safety

Workbook bytes, downstream response bodies, account identifiers, and session metadata must not be logged. Export failure returns the standard public 503 system response where the web stack can negotiate the error response.

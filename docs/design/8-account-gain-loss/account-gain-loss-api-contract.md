# Account Gain/Loss API Contract

`GET /api/v1/account/gain-loss?period=SINCE_SCHEME_INCEPTION|YEAR_TO_DATE`

The default period is `SINCE_SCHEME_INCEPTION`. Downstream mappings are `ssi` and `ytd`.

Both `gainLoss` and `totalGainLoss` are display-ready strings. Java sums raw `BigDecimal` values before rendering both fund and total values with effective `nav` plus `precision` metadata. Rendering uses an exact scale and must not round.

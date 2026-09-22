# Fund Price Decisions and Open Questions

## Approved decisions

- Three BFF endpoints are `/mode`, price query, and `/export` under `/api/v1/fund-price`.
- Mode metadata is loaded on page entry and stored in authenticated session state.
- `ReferenceDatePort` supplies the mode request reference date.
- `accountEnv` and policy number come from trusted selected-account context.
- Java does not set downstream-only `X-Datadomain`.
- `DAILY` and `MONTHLY` have defined Java behavior.
- Unsupported mode fails closed; Java does not invent `WEEKLY`.
- FE/downstream full date and FE month formats are separately configurable. The available-date response format is dd/MM/yyyy, aligned with the FE/downstream full-date format -- it is NOT an independently configured pattern (correcting an earlier yyyy-MM-dd assumption in this design pack).
- All nonblank downstream errors map to HTTP 503 `SYSTEM_ERROR`.
- Duplicate, missing, or unmatched Fund Price/Fund Info data maps to HTTP 503 `SYSTEM_ERROR`.
- Export filename is `fund_price_info.xlsx`.
- Export uses cached maximum history months and no FE range.
- Export sorting is price date descending, then risk level ascending (empty risk level
  ranks as the highest risk and therefore sorts last), then English fund name ascending.
- Web default sorting is risk level ascending (empty risk level ranks as the highest
  risk and therefore sorts last), then English fund name ascending, then unit price
  ascending, where unit price is the downstream bid-price.
- Risk level is IN SCOPE. It is sourced from FundInfo.risk-level on TRPGetFundPrice.
  It is a web response field, a web sort key, and an export sort key. It is NOT an
  export column. Empty risk level ranks as the highest risk in both sort orders.
- Web riskLevel emission is result-set scoped: when at least one fund in the result set
  carries a risk level, riskLevel is emitted on every row (absent values as null); when
  no fund in the result set carries one, the key is absent from every row.
- fund-seq is not mapped, not exposed to FE, and not used as a sort key. It is dropped
  at the adapter boundary.
- Feature-specific JaCoCo instruction coverage gate is at least 95.00%.
- Fund class and employer group filtering are owned entirely by the downstream system; Java implements no filtering logic for either.
- fund-price-type (BIDOFFER PRICE | UNIT PRICE) is returned by TRPGetFundPriceMode, cached as part of the mode Redis record, and used only internally to shape price/export field selection (bidUnitPrice+offerUnitPrice vs. a single unitPrice sourced from bid-price); it is never returned to FE, which owns all labelling.
- Fund price decimal precision is sourced from TRPGetGeneralMetaData (metadata-name=fund-pri, metadata-type=precision), cached in Redis following the existing Account Balance acbal-pcnt pattern, and varies by environment, trust-code, and policy-no.
- Export price cells are NUMERIC. Currency is a separate TEXT column sourced from
  fund-curr. The earlier combined-text decision (e.g. "HKD 103.87" in one cell) is
  superseded and must not be implemented.
- Export includes one Fund Name column and one 基金名稱 column; the Chinese column is left blank when chin-fund-name is absent from downstream data -- it is not backfilled from the English name.

## Open implementation-evidence decisions

These are not business assumptions. They must be resolved from live repository evidence during implementation planning:

1. Exact physical authenticated-session field and JSON compatibility approach.
2. Exact safe atomic update mechanism for adding account-scoped metadata.
3. Exact existing standard read-response type used by analogous authenticated controllers.
4. Exact existing validation error code used for unavailable/out-of-range `date`.
5. Exact YAML property namespace and message keys following current configuration conventions.
6. (Resolved) Risk level is a sort key only and is never an XLSX column, so no fixed-column treatment is required when a risk level is absent.
7. Exact test and documentation files that form the authoritative APIM operation ledger and guard.

No production code may guess these. Prompt 001 should first characterize them and add genuine fail-first tests.

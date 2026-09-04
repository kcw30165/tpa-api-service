# Account Gain/Loss Handover

## Current stage
Foundation contracts implemented on baseline `9cb1e1f65dcea6383ed54d5c9ef034a97ed7abec`.

## Locked decisions
- Public periods are `SINCE_SCHEME_INCEPTION` and `YEAR_TO_DATE`.
- APIM modes are `ssi` and `ytd`.
- Public `gainLoss` and `totalGainLoss` are strings.
- Raw arithmetic uses `BigDecimal`.
- Required metadata is exactly NET_ASSET_VALUE plus PRECISION, corresponding to `nav` plus `precision`.
- Java sums before rendering and must not round.
- `err.tpa.fund.swithing.in.progress` maps to `err.fund.swithing.in.progress`.

## Next stage
Add the reactive use case, metadata orchestration, TRPGetInvestGL adapter/configuration, controller, response policy, routing ledger, and focused tests.

## Foundation repair
The application period enum is downstream-independent. Future adapter code owns `ssi` and `ytd`. Exact number formatting is extracted to the feature-neutral `ExactPrecisionRenderer`; Account Balance and Account Gain/Loss translate failures into their own typed exceptions. Focused tests lock raw/public type separation, immutable collections, exact rendering, no rounding, and Account Balance preservation.

## APIM and application boundary stage
Added trusted get/fetch commands, the reactive use-case and source-port contracts, exact TRPGetInvestGL request/response DTOs, adapter-owned `ssi`/`ytd` mapping, strict one-item and fund validation, and startup-validated APIM path properties. Empty `InvestGL` is explicitly valid and will produce zero at the assembler stage. Executor invocation, metadata orchestration, aggregation, controller integration, response policy, routing ledger, and YAML binding remain for the next stage.

## Runtime core implementation
Implemented the executor-backed TRPGetInvestGL adapter, checked certificate conversion, safe downstream rejection logging, exact raw mapping, ReferenceDate and NAV-precision orchestration, exact BigDecimal overview aggregation, shared description selection, string rendering, runtime beans, and both active path configuration seams. Controller, web response policy, routing-ledger update, runtime tests, and final stabilization remain outstanding.

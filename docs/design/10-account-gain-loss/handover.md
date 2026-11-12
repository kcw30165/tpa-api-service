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
- `err.tpa.fund.switching.in.progress` maps to `err.fund.switching.in.progress`.

## Next stage
Add the reactive use case, metadata orchestration, TRPGetInvestGL adapter/configuration, controller, response policy, routing ledger, and focused tests.

## Foundation repair
The application period enum is downstream-independent. Future adapter code owns `ssi` and `ytd`. Exact number formatting is extracted to the feature-neutral `ExactPrecisionRenderer`; Account Balance and Account Gain/Loss translate failures into their own typed exceptions. Focused tests lock raw/public type separation, immutable collections, exact rendering, no rounding, and Account Balance preservation.

## APIM and application boundary stage
Added trusted get/fetch commands, the reactive use-case and source-port contracts, exact TRPGetInvestGL request/response DTOs, adapter-owned `ssi`/`ytd` mapping, strict one-item and fund validation, and startup-validated APIM path properties. Empty `InvestGL` is explicitly valid and will produce zero at the assembler stage. Executor invocation, metadata orchestration, aggregation, controller integration, response policy, routing ledger, and YAML binding remain for the next stage.

## Runtime core implementation
Implemented the executor-backed TRPGetInvestGL adapter, checked certificate conversion, safe downstream rejection logging, exact raw mapping, ReferenceDate and NAV-precision orchestration, exact BigDecimal overview aggregation, shared description selection, string rendering, runtime beans, and both active path configuration seams. Controller, web response policy, routing-ledger update, runtime tests, and final stabilization remain outstanding.

## Runtime hardening repair
The shared description resolver now preserves selected and fallback text exactly, including whitespace, so Account Balance behavior remains unchanged. Gain/Loss service orchestration is enclosed in `Mono.defer`. Downstream `err-message` values are logged only when they match a bounded error-key form of at most 128 characters; unsafe or free-text values are represented by `UNRECOGNIZED_APIM_ERROR`. Added focused shared-description, Account Balance preservation, Gain/Loss runtime-boundary, metadata-scope, aggregation-order, and executor-routing tests. Controller and public response integration remain blocked until this repair is reviewed.

## Runtime hardening compile repair
Replaced the complete Gain/Loss APIM adapter after the failed hardening writer placed `safeErrorCode` inside `fetch`. The sanitizer is now a class-level private static method. All prior localization-preservation, `Mono.defer`, safe logging, and focused test changes were preserved cumulatively.

## Public endpoint stage
Added `GET /api/v1/account/gain-loss` with optional `period`, defaulting to `SINCE_SCHEME_INCEPTION`. The controller reuses selected-account and request-language resolution and returns the approved nested response with string monetary fields. The global exception handler delegates typed Gain/Loss failures to the response policy: switching is HTTP 422/business rejection and system failure is HTTP 503. `TRPGetInvestGL` is recorded in the routing ledger as ACCOUNT_ENV, SAFE_BUSINESS_CALL, executor-owned; ledger cardinality is 18.

## Public endpoint compile repair
Corrected the routing ledger after the initial endpoint writer inserted `TRPGetInvestGL` into the existing Account Balance entry. The recovery normalizes the Account Balance entry to eight arguments and preserves one standalone Gain/Loss entry plus one expected-operation entry.

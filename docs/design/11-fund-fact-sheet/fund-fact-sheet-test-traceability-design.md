# Fund Fact Sheet Test and Coverage Design

## Coverage tiers

| Tier | Classes | Instruction | Branch |
|---|---|---|---|
| DTOs, records, validators, pure functions | `UrlValidator`, DTOs, commands, results, responses, `FundFactSheetNormalizer`, properties | >= 95% gated | >= 95% gated |
| Adapters, use cases, controllers | Adapter, service, controller, mapper, handler method | >= 95% gated | 85-90% gated; each uncovered branch justified in one line |

No exclusions, relocation, denominator manipulation or production-only seams. The repository JaCoCo configuration already excludes `*Config` and `*Properties` classes, so `FundFactSheetApimProperties`, `FundFactSheetApimValidationConfig` and `FundFactSheetApplicationConfig` are tested for behaviour but do not appear in the report; this delivery adds no exclusion of its own.

## Required scenarios

### UrlValidator
- Valid http/https absolute URLs returned verbatim (including query strings).
- null, empty, blank: empty, no event.
- Relative path, unparseable text: empty, exactly one event each.
- `javascript:` / `data:` absolute values pass (documents OQ-12).
- Leading/trailing whitespace is not trimmed.
- Null callback rejected at construction.

### APIM adapter
- Exact `ApimExchangeRequest`: path, request DTO (`policy-no`, `ref-date`), types, `ACCOUNT_ENV`, credential context, `SAFE_BUSINESS_CALL`.
- `ref-date` formatted `dd/MM/uuuu` (e.g. 05/05/2026, 29/02/2028).
- Flattening across multiple `data[]` items preserves order.
- `err.tpa.no.fund.fact.sheet` with `data` absent returns zero rows.
- `err.tpa.no.fund.fact.sheet` with `data` present also returns zero rows (the code decides).
- Empty `FundFactSheet` list inside a non-null item contributes zero rows.
- Empty `err-message` with `data: []`: system failure.
- Non-empty `err-message` (`err.scheme.invalid` and unknown): system failure.
- `response` null, `err-message` null, `data` null, null item, null list, null row: system failure.
- A null data item, list or row logs one value-free adapter event; no URL, name, `seq` or policy number appears in any adapter log.
- Transport error: system failure, no raw text in message.
- Properties: blank path fails startup; production constructor delegates.

### Normalizer
- Missing `seq` dropped with `missingSeq`.
- Duplicate `seq`: first occurrence kept, later dropped, `duplicateSeq` per dropped row.
- Blank and null `name-eng` kept with `blankEnglishName`.
- Null and blank `name-chi` kept without event.
- Malformed link omitted with `malformedFactsheet`; valid link kept verbatim.
- Event port methods take no arguments (architecture guard).

### Use case
- Reference date resolved with `accountEnv`; the fetch command carries `policyNo`, `referenceDate`, `accountEnv` and `actorUserRole`.
- A `FundFactSheetRetrievalException` passes through unchanged; any other failure maps to `systemFailure()`.
- A no-fund source result gives an empty `FundFactSheetResult`.
- The command carries no language.

### Web
- `en`: `fundName` = `nameEng`, `factsheetUrl` = `linkEng`.
- `zh_HK`: `fundName` = `nameChi`, falls back to `nameEng` when null or blank; `factsheetUrl` = `linkChi`, no fallback when absent.
- `fundNameZh` null serialized as `null`; blank passed through.
- Absent link omitted from JSON.
- Sort by display name (case-insensitive, `Locale.ROOT`), then `seq`; English before Chinese in `zh_HK`.
- No-fund result returns `rows: []`.
- Envelope shape `success/status/result.data.rows/messages/errors`.
- Route and envelope are tested with `WebTestClient.bindToController(controller)` (the repository has no `@WebFluxTest` usage), with the real `ApiExceptionHandler` as controller advice for the 503 path.
- Handler: 503, `SYSTEM_ERROR`, code `err.fund-fact-sheet.retrieval.unavailable`, type `SYSTEM`. No `message` field; the resolver is never called.

### Guards
- Routing ledger contains `TRPGetFundFactSheet` once; size 20.
- Application layer has no Spring dependency (existing `CleanArchitectureDependencyTest`).
- No URL, name or `seq` values in feature log statements.
- Account Balance sources unchanged (diff check in stabilization).
- Before each stage gate, run the architecture guards listed in the implementation plan section 1.7, then the full suite.

## Stage gates

Before each stage transition: full Maven test suite passes and Spring startup smoke test passes.

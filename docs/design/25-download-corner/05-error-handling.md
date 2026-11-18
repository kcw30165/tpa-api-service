# 05 — Error Handling

## Failure matrix

Every listed retrieval failure maps to HTTP 503 and public code **`NEW (proposed) err.download-corner.retrieval.unavailable`**. For error events, the proposed event name is **`NEW (proposed) download-corner.retrieval.downstream_failure`**, capability **`NEW (proposed) DOWNLOAD_CORNER`**, and operation `TRPGetDownloadCorner`.

| Condition | Failure origin / classification | Reason | `internalCode` | Public code | HTTP |
|---|---|---|---|---|---:|
| Non-empty `err-message` is `err.tpa.login.user.id.invalid`, `err.tpa.cert.no.not.found`, or `err.tpa.cert.no.required` | `DOWNSTREAM_CONTRACT`; account-context mismatch, following Vesting | `ACCOUNT_CONTEXT_MISMATCH` | Exact raw `err-message` (deliberate difference from Vesting) | `err.download-corner.retrieval.unavailable` (**NEW, proposed**) | 503 |
| Any other non-empty `err-message` | `DOWNSTREAM_CONTRACT`; unmapped error, following Vesting | `UNMAPPED_ERROR_CODE` | Exact raw `err-message` | Same proposed code | 503 |
| Missing/null `response` | `DOWNSTREAM_INVALID_RESPONSE`; malformed response, following Vesting | `MALFORMED_RESPONSE` | `null`, following Vesting for malformed cases | Same proposed code | 503 |
| Empty `err-message` and missing/null/empty `data` | `DOWNSTREAM_INVALID_RESPONSE` | `MALFORMED_RESPONSE` | `null` | Same proposed code | 503 |
| Empty `err-message` and `data` size other than one, including a null sole item | `DOWNSTREAM_INVALID_RESPONSE` | `MALFORMED_RESPONSE` | `null` | Same proposed code | 503 |
| Empty `err-message`; `categories` missing or null | `DOWNSTREAM_INVALID_RESPONSE` | `MALFORMED_RESPONSE`; malformed field `response.data[0].categories` | `null` | Same proposed code | 503 |
| Empty `err-message`; nested `items` missing or null | `DOWNSTREAM_INVALID_RESPONSE` | `MALFORMED_RESPONSE`; malformed field `response.data[0].categories[i].items` | `null` | Same proposed code | 503 |
| Empty `err-message`; `category-seq` missing, null, or non-numeric | `DOWNSTREAM_INVALID_RESPONSE` | `MALFORMED_RESPONSE`; malformed field `response.data[0].categories[i].category-seq` | `null` | Same proposed code | 503 |
| Empty `err-message`; `item-seq` missing, null, or non-numeric | `DOWNSTREAM_INVALID_RESPONSE` | `MALFORMED_RESPONSE`; malformed field `response.data[0].categories[i].items[j].item-seq` | `null` | Same proposed code | 503 |
| Empty `err-message`; `category-name-eng` missing, null, or blank | `DOWNSTREAM_INVALID_RESPONSE` | `MALFORMED_RESPONSE`; malformed field `response.data[0].categories[i].category-name-eng` | `null` | Same proposed code | 503 |
| Empty `err-message` and other DTO/tree parse or required-shape failure | `DOWNSTREAM_INVALID_RESPONSE` | `MALFORMED_RESPONSE`; use the parser-resolved malformed field path | `null` | Same proposed code | 503 |
| Other transport/timeout/downstream technical failure | Shared APIM classification, then feature retrieval-unavailable mapping | Existing classifier category / origin | `null` unless a non-empty DS error code exists | Same proposed code | 503 |

Missing/null English item name, link, or file name is not malformed: treat each as blank, then apply L5 fallback and L6 blank-link filtering. Missing/null Chinese category name, item name, link, or file name is likewise blank and falls back to English under L5. Empty `categories` and `items` arrays are valid; an empty category is preserved under L8. These conditions do not generate malformed-response events.

Vesting checks non-empty `err-message` before data cardinality, classifies the three context codes separately from other codes, and emits malformed-field details (`ApimVestingScaleAdapter.java:113-138,177-203,221-229`). Its feature exception maps to HTTP 503 (`VestingScaleExceptionMappings.java:15-34`). Download Corner adopts that classification and status while retaining raw non-empty DS codes in `internalCode`, as CIM does (`ApimInvestmentChoiceCancelAdapter.java:130-167`).

## Failure-event payload and malformed diagnostics

Publish through the existing `FailureEventPort`, from the adapter, as Vesting does (`ApimVestingScaleAdapter.java:177-219`; `FailureEventPort.java:5-10`). Use the shared request-correlation context value. For each non-empty DS error, copy the exact raw `err-message` to `internalCode`; CIM's adapter sets `internalCode = error.code()` before publishing (`ApimInvestmentChoiceCancelAdapter.java:130-167,183-195`). This is deliberately different from Vesting, whose event constructor supplies `null` (`ApimVestingScaleAdapter.java:205-218`).

For malformed JSON/tree data, record which field failed using the same dimensions as Vesting: `reason`, `malformedField`, and `malformedReason` (`ApimVestingScaleAdapter.java:194-203`). E2 paths include `response.data[0].categories`, `response.data[0].categories[i].items`, the relevant `category-seq`/`item-seq`, and `category-name-eng`; missing response/data and other nested DTO failures use their parser-resolved paths. Do not put document contents, links, filenames, account identifiers, or raw request bodies in event dimensions.

Use terminal-caller incident ownership and the same safe-read retry behavior as Vesting (`ApimVestingScaleAdapter.java:83-105`). No new logging mechanism, public diagnostic detail, or shared logger change is part of this feature.

## Exception mapping and registration

Add **`NEW (proposed) DownloadCornerExceptionMappings`** for the feature retrieval exception, returning the proposed public code with HTTP 503. Add an explicit entry to `ExceptionMappingRegistryConfig`, whose current registry is a manually assembled list (`ExceptionMappingRegistryConfig.java:25-63`). Add a focused **`NEW (proposed)` registration test** that resolves the Download Corner exception through the configured registry. The source investigation found no existing registration test for this proposed feature.

## Follow-up

Do not change Vesting Scale in this feature. A separate follow-up, not on this branch, will make Vesting record raw non-empty `err-message` as `internalCode` in the same way CIM does.

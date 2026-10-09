# Fund Fact Sheet Transformation Contract

## 1. Flatten (adapter)

Collect every row from every `data[].FundFactSheet[]`, preserving TAS order, into `RawFundFactSheetData(List<RawFundFactSheetRow>)`. A `FundFactSheet` list that is empty in every item yields an empty list (no funds). Structural nulls are system failures (APIM contract). The adapter does not validate row content; it preserves raw values.

`RawFundFactSheetRow(Integer seq, String nameEng, String nameChi, String linkEng, String linkChi)`

## 2. Normalize rows (application, `FundFactSheetNormalizer`)

| Condition | Action | Event (no values logged) |
|---|---|---|
| `seq` null | Drop row | `missingSeq` |
| `seq` duplicated | Keep the first occurrence in TAS order; drop later ones. Downstream guarantees uniqueness; this is defensive | `duplicateSeq` per dropped row |
| `name-eng` null or blank | Keep row unchanged (mandatory downstream; defensive) | `blankEnglishName` |
| `name-chi` null or blank | Keep row; value passed through as received | None |
| `link-eng` / `link-chi` | Validate each with `UrlValidator` | `malformedFactsheet` when rejected |

Both links are validated during normalization, so a malformed link in the language not requested still produces an event. This keeps normalization language-independent.

Events go through `FundFactSheetPresentationEventPort` (no-argument methods) implemented by `FundFactSheetPresentationEventLogger`, mirroring `AccountBalancePresentationEventPort`. Log lines use `event=fund-fact-sheet.<name>`; `seq`, names, URLs, policy number and account identifiers are never logged.

Output: `FundFactSheetResult(List<FundFactSheetRowResult>)` with `FundFactSheetRowResult(int seq, String nameEng, String nameChi, String linkEng, String linkChi)`; links are already validated and null when absent.

## 2a. Account identifier check (use case)

Before the fetch, `GetFundFactSheetService` requires `policyNo`, `certNo` and `schemeType` to be non-null and non-blank. If any is absent it reports `missingAccountIdentifier` (value-free, `event=fund-fact-sheet.missing-account-identifier`) and fails with `FundFactSheetRetrievalException.systemFailure()`; the source port is not called (decisions C-7). The event port therefore has five no-argument methods.

## 3. Language selection (web, `FundFactSheetWebMapper`)

| Language | `fundName` | `factsheetUrl` |
|---|---|---|
| `zh_HK` (`RequestLanguageResolver.isZhHk`) | `nameChi`; `nameEng` when `nameChi` is null or blank | `linkChi`; omitted when absent. No fallback to `linkEng` |
| otherwise | `nameEng` | `linkEng`; omitted when absent |

`DynamicDescriptionResolver` is not reused: for `en` it falls back to the Chinese name, and with both names blank it throws, which contradicts D-7 and D-10. Name selection is implemented locally in the web mapper (as `FundPriceWebMapper.displayFundName` does).

## 4. Sort (web)

Single level on the selected `fundName`, using the Fund Price fund-name comparison:

```java
Comparator.comparing((Row r) -> nullToEmpty(r.fundName()).toLowerCase(Locale.ROOT))
          .thenComparingInt(Row::seq)
```

- No `Collator`; English names sort before Chinese names in `zh_HK`.
- A blank or null display name (defensive case only) sorts first.
- Fund Price's risk-level, price and fund-code levels do not apply.
- Fund Price's private comparator is not shared; the rule is implemented locally.

## 5. Map

`FundFactSheetRowResponse(int seq, String fundName, String fundNameEn, String fundNameZh, @JsonInclude(NON_NULL) String factsheetUrl)` inside `FundFactSheetResponse(List rows)` inside `FundFactSheetResultResponse(data)` inside `MutationResponse.success(ApiStatus.SUCCESS, ..., List.of())`.

`fundNameEn` and `fundNameZh` are passed through as received; a null value is serialized as `null` (not omitted). Only `factsheetUrl` is omitted when absent. Do not use a class-level `@JsonInclude(NON_NULL)` (as `FundPriceRowResponse` does): it would drop `fundNameZh: null`. The repository has no global Jackson inclusion setting, so the default serializes null as `null`.

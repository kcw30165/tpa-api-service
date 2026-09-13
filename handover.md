# Handover: asOfDate sourcing refactor (account-balance / account-gain-loss)

## Stage
03 - Investigation only (final pre-implementation pass; no code changes applied)

## Resolved requirement mapping
- Requirement 1 (account-balance): `/api/v1/account-balance`'s
  `result.data.asOfDate` must switch from Java-computed (`referenceDate`)
  to APIM-supplied `TRPGetMemberAccBal` `response.data[0].as-of-date`
  (a brand-new field; `TrpGetMemberAccBalApimDataItem` never had a date
  field before).
- Requirement 2 (account-gain-loss): the user's wording named
  "TRPGetMemberAccBal" but the described current mapping
  ("result.data.asOfDate maps to apim's response.data[0].pri-date")
  only matches the Account GAIN LOSS flow: `TrpGetInvestGlApimDataItem
  .priceDate <- @JsonProperty("pri-date")`, flowing straight through
  `RawAccountGainLossData.priceDate` -> `AccountGainLossAssembler` ->
  `AccountGainLossResult.asOfDate` -> `AccountGainLossDataResponse.asOfDate`
  = `result.data.asOfDate` for `/api/v1/account-gain-loss`. This is also
  consistent with the branch name
  `refactor/use-as-of-date-from-progress-for-ab-and-gl` (ab = account
  balance, gl = gain loss). Treated as confirmed; only the wire
  `@JsonProperty` annotation value changes from `pri-date` to
  `as-of-date` on the existing `priceDate` field (Java field name is
  left unchanged to keep the diff minimal and scoped to the wire
  contract, per the requirement as stated).

## Full planned diff for script 04 (writer)
### Account Balance (new field)
1. `TrpGetMemberAccBalApimDataItem`: add
   `@JsonProperty("as-of-date") LocalDate asOfDate`.
2. `AccountBalanceApimContentValidator.requireSingleData`: require
   `item.asOfDate() != null` (mirrors GL's `priceDate() == null` check).
3. `RawAccountBalanceData`: add `LocalDate asOfDate` component.
4. `ApimAccountBalanceSourceAdapter.map()`: pass `x.asOfDate()` through.
5. `DefaultAccountBalanceAssembler.build()`: replace `d.format(DATE)`
   with `r.asOfDate().format(DATE)` (raw APIM value instead of the
   Java/ReferenceDatePort-resolved `referenceDate`).
6. Test updates: `ApimAccountBalanceSourceAdapterTest`,
   `AccountBalanceApimDtoContractTest` (sample JSON + all
   `TrpGetMemberAccBalApimDataItem` constructions),
   `AccountBalanceSourcePortContractTest`,
   `DefaultAccountBalanceAssemblerIntegrationTest` (all
   `RawAccountBalanceData` constructions) -- exact list captured in this
   script's evidence (02, 03).

### Account Gain Loss (rename wire property only)
1. `TrpGetInvestGlApimDataItem`: change
   `@JsonProperty("pri-date")` to `@JsonProperty("as-of-date")` on the
   existing `priceDate` field. No other Java-side renames.
2. Test updates: any GL contract/adapter test JSON fixtures asserting
   `pri-date` -- exact list captured in this script's evidence (04).

## Evidence
- Evidence directory: target/evidence/<timestamp>-03-review-full-blast-radius-and-test-fixtures
- Packaged as: 03-review-full-blast-radius-and-test-fixtures.zip.txt
- Exported to: /mnt/d/Projects/ORSO/llm-scripts/api-service

## Next stage
Script 04 = writer. Apply the full diff above in one pass, run
`mvn -DskipTests compile` and `mvn -DskipTests test-compile`, verify diff,
and commit.

## Commit
No commit at this stage (investigation-only, no source changes).

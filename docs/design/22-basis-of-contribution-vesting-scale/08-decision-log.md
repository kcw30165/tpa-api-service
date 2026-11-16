# 08 — Decision Log (all confirmed by Nick)

| # | Decision |
|---|---|
| Q1 | Company/member identity block is not part of this endpoint; no source exists yet. |
| Q2 | Pull-down uses `plan-type-desc-eng/chi` (language-filtered). |
| Q3/Q4 | Any member/plan field without a config entry is visible. |
| Q5 | `vesting-scale` HIDDEN → Vesting Scale section absent. |
| Q6 | Language-pair items use the selected language's config entry. |
| Q7 | Java enforces HIDDEN by omitting fields. |
| Q8 | Visible + null → returned as `null`. |
| Q9/N1 | Key present + no config entry → visible. Key absent → omitted (DS may remove hidden fields). Unknown item-id → ignore + one WARN from the APIM mapper (item-id only). Unknown config-value / duplicate item-id → contract violation (existing practice). |
| Q10 | `sch-type`, `function`, `item-type` not used. |
| Q11 | ApimExchangeExecutor supplies the shared envelope with JsonNode data; the feature adapter checks envelope/response, non-blank err-message before data, and exactly one non-null item, then converts data[0] to a typed APIM response DTO using the injected primary Jackson 2 ObjectMapper. Missing/null/empty/whitespace err-message is not an error. |
| Q12 | `member` always present (`{}` if all hidden); dates may be null; `member-category` may be `""`; `plans: []` is success → FE "no plan" message. Missing key = hidden; `[]` = no data; null = N/A. |
| Q13 | DS plan order; FE selects first. |
| N3/Q14 | Config list in the contract is the current full list; member-category is an opaque string. |
| Q15 | Vesting basis: description only, language-filtered. |
| Q16 | Vesting rows sorted ascending; percentage as number, FE adds `%`. |
| Q17/N2 | DS sends dd/MM/yyyy; Java parses strictly and outputs dd/MM/yyyy; other formats → 503. |
| Q18 | plan-service passed as DS sends. |
| Q19 | `planNo` exposed as plan key. |
| Q20 | `GET /api/v1/vesting-scale`, all plans in one call. |
| Q21 | All roles may access; DS controls access. |
| Q21a | No feature role gate; shared APIM credential resolver applies unchanged (resolver role support is platform scope). |
| Q22 | 3 identity codes → 503 public, internal ACCOUNT_CONTEXT_MISMATCH, ERROR; other codes generic path. |
| Q23 | `planTypes[].planType` uses nonblank English when the selected Chinese description is absent, null, empty, or whitespace-only; otherwise it emits Chinese. English is always required and non-blank. This is the only cross-language fallback; vesting-period and contribution-basis values remain selected-language-only, including null. Overview plans do not repeat `planType`. |
| Q24 | Print is FE only. |
| N4 | Not mapped: `env`, `plan-name`, `vest-period-base` code, config metadata. |
| N5 | E2E via the upcoming real orchestrator, as M5 after feature delivery. |
| Shape | `data` = `planTypes` + `overview{member, plans}` + `vestingScale{plans}`; BFF naming, not DS naming. |
| Q25 / R1-R4 | `plan-type-desc-eng` is required non-blank text; absent/null → `MISSING_REQUIRED_FIELD`, blank → `REQUIRED_TEXT_BLANK`, wrong kind → `INVALID_TYPE`, all at the indexed field path. `plan-type-desc-chi` is optional and accepts absent/null/empty/whitespace, but wrong kind → `INVALID_TYPE`. Chinese plan type falls back to English only in `planTypes[]`; no fallback applies to other localized fields. |
| Q26 / A-D | Keep `JsonNode` at the shared executor boundary, then convert the one accepted data item into typed APIM records. Use a contextual `ApimField<T>` to preserve ABSENT/NULL/VALUE and strictly validate scalar JSON kinds. `MalformedReason` is exactly `INVALID_DATE_FORMAT`, `REQUIRED_TEXT_BLANK`, `MISSING_REQUIRED_FIELD`, `INVALID_TYPE`, `INVALID_ENUM_VALUE`, `DUPLICATE_VALUE`, and `INVALID_CARDINALITY`; diagnostics use indexed paths and static labels only. |
| M4.1-FIX | The APIM mapper translates known config `item-id` strings using APIM DTO constants into the application-owned `VestingScaleConfigItem` enum (which contains no wire strings). Duplicate detection is on raw item IDs before translation. Unknown IDs are dropped and produce one item-ID-only WARN in the APIM mapper; the visibility policy uses enum keys only and does not log unknown IDs. `vestingPeriodBasedOn` preserves selected-language ABSENT as omitted JSON and NULL as JSON null, without fallback. |
| Derived (existing practice R5) | Required: `member` object, `plans` array, `config` array, non-blank unique `plan-no`, config item-id/value, and vesting row fields; violations → 503 malformed with the corresponding static reason and indexed field path. |

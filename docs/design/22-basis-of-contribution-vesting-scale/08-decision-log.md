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
| Q9/N1 | Key present + no config entry → visible. Key absent → omitted (DS may remove hidden fields). Unknown item-id → ignore + WARN. Unknown config-value / duplicate item-id → contract violation (existing practice). |
| Q10 | `sch-type`, `function`, `item-type` not used. |
| Q11 | Envelope/data cardinality via existing APIM generic handling; data may be absent when err-message non-empty. |
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
| Q23 | No cross-language fallback. |
| Q24 | Print is FE only. |
| N4 | Not mapped: `env`, `plan-name`, `vest-period-base` code, config metadata. |
| N5 | E2E via the upcoming real orchestrator, as M5 after feature delivery. |
| Shape | `data` = `planTypes` + `overview{member, plans}` + `vestingScale{plans}`; BFF naming, not DS naming. |
| Derived (existing practice R5) | Required: `member` object, `plans` array, `config` array, non-blank unique `plan-no`, non-blank `plan-type-desc-*`, vesting row fields; violations → 503 malformed. |

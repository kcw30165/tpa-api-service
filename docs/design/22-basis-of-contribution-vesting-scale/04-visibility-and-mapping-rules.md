# 04 — Visibility, Presence and Mapping Rules

## 4.1 Presence model
Application model carries every configurable/nullable field as a three-state `Presence<T>`:
- `ABSENT` — key missing in DS data, or hidden by config
- `PRESENT(null)` — key present with JSON null
- `PRESENT(value)`

Response DTOs must serialize ABSENT as **omitted key** and `PRESENT(null)` as **`null`**.
M1 preflight must confirm origin/dev Jackson settings (e.g. `spring.jackson.default-property-inclusion`, custom ObjectMapper customizers, Jackson major version) because a global NON_NULL would silently drop `null`. Implementation approach: a dedicated presence type with explicit serializer + `@JsonInclude(CUSTOM, valueFilter=…)` (or the equivalent proven in a contract test). Contract tests must assert all three states over the real WebFlux codec.

## 4.2 Visibility decision (per field)
```
if key absent in DS data            -> omit
else if config[VestingScaleConfigItem] == HIDDEN -> omit
else (READONLY or no config entry)  -> include (value or null)
```
Non-configurable fields (`plan-no`, `plan-type-desc-*`, `vest-period-base-*`) are always included when present.

Language pairs: for `basis-cont-ee-*` / `basis-cont-er-*`, use the config item of the **selected** language (`-eng` or `-chi`); the other language's item is not consulted.

`vesting-scale`: if config HIDDEN → omit the whole `vestingScale` section. Otherwise include, per plan, an entry only when that plan's `vesting-scale` key is present.

Visibility is applied in the application layer (`VestingScaleVisibilityPolicy`) for both language variants; the web mapper then picks the language.

The APIM mapper translates raw `item-id` values to application-owned `VestingScaleConfigItem` keys using the DTO wire-name constants. The application enum contains no wire strings, and `VestingScaleVisibilityPolicy` uses enum keys only. Duplicate detection occurs against the raw item ID before translation; unknown IDs are dropped and logged exactly once at WARN by the APIM mapper (item ID only), never by the visibility policy.

For `overview.plans[].vestingPeriodBasedOn`, the web mapper uses only the selected-language `vest-period-base` field: ABSENT is omitted from JSON, explicit NULL is emitted as `null`, and no cross-language fallback is applied.

## 4.3 Language
- Resolve via existing `RequestLanguageResolver`: `zh`, `zh-HK`, `zh_HK` → Chinese (`-chi`); everything else → English (`-eng`).
- For `planTypes[].planType`, if the selected Chinese description is absent, null, or blank, use the English description when it is nonblank.
- Other localized fields use only the selected language; no cross-language fallback. Null in the selected language stays null.

## 4.4 Formatting / ordering
- Dates: parse and output `dd/MM/yyyy` (formatter `dd/MM/uuuu`, STRICT). Null passes through.
- `planService`: `{years, months}` passed through exactly as DS sends.
- `vestingPercentage`: BigDecimal, serialized as JSON number; no `%`.
- Plans: DS order everywhere (`planTypes`, `overview.plans`, `vestingScale.plans`).
- Vesting rows: ascending `completedYears`, stable sort.

## 4.5 Restructuring (DS → BFF)
| DS | BFF |
|---|---|
| `plans[]` (plan-no, plan-type-desc) | `planTypes[]` |
| `member` + `plans[]` overview fields | `overview.member`, `overview.plans[]` |
| `plans[].vesting-scale` | `vestingScale.plans[].scales[]` |

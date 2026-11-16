# 06 — Test Strategy (fail-first)

Every behaviour is introduced by a failing test first; evidence (red then green) goes in the sub-task REPORT.md.

## Adapter (`ApimVestingScaleAdapterTest`, `ApimFieldDeserializerTest`, `TrpGetVestingScaleApimDtoContractTest`, `TrpGetVestingScaleApimMapperTest`, `VestingScaleApimConfigurationTest`)
- Request body: exactly 4 fields with values from context; role wire format `role_user_mbr`.
- Full sample (dates converted to dd/MM/yyyy) parses; `env` ignored.
- Presence: each configurable member/plan field absent / null / value, including through `ApimField<T>` deserialization and conversion to application `Presence`.
- `member: {}` ok; `plans: []` ok; `config: []` ok.
- `member-category: ""` passes through.
- Malformed: member/plans/config missing or null; wrong JSON kinds; bad date (`1995-10-01`, `31/02/2020`); unknown config-value; duplicate item-id/plan-no; blank plan-no or required English plan type; missing/null/blank/wrong-kind English plan type; wrong-kind Chinese plan type; vesting row missing fields. Assert exact indexed field path and `MalformedReason`.
- Strict scalar kinds: strings do not coerce to integers/numbers, floating-point values do not coerce to integers, numbers/booleans do not coerce to strings, and vesting percentages accept JSON numbers only.
- Cardinality: absent/empty/multiple/null data items → `INVALID_CARDINALITY` at `data`. Missing/null/empty/whitespace `err-message` succeeds with valid data; non-blank err-message wins over absent or malformed data.
- Malformed failure-event dimensions are limited to `reason`, `malformedField` (normalized indexed wire-field path) and `malformedReason` (static label); assert no downstream value or Jackson message is exposed.
- Unknown item-id → WARN log asserted, success.
- Each of the 3 identity err-messages → ACCOUNT_CONTEXT_MISMATCH, ERROR event, retrieval exception.
- Other err-message → generic path; data absent with err-message is handled.
- YAML path binding.

## Application (`GetVestingScaleServiceTest`, `VestingScaleVisibilityPolicyTest`)
- HIDDEN omits; READONLY keeps; no config entry keeps; absent stays absent even if READONLY.
- Language pair items independently applied.
- `vesting-scale` HIDDEN → section absent.
- Command built from PortalAccessContext.

## Web (`VestingScaleWebMapperTest`, `VestingScaleControllerTest` WebTestClient, `ExceptionMappingsTest`)
- Restructure into planTypes / overview / vestingScale; DS order preserved.
- en / zh-HK / missing / unsupported Accept-Language; Chinese plan type falls back to English for absent/null/empty/whitespace Chinese text, while non-blank Chinese is emitted and English always selects English; other localized fields have no cross-language fallback, including null values.
- Vesting rows sorted ascending (feed unsorted input).
- JSON over real codec: hidden key absent, null emitted as `null`, `[]` emitted.
- Date output dd/MM/yyyy.
- 503 envelope with `err.vesting-scale.retrieval.unavailable`; DS code not in body.
- Missing Account-Ref / session behaviour identical to CIM GET.
- Registry count +1.

## Gates per milestone
`mvn spotless:check`, targeted tests, full `mvn clean test`, then (M4) startup smoke + curl functional script + Sonar new-code clean.

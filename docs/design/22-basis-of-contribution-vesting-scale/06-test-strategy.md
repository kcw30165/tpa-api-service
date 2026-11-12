# 06 — Test Strategy (fail-first)

Every behaviour is introduced by a failing test first; evidence (red then green) goes in the sub-task REPORT.md.

## Adapter (`ApimVestingScaleAdapterTest`, `TrpGetVestingScaleApimDtoContractTest`, `TrpGetVestingScaleApimMapperTest`, `VestingScaleApimConfigurationTest`)
- Request body: exactly 4 fields with values from context; role wire format `role_user_mbr`.
- Full sample (dates converted to dd/MM/yyyy) parses; `env` ignored.
- Presence: each configurable field absent / null / value (member and plan).
- `member: {}` ok; `plans: []` ok; `config: []` ok.
- `member-category: ""` passes through.
- Malformed: member missing; plans missing/non-array; config missing; bad date (`1995-10-01`, `31/02/2020`); unknown config-value; duplicate item-id; blank/duplicate plan-no; blank plan-type-desc; wrong types; vesting row missing fields.
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
- en / zh-HK / missing / unsupported Accept-Language; no fallback (chi null, eng present → null).
- Vesting rows sorted ascending (feed unsorted input).
- JSON over real codec: hidden key absent, null emitted as `null`, `[]` emitted.
- Date output dd/MM/yyyy.
- 503 envelope with `err.vesting-scale.retrieval.unavailable`; DS code not in body.
- Missing Account-Ref / session behaviour identical to CIM GET.
- Registry count +1.

## Gates per milestone
`mvn spotless:check`, targeted tests, full `mvn clean test`, then (M4) startup smoke + curl functional script + Sonar new-code clean.

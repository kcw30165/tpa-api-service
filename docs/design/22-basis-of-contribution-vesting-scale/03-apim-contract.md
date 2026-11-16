# 03 — APIM Contract: POST /ws/NGTPA/v1/TRPGetVestingScale

## Request (`TrpGetVestingScaleApimRequest`)
| Wire field | Source |
|---|---|
| `policy-no` | `AccountContext.policyNo()` |
| `cert-no` | `AccountContext.certNo()` |
| `login-user-id` | `ActorContext.actorUserId()` |
| `login-user-role` | `ActorContext.actorUserRole()` in DS wire format (sample: `role_user_mbr`) |

Implementor MUST first grep origin/dev for any existing APIM request serializing `login-user-id` / `login-user-role` and reuse that exact conversion. If none exists, serialize the role as the lowercase enum name and add a contract test asserting `role_user_mbr` for the member role.

## Response envelope and typed parsing
ApimExchangeExecutor returns the shared envelope with `JsonNode` data. ApimVestingScaleAdapter checks that the envelope/response is present, then checks a non-blank `err-message` BEFORE reading data, then requires exactly one non-null data item (otherwise `INVALID_CARDINALITY`, field path `data`). Missing, null, empty, or whitespace-only `err-message` means no error. Only after these checks does the adapter use the injected primary Jackson 2 ObjectMapper to convert `data[0]` into `TrpGetVestingScaleApimDataItem`; the feature mapper consumes that typed DTO.

Response DTOs and `ApimField<T>` live under `adapter/out/apim/vestingscale/dto/`. Each response record ignores unknown properties. Wire-property constants are declared once in those DTOs and shared by Jackson annotations and mapper field paths. `ApimField<T>` preserves `ABSENT`, explicit `NULL`, and `VALUE`; its contextual deserializer accepts only the documented JSON scalar kinds and delegates nested records/lists to Jackson. Jackson failures become malformed-response diagnostics using only normalized wire-field paths and static reason labels.

## data[0] parsing rules (strict)
| Path | Rule | Violation → |
|---|---|---|
| `env` | ignored, never read | — |
| `member` | required JSON object (may be `{}`) | `MISSING_REQUIRED_FIELD` or `INVALID_TYPE` |
| `member.<field>` | absent → ABSENT; `null` → NULL; string → value | wrong type → malformed |
| `member.member-category` | any string incl. `""` | non-string non-null → `INVALID_TYPE` |
| member/plan date fields | string in `dd/MM/uuuu`, `ResolverStyle.STRICT`; null allowed | non-string → `INVALID_TYPE`; invalid date → `INVALID_DATE_FORMAT` |
| `config` | required JSON array (may be `[]`) | absent/null → `MISSING_REQUIRED_FIELD`; wrong type → `INVALID_TYPE` |
| `config[].item-id` | required non-blank string | absent/null → `MISSING_REQUIRED_FIELD`; blank → `REQUIRED_TEXT_BLANK`; wrong type → `INVALID_TYPE` |
| `config[].config-value` | required exact string `READONLY` or `HIDDEN` | absent/null → `MISSING_REQUIRED_FIELD`; wrong type → `INVALID_TYPE`; unknown value → `INVALID_ENUM_VALUE` |
| duplicate `item-id` | — | `DUPLICATE_VALUE` |
| unknown `item-id` (not in known list) | ignore + WARN log (item-id only) | — |
| `config[].item-name/item-type/function/sch-type` | ignored | — |
| `plans` | required JSON array (may be `[]`) | absent/null → `MISSING_REQUIRED_FIELD`; wrong type → `INVALID_TYPE` |
| `plans[].plan-no` | required non-blank; unique within response | absent/null → `MISSING_REQUIRED_FIELD`; blank → `REQUIRED_TEXT_BLANK`; duplicate → `DUPLICATE_VALUE`; wrong type → `INVALID_TYPE` |
| `plans[].plan-type-desc-eng` | required non-blank string | absent/null → `MISSING_REQUIRED_FIELD`; blank → `REQUIRED_TEXT_BLANK`; wrong type → `INVALID_TYPE` |
| `plans[].plan-type-desc-chi` | optional; absent, null, empty, or whitespace-only accepted | wrong type → `INVALID_TYPE` |
| `plans[].plan-name`, `vest-period-base` | ignored | — |
| `plans[].plan-service` | absent / null / object `{years, months}`; both keys required, each nullable integer | missing inner key → `MISSING_REQUIRED_FIELD`; wrong type → `INVALID_TYPE` |
| `plans[].vest-period-base-eng/chi`, `basis-cont-*` | absent / null / string | wrong type → `INVALID_TYPE` |
| `plans[].vesting-scale` | absent / null / array | wrong type → `INVALID_TYPE` |
| `vesting-scale[].completed-years` | required integer | absent/null → `MISSING_REQUIRED_FIELD`; wrong type → `INVALID_TYPE` |
| `vesting-scale[].vest-pcnt` | required JSON number (BigDecimal) | absent/null → `MISSING_REQUIRED_FIELD`; wrong type → `INVALID_TYPE` |

`MalformedReason` is exactly `INVALID_DATE_FORMAT`, `REQUIRED_TEXT_BLANK`, `MISSING_REQUIRED_FIELD`, `INVALID_TYPE`, `INVALID_ENUM_VALUE`, `DUPLICATE_VALUE`, and `INVALID_CARDINALITY`. Paths include array indices (for example `plans[1].vesting-scale[0].vest-pcnt`). Reason labels and paths are metadata only; raw response values and Jackson exception messages are never included.

## Known config item-ids (current full list)
`member-category, plan-effective-date, basis-cont-ee-chi, basis-cont-ee-eng, basis-cont-er-chi, basis-cont-er-eng, date-joined-company, date-joined-scheme, date-of-joining-plan, employment-date, plan-service, vesting-scale`

`item-id` equals the wire field name inside `member` or `plans[]`. The APIM mapper translates known IDs using the APIM DTO wire-name constants into the application-owned `VestingScaleConfigItem` enum; the enum contains no wire strings. Duplicate detection uses the raw `item-id` before translation. An unknown ID is dropped and produces exactly one WARN from the APIM mapper, containing only that item ID.

## Not mapped
`env`, `plan-name`, `vest-period-base` (code), all `config[]` attributes except `item-id`/`config-value`.

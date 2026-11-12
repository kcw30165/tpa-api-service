# 03 — APIM Contract: POST /ws/NGTPA/v1/TRPGetVestingScale

## Request (`TrpGetVestingScaleApimRequest`)
| Wire field | Source |
|---|---|
| `policy-no` | `AccountContext.policyNo()` |
| `cert-no` | `AccountContext.certNo()` |
| `login-user-id` | `ActorContext.actorUserId()` |
| `login-user-role` | `ActorContext.actorUserRole()` in DS wire format (sample: `role_user_mbr`) |

Implementor MUST first grep origin/dev for any existing APIM request serializing `login-user-id` / `login-user-role` and reuse that exact conversion. If none exists, serialize the role as the lowercase enum name and add a contract test asserting `role_user_mbr` for the member role.

## Response envelope
The typed envelope comes from ApimExchangeExecutor (transport/JSON syntax only). As in CIM (ApimInvestmentChoiceDetailsAdapter), ApimVestingScaleAdapter itself checks missing envelope/response, then non-blank err-message BEFORE reading data, then requires exactly one non-null data item; otherwise malformed.

## data[0] parsing rules (strict)
| Path | Rule | Violation → |
|---|---|---|
| `env` | ignored, never read | — |
| `member` | required JSON object (may be `{}`) | malformed |
| `member.<field>` | absent → ABSENT; `null` → NULL; string → value | wrong type → malformed |
| `member.member-category` | any string incl. `""` | non-string non-null → malformed |
| member/plan date fields | `dd/MM/uuuu`, `ResolverStyle.STRICT`; null allowed | other format → malformed |
| `config` | required JSON array (may be `[]`) | missing/non-array → malformed |
| `config[].item-id` | required non-blank string | malformed |
| `config[].config-value` | `READONLY` or `HIDDEN` (exact) | unknown value → malformed |
| duplicate `item-id` | — | malformed |
| unknown `item-id` (not in known list) | ignore + WARN log (item-id only) | — |
| `config[].item-name/item-type/function/sch-type` | ignored | — |
| `plans` | required JSON array (may be `[]`) | malformed |
| `plans[].plan-no` | required non-blank; unique within response | malformed |
| `plans[].plan-type-desc-eng/chi` | required non-blank (pull-down label) | malformed |
| `plans[].plan-name`, `vest-period-base` | ignored | — |
| `plans[].plan-service` | absent / null / object `{years, months}`; inner values integer or null, passed through | wrong type → malformed |
| `plans[].vest-period-base-eng/chi`, `basis-cont-*` | absent / null / string | wrong type → malformed |
| `plans[].vesting-scale` | absent / null / array | wrong type → malformed |
| `vesting-scale[].completed-years` | required integer | malformed |
| `vesting-scale[].vest-pcnt` | required number (BigDecimal) | malformed |

## Known config item-ids (current full list)
`member-category, plan-effective-date, basis-cont-ee-chi, basis-cont-ee-eng, basis-cont-er-chi, basis-cont-er-eng, date-joined-company, date-joined-scheme, date-of-joining-plan, employment-date, plan-service, vesting-scale`

`item-id` equals the wire field name inside `member` or `plans[]`.

## Not mapped
`env`, `plan-name`, `vest-period-base` (code), all `config[]` attributes except `item-id`/`config-value`.

# 02 — BFF API Contract: GET /api/v1/vesting-scale

## Request
| Item | Value |
|---|---|
| Method / path | `GET /api/v1/vesting-scale` |
| Headers | `Account-Ref` (required), `X-XSRF-TOKEN`, `Accept-Language` (optional), `X-Request-Id` (optional) |
| Cookie | portal session cookie |
| Body / query | none |
| Roles | all roles (DS controls access) |

## Success response (200, `MutationResponse<VestingScaleResultResponse>`)
```json
{
  "success": true,
  "status": 200,
  "result": {
    "data": {
      "planTypes": [
        { "planNo": "OCAC", "planType": "Company Account (Notional MPF)" },
        { "planNo": "OIAC", "planType": "Company Account (Top Up)" }
      ],
      "overview": {
        "member": {
          "memberCategory": "ACTIVE",
          "dateJoinedCompany": "01/10/1995",
          "dateJoinedScheme": "01/04/2000"
        },
        "plans": [
          {
            "planNo": "OCAC",
            "planType": "Company Account (Notional MPF)",
            "planEffectiveDate": "01/02/2019",
            "dateOfJoiningPlan": "01/02/2019",
            "vestingPeriodBasedOn": "Date Joined Company",
            "memberContributionBasis": null,
            "employerContributionBasis": "5% of MPF Relevant Income (cap at HK$1,500.00)"
          }
        ]
      },
      "vestingScale": {
        "plans": [
          {
            "planNo": "OCAC",
            "scales": [
              { "completedYears": 1, "vestingPercentage": 10.0 },
              { "completedYears": 2, "vestingPercentage": 20.0 }
            ]
          }
        ]
      }
    }
  },
  "messages": [],
  "errors": []
}
```
(Example uses a config where `employment-date` and `plan-service` are HIDDEN, so they are omitted.)

## Field table
| BFF field | Source (APIM) | Configurable item-id | Notes |
|---|---|---|---|
| `planTypes[].planNo` | `plans[].plan-no` | — | always present; join key |
| `planTypes[].planType` | `plan-type-desc-eng/chi` | — | language-filtered; pull-down text |
| `overview.member.memberCategory` | `member.member-category` | `member-category` | opaque string, `""` passed through |
| `overview.member.dateJoinedCompany` | `member.date-joined-company` | `date-joined-company` | dd/MM/yyyy or null |
| `overview.member.dateJoinedScheme` | `member.date-joined-scheme` | `date-joined-scheme` | dd/MM/yyyy or null |
| `overview.member.employmentDate` | `member.employment-date` | `employment-date` | dd/MM/yyyy or null |
| `overview.plans[].planNo` | `plan-no` | — | always present |
| `overview.plans[].planType` | `plan-type-desc-eng/chi` | — | language-filtered |
| `overview.plans[].planEffectiveDate` | `plan-effective-date` | `plan-effective-date` | dd/MM/yyyy or null |
| `overview.plans[].dateOfJoiningPlan` | `date-of-joining-plan` | `date-of-joining-plan` | dd/MM/yyyy or null |
| `overview.plans[].planService` | `plan-service` | `plan-service` | `{years, months}` passed as DS sends; or null |
| `overview.plans[].vestingPeriodBasedOn` | `vest-period-base-eng/chi` | — | language-filtered description only; code not exposed |
| `overview.plans[].memberContributionBasis` | `basis-cont-ee-eng/chi` | `basis-cont-ee-eng` / `basis-cont-ee-chi` (selected language's item) | text or null |
| `overview.plans[].employerContributionBasis` | `basis-cont-er-eng/chi` | `basis-cont-er-eng` / `basis-cont-er-chi` | text or null |
| `vestingScale.plans[].planNo` | `plan-no` | — | |
| `vestingScale.plans[].scales[].completedYears` | `vesting-scale[].completed-years` | `vesting-scale` | integer |
| `vestingScale.plans[].scales[].vestingPercentage` | `vesting-scale[].vest-pcnt` | `vesting-scale` | decimal number; FE adds `%` |

## Presence semantics (FE-facing)
| JSON state | Meaning | FE behaviour |
|---|---|---|
| key absent | hidden by scheme config | do not render the row/section |
| `null` | not applicable / no date in TAS | render row with FE's N/A treatment |
| `[]` | no data | `planTypes: []` → "no plan" message |

- `overview.member` is always present (may be `{}`).
- `planTypes`, `overview.plans` are always present (may be `[]`); same order as DS; FE selects first.
- `vestingScale` is absent when `vesting-scale` is HIDDEN (section not shown). Otherwise present; it contains an entry for each plan whose `vesting-scale` key is present (`scales` = sorted rows, `[]`, or `null`).

## Errors
| Case | HTTP | Public code |
|---|---|---|
| Any APIM err-message, malformed APIM response, downstream unavailable | 503 | `err.vesting-scale.retrieval.unavailable` (SYSTEM_ERROR) |
| Missing Account-Ref / session / CSRF | existing filter/controller behaviour (same as CIM GET) | existing |

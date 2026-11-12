# 01 — Solution Design

## 1. Scope
In scope (Java BFF):
- One read endpoint `GET /api/v1/vesting-scale` returning, for the selected account (Account-Ref):
  plan-type pull-down options, Overview (member + per-plan items), Vesting Scale (per-plan rows).
- Config-driven visibility (`config[]`), presence semantics (missing / null / []), Accept-Language filtering.
- 503 + ERROR classification for the three account-identity err-messages.

Out of scope:
- Company Name, Company Code, Member Name, Member Account No. (not part of this endpoint; no source exists yet).
- Print button (FE only). Static field labels / section headers (FE owns; BRD wording table).
- "No plan" message text (FE renders when `planTypes` is `[]`).
- Role-based access control: **all roles may call**; access control is owned by DS.
- Real login→feature→logout e2e orchestrator scenario (M5, after the orchestrator branch lands).
- `env` from the APIM response (DS testing only; never mapped).

## 2. Ownership
| Concern | Owner |
|---|---|
| Which fields a scheme shows (config), access control, data values, value wording (plan type desc, contribution basis, vesting basis) | DS / TAS |
| Applying visibility, presence semantics, language filtering, date validation/formatting, vesting-row sort, BFF naming/structure | Java BFF |
| Field labels, section headers, "no plan" message, `%` suffix, default selection (first plan), print | FE |

## 3. Component chain (mirrors CIM GET; see report R2)
```
adapter/in/web/vestingscale/VestingScaleController            GET /api/v1/vesting-scale
  -> application/port/in/GetVestingScaleUseCase
  -> application/usecase/vestingscale/GetVestingScaleService   (applies VestingScaleVisibilityPolicy)
  -> application/port/out/VestingScalePort
  -> adapter/out/apim/vestingscale/ApimVestingScaleAdapter     (ApimExchangeExecutor.postEnvelope, JsonNode parsing)
       dto/TrpGetVestingScaleApimRequest
       TrpGetVestingScaleApimMapper                            (strict parse, presence-aware)
  <- application/dto/vestingscale/*                            (presence-aware model, both languages)
  <- adapter/in/web/vestingscale/VestingScaleWebMapper          (language select, sort, format, restructure)
  <- adapter/in/web/vestingscale/response/*                     (MutationResponse<VestingScaleResultResponse>)
adapter/in/web/error/VestingScaleExceptionMappings              (registered in ExceptionMappingRegistryConfig)
```
Names above are binding unless the implementor proves a conflicting origin/dev convention in evidence.

## 4. Request context
- Session cookie + `X-XSRF-TOKEN` + `Account-Ref` validated by existing `PortalAccessContextWebFilter`; controller requires resolved Account-Ref and session ID exactly like `InvestmentChoiceController`.
- No Java role gate. No reference-date lookup (APIM request has no ref-date).
- APIM routing/credentials: same as CIM GET (`ACCOUNT_ENV` routing, credential context `actorUserRole` + `accountEnv`, `SAFE_BUSINESS_CALL`).
- YAML: `apim.vesting-scale.path: /ws/NGTPA/v1/TRPGetVestingScale` (same shape as `apim.investment-choice.details.path`).

## 5. Layering of rules
| Rule | Layer |
|---|---|
| Strict parsing, presence capture (ABSENT vs NULL vs value), err-message classification, contract violations | APIM adapter |
| Visibility (config HIDDEN → ABSENT; unknown item-id WARN) | Application service (`VestingScaleVisibilityPolicy`) |
| Language selection, date formatting, vesting sort, BFF restructuring, JSON omission | Web mapper / response DTOs |

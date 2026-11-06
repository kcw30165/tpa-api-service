# 17 - Change of Investment Choice (CIM)

BFF design pack for BRD *TPA ORSO Web Revamp 6.5 Change of Investment Choice* V0.3.

| Item | Value |
|---|---|
| Owner | Nick Deng (BFF API) |
| Code anchor | `origin/dev` 8eac2b30 (evidence 001 at 8f84da7a; 002, 004, 007 and Copilot 0.1 at 8eac2b30 code) |
| Implementor | GitHub Copilot, one sub-task prompt at a time (document 07) |
| Status | Approved for implementation, except the open items in document 06 |

## Documents

| # | Document | Content |
|---|---|---|
| 01 | [Solution design](01-solution-design.md) | Scope, responsibilities, components, flows |
| 02 | [API contract](02-api-contract.md) | BFF endpoints used by the frontend |
| 03 | [APIM contract](03-apim-contract.md) | TRPGetCIMDtl, TRPCIMReq, mapping rules |
| 04 | [Validation and error handling](04-validation-and-error-handling.md) | Validation rules, error matrix, logging ownership |
| 05 | [Implementation plan](05-implementation-plan.md) | Milestones, sub-tasks, gates |
| 06 | [Decisions and open items](06-decisions-and-open-items.md) | Every confirmed decision and every unknown |
| 07 | [Implementor prompts](07-implementor-prompts.md) | Copy-paste prompts per sub-task |

## Reading rule for the implementor

Anything not written here is not approved. If the code at the implementation HEAD contradicts
a fact in this pack, stop and report; do not adapt the design silently.

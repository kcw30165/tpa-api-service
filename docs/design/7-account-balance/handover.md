# Account Balance Design Handover

**Feature:** 6.14 Account Balance  
**Baseline:** 27/08/2026  
**Status:** Consolidated design baseline, pending repository investigation  

## Consolidation result

The uploaded package contained multiple versions of the same design areas. It has been consolidated without discarding the still-valid source inventory, requirements, APIM rules, transformation logic, validation, observability, test traceability, decisions, and handover content.

## Current cache decision

Use one lazy policy-scoped Redis entry:

```text
<prefix>:general-metadata:<opaque-env-trust-policy>
```

The entry embeds whether TAS resolved `policy-no=all` or the specific policy. This avoids separate-marker lifecycle management and avoids unsafe reuse of a default for an unseen policy.

## Next step

Run Phase I repository investigation first. Then revise the physical implementation plan with exact packages, classes, validators, Redis ports, concurrency mechanisms, APIM ledger locations, and test names before generating implementation artifacts.

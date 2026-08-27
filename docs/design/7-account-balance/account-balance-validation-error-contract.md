# Account Balance Validation and Error Contract

**Feature:** 6.14 Account Balance  
**Baseline:** 27/08/2026  
**Status:** Consolidated design baseline, pending repository investigation  

## Validation

Java validates X-Request-Id, Accept-Language, and Account-Ref through existing shared practice. Optional currency is validated only when present. Validation failure uses existing `VALIDATION_FAILED` codes and targets and invokes no feature downstream capability.

Only X-Request-Id is propagated to APIM. Accept-Language drives BFF localization. Account-Ref resolves context. Idempotency-Key is not part of the Account Balance GET contract and no feature idempotency store is introduced.

## Business mappings

```text
err.tpa.account.invalid -> BUSINESS_REJECTED / err.ab.account.not.exist
err.tpa.member.not.found -> BUSINESS_REJECTED / err.ab.account.mismatch
```

These exact approved pairings are locked by tests.

## System mapping

All other errors map to HTTP 503, `SYSTEM_ERROR`, `err.system.unavailable`, including TAS required-field errors, ReferenceDate failure, APIM failure, invalid array cardinality, join violations, visible missing/sentinel values, metadata failures, and missing effective precision.

Malformed factsheet URL is the exception: log safely, omit, continue success.

## Account Balance header profile

Required headers are:

```text
X-Request-Id
Accept-Language
Account-Ref
```

`Idempotency-Key` is excluded because Account Balance is a read-only GET operation. Phase I investigates the existing GET validation path only to identify the correct reuse point and confirm there is no unavoidable platform-wide constraint.

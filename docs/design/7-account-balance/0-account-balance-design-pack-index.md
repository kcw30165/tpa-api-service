# Account Balance Design Pack Index

**Feature:** 6.14 Account Balance  
**Baseline:** 27/08/2026  
**Status:** Consolidated logical baseline pending repository investigation  

## Review conclusion

The earlier archive contained parallel generations of the same subjects. This canonical pack retains the still-valid requirements, APIM contracts, transformation rules, shared metadata design, validation, observability, tests, decisions, implementation plan, and handover without retaining duplicate versions.

## Canonical documents

1. `account-balance-source-and-requirements.md`
2. `account-balance-master-design.md`
3. `account-balance-api-contract.md`
4. `account-balance-apim-contract.md`
5. `account-balance-transformation-contract.md`
6. `general-metadata-cache-resolver-design.md`
7. `account-balance-validation-error-contract.md`
8. `account-balance-configuration-observability.md`
9. `account-balance-sequence-diagrams.md`
10. `account-balance-test-traceability-design.md`
11. `account-balance-decisions-open-questions.md`
12. `account-balance-implementation-plan.md`
13. `handover.md`

## Header decision

Account Balance requires `X-Request-Id`, `Accept-Language`, and `Account-Ref`. It does not require `Idempotency-Key` because the GET operation is read-only and naturally repeatable.

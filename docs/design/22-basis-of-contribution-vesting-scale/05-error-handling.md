# 05 — Error Handling

Follow CIM GET practice (report R4/R5) unless stated.

| Case | Internal classification | Failure-event log | Public HTTP |
|---|---|---|---|
| err-message ∈ {`err.tpa.login.user.id.invalid`, `err.tpa.cert.no.not.found`, `err.tpa.cert.no.required`} | DOWNSTREAM_CONTRACT, reason `ACCOUNT_CONTEXT_MISMATCH` | ERROR | 503 `err.vesting-scale.retrieval.unavailable` |
| any other non-blank err-message | DOWNSTREAM_CONTRACT, existing generic reason (e.g. `UNMAPPED_ERROR_CODE`) | ERROR (existing) | 503 same code |
| malformed response (03 table) | DOWNSTREAM_INVALID_RESPONSE; when known, include static `malformedField` and `malformedReason` dimensions | ERROR (existing) | 503 same code |
| transport/timeout/5xx | existing `ApimExchangeExecutor` behaviour | existing | existing |

Rationale for the three codes: login-user-id and cert-no are Java-supplied from the opaque Account-Ref, so these indicate a BFF↔DS context inconsistency, not a user error.

- The DS err-message code is never returned to FE.
- Malformed-response diagnostics may include only code-defined field paths and violation categories;
  never include downstream values, plan identifiers, or response bodies.
- Exception: `VestingScaleRetrievalException` (public) mapped by `VestingScaleExceptionMappings.retrievalUnavailable()` → 503 / SYSTEM_ERROR / `err.vesting-scale.retrieval.unavailable` / MessagePolicy.NONE, log level as CIM.
- Register in `ExceptionMappingRegistryConfig`; `ExceptionMappingsTest` expected count = (count on the branch base) + 1 (28 → 29 at 6084ba77).
- Unknown config `item-id`: WARN with item-id only; request still succeeds.
- Logs must not contain policy-no, cert-no, login-user-id unmasked (centralized masking policy, design 9).

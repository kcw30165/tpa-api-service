# 05 — Error Handling

Follow CIM GET practice (report R4/R5) unless stated.

| Case | Internal classification | Failure-event log | Public HTTP |
|---|---|---|---|
| err-message ∈ {`err.tpa.login.user.id.invalid`, `err.tpa.cert.no.not.found`, `err.tpa.cert.no.required`} | DOWNSTREAM_CONTRACT, reason `ACCOUNT_CONTEXT_MISMATCH` | ERROR | 503 `err.vesting-scale.retrieval.unavailable` |
| any other non-blank err-message | DOWNSTREAM_CONTRACT, existing generic reason (e.g. `UNMAPPED_ERROR_CODE`) | ERROR (existing) | 503 same code |
| malformed response (03 table) | DOWNSTREAM_INVALID_RESPONSE; when known, include static `malformedField` and `malformedReason` dimensions | ERROR (existing) | 503 same code |
| transport/timeout/5xx | existing `ApimExchangeExecutor` behaviour | existing | existing |

The adapter checks `err-message` before `data`. Missing, null, empty, and whitespace-only values are not errors; any non-blank value is classified before cardinality or typed-data validation.

Rationale for the three codes: login-user-id and cert-no are Java-supplied from the opaque Account-Ref, so these indicate a BFF↔DS context inconsistency, not a user error.

- The DS err-message code is never returned to FE.
- The response body remains `JsonNode` at the shared executor boundary; after envelope, error-message, and cardinality checks, the adapter converts the single item to the typed Vesting Scale APIM DTO with the injected primary Jackson 2 ObjectMapper.
- Malformed-response diagnostics may include only normalized, indexed wire-field paths and the static reason labels `INVALID_DATE_FORMAT`, `REQUIRED_TEXT_BLANK`, `MISSING_REQUIRED_FIELD`, `INVALID_TYPE`, `INVALID_ENUM_VALUE`, `DUPLICATE_VALUE`, or `INVALID_CARDINALITY`;
  never include downstream values, plan identifiers, or response bodies.
- English `plan-type-desc-eng` is required and non-blank. Chinese `plan-type-desc-chi` is optional and may be absent, null, empty, or whitespace-only; a non-string value is malformed. Only `planTypes[].planType` falls back from blank/missing Chinese to English. Vesting-period and contribution-basis values have no cross-language fallback.
- Exception: `VestingScaleRetrievalException` (public) mapped by `VestingScaleExceptionMappings.retrievalUnavailable()` → 503 / SYSTEM_ERROR / `err.vesting-scale.retrieval.unavailable` / MessagePolicy.NONE, log level as CIM.
- Register in `ExceptionMappingRegistryConfig`; `ExceptionMappingsTest` expected count = (count on the branch base) + 1 (28 → 29 at 6084ba77).
- Unknown config `item-id`: WARN with item-id only; request still succeeds.
- Logs must not contain policy-no, cert-no, login-user-id unmasked (centralized masking policy, design 9).

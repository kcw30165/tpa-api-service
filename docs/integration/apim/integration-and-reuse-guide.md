# APIM Integration and Reuse Guide

## 1. Start from the application capability

Do not begin by copying an existing APIM adapter. First define what the application needs independently of APIM:

- Command fields
- Result fields
- Business outcomes
- Validation ownership
- Selected-account/context requirements
- Language behavior
- Idempotency/retry semantics
- Audit and privacy requirements

Then define an outbound port using application/domain types.

## 2. Implementation sequence

### Step 1 — Confirm the APIM contract

Obtain and review:

- Endpoint path and HTTP method
- APIM operation name and API version
- Request envelope and field names
- Response envelope and data cardinality
- Required headers
- Authentication/credential profile
- Fields requiring encryption/decryption
- Business and validation error codes
- Idempotency and retry guarantees
- Timeout expectations
- Example success and failure payloads

Do not infer missing fields from another operation.

### Step 2 — Define the application port

The port should express a business capability, not an APIM route. Use transport-neutral commands/results and reactive return types consistent with the codebase.

Bad boundary concept:

```text
callTrpSomething(TrpSomethingApimRequest)
```

Preferred boundary concept:

```text
fetchMemberInformation(MemberInformationCommand)
```

The exact naming should follow the repository's established capability terms.

### Step 3 — Create APIM DTOs

Create operation-specific request, response, envelope/data, and raw-error DTOs.

Rules:

- Preserve exact APIM JSON names.
- Model arrays versus single objects exactly.
- Use nullable fields only where the contract permits omission/null.
- Keep raw APIM codes as transport data until mapping.
- Redact sensitive values in `toString()`.
- Do not reuse public web DTOs.
- Avoid a generic map when a stable contract is known.

### Step 4 — Implement a contract mapper

Map every field explicitly and test the mapping independently.

The mapper should handle only contract transformation, including approved normalization such as padding or code mapping. It should not call WebClient, decide retry behavior, or resolve localized frontend messages.

### Step 5 — Add endpoint properties

Externalize:

- Path
- Operation name
- Error mappings
- Credential-profile selector where required
- Encryption field names
- Retry eligibility if configurable

Validate mandatory properties at startup. Do not silently call a default URL when an operation path is absent.

### Step 6 — Implement the transport

Prefer the established operation transport/shared facade pattern. Supply:

- Credential-resolution context
- Request ID
- Operation metadata
- Request DTO
- Expected response type
- Retry eligibility

Do not add an operation-specific raw `WebClient` unless the shared facade genuinely cannot support the contract and the deviation is reviewed.

### Step 7 — Implement the adapter

The adapter should:

1. Validate required application inputs.
2. Resolve the approved credential profile/context.
3. Map to the APIM request DTO.
4. Invoke the transport.
5. Validate/maps the APIM envelope.
6. Translate success data to application results.
7. Translate known business errors into typed domain outcomes.
8. Translate unknown/technical errors into safe exceptions.
9. Log only sanitized operation/correlation metadata.

### Step 8 — Configure encryption

For every sensitive field, determine whether encryption is required in the APIM contract.

Update and test:

- Request encryption field list
- Response decryption field list
- DTO JSON field name
- Certificate/key profile
- Encrypted and unencrypted contract fixtures

Do not assume annotation or Java property names automatically match encryption selectors.

### Step 9 — Classify retry eligibility

Choose explicitly:

- Safe business call
- Idempotent mutation
- Non-retryable

Document why. A POST method is not automatically non-retryable, and a GET method is not automatically safe if it has downstream side effects. Classification follows business semantics and downstream guarantees.

### Step 10 — Map downstream errors

Create a mapping matrix outside code review comments:

```text
Raw APIM/TAS code
  -> domain outcome/classification
  -> public BFF error code
  -> API status and HTTP status
  -> frontend target(s)
  -> retry/navigation behavior
  -> log level and idempotency-cache behavior
```

Unknown codes require a deliberate fallback.

### Step 11 — Wire Spring configuration

Register properties, mapper, transport, and adapter beans using the repository's conditional-injection patterns. Keep application classes Spring-free where that is the established pattern.

### Step 12 — Add tests before integration release

Minimum test groups:

- DTO serialization/deserialization
- Contract mapper
- Adapter business mapping
- Envelope validation
- OAuth/token behavior
- Certificate/encryption behavior
- Retry eligibility and boundaries
- Error/fallback mapping
- Logging redaction
- End-to-end sandbox/stub contract

## 3. Reuse decision guide

### Reuse the shared facade when

- The operation uses the standard APIM OAuth/API-key/certificate pipeline
- JSON envelope behavior is supported
- Existing field-level crypto applies
- Standard request correlation applies
- Retry eligibility can be expressed by the existing classifier

### Add an operation transport when

- The operation has distinct DTOs/path/response type
- A narrow interface improves adapter testing
- The same call is used by more than one adapter

### Extend the shared infrastructure only when

- A new authentication mechanism is required
- The APIM envelope is genuinely different
- A new encryption/signature algorithm is contractually required
- Streaming/binary/multipart behavior cannot fit the existing facade
- Retry semantics require a new cross-cutting primitive

A new APIM endpoint by itself is not a reason to change the shared facade.

## 4. Context and header rules

### Request ID

Propagate `X-Request-Id` to APIM. Preserve it across retries and include it in safe diagnostics.

### Language

Use `Accept-Language` as the canonical source when APIM requires language. Do not add a `lang` query parameter or fallback contract.

### Account context

For selected-account APIs, obtain account details from validated `PortalAccessContext`. The frontend sends only the selected `Account-Ref`; the BFF/APIM side resolves related account data.

### Session and CSRF

Do not propagate browser session cookies or CSRF tokens to APIM. They protect the BFF boundary and are resolved before the outbound call.

### Arbitrary headers

Do not forward arbitrary inbound headers. Add only contractually approved APIM headers through the facade.

## 5. Worked example: adding a new TAS operation

```text
1. Define GetSomethingPort with application command/result
2. Add GetSomethingApimRequest/Response DTOs
3. Add GetSomethingContractMapper
4. Add endpoint properties: path, operation name, errors
5. Add GetSomethingTransport backed by ApimWebClientFacade
6. Implement ApimGetSomethingAdapter
7. Add encrypted field selectors where required
8. Set retry eligibility from TAS semantics
9. Map known TAS codes and fallback
10. Wire beans
11. Add serialization, mapper, adapter, crypto, retry, and contract tests
12. Update this APIM documentation and operational runbook
```

## 6. Pull-request checklist

- [ ] APIM/TAS contract source identified
- [ ] Application port is transport-neutral
- [ ] APIM DTOs do not cross the port boundary
- [ ] Exact JSON field names tested
- [ ] Envelope/data cardinality tested
- [ ] Credential profile confirmed
- [ ] Request ID propagated
- [ ] Language and account context rules followed
- [ ] Sensitive fields encrypted/configured
- [ ] Retry eligibility explicitly justified
- [ ] Known and fallback error mappings covered
- [ ] No secrets or PII in logs
- [ ] Timeout and unknown outcome behavior tested
- [ ] Fail-first TDD evidence included
- [ ] Configuration and runbook updated
- [ ] Commit message included

Suggested commit message:

```text
docs(apim): document integration architecture, reuse, operations, and testing
```

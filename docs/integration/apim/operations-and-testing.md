# APIM Operations, Configuration, and Testing

## 1. Configuration reference

### 1.1 Connectivity

Review per environment:

- Base URL
- Operation paths
- Request timeout
- Connection/TLS behavior
- Proxy and DNS configuration
- APIM subscription/API-key header name and secret source

### 1.2 OAuth credential profiles

Review:

- Registration/profile ID
- Token URI
- Client ID and secret source
- Client authentication method
- Scope
- Base URL override
- Subscription/API key
- Certificate path
- Trust/profile selection rules

Secrets must come from approved secret management, not committed YAML.

### 1.3 Payload protection

Review:

- Encryption enabled flag
- Application private/public key or certificate path
- APIM public-certificate retrieval settings
- Certificate cache TTL
- Renewal skew
- Request encryption fields
- Response decryption fields
- Supported algorithms

The reviewed implementation uses an AES/RSA/JWT protection pipeline. Algorithm or key-format changes require compatibility testing with APIM.

### 1.4 Retry policy

Review:

- Maximum attempts
- Backoff
- Transient failure classification
- Operation retry eligibility
- `invalid_token` recovery
- Invalid-public-key recovery

The observed configuration uses up to three attempts with 100 ms backoff. Confirm the active environment and operation classification.

### 1.5 Endpoint-specific contract

For every operation maintain:

- Method/path
- Operation name
- Request/response DTO type
- Expected envelope and array cardinality
- Credential profile
- Encryption selectors
- Raw error mappings
- Fallback behavior
- Retry eligibility
- Owning downstream service/team

## 2. Troubleshooting runbook

### OAuth token acquisition fails

Check token URI, client ID/secret, scope, client authentication method, TLS trust, clock skew, and credential-profile selection. Do not log the token request body or secret.

### Repeated `invalid_token`

Confirm token eviction occurred, the correct profile cache key is used, token expiry/clock skew is correct, and APIM accepts the configured audience/scope. One bounded refresh/retry is appropriate; repeated attempts should fail safely.

### Certificate retrieval or parsing fails

Check certificate endpoint/profile, PEM format, X.509 validity, key ID, cache expiry, renewal skew, and system clock. Never print PEM or key material in diagnostics.

### `invalid_public_key` or decryption failure

Check whether cached APIM public material is stale, encryption field selectors match JSON wire names, algorithms/encoding agree with APIM, and the application certificate is current. Use the bounded certificate-eviction recovery path; do not loop.

### APIM returns HTTP success but adapter fails

Inspect the sanitized envelope shape, required data array, error/message fields, and response DTO compatibility. Treat malformed/missing data as a contract failure.

### Known TAS error becomes generic failure

Check endpoint error-mapping properties, raw code normalization, fallback order, and whether the adapter uses the correct operation mapping set.

### Timeout or connection failure

Check APIM availability, DNS, proxy, TLS, timeout, connection pool, and downstream latency. Determine whether the operation was sent before retrying a mutation; unknown outcomes require the operation's idempotency contract.

### Wrong account or language reaches APIM

Verify `PortalAccessContext` resolution, `Account-Ref` matching, application-to-APIM mapper, and `Accept-Language` handling. Do not accept business account context directly from arbitrary frontend headers/body fields.

### Works without encryption but fails when enabled

Compare the encrypted-field configuration with exact APIM JSON names, request/response direction, null handling, key/certificate profile, and protected test fixtures.

## 3. Logging and observability

### Safe fields

- `X-Request-Id`
- Operation name
- Endpoint identifier, not secret-bearing full URL
- Credential profile ID where approved
- HTTP status
- APIM public error code
- Retry attempt and eligibility
- Duration
- Token/certificate cache outcome category
- Exception type

### Forbidden fields

- OAuth access token
- Client ID/secret where the ID is sensitive
- Subscription/API key
- Authorization or Certificate header
- Private/public key material or PEM
- AES key, JWT, signature, or encrypted payload details
- Password
- User/member identifiers
- Policy/certificate number
- Email/mobile/address
- Clear request/response body containing PII

Recommended events:

```text
apim.request.started
apim.request.completed
apim.request.failed
apim.request.retried
apim.oauth.cache.hit
apim.oauth.refresh.failed
apim.certificate.cache.hit
apim.certificate.evicted
apim.contract.invalid
apim.business.rejected
apim.crypto.failed
```

Use request ID and operation name for correlation. Never use sensitive business identifiers.

## 4. Test strategy

### 4.1 DTO contract tests

- Exact JSON property names
- Required and optional fields
- Null/blank handling
- Array/single-object cardinality
- Envelope serialization/deserialization
- Raw downstream error representation
- Redacted `toString()` output

Use approved sanitized fixtures derived from the APIM contract. Do not commit production payloads.

### 4.2 Mapper tests

- Every application field maps to the correct APIM field
- Every APIM success field maps to the correct domain field
- Padding/normalization rules
- Enum/code conversions
- Missing/invalid data behavior
- No APIM DTO leaks into application results

### 4.3 Adapter tests

With a fake operation transport, test:

- Success
- Empty/malformed data
- Known business error mappings
- Unknown fallback mapping
- Transport failure
- Crypto failure
- Correlation context
- Credential-profile selection

### 4.4 Envelope validator tests

- Valid success envelope
- Valid business-error envelope
- Missing body/data/error fields
- Unexpected data cardinality
- Contradictory success/error indicators
- Invalid JSON/type mismatch

### 4.5 OAuth tests

- Cache hit before expiry
- Refresh near expiry
- One in-flight refresh for concurrent callers
- Profile isolation
- Token endpoint failure
- Malformed token response
- `invalid_token` eviction and bounded retry
- Credentials absent from logs/errors

### 4.6 Certificate and crypto tests

- PEM normalization/parsing
- Certificate validity/expiry
- Cache hit and renewal skew
- Concurrent certificate retrieval
- AES field encryption/decryption fixtures
- RSA key exchange compatibility
- JWT signing/verification
- Tampered payload/signature rejection
- Invalid-public-key eviction and bounded retry
- Selector uses exact JSON field names
- Key/certificate material absent from logs

### 4.7 Retry tests

- Safe call retries approved transient failure
- Business rejection is not retried
- Non-retryable mutation is attempted once
- Idempotent mutation follows approved policy
- Maximum-attempt boundary
- Backoff behavior using virtual/deterministic time
- Same request ID across attempts
- Unknown outcome behavior

### 4.8 HTTP integration tests

Against a stub/sandbox:

- Correct method/path
- OAuth, API key, certificate, Accept, and request-ID headers
- Request envelope
- Encrypted and unencrypted variants
- Response decryption
- Timeout
- 4xx/5xx translation
- Contract mismatch

### 4.9 End-to-end business tests

Cover representative critical operations:

- Member Login and OTP destination mapping
- Account List and multiple-account mapping
- Registration lookup/username/completion
- Personal-information GET and update validation errors
- Contribution/notification/reference data reads
- Reference-date fallback/cache behavior where APIM participates

## 5. Failure classification matrix

Maintain an operation-specific matrix with these columns in source-controlled documentation or configuration review evidence:

```text
Raw code
Domain status/classification
Public error code
API status
HTTP status
Targets
Retryable
Idempotency-cacheable
Log level
Frontend next step
```

This matrix must include unknown/fallback behavior.

## 6. Release evidence

Each APIM change should include:

- Contract source/version and owner
- DTO serialization fixtures
- Fail-first TDD evidence
- Mapper and adapter test results
- Crypto/encryption test results where relevant
- Retry classification evidence
- Error-mapping matrix
- Configuration diff by environment
- Sanitized sandbox/stub evidence
- Logging-redaction evidence
- Timeout and rollback plan
- Monitoring/alert updates
- Commit message

## 7. Production readiness checklist

- [ ] Base URL, paths, and timeout verified
- [ ] OAuth profile and secrets verified
- [ ] API key header configured
- [ ] Certificates valid and renewal monitored
- [ ] Encryption selectors match exact APIM fields
- [ ] Request/response fixtures pass
- [ ] Retry eligibility approved per operation
- [ ] Mutations have explicit unknown-outcome handling
- [ ] Known and fallback errors mapped
- [ ] `X-Request-Id` propagated across retries
- [ ] `Accept-Language` and account context rules verified
- [ ] No inbound cookies/CSRF/arbitrary headers forwarded
- [ ] Logs/APM contain no credentials, keys, PII, or payload secrets
- [ ] OAuth/certificate cache concurrency tested
- [ ] APIM outage and timeout runbooks exercised
- [ ] Dashboard/alerts cover latency, error rate, token, certificate, crypto, and retry failures
- [ ] Fail-first TDD evidence attached to the change

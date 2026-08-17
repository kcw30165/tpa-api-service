# APIM Operations, Testing, and Production Runbook

## 1. Purpose and Authority

This document is the operational companion to [`apim-exchange-executor-architecture-and-developer-guide.md`](apim-exchange-executor-architecture-and-developer-guide.md).

Use the architecture guide as the authoritative source for:

- executor and request contracts;
- operation routing;
- actor-role and account-environment authority;
- retry eligibility;
- business-envelope ownership;
- the final APIM operation ledger;
- rules for adding or changing an operation.

Use this runbook for:

- environment configuration review;
- runtime troubleshooting;
- logging and observability;
- operational test coverage;
- release evidence;
- production-readiness checks.

Do not maintain a second APIM operation ledger in this document.

---

## 2. Architectural Operating Rules

All governed APIM operations must execute through one canonical typed path:

```text
feature application boundary
    -> focused outbound command
    -> operation adapter or approved shared transport
    -> ApimExchangeRequest
    -> ApimExchangeExecutor.postEnvelope(...)
```

Feature adapters must not invoke these foundation components directly:

```text
ApimWebClientFacade
ApimCertificateService
ApimPayloadCryptoService
ApimCredentialProfileResolver
APIM token services
```

Those components are owned by the generic executor foundation.

A production test transport, alternate constructor, or conditional bypass of the executor is prohibited.

---

## 3. Environment Configuration Review

### 3.1 Connectivity

Review for each environment:

- APIM base URL;
- operation paths;
- request timeout;
- connection-pool behavior;
- TLS trust configuration;
- proxy and DNS configuration;
- APIM subscription or API-key header name;
- approved secret source.

An absent operation path must fail configuration validation. Do not silently use a default business endpoint.

### 3.2 OAuth credential profiles

Review:

- profile identifier;
- token URI;
- client ID and approved secret source;
- client-authentication method;
- OAuth scope or audience;
- base URL override;
- subscription or API key;
- application certificate path;
- actor-role and account-environment selection rules.

Secrets must come from approved secret management and must not be committed to YAML, source, evidence, or logs.

Feature adapters must supply routing authority through `ApimCredentialResolutionContext`. Feature adapters must not resolve profile IDs directly.

### 3.3 Payload protection

Review:

- encryption-enabled flag;
- application certificate or key location;
- APIM public-certificate retrieval settings;
- certificate cache TTL;
- renewal skew;
- request encryption-field selectors;
- response decryption-field selectors;
- supported algorithms and encoding.

The current generic foundation uses the repository-approved payload-protection pipeline. Algorithm or key-format changes require APIM compatibility evidence and are foundation changes, not feature-adapter changes.

Adding a sensitive DTO field does not encrypt that field automatically. The exact JSON wire name must also appear in the appropriate selector configuration and contract tests.

### 3.4 Retry policy

Review:

- maximum attempts;
- backoff;
- transient-failure classification;
- invalid-token recovery;
- invalid-public-key recovery;
- operation-level `ApimRetryEligibility`.

Always confirm active values from the current environment configuration. Historical observed attempt or backoff values are not normative architecture guarantees.

The operation must explicitly use one of the repository-supported classifications:

```text
SAFE_BUSINESS_CALL
IDEMPOTENT_MUTATION
NON_RETRYABLE
```

Java-side idempotency alone does not prove that a downstream mutation is safe to retry. Downstream duplicate-safety evidence is required.

### 3.5 Request correlation

Verify that:

- `X-Request-Id` is present or generated at the BFF boundary;
- the same canonical request ID reaches APIM;
- approved retries preserve the request ID;
- Reactor context is preserved across the adapter and executor boundary;
- arbitrary inbound headers are not forwarded.

### 3.6 Language and account context

`Accept-Language` is the canonical language source where downstream language is required.

For authenticated, selected-account operations:

- the frontend supplies only the selected Account-Ref;
- the BFF resolves protected account data from the authenticated session;
- the operation command carries validated account environment and actor role;
- the adapter does not accept arbitrary frontend account context as APIM routing authority.

Browser session cookies and CSRF tokens protect the BFF boundary and must not be propagated to APIM.

---

## 4. Troubleshooting Runbook

### 4.1 OAuth token acquisition fails

Check:

1. token URI;
2. client ID and secret source;
3. scope or audience;
4. client-authentication method;
5. TLS trust;
6. clock skew;
7. actor-role and account-environment credential-profile selection.

Do not log the token request body, client secret, access token, authorization header, or subscription key.

### 4.2 Repeated `invalid_token`

Check:

- token eviction occurred;
- the correct credential-profile cache key was used;
- token expiry and clock skew are correct;
- APIM accepts the configured audience or scope;
- the bounded invalid-token recovery path was entered only once.

Repeated refresh attempts must fail safely rather than loop.

### 4.3 Credential profile cannot be resolved

Check:

- the application caller supplied the correct actor role;
- `ACCOUNT_ENV` operations supplied a nonblank protected account environment;
- `NON_ACCOUNT` operations intentionally supplied a null environment;
- the active environment-to-profile mapping exists;
- the adapter did not introduce a fallback or infer scope from nullability.

Do not fix resolution by hardcoding a profile ID or role in the adapter.

### 4.4 Certificate retrieval or parsing fails

Check:

- certificate endpoint and resolved profile;
- PEM format and normalization;
- X.509 validity period;
- key identifier;
- cache expiry and renewal skew;
- system clock;
- concurrent certificate retrieval behavior.

Never print PEM, key, certificate, or JWT material in diagnostics.

### 4.5 `invalid_public_key` or response decryption fails

Check:

- whether cached APIM public material is stale;
- exact request and response selector names;
- JSON wire-name compatibility;
- algorithm and encoding agreement;
- application certificate validity;
- response-envelope shape.

Use only the bounded generic certificate-eviction and recovery path. Do not retry crypto manually in the feature adapter.

### 4.6 Works with encryption disabled but fails when enabled

Compare:

- encrypted-field selectors with exact APIM JSON names;
- request versus response direction;
- null and omitted-field handling;
- resolved credential profile;
- application and APIM certificates;
- approved protected fixtures.

The same routing scope, actor role, account environment, retry eligibility, and request ID must apply in plain and encrypted modes.

### 4.7 APIM returns HTTP success but the adapter fails

An HTTP 2xx response is not automatically business success.

Inspect sanitized evidence for:

- envelope status;
- error or message fields;
- required data array;
- expected item count;
- required response fields;
- contradictory success and error indicators;
- response DTO compatibility;
- operation-specific business-code mapping.

Keep operation-specific validation in the adapter or mapper, not in the generic executor.

### 4.8 Known TAS rejection becomes a generic failure

Check:

- operation-specific raw-code normalization;
- business-rejection mapping;
- fallback order;
- expected data cardinality;
- whether the adapter uses the correct result mapper;
- public error isolation.

The BFF must not expose raw downstream messages or `ApiStatus.DOWNSTREAM_REJECTED` to the frontend.

### 4.9 Timeout or connection failure

Check:

- APIM availability;
- DNS, proxy, and TLS;
- timeout and connection-pool configuration;
- downstream latency;
- operation retry eligibility;
- whether request transmission may have occurred.

For mutations, distinguish a known failure from an unknown outcome. Do not manually retry a mutation outside the executor.

### 4.10 Wrong account environment reaches APIM

Check:

- `ApimDatabaseScope` classification;
- protected account or trust authority;
- Account-Ref resolution;
- command construction;
- `ApimCredentialResolutionContext`;
- request DTO mapping;
- absence of fallback routing.

Response values may be business data for later protected state, but must not retroactively route the same call.

### 4.11 Missing request ID

Check:

- canonical request-ID creation;
- Reactor context preservation;
- adapter use of the approved context key;
- executor request construction;
- retry-attempt consistency.

Do not work around missing correlation by forwarding arbitrary inbound headers.

### 4.12 Architecture guard fails

Check whether the feature adapter or shared transport:

- directly imports facade, certificate, crypto, token, or resolver services;
- has more than one production constructor or execution path;
- contains a production test transport;
- infers scope from nullable account environment;
- substitutes an actor role;
- uses response data for routing;
- duplicates an existing operation path;
- is absent from the authoritative routing ledger.

Do not weaken the guard to permit a legacy execution path.

---

## 5. Logging and Observability

### 5.1 Safe diagnostic fields

Use only approved, sanitized fields such as:

- `X-Request-Id`;
- operation name;
- endpoint identifier without secret-bearing query data;
- database scope;
- retry eligibility;
- attempt number;
- HTTP status where safe;
- duration;
- normalized failure category;
- token or certificate cache outcome category;
- exception type;
- approved credential-profile identifier, only where disclosure is permitted.

### 5.2 Forbidden fields

Do not log:

- OAuth access tokens;
- client secrets;
- subscription or API keys;
- authorization or certificate headers;
- private or public key material;
- PEM content;
- AES keys, signatures, JWT payloads, or encrypted payload details;
- raw passwords or password confirmation;
- OTP values;
- captcha answers;
- full user, member, policy, certificate, or trust identifiers;
- unmasked email, mobile, or address data;
- clear request or response bodies containing protected data.

### 5.3 Recommended events

Use consistent events such as:

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

Use request ID and operation name for correlation. Do not use sensitive business identifiers as correlation keys.

---

## 6. Test Strategy

### 6.1 DTO contract tests

Test:

- exact JSON property names;
- required and optional fields;
- null and blank handling;
- array versus single-object cardinality;
- envelope serialization and deserialization;
- raw downstream error representation;
- routing metadata exclusion;
- redacted `toString()` behavior.

Use approved sanitized fixtures. Do not commit production payloads.

### 6.2 Mapper tests

Test:

- every application field maps to the correct APIM field;
- every APIM success field maps to the correct application field;
- normalization and padding rules;
- enum and code conversion;
- missing and invalid data behavior;
- business-error mapping;
- absence of APIM DTO leakage across the application port.

### 6.3 Adapter executor-boundary tests

Mock `ApimExchangeExecutor` and capture the submitted `ApimExchangeRequest`.

Assert:

- exact operation path;
- request DTO type and body;
- response data-item type;
- explicit database scope;
- explicit actor role;
- explicit account environment or intentional null;
- explicit retry eligibility;
- request-ID preservation;
- exactly one executor invocation;
- no feature-owned facade, certificate, crypto, token, or resolver dependency.

Do not add a fake production transport, test-only production constructor, or conditional production execution branch.

### 6.4 Application workflow tests

Test the current feature prerequisites and prove that invalid prerequisites invoke APIM zero times.

Depending on the flow, cover:

- captcha validation and one-time consumption;
- CSRF validation;
- OTP state and verification;
- protected context expiry;
- selected account or trust validation;
- account-environment authority;
- idempotency reservation and replay;
- concurrent duplicate requests;
- workflow transition and cleanup ordering;
- return-to-login or session-establishment behavior.

### 6.5 Executor and foundation tests

Test:

- `ACCOUNT_ENV` blank-environment rejection;
- `NON_ACCOUNT` null-environment acceptance;
- credential-profile resolution;
- token cache and refresh;
- one in-flight refresh for concurrent callers;
- invalid-token eviction and bounded recovery;
- certificate parsing and caching;
- encryption and decryption fixtures;
- invalid-public-key eviction and bounded recovery;
- plain and encrypted execution consistency;
- request-ID consistency across attempts;
- successful-attempt metadata consistency.

### 6.6 Retry tests

Test:

- safe calls retry approved transient failures;
- business rejections are not retried;
- non-retryable mutations are attempted once;
- idempotent mutations follow the approved policy;
- maximum-attempt boundary;
- backoff with deterministic or virtual time;
- same request ID across attempts;
- mutation unknown-outcome behavior.

Do not infer retry safety from the HTTP method.

### 6.7 Error and envelope tests

Test:

- valid success envelope;
- valid business-rejection envelope;
- missing body, data, error, or message fields;
- unexpected item count;
- contradictory success and error indicators;
- invalid JSON or type mismatch;
- known raw-code mapping;
- safe fallback for unknown raw codes;
- frontend status and field targets;
- downstream-origin isolation.

### 6.8 HTTP integration tests

Against an approved stub or sandbox, verify:

- method and path;
- OAuth, API key, certificate, `Accept`, and `X-Request-Id` headers;
- request envelope;
- encrypted and unencrypted variants;
- response decryption;
- timeout behavior;
- 4xx and 5xx translation;
- contract mismatch handling.

### 6.9 Architecture guards

Retain guards for:

- exact operation-set enforcement;
- canonical executor ownership;
- no feature-owned manual APIM orchestration;
- no duplicate paths;
- no alternate production test transport;
- explicit scope, role, account environment, and retry;
- no response-derived routing;
- business semantics outside generic infrastructure;
- public downstream-status isolation;
- Spring wiring uniqueness.

### 6.10 Validation commands

Use explicit focused test lists and the repository-standard environment:

```bash
export JAVA_HOME="C:/Java/OpenJDK/jdk-21"
export M2_HOME="/d/Tools/apache-maven-3.9.15"
export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH"

mvn -DskipTests test-compile
mvn -Dtest=<explicit-focused-test-list> test
mvn test
mvn -DskipTests compile
```

Run the full suite when production execution, shared wiring, generic foundation, or architecture guards change.

Do not rerun Maven for a report-only documentation change when the executable source, tests, build configuration, runtime configuration, generated resources, HEAD, index, and stash all match an already validated state. Record inherited validation explicitly.

---

## 7. Failure Classification Matrix

Maintain operation-specific error mapping in code, configuration review evidence, or the authoritative feature contract.

For each raw downstream outcome, record:

```text
raw APIM or TAS code
domain outcome
public error code
public API status
HTTP status
frontend target fields
retry eligibility
idempotency or replay behavior
log level
frontend next step
```

Every operation requires an intentional fallback for unknown codes.

Unknown codes must be logged safely using operation and request correlation, without exposing PII, credentials, or protected payloads.

---

## 8. Release Evidence

Each APIM behavior change should include:

- contract source, version, and owner;
- caller and call-chain inventory;
- routing and actor-role classification;
- request and response DTO inventory;
- sanitized serialization fixtures;
- genuine fail-first evidence, or an explicit governance exception where no honest defect exists;
- adapter and mapper test results;
- encryption and certificate tests where relevant;
- retry-classification evidence;
- error-mapping matrix;
- configuration diff by environment;
- sanitized stub or sandbox evidence;
- logging-redaction evidence;
- timeout and rollback plan;
- monitoring and alert updates;
- full-suite and compile evidence when required;
- final worktree, index, stash, and conflict fingerprints;
- commit message.

Do not manufacture a fail-first failure when the target invariant is already durably enforced.

---

## 9. Production-Readiness Checklist

### Connectivity and credentials

- [ ] Base URL, operation paths, and timeout verified.
- [ ] OAuth profile and approved secret sources verified.
- [ ] API-key or subscription header configured.
- [ ] Certificates are valid and renewal is monitored.
- [ ] Environment-to-profile selection rules are verified.

### Contract and protection

- [ ] Request and response fixtures pass.
- [ ] Encryption selectors match exact JSON wire names.
- [ ] Plain and encrypted execution use identical routing metadata.
- [ ] Required and optional fields are contract-tested.
- [ ] Routing metadata is not improperly serialized.

### Routing and retry

- [ ] Database scope is explicit.
- [ ] Actor-role authority is explicit.
- [ ] Account environment is protected and explicit, or intentionally null.
- [ ] Retry eligibility is approved per operation.
- [ ] Mutations have explicit idempotency and unknown-outcome handling.
- [ ] No response-derived routing or default fallback exists.

### Errors and observability

- [ ] Known and fallback business errors are mapped.
- [ ] `ApiStatus.DOWNSTREAM_REJECTED` is not exposed to FE.
- [ ] `X-Request-Id` is propagated across retries.
- [ ] Logs and APM contain no credentials, keys, PII, or payload secrets.
- [ ] Dashboards and alerts cover latency, error rate, token, certificate, crypto, and retry failures.

### Architecture and evidence

- [ ] One canonical executor-backed production path exists.
- [ ] No feature-owned facade, token, certificate, crypto, or resolver dependency exists.
- [ ] No production test transport or alternate constructor exists.
- [ ] No duplicate Spring bean or operation path exists.
- [ ] Architecture guards pass.
- [ ] Focused and full validation evidence is present when required.
- [ ] Skipped tests and warnings are dispositioned.
- [ ] Worktree, index, stash, and conflict state are recorded.
- [ ] Commit message is included.

---

## 10. Incident Evidence Checklist

When diagnosing a production APIM incident, capture only sanitized evidence:

- timestamp and environment;
- request ID;
- operation name;
- database scope;
- retry eligibility and attempt count;
- normalized failure category;
- HTTP status where safe;
- token or certificate cache outcome category;
- duration;
- relevant deployment and configuration version;
- whether the outcome may be unknown for a mutation;
- whether replay or reconciliation is permitted.

Never capture access tokens, secrets, key material, raw protected payloads, passwords, OTP values, or unmasked customer data.

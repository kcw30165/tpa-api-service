# Workflow and Error Contract Operations and Testing

## 1. Configuration inventory

Maintain a source-controlled inventory covering:

```text
Error code
Owning workflow
Type
Severity
Source
Targets
Next step
Page ID / variant
ApiStatus
HTTP status
Retry-After behavior
Idempotency cacheable
Localization/content key
Owner
```

The configured contract and code enums must agree.

## 2. Startup validation

The workflow error validator should reject deployment when it finds:

- Missing required error keys
- Duplicate codes
- Malformed code/key format
- Missing type, severity, source, or target metadata
- Invalid next step
- Missing navigation for a referenced step
- Blank/invalid page ID or variant
- Unsupported contract combinations

Treat startup failure as a contract defect, not something to bypass with a fallback definition.

## 3. Troubleshooting runbook

### Application fails to start after adding an error

Check the feature/shared key enum, configured definition, duplicate code index, type/severity/source, targets, next step, and navigation entry.

### Correct error code but wrong page

Check `WorkflowNextStep`, navigation mapping, page ID/variant, feature page-reference constants, and whether the mapper overwrote a service-provided page reference.

### Error appears against the wrong field

Trace application target → APIM/backend binding → frontend field ID mapping. Confirm targets use the current frontend schema and are not APIM names.

### HTTP status conflicts with body status

Check the feature HTTP response helper and error-code precedence. Controllers should not wrap an already mapped response with a second status decision.

### Rate-limit response lacks `Retry-After`

Check that the service result contains a positive retry delay and that the HTTP helper recognizes the mapped error/status.

### Frontend shows raw or missing message

Check whether the endpoint contract intentionally returns codes only, whether localization/config contains the code, `Accept-Language` resolution, variant context, and fallback behavior. Do not add bilingual text directly to the error.

### Unknown APIM/TAS code becomes generic system error

Check endpoint-specific explicit/fallback downstream classification, raw code normalization, operation/page selection, and config variant lookup. Monitor fallback use rather than exposing the raw code.

### Completed idempotency replay has stale navigation

Check replay snapshot schema, next-step/pageRef fields, contract compatibility, and TTL. A workflow contract change can make old replay snapshots incompatible.

### Duplicate errors appear

Check validation-rule aggregation, field splitting/mapping, error equality/deduplication, and whether both application and exception handler are adding the same error.

### Expected outcome is handled by exception handler

Move known conditions into typed result/status mapping. Reserve global exception handling for exceptional failures.

## 4. Logging and observability

### Safe fields

- `X-Request-Id`
- Workflow/scenario
- Operation
- Internal result status
- Public error code
- API and HTTP status
- Next step
- Page ID/variant where non-sensitive
- Downstream fallback-used flag
- Duration
- Exception type for unexpected failures

### Forbidden fields

- Password, OTP, CAPTCHA answer
- Session/CSRF/idempotency values
- Identity/contact/account data
- Raw downstream payload/message
- Stack trace or diagnostic text in public response
- Localized content containing sensitive submitted values

Recommended events:

```text
workflow.completed
workflow.validation_failed
workflow.business_rejected
workflow.security_rejected
workflow.downstream_rejected
workflow.system_failed
workflow.waiting
workflow.contract.fallback_used
workflow.contract.invalid
```

## 5. Metrics and alerts

Track by workflow, operation, and public error code:

- Success/update/waiting rate
- Validation and business rejection rate
- Security rejection rate
- Downstream rejection/error rate
- System failure rate
- Restart/return-to-login navigation count
- Unknown downstream fallback usage
- HTTP/API-status mismatch defects
- Contract startup-validation failures
- Retry-After/rate-limit outcomes

Alert on system/downstream spikes, unknown-code fallback spikes, missing navigation defects, and unusually high security or restart outcomes.

## 6. Test strategy

### 6.1 Workflow result tests

- Constructor/record validation
- Success, updated, and waiting results
- Validation/business/system failures
- Payload/message/error copying and immutability
- `nextStep` and pageRef consistency
- Terminal/wait/restart policy

### 6.2 Error key and descriptor tests

- Valid `err.*` formats
- Blank/unsafe/malformed keys rejected
- Correct type/severity/source/targets
- Frontend target IDs
- No sensitive values in descriptor/toString
- Shared versus feature-specific key ownership

### 6.3 Contract validator tests

- Complete valid configuration
- Missing required key
- Duplicate code
- Missing metadata
- Empty targets where prohibited
- Invalid next step
- Missing navigation
- Blank page ID/variant
- Unknown/unsupported enum values
- All Login/Registration/Shared keys covered

### 6.4 Resolver tests

- Code resolves exact definition
- Next step resolves correct page ID/variant
- No-navigation step behavior
- Missing code fails safely
- Duplicate/ambiguous definition rejected
- Defensive copying of targets

### 6.5 Mutation mapper tests

- Every `WorkflowResultStatus` maps to expected `ApiStatus`
- Payload and pageRef preserved
- Messages map to `ApiMessage`
- Errors map to `ApiError`
- Empty arrays are consistent
- Waiting/replay behavior
- No framework/infrastructure detail leaks

### 6.6 Feature web mapper tests

For each operation status enum:

- Every value has an explicit mapping
- Correct error code and targets
- Correct next step/pageRef
- Correct retry timing
- Correct cookie clear/set flags
- Safe payload only

Use exhaustive enum tests so newly added statuses fail tests until mapped.

### 6.7 HTTP response tests

- Success HTTP status
- `400`, `403`, `409`, `422`, `429`, `502`, and `503` mappings where applicable
- Error-code precedence when multiple errors exist
- `Retry-After`
- `X-Request-Id` echo
- Cookie set/clear behavior
- Body `ApiStatus` agrees with HTTP status

### 6.8 Exception-handler tests

- Application exception
- Validation exception
- Authentication/context failure
- Access denied
- APIM timeout/unavailable/upstream failure
- Redis/cache failure
- Unexpected exception
- Sanitized diagnostics
- Stable public error and no internal leakage

### 6.9 Downstream classification tests

- Every explicit raw code
- Endpoint/operation scoping
- Fallback behavior
- HTTP status and target
- Idempotency cacheable/TTL
- Fallback-used observability
- Raw message never exposed

### 6.10 End-to-end contract tests

Cover at least:

- Login start and OTP flow
- Registration CAPTCHA/OTP/trust/username/password steps
- Rate limit and cooldown
- Invalid/expired auth flow
- Idempotency duplicate and replay
- APIM business rejection and outage
- Registration success variants
- Cancellation and return-to-login

Validate JSON body, HTTP status, headers, cookies, error targets, next step, and page/action metadata together.

## 7. Contract-change procedure

For any code, status, target, next-step, or page change:

1. Identify frontend and support consumers.
2. Update application enum/key/descriptor.
3. Update configured definition/navigation.
4. Update localization/content configuration.
5. Update mapper and HTTP response rules.
6. Review idempotency snapshot compatibility.
7. Run startup validator and exhaustive tests.
8. Coordinate frontend deployment/versioning.
9. Update dashboards/runbooks.
10. Provide rollback behavior.

## 8. Release evidence

Each workflow-contract change should include:

- Before/after contract matrix
- Error/navigation ownership approval
- Fail-first TDD evidence
- Validator/resolver tests
- Mapper and HTTP contract tests
- Downstream mapping/fallback tests
- Frontend compatibility evidence
- Idempotency replay compatibility review
- Localization/content evidence
- Logging-redaction evidence
- Rollback plan
- Commit message

## 9. Production readiness checklist

- [ ] All required keys defined once
- [ ] Startup validator passes
- [ ] Error metadata complete
- [ ] Targets use frontend IDs
- [ ] Next steps and page refs resolve
- [ ] ApiStatus and HTTP status agree
- [ ] Retry-After behavior tested
- [ ] Cookie behavior tested
- [ ] Unknown downstream fallback configured
- [ ] No raw downstream/security data exposed
- [ ] Existing replay snapshots remain safe/compatible
- [ ] Frontend handling deployed or backward-compatible
- [ ] Contract dashboards/alerts updated
- [ ] Support error matrix updated
- [ ] Fail-first TDD evidence attached to the change

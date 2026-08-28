# BFF Phase 00: Scripted End-to-End Test Preparation Plan

## 1. Purpose

This phase establishes a repository-owned, curl-based end-to-end test framework for the entire BFF service. The framework replaces manual Postman-based validation with repeatable scripts, runtime configuration, deterministic assertions, secret-safe evidence, and explicit completion gates.

Phase 00 is a cross-cutting engineering capability. It is not owned by Account Balance or any single feature. Account Balance is the first planned consumer, but the framework must support login, registration, OTP, CAPTCHA-dependent boundaries, username recovery, Forgot Password, personal information, account selection, non-member access, Account Balance, and future BFF APIs.

Phase 00 does not renumber existing feature-delivery phases and does not implement production feature behavior.

## 2. Objectives

1. Provide one reusable scripted HTTP-validation framework for all BFF APIs.
2. Replace Postman as the authoritative local functional-validation mechanism.
3. Support pre-login and post-login test flows.
4. Support runtime-supplied sessions and selected `Account-Ref` values without retaining them in evidence.
5. Apply canonical request context consistently:
   - `Accept-Language` is the language source.
   - `X-Request-Id` is generated and propagated for traceability.
   - Post-login clients supply only the selected `Account-Ref`.
   - Backend session resolution remains authoritative for related account and protected context.
6. Capture deterministic timestamped evidence without passwords, OTPs, sessions, cookies, tokens, or personal data.
7. Allow feature-specific scenario packs to be added incrementally as contracts are approved.
8. Keep local scripted validation independent from unresolved Jenkins administrator dependencies.
9. Design the framework so Jenkins can invoke approved non-destructive scenario packs later without redesigning the scripts.

## 3. Scope

### 3.1 Included

- Investigation of existing scripts, API contracts, session handling, evidence conventions, and command-line tools.
- A common curl-based HTTP client.
- Runtime configuration and preflight validation.
- Anonymous, provided-session, and future scripted-authentication bootstrap boundaries.
- Shared request-context handling.
- JSON, HTTP, schema, error, and security assertions.
- Secret and personal-data redaction.
- Timestamped evidence generation.
- Reusable scenario-runner and scenario-pack conventions.
- Safe framework self-tests against approved non-destructive endpoints.
- Feature-specific scenario stubs that do not invent unconfirmed contracts.
- Documentation and cumulative `handover.md` updates.

### 3.2 Excluded

- Production controller, service, port, adapter, APIM, DTO, or business-logic implementation.
- Guessing unconfirmed endpoints, fields, statuses, or error mappings.
- CAPTCHA solving or bypass.
- Automatic OTP retrieval or uncontrolled SMS delivery.
- Committing populated environment files or secret values.
- Postman collections, Newman, and manual copy-and-paste validation.
- Destructive scenarios unless separately designed and approved.
- Jenkins plugin configuration, GitLab protected-merge policy, or immutable artifact promotion.

## 4. Guiding Principles

### 4.1 Framework first, feature packs second

The core runner, HTTP client, assertions, evidence, redaction, and bootstrap mechanisms must remain feature-neutral. Each BFF capability adds its own scenario pack without duplicating the framework.

### 4.2 Scripted and reproducible

Every supported scenario must be executable through a documented shell command. Manual Postman actions are not authoritative evidence.

### 4.3 Runtime configuration

Environment-specific behavior must be supplied at runtime. Base URLs, sessions, selected accounts, languages, timeouts, TLS policy, and approved test identities must not require source edits or image rebuilds.

### 4.4 Secret-safe by default

The framework must never retain passwords, OTPs, CAPTCHA answers, sessions, cookies, tokens, authorization values, or private keys. Personal and account values must be redacted unless an approved synthetic value is explicitly safe to retain.

### 4.5 Contract-led assertions

Assertions must be based on approved API contracts. Unconfirmed behavior must be represented as `PENDING_CONTRACT`, `BLOCKED_ENVIRONMENT`, or `NOT_APPLICABLE`, never fabricated.

### 4.6 Non-destructive default

The default suite must be safe to run repeatedly. State-changing scenarios require explicit classification, prepared data, cleanup rules, and approval.

### 4.7 Investigation first

Implementation decisions remain provisional until current repository conventions, tools, authentication constraints, API contracts, evidence flow, and environment limitations are mapped.

## 5. Proposed Repository Structure

The exact location is confirmed during Gate A. The target structure should follow this model:

```text
scripts/
  e2e/
    README.md
    run.sh
    lib/
      preflight.sh
      runtime.sh
      http-client.sh
      assertions.sh
      evidence.sh
      redaction.sh
      scenario-registry.sh
    bootstrap/
      anonymous.sh
      provided-session.sh
      scripted-auth.sh.example
    config/
      runtime.env.example
    scenarios/
      platform/
        health.sh
        request-id.sh
        language.sh
        base-response-contract.sh
        sensitive-data-audit.sh
      authentication/
        login/
        otp/
        captcha-boundary/
        account-selection/
      registration/
      username-recovery/
      forgot-password/
      personal-information/
      non-member-access/
      account-balance/
```

Feature packs may be introduced only when their approved contracts are available.

## 6. Runtime Configuration Contract

The framework should define variable names and ownership without committing populated values:

```text
BFF_BASE_URL
E2E_ENVIRONMENT
E2E_SCENARIO_PACK
E2E_SCENARIO_ID
E2E_BOOTSTRAP_MODE
E2E_EVIDENCE_ROOT
E2E_CONNECT_TIMEOUT_SECONDS
E2E_REQUEST_TIMEOUT_SECONDS
E2E_TLS_VERIFY
ACCEPT_LANGUAGE
REQUEST_ID_PREFIX
SESSION_ID
ACCOUNT_REF
TEST_USERNAME
TEST_PASSWORD
OTP_MODE
CAPTCHA_MODE
```

### 6.1 Initial bootstrap modes

```text
E2E_BOOTSTRAP_MODE=anonymous
E2E_BOOTSTRAP_MODE=provided-session
```

`anonymous` supports public and pre-authentication APIs that do not require a session.

`provided-session` supports post-login APIs using runtime-supplied `SESSION_ID` and, where required, selected `ACCOUNT_REF`. These values must not be retained in evidence.

### 6.2 Future bootstrap mode

```text
E2E_BOOTSTRAP_MODE=scripted-auth
```

This remains an extension point until login, CAPTCHA, OTP, destination choice, account selection, and external-effect controls are explicitly approved. The framework must not bypass CAPTCHA or assume automatic OTP delivery.

## 7. Common HTTP Client Contract

The HTTP client must support:

- HTTP method, path, query parameters, headers, and optional body.
- `Accept-Language` from runtime configuration.
- One unique `X-Request-Id` per request.
- Confirmed session transport.
- Selected `Account-Ref` only for APIs that require account context.
- JSON content type where applicable.
- Connection and request timeouts.
- Secure TLS verification by default.
- Separate capture of response headers, body, HTTP status, elapsed time, and curl exit code.
- Redacted command evidence.
- No automatic retry for business operations unless explicitly approved.
- A scenario classification that prevents destructive packs from running accidentally.

## 8. Assertion Contract

The shared assertion library should support:

- Expected curl exit code.
- Expected HTTP status.
- Expected public `ApiStatus`.
- Generic base API response compatibility.
- JSON path exists or is absent.
- Scalar equality and pattern checks.
- Array size and minimum-size checks.
- Error target checks using frontend field IDs.
- Response content-type checks.
- Request-ID traceability checks.
- Language-specific message checks.
- Required or forbidden header checks.
- Sensitive-field absence checks.
- Internal downstream status exposure checks.
- Contract-specific extension assertions supplied by feature packs.

The JSON tool must be confirmed during Gate A. If `jq` is selected, preflight must fail clearly when `jq` is unavailable.

## 9. Evidence Contract

Each execution must create one explicit timestamped evidence directory:

```text
target/evidence/<timestamp>-bff-e2e-<scenario-pack>-<scenario-id>/
```

Recommended contents:

```text
001-preflight.txt
002-runtime-input-manifest-redacted.txt
003-bootstrap-summary-redacted.txt
004-request-context-summary-redacted.txt
005-request-command-redacted.txt
006-response-headers-redacted.txt
007-response-body-redacted.json
008-assertions.txt
009-exit-codes.txt
010-final-status.txt
```

### 9.1 Evidence rules

- The evidence path must be explicit throughout the run.
- Newest-directory discovery is prohibited.
- Secrets and personal data must never be retained.
- Session IDs, cookies, tokens, passwords, OTPs, and CAPTCHA answers must always be redacted.
- `Account-Ref` must be redacted unless an approved synthetic value is used.
- Failure diagnostics must be preserved after redaction.
- Evidence must identify whether test data is real approved data or synthetic data.
- Synthetic data must never be represented as a real environment result.
- Final status must identify scenario pack, scenario ID, environment label, result, and evidence manifest.

## 10. Scenario-Pack Model

### 10.1 Platform pack

Cross-cutting scenarios shared by all features:

```text
PLATFORM-001 health or safe reachability
PLATFORM-002 request-ID generation and propagation
PLATFORM-003 Accept-Language behavior
PLATFORM-004 generic base-response schema
PLATFORM-005 unsupported method or route behavior
PLATFORM-006 sensitive-data and internal-status audit
PLATFORM-007 timeout and transport-failure handling
```

### 10.2 Authentication pack

```text
AUTH-001 login request contract
AUTH-002 CAPTCHA boundary
AUTH-003 OTP destination choice
AUTH-004 OTP verification
AUTH-005 account-selection response
AUTH-006 invalid or expired session
AUTH-007 lock or downstream policy mapping
```

External effects and unavailable test controls must be classified explicitly rather than automated unsafely.

### 10.3 Registration pack

Covers registration start, eligibility, CAPTCHA, OTP lifecycle, username completion, additional eligible-trust registration, cancellation, and return-to-login behavior.

### 10.4 Username Recovery pack

Covers CAPTCHA and identity verification, username disclosure rules, no-OTP behavior, public error mappings, and anti-enumeration controls approved for the feature.

### 10.5 Forgot Password pack

Covers the canonical Forgot Password feature, OTP controls, TAS password-policy mappings, public-status normalization, and exclusion of post-login Change Password.

### 10.6 Personal Information pack

Covers GET and PUT, language-based messages, field IDs in error targets, country-dependent validation, blocked-address rules, APIM validation mappings, and mutation-response behavior.

### 10.7 Non-Member Access pack

Covers the dedicated verification endpoint, authorization behavior, and protected-context boundaries.

### 10.8 Account Balance pack

The first intended feature consumer. Scenarios are added when the Account Balance endpoint and response contract are approved. The pack must remain a consumer of the common framework, not the owner of it.

### 10.9 Future feature packs

Every future BFF feature should add a scenario pack under the same framework and reuse the common bootstrap, HTTP, assertion, redaction, and evidence libraries.

## 11. Gate Model

# Gate A: Framework Design Ready

## Objective

Establish an evidence-based design for a BFF-wide scripted E2E framework without changing production behavior or inventing feature contracts.

## Activities

1. Inventory existing repository scripts, `.devsecops` assets, test utilities, and evidence conventions.
2. Inventory command-line tools, Bash version, curl, JSON processors, TLS tools, and archive utilities.
3. Map anonymous, pre-login, login, OTP, CAPTCHA, account-selection, provided-session, and post-login boundaries.
4. Map canonical headers and session transport.
5. Map the generic base API response and reusable public-error contracts.
6. Identify safe non-destructive endpoints for framework validation.
7. Map local, container, and deployed runtime configuration sources.
8. Define the framework directory, scenario registry, and feature-pack extension model.
9. Define runtime variables and ownership without values.
10. Define evidence ordering, redaction, and failure preservation.
11. Define `anonymous` and `provided-session` bootstrap modes.
12. Define the future `scripted-auth` boundary without implementing unsafe automation.
13. Classify existing and planned BFF features into scenario packs.
14. Identify destructive, external-effect, environment-blocked, and contract-pending scenarios.
15. Confirm that the framework does not overlap production feature implementation.
16. Update the plan and cumulative `handover.md` with confirmed findings.

## Required evidence

```text
001-repository-baseline.txt
002-existing-script-inventory.txt
003-tooling-inventory.txt
004-authentication-and-session-map.txt
005-request-context-map.txt
006-shared-response-contract-map.txt
007-safe-endpoint-inventory.txt
008-framework-and-runtime-design.txt
009-redaction-and-evidence-design.txt
010-feature-pack-decision-matrix.txt
011-preservation-audit.txt
012-final-status.txt
```

## Acceptance criteria

- Repository and evidence conventions are mapped.
- Required command-line dependencies are identified.
- Anonymous, authentication, session, and selected-account boundaries are documented.
- Canonical request-context ownership is confirmed.
- The framework design is feature-neutral.
- Runtime variable names and ownership are defined without values.
- The evidence and redaction model is complete.
- Initial bootstrap modes are `anonymous` and `provided-session`.
- Feature packs and extension rules are defined.
- No endpoint, field, status, or error mapping is invented.
- No production source code changes.
- No SMS, OTP, CAPTCHA, login, or mutation flow is triggered.
- The repository remains clean.

## Completion statement

```text
Gate A: FRAMEWORK_DESIGN_READY
Commit: NOT_REQUIRED_FOR_INVESTIGATION
Evidence manifest: target/evidence/<timestamp>-bff-e2e-gate-a/manifest.txt
Reviewed: repository conventions, tools, bootstrap modes, request context, shared contracts, evidence, redaction, feature packs
Outstanding: Gate B, Gate C, Gate D
```

# Gate B: Core Harness Implemented

## Objective

Implement and commit the feature-neutral runner, runtime handling, bootstrap modes, HTTP client, assertions, redaction, evidence generation, scenario registry, and safe scenario stubs.

## Activities

1. Create the repository-owned E2E framework structure.
2. Implement strict preflight checks.
3. Implement runtime configuration without populated committed values.
4. Implement `anonymous` bootstrap.
5. Implement `provided-session` bootstrap.
6. Implement the common curl client.
7. Implement unique request-ID generation.
8. Implement selected `Account-Ref` handling without client-side related-account resolution.
9. Implement response and timing capture.
10. Implement shared assertions.
11. Implement redaction and evidence safety audits.
12. Implement explicit timestamped evidence directories.
13. Implement scenario registry, pack selection, and destructive-scenario safeguards.
14. Add the platform scenario pack and pending feature-pack stubs.
15. Add safe self-tests that require no business feature endpoint.
16. Add shell syntax, redaction, assertion, and evidence-layout tests.
17. Document one-command execution and extension rules.
18. Update `handover.md`.
19. Create one deterministic implementation commit.

## Required evidence

```text
001-pre-state.txt
002-file-manifest.txt
003-shell-syntax.txt
004-static-safety-audit.txt
005-redaction-tests.txt
006-assertion-library-tests.txt
007-evidence-layout-test.txt
008-scenario-registry-test.txt
009-preservation-audit.txt
010-diff-check.txt
011-commit-verification.txt
012-gate-manifest.txt
013-final-status.txt
```

## Acceptance criteria

- One documented command starts the framework.
- Missing dependencies fail during preflight with actionable messages.
- Anonymous and provided-session modes work without retaining sensitive inputs.
- Canonical headers and context are applied consistently.
- Assertions return non-zero on failure.
- Evidence paths are explicit and repeatable.
- Redaction tests prove sensitive values are absent from retained evidence.
- Scenario packs reuse the shared framework.
- Pending scenario stubs do not invent contracts.
- Destructive scenarios cannot run by default.
- No populated environment file is tracked.
- Account Balance and other production features remain unchanged.
- Only intended framework, documentation, and handover files are committed.
- The repository is clean after commit.

## Completion statement

```text
Gate B: CORE_HARNESS_IMPLEMENTED
Commit: <full commit hash>
Evidence manifest: target/evidence/<timestamp>-bff-e2e-gate-b/manifest.txt
Reviewed: runner, preflight, bootstrap, HTTP client, assertions, redaction, evidence, scenario registry, platform pack, feature stubs
Outstanding: Gate C, Gate D
```

# Gate C: Framework Technically Validated

## Objective

Prove that the common framework operates correctly against safe non-destructive endpoints and that success, failure, redaction, timeout, TLS, and repeatability behavior are correct.

## Activities

1. Run preflight in the target local environment.
2. Execute a safe public or Actuator request through the common HTTP client.
3. Validate status, headers, body, elapsed time, request ID, and curl exit code.
4. Validate JSON assertion success and controlled failure.
5. Validate missing runtime inputs for each bootstrap mode.
6. Validate timeout handling using an approved controlled mechanism.
7. Validate secure TLS defaults.
8. Validate synthetic-secret redaction.
9. Validate independent evidence directories across repeated runs.
10. Validate scenario-pack selection and destructive-scenario protection.
11. Document environment limitations and deferred feature validations.

## Required evidence

```text
001-preflight.txt
002-runtime-input-manifest-redacted.txt
003-safe-request-command-redacted.txt
004-safe-response-headers.txt
005-safe-response-body.json
006-success-assertions.txt
007-controlled-failure-assertions.txt
008-bootstrap-validation.txt
009-timeout-and-tls-results.txt
010-redaction-audit.txt
011-repeatability-and-registry-audit.txt
012-exit-codes.txt
013-final-status.txt
```

## Acceptance criteria

- Preflight passes in the target environment.
- A safe request completes through the shared client.
- Status, headers, body, timing, request ID, and exit code are captured.
- Assertions produce correct pass and fail outcomes.
- Missing bootstrap inputs fail before unsafe requests.
- Synthetic secrets are absent from evidence.
- Secure TLS verification is the default.
- Repeated runs use separate explicit evidence paths.
- Scenario-pack selection works.
- Destructive scenarios remain blocked by default.
- No real login, OTP, CAPTCHA, SMS, business mutation, or feature operation is performed unless separately approved.

## Completion statement

```text
Gate C: FRAMEWORK_TECHNICALLY_VALIDATED
Commit: <Gate B commit hash>
Evidence manifest: target/evidence/<timestamp>-bff-e2e-gate-c/manifest.txt
Reviewed: preflight, safe requests, assertions, bootstrap failures, redaction, timeout, TLS, repeatability, scenario registry
Outstanding: Gate D feature-pack validation
```

# Gate D: Feature Pack Functionally Validated

## Objective

Complete and execute an approved feature-specific scenario pack after that feature's endpoint, contracts, authorization rules, integrations, and test environment are available.

Gate D is repeatable per feature. Completing Gate D for Account Balance does not imply Gate D completion for login, registration, personal information, Forgot Password, or any other feature.

## Entry criteria

- The feature endpoint and HTTP method are approved.
- Request and response contracts are approved.
- Public statuses and error mappings are approved.
- Authorization and context behavior are approved.
- Required APIM or downstream integrations are available.
- Approved test identities, accounts, sessions, and data are available.
- Environment base URL and TLS trust are available.
- External-effect controls are approved.
- Destructive scenarios have explicit data setup and cleanup rules.

## Activities

1. Replace pending markers with approved feature assertions.
2. Select the correct bootstrap mode.
3. Execute every mandatory scenario in the feature pack.
4. Validate success, validation, authorization, business rejection, technical failure, language, traceability, and sensitive-data behavior as applicable.
5. Validate generic base-response compatibility.
6. Validate that only canonical headers and context are supplied.
7. Validate that internal statuses and protected context are not exposed.
8. Capture redacted evidence for each scenario.
9. Produce a scenario result matrix.
10. Link failures to defects or explicit environment blockers.
11. Determine whether the pack is safe for later Jenkins execution.

## Required evidence

```text
001-environment-preflight.txt
002-bootstrap-summary-redacted.txt
003-request-context-summary-redacted.txt
004-scenario-manifest.txt
005-success-scenario-evidence/
006-validation-and-authorization-evidence/
007-business-error-evidence/
008-technical-error-evidence/
009-language-and-traceability-evidence/
010-sensitive-data-audit.txt
011-scenario-result-matrix.txt
012-defect-and-deferred-items.txt
013-exit-codes.txt
014-final-status.txt
```

## Acceptance criteria

- Every mandatory scenario has a pass, fail, blocked, or not-applicable result.
- Responses match approved feature and generic base contracts.
- Validation and authorization behavior is correct.
- Business and technical errors map to approved public statuses.
- Language behavior follows `Accept-Language`.
- Request-ID behavior is traceable.
- Internal context and unrelated data are absent.
- Evidence contains no credentials, OTPs, sessions, cookies, tokens, CAPTCHA answers, or protected context.
- Failures are linked to defects or explicit blockers.
- Jenkins validation is not claimed unless Jenkins independently runs the pack.

## Completion statement

```text
Gate D: FEATURE_PACK_FUNCTIONALLY_VALIDATED
Feature pack: <feature-pack-name>
Application commit: <full application commit hash>
Harness commit: <full harness commit hash>
Evidence manifest: target/evidence/<timestamp>-bff-e2e-<feature-pack>-gate-d/manifest.txt
Reviewed: approved scenarios, contracts, authorization, public error mapping, language, traceability, sensitive-data audit
Outstanding: other feature packs, Jenkins execution, and deployment acceptance as applicable
```

## 12. Gate Transition Rules

- Gate A must complete before Gate B implementation design is finalized.
- Gate B requires a committed feature-neutral harness and clean repository state.
- Gate C validates the framework, not any business feature.
- Gate D is completed separately for each feature pack.
- Local Gate D evidence cannot declare Jenkins validation complete.
- Deployment acceptance remains separate from local functional validation.
- External-effect and destructive scenarios require explicit approval.

## 13. Relationship to Feature Delivery Plans

This framework is a shared Phase 00 capability and should not be inserted into only the Account Balance plan.

```text
BFF Phase 00: Scripted End-to-End Test Framework
Feature plans: Login, Registration, Username Recovery, Forgot Password,
               Personal Information, Non-Member Access, Account Balance,
               and future BFF features
```

Each feature plan should reference the shared framework and add feature-specific Gate D scenarios at the appropriate contract and implementation stages.

## 14. Deliverables

### Gate A

- Framework investigation evidence.
- Runtime and bootstrap design.
- Redaction and evidence specification.
- Feature-pack matrix.
- Updated plan and `handover.md`.

### Gate B

- Shared runner and libraries.
- Runtime example without values.
- Anonymous and provided-session bootstrap.
- Scenario registry and platform pack.
- Feature-pack stubs.
- Documentation and `handover.md`.
- One implementation commit.

### Gate C

- Safe technical validation evidence.
- Controlled failure evidence.
- Redaction, timeout, TLS, repeatability, and registry results.

### Gate D, per feature

- Approved scenario scripts.
- Redacted per-scenario evidence.
- Scenario result matrix.
- Defect and blocker list.
- Feature-specific completion statement.

## 15. Initial Risks and Controls

### Authentication coupling

Start with anonymous and provided-session modes. Treat scripted authentication as a separate approved extension.

### Secret leakage

Centralize redaction, prohibit shell tracing with sensitive values, and test redaction using synthetic secrets.

### Client-side context reconstruction

For post-login account-scoped calls, send only selected `Account-Ref`. Backend session resolution remains authoritative.

### Invented contracts

Use pending markers until feature contracts are approved.

### Hardcoded environments

Supply base URLs, languages, sessions, accounts, timeouts, and TLS behavior at runtime.

### Curl-only success

Assert transport, HTTP, public API status, schema, and feature behavior separately.

### Destructive execution

Block destructive scenarios by default and require explicit approval plus data lifecycle controls.

### Local evidence confused with CI evidence

Keep local Gates C and D separate from Jenkins-owned validation.

### Framework fragmentation

Require every feature pack to reuse the common runner, HTTP client, assertions, redaction, evidence, and bootstrap libraries.

## 16. Recommended First Step

Begin with a read-only Gate A investigation script named:

```text
bff-phase-00-001-investigate-scripted-e2e-framework.sh
```

The investigation must produce timestamped evidence, leave the repository clean, and end with:

```text
GATE_A_FRAMEWORK_DESIGN_READY PASS
```

No commit is expected from the initial investigation.

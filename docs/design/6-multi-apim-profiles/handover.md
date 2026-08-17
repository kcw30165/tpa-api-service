## Stage 003 Actual Removal Correction

- Stage 003 removal is already present in `HEAD` commit `2fd58638d53c81f657529ad67664f021ba9aba3d`.
- Evidence directory: `target/004D-actually-remove-temporary-portal-access-context-20260811T065418/`.

## Filesystem and Git Truth Check

- `pwd`: `/d/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles`
- repo root: `D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles`
- branch: `features/feature-impl-multiple-apim-profiles`
- `git status --short` before this pass: `M README.md`, `M src/main/resources/application-local.yml`
- candidate files on disk: all four exact temporary source/test paths were `ABSENT`
- `git show --name-status --format=fuller HEAD` proves the temporary source/test deletions are in the existing commit

## Existing Commit Inspection

- Existing commit: `2fd58638d53c81f657529ad67664f021ba9aba3d`
- Deleted in that commit:
  - `src/main/java/com/bct/ngtpa/apiservice/adapter/out/security/TemporaryPortalAccessContextAdapter.java`
  - `src/main/java/com/bct/ngtpa/apiservice/adapter/out/security/TemporaryPortalAccessContextProperties.java`
  - `src/test/java/com/bct/ngtpa/apiservice/adapter/out/security/TemporaryPortalAccessContextAdapterTest.java`
  - `src/test/java/com/bct/ngtpa/apiservice/adapter/out/security/TemporaryPortalAccessContextPropertiesBindingTest.java`

## Production Classes Deleted

- No temporary production classes remain on disk.

## Tests Deleted or Migrated

- Dedicated temporary adapter/property tests are absent.
- Architecture coverage remains in:
  - `TemporaryPortalAccessContextHeaderSessionGuardTest`
  - `Task0043LegacySessionHeaderAndTemporaryContextIsolationTest`
  - `PortalAccessContextArchitectureGuardTest`
- These tests now assert absence of the temporary implementation while preserving authenticated-session security coverage.

## Runtime Configuration Removed

- `src/main/resources/application-local.yml` no longer contains the `temporary-portal-access-context` block.
- No `default-session-id` or `DEMO-SESSION-*` runtime entries remain in active source resources.

## Reference Cleanup

- Active source/resource/README surfaces no remaining must-remove references to the temporary implementation.
- Historical references remain only in docs and prompt artifacts.

## Final PortalAccessContextPort Wiring

- Primary bean remains `AuthenticatedSessionConfig.authenticatedPortalAccessContextPort(...)`.
- Implementation: `AuthenticatedAccountContextResolver`.
- `@Primary` remains on the authenticated bean.
- `PortalAccessContextWebFilter` still injects `PortalAccessContextPort` and resolves selected-account context through the authenticated flow.

## Git Deletion Proof

- `git show --name-status --format=fuller HEAD` shows the four required deletions.
- Working tree still only carried the pre-existing `README.md` and `application-local.yml` modifications during verification.

## Validation Results

- `mvn -DskipTests compile`: passed
- Focused role/model tests: passed
- Spring wiring tests: passed
- Authenticated context tests: passed
- Portal filter/architecture tests: passed
- Auth workflow regression subset: passed
- Cumulative regression subset: passed
- `mvn test`: passed
- `mvn -DskipTests package`: passed

## Static and Configuration Audits

- Active source/resource/README searches are clean for:
  - `TemporaryPortalAccessContextAdapter`
  - `TemporaryPortalAccessContextProperties`
  - `TemporaryPortalAccessContext`
  - `temporary-portal-access-context`
  - `default-session-id`
  - `DEMO-SESSION`
  - `SessionProfile`
  - `AccountProfile`

## Preservation Checks

- `ActorUserRole` remains exactly the approved ten-value enum.
- No `MEMBER`, `role_user`, or `EMPLOYEE` aliases were added.
- `ApimCredentialResolutionContext`, APIM routing, token/certificate caches, registration routing, forgot-password `accountEnv` propagation, and Username Recovery behavior were left untouched.

## Residual Risks

- Historical design and prompt artifacts still mention the removed temporary implementation, but they are non-runtime references only.

## Blockers

- None.

## Stage 003 Final Status

- Complete.

## Next Artifact

- `005-implement-strict-apim-routing-foundation.prompt.md`

## Suggested Commit Message

- `refactor: remove temporary portal access context`

## Stage 005B Registration Routing Validation Status

- Status: IMPLEMENTATION_COMPLETE_PENDING_EXTERNAL_INTEGRATION
- Evidence directory: `target/006B-validate-registration-apim-routing-20260811T184102/`

## Toolchain Proof

- Verified Java/Javac absolute tools are Java 21 and Maven runtime uses Java 21. See evidence file `002-toolchain-proof.txt`.

## Implementation Review

- Inspected the migrated registration call sites and focused transport/adapter classes. See `003-implementation-review.txt`.

## Focused Registration Tests

- All focused registration transport and adapter tests passed. See `004-focused-registration-tests.txt`.

## Registration Workflow Regressions

- No registration workflow regressions found in the targeted groups. See `005-registration-workflow-regressions.txt`.

## Stage 002 Through Stage 004 Regressions

- Stage 002, 003, and 004 regression groups executed and passed in-scope. See `006-stage-002-regressions.txt`, `007-stage-003-regressions.txt`, `008-stage-004-regressions.txt`.

## Forgot Password and Login Preservation

- Forgot Password and login/authenticated preservation groups unchanged and passed. See `009-forgot-password-preservation.txt` and `010-login-authenticated-preservation.txt`.

## Cumulative APIM Regressions

- Cumulative APIM-related adapter and infrastructure tests passed (no new in-scope failures). See `011-cumulative-apim-regressions.txt`.

## Full-Suite Scoped Result

- Full mvn test run produced one failure group limited to external documentation guards (four known items). All Stage-005 in-scope tests passed. See `012-full-suite-first.txt` and `013-full-suite-failure-ledger.txt`.

## Final Package Result

- Package step not executed after final acceptance because external-worktree documentation guards remain; previous compile/package evidence available.

## Remaining Bridge Callers

- Remaining deprecated bridge callers belong to future stages (Forgot Password -> Stage 006, Login/authenticated -> Stage 007). See `027-registration-bridge-audit.txt`.

## Static Audits

- Scope/context, environment-lineage, fallback, configuration/secret, and public-contract audits completed. See `028-scope-context-audit.txt`, `029-environment-lineage-audit.txt`, `030-fallback-audit.txt`, `031-configuration-secret-audit.txt`, `032-public-contract-audit.txt`.

## External Worktree Integration Dependencies

- Known external documentation guards failing (expected):
  - PersonalInformationGetValidationResponseContractDocumentationTest#contractDocumentationCapturesFeValidationResponseRules
  - PersonalInformationRequiredRuleRuntimeStateContractTest#requiredRuntimeStateContractIsDocumented
  - PersonalInformationContractDocumentationGuardTest#documentationCapturesUpdateResultArrayAndOptionSetsContracts
  - CopilotGuidanceContractTest#archivedPromptReadmeExists

Classified as EXTERNAL_WORKTREE_FIX_PENDING_INTEGRATION.

## Preservation Checks

- Preservation checks for Forgot Password, login/authenticated, Username Recovery, OTP, captcha, CSRF, idempotency, unknown-outcome pass. See `033-preservation-checks.txt`.

## Residual Risks

- External documentation artifacts remain missing in this worktree; they are non-blocking for Stage 005 implementation completion.

## Genuine Blockers

- None.

## Stage 005 Final Status

- IMPLEMENTATION_COMPLETE_PENDING_EXTERNAL_INTEGRATION

## Next Artifact

- `007-implement-forgot-password-apim-routing.prompt.md`

## Suggested Commit Message

- `feat: route registration APIM calls by explicit scope`

---

## Stage 006: Forgot Password APIM routing (this pass)

- Status: IN_PROGRESS (implementation applied locally; no commit created)
- Summary: Migrated Forgot Password APIM callers from legacy fail-closed bridge to explicit strict routing. Added authoritative accountEnv to ForgotPasswordAuthFlowContext and propagated it through ForgotPasswordCompletionCommand -> CompleteForgotPasswordService -> ApimForgotPasswordCompletionAdapter. TRP search transport continues to call NON_ACCOUNT; completion uses ACCOUNT_ENV with accountEnv from protected context.
- Files modified (working tree, unstaged):
  - src/main/java/.../application/dto/auth/forgotpassword/context/ForgotPasswordAuthFlowContext.java
  - src/main/java/.../application/usecase/auth/forgotpassword/complete/ForgotPasswordCompletionCommand.java
  - src/main/java/.../application/usecase/auth/forgotpassword/complete/CompleteForgotPasswordService.java
  - src/main/java/.../adapter/out/apim/forgotpassword/details/SharedApimTrpSearchRegedMemberTransport.java
  - src/main/java/.../adapter/out/apim/forgotpassword/ApimForgotPasswordCompletionAdapter.java
  - src/test/java/.../adapter/out/apim/forgotpassword/ApimForgotPasswordCompletionAdapterTest.java
- Evidence directory: `target/007-implement-forgot-password-apim-routing-20260811T200607/`
  - Includes inventory, protected-context summary, targeted test run logs, compile/package evidence, and diff/stat
- Validation performed:
  - Adapter unit test: ApimForgotPasswordCompletionAdapterTest — PASSED
  - mvn -DskipTests package — PASSED (jar produced)
  - Quick compile/test smoke runs performed; full mvn test not executed in this pass
- Remaining work before marking COMPLETE:
  - Add fail-first unit tests enforcing missing/unmapped accountEnv for ACCOUNT_ENV operations
  - Run full focused forgot-password workflow tests and regression groups
  - Verify TRPSearchRegedMember mapper persists env into protected context with unit tests
  - Run full `mvn test` and resolve any failures (external documentation guards allowed)
- Notes:
  - Backwards-compatible constructors added; accountEnv nullable for older sessions
  - No production API payloads were changed (routing hint only)
- Next artifact after acceptance: `008-...` (per project workflow)

---

---

## Cumulative Multiple APIM Profiles Status Correction

This section supersedes earlier intermediate status statements where they conflict with the accepted cumulative implementation and validation results below. Earlier evidence remains preserved as historical execution context.

### Stage 006 Forgot Password Routing Final Status

- Status: `IMPLEMENTATION_COMPLETE_PENDING_EXTERNAL_INTEGRATION`.
- Forgot Password APIM routing was completed and committed before Stage 007 began.
- `TRPSearchRegedMember` uses explicit `ApimDatabaseScope.NON_ACCOUNT` routing with `ActorUserRole.ROLE_USER` and `ApimRetryEligibility.SAFE_BUSINESS_CALL`.
- Forgot Password username lookup reuses the strict `TRPChkCustId` non-account transport.
- `TRPResetPassword` uses explicit `ApimDatabaseScope.ACCOUNT_ENV` routing with `ActorUserRole.ROLE_USER` and `ApimRetryEligibility.IDEMPOTENT_MUTATION`.
- The authoritative environment lineage is:

```text
TRPSearchRegedMember response env
-> ForgotPasswordDetailsLookupResult.accountEnv
-> protected ForgotPasswordAuthFlowContext.accountEnv
-> protected auth-flow session state
-> ForgotPasswordCompletionCommand.accountEnv
-> ApimForgotPasswordCompletionAdapter
-> ApimCredentialResolutionContext.accountEnv
-> ACCOUNT_ENV profile resolution
```

- The frontend cannot supply or override the routing environment.
- No `env` field was added to the `TRPResetPassword` APIM wire request.
- Focused Forgot Password tests, workflow regressions, prior-stage regressions, cumulative APIM tests, and scoped full-suite validation passed in-scope.
- Final package validation passed.
- Earlier text marking Stage 006 `IN_PROGRESS` is superseded by this final status.

### Stage 007 Login and Authenticated Routing Final Status

- Status: `IMPLEMENTATION_COMPLETE_PENDING_EXTERNAL_INTEGRATION`.
- Evidence directory: `target/008-stage007-scoped-validation-20260811T231527/`.
- `SharedApimTrpMemberLoginTransport` was migrated to the strict five-argument facade with explicit `ApimDatabaseScope.NON_ACCOUNT` routing.
- `SharedApimTrpGetAccountListTransport` was migrated to the strict five-argument facade with explicit `ApimDatabaseScope.NON_ACCOUNT` routing according to the approved operation classification.
- No additional authenticated production caller required migration because existing authenticated adapters already used strict routing.
- Account-Ref remains only the frontend account-selection token. Java resolves Account-Ref against the active server-owned authenticated session.
- Account-scoped authenticated operations obtain `accountEnv` from the authorized selected account stored in the authenticated session. Account-Ref is never treated as an environment or profile identifier.
- The approved session lineage remains:

```text
session ID and selected Account-Ref
-> authenticated session in Redis
-> authorized session account
-> accountEnv and account details
-> PortalAccessContext or typed internal context
-> strict APIM facade
-> ACCOUNT_ENV profile resolution where classified
```

- Current `ActorContext` remains unchanged with `actorUserId` and `actorUserRole`. The deferred ActorContext expansion was not implemented.
- Focused login/authenticated validation passed: 220 tests, 0 failures, 0 errors.
- Workflow and cumulative regression validation passed.
- Scoped full-suite result: 2,908 tests run, 3 failures, 1 error, and 12 skipped. All four failure/error results were the known external documentation guards. In-scope failures and errors were zero.
- Final package validation passed.

### Stage 008 Bridge Removal and Finalization Status

- Status: `IMPLEMENTATION_COMPLETE_PENDING_EXTERNAL_INTEGRATION`.
- Final evidence directory: `target/009A-final-validation-20260812T095554/`.
- Main compilation passed.
- Test compilation passed.
- Focused facade, resolver, bean-wiring, startup-validation, token, certificate, cache, retry, exception-mapping, logging, and sanitization validation passed.
- Registration, Forgot Password, login/authenticated, prior-stage, and cumulative APIM regression groups passed.
- The final package passed.

#### Facade Compatibility Removed

The obsolete fail-closed compatibility overloads were removed from `ApimWebClientFacade`:

```text
post(path, body)
post(path, body, resolutionContext)
post(path, body, resolutionContext, retryEligibility)
```

The remaining production facade contract requires all routing inputs explicitly:

```text
post(path, body, databaseScope, resolutionContext, retryEligibility)
```

No production APIM caller uses an implicit or compatibility overload.

#### Resolver Compatibility Removed

- The single-argument `resolve(ApimCredentialResolutionContext)` compatibility method was removed.
- The active resolver contract is:

```java
Mono<ApimCredentialProfile> resolve(
        ApimDatabaseScope scope,
        ApimCredentialResolutionContext context);
```

- `DefaultApimCredentialProfileResolver` is the sole strict production resolver implementation.
- Test doubles were migrated to the strict two-argument resolver method.
- No required profile resolution returns `Mono.empty()`.

#### Trust-Based Resolver Disposition

- `TrustBasedApimCredentialProfileResolver` was removed as an obsolete compatibility placeholder.
- Dedicated or dependent tests were removed or migrated to the strict resolver surface.
- No competing resolver bean remains.

#### Final Context Contract

`ApimCredentialResolutionContext` remains exactly:

```text
ActorUserRole actorUserRole
String accountEnv
```

No operation name, metadata map, request payload, endpoint inference, or profile identifier was introduced into routing context.

#### Final Routing and Fallback Guarantees

- Explicit scopes are limited to `NON_ACCOUNT` and `ACCOUNT_ENV`.
- No default-profile fallback remains.
- No first-profile fallback remains.
- No legacy-global credential fallback remains for required per-profile fields.
- No accountEnv-to-profile identity fallback remains.
- No implicit non-account fallback remains.
- No endpoint, DTO, payload, reflection, or request-metadata inference remains.
- Missing or invalid scope, actor role, environment, mapping, or profile fails before token acquisition, certificate access, encryption, or transmission.
- Token and certificate caches remain isolated by nonblank profile ID.
- Retry behavior reuses the same resolved profile.

### Final Scoped Validation Result

The final validation script reported:

```text
stage_status=IMPLEMENTATION_COMPLETE_PENDING_EXTERNAL_INTEGRATION
main_compile_exit=0
test_compile_exit=0
focused_exit=0
regression_exit=0
full_suite_exit=1
classification=SCOPED_PASS
package_exit=0
automatic_commit=NO
```

The nonzero full-suite exit is caused only by the four external documentation items below. There are no in-scope or newly introduced failures or errors.

### External Worktree Integration Ledger

The following tests remain classified as `EXTERNAL_WORKTREE_FIX_PENDING_INTEGRATION`:

1. `PersonalInformationGetValidationResponseContractDocumentationTest#contractDocumentationCapturesFeValidationResponseRules`
   - Missing file: `doc/others/personal-information-get-validation-response-contract.md`
2. `PersonalInformationRequiredRuleRuntimeStateContractTest#requiredRuntimeStateContractIsDocumented`
   - Missing file: `doc/others/personal-information-get-validation-response-quality-contract.md`
3. `PersonalInformationContractDocumentationGuardTest#documentationCapturesUpdateResultArrayAndOptionSetsContracts`
   - Missing file: `doc/others/personal-information-result-array-and-optionsets-contract.md`
4. `CopilotGuidanceContractTest#archivedPromptReadmeExists`
   - Missing file: `doc/development-log/copilot-prompts/README.md`

These files and tests must not be changed in the multiple-APIM-profile worktree. The owning worktree must be integrated before final project-wide validation.

### Final Zero-Failure Integration Gate

After the external documentation worktree is integrated, run synchronously with Java 21:

```bash
export JAVA_HOME="C:/Java/OpenJDK/jdk-21"
export M2_HOME="/d/Tools/apache-maven-3.9.15"
export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH"
hash -r
mvn test
mvn -DskipTests package
```

## Stage 008C Registration Username Check Runtime Repair

- Summary: Fixed two production defects discovered during local runtime: (1) registration username check was routed using NON_ACCOUNT profile; (2) APIM TRPChkCustId blank/empty err-message with no data was treated as success.

- Changes made:
  - adapter/out/apim/registration/SharedApimTrpChkCustIdTransport.java: added postWithAccountEnv(...) helper which resolves profile using ApimDatabaseScope.ACCOUNT_ENV and ApimCredentialResolutionContext(accountEnv). Implemented stricter response invariants and throw ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID) for malformed upstream payloads.
  - adapter/out/apim/registration/ApimRegistrationUsernameAvailabilityAdapter.java: use SharedApimTrpChkCustIdTransport.postWithAccountEnv when command.selectedTrust.accountEnv is present; fall back to generic transport for test harnesses.

- Runtime findings and validation:
  - Main compile and test-compile: SUCCESS
  - Focused adapter and controller tests covering username-check: ALL PASSED
  - Existing registration and workflow tests exercised; no in-scope regressions identified.

- Routing root cause:
  - Shared transport defaulted to NON_ACCOUNT resolver (line using ApimDatabaseScope.NON_ACCOUNT) causing registration username check to call non-account profile despite protected selectedTrust.accountEnv being available.

- Response validation root cause:
  - toEnvelopePreservingBusinessErrMessage accepted blank err-message as success when data was absent; missing explicit data validation allowed malformed payloads to be treated as success.

- Manual reproduction and result:
  - POST /api/v1/auth/registration/username/check with protected selectedTrust.accountEnv=DB and APIM returning {"response":{"err-message":""}} now results in HTTP 502 and public SYSTEM_ERROR (err.apim.response.invalid).

- Handover: include this section in cumulative handover; no commit created (work applied locally).


The workstream may be changed from `IMPLEMENTATION_COMPLETE_PENDING_EXTERNAL_INTEGRATION` to `COMPLETE` only when:

```text
Failures: 0
Errors: 0
Package: BUILD SUCCESS
```

### Final Preservation Checks

- `ActorUserRole` remains the approved ten-value enum without aliases.
- Temporary portal-access-context production and runtime artifacts remain removed.
- Registration routing and protected account-environment lineage remain intact.
- Forgot Password routing and protected account-environment lineage remain intact.
- Login and authenticated routing remain explicit and server-context-driven.
- Account-Ref remains an authorized session-account lookup token.
- FE, APIM, and TAS wire contracts remain unchanged except for approved internal routing fields.
- Username Recovery behavior remains unchanged.
- OTP, captcha, CSRF, origin, idempotency, replay, unknown-outcome, authenticated-session, logout, expiry, and terminal-state behavior remain unchanged.
- `Accept-Language` remains the canonical language source.
- Only approved request metadata, including `X-Request-Id`, is propagated.
- The deferred ActorContext expansion remains deferred.

### Residual Risks

- Final project-wide zero-failure validation remains dependent on integration of the four external documentation artifacts.
- Deployment configuration must supply valid explicit non-account profile selection, environment-to-profile mappings, and complete per-profile credentials.
- Production monitoring should continue to verify logging sanitization for previously unseen downstream payloads.

### Genuine Blockers

- No multiple-APIM-profile implementation blocker remains.
- External documentation integration is the only remaining project-wide completion dependency.

### Multiple APIM Profiles Final Status

- `IMPLEMENTATION_COMPLETE_PENDING_EXTERNAL_INTEGRATION`

### Final Evidence

- Stage 007 scoped validation: `target/008-stage007-scoped-validation-20260811T231527/`
- Stage 008 final validation: `target/009A-final-validation-20260812T095554/`
- Stage 008 planning and inventory: `target/009-remove-routing-bridges-and-finalize-20260812T071057/`

### Suggested Final Commit Message

```text
refactor: remove APIM routing compatibility bridges
```

### Suggested MR Title

```text
Implement strict multiple APIM profile routing
```

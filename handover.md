# Shared Auth-Flow Revision CAS Handover

## Permanent shared fix

- Shared replace and delete operations compare `sessionRevision`, not regenerated JSON.
- Replacement preserves remaining TTL.
- Legacy initialization retains whole-document proof because legacy records have no revision token.
- Temporary CAS diagnostics are removed.

## Repair 007

The focused Cancel policy test proved the production policy emits source `BFF` for `REVISION_CONFLICT`. Repair 006 retained the incorrect test expectation `SERVER`. Repair 007 changes only the expected source to `BFF`, while preserving `VALIDATION_FAILED`, `RETURN_TO_LOGIN`, `AUTH_FLOW_INVALID`, and HTTP 409. The conflict is still no longer mislabeled as idempotency-key reuse.

## APIM Routing and Request Context Modernization

### Prompt 00: Investigation and program orientation

- Working branch: `feature/apim-routing-context-modernization`.
- Program purpose: remove repeated APIM profile/certificate/encryption/recovery/retry orchestration and replace hidden authenticated request-context access with explicit inbound-to-application-to-outbound contracts.
- Fixed context invariant: authenticated session and Account-Ref resolve once at the inbound web boundary; Account-Ref is opaque; accountEnv and account metadata come only from the matched session AccountContext; application services and outbound adapters do not resolve ambient portal context.
- Fixed routing invariant: database scope and actor role are caller supplied; ACCOUNT_ENV requires authoritative accountEnv; scope is never inferred from blank accountEnv; generic execution must not hardcode NON_ACCOUNT or ROLE_USER_MBR.
- Fixed execution invariant: one profile per logical call; profile owns URL/token/API key/certificate/business call; certificate refresh regenerates encrypted payload; response verification uses the successful attempt key; retry eligibility and business-envelope/failure interpretation remain operation-specific.
- Confirmed context finding: `PortalAccessContextWebFilter` and `AuthenticatedAccountContextResolver` already resolve session-scoped selected-account context, but six application services still inject `CurrentPortalAccessContextResolver`: notifications get/update, contribution get/export, and personal-information get/update.
- Confirmed outbound boundary finding: `TrpSearchMemberAdapter` still accesses Reactor context.
- Confirmed APIM finding: most feature adapters own encryption branches, profile resolution, direct certificate access, encryption, post, and decryption. Selected shared transports use `postEncrypted`, but compatibility paths remain.
- Confirmed key-consistency defect: `ApimWebClientFacade` regenerates encrypted payload after `invalid_public_key` only when an encryption function is attached; legacy resolved-profile paths can retry stale ciphertext and decrypt with a key captured before the successful attempt.
- Routing ledger summary: 15 APIM operations were identified. Notifications retrieval infers scope from blank accountEnv; notification update uses ACCOUNT_ENV but hardcodes role; country-list currently uses NON_ACCOUNT despite the agreed authenticated ACCOUNT_ENV target; Forgot Password completion and TRPChkCustId retain mixed routing paths.
- Duplication clusters: ordinary facade calls; feature-owned encrypted execution; facade-owned `postEncrypted`; shared envelope/reflection conversion; and operation-specific failure policies that must remain outside the generic executor.
- Stashes: Username Recovery emergency backup is superseded duplicated orchestration; broad dev WIP and Forgot Password Redis/CAS WIP are unrelated; the older ContributionSummary clean-architecture stash is a routing/context assumption requiring review. No stash was applied.
- Stage 01 recommendation: characterization only, covering routing ledger, profile resolution count, token/certificate recovery, encrypted request regeneration, successful-attempt key consistency, no routing fallback, retry classification, encryption-field selection, and ambient-context dependency inventory.
- Architect questions: explicit pre-auth actor role; unconfirmed operation scopes; mutation retry safety; country-list HTTP authentication cutover; and expected-failing treatment of legacy stale-payload behavior.
- Prompt 00 made documentation changes only. No production or test implementation was authorized or performed. Stage 01 was not started.
- Prompt 00 final status: `READY_FOR_ARCHITECT_REVIEW`.
- Suggested commit message after architect approval: `docs(architecture): document APIM routing modernization baseline`.

### Stage 01: Baseline and characterization (20260814-221445)

- Status: BLOCKED.
- Scope: test-only characterization; no production implementation changed.
- Tests added: ApimLegacyStalePayloadCharacterizationTest, ApimRoutingLedgerCharacterizationTest, ApimExecutionBoundaryCharacterizationTest, AuthenticatedAmbientContextDependencyCharacterizationTest.
- Current behavior characterized: explicit profile routing, token/certificate recovery branches, operation retry classifications, API-name crypto field boundary, 15-operation routing ledger, and current ambient request-context dependencies.
- Known defect preserved: invalid_public_key without an attached encryption function retries the same encrypted payload; correction remains Stage 03.
- Successful-attempt key gap preserved: the current facade returns response body only and does not expose the successful attempt public key.
- Confirmed decisions represented: Forgot Password username lookup NON_ACCOUNT, Forgot Password completion NON_ACCOUNT target, TRPRegMember protected-state ACCOUNT_ENV, Notifications ACCOUNT_ENV target, and country-list current NON_ACCOUNT mismatch versus authenticated ACCOUNT_ENV target.
- Unconfirmed operation targets remain unchanged.
- Mutation finding: application idempotency precedes transport execution; current IDEMPOTENT_MUTATION classifications are recorded without claiming downstream idempotence.
- Evidence directory: target/evidence/apim-routing-context-stage-01-20260814-221445.
- Fail-first exit: 1; focused exit: 1; cumulative exit: 125; compile exit: 0; full suite: NOT_RUN.
- No src/main, APIM configuration, profile routing configuration, retry configuration, encryption behavior, session behavior, controller behavior, application service behavior, or feature adapter behavior was changed.
- Next gate: architect approval before Stage 02. Stage 02 was not started.
- Commit message after approval: test(apim): characterize profile-aware encrypted execution.

### Stage 01 correction: notification APIM test profile fixtures

- Status: BLOCKED.
- Classification: stale test fixtures after strict profile resolution became mandatory in encrypted Notifications adapter paths.
- Root cause:  and  passed a null ; encrypted adapter tests now call  before certificate access, producing NullPointerException before their intended assertions.
- Correction: supplied test-only profile resolvers returning mocked  values in the two existing facade test doubles.
- Production behavior changed: no.
- Original fail-first characterization evidence remains valid in [INFO] Scanning for projects...
[INFO] 
[INFO] ----------------------< com.bct:ngtpa-api-server >----------------------
[INFO] Building ngtpa-api-server 0.0.1-SNAPSHOT
[INFO]   from pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- jacoco:0.8.12:prepare-agent (default) @ ngtpa-api-server ---
[INFO] argLine set to -javaagent:D:\\Maven\\repository\\org\\jacoco\\org.jacoco.agent\\0.8.12\\org.jacoco.agent-0.8.12-runtime.jar=destfile=D:\\Projects\\ORSO\\ngtpa\\api-service-worktrees\\apim-profiles\\target\\jacoco.exec
[INFO] 
[INFO] --- resources:3.3.1:resources (default-resources) @ ngtpa-api-server ---
[INFO] Copying 7 resources from src\main\resources to target\classes
[INFO] Copying 5 resources from src\main\resources to target\classes
[INFO] 
[INFO] --- compiler:3.14.1:compile (default-compile) @ ngtpa-api-server ---
[INFO] Recompiling the module because of changed source code.
[INFO] Compiling 1360 source files with javac [debug parameters release 21] to target\classes
[INFO] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/ForgotPasswordHttpResponses.java: Some input files use or override a deprecated API.
[INFO] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/ForgotPasswordHttpResponses.java: Recompile with -Xlint:deprecation for details.
[INFO] 
[INFO] --- resources:3.3.1:testResources (default-testResources) @ ngtpa-api-server ---
[INFO] Copying 5 resources from src\test\resources to target\test-classes
[INFO] 
[INFO] --- compiler:3.14.1:testCompile (default-testCompile) @ ngtpa-api-server ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 837 source files with javac [debug parameters release 21] to target\test-classes
[WARNING] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimAdapterReflectionBranchProbeTest.java:[14,16] sun.misc.Unsafe is internal proprietary API and may be removed in a future release
[WARNING] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimWebClientFacadePrivateBranchTest.java:[11,16] sun.misc.Unsafe is internal proprietary API and may be removed in a future release
[WARNING] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimAdapterReflectionBranchProbeTest.java:[14,16] sun.misc.Unsafe is internal proprietary API and may be removed in a future release
[WARNING] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimWebClientFacadePrivateBranchTest.java:[11,16] sun.misc.Unsafe is internal proprietary API and may be removed in a future release
[WARNING] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimAdapterReflectionBranchProbeTest.java:[14,16] sun.misc.Unsafe is internal proprietary API and may be removed in a future release
[WARNING] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimWebClientFacadePrivateBranchTest.java:[11,16] sun.misc.Unsafe is internal proprietary API and may be removed in a future release
[WARNING] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimAdapterReflectionBranchProbeTest.java:[14,16] sun.misc.Unsafe is internal proprietary API and may be removed in a future release
[WARNING] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimWebClientFacadePrivateBranchTest.java:[11,16] sun.misc.Unsafe is internal proprietary API and may be removed in a future release
[WARNING] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimAdapterReflectionBranchProbeTest.java:[14,16] sun.misc.Unsafe is internal proprietary API and may be removed in a future release
[WARNING] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimWebClientFacadePrivateBranchTest.java:[11,16] sun.misc.Unsafe is internal proprietary API and may be removed in a future release
[WARNING] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimAdapterReflectionBranchProbeTest.java:[325,20] sun.misc.Unsafe is internal proprietary API and may be removed in a future release
[WARNING] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimAdapterReflectionBranchProbeTest.java:[325,20] sun.misc.Unsafe is internal proprietary API and may be removed in a future release
[WARNING] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimAdapterReflectionBranchProbeTest.java:[325,20] sun.misc.Unsafe is internal proprietary API and may be removed in a future release
[WARNING] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimAdapterReflectionBranchProbeTest.java:[325,20] sun.misc.Unsafe is internal proprietary API and may be removed in a future release
[WARNING] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimAdapterReflectionBranchProbeTest.java:[325,20] sun.misc.Unsafe is internal proprietary API and may be removed in a future release
[WARNING] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimAdapterReflectionBranchProbeTest.java:[326,23] sun.misc.Unsafe is internal proprietary API and may be removed in a future release
[WARNING] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimAdapterReflectionBranchProbeTest.java:[328,17] sun.misc.Unsafe is internal proprietary API and may be removed in a future release
[WARNING] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimWebClientFacadePrivateBranchTest.java:[48,20] sun.misc.Unsafe is internal proprietary API and may be removed in a future release
[WARNING] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimWebClientFacadePrivateBranchTest.java:[48,20] sun.misc.Unsafe is internal proprietary API and may be removed in a future release
[WARNING] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimWebClientFacadePrivateBranchTest.java:[48,20] sun.misc.Unsafe is internal proprietary API and may be removed in a future release
[WARNING] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimWebClientFacadePrivateBranchTest.java:[48,20] sun.misc.Unsafe is internal proprietary API and may be removed in a future release
[WARNING] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimWebClientFacadePrivateBranchTest.java:[48,20] sun.misc.Unsafe is internal proprietary API and may be removed in a future release
[WARNING] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimWebClientFacadePrivateBranchTest.java:[49,23] sun.misc.Unsafe is internal proprietary API and may be removed in a future release
[WARNING] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimWebClientFacadePrivateBranchTest.java:[51,17] sun.misc.Unsafe is internal proprietary API and may be removed in a future release
[INFO] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordControllerTest.java: Some input files use or override a deprecated API.
[INFO] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordControllerTest.java: Recompile with -Xlint:deprecation for details.
[INFO] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimPayloadCryptoServiceTest.java: Some input files use unchecked or unsafe operations.
[INFO] /D:/Projects/ORSO/ngtpa/api-service-worktrees/apim-profiles/src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimPayloadCryptoServiceTest.java: Recompile with -Xlint:unchecked for details.
[INFO] 
[INFO] --- surefire:3.5.5:test (default-test) @ ngtpa-api-server ---
[INFO] Using auto detected provider org.apache.maven.surefire.junitplatform.JUnitPlatformProvider
[INFO] 
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.bct.ngtpa.apiservice.adapter.out.apim.ApimLegacyStalePayloadCharacterizationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.280 s -- in com.bct.ngtpa.apiservice.adapter.out.apim.ApimLegacyStalePayloadCharacterizationTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] 
[INFO] --- jacoco:0.8.12:report (report) @ ngtpa-api-server ---
[INFO] Loading execution data file D:\Projects\ORSO\ngtpa\api-service-worktrees\apim-profiles\target\jacoco.exec
[INFO] Analyzed bundle 'ngtpa-api-server' with 1307 classes
[WARNING] Classes in bundle 'ngtpa-api-server' do not match with execution data. For report generation the same class files must be used as at runtime.
[WARNING] Execution data for class com/bct/ngtpa/apiservice/adapter/out/apim/registration/ApimRegistrationLookupAdapter does not match.
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  01:00 min
[INFO] Finished at: 2026-08-14T22:27:35+08:00
[INFO] ------------------------------------------------------------------------ through .
- Repair evidence remains in the same exact Stage 01 directory: .
- Repair focused exit: 1; cumulative exit: 125; compile exit: 0; full suite: NOT_RUN.
- Stage 02 was not started.
- Commit message after architect approval: test(apim): characterize profile-aware encrypted execution.

### Stage 01 correction: valid APIM profile fixtures

- Status: BLOCKED.
- Classification: incomplete first test-fixture correction; production behavior remained unchanged.
- Root cause: a mocked  returned null ;  uses  as a  key, so the encrypted tests still failed before certificate behavior.
- Correction: replaced each mocked profile with a valid concrete test profile carrying nonblank profileId, client, token URI, base URL, and certificate path.
- Exact retained evidence directory: .
- Original Prompt 01 fail-first remains preserved in files 010 through 012.
- Second-repair focused exit: 1; cumulative exit: 125; compile exit: 0; full suite: NOT_RUN.
- No production implementation changed. Stage 02 was not started.
- Commit message after architect approval: test(apim): characterize profile-aware encrypted execution.

### Stage 01 correction: valid APIM profile fixtures

- Status: BLOCKED.
- Classification: incomplete first test-fixture correction; production behavior remained unchanged.
- Root cause: a mocked  returned null ;  uses  as a  key, so the encrypted tests still failed before certificate behavior.
- Correction: replaced each mocked profile with a valid concrete test profile carrying nonblank profileId, client, token URI, base URL, and certificate path.
- Exact retained evidence directory: .
- Original Prompt 01 fail-first remains preserved in files 010 through 012.
- Second-repair focused exit: 1; cumulative exit: 125; compile exit: 0; full suite: NOT_RUN.
- No production implementation changed. Stage 02 was not started.
- Commit message after architect approval: test(apim): characterize profile-aware encrypted execution.


### Stage 01 correction: profile-aware certificate test doubles

- Status: BLOCKED.
- Classification: stale certificate test doubles after profile-aware certificate routing.
- Root cause: the six Notifications certificate doubles overrode `getBctPublicKey()`, while production now invokes `getPublicKey(ApimCredentialProfile)`. The inherited method attempted real certificate fetching with a null WebClient builder.
- Correction: all six certificate doubles now override the profile-aware method and retain their original fixed, rejecting, or failing behavior.
- Exact retained evidence directory: `target/evidence/apim-routing-context-stage-01-20260814-221445`.
- Original Prompt 01 fail-first remains preserved in files 010 through 012.
- Third-repair focused exit: 1; cumulative exit: 125; compile exit: 0; full suite: NOT_RUN.
- No production implementation changed. Stage 02 was not started.
- Commit message after architect approval: `test(apim): characterize profile-aware encrypted execution`.


### Stage 01 correction: resolved-profile facade test doubles

- Status: READY_FOR_ARCHITECT_REVIEW.
- Classification: stale facade test doubles after resolved-profile encrypted execution.
- Root cause: encrypted Notifications paths call `post(path, body, profile, retryEligibility)`, while the facade doubles only overrode the scope/context overload. The inherited profile overload reached a null token service.
- Correction: both facade doubles now override the resolved-profile overload and retain their original request capture and fixed-response behavior.
- Exact retained evidence directory: `target/evidence/apim-routing-context-stage-01-20260814-221445`.
- Original Prompt 01 fail-first remains preserved in files 010 through 012.
- Fourth-repair focused exit: 0; cumulative exit: 0; compile exit: 0; full suite: NOT_RUN.
- No production implementation changed. Stage 02 was not started.
- Commit message after architect approval: `test(apim): characterize profile-aware encrypted execution`.


### Stage 02: Generic APIM execution contract

- Status: `READY_FOR_ARCHITECT_REVIEW`.
- Production contracts added under `adapter.out.apim.execution`: `ApimExchangeRequest<R,D>`, `ApimExchangeExecutor`, and `ApimSuccessfulExchange`.
- Executor signature: `<R,D> Mono<ApimResponseEnvelope<D>> postEnvelope(ApimExchangeRequest<R,D> request)`. No implementation or Spring bean was added.
- Request signature fields: path, plain request, request type, response data type, database scope, credential resolution context, and operation-specific retry eligibility.
- Request invariants: nonblank path; nonnull and type-compatible request; nonnull response type, scope, context, actor role, and retry eligibility; `ACCOUNT_ENV` requires nonblank accountEnv; `NON_ACCOUNT` preserves any supplied accountEnv; no scope, role, or retry default.
- Successful metadata signature: response body, resolved credential profile, and successful-attempt response verification key. Token, API key, certificate text, request payload, session data, and business result are not exposed. Null key remains uninterpreted for non-encrypted exchange handling in Stage 03/04.
- Non-goals preserved: no executor implementation; no facade, crypto, certificate, resolver, retry, adapter, transport, controller, service, use-case, command, configuration, or wiring change; stale-payload behavior remains characterized; no feature migration.
- Evidence directory: `target/evidence/apim-routing-context-stage-02-20260814-230802`.
- Validation exits: fail-first 1; Stage 02 focused 0; Stage 01 preservation 0; cumulative 0; compile 0; full suite NOT_RUN.
- Stash remained unchanged. Stage 03 remains gated by architect approval and was not started.
- Commit message after approval: `feat(apim): define generic profile-aware exchange contract`.


### Stage 03: Successful-attempt key consistency

- Status: `BLOCKED`.
- New facade APIs: `Mono<ApimSuccessfulExchange> postSuccessfulExchange(String,Object,ApimDatabaseScope,ApimCredentialResolutionContext,ApimRetryEligibility)` and `Mono<ApimSuccessfulExchange> postEncryptedSuccessfulExchange(String,BiFunction<ApimCredentialProfile,PublicKey,Object>,ApimDatabaseScope,ApimCredentialResolutionContext,ApimRetryEligibility)`.
- Successful encrypted metadata carries the one resolved profile and exact public key used to encrypt the successful HTTP attempt. Plain successful metadata carries the profile and null key.
- `invalid_public_key` on corrected encrypted execution evicts the profile certificate, refreshes the key, regenerates ciphertext, retries once on the same profile, and returns the refreshed successful key.
- Body-only resolved-profile execution no longer retries unchanged ciphertext because no regeneration function exists.
- Token recovery retains the same profile, ciphertext, and verification key. Encrypted business retry creates a fresh encrypted attempt and returns its key.
- Existing public `Mono<String>` methods remain and safely delegate to successful-exchange methods where applicable. No feature adapter or transport was migrated.
- Evidence directory: `target/evidence/apim-routing-context-stage-03-20260814-233639`. Validation exits: fail-first 1; focused 1; Stage 02 preservation 0; cumulative 125; compile 0; full suite NOT_RUN.
- Stash remained unchanged. Stage 04 remains gated and was not started.
- Commit message after approval: `refactor(apim): preserve encryption key across successful exchange attempts`.


### Stage 03 validation rerun correction (target/evidence/apim-routing-context-stage-03-20260814-233639)

- Status: `BLOCKED`.
- The original Stage 03 focused output was invalid because a generated test contained a stray character and failed test compilation. The user corrected the test typo; no production behavior was changed by that correction.
- Validation-only rerun results: focused 1; Stage 02 preservation 125; cumulative APIM regressions 125; compile 0; full suite NOT_RUN.
- Exact evidence directory retained: `target/evidence/apim-routing-context-stage-03-20260814-233639`.
- The rerun script changed no production or test code. HEAD and stash state stayed unchanged. Stage 04 was not started.
- Commit message after architect approval: `refactor(apim): preserve encryption key across successful exchange attempts`.


### Stage 03 obsolete body-only retry test correction (target/evidence/apim-routing-context-stage-03-20260814-233639)

- Status: `BLOCKED`.
- Two existing facade retry tests still expected Stage 01 body-only `invalid_public_key` retry behavior. Stage 03 intentionally removes that unsafe retry.
- The tests now require one HTTP attempt, one certificate eviction, no unchanged-payload retry, and normalized APIM failure propagation.
- Validation exits: focused 1; Stage 02 preservation 125; cumulative 125; compile 0.
- Exact evidence directory: `target/evidence/apim-routing-context-stage-03-20260814-233639`. No additional production change was made. Stage 04 was not started.
- Commit message after approval: `refactor(apim): preserve encryption key across successful exchange attempts`.


### Stage 03 inline credential-profile fixture correction (target/evidence/apim-routing-context-stage-03-20260814-233639)

- Status: `READY_FOR_ARCHITECT_REVIEW`.
- The preceding narrow retry-test update referenced an undefined `validProfile(String)` helper. Exactly two calls were replaced with explicit `ApimCredentialProfile` test fixtures.
- No production behavior changed. Validation exits: focused 0; Stage 02 preservation 0; cumulative 0; compile 0.
- Exact evidence directory retained: `target/evidence/apim-routing-context-stage-03-20260814-233639`. HEAD and stash remained unchanged. Stage 04 was not started.
- Commit message after approval: `refactor(apim): preserve encryption key across successful exchange attempts`.

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


### Stage 04: Generic APIM exchange executor

- Status: `BLOCKED`.
- Added `DefaultApimExchangeExecutor` as the sole `ApimExchangeExecutor` implementation with constructor dependencies `ApimWebClientFacade`, `ApimPayloadCryptoService`, and `ApimProperties`.
- Plain mode transforms the request with a null key, calls `postSuccessfulExchange`, requires a null successful key, and parses the typed envelope with a null key.
- Encrypted mode supplies the crypto transformation callback to `postEncryptedSuccessfulExchange` and decrypts the response with the exact successful-attempt key.
- Scope, credential context, request/reply type tokens, and retry eligibility pass directly from `ApimExchangeRequest`; no routing default or inference was introduced.
- Synchronous crypto failures are contained by `Mono.defer`. Empty or inconsistent successful metadata fails deterministically. Business-envelope interpretation remains feature-owned.
- No feature adapter or transport migrated. Evidence directory: `target/evidence/apim-routing-context-stage-04-20260815-001352`. Validation exits: fail-first 1; focused 0; Stage 02/03 preservation 1; cumulative 125; compile 0; full suite NOT_RUN.
- Stash remained unchanged. Stage 05 remains gated and was not started.
- Commit message after approval: `feat(apim): add generic profile-aware exchange executor`.


### Stage 04 execution-package inventory correction (target/evidence/apim-routing-context-stage-04-20260815-001352)

- Status: `READY_FOR_ARCHITECT_REVIEW`.
- `ApimExecutionContractArchitectureTest` still enforced the Stage 02 three-file package inventory. Stage 04 intentionally adds `DefaultApimExchangeExecutor`.
- The test now preserves the three approved Stage 02 contracts and permits only the approved default executor as the fourth Java file. No production code changed in this repair.
- Validation exits: Stage 04 focused 0; Stage 02/03 preservation 0; cumulative 0; compile 0; full suite NOT_RUN.
- Exact evidence directory retained: `target/evidence/apim-routing-context-stage-04-20260815-001352`. HEAD and stash remained unchanged. No feature migrated and Stage 05 was not started.
- Commit message after approval: `feat(apim): add generic profile-aware exchange executor`.


### Stage 05: Forgot Password username-lookup pilot

- Status: `READY_FOR_ARCHITECT_REVIEW`.
- Migrated chain: trusted Forgot Password start mapper supplies `ROLE_USER_MBR`; start command and lookup command carry it explicitly; the APIM adapter creates `ApimCredentialResolutionContext(role, null)`, selects `NON_ACCOUNT` and `SAFE_BUSINESS_CALL`, builds typed `ApimExchangeRequest`, and invokes `ApimExchangeExecutor.postEnvelope`.
- Manual facade, certificate, crypto, encryption-branch, and profile-resolution ownership was removed from the migrated adapter. Operation-specific TRPChkCustId envelope/error mapping remains in the adapter.
- Registration and `SharedApimTrpChkCustIdTransport` remain unchanged. No other APIM operation migrated.
- Evidence: `target/evidence/apim-routing-context-stage-05-20260815-075842`. Exits: fail-first 1; focused 0; foundation 0; cumulative 0; compile 0; full NOT_RUN. Stash unchanged. Stage 06 was not started.
- Commit after approval: `refactor(forgot-password): use generic APIM exchange for username lookup`.


### Stage 005 behavioral-test and evidence repair (target/evidence/apim-routing-context-stage-05-20260815-075842)

- Status: `BLOCKED`.
- Added direct adapter tests for executor request capture, success, recognized business rejection, and malformed response behavior.
- Added mapper proof of server-side `ROLE_USER_MBR`, service proof that the role reaches `StartForgotPasswordUsernameLookupCommand`, and reflection proof that the FE start request exposes only `username`.
- Validation totals: complete focused ; foundation ; cumulative ; compile exit 0.
- Registration production files, HEAD, and stash remained unchanged. Evidence: `target/evidence/apim-routing-context-stage-05-20260815-075842`. Stage 006 was not started.
- Commit after approval: `refactor(forgot-password): use generic APIM exchange for username lookup`.


### Stage 005 behavioral-test compilation correction (target/evidence/apim-routing-context-stage-05-20260815-075842)

- Status: `READY_FOR_ARCHITECT_REVIEW`.
- Corrected only the new behavioral tests: repository `MemberIdentityType.ID`, Mockito generic `thenAnswer`, and FE DTO component-type name assertion.
- Behavioral intent is unchanged: direct adapter routing/mapping, mapper role provenance, service role propagation, and FE authority exclusion.
- Validation totals: focused 19; foundation 40; cumulative 54; compile exit 0. Registration production, HEAD, and stash remained unchanged.
- Evidence: `target/evidence/apim-routing-context-stage-05-20260815-075842`. Stage 006 was not started. Commit after approval: `refactor(forgot-password): use generic APIM exchange for username lookup`.


### Stage 006: Forgot Password completion pilot

- Status: `BLOCKED`. Trusted mapper assigns `ROLE_USER_MBR`; completion commands carry it explicitly; adapter uses `NON_ACCOUNT`, null environment, `IDEMPOTENT_MUTATION`, and `ApimExchangeExecutor`.
- Application idempotency key+payload behavior and completion-specific success, rejection, system failure, malformed response, and unknown-outcome ownership remain unchanged. No other APIM operation migrated.
- Evidence: `target/evidence/apim-routing-context-stage-06-20260815-084115`. Exits: fail-first 1; focused 1; preservation 0; cumulative 125; compile 0. Stash unchanged. Stage 007 was not started.
- Commit after approval: `refactor(forgot-password): use generic APIM exchange for password completion`.


### Stage 006 architecture whitespace repair (target/evidence/apim-routing-context-stage-06-20260815-084115)

- Status: `BLOCKED`.
- Corrected only a whitespace-sensitive architecture assertion. Production already used caller role and null environment correctly.
- Validation totals: focused 14; Stage 005/foundation preservation 40; cumulative 23; compile exit 0.
- HEAD and stash remained unchanged. Evidence: `target/evidence/apim-routing-context-stage-06-20260815-084115`. Stage 007 was not started.
- Commit after approval: `refactor(forgot-password): use generic APIM exchange for password completion`.


### Stage 006 routing-ledger ownership correction v2 (target/evidence/apim-routing-context-stage-06-20260815-084115)

- Status: `BLOCKED`. The routing ledger now follows Stage 006 ownership regardless of source formatting: trusted mapper owns `ROLE_USER_MBR`; commands carry it; adapter consumes `command.actorUserRole()` and owns `NON_ACCOUNT` plus `IDEMPOTENT_MUTATION`.
- No production code changed. Validation totals: focused 5; preservation ; cumulative ; compile exit 0. HEAD and stash unchanged.
- Evidence: `target/evidence/apim-routing-context-stage-06-20260815-084115`. Stage 007 was not started. Commit after approval: `refactor(forgot-password): use generic APIM exchange for password completion`.


### Stage 006 exact routing-ledger entry correction (target/evidence/apim-routing-context-stage-06-20260815-084115)

- Status: `READY_FOR_ARCHITECT_REVIEW`.
- Updated the exact TRPResetPassword routing-ledger entry from the pre-migration state to `NON_ACCOUNT`, `command.actorUserRole()`, null environment, `IDEMPOTENT_MUTATION`, and executor ownership.
- The separate trusted-role provenance test remains. No production code changed.
- Validation exits: focused 0; Stage 005/foundation preservation 0; cumulative 0; compile 0.
- Exact evidence directory: `target/evidence/apim-routing-context-stage-06-20260815-084115`. HEAD and stash remained unchanged. Stage 007 was not started.
- Commit after approval: `refactor(forgot-password): use generic APIM exchange for password completion`.


### Stage 006 behavioral and idempotency evidence repair (target/evidence/apim-routing-context-stage-06-20260815-084115)

- Status: `BLOCKED`. Added executor-backed completion behavior coverage, all completion result/error scenarios, idempotency suppression and replay call-count proof, explicit service role propagation, FE authority exclusion, and a documented `testTransport` compatibility seam.
- Validation totals: focused 3; Stage 005/foundation ; cumulative completion/architecture ; compile exit 0. Production scope, HEAD, and stash remained unchanged.
- Evidence: `target/evidence/apim-routing-context-stage-06-20260815-084115`. Stage 007 was not started. Commit after approval: `refactor(forgot-password): use generic APIM exchange for password completion`.


### Stage 006 idempotency forced-record fixture correction (target/evidence/apim-routing-context-stage-06-20260815-084115)

- Status: `READY_FOR_ARCHITECT_REVIEW`. Corrected the test-only forced idempotency outcome fixture to supply the nonnull record required by `ForgotPasswordIdempotencyStartResult`.
- All executor-backed completion, result/error, idempotency suppression/replay, role propagation, FE authority, and compatibility-seam tests remain in scope.
- Validation totals: focused 31; Stage 005/foundation 40; cumulative 66; compile exit 0. No production change; HEAD and stash unchanged.
- Evidence: `target/evidence/apim-routing-context-stage-06-20260815-084115`. Stage 007 was not started. Commit after approval: `refactor(forgot-password): use generic APIM exchange for password completion`.

### Stage 007 authenticated-context contract correction fail-first (target/evidence/apim-routing-context-stage-07-20260815-100259)

- Status: `FAIL_FIRST_CONFIRMED`.
- Exact inventory: `target/evidence/apim-routing-context-stage-07-20260815-100259/005-authenticated-context-exact-inventory.txt` and `target/evidence/apim-routing-context-stage-07-20260815-100259/006-authenticated-context-exact-sources.txt`.
- Added package-wide architecture enforcement before production changes.
- Real failing Maven run: `target/evidence/apim-routing-context-stage-07-20260815-100259/011-fail-first-output.txt`.
- Fail-first Maven exit: `1`.
- Target flow: inbound accessor and authenticated controllers own current-context access; application services and outbound adapters receive explicit trusted values; APIM execution remains unchanged.
- Notifications APIM migration was not started. Stage 008 was not started.

### Stage 007 authenticated-context contract correction implementation

- Explicit evidence: `target/evidence/apim-routing-context-stage-07-20260815-100259`.
- The inbound controllers now copy trusted account and actor values into explicit commands. Six application services and `UseCaseConfig` no longer receive `CurrentPortalAccessContextResolver`.
- Portal identity Reactor access is prohibited outside inbound web delivery. Request-ID and logging Reactor context remain permitted.
- APIM production adapters and execution behavior are unchanged. Notifications migration and Stage 008 were not started.
- See `stage-07-completion-report.txt` and `090-final-status.txt` for validation status.
- Commit after approval: `refactor(auth): make portal access context explicit at use-case boundary`.

### Stage 007 cumulative test-call-site repair

- Exact evidence: `target/evidence/apim-routing-context-stage-07-20260815-100259`.
- Updated affected tests to use explicit authenticated command values and the new service/configuration signatures, without reintroducing ambient resolver injection.
- Controller tests provide the inbound-only resolver. APIM production remains unchanged, and Stage 008 was not started.
- Final status is recorded in `090-final-status.txt` and `stage-07-completion-report.txt`.

### Stage 007 final validation

- Explicit evidence: `target/evidence/apim-routing-context-stage-07-20260815-100259`.
- Final focused, architecture, foundation, compile, ambient-context, APIM-preservation, file-classification, HEAD, and stash gates are recorded in the Stage 007 completion package.
- No APIM adapter migrated. Notifications migration and Stage 008 were not started.
- Final status: see `090-final-status.txt` and `stage-07-completion-report.txt`.
- Commit after approval: `refactor(auth): make portal access context explicit at use-case boundary`.

### Stage 007 final focused repair

- Exact evidence: `target/evidence/apim-routing-context-stage-07-20260815-100259`.
- Corrected the enriched update-command test stub and made the controller trusted-context architecture rule identifier-agnostic.
- Final focused, architecture, foundation, compile, ambient-context, APIM, HEAD, stash, classification, and diff evidence is retained in the same explicit directory.
- Notifications migration and Stage 008 were not started.
- Final status is in `090-final-status.txt` and `stage-07-completion-report.txt`.

### Stage 007 completion

- Explicit evidence: `target/evidence/apim-routing-context-stage-07-20260815-100259`.
- Focused, architecture, foundation, compile, ambient-context, outbound Reactor-context, APIM scope, complete file classification, diff, HEAD, and stash gates are retained.
- Application services and `UseCaseConfig` are ambient-context free. Controllers own trusted context copying into explicit commands.
- Request-ID and logging Reactor context remain allowed. No APIM adapter migrated, Notifications migration was not started, and Stage 008 was not started.
- Final status: see `090-final-status.txt` and `stage-07-completion-report.txt`.
- Commit after approval: `refactor(auth): make portal access context explicit at use-case boundary`.

### Stage 008 Notifications authenticated executor fail-first

- Exact evidence: `target/evidence/apim-routing-context-stage-08-20260815-114232`.
- Added the Stage 008 fail-first architecture guard for `TRPGetMsgBoard` and `TRPUpdMsgRead` before production changes.
- The guard requires typed `ApimExchangeExecutor` ownership, authoritative `ACCOUNT_ENV`, command-supplied actor role and account environment, operation-specific retry eligibility, retained feature mapping, and Stage 007 context preservation.
- Real failing Maven output is retained in `011-fail-first-complete-output.txt`; production was unchanged and Stage 009 was not started.

### Stage 008 boundary-guard routing-inference correction

- Exact evidence: `target/evidence/apim-routing-context-stage-08-20260815-114232`.
- Removed the remaining broad prohibition on `StringUtils.hasText` from the retrieval architecture assertion. Date parsing may use `StringUtils`; routing inference via `NON_ACCOUNT`, hardcoded role, facade, certificate, crypto, or profile handling remains prohibited.
- No production code changed in this repair. The original fail-first and prior blocked focused outputs remain preserved.
- Stage 009 was not started.

### Stage 008 focused rerun after exact boundary-guard inventory

- Exact evidence: `target/evidence/apim-routing-context-stage-08-20260815-114232`.
- Inventory proved the stale `StringUtils.hasText` prohibition was already absent from the current untracked Stage 008 guard. No additional code correction was necessary.
- The guard and complete Stage 008 focused suite were rerun against the exact inventoried test hash.
- No production or test content was modified by this rerun. Stage 009 was not started.

### Stage 008 completion

- Exact evidence: `target/evidence/apim-routing-context-stage-08-20260815-114232`.
- Both authenticated Notifications APIM operations use typed generic executor requests with authoritative `ACCOUNT_ENV`, explicit command role/environment, and approved retry classifications.
- Focused, Stage 007 preservation, APIM foundation, cumulative Notifications, compile, static scope, stash, HEAD, index, and non-Notifications APIM preservation evidence is retained.
- No other APIM operation migrated. Stage 009 was not started.
- Final status is in `099-final-status.txt` and `100-stage-08-completion-report.txt`.

### Stage 008 complete behavioral-test and evidence repair

- Exact evidence: `target/evidence/apim-routing-context-stage-08-20260815-114232`.
- Restored all operation-specific retrieval and update behavior coverage and added direct executor-backed tests for exact exchange fields, single invocation, blank-environment precondition, malformed/business/technical failures, and mapping behavior.
- Focused validation explicitly includes Notifications service, controller, mapper, reference-date, date-format, timezone, filtering, sorting, and pagination ownership tests.
- Corrected completion totals are derived from the new Maven outputs. Production was unchanged by this repair, APIM foundation and stash are preserved, and Stage 009 was not started.

### Stage 008 exact test-API contract repair

- Exact evidence: `target/evidence/apim-routing-context-stage-08-20260815-114232`.
- Corrected test-only API assumptions to the repository contracts: `ApimExchangeRequest.request()`, `ActorUserRole.ROLE_USER_EPR`, and `NoticeMessage.msgStatus()`.
- Re-ran adapter behavior, complete focused, Stage 007 preservation, APIM foundation/prior pilots, cumulative Notifications/architecture, and compile gates.
- Final totals are derived from the new Maven outputs. Production, HEAD, index, stash, and APIM foundation are preserved. Stage 009 was not started.

### Stage 009 country-list authenticated executor fail-first

- Exact evidence: `target/evidence/apim-routing-context-stage-09-20260815-134755`.
- Added the Stage 009 fail-first architecture guard before production changes.
- The guard requires inbound trusted selected-account context, an explicit country-list command, typed `ApimExchangeExecutor` ownership, `ACCOUNT_ENV`, command role/environment, `SAFE_BUSINESS_CALL`, preserved language and mapping behavior, and executor-owned routing ledger state.
- The real failing Maven run is retained in `011-fail-first-complete-output.txt`. Production, HEAD, index, stash, and APIM foundation remain unchanged. Stage 010 was not started.

### Stage 009 country-list authenticated correction implementation

- Exact evidence: `target/evidence/apim-routing-context-stage-09-20260815-134755`.
- Country list now receives trusted `accountEnv` and actor role from the inbound current-context resolver, carries them explicitly through the command and port, and invokes the typed generic APIM executor with `ACCOUNT_ENV` and `SAFE_BUSINESS_CALL`.
- Country mapping, language selection, public path, and shared envelope interpretation remain feature-owned. Direct facade, crypto, hardcoded role, and non-account routing were removed only from the country-list adapter.
- Validation is still required. Stage 010 was not started.

### Stage 009 country-list validation

- Exact evidence: `target/evidence/apim-routing-context-stage-09-20260815-134755`.
- Final validation status: `BLOCKED`.
- Fail-first remains 5 tests run with 4 failures, 0 errors, and 0 skipped before implementation.
- Focused, architecture, Stage 007, Stage 008, APIM foundation, cumulative, compile, static ownership, and preservation evidence are retained in the explicit Stage 009 directory.
- Stage 010 was not started.

### Stage 009 country-list validation

- Exact evidence: `target/evidence/apim-routing-context-stage-09-20260815-134755`.
- Final validation status: `BLOCKED`.
- Fail-first remains 5 tests run with 4 failures, 0 errors, and 0 skipped before implementation.
- Focused, architecture, Stage 007, Stage 008, APIM foundation, cumulative, compile, static ownership, and preservation evidence are retained in the explicit Stage 009 directory.
- Stage 010 was not started.

### Stage 009 country-list validation

- Exact evidence: `target/evidence/apim-routing-context-stage-09-20260815-134755`.
- Final validation status: `PASS`.
- Fail-first remains 5 tests run with 4 failures, 0 errors, and 0 skipped before implementation.
- Focused, architecture, Stage 007, Stage 008, APIM foundation, cumulative, compile, static ownership, and preservation evidence are retained in the explicit Stage 009 directory.
- Stage 010 was not started.

### Stage 009 boundary-guard correction

- Exact evidence: `target/evidence/apim-routing-context-stage-09-20260815-134755`.
- Corrected the stale country-list service architecture assertion introduced by the cumulative writer. The controller must consume `CurrentPortalAccessContextResolver`; the application service must remain ambient-context free and pass the explicit command to the port.
- Production was unchanged by this repair. The complete Stage 009 validator was rerun, and Stage 010 was not started.

### Stage 009 worktree-integrity and bounded-validation repair

- Exact evidence: `target/evidence/apim-routing-context-stage-09-20260815-134755`.
- Restored 811 unrelated line-ending-only test-file modifications after raw HEAD-versus-worktree newline-normalized comparison; no unrelated real content change was accepted.
- The corrected review classifier allows only the country-list implementation, country-list tests and guards, routing ledger, `handover.md`, and `docs/integration/apim/authenticated-context-apim-profile-implementation-plan.md`.
- Final bounded validation status: `PASS`. Maven totals are recorded only from the actual outputs in the corrected completion report.
- HEAD, index, stash, implementation plan, and non-country production content are preserved. Stage 010 was not started.

## Stage 010 final explicit-role repair

- Removed all Personal Information retrieval constructor paths that substituted `ROLE_USER_MBR`.
- Updated affected callers and tests to pass `ActorUserRole` explicitly.
- Added `PersonalInformationRetrievalNoRoleSubstitutionGuardTest`.
- Retained the original real fail-first evidence in the explicit Stage 010 evidence directory.
- Personal Information update production remains unchanged and Stage 011 has not started.

## Stage 011 Personal Information update

- Migrated `TRPUpdMemberInfo` to `ApimExchangeExecutor.postEnvelope`.
- Routing is explicit: `ACCOUNT_ENV`, command actor role/account environment, and `IDEMPOTENT_MUTATION`.
- Preserved YAML validation, frontend targets, result-array and partial-success behavior, business/technical mapping, and mutation uncertainty checks.
- Stage 010 retrieval remains unchanged. Stage 012 has not started.

### Stage 012: Contribution Summary authenticated transport migration
- Accepted the original classification blocker as a governance checkpoint and revised the boundary to both independent callers sharing `TRPGetContSumy`.
- Retrieval and export each execute one independent APIM retrieval; export workbook formatting remains after retrieval and outside transport.
- The shared focused command contains five payload fields plus `accountEnv` and `actorUserRole` routing metadata.
- Routing is `ACCOUNT_ENV`, command actor role/account environment, and `SAFE_BUSINESS_CALL` through `ApimExchangeExecutor.postEnvelope`.
- Stage 010 and 011 production, APIM foundation, and workbook formatting remain preserved. Stage 013 has not started.


## Stage 013 - Internal Reference-Date Refresh Routing Correction

Implementation in progress. Internal refresh uses an explicit compatibility role and typed APIM exchange; normal reads no longer auto-refresh through APIM. Evidence: target/evidence/apim-routing-context-stage-13-20260816-101615.

## Stage 014 Login authentication APIM exchange
TRPMemberLogin uses NON_ACCOUNT routing with null accountEnv, explicit pre-authentication ROLE_USER_MBR routing metadata supplied by StartLoginService, SAFE_BUSINESS_CALL, and ApimExchangeExecutor. TRPGetAccountList remains reserved for Stage 015.

## Stage 015 Login account-list APIM exchange
TRPGetAccountList uses NON_ACCOUNT routing with null accountEnv, actor role validated from protected LoginPreAuthContext, SAFE_BUSINESS_CALL, and ApimExchangeExecutor. Returned account environments remain output data. OTP, validation, Account-Ref assignment, and session order are unchanged. Stage 016 is not started.

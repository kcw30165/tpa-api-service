# 07 Implementor prompts (GitHub Copilot)

Use a fresh Copilot session for each milestone. Common preamble - prepend to every prompt:

```text
You are the implementor for api-service feature "Change of Investment Choice" (CIM).
Worktree: /home/nd2887/projects/orso/ngtpa/api-service-worktrees/change-of-investment-choice
Branch: feature/change-of-investment-choice. Design pack: docs/design/17-change-of-investment-choice/ (authoritative).
Toolchain: Maven 3.9.15 /home/nd2887/tools/apache-maven, Java 21.0.12.1 /home/nd2887/tools/jdk-21.0.12.1+1.
Redis for tests: -Dm15.redis.server=/usr/bin/redis-server on every test run; zero skipped tests required.
Rules: do only this sub-task; no guesses - if code at HEAD contradicts the pack, STOP and report with file:line;
fail-first tests where behaviour changes; keep public contracts of other features unchanged;
use the existing MutationResponse / ApiStatus / ApiError types unchanged; no nextStep;
no direct log.error / atError in CIM code - incidents go through FailureEventPort (04 s3);
ErrorLogCallSiteRatchetArchitectureTest KNOWN_REMAINING must not change;
never log bodies, tokens, policy, certificate, user id, account ref, fund codes or percentages;
run spotless:check, compile, test-compile and the targeted tests; produce one commit with the given message; do not push.
```

## 0.1 Read-only check of remaining facts

```text
READ-ONLY. No source, test or doc changes, no commit, no Maven.
First verify HEAD equals the pack commit given by Nick and the worktree is clean; otherwise STOP.
J/ = src/main/java/com/bct/ngtpa/apiservice/. Quote file:line; mark CONFIRMED / DIFFERS (detail) / NOT FOUND.
1 ExceptionMappings: how a mapping declares log level and stack-trace policy (ErrorDescriptor, LogLevel, singleWithoutMessage
  signatures); confirm a 503 mapping can declare WARN without stack trace; show FundFactSheetExceptionMappings as reference
  and how ExceptionMappingRegistryConfig registers it.
2 FailureEventPort: how ApimRegistrationLookupAdapter obtains it, and whether an application-layer use case may depend on it
  (existing application callers, architecture tests that constrain it).
3 PersonalInformationConfigCacheService wiring: where the bean is constructed (config class), its constructor dependencies,
  and PersonalInformationConfigCacheEntry.belongsTo implementation.
4 Password validation: token header name constant, PasswordValidationRouteMatcher paths, how the gate resolves purpose today,
  and the existing PI issuance validation status code.
5 RequestLanguageResolver.resolve signature and the zh_HK constant used by FundPriceWebMapper.displayFundName.
6 ApimRoutingLedgerCharacterizationTest: how an entry is declared (one example) and the asserted count.
7 Architecture tests that would constrain new packages adapter/in/web/investmentchoice, application/usecase/investmentchoice,
  adapter/out/apim/investmentchoice (list test class and rule).
Stop after the table; list every DIFFERS separately at the end.
```

## 1.1 - 1.4 Password validation second purpose

```text
Sub-task 1.<n> per 05-implementation-plan.md M1.
1.1 add CHANGE_INVESTMENT_CHOICE; regression tests proving PERSONAL_INFORMATION_UPDATE behaviour is unchanged first.
1.2 issuance structure check selected by purpose; CIM = strict V1-V8 of 04 s2; unknown members rejected.
1.3 token gate maps (method, route) -> purpose; add POST /api/v1/investment-choice/instructions; path-variant tests
    (trailing slash, case, encoded segments) as for Personal Information.
1.4 register the CIM route as confidential; logging tests prove no body/token/error text is logged.
Commit messages:
1.1 feat(password-validation): add change-of-investment-choice purpose
1.2 feat(password-validation): validate issuance payload structure per purpose
1.3 feat(password-validation): gate investment-choice instruction route
1.4 merged into 1.3: feat(password-validation): gate and protect investment-choice instruction route
```

## 2.1 - 2.2 TRPGetCIMDtl

```text
Implement 03 section 1 exactly, with local strict formatters (D-33). SAFE_BUSINESS_CALL, ACCOUNT_ENV.
Per-operation envelope interpretation, not ApimResponseValidator.
Error ownership per 04 s3: classify with ApimFailureClassifier, map category to FailureOrigin, publish exactly one
FailureEvent, then the feature exception. Nonblank err-message -> DOWNSTREAM_CONTRACT; adapter-detected malformed ->
DOWNSTREAM_INVALID_RESPONSE.
Map ApimFailureCategory -> FailureOrigin with an exhaustive switch inside the adapter (same-named values; no shared mapper).
Build events with new FailureEvent(...), capability "INVESTMENT_CHOICE".
Add the adapter to ApimExecutionBoundaryCharacterizationTest's adapter list.
Tests first: every field rule, every integrity rule, strict dates, empty data, >1 item, each err-message row,
exactly one FailureEvent per failure with the expected origin, empty eligibility slot, ledger entry, ratchet unchanged.
Commit messages:
2.1 feat(investment-choice): add TRPGetCIMDtl APIM contract types
2.2 feat(investment-choice): add TRPGetCIMDtl adapter
```

## 3.1 - 3.3 GET

```text
3.1 InvestmentChoiceSnapshotCacheService: copy the structure of PersonalInformationConfigCacheService (01 s6):
    key investment-choice.snapshot, Wire record, read = miss on any failure, write = updateIfUsable best effort,
    belongsTo(accountRef, policyNo). Tests mirror PersonalInformationConfigCacheServiceTest.
3.2 GetInvestmentChoiceUseCase: assembly, AccountIdentifierFormatter (failure -> LOCAL_SYSTEM event + exception),
    snapshot write after success (never fails the GET).
3.3 Controller GET + InvestmentChoiceWebMapper (01 s7) + exception mappings (WARN, ApiError.system) + ErrorCodes.
    Contract tests assert $.success, $.status, $.result.data..., $.messages, $.errors and for 503
    $.result null, $.errors[0].type SYSTEM, code, targets [], severity ERROR, source SERVER, no message.
Commit messages:
3.1 feat(investment-choice): cache submit snapshot in session feature state
3.2 feat(investment-choice): assemble investment choice details
3.3 feat(investment-choice): expose GET /api/v1/investment-choice
```

## 4.1 - 4.4 Submit

```text
Implement 01 s8-s9, 02 s2, 03 s2, 04 s1-s4.
4.1 TRPCIMReq adapter: NON_RETRYABLE, never resubmitted; no success-flag check (D-14); outcome = success /
    err-message (DOWNSTREAM_CONTRACT event) / unknown (classified event); one FailureEvent per failure.
    Same exhaustive category->origin switch and FailureEvent constructor as 2.2; add to ApimExecutionBoundaryCharacterizationTest.
4.2 Validator V1-V8, S1-S3 exactly as 04 s2; S2 pinned by one dedicated test (D-37: true = editable).
4.3 Use case: snapshot miss -> LOCAL_SYSTEM event + 503; expansion; reconciliation once with TRPGetCIMDtl, today
    Asia/Hong_Kong, exact (switch-src, fund-code, pcnt) set; exactly one match -> 200 SUBMITTED; WARN logs only.
4.4 Controller POST behind the token gate; VALIDATION_FAILED -> 422 override as PersonalInformationController.
Commit messages:
4.1 feat(investment-choice): add TRPCIMReq adapter
4.2 feat(investment-choice): validate instruction against snapshot
4.3 feat(investment-choice): submit and reconcile instruction
4.4 feat(investment-choice): expose POST /api/v1/investment-choice/instructions
```

## 4b.0 - 4b.3 Cancel (M4b)

Prompts are issued one at a time by Nick's reviewer with the expected HEAD filled in. Scope and rules are in 01 s10, 02 s3, 03 s3, 04 s8 and 06 D-38 to D-43. Commit messages:

```text
4b.0 feat(password-validation): add cancel-investment-choice purpose and issuance check
4b.0 feat(password-validation): gate and protect investment-choice cancel route
4b.1 feat(investment-choice): add TRPCnclCIMReq adapter
4b.2 feat(investment-choice): track pending references in the snapshot
4b.2 feat(investment-choice): cancel pending instruction
4b.3 feat(investment-choice): expose POST /api/v1/investment-choice/instructions/cancel
```

## M6 purpose-owned prechecks (approved target; implementation not authorized here)

The M1/M4 prompts above preserve the implementation history and acceptance
criteria for M5. For a separately authorized M6 implementation, use design
pack 16's M6 sections and the target contracts in documents 01, 02, 04, 05 and
06 of this pack. Do not execute this guidance without a new implementation
prompt.

Required implementation boundaries:

- Keep Jackson parsing in adapters. Add only the minimal purpose-specific
  payload representation needed to preserve presence, JSON kinds, unknown
  member names and decoded values. Do not create a general-purpose JSON
  framework.
- Register each supported purpose's decoder and framework-free application
  precheck policy. Fail composition for missing, duplicate or mismatched
  registrations. Adding a purpose must not add branches to shared parsing or
  issuance algorithms.
- Run CIM submit V1-V8/S1-S3 and cancel CV1-CV2/pending-reference membership
  exactly once at issuance, before throttle/password verification. Return
  detailed 422 errors, or feature-specific 503 for snapshot miss with exactly
  one feature-owned `LOCAL_SYSTEM` event and no shared duplicate.
- Reuse the existing gate binding attribute. Require it at the CIM controller
  boundary and in the command. Do not treat `PayloadDigest` alone as
  authorization; the gate's successful scope/token consumption and
  canonicalizer-based body-binding checks establish trusted authorization.
- Keep strict decoding and typed construction at execution, but do not rerun
  feature prechecks. Keep submit reconciliation and existing successful
  snapshot behavior. Cancel does no snapshot/membership check before dispatch;
  its post-success snapshot cleanup is best effort and cannot negate downstream
  success.
- Preserve PI issuance/PUT behavior, Progress's existing current-state
  enforcement, token scope/lifetime/consumption/canonicalization and all
  explicit exclusions in document 06. No token migration/versioning, snapshot
  concurrency redesign, CAS refactor or GET fencing.

Tests must prove registration failures, precheck-before-throttle/password
ordering, no precheck rerun at execution, unbypassable trusted handoff,
feature error/event ownership, PI compatibility, best-effort cancel cleanup,
and absence of submitted values/secrets from logs.

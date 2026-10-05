# Centralized Template Service — Implementation & Migration Plan

Companion to `template-service-solution-design.md` (the "what/why"). This
document is the "how/when": it sequences all remaining work into phases
and stages, so no stage is skipped and migration never begins before the
service it depends on is actually live.

Status key: `DONE`, `IN PROGRESS`, `PENDING`.

---

## Phase I — Centralized Template Service Implementation (producer side)

Goal: make the Template Service itself real and operational — a live,
Spring-wired component with real content — before any workflow is asked
to depend on it.

| Stage | Work | Status |
|---|---|---|
| I-1 | Core abstractions: `NoticeTemplateDefinition` (record/DTO), `NoticeTemplateRegistryPort` (interface) | DONE (script 010) |
| I-2 | YAML-backed implementation: `NoticeTemplateProperties` (`@ConfigurationProperties`), `YamlNoticeTemplateRegistryAdapter` | DONE (script 014); registered as Spring beans in I-4 |
| I-3 | APIM DTO extension: `schemeName`/`schemeNameChi` (`sch-name`/`chin-sch-name`) added to `TrpGetAccountListApimDataItem`, `ForgotPasswordTrpSearchMemberDataItem`, `TrpSearchMemberApimDataItem` (usernamerecovery) | DONE (script 018) |
| I-4 | **Spring wiring**: `NoticeTemplateServiceConfig` class with `@EnableConfigurationProperties(NoticeTemplateProperties.class)`, a constructor that calls `properties.validate()` at startup (fail-fast), and a `@Bean NoticeTemplateRegistryPort` factory method. Confirmed precedent via investigation (script 024): mirrors `FundPriceDateFormatValidationConfig` exactly (validate-in-constructor + single `@Bean` producing what the properties back), not merely `TrpGetAccountListAdapterConfig`'s more elaborate multi-bean shape. Covered by a context-loading test using Spring Boot's `ApplicationContextRunner` | DONE |
| I-5 | **Populate `application.yml`** under `notice-templates.definitions.*` with real content for all 4 message types x 2 channels x 2 locales, sourced verbatim from the BRD SMS sheet and the full literal text of the 3 email docx files, with `{ttlValue}`/`{ttlUnit}` placeholders (confirmed: keep dynamic TTL wording for SMS and email, consistent with current production; NOT applied to `username-recovery`, which the BRD defines with no OTP/expiry). Generic Chinese SMS templates for `login-otp`/`forgot-password-otp` and `username-recovery` reconstructed from the BRD's filled-in examples by substituting example values back to placeholders. **Known content gap, transcribed faithfully and flagged for business review, not altered:** the Chinese email subject for `login-otp`/`forgot-password-otp` and `username-recovery` has no trust-code placeholder in the source docx, unlike the English subject. | DONE |
| I-6 | Startup validation / smoke test: `NoticeTemplateRegistryFullMatrixSmokeTest` confirms `YamlNoticeTemplateRegistryAdapter.resolve(...)` returns correct content for all 4x2x2 combinations against the real `application.yml`, including per-type required-placeholder sets, non-blank EMAIL subjects, null SMS subjects, `login-otp`/`forgot-password-otp` byte-for-byte content synchronization (§5), and that `en`/`zh_HK` content genuinely differs (guards against a silent locale-fallback regression) | DONE |

**Gate before Phase II begins:** I-4, I-5, and I-6 are all complete and
validated. **Phase I is now closed.** The Template Service is live,
Spring-wired, populated with real content, and correctness-tested across
the full 4x2x2 matrix. Phase II (workflow migration) may now begin,
starting with Stage II-1 (Username Recovery).

---

## Phase II — Workflow Migration (consumer side)

Goal: switch each of the four workflow adapters from its own scattered
policy lookup to calling the now-live `NoticeTemplateRegistryPort`
directly, per the seam identified in the solution design (§7.2):
`RegistrationMessageDeliveryAdapter`-style classes resolve content, then
still build `MessageDeliveryCommand` and call
`MessageDeliveryPort.deliver(command)` exactly as today.

| Stage | Workflow | Notes | Status |
|---|---|---|---|
| II-1 | Username Recovery | Simplest — single call site in `UsernameRecoveryFeatureConfig` / `UsernameRecoveryRequestProtectedOperation`. First to confirm whether `schemeName`/`schemeNameChi` already flow from the DTO through `TrpSearchMemberApimResultMapper` → `UsernameRecoveryLookupCandidate`, or need additive plumbing. | IN PROGRESS |
| II-2 | Registration | Replace `RegistrationMessageDeliveryAdapter`'s `subject()`/`body()` policy lookup with `NoticeTemplateRegistryPort.resolve(...)`. No scheme-name placeholder needed (§8.2). | PENDING |
| II-3 | Login | Replace `LoginOtpSendPolicy`-based lookup. Requires scheme-name plumbing from `TrpGetAccountListApimDataItem`. | PENDING |
| II-4 | Forgot Password | **Last** — highest risk, since it currently has no live YAML wiring at all. Reconcile content to match `login-otp` verbatim (§5). Requires scheme-name plumbing from `ForgotPasswordTrpSearchMemberDataItem`. | PENDING |

Each stage follows the established investigate → implement → validate
pattern: confirm full current source of the call site before any edit,
implement with full test coverage per the tiered policy, validate with
`mvn -DskipTests compile` + `test-compile` (full `mvn test` + app-startup
smoke test before each stage transition, per standing convention), commit.

---

## Phase III — Cleanup (only after all four workflows are migrated)

| Stage | Work | Status |
|---|---|---|
| III-1 | Remove the four old scattered config blocks (`auth-workflows.registration.otp.*`, `auth-workflows.login.otp.*`, `auth-workflows.username-recovery.template.*`, and the orphaned `auth-workflows.forgot-password.otp.localized-template.*` node) | PENDING |
| III-2 | Remove `ForgotPasswordOtpTemplates` Java class | PENDING |
| III-3 | Remove the duplicated `ttlUnit()` helpers in `ForgotPasswordDetailsOtpProtectedOperation` / `ForgotPasswordOtpResendProtectedOperation` | PENDING |

Cleanup is deliberately deferred until **all four** workflows are
migrated — not performed per-workflow — so that no workflow is left
depending on a config block that has already been removed.

---

## Current Position

Phase I (stages I-1 through I-6) is complete and closed. Phase II, Stage II-1 (Username
Recovery) is implemented and committed (II-1.a to II-1.d, see Appendix A.1) but remains
**IN PROGRESS** until its stage gate is met: a passing full `mvn test` and an application
startup smoke test with no bean or configuration errors, both evidenced. Decisions, verified
gate inputs and open items are recorded in Appendix A.2. Stages II-2 (Registration), II-3
(Login), II-4 (Forgot Password) and Phase III are PENDING. Work that is deliberately outside
this workstream is listed in Appendix B.

## Appendix A: Stage II-1 Sub-Plan (Username Recovery Migration Detail)

Investigation (scripts 031–033) traced the full current send path and
confirmed the following before any implementation:

- **Gap confirmed:** `schemeName`/`schemeNameChi` (added to the three APIM
  DTOs in Phase I, stage I-3) do **not** yet flow through
  `TrpSearchMemberApimResultMapper` into `UsernameRecoveryLookupCandidate`,
  `UsernameRecoveryCandidate` (domain), or `UsernameRecoverySessionCandidate`.
  Additive plumbing is required end-to-end.
- **Confirmed available:** `trustCode` already survives the full chain
  (`UsernameRecoveryCandidate.trustCode()` is accessible at
  `buildDeliveryCommand()` time). `context.language()` is a required,
  non-null field populated once at session-start time from
  `command.language()`, reliably present for every session.
- **Confirmed behavior change (user-approved):** today,
  `UsernameRecoveryFeatureConfig` wires `emailSubject`/`emailBody`/
  `smsBody` as fixed strings **once at Spring startup** from
  `AuthWorkflowProperties.UsernameRecovery.getTemplate()` (a single,
  locale-agnostic YAML value). This migration changes that to **per-request
  resolution** via `NoticeTemplateRegistryPort.resolve(USERNAME_RECOVERY,
  channel, locale)`, using the member's actual `context.language()` at
  request time. This is an intended improvement (matches the BRD's
  per-locale requirement), not an unapproved scope expansion.

Because this migration spans three layers (APIM DTO → domain model →
feature wiring → request-time resolution), it is split into four
sub-stages, each independently compiled, tested, and committed:

| Sub-stage | Change | Files touched |
|---|---|---|
| II-1.a | Add `schemeName`/`schemeNameChi` to `UsernameRecoveryLookupCandidate` (DTO) and `UsernameRecoveryCandidate` (domain), both via compatibility constructors so existing call sites are untouched. Wire `TrpSearchMemberApimResultMapper` to read `item.schemeName()`/`item.schemeNameChi()` from the APIM DTO and populate the new fields. | `UsernameRecoveryLookupCandidate.java`, `UsernameRecoveryCandidate.java`, `TrpSearchMemberApimResultMapper.java` + tests |
| II-1.b | Add `schemeName()`/`schemeNameChi()` accessor methods to `UsernameRecoverySessionCandidate` (delegating to the wrapped `candidate`). Thread the new fields through `UsernameRecoveryStartProtectedOperation.createRevisioned()`'s candidate construction so they survive into the session. **Known landmine to fix in this sub-stage:** `UsernameRecoverySessionCandidate.withState()` currently rebuilds its wrapped `UsernameRecoveryCandidate` via the old 6-arg constructor; once that becomes a compatibility constructor (II-1.a), every state transition (reserve/markRequested/markFailed/markUnknownOutcome) would silently null out `schemeName`/`schemeNameChi`. `withState()` must be updated to use the canonical 8-arg constructor, carrying forward `candidate.schemeName()`/`candidate.schemeNameChi()`. | `UsernameRecoverySessionCandidate.java`, `UsernameRecoveryStartProtectedOperation.java` + tests |
| II-1.c | Replace `UsernameRecoveryFeatureConfig`'s static-string constructor wiring with injecting `NoticeTemplateRegistryPort` directly. Update `UsernameRecoveryRequestProtectedOperation`'s constructor signature accordingly (drops `emailSubject`/`emailBody`/`smsBody` parameters, adds the port). | `UsernameRecoveryFeatureConfig.java`, `UsernameRecoveryRequestProtectedOperation.java` + tests |
| II-1.d | Update `UsernameRecoveryRequestProtectedOperation.buildDeliveryCommand()` to resolve content per request via `NoticeTemplateRegistryPort.resolve(MessageDeliveryType.USERNAME_RECOVERY, channel, context.language().toLanguageTag())`, apply the confirmed business rule (empty `schemeNameChi` falls back to `schemeName`), and populate the `MessageDeliveryCommand` variables map with `username`, `trustCode`, `schemeName`/`schemeNameChi`. | `UsernameRecoveryRequestProtectedOperation.java` + tests |

**Cleanup note:** the old `auth-workflows.username-recovery.template.*`
YAML block remains in place but unused after II-1.c/d completes — per the
Phase III gate, removal is deferred until **all four** workflows are
migrated, not performed per-workflow.

Each sub-stage follows: confirm no further assumptions needed → implement
→ `mvn -DskipTests compile` + `test-compile` → scoped `mvn test` for the
affected classes → commit → proceed to the next sub-stage.

## Appendix A.1: Stage II-1 Progress and Gate Status

| Sub-stage | Status | Reference |
|---|---|---|
| II-1.a schemeName/schemeNameChi through lookup candidate and domain candidate | DONE | b3c1289c |
| II-1.b session candidate accessors, withState() field-loss fix, StartProtectedOperation threading | DONE | dd1aa4fd |
| II-1.b addendum: protected session context serializer | DONE | 8889c0a1 |
| II-1.c NoticeTemplateRegistryPort injected into UsernameRecoveryFeatureConfig | DONE | 8889c0a1 |
| II-1.d per-request resolution in UsernameRecoveryRequestProtectedOperation | DONE | 8889c0a1 |

**Finding added during implementation (not in the original sub-plan).**
`AesGcmUsernameRecoveryAuthFlowContextProtectorAdapter` keeps a private `Candidate` payload
record that serializes fields explicitly and rebuilt the domain candidate through the 6-argument
compatibility constructor. `schemeName`/`schemeNameChi` were therefore dropped whenever the session
context was protected after START and unprotected at REQUEST, so REQUEST never saw them. The
payload now carries both fields and a round-trip test guards it. Any future field added to
`UsernameRecoveryCandidate` must also be added to that payload.

**Carry into II-3 (Login) and II-4 (Forgot Password).** Both workflows keep protected session
contexts. Before changing their send paths, read their context serializers in full and confirm
whether they serialize candidate or account fields explicitly. `schemeName`/`schemeNameChi` must
survive that round trip, the same way they now do for Username Recovery.

**Test-scope finding.** Script 034 changed `UsernameRecoveryCandidate.toString()` to list the new
fields and ran only a hand-picked test list, which missed two existing exact-string guards
(`UsernameRecoveryCandidateTest`, `UsernameRecoverySessionCandidateTest`). The wide pattern run
in script 044 caught them. `toString()` now omits `schemeName`/`schemeNameChi` again, so the
redaction format those tests pin is unchanged and the values are still never disclosed.
Scoped runs for this workstream use wildcard patterns, not named lists.

**Known behaviour, decision pending.** EMAIL templates for `username-recovery` require
`{schemeName}`. If the APIM response has a blank or absent `sch-name`, rendering throws and
`MessageDeliveryAdapter` reports FAILED for that email. SMS content has no scheme placeholder and
is unaffected. The business rule implemented is only: blank `chin-sch-name` falls back to `sch-name`.

**Language finding (scripts 046-049).** Username Recovery START builds `language` with
`Locale.forLanguageTag(Accept-Language)` and REQUEST selects the template from
`context.language().toLanguageTag()`. The registry matched only the exact tag `zh_HK` (after `-`
to `_`), so `zh`, `zh-TW`, `zh-Hant` and similar fell back to English content, and the returned
definition reported the unmatched tag rather than the key used. The shared request pipeline treats
every language starting with `zh` as Traditional Chinese (`RequestHeaderContext`). The registry now
selects from the configured keys (exact, then same language, then `en`) and reports the selected
key; see solution design section 8.4. Fixed in `b9012557`.
Not changed: `UsernameRecoveryController.resolveLanguage` still builds the `Locale` from the raw
header, so a multi-value header becomes `und` and selects English. Carry into II-2 to II-4: pass a
single tag, and read each workflow's language source before changing its send path.

**Stage gate (not yet satisfied, so II-1 is not marked DONE).** The standing convention requires,
before a stage transition, evidence that the full Maven test suite passed and that an application
startup smoke test confirmed the Spring application starts without bean or configuration errors.
Both are run manually. Stage II-1 becomes DONE only after that evidence exists.

## Appendix A.2: Stage II-1 Decisions, Verified Gate Inputs and Open Items

Recorded after scripts 050 and 051 (read-only evidence). Statements below are limited to what
that evidence shows; anything not shown is listed under Open items.

### Decisions and positions (2026-10-05)

| # | Topic | Position | Status |
| --- | --- | --- | --- |
| D1 | Blank or missing `sch-name` | `sch-name` is non-empty in the source database, so EMAIL rendering keeps failing fast on a blank value and no fallback is implemented. See solution design section 8.5. | Confirmed by Nick |
| D2 | `UsernameRecoveryLookupCandidate.toString()` | No change. It prints `<redacted>` or null for `userName`, `schemeName` and `schemeNameChi`, and `UsernameRecoveryLookupCandidateSchemeNameTest` guards that. The domain `UsernameRecoveryCandidate.toString()` omits the scheme names and its exact-string tests pin that format. Neither discloses the values. | Recorded, no change |
| D3 | Accept-Language | The front end always sends a single tag. `UsernameRecoveryController` keeps building its `Locale` from the raw header. See solution design section 8.5. | Recorded, no code change |
| D4 | `zh-CN` content | `zh-CN` receives `zh_HK` content until a `zh_CN` template exists. See solution design section 8.5 for the caveat. | Confirmed by Nick |
| D5 | Rename of the `UsernameRecoveryLookup*` types | Out of scope for this workstream and larger than it looks. Deferred, see Appendix B. | Confirmed by Nick |

### Verified gate inputs (scripts 050 and 051)

Runtime wrapper
- The wrapper scripts `ngtpa-runtime-start`, `ngtpa-runtime-stop` and `ngtpa-runtime-status` exist in `~/.local/bin`, outside the repository. Each takes a `<worktree-id>`. They source `~/.config/ngtpa/runtime/common.sh` (not yet read when this appendix was written) and keep state under `~/.local/state/ngtpa`.
- Start preconditions, from its source: the worktree is clean including untracked files; `.env` exists with mode 600; `.local/redis-tls/ca.crt`, `tls.crt` and `tls.key` exist and `tls.key` is mode 600; every host:port in `REDIS_SENTINEL_NODES` accepts a TCP connection; the application port is free.
- Start action: `mvn -B -ntp -DskipTests -Dstyle.color=never -Dspring-boot.run.arguments=--spring.output.ansi.enabled=never spring-boot:run` in its own process group, with `SPRING_PROFILES_ACTIVE` defaulting to `local`, the `.env` assignments exported, and output written to `application.log` under `target/evidence/<timestamp>-runtime-<id>/`.
- Health: polls `http://127.0.0.1:${SERVER_PORT:-8888}/actuator/health` up to 60 times (3 s request timeout, 2 s sleep) and succeeds only on HTTP 200 with `"status":"UP"`. On failure it terminates the process group and writes `Status: FAIL`.
- Stop: SIGTERM to the recorded process group, waits up to 30 s, never sends SIGKILL and fails if the group is still alive.

Port and endpoints
- `server.port` is `${SERVER_PORT:8888}`, `.env` sets 8888 and no `management.server.port` is configured, so the actuator is served on the application port. The 8090 in `docs/wsl/phase-04-direct-spring-runtime.md` does not match this.

Startup dependencies
- `AuthFlowSessionPersistenceCompatibilityStartupGate` (an `ApplicationRunner` registered by `RedisAdapterConfig`) blocks on Redis for `redis-cache.timeout-milliseconds` and throws `IllegalStateException` when the auth-flow schema is incompatible or unavailable. An unreachable Redis is therefore a startup failure, not a degraded start.
- `ApimAppCertificateService.init()` (`@PostConstruct`) builds the APIM application certificate only when APIM encryption is enabled and both PEM values are present. `WorkflowErrorContractConfig` validates the workflow error contract through a `SmartInitializingSingleton`.
- `application-local.yml` points the `redis-mtls` bundle at `.local/redis-tls/*`; all three files exist and `tls.key` is mode 600. `application.yml` points at `/etc/redis-tls/*`, which do not exist in WSL, and the `local` profile is expected to override them.
- At the time of script 051 the Sentinel node from `.env` resolved in DNS but did not accept a TCP connection (0 of 1), and nothing answered on 127.0.0.1 ports 6379 and 26379. `docs/wsl/local-wsl-development-workstream.md` assigns Redis port forwarding to the Windows side, so the forward must be running before the gate.
- `APIM_CERTIFICATE_PATH` and `SMS_SEND_SINGLE_PATH` are not local files. Their consumers are the `certificate-path` settings under `apim` (passed to `replacePath(...)` in `ApimCertificateService`) and `sms.send-single-path`, so they are treated as request paths and not as startup file dependencies. This is inferred from the consumers and is to be confirmed by the startup run.
- No placeholder without a default is unresolved (script 051: missing=0, empty=0).
- The Config Service base URL answered HTTP 401 (reachable, credentials not tested). Whether startup needs it is not verified.
- The earlier `startup.log` (2026-09-29) shows a successful start ("Started NgtpaApiServerApplication in 8.697 seconds", Netty on 8888, one actuator endpoint exposed). It predates this branch's HEAD and is not gate evidence.

Test setup
- The repository has 1141 `*Test.java` files and no `*IT.java` or `*Tests.java`. `target/surefire-reports` holds 2278 files from earlier runs, the newest from 2026-10-05 10:37, so the gate must not read that directory without establishing freshness.

### Open items

1. Registration's `TrpSearchMemberApimDataItem` (package `registration.dto`) has no `sch-name` or `chin-sch-name`, by design (solution design section 8.2). Whether it tolerates the new response fields (unknown-property handling) is not yet verified. Script 052 collects that DTO and the Jackson configuration as evidence; the result is reviewed before this item is closed.
2. The id under which this worktree is registered with the runtime wrapper is unknown, because `common.sh` had not been read. Script 052 collects it as evidence.
3. Whether application startup depends on the Config Service is not verified.
4. `docs/wsl/phase-04-direct-spring-runtime.md` still describes the wrapper in the future tense and names port 8090. It is outside the Template Service docs and is not edited here.

### Gate A evidence, test count reconciliation and coverage closure (scripts 053-056)

Recorded 2026-10-05 from the evidence of scripts 053, 054 and 055. Statements are limited to what that evidence shows.

Gate A, first run (HEAD 8c785a2c; `src/` and `pom.xml` identical to b9012557):

- Full `mvn test`: BUILD SUCCESS, exit code 0, 07:08 min. Maven reported 5516 tests run, 10 skipped, 0 failures, 0 errors. The 1140 surefire XML reports show failures=0 and errors=0; the 10 skipped tests are in `AuthenticatedLoginCommittedReplaySubtask006001a6005FailFirstTest` (4), `AuthenticatedLoginHandoffRecoverySubtask006001a6FailFirstTest` (4), `RegistrationOtpResendLifecycleOwnershipTest` (1) and `P3SecurityFoundationIntegrationTest` (1).
- Workstream classes: `YamlNoticeTemplate*Test`, `NoticeTemplate*Test`, `ApplicationYamlNoticeTemplatesBindingTest` and `CleanArchitectureDependencyTest` (23 tests) ran with 0 failures, as did the 97 `UsernameRecovery*` classes (496 tests), 9 `TrpSearchMember*` classes and 5 `*SchemeName*` classes.
- Count difference: the XML `tests` attributes sum to 5506, but the XML holds 5516 testcase elements, equal to Maven's total. The 10 come from six ArchUnit suites under `architecture/` whose `tests` attribute is lower than their testcase count: `EmailSenderAdapterArchitectureGuardTest` (2 vs 4), `MessageDeliveryApplicationBoundaryArchitectureTest` (1 vs 2), `MessageDeliveryArchitectureGuardTest` (3 vs 6), `MessageDeliveryServiceArchitectureGuardTest` (1 vs 2), `SenderApplicationBoundaryArchitectureTest` (1 vs 2) and `SmsSenderAdapterArchitectureGuardTest` (2 vs 4). In each, every test name appears twice, once under the fully qualified class name and once under the simple class name, and no case failed. Why these suites report this way is not established; it is outside this workstream.
- Three test files are named differently from the class they declare: `AuthForgotPasswordControllerTest.java` declares `ForgotPasswordControllerTest` (3 tests), `ContributionSummaryResponseTest.java` declares `ContributionListResponseTest` (1) and `ReactorPortalAccessContextResolverTest.java` declares `ReactorCurrentPortalAccessContextResolverTest` (5). Each has a report under its declared class name with 0 failures, so none was skipped. Renaming them is outside this workstream.
- Gate B was NOT RUN: the preflight found no Redis Sentinel node accepting a TCP connection (0 of 1) and nothing was started. The other preflight checks passed (wrapper scripts, registry mapping of `apim-profiles`, `.env` mode 600, Redis TLS files, port 8888 free, no application process).

Coverage of the lines added since the baseline 632dd215 (script 054, JaCoCo line data): 99.61% instruction (517 of 519) and 94.00% branch (47 of 50) over 16 main files. The whole-class figures printed by script 053 include older code and are not the measure here. Three branches were not covered:

- `NoticeTemplateDefinition` line 36 (`locale == null`) and line 39 (`body == null`). It is a record, so the 95% branch tier applies; it stood at 8 of 10 branches.
- `YamlNoticeTemplateRegistryAdapter` line 116 (`template == null` in `placeholders`); the file is at 27 of 28 branches (96.4%).

Closure (script 056):

- Two tests were added to `NoticeTemplateDefinitionTest`: `rejectsNullLocale` and `rejectsNullBody`. The script committed them only after a scoped test run passed and JaCoCo showed no missed branch in `NoticeTemplateDefinition`. The scoped run used wildcard patterns: `NoticeTemplate*Test`, `YamlNoticeTemplate*Test`, `ApplicationYamlNoticeTemplatesBindingTest` and `CleanArchitectureDependencyTest`.
- Justification for the remaining adapter branch (adapter tier, 85-90% branch): defensive, not reachable from the Spring-wired registry. `NoticeTemplateProperties.validate()` runs at startup (plan stage I-4) and rejects a null or blank SMS body and a null or blank email subject or body, so `placeholders(null)` cannot occur there. It is reachable only when the registry is built from properties that skipped `validate()`.

Because the closure changes `src/test`, the Gate A evidence above no longer matches the final code. Gate A and Gate B are rerun once on the final code commit, with the source pin of the gate script moved to that commit.

Update to Open items (Appendix A.2): item 1 is resolved. The Registration `TrpSearchMemberApimDataItem` and `ApimResponseEnvelope` both carry `@JsonIgnoreProperties(ignoreUnknown = true)` (script 052 evidence), so the new `sch-name` and `chin-sch-name` response fields do not break Registration. Item 2 is resolved: the worktree id for the runtime wrapper is `apim-profiles`, mapped in `~/.config/ngtpa/copilot-launcher/worktrees.tsv`. Items 3 and 4 remain open.

## Appendix B: Deferred Refactors (outside the Template Service workstream)

### B-1 Rename or restructure the `UsernameRecoveryLookup*` types

Status: DEFERRED. Decision by Nick, 2026-10-05: out of scope for this workstream and larger than it looks. Nothing in this appendix has been started.

Why the names confuse:
- `UsernameRecoveryLookupCandidate` (application DTO) and `UsernameRecoveryCandidate` (domain model) differ by one word, and neither name says that the data comes from the APIM `TRPSearchMember` operation.
- `TrpSearchMemberAdapter` (`adapter/out/apim/usernamerecovery`) implements `UsernameRecoveryLookupPort` (`application/port/out`). The port is named for what the workflow needs and the adapter for the APIM operation, so nothing in either name links them. The arrangement itself is ports and adapters: the application layer owns the port and exposes only application contracts (`UsernameRecoveryLookupCommand`, `UsernameRecoveryLookupResult`), the adapter owns APIM transport, and `CleanArchitectureDependencyTest` forbids the application layer from depending on adapter packages.

Sizing (script 050 evidence):
- 4 main files are named `UsernameRecoveryLookup*` (Candidate, Command, Result, Port) and 8 test files are named for them.
- 30 Java and Markdown files mention the name. Identifier counts: `UsernameRecoveryLookupResult` 101, `UsernameRecoveryLookupCandidate` 43, `UsernameRecoveryLookupCommand` 40, `UsernameRecoveryLookupPort` 29.
- Main-code users include `TrpSearchMemberAdapter`, `TrpSearchMemberApimMapper`, `TrpSearchMemberApimResultMapper`, `UsernameRecoveryStartProtectedOperation` and `UsernameRecoveryFeatureConfig`. Documents that mention it: `username-recovery-apim-integration-design.md`, `username-recovery-test-traceability-design.md` and this plan.
- Tests that reference the names and must change with any rename include `UsernameRecoveryLookupOutboundPortArchitectureGuardTest`, `UsernameRecoveryLookupPortReconciliationTest` and `UsernameRecoveryLookupBoundaryReconciliationTest`.

Candidate names discussed for the Candidate type, none chosen: `UsernameRecoverySearchMemberCandidate`, `UsernameRecoveryTrpSearchMemberCandidate`, `UsernameRecoveryMemberMatch`. Any rename has to be decided for the whole family (Candidate, Command, Result, Port), not for one class.

Before it is started: its own branch, its own investigation and plan under `docs/design/6-username-recovery`, and a pure-rename commit kept separate from behaviour changes.

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
| II-1 | Username Recovery | Simplest — single call site in `UsernameRecoveryFeatureConfig` / `UsernameRecoveryRequestProtectedOperation`. First to confirm whether `schemeName`/`schemeNameChi` already flow from the DTO through `TrpSearchMemberApimResultMapper` → `UsernameRecoveryLookupCandidate`, or need additive plumbing. | DONE (gate evidence: Appendix A.3) |
| II-2 | Registration | Replace `RegistrationMessageDeliveryAdapter`'s `subject()`/`body()` policy lookup with `NoticeTemplateRegistryPort.resolve(...)`. No scheme-name placeholder needed (§8.2). | IN PROGRESS (investigation done; draft sub-plan: Appendix C) |
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
Recovery) is DONE: sub-stages II-1.a to II-1.d are committed (Appendix A.1) and the stage gate is
met on the final code, with a passing full `mvn test` and an application startup smoke test with
no bean or configuration errors, both evidenced (Appendix A.3). Stage II-2 (Registration) is IN
PROGRESS: its read-only investigation (scripts 059, 060 and 062) is recorded in Appendix C, where
sub-stage II-2.a is done, and no code has been changed for it. Decision D6 (how the `{ttlUnit}`
placeholder is supplied) and decision D7 are OPEN. The next steps are the confirmation of D6 and
D7 and the design update (II-2.0), then the implementation (II-2.b). Stages II-3 (Login), II-4
(Forgot Password) and Phase III remain PENDING. Decisions and open items for II-1 are in Appendix
A.2. Work that is deliberately outside this workstream is listed in Appendix B.

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

**Known behaviour (decided as D1 in Appendix A.2).** EMAIL templates for `username-recovery` require
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

**Stage gate (satisfied on 2026-10-05; evidence in Appendix A.3).** The standing convention requires,
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

## Appendix A.3: Stage II-1 Gate Result (script 057)

Recorded 2026-10-05 from the evidence of script 057, which was run twice. Statements are limited to
what that evidence shows.

Pin, identical in both runs: HEAD 43209e2f, `src/` and `pom.xml` identical to the final code commit
211f3702, working tree clean. 211f3702 differs from the earlier code commit b9012557 only by the two
tests added to `NoticeTemplateDefinitionTest`, which is why the full-test total is 5518 and not 5516.

Run 1 (evidence 20261005-151158): Gate A FAIL, Gate B PASS.

- `mvn test` exited with code 1 after 07:35 min: 5518 tests, 7 failures, 1 error, 10 skipped. All eight
  are ArchUnit tests under `architecture/`: `CaptchaFoundationArchitectureGuardTest`,
  `ForgotPasswordCancelArchitectureGuardTest`, `MessageTemplateRendererArchitectureGuardTest`,
  `RegistrationCancelUseCaseArchitectureGuardTest`, `UsernameRecoveryFeatureWebArchitectureGuardTest`,
  `UsernameRecoveryStartReplayArchitectureGuardTest` (two tests) and
  `Task0043SharedAuthFlowContractArchitectureTest` (the error).
- The 22 workstream key classes, including every `NoticeTemplate*`, `YamlNoticeTemplate*` and
  `UsernameRecovery*` class named in the gate, ran with at least their floor counts and 0 failures.
- Three of the failure messages say the rule "failed to check any classes", and the error is a
  `NoClassDefFoundError` for `AuthFlowIdempotencyPort`. That is consistent with class files being
  missing from `target/classes` while the run executed. The cause was not established, and whether
  another process touched this worktree or `target/` during the run was not confirmed.

Run 2 (evidence 20261005-152704): Gate A PASS, Gate B PASS. No file under `src/` or `pom.xml` changed
between the two runs.

- Gate A: `mvn test` BUILD SUCCESS, exit code 0, 07:18 min, 5518 tests, 0 failures, 0 errors, 10
  skipped (the same four classes as before). 1140 fresh reports (oldest 15:20:53, newest source
  change 14:45:24). `NoticeTemplateDefinitionTest` ran 12 tests and JaCoCo shows 0 missed branches
  in `NoticeTemplateDefinition`. The Maven console total equals the number of testcase elements.
- Gate B: started through `ngtpa-runtime-start apim-profiles` (profile local) in 11.555 s. The health
  poll was refused until attempt 41, returned HTTP 503 once, then HTTP 200 and `"status":"UP"`.
  Readiness returned HTTP 200 and UP. After a 20 s wait the process group was alive, health was
  HTTP 200 and the log had 0 failure markers (no bean, binding, configuration or
  schema-incompatibility error). `ngtpa-runtime-stop` stopped it with SIGTERM: the process group was
  gone, port 8888 was released and the log shows one graceful-shutdown line.
- The single ERROR line in the log, in both runs, is the request log of the 503 that the wrapper's own
  health poll received on `/actuator/health` just before the first 200. There was also one WARN line
  per run; its content was counted and not examined. The log has no `NoticeTemplate` lines, so the
  registry's startup is not visible there individually; the evidence is a successful start with no
  bean or configuration failure.

Run 1 is kept as a record of intermittent behaviour. The gate evidence is run 2, taken on the same code
after run 1. Rule for future full-test runs: no other build, IDE auto-build or agent session on the
same worktree while it runs.

Whole-class coverage printed by the gate (informational; it includes older code): below the tier
thresholds are `UsernameRecoveryCandidate` (branch 90.9%), `UsernameRecoverySessionCandidate` (branch
91.7%), `TrpGetAccountListApimDataItem` (branch 50.0%),
`AesGcmUsernameRecoveryAuthFlowContextProtectorAdapter` (instruction 91.4%, branch 61.1%) and
`UsernameRecoveryRequestProtectedOperation` (instruction 92.1%, branch 69.4%). The measure for this
workstream is the added lines (script 054), where the only uncovered branches were the two in
`NoticeTemplateDefinition`, now covered, and the justified adapter branch (see the section on script
056).

Result: Stage II-1 is DONE. Open items 3 (whether startup depends on the Config Service) and 4 (the
Phase 04 document) of Appendix A.2 remain open. The next stage is II-2 (Registration).

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

## Appendix C: Stage II-2 (Registration) Investigation and Draft Sub-Plan

Status: DRAFT. No code has been changed for Stage II-2. Recorded 2026-10-05 from the evidence of
scripts 059 and 060 (read-only; HEAD 78474055; `src/` and `pom.xml` identical to 211f3702).
Statements are limited to what that evidence shows; anything not shown is listed under Open items.

### C.1 Current send path

- `RegistrationMessageDeliveryAdapter` (`adapter/out/registration`, 104 lines, implements
  `RegistrationMessagePort`) is the only class that constructs a command with
  `MessageDeliveryType.REGISTRATION_OTP`. It reads the OTP policy through
  `AuthWorkflowPolicyPort.registrationPolicy()` for `smsTemplate` and `email.subject` /
  `email.body`, and falls back silently to the subject `registration-otp` and the body `{code}`
  when a value is blank or absent. MOBILE maps to SMS and every other contact type to EMAIL. The
  template variables are `code`, `ttlValue` (`max(1, ttlSeconds / 60)`) and `ttlUnit`, which is
  the literal `minutes`. The command carries scenario `REGISTRATION`, template code
  `registration-otp` and the raw `acceptLanguage` as its locale.
- The adapter is wrapped by `RegistrationMessageDestinationOverrideAdapter`, which substitutes the
  recipient outside production-like deployments. `RegistrationStartGatewayConfig.registrationMessageGateway`
  builds both and passes `AuthWorkflowPolicyPort` to the delivery adapter. The bean is injected
  into START (`RegistrationStartProtectedOperation`, line 218) and RESEND
  (`RegistrationOtpResendProtectedOperation`, line 57), which call
  `deliverRegistrationOtp(contactType, recipient, otpCode, ttlSeconds, acceptLanguage, correlationId)`.
- `AuthWorkflowPolicyPort` is mentioned in 17 main files. Removing it from the delivery adapter
  changes that constructor and the bean method; the other users (OTP challenge adapter, captcha,
  username and password validation, START) keep their own references.
- START and RESEND take the language from the current request. The Registration protected session
  context (`RegistrationAuthFlowContext`) holds identity type and value, date of birth, contact
  type and contact value only, so no language is stored and no serializer change is needed. This
  differs from Stage II-1.
- Delivery layer: `MessageDeliveryAdapter` (`application/usecase/message`) rejects a command with
  a blank body or, for EMAIL, a blank subject; an SMS subject is not checked. Rendering runs inside
  its guarded block, so a missing template variable (`IllegalArgumentException` from
  `DefaultMessageTemplateRenderer`) produces a FAILED result. The Registration adapter reports that
  as `false`, and both callers treat `false` as a failed delivery (RESEND reports
  `MESSAGE_DELIVERY_FAILED`).

### C.2 Content

`notice-templates.definitions.registration-otp` exists in `application.yml` (lines 586-612) with
en and zh_HK SMS and EMAIL content that uses `{code}`, `{ttlValue}` and `{ttlUnit}`. Its wording
differs from the old `auth-workflows.registration.otp` templates (lines 271-274); for example the
SMS now opens with "BCT Pension: Your One-Time Password (OTP) for Account Registration". This is
the BRD-sourced content of stage I-5, so the migration changes the wording that Registration
customers receive.

### C.3 Finding: the `{ttlUnit}` placeholder is supplied three different ways

| Workflow | `ttlValue` | `ttlUnit` | Where |
| --- | --- | --- | --- |
| Registration | `max(1, ttlSeconds / 60)` | literal `minutes` | `RegistrationMessageDeliveryAdapter`, lines 46-47 |
| Login (send and resend) | the policy value in seconds | literal `seconds` | `LoginOtpSendService`, line 157; `LoginOtpResendService`, lines 121-124 |
| Forgot Password, details OTP | literal `5` | `ttlUnit(language)` | `ForgotPasswordDetailsOtpProtectedOperation`, lines 274 and 305 |
| Forgot Password, resend | `max(1, ttlSeconds / 60)` | `ttlUnit(language)` | `ForgotPasswordOtpResendProtectedOperation`, lines 537-540 and 545 |

The two `ttlUnit(language)` helpers are identical: they return `分鐘` when the language equals
`zh_HK` after replacing `-` with `_` (ignoring case), and `minutes` otherwise. The registry content
writes the two placeholders next to each other in zh_HK without a space (for example
`有效期為{ttlValue}{ttlUnit}。` in the SMS and `此密碼於{ttlValue}{ttlUnit}內有效。` in the email) and
with a space in en. Consequences:

1. Registration migrated as it is would render zh_HK content with the English unit, for example
   `5minutes` inside Chinese text.
2. A unit derived from the request tag with the existing helper matches only `zh_HK` exactly, while
   the registry selects zh_HK content for `zh`, `zh-TW` and `zh-Hant` as well (design section 8.4).
   The unit must follow the locale the registry selected (`NoticeTemplateDefinition.locale()`), not
   the request tag.
3. Login supplies seconds through the same placeholder, while the BRD wording is 5 minutes
   (design section 8.3). That belongs to II-3 and is carried there.
4. Phase III stage III-3 deletes the two Forgot Password helpers without saying where the unit then
   comes from.

### C.4 Decisions

| # | Topic | Options | Status |
| --- | --- | --- | --- |
| D6 | How the unit word reaches the template | A: configure the unit word per locale in the `notice-templates` YAML and return it with the resolved definition, so a caller passes only the number; every workflow and locale is then YAML only. B: a small helper inside the Registration adapter that picks the unit from `definition.locale()`; smallest change, but a third copy of the logic and a locale-to-word map in code. C: write `分鐘` literally into the zh_HK content and drop `{ttlUnit}` there; YAML only, but it breaks the confirmed rule to keep dynamic `{ttlValue}` / `{ttlUnit}` wording. | OPEN. Option A is the assistant's proposal and is not confirmed by Nick. A touches Phase I classes (`NoticeTemplateDefinition`, `NoticeTemplateProperties`, the registry adapter) and their tests, and needs a design update first. |
| D7 | Failure when the definition is missing | Resolve inside `deliverRegistrationOtp` as the policy lookup is resolved today, so a missing definition throws `IllegalStateException` at the call, as a missing policy does now. | Proposed, not yet confirmed. |

### C.5 Behaviour changes to record

- The wording of the Registration SMS and EMAIL changes to the BRD content (C.2).
- The silent fallback to the subject `registration-otp` and the body `{code}` disappears; a missing
  definition fails (D7).
- The locale passed to the registry is the raw `acceptLanguage`; a null or blank value selects en
  (design section 8.4), and the front end sends a single tag (D3).
- The old `auth-workflows.registration.otp.sms-template` and `email-template` stay until stage
  III-1, and `AuthWorkflowProperties` still validates their placeholders (line 497).

### C.6 Test and coverage baseline

- `RegistrationMessageDeliveryAdapterTest` (249 lines, 11 tests) builds the adapter with a mocked
  `AuthWorkflowPolicyPort`. Its two constructor guards, two policy-null guards and three
  subject/body fallback tests describe behaviour that this migration removes, so it is rewritten,
  not patched.
- Coverage of the adapter in the last full test run (JaCoCo of script 060): 163 of 163 instructions
  and 25 of 28 branches. The three missed branches are in the subject, body and SMS-template
  fallback conditions (lines 85, 96 and 99), which the migration removes. The migrated adapter is in
  the adapter tier: at least 95% instruction and 85-90% branch, with a one-line justification for
  each uncovered branch.

### C.7 Draft sub-plan

| Sub-stage | Change | Status |
| --- | --- | --- |
| II-2.0 | Confirm D6 and D7. Update the solution design (a new section for the unit) and this appendix before any code. | PENDING (needs Nick's decision) |
| II-2.a | Read-only investigation of the three tests that name the gateway config (`RegistrationMessageDestinationOverrideWiringTest`, `RegistrationHardcodedPolicyArchitectureGuardTest`, `RegistrationSharedCoreMigrationArchitectureTest`) and of the tests that build the adapter or mention `RegistrationMessagePort` (17 files in main and test). | DONE (script 062; findings in C.9) |
| II-2.b | `RegistrationMessageDeliveryAdapter` resolves content through `NoticeTemplateRegistryPort.resolve(REGISTRATION_OTP, channel, acceptLanguage)`, builds `code`, `ttlValue` and `ttlUnit` as decided in D6, and no longer takes `AuthWorkflowPolicyPort`. `RegistrationStartGatewayConfig.registrationMessageGateway` injects the registry port. Rewrite `RegistrationMessageDeliveryAdapterTest`, and adjust whichever test from II-2.a pins the old wiring. | PENDING |
| II-2.c | A test with the real `YamlNoticeTemplateRegistryAdapter` and `DefaultMessageTemplateRenderer` that renders START and RESEND content for SMS and EMAIL in en and zh_HK and asserts that no placeholder is left unreplaced and that the unit follows the selected locale. | PENDING |
| II-2.d | Stage gate on the final code: full `mvn test` and an application startup smoke test, then the plan is updated. | PENDING |

### C.8 Open items

1. D6 and D7 are not confirmed.
2. The contents of the three tests in II-2.a have not been read.
3. Which tests, besides `RegistrationMessageDeliveryAdapterTest`, construct the adapter is not
   established.
4. Login passes seconds through `{ttlUnit}` (II-3). The Forgot Password helpers match only `zh_HK`
   exactly and the details OTP passes a literal `5` (II-4 and III-3).

### C.9 Sub-stage II-2.a findings (script 062)

Recorded 2026-10-05 from the evidence of script 062 (read-only; HEAD 55caa2b0; `src/` and `pom.xml`
identical to 211f3702). It closes open items 2 and 3 of C.8 for the tests named there and records
the constraints that sub-stage II-2.b must respect. Decisions D6 and D7 are unchanged and still
open. A wording defect is also corrected: the Current Position text written with C.7 described
II-2.b as a read-only investigation, but II-2.b is the implementation sub-stage.

#### C.9.1 The three tests named in C.7

| Test | What it checks | Effect of II-2.b |
| --- | --- | --- |
| `RegistrationMessageDestinationOverrideWiringTest` (97 lines, 4 tests) | Calls `RegistrationStartGatewayConfig.registrationMessageGateway(mock(MessageDeliveryPort.class), mock(AuthWorkflowPolicyPort.class), DeploymentEnvironmentPolicy, AuthWorkflowProperties)` at six places (lines 21, 33, 47, 56, 68 and 75). It asserts that the result is a `RegistrationMessageDestinationOverrideAdapter` and that bean creation fails for missing override destinations. | Any change to the parameter list of `registrationMessageGateway` (the registry port is added, and `AuthWorkflowPolicyPort` leaves the delivery adapter) requires all six calls to change. The decorator and destination assertions do not depend on the adapter content. |
| `RegistrationHardcodedPolicyArchitectureGuardTest` (72 lines, 4 tests) | Reads source files as text. Its gateway test (lines 47-58) reads `RegistrationStartGatewayConfig.java` and asserts that it contains `AuthFlowSessionProperties` and does not contain two `@Value` fallbacks. It does not name the adapter or the policy port. | None, as long as those two conditions stay true. |
| `RegistrationSharedCoreMigrationArchitectureTest` (45 lines, 3 tests) | Its third test (lines 33-44) reads `RegistrationStartGatewayConfig.java` as text and asserts that it contains the exact strings `OtpChallengePort otpChallengePort` and `MessageDeliveryPort messageDeliveryPort` and does not contain `AuthFlowOtpPort otpChallengePort` or `AuthFlowMessagePort messageDeliveryPort`. | The two parameter declarations must stay exactly as they are in the gateway config. |

#### C.9.2 Tests that construct or fake the Registration message port

- `RegistrationMessageDeliveryAdapterTest` is the only test that constructs the delivery adapter
  (lines 30, 37 and 43).
- Nine test files mention `RegistrationMessagePort`. Eight of them implement a fake of the port or
  mock it: `RegistrationStartWorkflowIntegrationTest` (line 900),
  `RegistrationOtpResendServiceIntegrationTest` (239), `RegistrationStartMandatoryCompositionTest`
  (a mock at line 43), `RegistrationDeliveryEnvironmentIntegrationTest` (183),
  `RegistrationMessageDestinationOverrideAdapterTest` (177), `ResendRegistrationOtpUseCaseTest`
  (193), `StartRegistrationUseCaseTest` (336) and `StartRegistrationCaptchaGateTest` (481). The
  ninth, `SharedResendRegistrationOtpServiceTest`, defines `deliverRegistrationOtp` at line 137; the
  pattern used by the script did not classify how.
- None of the nine constructs the delivery adapter and the port signature does not change in II-2.b,
  so they are not expected to change.

#### C.9.3 Tests and values that pin the old configuration

- `RegistrationMessageDeliveryAdapterTest` pins the fallback subject `registration-otp` and body
  `{code}` (line 227), the SMS template used as the body (line 234) and the clamp of `ttlValue` to
  1 (line 177). It is rewritten in II-2.b (C.6).
- `AuthWorkflowPropertiesBindingTest` (lines 66-72) pins the old registration `sms-template` and
  `email-template` values as bound from configuration. That is the old configuration, which stays
  until III-1, so this test changes in III-1 and not in II-2.
- No test asserts the `ttlUnit` value for Registration: among the test lines that mention
  `ttlValue` or `ttlUnit` and whose path or text mentions registration, only line 177 above appears.
- `RegistrationOtpChallengeLifecycleIntegrationTest` (lines 155-166) builds `OtpPolicy` and
  `OtpEmailPolicy` for the OTP challenge adapter. `OtpPolicy`, `OtpEmailPolicy` and their binding
  stay until III-1.

#### C.9.4 Registry content and consumers (input for D6; no decision made)

- The `notice-templates` block is lines 584-708 of `application.yml`.
- All three OTP types (registration, login and forgot password) write the unit next to the number:
  with a space in en (SMS lines 588, 615 and 649; EMAIL bodies 599, 625 and 659) and without one in
  zh_HK (SMS lines 589, 616 and 650; EMAIL bodies 609, 639 and 669). Whatever supplies the zh_HK
  unit therefore has to serve all three workflows, not only Registration.
- `NoticeTemplateRegistryFullMatrixSmokeTest` asserts the required placeholders of `registration-otp`
  (SMS and EMAIL, en) as exactly `code`, `ttlValue` and `ttlUnit`, and those of login and forgot
  password as the sets in design section 8.2. A design that keeps `{ttlUnit}` in the content keeps
  those assertions valid. The same test asserts that `definition.locale()` equals the requested
  locale for `en` and `zh_HK`.
- The only main-code constructions of `NoticeTemplateDefinition` are the two in
  `YamlNoticeTemplateRegistryAdapter` (lines 50 and 63). No main code in the Username Recovery
  package or the notice adapter package reads `requiredPlaceholders()`; Stage II-1 uses only
  `subject()` and `body()`. The number of test constructions of `NoticeTemplateDefinition` and of
  `NoticeTemplateProperties.NoticeDefinition` is not yet established; script 063 collects it.

#### C.9.5 Old validation and policy mapping (context for III-1)

- `AuthWorkflowProperties` checks the login SMS template for `{code}` (lines 340-345) and has a
  `requireTemplate` helper that demands `{code}`, `{ttlValue}` and `{ttlUnit}` (lines 493-502); its
  Forgot Password calls are commented out (lines 484-490). Whether `requireTemplate` is still called
  for Registration was not read; script 063 collects the call sites.
- `ConfigBackedAuthWorkflowPolicyAdapter` maps the registration `sms-template` and the email subject
  and body into `OtpPolicy` (lines 48-51).

#### C.9.6 Updated open items

1. D6 and D7 are not confirmed.
2. Closed by script 062: the contents of the three tests (C.9.1) and the tests that construct the
   adapter or fake the port (C.9.2).
3. Not yet established: the number of test sites that construct `NoticeTemplateDefinition` or
   `NoticeTemplateProperties.NoticeDefinition`; the Forgot Password and Login tests that pin
   `ttlUnit`; whether `requireTemplate` is called for Registration. Script 063 collects all three.

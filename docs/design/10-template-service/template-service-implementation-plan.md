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
| II-2 | Registration | Replace `RegistrationMessageDeliveryAdapter`'s `subject()`/`body()` policy lookup with `NoticeTemplateRegistryPort.resolve(...)`. No scheme-name placeholder needed (§8.2). | DONE (gate evidence: Appendix C.17) |
| II-3 | Login | Replaced `LoginOtpSendPolicy`-based lookup with `NoticeTemplateRegistryPort.resolve(...)` in `LoginOtpSendService`/`LoginOtpResendService`. Scheme-name plumbing comes from `TrpMemberLoginApimDataItem` (D8, corrected model; see D.11.1), not `TrpGetAccountListApimDataItem`. | DONE (gate evidence: Appendix D.13) |
| II-4 | Forgot Password | **Last** — highest risk, since it currently has no live YAML wiring at all. Reconcile content to match `login-otp` verbatim (§5). Scheme-name plumbing from `ForgotPasswordTrpSearchMemberDataItem` already exists; the gap is downstream (see Appendix E.3). | DONE (see Appendix E.11) |

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

Phase I (stages I-1 through I-6) is complete and closed. Phase II, Stage II-1 (Username Recovery) is
DONE: sub-stages II-1.a to II-1.d are committed (Appendix A.1) and the stage gate is met on the
final code, with a passing full `mvn test` and an application startup smoke test with no bean or
configuration errors, both evidenced (Appendix A.3). Stage II-2 (Registration) is DONE: decisions D6
(option A) and D7 are confirmed, the design is solution design section 8.6, and all of its
sub-stages (II-2.0, II-2.a, II-2.a2, II-2.b0, II-2.b, II-2.c, II-2.e and II-2.d) are DONE. The code
is committed as be7294d5 (Template Service extension), ce2176e7 (Registration adapter migration),
14c71b24 (a rendering test with the real registry and renderer), 951049f8 (START with the captcha
gate off, and RESEND, tests for a missing definition) and da136a8f (START with the captcha gate on);
each was validated with compile, test-compile and a scoped run with 0 failures (C.12 to C.14, C.16
and C.17). The stage gate is met on the final II-2 code commit da136a8f: a passing full `mvn test`
(5564 tests, 0 failures, 0 errors, 10 skipped) and an application startup smoke test with no bean or
configuration errors, both evidenced (C.17). Registration now takes its content and its unit word
from the registry. Stage II-3 (Login) is DONE: decisions D8 to D11 are resolved (Appendix D.11) and
all of its sub-stages (II-3.0, II-3.a, II-3.a2, II-3.b0, II-3.b, II-3.c, II-3.d and II-3.e) are
DONE. The stage gate is met on the final code commit 5eafe73d: a passing full `mvn test` and an
application startup smoke test with no bean or configuration errors, both evidenced (Appendix D.13).
Stage II-4 (Forgot Password) is DONE (Appendix E.11, closed 2026-10-09). Phase III is tracked in its own table, which is the authority for the status of its stages. Decisions and open items for II-1 are in
Appendix A.2. Work that is deliberately outside this workstream is listed in Appendix B.

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
| D6 | How the unit word reaches the template | A: configure the unit word per locale in the `notice-templates` YAML and return it with the resolved definition, so a caller passes only the number; every workflow and locale is then YAML only. B: a small helper inside the Registration adapter that picks the unit from `definition.locale()`; smallest change, but a third copy of the logic and a locale-to-word map in code. C: write `分鐘` literally into the zh_HK content and drop `{ttlUnit}` there; YAML only, but it breaks the confirmed rule to keep dynamic `{ttlValue}` / `{ttlUnit}` wording. | Confirmed by Nick, 2026-10-05 (option A; design: solution design section 8.6; C.11). |
| D7 | Failure when the definition is missing | Resolve inside `deliverRegistrationOtp` as the policy lookup is resolved today, so a missing definition throws `IllegalStateException` at the call, as a missing policy does now. | Confirmed by Nick, 2026-10-05 (C.11). |

### C.5 Behaviour changes to record

- The wording of the Registration SMS and EMAIL changes to the BRD content (C.2).
- The silent fallback to the subject `registration-otp` and the body `{code}` disappears; a missing
  definition fails (D7).
- The locale passed to the registry is the raw `acceptLanguage`; a null or blank value selects en
  (design section 8.4), and the front end sends a single tag (D3).
- The old `auth-workflows.registration.otp.sms-template` and `email-template` stay until stage
  III-1. (Corrected in C.10.1: `AuthWorkflowProperties` does not validate them.)

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
| II-2.0 | Confirm D6 and D7. Update the solution design (a new section for the unit) and this appendix before any code. | DONE (D6 option A and D7 confirmed; design: solution design section 8.6; script 065) |
| II-2.a | Read-only investigation of the three tests that name the gateway config (`RegistrationMessageDestinationOverrideWiringTest`, `RegistrationHardcodedPolicyArchitectureGuardTest`, `RegistrationSharedCoreMigrationArchitectureTest`) and of the tests that build the adapter or mention `RegistrationMessagePort` (17 files in main and test). | DONE (script 062; findings in C.9) |
| II-2.b0 | Template Service extension (an amendment to Phase I; design: solution design section 8.6). `NoticeTemplateDefinition` gains a nullable last component `ttlUnit` and keeps a public six-argument constructor; `NoticeTemplateProperties` gains `ttlUnits` and validates it; `YamlNoticeTemplateRegistryAdapter` attaches the unit that belongs to the locale key it selected; `application.yml` gains `notice-templates.ttl-units` (en `minutes`, zh_HK `分鐘`) in the SAME commit as the validation, because the validation would otherwise fail the application at startup. Tests per section 8.6. II-2.b depends on it. | DONE (commit be7294d5; script 066; result in C.12) |
| II-2.b | `RegistrationMessageDeliveryAdapter` resolves content through `NoticeTemplateRegistryPort.resolve(REGISTRATION_OTP, channel, acceptLanguage)`, builds `code`, `ttlValue` and `ttlUnit` as decided in D6, and no longer takes `AuthWorkflowPolicyPort`. `RegistrationStartGatewayConfig.registrationMessageGateway` injects the registry port. Rewrite `RegistrationMessageDeliveryAdapterTest`, and adjust whichever test from II-2.a pins the old wiring. | DONE (commit ce2176e7; script 068; result in C.13) |
| II-2.c | A test with the real `YamlNoticeTemplateRegistryAdapter` and `DefaultMessageTemplateRenderer` that renders START and RESEND content for SMS and EMAIL in en and zh_HK and asserts that no placeholder is left unreplaced and that the unit follows the selected locale. | DONE (commit 14c71b24; script 070; result in C.14) |
| II-2.e | START and RESEND tests for a missing registration-otp definition (decision D7), requested by Nick on 2026-10-06. START with the captcha gate off, and RESEND: the original IllegalStateException reaches the caller as an error signal and nothing is sent (commit 951049f8, script 076). START with the captcha gate on, the production setting: the operation turns it into a SYSTEM_FAILED result with SYSTEM_UNAVAILABLE and nothing is sent (commit da136a8f, script 078, C.17). Each code commit moves the source pin; II-2.d is rerun on the last one (C.15, C.16). | DONE (commits 951049f8 and da136a8f; scripts 076 and 078; results in C.16 and C.17) |
| II-2.d | Stage gate on the final code: full `mvn test` and an application startup smoke test, then the plan is updated. | DONE (script 079, run 20261006-153630 on da136a8f; result in C.17) |

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

### C.10 Findings of script 063 and a correction

Recorded 2026-10-05 from the evidence of script 063 (read-only; HEAD 55caa2b0; `src/` and `pom.xml`
identical to 211f3702). It closes open item 3 of C.9.6 and corrects one statement in C.5. Decisions
D6 and D7 are unchanged and still open; this section records facts and does not choose an option.

#### C.10.1 Correction to C.5: no live validation of the Registration templates

C.5 said that `AuthWorkflowProperties` still validates the placeholders of the old Registration
`sms-template` and `email-template` (line 497). That was inferred from one matching line and is
wrong. Line 497 is inside the private method `requireTemplate` (lines 493-502). Its only two call
sites (lines 484 and 489, in `validateForgotPasswordOtpPolicy`) are commented out, so the method has
no live caller. The methods of `AuthWorkflowProperties` whose names start with `validate` are
`validateRequiredDestinations` (lines 221 and 225), `validateLoginPolicy` (313),
`validateForgotPasswordPolicy` (379), `validateUsernameRecoveryPolicy` (441),
`validateUsernameRecoveryTemplate` (463) and `validateForgotPasswordOtpPolicy` (473). None concerns
Registration, and no line of the file mentions `registration.otp` or `registration.getOtp`. Other
classes were not searched for such a validation.

Consequences:

- The migration does not remove a startup check of the Registration templates, because none was
  found in that file.
- The new registry does not check placeholders either: `NoticeTemplateProperties.validate()` rejects an
  empty definition and blank content but does not look for `{code}` (script 055 source dump). What
  guards `registration-otp` today is `NoticeTemplateRegistryFullMatrixSmokeTest` (lines 89-99), which
  asserts that the English SMS and EMAIL content require exactly `code`, `ttlValue` and `ttlUnit`.
  It does not assert the placeholder set of the zh_HK Registration content. At run time
  `DefaultMessageTemplateRenderer` throws on a variable that the template needs and the command does
  not carry, and ignores a variable that the template does not use.
- II-2.c (C.7) should therefore also assert the placeholders and the rendered text of the zh_HK
  Registration content, not only that no placeholder is left unreplaced.

#### C.10.2 Sizing facts for D6 (no option chosen)

- `NoticeTemplateDefinition` is constructed on 14 test lines in 2 files: 13 in
  `NoticeTemplateDefinitionTest` and 1 in the fake registry inside
  `UsernameRecoveryRequestProtectedOperationTest` (line 542). Main code constructs it twice, both in
  `YamlNoticeTemplateRegistryAdapter` (lines 50 and 63). A new record component without a secondary
  constructor of the current six-argument shape would require editing all 14 test lines; with one,
  none. Design section 8.2 records the same secondary-constructor pattern for the APIM DTOs.
- `NoticeTemplateProperties`, `NoticeDefinition` or `EmailContent` appear in 6 test files (matching
  lines per file): `NoticeTemplatePropertiesTest` 21, `YamlNoticeTemplateRegistryAdapterTest` 16,
  `YamlNoticeTemplateRegistryAdapterLocaleSelectionTest` 15, `NoticeTemplateRegistryFullMatrixSmokeTest`
  6, `ApplicationYamlNoticeTemplatesBindingTest` 3 and `NoticeTemplateDefinitionTest` 1. They build
  the properties through `setSms`, `setEmail` and `setDefinitions`. The classes are Lombok `@Getter`
  and `@Setter` classes with initialised maps (script 055 source dump), so an added optional
  property with a default value should not change those calls. That is an inference, to be
  confirmed by compilation.
- `NoticeTemplateRegistryPortContractShapeTest` pins one method, `resolve(MessageDeliveryType,
  MessageDeliveryChannel, String)`, returning `NoticeTemplateDefinition`. Two tests touch the port
  itself: a mock in `UsernameRecoveryFeatureConfigTest` (line 102) and `FakeNoticeTemplateRegistryPort`
  in `UsernameRecoveryRequestProtectedOperationTest` (line 529), which implements it. Carrying a
  value inside the returned definition leaves the port unchanged; a new abstract method on the port
  would stop that one fake from compiling.
- `DefaultMessageTemplateRendererTest` (lines 98-99) renders `有效期為 {ttlValue}{ttlUnit}` and expects
  `有效期為 5分鐘`. The renderer therefore already produces the intended Chinese text when the unit
  it receives is `分鐘` (line 98 is cut off in the evidence; the expected value implies the unit).
- Other tests that mention the unit word, besides the placeholder names in the registry smoke test:
  `AuthWorkflowPropertiesBindingTest` (lines 66-72, old Registration values; changes in III-1),
  `ForgotPasswordWorkflowPropertiesValidationTest` (lines 34-36, old Forgot Password configuration)
  and `ForgotPasswordInitialOtpBindingSubtask005002FailFirstTest` (line 58, which asserts that a
  rendered Forgot Password SMS contains `minutes`; relevant to II-4 and III-2 and III-3). No test
  pins the `ttlUnit` value that Login or Registration passes.

#### C.10.3 Updated open items

1. D6 and D7 are not confirmed.
2. Closed by script 063: the number of test sites that construct `NoticeTemplateDefinition` or use
   the properties classes, the tests that mention the unit word (C.10.2), and whether `requireTemplate`
   is called for Registration (it is not, C.10.1).
3. Not designed yet: the shape of the per-locale unit configuration if option A is chosen, and the
   unit wording per locale. They are the content of II-2.0 once D6 is confirmed.

### C.11 Decisions D6 and D7 confirmed; design and sub-plan update (script 065)

Recorded 2026-10-05. Nick confirmed decision D6 option A and decision D7 on 2026-10-05. The design is
recorded in the solution design, section 8.6 (Per-Locale TTL Unit). Nothing is implemented yet.

#### C.11.1 Decisions

| # | Decision | Status |
| --- | --- | --- |
| D6 | Option A. The unit word for `{ttlUnit}` is configured per locale in the `notice-templates` YAML (`notice-templates.ttl-units`), looked up by the locale key that the registry selected for the content, and returned in the resolved `NoticeTemplateDefinition`. A caller passes only the number and never translates the unit. | Confirmed by Nick, 2026-10-05 |
| D7 | A missing Registration definition fails: `NoticeTemplateRegistryPort.resolve` throws `IllegalStateException` inside `deliverRegistrationOtp`, as a missing policy does today, and the adapter does not fall back to the old subject `registration-otp` and body `{code}`. | Confirmed by Nick, 2026-10-05 |

#### C.11.2 Updated sub-plan

| Sub-stage | Change | Status |
| --- | --- | --- |
| II-2.0 | Confirm D6 and D7; write the design (solution design section 8.6) and this section. | DONE (script 065) |
| II-2.a | Tests named in C.7. | DONE (script 062; findings in C.9) |
| II-2.a2 | Read-only script that prints the real sources and tests that II-2.b0 amends (`NoticeTemplateDefinition`, `NoticeTemplateProperties`, `YamlNoticeTemplateRegistryAdapter`, their tests, the `notice-templates` block of `application.yml`), so that the patch anchors come from the real files. Script 065 already collects a first copy of this evidence. | DONE (the real sources of the 065 evidence were reviewed; script 066 was patched against them) |
| II-2.b0 | Template Service extension (a Phase I amendment), see the II-2.b0 row in C.7 and solution design section 8.6. | DONE (commit be7294d5; script 066; result in C.12) |
| II-2.b | `RegistrationMessageDeliveryAdapter` migration, as in C.7. Before editing, read how START and RESEND treat an exception thrown synchronously by `deliverRegistrationOtp` (D7), and add a test that a missing definition surfaces as an error and not as the value `false`. | DONE (commit ce2176e7; script 068; result in C.13) |
| II-2.c | Rendering test with the real registry and renderer, extended by C.10.1: assert the placeholders and the rendered text of the zh_HK Registration content as well, including the unit (for example `有效期為5分鐘。`). | DONE (commit 14c71b24; script 070; result in C.14) |
| II-2.e | START and RESEND tests for a missing registration-otp definition (decision D7), requested by Nick on 2026-10-06. START with the captcha gate off, and RESEND: the original IllegalStateException reaches the caller as an error signal and nothing is sent (commit 951049f8, script 076). START with the captcha gate on, the production setting: the operation turns it into a SYSTEM_FAILED result with SYSTEM_UNAVAILABLE and nothing is sent (commit da136a8f, script 078, C.17). Each code commit moves the source pin; II-2.d is rerun on the last one (C.15, C.16). | DONE (commits 951049f8 and da136a8f; scripts 076 and 078; results in C.16 and C.17) |
| II-2.d | Stage gate on the final code: full `mvn test` and an application startup smoke test, then the plan is updated. | DONE (script 079, run 20261006-153630 on da136a8f; result in C.17) |

#### C.11.3 Carried to later stages (not part of II-2)

- II-3 (Login): `LoginOtpSendService` and `LoginOtpResendService` pass the policy value in seconds with the
  unit `seconds` (C.3). With the unit configured as minutes, Login must pass the value in minutes. That changes
  what Login sends today and is confirmed at II-3.
- II-4 (Forgot Password): the two `ttlUnit(language)` helpers are replaced by `definition.ttlUnit()`; the details
  OTP passes a literal `5`. Stage III-3 then deletes the helpers.
- English plural wording is unchanged: a value of 1 is still written with the unit `minutes`.

#### C.11.4 Open items

1. How START and RESEND surface an exception thrown by `deliverRegistrationOtp` (D7) was not read in full;
   script 065 collects the `onError*` lines of the callers. It is settled in II-2.b.
2. Whether `application-local.yml` overrides any `notice-templates` key is not established; script 065 collects
   the files that define the key.
3. The exact current text of the three Phase I classes and their tests is taken from the evidence of script 055
   and, again, from script 065. II-2.b0 is patched against the real files, not against this section.

### C.12 Sub-stage II-2.b0 result (script 066)

Recorded 2026-10-05 from the evidence of script 066 (HEAD before the script 434c2504; code commit
be7294d5). Statements are limited to what that evidence shows.

#### C.12.1 What was committed

- Commit be7294d5 `feat(template-service): configure the TTL unit per locale and return it with the
  resolved definition`: 8 files, 661 insertions and 3 deletions. Modified: `NoticeTemplateDefinition`
  (nullable last component `ttlUnit`, blank rejected, public six-argument constructor kept),
  `NoticeTemplateProperties` (`ttlUnits`; `validate()` split into `validateDefinitions()` and
  `validateTtlUnits()`), `YamlNoticeTemplateRegistryAdapter` (the unit of the SELECTED locale key;
  fails for a required but unconfigured unit) and `application.yml` (`notice-templates.ttl-units`:
  `en` `minutes`, `zh_HK` `分鐘`). New: `NoticeTemplateDefinitionTtlUnitTest` (5 tests),
  `NoticeTemplatePropertiesTtlUnitTest` (10), `YamlNoticeTemplateRegistryAdapterTtlUnitTest` (8) and
  `NoticeTemplateRegistryTtlUnitMatrixTest` (3, against the real `application.yml`).
- `NoticeTemplateRegistryPort`, the workflows and the test fakes of the port were not touched.
- Source pin from now on: `src/` and `pom.xml` equal be7294d5, which replaces 211f3702 as the last
  code commit. Docs-only commits do not change it.

#### C.12.2 Validation

- `mvn -DskipTests compile` exit 0 (33 s); `mvn -DskipTests test-compile` exit 0 (48 s).
- Scoped run with wildcard patterns `NoticeTemplate*Test`, `YamlNoticeTemplate*Test`,
  `ApplicationYamlNoticeTemplatesBindingTest`, `CleanArchitectureDependencyTest`,
  `UsernameRecovery*Test` and `*MessageDelivery*Test`: 660 tests, 0 failures, 0 errors, 0 skipped
  (216 s), 121 classes with fresh reports. All 13 narrow classes of the source ran, every observed
  floor held, 97 `UsernameRecovery*Test` and 11 `*MessageDelivery*Test` classes were fresh, and
  each new class ran exactly the number of `@Test` methods it declares.
- JaCoCo of that run: `NoticeTemplateDefinition` has 0 missed instructions and 0 missed branches
  of 14 (record tier 95% and 95%: met). `YamlNoticeTemplateRegistryAdapter` is at 99.4%
  instruction and 97.4% branch (adapter tier 95% and 85-90%: met). Its one missed branch is the
  defensive `template == null` branch of `placeholders`, justified in Appendix A.2 at line 116; it
  now sits at lines 125-126 because the patch added lines above it. Nothing new is uncovered.
- Not run in 066 by design: the full `mvn test`, the application startup smoke test (both belong
  to the stage gate II-2.d on the final II-2 code commit) and `mvn spotless:apply` (deferred to the
  feature-level stabilization).

#### C.12.3 Observation for II-2.b (from sources already printed, not yet tested)

START (`RegistrationStartProtectedOperation`, line 218) and RESEND
(`RegistrationOtpResendProtectedOperation`, line 57) both call
`sessions.updateOtpChallenge(...).then(messages.deliverRegistrationOtp(...))`. The argument of
`then` is evaluated while the chain is assembled inside a `flatMap` lambda, before
`updateOtpChallenge` is subscribed. Today a missing policy therefore throws inside
`deliverRegistrationOtp` at assembly time: the OTP challenge already exists, the session update does
not run, and the exception becomes an error signal of the `flatMap`. Decision D7 keeps exactly that
place and behaviour for a missing definition. II-2.b must (a) keep the registry call eager, in the
same place, (b) test that a missing definition surfaces as an error and not as the value `false`
(`false` already means a failed delivery and is handled by both callers), and (c) read how the two
operations and the web layer treat that error before editing (script 067 collects it).

#### C.12.4 Open items

1. II-2.b is not started. How START and RESEND, the idempotency layer and the controller surface
   an exception thrown synchronously by `deliverRegistrationOtp` is not read yet; script 067 collects
   the sources.
2. Whether any test that builds a Spring context around `RegistrationStartGatewayConfig` lacks a
   `NoticeTemplateRegistryPort` bean (the gateway method gains that parameter in II-2.b) is not
   established; script 067 collects the candidates.
3. The gates of II-2.d pin `src/` and `pom.xml` to the final II-2 code commit, not to be7294d5.

### C.13 Sub-stage II-2.b result (script 068)

Recorded 2026-10-06 from the evidence of script 068 (HEAD before the script 11c12158; code commit
ce2176e7). Statements are limited to what that evidence shows.

#### C.13.1 What was committed

- Commit ce2176e7 `feat(template-service): resolve Registration OTP content through the notice
  template registry`: 5 files, 361 insertions and 226 deletions.
  - `RegistrationMessageDeliveryAdapter` (+23, -42): the constructor takes `NoticeTemplateRegistryPort`
    in place of `AuthWorkflowPolicyPort`; `deliverRegistrationOtp` resolves `REGISTRATION_OTP` for the
    channel and the raw `acceptLanguage` before it builds the command and sends `code`, `ttlValue` and
    the `ttlUnit` of the definition; the policy lookup, the fallback subject and body and the literal
    unit `minutes` are removed.
  - `RegistrationStartGatewayConfig` (+3, -2): `registrationMessageGateway` takes
    `NoticeTemplateRegistryPort` in place of `AuthWorkflowPolicyPort` and passes it to the delivery
    adapter; the OTP gateway keeps its policy port.
  - `RegistrationMessageDeliveryAdapterTest` is rewritten (12 tests, +220, -175).
  - `RegistrationMessageDestinationOverrideWiringTest` (+7, -7): its six gateway calls pass a registry
    mock.
  - New `RegistrationStartGatewayConfigRegistryWiringTest` (2 tests, 108 lines): the injected registry is
    the one the adapter resolves from, and a missing definition fails the gateway call with nothing sent.
- Not touched: `NoticeTemplateRegistryPort`, START and RESEND, the destination override decorator, the
  OTP gateway and the old `auth-workflows.registration.otp` configuration (it stays until III-1).
- Source pin from now on: `src/` and `pom.xml` equal ce2176e7, which replaces be7294d5 as the last
  code commit. Docs-only commits do not change it.

#### C.13.2 Validation

- `mvn -DskipTests compile` exit 0 (19 s); `mvn -DskipTests test-compile` exit 0 (42 s).
- Scoped run with the wildcard patterns `*Registration*Test`, `NoticeTemplate*Test`,
  `YamlNoticeTemplate*Test`, `ApplicationYamlNoticeTemplatesBindingTest`,
  `CleanArchitectureDependencyTest` and `*MessageDelivery*Test`: 918 tests, 0 failures, 0 errors,
  1 skipped (178 s). 189 classes have a fresh report, which is every one of the 189 source classes
  that match a pattern (a class can match more than one pattern): `*Registration*Test` 166 (floor
  90), `*MessageDelivery*Test` 11, `NoticeTemplate*Test` 8, `YamlNoticeTemplate*Test` 3, the binding
  test 1 and the layering test 1.
- All 15 observed floors held. They include the three guard tests of C.9.1:
  `RegistrationMessageDestinationOverrideWiringTest` 4, `RegistrationHardcodedPolicyArchitectureGuardTest`
  4 and `RegistrationSharedCoreMigrationArchitectureTest` 3, so the exact-text constraints on the
  gateway config held, and `RegistrationStartMandatoryCompositionTest` 1. The 12 tests of the
  rewritten adapter test and the 2 of the new test ran exactly as declared.
- JaCoCo: `RegistrationMessageDeliveryAdapter` is at 100.0% instruction (0 missed) and 100.0% branch
  (0 of 6 missed). The adapter tier (95% and 85-90%) is met with no uncovered branch to justify.
- One skipped test. The 068 evidence does not name its class. Of the four classes known from
  Appendix A.3 to hold the 10 skips of a full run, only `RegistrationOtpResendLifecycleOwnershipTest`
  (1 skip) matches a scoped pattern, so this skip is consistent with it; script 069 lists the skipped
  test cases found in the surefire reports (evidence file 04b).
- Not run in 068 by design: the full `mvn test` and the application startup smoke test (both belong to
  the stage gate II-2.d on the final II-2 code commit) and `mvn spotless:apply`.

#### C.13.3 Registration behaviour now in effect (C.5)

- The SMS and EMAIL wording is the BRD content of `notice-templates` (C.2).
- There is no fallback subject, body or unit: a missing definition throws `IllegalStateException`
  inside `deliverRegistrationOtp`, before anything is sent (decision D7). The adapter test and the
  gateway wiring test assert this with a registry fake and with a mock.
- The registry receives the raw `acceptLanguage` and the unit comes from the selected locale key, so
  `zh`, `zh-TW` and `zh-Hant` get 分鐘 together with the zh_HK content (solution design 8.6, rules 1
  and 6). The adapter tests use a registry fake that returns a definition with the unit; the
  selection itself is covered by the registry tests of II-2.b0 (C.12).

#### C.13.4 The error path of decision D7 (evidence of script 067; not yet read in full)

- The registry call stays eager and in the place where the policy lookup was: the argument of
  `then(...)` in START (line 218) and RESEND (line 57) is evaluated while the chain is assembled
  (C.12.3).
- START with the captcha gate on ends its chain with `onErrorResume(ignored -> failure(SYSTEM_FAILED,
  SYSTEM_UNAVAILABLE, "PAGE"))` (`RegistrationStartProtectedOperation`, lines 123-129). With the gate
  off, `lookup(command, validated)` is returned directly (line 113) and that class has no handler. RESEND
  handles only `RegistrationOtpSendRateLimitedException` and `RegistrationOtpResendRejectedException`
  (lines 75-97).
- `SharedStartRegistrationService` (line 54 `RegistrationSharedFailureClassifiers.possibleSideEffect()`,
  line 89 `SYSTEM_FAILED`) and `SharedResendRegistrationOtpService` (lines 98 and 130) wrap those
  operations, and the result bridges map `SYSTEM_FAILED` to HTTP 500
  (`RegistrationStartSharedExecutionBridge` line 174, `RegistrationOtpResendSharedExecutionBridge`
  line 83). That an `IllegalStateException` from the adapter ends as `SYSTEM_FAILED` and 500 is therefore
  the likely path, but the code between those lines was not printed and no test exercises START or
  RESEND with a missing definition. D7 keeps today's behaviour for a missing policy, so this is not a
  regression. (Corrected in C.14.3: the exception is not turned into a `SYSTEM_FAILED` result there.)

#### C.13.5 Open items

1. The shared services and the idempotency classifiers of C.13.4 are not read; script 069 collects them.
   Whether a START or RESEND test for a missing definition is worth adding is decided after reading them.
2. Context tests (open item 2 of C.12.4): every `*Registration*Test` class passed, so none of them
   builds `RegistrationStartGatewayConfig` without a `NoticeTemplateRegistryPort` bean. The context
   tests whose names do not match a scoped pattern run in the full test of II-2.d.
3. II-2.c is not started. Script 069 prints the rendering and sender classes and the precedents it
   needs, so that the test is written against the real sources.
4. The gates of II-2.d pin `src/` and `pom.xml` to the final II-2 code commit (ce2176e7 now; the commit
   of II-2.c if it adds test code).

### C.14 Sub-stage II-2.c result (script 070) and the error path of decision D7

Recorded 2026-10-06 from the evidence of script 070 (HEAD before the script acbb5819; code commit
14c71b24) and from the executor and service sources that scripts 069 and 070 printed. Statements are
limited to what that evidence shows; inferences are marked as such.

#### C.14.1 What was committed

- Commit 14c71b24 `test(template-service): render Registration OTP content end to end with the real
  registry and renderer`: one new file, `RegistrationOtpRenderedContentIntegrationTest`, 297 lines
  added and 0 removed, 11 tests. Test-only: no main code and no resource changed.
- The test runs the whole chain with the real `application.yml`, the real
  `YamlNoticeTemplateRegistryAdapter`, the real `RegistrationMessageDeliveryAdapter` and the real
  `MessageDeliveryAdapter` with its default renderer; only the two senders are recorders. It asserts,
  for SMS and EMAIL: English keeps a space and writes `minutes`; zh-HK writes the number directly
  followed by the Chinese unit (`有效期為5分鐘。` in the SMS and `此密碼於5分鐘內有效。` in the e-mail);
  `zh`, `zh-TW`, `zh-Hant`, `zh-Hant-HK`, `zh-CN`, `zh-Hans` and `ZH_hk` render exactly the zh-HK SMS;
  `fr`, `und`, blank and null render the English one; the unit comes from `notice-templates.ttl-units`;
  `ttlValue` is whole minutes with a minimum of 1; no placeholder is left in any SMS body, e-mail
  subject or e-mail body; a missing `registration-otp` definition (D7) and a required but unconfigured
  unit fail before anything is sent.
- The two Chinese phrases passed against the real `application.yml`, so the wording of the BRD content
  still has the unit directly after the number, as C.3 and C.10 describe.
- Source pin from now on: `src/` and `pom.xml` equal 14c71b24 (they differ from ce2176e7 by exactly the
  new test file), which replaces ce2176e7 as the last code commit. Docs-only commits do not change it.

#### C.14.2 Validation

- `mvn -DskipTests compile` exit 0 (20 s); `mvn -DskipTests test-compile` exit 0 (41 s).
- Scoped run with the wildcard patterns `*Registration*Test`, `NoticeTemplate*Test`,
  `YamlNoticeTemplate*Test`, `ApplicationYamlNoticeTemplatesBindingTest`,
  `CleanArchitectureDependencyTest`, `*MessageDelivery*Test` and `DefaultMessageTemplateRenderer*Test`:
  941 tests, 0 failures, 0 errors, 1 skipped (103 s). 191 classes have a fresh report, which is every one
  of the 191 source classes that match a pattern (a class can match more than one pattern):
  `*Registration*Test` 167 (floor 160), `*MessageDelivery*Test` 11, `NoticeTemplate*Test` 8,
  `YamlNoticeTemplate*Test` 3, the binding test 1, the layering test 1 and the renderer test 1.
- All 18 observed floors held, including the three guard tests of C.9.1 (4, 4 and 3), the 12 tests of
  `RegistrationMessageDeliveryAdapterTest`, the 2 of `RegistrationStartGatewayConfigRegistryWiringTest`
  and the 12 of `DefaultMessageTemplateRendererTest`. The new class ran 11 of its 11 tests.
- JaCoCo: `RegistrationMessageDeliveryAdapter` stays at 100.0% instruction and 100.0% branch (0 of 6
  missed), as expected for a test-only commit.
- The one skipped test is not named in the 070 evidence. The 069 evidence listed
  `RegistrationOtpResendLifecycleOwnershipTest.adapterContainsNoOverlappingDestinationSendLimiter`
  among the skipped cases of the surefire reports, written in the 068 run; the skip is presumably that
  one. Script 071 lists the skipped cases again (evidence file 04b).
- Not run in 070 by design: the full `mvn test`, the application startup smoke test (the stage gate
  II-2.d) and `mvn spotless:apply`.

#### C.14.3 Correction to C.13.4: where an exception from the adapter ends

C.13.4 said that an `IllegalStateException` from the adapter probably ends as `SYSTEM_FAILED` and HTTP
500. The executor source shows that this is not the path for a request that reaches the executor.

- `DefaultIdempotencyExecutor.executeProtected` (lines 67-75) runs the protected operation inside
  `Mono.defer(...)` and sends any failure to `classifyProtectedFailureThenError` (lines 104-125). With
  the classifier that START and RESEND use, `possibleSideEffect()` (`IdempotencyFailureClassifiers`,
  which returns `POSSIBLE_SIDE_EFFECT` for every failure), it calls `markUnknownThenError` (lines
  127-143): the idempotency record is marked unknown and the ORIGINAL exception is re-emitted; only a
  failure of the marking itself becomes an `IdempotencyExecutionException`.
- `SharedStartRegistrationService.start` (lines 41-60) and the Resend service (lines 88-104) end in
  `executor.execute(execution).map(result -> route(...))` and have no error handler. `route` (start
  lines 62-85, resend lines 106-126) maps only outcomes of the reservation: `FAILED_EXISTING` and
  `UNKNOWN_OUTCOME_EXISTING` become a `SYSTEM_FAILED` result (`SYSTEM_UNAVAILABLE` for START,
  `REGISTRATION_OTP_RESEND_FAILED` for RESEND). A thrown exception is therefore an ERROR signal that
  leaves the use case; it is not a `SYSTEM_FAILED` result on that request. The result bridges that map
  `SYSTEM_FAILED` to HTTP 500 (C.13.4) are not on this path.
- The registry call is evaluated while the chain is assembled inside a `flatMap` mapper (C.12.3).
  Reactor turns an exception thrown there into an error signal of that chain, which is why it reaches
  the executor's handler.
- The exception to this is START with the captcha gate on: `RegistrationStartProtectedOperation` ends
  that branch with `onErrorResume(ignored -> failure(SYSTEM_FAILED, SYSTEM_UNAVAILABLE, "PAGE"))`
  (067 evidence, lines 123-129), so the executor receives a `SYSTEM_FAILED` result and completes the key
  normally. With the gate off, START returns `lookup(...)` directly (line 113) and RESEND handles only
  `RegistrationOtpSendRateLimitedException` and `RegistrationOtpResendRejectedException`; both then
  behave as described above.
- Inferred, not read: a retry with the same idempotency key finds the unknown record and reaches
  `UNKNOWN_OUTCOME_EXISTING`, which `route` maps to `SYSTEM_FAILED`. The persistence code that decides
  that outcome was not printed.
- What the controller and the central handler do with the error signal was not read: the 067 evidence
  lists five `catch (RuntimeException exception)` blocks in `RegistrationController` (lines 156, 200,
  246, 317 and 376) and an `@ExceptionHandler(Exception.class)` in `ApiExceptionHandler` (line 230) with
  an `ApiStatus.SYSTEM_ERROR` at line 238, but not the code around them. Script 071 prints it.

This is the behaviour of a missing policy before the migration (C.1: the old adapter threw
`IllegalStateException` in the same place), so decision D7 is unchanged and nothing is regressed.

#### C.14.4 Observation, outside this workstream (nothing started)

`possibleSideEffect()` classifies every failure of the protected operation as possibly side-effecting.
A configuration error that is certain to occur before anything is sent, such as a missing definition,
therefore also marks the idempotency key unknown, as a missing policy did. A proven-before-dispatch
classifier exists (`provenPreDispatch()` in `RegistrationSharedFailureClassifiers`) but START and RESEND
do not use it for this call. Changing that belongs to the shared idempotency and public-error work,
not to the Template Service, and D7 keeps today's behaviour. It is recorded here so that it is not
rediscovered; no change is proposed in this workstream.

#### C.14.5 Open items

1. The controller catch blocks and the central handler of C.14.3 are not read; script 071 prints them.
2. Proposal, not yet confirmed by Nick: no START or RESEND test for a missing definition is added in
   II-2. The adapter test and the gateway wiring test assert the throw before anything is sent, and
   what the shared executor does with any exception is a shared-layer matter that D7 leaves unchanged.
   Revisit if that behaviour should be pinned by a test.
3. II-2.d is next. Its gate pins `src/` and `pom.xml` to 14c71b24. The 066 to 070 scoped runs do not
   replace the full `mvn test` and the startup smoke test. Script 071 collects a read-only preflight of
   what the smoke test needs (evidence file 06); the first attempt at the II-1 gate failed because no
   Redis Sentinel node accepted a TCP connection (A.2), so that count is the item to look at first.
4. The skipped test of C.14.2 is named from earlier evidence, not from the 070 run.

### C.15 Decision to add the START and RESEND tests (II-2.e) and the gate run on 14c71b24

Recorded 2026-10-06 from the evidence of scripts 072 and 073. Statements are limited to what that
evidence shows; inferences are marked.

#### C.15.1 Decision

- Nick confirmed the proposal of C.14.5 item 2 on 2026-10-06 ("yes, add them"): tests for START and
  RESEND that pin what happens when the `registration-otp` definition is missing (decision D7). This is
  sub-stage II-2.e.
- Consequences: it is a new code commit, so the source pin (`src/` and `pom.xml`) moves from 14c71b24 to
  that commit and the stage gate II-2.d is rerun on it with a new script. II-2.d stays PENDING and Stage
  II-2 stays IN PROGRESS until then.
- Default, not yet confirmed by Nick: the tests do not assert the state of the idempotency key, because
  C.14.4 records that behaviour as questionable and a test would pin it.

#### C.15.2 Gate run on 14c71b24 (script 072; a record for that commit only)

- Run 1 (evidence 20261006-130845): the preflight FAILED because a Java language server (up about 22
  hours) had this worktree as its working directory; Gate A and Gate B were NOT RUN and nothing was built.
- Run 2 (evidence 20261006-131109), after the language server was closed: Gate A PASS, Gate B PASS.
  - Gate A: `mvn -B -ntp test`, BUILD SUCCESS, exit 0, 03:39 min; 5558 tests, 0 failures, 0 errors, 10
    skipped (the same four classes as in A.3); 1146 fresh reports; the XML holds 5558 testcase elements,
    equal to the console total; 0 lines with `NoClassDefFoundError`; all 16 workstream floors held.
  - Gate B: started through `ngtpa-runtime-start apim-profiles` in 5.895 s; health and readiness HTTP 200
    and UP right after the start and health again after 20 s; 0 failure markers and 0 ERROR lines in the
    application log; 1 WARN from `SecurityConfig` (in-process authentication DISABLED; not examined); 0
    NoticeTemplate lines; the stop exited 0 and port 8888 was released.
- The pin of that run is 14c71b24. It does not cover II-2.e.

#### C.15.3 Findings of script 073 (read-only)

- START (line 218) and RESEND (line 57) call `deliverRegistrationOtp` as the argument of `then(...)`
  inside a `flatMap` mapper, so a missing definition throws while the chain is assembled (C.12.3).
- The RESEND operation handles only `RegistrationOtpSendRateLimitedException` and
  `RegistrationOtpResendRejectedException` (lines 75-97); an `IllegalStateException` propagates.
- START with the captcha gate off returns `lookup(...)` directly (line 113) and has no handler, so the
  error propagates. With the gate on, `onErrorResume` (lines 123-129) turns it into a `SYSTEM_FAILED`
  result with `SYSTEM_UNAVAILABLE`, so the shared executor completes the key normally and the caller gets a
  result, not an error. Inferred, not read: that result is then captured as the terminal response of the
  key. Whether production runs with the gate on is not established; script 074 collects the policy config.
- `DefaultIdempotencyExecutor` lines 104-143 confirm C.14.3: with `possibleSideEffect()` the key is marked
  unknown and the ORIGINAL exception is re-emitted.
- The `Message` fakes of the existing tests never throw. The `Executor` fakes of
  `ResendRegistrationOtpUseCaseTest` and `RegistrationOtpResendServiceIntegrationTest` call the protected
  operation directly and apply no classifier, so they do not exercise the key state. The session fakes
  count `updateOtpChallenge` when it is called, not when it is subscribed, so "the session was not
  updated" cannot be asserted with them.
- `FakePort` of `DefaultIdempotencyExecutorTest` is package-private.
- Script 073 had a defect: its section 07 (controller and `ApiExceptionHandler`) is empty because of a
  typo although section 03 found both classes, and section 09 printed only the first 90 lines and an index
  of `StartRegistrationUseCaseTest`, so its `Fixture` was not read. Script 074 collects both (evidence files
  04a and 04b).

#### C.15.4 Test design (draft, to be confirmed against the 074 evidence)

- New test class or classes beside the other II-2 tests; no existing test is edited.
- Chain: the real `RegistrationMessageDeliveryAdapter` with a registry that throws `IllegalStateException`
  for the missing definition and a `MessageDeliveryPort` mock; the real shared services and protected
  operations; an executor fake like the existing ones.
- Assertions: the `IllegalStateException` reaches the caller of `start()` and `resend()` as an error
  signal; no result is emitted (neither a `MESSAGE_DELIVERY_FAILED` result derived from `false` nor a
  `SYSTEM_FAILED` result); the `MessageDeliveryPort` is never called. No assertion on the idempotency key
  state and none on the session update count (C.15.1 and C.15.3).
- START is covered with the captcha gate off. The gate-on behaviour (a `SYSTEM_FAILED` result) needs the
  captcha fixtures of `StartRegistrationCaptchaGateTest`, which are not read; it is an open item.

#### C.15.5 Open items

1. Read the controller catch blocks and the central handler (074 evidence 04a).
2. Read the `StartRegistrationUseCaseTest` fixture and the idempotency types (074 evidence 04b and 04c).
3. Establish whether the captcha gate is on in production (074 evidence 04d), and decide whether the
   gate-on START case is pinned in II-2.e.
4. Nick to confirm or change the default of C.15.1 (no assertion on the key state).
5. Sequence: script 075 writes and commits the tests; script 076 reruns the gate on the new commit; a
   final docs commit then marks II-2.e, II-2.d and Stage II-2 DONE.

### C.16 Result of script 076 (II-2.e, START gate off and RESEND) and the production START path

Recorded 2026-10-06 from the evidence of scripts 074 and 076. Statements are limited to what that
evidence shows; inferences are marked.

#### C.16.1 Script 076 (script 075 never ran)

- The saved file of script 075 ends in the middle of line 198 and bash reported "syntax error:
  unexpected end of file", so it executed nothing. Script 076 is its reissue with a truncation
  guard; the Java test is unchanged.
- Commit 951049f8, subject "test(template-service): START and RESEND fail with the original error
  when the registration-otp definition is missing": one new file,
  `RegistrationMissingDefinitionStartResendIntegrationTest`, 389 lines added, 4 tests. Test-only: no
  main code, no resource and no pom.xml changed.
- The tests run the real `RegistrationMessageDeliveryAdapter` with a registry fake that throws
  `IllegalStateException` for the missing definition and a `MessageDeliveryPort` mock, the real
  `DefaultIdempotencyExecutor` over a port that always grants the reservation, the real shared
  services and the real protected operations:
  - START with the captcha gate off (policy and gate null) and a missing definition: the caller gets
    the original `IllegalStateException` as an error signal, no result is emitted, the registry is
    called once for `REGISTRATION_OTP` with the locale `en`, and the delivery port is never called.
  - RESEND and a missing definition: the same; the registry is asked for the EMAIL channel.
  - Two control tests with a configured definition: the START operation reaches `STARTED` and the
    RESEND operation reaches `RESENT`, each with one delivery. They stop the failure tests from
    passing for a wrong reason.
- Not asserted, by design (C.15.1): the state of the idempotency key and the number of session
  updates.

#### C.16.2 Validation of 076

- `mvn -DskipTests compile` exit 0 (22.993 s); `mvn -DskipTests test-compile` exit 0 (49.512 s).
- Scoped run with the patterns `*Registration*Test`, `NoticeTemplate*Test`,
  `YamlNoticeTemplate*Test`, `ApplicationYamlNoticeTemplatesBindingTest`,
  `CleanArchitectureDependencyTest`, `*MessageDelivery*Test`, `DefaultMessageTemplateRenderer*Test`
  and `DefaultIdempotencyExecutorTest`: BUILD SUCCESS, 952 tests, 0 failures, 0 errors, 1 skipped
  (04:34 min). 193 fresh reports, which is every one of the 193 source classes that match a pattern;
  954 older reports were ignored; the console total equals the XML. The new class ran 4 of 4; all 17
  observed floors held. The one skipped test is `RegistrationOtpResendLifecycleOwnershipTest` (1),
  as in C.14.2.
- 952 equals 941 (script 070) + 4 (new) + 7 (`DefaultIdempotencyExecutorTest`, added to the patterns
  in this run). That is arithmetic on the evidence, not a pass criterion.
- The source file has two lines over 100 characters (lines 164 and 184). `mvn spotless:apply` is
  deferred to the feature-level stabilization.
- Not run in 076 by design: the full `mvn test`, the application startup smoke test and spotless.

#### C.16.3 New source pin

- `src/` and `pom.xml` now equal 951049f8; they differ from 14c71b24 by exactly the new test file
  (the script's final check shows one `A` line for it). 951049f8 replaces 14c71b24 as the last code
  commit. Docs-only commits do not change it.
- The 072 gate run (C.15.2) is a record for 14c71b24 only.

#### C.16.4 Production START path (evidence of scripts 073 and 074)

- `application.yml` has `auth-workflows.registration.pre-start-challenge` with `enabled: true` and
  `type: CAPTCHA` (lines 248-250); of the resource files only `application-local.yml` sets
  `enabled: false` (lines 141-142). Unless an external configuration overrides it (not checked),
  START therefore takes the captcha-gate-on path everywhere except the local profile.
- With the gate on, `RegistrationStartProtectedOperation` ends the chain with
  `onErrorResume(ignored -> failure(SYSTEM_FAILED, SYSTEM_UNAVAILABLE, "PAGE"))` (lines 123-129). A
  missing definition, which throws while the chain is assembled, therefore does NOT reach the caller
  as an error: START returns a `SYSTEM_FAILED` result with the error key `SYSTEM_UNAVAILABLE` on
  target `PAGE`. The captcha has been consumed by then (it is consumed before the lookup), so the
  member needs a new one; no OTP is sent.
- Read in the bridge: `SYSTEM_FAILED` maps to HTTP 500 (`RegistrationStartSharedExecutionBridge`,
  line 174). Inferred, not read: the shared executor receives a normal result, captures it and
  completes the idempotency key; the capture of a result without a session id or a CSRF token was
  not read.
- The 076 tests therefore cover the local profile (gate off) for START, and RESEND everywhere. They
  do not cover the production START path.
- Narrowing of C.14.4: the observation that `possibleSideEffect()` marks the key unknown for an
  error that happens before any send applies to RESEND and to START with the gate off. With the gate
  on the protected operation converts the error to a result before it reaches the executor (inferred
  from the printed source; no test).

#### C.16.5 Where the error signal ends (074 evidence 04a)

- `RegistrationController` START (line 80) and RESEND (line 252) call the use case and `.map(...)`
  its result with no error handler. The `catch (RuntimeException)` blocks (lines 156, 200, 246, 317,
  376) only wrap command construction or the assembly of a call, not the signal of the returned
  `Mono`.
- The error signal of START with the gate off, and of RESEND, therefore reaches
  `ApiExceptionHandler.handleUnexpectedException` (`@ExceptionHandler(Exception.class)`, line 230):
  HTTP 500, `ApiStatus.SYSTEM_ERROR`, error code `SYSTEM_UNEXPECTED`. The handler has no mention of
  `IdempotencyExecutionException`. That the advice class is registered was not read (inferred).

#### C.16.6 Design of the missing test (the next code script)

- Same decision as C.15.1: Nick asked for the START and RESEND tests, and the production START path
  is START. It is added to II-2.e. Nick can drop it.
- New test class beside the 076 class; no existing test is edited. Fixtures are copied in shape from
  `StartRegistrationCaptchaGateTest` (074 evidence 11a): a `RegistrationAuthWorkflowPolicy` with
  `PreStartChallengePolicy(true, CAPTCHA)`, a `RegistrationCaptchaGateService` over a fake
  `CaptchaChallengePort` that holds a VERIFIED, unexpired challenge, the real
  `RegistrationMessageDeliveryAdapter` with a registry that throws, and a `MessageDeliveryPort`
  mock.
- It calls the START operation directly (not the service and executor), because the executor-side
  capture of this result was not read.
- Assertions: a result is emitted and no error; its status is `SYSTEM_FAILED` with a single error
  with key `SYSTEM_UNAVAILABLE` on target `PAGE`; the registry is called once for
  `REGISTRATION_OTP`; the delivery port is never called; the captcha is consumed exactly once. A
  control test with a configured definition and the same fixtures reaches `STARTED`.
- Still not asserted: the state of the idempotency key.

#### C.16.7 Open items and sequence

1. Script 078 adds the gate-on START test (a code commit; it moves the pin again). Script 079 reruns
   the stage gate on the last code commit with a new gate script. A final docs commit then marks
   II-2.e, II-2.d and Stage II-2 DONE. This corrects C.15.5 item 5, which numbered the scripts
   differently.
2. Whether production START should turn a missing definition into `SYSTEM_FAILED` after consuming
   the captcha is the existing behaviour and is not changed by this stage (decision D7 keeps it).
3. The two long lines of the 076 test file are left to the feature-level formatting pass.

### C.17 Closure of Stage II-2: START with the captcha gate on (078) and the stage gate (079)

Recorded 2026-10-06 from the evidence of scripts 078 and 079; this docs commit is script 080.
Statements are limited to what that evidence shows; inferences are marked.

#### C.17.1 Script 078 (sub-stage II-2.e, the production path of START)

- Commit da136a8f, subject "test(template-service): START with the captcha gate on returns
  SYSTEM_FAILED and sends nothing when the registration-otp definition is missing": one new file,
  RegistrationMissingDefinitionStartCaptchaGateOnIntegrationTest, 362 lines added, 2 tests.
  Test-only: no main code, no resource and no pom.xml changed.
- The tests call the START operation directly with the production setting: a
  `PreStartChallengePolicy` with enabled true and type CAPTCHA, a `RegistrationCaptchaGateService`
  over a fake `CaptchaChallengePort` that holds one VERIFIED, unexpired challenge, the real
  `RegistrationMessageDeliveryAdapter` with a registry fake that throws `IllegalStateException` for
  the missing definition, and a `MessageDeliveryPort` mock. The fixtures follow the shape of
  `StartRegistrationCaptchaGateTest`.
- Missing definition: a result is emitted and the Mono completes without an error. Its status is
  `SYSTEM_FAILED`, its next step is `NONE`, it has a single error with the key `SYSTEM_UNAVAILABLE`
  on the target `PAGE`, and it has no CSRF token and no auth-flow session id. The captcha is
  consumed exactly once, before the registry is called (the recorded order is captcha.load,
  captcha.consume, registry.resolve). The registry is called once for `REGISTRATION_OTP` with the
  locale en, and the delivery port is never called.
- Control test with a configured definition and the same fixtures: the result is `STARTED`, the
  captcha is consumed once, the registry is called once and one delivery is made.
- Not asserted, by design (C.15.1): the state of the idempotency key, the number of session updates
  and the contact channel.
- Validation: `mvn -DskipTests compile` exit 0 (17.681 s); `mvn -DskipTests test-compile` exit 0
  (31.743 s). Scoped run with the patterns of C.16.2: BUILD SUCCESS, 954 tests, 0 failures, 0
  errors, 1 skipped (02:04 min). 194 fresh reports, which is every one of the 194 source classes
  that match a pattern; the console total equals the XML. The new class ran 2 of 2, the 076 class
  ran 4, `StartRegistrationCaptchaGateTest` ran 11, and all 19 observed floors held. The one skipped
  test is `RegistrationOtpResendLifecycleOwnershipTest` (1), as before. 954 equals 952 (C.16.2) + 2.
- 51 anchors matched in the real files, 38 imports resolved to real files, and the new file has no
  line over 100 characters. This was the first compile of that Java and it succeeded without a
  change.
- Step 02 of script 078 listed one java process (pid 827143, a Java language server) whose working
  directory is the main api-service repository, not this worktree. It did not block the script.

#### C.17.2 New source pin

- `src/` and `pom.xml` now equal da136a8f. They differ from 951049f8 by exactly the new test file
  (one A line in the final check of script 078). da136a8f is the final II-2 code commit and replaces
  951049f8 as the source pin. Docs-only commits do not change it.

#### C.17.3 Stage gate II-2.d (script 079)

One run on 2026-10-06 (evidence 20261006-153630), pinned to da136a8f: HEAD da136a8f, `src/` and
`pom.xml` identical to da136a8f, working tree clean. The script changes no tracked file and makes no
commit; HEAD, the tree and the pin were unchanged at its end.

- Preflight OK: Maven 3.9.15 and Java 21.0.12.1; the wrapper scripts, the .env file (mode 600) and
  the Redis TLS files present; 1 of 1 Redis Sentinel nodes accepted a TCP connection; port 8888
  free; 968605 MB free. No process was tied to the worktree.
- One WARN: a Java language server (pid 827143, up 01:00:44 at the check, working directory the main
  api-service repository) was running and was still running after Gate A (01:05:00). It was not tied
  to this worktree. Inferred, not established: it did not interfere, because the tree stayed clean
  and the Maven log has no class-file error.

Gate A, full `mvn test`:

- `mvn -B -ntp test`: BUILD SUCCESS, exit code 0, 04:12 min (254 s). Maven reported 5564 tests, 0
  failures, 0 errors, 10 skipped.
- 1148 fresh surefire reports, 0 stale, 0 unparseable. The XML holds 5564 testcase elements, equal
  to the console total. Their tests attributes sum to 5554; the difference of 10 comes from the same
  six ArchUnit classes as in A.2 and C.15.2.
- 5564 equals 5558 (gate 072, C.15.2) + 4 (script 076) + 2 (script 078). That is arithmetic on the
  evidence, not a pass criterion.
- 10 skipped, in the same four classes and with the same counts as in A.3 and C.15.2.
- 0 lines with NoClassDefFoundError and 0 lines with "failed to check any classes" in the Maven log:
  the class-file problem of the II-1 run 1 did not occur.
- All 20 workstream floors held. Four of them are new in the gate script since gate 072:
  `RegistrationMissingDefinitionStartResendIntegrationTest` 4,
  `RegistrationMissingDefinitionStartCaptchaGateOnIntegrationTest` 2,
  `StartRegistrationCaptchaGateTest` 11 and `DefaultIdempotencyExecutorTest` 7. The others are the
  floors of gate 072, and every class ran with at least its floor.
- Test source files: 1149 `*Test.java` files and 1148 reports. Gate 072 had 1147 and 1146, so both
  counts rose by 2, the two new test classes. The difference of one file is unchanged and was not
  examined.
- JaCoCo, whole classes (informational; older code is included), the same figures as in gate 072,
  C.12.2 and C.13.2: `RegistrationMessageDeliveryAdapter` 100.0% instruction and 100.0% branch;
  `NoticeTemplateDefinition` 100.0% and 100.0%; `YamlNoticeTemplateRegistryAdapter` 99.4%
  instruction (2 missed) and 97.4% branch (1 missed), the defensive branch justified in A.2.

Gate B, application startup smoke test:

- `ngtpa-runtime-start apim-profiles` exited 0 after 46 s in the wrapper, with refused health polls
  before the first answer. The log has "Started NgtpaApiServerApplication in 6.83 seconds".
- Right after the start: `/actuator/health` HTTP 200 and UP, readiness HTTP 200 and UP. After a 20 s
  wait: health still HTTP 200 and UP and port 8888 listening.
- Application log (122 lines): 0 failure markers (bean creation, unsatisfied dependency, bean
  definition, binding, "failed to start", schema incompatibility), 0 ERROR lines, and 1 WARN line
  from `SecurityConfig` (in-process authentication DISABLED; the scan cut the line at 200
  characters; the same line as in C.15.2; not examined). 0 lines mention NoticeTemplate, so, as in
  II-1, the registry's startup is not visible individually. Inferred: a clean start is consistent
  with the startup validation of the template properties having passed.
- `ngtpa-runtime-stop` exited 0, port 8888 was released and the log has 2 graceful-shutdown lines.

Redaction: the first pass replaced the value of `SMS_SEND_SINGLE_PATH` (a request path of 25
characters) 21 times in the local Maven log, which is not in the zip. The rescan found 0 remaining.

#### C.17.4 Result

- Sub-stage II-2.e is DONE: tests for a missing registration-otp definition on START with the
  captcha gate off and on RESEND (951049f8, script 076, C.16), and on START with the captcha gate on
  (da136a8f, script 078).
- Sub-stage II-2.d is DONE: the stage gate is met on the final II-2 code commit da136a8f (script
  079).
- Stage II-2 (Registration) is DONE. Its code: be7294d5 (Template Service extension), ce2176e7
  (Registration adapter migration), 14c71b24 (rendering test), 951049f8 (START gate-off and RESEND
  tests) and da136a8f (START gate-on test).
- Registration takes its content and its unit word from `NoticeTemplateRegistryPort`. The old
  `auth-workflows.registration.otp.*` configuration stays until stage III-1.
- Behaviour pinned by II-2.e (decision D7, unchanged): with the definition missing and nothing sent
  in every case, RESEND and START with the captcha gate off surface the original
  `IllegalStateException` as an error signal (to the central handler, HTTP 500, inferred in C.16.5),
  and START with the captcha gate on, the production setting, returns a `SYSTEM_FAILED` result with
  `SYSTEM_UNAVAILABLE` after the captcha has been consumed.
- The next stage is II-3 (Login). The introduction of Appendix C (Status DRAFT, written on
  2026-10-05) predates the code of this stage; this section supersedes it.

#### C.17.5 Carried forward

1. II-3 (Login): the policy value is passed in seconds with the unit seconds today and must pass
   minutes (C.11.3). Before changing the send path, read the Login protected-context serializers in
   full (A.1) and plan the scheme-name plumbing from `TrpGetAccountListApimDataItem`.
2. II-4 (Forgot Password): the two `ttlUnit(language)` helpers are replaced by
   `definition.ttlUnit()` and the details OTP passes a literal 5 (C.11.3); stage III-3 then deletes
   the helpers.
3. The observation of C.14.4 about `possibleSideEffect()` stays outside this workstream and nothing
   was started. C.16.4 narrows it: it applies to RESEND and to START with the gate off, while START
   with the gate on converts the error into a result before the executor sees it. No test pins the
   key state.
4. Not checked: whether an external configuration overrides `pre-start-challenge` in production
   (C.16.4).
5. The two long lines of the 076 test file (lines 164 and 184) stay for the feature-level formatting
   pass; `mvn spotless:apply` is still deferred.
6. Open items 3 and 4 of A.2 (whether startup depends on the Config Service, and the Phase 04
   document) are unchanged.

### Appendix D: Stage II-3 (Login) Investigation and Draft Sub-Plan

Status: DRAFT. No code has been changed for Stage II-3. Recorded 2026-10-06 from the evidence of
scripts 081 and 082 (read-only; HEAD 3189b444; `src/` and `pom.xml` identical to da136a8f).
Statements are limited to what that evidence shows; inferences are marked; anything not shown is
listed under D.9. This appendix answers C.17.5 item 1, except for decision D8.

#### D.1 Current send path

- `LoginOtpSendService` (application layer, 176 lines) and `LoginOtpResendService` (176 lines) are
  the only classes that build a `MessageDeliveryCommand` for `MessageDeliveryType.LOGIN_OTP`. Each
  is wrapped by a shared-idempotency coordinator (`SharedLoginOtpSendService`,
  `SharedLoginOtpResendService`); the coordinators use
  `LoginFailureClassifiers.possibleSideEffect()` and do not read content.
- Send builds the command in `createDeliverBind` (lines 97-108): scenario LOGIN, the channel and
  destination of the resolved destination, the subject only for EMAIL, the body, no template code
  (null), the variables of `templateVariables` (lines 155-158) and the raw
  `command.acceptLanguage()`. The variables are `code`, `ttlValue` = `policy.ttlSeconds()` (300 by
  default, in seconds) and `ttlUnit` = the literal "seconds". Resend builds the same command inline
  in `deliver` (lines 107-126, variables at lines 118-124).
- The policy is a nine-component record `LoginOtpSendPolicy` (length, numericOnly, ttlSeconds,
  resendCooldownSeconds, maxVerifyAttempts, resendLimitEnabled, maxResendAttempts, messageTemplate,
  emailSubject). It has no e-mail body. Both services therefore send `policy.messageTemplate()`,
  which is the SMS template, as the body of an EMAIL too, with `policy.emailSubject()` as the
  subject (send lines 103-104; resend lines 113-116). The `email-template.body` of the old
  configuration is validated but is not read by the send path.
- The policy is built once at startup from `auth-workflows.login.otp.*` by the two application
  configs (`LoginOtpSendApplicationConfig` lines 44-54, `LoginOtpResendApplicationConfig` lines
  33-43) and passed to the constructors (lines 55-64 and line 44). Neither config has a
  `NoticeTemplateRegistryPort`. The registry bean exists (`NoticeTemplateServiceConfig` lines 32-33)
  and `UsernameRecoveryFeatureConfig` already injects it (lines 219-222).
- Order of effects: the protected context is loaded; the OTP challenge is created (send) or replaced
  (resend); only then is the command built and delivered. After a FAILED or UNKNOWN_OUTCOME delivery
  the challenge is invalidated (send lines 113-130); on success the session is bound. Resend
  delivers only when the challenge was replaced; a cooldown or a limit returns before delivery.
- Locale: `LoginController` passes the raw `Accept-Language` header (send line 124, resend line 169)
  to the commands. `SendLoginOtpCommand` trims it and turns a blank value into null;
  `ResendLoginOtpCommand` keeps what it is given, and the controller code after line 183 was not
  read. The pre-auth context stores no language. Today the locale reaches the delivery command but
  the content does not depend on it.
- Among the test files with Login in the path, none mentions `ttlUnit` or `ttlValue` (081
  inventory), and the only ones that mention `LOGIN_OTP` are `LoginOtpSendServiceTest` and three
  text guards (D.6 and D.7).

#### D.2 Finding: the trust code and the scheme name are not available when the OTP is sent

- The `login-otp` definition (`application.yml` lines 618-648) requires `{trustCode}` in the SMS (en
  and zh_HK) and in the English e-mail subject, `{schemeName}` in the English e-mail body and
  `{schemeNameChi}` in the zh_HK e-mail body, besides `{code}`, `{ttlValue}` and `{ttlUnit}`.
  `forgot-password-otp` (lines 649-678) is identical by decision (solution design section 5). The
  Login send path supplies none of the three. Rendering throws on a variable that the template needs
  and the command does not carry (C.1), so, inferred from C.1, the migrated Login OTP would end as a
  failed delivery for every member unless D8 is settled.
- What the send path has: `LoginPreAuthContext` (normalized username, role, destinations by id,
  verification time, workflow version) and `LoginPreAuthDestination` (id, channel, destination,
  masked destination). The start operation builds it from the credential result
  (`LoginStartProtectedOperation` line 79), and `TrpMemberLoginAdapter` implements that credential
  port: `TrpMemberLoginApimDataItem` has `success`, `login-user-role`, `fail-count` and an `otp`
  list of channel and destination, and `TrpMemberLoginContractMapper.toAuthenticated` (lines 26-59)
  uses only those. The item is annotated `@JsonIgnoreProperties(ignoreUnknown = true)`, so other
  APIM fields would be ignored; none is known.
- Where trust data exists: the `TRPGetAccountList` item carries the trust code, the scheme type and,
  since stage I-3, `sch-name` and `chin-sch-name` (`TrpGetAccountListApimDataItem` lines 6-15). It
  is called from exactly one place in `src/main`: `LoginOtpVerifyService.continueToAccounts` (lines
  312-372), after the OTP has been verified and consumed (lines 241-262). The request is built from
  the normalized username only (`GetLoginAccountsUseCase` lines 10 and 17-26). The account list
  follows the OTP (the account-selection projection `LoginOtpAccountResponse` is returned after
  verification) and the verify result carries a list, so a member can hold several accounts.
- The plumbing from stage I-3 is only half there: `TrpGetAccountListContractMapper.toRecord` (lines
  14-23) maps seven fields and drops `schemeName` and `schemeNameChi`; `LoginAccountRecord`,
  `LoginValidatedAccount` and `LoginSessionAccount` have no scheme-name component;
  `LoginOtpAccountResponse` has the trust code but no scheme name. The plan row of II-3
  ("scheme-name plumbing from `TrpGetAccountListApimDataItem`") does not say when the names are
  needed; at the send they are not available.
- Not known: whether the BRD defines Login OTP content that applies to a member before an account or
  trust is chosen. The Login and Forgot Password content was taken as one (stage I-5 and solution
  design section 5); the Forgot Password lookup returns trust data per candidate (stage I-3), the
  Login credential step does not. The question for Nick is in D.4, decision D8.

#### D.3 Serializer and session (A.1 carry-over)

- `AesGcmLoginPreAuthContextProtector` serializes a private record `ProtectedPayload`
  (normalizedUsername, userRole, destinationsById, credentialVerifiedAt as an ISO string,
  workflowVersion) with explicit `from` and `toDomain` methods (lines 103-128). A field added to
  `LoginPreAuthContext` is therefore dropped on the round trip unless it is added to
  `ProtectedPayload` by hand, the same finding as for Username Recovery (A.1). The record
  `LoginPreAuthDestination` is serialized as it is by the injected `ObjectMapper`
  (`LoginPreAuthFoundationConfig` lines 24-30); that mapper's configuration was not read.
- The context is created once at START (`LoginStartProtectedOperation.establish` lines 79-85:
  `contexts.create`, `protector.protect`, four `loginContext.*` session attributes) and decrypted at
  SEND, RESEND and VERIFY (`DefaultLoginProtectedAuthFlowContextLoader` lines 64-72). For RESEND the
  selected destination is found by its masked value (lines 74-82).
- `AesGcmLoginStartReplayProtector` serializes only the authFlowSessionId, the CSRF token, the
  expiry and the destination choices (lines 75-107), and the send and resend replay snapshots carry
  only the status, the masked destination and seconds. Neither holds the context or message content,
  so a template change does not touch them.
- Inferred, not read: pre-auth sessions are short-lived, so a protected payload written before a
  deployment is not expected to be read after it; the shared Login idempotency migration has not
  been deployed to any environment yet (Nick, 2026-09-05).

#### D.4 Decisions

| # | Topic | Position or options | Status |
|---|---|---|---|
| D8 | Trust code and scheme name for the Login OTP content | A: call `TRPGetAccountList` at Login START after `TRPMemberLogin` succeeds, keep the trust data in the protected pre-auth context and render it; needs a rule for a member with several trusts, adds an APIM call and a failure mode before any OTP is sent, and changes `LoginPreAuthContext`, `ProtectedPayload`, `LoginPreAuthContextFactory` and `LoginStartProtectedOperation`. B: Login OTP content without trust code and scheme name (BRD sign-off needed): changes the `login-otp` definition in en and zh_HK, which then differs from `forgot-password-otp`, so the rule that the two stay identical must change too. C: call `TRPGetAccountList` at every SEND and RESEND: the same ambiguity as A and one more APIM call per message. D: extend the APIM `TRPMemberLogin` response with the trust data (the DTO ignores unknown properties, so what the real response carries is not known; the ORSO team owns APIM and TAS). | RESOLVED (option D, corrected model, 2026-10-06; see D.11.1) |
| D9 | The unit | Pass `ttlValue` in minutes, `max(1, policy.ttlSeconds() / 60)`, and `ttlUnit` from `definition.ttlUnit()`, as D6 does for Registration. The source of the seconds stays `policy.ttlSeconds()`. This changes what Login sends today (C.11.3). | CONFIRMED (Nick, 2026-10-06; see D.11.2) |
| D10 | Where the registry is resolved and what a missing definition does | Resolve before the challenge is created or replaced (the channel and the locale are known there), so that a missing definition has no side effect: no challenge, no cooldown, no binding. A missing definition fails with `IllegalStateException`, no fallback (as D7). Resolving inside the delivery lambda, as Registration does, would leave an un-invalidated challenge on send and a replaced challenge on resend (inferred: with its cooldown running). The error signal passes the shared executor (key marked unknown, C.14.3) and, inferred, ends at the central handler as HTTP 500; the controller catch blocks were not read. | CONFIRMED (Nick, 2026-10-06; see D.11.3) |
| D11 | The old Login configuration | Keep `LoginOtpSendPolicy` with its nine components and keep `validateLoginPolicy` until stage III-1; the two services stop reading `messageTemplate()` and `emailSubject()`. Unlike Registration (C.10.1), the Login templates have a live check (`validateLoginPolicy` lines 336-346 require `{code}` in the SMS template and a valid e-mail template), so III-1 must remove it together with the YAML. | CONFIRMED (Nick, 2026-10-06; see D.11.4) |

#### D.5 Behaviour changes to record if D9 to D11 are confirmed

- The SMS and e-mail wording becomes the BRD content of `login-otp` (en and zh_HK), replacing "Your
  BCT login verification code is {code}. It is valid for {ttlValue} {ttlUnit}." (`application.yml`
  lines 199-202).
- The unit changes from seconds to minutes: "300 seconds" becomes "5 minutes" (D9).
- The e-mail body changes from the SMS template to the registry e-mail body (D.1).
- The content follows the locale of the send or resend request: en for a null, blank or unsupported
  tag, zh_HK for the Chinese tags, as for Registration (design section 8.4).
- A missing definition fails instead of silently using the old configuration (D10).
- The values of `{trustCode}`, `{schemeName}` and `{schemeNameChi}` follow D8.

#### D.6 Existing guards that constrain the code

- `Task0043LoginOtpSendOrchestrationArchitectureTest` reads `LoginOtpSendService.java` as text. The
  source must contain `implements SendLoginOtpUseCase`, `OtpChallengePort`, `MessageDeliveryPort`,
  `OtpScenario.LOGIN`, `MessageDeliveryScenario.LOGIN`, `MessageDeliveryType.LOGIN_OTP`,
  `challenge.rawCode()` and `templateVariables` (lines 16-23 and 40), and must not contain "Redis",
  "WebClient", "org.springframework", "SecureRandom", "LoginOtpPort", "LoginMessagePort", "log.",
  "System.out", "rawCode=<" or "providerMessageId" (lines 24-31 and 41). The text "log." also
  matches inside other words, so no comment or identifier may contain it.
- `Task0043LoginOtpResendArchitectureTest` requires `replaceChallenge`, `MessageDeliveryPort` and
  `MessageDeliveryType.LOGIN_OTP` in `LoginOtpResendService.java` and forbids "Redis", "WebClient"
  and "org.springframework" (lines 15-17).

#### D.7 Test and coverage baseline

- `LoginOtpSendServiceTest` (221 lines, 3 tests) builds the service in one helper (lines 164-191)
  with the nine-component policy; `LoginOtpResendServiceTest` (161 lines, 3 tests) does the same
  (lines 130-142). Both constructors gain a registry parameter, so each helper changes and each test
  needs a registry fake. Their only content assertion is that `templateVariables` contains `code`
  (send line 86, resend line 71); the send test also pins the order load, create, deliver, bind
  (line 78).
- `LoginOtpSendSharedRuntimeCompositionTest` (lines 71-96) and
  `LoginOtpResendSharedRuntimeCompositionTest` (lines 64-89) load the application configs with mocks
  and no `NoticeTemplateRegistryPort` bean. Inferred: once the config methods take the registry,
  each needs a bean for it. `ProductionComposedLoginControllerBoundaryTest` and
  `LoginStartSharedRuntimeCompositionTest` set Login OTP templates (lines 252-253 and 129-130) and
  may load the same configs; they were not read.
- The coverage of the two services was not collected. The tier for use cases and adapters with
  orchestration applies (95% instruction gated, 85-90% branch, each uncovered branch justified).

#### D.8 Draft sub-plan

| Sub-stage | Change | Status |
|---|---|---|
| II-3.0 | Settle D8 to D11; write the solution-design section for the Login content and update this appendix before any code. | DONE (D8 to D11 resolved 2026-10-06; see D.11) |
| II-3.a | Read-only investigation of the send path, the context serializers and the trust source. | DONE (scripts 081 and 082; findings in D.1 to D.7) |
| II-3.a2 | Read-only: the send and resend endpoints and their error handling in `LoginController`, the context tests that load the two configs, and, if D8 is A or C, the callers of `LoginPreAuthContext`. | DONE (script 084; findings in D.10) |
| II-3.b0 | Add `trustCode`/`schemeName`/`schemeNameChi` to `TrpMemberLoginApimDataItem` (modeled ahead of the downstream TAS/APIM change) and remove them from `TrpGetAccountListApimDataItem`; carry the three fields through `CredentialAuthenticationResult`, `LoginPreAuthContext` and `ProtectedPayload` to the send and resend services, with a round-trip test. | DONE (script 093, commit 0a30f4e0) |
| II-3.b | `LoginOtpSendService` and `LoginOtpResendService` resolve `LOGIN_OTP` through `NoticeTemplateRegistryPort` (D9, D10), the two configs inject the port, the two unit tests and the two composition tests are adjusted. | DONE (script 095, commit 665650fe) |
| II-3.c | Rendering test with the real registry and renderer for SMS and EMAIL in en and zh_HK (the minutes, the unit, no placeholder left over, the content of D8). | DONE (script 108, commit 5eafe73d; combined with II-3.d) |
| II-3.d | Tests for a missing definition on send and on resend: the error reaches the caller, nothing is created or sent. | DONE (script 108, commit 5eafe73d; combined with II-3.c) |
| II-3.e | Stage gate on the final code: full `mvn test` and an application startup smoke test; then the plan is updated. | DONE (script 109; see D.13) |

#### D.9 Open items

1. D8 needs an answer from Nick (BRD): which trust code and scheme name, if any, the Login OTP
   message carries when the member's accounts are not known yet.
2. The send and resend endpoints of `LoginController` (try at lines 136 and 179) were printed only
   up to the call; their catch blocks and the central handler path for an error signal are not read.
3. Which context tests load `LoginOtpSendApplicationConfig` and `LoginOtpResendApplicationConfig` is
   not established (D.7).
4. The configuration of the `ObjectMapper` injected into `AesGcmLoginPreAuthContextProtector` is not
   read; it matters only if a field is added to `LoginPreAuthDestination`.
5. The delivery layer was not read for a null template code: Login passes null, Registration passes
   "registration-otp". No change is proposed.
6. The coverage baseline of the two services is not collected (D.7).
7. The VERIFY path builds no message and is outside this stage.

#### D.10 Findings of script 084 (read-only; HEAD c0e98653; src and pom.xml as at da136a8f)

Recorded 2026-10-06 from the evidence of script 084. Statements are limited to what that evidence
shows; inferences are marked. This section closes items 2 and 3 of D.9, narrows items 4, 5 and 6,
corrects one statement each in D.4 and D.7, and adds the evidence for decision D8 (D.10.9). D8 stays
OPEN; nothing is decided here.

##### D.10.1 What the member sees when a send or a resend fails (closes D.9 item 2)

- `LoginController.send` (lines 115-159) checks the cookie, the CSRF token, the idempotency key, the
  request id and the destination id before its `try` (lines 129-135). In the `try` it builds the
  command, calls `sendLoginOtpUseCase.send` and maps the result with
  `loginOtpSendMapper::toMutationResponse` and `LoginHttpResponses::toResponse` (lines 137-149). It
  ends the chain with `onErrorResume` (lines 150-155), which answers every error signal with
  `loginOtpSendMapper.missing(SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value(), "PAGE")`. The
  `catch (RuntimeException)` (lines 156-158) returns
  `sendMissing(LoginErrorKey.REQUEST_INVALID.value(), "PAGE")` and covers only exceptions thrown
  while the call is assembled.
- `LoginOtpSendWebMapper.missing` builds a failure with `ApiStatus.VALIDATION_FAILED`, the next step
  `SELECT_OTP_DESTINATION` and one `PAGE` error from the BFF (081 evidence, lines 51-57).
  `LoginHttpResponses.status` (lines 15-36) answers 400 for a code that is not in its list when the
  status is neither `BUSINESS_REJECTED` nor `SYSTEM_ERROR`. Read from the printed code, not run: an
  error signal from the send use case, for example a missing definition, reaches the member as HTTP
  400 with the error `SYSTEM_UNAVAILABLE`, unless that code equals one of the listed `LoginErrorKey`
  values (not read; script 085 prints `LoginErrorKey` and `SharedWorkflowErrorKey`). It does not
  reach the central handler.
- `LoginController.resend` (lines 161-209) has the same checks (lines 174-178) and a `try` (line
  179) that maps the result and adds a `Retry-After` header when the retry value is positive (lines
  192-205). It has no `onErrorResume`; its `catch` (lines 206-208) returns
  `resendMissing(LoginErrorKey.REQUEST_INVALID.value(), "PAGE")` for assembly exceptions only. An
  error signal from the resend use case therefore leaves the controller (inferred: it ends at the
  central handler, as in C.16.5; the registration of that handler was not read).
- The remaining error lines of the controller are START's own `onErrorResume` (line 73) and VERIFY's
  `doOnError` (line 262), both outside this stage. `clean(...)` is applied to `Accept-Language` in
  send, resend and verify (lines 146, 188 and 242); its body was not read.
- Correction to D.4, D10: its last sentence ("ends at the central handler as HTTP 500") holds for
  resend only. For send, a missing definition gives HTTP 400 `SYSTEM_UNAVAILABLE`, the existing
  answer to any send error. This stage does not change it.

##### D.10.2 The delivery layer (narrows D.9 item 5)

- `MessageDeliveryCommand` (lines 7-17) has the components message type, scenario, channel,
  recipient, subject, body, template code, variables, locale and correlation id.
  `MessageDeliveryAdapter` never reads the template code. The only reader in `src/main` besides the
  record is `MessageDeliveryRuntimeConfig` (line 68), which was not printed; script 085 prints it
  and the Registration adapter. D.9 item 5 says that Registration passes "registration-otp"; no
  evidence of scripts 081 to 084 shows that.
- `MessageDeliveryAdapter.deliver` validates the command (lines 116-138): type, scenario, channel,
  recipient and body must be present, and an EMAIL needs a subject. It renders the subject and the
  body inside the `try` that starts at line 48, so a `RuntimeException` from rendering is caught at
  line 62 and becomes a FAILED result with `MESSAGE_DELIVERY_REJECTED` (lines 62-64 and 102-105).
  This confirms D.2: a placeholder without a variable (the rendering failure of C.1) does not
  surface as an exception; it ends as a FAILED delivery. `LoginOtpSendService` then invalidates the
  new challenge and returns `DELIVERY_FAILED` (081 evidence, lines 122-130); the send mapper turns
  that into `SYSTEM_ERROR` and `LoginHttpResponses` into HTTP 502 (read from the code, not run).
- Consequence for D10: the subject and the body templates are already in the command when it reaches
  the adapter, so the lookup in the registry has to happen before the command is built.

##### D.10.3 Where the context is built and used (supports II-3.b0)

- `LoginStartProtectedOperation.establish` is the only place that creates the context
  (`contexts.create`, line 79); `AesGcmLoginPreAuthContextProtector.toDomain` rebuilds it (line
  121). `new LoginPreAuthContext(` appears twice in `src/main` and 15 times in 10 test files;
  `new LoginPreAuthDestination(` once in `src/main` (`LoginPreAuthContextFactory` line 37) and 11
  times in 5 test files; `new CredentialAuthenticationResult(` twice in `src/main`
  (`TrpMemberLoginContractMapper` lines 52 and 70) and 6 times in 3 test files. Records are
  constructed positionally, so a new component needs either a compatibility constructor, as stage
  I-3 added for the APIM DTOs and the Username Recovery candidate, or a change at every call.
- `AesGcmLoginPreAuthContextProtectorTest` (78 lines, 2 tests) asserts the round trip with
  `isEqualTo(context)` (line 36) using `new ObjectMapper().findAndRegisterModules()` (line 20), but
  its contexts have no new field. A `ProtectedPayload` that forgets a new field is caught only if
  the test sets it, so the test must be extended with the change.
- `LoginStartProtectedOperation` takes 10 constructor arguments (lines 39-49) and is built by
  `LoginStartProtectedOperationTest` (262 lines, 7 tests). `LoginStartSharedRuntimeCompositionTest`
  (lines 78-83) and `ProductionComposedLoginControllerBoundaryTest` (lines 201-206) provide mocks of
  `LoginPreAuthContextFactory`, `CredentialAuthenticationPort` and
  `LoginPreAuthContextProtectorPort`, so a new port on that path would need a bean in both
  (inferred).

##### D.10.4 The credential result and `TRPMemberLogin` (D8, option D)

- `CredentialAuthenticationResult` has six components: status, normalized username, role, fail
  count, destinations and downstream code (lines 6-12). `LoginCredentialDestination` has a channel
  and a destination. Neither carries trust or scheme data. `TrpMemberLoginApimDataItem` is built
  with four arguments (success, role, fail count, OTP list) in `TrpMemberLoginContractMapperTest`
  (lines 36-42, among others) and `TrpMemberLoginAdapterTest` (lines 19, 46 and 60).
- No sample or contract file of the real `TRPMemberLogin` response exists under
  `src/test/resources`; the only contract files there are two registration CSVs. The master design
  `docs/design/4-login/login-master-design.md` says at line 64 that Progress returns one result with
  `cust-type`, an optional `user-role`, `fail-count` and destination entries (the script cut the
  line there). The DTO uses `login-user-role` and has no `cust-type`, so the design and the code
  already differ in field names. Whether the real response carries a customer type or any trust data
  is not established.
- The Login design documents were listed but not printed (for example `login-apim-contract.md`,
  3,592 bytes, and `login-decisions-open-questions.md`, 2,929 bytes). Script 085 prints them.

##### D.10.5 What the design documents say about the trust data and the call order (D8)

- Solution design section 3 (lines 40-48) records the BRD inventory: E0002 / S0002
  "Login_ForgotPassword" has the dynamic fields `<trust code>` and `<Scheme Name>` (email only), and
  the BRD treats Login and Forgot Password as one notice, differentiated only by trust code. Section
  8.2 (lines 349-353) turns that into `{trustCode}` in the SMS and in the English e-mail subject and
  `{schemeName}` or `{schemeNameChi}` in the e-mail body, for `LOGIN_OTP` and `FORGOT_PASSWORD_OTP`
  alike. Lines 355-357 say that the scheme names come from `sch-name` and `chin-sch-name` of the
  `TRPGetAccountList` and `TRPSearchMember` responses, and the table at line 366 lists
  `TrpGetAccountListApimDataItem` as the Login DTO. The design therefore names `TRPGetAccountList`
  as the Login source of the scheme name. It does not say when the call happens, and the printed
  lines name no source for the Login trust code (the account-list item carries one).
- The documented Login sequence puts `TRPGetAccountList` after the OTP: `login-master-design.md`
  line 69 ("On OTP success, Java calls TRPGetAccountList using values from protected pre-auth
  context only") and line 25; the APIM guide
  `apim-exchange-executor-architecture-and-developer-guide.md` lines 945 and 993 (invoked "before
  the authenticated session and selected Account-Ref exist");
  `authenticated-context-apim-profile-implementation-plan.md` line 975 ("after successful
  authentication"). The request needs only the normalized username and the role
  (`GetLoginAccountsUseCase` lines 17-26), which the context holds from START, so a call before the
  OTP is technically possible. Whether TAS accepts it there, and returns the same list, is not
  shown.
- No file with "brd" in its name exists under `docs` or `src/test/resources`; section 3 is the only
  record of the BRD in the repository. `login-decisions-open-questions.md` was not printed.

##### D.10.6 The tests that load the two configs (closes D.9 item 3)

- Four test files mention `LoginOtpSendApplicationConfig` or `LoginOtpResendApplicationConfig`:
  `LoginOtpSendSharedRuntimeCompositionTest` and `LoginOtpResendSharedRuntimeCompositionTest`, which
  load the config, and `Task0043LoginOtpSendEndpointArchitectureTest` and
  `Task0043LoginOtpResendEndpointArchitectureTest` (one mention each; not read).
- `ProductionComposedLoginControllerBoundaryTest` (lines 184-221) and
  `LoginStartSharedRuntimeCompositionTest` (lines 61-98) load `LoginSharedIdempotencyConfig` and
  `LoginApplicationConfig` only. None of their 17 matching lines names the two configs,
  `MessageDeliveryPort` or `NoticeTemplateRegistryPort`. The doubt of D.7 about them is therefore
  resolved: the II-3.b change does not require a registry bean there. `LoginApplicationConfig`
  itself (96 lines) was not read.
- No `@Import` or `@ComponentScan` in `src/main/java` names the two configs, so the application
  picks them up by component scanning (inferred).

##### D.10.7 The ObjectMapper (narrows D.9 item 4)

- `src/main` has one `ObjectMapper` bean method, `JacksonConfig.objectMapper()`
  (`infrastructure/jackson/JacksonConfig.java`, line 13); the other mappers in `src/main` are
  `new ObjectMapper()` fields of codecs and protectors. `LoginPreAuthFoundationConfig` injects
  `ObjectMapper mapper` without a qualifier (082 evidence, lines 24-30), so it receives that bean
  (inferred). `src/main/resources` has no `spring.jackson` setting. The body of `JacksonConfig` was
  not printed. The protector test builds its own mapper and does not use the bean.

##### D.10.8 Coverage baseline (narrows D.9 item 6)

- Script 084 took the first JaCoCo CSV that `find` returned:
  `target/evidence/20261002-130616-009-full-suite-coverage/jacoco-report/jacoco.csv`, modified
  2026-10-02 13:09:46. Its header said that the last full run was script 079; that wording was
  wrong, the file predates the II-2 work. The send and resend services, their two coordinators, the
  context loader, the protector, the destination resolver and `LoginStartProtectedOperation` were
  last modified between 2026-09-06 and 2026-09-14, earlier than the CSV, so for them the figures
  describe the current source. `LoginPreAuthContextFactory` was modified on 2026-10-06 and the date
  of `LoginController` was not printed. Script 085 prints the CSV of the 079 run for comparison.
- Figures, as instruction covered/missed, instruction percentage, branch covered/missed, branch
  percentage: `LoginOtpSendService` 288/48, 85.7%, 11/7, 61.1%; `LoginOtpResendService` 271/54,
  83.4%, 11/12, 47.8%; `SharedLoginOtpSendService` 142/5, 96.6%, 8/3, 72.7%;
  `SharedLoginOtpResendService` 140/5, 96.6%, 8/3, 72.7%;
  `DefaultLoginProtectedAuthFlowContextLoader` 225/74, 75.3%, 22/22, 50.0%;
  `AesGcmLoginPreAuthContextProtector` 182/30, 85.8%, 7/5, 58.3%; `LoginOtpDestinationResolver`
  60/10, 85.7%, 5/4, 55.6%; `LoginPreAuthContextFactory` 109/5, 95.6%, 11/1, 91.7%;
  `LoginStartProtectedOperation` 254/0, 100.0%, 7/0, 100.0%; `LoginController` 269/353, 43.2%,
  32/44, 42.1%.
- D.7 states that the tier of 95% instruction and 85-90% branch applies to the two services. This
  plan has no coverage section of its own; that tier is the one recorded for the Fund Price and the
  centralized masking workstreams, so for II-3 it is an assumption. Both services are below it
  today. Whether II-3.b must lift them, or only cover its own changes (the legacy gap belonging to
  the code-coverage-95 workstream), is open for Nick.

##### D.10.9 Evidence for D8 by option (D8 stays OPEN)

| Option | What the repository supports | What it argues against | What it touches |
|---|---|---|---|
| A: call `TRPGetAccountList` at START and keep the result in the context | The BRD inventory (section 3) puts trust code and scheme name into the Login message; section 8.2 names `TRPGetAccountList` as the Login source of the scheme name; the request needs only values that the context holds at START (D.10.5) | `login-master-design.md` line 69 and the APIM guides put the call after the OTP; it adds an APIM call and a failure mode before any OTP is sent; nothing printed says which trust a member with several trusts is shown | `LoginPreAuthContext` (15 test constructions), `ProtectedPayload`, `LoginPreAuthContextFactory`, `LoginStartProtectedOperation` (10 arguments, 7 tests), two composition tests that mock the start ports (D.10.3) |
| B: Login content without trust code and scheme name | The smallest code change | It contradicts the BRD inventory of section 3 as recorded; it ends the rule that `login-otp` and `forgot-password-otp` stay identical (section 5), so that rule and the BRD would have to change | The `login-otp` definition (en and zh_HK), the plan; needs BRD sign-off |
| C: call `TRPGetAccountList` at every send and resend | As A | As A, and one APIM call per message, inside the idempotent operation whose failures are classified as possible side effects (D.10.1) | The constructors, configs and tests of `LoginOtpSendService` and `LoginOtpResendService` |
| D: add the trust data to the `TRPMemberLogin` response | No second call and no change of the documented sequence | The real response is not documented in the repository and the master design names fields that the DTO does not have (D.10.4); it needs a change by the owners of APIM and TAS | `TrpMemberLoginApimDataItem` (4-argument uses in tests), `CredentialAuthenticationResult` (6 constructions), the context (D.10.3) |

- Common to all options: the printed documents do not say which trust a member with several trusts
  is shown. Option B is the only one that the BRD inventory as recorded contradicts.

##### D.10.10 Open items after script 084

1. D8, for Nick (BRD): which trust code and scheme name the Login OTP message names, and in
   particular for a member with several trusts. No document in the repository answers it.
2. Printed by script 085 and recorded in a later docs commit: the Login and Forgot Password design
   documents, `LoginErrorKey` and `SharedWorkflowErrorKey` (whether `SYSTEM_UNAVAILABLE` is in the
   status list of D.10.1), `JacksonConfig`, `MessageDeliveryRuntimeConfig` and the Registration
   adapter (D.10.2), `LoginApplicationConfig`, the two endpoint architecture tests, the end of
   `LoginController`, the JaCoCo CSV of the 079 run (D.10.8) and the sections of the solution design
   that bear on D8.
3. The coverage expectation for II-3.b (D.10.8).
4. D.9 items 2 and 3 are closed; items 4, 5 and 6 are narrowed; items 1 and 7 are unchanged.

### D.11 Resolution of D8 to D11 and the coverage scope (Nick, 2026-10-06)

Recorded after the repository was rebased onto `origin/dev` (new HEAD starts `c7c50871`). Every
commit hash recorded earlier in this plan, including `da136a8f` and the docs commits through
`0662dc62`, was rewritten by the rebase and no longer resolves in this repository; see D.11.6.

#### D.11.1 D8 resolved: corrected model, TRPMemberLogin carries the trust and scheme data

- Correction to D.10.9: stage I-3 added `sch-name`/`chin-sch-name` to
  `TrpGetAccountListApimDataItem` for Login. Nick confirms that was the wrong DTO: the trust code
  and the two scheme names belong on `TRPMemberLogin`, which this stage's own finding (D.10.5) shows
  is called at START, before the OTP, unlike `TRPGetAccountList`, which the design and the code both
  call only after OTP verification (D.10.5, D.10.1).
- This is option D of D.10.9, modeled ahead of the downstream change: `TrpMemberLoginApimDataItem`
  gains three components, `@JsonProperty("trust-code") String trustCode`,
  `@JsonProperty("sch-name") String schemeName` and
  `@JsonProperty("chin-sch-name") String schemeNameChi`, each `null` until TAS ships them in the
  real response; `@JsonIgnoreProperties(ignoreUnknown = true)` already on the class is unaffected
  either way. A compatibility constructor preserves the existing four-argument call sites (seven
  found in 084 evidence), in the same shape as the compatibility constructors stage I-3 added to the
  other three DTOs.
- `TrpGetAccountListApimDataItem` reverts to its original seven components: `schemeName`,
  `schemeNameChi` and the compatibility constructor that bridged seven arguments to nine are
  removed. This is safe because `TrpGetAccountListContractMapper.toRecord` never read the two fields
  (D.2), so no mapping code changes.
- Not modeled here (the next code script, II-3.b0): plumbing
  `trustCode`/`schemeName`/`schemeNameChi` from `TrpMemberLoginContractMapper.toAuthenticated`
  through `CredentialAuthenticationResult`, `LoginPreAuthContextFactory`, `LoginPreAuthContext` and
  `AesGcmLoginPreAuthContextProtector.ProtectedPayload` (D.10.3) down to `LoginOtpSendService` and
  `LoginOtpResendService`.
- Downstream dependency, unchanged by this correction: the real `TRPMemberLogin` response does not
  carry these fields yet; TAS and the owners of APIM add them later (D.10.4). Until then the three
  fields resolve to `null` and the template variables they feed are not populated; D.11.2 to D.11.4
  do not change that.

#### D.11.2 D9 confirmed: seconds in configuration, minutes on the wire

- Nick confirms the D9 proposal as written: `auth-workflows.login.otp.ttl-seconds` stays in seconds;
  `ttlValue` is converted to minutes at the point of use with
  `Math.max(1, policy.ttlSeconds() / 60)`, the same expression `RegistrationMessageDeliveryAdapter`
  already uses (line 56, D.10.2); `ttlUnit` comes from `definition.ttlUnit()`.

#### D.11.3 D10 confirmed: why a missing definition is guarded against

- Nick asked why a missing definition would occur. Today it would not: `application.yml` already
  defines `login-otp` for both channels and both locales (D.5). The check is defensive for the same
  reason Registration has one (decision D7): it guards against an operator dropping a locale or
  channel block in a future YAML edit, a deployment where the code ships before the YAML does, or a
  later channel (solution design section 7.3) whose content entry is forgotten. Nick confirms the
  D10 design as proposed: resolve the registry before the challenge is created or replaced, so a
  missing definition has no side effect, and fail with `IllegalStateException` and no fallback.

#### D.11.4 D11 confirmed: LoginOtpSendPolicy's content fields are removed at stage III-1

- Nick asked when. The answer is stage III-1 (Phase III: Cleanup), the same stage that removes
  Registration's old `auth-workflows.registration.otp.*` configuration (C.17.4). Until then
  `LoginOtpSendPolicy` stays; II-3.b only stops reading `messageTemplate()` and `emailSubject()`
  from it. At III-1 those two components and the two `validateLoginPolicy` checks that validate them
  (lines 340-346, D.10) are removed together with the `auth-workflows.login.otp.sms-template` and
  `email-template.*` YAML keys. The other seven components of the policy (length, numericOnly,
  ttlSeconds, resendCooldownSeconds, maxVerifyAttempts, resendLimitEnabled, maxResendAttempts) are
  not content and are not touched by this workstream.

#### D.11.5 Coverage confirmed: new code only

- Nick confirms II-3.b covers only the lines it changes or adds; it does not have to lift
  `LoginOtpSendService` (85.7% instruction, 61.1% branch) or `LoginOtpResendService` (83.4%
  instruction, 47.8% branch, D.10.8) to the 95%/85-90% tier. That pre-existing gap stays with the
  separate `code-coverage-95` workstream (branch `feature/increase-code-coverage-95`), not this one.

#### D.11.6 Rebase note, read before the next script

- `git rebase origin/dev` rewrites the hash of every commit on this branch, even commits whose
  content does not change, and may carry unrelated upstream changes into files outside this
  workstream. This script therefore verified the plan and the II-2 code by content (step 03), not by
  diffing the whole of `src/` and `pom.xml` against a single old commit hash, and it records no new
  source pin.
- The next code script (II-3.b0) must establish a fresh source pin from the current `HEAD` of this
  branch before making any change, and must not reuse `da136a8f` or any other pre-rebase hash.

#### D.11.7 Updated status of the II-3 sub-plan

- II-3.0 is DONE (D.8 table, this section).
- II-3.a and II-3.a2 remain DONE (scripts 081, 082 and 084).
- II-3.b0 is PENDING and is now unconditional (D8 is resolved as option D); its row in the D.8 table
  is updated to name the two DTOs and the propagation path.
- II-3.b, II-3.c, II-3.d and II-3.e remain PENDING.

### D.12 Closure of II-3.b0 and II-3.b (2026-10-07)

D.11.7's closing bullet ("II-3.b0 is PENDING... II-3.b... remain PENDING") is superseded by this
section; it is left in place above as a historical record of the state right after D8 to D11 were
resolved, rather than edited in place.

- II-3.b0 is DONE: script 093 committed `0a30f4e0`. `TrpMemberLoginApimDataItem` gained
  `trustCode`/`schemeName`/`schemeNameChi` (modeled now, null until TAS/APIM ship the real fields);
  `TrpGetAccountListApimDataItem` reverted to its original 7 components; the three fields are
  carried through `CredentialAuthenticationResult`, `LoginPreAuthContextFactory`,
  `LoginPreAuthContext` and the AES-GCM protector round trip; the obsolete
  `TrpGetAccountListApimDataItemTest` was deleted (script 089 confirmed it was scaffolding from the
  same mistaken stage-I-3 commit); `Task0043CredentialAuthenticationContractTest`'s
  exact-component-list assertion was extended by the 3 new names (scripts 091 and 092 confirmed no
  other guard needed any change).
- II-3.b is DONE: script 095 committed `665650fe`. `LoginOtpSendService` and `LoginOtpResendService`
  resolve `MessageDeliveryType.LOGIN_OTP` through `NoticeTemplateRegistryPort` before the OTP
  challenge is created (send) or replaced (resend) (D10); `ttlValue` is in minutes via
  `Math.max(1, policy.ttlSeconds() / 60)` with `ttlUnit` from the resolved definition (D9);
  `policy.messageTemplate()`/`emailSubject()` are no longer read by either service (D11,
  mechanically re-verified by the script itself before compiling); the two application configs
  inject the port; the two unit tests and the two composition tests are adjusted; all four
  `Task0043Login*` text-guard tests pass unedited (confirmed in full by script 094 before any code
  was written, and re-confirmed mechanically by script 095 after patching).
- Remaining: II-3.c (a rendering test with the real registry and renderer for SMS and EMAIL in en
  and zh_HK), II-3.d (tests for a missing definition on send and on resend reaching the caller, at
  the level above the unit tests script 095 already added), and II-3.e (the stage gate: full
  `mvn test` and an application startup smoke test, then this plan is updated).

### D.13 Closure of II-3.c, II-3.d and II-3.e: Stage II-3 (Login) is DONE (2026-10-08)

- II-3.c and II-3.d are DONE, combined into one test file per Nick's instruction: script 108
  committed `5eafe73d`, adding `LoginOtpRenderedContentIntegrationTest.java`. It exercises the real
  `application.yml`, the real `YamlNoticeTemplateRegistryAdapter`, the real
  `LoginOtpSendService`/`LoginOtpResendService` and the real `MessageDeliveryAdapter` with its
  default renderer; only the OTP challenge port, the session binding port, the protected-context
  loader and the two senders are stubbed. Its centerpiece test uses the exact field values from a
  real `TRPMemberLogin` response Nick provided (trust-code `JPM`, sch-name `JP MORGAN CHASE - JMCI`,
  chin-sch-name blank), confirming end to end, through the real YAML, that the `chin-sch-name`
  fallback (script 102) closes the real gap it was built for. A second test confirms a populated
  `chin-sch-name` is rendered as-is and never overridden by the fallback. Further tests cover the
  Chinese unit placement directly after the number, the whole-minutes-with-a-minimum-of-one rule,
  `zh_HK` and `en` content genuinely differing, and II-3.d: a missing registry definition fails send
  and resend before any challenge is created or replaced.
- Two fixes landed before II-3.c/d could pass. Script 100 (commit `622c085c`) threads
  `trustCode`/`schemeName`/`schemeNameChi` from `LoginPreAuthContext` into the rendered template
  variables in both services, which script 095 had wired the registry resolution for but never
  actually populated. Script 102 (commit `76fba151`) adds the same "blank `chin-sch-name` falls back
  to `sch-name`" fallback Username Recovery already has, after Nick found a real `TRPMemberLogin`
  response with a blank `chin-sch-name` and a populated `sch-name`.
- II-3.e is DONE: the stage gate (script 109) on the final code commit `5eafe73d` passed both gates.
  Gate A, a full `mvn test` (no skip, no scope): BUILD SUCCESS, 5585 tests, 0 failures, 0 errors, 10
  skipped (the same stable 4-class baseline held since Stage II-1), console and XML fully
  reconciled. Gate B, an application startup smoke test: the application started, health and
  readiness both reported UP immediately and after a 20-second wait, the configured port was
  listening, the application log held 0 bean/configuration/startup failure markers, and the
  application stopped and released the port cleanly.
- Stage II-3 (Login) is now DONE in full: sub-stages II-3.0, II-3.a, II-3.a2, II-3.b0, II-3.b,
  II-3.c, II-3.d and II-3.e are all DONE. The next stage is II-4 (Forgot Password).

## Appendix E: Stage II-4 (Forgot Password) Investigation and Draft Sub-Plan

### E.1 Current send and resend path (scripts 114, 115)

`ForgotPasswordDetailsOtpProtectedOperation` (send, under lock after identity and details
verification) and `ForgotPasswordOtpResendProtectedOperation` (resend) both build a
`MessageDeliveryCommand` from a private `message(...)` method, reading subject/body from a static
field `TEMPLATES = ForgotPasswordOtpTemplates.approved()` (send) or an injected
`ForgotPasswordOtpTemplates templates` (resend). Both read `{code}`, `{ttlValue}`, `{ttlUnit}` into
the template variables map; neither reads `trustCode`, `schemeName`, or `schemeNameChi` today,
because the protected context carries none of those three fields (E.3).

### E.2 The content source is a real, already-wired Java class, not a dead YAML key

`ForgotPasswordOtpTemplates` is a real `@Bean`
(`ForgotPasswordApplicationConfig.forgotPasswordOtpTemplates()`) with a static `approved()` factory
containing hardcoded en/zh_HK strings for SMS, email subject and email body, each validated at
construction time for the `{code}`/`{ttlValue}`/`{ttlUnit}` placeholders. The YAML node
`auth-workflows.forgot-password.otp.localized-template.*` genuinely exists in `application.yml` but
is read by nothing in the codebase - confirming D.11.4's "orphaned" description precisely: the real
content lives in Java, not config, with a separate, unused YAML node sitting alongside it.

### E.3 The real gap: the protected context, not the APIM source

`ForgotPasswordAuthFlowContext` has **no** `trustCode`/`schemeName`/`schemeNameChi` fields today,
and a direct, repository-wide search confirms nothing in the `forgotpassword` package tree reads any
of those three accessor names anywhere (the one unrelated match, `item.trustCode()` in
`ForgotPasswordTrpSearchMemberMapper`, reads the raw APIM response item, not the protected context).
`VerifyForgotPasswordDetailsService`, which is where the lookup result would need to flow into the
context, extracts only `accountEnv` from the lookup result today and discards the rest.

### E.4 The APIM source already has the data; no D8-style correction needed

Unlike Login's original D8, there is no wrong-DTO problem here.
`ForgotPasswordTrpSearchMemberDataItem` (the Forgot-Password-owned, independently-named
`TrpSearchMemberApimDataItem` under its own package) already models `trustCode`, `schemeName` (from
`sch-name`), and `schemeNameChi` (from `chin-sch-name`), via a compatibility constructor that
defaults the two newer fields to `null` for pre-existing call sites - the same shape Username
Recovery's own copy of this class uses (E.7). The work for Stage II-4 is purely downstream: thread
these three fields from the lookup result into `ForgotPasswordAuthFlowContext`, and from there into
the rendered message.

### E.5 Content parity with Login is already decided, not open

Solution design section 5 ("Resolved Decision: Login vs. Forgot Password Templates"), read directly:
`LOGIN_OTP` and `FORGOT_PASSWORD_OTP` remain **two separate notice codes with two separate YAML
config entries** - they are not merged, since they originate from different workflows and may need
to diverge independently in future - but **their content (subject/body text, per channel/locale)
must be synchronized to be identical**, reconciling the current wording discrepancy (Login: "Your
BCT login verification code is {code}..."; Forgot Password: "Your BCT forgot password verification
code is {code}..."). The new `forgot-password-otp` registry entries should carry the exact same
literal subject/body text as `login-otp`'s, not new content.

### E.6 Two content-drift findings in the current hardcoded path (script 115)

- `ForgotPasswordDetailsOtpProtectedOperation.message()` hardcodes the literal string `"5"` for
  `ttlValue`, while the OTP challenge's TTL is separately hardcoded to 300 seconds a few lines
  above. 300 / 60 = 5, so today's rendered output is correct, but the two numbers are independent
  magic literals with nothing enforcing they stay in sync - a drift risk, not a current defect.
- `ForgotPasswordOtpResendProtectedOperation.message()` already derives `ttlValue` correctly via
  `Math.max(1, otpPolicy.ttlSeconds() / 60)`.
- Both operations additionally declare an identical, copy-pasted private `ttlUnit(String language)`
  method (same hardcoded `"分鐘"`/`"minutes"` logic). Routing both channels through
  `NoticeTemplateRegistryPort` removes the duplication and the hardcoding together, the same way
  Login's D9/D.11.2 resolution did.

### E.7 Cross-workflow DTO inventory (script 116, directly answering Nick's question)

Each workflow that calls `TRPSearchMember` maintains its **own**, independently-named,
package-scoped `TrpSearchMemberApimDataItem` (or, for Forgot Password, the already-prefixed
`ForgotPasswordTrpSearchMemberDataItem`) - there is no shared, generic DTO across workflows.
Username Recovery's own copy has `schemeName`/`schemeNameChi` (added per this workstream's section
8.2, carried through `UsernameRecoveryLookupCandidate` and the domain `UsernameRecoveryCandidate`).
Registration's own copy of the same simply-named class does **not** carry either field - confirmed
by a direct, repository-wide search finding zero occurrences of `schemeName`/`schemeNameChi`
anywhere in Registration's code (script 118) - consistent with, not contradicting, the Phase II
table's own "No scheme-name placeholder needed (§8.2)" text for Stage II-2. Forgot Password should
follow Username Recovery's pattern directly: its own DTO already has the shape; the remaining work
is purely in the context and session layers (E.3).

### E.8 The rebuild-safety precedent to replicate (scripts 117, 118)

Username Recovery once had exactly the bug shape this stage must now guard against:
`UsernameRecoverySessionCandidate.withState()` rebuilt a candidate from an existing one using the
dangerous 6-arg compatibility constructor, silently dropping `schemeName`/`schemeNameChi` on every
delivery-state transition. This was caught during implementation (not design), fixed in commit
`8889c0a1` to use the canonical 8-arg constructor and explicitly carry both fields forward, and is
now guarded by three regression tests (`withStatePreservesSchemeNameAcrossReserveTransition`,
`...AcrossRequestedTransition`, `...HandlesNullSchemeNameFieldsGracefully`). A direct,
repository-wide search confirms no other live violation of this pattern exists today, and confirms
Registration cannot have the same bug, because it never carries `schemeName`/`schemeNameChi` in any
domain object in the first place (E.7). When `ForgotPasswordAuthFlowContext` gains these three
fields (II-4.b0), its existing rebuild methods - `withPhase(...)` and `withResetAuthorization(...)`,
both already present today - must carry the new fields forward explicitly, each proven by a
regression test of the same shape as Username Recovery's.

### E.9 Draft sub-plan

| Sub-stage | Change | Status |
|---|---|---|
| II-4.0 | Settle the one open item below (E.10); confirm this appendix before any code. | DONE (see E.10) |
| II-4.a | Read-only investigation of the send/resend path, the content source, the context, the APIM source, the cross-workflow DTO inventory, and the rebuild-safety precedent. | DONE (scripts 114-118; findings in E.1-E.8) |
| II-4.b0 | Add `trustCode`/`schemeName`/`schemeNameChi` to `ForgotPasswordAuthFlowContext` (compatibility constructor, same pattern as Login's `LoginPreAuthContext`); update `VerifyForgotPasswordDetailsService` to populate them from the lookup result; update the AES-GCM protector's private `Payload` record in lockstep (the Appendix A.1 carry-over warning, made concrete for this workflow); update `withPhase(...)`/`withResetAuthorization(...)` to carry the three fields forward explicitly, each with a regression test (E.8). | DONE (script 123, commit `47b6f99e`; see E.11) |
| II-4.b | `ForgotPasswordDetailsOtpProtectedOperation` and `ForgotPasswordOtpResendProtectedOperation` resolve `FORGOT_PASSWORD_OTP` through `NoticeTemplateRegistryPort`, removing the hardcoded `ForgotPasswordOtpTemplates` content path, the hardcoded `"5"` `ttlValue` literal, and the duplicated `ttlUnit()` method from both operations; new `forgot-password-otp` YAML entries carry content synchronized to `login-otp`'s (E.5). | DONE (script 129, commit `cac6b38f`; see E.11) |
| II-4.c/d | Combined rendering and missing-definition test with the real registry and renderer for SMS and EMAIL in en and zh_HK (the minutes, the unit, no placeholder left over, content identical to Login's) and a missing definition on send and on resend (the error reaches the caller, nothing is created or sent). | DONE (prerequisites: script 132 commit `0457a465`, script 134 commit `2a63c3af`; test: script 136 commit `8740ba7f`; see E.11) |
| II-4.e | Stage gate on the final code: full `mvn test` and an application startup smoke test; then the plan is updated. | DONE (script 142; see E.11) |

### E.10 Decision: II-4.c and II-4.d are combined (Nick, 2026-10-08)

Nick confirmed II-4.c and II-4.d are combined into a single sub-stage, the same way Login's II-3.c
and II-3.d were combined (script 108). Reasoning: the real test harness - the real
`application.yml`, the real `NoticeTemplateRegistryPort` adapter, the real send/resend services, and
the real `MessageDeliveryAdapter` with its renderer - is identical for both the rendering assertions
and the missing-definition assertions, so building it once and asserting both is strictly less code
than two parallel copies of the same real-dependency wiring. Login's own combined test also produced
one strong, data-backed proof (the real `TRPMemberLogin` response rendering correctly end to end)
rather than two weaker, separate ones; the same benefit applies here. This decision is final; no
other sub-stage in Appendix E remains open.

### E.11 Closure of II-4.0, II-4.b0, II-4.b, II-4.c/d and II-4.e: Stage II-4 (Forgot Password) is DONE (2026-10-09)

- II-4.0 is DONE: the one open item (sub-stage granularity) was settled by Nick's decision at
  E.10, docs commit `b89d4c4b`.
- II-4.b0 is DONE: script 123 committed `47b6f99e`, threading `trustCode`/`schemeName`/
  `schemeNameChi` through `ForgotPasswordAuthFlowContext`, the lookup result and the AES-GCM
  protector, with `withPhase(...)`/`withResetAuthorization(...)` carrying the three fields
  forward explicitly (mirroring Username Recovery's E.8 rebuild-safety precedent).
- II-4.b is DONE: script 129 (a clean retry of script 127, which aborted with zero files written
  after script 128's investigation found a single anchor mismatch - an 11-asterisk masked-email
  literal where the real test file had 12) committed `cac6b38f`, routing both
  `ForgotPasswordDetailsOtpProtectedOperation` and `ForgotPasswordOtpResendProtectedOperation`
  through `NoticeTemplateRegistryPort` and removing the hardcoded `ForgotPasswordOtpTemplates`
  content path, the hardcoded `"5"` `ttlValue` literal, and the duplicated `ttlUnit()` method.
- II-4.c/d is DONE, combined into one test file per Nick's E.10 decision. Two prerequisite fixes
  landed first, mirroring Login's own two-fix precedent (D.13): script 132 committed `0457a465`,
  threading `trustCode`/`schemeName`/`schemeNameChi` into the rendered content with the same
  blank-`schemeNameChi`-falls-back-to-`schemeName` rule Login already has (script 102); script 134
  committed `2a63c3af`, reordering both operations so the registry definition is resolved before
  any OTP challenge is created or (resend only) the session is mutated, mirroring Login's own
  already-safe ordering. Script 136 then committed `8740ba7f`, adding
  `ForgotPasswordOtpRenderedContentIntegrationTest.java`: the real `YamlNoticeTemplateRegistryAdapter`
  and the real `MessageDeliveryAdapter`, 10 tests covering rendering on all 4 channels, the
  blank-`schemeNameChi` fallback on both send and resend, the Chinese unit placed directly after
  the number, the whole-minutes-with-a-minimum-of-one rule, and the two missing-definition cases
  (send: no OTP challenge created; resend: no OTP challenge created and no session mutated).
- II-4.e is DONE: the stage gate (script 142, on commit `8740ba7f`) passed both gates. Two
  earlier gate-script attempts (139, 140) surfaced and were fixed as script-only bugs - a
  process-group-signaling assumption that left two orphaned processes behind (cleaned up by
  script 141) and a failure-marker double-count caused by an unnecessary `|| echo 0` fallback -
  neither was a code or test defect. Gate A, a full `mvn test` (no skip, no scope): BUILD SUCCESS,
  5608 tests, 0 failures, 0 errors, 10 skipped (the same stable baseline held since Stage II-1).
  Gate B, a real application startup smoke test: the application started, health reported UP
  immediately (36 seconds) and again after a 20-second wait, the configured port (8889, this
  worktree's own real `SERVER_PORT`) was listening, the startup log held 0 bean/configuration/
  startup failure markers, and the application stopped and released the port cleanly.
- Stage II-4 (Forgot Password) is now DONE in full: sub-stages II-4.0, II-4.a, II-4.b0, II-4.b,
  II-4.c/d and II-4.e are all DONE.

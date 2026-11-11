# 06 Implementation plan

This is a proposed incremental plan, not authorization to edit code now. One subtask/commit at a time. Use a dedicated branch from the verified current origin/dev; branch name is selected at execution, not assumed to exist. Never amend unrelated history or run Maven concurrently in the same worktree.

## M0: Re-anchor and contract freeze
- Verify HEAD, clean/dirty state, origin/dev drift from E1, active guidance and existing response conventions.
- Pin new error codes and response DTO shape; preserve existing Personal Information contract.
- Resolve technical gates G1-G7 in 07 before production changes: storage atomicity, body-order boundary, limits, logging, crypto/routing, FE header deployment, apply-all semantics.
- Record chosen class/file locations from actual source; no speculative replacement anchors.
Exit: reviewed source map, contract fixtures and exact commit anchor. Documentation-only commit if appropriate; no Maven for docs-only edits.

## M1: Pure contracts and downstream adapter
1. Add request/result/domain types with secret-safe toString and purpose registry limited to Personal Information.
2. Add canonicalizer and digest vectors, duplicate-key handling and exact-decimal rules.
3. Add TRPPwdValidation DTOs/adapter with NON_ACCOUNT/no accountEnv/NON_RETRYABLE; recognized outcomes and defensive response parsing.
Exit: targeted tests and transport no-retry proof; no public endpoint exposed without security.

## M1.5: Session-lifecycle CAS generalization (prerequisite subtask, blocking M2, own review gate)
- Generalize the shared conditional-write mechanism with operation-specific eligibility. Preserve updateIfUsable behavior for grant mutations and existing consumers. Preserve-current TTL remains an in-script PTTL policy; lifecycle writes use a sibling absolute-deadline CAS script with Redis TIME validation and atomic PEXPIREAT, per Section 6. No Java-derived relative TTL may extend the deadline through execution delay.
- Migrate AuthenticatedSessionLifecycleService loadAndTouch and persistTerminal onto the generalized mechanism, removing their separate blind touch-based write path for authenticated sessions; initial session creation may keep its existing store path.
- Add the ten deterministic tests from Section 6 (expired-ACTIVE transition, actual Redis TTL extension on touch, actual Redis TTL shortening on terminal transition, terminal-state idempotency, both stale-write races, and the two added discovered-expiry/absolute-expiry-cap tests), plus a full regression pass of every existing consumer of the current CAS method -- AuthenticatedSessionCsrfRefreshService (CSRF hash rotation), FundComparisonOptionsCacheService, and PersonalInformationConfigCacheService (both cache services already mutate only their own featureState entry via withFeatureState) -- confirming no behavior change for any of the three and that each still preserves every other caller's unrelated featureState entries during its own mutation.
- This subtask changes existing, already-shipped session-lifecycle code and is explicitly NOT password-validation-specific. It requires its own dedicated review handoff and an independent APPROVED verdict before M2's grant-issuance/consumption work begins, separate from and in addition to the overall password-validation design-package gate.
Exit: generalized CAS mechanism implemented and tested in isolation from password-validation; all ten enumerated race/lifecycle tests, the additional delayed-execution deadline proof, and the full existing session-lifecycle regression suite pass; independent review approval obtained for this subtask specifically, with its own milestone identifier and commit recorded.

## M2: Atomic authorization lifecycle and throttling
1. Implement approved username-wide limiter using existing rate-limit capability where compatible; do not assume its existing enum covers this feature.
2. Implement grant issue/replace and ownership-aware compare-and-consume, expiry and session-usability checks.
3. Add concurrent Redis tests, uncertain write/consume behavior, revoke/expiry tests and no-resurrection guards.
Exit: exactly one consumer, same-scope replacement and fail-closed behavior proven. No read-then-delete implementation accepted.

## M3: Shared issuance endpoint
1. Compose existing authentication/account context, input policy, throttle, downstream check and grant issuance.
2. Implement lock cleanup with unconditional cookie clearing and 503 on invalidation failure.
3. Add feature HTTP/error mapping, no-store and safe logging exclusions; no password/result replay.
Exit: all issuance HTTP cases, logging guards and downstream-call counts pass. Endpoint supports only PERSONAL_INFORMATION_UPDATE.

## M4: Personal Information enforcement
1. Add route-scoped pre-body gate requiring token and authenticated account context.
2. Consume before body parsing; compare complete payload before existing YAML/access/update logic.
3. Preserve existing update response and downstream mapping; no GET changes.
4. Add full HTTP malformed-body and missing-account bypass tests; logging-enabled tests; apply-all and existing regression coverage.
Exit: protected mutation cannot dispatch without valid consumed authorization. No fallback that permits tokenless update.

## M5: Feature stabilization and coordinated release
- Run milestone-end formatting/full tests/coverage/startup/SonarQube gates once per agreed milestone boundary, not every subtask.
- FE contract tests cover frozen payload, one in-flight confirmation per scope, token discard, 401 versus 423 behavior and no automatic timeout resubmission.
- Verify gateway/CORS new-header support, runtime TTL/limiter settings, Redis compatibility and APIM route/profile configuration.
- Coordinate FE and BFF enforcement deployment. Do not silently introduce a security-bypass compatibility mode for old FE.
- Rollback must be coordinated: reverting enforcement can restore unprotected updates. Document that risk explicitly rather than calling rollback security-neutral.
Exit: reviewed evidence, clean committed worktree, deployment compatibility and operational handling of uncertain mutations confirmed. Only then mark the feature complete.

## M6: Purpose-owned prechecks (approved design; not implemented)

This is a future implementation plan. The documentation checkpoint does not
authorize starting these changes. Preserve all M1-M5 implementation and gate
evidence above.

1. **Shared decoder/policy composition and PI compatibility.** Add the minimal
   purpose payload records, decoder registry, framework-free precheck policy
   registry and Spring composition check. Remove purpose branches from the
   shared parser/issuance algorithm. Keep PI issuance 400 and PI PUT behavior
   unchanged. Prove missing, duplicate and mismatched registrations fail
   composition and that prechecks precede throttle/password.
2. **CIM issuance policies and event ownership.** Run submit V1-V8/S1-S3 and
   cancel CV1-CV2/pending-reference membership once at issuance. Return detailed
   422 errors, or feature-specific 503 for snapshot miss. Assert one
   feature-owned `LOCAL_SYSTEM` event for snapshot miss and no shared duplicate;
   expected violations create no technical incident.
3. **Required existing gate binding at execution.** Reuse the existing
   `BINDING_ATTRIBUTE`; require it at each CIM controller and in each command.
   Strictly decode/construct commands without repeating business prechecks.
   Preserve token scope/lifetime/consumption/canonicalization, submit
   reconciliation, downstream behavior, and best-effort cancel cleanup.
4. **Contract/documentation closure.** Run the M6 tests in document 05 and
   update current-versus-target evidence only from executed results. No
   snapshot concurrency/CAS/GET-fencing work and no token migration/versioning.

Exit requires the approved tests, targeted/full gates and review for the
implementation revision. Report failures/skips and do not claim runtime or
Progress behavior beyond existing contracts. Implementation order and commit
boundaries remain subject to a separate execution prompt.

## Script/evidence conventions
Next generated script after investigation 001 uses 002, unless another artifact consumes that script sequence before execution. This document archive is not an executable script. Each script gets a unique filename; timestamped target/evidence directory; near-top EXIT trap that archives on success/failure as <seq>-<brief>.zip.txt and copies to the established api-service llm-scripts directory. Use python, not python3. Read-only scripts must not create commits.

Use the validated WSL toolchain for this workflow unless a current worktree-specific override is explicitly verified. Do not run full mvn test, formatter, smoke or SonarQube in every subtask. Formatting application belongs at feature stabilization, followed by compile/test-compile. Implementation deliverables include proposed commit messages and stop at their stated gate. Escalate after three consecutive failures or ten scripts without a commit, rather than continuing silently.

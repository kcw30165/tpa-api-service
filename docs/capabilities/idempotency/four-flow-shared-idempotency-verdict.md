# Four-Flow Shared-Idempotency Migration Review Verdict (P3–P6)

**Scope:** Registration (P3), Login (P4), Forgot Password (P5), Username Recovery (P6).
**Type:** Review-only verification. No history reconstruction, no cherry-pick, no production change.
**Baseline:** branch `refactor/idempotency-capability`, HEAD `9a328c743281223274fc598c79ceba26ae660bf4`.
**Evidence rounds:** 1029 (inventory / wiring / remnants / protected) and 1030
(per-flow `@Bean` composition + 20 green migration architecture guards). Working tree clean.

## Overall verdict

All four flows are **exclusively composed through the shared `IdempotencyExecutor` +
`IdempotencyPort`** in production. No flow retains its own response-idempotency lifecycle
coordinator. The 20 focused migration guard tests all passed (0 failures / 0 errors).
Legitimate protected-replay, OTP commit-state, committed-login recovery/handoff, encrypted
auth-flow context, and shared public vocabulary are correctly retained and are **not**
duplicate lifecycle ownership.

**One P7-pending residue (not a four-flow defect):** the Registration legacy Redis lifecycle
files (`RedisRegistrationIdempotencyOperations`, `ReactiveRedisRegistrationIdempotencyOperations`,
`RedisRegistrationIdempotencyRecord`, `RedisRegistrationIdempotencyResponseSnapshotRecord`) still
exist on disk but are **not wired by any `@Bean`/configuration** — they are orphaned dead code
whose deletion belongs to P7 repository consolidation. No production flow consumes them, so this
does not falsify the P3–P6 runtime migration; it is a cleanup item for P7.

## Per-flow verdict

### P3 Registration — PASS (shared)
| Action | Production bean → concrete impl | Shared executor |
|---|---|---|
| Start | `RegistrationStartUseCaseConfig` → `SharedStartRegistrationService` | yes |
| Complete | `RegistrationCompleteUseCaseConfig` → `SharedCompleteRegistrationService` | yes |
| OTP Verify | `RegistrationOtpVerifyUseCaseConfig` → `SharedVerifyRegistrationOtpService` | yes |
| OTP Resend | `RegistrationOtpResendWebConfig` → `SharedResendRegistrationOtpService` | yes |

- Each bean constructs `IdempotencyExecutor` + `RegistrationSharedIdempotencyIdentityFactory` +
  `RegistrationSharedFingerprintFactory` and a protected operation + shared execution bridge.
- **Retained legitimate:** `RegistrationStart*ProtectedReplay*` (AES-GCM), protected auth-flow
  context, and `RegistrationIdempotencyKey/ResponseSnapshot/StartOutcome` as shared **public
  vocabulary** consumed by web mappers, commands, and shared bridges.
- **Supporting commits:** `165da95a refactor(registration): migrate idempotency to shared capability`,
  `c1580c21 fix(registration): compose shared idempotency identity`.
- **P7 residue:** orphaned `RedisRegistrationIdempotency*` (unwired) — delete in P7.

### P4 Login — PASS (shared)
| Action | Production bean → concrete impl | Protected core |
|---|---|---|
| Start | `LoginApplicationConfig` → `SharedStartLoginService` | `LoginStartProtectedOperation` + replay committer |
| OTP Send | `LoginOtpSendApplicationConfig` → `SharedLoginOtpSendService` | wraps `LoginOtpSendService` |
| OTP Resend | `LoginOtpResendApplicationConfig` → `SharedLoginOtpResendService` | wraps `LoginOtpResendService` |
| OTP Verify | `LoginOtpVerifyApplicationConfig` → `SharedLoginOtpVerifyService` | wraps `LoginOtpVerifyService` |

- No `*Service` is a Login idempotency **lifecycle** coordinator besides the `Shared*` wrappers
  (confirmed "none").
- **Retained legitimate:** `LoginStartProtectedReplayCommitter` (AES-GCM start replay),
  committed-login recovery + `AuthenticatedLoginHandoff` + `LoginOtpConsumptionProof`, and
  `RedisLoginIdempotencyKeyFactory` — now consumed **only** by the OTP-verify persistence adapter
  and the authenticated-login handoff adapter (protected recovery), not a response lifecycle.
- The legacy literal `:auth:idempotency:login:` survives **inside that key factory**, serving
  protected OTP-verify/handoff persistence — legitimate, not duplicate lifecycle.
- **Supporting commits:** `cc75ae8b refactor(login): migrate idempotency to shared capability`,
  `5b909940 refactor(login): consolidate shared idempotency lifecycle`,
  `fd8af5c6 refactor(login): move transient JSON behind application port`.

### P5 Forgot Password — PASS (shared)
| Action | Production bean → concrete impl |
|---|---|
| Start | `ForgotPasswordApplicationConfig` → `IdempotentStartForgotPasswordService` |
| Details+OTP | → `IdempotentForgotPasswordDetailsOtpService` |
| OTP Resend | → `IdempotentResendForgotPasswordOtpService` |
| OTP Verify | → `IdempotentVerifyForgotPasswordOtpService` |
| Complete | → `IdempotentCompleteForgotPasswordService` |

- Every idempotent action bean receives `IdempotencyExecutor`. **Cancel** is
  `CancelForgotPasswordService` — ordinary revision-aware auth-flow cancellation with no
  idempotency lifecycle (legitimately out of scope).
- **Retained legitimate:** `ForgotPasswordStart*ProtectedReplay*` (AES-GCM),
  `ForgotPasswordOtpCommitStatePort` (OTP commit-state), protected auth-flow context.
- **Supporting commits:** `9a44299b refactor(idempotency): remove legacy Forgot Password Start service`,
  `b730ca6c refactor(idempotency): migrate forgot password otp resend`,
  `3c2b6511 refactor(idempotency): migrate forgot password otp verify`,
  `fefa2ec7 refactor(idempotency): migrate forgot password complete`,
  `54beeff4 refactor(idempotency): remove forgot password legacy lifecycle`.

### P6 Username Recovery — PASS (shared)
| Action | Production bean → concrete impl |
|---|---|
| Start | `UsernameRecoveryFeatureConfig:180` → `IdempotentStartUsernameRecoveryService` |
| Request | `:234` → `IdempotentRequestUsernameRecoveryService` |
| Cancel | `:280` → `IdempotentCancelUsernameRecoveryService` |

- All three beans construct `IdempotencyExecutor` + `UsernameRecoveryIdempotencyIntegrationPolicy`
  + a protected operation + execution bridge.
- The feature-local response-idempotency port, Redis lifecycle adapter, records, key factory,
  legacy CANCEL coordinator, action-local fingerprint generators, and direct-session replay
  descriptors are **absent** (removed in P6).
- **Retained legitimate:** `UsernameRecoveryStart*ProtectedReplay*` (AES-GCM), protected auth-flow context.
- **Supporting commits:** `245040a3 refactor(username-recovery): add shared idempotency contracts`,
  `929d18dc migrate start`, `ff5f9e73 migrate request`, `0180bfc3 migrate cancel to shared idempotency`.

## Legitimate vs duplicate — classification key
- **Protected replay (AES-GCM `*ProtectedReplay*`, `rpl_...`):** encrypted workflow continuity for
  Start/OTP recovery. Keep.
- **OTP commit-state / committed-login recovery / handoff / consumption proof:** crash-recovery
  evidence outside the safe snapshot. Keep.
- **Encrypted auth-flow context / checkpoint:** session continuity, not response idempotency. Keep.
- **Shared public vocabulary (`*IdempotencyKey/ResponseSnapshot/StartOutcome`):** consumed by
  mappers/bridges as the shared contract. Keep.
- **Duplicate lifecycle (feature-local port + Redis lifecycle adapter + record + status enum +
  fingerprint coordinator):** must be gone. Gone for all four flows at runtime; only the unwired
  Registration Redis files remain on disk for P7 deletion.

## Guard evidence (1030) — all green
`SharedIdempotencyRuntimeConfigTest`, `DefaultIdempotencyExecutorTest`,
`RegistrationSharedCoreMigrationArchitectureTest`, `SharedVerifyRegistrationOtpServiceArchitectureTest`,
`RegistrationStart/CompleteProtectedOperationArchitectureTest`,
`Task0043LoginStartSharedProductionCompositionTest`, `LoginOtpVerifyNoLegacyVerificationGuardTest`,
`ForgotPasswordStart/OtpVerifyCompositionArchitectureTest`,
`IdempotentStartForgotPasswordServiceArchitectureTest`,
`ForgotPasswordComplete/OtpResend/OtpVerify/DetailsOtpSharedIdempotencyFailFirstTest`,
`IdempotentStart/RequestUsernameRecoveryServiceTest`,
`UsernameRecoveryCancelSharedMigrationFailFirstTest`,
`UsernameRecoveryStartReplayArchitectureGuardTest` — 0 failures, 0 errors.

## Recommendation
No genuine migration defect in P3–P6. Proceed to **P7** to delete the orphaned, unwired
`RedisRegistrationIdempotency*` files and add a repository-wide guard forbidding feature-local
idempotency lifecycle, then run full-suite + startup stabilization.

## P7 cleanup alignment
Post-verdict repository consolidation completed the physical removal of the orphaned/dead
feature-local idempotency lifecycle noted above:
- P7-B deleted the unwired Registration Redis cluster (`d28b2679dcfdef307bad37cc0430ace7fa6c0e77`).
- P7-C deleted the dead Login idempotency DTO cluster (`6b787bb7a1c75db46815e39bd3ebd1be03f17ee4`).
- P7-E added `SharedIdempotencyNoFeatureLocalLifecycleArchitectureTest` (`730530fd5f02a54d80c430f726d605bbbe4a8a86`).
See `p7-legacy-cleanup-record.md` for the full record.

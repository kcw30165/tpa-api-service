# P8 Release Evidence -- Shared Idempotency Consolidation

**Branch:** `refactor/idempotency-capability` (not pushed; remote publication handled by Nick).

## Scope
Stabilization and release evidence for the P3-P6 shared-idempotency migration, the P7 legacy
consolidation, and the Forgot Password / Login FE-exposure fixes.

## Gate results (this round)
- spotless:apply, compile, test-compile: see evidence 03-05.
- Full `mvn test`: see 06 / 06b (aggregate) and 06c (regression guards).
- Application startup smoke test: see 07.

## Redis impact / rollback / compatibility-expiry / production monitoring -- APPLICABILITY

Per the consolidation plan section 7 (Compatibility) and the recorded phase-gate statements
(P4-F, P5-E, P6-F), **the shared-idempotency migration has not been deployed to any environment.**
Therefore the following P8 bullet-4 items are **Not Applicable (pre-deployment)** and are recorded
here explicitly rather than omitted:

- **Redis impact:** No Redis flush or broad deletion is part of the migration (plan section 7). The
  shared capability writes the new schema; legacy records are read-only via bounded readers. No
  production Redis change has occurred because nothing is deployed.
- **Rollback window:** N/A -- no deployment, so no rollout/rollback window is open. When first
  deployed, the shared reader fails closed on ambiguous/corrupt legacy records (plan section 7).
- **Compatibility-expiry (legacy TTL drainage):** N/A pre-deployment. Legacy readers are retained
  in code only until the max legacy TTL + rollout window expire after the first deployment; that
  clock has not started.
- **Production monitoring:** N/A pre-deployment. Observability vocabulary is owned by the shared
  capability (plan section 4) and will be wired to dashboards at deployment time.

## FE-exposure hardening included in this release
- Forgot Password COMPLETE: password-invalid / password-reused / member-not-found now emit BFF-owned
  BUSINESS codes (no raw err.tpa.* to FE); HTTP 422.
- Forgot Password DETAILS-OTP: member-not-found emits BFF-owned code (no raw err.tpa.* to FE).
- Login: incorrect-username and incorrect-password both collapse to `err.login.credentials.invalid`
  (HTTP 401) -- no credential enumeration. Internal APIM classification enums retained.
- Regression guards: SharedIdempotencyNoFeatureLocalLifecycleArchitectureTest,
  ForgotPasswordPasswordReuseExposureGuardTest, ForgotPasswordDetailsOtpMemberNotFoundExposureGuardTest,
  LoginCredentialExposureGuardTest.

## Conclusion
When all gates above are green, the migration + consolidation + FE-exposure hardening are
stabilized on the branch and ready for MR. Deployment-time evidence (Redis, rollback, TTL drainage,
monitoring) is to be captured at first environment rollout.

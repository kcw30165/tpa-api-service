# Phase 08: Jenkins Quality Correction

## Objective

Make Jenkins an independent and trustworthy CI authority before changing the delivery model.

## Activities

1. Replace the placeholder Unit Tests stage with real project validation.
2. Define a repository-owned validation command used locally and by Jenkins.
3. Run real unit tests with reports.
4. Add required compilation and test-compilation checks where needed.
5. Stabilize Sonar and make the agreed quality gate non-optional for protected flows.
6. Report Jenkins status to GitLab.
7. After a stable trial period, require successful Jenkins status before protected-branch merge.
8. Build the image from an exact source commit.
9. Add image metadata and traceability.
10. Add HTTP Actuator verification after deployment.
11. Keep side-effecting login and SMS tests outside unconditional deployment probes.

## Acceptance criteria

- Jenkins no longer reports placeholder tests as Unit Tests.
- Required checks are deterministic.
- GitLab receives status for the exact commit.
- Protected merges can be blocked on stable required checks.
- Deployment verification includes application-level HTTP health.

## Gate B implementation status

### API-service repository

- Repository-owned validation entrypoint: `scripts/ci/validate.sh`.
- Required toolchain: Java 21 and Maven 3.9.15.
- Validation order: Spotless check, production compile, test compilation, real Maven tests, JaCoCo report.
- Required reports: `target/surefire-reports/TEST-*.xml` and `target/site/jacoco/jacoco.xml`.
- Post-deployment application check: `.devsecops/healthcheck.sh`, using only Actuator readiness.
- Login, OTP, captcha, and SMS calls are excluded from unconditional health verification.
- Jenkins pipeline correction remains a separate commit and MR on `features/feature-ngtpa-131-141-restructure-jenkins-repo`.
- Gate E remains outstanding until Jenkins independently runs the checks for the exact application commit.

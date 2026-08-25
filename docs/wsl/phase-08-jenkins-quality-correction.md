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

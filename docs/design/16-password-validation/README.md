# Password Validation and Transaction Authorization: design package

Version 1.12. As-built documentation added: frontend integration, release and operations, and traceability. Requirements unchanged.
M1.5 approved at `bfbac598d47558a306caa0bbd5b829e19f83d17f`.
M2 approved at `ec7f1617868b03c0584a026150ffcd988c07bdcc`.
Nick authorized M3 only (D17): the shared issuance endpoint and confidentiality
guards. M3 requires independent review. M4 enforcement and all M5 work remain
unauthorized; existing Personal Information updates do not yet require a grant.
M3 CHANGES REQUIRED at `eddba8f64aa2d5d9017c913945f2f8d83dc55c1f`:
D18 repairs routing-equivalent confidentiality matching and explicit filter order.
Status: final requirements/design baseline for review; no implementation or deployment approval is implied.

The member confirms a password before submitting reviewed Personal Information changes. One shared BFF endpoint validates that password through TAS/APIM and issues a five-minute, single-use authorization bound to the authenticated session, selected account, purpose, and exact submitted changes. This is password confirmation, not a PIN or OTP feature.

## Reading order
1. [Solution design](01-solution-design.md)
2. [API contract](02-api-contract.md)
3. [Downstream contract](03-downstream-contract.md)
4. [Validation and error handling](04-validation-and-error-handling.md)
5. [Test and traceability](05-test-and-traceability.md)
6. [Implementation plan](06-implementation-plan.md)
7. [Decisions, evidence and release checks](07-decisions-and-evidence.md)

## Authority and boundaries
Approved requirements are explicitly identified. Detailed technical specifications and new error-code names in this package are authored design choices, not claims about existing code or separate stakeholder approvals. Implementation verification gates are not hidden assumptions and must not be represented as completed.

Source baseline E1: user-supplied 001-password-validation-readonly-investigation.zip.txt, immutable origin/dev commit 49b5ffb4089b04d214444b3e3d7cd33f2d5dbc4c. Evidence was reviewed in this conversation. Source selection was keyword/import/type based, not an exhaustive architecture audit. No tests, startup, formatting or SonarQube gates were run by the investigation.

This package records the reviewed design and implementation boundaries. Runtime
gate results and exact reviewed revisions belong in each milestone's evidence
archive; this document alone does not certify deployment readiness.

## As-built documents (M5)

8. [Frontend integration](08-frontend-integration.md)
9. [Release and operations](09-release-and-operations.md)
10. [As-built traceability](10-as-built-traceability.md)

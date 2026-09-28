# Centralized Masking Policy - Implementation Plan

Related design doc:
`docs/design/7-centralized-masking-policy/centralized-masking-policy-solution-design.md`

Branch: `refactor/centralized-masking-policy` (created from `origin/dev` at
`752800572da8bab9cb1e6839a18e0ec40c1aae15`)

## Progress log

| Phase | Description | Status | Evidence / commit |
|---|---|---|---|
| P1 | Core service: `MaskMode`, `MaskingProperties`, `MaskingService` in `shared.masking`, fail-first TDD, YAML defaults wired | DONE | `7e66625c` - "feat(masking): add centralized MaskingService and MaskingProperties core" (script 003) |
| P2 | Migrate the 4 FE-facing destination maskers (Login, ForgotPassword, Registration, UsernameRecovery) to call `MaskingService`; delete the 4 old classes; update their existing tests | PENDING | - |
| P3 | Migrate the 3 logging-facing maskers (`EmailAddressMasker`, `SmsSenderAdapter.maskPhone`, `RegistrationLookupLogMasker`) to call `MaskingService`; delete/inline as appropriate; integrate `LoggingSanitizer` placeholder sourcing; relocate `sensitive-tokens` config | PENDING | - |
| P4 | Update all remaining existing tests across the ~20+ files that assert old masked-value formats (see inventory below) so they reflect the new centralized formats (mobile now variable-length, keeping only first+last digit, not last-4) | PENDING | - |
| P5 | Full validation: `mvn test` (full suite, run manually per standing convention), architecture/contract tests re-verified, MR prep | PENDING | - |

## Phase 1 detail (DONE)

- `MaskMode` enum: `VARIABLE_LENGTH`, `FIXED_LENGTH`.
- `MaskingProperties` (`@ConfigurationProperties(prefix = "masking")`):
  nested `EmailMobile`, `Identity`, `DateOfBirth`, `Logging` groups, plus
  top-level `disabledPlaceholder`.
- `MaskingService` (`@Component`): `maskEmail`, `maskMobile`,
  `maskIdentity`, `maskDateOfBirth`, `loggingPlaceholder`.
- `MaskingServiceTest` (23 tests) + `MaskingPropertiesTest` (1 test), all
  passing; fail-first RED phase proven (test-compile failed with
  `cannot find symbol MaskingService`/`MaskingProperties` before
  implementation was written).
- `application.yml` updated with the full `masking:` block and documented
  defaults.
- Gates passed: RED test-compile (expected failure), GREEN compile,
  GREEN test-compile, GREEN scoped test run
  (`MaskingServiceTest,MaskingPropertiesTest` - 24/24 passed). Full `mvn
  test` suite intentionally NOT run in this phase per standing convention
  (deferred to P5).

## Phase 2 scope (call sites - FE destination display)

1. `LoginDestinationMasker` (`application.usecase.auth.login.preauth`) -
   repoint `LoginPreAuthContextFactory` and its `@Bean` wiring
   (`LoginPreAuthFoundationConfig`) to `MaskingService`; delete class.
2. `ForgotPasswordDestinationMasker` (`application.usecase.auth.forgotpassword.otp`)
   - currently a thin delegate to #1; repoint
   `ForgotPasswordOtpResendProtectedOperation` and
   `ForgotPasswordDetailsOtpProtectedOperation` (+ their `@Bean` wiring in
   `ForgotPasswordApplicationConfig`) to `MaskingService`; delete class.
3. `RegistrationContactMasker` (`application.service.auth.registration`) -
   repoint `RegistrationContactDeriver`; delete class.
4. `UsernameRecoveryRequestDestinationMasker`
   (`application.usecase.auth.usernamerecovery`) - repoint
   `UsernameRecoveryRequestProtectedOperation`; delete class.

Each of the 4 has direct fail-first-style tests
(`LoginDestinationMasker` has no dedicated test file - covered indirectly
via `LoginPreAuthContextFactoryTest`; `RegistrationContactMaskerTest`,
`UsernameRecoveryRequestDestinationMaskerTest` exist standalone and will
be deleted, with equivalent coverage confirmed already present in
`MaskingServiceTest`).

## Phase 3 scope (call sites - adapter/structured logging)

1. `EmailAddressMasker` (`adapter.out.email`) - repoint
   `SmtpEmailSenderAdapter`; delete class.
2. `SmsSenderAdapter.maskPhone` (private method, `adapter.out.sms`) -
   replace with `MaskingService.maskMobile` call; remove private method.
3. `RegistrationLookupLogMasker` (`adapter.out.apim.registration`) -
   repoint `ApimRegistrationLookupAdapter`'s use of `.contact(...)` to
   `MaskingService.maskEmail`/`.maskMobile`, `.identity(...)` to
   `MaskingService.maskIdentity`, `.dateOfBirth(...)` to
   `MaskingService.maskDateOfBirth`; delete class once all three methods
   are repointed.
4. `LoggingSanitizer` (`infrastructure.logging`) - replace the private
   `MASKED_VALUE = "***"` constant with
   `maskingService.loggingPlaceholder()` (constructor-injected); confirm
   whether `logging-sanitizer.sensitive-tokens` YAML key relocates under
   `masking.logging.sensitive-tokens` or stays put (decide in that
   script, do not assume).

## Phase 4 scope (existing test inventory to update)

Known call sites where existing tests assert the OLD mobile format
(last-4-digits) that must change to the NEW format (first+last digit,
variable-length by default) - non-exhaustive, confirm full list via grep
before implementing:

- `RegistrationContactMaskerTest`, `RegistrationContactDeriverTest`
- `UsernameRecoveryRequestDestinationMaskerTest`
- `LoginOtpDestinationResolverTest`, `LoginOtpSendServiceTest`,
  `LoginOtpResendServiceTest`
- `ForgotPasswordInitialOtpBindingSubtask005002FailFirstTest`
- `SmtpEmailSenderAdapterTest` (email format also changes: 3-star fixed
  -> variable-length by default)
- Various `*WebMapperTest` / integration tests asserting literal
  `maskedDestination` JSON values (e.g.
  `RegistrationOtpResendWebMapperTest`,
  `ForgotPasswordOtpResendWebMapperTest`,
  `UsernameRecoveryRequestWebMapperTest`,
  `RegistrationStartWorkflowResponseMapperTest`)
- `RegistrationStructuredFailureLoggingContractTest`,
  `RegistrationLoggingLookupStabilizationIntegrationTest` (assert
  `RegistrationLookupLogMasker.java` source text directly - will need
  updating once that class is deleted)

## Phase 5 scope

- Full `mvn test` (manual run, not scripted, per standing convention).
- Confirm no residual references to any of the 7 deleted classes
  (`grep -rl` sweep).
- MR title/description preparation once P2-P4 are committed.

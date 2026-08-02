# Feature 0061 Subtask 007 Evidence

## Scope

Subtask 007 completed the public forgot-password password reset finalization flow. The implemented slice covers password completion transport, structural password validation, protected reset-authorization validation, COMPLETE idempotency and replay handling, APIM `TRPResetPassword` integration, terminal cookie clearing, return-to-login responses, and irreversible unknown-outcome blocking.

Out of scope and still not implemented here:

- automatic Login or authenticated-session creation after reset
- post-login Change Password
- `Account-Ref` requirements for forgot-password completion
- `ActorContext` changes
- Subtask 008, Subtask 009, and Subtask 010

## Fail-First TDD Evidence

Failing tests were added or updated first in these focused slices:

- `ApimForgotPasswordCompletionAdapterTest`
- `CompleteForgotPasswordServiceTest`
- `AuthForgotPasswordCompleteControllerTest`

Expected failure reason:

- no `TRPResetPassword` outbound adapter existed
- no forgot-password completion application/web slice existed
- no protected `PASSWORD_SETUP` phase existed
- no forgot-password password validator existed

Observed targeted failure results before the final implementation stabilized:

- focused completion tests failed with unresolved symbols for `CompleteForgotPasswordService`, `CompleteForgotPasswordCommand`, `ForgotPasswordPasswordValidator`, and `PASSWORD_SETUP`
- the first happy-path service run after implementation returned `INVALID_AUTH_FLOW` instead of `SUCCESS` because the test fixture did not yet populate the encrypted protected-context session attributes required by the real service path

Production changes made after those failures:

- implemented the `TRPResetPassword` APIM adapter and mappers
- implemented `CompleteForgotPasswordUseCase` and `CompleteForgotPasswordService`
- implemented `ForgotPasswordPasswordValidator`
- added `PASSWORD_SETUP` and preserved protected identity state across phase transitions
- added the public completion controller, request/response DTOs, and mapper
- wired the new beans in forgot-password web and application config
- added replay, cleanup-pending finalization, and unknown-outcome blocking behavior

## Validation Evidence

Targeted pass result:

- `runTests` on `ApimForgotPasswordCompletionAdapterTest`, `CompleteForgotPasswordServiceTest`, and `AuthForgotPasswordCompleteControllerTest`: PASS

Broader adjacent validation result:

- `runTests` on `ApimForgotPasswordCompletionAdapterTest`, `VerifyForgotPasswordOtpServiceTest`, `CompleteForgotPasswordServiceTest`, and `AuthForgotPasswordCompleteControllerTest`: PASS
- summary: 14 passed, 0 failed

Surefire report summaries:

- `target/surefire-reports/com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.ApimForgotPasswordCompletionAdapterTest.txt`: `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`
- `target/surefire-reports/com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify.VerifyForgotPasswordOtpServiceTest.txt`: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`
- `target/surefire-reports/com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.CompleteForgotPasswordServiceTest.txt`: `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`
- `target/surefire-reports/com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthForgotPasswordCompleteControllerTest.txt`: `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`

Tests not run:

- full `mvn test` after Subtask 007 was not rerun in this round
- canonical `mvn -DskipTests compile` after Subtask 007 was not rerun in this round

Reason:

- the changed slice was validated through focused executable tests plus Surefire inspection for the touched completion and adjacent OTP/APIM classes

## Behavior Summary

Implemented completion behavior:

- public `POST /api/v1/auth/forgot-password/complete`
- request body restricted to `password` and `confirmPassword`
- structural validation from the repository-approved registration password policy
- reset authorization required in protected forgot-password context
- COMPLETE idempotency replay that excludes password and auth-flow identity from the request hash
- one mutation-lock lifecycle with revision-aware session reload
- `err.tpa.password.invalid` replayable business rejection without authorization consumption
- `err.tpa.member.not.found` terminal business rejection with cookie clearing and `RETURN_TO_LOGIN`
- success consumes authorization, finalizes the flow, clears pre-auth cookies, and returns `RETURN_TO_LOGIN`
- unknown outcome persists irreversible blocked state and prevents later retries across keys
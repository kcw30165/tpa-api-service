# Controller Refactoring Classification

## Executive result

READY_FOR_ARCHITECT_REVIEW

## Repository and module structure
- Single root Maven project.
- Main source roots: `src/main/java`
- Test source roots: `src/test/java`

## Current inbound web type counts
- Endpoint controllers: 17
- Advice classes: 1
- Filter-stack exception handlers: 1
- Effective annotation-proven endpoints: 36

## Controller-by-controller classification
- `com.bct.ngtpa.apiservice.adapter.in.web.controller.ApiExceptionHandler`: **MOVE** to `com.bct.ngtpa.apiservice.adapter.in.web.advice.ApiExceptionHandler`. Role: `ADVICE`. Cross-cutting REST advice.
- `com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthCaptchaController`: **RENAME** to `com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.CaptchaController`. Role: `ENDPOINT_CONTROLLER`. Standalone capability boundary.
- `com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthForgotPasswordController`: **CONSOLIDATE** to `com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.ForgotPasswordController`. Role: `ENDPOINT_CONTROLLER`. Cohesive Forgot Password HTTP journey.
- `com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthForgotPasswordOtpController`: **CONSOLIDATE** to `com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.ForgotPasswordController`. Role: `ENDPOINT_CONTROLLER`. Cohesive Forgot Password HTTP journey.
- `com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthLoginCancelController`: **CONSOLIDATE** to `com.bct.ngtpa.apiservice.adapter.in.web.auth.login.LoginController`. Role: `ENDPOINT_CONTROLLER`. Cohesive Login HTTP journey; application ports remain separate.
- `com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthLoginController`: **CONSOLIDATE** to `com.bct.ngtpa.apiservice.adapter.in.web.auth.login.LoginController`. Role: `ENDPOINT_CONTROLLER`. Cohesive Login HTTP journey; application ports remain separate.
- `com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthLoginOtpController`: **CONSOLIDATE** to `com.bct.ngtpa.apiservice.adapter.in.web.auth.login.LoginController`. Role: `ENDPOINT_CONTROLLER`. Cohesive Login HTTP journey; application ports remain separate.
- `com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthLoginOtpVerifyController`: **CONSOLIDATE** to `com.bct.ngtpa.apiservice.adapter.in.web.auth.login.LoginController`. Role: `ENDPOINT_CONTROLLER`. Cohesive Login HTTP journey; application ports remain separate.
- `com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthLogoutController`: **RENAME** to `com.bct.ngtpa.apiservice.adapter.in.web.auth.logout.LogoutController`. Role: `ENDPOINT_CONTROLLER`. Standalone capability boundary.
- `com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationController`: **CONSOLIDATE** to `com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.RegistrationController`. Role: `ENDPOINT_CONTROLLER`. Cohesive Registration HTTP journey; eligible-trust behavior preserved.
- `com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationOtpController`: **CONSOLIDATE** to `com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.RegistrationController`. Role: `ENDPOINT_CONTROLLER`. Cohesive Registration HTTP journey; eligible-trust behavior preserved.
- `com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationUsernameController`: **CONSOLIDATE** to `com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.RegistrationController`. Role: `ENDPOINT_CONTROLLER`. Cohesive Registration HTTP journey; eligible-trust behavior preserved.
- `com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthUsernameRecoveryController`: **RENAME** to `com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryController`. Role: `ENDPOINT_CONTROLLER`. Standalone capability boundary.
- `com.bct.ngtpa.apiservice.adapter.in.web.controller.ContributionController`: **MOVE** to `com.bct.ngtpa.apiservice.adapter.in.web.contribution.ContributionController`. Role: `ENDPOINT_CONTROLLER`. Standalone capability boundary.
- `com.bct.ngtpa.apiservice.adapter.in.web.controller.NotificationController`: **MOVE** to `com.bct.ngtpa.apiservice.adapter.in.web.notification.NotificationController`. Role: `ENDPOINT_CONTROLLER`. Standalone capability boundary.
- `com.bct.ngtpa.apiservice.adapter.in.web.controller.PersonalInformationController`: **MOVE** to `com.bct.ngtpa.apiservice.adapter.in.web.personalinformation.PersonalInformationController`. Role: `ENDPOINT_CONTROLLER`. Standalone capability boundary.
- `com.bct.ngtpa.apiservice.adapter.in.web.controller.ReferenceDataController`: **MOVE** to `com.bct.ngtpa.apiservice.adapter.in.web.referencedata.ReferenceDataController`. Role: `ENDPOINT_CONTROLLER`. Authenticated customer-facing reference-data capability, distinct from internal reference-date administration.
- `com.bct.ngtpa.apiservice.adapter.in.web.controller.ReferenceDateController`: **MOVE** to `com.bct.ngtpa.apiservice.adapter.in.web.internal.referencedate.ReferenceDateController`. Role: `ENDPOINT_CONTROLLER`. Internal reference-date administration boundary.
- `com.bct.ngtpa.apiservice.adapter.in.web.filter.AuthenticatedPortalContextWebExceptionHandler`: **KEEP** to `com.bct.ngtpa.apiservice.adapter.in.web.filter.AuthenticatedPortalContextWebExceptionHandler`. Role: `FILTER_EXCEPTION_HANDLER`. Filter-stack error handling, not controller advice.

## Endpoint ownership and route collision result
- The corrected inventory contains 36 annotation-proven endpoints.
- Artificial self-joined paths and unrelated string literals have been removed.
- See `docs/refactor/controller/controller-refactoring-endpoint-inventory.md` and evidence 065.

## Security-boundary result
- Pre-authentication auth journeys, authenticated logout, customer-facing authenticated capabilities, internal administration, REST advice, and filter-error handling remain distinct.

## Advice-boundary result
- Move `ApiExceptionHandler` to `web.advice`. It directly depends on `infrastructure.logging.LoggingSanitizer`; Stage 002 must preserve behavior and either accept this cross-cutting dependency explicitly or introduce an architect-approved boundary without broadening scope.

## Internal and reference-data result
- Move `ReferenceDateController` to `web.internal.referencedate`.
- Move customer-facing `ReferenceDataController` to `web.referencedata`; it is not part of internal reference-date administration.

## Filter exception-handler result
- Keep `AuthenticatedPortalContextWebExceptionHandler` under `web.filter`; it is filter-stack error handling, not controller advice or an endpoint controller.

## Journey consolidation results
- Login: provisional CONSOLIDATE.
- Registration: provisional CONSOLIDATE with additional eligible trust behavior preserved.
- Forgot Password: provisional CONSOLIDATE; Username Recovery remains separate.

## Reference-date duplicate result
- Exactly one `ReferenceDateController.java` exists in the repository source roots. The duplicate screenshot entry is not present in the repository.

## Baseline validation result
- Compile: PASS, exit 0.
- Bounded controller suite: PASS, 191 tests, 0 failures, 0 errors, 0 skipped.

## Proposed Stage 002 scope
- Move `ApiExceptionHandler` to `web.advice`.
- Move `ReferenceDateController` to `web.internal.referencedate`.
- Add focused package/architecture protections required for those two boundaries only.
- Do not move `ReferenceDataController` until Stage 003 or a separately approved stage adjustment.

## Blockers and architect decisions required
- No Stage 001 classification blocker remains.
- Stage 002 must account for the existing `LoggingSanitizer` infrastructure dependency without changing public error behavior.
## Contract-completion evidence

- Java response types are resolved for all 36 annotation-proven endpoints.
- Runtime-dependent status selection identifies exact response-selector components rather than inventing a single status.
- Security boundaries are derived from `src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java`.
- Endpoint test evidence is separated into focused HTTP/mapping, controller-method behavior, security, architecture/package-sensitive, class-reference-only, and no-evidence categories.
- Immediate call chains identify web collaborators, application input ports, and statically discoverable implementations.
- Evidence precedence is defined in `082-superseded-evidence-manifest.txt`.

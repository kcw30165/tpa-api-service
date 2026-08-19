# Controller Refactoring Endpoint Inventory

- Precedence: this canonical document and evidence `078` supersede original evidence `013` and `016`.
- Security classification is derived from the active `SecurityConfig`, not from route-name heuristics.
- Endpoint controllers: 17
- Advice classes: 1
- Filter-stack exception handlers: 1
- Effective annotation-proven endpoints: 36
- Endpoints with focused mapping or HTTP-contract evidence: 20
- Endpoints with focused mapping, HTTP-contract, or controller-method behavior evidence: 35
- Endpoints with class-reference-only evidence: 1
- Endpoints without identifiable evidence: 0

## Endpoint contracts

### `POST /api/v1/auth/captcha/challenge`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.CaptchaController#challenge`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/captcha/CaptchaController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `RequestCorrelation.REQUEST_ID_HEADER (optional)`, `RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER (optional)`
- Cookies: None declared
- Path variables: None declared
- Query parameters: None declared
- Request body type: `NONE`
- Java response type: `Mono< ResponseEntity<MutationResponse<CaptchaWorkflowResultResponse<CaptchaChallengeResponse>>>>`
- Success/status selection: Runtime-dependent; no direct status selector statically resolved in controller method
- Validation annotations: None declared at method boundary
- Immediate call chain: APPLICATION_INPUT_PORT `CreateCaptchaChallengeUseCase.create` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/captcha/CreateCaptchaChallengeService.java
- Test-evidence classification: `CLASS_REFERENCE_ONLY`, `CONTROLLER_METHOD_BEHAVIOR`, `FOCUSED_MAPPING_OR_HTTP_CONTRACT`, `SECURITY_TEST`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/AuthCaptchaControllerArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/CaptchaGeneratorArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/CaptchaPublicErrorOwnershipGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RedisCaptchaChallengeArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RegistrationHttpAndCookieSlidingContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RequiredRequestIdRegistrationContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/integration/registration/RegistrationCaptchaAndStartIntegrationTest.java`, `src/test/java/com/bct/ngtpa/apiservice/integration/registration/RegistrationIntegrationHarnessTest.java`, `src/test/java/com/bct/ngtpa/apiservice/auth/registration/P8CaptchaSecurityBoundaryGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/filter/RequiredRequestIdWebFilterTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/captcha/AuthCaptchaControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/captcha/CaptchaWebMapperTest.java`

### `POST /api/v1/auth/captcha/refresh`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.CaptchaController#refresh`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/captcha/CaptchaController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `RequestCorrelation.REQUEST_ID_HEADER (optional)`, `RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER (optional)`
- Cookies: None declared
- Path variables: None declared
- Query parameters: None declared
- Request body type: `CaptchaRefreshRequest`
- Java response type: `Mono< ResponseEntity<MutationResponse<CaptchaWorkflowResultResponse<CaptchaRefreshResponse>>>>`
- Success/status selection: Runtime-dependent via exact selector(s): `captchaHttpResponses.toResponse`
- Validation annotations: None declared at method boundary
- Immediate call chain: APPLICATION_INPUT_PORT `RefreshCaptchaUseCase.refresh` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/captcha/RefreshCaptchaService.java; WEB_COLLABORATOR `CaptchaHttpResponses.toResponse`; WEB_COLLABORATOR `CaptchaWebMapper.invalidRefreshChallengeIdResponse`
- Test-evidence classification: `CLASS_REFERENCE_ONLY`, `CONTROLLER_METHOD_BEHAVIOR`, `FOCUSED_MAPPING_OR_HTTP_CONTRACT`, `SECURITY_TEST`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/AuthCaptchaControllerArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/CaptchaGeneratorArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/CaptchaPublicErrorOwnershipGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RedisCaptchaChallengeArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RegistrationHttpAndCookieSlidingContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RequiredRequestIdRegistrationContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/integration/registration/RegistrationCaptchaAndStartIntegrationTest.java`, `src/test/java/com/bct/ngtpa/apiservice/integration/registration/RegistrationIntegrationHarnessTest.java`, `src/test/java/com/bct/ngtpa/apiservice/auth/registration/P8CaptchaSecurityBoundaryGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/captcha/AuthCaptchaControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/captcha/CaptchaWebMapperTest.java`

### `POST /api/v1/auth/captcha/verify`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.CaptchaController#verify`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/captcha/CaptchaController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `RequestCorrelation.REQUEST_ID_HEADER (optional)`, `RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER (optional)`
- Cookies: None declared
- Path variables: None declared
- Query parameters: None declared
- Request body type: `CaptchaVerifyRequest`
- Java response type: `Mono< ResponseEntity<MutationResponse<CaptchaWorkflowResultResponse<CaptchaVerifyResponse>>>>`
- Success/status selection: Runtime-dependent via exact selector(s): `captchaHttpResponses.toResponse`
- Validation annotations: None declared at method boundary
- Immediate call chain: WEB_COLLABORATOR `CaptchaHttpResponses.toResponse`; WEB_COLLABORATOR `CaptchaWebMapper.missingChallengeIdResponse`; WEB_COLLABORATOR `CaptchaHttpResponses.toResponse`; WEB_COLLABORATOR `CaptchaWebMapper.missingAnswerResponse`; APPLICATION_INPUT_PORT `VerifyCaptchaUseCase.verify` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/captcha/VerifyCaptchaService.java; WEB_COLLABORATOR `CaptchaWebMapper.toVerifyMutationResponse`; WEB_COLLABORATOR `CaptchaHttpResponses.toResponse`; WEB_COLLABORATOR `CaptchaWebMapper.invalidChallengeIdResponse`
- Test-evidence classification: `CLASS_REFERENCE_ONLY`, `CONTROLLER_METHOD_BEHAVIOR`, `FOCUSED_MAPPING_OR_HTTP_CONTRACT`, `SECURITY_TEST`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/AuthCaptchaControllerArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/CaptchaGeneratorArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/CaptchaPublicErrorOwnershipGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RedisCaptchaChallengeArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RegistrationHttpAndCookieSlidingContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RequiredRequestIdRegistrationContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/integration/registration/RegistrationCaptchaAndStartIntegrationTest.java`, `src/test/java/com/bct/ngtpa/apiservice/integration/registration/RegistrationIntegrationHarnessTest.java`, `src/test/java/com/bct/ngtpa/apiservice/auth/registration/P8CaptchaSecurityBoundaryGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/captcha/AuthCaptchaControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/captcha/CaptchaWebMapperTest.java`

### `POST /api/v1/auth/forgot-password/cancel`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthForgotPasswordController#cancel`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `X-XSRF-TOKEN (optional)`, `Origin (optional)`, `Referer (optional)`, `RequestCorrelation.REQUEST_ID_HEADER (optional)`, `Accept-Language (optional)`
- Cookies: `auth_flow_sid (optional)`
- Path variables: None declared
- Query parameters: None declared
- Request body type: `NONE`
- Java response type: `Mono< ResponseEntity< MutationResponse< ForgotPasswordWorkflowResultResponse<ForgotPasswordCancelDataResponse>>>>`
- Success/status selection: Runtime-dependent via exact selector(s): `ForgotPasswordHttpResponses.toResponse`, `CsrfHttpResponses.missingToken`
- Validation annotations: None declared at method boundary
- Immediate call chain: WEB_COLLABORATOR `AuthFlowCookieWriter.clearAuthFlowCookie`; WEB_COLLABORATOR `XsrfCookieWriter.clearXsrfCookie`; WEB_COLLABORATOR `ForgotPasswordCancelWebMapper.missingSession`; APPLICATION_INPUT_PORT `CancelForgotPasswordUseCase.cancel` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/cancel/CancelForgotPasswordService.java; WEB_COLLABORATOR `ForgotPasswordCancelWebMapper.toCommand`; WEB_COLLABORATOR `AuthFlowCookieWriter.clearAuthFlowCookie`; WEB_COLLABORATOR `XsrfCookieWriter.clearXsrfCookie`; WEB_COLLABORATOR `ForgotPasswordCancelWebMapper.toResponse`; WEB_COLLABORATOR `ForgotPasswordCancelWebMapper.systemUnavailable`; WEB_COLLABORATOR `ForgotPasswordCancelWebMapper.csrfInvalid`
- Test-evidence classification: `CONTROLLER_METHOD_BEHAVIOR`, `CLASS_REFERENCE_ONLY`, `SECURITY_TEST`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/ForgotPasswordCancelArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/ForgotPasswordEndpointSecurityContractSubtask009Test.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043FlowSpecificCsrfHttpMappingTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordCancelControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordCompleteControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordDetailsControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordIdentityControllerTest.java`

### `POST /api/v1/auth/forgot-password/complete`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthForgotPasswordController#complete`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `X-XSRF-TOKEN (optional)`, `Idempotency-Key (optional)`, `RequestCorrelation.REQUEST_ID_HEADER (optional)`, `Accept-Language (optional)`, `Origin (optional)`, `Referer (optional)`
- Cookies: `auth_flow_sid (optional)`
- Path variables: None declared
- Query parameters: None declared
- Request body type: `ForgotPasswordCompleteRequest`
- Java response type: `Mono< ResponseEntity< MutationResponse< ForgotPasswordWorkflowResultResponse<ForgotPasswordCompleteDataResponse>>>>`
- Success/status selection: Runtime-dependent via exact selector(s): `ForgotPasswordHttpResponses.toResponse`, `CsrfHttpResponses.missingToken`
- Validation annotations: None declared at method boundary
- Immediate call chain: WEB_COLLABORATOR `ForgotPasswordCompleteWebMapper.missingAuthFlowCookie`; WEB_COLLABORATOR `ForgotPasswordCompleteWebMapper.missingIdempotencyKey`; WEB_COLLABORATOR `ForgotPasswordCompleteWebMapper.invalidRequest`; APPLICATION_INPUT_PORT `CompleteForgotPasswordUseCase.complete` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/complete/CompleteForgotPasswordService.java; WEB_COLLABORATOR `ForgotPasswordCompleteWebMapper.toCommand`; WEB_COLLABORATOR `AuthFlowCookieWriter.clearAuthFlowCookie`; WEB_COLLABORATOR `XsrfCookieWriter.clearXsrfCookie`; WEB_COLLABORATOR `ForgotPasswordCompleteWebMapper.toResponse`; WEB_COLLABORATOR `ForgotPasswordCompleteWebMapper.systemUnavailable`; WEB_COLLABORATOR `ForgotPasswordCompleteWebMapper.invalidRequest`
- Test-evidence classification: `CLASS_REFERENCE_ONLY`, `CONTROLLER_METHOD_BEHAVIOR`, `SECURITY_TEST`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/ForgotPasswordCancelArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/ForgotPasswordEndpointSecurityContractSubtask009Test.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043FlowSpecificCsrfHttpMappingTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordCancelControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordCompleteControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordDetailsControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordIdentityControllerTest.java`

### `POST /api/v1/auth/forgot-password/details`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthForgotPasswordController#details`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `X-XSRF-TOKEN (optional)`, `Origin (optional)`, `Referer (optional)`, `RequestCorrelation.REQUEST_ID_HEADER (optional)`, `Accept-Language (optional)`, `Idempotency-Key (optional)`
- Cookies: `auth_flow_sid (optional)`
- Path variables: None declared
- Query parameters: None declared
- Request body type: `ForgotPasswordDetailsRequest`
- Java response type: `Mono< ResponseEntity< MutationResponse< ForgotPasswordWorkflowResultResponse<ForgotPasswordOtpDataResponse>>>>`
- Success/status selection: Runtime-dependent via exact selector(s): `ForgotPasswordHttpResponses.toResponse`, `CsrfHttpResponses.missingToken`
- Validation annotations: None declared at method boundary
- Immediate call chain: WEB_COLLABORATOR `ForgotPasswordDetailsOtpWebMapper.map`; APPLICATION_INPUT_PORT `ProcessForgotPasswordDetailsOtpUseCase.process` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/detailsotp/ForgotPasswordDetailsOtpOrchestrationService.java; WEB_COLLABORATOR `ForgotPasswordDetailsOtpWebMapper.map`; WEB_COLLABORATOR `ForgotPasswordDetailsOtpWebMapper.map`
- Test-evidence classification: `CLASS_REFERENCE_ONLY`, `CONTROLLER_METHOD_BEHAVIOR`, `SECURITY_TEST`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/ForgotPasswordCancelArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/ForgotPasswordEndpointSecurityContractSubtask009Test.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043FlowSpecificCsrfHttpMappingTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordCancelControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordCompleteControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordDetailsControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordIdentityControllerTest.java`

### `POST /api/v1/auth/forgot-password/details-check`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthForgotPasswordController#details`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `X-XSRF-TOKEN (optional)`, `Origin (optional)`, `Referer (optional)`, `RequestCorrelation.REQUEST_ID_HEADER (optional)`, `Accept-Language (optional)`, `Idempotency-Key (optional)`
- Cookies: `auth_flow_sid (optional)`
- Path variables: None declared
- Query parameters: None declared
- Request body type: `ForgotPasswordDetailsRequest`
- Java response type: `Mono< ResponseEntity< MutationResponse< ForgotPasswordWorkflowResultResponse<ForgotPasswordOtpDataResponse>>>>`
- Success/status selection: Runtime-dependent via exact selector(s): `ForgotPasswordHttpResponses.toResponse`, `CsrfHttpResponses.missingToken`
- Validation annotations: None declared at method boundary
- Immediate call chain: WEB_COLLABORATOR `ForgotPasswordDetailsOtpWebMapper.map`; APPLICATION_INPUT_PORT `ProcessForgotPasswordDetailsOtpUseCase.process` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/detailsotp/ForgotPasswordDetailsOtpOrchestrationService.java; WEB_COLLABORATOR `ForgotPasswordDetailsOtpWebMapper.map`; WEB_COLLABORATOR `ForgotPasswordDetailsOtpWebMapper.map`
- Test-evidence classification: `CLASS_REFERENCE_ONLY`, `CONTROLLER_METHOD_BEHAVIOR`, `SECURITY_TEST`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/ForgotPasswordCancelArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/ForgotPasswordEndpointSecurityContractSubtask009Test.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043FlowSpecificCsrfHttpMappingTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordCancelControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordCompleteControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordDetailsControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordIdentityControllerTest.java`

### `POST /api/v1/auth/forgot-password/identity-check`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthForgotPasswordController#check`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `X-XSRF-TOKEN (optional)`, `Origin (optional)`, `Referer (optional)`, `RequestCorrelation.REQUEST_ID_HEADER (optional)`, `Accept-Language (optional)`
- Cookies: `auth_flow_sid (optional)`
- Path variables: None declared
- Query parameters: None declared
- Request body type: `ForgotPasswordIdentityCheckRequest`
- Java response type: `Mono< ResponseEntity< MutationResponse<ForgotPasswordWorkflowResultResponse<Map<String, Object>>>>>`
- Success/status selection: Runtime-dependent via exact selector(s): `ForgotPasswordHttpResponses.toResponse`, `CsrfHttpResponses.missingToken`
- Validation annotations: None declared at method boundary
- Immediate call chain: WEB_COLLABORATOR `ForgotPasswordIdentityWebMapper.response`; APPLICATION_INPUT_PORT `VerifyForgotPasswordIdentityUseCase.verify` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/identity/VerifyForgotPasswordIdentityService.java; WEB_COLLABORATOR `ForgotPasswordIdentityWebMapper.command`; WEB_COLLABORATOR `ForgotPasswordIdentityWebMapper.response`; WEB_COLLABORATOR `ForgotPasswordIdentityWebMapper.response`
- Test-evidence classification: `CONTROLLER_METHOD_BEHAVIOR`, `CLASS_REFERENCE_ONLY`, `SECURITY_TEST`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/ForgotPasswordCancelArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/ForgotPasswordEndpointSecurityContractSubtask009Test.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043FlowSpecificCsrfHttpMappingTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordCancelControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordCompleteControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordDetailsControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordIdentityControllerTest.java`

### `POST /api/v1/auth/forgot-password/otp/resend`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthForgotPasswordOtpController#resend`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordOtpController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `X-XSRF-TOKEN (optional)`, `Origin (optional)`, `Referer (optional)`, `RequestCorrelation.REQUEST_ID_HEADER (optional)`, `Accept-Language (optional)`, `Idempotency-Key (optional)`
- Cookies: `auth_flow_sid (optional)`
- Path variables: None declared
- Query parameters: None declared
- Request body type: `NONE`
- Java response type: `Mono< ResponseEntity< MutationResponse< ForgotPasswordWorkflowResultResponse<ForgotPasswordOtpDataResponse>>>>`
- Success/status selection: Runtime-dependent via exact selector(s): `ForgotPasswordHttpResponses.toResponse`, `CsrfHttpResponses.missingToken`
- Validation annotations: None declared at method boundary
- Immediate call chain: WEB_COLLABORATOR `ForgotPasswordOtpResendWebMapper.missingIdempotencyKey`; WEB_COLLABORATOR `ForgotPasswordOtpResendWebMapper.systemUnavailable`; APPLICATION_INPUT_PORT `ResendForgotPasswordOtpUseCase.resend` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/otp/resend/ResendForgotPasswordOtpService.java; WEB_COLLABORATOR `ForgotPasswordOtpResendWebMapper.systemUnavailable`; WEB_COLLABORATOR `ForgotPasswordOtpResendWebMapper.systemUnavailable`
- Test-evidence classification: `CLASS_REFERENCE_ONLY`, `CONTROLLER_METHOD_BEHAVIOR`, `SECURITY_TEST`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/ForgotPasswordEndpointSecurityContractSubtask009Test.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043FlowSpecificCsrfHttpMappingTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordOtpResendControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordOtpResendEndpointArchitectureFailFirstTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordOtpVerifyControllerTest.java`

### `POST /api/v1/auth/forgot-password/otp/verify`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthForgotPasswordOtpController#verify`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordOtpController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `X-XSRF-TOKEN (optional)`, `Idempotency-Key (optional)`, `RequestCorrelation.REQUEST_ID_HEADER (optional)`, `Accept-Language (optional)`, `Origin (optional)`, `Referer (optional)`
- Cookies: `auth_flow_sid (optional)`
- Path variables: None declared
- Query parameters: None declared
- Request body type: `ForgotPasswordOtpVerifyRequest`
- Java response type: `Mono< ResponseEntity< MutationResponse< ForgotPasswordWorkflowResultResponse<ForgotPasswordPasswordSetupDataResponse>>>>`
- Success/status selection: Runtime-dependent via exact selector(s): `ForgotPasswordHttpResponses.toResponse`, `CsrfHttpResponses.missingToken`
- Validation annotations: None declared at method boundary
- Immediate call chain: WEB_COLLABORATOR `ForgotPasswordOtpVerifyWebMapper.missingAuthFlowCookie`; WEB_COLLABORATOR `ForgotPasswordOtpVerifyWebMapper.missingIdempotencyKey`; WEB_COLLABORATOR `ForgotPasswordOtpVerifyWebMapper.missingCode`; APPLICATION_INPUT_PORT `VerifyForgotPasswordOtpUseCase.verify` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/otp/verify/VerifyForgotPasswordOtpService.java; WEB_COLLABORATOR `ForgotPasswordOtpVerifyWebMapper.systemUnavailable`; WEB_COLLABORATOR `ForgotPasswordOtpVerifyWebMapper.systemUnavailable`
- Test-evidence classification: `CLASS_REFERENCE_ONLY`, `CONTROLLER_METHOD_BEHAVIOR`, `SECURITY_TEST`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/ForgotPasswordEndpointSecurityContractSubtask009Test.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043FlowSpecificCsrfHttpMappingTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordOtpResendControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordOtpResendEndpointArchitectureFailFirstTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordOtpVerifyControllerTest.java`

### `POST /api/v1/auth/forgot-password/start`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthForgotPasswordController#startForgotPassword`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `Idempotency-Key (optional)`, `RequestCorrelation.REQUEST_ID_HEADER (optional)`, `Accept-Language (optional)`
- Cookies: `auth_flow_sid (optional)`
- Path variables: None declared
- Query parameters: None declared
- Request body type: `ForgotPasswordStartRequest`
- Java response type: `Mono< ResponseEntity< MutationResponse< ForgotPasswordWorkflowResultResponse<ForgotPasswordStartDataResponse>>>>`
- Success/status selection: Runtime-dependent via exact selector(s): `ForgotPasswordHttpResponses.toResponse`
- Validation annotations: None declared at method boundary
- Immediate call chain: WEB_COLLABORATOR `ForgotPasswordStartWebMapper.missingIdempotencyKey`; WEB_COLLABORATOR `ForgotPasswordStartWebMapper.invalidRequest`; APPLICATION_INPUT_PORT `StartForgotPasswordUseCase.start` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/start/StartForgotPasswordService.java; WEB_COLLABORATOR `ForgotPasswordStartWebMapper.toCommand`; WEB_COLLABORATOR `AuthFlowCookieWriter.writeAuthFlowCookie`; WEB_COLLABORATOR `AuthFlowCookieWriter.clearAuthFlowCookie`; WEB_COLLABORATOR `XsrfCookieWriter.clearXsrfCookie`; WEB_COLLABORATOR `ForgotPasswordStartWebMapper.toMutationResponse`; WEB_COLLABORATOR `ForgotPasswordStartWebMapper.systemUnavailable`; WEB_COLLABORATOR `ForgotPasswordStartWebMapper.invalidRequest`
- Test-evidence classification: `CLASS_REFERENCE_ONLY`, `CONTROLLER_METHOD_BEHAVIOR`, `SECURITY_TEST`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/ForgotPasswordCancelArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/ForgotPasswordEndpointSecurityContractSubtask009Test.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043FlowSpecificCsrfHttpMappingTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordCancelControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordCompleteControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordDetailsControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordIdentityControllerTest.java`

### `POST /api/v1/auth/login`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.auth.login.LoginController#startLogin`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/LoginController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `Idempotency-Key (optional)`, `RequestCorrelation.REQUEST_ID_HEADER (optional)`, `Accept-Language (optional)`
- Cookies: None declared
- Path variables: None declared
- Query parameters: None declared
- Request body type: `LoginStartRequest`
- Java response type: `Mono<ResponseEntity<MutationResponse<LoginWorkflowResultResponse<LoginStartDataResponse>>>>`
- Success/status selection: Runtime-dependent via exact selector(s): `LoginHttpResponses.toResponse`
- Validation annotations: None declared at method boundary
- Immediate call chain: WEB_COLLABORATOR `LoginStartWebMapper.missingRequestId`; WEB_COLLABORATOR `LoginStartWebMapper.missingIdempotencyKey`; WEB_COLLABORATOR `LoginStartWebMapper.invalidRequest`; APPLICATION_INPUT_PORT `StartLoginUseCase.start` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/start/StartLoginService.java; WEB_COLLABORATOR `LoginStartWebMapper.toCommand`; WEB_COLLABORATOR `AuthFlowCookieWriter.writeAuthFlowCookie`; WEB_COLLABORATOR `LoginStartWebMapper.toMutationResponse`; WEB_COLLABORATOR `LoginStartWebMapper.systemUnavailable`; WEB_COLLABORATOR `LoginStartWebMapper.invalidRequest`
- Test-evidence classification: `FOCUSED_MAPPING_OR_HTTP_CONTRACT`, `CLASS_REFERENCE_ONLY`, `CONTROLLER_METHOD_BEHAVIOR`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043LoginOtpSendEndpointArchitectureTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043LoginStartFinalEvidenceTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043LoginWebEndpointArchitectureTest.java`, `src/test/java/com/bct/ngtpa/apiservice/integration/login/LoginStartEvidenceIntegrationTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthLoginControllerTest.java`

### `POST /api/v1/auth/login/cancel`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.auth.login.LoginController#cancel`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/LoginController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `X-XSRF-TOKEN (optional)`, `X-Request-Id (optional)`, `Origin (optional)`, `Referer (optional)`
- Cookies: `auth_flow_sid (optional)`
- Path variables: None declared
- Query parameters: None declared
- Request body type: `NONE`
- Java response type: `Mono< ResponseEntity<MutationResponse<LoginWorkflowResultResponse<CancelLoginDataResponse>>>>`
- Success/status selection: Runtime-dependent via exact selector(s): `LoginHttpResponses.toResponse`, `CsrfHttpResponses.missingToken`
- Validation annotations: None declared at method boundary
- Immediate call chain: WEB_COLLABORATOR `CancelLoginWebMapper.missingSession`; APPLICATION_INPUT_PORT `CancelLoginUseCase.cancel` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/cancel/CancelLoginService.java; WEB_COLLABORATOR `AuthFlowCookieWriter.clearAuthFlowCookie`; WEB_COLLABORATOR `XsrfCookieWriter.clearXsrfCookie`; WEB_COLLABORATOR `CancelLoginWebMapper.toMutationResponse`; WEB_COLLABORATOR `CancelLoginWebMapper.missingCsrf`
- Test-evidence classification: `CLASS_REFERENCE_ONLY`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043FlowSpecificCsrfHttpMappingTest.java`

### `POST /api/v1/auth/login/otp/resend`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.auth.login.LoginController#resend`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/LoginController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `X-XSRF-TOKEN (optional)`, `Idempotency-Key (optional)`, `X-Request-Id (optional)`, `Accept-Language (optional)`, `Origin (optional)`, `Referer (optional)`
- Cookies: `auth_flow_sid (optional)`
- Path variables: None declared
- Query parameters: None declared
- Request body type: `NONE`
- Java response type: `Mono< ResponseEntity<MutationResponse<LoginWorkflowResultResponse<LoginOtpSendDataResponse>>>>`
- Success/status selection: Runtime-dependent via exact selector(s): `CsrfHttpResponses.missingToken`, `ResponseEntity.status(entity.getStatusCode()`
- Validation annotations: None declared at method boundary
- Immediate call chain: APPLICATION_INPUT_PORT `ResendLoginOtpUseCase.resend` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/otp/resend/IdempotentLoginOtpResendService.java, src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/otp/resend/LoginOtpResendService.java
- Test-evidence classification: `CONTROLLER_METHOD_BEHAVIOR`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043FlowSpecificCsrfHttpMappingTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043LoginOtpResendEndpointArchitectureTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043LoginOtpSendEndpointArchitectureTest.java`

### `POST /api/v1/auth/login/otp/send`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.auth.login.LoginController#send`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/LoginController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `X-XSRF-TOKEN (optional)`, `Idempotency-Key (optional)`, `X-Request-Id (optional)`, `Accept-Language (optional)`, `Origin (optional)`, `Referer (optional)`
- Cookies: `auth_flow_sid (optional)`
- Path variables: None declared
- Query parameters: None declared
- Request body type: `LoginOtpSendRequest`
- Java response type: `Mono< ResponseEntity<MutationResponse<LoginWorkflowResultResponse<LoginOtpSendDataResponse>>>>`
- Success/status selection: Runtime-dependent via exact selector(s): `CsrfHttpResponses.missingToken`, `LoginHttpResponses.toResponse`
- Validation annotations: None declared at method boundary
- Immediate call chain: APPLICATION_INPUT_PORT `SendLoginOtpUseCase.send` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/otp/IdempotentLoginOtpSendService.java, src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/otp/LoginOtpSendService.java; WEB_COLLABORATOR `LoginOtpSendWebMapper.missing`
- Test-evidence classification: `CONTROLLER_METHOD_BEHAVIOR`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043FlowSpecificCsrfHttpMappingTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043LoginOtpResendEndpointArchitectureTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043LoginOtpSendEndpointArchitectureTest.java`

### `POST /api/v1/auth/login/otp/verify`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.auth.login.LoginController#verify`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/LoginController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `X-XSRF-TOKEN (optional)`, `Idempotency-Key (optional)`, `RequestCorrelation.REQUEST_ID_HEADER (optional)`, `RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER (optional)`, `Origin (optional)`, `Referer (optional)`
- Cookies: `auth_flow_sid (optional)`
- Path variables: None declared
- Query parameters: None declared
- Request body type: `LoginOtpVerifyRequest`
- Java response type: `Mono< ResponseEntity<MutationResponse<LoginWorkflowResultResponse<LoginOtpVerifyDataResponse>>>>`
- Success/status selection: Runtime-dependent via exact selector(s): `CsrfHttpResponses.missingToken`, `LoginHttpResponses.toResponse`
- Validation annotations: None declared at method boundary
- Immediate call chain: APPLICATION_INPUT_PORT `VerifyLoginOtpUseCase.verify` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/otp/verify/LoginOtpVerifyService.java; WEB_COLLABORATOR `AuthenticatedSessionCookieWriter.write`; WEB_COLLABORATOR `AuthFlowCookieWriter.clearAuthFlowCookie`; WEB_COLLABORATOR `XsrfCookieWriter.clearXsrfCookie`; WEB_COLLABORATOR `LoginOtpVerifyWebMapper.toMutationResponse`
- Test-evidence classification: `CONTROLLER_METHOD_BEHAVIOR`, `CLASS_REFERENCE_ONLY`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043AuthenticatedSidTransportArchitectureTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043FlowSpecificCsrfHttpMappingTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043LoginAuthenticatedSessionTransitionContractArchitectureTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthLoginOtpVerifyAuthenticatedTransportTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthLoginOtpVerifyCookiePublicationTest.java`

### `POST /api/v1/auth/logout`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.auth.logout.LogoutController#logout`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/logout/LogoutController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `RequestHeaderContextKeys.XSRF_TOKEN_HEADER (optional)`, `RequestCorrelation.REQUEST_ID_HEADER (optional)`
- Cookies: None declared
- Path variables: None declared
- Query parameters: None declared
- Request body type: `NONE`
- Java response type: `Mono<ResponseEntity<MutationResponse<Void>>>`
- Success/status selection: Runtime-dependent via exact selector(s): `ResponseEntity.ok(mapper.toResponse(result)`, `ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)`, `HttpStatus.SERVICE_UNAVAILABLE`
- Validation annotations: None declared at method boundary
- Immediate call chain: WEB_COLLABORATOR `AuthenticatedSessionCookieWriter.cookieName`; APPLICATION_INPUT_PORT `ValidateAuthenticatedSessionUseCase.validate` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/session/authenticated/AuthenticatedSessionValidationService.java; APPLICATION_INPUT_PORT `AuthenticatedLogoutUseCase.logout` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/logout/AuthenticatedLogoutService.java; WEB_COLLABORATOR `AuthenticatedLogoutWebMapper.toResponse`; WEB_COLLABORATOR `AuthenticatedLogoutWebMapper.toResponse`
- Test-evidence classification: `CONTROLLER_METHOD_BEHAVIOR`, `FOCUSED_MAPPING_OR_HTTP_CONTRACT`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043AuthenticatedLogoutHttpArchitectureTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043FlowSpecificCsrfHttpMappingTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthLogoutControllerTest.java`

### `POST /api/v1/auth/registration/cancel`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationController#cancelRegistration`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthRegistrationController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `X-XSRF-TOKEN (optional)`, `X-Request-Id (optional)`, `Accept-Language (optional)`, `Origin (optional)`, `Referer (optional)`
- Cookies: `auth_flow_sid (optional)`
- Path variables: None declared
- Query parameters: None declared
- Request body type: `NONE`
- Java response type: `Mono< ResponseEntity< MutationResponse<RegistrationWorkflowResultResponse<CancelRegistrationResponse>>>>`
- Success/status selection: Runtime-dependent via exact selector(s): `RegistrationHttpResponses.toResponse`, `CsrfHttpResponses.missingToken`
- Validation annotations: None declared at method boundary
- Immediate call chain: WEB_COLLABORATOR `AuthFlowCookieWriter.clearAuthFlowCookie`; WEB_COLLABORATOR `XsrfCookieWriter.clearXsrfCookie`; WEB_COLLABORATOR `CancelRegistrationWebMapper.missingSession`; APPLICATION_INPUT_PORT `CancelRegistrationUseCase.cancel` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/cancel/CancelRegistrationService.java; WEB_COLLABORATOR `CancelRegistrationWebMapper.toCommand`; WEB_COLLABORATOR `AuthFlowCookieWriter.clearAuthFlowCookie`; WEB_COLLABORATOR `XsrfCookieWriter.clearXsrfCookie`; WEB_COLLABORATOR `CancelRegistrationWebMapper.toResponse`; WEB_COLLABORATOR `CancelRegistrationWebMapper.missingCsrf`
- Test-evidence classification: `CLASS_REFERENCE_ONLY`, `FOCUSED_MAPPING_OR_HTTP_CONTRACT`, `CONTROLLER_METHOD_BEHAVIOR`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/AuthRegistrationControllerArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/AuthRegistrationOtpVerifyControllerArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/P6RegistrationStartWorkflowContractArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/P8CaptchaArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RegistrationCancelWebContractGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RegistrationCompleteArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RegistrationHttpAndCookieSlidingContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RequiredRequestIdRegistrationContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043FlowSpecificCsrfHttpMappingTest.java`, `src/test/java/com/bct/ngtpa/apiservice/integration/registration/RegistrationStartWorkflowIntegrationTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/AuthRegistrationCompleteCancelControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/AuthRegistrationControllerCaptchaGateTest.java`

### `POST /api/v1/auth/registration/complete`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationController#completeRegistration`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthRegistrationController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `X-XSRF-TOKEN (optional)`, `Idempotency-Key (optional)`, `X-Request-Id (optional)`, `Accept-Language (optional)`, `Origin (optional)`, `Referer (optional)`
- Cookies: `auth_flow_sid (optional)`
- Path variables: None declared
- Query parameters: None declared
- Request body type: `CompleteRegistrationRequest`
- Java response type: `Mono< ResponseEntity< MutationResponse<RegistrationWorkflowResultResponse<CompleteRegistrationResponse>>>>`
- Success/status selection: Runtime-dependent via exact selector(s): `RegistrationHttpResponses.toResponse`, `CsrfHttpResponses.missingToken`
- Validation annotations: None declared at method boundary
- Immediate call chain: WEB_COLLABORATOR `CompleteRegistrationWebMapper.missing`; WEB_COLLABORATOR `CompleteRegistrationWebMapper.missing`; WEB_COLLABORATOR `CompleteRegistrationWebMapper.missing`; APPLICATION_INPUT_PORT `CompleteRegistrationUseCase.complete` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/complete/CompleteRegistrationService.java; WEB_COLLABORATOR `CompleteRegistrationWebMapper.toCommand`; WEB_COLLABORATOR `AuthFlowCookieWriter.clearAuthFlowCookie`; WEB_COLLABORATOR `AuthFlowCookieWriter.writeAuthFlowCookie`; WEB_COLLABORATOR `CompleteRegistrationWebMapper.toResponse`; WEB_COLLABORATOR `CompleteRegistrationWebMapper.missing`
- Test-evidence classification: `CLASS_REFERENCE_ONLY`, `FOCUSED_MAPPING_OR_HTTP_CONTRACT`, `CONTROLLER_METHOD_BEHAVIOR`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/AuthRegistrationControllerArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/AuthRegistrationOtpVerifyControllerArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/P6RegistrationStartWorkflowContractArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/P8CaptchaArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RegistrationCancelWebContractGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RegistrationCompleteArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RegistrationHttpAndCookieSlidingContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RequiredRequestIdRegistrationContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043FlowSpecificCsrfHttpMappingTest.java`, `src/test/java/com/bct/ngtpa/apiservice/integration/registration/RegistrationStartWorkflowIntegrationTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/AuthRegistrationCompleteCancelControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/AuthRegistrationControllerCaptchaGateTest.java`

### `POST /api/v1/auth/registration/otp/resend`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationOtpController#resendOtp`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthRegistrationOtpController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `X-XSRF-TOKEN (optional)`, `Idempotency-Key (optional)`, `X-Request-Id (optional)`, `Accept-Language (optional)`, `Origin (optional)`, `Referer (optional)`
- Cookies: `auth_flow_sid (optional)`
- Path variables: None declared
- Query parameters: None declared
- Request body type: `NONE`
- Java response type: `Mono< ResponseEntity< MutationResponse<RegistrationWorkflowResultResponse<RegistrationOtpResendResponse>>>>`
- Success/status selection: Runtime-dependent via exact selector(s): `RegistrationHttpResponses.toResponse`, `CsrfHttpResponses.missingToken`
- Validation annotations: None declared at method boundary
- Immediate call chain: WEB_COLLABORATOR `RegistrationOtpResendWebMapper.missingAuthFlowCookieResponse`; WEB_COLLABORATOR `RegistrationOtpResendWebMapper.missingIdempotencyKeyResponse`; WEB_COLLABORATOR `RegistrationIdempotencyProperties.otpResendTtl`; WEB_COLLABORATOR `RegistrationOtpResendWebMapper.missingAuthFlowCookieResponse`; APPLICATION_INPUT_PORT `ResendRegistrationOtpUseCase.resend` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/otp/ResendRegistrationOtpService.java; WEB_COLLABORATOR `AuthFlowCookieWriter.writeAuthFlowCookie`; WEB_COLLABORATOR `RegistrationOtpResendWebMapper.toMutationResponse`
- Test-evidence classification: `CLASS_REFERENCE_ONLY`, `CONTROLLER_METHOD_BEHAVIOR`, `SECURITY_TEST`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/AuthRegistrationOtpResendControllerArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/P7RegistrationOtpArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RegistrationHardcodedPolicyArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RegistrationHttpAndCookieSlidingContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RegistrationRuntimeSemanticRegressionTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RequiredRequestIdRegistrationContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043FlowSpecificCsrfHttpMappingTest.java`, `src/test/java/com/bct/ngtpa/apiservice/auth/registration/P7RegistrationOtpIntegrationTest.java`, `src/test/java/com/bct/ngtpa/apiservice/auth/registration/P8CaptchaSecurityBoundaryGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/AuthRegistrationOtpResendControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/AuthRegistrationOtpVerifyControllerTest.java`

### `POST /api/v1/auth/registration/otp/verify`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationOtpController#verifyOtp`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthRegistrationOtpController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `X-XSRF-TOKEN (optional)`, `Idempotency-Key (optional)`, `X-Request-Id (optional)`, `Accept-Language (optional)`, `Origin (optional)`, `Referer (optional)`
- Cookies: `auth_flow_sid (optional)`
- Path variables: None declared
- Query parameters: None declared
- Request body type: `RegistrationOtpVerifyRequest`
- Java response type: `Mono< ResponseEntity< MutationResponse<RegistrationWorkflowResultResponse<RegistrationOtpVerifyResponse>>>>`
- Success/status selection: Runtime-dependent via exact selector(s): `RegistrationHttpResponses.toResponse`, `CsrfHttpResponses.missingToken`
- Validation annotations: None declared at method boundary
- Immediate call chain: WEB_COLLABORATOR `RegistrationOtpVerifyWebMapper.missingAuthFlowCookieResponse`; WEB_COLLABORATOR `RegistrationOtpVerifyWebMapper.missingIdempotencyKeyResponse`; WEB_COLLABORATOR `RegistrationOtpVerifyWebMapper.missingCodeResponse`; WEB_COLLABORATOR `RegistrationIdempotencyProperties.otpVerifyTtl`; WEB_COLLABORATOR `RegistrationOtpVerifyWebMapper.missingAuthFlowCookieResponse`; APPLICATION_INPUT_PORT `VerifyRegistrationOtpUseCase.verify` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/otp/VerifyRegistrationOtpService.java; WEB_COLLABORATOR `AuthFlowCookieWriter.writeAuthFlowCookie`; WEB_COLLABORATOR `RegistrationOtpVerifyWebMapper.toMutationResponse`
- Test-evidence classification: `CLASS_REFERENCE_ONLY`, `CONTROLLER_METHOD_BEHAVIOR`, `SECURITY_TEST`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/AuthRegistrationOtpResendControllerArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/P7RegistrationOtpArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RegistrationHardcodedPolicyArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RegistrationHttpAndCookieSlidingContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RegistrationRuntimeSemanticRegressionTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RequiredRequestIdRegistrationContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043FlowSpecificCsrfHttpMappingTest.java`, `src/test/java/com/bct/ngtpa/apiservice/auth/registration/P7RegistrationOtpIntegrationTest.java`, `src/test/java/com/bct/ngtpa/apiservice/auth/registration/P8CaptchaSecurityBoundaryGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/AuthRegistrationOtpResendControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/AuthRegistrationOtpVerifyControllerTest.java`

### `POST /api/v1/auth/registration/start`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationController#startRegistration`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthRegistrationController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `Idempotency-Key (optional)`, `X-Request-Id (optional)`, `Accept-Language (optional)`
- Cookies: `auth_flow_sid (optional)`
- Path variables: None declared
- Query parameters: None declared
- Request body type: `RegistrationStartRequest`
- Java response type: `Mono< ResponseEntity< MutationResponse< RegistrationWorkflowResultResponse<RegistrationStartWorkflowDataResponse>>>>`
- Success/status selection: Runtime-dependent via exact selector(s): `RegistrationHttpResponses.toResponse`
- Validation annotations: None declared at method boundary
- Immediate call chain: WEB_COLLABORATOR `RegistrationStartWebMapper.missingIdempotencyKeyResponse`; APPLICATION_INPUT_PORT `StartRegistrationUseCase.start` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/StartRegistrationService.java; WEB_COLLABORATOR `RegistrationStartWebMapper.toCommand`; WEB_COLLABORATOR `AuthFlowCookieWriter.writeAuthFlowCookie`; WEB_COLLABORATOR `RegistrationStartWebMapper.toMutationResponse`
- Test-evidence classification: `CLASS_REFERENCE_ONLY`, `FOCUSED_MAPPING_OR_HTTP_CONTRACT`, `CONTROLLER_METHOD_BEHAVIOR`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/AuthRegistrationControllerArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/P6RegistrationStartWorkflowContractArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/P8CaptchaArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RegistrationCancelWebContractGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RegistrationCompleteArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RegistrationHttpAndCookieSlidingContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RequiredRequestIdRegistrationContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043FlowSpecificCsrfHttpMappingTest.java`, `src/test/java/com/bct/ngtpa/apiservice/session/P4ASessionManagementFoundationIntegrationTest.java`, `src/test/java/com/bct/ngtpa/apiservice/integration/registration/RegistrationStartWorkflowIntegrationTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/session/config/AuthFlowSessionPropertiesBindingTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/AuthRegistrationCompleteCancelControllerTest.java`

### `POST /api/v1/auth/registration/username/check`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationUsernameController#checkUsername`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthRegistrationUsernameController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `X-XSRF-TOKEN (optional)`, `X-Request-Id (optional)`, `Accept-Language (optional)`, `Origin (optional)`, `Referer (optional)`
- Cookies: `auth_flow_sid (optional)`
- Path variables: None declared
- Query parameters: None declared
- Request body type: `RegistrationUsernameCheckRequest`
- Java response type: `Mono< ResponseEntity< MutationResponse< RegistrationWorkflowResultResponse<RegistrationUsernameCheckResponse>>>>`
- Success/status selection: Runtime-dependent via exact selector(s): `RegistrationHttpResponses.toResponse`, `CsrfHttpResponses.missingToken`
- Validation annotations: None declared at method boundary
- Immediate call chain: WEB_COLLABORATOR `RegistrationUsernameCheckWebMapper.missingAuthFlowCookieResponse`; WEB_COLLABORATOR `RegistrationUsernameCheckWebMapper.missingRequestBodyResponse`; WEB_COLLABORATOR `RegistrationUsernameCheckWebMapper.toCommand`; WEB_COLLABORATOR `RegistrationUsernameCheckWebMapper.missingAuthFlowCookieResponse`; APPLICATION_INPUT_PORT `CheckRegistrationUsernameUseCase.check` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/username/CheckRegistrationUsernameService.java; WEB_COLLABORATOR `AuthFlowCookieWriter.writeAuthFlowCookie`; WEB_COLLABORATOR `RegistrationUsernameCheckWebMapper.toMutationResponse`
- Test-evidence classification: `FOCUSED_MAPPING_OR_HTTP_CONTRACT`, `CLASS_REFERENCE_ONLY`, `CONTROLLER_METHOD_BEHAVIOR`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/AuthRegistrationOtpVerifyControllerArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/AuthRegistrationUsernameControllerArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RegistrationHttpAndCookieSlidingContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/RequiredRequestIdRegistrationContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043FlowSpecificCsrfHttpMappingTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/AuthRegistrationUsernameControllerTest.java`

### `POST /api/v1/auth/username-recovery/cancel`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryController#cancel`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/usernamerecovery/UsernameRecoveryController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `Idempotency-Key (optional)`, `X-XSRF-TOKEN (optional)`, `Origin (optional)`, `Referer (optional)`, `Accept-Language (optional)`, `RequestCorrelation.REQUEST_ID_HEADER (optional)`
- Cookies: `auth_flow_sid (optional)`
- Path variables: None declared
- Query parameters: None declared
- Request body type: `NONE`
- Java response type: `Mono<ResponseEntity<MutationResponse<UsernameRecoveryWorkflowResultResponse<Void>>>>`
- Success/status selection: Runtime-dependent via exact selector(s): `CookieHttpResponses.missingCookie`, `IdempotencyHttpResponses.missingKey`, `CsrfHttpResponses.missingToken`
- Validation annotations: None declared at method boundary
- Immediate call chain: WEB_COLLABORATOR `UsernameRecoveryCancelResponsePolicy.toPolicy`; APPLICATION_INPUT_PORT `CancelUsernameRecoveryUseCase.cancel` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/usernamerecovery/CancelUsernameRecoveryService.java; WEB_COLLABORATOR `HttpSessionCookieAdapter.apply`; WEB_COLLABORATOR `UsernameRecoveryCookieWriter.clearAuthFlowCookie`; WEB_COLLABORATOR `UsernameRecoveryCancelResponsePolicy.toPolicy`; WEB_COLLABORATOR `UsernameRecoveryCancelResponsePolicy.toPolicy`
- Test-evidence classification: `CONTROLLER_METHOD_BEHAVIOR`, `SECURITY_TEST`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/UsernameRecoveryControllerArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/UsernameRecoveryControllerCompositionIntegrationTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/UsernameRecoveryControllerPrerequisiteArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthUsernameRecoveryControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/UsernameRecoveryControllerContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/UsernameRecoveryControllerSecurityTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/UsernameRecoveryRequestCancelPublicContractIntegrationTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/UsernameRecoveryStartPublicContractIntegrationTest.java`

### `POST /api/v1/auth/username-recovery/request`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryController#request`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/usernamerecovery/UsernameRecoveryController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `Idempotency-Key (optional)`, `X-XSRF-TOKEN (optional)`, `SCHEME_REF_HEADER (optional)`, `Origin (optional)`, `Referer (optional)`, `Accept-Language (optional)`, `RequestCorrelation.REQUEST_ID_HEADER (optional)`
- Cookies: `auth_flow_sid (optional)`
- Path variables: None declared
- Query parameters: None declared
- Request body type: `NONE`
- Java response type: `Mono< ResponseEntity< MutationResponse< UsernameRecoveryWorkflowResultResponse<UsernameRecoveryRequestDataResponse>>>>`
- Success/status selection: Runtime-dependent via exact selector(s): `CookieHttpResponses.missingCookie`, `IdempotencyHttpResponses.missingKey`, `CsrfHttpResponses.missingToken`, `RequestHeaderHttpResponses.missingRequiredHeader`
- Validation annotations: None declared at method boundary
- Immediate call chain: WEB_COLLABORATOR `UsernameRecoveryRequestResponsePolicy.toPolicy`; WEB_COLLABORATOR `UsernameRecoveryRequestResponsePolicy.toPolicy`; APPLICATION_INPUT_PORT `RequestUsernameRecoveryUseCase.request` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/usernamerecovery/RequestUsernameRecoveryService.java; WEB_COLLABORATOR `UsernameRecoveryRequestResponsePolicy.toPolicy`; WEB_COLLABORATOR `UsernameRecoveryRequestResponsePolicy.toPolicy`
- Test-evidence classification: `CONTROLLER_METHOD_BEHAVIOR`, `SECURITY_TEST`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/UsernameRecoveryControllerArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/UsernameRecoveryControllerCompositionIntegrationTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/UsernameRecoveryControllerPrerequisiteArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthUsernameRecoveryControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/UsernameRecoveryControllerContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/UsernameRecoveryControllerSecurityTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/UsernameRecoveryRequestCancelPublicContractIntegrationTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/UsernameRecoveryStartPublicContractIntegrationTest.java`

### `POST /api/v1/auth/username-recovery/start`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryController#start`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/usernamerecovery/UsernameRecoveryController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `Accept-Language (optional)`, `RequestCorrelation.REQUEST_ID_HEADER (optional)`, `Idempotency-Key (optional)`
- Cookies: None declared
- Path variables: None declared
- Query parameters: None declared
- Request body type: `UsernameRecoveryStartRequest`
- Java response type: `Mono< ResponseEntity< MutationResponse< UsernameRecoveryWorkflowResultResponse<UsernameRecoveryStartDataResponse>>>>`
- Success/status selection: Runtime-dependent via exact selector(s): `IdempotencyHttpResponses.missingKey`
- Validation annotations: None declared at method boundary
- Immediate call chain: WEB_COLLABORATOR `UsernameRecoveryStartWebMapper.toCommand`; WEB_COLLABORATOR `UsernameRecoveryStartResponsePolicy.toPolicy`; APPLICATION_INPUT_PORT `StartUsernameRecoveryUseCase.start` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/usernamerecovery/StartUsernameRecoveryService.java; WEB_COLLABORATOR `HttpSessionCookieAdapter.apply`; WEB_COLLABORATOR `UsernameRecoveryCookieWriter.authFlowCookie`; WEB_COLLABORATOR `UsernameRecoveryStartResponsePolicy.toPolicy`; WEB_COLLABORATOR `UsernameRecoveryStartResponsePolicy.toPolicy`
- Test-evidence classification: `CLASS_REFERENCE_ONLY`, `CONTROLLER_METHOD_BEHAVIOR`, `FOCUSED_MAPPING_OR_HTTP_CONTRACT`, `SECURITY_TEST`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/UsernameRecoveryControllerArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/UsernameRecoveryControllerCompositionIntegrationTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/UsernameRecoveryControllerPrerequisiteArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthUsernameRecoveryControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/UsernameRecoveryControllerContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/UsernameRecoveryControllerSecurityTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/UsernameRecoveryRequestCancelPublicContractIntegrationTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/UsernameRecoveryStartPublicContractIntegrationTest.java`

### `GET /api/v1/contributions`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.contribution.ContributionController#getContributionSummary`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/contribution/ContributionController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `DECLARED (optional)`
- Cookies: None declared
- Path variables: None declared
- Query parameters: `fromDate (required)`, `toDate (required)`, `page (optional)`, `pageSize (optional)`
- Request body type: `NONE`
- Java response type: `Mono<ContributionListResponse>`
- Success/status selection: Runtime-dependent; no direct status selector statically resolved in controller method
- Validation annotations: None declared at method boundary
- Immediate call chain: WEB_COLLABORATOR `CurrentPortalAccessContextResolver.current`; APPLICATION_INPUT_PORT `GetContributionSummaryUseCase.execute` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/GetContributionSummaryService.java; WEB_COLLABORATOR `ContributionSummaryWebMapper.toListResponse`; WEB_COLLABORATOR `ContributionWebDisplayConfigProvider.get`
- Test-evidence classification: `CLASS_REFERENCE_ONLY`, `FOCUSED_MAPPING_OR_HTTP_CONTRACT`, `CONTROLLER_METHOD_BEHAVIOR`, `SECURITY_TEST`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/AuthenticatedContextExplicitUseCaseBoundaryTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/ContributionCurrentContextBoundaryGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/FrontendOwnedLocalizationContractFailFirstTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/PortalAccessContextFinalMigrationEvidenceTest.java`, `src/test/java/com/bct/ngtpa/apiservice/config/SecurityConfigAuthDisabledTest.java`, `src/test/java/com/bct/ngtpa/apiservice/config/SecurityConfigAuthTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ApiResponseExceptionHandlingBoundaryTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ContributionControllerTest.java`

### `GET /api/v1/contributions/export`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.contribution.ContributionController#exportContributionSummary`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/contribution/ContributionController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `DECLARED (optional)`
- Cookies: None declared
- Path variables: None declared
- Query parameters: None declared
- Request body type: `NONE`
- Java response type: `Mono<ResponseEntity<byte[]>>`
- Success/status selection: Runtime-dependent via exact selector(s): `ResponseEntity.ok()`
- Validation annotations: None declared at method boundary
- Immediate call chain: WEB_COLLABORATOR `CurrentPortalAccessContextResolver.current`; APPLICATION_INPUT_PORT `ExportContributionSummaryUseCase.execute` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/ExportContributionSummaryService.java; WEB_COLLABORATOR `ContributionSummaryWorkbookExporter.write`
- Test-evidence classification: `CLASS_REFERENCE_ONLY`, `FOCUSED_MAPPING_OR_HTTP_CONTRACT`, `CONTROLLER_METHOD_BEHAVIOR`, `SECURITY_TEST`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/AuthenticatedContextExplicitUseCaseBoundaryTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/ContributionCurrentContextBoundaryGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/FrontendOwnedLocalizationContractFailFirstTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/PortalAccessContextFinalMigrationEvidenceTest.java`, `src/test/java/com/bct/ngtpa/apiservice/config/SecurityConfigAuthTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ApiResponseExceptionHandlingBoundaryTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ContributionControllerTest.java`

### `DELETE /api/v1/internal/reference-date/all`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.controller.ReferenceDateController#cleanUpAllReferenceDates`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ReferenceDateController.java`
- Security boundary: `INTERNAL_ADMINISTRATION`
- Security evidence: INTERNAL_ADMINISTRATION; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: None declared
- Cookies: None declared
- Path variables: None declared
- Query parameters: None declared
- Request body type: `NONE`
- Java response type: `Mono<MutationResponse<CleanUpAllReferenceDatesResponse>>`
- Success/status selection: Runtime-dependent; no direct status selector statically resolved in controller method
- Validation annotations: None declared at method boundary
- Immediate call chain: APPLICATION_INPUT_PORT `CleanUpAllReferenceDatesUseCase.execute` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/CleanUpAllReferenceDatesService.java
- Test-evidence classification: `CLASS_REFERENCE_ONLY`, `FOCUSED_MAPPING_OR_HTTP_CONTRACT`, `SECURITY_TEST`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/config/SecurityConfigAuthDisabledTest.java`, `src/test/java/com/bct/ngtpa/apiservice/config/SecurityConfigAuthTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ReferenceDateAllControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ReferenceDateAllMutationResponseContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ReferenceDateRefreshControllerTest.java`

### `GET /api/v1/internal/reference-date/all`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.controller.ReferenceDateController#getAllReferenceDates`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ReferenceDateController.java`
- Security boundary: `INTERNAL_ADMINISTRATION`
- Security evidence: INTERNAL_ADMINISTRATION; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: None declared
- Cookies: None declared
- Path variables: None declared
- Query parameters: None declared
- Request body type: `NONE`
- Java response type: `Mono<MutationResponse<GetAllReferenceDatesResponse>>`
- Success/status selection: Runtime-dependent; no direct status selector statically resolved in controller method
- Validation annotations: None declared at method boundary
- Immediate call chain: APPLICATION_INPUT_PORT `GetAllReferenceDatesUseCase.execute` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/GetAllReferenceDatesService.java
- Test-evidence classification: `CLASS_REFERENCE_ONLY`, `FOCUSED_MAPPING_OR_HTTP_CONTRACT`, `SECURITY_TEST`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/config/SecurityConfigAuthDisabledTest.java`, `src/test/java/com/bct/ngtpa/apiservice/config/SecurityConfigAuthTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ReferenceDateAllControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ReferenceDateAllMutationResponseContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ReferenceDateRefreshControllerTest.java`

### `POST /api/v1/internal/reference-date/refresh`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.controller.ReferenceDateController#refreshReferenceDate`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ReferenceDateController.java`
- Security boundary: `INTERNAL_ADMINISTRATION`
- Security evidence: INTERNAL_ADMINISTRATION; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: None declared
- Cookies: None declared
- Path variables: None declared
- Query parameters: None declared
- Request body type: `RefreshReferenceDateRequest`
- Java response type: `Mono<RefreshReferenceDateResponse>`
- Success/status selection: Runtime-dependent; no direct status selector statically resolved in controller method
- Validation annotations: `@Valid`
- Immediate call chain: APPLICATION_INPUT_PORT `RefreshReferenceDateUseCase.execute` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/RefreshReferenceDateService.java
- Test-evidence classification: `FOCUSED_MAPPING_OR_HTTP_CONTRACT`, `CLASS_REFERENCE_ONLY`, `SECURITY_TEST`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/config/SecurityConfigAuthDisabledTest.java`, `src/test/java/com/bct/ngtpa/apiservice/config/SecurityConfigAuthTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ReferenceDateAllControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ReferenceDateAllMutationResponseContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ReferenceDateRefreshControllerTest.java`

### `GET /api/v1/notifications`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.notification.NotificationController#getNotifications`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/notification/NotificationController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: None declared
- Cookies: None declared
- Path variables: None declared
- Query parameters: `page (optional)`, `size (optional)`, `dateFormat (optional)`, `timezone (optional)`
- Request body type: `NONE`
- Java response type: `Mono<NotificationListResponse>`
- Success/status selection: Runtime-dependent; no direct status selector statically resolved in controller method
- Validation annotations: None declared at method boundary
- Immediate call chain: WEB_COLLABORATOR `CurrentPortalAccessContextResolver.current`; APPLICATION_INPUT_PORT `GetNotificationsUseCase.execute` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/GetNotificationsService.java
- Test-evidence classification: `CLASS_REFERENCE_ONLY`, `FOCUSED_MAPPING_OR_HTTP_CONTRACT`, `CONTROLLER_METHOD_BEHAVIOR`, `SECURITY_TEST`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/AuthenticatedContextExplicitUseCaseBoundaryTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/NotificationCurrentContextBoundaryGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/PortalAccessContextFinalMigrationEvidenceTest.java`, `src/test/java/com/bct/ngtpa/apiservice/config/SecurityConfigAuthDisabledTest.java`, `src/test/java/com/bct/ngtpa/apiservice/config/SecurityConfigAuthTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/NotificationsAuthenticatedExecutorBoundaryTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ApiExceptionHandlerMutationResponseContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ApiExceptionHandlerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ApiResponseExceptionHandlingBoundaryTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/NotificationControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/NotificationReferenceDateFlowTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/filter/RequestLoggingPropertiesTest.java`

### `PATCH /api/v1/notifications`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.notification.NotificationController#updateNotificationsReadStatus`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/notification/NotificationController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: None declared
- Cookies: None declared
- Path variables: None declared
- Query parameters: None declared
- Request body type: `UpdateNotificationsReadStatusRequest`
- Java response type: `Mono<MutationResponse<UpdateNotificationsReadStatusResponse>>`
- Success/status selection: Runtime-dependent; no direct status selector statically resolved in controller method
- Validation annotations: `@Valid`
- Immediate call chain: WEB_COLLABORATOR `CurrentPortalAccessContextResolver.current`; APPLICATION_INPUT_PORT `UpdateNotificationsReadStatusUseCase.execute` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/UpdateNotificationsReadStatusService.java
- Test-evidence classification: `CLASS_REFERENCE_ONLY`, `FOCUSED_MAPPING_OR_HTTP_CONTRACT`, `CONTROLLER_METHOD_BEHAVIOR`, `SECURITY_TEST`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/AuthenticatedContextExplicitUseCaseBoundaryTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/NotificationCurrentContextBoundaryGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/PortalAccessContextFinalMigrationEvidenceTest.java`, `src/test/java/com/bct/ngtpa/apiservice/config/SecurityConfigAuthDisabledTest.java`, `src/test/java/com/bct/ngtpa/apiservice/config/SecurityConfigAuthTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/NotificationsAuthenticatedExecutorBoundaryTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ApiExceptionHandlerMutationResponseContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ApiExceptionHandlerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ApiResponseExceptionHandlingBoundaryTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/NotificationControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/NotificationReferenceDateFlowTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/filter/RequestLoggingPropertiesTest.java`

### `GET /api/v1/personal-information`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.personalinformation.PersonalInformationController#getPersonalInformation`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/personalinformation/PersonalInformationController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `DECLARED (optional)`
- Cookies: None declared
- Path variables: None declared
- Query parameters: None declared
- Request body type: `NONE`
- Java response type: `Mono<FormPageResponse<FormSchemaResponse>>`
- Success/status selection: Runtime-dependent; no direct status selector statically resolved in controller method
- Validation annotations: None declared at method boundary
- Immediate call chain: WEB_COLLABORATOR `CurrentPortalAccessContextResolver.current`; APPLICATION_INPUT_PORT `GetPersonalInformationUseCase.execute` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/GetPersonalInformationService.java; WEB_COLLABORATOR `PersonalInformationWebMapper.toFormPageResponse`
- Test-evidence classification: `CLASS_REFERENCE_ONLY`, `CONTROLLER_METHOD_BEHAVIOR`, `FOCUSED_MAPPING_OR_HTTP_CONTRACT`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/AuthenticatedContextExplicitUseCaseBoundaryTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/GetPersonalInformationCommandBoundaryGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/PersonalInformationControllerCurrentContextArchitectureTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/PersonalInformationRetrievalNoRoleSubstitutionGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/PortalAccessContextArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/PortalAccessContextFinalMigrationEvidenceTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/UpdatePersonalInformationCurrentContextBoundaryGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/PersonalInformationRetrievalAuthenticatedExecutorBoundaryTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ApiResponseExceptionHandlingBoundaryTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/PersonalInformationControllerContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/PersonalInformationControllerCurrentContextArchitectureTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/PersonalInformationControllerTest.java`

### `PUT /api/v1/personal-information`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.personalinformation.PersonalInformationController#update`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/personalinformation/PersonalInformationController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `DECLARED (optional)`
- Cookies: None declared
- Path variables: None declared
- Query parameters: None declared
- Request body type: `Mono<UpdatePersonalInformationRequest>`
- Java response type: `Mono<MutationResponse<List<PersonalInformationUpdateResultResponse>>>`
- Success/status selection: Runtime-dependent; no direct status selector statically resolved in controller method
- Validation annotations: None declared at method boundary
- Immediate call chain: WEB_COLLABORATOR `CurrentPortalAccessContextResolver.current`; WEB_COLLABORATOR `PersonalInformationUpdateYamlValidator.validate`; WEB_COLLABORATOR `PersonalInformationUpdateWebMapper.toCommand`; APPLICATION_INPUT_PORT `UpdatePersonalInformationUseCase.execute` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/UpdatePersonalInformationService.java
- Test-evidence classification: `CLASS_REFERENCE_ONLY`, `CONTROLLER_METHOD_BEHAVIOR`, `FOCUSED_MAPPING_OR_HTTP_CONTRACT`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/AuthenticatedContextExplicitUseCaseBoundaryTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/GetPersonalInformationCommandBoundaryGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/PersonalInformationControllerCurrentContextArchitectureTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/PersonalInformationRetrievalNoRoleSubstitutionGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/PortalAccessContextArchitectureGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/PortalAccessContextFinalMigrationEvidenceTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/UpdatePersonalInformationCurrentContextBoundaryGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/PersonalInformationRetrievalAuthenticatedExecutorBoundaryTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ApiResponseExceptionHandlingBoundaryTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/PersonalInformationControllerContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/PersonalInformationControllerCurrentContextArchitectureTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/PersonalInformationControllerTest.java`

### `GET /api/v1/reference-data/countries`
- Controller: `com.bct.ngtpa.apiservice.adapter.in.web.referencedata.ReferenceDataController#getCountries`
- Source: `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/referencedata/ReferenceDataController.java`
- Security boundary: `AUTHENTICATED_CUSTOMER`
- Security evidence: AUTHENTICATED_CUSTOMER; in-process authentication required when api.security.require-authentication=true; when false, SecurityConfig explicitly delegates protection externally by permitting exchanges; evidence: src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java
- Headers: `DECLARED (optional)`
- Cookies: None declared
- Path variables: None declared
- Query parameters: None declared
- Request body type: `NONE`
- Java response type: `Mono<ReferenceDataCountriesResult>`
- Success/status selection: Runtime-dependent; no direct status selector statically resolved in controller method
- Validation annotations: None declared at method boundary
- Immediate call chain: WEB_COLLABORATOR `CurrentPortalAccessContextResolver.current`; APPLICATION_INPUT_PORT `GetReferenceDataCountriesUseCase.execute` -> immediate implementation(s): src/main/java/com/bct/ngtpa/apiservice/application/usecase/GetReferenceDataCountriesService.java
- Test-evidence classification: `CLASS_REFERENCE_ONLY`, `CONTROLLER_METHOD_BEHAVIOR`, `FOCUSED_MAPPING_OR_HTTP_CONTRACT`, `ARCHITECTURE_OR_PACKAGE_SENSITIVE`
- Supporting tests: `src/test/java/com/bct/ngtpa/apiservice/architecture/FrontendOwnedLocalizationContractFailFirstTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/GetReferenceDataCountriesCommandBoundaryGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/PortalAccessContextFinalMigrationEvidenceTest.java`, `src/test/java/com/bct/ngtpa/apiservice/architecture/PortalAccessContextResponsibilityBoundaryGuardTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/CountryListAuthenticatedExecutorBoundaryTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ReferenceDataControllerTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ReferenceDataCountriesControllerContractTest.java`, `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/filter/PortalAccessContextWebFilterTest.java`

## Duplicate and ambiguity analysis

- No duplicate annotation-proven HTTP method/path pairs found.
- No composed mapping annotation is reported as `ANY`; `ANY` is reserved for an unconstrained `@RequestMapping`.
- Statuses described as runtime-dependent identify the exact response selector rather than inventing a single HTTP status.

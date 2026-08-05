package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043FlowSpecificCsrfHttpMappingTest {

    @Test
    void loginCsrfUsesFlowSpecificForbiddenValidationContract() throws Exception {
        String responses = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/LoginHttpResponses.java"));
        String send = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/mapper/LoginOtpSendWebMapper.java"));
        String resend = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/mapper/LoginOtpResendWebMapper.java"));
        String verify = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/mapper/LoginOtpVerifyWebMapper.java"));

        assertThat(responses).contains("LoginErrorKey.CSRF_INVALID", "HttpStatus.FORBIDDEN");
        assertThat(send).contains("CSRF_INVALID", "LoginErrorKey.CSRF_INVALID", "SERVER");
        assertThat(resend).contains("CSRF_INVALID", "LoginErrorKey.CSRF_INVALID", "SERVER");
        assertThat(verify).contains("CSRF_INVALID", "LoginErrorKey.CSRF_INVALID", "SERVER");
    }

    @Test
    void logoutDistinguishesInvalidSessionFromInvalidCsrf() throws Exception {
        String controller = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthLogoutController.java"));
        String errors = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/dto/workflow/LogoutErrorKey.java"));

        assertThat(controller).contains(
                "AuthenticatedCsrfValidationException.class",
                "HttpStatus.FORBIDDEN",
                "ApiStatus.VALIDATION_FAILED",
                "LogoutErrorKey.CSRF_INVALID");
        assertThat(controller).contains(
                "AuthenticatedPortalContextResolutionException.class",
                "HttpStatus.UNAUTHORIZED");
        assertThat(errors).contains("err.logout.csrf.invalid");
    }

    @Test
    void missingCsrfReturnsUnauthorizedAtTheWebBoundary() throws Exception {
        String loginSend = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthLoginOtpController.java"));
        String loginVerify = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthLoginOtpVerifyController.java"));
        String loginCancel = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthLoginCancelController.java"));
        String registration = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthRegistrationController.java"));
        String registrationOtp = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthRegistrationOtpController.java"));
        String registrationUsername = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthRegistrationUsernameController.java"));
        String forgotPassword = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordController.java"));
        String forgotPasswordOtp = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordOtpController.java"));

        assertThat(loginSend).contains("CsrfHttpResponses.missingToken()");
        assertThat(loginVerify).contains("CsrfHttpResponses.missingToken()");
        assertThat(loginCancel).contains("CsrfHttpResponses.missingToken()");
        assertThat(registration).contains("CsrfHttpResponses.missingToken()");
        assertThat(registrationOtp).contains("CsrfHttpResponses.missingToken()");
        assertThat(registrationUsername).contains("CsrfHttpResponses.missingToken()");
        assertThat(forgotPassword).contains("CsrfHttpResponses.missingToken()");
        assertThat(forgotPasswordOtp).contains("CsrfHttpResponses.missingToken()");
    }

    @Test
    void sharedMissingCsrfHelperReturnsUnauthorizedWithAuthenticationRequired() throws Exception {
        String helper = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/response/CsrfHttpResponses.java"));

        assertThat(helper).contains(
                "HttpStatus.UNAUTHORIZED",
                "err.security.authentication.required",
                "BUSINESS_REJECTED");
    }
}

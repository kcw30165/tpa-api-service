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
    void invalidCsrfRemainsSideEffectFreeAtTheWebMappingBoundary() throws Exception {
        String sendController = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthLoginOtpController.java"));
        String verifyController = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthLoginOtpVerifyController.java"));

        assertThat(sendController).contains("mapper.csrfInvalid()", "resendMapper.csrfInvalid()");
        assertThat(verifyController).contains("mapper.csrfInvalid()");
    }
}

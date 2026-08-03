package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ForgotPasswordEndpointSecurityContractSubtask009Test {

    @Test
    void forgotPasswordControllersDoNotRequireAccountRef() throws Exception {
        assertThat(source("AuthForgotPasswordController.java")).doesNotContain("Account-Ref");
        assertThat(source("AuthForgotPasswordIdentityController.java")).doesNotContain("Account-Ref");
        assertThat(source("AuthForgotPasswordDetailsController.java")).doesNotContain("Account-Ref");
        assertThat(source("AuthForgotPasswordOtpController.java")).doesNotContain("Account-Ref");
        assertThat(source("AuthForgotPasswordCompleteController.java")).doesNotContain("Account-Ref");
    }

    @Test
    void forgotPasswordControllersEchoRequestIdOnPublicEndpoints() throws Exception {
        assertThat(source("AuthForgotPasswordController.java")).contains("response.getHeaders().set(RequestCorrelation.REQUEST_ID_HEADER");
        assertThat(source("AuthForgotPasswordIdentityController.java")).contains("response.getHeaders().set(RequestCorrelation.REQUEST_ID_HEADER");
        assertThat(source("AuthForgotPasswordDetailsController.java")).contains("response.getHeaders().set(RequestCorrelation.REQUEST_ID_HEADER");
        assertThat(source("AuthForgotPasswordOtpController.java")).contains("response.getHeaders().set(RequestCorrelation.REQUEST_ID_HEADER");
        assertThat(source("AuthForgotPasswordCompleteController.java")).contains("response.getHeaders().set(RequestCorrelation.REQUEST_ID_HEADER");
    }

    @Test
    void protectedForgotPasswordEndpointsRequireExpectedCookieAndHeaderGuards() throws Exception {
        String identity = source("AuthForgotPasswordIdentityController.java");
        String details = source("AuthForgotPasswordDetailsController.java");
        String otp = source("AuthForgotPasswordOtpController.java");
        String complete = source("AuthForgotPasswordCompleteController.java");

        assertThat(identity).contains("auth_flow_sid").contains("X-XSRF-TOKEN").contains("Origin").contains("Referer");
        assertThat(details).contains("auth_flow_sid").contains("X-XSRF-TOKEN").contains("Origin").contains("Referer").contains("Idempotency-Key");
        assertThat(otp).contains("auth_flow_sid").contains("X-XSRF-TOKEN").contains("Origin").contains("Referer").contains("Idempotency-Key");
        assertThat(complete).contains("auth_flow_sid").contains("X-XSRF-TOKEN").contains("Origin").contains("Referer").contains("Idempotency-Key");
    }

    private static String source(String fileName) throws Exception {
        try (var paths = Files.walk(Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller"))) {
            Path path = paths
                    .filter(Files::isRegularFile)
                    .filter(candidate -> candidate.getFileName().toString().equals(fileName))
                    .findFirst()
                    .orElseThrow();
            return Files.readString(path);
        }
    }
}
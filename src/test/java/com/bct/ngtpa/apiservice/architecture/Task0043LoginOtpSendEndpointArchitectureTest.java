package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class Task0043LoginOtpSendEndpointArchitectureTest {
    @Test
    void endpointRequiresCookieCsrfIdempotencyAndRequestId() throws Exception {
        String s = Files.readString(Path
                .of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthLoginOtpController.java"));
        assertThat(s).contains("/api/v1/auth/login/otp", "/send", "auth_flow_sid", "X-XSRF-TOKEN", "Idempotency-Key",
                "X-Request-Id", "Origin", "Referer");
        assertThat(s).doesNotContain("Account-Ref", "csrfToken()", "rawCode");
    }

    @Test
    void compositionWrapsCoreWithIdempotency() throws Exception {
        String s = Files.readString(
                Path.of("src/main/java/com/bct/ngtpa/apiservice/config/auth/login/LoginOtpSendApplicationConfig.java"));
        assertThat(s).contains("new LoginOtpSendService", "new IdempotentLoginOtpSendService", "getOtpSendTtlSeconds");
    }

    @Test
    void responseCannotExposeCsrfSessionDestinationOrOtpCode() {
        var componentNames = java.util.Arrays.stream(
                com.bct.ngtpa.apiservice.adapter.in.web.auth.login.otp.LoginOtpSendDataResponse.class
                        .getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();
        assertThat(componentNames).containsExactly(
                "maskedDestination",
                "expiresInSeconds",
                "resendCooldownSeconds",
                "retryAfterSeconds");
        assertThat(componentNames).doesNotContain(
                "csrfToken",
                "authFlowSessionId",
                "sessionId",
                "destinationId",
                "destination",
                "otpCode",
                "rawCode",
                "code");
    }
}

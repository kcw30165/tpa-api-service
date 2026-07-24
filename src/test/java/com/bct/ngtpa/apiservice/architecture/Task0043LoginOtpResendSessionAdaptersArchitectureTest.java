package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class Task0043LoginOtpResendSessionAdaptersArchitectureTest {
    @Test
    void loaderReusesLifecycleCsrfAndProtector() throws Exception {
        String s = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/service/auth/login/context/DefaultLoginOtpResendContextLoader.java"));
        assertThat(s).contains("AuthFlowStatus.OTP_SENT", "AuthFlowScenario.LOGIN", "AuthFlowSessionCsrfService",
                "LoginPreAuthContextProtectorPort");
        assertThat(s).doesNotContain("Redis", "WebClient", "org.springframework");
    }

    @Test
    void bindingUsesLifecycleWithoutDirectRepository() throws Exception {
        String s = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/session/login/LoginOtpReplacementBindingAdapter.java"));
        assertThat(s).contains("updateActiveOtpChallenge");
        assertThat(s).doesNotContain("AuthFlowSessionPort", "Redis");
    }
}

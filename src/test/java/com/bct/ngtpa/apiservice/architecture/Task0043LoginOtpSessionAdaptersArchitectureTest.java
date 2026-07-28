package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class Task0043LoginOtpSessionAdaptersArchitectureTest {
    @Test
    void loaderReusesLifecycleCsrfAndProtectorWithoutInfrastructure() throws Exception {
        String s = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/service/auth/login/context/DefaultLoginProtectedAuthFlowContextLoader.java"));
        assertThat(s).contains("AuthFlowSessionLifecycleService", "AuthFlowSessionCsrfService",
                "LoginPreAuthContextProtectorPort", "AuthFlowScenario.LOGIN", "expectedStatus");
        assertThat(s).doesNotContain("Redis", "WebClient", "org.springframework", "Cipher");
    }

    @Test
    void bindingUsesExistingLifecycleAndMarksOtpSent() throws Exception {
        String s = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/session/login/LoginOtpSessionBindingAdapter.java"));
        assertThat(s).contains("updateActiveOtpChallenge", "updateStatus", "resultingStatus");
        assertThat(s).doesNotContain("RedisTemplate", "ReactiveRedis", "AuthFlowSessionPort");
    }
}

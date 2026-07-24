package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043LoginAuthenticatedSessionTransitionCompositionArchitectureTest {
    @Test
    void compositionReusesExistingSessionPolicyClockAndPorts() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/config/auth/session/AuthenticatedSessionConfig.java"));
        assertThat(source)
                .contains("AuthenticatedSessionLifecycleService")
                .contains("getIdleTimeoutSeconds", "getTtlSeconds", "Clock")
                .contains("EstablishAuthenticatedLoginSessionService")
                .contains("AuthFlowSessionPort", "DeleteAuthFlowSessionPort")
                .doesNotContain("ReactiveStringRedisTemplate", "RedisConnectionFactory");
    }

    @Test
    void broadPreAuthPortIsRestrictedToCompositionAdapter() throws Exception {
        String service = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/session/EstablishAuthenticatedLoginSessionService.java"));
        assertThat(service)
                .contains("DeleteAuthFlowSessionPort")
                .doesNotContain("import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort");
    }

    @Test
    void otpIntegrationAndWebTransportRemainDeferred() {
        assertThat(Files.exists(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/config/AuthenticatedSessionCookieWriter.java"))).isFalse();
    }
}

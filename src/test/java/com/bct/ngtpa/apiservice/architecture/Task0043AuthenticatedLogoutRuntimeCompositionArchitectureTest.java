package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043AuthenticatedLogoutRuntimeCompositionArchitectureTest {
    @Test
    void sessionConfigurationComposesNormalLogoutCapability() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/config/auth/session/AuthenticatedSessionConfig.java"));
        String compact = source.replaceAll("\\s+", "");
        assertThat(compact).contains(
                "AuthenticatedLogoutUseCaseauthenticatedLogoutUseCase(",
                "LogoutAuthenticatedSessionUseCaselogout)",
                "returnnewAuthenticatedLogoutService(logout)");
    }

    @Test
    void configurationDoesNotBypassLifecycleWithPersistencePort() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/config/auth/session/AuthenticatedSessionConfig.java"));
        int bean = source.indexOf("AuthenticatedLogoutUseCase authenticatedLogoutUseCase");
        assertThat(bean).isGreaterThanOrEqualTo(0);
        String logoutSection = source.substring(bean, source.indexOf("}", bean) + 1);
        assertThat(logoutSection).doesNotContain(
                "AuthenticatedSessionPort",
                "RedisAuthenticatedSessionAdapter",
                "CachePort",
                "RevokeAuthenticatedSessionUseCase");
    }
}

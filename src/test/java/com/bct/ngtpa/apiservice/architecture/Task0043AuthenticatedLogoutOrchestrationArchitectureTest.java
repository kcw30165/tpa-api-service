package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043AuthenticatedLogoutOrchestrationArchitectureTest {
    @Test
    void serviceDelegatesNormalLogoutToLoggedOutLifecycleCapability() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/logout/AuthenticatedLogoutService.java"));
        String compact = source.replaceAll("\\s+", "");
        assertThat(compact).contains(
                "implementsAuthenticatedLogoutUseCase",
                "LogoutAuthenticatedSessionUseCase",
                "newLogoutActiveAuthenticatedSessionCommand(",
                "logout.logout(");
        assertThat(source).doesNotContain(
                "RevokeAuthenticatedSessionUseCase",
                "RevokeActiveAuthenticatedSessionCommand",
                "USER_LOGOUT_REASON");
    }

    @Test
    void orchestrationHasNoWebPersistenceOrCryptoDependencies() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/logout/AuthenticatedLogoutService.java"));
        assertThat(source).doesNotContain(
                "org.springframework",
                "ServerWebExchange",
                "HttpHeaders",
                "Redis",
                "CachePort",
                "ObjectMapper",
                "AuthenticatedSessionPort");
    }
}

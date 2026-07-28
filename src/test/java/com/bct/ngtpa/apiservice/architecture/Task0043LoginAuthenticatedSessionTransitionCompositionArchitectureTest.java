package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043LoginAuthenticatedSessionTransitionCompositionArchitectureTest {
    private static final Path CONFIG = Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/config/auth/session/AuthenticatedSessionConfig.java");

    @Test
    void compositionReusesConfiguredIdleAbsoluteAndTerminalPolicies() throws Exception {
        String source = Files.readString(CONFIG);
        assertThat(source).contains(
                "getIdleTimeoutSeconds",
                "getAbsoluteTimeoutSeconds",
                "getTerminalRetentionSeconds",
                "Clock",
                "AuthenticatedSessionPort",
                "AuthenticatedSessionProtectorPort",
                "SecureAuthenticatedSessionIdGenerator");
        assertThat(source).doesNotContain("getTtlSeconds");
    }

    @Test
    void transitionReusesLifecycleForCreateAndSecurityRevocation() throws Exception {
        String source = Files.readString(CONFIG);
        String compact = source.replaceAll("\\s+", "");
        assertThat(compact).contains(
                "returnnewEstablishAuthenticatedLoginSessionService(",
                "lifecycle,cleanup,lifecycle,csrfTokenGenerator,csrfTokenHasher)");
    }

    @Test
    void normalLogoutUsesDedicatedLoggedOutCapability() throws Exception {
        String source = Files.readString(CONFIG);
        String compact = source.replaceAll("\\s+", "");
        assertThat(compact).contains(
                "AuthenticatedLogoutUseCaseauthenticatedLogoutUseCase(",
                "LogoutAuthenticatedSessionUseCaselogout)",
                "returnnewAuthenticatedLogoutService(logout)");
    }
}

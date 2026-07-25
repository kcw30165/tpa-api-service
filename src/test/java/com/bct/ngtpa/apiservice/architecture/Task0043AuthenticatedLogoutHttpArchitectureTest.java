package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043AuthenticatedLogoutHttpArchitectureTest {
    @Test
    void endpointUsesCanonicalSidTransportAndThinUseCaseBoundary() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthLogoutController.java"));
        assertThat(source).contains(
                "@PostMapping(\"/logout\")",
                "RequestHeaderContextKeys.AUTHENTICATED_SESSION_HEADER",
                "AuthenticatedLogoutUseCase",
                "AuthenticatedLogoutCommand");
        assertThat(source).doesNotContain(
                "Session-Id",
                "AuthenticatedSessionPort",
                "Redis",
                "RevokeAuthenticatedSessionCommand");
    }

    @Test
    void publicMapperDoesNotExposeLogoutExistenceOrSessionIdentity() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/logout/AuthenticatedLogoutWebMapper.java"));
        assertThat(source).doesNotContain(
                "AuthenticatedSessionId",
                "sessionId",
                "actorContext",
                "accounts");
    }
}

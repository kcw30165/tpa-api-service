package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043AuthenticatedLogoutContractArchitectureTest {
        @Test
        void logoutContractIsFrameworkAndTransportNeutral() throws Exception {
                String source = Files.readString(Path.of(
                                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/logout/AuthenticatedLogoutUseCase.java"));
                assertThat(source).contains("Mono<AuthenticatedLogoutResult>", "logout(AuthenticatedLogoutCommand")
                                .doesNotContain("org.springframework", "ServerWebExchange", "Cookie", "Redis");
        }

        @Test
        void logoutContractDoesNotExposeSessionIdentityInResult() throws Exception {
                String result = Files.readString(Path.of(
                                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/logout/AuthenticatedLogoutResult.java"));
                assertThat(result).contains("AuthenticatedLogoutStatus")
                                .doesNotContain("AuthenticatedSessionId", "sessionId", "ActorContext",
                                                "AccountContext");
        }
}

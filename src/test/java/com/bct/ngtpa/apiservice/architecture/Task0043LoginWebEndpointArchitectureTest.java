package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043LoginWebEndpointArchitectureTest {
        @Test
        void controllerIsThinAndUsesApplicationInputPort() throws Exception {
                String source = Files.readString(Path.of(
                                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthLoginController.java"));
                assertThat(source).contains(
                                "@RequestMapping(\"/api/v1/auth\")",
                                "@PostMapping(\"/login\")",
                                "StartLoginUseCase",
                                "AuthFlowCookieWriter",
                                "Idempotency-Key",
                                "X-Request-Id",
                                "Accept-Language");
                assertThat(source).doesNotContain(
                                "Redis", "WebClient", "CredentialAuthenticationPort",
                                "LoginStartReplayPort", "AuthFlowSessionLifecycleService", "Cipher");
        }

        @Test
        void webDtosDoNotExposeSessionReplayOrCredentialsInResponse() throws Exception {
                String response = Files.readString(Path.of(
                                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/dto/LoginStartDataResponse.java"));
                assertThat(response).contains("csrfToken", "destinations", "expiresInSeconds", "actions");
                assertThat(response).doesNotContain(
                                "authFlowSessionId", "replayCommitRef", "password", "ciphertext", "rawDestination");
        }
}

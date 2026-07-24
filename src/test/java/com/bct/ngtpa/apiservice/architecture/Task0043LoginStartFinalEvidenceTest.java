package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043LoginStartFinalEvidenceTest {
    @Test
    void controllerEchoesAndRequiresRequestIdAndContainsReactiveErrors() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthLoginController.java"));
        assertThat(source).contains(
                "RequestCorrelation.REQUEST_ID_HEADER",
                "mapper.missingRequestId()",
                "echoRequestId(response, requestId)",
                ".onErrorResume(",
                "mapper.systemUnavailable()");
        assertThat(source).doesNotContain(
                "Redis", "WebClient", "LoginStartReplayPort",
                "CredentialAuthenticationPort", "AuthFlowSessionLifecycleService");
    }

    @Test
    void responseDtosAndMapperExcludeSensitiveTransportState() throws Exception {
        String data = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/dto/LoginStartDataResponse.java"));
        String mapper = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/mapper/LoginStartWebMapper.java"));
        assertThat(data).doesNotContain(
                "authFlowSessionId", "replayCommitRef", "password",
                "ciphertext", "rawDestination");
        assertThat(mapper).doesNotContain(
                "authFlowSessionId()", "replayCommitRef()", "ciphertext");
    }
}

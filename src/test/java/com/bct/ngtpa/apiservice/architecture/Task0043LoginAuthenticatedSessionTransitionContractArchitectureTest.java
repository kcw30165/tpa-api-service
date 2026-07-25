package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043LoginAuthenticatedSessionTransitionContractArchitectureTest {
    @Test
    void transitionContractIsApplicationOwnedAndFrameworkNeutral() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/session/EstablishAuthenticatedLoginSessionCommand.java"));
        String port = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/port/in/EstablishAuthenticatedLoginSessionUseCase.java"));
        assertThat(source + port)
                .contains("AuthFlowSessionId", "ActorContext", "LoginSessionAccount")
                .doesNotContain("org.springframework", "Redis", "ServerHttp", "Cookie", "ResponseEntity");
    }

    @Test
    void accountMappingCannotRetainRawIdentifiers() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/session/LoginAuthenticatedSessionAccountMapper.java"));
        assertThat(source)
                .contains("account.policyNo()", "account.certNo()")
                .doesNotContain("account.rawPolicyNo()", "account.rawCertNo()");
    }

    @Test
    void orchestrationAndCanonicalHeaderTransportAreDelivered() throws Exception {
        Path service = Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/session/EstablishAuthenticatedLoginSessionService.java");
        Path controller = Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthLoginOtpVerifyController.java");
        assertThat(Files.exists(service)).isTrue();
        assertThat(Files.readString(service)).contains(
                "CreateAuthenticatedSessionUseCase",
                "DeleteAuthFlowSessionPort",
                "RevokeAuthenticatedSessionUseCase");
        assertThat(Files.readString(controller)).contains(
                "RequestHeaderContextKeys.AUTHENTICATED_SESSION_HEADER",
                "result.authenticatedSessionId().value()");
        assertThat(Files.exists(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/config/AuthenticatedSessionCookieWriter.java"))).isFalse();
    }
}

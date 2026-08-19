package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043LoginAuthenticatedSessionTransitionContractArchitectureTest {
  @Test
  void transitionContractIsApplicationOwnedAndFrameworkNeutral() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/session/EstablishAuthenticatedLoginSessionCommand.java"));
    String port =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/port/in/EstablishAuthenticatedLoginSessionUseCase.java"));
    assertThat(source + port)
        .contains("AuthFlowSessionId", "ActorContext", "LoginSessionAccount")
        .doesNotContain("org.springframework", "Redis", "ServerHttp", "Cookie", "ResponseEntity");
  }

  @Test
  void accountMappingCannotRetainRawIdentifiers() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/session/LoginAuthenticatedSessionAccountMapper.java"));
    assertThat(source)
        .contains("account.policyNo()", "account.certNo()")
        .doesNotContain("account.rawPolicyNo()", "account.rawCertNo()");
  }

  @Test
  void orchestrationAndAuthenticatedCookieTransportAreDelivered() throws Exception {
    String controller =
        java.nio.file.Files.readString(
            java.nio.file.Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/LoginController.java"));
    String service =
        java.nio.file.Files.readString(
            java.nio.file.Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/session/EstablishAuthenticatedLoginSessionService.java"));
    org.assertj.core.api.Assertions.assertThat(service)
        .contains(
            "CreateAuthenticatedSessionUseCase",
            "DeleteAuthFlowSessionPort",
            "RevokeAuthenticatedSessionUseCase");
    org.assertj.core.api.Assertions.assertThat(controller)
        .contains(
            "AuthenticatedSessionCookieWriter",
            "r.authenticatedSessionId().value()",
            "r.authenticatedCsrfToken()")
        .doesNotContain("AUTHENTICATED_SESSION_HEADER", "X-Authenticated-Session-Id");
  }
}

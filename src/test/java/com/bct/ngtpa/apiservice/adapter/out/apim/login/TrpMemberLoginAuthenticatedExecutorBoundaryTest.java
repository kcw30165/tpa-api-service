package com.bct.ngtpa.apiservice.adapter.out.apim.login;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TrpMemberLoginAuthenticatedExecutorBoundaryTest {
  private static String source(String path) throws Exception {
    return Files.readString(Path.of(path));
  }

  @Test
  void callerContractCarriesExplicitPreAuthenticationRole() throws Exception {
    String command =
        source(
            "src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/login/CredentialAuthenticationCommand.java");
    String start =
        source(
            "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/start/SharedStartLoginService.java");
    assertThat(command).contains("ActorUserRole actorUserRole");
    assertThat(start).contains("ActorUserRole.ROLE_USER_MBR");
  }

  @Test
  void transportUsesTypedNonAccountExecutorWithoutRoleSubstitution() throws Exception {
    String transport =
        source(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/login/SharedApimTrpMemberLoginTransport.java");
    assertThat(transport)
        .contains(
            "ApimExchangeExecutor",
            "ApimExchangeRequest",
            "postEnvelope(",
            "ApimDatabaseScope.NON_ACCOUNT",
            "command.actorUserRole()",
            "ApimRetryEligibility.SAFE_BUSINESS_CALL",
            "TrpMemberLoginApimRequest.class",
            "TrpMemberLoginApimDataItem.class");
    assertThat(transport)
        .doesNotContain(
            "ROLE_USER_MBR",
            "ApimWebClientFacade",
            "ApimCertificateService",
            "ApimPayloadCryptoService",
            "ApimProperties",
            "resolveProfile(",
            "getPublicKey(",
            "encryptRequest(",
            "decryptResponseEnvelope(");
  }

  @Test
  void accountEnvironmentAndAuthenticatedContextAreAbsent() throws Exception {
    String command =
        source(
            "src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/login/CredentialAuthenticationCommand.java");
    String transport =
        source(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/login/SharedApimTrpMemberLoginTransport.java");
    assertThat(command).doesNotContain("accountEnv", "PortalAccessContext", "accountRef");
    assertThat(transport)
        .contains("new ApimCredentialResolutionContext(command.actorUserRole(), null)");
  }
}

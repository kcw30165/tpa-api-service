package com.bct.ngtpa.apiservice.adapter.out.apim.login.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TrpGetAccountListExecutorBoundaryTest {
  private String source(String path) throws Exception {
    return Files.readString(Path.of(path));
  }

  @Test
  void focusedCommandsCarryExplicitProtectedActorRole() throws Exception {
    assertThat(
            source(
                "src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/login/account/GetLoginAccountsCommand.java"))
        .contains("ActorUserRole actorUserRole");
    assertThat(
            source(
                "src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/login/account/LoginAccountAuthorizationCommand.java"))
        .contains("ActorUserRole actorUserRole");
  }

  @Test
  void transportUsesTypedNonAccountExecutorAndNoLocalRoleSubstitution() throws Exception {
    String source =
        source(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/login/account/SharedApimTrpGetAccountListTransport.java");
    assertThat(source)
        .contains(
            "ApimExchangeExecutor",
            "ApimExchangeRequest",
            "postEnvelope(",
            "ApimDatabaseScope.NON_ACCOUNT",
            "command.actorUserRole()",
            "new ApimCredentialResolutionContext(command.actorUserRole(), null)",
            "ApimRetryEligibility.SAFE_BUSINESS_CALL",
            "TrpGetAccountListApimRequest.class",
            "TrpGetAccountListApimDataItem.class");
    assertThat(source)
        .doesNotContain(
            "ROLE_USER_MBR",
            "ApimWebClientFacade",
            "ApimCertificateService",
            "ApimPayloadCryptoService",
            "ApimProperties",
            "resolveProfile(",
            "getPublicKey(",
            "encryptRequest(",
            "decryptResponseEnvelope(",
            "PortalAccessContext",
            "accountRef");
  }

  @Test
  void returnedAccountEnvironmentIsOutputOnly() throws Exception {
    String request =
        source(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/login/account/dto/TrpGetAccountListApimRequest.java");
    String command =
        source(
            "src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/login/account/LoginAccountAuthorizationCommand.java");
    assertThat(request).doesNotContain("accountEnv", "accountRef", "PortalAccessContext");
    assertThat(command).doesNotContain("accountEnv", "accountRef", "PortalAccessContext");
  }
}

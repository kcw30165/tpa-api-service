package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InternalReferenceDateRefreshRoutingBoundaryTest {
  private static String source(String path) throws Exception {
    return Files.readString(Path.of(path));
  }

  @Test
  void internalRefreshUsesExplicitFocusedCommandAndGenericExecutor() throws Exception {
    String adapter =
        source(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimReferenceDateRefreshAdapter.java");
    String service =
        source(
            "src/main/java/com/bct/ngtpa/apiservice/application/usecase/RefreshReferenceDateService.java");
    String command =
        source(
            "src/main/java/com/bct/ngtpa/apiservice/application/dto/RefreshReferenceDateCommand.java");
    assertThat(command).contains("ActorUserRole actorUserRole");
    assertThat(service)
        .contains("new FetchReferenceDateCommand(command.accountEnv(), command.actorUserRole())");
    assertThat(adapter)
        .contains(
            "ApimExchangeExecutor",
            "ApimDatabaseScope.ACCOUNT_ENV",
            "command.actorUserRole()",
            "command.accountEnv()",
            "ApimRetryEligibility.SAFE_BUSINESS_CALL");
    assertThat(adapter)
        .doesNotContain(
            "ROLE_USER_MBR",
            "NON_ACCOUNT",
            "ApimWebClientFacade",
            "ApimCertificateService",
            "ApimPayloadCryptoService",
            "ApimProperties");
  }

  @Test
  void normalReadHasNoAutomaticApimFallback() throws Exception {
    String adapter =
        source(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/out/referencedate/ReferenceDateAdapter.java");
    assertThat(adapter)
        .doesNotContain("ApimReferenceDateRefreshPort", "readFromApim", "persistApimDate");
  }
}

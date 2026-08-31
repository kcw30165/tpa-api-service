package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AccountBalanceApplicationBoundaryGuardTest {
  private static final Path SERVICE =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/GetAccountBalanceService.java");
  private static final Path COMMAND =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/dto/FetchAccountBalanceCommand.java");

  @Test
  void applicationOrchestrationIsExplicitReactiveAndContextFree() throws Exception {
    String service = Files.readString(SERVICE);
    assertThat(service)
        .contains("ReferenceDatePort", "resolveReferenceDate(command.accountEnv())", ".flatMap(");
    assertThat(service)
        .doesNotContain(
            "PortalAccessContext",
            "CurrentPortalAccessContextResolver",
            ".block(",
            ".subscribe(",
            "deferContextual");
  }

  @Test
  void focusedCommandPreservesTypedRoleAndStringIdentifiersWithoutAccountRef() throws Exception {
    String command = Files.readString(COMMAND);
    assertThat(command)
        .contains(
            "String policyNo",
            "String certNo",
            "ActorUserRole actorUserRole",
            "LocalDate referenceDate");
    assertThat(command)
        .doesNotContain("accountRef", "Integer certNo", "Long certNo", "String loginUserRole");
  }

  @Test
  void accountBalanceAdapterUsesExecutorBoundary() throws Exception {
    String adapter =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/accountbalance/ApimAccountBalanceSourceAdapter.java"));
    assertThat(adapter)
        .contains(
            "ApimExchangeExecutor", "ApimExchangeRequest", "ACCOUNT_ENV", "SAFE_BUSINESS_CALL")
        .doesNotContain(
            "import com.bct.ngtpa.apiservice.adapter.out.apim.ApimWebClientFacade;",
            "import com.bct.ngtpa.apiservice.adapter.out.apim.ApimPayloadCryptoService;",
            "import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;");
  }
}

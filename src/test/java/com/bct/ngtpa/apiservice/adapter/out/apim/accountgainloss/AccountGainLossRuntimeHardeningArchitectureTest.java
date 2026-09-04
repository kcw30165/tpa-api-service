package com.bct.ngtpa.apiservice.adapter.out.apim.accountgainloss;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AccountGainLossRuntimeHardeningArchitectureTest {
  @Test
  void adapterSanitizesDownstreamErrorBeforeLogging() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/accountgainloss/ApimAccountGainLossSourceAdapter.java"));
    assertThat(source)
        .contains("safeErrorCode(error)", "[A-Za-z0-9._-]+", "UNRECOGNIZED_APIM_ERROR")
        .doesNotContain("errorCode={}, error)");
  }

  @Test
  void applicationOrchestrationIsDeferredAndRequestsOnlyNavPrecision() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/GetAccountGainLossService.java"));
    assertThat(source)
        .contains(
            "Mono.defer",
            "GeneralMetadataName.NET_ASSET_VALUE",
            "GeneralMetadataType.PRECISION",
            "Set.of(NAV_PRECISION)")
        .doesNotContain("FUND_PRICE", "UNIT_BALANCE", "FX_RATE", ".block(", ".subscribe(");
  }

  @Test
  void adapterPreservesAuthenticatedExecutorRouting() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/accountgainloss/ApimAccountGainLossSourceAdapter.java"));
    assertThat(source)
        .contains(
            "ApimExchangeExecutor",
            "ApimDatabaseScope.ACCOUNT_ENV",
            "command.actorUserRole()",
            "command.accountEnv()",
            "ApimRetryEligibility.SAFE_BUSINESS_CALL");
  }
}

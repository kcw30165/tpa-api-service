package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AccountBalancePublicErrorHardeningGuardTest {
  private static final Path MAIN = Path.of("src/main/java/com/bct/ngtpa/apiservice");

  @Test
  void useCaseSanitizesEveryUnexpectedFailureButPreservesTypedBusinessFailures() throws Exception {
    String source =
        Files.readString(MAIN.resolve("application/usecase/GetAccountBalanceService.java"));
    assertThat(source)
        .contains(".onErrorMap(")
        .contains("instanceof AccountBalanceRetrievalException")
        .contains("AccountBalanceRetrievalException.systemFailure()")
        .contains("error -> !(error instanceof AccountBalanceRetrievalException)");
  }

  @Test
  void onlyApprovedTasCodesReachApprovedPublicBusinessCodes() throws Exception {
    String adapter =
        Files.readString(
            MAIN.resolve("adapter/out/apim/accountbalance/ApimAccountBalanceSourceAdapter.java"));
    String policy =
        Files.readString(
            MAIN.resolve(
                "adapter/in/web/accountbalance/AccountBalanceRetrievalResponsePolicy.java"));
    assertThat(adapter).contains("err.tpa.account.invalid", "err.tpa.member.not.found");
    assertThat(policy)
        .contains("err.ab.account.not.exist", "err.ab.account.mismatch")
        .contains("err.system.unavailable")
        .contains("SYSTEM_ERROR", "BUSINESS_REJECTED");
  }

  @Test
  void rawTasDetailsAreNotEmbeddedInTheFeatureException() throws Exception {
    String exception =
        Files.readString(
            MAIN.resolve("application/exception/AccountBalanceRetrievalException.java"));
    assertThat(exception).doesNotContain("err.tpa.", "responseMessage", "rawMessage");
  }
}

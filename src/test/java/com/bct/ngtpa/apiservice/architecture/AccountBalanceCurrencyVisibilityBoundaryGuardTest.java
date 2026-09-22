package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AccountBalanceCurrencyVisibilityBoundaryGuardTest {

  private static final Path RESPONSE_ROOT =
      Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/response/accountsummary");

  @Test
  void omissionIsScopedAcrossBothViewsAndForbiddenFieldIsAbsent() throws Exception {
    String fund = Files.readString(RESPONSE_ROOT.resolve("FundLevelFundResponse.java"));
    String fundLeaf =
        Files.readString(RESPONSE_ROOT.resolve("FundLevelSubDisplayAccountResponse.java"));
    String subDisplayFund =
        Files.readString(RESPONSE_ROOT.resolve("SubDisplayLevelFundResponse.java"));
    String overview =
        Files.readString(RESPONSE_ROOT.resolve("AccountOverviewDisplayAccountResponse.java"))
            + Files.readString(RESPONSE_ROOT.resolve("AccountOverviewTotalsResponse.java"));
    String combined = fund + fundLeaf + subDisplayFund + overview;

    assertThat(combined).doesNotContain("fundFactsheetEnabled");
    assertThat(fundLeaf).doesNotContain("factsheetUrl");
    assertThat(count(combined, "Include.NON_NULL")).isEqualTo(5);
    assertThat(count(combined, "Include.ALWAYS")).isEqualTo(2);
    assertThat(combined).doesNotContain("setSerializationInclusion");
  }

  @Test
  void malformedLoggerCannotReceiveTheUrl() throws Exception {
    String port =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/port/out/AccountBalancePresentationEventPort.java"));
    String logger =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/infrastructure/logging/AccountBalancePresentationEventLogger.java"));

    assertThat(port).contains("void malformedFactsheet()").doesNotContain("String");
    assertThat(logger).doesNotContain("url", "factsheetUrl");
  }

  private static int count(String value, String token) {
    return (value.length() - value.replace(token, "").length()) / token.length();
  }
}

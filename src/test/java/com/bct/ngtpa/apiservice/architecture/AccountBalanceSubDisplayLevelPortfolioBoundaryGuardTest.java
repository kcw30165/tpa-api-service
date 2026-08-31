package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.*;
import org.junit.jupiter.api.Test;

class AccountBalanceSubDisplayLevelPortfolioBoundaryGuardTest {
  @Test
  void remainsIndependentAndPresentationNeutral() throws Exception {
    String s =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/service/accountbalance/DefaultSubDisplayLevelPortfolioBuilder.java"));
    assertThat(s)
        .doesNotContain(
            "RawFundLevel",
            "DefaultFundLevel",
            "adapter.out.apim",
            "JsonProperty",
            "MutationResponse",
            "DecimalFormat",
            "NumberFormat",
            "factsheetUrl",
            "@Service",
            "Mono<");
  }
}

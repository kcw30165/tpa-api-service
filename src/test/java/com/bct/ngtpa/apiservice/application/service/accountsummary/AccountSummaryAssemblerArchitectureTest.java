package com.bct.ngtpa.apiservice.application.service.accountsummary;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Mirrors AccountGainLossAssemblerArchitectureTest: no rounding, no framework leakage. */
class AccountSummaryAssemblerArchitectureTest {

  @Test
  void staysFrameworkFreeAndNeverRounds() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/service/accountsummary/AccountSummaryAssembler.java"));
    assertThat(source)
        .doesNotContain("org.springframework", "PortalAccessContext", "RoundingMode", "double", "Double");
    assertThat(source).contains("BigDecimal::add", "ExactPrecisionRenderer");
  }
}

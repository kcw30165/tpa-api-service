package com.bct.ngtpa.apiservice.application.service.accountgainloss;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AccountGainLossAssemblerArchitectureTest {
  @Test
  void sumsRawValuesBeforeRenderingAndUsesNavPrecision() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/service/accountgainloss/AccountGainLossAssembler.java"));
    int reduce = source.indexOf("reduce(BigDecimal.ZERO, BigDecimal::add)");
    int renderFunds = source.indexOf("renderer.render(f.gainLoss(), precision)");
    int renderTotal = source.indexOf("renderer.render(total, precision)");
    assertThat(reduce).isGreaterThanOrEqualTo(0);
    assertThat(renderFunds).isGreaterThan(reduce);
    assertThat(renderTotal).isGreaterThan(reduce);
    assertThat(source)
        .contains("GeneralMetadataName.NET_ASSET_VALUE", "GeneralMetadataType.PRECISION")
        .doesNotContain("RoundingMode", "double", "Double");
  }
}

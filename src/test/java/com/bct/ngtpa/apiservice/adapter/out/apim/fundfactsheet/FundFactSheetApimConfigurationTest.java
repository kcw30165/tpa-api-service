package com.bct.ngtpa.apiservice.adapter.out.apim.fundfactsheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;

class FundFactSheetApimConfigurationTest {

  @Test
  void validatesAndTrimsThePath() {
    var properties = new FundFactSheetApimProperties();
    properties.setPath(" /ws/NGTPA/v1/TRPGetFundFactSheet ");

    properties.validate();

    assertThat(properties.getPath()).isEqualTo("/ws/NGTPA/v1/TRPGetFundFactSheet");
  }

  @Test
  void rejectsNullAndBlankPaths() {
    var missing = new FundFactSheetApimProperties();
    assertThatThrownBy(missing::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("apim.fund-fact-sheet.path");

    var blank = new FundFactSheetApimProperties();
    blank.setPath("   ");
    assertThatThrownBy(blank::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("apim.fund-fact-sheet.path");
  }

  @Test
  void validationConfigurationFailsStartupOnABlankPath() {
    assertThatThrownBy(() -> new FundFactSheetApimValidationConfig(new FundFactSheetApimProperties()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("apim.fund-fact-sheet.path");
  }

  @Test
  void validationConfigurationTrimsTheConfiguredPath() {
    var properties = new FundFactSheetApimProperties();
    properties.setPath("  /ws/x  ");

    new FundFactSheetApimValidationConfig(properties);

    assertThat(properties.getPath()).isEqualTo("/ws/x");
  }

  @Test
  void bindsFromTheApimFundFactSheetPrefix() {
    var binder =
        new Binder(new MapConfigurationPropertySource(Map.of("apim.fund-fact-sheet.path", "/ws/bound")));

    FundFactSheetApimProperties bound =
        binder
            .bind("apim.fund-fact-sheet", Bindable.of(FundFactSheetApimProperties.class))
            .orElseThrow(() -> new IllegalStateException("Failed to bind FundFactSheetApimProperties"));

    assertThat(bound.getPath()).isEqualTo("/ws/bound");
  }

  @Test
  void shippedApplicationYmlDefinesThePathUnderTheApimBlock() throws IOException {
    String yaml = new ClassPathResource("application.yml").getContentAsString(StandardCharsets.UTF_8);

    assertThat(yaml)
        .contains(
            "\n  fund-fact-sheet:\n"
                + "    path: ${APIM_FUND_FACT_SHEET_PATH:/ws/NGTPA/v1/TRPGetFundFactSheet}\n");
  }
}

package com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice;

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

class InvestmentChoiceDetailsApimConfigurationTest {

  @Test
  void validatesAndTrimsTheDetailsPath() {
    var properties = new InvestmentChoiceDetailsApimProperties();
    properties.setPath(" /ws/NGTPA/v1/TRPGetCIMDtl ");

    properties.validate();

    assertThat(properties.getPath()).isEqualTo("/ws/NGTPA/v1/TRPGetCIMDtl");
  }

  @Test
  void rejectsMissingAndBlankDetailsPaths() {
    assertThatThrownBy(
            () ->
                new InvestmentChoiceDetailsApimValidationConfig(
                    new InvestmentChoiceDetailsApimProperties()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("apim.investment-choice.details.path");

    var blank = new InvestmentChoiceDetailsApimProperties();
    blank.setPath("   ");
    assertThatThrownBy(() -> new InvestmentChoiceDetailsApimValidationConfig(blank))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("apim.investment-choice.details.path");
  }

  @Test
  void bindsFromTheInvestmentChoiceDetailsPrefix() {
    var binder =
        new Binder(
            new MapConfigurationPropertySource(
                Map.of("apim.investment-choice.details.path", "/ws/bound")));

    InvestmentChoiceDetailsApimProperties bound =
        binder
            .bind(
                "apim.investment-choice.details",
                Bindable.of(InvestmentChoiceDetailsApimProperties.class))
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Failed to bind InvestmentChoiceDetailsApimProperties"));

    assertThat(bound.getPath()).isEqualTo("/ws/bound");
  }

  @Test
  void shippedApplicationYmlDefinesTheDetailsPath() throws IOException {
    String yaml =
        new ClassPathResource("application.yml").getContentAsString(StandardCharsets.UTF_8);

    assertThat(yaml)
        .contains(
            "\n  investment-choice:\n"
                + "    details:\n"
                + "      path: ${APIM_INVESTMENT_CHOICE_DETAILS_PATH:/ws/NGTPA/v1/TRPGetCIMDtl}\n");
  }
}

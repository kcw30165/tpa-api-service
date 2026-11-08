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

class InvestmentChoiceCancelApimConfigurationTest {

  @Test
  void validatesAndTrimsTheCancelPath() {
    var properties = new InvestmentChoiceCancelApimProperties();
    properties.setPath(" /ws/NGTPA/v1/TRPCnclCIMReq ");

    properties.validate();

    assertThat(properties.getPath()).isEqualTo("/ws/NGTPA/v1/TRPCnclCIMReq");
  }

  @Test
  void rejectsMissingAndBlankCancelPaths() {
    var missing = new InvestmentChoiceCancelApimProperties();
    assertThatThrownBy(() -> new InvestmentChoiceCancelApimValidationConfig(missing))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("apim.investment-choice.cancel.path");

    var blank = new InvestmentChoiceCancelApimProperties();
    blank.setPath("   ");
    assertThatThrownBy(() -> new InvestmentChoiceCancelApimValidationConfig(blank))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("apim.investment-choice.cancel.path");
  }

  @Test
  void bindsFromTheInvestmentChoiceCancelPrefix() {
    var binder =
        new Binder(
            new MapConfigurationPropertySource(
                Map.of("apim.investment-choice.cancel.path", "/ws/bound")));

    InvestmentChoiceCancelApimProperties bound =
        binder
            .bind(
                "apim.investment-choice.cancel",
                Bindable.of(InvestmentChoiceCancelApimProperties.class))
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Failed to bind InvestmentChoiceCancelApimProperties"));

    assertThat(bound.getPath()).isEqualTo("/ws/bound");
  }

  @Test
  void shippedApplicationYmlDefinesTheCancelPath() throws IOException {
    String yaml =
        new ClassPathResource("application.yml").getContentAsString(StandardCharsets.UTF_8);

    assertThat(yaml)
        .contains(
            """

              investment-choice:
                details:
                  path: ${APIM_INVESTMENT_CHOICE_DETAILS_PATH:/ws/NGTPA/v1/TRPGetCIMDtl}
                instruction:
                  path: ${APIM_INVESTMENT_CHOICE_INSTRUCTION_PATH:/ws/NGTPA/v1/TRPCIMReq}
                cancel:
                  path: ${APIM_INVESTMENT_CHOICE_CANCEL_PATH:/ws/NGTPA/v1/TRPCnclCIMReq}
            """);
  }
}

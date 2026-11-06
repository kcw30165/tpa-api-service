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

class InvestmentChoiceInstructionApimConfigurationTest {

  @Test
  void validatesAndTrimsTheInstructionPath() {
    var properties = new InvestmentChoiceInstructionApimProperties();
    properties.setPath(" /ws/NGTPA/v1/TRPCIMReq ");

    properties.validate();

    assertThat(properties.getPath()).isEqualTo("/ws/NGTPA/v1/TRPCIMReq");
  }

  @Test
  void rejectsMissingAndBlankInstructionPaths() {
    var missing = new InvestmentChoiceInstructionApimProperties();
    assertThatThrownBy(() -> new InvestmentChoiceInstructionApimValidationConfig(missing))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("apim.investment-choice.instruction.path");

    var blank = new InvestmentChoiceInstructionApimProperties();
    blank.setPath("   ");
    assertThatThrownBy(() -> new InvestmentChoiceInstructionApimValidationConfig(blank))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("apim.investment-choice.instruction.path");
  }

  @Test
  void bindsFromTheInvestmentChoiceInstructionPrefix() {
    var binder =
        new Binder(
            new MapConfigurationPropertySource(
                Map.of("apim.investment-choice.instruction.path", "/ws/bound")));

    InvestmentChoiceInstructionApimProperties bound =
        binder
            .bind(
                "apim.investment-choice.instruction",
                Bindable.of(InvestmentChoiceInstructionApimProperties.class))
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Failed to bind InvestmentChoiceInstructionApimProperties"));

    assertThat(bound.getPath()).isEqualTo("/ws/bound");
  }

  @Test
  void shippedApplicationYmlDefinesTheInstructionPath() throws IOException {
    String yaml =
        new ClassPathResource("application.yml").getContentAsString(StandardCharsets.UTF_8);

    assertThat(yaml)
        .contains(
            "\n  investment-choice:\n"
                + "    details:\n"
                + "      path: ${APIM_INVESTMENT_CHOICE_DETAILS_PATH:/ws/NGTPA/v1/TRPGetCIMDtl}\n"
                + "    instruction:\n"
                + "      path: ${APIM_INVESTMENT_CHOICE_INSTRUCTION_PATH:/ws/NGTPA/v1/TRPCIMReq}\n");
  }
}

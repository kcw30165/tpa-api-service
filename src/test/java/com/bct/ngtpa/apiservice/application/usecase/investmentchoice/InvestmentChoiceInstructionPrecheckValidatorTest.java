package com.bct.ngtpa.apiservice.application.usecase.investmentchoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation.ChangeInvestmentChoicePasswordValidationPayloadDecoder;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceInstructionRequest;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceViolation;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class InvestmentChoiceInstructionPrecheckValidatorTest {
  private static final String VALID_ALLOCATION = "{\"fundCode\":\"DBLF\",\"percentage\":100}";
  private static final ObjectMapper JSON = new ObjectMapper();

  private final ChangeInvestmentChoicePasswordValidationPayloadDecoder decoder =
      new ChangeInvestmentChoicePasswordValidationPayloadDecoder();
  private final InvestmentChoiceInstructionPrecheckValidator validator =
      new InvestmentChoiceInstructionPrecheckValidator();

  @ParameterizedTest(name = "{0}")
  @MethodSource("v1ToV8Cases")
  void returnsTheDetailedViolationForEachRule(
      String caseName, String body, List<InvestmentChoiceViolation> expected)
      throws JsonProcessingException {
    var result = validator.validate(decoder.decode(JSON.readTree(body)));

    assertThat(result.isValid()).as(caseName).isFalse();
    assertThat(result.request()).as(caseName).isNull();
    assertThat(result.violations()).as(caseName).containsExactlyElementsOf(expected);
  }

  @Test
  void malformedStructurePrecedesFieldViolations() throws JsonProcessingException {
    var result =
        validator.validate(
            decoder.decode(
                JSON.readTree("{\"switchSources\":[\" \"],\"allocations\":[],\"unknown\":true}")));

    assertThat(result.violations())
        .containsExactly(
            violation(
                InvestmentChoiceViolation.Kind.FORM, ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID));
    assertThat(result.structurallyValid()).isFalse();
  }

  @Test
  void invalidPercentageSuppressesTotalViolation() throws JsonProcessingException {
    var result =
        validator.validate(
            decoder.decode(
                JSON.readTree(
                    "{\"switchSources\":[\"BO\"],\"allocations\":["
                        + "{\"fundCode\":\"DBLF\",\"percentage\":\"60\"},"
                        + "{\"fundCode\":\"DHKM\",\"percentage\":39}]}")));

    assertThat(result.violations())
        .containsExactly(
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_INVALID,
                "allocations[0].percentage"));
    assertThat(result.structurallyValid()).isTrue();
  }

  @Test
  void createsTypedRequestOnlyWhenAllVRulesPass() throws JsonProcessingException {
    var result =
        validator.validate(
            decoder.decode(
                JSON.readTree(
                    "{\"switchSources\":[\"BO\"],\"allocations\":["
                        + "{\"fundCode\":\"DBLF\",\"percentage\":60},"
                        + "{\"fundCode\":\"DHKM\",\"percentage\":40}]}")));

    assertThat(result.isValid()).isTrue();
    assertThat(result.structurallyValid()).isTrue();
    assertThat(result.violations()).isEmpty();
    assertThat(result.request())
        .isEqualTo(
            new InvestmentChoiceInstructionRequest(
                List.of("BO"),
                List.of(
                    new InvestmentChoiceInstructionRequest.Allocation("DBLF", 60),
                    new InvestmentChoiceInstructionRequest.Allocation("DHKM", 40))));
  }

  private static Stream<Arguments> v1ToV8Cases() {
    return Stream.of(
        Arguments.of(
            "V1 malformed structure",
            "{\"switchSources\":\"BO\",\"allocations\":[" + VALID_ALLOCATION + "]}",
            List.of(
                violation(
                    InvestmentChoiceViolation.Kind.FORM,
                    ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID))),
        Arguments.of(
            "V2 required switch source",
            "{\"allocations\":[" + VALID_ALLOCATION + "]}",
            List.of(
                violation(
                    InvestmentChoiceViolation.Kind.FIELD,
                    ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_REQUIRED,
                    "switchSources"))),
        Arguments.of(
            "V3 duplicate switch source",
            "{\"switchSources\":[\"BO\",\"BO\"],\"allocations\":[" + VALID_ALLOCATION + "]}",
            List.of(
                violation(
                    InvestmentChoiceViolation.Kind.FIELD,
                    ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_DUPLICATE,
                    "switchSources[1]"))),
        Arguments.of(
            "V4 required allocation",
            "{\"switchSources\":[\"BO\"],\"allocations\":[]}",
            List.of(
                violation(
                    InvestmentChoiceViolation.Kind.FIELD,
                    ErrorCodes.INVESTMENT_CHOICE_ALLOCATION_REQUIRED,
                    "allocations"))),
        Arguments.of(
            "V5 required fund",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"percentage\":100}]}",
            List.of(
                violation(
                    InvestmentChoiceViolation.Kind.FIELD,
                    ErrorCodes.INVESTMENT_CHOICE_FUND_REQUIRED,
                    "allocations[0].fundCode"))),
        Arguments.of(
            "V6 duplicate fund",
            "{\"switchSources\":[\"BO\"],\"allocations\":["
                + "{\"fundCode\":\"DBLF\",\"percentage\":50},"
                + "{\"fundCode\":\"DBLF\",\"percentage\":50}]}",
            List.of(
                violation(
                    InvestmentChoiceViolation.Kind.FIELD,
                    ErrorCodes.INVESTMENT_CHOICE_FUND_DUPLICATE,
                    "allocations[1].fundCode"))),
        Arguments.of(
            "V7 invalid percentage",
            "{\"switchSources\":[\"BO\"],\"allocations\":["
                + "{\"fundCode\":\"DBLF\",\"percentage\":60.0},"
                + "{\"fundCode\":\"DHKM\",\"percentage\":40}]}",
            List.of(
                violation(
                    InvestmentChoiceViolation.Kind.FIELD,
                    ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_INVALID,
                    "allocations[0].percentage"))),
        Arguments.of(
            "V8 invalid total",
            "{\"switchSources\":[\"BO\"],\"allocations\":["
                + "{\"fundCode\":\"DBLF\",\"percentage\":60},"
                + "{\"fundCode\":\"DHKM\",\"percentage\":39}]}",
            List.of(
                violation(
                    InvestmentChoiceViolation.Kind.CROSS_FIELD,
                    ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_TOTAL_INVALID,
                    "allocations"))));
  }

  private static InvestmentChoiceViolation violation(
      InvestmentChoiceViolation.Kind kind, String code, String... targets) {
    return new InvestmentChoiceViolation(kind, code, List.of(targets));
  }
}

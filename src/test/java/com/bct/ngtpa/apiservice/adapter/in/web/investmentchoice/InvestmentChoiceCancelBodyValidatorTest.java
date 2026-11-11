package com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceViolation;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class InvestmentChoiceCancelBodyValidatorTest {

  private static final String REF_REQUIRED = "err.investment-choice.cancel.ref-required";
  private static final String VALID_REFERENCE_NO = "260001449";
  private static final ObjectMapper JSON = new ObjectMapper();
  private final InvestmentChoiceCancelBodyValidator validator =
      new InvestmentChoiceCancelBodyValidator();

  @Test
  void acceptsTheReferenceWithoutChangingIt() throws JsonProcessingException {
    var result =
        validator.validate(
            JSON.readTree(
                """
                {"referenceNo":"260001449"}
                """));

    assertThat(result.isValid()).isTrue();
    assertThat(result.referenceNo()).isEqualTo(VALID_REFERENCE_NO);
    assertThat(result.violations()).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formViolations")
  void rejectsMalformedShapesAsFormViolations(
      String name, String rawBody, InvestmentChoiceViolation expected)
      throws JsonProcessingException {
    JsonNode body = rawBody == null ? null : JSON.readTree(rawBody);

    var result = validator.validate(body);

    assertThat(result.isValid()).as(name).isFalse();
    assertThat(result.referenceNo()).isNull();
    assertThat(result.violations()).containsExactly(expected);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("requiredReferenceViolations")
  void rejectsMissingOrBlankReferenceAsFieldViolations(
      String name, String rawBody, InvestmentChoiceViolation expected)
      throws JsonProcessingException {
    var result = validator.validate(JSON.readTree(rawBody));

    assertThat(result.isValid()).as(name).isFalse();
    assertThat(result.referenceNo()).isNull();
    assertThat(result.violations()).containsExactly(expected);
  }

  private static Stream<Arguments> formViolations() {
    InvestmentChoiceViolation violation =
        new InvestmentChoiceViolation(
            InvestmentChoiceViolation.Kind.FORM,
            ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID,
            List.of());
    return Stream.of(
        Arguments.of("missing body", null, violation),
        Arguments.of("null root", "null", violation),
        Arguments.of("array root", "[]", violation),
        Arguments.of("string root", "\"body\"", violation),
        Arguments.of("unknown member", "{\"referenceNo\":\"260001449\",\"extra\":true}", violation),
        Arguments.of("legacy refNo is unknown", "{\"refNo\":\"260001449\"}", violation),
        Arguments.of(
            "both public names are unknown-member input",
            "{\"referenceNo\":\"260001449\",\"refNo\":\"260001450\"}",
            violation),
        Arguments.of("unknown member takes precedence", "{\"extra\":true}", violation),
        Arguments.of("null reference", "{\"referenceNo\":null}", violation),
        Arguments.of("numeric reference", "{\"referenceNo\":260001449}", violation),
        Arguments.of("boolean reference", "{\"referenceNo\":false}", violation),
        Arguments.of("array reference", "{\"referenceNo\":[]}", violation),
        Arguments.of("object reference", "{\"referenceNo\":{}}", violation));
  }

  private static Stream<Arguments> requiredReferenceViolations() {
    InvestmentChoiceViolation violation =
        new InvestmentChoiceViolation(
            InvestmentChoiceViolation.Kind.FIELD, REF_REQUIRED, List.of("referenceNo"));
    return Stream.of(
        Arguments.of("missing reference", "{}", violation),
        Arguments.of("empty reference", "{\"referenceNo\":\"\"}", violation),
        Arguments.of("whitespace reference", "{\"referenceNo\":\"   \"}", violation));
  }
}

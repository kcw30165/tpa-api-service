package com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceInstructionRequest;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSnapshot;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceViolation;
import com.bct.ngtpa.apiservice.application.usecase.investmentchoice.InvestmentChoiceSnapshotValidator;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class InvestmentChoiceInstructionBodyValidatorTest {

  private static final String ALLOCATION = "{\"fundCode\":\"DBLF\",\"percentage\":100}";

  private final ObjectMapper json = new ObjectMapper();
  private final InvestmentChoiceInstructionBodyValidator validator =
      new InvestmentChoiceInstructionBodyValidator();

  private InvestmentChoiceInstructionBodyValidator.ValidationResult validate(String body) {
    try {
      return validator.validate(json.readTree(body));
    } catch (JsonProcessingException exception) {
      throw new AssertionError(exception);
    }
  }

  private static InvestmentChoiceViolation violation(
      InvestmentChoiceViolation.Kind kind, String code, String... targets) {
    return new InvestmentChoiceViolation(kind, code, List.of(targets));
  }

  @ParameterizedTest
  @MethodSource("malformedBodies")
  void reportsOnlyFormViolationForMalformedBodyStructure(String body) {
    var result = validate(body);

    assertThat(result.request()).isNull();
    assertThat(result.violations())
        .containsExactly(
            violation(
                InvestmentChoiceViolation.Kind.FORM, ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID));
  }

  @Test
  void formViolationTakesPrecedenceOverFieldViolations() {
    var result = validate("{\"switchSources\":[\" \"],\"allocations\":[],\"unknown\":true}");

    assertThat(result.violations())
        .containsExactly(
            violation(
                InvestmentChoiceViolation.Kind.FORM, ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID));
  }

  @Test
  void treatsMissingBodyAsMalformed() {
    var result = validator.validate(null);

    assertThat(result.request()).isNull();
    assertThat(result.violations())
        .containsExactly(
            violation(
                InvestmentChoiceViolation.Kind.FORM, ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID));
  }

  private static Stream<Arguments> malformedBodies() {
    return Stream.of(
        Arguments.of("null"),
        Arguments.of("[]"),
        Arguments.of(
            "{\"switchSources\":\"BO\",\"allocations\":[{\"fundCode\":\"DBLF\",\"percentage\":100}]}"),
        Arguments.of("{\"switchSources\":[\"BO\"],\"allocations\":{}}"),
        Arguments.of("{\"switchSources\":[1],\"allocations\":[]}"),
        Arguments.of("{\"switchSources\":[\"BO\"],\"allocations\":[1]}"),
        Arguments.of(
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":\"DBLF\",\"percentage\":100,\"extra\":true}]}"),
        Arguments.of(
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":true,\"percentage\":100}]}"),
        Arguments.of(
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":\"DBLF\",\"percentage\":100}],\"extra\":true}"));
  }

  @ParameterizedTest
  @MethodSource("missingOrInvalidSwitchSources")
  void reportsRequiredSwitchSourceViolation(String body) {
    var result = validate(body);

    assertThat(result.violations())
        .containsExactly(
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_REQUIRED,
                "switchSources"));
  }

  private static Stream<Arguments> missingOrInvalidSwitchSources() {
    return Stream.of(
        Arguments.of("{\"allocations\":[" + ALLOCATION + "]}"),
        Arguments.of("{\"switchSources\":[],\"allocations\":[" + ALLOCATION + "]}"),
        Arguments.of("{\"switchSources\":[\" \",\"BO\"],\"allocations\":[" + ALLOCATION + "]}"));
  }

  @Test
  void reportsEverySwitchSourceDuplicateAfterItsFirstOccurrence() {
    var result =
        validate(
            "{\"switchSources\":[\"BO\",\"EE\",\"BO\",\"EE\"],\"allocations\":["
                + ALLOCATION
                + "]}");

    assertThat(result.violations())
        .containsExactly(
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_DUPLICATE,
                "switchSources[2]"),
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_DUPLICATE,
                "switchSources[3]"));
  }

  @ParameterizedTest
  @MethodSource("missingOrEmptyAllocations")
  void reportsRequiredAllocationViolation(String body) {
    var result = validate(body);

    assertThat(result.violations())
        .containsExactly(
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_ALLOCATION_REQUIRED,
                "allocations"));
  }

  private static Stream<Arguments> missingOrEmptyAllocations() {
    return Stream.of(
        Arguments.of("{\"switchSources\":[\"BO\"]}"),
        Arguments.of("{\"switchSources\":[\"BO\"],\"allocations\":[]}"));
  }

  @Test
  void reportsEveryMissingOrBlankFundCodeAtItsAllocationIndex() {
    var result =
        validate(
            "{\"switchSources\":[\"BO\"],\"allocations\":["
                + "{\"percentage\":50},"
                + "{\"fundCode\":\" \",\"percentage\":50}]}");

    assertThat(result.violations())
        .containsExactly(
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_REQUIRED,
                "allocations[0].fundCode"),
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_REQUIRED,
                "allocations[1].fundCode"));
  }

  @Test
  void reportsEveryDuplicateFundCodeAfterItsFirstOccurrence() {
    var result =
        validate(
            "{\"switchSources\":[\"BO\"],\"allocations\":["
                + "{\"fundCode\":\"DBLF\",\"percentage\":20},"
                + "{\"fundCode\":\"DHKM\",\"percentage\":20},"
                + "{\"fundCode\":\"DBLF\",\"percentage\":30},"
                + "{\"fundCode\":\"DBLF\",\"percentage\":30}]}");

    assertThat(result.violations())
        .containsExactly(
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_DUPLICATE,
                "allocations[2].fundCode"),
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_DUPLICATE,
                "allocations[3].fundCode"));
  }

  @ParameterizedTest
  @MethodSource("invalidPercentages")
  void reportsInvalidPercentageAtItsAllocationIndex(String allocation) {
    var result =
        validate(
            "{\"switchSources\":[\"BO\"],\"allocations\":["
                + allocation
                + ",{\"fundCode\":\"DHKM\",\"percentage\":40}]}");

    assertThat(result.violations())
        .containsExactly(
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_INVALID,
                "allocations[0].percentage"));
  }

  private static Stream<Arguments> invalidPercentages() {
    return Stream.of(
        Arguments.of("{\"fundCode\":\"DBLF\"}"),
        Arguments.of("{\"fundCode\":\"DBLF\",\"percentage\":null}"),
        Arguments.of("{\"fundCode\":\"DBLF\",\"percentage\":60.0}"),
        Arguments.of("{\"fundCode\":\"DBLF\",\"percentage\":\"60\"}"),
        Arguments.of("{\"fundCode\":\"DBLF\",\"percentage\":true}"),
        Arguments.of("{\"fundCode\":\"DBLF\",\"percentage\":0}"),
        Arguments.of("{\"fundCode\":\"DBLF\",\"percentage\":101}"));
  }

  @ParameterizedTest
  @MethodSource("invalidTotals")
  void reportsInvalidPercentageTotal(String allocations) {
    var result = validate("{\"switchSources\":[\"BO\"],\"allocations\":" + allocations + "}");

    assertThat(result.violations())
        .containsExactly(
            violation(
                InvestmentChoiceViolation.Kind.CROSS_FIELD,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_TOTAL_INVALID,
                "allocations"));
  }

  private static Stream<Arguments> invalidTotals() {
    return Stream.of(
        Arguments.of(
            "[{\"fundCode\":\"DBLF\",\"percentage\":60},{\"fundCode\":\"DHKM\",\"percentage\":39}]"),
        Arguments.of(
            "[{\"fundCode\":\"DBLF\",\"percentage\":51},{\"fundCode\":\"DHKM\",\"percentage\":50}]"));
  }

  @Test
  void reportsAllApplicableViolationsTogetherInRuleAndIndexOrder() {
    var result =
        validate(
            "{\"switchSources\":[\"BO\",\"BO\",\" \"],\"allocations\":["
                + "{\"fundCode\":\"DBLF\",\"percentage\":40},"
                + "{\"fundCode\":\"DBLF\",\"percentage\":\"60\"},"
                + "{\"percentage\":0}]}");

    assertThat(result.violations())
        .containsExactly(
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_REQUIRED,
                "switchSources"),
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_DUPLICATE,
                "switchSources[1]"),
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_REQUIRED,
                "allocations[2].fundCode"),
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_DUPLICATE,
                "allocations[1].fundCode"),
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_INVALID,
                "allocations[1].percentage"),
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_INVALID,
                "allocations[2].percentage"));
  }

  @Test
  void reportsCombinedSourceFundAndTotalViolationsWhenPercentagesAreValid() {
    var result =
        validate(
            "{\"switchSources\":[\"BO\",\"BO\",\" \"],\"allocations\":["
                + "{\"fundCode\":\"DBLF\",\"percentage\":20},"
                + "{\"fundCode\":\"DBLF\",\"percentage\":30},"
                + "{\"percentage\":40}]}");

    assertThat(result.violations())
        .containsExactly(
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_REQUIRED,
                "switchSources"),
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_DUPLICATE,
                "switchSources[1]"),
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_REQUIRED,
                "allocations[2].fundCode"),
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_DUPLICATE,
                "allocations[1].fundCode"),
            violation(
                InvestmentChoiceViolation.Kind.CROSS_FIELD,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_TOTAL_INVALID,
                "allocations"));
  }

  @Test
  void skipsPercentageTotalViolationWhenAnyPercentageIsInvalid() {
    var result =
        validate(
            "{\"switchSources\":[\"BO\"],\"allocations\":["
                + "{\"fundCode\":\"DBLF\",\"percentage\":\"60\"},"
                + "{\"fundCode\":\"DHKM\",\"percentage\":39}]}");

    assertThat(result.violations())
        .containsExactly(
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_INVALID,
                "allocations[0].percentage"));
  }

  @Test
  void validRequestIsTypedAndPassesSnapshotValidation() {
    var body =
        validate(
            "{\"switchSources\":[\"BO\",\"EE\"],\"allocations\":["
                + "{\"fundCode\":\"DBLF\",\"percentage\":60},"
                + "{\"fundCode\":\"DHKM\",\"percentage\":40}]}");
    var snapshot =
        new InvestmentChoiceSnapshot(
            "acc_validvaluevalidvaluevalidvalue",
            "policy-valid",
            20,
            List.of(
                new InvestmentChoiceSnapshot.SwitchSource("BO", true),
                new InvestmentChoiceSnapshot.SwitchSource("EE", true)),
            List.of(
                new InvestmentChoiceSnapshot.Fund("DBLF", true),
                new InvestmentChoiceSnapshot.Fund("DHKM", true)));

    assertThat(body.isValid()).isTrue();
    assertThat(body.request())
        .isEqualTo(
            new InvestmentChoiceInstructionRequest(
                List.of("BO", "EE"),
                List.of(
                    new InvestmentChoiceInstructionRequest.Allocation("DBLF", 60),
                    new InvestmentChoiceInstructionRequest.Allocation("DHKM", 40))));
    assertThat(body.violations()).isEmpty();
    assertThat(new InvestmentChoiceSnapshotValidator().validate(body.request(), snapshot))
        .isEmpty();
  }
}

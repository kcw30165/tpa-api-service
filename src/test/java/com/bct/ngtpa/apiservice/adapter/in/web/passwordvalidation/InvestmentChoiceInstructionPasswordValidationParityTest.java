package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.InvestmentChoiceCancelBodyValidator;
import com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.InvestmentChoiceInstructionBodyValidator;
import com.bct.ngtpa.apiservice.application.usecase.investmentchoice.InvestmentChoiceCancelPrecheckValidator;
import com.bct.ngtpa.apiservice.application.usecase.investmentchoice.InvestmentChoiceInstructionPrecheckValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class InvestmentChoiceInstructionPasswordValidationParityTest {

  private static final String VALID =
      "{\"switchSources\":[\"BO\",\"EE\"],\"allocations\":["
          + "{\"fundCode\":\"DBLF\",\"percentage\":60},"
          + "{\"fundCode\":\"DHKM\",\"percentage\":40}]}";
  private static final String VALID_CANCEL = "{\"referenceNo\":\"260001449\"}";
  private final ObjectMapper json = new ObjectMapper();
  private final ChangeInvestmentChoicePasswordValidationPayloadDecoder submitDecoder =
      new ChangeInvestmentChoicePasswordValidationPayloadDecoder();
  private final CancelInvestmentChoicePasswordValidationPayloadDecoder cancelDecoder =
      new CancelInvestmentChoicePasswordValidationPayloadDecoder();
  private final InvestmentChoiceInstructionBodyValidator submitFacade =
      new InvestmentChoiceInstructionBodyValidator();
  private final InvestmentChoiceCancelBodyValidator cancelFacade =
      new InvestmentChoiceCancelBodyValidator();
  private final InvestmentChoiceInstructionPrecheckValidator submitPolicyValidator =
      new InvestmentChoiceInstructionPrecheckValidator();
  private final InvestmentChoiceCancelPrecheckValidator cancelPolicyValidator =
      new InvestmentChoiceCancelPrecheckValidator();
  private final PasswordValidationRequestParser parser =
      new PasswordValidationRequestParser(
          PasswordValidationTestRegistrations.decoders(), json, 3, 5);

  @ParameterizedTest(name = "{0}")
  @MethodSource("parityCases")
  void submitPolicyAndExistingBodyValidatorAgree(
      String name, String body, boolean expectedPolicyAcceptance, boolean expectedFacadeAcceptance)
      throws JsonProcessingException {
    JsonNode operation = json.readTree(body);
    boolean policyAccepts =
        submitPolicyValidator.validate(submitDecoder.decode(operation)).isValid();
    boolean facadeAccepts = submitFacade.validate(operation).isValid();

    assertThat(new boolean[] {policyAccepts, facadeAccepts})
        .as(name)
        .containsExactly(expectedPolicyAcceptance, expectedFacadeAcceptance);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cancelParityCases")
  void cancelPolicyAndExistingBodyValidatorAgree(
      String name, String body, boolean expectedAcceptance) throws JsonProcessingException {
    JsonNode operation = json.readTree(body);
    boolean policyAccepts =
        cancelPolicyValidator.validate(cancelDecoder.decode(operation)).isValid();
    boolean facadeAccepts = cancelFacade.validate(operation).isValid();

    assertThat(new boolean[] {policyAccepts, facadeAccepts})
        .as(name)
        .containsExactly(expectedAcceptance, expectedAcceptance);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("duplicateMemberBodies")
  void strictRawParsingRejectsDuplicateMembersAtTheIssuanceBoundary(String name, String body) {
    assertThatThrownBy(() -> parser.parse(issuanceRequest("CHANGE_INVESTMENT_CHOICE", body)))
        .as(name)
        .isInstanceOf(PasswordValidationRequestInvalidException.class)
        .hasNoCause();
  }

  @Test
  void strictRawParsingRejectsTrailingContentWithoutClaimingTheTreeDecoderDetectsDuplicates() {
    assertThatThrownBy(
            () -> parser.parse(issuanceRequest("CHANGE_INVESTMENT_CHOICE", VALID) + " {}"))
        .isInstanceOf(PasswordValidationRequestInvalidException.class)
        .hasNoCause();
  }

  static Stream<Arguments> parityCases() {
    return Stream.of(
        caseOf("valid percentage 60", VALID, true, true),
        caseOf("member order reversed", reversedMembers(), true, true),
        caseOf("body not an object", "[]", false, false),
        caseOf("string body", "\"body\"", false, false),
        caseOf("empty object", "{}", false, false),
        caseOf("unknown top-level member", unknownTopLevelMember(), false, false),
        caseOf("unknown allocation member", unknownAllocationMember(), false, false),
        caseOf(
            "switchSources not array",
            "{\"switchSources\":\"BO\",\"allocations\":[" + oneHundred() + "]}",
            false,
            false),
        caseOf(
            "allocations not array",
            "{\"switchSources\":[\"BO\"],\"allocations\":{}}",
            false,
            false),
        caseOf(
            "switchSources item not string",
            "{\"switchSources\":[1],\"allocations\":[" + oneHundred() + "]}",
            false,
            false),
        caseOf(
            "allocation item not object",
            "{\"switchSources\":[\"BO\"],\"allocations\":[1]}",
            false,
            false),
        caseOf(
            "fundCode not string",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":true,\"percentage\":100}]}",
            false,
            false),
        caseOf("switchSources missing", "{\"allocations\":[" + oneHundred() + "]}", false, false),
        caseOf(
            "switchSources empty",
            "{\"switchSources\":[],\"allocations\":[" + oneHundred() + "]}",
            false,
            false),
        caseOf(
            "switchSources blank",
            "{\"switchSources\":[\" \"],\"allocations\":[" + oneHundred() + "]}",
            false,
            false),
        caseOf(
            "duplicate switchSources values",
            "{\"switchSources\":[\"BO\",\"BO\"],\"allocations\":[" + oneHundred() + "]}",
            false,
            false),
        caseOf("allocations missing", "{\"switchSources\":[\"BO\"]}", false, false),
        caseOf(
            "allocations empty", "{\"switchSources\":[\"BO\"],\"allocations\":[]}", false, false),
        caseOf(
            "fundCode missing",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"percentage\":100}]}",
            false,
            false),
        caseOf(
            "fundCode blank",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":\" \",\"percentage\":100}]}",
            false,
            false),
        caseOf(
            "duplicate funds",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":\"DBLF\",\"percentage\":50},{\"fundCode\":\"DBLF\",\"percentage\":50}]}",
            false,
            false),
        caseOf(
            "percentage missing",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":\"DBLF\"}]}",
            false,
            false),
        caseOf("percentage 60.0", twoAllocationBody("60.0"), false, false),
        caseOf("percentage 6e1", twoAllocationBody("6e1"), false, false),
        caseOf("percentage 1E2", oneAllocationBody("1E2"), false, false),
        caseOf("percentage string 60", twoAllocationBody("\"60\""), false, false),
        caseOf("percentage boolean", twoAllocationBody("true"), false, false),
        caseOf("percentage null", twoAllocationBody("null"), false, false),
        caseOf(
            "percentage outsize integer", oneAllocationBody("100000000000000000000"), false, false),
        caseOf("percentage negative zero", oneAllocationBody("-0"), false, false),
        caseOf("percentage zero", oneAllocationBody("0"), false, false),
        caseOf("percentage 101", oneAllocationBody("101"), false, false),
        caseOf(
            "total 99",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":\"DBLF\",\"percentage\":60},{\"fundCode\":\"DHKM\",\"percentage\":39}]}",
            false,
            false),
        caseOf(
            "total 101",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":\"DBLF\",\"percentage\":51},{\"fundCode\":\"DHKM\",\"percentage\":50}]}",
            false,
            false),
        caseOf(
            "unknown source code remains shape-valid",
            "{\"switchSources\":[\"XX\"],\"allocations\":[" + oneHundred() + "]}",
            true,
            true));
  }

  static Stream<Arguments> cancelParityCases() {
    return Stream.of(
        cancelCase("valid", VALID_CANCEL, true),
        cancelCase("extra member", "{\"referenceNo\":\"260001449\",\"extra\":true}", false),
        cancelCase("legacy refNo", "{\"refNo\":\"260001449\"}", false),
        cancelCase(
            "both public names", "{\"referenceNo\":\"260001449\",\"refNo\":\"260001450\"}", false),
        cancelCase("missing referenceNo", "{}", false),
        cancelCase("null referenceNo", "{\"referenceNo\":null}", false),
        cancelCase("numeric referenceNo", "{\"referenceNo\":260001449}", false),
        cancelCase("boolean referenceNo", "{\"referenceNo\":false}", false),
        cancelCase("array referenceNo", "{\"referenceNo\":[]}", false),
        cancelCase("object referenceNo", "{\"referenceNo\":{}}", false),
        cancelCase("empty referenceNo", "{\"referenceNo\":\"\"}", false),
        cancelCase("whitespace referenceNo", "{\"referenceNo\":\"   \"}", false),
        cancelCase("array root", "[]", false),
        cancelCase("string root", "\"body\"", false),
        cancelCase("empty object", "{}", false));
  }

  static Stream<Arguments> duplicateMemberBodies() {
    return Stream.of(
        Arguments.of("duplicate top-level switchSources", duplicateTopLevelSwitchSources()),
        Arguments.of("duplicate top-level allocations", duplicateTopLevelAllocations()),
        Arguments.of("duplicate allocation percentage", duplicateAllocationPercentage()));
  }

  private static Arguments cancelCase(String name, String body, boolean accepted) {
    return Arguments.of(name, body, accepted);
  }

  private static Arguments caseOf(
      String name, String body, boolean policyAccepted, boolean facadeAccepted) {
    return Arguments.of(name, body, policyAccepted, facadeAccepted);
  }

  private static String oneHundred() {
    return "{\"fundCode\":\"DBLF\",\"percentage\":100}";
  }

  private static String oneAllocationBody(String percentage) {
    return "{\"switchSources\":[\"BO\"],\"allocations\":["
        + "{\"fundCode\":\"DBLF\",\"percentage\":"
        + percentage
        + "}]}";
  }

  private static String twoAllocationBody(String firstPercentage) {
    return "{\"switchSources\":[\"BO\"],\"allocations\":["
        + "{\"fundCode\":\"DBLF\",\"percentage\":"
        + firstPercentage
        + "},{\"fundCode\":\"DHKM\",\"percentage\":40}]}";
  }

  private static String unknownTopLevelMember() {
    return "{\"switchSources\":[\"BO\"],\"allocations\":[" + oneHundred() + "],\"extra\":true}";
  }

  private static String unknownAllocationMember() {
    return "{\"switchSources\":[\"BO\"],\"allocations\":["
        + "{\"fundCode\":\"DBLF\",\"percentage\":100,\"extra\":true}]}";
  }

  private static String duplicateTopLevelSwitchSources() {
    return "{\"switchSources\":[\"EE\"],\"switchSources\":[\"BO\"],\"allocations\":["
        + oneHundred()
        + "]}";
  }

  private static String duplicateTopLevelAllocations() {
    return "{\"switchSources\":[\"BO\"],\"allocations\":["
        + oneHundred()
        + "],\"allocations\":[{\"fundCode\":\"DHKM\",\"percentage\":100}]}";
  }

  private static String duplicateAllocationPercentage() {
    return "{\"switchSources\":[\"BO\"],\"allocations\":["
        + "{\"fundCode\":\"DBLF\",\"percentage\":50,\"percentage\":100}]}";
  }

  private static String reversedMembers() {
    return "{\"allocations\":[{\"percentage\":60,\"fundCode\":\"DBLF\"},"
        + "{\"percentage\":40,\"fundCode\":\"DHKM\"}],\"switchSources\":[\"BO\",\"EE\"]}";
  }

  private static String issuanceRequest(String purpose, String body) {
    return "{\"password\":\"abc\",\"purpose\":\""
        + purpose
        + "\",\"operationPayload\":"
        + body
        + "}";
  }
}

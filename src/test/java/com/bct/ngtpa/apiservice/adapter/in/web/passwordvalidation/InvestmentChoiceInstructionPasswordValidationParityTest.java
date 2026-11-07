package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.InvestmentChoiceInstructionBodyValidator;
import com.bct.ngtpa.apiservice.adapter.out.passwordvalidation.JsonOperationPayloadCanonicalizer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class InvestmentChoiceInstructionPasswordValidationParityTest {

  private static final String VALID =
      "{\"switchSources\":[\"BO\",\"EE\"],\"allocations\":["
          + "{\"fundCode\":\"DBLF\",\"percentage\":60},"
          + "{\"fundCode\":\"DHKM\",\"percentage\":40}]}";

  private final ObjectMapper json = new ObjectMapper();
  private final PasswordValidationRequestParser parser =
      new PasswordValidationRequestParser(new JsonOperationPayloadCanonicalizer(), 3, 5);
  private final InvestmentChoiceInstructionBodyValidator validator =
      new InvestmentChoiceInstructionBodyValidator();

  @ParameterizedTest(name = "{0}")
  @MethodSource("parityCases")
  void issuanceParserAndSubmitBodyValidatorAgree(
      String name, String body, boolean expectedAcceptance) throws JsonProcessingException {
    boolean issuanceAccepts = issuanceAccepts(body);
    boolean submitAccepts = validator.validate(json.readTree(body)).isValid();

    assertThat(new boolean[] {issuanceAccepts, submitAccepts})
        .containsExactly(expectedAcceptance, expectedAcceptance);
  }

  private boolean issuanceAccepts(String body) {
    try {
      parser.parse(
          "{\"password\":\"abc\",\"purpose\":\"CHANGE_INVESTMENT_CHOICE\",\"operationPayload\":"
              + body
              + "}");
      return true;
    } catch (PasswordValidationRequestInvalidException exception) {
      return false;
    }
  }

  private static Stream<Arguments> parityCases() {
    return Stream.of(
        Arguments.of("valid", VALID, true),
        Arguments.of("member order reversed", reversedMembers(), true),
        Arguments.of("body not an object", "[]", false),
        Arguments.of(
            "unknown top-level member",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":\"DBLF\",\"percentage\":100}],\"extra\":true}",
            false),
        Arguments.of(
            "unknown allocation member",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":\"DBLF\",\"percentage\":100,\"extra\":true}]}",
            false),
        Arguments.of(
            "switchSources not array",
            "{\"switchSources\":\"BO\",\"allocations\":[{\"fundCode\":\"DBLF\",\"percentage\":100}]}",
            false),
        Arguments.of(
            "allocations not array", "{\"switchSources\":[\"BO\"],\"allocations\":{}}", false),
        Arguments.of(
            "switchSources item not string",
            "{\"switchSources\":[1],\"allocations\":[{\"fundCode\":\"DBLF\",\"percentage\":100}]}",
            false),
        Arguments.of(
            "allocation item not object",
            "{\"switchSources\":[\"BO\"],\"allocations\":[1]}",
            false),
        Arguments.of(
            "fundCode not string",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":true,\"percentage\":100}]}",
            false),
        Arguments.of(
            "switchSources missing",
            "{\"allocations\":[{\"fundCode\":\"DBLF\",\"percentage\":100}]}",
            false),
        Arguments.of(
            "switchSources empty",
            "{\"switchSources\":[],\"allocations\":[{\"fundCode\":\"DBLF\",\"percentage\":100}]}",
            false),
        Arguments.of(
            "switchSources blank",
            "{\"switchSources\":[\" \"],\"allocations\":[{\"fundCode\":\"DBLF\",\"percentage\":100}]}",
            false),
        Arguments.of(
            "duplicate switchSources",
            "{\"switchSources\":[\"BO\",\"BO\"],\"allocations\":[{\"fundCode\":\"DBLF\",\"percentage\":100}]}",
            false),
        Arguments.of("allocations missing", "{\"switchSources\":[\"BO\"]}", false),
        Arguments.of("allocations empty", "{\"switchSources\":[\"BO\"],\"allocations\":[]}", false),
        Arguments.of(
            "fundCode missing",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"percentage\":100}]}",
            false),
        Arguments.of(
            "fundCode blank",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":\" \",\"percentage\":100}]}",
            false),
        Arguments.of(
            "duplicate funds",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":\"DBLF\",\"percentage\":50},{\"fundCode\":\"DBLF\",\"percentage\":50}]}",
            false),
        Arguments.of(
            "percentage missing",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":\"DBLF\"}]}",
            false),
        Arguments.of(
            "percentage null",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":\"DBLF\",\"percentage\":null}]}",
            false),
        Arguments.of(
            "percentage decimal",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":\"DBLF\",\"percentage\":60.0}]}",
            false),
        Arguments.of(
            "percentage string",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":\"DBLF\",\"percentage\":\"60\"}]}",
            false),
        Arguments.of(
            "percentage boolean",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":\"DBLF\",\"percentage\":true}]}",
            false),
        Arguments.of(
            "percentage zero",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":\"DBLF\",\"percentage\":0}]}",
            false),
        Arguments.of(
            "percentage 101",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":\"DBLF\",\"percentage\":101}]}",
            false),
        Arguments.of(
            "total 99",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":\"DBLF\",\"percentage\":60},{\"fundCode\":\"DHKM\",\"percentage\":39}]}",
            false),
        Arguments.of(
            "total 101",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":\"DBLF\",\"percentage\":51},{\"fundCode\":\"DHKM\",\"percentage\":50}]}",
            false),
        Arguments.of(
            "unknown source code remains shape-valid",
            "{\"switchSources\":[\"XX\"],\"allocations\":[{\"fundCode\":\"DBLF\",\"percentage\":100}]}",
            true));
  }

  private static String reversedMembers() {
    return "{\"allocations\":[{\"percentage\":60,\"fundCode\":\"DBLF\"},"
        + "{\"percentage\":40,\"fundCode\":\"DHKM\"}],\"switchSources\":[\"BO\",\"EE\"]}";
  }
}

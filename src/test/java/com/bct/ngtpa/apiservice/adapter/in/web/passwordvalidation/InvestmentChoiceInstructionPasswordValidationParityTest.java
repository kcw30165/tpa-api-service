package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieReader;
import com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.InvestmentChoiceCancelBodyValidator;
import com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.InvestmentChoiceController;
import com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.InvestmentChoiceInstructionBodyValidator;
import com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.InvestmentChoiceWebMapper;
import com.bct.ngtpa.apiservice.adapter.out.passwordvalidation.JsonOperationPayloadCanonicalizer;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceCancelOutcome;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceCancellation;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceInstructionRequest;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSubmission;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSubmitOutcome;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.port.in.CancelInvestmentChoiceUseCase;
import com.bct.ngtpa.apiservice.application.port.in.GetInvestmentChoiceUseCase;
import com.bct.ngtpa.apiservice.application.port.in.SubmitInvestmentChoiceUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class InvestmentChoiceInstructionPasswordValidationParityTest {

  private static final String VALID =
      "{\"switchSources\":[\"BO\",\"EE\"],\"allocations\":["
          + "{\"fundCode\":\"DBLF\",\"percentage\":60},"
          + "{\"fundCode\":\"DHKM\",\"percentage\":40}]}";
  private static final String VALID_CANCEL =
      """
      {"refNo":"260001449"}
      """;

  private final ObjectMapper json = new ObjectMapper();
  private final PasswordValidationRequestParser parser =
      new PasswordValidationRequestParser(new JsonOperationPayloadCanonicalizer(), 3, 5);
  private final InvestmentChoiceInstructionBodyValidator validator =
      new InvestmentChoiceInstructionBodyValidator();
  private final InvestmentChoiceCancelBodyValidator cancelValidator =
      new InvestmentChoiceCancelBodyValidator();
  private final AtomicReference<InvestmentChoiceInstructionRequest> receivedInstruction =
      new AtomicReference<>();
  private final AtomicReference<String> receivedCancelReferenceNo = new AtomicReference<>();
  private final WebTestClient controllerClient = buildControllerClient();

  @ParameterizedTest(name = "{0}")
  @MethodSource("parityCases")
  void issuanceParserAndSubmitBodyValidatorAgree(
      String name,
      String body,
      boolean expectedIssuanceAcceptance,
      boolean expectedValidatorAcceptance)
      throws JsonProcessingException {
    boolean issuanceAccepts = issuanceAccepts(body);
    boolean submitAccepts = validator.validate(json.readTree(body)).isValid();
    var controllerResponse =
        controllerClient
            .post()
            .uri("/api/v1/investment-choice/instructions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectBody()
            .returnResult();

    assertThat(new boolean[] {issuanceAccepts, submitAccepts})
        .containsExactly(expectedIssuanceAcceptance, expectedValidatorAcceptance);
    assertThat(controllerResponse.getStatus().value())
        .as(name)
        .isEqualTo(expectedValidatorAcceptance ? 200 : 422);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("duplicateMemberCases")
  void controllerKeepsLastDuplicateMemberAndGateIsBodyIntegrityAuthority(
      String name, String body, InvestmentChoiceInstructionRequest expectedInstruction) {
    // Characterization only, not endorsement: raw-body integrity belongs to the gate.
    var controllerResponse =
        controllerClient
            .post()
            .uri("/api/v1/investment-choice/instructions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectBody()
            .returnResult();

    assertThat(controllerResponse.getStatus().value()).as(name).isEqualTo(200);
    assertThat(receivedInstruction.get()).isEqualTo(expectedInstruction);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cancelParityCases")
  void cancelIssuanceParserAndBodyValidatorAgree(
      String name, String body, boolean expectedAcceptance) throws JsonProcessingException {
    boolean issuanceAccepts = cancelIssuanceAccepts(body);
    boolean validatorAccepts = cancelValidator.validate(json.readTree(body)).isValid();
    var controllerResponse =
        controllerClient
            .post()
            .uri("/api/v1/investment-choice/instructions/cancel")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectBody()
            .returnResult();

    assertThat(new boolean[] {issuanceAccepts, validatorAccepts})
        .containsExactly(expectedAcceptance, expectedAcceptance);
    assertThat(controllerResponse.getStatus().value())
        .as(name)
        .isEqualTo(expectedAcceptance ? 200 : 422);
  }

  @Test
  void cancelControllerUsesLastDuplicateMemberAndGateOwnsDuplicateRejection() {
    var controllerResponse =
        controllerClient
            .post()
            .uri("/api/v1/investment-choice/instructions/cancel")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {"refNo":"260001449","refNo":"260001450"}
                """)
            .exchange()
            .expectBody()
            .returnResult();

    assertThat(controllerResponse.getStatus().value()).isEqualTo(200);
    assertThat(receivedCancelReferenceNo.get()).isEqualTo("260001450");
  }

  private WebTestClient buildControllerClient() {
    var context =
        new PortalAccessContext(
            new ActorContext("actor", ActorUserRole.ROLE_USER_MBR),
            new AccountContext(
                "account-reference",
                "JP",
                "policy-number",
                "certificate-number",
                "T",
                "OE",
                TermStatus.BLANK,
                null));
    CurrentPortalAccessContextResolver contexts =
        new CurrentPortalAccessContextResolver() {
          @Override
          public Mono<PortalAccessContext> current() {
            return Mono.just(context);
          }

          @Override
          public Mono<PortalAccessContext> currentOrEmpty() {
            return Mono.just(context);
          }
        };
    GetInvestmentChoiceUseCase getUseCase =
        command -> Mono.error(new AssertionError("GET use case must not run"));
    SubmitInvestmentChoiceUseCase submitUseCase =
        command -> {
          receivedInstruction.set(command.request());
          return Mono.just(
              new InvestmentChoiceSubmitOutcome.Submitted(
                  new InvestmentChoiceSubmission(
                      "bridge-reference", LocalDate.of(2026, 11, 6), LocalTime.NOON)));
        };
    CancelInvestmentChoiceUseCase cancelUseCase =
        command -> {
          receivedCancelReferenceNo.set(command.referenceNo());
          return Mono.just(
              new InvestmentChoiceCancelOutcome.Cancelled(
                  new InvestmentChoiceCancellation(command.referenceNo())));
        };
    AuthenticatedSessionCookieReader sessions =
        exchange -> Optional.of(new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx"));
    var controller =
        new InvestmentChoiceController(
            getUseCase,
            submitUseCase,
            cancelUseCase,
            contexts,
            sessions,
            new InvestmentChoiceWebMapper(),
            json);
    return WebTestClient.bindToController(controller).build();
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

  private boolean cancelIssuanceAccepts(String body) {
    try {
      parser.parse(
          """
          {"password":"abc","purpose":"CANCEL_INVESTMENT_CHOICE","operationPayload":%s}
          """
              .formatted(body));
      return true;
    } catch (PasswordValidationRequestInvalidException exception) {
      return false;
    }
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
        cancelCase("extra member", "{\"refNo\":\"260001449\",\"extra\":true}", false),
        cancelCase("missing refNo", "{}", false),
        cancelCase("null refNo", "{\"refNo\":null}", false),
        cancelCase("numeric refNo", "{\"refNo\":260001449}", false),
        cancelCase("boolean refNo", "{\"refNo\":false}", false),
        cancelCase("array refNo", "{\"refNo\":[]}", false),
        cancelCase("object refNo", "{\"refNo\":{}}", false),
        cancelCase("empty refNo", "{\"refNo\":\"\"}", false),
        cancelCase("whitespace refNo", "{\"refNo\":\"   \"}", false),
        cancelCase("array root", "[]", false),
        cancelCase("string root", "\"body\"", false),
        cancelCase("empty object", "{}", false));
  }

  private static Arguments cancelCase(String name, String body, boolean expectedAcceptance) {
    return Arguments.of(name, body, expectedAcceptance);
  }

  static Stream<Arguments> duplicateMemberCases() {
    return Stream.of(
        Arguments.of(
            "duplicate top-level switchSources",
            duplicateTopLevelSwitchSources(),
            new InvestmentChoiceInstructionRequest(
                List.of("BO"),
                List.of(new InvestmentChoiceInstructionRequest.Allocation("DBLF", 100)))),
        Arguments.of(
            "duplicate top-level allocations",
            duplicateTopLevelAllocations(),
            new InvestmentChoiceInstructionRequest(
                List.of("BO"),
                List.of(new InvestmentChoiceInstructionRequest.Allocation("DHKM", 100)))),
        Arguments.of(
            "duplicate allocation percentage",
            duplicateAllocationPercentage(),
            new InvestmentChoiceInstructionRequest(
                List.of("BO"),
                List.of(new InvestmentChoiceInstructionRequest.Allocation("DBLF", 100)))));
  }

  static Stream<Arguments> duplicateMemberBodies() {
    return Stream.of(
        Arguments.of("duplicate top-level switchSources", duplicateTopLevelSwitchSources()),
        Arguments.of("duplicate top-level allocations", duplicateTopLevelAllocations()),
        Arguments.of("duplicate allocation percentage", duplicateAllocationPercentage()));
  }

  private static Arguments caseOf(
      String name, String body, boolean issuanceAccepts, boolean validatorAccepts) {
    return Arguments.of(name, body, issuanceAccepts, validatorAccepts);
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
}

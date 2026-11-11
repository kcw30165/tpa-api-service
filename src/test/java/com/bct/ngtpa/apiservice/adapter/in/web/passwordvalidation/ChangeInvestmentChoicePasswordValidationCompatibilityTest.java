package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.InvestmentChoiceInstructionBodyValidator;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.ChangeInvestmentChoicePasswordValidationPayload.JsonKind;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckContext;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckResult;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckViolation;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.usecase.investmentchoice.InvestmentChoiceInstructionPrecheckValidator;
import com.bct.ngtpa.apiservice.application.usecase.investmentchoice.InvestmentChoiceSnapshotCacheService;
import com.bct.ngtpa.apiservice.application.usecase.passwordvalidation.ChangeInvestmentChoicePasswordValidationPrecheckPolicy;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ChangeInvestmentChoicePasswordValidationCompatibilityTest {
  private static final String SENSITIVE_SOURCE = "source-sensitive";
  private static final String SENSITIVE_FUND = "fund-sensitive";
  private static final String SENSITIVE_VERSION = "payload-sensitive";
  private static final String SAFE_SESSION_ID = "sid_abcdefghijklmnopqrstuvwx";
  private static final JsonFactory STRICT_JSON_FACTORY =
      JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final ObjectMapper STRICT_JSON = new ObjectMapper(STRICT_JSON_FACTORY);

  private final ChangeInvestmentChoicePasswordValidationPayloadDecoder decoder =
      new ChangeInvestmentChoicePasswordValidationPayloadDecoder();
  private final InvestmentChoiceInstructionPrecheckValidator validator =
      new InvestmentChoiceInstructionPrecheckValidator();
  private final InvestmentChoiceInstructionBodyValidator executionFacade =
      new InvestmentChoiceInstructionBodyValidator();

  @ParameterizedTest(name = "{0}")
  @MethodSource("existingSubmitCorpus")
  void adapterFacadeAndSharedValidatorAgreeAcrossTheExistingSubmitCorpus(
      String caseName, String body) throws JsonProcessingException {
    JsonNode operationPayload = JSON.readTree(body);
    var facadeResult = executionFacade.validate(operationPayload);
    var precheckResult = validator.validate(decoder.decode(operationPayload));

    assertThat(precheckResult.request()).as(caseName).isEqualTo(facadeResult.request());
    assertThat(precheckResult.violations()).as(caseName).isEqualTo(facadeResult.violations());
  }

  @Test
  void decoderPreservesUnknownMembersKindsAndIntegralNumberMetadata()
      throws JsonProcessingException {
    var payload =
        decoder.decode(
            JSON.readTree(
                """
                {"switchSources":[null],"allocations":[
                  {"fundCode":null,"percentage":99.0,"extra":true}],"unknown":true}
                """));

    assertThat(payload.rootKind()).isEqualTo(JsonKind.OBJECT);
    assertThat(payload.rootMembers()).containsExactly("switchSources", "allocations", "unknown");
    assertThat(payload.switchSourcesKind()).isEqualTo(JsonKind.ARRAY);
    assertThat(payload.switchSources()).hasSize(1);
    assertThat(payload.switchSources().getFirst().kind()).isEqualTo(JsonKind.NULL);
    assertThat(payload.allocations()).hasSize(1);
    assertThat(payload.allocations().getFirst().memberNames())
        .containsExactly("fundCode", "percentage", "extra");
    assertThat(payload.allocations().getFirst().fundCode().kind()).isEqualTo(JsonKind.NULL);
    assertThat(payload.allocations().getFirst().percentage().kind()).isEqualTo(JsonKind.NUMBER);
    assertThat(payload.allocations().getFirst().percentage().integralNumber()).isFalse();
  }

  @Test
  void strictRawParsingRejectsDuplicatesAndTrailingTokensBeforeTheJsonNodeDecoder() {
    assertThat(strictSingleRootAccepted("{\"switchSources\":[],\"switchSources\":[]}")).isFalse();
    assertThat(strictSingleRootAccepted("{\"switchSources\":[]} {}")).isFalse();
  }

  @Test
  void decoderAndPolicyDeclareTheCimSubmitPurposeAndSamePayloadType() {
    var policy =
        new ChangeInvestmentChoicePasswordValidationPrecheckPolicy(
            mock(InvestmentChoiceSnapshotCacheService.class), mock(FailureEventPort.class));

    assertThat(decoder.purpose()).isEqualTo(PasswordValidationPurpose.CHANGE_INVESTMENT_CHOICE);
    assertThat(decoder.purpose()).isEqualTo(policy.purpose());
    assertThat(decoder.payloadType()).isEqualTo(policy.payloadType());
  }

  @Test
  void payloadNestedValuesAndPrecheckContextHaveSafeStringForms() throws JsonProcessingException {
    var payload =
        decoder.decode(
            JSON.readTree(
                "{\"switchSources\":[\""
                    + SENSITIVE_SOURCE
                    + "\"],\"allocations\":[{\"fundCode\":\""
                    + SENSITIVE_FUND
                    + "\",\"percentage\":100}]}"));
    var rejected =
        new PasswordValidationPrecheckViolation(
            PasswordValidationPrecheckViolation.Kind.FORM, SENSITIVE_VERSION, List.of());
    var result = PasswordValidationPrecheckResult.rejected(List.of(rejected));
    var instructionResult = validator.validate(payload);
    var executionResult =
        executionFacade.validate(
            JSON.readTree(
                "{\"switchSources\":[\""
                    + SENSITIVE_SOURCE
                    + "\"],\"allocations\":[{\"fundCode\":\""
                    + SENSITIVE_FUND
                    + "\",\"percentage\":100}]}"));
    var context =
        new PasswordValidationPrecheckContext(
            new AuthenticatedSessionId(SAFE_SESSION_ID),
            new PortalAccessContext(
                new ActorContext("actor-sensitive-marker", ActorUserRole.ROLE_INPUT),
                new AccountContext(
                    "account-sensitive-marker",
                    "TEST",
                    "policy-sensitive-marker",
                    null,
                    null,
                    null,
                    null,
                    null)),
            "request-safe");

    assertThat(payload.toString())
        .doesNotContain(SENSITIVE_SOURCE, SENSITIVE_FUND, SENSITIVE_VERSION);
    assertThat(payload.switchSources().getFirst().toString()).doesNotContain(SENSITIVE_SOURCE);
    assertThat(payload.allocations().getFirst().toString()).doesNotContain(SENSITIVE_FUND);
    assertThat(payload.allocations().getFirst().percentage().toString()).doesNotContain("100");
    assertThat(decoder.toString())
        .doesNotContain(SENSITIVE_SOURCE, SENSITIVE_FUND, SENSITIVE_VERSION);
    assertThat(instructionResult.toString())
        .doesNotContain(SENSITIVE_SOURCE, SENSITIVE_FUND, SENSITIVE_VERSION);
    assertThat(executionResult.toString()).doesNotContain(SENSITIVE_SOURCE, SENSITIVE_FUND);
    assertThat(result.toString()).doesNotContain(SENSITIVE_VERSION);
    assertThat(rejected.toString()).doesNotContain(SENSITIVE_VERSION);
    assertThat(context.toString()).doesNotContain(SAFE_SESSION_ID, "policy-sensitive-marker");
  }

  static Stream<Arguments> existingSubmitCorpus() {
    return InvestmentChoiceInstructionPasswordValidationParityTest.parityCases()
        .map(
            arguments -> {
              Object[] values = arguments.get();
              return Arguments.of(values[0], values[1]);
            });
  }

  private static boolean strictSingleRootAccepted(String body) {
    try (JsonParser parser = STRICT_JSON_FACTORY.createParser(body)) {
      JsonNode root = STRICT_JSON.readTree(parser);
      return root != null && parser.nextToken() == null;
    } catch (IOException exception) {
      return false;
    }
  }
}

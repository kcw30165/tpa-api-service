package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.InvestmentChoiceCancelBodyValidator;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceViolation;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.CancelInvestmentChoicePasswordValidationPayload.JsonKind;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckContext;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.usecase.investmentchoice.InvestmentChoiceCancelPrecheckValidator;
import com.bct.ngtpa.apiservice.application.usecase.passwordvalidation.CancelInvestmentChoicePasswordValidationPrecheckPolicy;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CancelInvestmentChoicePasswordValidationCompatibilityTest {

  private static final String SENSITIVE_REFERENCE = "private-reference-marker";
  private static final String CANCEL_PURPOSE = "CANCEL_INVESTMENT_CHOICE";
  private static final String SAFE_SESSION_ID = "sid_abcdefghijklmnopqrstuvwx";
  private static final String VALID_BODY = "{\"referenceNo\":\"valid-reference\"}";
  private static final ObjectMapper JSON = new ObjectMapper();

  private final CancelInvestmentChoicePasswordValidationPayloadDecoder decoder =
      new CancelInvestmentChoicePasswordValidationPayloadDecoder();
  private final InvestmentChoiceCancelPrecheckValidator validator =
      new InvestmentChoiceCancelPrecheckValidator();
  private final InvestmentChoiceCancelBodyValidator executionFacade =
      new InvestmentChoiceCancelBodyValidator();
  private final PasswordValidationRequestParser parser =
      new PasswordValidationRequestParser(
          PasswordValidationTestRegistrations.decoders(), JSON, 3, 5);

  @ParameterizedTest(name = "{0}")
  @MethodSource("cancelCorpus")
  void existingFacadeAndPurposeValidatorAgree(String caseName, String body)
      throws JsonProcessingException {
    JsonNode operationPayload = JSON.readTree(body);
    var facadeResult = executionFacade.validate(operationPayload);
    var precheckResult = validator.validate(decoder.decode(operationPayload));

    assertThat(precheckResult.referenceNo()).as(caseName).isEqualTo(facadeResult.referenceNo());
    assertThat(precheckResult.violations()).as(caseName).isEqualTo(facadeResult.violations());
  }

  @Test
  void decoderPreservesRootMembersReferencePresenceKindAndExactValue()
      throws JsonProcessingException {
    var payload =
        decoder.decode(
            JSON.readTree(
                "{\"referenceNo\":\"" + SENSITIVE_REFERENCE + "\",\"unrecognized\":true}"));
    var missingReference = decoder.decode(JSON.readTree("{}"));
    var nullReference = decoder.decode(JSON.readTree("{\"referenceNo\":null}"));

    assertThat(payload.rootKind()).isEqualTo(JsonKind.OBJECT);
    assertThat(payload.rootMembers()).containsExactly("referenceNo", "unrecognized");
    assertThat(payload.referenceNoKind()).isEqualTo(JsonKind.STRING);
    assertThat(payload.referenceNoValue()).isEqualTo(SENSITIVE_REFERENCE);
    assertThat(missingReference.referenceNoKind()).isEqualTo(JsonKind.MISSING);
    assertThat(missingReference.referenceNoValue()).isNull();
    assertThat(nullReference.referenceNoKind()).isEqualTo(JsonKind.NULL);
    assertThat(nullReference.referenceNoValue()).isNull();
    assertThat(decoder.decode(JSON.readTree("{\"referenceNo\":123}")).referenceNoValue())
        .isEqualTo("123");
    assertThat(decoder.decode(JSON.readTree("{\"referenceNo\":true}")).referenceNoValue())
        .isEqualTo("true");
    assertThat(decoder.decode(JSON.readTree("{\"referenceNo\":[]}")).referenceNoValue())
        .isEqualTo("[]");
    assertThat(decoder.decode(JSON.readTree("{\"referenceNo\":{}}")).referenceNoValue())
        .isEqualTo("{}");
  }

  @Test
  void legacyRefNoAndBothNamesAreRejectedAsUnknownMembers() throws JsonProcessingException {
    for (String body :
        Stream.of(
                "{\"refNo\":\"valid-reference\"}",
                "{\"referenceNo\":\"valid-reference\",\"refNo\":\"other-reference\"}")
            .toList()) {
      var result = validator.validate(decoder.decode(JSON.readTree(body)));

      assertThat(result.violations())
          .containsExactly(
              new InvestmentChoiceViolation(
                  InvestmentChoiceViolation.Kind.FORM,
                  ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID,
                  java.util.List.of()));
    }
  }

  @Test
  void rawIssuanceParserRejectsDuplicateCancelMemberAndTrailingContent() {
    assertThatThrownBy(
            () ->
                parser.parse(
                    issuanceRequest("{\"referenceNo\":\"first\",\"referenceNo\":\"second\"}")))
        .isInstanceOf(PasswordValidationRequestInvalidException.class);
    assertThatThrownBy(() -> parser.parse(issuanceRequest(VALID_BODY) + " trailing"))
        .isInstanceOf(PasswordValidationRequestInvalidException.class);
  }

  @Test
  void decoderAndPolicyDeclareTheCancelPurposeAndSamePayloadType() {
    var policy =
        new CancelInvestmentChoicePasswordValidationPrecheckPolicy(
            org.mockito.Mockito.mock(
                com.bct.ngtpa.apiservice.application.usecase.investmentchoice
                    .InvestmentChoiceSnapshotCacheService.class),
            org.mockito.Mockito.mock(
                com.bct.ngtpa.apiservice.application.port.out.FailureEventPort.class));

    assertThat(decoder.purpose()).isEqualTo(PasswordValidationPurpose.CANCEL_INVESTMENT_CHOICE);
    assertThat(decoder.purpose()).isEqualTo(policy.purpose());
    assertThat(decoder.payloadType()).isEqualTo(policy.payloadType());
  }

  @Test
  void payloadValidatorFacadeAndContextStringFormsDoNotExposeReference()
      throws JsonProcessingException {
    JsonNode operationPayload = JSON.readTree("{\"referenceNo\":\"" + SENSITIVE_REFERENCE + "\"}");
    var payload = decoder.decode(operationPayload);
    var result = validator.validate(payload);
    var facadeResult = executionFacade.validate(operationPayload);
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

    assertThat(payload.toString()).doesNotContain(SENSITIVE_REFERENCE);
    assertThat(decoder.toString()).doesNotContain(SENSITIVE_REFERENCE);
    assertThat(result.toString()).doesNotContain(SENSITIVE_REFERENCE);
    assertThat(facadeResult.toString()).doesNotContain(SENSITIVE_REFERENCE);
    assertThat(context.toString())
        .doesNotContain(SAFE_SESSION_ID, "account-sensitive-marker", "policy-sensitive-marker");
  }

  static Stream<Arguments> cancelCorpus() {
    Stream<Arguments> existing =
        InvestmentChoiceInstructionPasswordValidationParityTest.cancelParityCases()
            .map(
                arguments -> {
                  Object[] values = arguments.get();
                  return Arguments.of(values[0], values[1]);
                });
    return Stream.concat(
        existing,
        Stream.of(
            Arguments.of("leading zeroes preserved", "{\"referenceNo\":\"0000123\"}"),
            Arguments.of(
                "nonblank surrounding whitespace preserved", "{\"referenceNo\":\" 0000123 \"}")));
  }

  private static String issuanceRequest(String operationPayload) {
    return "{\"password\":\"fixture-password\",\"purpose\":\""
        + CANCEL_PURPOSE
        + "\",\"operationPayload\":"
        + operationPayload
        + "}";
  }
}

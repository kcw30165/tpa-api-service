package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import static org.assertj.core.api.Assertions.*;

import com.bct.ngtpa.apiservice.adapter.out.passwordvalidation.JsonOperationPayloadCanonicalizer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordValidationRequestParserTest {
  private static final String CANCEL_PURPOSE = "CANCEL_INVESTMENT_CHOICE";
  private static final String REFERENCE_NO_FIELD = "referenceNo";
  private static final String CANCEL_REFERENCE_NO = "260001449";
  private static final String OTHER_REFERENCE_NO = "260001450";
  private static final String CANCEL_OPERATION = quotedReferenceNoOperation(CANCEL_REFERENCE_NO);
  private static final String DIFFERENT_CANCEL_OPERATION =
      quotedReferenceNoOperation(OTHER_REFERENCE_NO);
  private static final String OPERATION =
      "{\"formVersion\":\"v1\",\"applyToAllAccounts\":true,\"fields\":{\"unknown\":null},\"extra\":[1.000]}";
  private static final String CIM_OPERATION =
      "{\"switchSources\":[\"BO\",\"EE\"],\"allocations\":[{\"fundCode\":\"DBLF\",\"percentage\":60},{\"fundCode\":\"DHKM\",\"percentage\":40}]}";
  private final PasswordValidationRequestParser parser =
      new PasswordValidationRequestParser(
          PasswordValidationTestRegistrations.decoders(),
          new com.fasterxml.jackson.databind.ObjectMapper(),
          3,
          5);

  @Test
  void usesExplicitLoginBoundsPreservesSpacesAndBindsAllRawOperationMembers() {
    var parsed = parser.parse(body(" AbC ", OPERATION));
    assertThat(parsed.password()).isEqualTo(" AbC ");
    assertThat(parsed.operationPayloadJson()).isEqualTo(OPERATION);
    assertThat(new JsonOperationPayloadCanonicalizer().digest(parsed.operationPayloadJson()))
        .isEqualTo(new JsonOperationPayloadCanonicalizer().digest(OPERATION));
    assertThat(parsed.toString()).doesNotContain(" AbC ", OPERATION);
    assertThat(parser.parse(body("   ", OPERATION)).password()).isEqualTo("   ");
    String tooShort = body("ab", OPERATION);
    String tooLong = body("abcdef", OPERATION);
    assertThatThrownBy(() -> parser.parse(tooShort))
        .isInstanceOf(PasswordValidationRequestInvalidException.class)
        .hasMessage("Invalid password-validation request");
    assertThatThrownBy(() -> parser.parse(tooLong))
        .isInstanceOf(PasswordValidationRequestInvalidException.class);
  }

  @Test
  void cimPayloadBindingIgnoresObjectMemberOrderButPreservesAllocationOrder() {
    var parsed = parser.parse(body("abc", "CHANGE_INVESTMENT_CHOICE", CIM_OPERATION));
    var reorderedMembers =
        parser.parse(
            body(
                "abc",
                "CHANGE_INVESTMENT_CHOICE",
                "{\"allocations\":[{\"percentage\":60,\"fundCode\":\"DBLF\"},{\"percentage\":40,\"fundCode\":\"DHKM\"}],\"switchSources\":[\"BO\",\"EE\"]}"));
    var reorderedAllocations =
        parser.parse(
            body(
                "abc",
                "CHANGE_INVESTMENT_CHOICE",
                "{\"switchSources\":[\"BO\",\"EE\"],\"allocations\":[{\"fundCode\":\"DHKM\",\"percentage\":40},{\"fundCode\":\"DBLF\",\"percentage\":60}]}"));

    var canonicalizer = new JsonOperationPayloadCanonicalizer();
    assertThat(canonicalizer.digest(parsed.operationPayloadJson()))
        .isEqualTo(canonicalizer.digest(reorderedMembers.operationPayloadJson()));
    assertThat(canonicalizer.digest(parsed.operationPayloadJson()))
        .isNotEqualTo(canonicalizer.digest(reorderedAllocations.operationPayloadJson()));
  }

  @Test
  void cancelPayloadBindingIsStableForTheSameReferenceAndDifferentForAnother() {
    var parsed = parser.parse(body("abc", CANCEL_PURPOSE, CANCEL_OPERATION));
    var repeated = parser.parse(body("abc", CANCEL_PURPOSE, CANCEL_OPERATION));
    var different = parser.parse(body("abc", CANCEL_PURPOSE, DIFFERENT_CANCEL_OPERATION));

    assertThat(parsed.purpose().name()).isEqualTo(CANCEL_PURPOSE);
    var canonicalizer = new JsonOperationPayloadCanonicalizer();
    assertThat(parsed.purpose().name()).isEqualTo(CANCEL_PURPOSE);
    assertThat(canonicalizer.digest(parsed.operationPayloadJson()))
        .isEqualTo(canonicalizer.digest(repeated.operationPayloadJson()))
        .isNotEqualTo(canonicalizer.digest(different.operationPayloadJson()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidCancelOperations")
  void cancelPayloadShapesAreDecodedForPurposeOwnedValidation(String name, String operation) {
    var parsed = parser.parse(body("abc", CANCEL_PURPOSE, operation));
    assertThat(parsed.payload())
        .isInstanceOf(
            com.bct.ngtpa.apiservice.application.dto.passwordvalidation
                .CancelInvestmentChoicePasswordValidationPayload.class);
  }

  private static Stream<Arguments> invalidCancelOperations() {
    return Stream.of(
        Arguments.of("root-not-object", "[]"),
        Arguments.of("unknown-member", unknownMemberOperation()),
        Arguments.of("missing-referenceNo", "{}"),
        Arguments.of("null-referenceNo", referenceNoOperation("null")),
        Arguments.of("number-referenceNo", referenceNoOperation(CANCEL_REFERENCE_NO)),
        Arguments.of("boolean-referenceNo", referenceNoOperation("true")),
        Arguments.of("array-referenceNo", referenceNoOperation("[]")),
        Arguments.of("object-referenceNo", referenceNoOperation("{}")),
        Arguments.of("blank-referenceNo", referenceNoOperation("\" \\t \"")),
        Arguments.of("legacy-refNo", "{\"refNo\":\"260001449\"}"),
        Arguments.of(
            "both-public-names", "{\"referenceNo\":\"260001449\",\"refNo\":\"260001450\"}"),
        Arguments.of("unknown-member", unknownMemberOperation()));
  }

  private static String quotedReferenceNoOperation(String value) {
    return """
        {"%s":"%s"}
        """
        .formatted(REFERENCE_NO_FIELD, value)
        .strip();
  }

  private static String referenceNoOperation(String value) {
    return """
        {"%s":%s}
        """
        .formatted(REFERENCE_NO_FIELD, value)
        .strip();
  }

  private static String unknownMemberOperation() {
    return """
        {"%s":"%s","extra":true}
        """
        .formatted(REFERENCE_NO_FIELD, CANCEL_REFERENCE_NO)
        .strip();
  }

  private static String duplicateReferenceNoOperation() {
    return """
        {"%s":"%s","%s":"%s"}
        """
        .formatted(REFERENCE_NO_FIELD, CANCEL_REFERENCE_NO, REFERENCE_NO_FIELD, OTHER_REFERENCE_NO)
        .strip();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"formVersion\":null,\"applyToAllAccounts\":false,\"fields\":{\"x\":1}}",
        "{\"formVersion\":\" \",\"applyToAllAccounts\":false,\"fields\":{\"x\":1}}",
        "{\"formVersion\":\"v1\",\"fields\":{\"x\":1}}",
        "{\"formVersion\":\"v1\",\"applyToAllAccounts\":null,\"fields\":{\"x\":1}}",
        "{\"formVersion\":\"v1\",\"applyToAllAccounts\":false}",
        "{\"formVersion\":\"v1\",\"applyToAllAccounts\":false,\"fields\":null}",
        "{\"formVersion\":\"v1\",\"applyToAllAccounts\":false,\"fields\":[]}",
        "{\"formVersion\":\"v1\",\"applyToAllAccounts\":false,\"fields\":{}}"
      })
  void personalInformationShapeValidationIsDeferredToItsPurposePolicy(String operation) {
    var parsed = parser.parse(body("abc", operation));
    assertThat(parsed.payload())
        .isInstanceOf(
            com.bct.ngtpa.apiservice.application.dto.passwordvalidation
                .PersonalInformationPasswordValidationPayload.class);
  }

  @Test
  void strictRawParsingStillRejectsDuplicateMembers() {
    assertThatThrownBy(
            () -> parser.parse(body("abc", CANCEL_PURPOSE, duplicateReferenceNoOperation())))
        .isInstanceOf(PasswordValidationRequestInvalidException.class)
        .hasMessage("Invalid password-validation request")
        .hasNoCause();
  }

  @Test
  void parserDiagnosticsAndTrailingContentNeverRetainSensitiveValues() {
    String raw = body("abc", OPERATION) + " diagnostic-marker";
    assertThatThrownBy(() -> parser.parse(raw))
        .isInstanceOf(PasswordValidationRequestInvalidException.class)
        .hasMessage("Invalid password-validation request")
        .hasNoCause();
    assertThatThrownBy(() -> parser.parse(null))
        .isInstanceOf(PasswordValidationRequestInvalidException.class)
        .hasNoCause();
  }

  private static String body(String password, String operation) {
    return body(password, "PERSONAL_INFORMATION_UPDATE", operation);
  }

  private static String body(String password, String purpose, String operation) {
    return "{\"password\":\""
        + password
        + "\",\"purpose\":\""
        + purpose
        + "\",\"operationPayload\":"
        + operation
        + "}";
  }
}

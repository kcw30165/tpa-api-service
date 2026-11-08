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
  private static final String REF_NO_FIELD = "refNo";
  private static final String CANCEL_REFERENCE_NO = "260001449";
  private static final String OTHER_REFERENCE_NO = "260001450";
  private static final String CANCEL_OPERATION = quotedRefNoOperation(CANCEL_REFERENCE_NO);
  private static final String DIFFERENT_CANCEL_OPERATION = quotedRefNoOperation(OTHER_REFERENCE_NO);
  private static final String OPERATION =
      "{\"formVersion\":\"v1\",\"applyToAllAccounts\":true,\"fields\":{\"unknown\":null},\"extra\":[1.000]}";
  private static final String CIM_OPERATION =
      "{\"switchSources\":[\"BO\",\"EE\"],\"allocations\":[{\"fundCode\":\"DBLF\",\"percentage\":60},{\"fundCode\":\"DHKM\",\"percentage\":40}]}";
  private final PasswordValidationRequestParser parser =
      new PasswordValidationRequestParser(new JsonOperationPayloadCanonicalizer(), 3, 5);

  @Test
  void usesExplicitLoginBoundsPreservesSpacesAndBindsAllRawOperationMembers() {
    var parsed = parser.parse(body(" AbC ", OPERATION));
    assertThat(parsed.password()).isEqualTo(" AbC ");
    assertThat(parsed.binding())
        .isEqualTo(new JsonOperationPayloadCanonicalizer().digest(OPERATION));
    assertThat(parsed.toString()).doesNotContain(" AbC ", OPERATION, parsed.binding().sha256());
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

    assertThat(parsed.binding()).isEqualTo(reorderedMembers.binding());
    assertThat(parsed.binding()).isNotEqualTo(reorderedAllocations.binding());
  }

  @Test
  void cancelPayloadBindingIsStableForTheSameReferenceAndDifferentForAnother() {
    var parsed = parser.parse(body("abc", CANCEL_PURPOSE, CANCEL_OPERATION));
    var repeated = parser.parse(body("abc", CANCEL_PURPOSE, CANCEL_OPERATION));
    var different = parser.parse(body("abc", CANCEL_PURPOSE, DIFFERENT_CANCEL_OPERATION));

    assertThat(parsed.purpose().name()).isEqualTo(CANCEL_PURPOSE);
    assertThat(parsed.binding()).isEqualTo(repeated.binding()).isNotEqualTo(different.binding());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidCancelOperations")
  void invalidCancelOperationShapesAreRejected(String name, String operation) {
    String raw = body("abc", CANCEL_PURPOSE, operation);

    assertThatThrownBy(() -> parser.parse(raw))
        .isInstanceOf(PasswordValidationRequestInvalidException.class)
        .hasMessage("Invalid password-validation request")
        .hasNoCause();
  }

  private static Stream<Arguments> invalidCancelOperations() {
    return Stream.of(
        Arguments.of("root-not-object", "[]"),
        Arguments.of("unknown-member", unknownMemberOperation()),
        Arguments.of("missing-refNo", "{}"),
        Arguments.of("null-refNo", refNoOperation("null")),
        Arguments.of("number-refNo", refNoOperation(CANCEL_REFERENCE_NO)),
        Arguments.of("boolean-refNo", refNoOperation("true")),
        Arguments.of("array-refNo", refNoOperation("[]")),
        Arguments.of("object-refNo", refNoOperation("{}")),
        Arguments.of("blank-refNo", refNoOperation("\" \\t \"")),
        Arguments.of("duplicate-refNo", duplicateRefNoOperation()));
  }

  private static String quotedRefNoOperation(String value) {
    return """
        {"%s":"%s"}
        """
        .formatted(REF_NO_FIELD, value)
        .strip();
  }

  private static String refNoOperation(String value) {
    return """
        {"%s":%s}
        """
        .formatted(REF_NO_FIELD, value)
        .strip();
  }

  private static String unknownMemberOperation() {
    return """
        {"%s":"%s","extra":true}
        """
        .formatted(REF_NO_FIELD, CANCEL_REFERENCE_NO)
        .strip();
  }

  private static String duplicateRefNoOperation() {
    return """
        {"%s":"%s","%s":"%s"}
        """
        .formatted(REF_NO_FIELD, CANCEL_REFERENCE_NO, REF_NO_FIELD, OTHER_REFERENCE_NO)
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
  void malformedOperationShapeHasValueFreeExceptionWithoutCause(String operation) {
    String raw = body("abc", operation);
    assertThatThrownBy(() -> parser.parse(raw))
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

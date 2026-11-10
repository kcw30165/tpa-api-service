package com.bct.ngtpa.apiservice.application.usecase.investmentchoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceViolation;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.CancelInvestmentChoicePasswordValidationPayload;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.CancelInvestmentChoicePasswordValidationPayload.JsonKind;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class InvestmentChoiceCancelPrecheckValidatorTest {

  private static final String REFERENCE_NO = " 000123 ";
  private static final String REF_NO = "refNo";

  private final InvestmentChoiceCancelPrecheckValidator validator =
      new InvestmentChoiceCancelPrecheckValidator();

  @ParameterizedTest(name = "{0}")
  @MethodSource("cv1Payloads")
  void returnsCv1FormViolationBeforeCv2(
      String name, CancelInvestmentChoicePasswordValidationPayload payload) {
    var result = validator.validate(payload);

    assertThat(result.isValid()).as(name).isFalse();
    assertThat(result.referenceNo()).as(name).isNull();
    assertThat(result.violations())
        .as(name)
        .containsExactly(
            violation(
                InvestmentChoiceViolation.Kind.FORM, ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cv2Payloads")
  void returnsCv2ForMissingOrBlankString(
      String name, CancelInvestmentChoicePasswordValidationPayload payload) {
    var result = validator.validate(payload);

    assertThat(result.isValid()).as(name).isFalse();
    assertThat(result.referenceNo()).as(name).isNull();
    assertThat(result.violations())
        .as(name)
        .containsExactly(
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_CANCEL_REF_REQUIRED,
                REF_NO));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("acceptedReferences")
  void acceptsAndPreservesReferenceWithoutNormalization(String name, String referenceNo) {
    var result =
        validator.validate(payload(JsonKind.OBJECT, List.of(REF_NO), JsonKind.STRING, referenceNo));

    assertThat(result.isValid()).as(name).isTrue();
    assertThat(result.referenceNo()).as(name).isEqualTo(referenceNo);
    assertThat(result.violations()).as(name).isEmpty();
  }

  static Stream<Arguments> cv1Payloads() {
    return Stream.of(
        Arguments.of("missing body", payload(JsonKind.MISSING, List.of(), JsonKind.MISSING, null)),
        Arguments.of("null root", payload(JsonKind.NULL, List.of(), JsonKind.MISSING, null)),
        Arguments.of("array root", payload(JsonKind.ARRAY, List.of(), JsonKind.MISSING, null)),
        Arguments.of("string root", payload(JsonKind.STRING, List.of(), JsonKind.MISSING, null)),
        Arguments.of("boolean root", payload(JsonKind.BOOLEAN, List.of(), JsonKind.MISSING, null)),
        Arguments.of("number root", payload(JsonKind.NUMBER, List.of(), JsonKind.MISSING, null)),
        Arguments.of(
            "unknown member precedes missing reference",
            payload(JsonKind.OBJECT, List.of("other"), JsonKind.MISSING, null)),
        Arguments.of(
            "unknown member precedes blank reference",
            payload(JsonKind.OBJECT, List.of(REF_NO, "other"), JsonKind.STRING, "")),
        Arguments.of(
            "null reference", payload(JsonKind.OBJECT, List.of(REF_NO), JsonKind.NULL, null)),
        Arguments.of(
            "number reference", payload(JsonKind.OBJECT, List.of(REF_NO), JsonKind.NUMBER, null)),
        Arguments.of(
            "boolean reference", payload(JsonKind.OBJECT, List.of(REF_NO), JsonKind.BOOLEAN, null)),
        Arguments.of(
            "array reference", payload(JsonKind.OBJECT, List.of(REF_NO), JsonKind.ARRAY, null)),
        Arguments.of(
            "object reference", payload(JsonKind.OBJECT, List.of(REF_NO), JsonKind.OBJECT, null)));
  }

  static Stream<Arguments> cv2Payloads() {
    return Stream.of(
        Arguments.of(
            "missing reference", payload(JsonKind.OBJECT, List.of(), JsonKind.MISSING, null)),
        Arguments.of(
            "empty reference", payload(JsonKind.OBJECT, List.of(REF_NO), JsonKind.STRING, "")),
        Arguments.of(
            "whitespace-only reference",
            payload(JsonKind.OBJECT, List.of(REF_NO), JsonKind.STRING, " \t ")));
  }

  static Stream<Arguments> acceptedReferences() {
    return Stream.of(
        Arguments.of("leading zeroes retained", "000123"),
        Arguments.of("surrounding whitespace retained", REFERENCE_NO));
  }

  private static CancelInvestmentChoicePasswordValidationPayload payload(
      JsonKind rootKind, List<String> rootMembers, JsonKind refNoKind, String referenceNo) {
    String refNoValue =
        refNoKind == JsonKind.MISSING || refNoKind == JsonKind.NULL
            ? null
            : referenceNo == null ? "non-string value" : referenceNo;
    return new CancelInvestmentChoicePasswordValidationPayload(
        rootKind, rootMembers, refNoKind, refNoValue);
  }

  private static InvestmentChoiceViolation violation(
      InvestmentChoiceViolation.Kind kind, String code, String... targets) {
    return new InvestmentChoiceViolation(kind, code, List.of(targets));
  }
}

package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import static org.assertj.core.api.Assertions.*;

import com.bct.ngtpa.apiservice.adapter.out.passwordvalidation.JsonOperationPayloadCanonicalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordValidationRequestParserTest {
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

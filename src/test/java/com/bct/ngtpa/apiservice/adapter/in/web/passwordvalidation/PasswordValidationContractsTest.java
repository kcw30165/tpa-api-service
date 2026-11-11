package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordCheckCommand;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationIssuanceResult;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class PasswordValidationContractsTest {
  private static final String CANCEL_PURPOSE = "CANCEL_INVESTMENT_CHOICE";
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void featureErrorsAreRegisteredWithStableCodeOnlyContracts() throws Exception {
    String[][] codes = {
      {"PASSWORD_VALIDATION_REQUEST_INVALID", "err.password-validation.request.invalid"},
      {"PASSWORD_VALIDATION_PASSWORD_INVALID", "err.password-validation.password.invalid"},
      {"PASSWORD_VALIDATION_ACCOUNT_LOCKED", "err.password-validation.account.locked"},
      {
        "PASSWORD_VALIDATION_AUTHORIZATION_INVALID", "err.password-validation.authorization.invalid"
      },
      {"PASSWORD_VALIDATION_RATE_LIMITED", "err.password-validation.rate-limited"},
      {"PASSWORD_VALIDATION_UNAVAILABLE", "err.password-validation.unavailable"}
    };
    for (var code : codes) {
      assertThat(com.bct.ngtpa.apiservice.shared.error.ErrorCodes.class.getField(code[0]).get(null))
          .isEqualTo(code[1]);
    }
  }

  @Test
  void onlyExactSupportedPurposesAreAccepted() {
    assertThat(PasswordValidationPurpose.parse("PERSONAL_INFORMATION_UPDATE"))
        .isEqualTo(PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE);
    assertThat(PasswordValidationPurpose.parse("CHANGE_INVESTMENT_CHOICE"))
        .isEqualTo(PasswordValidationPurpose.CHANGE_INVESTMENT_CHOICE);
    assertThat(PasswordValidationPurpose.parse(CANCEL_PURPOSE).name()).isEqualTo(CANCEL_PURPOSE);
    for (String value :
        new String[] {
          null,
          "",
          "personal_information_update",
          " PERSONAL_INFORMATION_UPDATE",
          "INVESTMENT_CHANGE",
          "FUND_SWITCH",
          "UNKNOWN"
        }) {
      assertThatThrownBy(() -> PasswordValidationPurpose.parse(value))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Unsupported password-validation purpose");
    }
  }

  @Test
  void changeOfInvestmentChoicePurposeUsesItsEnumNameAsWireValue() {
    assertThat(PasswordValidationPurpose.valueOf("CHANGE_INVESTMENT_CHOICE").name())
        .isEqualTo("CHANGE_INVESTMENT_CHOICE");
  }

  @Test
  void cancelInvestmentChoicePurposeUsesItsEnumNameAsWireValue() {
    assertThat(PasswordValidationPurpose.parse(CANCEL_PURPOSE).name()).isEqualTo(CANCEL_PURPOSE);
  }

  @Test
  void requestAndCommandPreservePasswordAndRedactAllSensitiveFields() throws Exception {
    var payload = mapper.readTree("{\"fields\":{\"test\":\"reviewed-value\"}}");
    var request = new PasswordValidationRequest(" AbC ", "PERSONAL_INFORMATION_UPDATE", payload);
    var command =
        new PasswordCheckCommand(
            "test-customer", request.password(), ActorUserRole.ROLE_INPUT, " DB ");
    assertThat(request.password()).isEqualTo(" AbC ");
    assertThat(command.password()).isEqualTo(" AbC ");
    assertThat(command.customerId()).isEqualTo("test-customer");
    assertThat(command.accountEnv()).isEqualTo("DB");
    assertThat(command.toString()).contains("accountEnv=<redacted>").doesNotContain("DB");
    assertThat(request.toString())
        .contains("password=<redacted>", "operationPayload=<redacted>")
        .doesNotContain(" AbC ", "reviewed-value");
    assertThat(command.toString())
        .contains("customerId=<redacted>", "password=<redacted>")
        .doesNotContain("test-customer", " AbC ");
    assertThatThrownBy(() -> new PasswordCheckCommand("test", "x", null, "DB"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void resultPinsDataWrapperAndNeverPrintsToken() throws Exception {
    var data = new PasswordValidationResult.Data("test-only-value", 300);
    var result = new PasswordValidationResult(data);
    var envelope = MutationResponse.success(ApiStatus.SUCCESS, result, List.of());
    var json = mapper.readTree(mapper.writeValueAsString(envelope));
    assertThat(json.path("result").path("data").path("token").asText())
        .isEqualTo("test-only-value");
    assertThat(json.path("result").path("data").path("expiresInSeconds").asInt()).isEqualTo(300);
    assertThat(json.has("requestId")).isFalse();
    assertThat(result.toString()).doesNotContain("test-only-value");
    assertThat(data.toString()).contains("token=<redacted>").doesNotContain("test-only-value");
  }

  @Test
  void restrictedCimIssuanceUsesTheApprovedBusinessResponse() throws Exception {
    var webMapper =
        new PasswordValidationWebMapper(
            new AuthenticatedSessionCookieWriter(new AuthWorkflowProperties.AuthenticatedCookie()));
    var response =
        webMapper.toResponse(
            PasswordValidationIssuanceResult.featureBusinessRejected(
                ErrorCodes.INVESTMENT_CHOICE_FUNCTION_UNAVAILABLE),
            MockServerWebExchange.from(MockServerHttpRequest.post("/").build()).getResponse());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    var json = mapper.readTree(mapper.writeValueAsBytes(response.getBody()));
    assertThat(json.path("success").asBoolean()).isFalse();
    assertThat(json.path("status").asText()).isEqualTo("BUSINESS_REJECTED");
    assertThat(json.at("/errors/0/type").asText()).isEqualTo("BUSINESS");
    assertThat(json.at("/errors/0/code").asText())
        .isEqualTo(ErrorCodes.INVESTMENT_CHOICE_FUNCTION_UNAVAILABLE);
    assertThat(json.at("/errors/0/targets").isArray()).isTrue();
    assertThat(json.at("/errors/0/targets")).isEmpty();
    assertThat(json.at("/errors/0/message").isMissingNode()).isTrue();
    assertThat(json.at("/errors/0/severity").asText()).isEqualTo("ERROR");
    assertThat(json.at("/errors/0/source").asText()).isEqualTo("SERVER");
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.NullAndEmptySource
  @org.junit.jupiter.params.provider.ValueSource(strings = {" ", "\t\r\n"})
  void commandRejectsUnusableAccountEnvironment(String accountEnv) {
    assertThatThrownBy(
            () -> new PasswordCheckCommand("test", "x", ActorUserRole.ROLE_INPUT, accountEnv))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void requestRejectsIdentityOverridesRatherThanAcceptingCustId() {
    assertThatThrownBy(
            () ->
                mapper.readValue(
                    "{\"password\":\"x\",\"purpose\":\"PERSONAL_INFORMATION_UPDATE\","
                        + "\"operationPayload\":{},\"cust-id\":\"override\"}",
                    PasswordValidationRequest.class))
        .isInstanceOf(com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException.class);
  }
}

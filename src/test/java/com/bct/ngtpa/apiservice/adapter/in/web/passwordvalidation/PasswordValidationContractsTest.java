package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordCheckCommand;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class PasswordValidationContractsTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void onlyExactInitialPurposeIsAccepted() {
    assertThat(PasswordValidationPurpose.parse("PERSONAL_INFORMATION_UPDATE"))
        .isEqualTo(PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE);
    for (String value :
        new String[] {null, "", "personal_information_update", " PERSONAL_INFORMATION_UPDATE",
          "INVESTMENT_CHANGE", "FUND_SWITCH", "UNKNOWN"}) {
      assertThatThrownBy(() -> PasswordValidationPurpose.parse(value))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Unsupported password-validation purpose");
    }
  }

  @Test
  void requestAndCommandPreservePasswordAndRedactAllSensitiveFields() throws Exception {
    var payload = mapper.readTree("{\"fields\":{\"test\":\"reviewed-value\"}}");
    var request = new PasswordValidationRequest(" AbC ", "PERSONAL_INFORMATION_UPDATE", payload);
    var command =
        new PasswordCheckCommand("test-customer", request.password(), ActorUserRole.ROLE_INPUT);
    assertThat(request.password()).isEqualTo(" AbC ");
    assertThat(command.password()).isEqualTo(" AbC ");
    assertThat(command.customerId()).isEqualTo("test-customer");
    assertThat(request.toString()).contains("password=<redacted>", "operationPayload=<redacted>")
        .doesNotContain(" AbC ", "reviewed-value");
    assertThat(command.toString()).contains("customerId=<redacted>", "password=<redacted>")
        .doesNotContain("test-customer", " AbC ");
    assertThatThrownBy(() -> new PasswordCheckCommand("test", "x", null))
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
  void requestRejectsIdentityOverridesRatherThanAcceptingCustId() {
    assertThatThrownBy(
            () -> mapper.readValue(
                "{\"password\":\"x\",\"purpose\":\"PERSONAL_INFORMATION_UPDATE\","
                    + "\"operationPayload\":{},\"cust-id\":\"override\"}",
                PasswordValidationRequest.class))
        .isInstanceOf(com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException.class);
  }
}

package com.bct.ngtpa.apiservice.application.dto.session.authflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthFlowSessionProtectedUsernameCheckContractTest {

  @Test
  void sessionDataHasNoPlaintextUsernameCheckComponent() {
    assertThat(
            Arrays.stream(AuthFlowSessionData.class.getRecordComponents())
                .map(RecordComponent::getName))
        .containsExactly("contactType", "maskedDestination", "attributes")
        .doesNotContain("usernameCheck");
  }

  @Test
  void encryptedUsernameCheckIsStoredOnlyAsProtectedAttributes() {
    var data =
        new AuthFlowSessionData(
            "EMAIL",
            "m****r@example.com",
            Map.of(
                RegistrationUsernameCheckAttributeNames.USERNAME_CHECK_CIPHERTEXT, "enc-value",
                RegistrationUsernameCheckAttributeNames.USERNAME_CHECK_KEY_VERSION, "v1",
                RegistrationUsernameCheckAttributeNames.USERNAME_CHECK_ALGORITHM, "AES_GCM_V1"));

    assertThat(data.attributes())
        .containsEntry(
            RegistrationUsernameCheckAttributeNames.USERNAME_CHECK_CIPHERTEXT, "enc-value");
    assertThat(data.toString()).doesNotContain("enc-value");
  }
}

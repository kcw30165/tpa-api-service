package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationSessionContextPersistenceGuardTest {

  @Test
  void registrationSessionAdapterDoesNotDropEncryptedContextAttributes() throws Exception {
    Path adapter =
        Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/out/registration/RegistrationAuthFlowSessionAdapter.java");
    assertThat(Files.exists(adapter)).isTrue();
    String source = Files.readString(adapter);
    assertThat(source).contains("RegistrationAuthFlowContextAttributeNames.CONTEXT_CIPHERTEXT");
    assertThat(source).contains("attributes == null ? Map.of() : Map.copyOf(attributes)");
    assertThat(source)
        .doesNotContain("This fallback preserves behavior but does not store encrypted context");
  }

  @Test
  void registrationSessionAdapterMarksOtpVerified() throws Exception {
    Path adapter =
        Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/out/registration/RegistrationAuthFlowSessionAdapter.java");
    String source = Files.readString(adapter);
    assertThat(source)
        .contains("markOtpVerified", "AuthFlowStatus.OTP_VERIFIED", "UpdateAuthFlowStatusCommand");
  }
}

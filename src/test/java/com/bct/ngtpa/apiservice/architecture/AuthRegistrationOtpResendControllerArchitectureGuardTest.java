package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthRegistrationOtpResendControllerArchitectureGuardTest {

  @Test
  void otpResendControllerDoesNotDependOnOutboundAdapters() throws Exception {
    Path controller =
        Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/RegistrationController.java");
    if (!Files.exists(controller)) {
      return;
    }
    String source = Files.readString(controller);
    assertThat(source)
        .doesNotContain(
            "adapter.out.redis",
            "adapter.out.apim",
            "adapter.out.sms",
            "adapter.out.email",
            "ApimWebClientFacade",
            "ReactiveRedisTemplate",
            "OtpChallengePort",
            "MessageDeliveryPort",
            "RegistrationLookupPort");
  }
}

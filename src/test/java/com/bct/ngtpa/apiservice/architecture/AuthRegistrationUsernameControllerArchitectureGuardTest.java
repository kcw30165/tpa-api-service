package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthRegistrationUsernameControllerArchitectureGuardTest {
  @Test
  void usernameCheckControllerStaysThinAndDoesNotDependOnOutboundAdaptersOrSessionPorts()
      throws Exception {
    Path controller =
        Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/RegistrationController.java");
    if (!Files.exists(controller)) {
      throw new AssertionError("Expected username-check controller to exist: " + controller);
    }
    String source = Files.readString(controller);
    assertThat(source)
        .doesNotContain(
            "RegistrationUsernameAvailabilityPort",
            "DownstreamErrorClassificationPort",
            "RegistrationSessionPort",
            "AuthFlowSessionPort",
            "adapter.out.redis",
            "adapter.out.apim",
            "ReactiveRedisTemplate",
            "ApimWebClientFacade");
    assertThat(source)
        .contains("CheckRegistrationUsernameUseCase", "RegistrationUsernameCheckWebMapper");
  }
}

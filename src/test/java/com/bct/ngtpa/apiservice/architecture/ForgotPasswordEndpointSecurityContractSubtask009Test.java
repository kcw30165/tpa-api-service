package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ForgotPasswordEndpointSecurityContractSubtask009Test {

  @Test
  void forgotPasswordControllersDoNotRequireAccountRef() throws Exception {
    assertThat(source("AuthForgotPasswordController.java")).doesNotContain("Account-Ref");
    assertThat(source("AuthForgotPasswordOtpController.java")).doesNotContain("Account-Ref");
  }

  @Test
  void forgotPasswordControllersEchoRequestIdOnPublicEndpoints() throws Exception {
    assertThat(source("AuthForgotPasswordController.java"))
        .contains("response.getHeaders().set(RequestCorrelation.REQUEST_ID_HEADER");
    assertThat(source("AuthForgotPasswordOtpController.java"))
        .contains("response.getHeaders().set(RequestCorrelation.REQUEST_ID_HEADER");
  }

  @Test
  void protectedForgotPasswordEndpointsRequireExpectedCookieAndHeaderGuards() throws Exception {
    String controller = source("AuthForgotPasswordController.java");
    String otp = source("AuthForgotPasswordOtpController.java");

    assertThat(controller)
        .contains("auth_flow_sid")
        .contains("X-XSRF-TOKEN")
        .contains("Origin")
        .contains("Referer")
        .contains("Idempotency-Key");
    assertThat(otp)
        .contains("auth_flow_sid")
        .contains("X-XSRF-TOKEN")
        .contains("Origin")
        .contains("Referer")
        .contains("Idempotency-Key");
  }

  private static String source(String fileName) throws Exception {
    try (var paths =
        Files.walk(Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller"))) {
      Path path =
          paths
              .filter(Files::isRegularFile)
              .filter(candidate -> candidate.getFileName().toString().equals(fileName))
              .findFirst()
              .orElseThrow();
      return Files.readString(path);
    }
  }
}

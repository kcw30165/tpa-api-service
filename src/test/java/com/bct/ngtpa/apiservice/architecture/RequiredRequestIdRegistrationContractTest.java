package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RequiredRequestIdRegistrationContractTest {

  @Test
  void globalApiFilterRequiresClientSuppliedRequestIdBeforeBusinessProcessing() throws Exception {
    String filter =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/filter/RequiredRequestIdWebFilter.java"));
    assertThat(filter)
        .contains("path.startsWith(API_PREFIX)")
        .contains("REQUEST_ID_HEADER")
        .contains("HttpStatus.BAD_REQUEST")
        .contains("ErrorCodes.REQUEST_ID_MISSING");
  }

  @Test
  void registrationAndCaptchaEndpointsRemainUnderApiPrefix() throws Exception {
    String captcha =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/captcha/CaptchaController.java"));
    String registration =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthRegistrationController.java"));
    String otp =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthRegistrationOtpController.java"));
    String username =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthRegistrationUsernameController.java"));

    assertThat(captcha).contains("/api/v1/auth/captcha");
    assertThat(registration).contains("/api/v1/auth/registration");
    assertThat(otp).contains("/api/v1/auth/registration");
    assertThat(username).contains("/api/v1/auth/registration");
  }

  @Test
  void requestIdErrorCodeIsStableAndPrefixed() throws Exception {
    String errorCodes =
        Files.readString(
            Path.of("src/main/java/com/bct/ngtpa/apiservice/shared/error/ErrorCodes.java"));
    assertThat(errorCodes).contains("REQUEST_ID_MISSING").contains("err.request.id.missing");
  }
}

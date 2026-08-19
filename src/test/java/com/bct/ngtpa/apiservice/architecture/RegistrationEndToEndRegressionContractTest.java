package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Static end-to-end guard over the configured registration journey. */
class RegistrationEndToEndRegressionContractTest {

  @Test
  void journeyContainsEveryAgreedStepWithoutSchemeType() throws Exception {
    String yaml = Files.readString(Path.of("src/main/resources/application-page-registration.yml"));

    assertThat(yaml)
        .contains(
            "registration-start:",
            "registration-otp-verification:",
            "registration-trust-selection:",
            "registration-username-check:",
            "registration-password-setup:",
            "registration-success:",
            "registration-cancelled:");
    assertThat(yaml).doesNotContain("schemeType");
  }

  @Test
  void successJourneyNavigationIsConfigurationOwned() throws Exception {
    String yaml = Files.readString(Path.of("src/main/resources/application-page-registration.yml"));
    for (String nextStep : List.of("SELECT_TRUST", "RETURN_TO_LOGIN")) {
      assertThat(yaml).contains("nextStep: " + nextStep);
    }
  }

  @Test
  void publicErrorNavigationIsConfigurationOwned() throws Exception {
    String yaml =
        Files.readString(Path.of("src/main/resources/application-registration-errors.yml"));
    assertThat(yaml)
        .contains(
            "next-step: VERIFY_OTP",
            "next-step: SELECT_TRUST",
            "next-step: USERNAME_CHECK",
            "next-step: PASSWORD_SETUP",
            "next-step: RETURN_TO_LOGIN");
    assertThat(yaml).contains("targets:", "next-step:").doesNotContain("messages:", "zh_HK:");
  }
}

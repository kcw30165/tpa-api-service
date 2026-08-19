package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationPublicErrorOriginHidingTest {

  @Test
  void registrationPublicMappingsDoNotExposeDownstreamOrigin() throws Exception {
    String username =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/username/CheckRegistrationUsernameService.java"));
    String complete =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/complete/CompleteRegistrationService.java"));
    String yaml = Files.readString(Path.of("src/main/resources/application-page-registration.yml"));

    assertThat(username)
        .containsIgnoringWhitespaces("err.registration.username.isRegistered")
        .containsIgnoringWhitespaces("err.registration.username.checkFailed");
    assertThat(complete)
        .containsIgnoringWhitespaces("RegistrationErrorKey.REGISTRATION_COMPLETE_FAILED")
        .doesNotContain("SharedWorkflowErrorKey.DOWNSTREAM_BUSINESS_REJECTED");

    assertThat(yaml)
        .containsIgnoringWhitespaces("responseCode: err.registration.username.isRegistered")
        .containsIgnoringWhitespaces("responseCode: err.registration.username.checkFailed")
        .containsIgnoringWhitespaces("responseCode: err.registration.trustLookup.failed")
        .containsIgnoringWhitespaces("responseCode: err.registration.complete.failed")
        .doesNotContain("responseCode: err.workflow.downstream.businessRejected")
        .doesNotContain("responseCode: downstream.generic.businessRejected");
  }
}

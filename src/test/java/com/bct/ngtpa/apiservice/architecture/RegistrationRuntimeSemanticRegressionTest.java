package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationRuntimeSemanticRegressionTest {
  @Test
  void unexpectedLookupErrorsLeaveTheBusinessResultPath() throws Exception {
    String status = read("RegistrationLookupStatus.java");
    String mapper = read("TrpSearchUnregMemberApimResultMapper.java");
    String handler = read("ApiExceptionHandler.java");
    assertThat(status)
        .containsIgnoringWhitespaces("ELIGIBLE", "NOT_ELIGIBLE")
        .doesNotContain("UPSTREAM_BUSINESS_REJECTED");
    assertThat(mapper)
        .containsIgnoringWhitespaces("RegistrationLookupDownstreamException")
        .containsIgnoringWhitespaces("err.tpa.member.not.found");
    assertThat(handler)
        .containsIgnoringWhitespaces("HttpStatus.SERVICE_UNAVAILABLE")
        .containsIgnoringWhitespaces("SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE");
  }

  @Test
  void resendSendRateLimitIsBusinessOutcomeWithLocalizedMessage() throws Exception {
    String mapper = read("RegistrationOtpResendWebMapper.java");
    String controller = read("RegistrationController.java");
    assertThat(mapper)
        .containsIgnoringWhitespaces(
            "SEND_RATE_LIMITED, MAX_RESEND_ATTEMPTS_REACHED -> WorkflowResultStatus.BUSINESS_FAILED")
        .doesNotContain("ErrorMessageResolver");
    assertThat(controller)
        .containsIgnoringWhitespaces("toMutationResponse(result, acceptLanguage)");
  }

  private String read(String name) throws Exception {
    try (var paths = Files.walk(Path.of("src/main/java"))) {
      Path path =
          paths.filter(p -> p.getFileName().toString().equals(name)).findFirst().orElseThrow();
      return Files.readString(path);
    }
  }
}

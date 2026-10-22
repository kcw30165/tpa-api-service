package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorContext;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorDescriptor;
import com.bct.ngtpa.apiservice.adapter.in.web.error.RegistrationExceptionMappings;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupDownstreamException;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RegistrationRuntimeSemanticRegressionTest {
  @Test
  void unexpectedLookupErrorsLeaveTheBusinessResultPath() throws Exception {
    String status = read("RegistrationLookupStatus.java");
    String mapper = read("TrpSearchMemberApimResultMapper.java");
    assertThat(status)
        .containsIgnoringWhitespaces("ELIGIBLE", "NOT_ELIGIBLE")
        .doesNotContain("UPSTREAM_BUSINESS_REJECTED");
    assertThat(mapper)
        .containsIgnoringWhitespaces("RegistrationLookupDownstreamException")
        .containsIgnoringWhitespaces("err.tpa.member.not.found");

    // Sub-task 1.4 rewrite: the handler's mapping logic moved out of ApiExceptionHandler.java
    // into RegistrationExceptionMappings; assert the resolved behaviour directly instead of
    // reading ApiExceptionHandler.java source text.
    var mapping = RegistrationExceptionMappings.lookupDownstream();
    var exception =
        new RegistrationLookupDownstreamException(
            RegistrationLookupDownstreamException.Reason.UNEXPECTED_ERROR, "err.tpa.unexpected");
    ErrorDescriptor descriptor = mapping.describe(exception, new ErrorContext("GET", "/x", null));
    assertThat(descriptor.httpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(descriptor.apiStatus()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(descriptor.errorCode())
        .isEqualTo(SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value());
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
    Path registrationMapper =
        Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/registration/mapper", name);
    if (Files.exists(registrationMapper)) {
      return Files.readString(registrationMapper);
    }
    try (var paths = Files.walk(Path.of("src/main/java"))) {
      Path path =
          paths.filter(p -> p.getFileName().toString().equals(name)).findFirst().orElseThrow();
      return Files.readString(path);
    }
  }
}

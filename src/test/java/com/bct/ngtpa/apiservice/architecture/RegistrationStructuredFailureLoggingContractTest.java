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

class RegistrationStructuredFailureLoggingContractTest {
  @Test
  void lookupContractFailureIsLoggedThroughGenericPort() throws Exception {
    String adapter = read("ApimRegistrationLookupAdapter.java");
    assertThat(adapter)
        .contains("FailureEventPort")
        .contains("registration.lookup.downstream_contract_failure")
        .contains("FailureOrigin.DOWNSTREAM_CONTRACT")
        .contains("TRPSearchMember")
        .contains("SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE")
        .contains("503");
  }

  @Test
  void lookupDiagnosticValuesAreMaskedNotHashed() throws Exception {
    String adapter = read("ApimRegistrationLookupAdapter.java");
    assertThat(adapter)
        .contains("MaskingService")
        .contains("maskingService.maskIdentity")
        .contains("maskingService.maskDateOfBirth")
        .doesNotContain("MessageDigest", "SHA-256");
  }

  /**
   * Sub-task 1.4 rewrite: previously read {@code ApiExceptionHandler.java} source text for the
   * handler's status/error-code mapping, which moved into {@code RegistrationExceptionMappings} in
   * this sub-task. Asserts the same contract (service-unavailable, system error, the shared
   * downstream-unavailable workflow code) against the resolved {@link ErrorDescriptor} instead.
   */
  @Test
  void lookupExceptionReturnsSanitizedServiceUnavailable() {
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
  void everyCompletedNon2xxResponseUsesFailedEventAndSeverity() throws Exception {
    String filter = read("RequestLoggingWebFilter.java");
    assertThat(filter)
        .contains("http.request.failed")
        .contains("status >= 500")
        .contains("status >= 400")
        .contains("log.error")
        .contains("log.warn");
  }

  @Test
  void semanticEventDoesNotLogRawRequestBodyOrSecrets() throws Exception {
    String adapter = read("ApimRegistrationLookupAdapter.java");
    assertThat(adapter)
        .doesNotContain("requestBody")
        .doesNotContain("captchaAnswer")
        .doesNotContain("password")
        .doesNotContain("csrfToken")
        .doesNotContain("auth_flow_sid");
  }

  private String read(String name) throws Exception {
    try (var paths = Files.walk(Path.of("src/main/java"))) {
      Path path =
          paths
              .filter(candidate -> candidate.getFileName().toString().equals(name))
              .findFirst()
              .orElseThrow();
      return Files.readString(path);
    }
  }
}

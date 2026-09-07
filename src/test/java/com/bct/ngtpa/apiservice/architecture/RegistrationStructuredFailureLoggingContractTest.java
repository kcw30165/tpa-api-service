package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

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
    String masker = read("RegistrationLookupLogMasker.java");
    assertThat(masker)
        .contains("maskedLocal")
        .contains("substring(text.length() - suffix)")
        .doesNotContain("MessageDigest", "SHA-256");
  }

  @Test
  void lookupExceptionReturnsSanitizedServiceUnavailable() throws Exception {
    String handler = read("ApiExceptionHandler.java");
    assertThat(handler)
        .contains("RegistrationLookupDownstreamException.class")
        .contains("HttpStatus.SERVICE_UNAVAILABLE")
        .contains("SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE")
        .contains("ApiStatus.SYSTEM_ERROR");
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

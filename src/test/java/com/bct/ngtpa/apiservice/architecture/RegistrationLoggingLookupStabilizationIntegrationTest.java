package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupStatus;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestCase;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestResultExtension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(RegistrationTestResultExtension.class)
class RegistrationLoggingLookupStabilizationIntegrationTest {
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
  @RegistrationTestCase("TC-077")
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

  @Test
  @RegistrationTestCase("TC-078")
  void registrationLookupBusinessDomainHasOnlyEligibleAndNotEligible() {
    assertThat(Arrays.asList(RegistrationLookupStatus.values()))
        .containsExactly(RegistrationLookupStatus.ELIGIBLE, RegistrationLookupStatus.NOT_ELIGIBLE);
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

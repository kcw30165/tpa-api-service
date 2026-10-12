package com.bct.ngtpa.apiservice.application.dto.auth.login;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CredentialAuthenticationResultFailureTest {

  private static CredentialAuthenticationStatus aFailureStatus() {
    return Arrays.stream(CredentialAuthenticationStatus.values())
        .filter(value -> value != CredentialAuthenticationStatus.AUTHENTICATED)
        .findFirst()
        .orElseThrow();
  }

  @Test
  void failureCarriesOnlyTheStatusAndTheFailCount() {
    CredentialAuthenticationStatus status = aFailureStatus();

    CredentialAuthenticationResult result = CredentialAuthenticationResult.failure(status, 3);

    assertThat(result.status()).isEqualTo(status);
    assertThat(result.failCount()).isEqualTo(3);
    assertThat(result.normalizedUsername()).isNull();
    assertThat(result.userRole()).isNull();
    assertThat(result.destinations()).isEmpty();
    assertThat(result.downstreamCode()).isNull();
    assertThat(result.trustCode()).isNull();
    assertThat(result.schemeName()).isNull();
    assertThat(result.schemeNameChi()).isNull();
  }

  @Test
  void failureKeepsAnAbsentFailCount() {
    CredentialAuthenticationResult result =
        CredentialAuthenticationResult.failure(aFailureStatus(), null);

    assertThat(result.failCount()).isNull();
    assertThat(result.destinations()).isEmpty();
  }
}

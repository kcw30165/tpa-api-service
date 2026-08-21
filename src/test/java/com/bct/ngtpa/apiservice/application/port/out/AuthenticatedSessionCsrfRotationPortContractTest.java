package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthenticatedSessionCsrfRotationPortContractTest {
  @Test
  void loginReplayPortRetainsTheSharedAtomicRotationCapability() {
    assertThat(AuthenticatedSessionCsrfRotationPort.class)
        .isAssignableFrom(AuthenticatedLoginReplaySessionPort.class);
  }
}

package com.bct.ngtpa.apiservice.application.usecase.auth.csrf;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RefreshCsrfTokenServiceStructureTest {
  @Test
  void orchestrationDependsOnTypedSessionFamilyServices() {
    assertThat(RefreshCsrfTokenService.class.getDeclaredFields())
        .extracting(field -> field.getType().getName())
        .contains(
            AuthenticatedSessionCsrfRefreshService.class.getName(),
            PreAuthSessionCsrfRefreshService.class.getName());
  }
}

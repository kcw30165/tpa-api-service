package com.bct.ngtpa.apiservice.application.usecase.auth.csrf;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class RefreshCsrfTokenServiceStructureTest {
  @Test
  void orchestrationDependsOnTypedSessionFamilyServices() {
    assertThat(RefreshCsrfTokenService.class.getDeclaredFields())
        .extracting(Field::getType)
        .contains(
            AuthenticatedSessionCsrfRefreshService.class, PreAuthSessionCsrfRefreshService.class);
  }
}

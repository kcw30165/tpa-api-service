package com.bct.ngtpa.apiservice.application.usecase.auth.csrf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.csrf.RefreshCsrfTokenStatus;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionCsrfRotationPort;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AuthenticatedSessionCsrfRefreshServiceContractTest {
  @Test
  void constructorUsesTheCapabilityScopedRotationPort() {
    assertThat(AuthenticatedSessionCsrfRefreshService.class.getDeclaredConstructors())
        .anySatisfy(
            constructor ->
                assertThat(Arrays.asList(constructor.getParameterTypes()))
                    .contains(AuthenticatedSessionCsrfRotationPort.class));
  }

  @Test
  void failureOutcomeCannotContainRawToken() {
    var outcome =
        AuthenticatedSessionCsrfRefreshService.Outcome.failure(RefreshCsrfTokenStatus.CONFLICT);
    assertThat(outcome.csrfToken()).isNull();
    assertThat(outcome.toString()).doesNotContain("raw-token");
    assertThatThrownBy(
            () ->
                new AuthenticatedSessionCsrfRefreshService.Outcome(
                    RefreshCsrfTokenStatus.CONFLICT, "raw-token"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

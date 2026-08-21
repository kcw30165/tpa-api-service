package com.bct.ngtpa.apiservice.application.usecase.auth.csrf;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.port.in.RefreshCsrfTokenUseCase;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class RefreshCsrfTokenServiceFailFirstTest {
  @Test
  void orchestrationServiceMustExistAndImplementInboundPort() throws Exception {
    Class<?> service =
        Class.forName(
            "com.bct.ngtpa.apiservice.application.usecase.auth.csrf.RefreshCsrfTokenService");

    assertThat(RefreshCsrfTokenUseCase.class).isAssignableFrom(service);
    Method refresh =
        service.getMethod(
            "refresh",
            Class.forName(
                "com.bct.ngtpa.apiservice.application.dto.auth.csrf.RefreshCsrfTokenCommand"));
    assertThat(refresh.getReturnType().getName()).isEqualTo("reactor.core.publisher.Mono");
  }

  @Test
  void authenticatedRotationServiceMustDependOnCapabilityScopedPort() throws Exception {
    Class<?> service =
        Class.forName(
            "com.bct.ngtpa.apiservice.application.usecase.auth.csrf.AuthenticatedSessionCsrfRefreshService");
    Class<?> sharedPort =
        Class.forName(
            "com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionCsrfRotationPort");

    assertThat(service.getDeclaredConstructors())
        .anySatisfy(
            constructor -> assertThat(constructor.getParameterTypes()).contains(sharedPort));
  }
}

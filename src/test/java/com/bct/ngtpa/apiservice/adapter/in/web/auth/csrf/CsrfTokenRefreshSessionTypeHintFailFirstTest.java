package com.bct.ngtpa.apiservice.adapter.in.web.auth.csrf;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Fail-first for M4 (session-type hint on {@code POST /api/v1/auth/csrf-token/refresh}).
 *
 * <p>Uses only string-based reflection (no direct imports of the new types), matching this
 * codebase's established FailFirstTest convention (see {@code
 * RefreshCsrfTokenServiceFailFirstTest}), so this file compiles before the new types exist and
 * fails at runtime for the expected reason rather than a compile error.
 */
class CsrfTokenRefreshSessionTypeHintFailFirstTest {

  @Test
  void requestDtoWithSessionTypeFieldMustExist() throws Exception {
    Class<?> dto =
        Class.forName(
            "com.bct.ngtpa.apiservice.adapter.in.web.auth.csrf.dto.CsrfTokenRefreshRequest");
    Method sessionType = dto.getMethod("sessionType");
    assertThat(sessionType.getReturnType().getName())
        .isEqualTo("com.bct.ngtpa.apiservice.application.dto.auth.csrf.CsrfRefreshSessionType");
  }

  @Test
  void controllerRefreshMethodMustAcceptTheRequestDto() throws Exception {
    Class<?> controller =
        Class.forName(
            "com.bct.ngtpa.apiservice.adapter.in.web.auth.csrf.CsrfTokenRefreshController");
    Class<?> dto =
        Class.forName(
            "com.bct.ngtpa.apiservice.adapter.in.web.auth.csrf.dto.CsrfTokenRefreshRequest");
    boolean found =
        Arrays.stream(controller.getMethods())
            .filter(m -> m.getName().equals("refresh"))
            .anyMatch(m -> Arrays.asList(m.getParameterTypes()).contains(dto));
    assertThat(found).as("a refresh(...) overload must accept the request DTO").isTrue();
  }

  @Test
  void httpResponsesMustExposeASessionTypeCookieMissingCase() throws Exception {
    Class<?> responses =
        Class.forName(
            "com.bct.ngtpa.apiservice.adapter.in.web.auth.csrf.CsrfTokenRefreshHttpResponses");
    Method method = responses.getMethod("sessionTypeCookieMissing");
    assertThat(method.getReturnType().getName())
        .isEqualTo("org.springframework.http.ResponseEntity");
  }
}

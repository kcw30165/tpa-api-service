package com.bct.ngtpa.apiservice.adapter.in.web.auth.csrf;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class CsrfTokenRefreshControllerFailFirstTest {
  @Test
  void controllerContractMustUseTheApprovedPostRoute() throws Exception {
    Class<?> controller =
        Class.forName(
            "com.bct.ngtpa.apiservice.adapter.in.web.auth.csrf.CsrfTokenRefreshController");
    RequestMapping classMapping = controller.getAnnotation(RequestMapping.class);
    assertThat(classMapping).isNotNull();
    assertThat(classMapping.value()).containsExactly("/api/v1/auth/csrf-token");

    Method refresh =
        java.util.Arrays.stream(controller.getDeclaredMethods())
            .filter(method -> method.getAnnotation(PostMapping.class) != null)
            .findFirst()
            .orElseThrow();
    assertThat(refresh.getAnnotation(PostMapping.class).value()).containsExactly("/refresh");
  }
}

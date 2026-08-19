package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.lang.reflect.Method;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class AuthFlowSessionPortContractShapeTest {
  @Test
  void authFlowSessionPortExposesLifecycleOperations() throws Exception {
    assertMethod(
        "create",
        new Class<?>[] {AuthFlowSessionCreateCommand.class},
        "reactor.core.publisher.Mono<com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession>");
    assertMethod(
        "load",
        new Class<?>[] {AuthFlowSessionId.class},
        "reactor.core.publisher.Mono<java.util.Optional<com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession>>");
    assertMethod(
        "update",
        new Class<?>[] {AuthFlowSession.class},
        "reactor.core.publisher.Mono<com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession>");
    assertMethod(
        "touch",
        new Class<?>[] {AuthFlowSessionId.class, Instant.class},
        "reactor.core.publisher.Mono<com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession>");
    assertMethod(
        "complete",
        new Class<?>[] {AuthFlowSessionId.class, Instant.class},
        "reactor.core.publisher.Mono<com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession>");
    assertMethod(
        "cancel",
        new Class<?>[] {AuthFlowSessionId.class, Instant.class},
        "reactor.core.publisher.Mono<com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession>");
    assertMethod(
        "delete",
        new Class<?>[] {AuthFlowSessionId.class},
        "reactor.core.publisher.Mono<java.lang.Void>");
  }

  private static void assertMethod(String name, Class<?>[] params, String expectedReturnType)
      throws Exception {
    Method method = AuthFlowSessionPort.class.getMethod(name, params);
    assertThat(method.getReturnType()).isEqualTo(Mono.class);
    assertThat(method.getGenericReturnType().getTypeName()).isEqualTo(expectedReturnType);
  }
}

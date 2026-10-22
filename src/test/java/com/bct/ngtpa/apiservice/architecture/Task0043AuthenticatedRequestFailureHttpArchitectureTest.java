package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistryConfig;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.AuthenticatedPortalContextWebExceptionHandler;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedPortalContextResolutionException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.test.StepVerifier;

class Task0043AuthenticatedRequestFailureHttpArchitectureTest {
  @Test
  void filterAndResolverUseSameTypedFailure() throws Exception {
    String f =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/filter/PortalAccessContextWebFilter.java"));
    String e =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/session/authenticated/AuthenticatedPortalContextResolutionException.java"));
    assertThat(f)
        .contains("new AuthenticatedPortalContextResolutionException()")
        .doesNotContain("new IllegalStateException");
    assertThat(e).doesNotContain("sid_", "accountRef", "policyNo");
  }

  /**
   * Sub-task 1.6-fix (milestone-end gate fix, script 031 investigation): this previously read
   * {@code AuthenticatedPortalContextWebExceptionHandler.java} source text for the literal {@code
   * "SECURITY_AUTHENTICATION_REQUIRED"}, which moved to {@code
   * AuthenticatedPortalContextExceptionMappings.java} in sub-task 1.5. Rewritten as a behaviour
   * test: the handler still implements {@code WebExceptionHandler} (structural check, unchanged),
   * still never calls {@code getMessage()} or {@code printStackTrace} on the failure (source-text
   * check, unchanged -- this is still the right tool for "does this class leak raw exception
   * details"), and a real {@code handle()} invocation now proves the pre-controller failure
   * contract behaviourally: HTTP 401 and the {@code SECURITY_AUTHENTICATION_REQUIRED} error code in
   * the actual response body.
   */
  @Test
  void webExceptionHandlerOwnsPreControllerFailureContract() throws Exception {
    String h =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/filter/AuthenticatedPortalContextWebExceptionHandler.java"));
    assertThat(h)
        .contains("implements WebExceptionHandler")
        .doesNotContain("getMessage()", "printStackTrace");

    var handler =
        new AuthenticatedPortalContextWebExceptionHandler(
            new ObjectMapper(), new ExceptionMappingRegistryConfig().exceptionMappingRegistry());
    assertThat(handler).isInstanceOf(WebExceptionHandler.class);

    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/fund-price"));

    StepVerifier.create(
            handler.handle(exchange, new AuthenticatedPortalContextResolutionException()))
        .verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    String body = exchange.getResponse().getBodyAsString().block();
    assertThat(body).contains(ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED);
  }
}

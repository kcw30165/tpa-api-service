package com.bct.ngtpa.apiservice.adapter.in.web.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistryConfig;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedPortalContextResolutionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

class AuthenticatedPortalContextWebExceptionHandlerTest {
  @Test
  void mapsAuthenticatedContextFailureToGenericUnauthorizedEnvelope() {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/personal-information")
                .header("X-Request-Id", "req-1"));
    var handler =
        new AuthenticatedPortalContextWebExceptionHandler(
            new ObjectMapper(), new ExceptionMappingRegistryConfig().exceptionMappingRegistry());
    StepVerifier.create(
            handler.handle(exchange, new AuthenticatedPortalContextResolutionException()))
        .verifyComplete();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(exchange.getResponse().getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_JSON);
    String body = exchange.getResponse().getBodyAsString().block();
    assertThat(body)
        .contains("err.security.authentication.required", "AUTHENTICATION_REQUIRED")
        .doesNotContain("sid_", "acc_", "Authenticated account context unavailable");
  }
}

package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.PortalAccessContextWebFilter;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingWebFilter;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedCsrfValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class PasswordValidationRequestWebFilterTest {
  private final PasswordValidationRequestWebFilter filter =
      new PasswordValidationRequestWebFilter(
          new PasswordValidationWebMapper(
              new AuthenticatedSessionCookieWriter(
                  new AuthWorkflowProperties.AuthenticatedCookie())),
          new ObjectMapper());

  @ParameterizedTest
  @CsvSource({
    "POST,/api/v1/password-validation",
    "POST,/api/v1/password-validation;x=1",
    "POST,/api/v1/%70assword-validation",
    "PUT,/api/v1/personal-information",
    "PUT,/api/v1/personal-information;x=1",
    "PUT,/api/v1/%70ersonal-information"
  })
  void routedVariantsHaveNoStoreAndSafeCsrfMapping(String method, String path) {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.method(HttpMethod.valueOf(method), URI.create(path)).build());
    var calls = new AtomicInteger();
    filter
        .filter(
            exchange,
            next -> {
              calls.incrementAndGet();
              return Mono.error(new AuthenticatedCsrfValidationException());
            })
        .block();
    assertThat(calls).hasValue(1);
    assertThat(exchange.getResponse().getHeaders().getCacheControl()).isEqualTo("no-store");
    assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(403);
    assertThat(exchange.getResponse().getBodyAsString().block())
        .contains("err.security.access.denied")
        .doesNotContain("exception", "message\":");
  }

  @ParameterizedTest
  @CsvSource({
    "GET,/api/v1/password-validation",
    "POST,/api/v1/personal-information",
    "PUT,/api/v1/password-validation",
    "POST,/api/v1/other",
    "POST,/api/v1/password-validation/",
    "POST,/api/v1/Password-validation",
    "PUT,/api/v1/personal-information/",
    "PUT,/api/v1/Personal-information"
  })
  void otherMethodsAndUnmatchedPathsPassThroughOnceWithoutChangingHeaders(
      String method, String path) {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.method(HttpMethod.valueOf(method), URI.create(path)).build());
    exchange.getResponse().getHeaders().setCacheControl("private");
    exchange.getResponse().getHeaders().set("X-Existing", "unchanged");
    var before = new org.springframework.http.HttpHeaders();
    before.putAll(exchange.getResponse().getHeaders());
    var calls = new AtomicInteger();
    filter
        .filter(
            exchange,
            next -> {
              assertThat(next).isSameAs(exchange);
              calls.incrementAndGet();
              return Mono.empty();
            })
        .block();
    assertThat(calls).hasValue(1);
    assertThat(exchange.getResponse().getHeaders()).isEqualTo(before);
  }

  @Test
  void explicitOrderIsAfterLoggingAndBeforeAuthenticatedContextResolution() {
    int logging = RequestLoggingWebFilter.class.getAnnotation(Order.class).value();
    int route = PasswordValidationRequestWebFilter.class.getAnnotation(Order.class).value();
    int context = PortalAccessContextWebFilter.class.getAnnotation(Order.class).value();
    assertThat(route).isGreaterThan(logging).isLessThan(context);
  }
}

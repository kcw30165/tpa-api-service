package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.logout.AuthenticatedLogoutWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.logout.LogoutController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieWriter;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.logout.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.ValidateAuthenticatedSessionUseCase;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthLogoutControllerTest {
  private static final String SID = "sid_abcdefghijklmnopqrstuvwx";
  private static final String CSRF = "authenticated_csrf";

  @Test
  void validPortalCookieAndCsrfInvokeLogoutAndClearCookies() {
    AtomicBoolean invoked = new AtomicBoolean();
    AuthenticatedLogoutUseCase useCase =
        command -> {
          invoked.set(true);
          assertThat(command.authenticatedSessionId().value()).isEqualTo(SID);
          assertThat(command.correlationId()).isEqualTo("req-1");
          return Mono.just(AuthenticatedLogoutResult.loggedOut());
        };
    var controller = controller(useCase, command -> Mono.just(session()));
    var exchange = exchange(SID);

    StepVerifier.create(controller.logout(CSRF, "req-1", exchange, exchange.getResponse()))
        .assertNext(
            entity -> {
              assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
              assertThat(entity.getBody().success()).isTrue();
            })
        .verifyComplete();

    assertThat(invoked).isTrue();
    assertThat(exchange.getResponse().getHeaders().getCacheControl())
        .isEqualTo(CacheControl.noStore().getHeaderValue());
    assertThat(exchange.getResponse().getCookies().getFirst("portal_sid").getMaxAge().isZero())
        .isTrue();
  }

  @Test
  void missingOrMalformedPortalCookieIsIdempotentWithoutUseCaseCall() {
    AtomicBoolean invoked = new AtomicBoolean();
    var controller =
        controller(
            command -> {
              invoked.set(true);
              return Mono.error(new AssertionError("logout must not run"));
            },
            command -> Mono.error(new AssertionError("validation must not run")));

    for (String sid : new String[] {null, "invalid"}) {
      var exchange = exchange(sid);
      StepVerifier.create(controller.logout(CSRF, "req-1", exchange, exchange.getResponse()))
          .assertNext(entity -> assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK))
          .verifyComplete();
    }
    assertThat(invoked).isFalse();
  }

  @Test
  void presentedSessionWithInvalidCsrfReturnsGenericUnauthorized() {
    var controller =
        controller(
            command -> Mono.error(new AssertionError("logout must not run")),
            command ->
                Mono.error(
                    new com.bct.ngtpa.apiservice.application.usecase.session.authenticated
                        .AuthenticatedPortalContextResolutionException()));
    var exchange = exchange(SID);

    StepVerifier.create(controller.logout("wrong", "req-1", exchange, exchange.getResponse()))
        .assertNext(
            entity -> {
              assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
              assertThat(entity.getBody().errors())
                  .singleElement()
                  .satisfies(
                      error ->
                          assertThat(error.code())
                              .isEqualTo("err.security.authentication.required"));
            })
        .verifyComplete();
  }

  @Test
  void revocationFailureReturnsServiceUnavailable() {
    var controller =
        controller(
            command -> Mono.just(AuthenticatedLogoutResult.failed()),
            command -> Mono.just(session()));
    var exchange = exchange(SID);
    StepVerifier.create(controller.logout(CSRF, "req-1", exchange, exchange.getResponse()))
        .assertNext(
            entity -> assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE))
        .verifyComplete();
  }

  private LogoutController controller(
      AuthenticatedLogoutUseCase useCase, ValidateAuthenticatedSessionUseCase validation) {
    return new LogoutController(
        useCase,
        new AuthenticatedLogoutWebMapper(),
        validation,
        new AuthenticatedSessionCookieWriter(new AuthWorkflowProperties.AuthenticatedCookie()));
  }

  private MockServerWebExchange exchange(String sid) {
    var request = MockServerHttpRequest.post("/api/v1/auth/logout");
    if (sid != null) request.cookie(new HttpCookie("portal_sid", sid));
    return MockServerWebExchange.from(request);
  }

  private AuthenticatedSession session() {
    return new AuthenticatedSession(
        new AuthenticatedSessionId(SID),
        new ActorContext("member", ActorUserRole.ROLE_USER_MBR),
        List.of(),
        AuthenticatedSessionStatus.ACTIVE,
        "csrf_hash",
        Instant.EPOCH,
        Instant.EPOCH,
        Instant.EPOCH.plusSeconds(300),
        Instant.EPOCH.plusSeconds(900));
  }
}

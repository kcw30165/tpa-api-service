package com.bct.ngtpa.apiservice.adapter.in.web.auth.csrf;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.session.config.AuthFlowSessionProperties;
import com.bct.ngtpa.apiservice.application.dto.auth.csrf.CsrfRefreshSessionType;
import com.bct.ngtpa.apiservice.application.dto.auth.csrf.RefreshCsrfTokenCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.csrf.RefreshCsrfTokenResult;
import com.bct.ngtpa.apiservice.application.port.in.RefreshCsrfTokenUseCase;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class CsrfTokenRefreshControllerBehaviorTest {
  @Test
  void authenticatedCookieWinsAndResponseIsNonCacheableWithoutPublishingCookies() {
    AtomicReference<RefreshCsrfTokenCommand> captured = new AtomicReference<>();
    var exchange =
        exchange(
            new HttpCookie("portal_sid", "sid_authenticated"),
            new HttpCookie("auth_flow_sid", "afs_pre_auth"));
    var controller =
        controller(
            command -> {
              captured.set(command);
              return Mono.just(
                  RefreshCsrfTokenResult.success(
                      CsrfRefreshSessionType.AUTHENTICATED, "new-token"));
            });

    var entity =
        controller
            .refresh("https://portal.example", null, "req-1", exchange, exchange.getResponse())
            .block();

    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(captured.get().authenticatedSessionId()).isEqualTo("sid_authenticated");
    assertThat(captured.get().authFlowSessionId()).isEqualTo("afs_pre_auth");
    assertThat(exchange.getResponse().getHeaders().getCacheControl()).contains("no-store");
    assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.PRAGMA))
        .isEqualTo("no-cache");
    assertThat(exchange.getResponse().getHeaders().getFirst("X-Request-Id")).isEqualTo("req-1");
    assertThat(exchange.getResponse().getCookies()).isEmpty();
  }

  @Test
  void blankAuthenticatedCookieNeverFallsBackToPreAuth() {
    AtomicInteger calls = new AtomicInteger();
    var exchange =
        exchange(new HttpCookie("portal_sid", " "), new HttpCookie("auth_flow_sid", "afs_valid"));
    var entity =
        controller(
                command -> {
                  calls.incrementAndGet();
                  return Mono.just(
                      RefreshCsrfTokenResult.success(CsrfRefreshSessionType.PRE_AUTH, "token"));
                })
            .refresh("https://portal.example", null, "req-2", exchange, exchange.getResponse())
            .block();

    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(calls).hasValue(0);
  }

  @Test
  void duplicateSelectedCookieIsRejectedBeforeUseCase() {
    AtomicInteger calls = new AtomicInteger();
    var request =
        MockServerHttpRequest.post("/api/v1/auth/csrf-token/refresh")
            .cookie(new HttpCookie("portal_sid", "sid-1"), new HttpCookie("portal_sid", "sid-2"))
            .build();
    var exchange = MockServerWebExchange.from(request);
    var entity =
        controller(
                command -> {
                  calls.incrementAndGet();
                  return Mono.empty();
                })
            .refresh("https://portal.example", null, "req-3", exchange, exchange.getResponse())
            .block();

    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(calls).hasValue(0);
  }

  @Test
  void preAuthCookieIsUsedOnlyWhenAuthenticatedCookieIsAbsent() {
    AtomicReference<RefreshCsrfTokenCommand> captured = new AtomicReference<>();
    var exchange = exchange(new HttpCookie("auth_flow_sid", "afs_only"));
    var entity =
        controller(
                command -> {
                  captured.set(command);
                  return Mono.just(
                      RefreshCsrfTokenResult.success(CsrfRefreshSessionType.PRE_AUTH, "new-token"));
                })
            .refresh(null, "https://portal.example/page", "req-4", exchange, exchange.getResponse())
            .block();

    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(captured.get().authenticatedSessionId()).isNull();
    assertThat(captured.get().authFlowSessionId()).isEqualTo("afs_only");
  }

  private static CsrfTokenRefreshController controller(RefreshCsrfTokenUseCase useCase) {
    return new CsrfTokenRefreshController(
        useCase,
        new CsrfTokenRefreshHttpResponses(),
        new AuthWorkflowProperties(),
        new AuthFlowSessionProperties());
  }

  private static MockServerWebExchange exchange(HttpCookie... cookies) {
    return MockServerWebExchange.from(
        MockServerHttpRequest.post("/api/v1/auth/csrf-token/refresh").cookie(cookies).build());
  }
}

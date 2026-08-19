package com.bct.ngtpa.apiservice.adapter.in.web.session.cookie;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieInstruction;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieOperation;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookiePolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieSameSite;
import com.bct.ngtpa.apiservice.application.service.session.SessionCookieInstructionFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

class HttpSessionCookieAdapterTest {

  private final HttpSessionCookieAdapter adapter = new HttpSessionCookieAdapter();
  private final SessionCookieInstructionFactory factory = new SessionCookieInstructionFactory();

  @Test
  void httpAdapterWritesAuthFlowSessionCookie() {
    var response = new MockServerHttpResponse();
    var instruction = factory.setCookie(authFlowCookiePolicy(), "afs_abc");

    adapter.apply(response, instruction);

    ResponseCookie cookie = response.getCookies().getFirst("auth_flow_sid");
    assertThat(cookie).isNotNull();
    assertThat(cookie.getName()).isEqualTo("auth_flow_sid");
    assertThat(cookie.getValue()).isEqualTo("afs_abc");
    assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
    assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(1800);
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.isSecure()).isTrue();
    assertThat(cookie.getSameSite()).isEqualTo("None");
  }

  @Test
  void httpAdapterWritesXsrfCookieWithoutHttpOnly() {
    var response = new MockServerHttpResponse();
    var instruction = factory.setCookie(xsrfCookiePolicy(), "csrf_abc");

    adapter.apply(response, instruction);

    ResponseCookie cookie = response.getCookies().getFirst("XSRF-TOKEN");
    assertThat(cookie).isNotNull();
    assertThat(cookie.getValue()).isEqualTo("csrf_abc");
    assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
    assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(1800);
    assertThat(cookie.isHttpOnly()).isFalse();
    assertThat(cookie.isSecure()).isTrue();
    assertThat(cookie.getSameSite()).isEqualTo("None");
  }

  @Test
  void httpAdapterClearsAuthFlowSessionCookie() {
    var response = new MockServerHttpResponse();
    var instruction = factory.clearCookie(authFlowCookiePolicy());

    adapter.apply(response, instruction);

    ResponseCookie cookie = response.getCookies().getFirst("auth_flow_sid");
    assertThat(cookie).isNotNull();
    assertThat(cookie.getValue()).isEmpty();
    assertThat(cookie.getMaxAge().getSeconds()).isZero();
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.isSecure()).isTrue();
    assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
    assertThat(cookie.getSameSite()).isEqualTo("None");
  }

  @Test
  void httpAdapterClearsXsrfCookieWithoutHttpOnly() {
    var response = new MockServerHttpResponse();
    var instruction = factory.clearCookie(xsrfCookiePolicy());

    adapter.apply(response, instruction);

    ResponseCookie cookie = response.getCookies().getFirst("XSRF-TOKEN");
    assertThat(cookie).isNotNull();
    assertThat(cookie.getValue()).isEmpty();
    assertThat(cookie.getMaxAge().getSeconds()).isZero();
    assertThat(cookie.isHttpOnly()).isFalse();
    assertThat(cookie.isSecure()).isTrue();
    assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
    assertThat(cookie.getSameSite()).isEqualTo("None");
  }

  @Test
  void applyAllWritesMultipleCookies() {
    var response = new MockServerHttpResponse();
    List<SessionCookieInstruction> instructions =
        List.of(
            factory.setCookie(authFlowCookiePolicy(), "afs_abc"),
            factory.setCookie(xsrfCookiePolicy(), "csrf_abc"));

    adapter.applyAll(response, instructions);

    assertThat(response.getCookies().getFirst("auth_flow_sid")).isNotNull();
  }

  @Test
  void cookieAdapterDoesNotLogCookieValues() {
    assertThat(HttpSessionCookieAdapter.class.getDeclaredFields())
        .noneMatch(
            field ->
                field.getName().equalsIgnoreCase("log")
                    || field.getName().equalsIgnoreCase("logger")
                    || field.getType().getName().contains("Logger"));
    var instruction =
        new SessionCookieInstruction(
            SessionCookieOperation.SET, authFlowCookiePolicy(), "afs_secret_value");
    assertThat(instruction.toString()).doesNotContain("afs_secret_value");
  }

  private static SessionCookiePolicy authFlowCookiePolicy() {
    return new SessionCookiePolicy(
        "auth_flow_sid", "/api/v1/auth", 1800, true, true, SessionCookieSameSite.NONE);
  }

  private static SessionCookiePolicy xsrfCookiePolicy() {
    return new SessionCookiePolicy(
        "XSRF-TOKEN", "/api/v1/auth", 1800, false, true, SessionCookieSameSite.NONE);
  }
}

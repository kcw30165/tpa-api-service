package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.in.web.session.cookie.HttpSessionCookieAdapter;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieInstruction;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieOperation;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookiePolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieSameSite;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

class UsernameRecoveryCookieWriterTest {
  private static final String UR_PATH = "/api/v1/auth/username-recovery";
  private final UsernameRecoveryCookieWriter writer =
      new UsernameRecoveryCookieWriter(
          new SessionCookiePolicy(
              "auth_flow_sid", UR_PATH, 600, true, true, SessionCookieSameSite.STRICT));
  private final HttpSessionCookieAdapter adapter = new HttpSessionCookieAdapter();

  @Test
  void authFlowCookieWriteUsesExactAttributes() {
    SessionCookieInstruction instruction =
        writer.authFlowCookie(new AuthFlowSessionId("afs_opaque-1"));
    assertThat(instruction.operation()).isEqualTo(SessionCookieOperation.SET);
    assertThat(instruction.value()).isEqualTo("afs_opaque-1");
    assertThat(instruction.policy().name()).isEqualTo("auth_flow_sid");
    assertThat(instruction.policy().path()).isEqualTo(UR_PATH);
    assertThat(instruction.policy().maxAgeSeconds()).isEqualTo(600);
    assertThat(instruction.policy().httpOnly()).isTrue();
    assertThat(instruction.policy().secure()).isTrue();
    assertThat(instruction.policy().sameSite()).isEqualTo(SessionCookieSameSite.STRICT);
    var response = new MockServerHttpResponse();
    adapter.apply(response, instruction);
    var cookie = response.getCookies().getFirst("auth_flow_sid");
    assertThat(cookie).isNotNull();
    assertThat(cookie.getValue()).isEqualTo("afs_opaque-1");
    assertThat(cookie.getPath()).isEqualTo(UR_PATH);
    assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofSeconds(600));
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.isSecure()).isTrue();
    assertThat(cookie.getSameSite()).isEqualTo("Strict");
    assertThat(response.getCookies().getFirst("XSRF-TOKEN")).isNull();
  }

  @Test
  void clearWritesOnlyAuthFlowCookieAtExactPathWithZeroMaxAge() {
    var response = new MockServerHttpResponse();
    adapter.apply(response, writer.clearAuthFlowCookie());
    var authFlow = response.getCookies().getFirst("auth_flow_sid");
    assertThat(authFlow).isNotNull();
    assertThat(authFlow.getValue()).isEmpty();
    assertThat(authFlow.getMaxAge()).isEqualTo(Duration.ZERO);
    assertThat(authFlow.getPath()).isEqualTo(UR_PATH);
    assertThat(response.getCookies().getFirst("XSRF-TOKEN")).isNull();
  }

  @Test
  void writerExposesNoXsrfCookieApi() {
    assertThat(
            Arrays.stream(UsernameRecoveryCookieWriter.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName))
        .doesNotContain("xsrfCookie", "clearXsrfCookie");
  }

  @Test
  void writeRejectsMissingSessionId() {
    assertThatThrownBy(() -> writer.authFlowCookie(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void instructionsRedactCookieValuesInToString() {
    assertThat(writer.authFlowCookie(new AuthFlowSessionId("afs_secret-1")).toString())
        .doesNotContain("afs_secret-1")
        .contains("value=<redacted>");
    assertThat(writer.clearAuthFlowCookie().toString()).contains("value=<empty>");
  }
}

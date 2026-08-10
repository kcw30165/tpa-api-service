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
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

/**
 * Repair 024A cookie-writer contract: the Spring-free writer produces framework-free
 * {@link SessionCookieInstruction} values with the exact Username Recovery policy (name,
 * path {@code /api/v1/auth/username-recovery}, Max-Age = UR session TTL default 600,
 * HttpOnly per cookie, Secure per approved policy, SameSite Strict, no Domain). The
 * committed {@link HttpSessionCookieAdapter} applies them to the response; CLEAR becomes an
 * empty-value cookie with Max-Age 0 at the exact UR path.
 */
class UsernameRecoveryCookieWriterTest {

    private static final String UR_PATH = "/api/v1/auth/username-recovery";

    private final UsernameRecoveryCookieWriter writer = new UsernameRecoveryCookieWriter(
            new SessionCookiePolicy(
                    "auth_flow_sid", UR_PATH, 600, true, true, SessionCookieSameSite.STRICT),
            new SessionCookiePolicy(
                    "XSRF-TOKEN", UR_PATH, 600, false, true, SessionCookieSameSite.STRICT));
    private final HttpSessionCookieAdapter adapter = new HttpSessionCookieAdapter();

    @Test
    void authFlowCookieWriteUsesExactAttributes() {
        SessionCookieInstruction instruction = writer.authFlowCookie(new AuthFlowSessionId("afs_opaque-1"));

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
    }

    @Test
    void xsrfCookieWriteUsesExactAttributesAndHttpOnlyFalse() {
        SessionCookieInstruction instruction = writer.xsrfCookie("raw-csrf-token");

        assertThat(instruction.operation()).isEqualTo(SessionCookieOperation.SET);
        assertThat(instruction.value()).isEqualTo("raw-csrf-token");
        assertThat(instruction.policy().name()).isEqualTo("XSRF-TOKEN");
        assertThat(instruction.policy().path()).isEqualTo(UR_PATH);
        assertThat(instruction.policy().maxAgeSeconds()).isEqualTo(600);
        assertThat(instruction.policy().httpOnly()).isFalse();
        assertThat(instruction.policy().secure()).isTrue();
        assertThat(instruction.policy().sameSite()).isEqualTo(SessionCookieSameSite.STRICT);

        var response = new MockServerHttpResponse();
        adapter.apply(response, instruction);
        var cookie = response.getCookies().getFirst("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isEqualTo("raw-csrf-token");
        assertThat(cookie.getPath()).isEqualTo(UR_PATH);
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofSeconds(600));
        assertThat(cookie.isHttpOnly()).isFalse();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
    }



    @Test
    void bothCookiesClearAtExactUsernameRecoveryPathWithZeroMaxAge() {
        var response = new MockServerHttpResponse();
        adapter.apply(response, writer.clearAuthFlowCookie());
        adapter.apply(response, writer.clearXsrfCookie());

        var authFlow = response.getCookies().getFirst("auth_flow_sid");
        assertThat(authFlow).isNotNull();
        assertThat(authFlow.getValue()).isEmpty();
        assertThat(authFlow.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(authFlow.getPath()).isEqualTo(UR_PATH);

        var xsrf = response.getCookies().getFirst("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
        assertThat(xsrf.getValue()).isEmpty();
        assertThat(xsrf.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(xsrf.getPath()).isEqualTo(UR_PATH);
    }

    @Test
    void writeRejectsMissingSessionIdAndBlankCsrf() {
        assertThatThrownBy(() -> writer.authFlowCookie(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> writer.xsrfCookie(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> writer.xsrfCookie("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void instructionsRedactCookieValuesInToString() {
        assertThat(writer.authFlowCookie(new AuthFlowSessionId("afs_secret-1")).toString())
                .doesNotContain("afs_secret-1")
                .contains("value=<redacted>");
        assertThat(writer.xsrfCookie("csrf-secret").toString())
                .doesNotContain("csrf-secret")
                .contains("value=<redacted>");
        assertThat(writer.clearAuthFlowCookie().toString()).contains("value=<empty>");
    }
}

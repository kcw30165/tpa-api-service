package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

class AuthFlowCookieWriterClearTest {
    @Test
    void clearsCookieWithSameSecurityPolicyAndZeroMaxAge() {
        var writer = new AuthFlowCookieWriter(new AuthFlowCookieProperties(
                "auth_flow_sid", "/api/v1/auth", Duration.ofMinutes(30), true, true, "None"));
        var response = new MockServerHttpResponse();
        writer.clearAuthFlowCookie(response);
        var cookie = response.getCookies().getFirst("auth_flow_sid");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("None");
    }
}

package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RegistrationStartCookieMappingTest {
    @Test
    void registrationStartSetsAuthFlowCookieOnSuccess() {
        var writer = new AuthFlowCookieWriter(new AuthFlowCookieProperties("auth_flow_sid", "/api/v1/auth", Duration.ofMinutes(15), true, true, "None"));
        var cookie = writer.createAuthFlowCookie("afs_abc");

        assertThat(cookie.getName()).isEqualTo("auth_flow_sid");
        assertThat(cookie.getValue()).isEqualTo("afs_abc");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("None");
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void registrationStartDoesNotSetXsrfCookieByDefault() {
        assertThat(AuthFlowCookieWriter.defaultWriter().csrfCookieEnabled()).isFalse();
    }
}

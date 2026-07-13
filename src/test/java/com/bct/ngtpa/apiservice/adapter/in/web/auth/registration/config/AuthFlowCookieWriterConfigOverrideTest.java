package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.session.config.AuthFlowSessionProperties;
import org.junit.jupiter.api.Test;

class AuthFlowCookieWriterConfigOverrideTest {

    @Test
    void cookieWriterUsesAuthFlowSessionCookiePolicyOverrides() {
        var properties = new AuthFlowSessionProperties();
        properties.getCookie().setName("custom_auth_sid");
        properties.getCookie().setPath("/custom/auth");
        properties.getCookie().setMaxAgeSeconds(77);
        properties.getCookie().setHttpOnly(false);
        properties.getCookie().setSecure(false);
        properties.getCookie().setSameSite("Lax");

        var cookie = AuthFlowCookieWriter.from(properties).createAuthFlowCookie("afs_custom");

        assertThat(cookie.getName()).isEqualTo("custom_auth_sid");
        assertThat(cookie.getValue()).isEqualTo("afs_custom");
        assertThat(cookie.getPath()).isEqualTo("/custom/auth");
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(77);
        assertThat(cookie.isHttpOnly()).isFalse();
        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
    }
}

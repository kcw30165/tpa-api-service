package com.bct.ngtpa.apiservice.adapter.in.web.auth.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

class AuthenticatedSessionCookieFoundationTest {
    @Test
    void portalSessionCookieIsHttpOnlyAndCsrfCookieIsReadable() {
        var session = new AuthWorkflowProperties.AuthenticatedCookie();
        var csrf = new AuthWorkflowProperties.AuthenticatedCsrf();
        var response = new MockServerHttpResponse();

        new AuthenticatedSessionCookieWriter(session).write(response, "sid_abcdefghijklmnopqrstuvwx");
        new AuthenticatedCsrfCookieWriter(csrf).write(response, "csrf_opaque_value");

        var portal = response.getCookies().getFirst("portal_sid");
        var xsrf = response.getCookies().getFirst("XSRF-TOKEN");
        assertThat(portal).isNotNull();
        assertThat(portal.isHttpOnly()).isTrue();
        assertThat(portal.getPath()).isEqualTo("/api");
        assertThat(portal.getValue()).startsWith("sid_");
        assertThat(xsrf).isNotNull();
        assertThat(xsrf.isHttpOnly()).isFalse();
        assertThat(xsrf.getPath()).isEqualTo("/api");
    }

    @Test
    void clearUsesSameNamesAndPathsWithZeroMaxAge() {
        var response = new MockServerHttpResponse();
        new AuthenticatedSessionCookieWriter(new AuthWorkflowProperties.AuthenticatedCookie()).clear(response);
        new AuthenticatedCsrfCookieWriter(new AuthWorkflowProperties.AuthenticatedCsrf()).clear(response);

        assertThat(response.getCookies().getFirst("portal_sid").getMaxAge().isZero()).isTrue();
        assertThat(response.getCookies().getFirst("XSRF-TOKEN").getMaxAge().isZero()).isTrue();
    }
}

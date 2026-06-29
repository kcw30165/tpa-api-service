package com.bct.ngtpa.apiservice.application.service.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieOperation;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookiePolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieSameSite;
import com.bct.ngtpa.apiservice.application.dto.session.XsrfTokenTransportPolicy;
import org.junit.jupiter.api.Test;

class XsrfCookieInstructionServiceTest {

    private final XsrfCookieInstructionService service = new XsrfCookieInstructionService();

    @Test
    void createsSetXsrfCookieInstruction() {
        var instruction = service.setXsrfCookie(transportPolicy(), "csrf_abc");
        assertThat(instruction.operation()).isEqualTo(SessionCookieOperation.SET);
        assertThat(instruction.policy().name()).isEqualTo("XSRF-TOKEN");
        assertThat(instruction.policy().httpOnly()).isFalse();
        assertThat(instruction.value()).isEqualTo("csrf_abc");
    }

    @Test
    void createsClearXsrfCookieInstruction() {
        var instruction = service.clearXsrfCookie(transportPolicy());
        assertThat(instruction.operation()).isEqualTo(SessionCookieOperation.CLEAR);
        assertThat(instruction.policy().name()).isEqualTo("XSRF-TOKEN");
        assertThat(instruction.policy().httpOnly()).isFalse();
        assertThat(instruction.value()).isNull();
    }

    @Test
    void setXsrfCookieRejectsBlankToken() {
        assertThatThrownBy(() -> service.setXsrfCookie(transportPolicy(), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cookie value is required for SET operation")
                .hasMessageNotContaining("csrf_");
    }

    @Test
    void xsrfCookieInstructionToStringDoesNotExposeToken() {
        var instruction = service.setXsrfCookie(transportPolicy(), "csrf_secret_value");
        assertThat(instruction.toString()).doesNotContain("csrf_secret_value").contains("value=<redacted>");
    }

    @Test
    void rejectsNullTransportPolicy() {
        assertThatThrownBy(() -> service.clearXsrfCookie(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("policy must not be null");
    }

    private static XsrfTokenTransportPolicy transportPolicy() {
        return new XsrfTokenTransportPolicy(
                new SessionCookiePolicy("XSRF-TOKEN", "/api/v1/auth", 1800, false, true, SessionCookieSameSite.NONE),
                "X-XSRF-TOKEN");
    }
}

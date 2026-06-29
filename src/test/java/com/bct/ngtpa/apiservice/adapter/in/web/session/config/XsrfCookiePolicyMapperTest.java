package com.bct.ngtpa.apiservice.adapter.in.web.session.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieSameSite;
import com.bct.ngtpa.apiservice.infrastructure.security.CorsProperties;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class XsrfCookiePolicyMapperTest {

    private final XsrfCookiePolicyMapper mapper = new XsrfCookiePolicyMapper();

    @Test
    void mapsAuthFlowSessionCsrfConfigToXsrfTransportPolicy() {
        var mapped = mapper.map(csrfProperties(true, "None"));
        assertThat(mapped.cookiePolicy().name()).isEqualTo("XSRF-TOKEN");
        assertThat(mapped.cookiePolicy().path()).isEqualTo("/api/v1/auth");
        assertThat(mapped.cookiePolicy().maxAgeSeconds()).isEqualTo(1800);
        assertThat(mapped.cookiePolicy().httpOnly()).isFalse();
        assertThat(mapped.cookiePolicy().secure()).isTrue();
        assertThat(mapped.cookiePolicy().sameSite()).isEqualTo(SessionCookieSameSite.NONE);
        assertThat(mapped.headerName()).isEqualTo("X-XSRF-TOKEN");
    }

    @Test
    void xsrfTokenCookieIsNotHttpOnly() {
        assertThat(mapper.map(csrfProperties(true, "None")).cookiePolicy().httpOnly()).isFalse();
    }

    @Test
    void xsrfTokenCookieRequiresSecureWhenSameSiteNone() {
        assertThatThrownBy(() -> mapper.map(csrfProperties(false, "None")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SameSite=None cookies must be Secure");
    }

    @Test
    void xsrfCookiePolicyMapperDoesNotCreateCorsConfig() {
        assertThat(Arrays.stream(XsrfCookiePolicyMapper.class.getDeclaredFields()).map(Field::getType))
                .doesNotContain(CorsProperties.class);
        assertThat(Arrays.stream(XsrfCookiePolicyMapper.class.getDeclaredConstructors()))
                .allMatch(constructor -> constructor.getParameterCount() == 0);
    }

    @Test
    void mapsSameSiteCaseInsensitively() {
        assertThat(mapper.map(csrfProperties(true, "none")).cookiePolicy().sameSite()).isEqualTo(SessionCookieSameSite.NONE);
        assertThat(mapper.map(csrfProperties(true, "Lax")).cookiePolicy().sameSite()).isEqualTo(SessionCookieSameSite.LAX);
        assertThat(mapper.map(csrfProperties(true, "STRICT")).cookiePolicy().sameSite()).isEqualTo(SessionCookieSameSite.STRICT);
    }

    private static AuthFlowSessionProperties.Csrf csrfProperties(boolean secure, String sameSite) {
        var csrf = new AuthFlowSessionProperties.Csrf();
        csrf.setCookieName("XSRF-TOKEN");
        csrf.setHeaderName("X-XSRF-TOKEN");
        csrf.setCookiePath("/api/v1/auth");
        csrf.setMaxAgeSeconds(1800);
        csrf.setSecure(secure);
        csrf.setSameSite(sameSite);
        return csrf;
    }
}

package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config;

import com.bct.ngtpa.apiservice.adapter.in.web.session.config.AuthFlowSessionProperties;
import java.time.Duration;
import java.util.Objects;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;

public final class XsrfCookieWriter {
    private final String name;
    private final String path;
    private final boolean secure;
    private final String sameSite;

    public XsrfCookieWriter(AuthFlowSessionProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        var csrf = properties.getCsrf();
        this.name = csrf.getCookieName();
        this.path = csrf.getCookiePath();
        this.secure = csrf.isSecure();
        this.sameSite = csrf.getSameSite();
    }

    public void clearXsrfCookie(ServerHttpResponse response) {
        if (response == null) {
            return;
        }
        response.addCookie(ResponseCookie.from(name, "")
                .path(path)
                .maxAge(Duration.ZERO)
                .httpOnly(false)
                .secure(secure)
                .sameSite(sameSite)
                .build());
    }
}

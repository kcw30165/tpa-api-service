package com.bct.ngtpa.apiservice.adapter.in.web.auth.session;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import java.time.Duration;
import java.util.Objects;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;

/** Publishes the readable double-submit CSRF cookie; the matching hash stays server-side. */
public final class AuthenticatedCsrfCookieWriter {
    private final AuthWorkflowProperties.AuthenticatedCsrf properties;

    public AuthenticatedCsrfCookieWriter(AuthWorkflowProperties.AuthenticatedCsrf properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        validate(properties);
    }

    public String cookieName() {
        return properties.getCookieName().trim();
    }

    public String headerName() {
        return properties.getHeaderName().trim();
    }

    public void write(ServerHttpResponse response, String rawCsrfToken) {
        if (response == null || rawCsrfToken == null || rawCsrfToken.isBlank()) {
            throw new IllegalArgumentException("response and rawCsrfToken are required");
        }
        response.addCookie(cookie(rawCsrfToken.trim(), Duration.ofSeconds(properties.getMaxAgeSeconds())));
    }

    public void clear(ServerHttpResponse response) {
        if (response != null) {
            response.addCookie(cookie("", Duration.ZERO));
        }
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(cookieName(), value)
                .path(properties.getCookiePath().trim())
                .maxAge(maxAge)
                .httpOnly(false)
                .secure(properties.isSecure())
                .sameSite(properties.getSameSite().trim())
                .build();
    }

    private static void validate(AuthWorkflowProperties.AuthenticatedCsrf value) {
        requireText(value.getCookieName(), "auth-workflows.login.session.csrf.cookie-name");
        requireText(value.getHeaderName(), "auth-workflows.login.session.csrf.header-name");
        requireText(value.getCookiePath(), "auth-workflows.login.session.csrf.cookie-path");
        requireText(value.getSameSite(), "auth-workflows.login.session.csrf.same-site");
        if (value.getMaxAgeSeconds() <= 0) {
            throw new IllegalStateException("Authenticated CSRF max-age-seconds must be positive");
        }
    }

    private static void requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " must not be blank");
        }
    }
}

package com.bct.ngtpa.apiservice.adapter.in.web.auth.session;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import java.time.Duration;
import java.util.Objects;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;

/** Writes and clears the browser-managed authenticated portal session cookie. */
public final class AuthenticatedSessionCookieWriter {
    private final AuthWorkflowProperties.AuthenticatedCookie properties;

    public AuthenticatedSessionCookieWriter(AuthWorkflowProperties.AuthenticatedCookie properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        validate(properties);
    }

    public String cookieName() {
        return properties.getName().trim();
    }

    public void write(ServerHttpResponse response, String authenticatedSessionId) {
        if (response == null || authenticatedSessionId == null || authenticatedSessionId.isBlank()) {
            throw new IllegalArgumentException("response and authenticatedSessionId are required");
        }
        response.addCookie(cookie(authenticatedSessionId.trim(), Duration.ofSeconds(properties.getMaxAgeSeconds())));
    }

    public void clear(ServerHttpResponse response) {
        if (response != null) {
            response.addCookie(cookie("", Duration.ZERO));
        }
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(cookieName(), value)
                .path(properties.getPath().trim())
                .maxAge(maxAge)
                .httpOnly(true)
                .secure(properties.isSecure())
                .sameSite(properties.getSameSite().trim())
                .build();
    }

    private static void validate(AuthWorkflowProperties.AuthenticatedCookie value) {
        requireText(value.getName(), "auth-workflows.login.session.cookie.name");
        requireText(value.getPath(), "auth-workflows.login.session.cookie.path");
        requireText(value.getSameSite(), "auth-workflows.login.session.cookie.same-site");
        if (!value.isHttpOnly()) {
            throw new IllegalStateException("Authenticated session cookie must be HttpOnly");
        }
        if (value.getMaxAgeSeconds() <= 0) {
            throw new IllegalStateException("Authenticated session cookie max-age-seconds must be positive");
        }
    }

    private static void requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " must not be blank");
        }
    }
}

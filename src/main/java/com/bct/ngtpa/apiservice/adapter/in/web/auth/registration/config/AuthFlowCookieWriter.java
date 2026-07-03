package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config;

import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;

public class AuthFlowCookieWriter {
    private final AuthFlowCookieProperties properties;
    private final boolean csrfCookieEnabled;
    public AuthFlowCookieWriter(AuthFlowCookieProperties properties) { this(properties, false); }
    public AuthFlowCookieWriter(AuthFlowCookieProperties properties, boolean csrfCookieEnabled) { this.properties = properties; this.csrfCookieEnabled = csrfCookieEnabled; }
    public static AuthFlowCookieWriter defaultWriter() { return new AuthFlowCookieWriter(new AuthFlowCookieProperties("auth_flow_sid", "/api/v1/auth", Duration.ofMinutes(15), true, true, "None"), false); }
    public boolean csrfCookieEnabled() { return csrfCookieEnabled; }
    public ResponseCookie createAuthFlowCookie(String authFlowSessionId) { return ResponseCookie.from(properties.name(), authFlowSessionId).path(properties.path()).maxAge(properties.maxAge()).httpOnly(properties.httpOnly()).secure(properties.secure()).sameSite(properties.sameSite()).build(); }
    public void writeAuthFlowCookie(ServerHttpResponse response, String authFlowSessionId) { if (response != null && authFlowSessionId != null && !authFlowSessionId.isBlank()) response.addCookie(createAuthFlowCookie(authFlowSessionId)); }
}

package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieInstruction;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieOperation;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookiePolicy;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.util.Objects;

/**
 * Spring-free Username Recovery cookie writer (Repair 024A).
 *
 * <p>Produces framework-free {@link SessionCookieInstruction} values with the exact Username
 * Recovery policy: name {@code auth_flow_sid}/{@code XSRF-TOKEN}, path
 * {@code /api/v1/auth/username-recovery}, Max-Age equal to the UR session TTL, HttpOnly per
 * cookie, Secure per the approved policy, SameSite Strict, and no Domain. The committed
 * {@code HttpSessionCookieAdapter} applies the instructions to the reactive response from Task
 * 024; no {@code Set-Cookie} string is hand-built here. Values are never retained, and
 * {@link SessionCookieInstruction#toString()} never discloses them (SET values redact as
 * {@code value=&lt;redacted&gt;}, CLEAR as {@code value=&lt;empty&gt;}).
 */
public final class UsernameRecoveryCookieWriter {

    private final SessionCookiePolicy authFlowPolicy;
    private final SessionCookiePolicy xsrfPolicy;

    public UsernameRecoveryCookieWriter(
            SessionCookiePolicy authFlowPolicy, SessionCookiePolicy xsrfPolicy) {
        this.authFlowPolicy = Objects.requireNonNull(authFlowPolicy, "authFlowPolicy must not be null");
        this.xsrfPolicy = Objects.requireNonNull(xsrfPolicy, "xsrfPolicy must not be null");
    }

    public SessionCookieInstruction authFlowCookie(AuthFlowSessionId sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        return new SessionCookieInstruction(
                SessionCookieOperation.SET, authFlowPolicy, sessionId.value());
    }

    public SessionCookieInstruction xsrfCookie(String rawCsrfToken) {
        return new SessionCookieInstruction(
                SessionCookieOperation.SET, xsrfPolicy, rawCsrfToken);
    }

    public SessionCookieInstruction clearAuthFlowCookie() {
        return new SessionCookieInstruction(SessionCookieOperation.CLEAR, authFlowPolicy, null);
    }

    public SessionCookieInstruction clearXsrfCookie() {
        return new SessionCookieInstruction(SessionCookieOperation.CLEAR, xsrfPolicy, null);
    }
}
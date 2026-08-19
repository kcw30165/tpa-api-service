package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieInstruction;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieOperation;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookiePolicy;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.util.Objects;

/** Writes only the opaque Username Recovery auth-flow session cookie. */
public final class UsernameRecoveryCookieWriter {
  private final SessionCookiePolicy authFlowPolicy;

  public UsernameRecoveryCookieWriter(SessionCookiePolicy authFlowPolicy) {
    this.authFlowPolicy = Objects.requireNonNull(authFlowPolicy, "authFlowPolicy must not be null");
  }

  public SessionCookieInstruction authFlowCookie(AuthFlowSessionId sessionId) {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    return new SessionCookieInstruction(
        SessionCookieOperation.SET, authFlowPolicy, sessionId.value());
  }

  public SessionCookieInstruction clearAuthFlowCookie() {
    return new SessionCookieInstruction(SessionCookieOperation.CLEAR, authFlowPolicy, null);
  }
}

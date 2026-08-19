package com.bct.ngtpa.apiservice.application.service.session;

import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieInstruction;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieOperation;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookiePolicy;
import java.util.Objects;

public class SessionCookieInstructionFactory {

  public SessionCookieInstruction setCookie(SessionCookiePolicy policy, String value) {
    Objects.requireNonNull(policy, "policy must not be null");
    return new SessionCookieInstruction(SessionCookieOperation.SET, policy, value);
  }

  public SessionCookieInstruction clearCookie(SessionCookiePolicy policy) {
    Objects.requireNonNull(policy, "policy must not be null");
    return new SessionCookieInstruction(SessionCookieOperation.CLEAR, policy, null);
  }
}

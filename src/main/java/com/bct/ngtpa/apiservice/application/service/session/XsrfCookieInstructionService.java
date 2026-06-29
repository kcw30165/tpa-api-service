package com.bct.ngtpa.apiservice.application.service.session;

import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieInstruction;
import com.bct.ngtpa.apiservice.application.dto.session.XsrfTokenTransportPolicy;
import java.util.Objects;

public class XsrfCookieInstructionService {

    private final SessionCookieInstructionFactory instructionFactory;

    public XsrfCookieInstructionService() {
        this(new SessionCookieInstructionFactory());
    }

    XsrfCookieInstructionService(SessionCookieInstructionFactory instructionFactory) {
        this.instructionFactory = Objects.requireNonNull(instructionFactory, "instructionFactory must not be null");
    }

    public SessionCookieInstruction setXsrfCookie(XsrfTokenTransportPolicy policy, String rawToken) {
        Objects.requireNonNull(policy, "policy must not be null");
        return instructionFactory.setCookie(policy.cookiePolicy(), rawToken);
    }

    public SessionCookieInstruction clearXsrfCookie(XsrfTokenTransportPolicy policy) {
        Objects.requireNonNull(policy, "policy must not be null");
        return instructionFactory.clearCookie(policy.cookiePolicy());
    }
}

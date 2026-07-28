package com.bct.ngtpa.apiservice.application.usecase.session.authenticated;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;

@FunctionalInterface
public interface AuthenticatedSessionIdGenerator {
    AuthenticatedSessionId generate();
}

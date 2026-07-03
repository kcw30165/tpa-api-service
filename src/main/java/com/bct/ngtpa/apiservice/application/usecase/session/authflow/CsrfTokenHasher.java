package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

public interface CsrfTokenHasher {
    String hash(String rawToken);
    boolean matches(String rawToken, String tokenHash);
}

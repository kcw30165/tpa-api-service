package com.bct.ngtpa.apiservice.application.service.security.csrf;

public interface CsrfTokenHasher {

    String hash(String rawToken);

    boolean matches(String rawToken, String storedHash);
}

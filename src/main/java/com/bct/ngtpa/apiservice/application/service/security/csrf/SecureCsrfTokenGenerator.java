package com.bct.ngtpa.apiservice.application.service.security.csrf;

import java.security.SecureRandom;
import java.util.Base64;

public class SecureCsrfTokenGenerator {

    private static final int TOKEN_BYTES = 32;
    private final SecureRandom secureRandom;

    public SecureCsrfTokenGenerator() {
        this(new SecureRandom());
    }

    SecureCsrfTokenGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return "csrf_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

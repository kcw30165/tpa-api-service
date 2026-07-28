package com.bct.ngtpa.apiservice.adapter.out.security.login;

import com.bct.ngtpa.apiservice.application.port.out.LoginDestinationIdGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

public final class SecureRandomLoginDestinationIdGenerator implements LoginDestinationIdGenerator {
    private final SecureRandom random;
    private final int entropyBytes;

    public SecureRandomLoginDestinationIdGenerator(SecureRandom random, int entropyBytes) {
        this.random = Objects.requireNonNull(random);
        if (entropyBytes < 16)
            throw new IllegalArgumentException("entropyBytes must be at least 16");
        this.entropyBytes = entropyBytes;
    }

    public String generate() {
        byte[] value = new byte[entropyBytes];
        random.nextBytes(value);
        return "dst_" + Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}

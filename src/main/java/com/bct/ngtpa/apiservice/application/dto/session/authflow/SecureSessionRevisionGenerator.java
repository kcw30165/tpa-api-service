package com.bct.ngtpa.apiservice.application.dto.session.authflow;

import java.security.SecureRandom;
import java.util.HexFormat;

public final class SecureSessionRevisionGenerator implements SessionRevisionGenerator {
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public SessionRevision generate() {
        byte[] entropy = new byte[16];
        secureRandom.nextBytes(entropy);
        return new SessionRevision(HexFormat.of().formatHex(entropy));
    }
}

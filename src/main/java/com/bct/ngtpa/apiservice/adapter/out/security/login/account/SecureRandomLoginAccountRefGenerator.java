package com.bct.ngtpa.apiservice.adapter.out.security.login.account;

import com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginAccountRef;
import com.bct.ngtpa.apiservice.application.port.out.LoginAccountRefGeneratorPort;
import java.security.SecureRandom;
import java.util.*;

public final class SecureRandomLoginAccountRefGenerator implements LoginAccountRefGeneratorPort {
    private final String prefix;
    private final int entropyBytes;
    private final SecureRandom random;

    public SecureRandomLoginAccountRefGenerator(String prefix, int entropyBytes) {
        this(prefix, entropyBytes, new SecureRandom());
    }

    SecureRandomLoginAccountRefGenerator(String prefix, int entropyBytes, SecureRandom random) {
        if (prefix == null || !prefix.matches("[A-Za-z][A-Za-z0-9_-]{1,15}"))
            throw new IllegalArgumentException("Invalid account reference prefix");
        if (entropyBytes < 16)
            throw new IllegalArgumentException("Account reference entropy must be at least 16 bytes");
        this.prefix = prefix;
        this.entropyBytes = entropyBytes;
        this.random = Objects.requireNonNull(random);
    }

    public LoginAccountRef generate() {
        byte[] value = new byte[entropyBytes];
        random.nextBytes(value);
        return new LoginAccountRef(prefix + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(value));
    }
}

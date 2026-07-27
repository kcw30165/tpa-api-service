package com.bct.ngtpa.apiservice.adapter.out.security.login.replay;

import com.bct.ngtpa.apiservice.application.dto.auth.login.replay.*;
import com.bct.ngtpa.apiservice.application.port.out.LoginStartReplayProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.LoginDestinationChoice;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesGcmLoginStartReplayProtector implements LoginStartReplayProtectorPort {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private final ObjectMapper mapper;
    private final String version;
    private final SecretKeySpec key;

    public AesGcmLoginStartReplayProtector(ObjectMapper mapper, String version, String secret) {
        this.mapper = Objects.requireNonNull(mapper);
        this.version = require(version, "version");
        byte[] decoded = Base64.getDecoder().decode(require(secret, "secret"));
        if (decoded.length != 32)
            throw new IllegalArgumentException("Login replay secret must decode to 32 bytes");
        this.key = new SecretKeySpec(decoded, "AES");
    }

    public ProtectedLoginStartReplay protect(LoginStartReplayPayload payload) {
        try {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance(ALGORITHM);
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            c.updateAAD(version.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = c.doFinal(mapper.writeValueAsBytes(Wire.from(payload)));
            return new ProtectedLoginStartReplay(version, ALGORITHM, Base64.getEncoder().encodeToString(iv),
                    Base64.getEncoder().encodeToString(encrypted));
        } catch (Exception e) {
            throw new LoginStartReplayProtectionException("Failed to protect login start replay", e);
        }
    }

    public LoginStartReplayPayload unprotect(ProtectedLoginStartReplay protectedReplay) {
        if (!version.equals(protectedReplay.keyVersion()) || !ALGORITHM.equals(protectedReplay.algorithm()))
            throw new LoginStartReplayProtectionException("Unsupported login replay protection version or algorithm");
        try {
            byte[] iv = Base64.getDecoder().decode(protectedReplay.iv());
            if (iv.length != 12)
                throw new IllegalArgumentException("Invalid replay IV");
            Cipher c = Cipher.getInstance(ALGORITHM);
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            c.updateAAD(version.getBytes(StandardCharsets.UTF_8));
            Wire wire = mapper.readValue(c.doFinal(Base64.getDecoder().decode(protectedReplay.ciphertext())),
                    Wire.class);
            return wire.toDomain();
        } catch (Exception e) {
            throw new LoginStartReplayProtectionException("Failed to unprotect login start replay", e);
        }
    }

    private String require(String v, String n) {
        if (v == null || v.isBlank())
            throw new IllegalArgumentException(n + " must not be blank");
        return v.trim();
    }

    private record Choice(String destinationId, String channel, String maskedDestination) {
    }

    private record Wire(String authFlowSessionId, String csrfToken, int expiresInSeconds, List<Choice> destinations,
            String expiresAt) {
        static Wire from(LoginStartReplayPayload p) {
            return new Wire(p.authFlowSessionId(), p.csrfToken(), p.expiresInSeconds(),
                    p.destinations().stream()
                            .map(d -> new Choice(d.destinationId(), d.channel(), d.maskedDestination())).toList(),
                    p.expiresAt().toString());
        }

        LoginStartReplayPayload toDomain() {
            return new LoginStartReplayPayload(authFlowSessionId, csrfToken, expiresInSeconds,
                    destinations == null ? List.of()
                            : destinations.stream().map(
                                    d -> new LoginDestinationChoice(d.destinationId, d.channel, d.maskedDestination))
                                    .toList(),
                    Instant.parse(expiresAt));
        }
    }
}

package com.bct.ngtpa.apiservice.application.port.out;

import java.util.regex.Pattern;

/**
 * Opaque semantic request fingerprint for Username Recovery idempotency comparison.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-session-redis-design.md}
 * section 1 (recovery idempotency hash; the captcha answer is never included) and section 8
 * (safe persistence). The value is opaque and redacted; the hashing algorithm that produces it
 * belongs to Task 014. Mirrors {@code RegistrationRequestHash} opaque hex-digest format.
 * {@link #toString()} never discloses the fingerprint.
 */
public record UsernameRecoveryIdempotencyFingerprint(String value) {

    private static final Pattern SHA_256_HEX = Pattern.compile("^[a-f0-9]{64}$");

    public UsernameRecoveryIdempotencyFingerprint {
        if (value == null || value.isBlank() || !SHA_256_HEX.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid username recovery semantic fingerprint");
        }
    }

    @Override
    public String toString() {
        return "UsernameRecoveryIdempotencyFingerprint[redacted]";
    }
}

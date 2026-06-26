package com.bct.ngtpa.apiservice.application.service.auth.otp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Sha256OtpCodeHasher implements OtpCodeHasher {

    @Override
    public String hash(String rawCode) {
        if (isBlank(rawCode)) {
            throw new IllegalArgumentException("OTP code must not be null or blank.");
        }

        MessageDigest messageDigest = messageDigest();
        byte[] digest = messageDigest.digest(rawCode.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    @Override
    public boolean matches(String rawCode, String storedHash) {
        if (isBlank(rawCode) || isBlank(storedHash)) {
            return false;
        }

        return hash(rawCode).equals(storedHash);
    }

    private MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available in the runtime.", exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
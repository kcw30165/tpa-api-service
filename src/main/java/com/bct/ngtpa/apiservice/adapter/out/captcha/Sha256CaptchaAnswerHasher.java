package com.bct.ngtpa.apiservice.adapter.out.captcha;

import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaAnswerHash;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaAnswerHasherPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class Sha256CaptchaAnswerHasher implements CaptchaAnswerHasherPort {

    @Override
    public CaptchaAnswerHash hash(String answer, boolean caseSensitive) {
        String normalized = normalize(answer, caseSensitive);
        return new CaptchaAnswerHash(
                HexFormat.of().formatHex(messageDigest().digest(normalized.getBytes(StandardCharsets.UTF_8))));
    }

    @Override
    public boolean matches(String submittedAnswer, CaptchaAnswerHash expectedHash, boolean caseSensitive) {
        if (expectedHash == null) {
            return false;
        }
        try {
            byte[] actual = hash(submittedAnswer, caseSensitive).value().getBytes(StandardCharsets.UTF_8);
            byte[] expected = expectedHash.value().getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(actual, expected);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String normalize(String answer, boolean caseSensitive) {
        if (answer == null) {
            throw new IllegalArgumentException("Captcha answer is invalid");
        }
        String trimmed = answer.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("Captcha answer is invalid");
        }
        return caseSensitive ? trimmed : trimmed.toUpperCase(Locale.ROOT);
    }

    private MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available in the runtime.", exception);
        }
    }
}
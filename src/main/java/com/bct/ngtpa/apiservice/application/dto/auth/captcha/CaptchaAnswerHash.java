package com.bct.ngtpa.apiservice.application.dto.auth.captcha;

import java.util.regex.Pattern;

public record CaptchaAnswerHash(String value) {
    private static final Pattern SHA_256_HEX = Pattern.compile("^[a-f0-9]{64}$");

    public CaptchaAnswerHash {
        if (value == null || value.isBlank() || !SHA_256_HEX.matcher(value).matches()) {
            throw new IllegalArgumentException("CaptchaAnswerHash is invalid");
        }
    }

    @Override
    public String toString() {
        return "CaptchaAnswerHash[value=<redacted>]";
    }
}
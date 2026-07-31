package com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword;

public final class RedisForgotPasswordOtpCommitStateKeyFactory {
    private final String prefix;

    public RedisForgotPasswordOtpCommitStateKeyFactory(String p) {
        if (p == null || p.isBlank())
            throw new IllegalArgumentException("prefix required");
        prefix = p.endsWith(":") ? p.substring(0, p.length() - 1) : p;
    }

    public String keyFor(String ref) {
        if (ref == null || !ref.matches("^[A-Za-z0-9_-]{22,128}$"))
            throw new IllegalArgumentException("invalid opaque commit reference");
        return prefix + ":forgot-password:otp-commit:" + ref;
    }
}

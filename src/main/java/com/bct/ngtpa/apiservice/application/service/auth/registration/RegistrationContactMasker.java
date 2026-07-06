package com.bct.ngtpa.apiservice.application.service.auth.registration;

public final class RegistrationContactMasker {
    private RegistrationContactMasker() {
    }

    public static String maskEmail(String email) {
        String value = normalizedOrThrow(email);
        int at = value.indexOf('@');
        if (at <= 0) {
            throw new IllegalArgumentException("invalid email contact");
        }
        String local = value.substring(0, at);
        String domain = value.substring(at);
        if (local.length() == 1) {
            return local + "****" + domain;
        }
        return local.charAt(0) + "****" + local.charAt(local.length() - 1) + domain;
    }

    public static String maskMobile(String mobile) {
        String value = normalizedOrThrow(mobile);
        if (value.length() < 4) {
            throw new IllegalArgumentException("invalid mobile contact");
        }
        return "****" + value.substring(value.length() - 4);
    }

    private static String normalizedOrThrow(String value) {
        if (value == null || value.trim().isBlank()) {
            throw new IllegalArgumentException("contact must not be blank");
        }
        return value.trim();
    }
}

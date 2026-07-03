package com.bct.ngtpa.apiservice.application.dto.session.authflow;

import java.util.Objects;

public record SelectedTrust(String trustCode, String accountEnv, String schemeName, String logoKey, Integer displayOrder) {
    public SelectedTrust {
        trustCode = requiredText(trustCode, "trustCode");
        accountEnv = requiredText(accountEnv, "accountEnv");
    }

    private static String requiredText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}

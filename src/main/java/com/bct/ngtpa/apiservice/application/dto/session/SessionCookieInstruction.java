package com.bct.ngtpa.apiservice.application.dto.session;

import java.util.Objects;

public record SessionCookieInstruction(
        SessionCookieOperation operation,
        SessionCookiePolicy policy,
        String value) {

    public SessionCookieInstruction {
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        if (operation == SessionCookieOperation.SET && (value == null || value.isBlank())) {
            throw new IllegalArgumentException("Cookie value is required for SET operation");
        }
        if (operation == SessionCookieOperation.CLEAR && (value == null || value.isBlank())) {
            value = null;
        }
    }

    @Override
    public String toString() {
        String valueSummary = operation == SessionCookieOperation.SET ? "<redacted>" : "<empty>";
        return "SessionCookieInstruction[operation=" + operation
                + ", policy=" + policy
                + ", value=" + valueSummary
                + "]";
    }
}

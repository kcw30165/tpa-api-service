package com.bct.ngtpa.apiservice.application.dto.auth.registration;

import java.util.List;
import java.util.Objects;

public record RegistrationLookupResult(
        RegistrationLookupStatus status,
        List<RegistrationLookupAccount> accounts,
        String upstreamErrorCode) {
    public RegistrationLookupResult {
        Objects.requireNonNull(status, "status must not be null");
        accounts = accounts == null ? List.of() : List.copyOf(accounts);
        if (status != RegistrationLookupStatus.ELIGIBLE && !accounts.isEmpty()) {
            throw new IllegalArgumentException("accounts must be empty unless lookup is eligible");
        }
        upstreamErrorCode = upstreamErrorCode == null || upstreamErrorCode.isBlank() ? null : upstreamErrorCode.trim();
    }

    @Override
    public String toString() {
        return "RegistrationLookupResult[status=" + status
                + ", accountCount=" + accounts.size()
                + ", upstreamErrorCode=" + upstreamErrorCode + "]";
    }
}

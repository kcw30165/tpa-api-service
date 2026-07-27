package com.bct.ngtpa.apiservice.application.dto.auth.login.preauth;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record LoginPreAuthContext(
        String normalizedUsername,
        String custType,
        String userRole,
        Map<String, LoginPreAuthDestination> destinationsById,
        Instant credentialVerifiedAt,
        String workflowVersion) {
    public LoginPreAuthContext {
        Objects.requireNonNull(normalizedUsername);
        Objects.requireNonNull(custType);
        destinationsById = destinationsById == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(destinationsById));
        Objects.requireNonNull(credentialVerifiedAt);
        Objects.requireNonNull(workflowVersion);
    }

    @Override
    public String toString() {
        return "LoginPreAuthContext[normalizedUsername=<redacted>, custType=" + custType
                + ", userRole=" + userRole + ", destinationsCount=" + destinationsById.size()
                + ", credentialVerifiedAt=" + credentialVerifiedAt + ", workflowVersion="
                + workflowVersion + "]";
    }
}

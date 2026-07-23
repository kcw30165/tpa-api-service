package com.bct.ngtpa.apiservice.application.dto.auth.login;

import java.util.List;
import java.util.Objects;

/** Transport-neutral result used to establish protected LOGIN pre-auth context. */
public record CredentialAuthenticationResult(
        CredentialAuthenticationStatus status,
        String normalizedUsername,
        String custType,
        String userRole,
        Integer failCount,
        List<LoginCredentialDestination> destinations,
        String downstreamCode) {

    public CredentialAuthenticationResult {
        Objects.requireNonNull(status, "status must not be null");
        destinations = destinations == null ? List.of() : List.copyOf(destinations);
        // Raw Progress/APIM codes are adapter-internal and must never cross this boundary.
        downstreamCode = null;
    }

    @Override
    public String toString() {
        return "CredentialAuthenticationResult[status=" + status
                + ", normalizedUsername=<redacted>, custType=" + custType
                + ", userRole=" + userRole + ", failCount=" + failCount
                + ", destinationsCount=" + destinations.size()
                + ", downstreamCode=<redacted>]";
    }
}

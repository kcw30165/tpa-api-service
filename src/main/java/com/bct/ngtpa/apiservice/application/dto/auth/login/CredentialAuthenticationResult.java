package com.bct.ngtpa.apiservice.application.dto.auth.login;
import java.util.List;
import java.util.Objects;
public record CredentialAuthenticationResult(
        CredentialAuthenticationStatus status, String normalizedUsername, String userRole,
        Integer failCount, List<LoginCredentialDestination> destinations, String downstreamCode) {
    public CredentialAuthenticationResult {
        Objects.requireNonNull(status, "status must not be null");
        destinations = destinations == null ? List.of() : List.copyOf(destinations);
        downstreamCode = null;
    }
    @Override public String toString() { return "CredentialAuthenticationResult[status=" + status + ", normalizedUsername=<redacted>, userRole=" + userRole + ", failCount=" + failCount + ", destinationsCount=" + destinations.size() + ", downstreamCode=<redacted>]"; }
}

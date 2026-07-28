package com.bct.ngtpa.apiservice.application.usecase.auth.login.session;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import java.util.Objects;

public record EstablishAuthenticatedLoginSessionResult(
        EstablishAuthenticatedLoginSessionStatus status,
        AuthenticatedSessionId authenticatedSessionId,
        String authenticatedCsrfToken,
        boolean clearAuthFlowCookie,
        boolean clearXsrfCookie) {

    public EstablishAuthenticatedLoginSessionResult {
        Objects.requireNonNull(status, "status must not be null");
        boolean established = status == EstablishAuthenticatedLoginSessionStatus.ESTABLISHED;
        if (established && authenticatedSessionId == null) {
            throw new IllegalArgumentException("ESTABLISHED requires authenticatedSessionId");
        }
        if (established && (authenticatedCsrfToken == null || authenticatedCsrfToken.isBlank())) {
            throw new IllegalArgumentException("ESTABLISHED requires authenticatedCsrfToken");
        }
        if (!established && (authenticatedSessionId != null || authenticatedCsrfToken != null)) {
            throw new IllegalArgumentException("Failure result cannot expose authenticated credentials");
        }
        authenticatedCsrfToken = established ? authenticatedCsrfToken.trim() : null;
    }

    public static EstablishAuthenticatedLoginSessionResult established(
            AuthenticatedSessionId id,
            String rawAuthenticatedCsrfToken) {
        return new EstablishAuthenticatedLoginSessionResult(
                EstablishAuthenticatedLoginSessionStatus.ESTABLISHED,
                Objects.requireNonNull(id),
                rawAuthenticatedCsrfToken,
                true,
                true);
    }

    public static EstablishAuthenticatedLoginSessionResult failed(
            EstablishAuthenticatedLoginSessionStatus status) {
        return new EstablishAuthenticatedLoginSessionResult(status, null, null, false, false);
    }

    @Override
    public String toString() {
        return "EstablishAuthenticatedLoginSessionResult[status=" + status
                + ", authenticatedSessionId=<redacted>, authenticatedCsrfToken=<redacted>"
                + ", clearAuthFlowCookie=" + clearAuthFlowCookie
                + ", clearXsrfCookie=" + clearXsrfCookie + "]";
    }
}

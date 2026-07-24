package com.bct.ngtpa.apiservice.application.usecase.auth.login.session;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import java.util.Objects;

public record EstablishAuthenticatedLoginSessionResult(
        EstablishAuthenticatedLoginSessionStatus status,
        AuthenticatedSessionId authenticatedSessionId,
        boolean clearAuthFlowCookie,
        boolean clearXsrfCookie) {
    public EstablishAuthenticatedLoginSessionResult {
        Objects.requireNonNull(status, "status must not be null");
        if (status == EstablishAuthenticatedLoginSessionStatus.ESTABLISHED && authenticatedSessionId == null) {
            throw new IllegalArgumentException("ESTABLISHED requires authenticatedSessionId");
        }
        if (status != EstablishAuthenticatedLoginSessionStatus.ESTABLISHED && authenticatedSessionId != null) {
            throw new IllegalArgumentException("Failure result cannot expose authenticatedSessionId");
        }
    }

    public static EstablishAuthenticatedLoginSessionResult established(AuthenticatedSessionId id) {
        return new EstablishAuthenticatedLoginSessionResult(
                EstablishAuthenticatedLoginSessionStatus.ESTABLISHED,
                Objects.requireNonNull(id),
                true,
                true);
    }

    public static EstablishAuthenticatedLoginSessionResult failed(
            EstablishAuthenticatedLoginSessionStatus status) {
        return new EstablishAuthenticatedLoginSessionResult(status, null, false, false);
    }

    @Override
    public String toString() {
        return "EstablishAuthenticatedLoginSessionResult[status=" + status
                + ", authenticatedSessionId=<redacted>, clearAuthFlowCookie=" + clearAuthFlowCookie
                + ", clearXsrfCookie=" + clearXsrfCookie + "]";
    }
}

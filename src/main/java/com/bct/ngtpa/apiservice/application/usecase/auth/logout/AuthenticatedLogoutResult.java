package com.bct.ngtpa.apiservice.application.usecase.auth.logout;

import java.util.Objects;

public record AuthenticatedLogoutResult(AuthenticatedLogoutStatus status) {
    public AuthenticatedLogoutResult {
        Objects.requireNonNull(status, "status must not be null");
    }

    public static AuthenticatedLogoutResult loggedOut() {
        return new AuthenticatedLogoutResult(AuthenticatedLogoutStatus.LOGGED_OUT);
    }

    public static AuthenticatedLogoutResult alreadyLoggedOut() {
        return new AuthenticatedLogoutResult(AuthenticatedLogoutStatus.ALREADY_LOGGED_OUT);
    }

    public static AuthenticatedLogoutResult failed() {
        return new AuthenticatedLogoutResult(AuthenticatedLogoutStatus.FAILED);
    }

    public boolean successful() {
        return status != AuthenticatedLogoutStatus.FAILED;
    }
}

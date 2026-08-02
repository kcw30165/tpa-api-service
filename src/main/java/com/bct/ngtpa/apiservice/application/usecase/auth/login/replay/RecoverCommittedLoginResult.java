package com.bct.ngtpa.apiservice.application.usecase.auth.login.replay;

import com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginSessionAccount;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import java.util.List;

public record RecoverCommittedLoginResult(
        boolean recovered,
        List<LoginSessionAccount> accounts,
        AuthenticatedSessionId authenticatedSessionId,
        String authenticatedCsrfToken) {
    public RecoverCommittedLoginResult {
        accounts = accounts == null ? List.of() : List.copyOf(accounts);
        if (recovered && (accounts.isEmpty() || authenticatedSessionId == null
                || authenticatedCsrfToken == null || authenticatedCsrfToken.isBlank()))
            throw new IllegalArgumentException("recovered result requires authenticated response data");
        if (!recovered && (!accounts.isEmpty() || authenticatedSessionId != null || authenticatedCsrfToken != null))
            throw new IllegalArgumentException("failed recovery cannot carry authenticated response data");
    }
    public static RecoverCommittedLoginResult failed() {
        return new RecoverCommittedLoginResult(false, List.of(), null, null);
    }
}

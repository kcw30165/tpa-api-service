package com.bct.ngtpa.apiservice.application.usecase.auth.login.session;

import com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginSessionAccount;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedAccountRef;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionAccount;
import java.util.List;

public final class LoginAuthenticatedSessionAccountMapper {
    private LoginAuthenticatedSessionAccountMapper() {}

    public static List<AuthenticatedSessionAccount> map(List<LoginSessionAccount> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            throw new IllegalArgumentException("accounts must not be empty");
        }
        return accounts.stream()
                .map(account -> new AuthenticatedSessionAccount(
                        new AuthenticatedAccountRef(account.accountRef().value()),
                        account.accountEnv(),
                        account.policyNo(),
                        account.certNo(),
                        account.trustCode(),
                        account.schemeType(),
                        account.termStatus(),
                        account.termCompletionDate()))
                .toList();
    }
}

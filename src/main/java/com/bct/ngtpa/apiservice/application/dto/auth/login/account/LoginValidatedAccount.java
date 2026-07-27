package com.bct.ngtpa.apiservice.application.dto.auth.login.account;

import java.time.LocalDate;
import java.util.Objects;

public record LoginValidatedAccount(String accountEnv, String trustCode, String rawPolicyNo, String policyNo,
        String schemeType, int rawCertNo, String certNo, String termStatus, LocalDate termCompletionDate) {
    public LoginValidatedAccount {
        Objects.requireNonNull(accountEnv);
        Objects.requireNonNull(trustCode);
        Objects.requireNonNull(rawPolicyNo);
        Objects.requireNonNull(policyNo);
        Objects.requireNonNull(certNo);
    }

    @Override
    public String toString() {
        return "LoginValidatedAccount[accountEnv=<redacted>, trustCode=<redacted>, rawPolicyNo=<redacted>, policyNo=<redacted>, schemeType="
                + (schemeType == null ? "null" : "present") + ", rawCertNo=<redacted>, certNo=<redacted>, termStatus="
                + (termStatus == null ? "blank" : "T") + ", termCompletionDate=<redacted>]";
    }
}

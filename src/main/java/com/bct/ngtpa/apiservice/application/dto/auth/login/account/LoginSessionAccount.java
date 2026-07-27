package com.bct.ngtpa.apiservice.application.dto.auth.login.account;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Account mapping intended only for the later encrypted authenticated-session
 * context.
 */
public record LoginSessionAccount(LoginAccountRef accountRef, String accountEnv, String rawPolicyNo, String policyNo,
        String rawCertNo, String certNo, String trustCode, String schemeType, String termStatus,
        LocalDate termCompletionDate) {
    public LoginSessionAccount {
        Objects.requireNonNull(accountRef);
        Objects.requireNonNull(accountEnv);
        Objects.requireNonNull(rawPolicyNo);
        Objects.requireNonNull(policyNo);
        Objects.requireNonNull(rawCertNo);
        Objects.requireNonNull(certNo);
        Objects.requireNonNull(trustCode);
    }

    @Override
    public String toString() {
        return "LoginSessionAccount[accountRef=<redacted>, accountEnv=<redacted>, rawPolicyNo=<redacted>, policyNo=<redacted>, rawCertNo=<redacted>, certNo=<redacted>, trustCode=<redacted>, schemeType="
                + (schemeType == null ? "null" : "present") + ", termStatus=" + (termStatus == null ? "blank" : "T")
                + ", termCompletionDate=<redacted>]";
    }
}

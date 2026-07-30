package com.bct.ngtpa.apiservice.application.dto.session.authenticated;

import java.time.LocalDate;
import java.util.Objects;

public record AuthenticatedSessionAccount(AuthenticatedAccountRef accountRef, String accountEnv, String policyNo,
        String certNo, String trustCode, String schemeType, String termStatus, LocalDate termCompletionDate) {
    public AuthenticatedSessionAccount {
        Objects.requireNonNull(accountRef);
        accountEnv = required(accountEnv, "accountEnv");
        policyNo = required(policyNo, "policyNo");
        certNo = required(certNo, "certNo");
        trustCode = required(trustCode, "trustCode");
        schemeType = optional(schemeType);
        termStatus = optional(termStatus);
        if (termStatus == null && termCompletionDate != null)
            throw new IllegalArgumentException("termCompletionDate requires termStatus");
        if (termStatus != null && !"T".equals(termStatus))
            throw new IllegalArgumentException("Unsupported termStatus");
        if ("T".equals(termStatus) && termCompletionDate == null)
            throw new IllegalArgumentException("Terminated account requires termCompletionDate");
    }

    private static String required(String v, String n) {
        if (v == null || v.isBlank())
            throw new IllegalArgumentException(n + " required");
        return v.trim();
    }

    private static String optional(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    @Override
    public String toString() {
        return "AuthenticatedSessionAccount[accountRef=<redacted>, accountEnv=<redacted>, policyNo=<redacted>, certNo=<redacted>, trustCode=<redacted>, schemeType="
                + (schemeType == null ? "null" : "present") + ", termStatus=" + (termStatus == null ? "blank" : "T")
                + ", termCompletionDate=<redacted>]";
    }
}

package com.bct.ngtpa.apiservice.application.dto.auth.login.account;

/** Raw APIM account item. Deliberately permissive; application validation follows. */
public record LoginAccountRecord(
        String accountEnv,
        String trustCode,
        String rawPolicyNo,
        String schemeType,
        int rawCertNo,
        String termStatus,
        String termCompletionDate) {
    @Override
    public String toString() {
        return "LoginAccountRecord[accountEnv=<redacted>, trustCode=<redacted>, "
                + "rawPolicyNo=<redacted>, schemeType=" + safePresence(schemeType)
                + ", rawCertNo=<redacted>, termStatus=" + safePresence(termStatus)
                + ", termCompletionDate=<redacted>]";
    }
    private static String safePresence(String value) {
        return value == null || value.isBlank() ? "blank" : "present";
    }
}

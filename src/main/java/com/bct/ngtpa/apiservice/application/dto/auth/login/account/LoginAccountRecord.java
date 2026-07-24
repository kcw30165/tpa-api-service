package com.bct.ngtpa.apiservice.application.dto.auth.login.account;

/** Raw validated-shape account item; formatting and business validation occur later. */
public record LoginAccountRecord(
        String accountEnv,
        String trustCode,
        String rawPolicyNo,
        String schemeType,
        int rawCertNo,
        String termStatus,
        String termCompletionDate) {
    public LoginAccountRecord {
        accountEnv = required(accountEnv,"accountEnv");
        trustCode = required(trustCode,"trustCode");
        rawPolicyNo = required(rawPolicyNo,"rawPolicyNo");
        schemeType = optional(schemeType);
        if(rawCertNo<=0)throw new IllegalArgumentException("rawCertNo must be positive");
        termStatus = optional(termStatus);
        termCompletionDate = optional(termCompletionDate);
    }
    private static String required(String value,String name){if(value==null||value.isBlank())throw new IllegalArgumentException(name+" must not be blank");return value.trim();}
    private static String optional(String value){return value==null||value.isBlank()?null:value.trim();}
    @Override public String toString(){return "LoginAccountRecord[accountEnv=<redacted>, trustCode=<redacted>, rawPolicyNo=<redacted>, schemeType="+schemeType+", rawCertNo=<redacted>, termStatus="+(termStatus==null?"blank":"present")+", termCompletionDate=<redacted>]";}
}

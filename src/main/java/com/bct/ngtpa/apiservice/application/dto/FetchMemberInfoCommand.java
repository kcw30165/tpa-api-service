package com.bct.ngtpa.apiservice.application.dto;

import java.util.Objects;

public class FetchMemberInfoCommand {

    private final String accountEnv;
    private final String policyNo;
    private final String certNo;
    private final String userId;

    public FetchMemberInfoCommand(String accountEnv, String policyNo, String certNo, String userId) {
        this.accountEnv = accountEnv;
        this.policyNo = policyNo;
        this.certNo = certNo;
        this.userId = userId;
    }

    public String getAccountEnv() {
        return accountEnv;
    }

    public String getPolicyNo() {
        return policyNo;
    }

    public String getCertNo() {
        return certNo;
    }

    public String getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FetchMemberInfoCommand that = (FetchMemberInfoCommand) o;
        return Objects.equals(accountEnv, that.accountEnv) && Objects.equals(policyNo, that.policyNo) && Objects.equals(certNo, that.certNo) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountEnv, policyNo, certNo, userId);
    }
}

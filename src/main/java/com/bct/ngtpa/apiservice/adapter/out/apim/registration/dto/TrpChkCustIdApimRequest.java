package com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto;

/**
 * Adapter-only DTO for TRPChkCustId. Field names are intentionally isolated in the
 * APIM adapter because the final TAS contract remains boundary-sensitive.
 */
public record TrpChkCustIdApimRequest(
        String trustCode,
        String accountEnv,
        String customerId) {
    @Override
    public String toString() {
        return "TrpChkCustIdApimRequest[trustCode=" + trustCode
                + ", accountEnv=" + accountEnv
                + ", customerId=<redacted>]";
    }
}

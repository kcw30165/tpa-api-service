package com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto;

/**
 * Placeholder adapter-only response item for TRPChkCustId success payload.
 * Current BFF behavior only needs to distinguish success from err-message.
 */
public record TrpChkCustIdApimDataItem(Boolean available) {
}

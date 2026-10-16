package com.bct.ngtpa.apiservice.adapter.out.apim.fundcomparison.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Downstream wire request for TRPGetFundCmpOpt. Raw downstream field names confined here, per
 * docs/design/13-fund-comparison/03-downstream-integration.md section 3.1.
 */
@JsonPropertyOrder({"policy-no"})
public record TrpGetFundCmpOptApimRequest(@JsonProperty("policy-no") String policyNo) {}

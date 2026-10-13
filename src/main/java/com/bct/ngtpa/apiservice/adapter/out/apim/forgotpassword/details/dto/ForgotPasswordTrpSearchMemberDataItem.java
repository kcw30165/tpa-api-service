package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Forgot Password-owned TRPSearchMember response item. Sensitive values are never logged. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ForgotPasswordTrpSearchMemberDataItem(
    @JsonProperty("env") String env,
    @JsonProperty("trust-code") String trustCode,
    @JsonProperty("sch-type") String schemeType,
    @JsonProperty("sch-name") String schemeName,
    @JsonProperty("chin-sch-name") String schemeNameChi,
    @JsonProperty("policy-no") String policyNo,
    @JsonProperty("cert-no") String certNo,
    @JsonProperty("term-status") String termStatus,
    @JsonProperty("term-complete-date") String termCompleteDate,
    @JsonProperty("isRegistered") Boolean isRegistered,
    @JsonProperty("cust-id") String customerId) {

  @Override
  public String toString() {
    return "ForgotPasswordTrpSearchMemberDataItem[env=, trustCode=, schemeType=, schemeName=, schemeNameChi=, policyNo=, "
        + "certNo=, termStatus=, termCompleteDate=, isRegistered="
        + isRegistered
        + ", customerId=]";
  }
}

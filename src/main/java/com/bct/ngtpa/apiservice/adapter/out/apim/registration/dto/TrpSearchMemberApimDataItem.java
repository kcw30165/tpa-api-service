package com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Adapter-only TRPSearchMember response item for registration lookup. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrpSearchMemberApimDataItem(
    @JsonProperty("env") String env,
    @JsonProperty("trust-code") String trustCode,
    @JsonProperty("sch-type") String schemeType,
    @JsonProperty("policy-no") String policyNo,
    @JsonProperty("cert-no") String certNo,
    @JsonProperty("term-status") String termStatus,
    @JsonProperty("term-complete-date") String termCompleteDate,
    @JsonProperty("isRegistered") Boolean isRegistered,
    @JsonProperty("cust-id") String customerId) {
  /**
   * Compatibility constructor for existing fixtures while they migrate to the complete member
   * shape.
   */
  public TrpSearchMemberApimDataItem(
      String email, String mobileNumber, String env, String trustCode) {
    this(env, trustCode, null, null, null, null, null, Boolean.FALSE, null);
  }

  @Override
  public String toString() {
    return "TrpSearchMemberApimDataItem[env=<redacted>, trustCode=<redacted>, schemeType=<redacted>, policyNo=<redacted>, certNo=<redacted>, termStatus=<redacted>, termCompleteDate=<redacted>, isRegistered="
        + isRegistered
        + ", customerId=<redacted>]";
  }
}

package com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Adapter-only TRPSearchMember response {@code data[]} item.
 *
 * <p>Raw APIM field names stay inside the APIM boundary: {@code env}, {@code trust-code}, {@code
 * sch-type}, {@code policy-no}, {@code cert-no}, {@code term-status}, {@code term-complete-date},
 * {@code isRegistered}, {@code cust-id}. Policy and certificate numbers are modeled as strings so
 * leading zeros are preserved at the transport boundary. Unknown response fields are ignored
 * without exposure. {@link #toString()} never discloses identity, contact, username, policy,
 * certificate, or other sensitive values.
 */
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
    @JsonProperty("cust-id") String custId) {

  @Override
  public String toString() {
    return "TrpSearchMemberApimDataItem[env=<redacted>, trustCode=<redacted>, "
        + "schemeType=<redacted>, policyNo=<redacted>, certNo=<redacted>, "
        + "termStatus=<redacted>, termCompleteDate=<redacted>, isRegistered="
        + isRegistered
        + ", custId=<redacted>]";
  }
}

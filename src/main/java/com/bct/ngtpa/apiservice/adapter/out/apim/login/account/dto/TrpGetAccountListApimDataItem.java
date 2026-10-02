package com.bct.ngtpa.apiservice.adapter.out.apim.login.account.dto;

import com.fasterxml.jackson.annotation.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TrpGetAccountListApimDataItem(
    @JsonProperty("env") String accountEnv,
    @JsonProperty("trust-code") String trustCode,
    @JsonProperty("policy-no") String policyNo,
    @JsonProperty("sch-type") String schemeType,
    @JsonProperty("sch-name") String schemeName,
    @JsonProperty("chin-sch-name") String schemeNameChi,
    @JsonProperty("cert-no") Integer certNo,
    @JsonProperty("term-status") String termStatus,
    @JsonProperty("term-complete-date") String termCompleteDate) {

  /**
   * Compatibility constructor for existing call sites predating the addition of {@code sch-name}
   * / {@code chin-sch-name} (newly added APIM response fields; see
   * docs/design/10-template-service/template-service-solution-design.md section 8.2). Both new
   * fields default to {@code null}.
   */
  public TrpGetAccountListApimDataItem(
      String accountEnv,
      String trustCode,
      String policyNo,
      String schemeType,
      Integer certNo,
      String termStatus,
      String termCompleteDate) {
    this(accountEnv, trustCode, policyNo, schemeType, null, null, certNo, termStatus, termCompleteDate);
  }

  @Override
  public String toString() {
    return "TrpGetAccountListApimDataItem[accountEnv=<redacted>, trustCode=<redacted>, policyNo=<redacted>, schemeType="
        + schemeType
        + ", schemeName=<redacted>, schemeNameChi=<redacted>, certNo=<redacted>, termStatus="
        + (termStatus == null ? "blank" : "present")
        + ", termCompleteDate=<redacted>]";
  }
}

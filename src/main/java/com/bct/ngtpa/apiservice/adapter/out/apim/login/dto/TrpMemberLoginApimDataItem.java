package com.bct.ngtpa.apiservice.adapter.out.apim.login.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TrpMemberLoginApimDataItem(
    @JsonProperty("success") boolean success,
    @JsonProperty("login-user-role") String loginUserRole,
    @JsonProperty("fail-count") Integer failCount,
    @JsonProperty("otp") List<TrpMemberLoginApimOtpItem> otp,
    @JsonProperty("trust-code") String trustCode,
    @JsonProperty("sch-name") String schemeName,
    @JsonProperty("chin-sch-name") String schemeNameChi) {

  /**
   * Compatibility constructor for existing call sites predating the addition of {@code
   * trust-code} / {@code sch-name} / {@code chin-sch-name} (modeled ahead of the downstream
   * TAS/APIM change; see
   * docs/design/10-template-service/template-service-implementation-plan.md section D.11.1). All
   * three new fields default to {@code null}.
   */
  public TrpMemberLoginApimDataItem(
      boolean success,
      String loginUserRole,
      Integer failCount,
      List<TrpMemberLoginApimOtpItem> otp) {
    this(success, loginUserRole, failCount, otp, null, null, null);
  }
}

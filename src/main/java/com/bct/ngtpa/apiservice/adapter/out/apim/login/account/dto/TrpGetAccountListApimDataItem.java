package com.bct.ngtpa.apiservice.adapter.out.apim.login.account.dto;

import com.fasterxml.jackson.annotation.*;

/**
 * Reverted to its original 7 components (plan section D.11.1, 2026-10-06): the trust code and
 * scheme names needed by the Login OTP content are modeled on {@link
 * com.bct.ngtpa.apiservice.adapter.out.apim.login.dto.TrpMemberLoginApimDataItem} instead, since
 * that call happens at START, before any OTP is sent, while this item is only available after OTP
 * verification. Script 087 confirmed {@code TrpGetAccountListContractMapper} never read {@code
 * schemeName}/{@code schemeNameChi} and that every real call site already used this 7-argument
 * shape, so this revert changes no other file.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrpGetAccountListApimDataItem(
    @JsonProperty("env") String accountEnv,
    @JsonProperty("trust-code") String trustCode,
    @JsonProperty("policy-no") String policyNo,
    @JsonProperty("sch-type") String schemeType,
    @JsonProperty("cert-no") Integer certNo,
    @JsonProperty("term-status") String termStatus,
    @JsonProperty("term-complete-date") String termCompleteDate) {

  @Override
  public String toString() {
    return "TrpGetAccountListApimDataItem[accountEnv=<redacted>, trustCode=<redacted>, policyNo=<redacted>, schemeType="
        + schemeType
        + ", certNo=<redacted>, termStatus="
        + (termStatus == null ? "blank" : "present")
        + ", termCompleteDate=<redacted>]";
  }
}

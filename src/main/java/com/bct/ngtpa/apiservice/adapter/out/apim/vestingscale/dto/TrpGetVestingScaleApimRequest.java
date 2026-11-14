package com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TrpGetVestingScaleApimRequest(
    @JsonProperty(LOGIN_USER_ID) String loginUserId,
    @JsonProperty(LOGIN_USER_ROLE) String loginUserRole,
    @JsonProperty(POLICY_NO) String policyNo,
    @JsonProperty(CERT_NO) String certNo) {

  public static final String LOGIN_USER_ID = "login-user-id";
  public static final String LOGIN_USER_ROLE = "login-user-role";
  public static final String POLICY_NO = "policy-no";
  public static final String CERT_NO = "cert-no";
}

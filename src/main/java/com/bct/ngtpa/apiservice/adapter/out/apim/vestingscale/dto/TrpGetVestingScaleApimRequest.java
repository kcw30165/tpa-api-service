package com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TrpGetVestingScaleApimRequest(
    @JsonProperty(LOGIN_USER_ID_KEY) String loginUserId,
    @JsonProperty(LOGIN_USER_ROLE_KEY) String loginUserRole,
    @JsonProperty(POLICY_NO_KEY) String policyNo,
    @JsonProperty(CERT_NO_KEY) String certNo) {

  public static final String LOGIN_USER_ID_KEY = "login-user-id";
  public static final String LOGIN_USER_ROLE_KEY = "login-user-role";
  public static final String POLICY_NO_KEY = "policy-no";
  public static final String CERT_NO_KEY = "cert-no";
}

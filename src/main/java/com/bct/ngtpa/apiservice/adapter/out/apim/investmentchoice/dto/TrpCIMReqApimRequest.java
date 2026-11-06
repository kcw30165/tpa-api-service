package com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"login-user-id", "login-user-role", "policy-no", "cert-no", "CIMInstr"})
public record TrpCIMReqApimRequest(
    @JsonProperty("login-user-id") String loginUserId,
    @JsonProperty("login-user-role") String loginUserRole,
    @JsonProperty("policy-no") String policyNo,
    @JsonProperty("cert-no") String certNo,
    @JsonProperty("CIMInstr") List<CimInstruction> cimInstr) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonPropertyOrder({"switch-src", "fund-code", "pcnt"})
  public record CimInstruction(
      @JsonProperty("switch-src") String switchSource,
      @JsonProperty("fund-code") String fundCode,
      @JsonProperty("pcnt") int percentage) {}
}

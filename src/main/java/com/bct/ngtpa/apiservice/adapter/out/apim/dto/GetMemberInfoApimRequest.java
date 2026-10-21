package com.bct.ngtpa.apiservice.adapter.out.apim.dto;

import com.fasterxml.jackson.annotation.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetMemberInfoApimRequest {
  @JsonProperty("policy-no")
  private String policyNo;

  @JsonProperty("cert-no")
  private String certNo;

  @JsonProperty("login-user-id")
  private String loginUserId;

  @JsonProperty("login-user-role")
  private String loginUserRole;
}

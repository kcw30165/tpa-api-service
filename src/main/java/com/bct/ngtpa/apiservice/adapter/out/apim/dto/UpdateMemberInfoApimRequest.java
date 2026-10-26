package com.bct.ngtpa.apiservice.adapter.out.apim.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMemberInfoApimRequest {
  @JsonProperty("policy-no")
  private String policyNo;

  @JsonProperty("cert-no")
  private String certNo;

  @JsonProperty("apply-all")
  private boolean applyAll;

  @JsonProperty("update-fields")
  private Map<String, Object> updateFields;

  @JsonProperty("login-user-id")
  private String loginUserId;

  @JsonProperty("login-user-role")
  private String loginUserRole;
}

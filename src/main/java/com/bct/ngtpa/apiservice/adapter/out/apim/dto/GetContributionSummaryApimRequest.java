package com.bct.ngtpa.apiservice.adapter.out.apim.dto;

import com.fasterxml.jackson.annotation.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetContributionSummaryApimRequest {
  @JsonProperty("policy-no")
  private String policyNo;

  @JsonProperty("cert-no")
  private String certNo;

  @JsonProperty("cover-from")
  private String coverFrom;

  @JsonProperty("cover-to")
  private String coverTo;

  @JsonProperty("login-user-id")
  private String loginUserId;

  @JsonProperty("login-user-role")
  private String loginUserRole;
}

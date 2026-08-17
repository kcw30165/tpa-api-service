package com.bct.ngtpa.apiservice.adapter.out.apim.dto;

import com.fasterxml.jackson.annotation.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateNotificationReadStatusApimRequest {
    @JsonProperty("policy-no")
    private String policyNo;
    @JsonProperty("cert-no")
    private String certNo;
    @JsonProperty("login-user-id")
    private String loginUserId;
    @JsonProperty("login-user-role")
    private String loginUserRole;
    @JsonProperty("ref-date")
    private String refDate;
    @JsonProperty("env")
    private String accountEnv;
    @JsonProperty("msg-code-long")
    private String msgCodeLong;
}

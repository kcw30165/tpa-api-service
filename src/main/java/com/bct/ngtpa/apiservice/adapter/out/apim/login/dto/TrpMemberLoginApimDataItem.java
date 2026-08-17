package com.bct.ngtpa.apiservice.adapter.out.apim.login.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrpMemberLoginApimDataItem(
        @JsonProperty("success") boolean success,
        @JsonProperty("login-user-role") String loginUserRole,
        @JsonProperty("fail-count") Integer failCount,
        @JsonProperty("otp") List<TrpMemberLoginApimOtpItem> otp) { }

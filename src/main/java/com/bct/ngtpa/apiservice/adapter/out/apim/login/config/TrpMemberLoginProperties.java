package com.bct.ngtpa.apiservice.adapter.out.apim.login.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "apim.login.member-login")
public class TrpMemberLoginProperties {
  private String path = "/ws/NGTPA/v1/TRPMemberLogin";
  private String operationName = "TRPMemberLogin";
}

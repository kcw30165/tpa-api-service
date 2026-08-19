package com.bct.ngtpa.apiservice.adapter.out.security.login.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "auth-workflows.login.context-protection")
public class LoginPreAuthContextProtectionProperties {
  private boolean enabled = true;
  private String keyVersion;
  private String secret;
  private int destinationIdEntropyBytes = 24;
}

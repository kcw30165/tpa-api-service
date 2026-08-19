package com.bct.ngtpa.apiservice.adapter.out.security.forgotpassword;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth-workflows.forgot-password.context-protection")
public class ForgotPasswordAuthFlowContextProperties {
  private boolean enabled = true;
  private String keyVersion = "v1";
  private String secret;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getKeyVersion() {
    return keyVersion;
  }

  public void setKeyVersion(String keyVersion) {
    this.keyVersion = keyVersion;
  }

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public void validate() {
    if (!enabled) {
      throw new IllegalStateException(
          "auth-workflows.forgot-password.context-protection.enabled must remain true");
    }
    if (keyVersion == null || keyVersion.isBlank()) {
      throw new IllegalStateException(
          "auth-workflows.forgot-password.context-protection.key-version must not be blank");
    }
    if (secret == null || secret.length() < 32) {
      throw new IllegalStateException(
          "auth-workflows.forgot-password.context-protection.secret must contain at least 32 characters");
    }
  }
}

package com.bct.ngtpa.apiservice.adapter.out.apim.passwordvalidation;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "apim.password-validation")
public class PasswordValidationApimProperties {
  private String path;

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public void validate() {
    if (path == null || path.isBlank()) {
      throw new IllegalStateException("apim.password-validation.path must not be blank");
    }
    path = path.trim();
  }
}

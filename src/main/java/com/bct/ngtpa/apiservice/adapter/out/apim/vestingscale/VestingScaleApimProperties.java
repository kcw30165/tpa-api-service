package com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "apim.vesting-scale")
public class VestingScaleApimProperties {

  private String path;

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public void validate() {
    if (path == null || path.isBlank()) {
      throw new IllegalStateException("apim.vesting-scale.path must not be blank");
    }
    path = path.trim();
  }
}

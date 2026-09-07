package com.bct.ngtpa.apiservice.adapter.out.apim.accountgainloss;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "apim.account-gain-loss")
public class AccountGainLossApimProperties {
  private String path;

  public String getPath() {
    if (path == null || path.isBlank()) {
      throw new IllegalStateException("apim.account-gain-loss.path must not be blank");
    }
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }
}

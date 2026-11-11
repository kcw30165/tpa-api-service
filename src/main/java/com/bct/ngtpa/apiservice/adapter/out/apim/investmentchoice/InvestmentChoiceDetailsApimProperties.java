package com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Downstream path of TRPGetCIMDtl ({@code apim.investment-choice.details.path}). */
@ConfigurationProperties(prefix = "apim.investment-choice.details")
public class InvestmentChoiceDetailsApimProperties {

  private String path;

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public void validate() {
    if (path == null || path.isBlank()) {
      throw new IllegalStateException("apim.investment-choice.details.path must not be blank");
    }
    path = path.trim();
  }
}

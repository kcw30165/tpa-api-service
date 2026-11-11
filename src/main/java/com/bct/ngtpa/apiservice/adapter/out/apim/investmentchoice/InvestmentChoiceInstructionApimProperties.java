package com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Downstream path of TRPCIMReq ({@code apim.investment-choice.instruction.path}). */
@ConfigurationProperties(prefix = "apim.investment-choice.instruction")
public class InvestmentChoiceInstructionApimProperties {

  private String path;

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public void validate() {
    if (path == null || path.isBlank()) {
      throw new IllegalStateException("apim.investment-choice.instruction.path must not be blank");
    }
    path = path.trim();
  }
}

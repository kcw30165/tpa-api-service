package com.bct.ngtpa.apiservice.adapter.out.apim.fundfactsheet;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Downstream path of TRPGetFundFactSheet ({@code apim.fund-fact-sheet.path}). */
@ConfigurationProperties(prefix = "apim.fund-fact-sheet")
public class FundFactSheetApimProperties {

  private String path;

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public void validate() {
    if (path == null || path.isBlank()) {
      throw new IllegalStateException("apim.fund-fact-sheet.path must not be blank");
    }
    path = path.trim();
  }
}

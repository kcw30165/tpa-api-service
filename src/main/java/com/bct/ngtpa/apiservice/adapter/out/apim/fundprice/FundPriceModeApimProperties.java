package com.bct.ngtpa.apiservice.adapter.out.apim.fundprice;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "apim.fund-price-mode")
public class FundPriceModeApimProperties {

  private String path;

  public void validate() {
    if (path == null || path.isBlank()) {
      throw new IllegalStateException("apim.fund-price-mode.path must not be blank");
    }
    path = path.trim();
  }
}

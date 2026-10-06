package com.bct.ngtpa.apiservice.adapter.out.apim.fundprice;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TRPGetFundPrice's own dedicated downstream path property, confirmed (investigation script 096)
 * as the established per-operation pattern in this codebase: mirrors {@link
 * FundPriceModeApimProperties} exactly (a distinct class with its own prefix), rather than a
 * second field bolted onto the mode-specific class.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "apim.fund-price")
public class FundPriceApimProperties {

  private String path;

  public void validate() {
    if (path == null || path.isBlank()) {
      throw new IllegalStateException("apim.fund-price.path must not be blank");
    }
    path = path.trim();
  }
}

package com.bct.ngtpa.apiservice.adapter.out.apim.fundcomparison;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TRPGetFundCmpOpt downstream path configuration, mirroring {@code FundPriceModeApimProperties}'s
 * exact shape: a single {@code path} field bound under its own dedicated
 * {@code @ConfigurationProperties} prefix -- confirmed (script 038 investigation) to be the
 * universal, repo-wide pattern for every TRP operation's downstream path, not a Fund-Price-specific
 * convention.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "apim.fund-comparison-options")
public class FundComparisonOptionsApimProperties {

  private String path;

  public void validate() {
    if (path == null || path.isBlank()) {
      throw new IllegalStateException("apim.fund-comparison-options.path must not be blank");
    }
    path = path.trim();
  }
}

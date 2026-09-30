package com.bct.ngtpa.apiservice.application.usecase.fundprice;

import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceModeSessionMetadata;

/**
 * {@code metadata} is non-null only when {@code outcome} is {@link
 * FundPriceModeUseCaseOutcome#APPLIED}; {@link FundPriceModeUseCaseOutcome#SYSTEM_FAILURE} carries
 * no metadata.
 */
public record FundPriceModeUseCaseResult(
    FundPriceModeUseCaseOutcome outcome, FundPriceModeSessionMetadata metadata) {

  public FundPriceModeUseCaseResult {
    if (outcome == null) {
      throw new IllegalArgumentException("outcome must not be null");
    }
    boolean requiresMetadata = outcome == FundPriceModeUseCaseOutcome.APPLIED;
    if (requiresMetadata && metadata == null) {
      throw new IllegalArgumentException("APPLIED result must carry metadata");
    }
    if (!requiresMetadata && metadata != null) {
      throw new IllegalArgumentException("non-APPLIED result must not carry metadata");
    }
  }

  public static FundPriceModeUseCaseResult applied(FundPriceModeSessionMetadata metadata) {
    return new FundPriceModeUseCaseResult(FundPriceModeUseCaseOutcome.APPLIED, metadata);
  }

  public static FundPriceModeUseCaseResult systemFailure() {
    return new FundPriceModeUseCaseResult(FundPriceModeUseCaseOutcome.SYSTEM_FAILURE, null);
  }
}

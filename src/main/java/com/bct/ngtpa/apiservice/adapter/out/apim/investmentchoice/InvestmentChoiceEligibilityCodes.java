package com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice;

import java.util.Map;
import java.util.Optional;

final class InvestmentChoiceEligibilityCodes {

  private static final Map<String, String> FEATURE_CODES = Map.of();

  private InvestmentChoiceEligibilityCodes() {}

  static Optional<String> featureCode(String errMessage) {
    return Optional.ofNullable(FEATURE_CODES.get(errMessage));
  }
}

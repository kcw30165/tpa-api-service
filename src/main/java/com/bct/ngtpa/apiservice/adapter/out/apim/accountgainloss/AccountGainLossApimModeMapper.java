package com.bct.ngtpa.apiservice.adapter.out.apim.accountgainloss;

import com.bct.ngtpa.apiservice.application.dto.accountgainloss.AccountGainLossPeriod;

public final class AccountGainLossApimModeMapper {
  private AccountGainLossApimModeMapper() {}

  public static String toWireValue(AccountGainLossPeriod period) {
    if (period == null) {
      throw new IllegalArgumentException("period is required");
    }
    return switch (period) {
      case SINCE_SCHEME_INCEPTION -> "ssi";
      case YEAR_TO_DATE -> "ytd";
    };
  }
}

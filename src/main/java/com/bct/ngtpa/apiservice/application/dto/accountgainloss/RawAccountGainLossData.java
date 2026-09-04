package com.bct.ngtpa.apiservice.application.dto.accountgainloss;

import java.time.LocalDate;
import java.util.List;

public record RawAccountGainLossData(LocalDate priceDate, List<RawFundGainLoss> funds) {
  public RawAccountGainLossData {
    funds = funds == null ? null : List.copyOf(funds);
  }
}

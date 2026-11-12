package com.bct.ngtpa.apiservice.application.dto.vestingscale;

import java.math.BigDecimal;
import java.util.Objects;

public record VestingScaleRow(int completedYears, BigDecimal vestingPercentage) {

  public VestingScaleRow {
    Objects.requireNonNull(vestingPercentage, "vestingPercentage must not be null");
  }
}

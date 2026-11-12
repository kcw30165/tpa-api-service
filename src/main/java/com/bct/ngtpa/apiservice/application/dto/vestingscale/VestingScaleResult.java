package com.bct.ngtpa.apiservice.application.dto.vestingscale;

import java.util.List;
import java.util.Objects;

public record VestingScaleResult(
    VestingScaleMember member, List<VestingScalePlan> plans, boolean vestingScaleSectionVisible) {

  public VestingScaleResult {
    Objects.requireNonNull(member, "member must not be null");
    plans = List.copyOf(Objects.requireNonNull(plans, "plans must not be null"));
  }
}

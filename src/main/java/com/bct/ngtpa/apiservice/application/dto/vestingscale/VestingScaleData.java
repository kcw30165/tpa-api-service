package com.bct.ngtpa.apiservice.application.dto.vestingscale;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record VestingScaleData(
    VestingScaleMember member,
    List<VestingScalePlan> plans,
    Map<String, VestingScaleConfigValue> config) {

  public VestingScaleData {
    Objects.requireNonNull(member, "member must not be null");
    plans = List.copyOf(Objects.requireNonNull(plans, "plans must not be null"));
    config = Map.copyOf(Objects.requireNonNull(config, "config must not be null"));
  }
}

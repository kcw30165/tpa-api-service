package com.bct.ngtpa.apiservice.application.dto.vestingscale;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record VestingScalePlan(
    String planNo,
    String planTypeDescEng,
    String planTypeDescChi,
    Presence<LocalDate> planEffectiveDate,
    Presence<LocalDate> dateOfJoiningPlan,
    Presence<VestingScalePlanService> planService,
    Presence<String> vestPeriodBaseEng,
    Presence<String> vestPeriodBaseChi,
    Presence<String> basisContEeEng,
    Presence<String> basisContEeChi,
    Presence<String> basisContErEng,
    Presence<String> basisContErChi,
    Presence<List<VestingScaleRow>> vestingScale) {

  public VestingScalePlan {
    Objects.requireNonNull(planNo, "planNo must not be null");
    Objects.requireNonNull(planTypeDescEng, "planTypeDescEng must not be null");
    Objects.requireNonNull(planEffectiveDate, "planEffectiveDate must not be null");
    Objects.requireNonNull(dateOfJoiningPlan, "dateOfJoiningPlan must not be null");
    Objects.requireNonNull(planService, "planService must not be null");
    Objects.requireNonNull(vestPeriodBaseEng, "vestPeriodBaseEng must not be null");
    Objects.requireNonNull(vestPeriodBaseChi, "vestPeriodBaseChi must not be null");
    Objects.requireNonNull(basisContEeEng, "basisContEeEng must not be null");
    Objects.requireNonNull(basisContEeChi, "basisContEeChi must not be null");
    Objects.requireNonNull(basisContErEng, "basisContErEng must not be null");
    Objects.requireNonNull(basisContErChi, "basisContErChi must not be null");
    Objects.requireNonNull(vestingScale, "vestingScale must not be null");
    vestingScale =
        vestingScale.isPresent() && vestingScale.value() != null
            ? Presence.present(List.copyOf(vestingScale.value()))
            : vestingScale;
  }
}

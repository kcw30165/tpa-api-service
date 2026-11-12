package com.bct.ngtpa.apiservice.application.usecase.vestingscale;

import com.bct.ngtpa.apiservice.application.dto.vestingscale.Presence;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleConfigValue;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleData;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleMember;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScalePlan;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleResult;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class VestingScaleVisibilityPolicy {

  public VestingScaleResult apply(VestingScaleData data) {
    Objects.requireNonNull(data, "data must not be null");
    var config = data.config();
    boolean vestingScaleSectionVisible =
        config.get("vesting-scale") != VestingScaleConfigValue.HIDDEN;

    VestingScaleMember member =
        new VestingScaleMember(
            visible(data.member().memberCategory(), config, "member-category"),
            visible(data.member().dateJoinedCompany(), config, "date-joined-company"),
            visible(data.member().dateJoinedScheme(), config, "date-joined-scheme"),
            visible(data.member().employmentDate(), config, "employment-date"));
    List<VestingScalePlan> plans =
        data.plans().stream()
            .map(
                plan ->
                    new VestingScalePlan(
                        plan.planNo(),
                        plan.planTypeDescEng(),
                        plan.planTypeDescChi(),
                        visible(plan.planEffectiveDate(), config, "plan-effective-date"),
                        visible(plan.dateOfJoiningPlan(), config, "date-of-joining-plan"),
                        visible(plan.planService(), config, "plan-service"),
                        plan.vestPeriodBaseEng(),
                        plan.vestPeriodBaseChi(),
                        visible(plan.basisContEeEng(), config, "basis-cont-ee-eng"),
                        visible(plan.basisContEeChi(), config, "basis-cont-ee-chi"),
                        visible(plan.basisContErEng(), config, "basis-cont-er-eng"),
                        visible(plan.basisContErChi(), config, "basis-cont-er-chi"),
                        visible(plan.vestingScale(), config, "vesting-scale")))
            .toList();

    return new VestingScaleResult(member, plans, vestingScaleSectionVisible);
  }

  private static <T> Presence<T> visible(
      Presence<T> presence, Map<String, VestingScaleConfigValue> config, String itemId) {
    if (!presence.isPresent() || config.get(itemId) == VestingScaleConfigValue.HIDDEN) {
      return Presence.absent();
    }
    return presence;
  }
}

package com.bct.ngtpa.apiservice.application.usecase.vestingscale;

import com.bct.ngtpa.apiservice.application.dto.vestingscale.Presence;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleConfigItem;
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
        config.get(VestingScaleConfigItem.VESTING_SCALE) != VestingScaleConfigValue.HIDDEN;

    VestingScaleMember member =
        new VestingScaleMember(
            visible(data.member().memberCategory(), config, VestingScaleConfigItem.MEMBER_CATEGORY),
            visible(
                data.member().dateJoinedCompany(),
                config,
                VestingScaleConfigItem.DATE_JOINED_COMPANY),
            visible(
                data.member().dateJoinedScheme(),
                config,
                VestingScaleConfigItem.DATE_JOINED_SCHEME),
            visible(
                data.member().employmentDate(), config, VestingScaleConfigItem.EMPLOYMENT_DATE));
    List<VestingScalePlan> plans =
        data.plans().stream()
            .map(
                plan ->
                    new VestingScalePlan(
                        plan.planNo(),
                        plan.planTypeDescEng(),
                        plan.planTypeDescChi(),
                        visible(
                            plan.planEffectiveDate(),
                            config,
                            VestingScaleConfigItem.PLAN_EFFECTIVE_DATE),
                        visible(
                            plan.dateOfJoiningPlan(),
                            config,
                            VestingScaleConfigItem.DATE_OF_JOINING_PLAN),
                        visible(plan.planService(), config, VestingScaleConfigItem.PLAN_SERVICE),
                        plan.vestPeriodBaseEng(),
                        plan.vestPeriodBaseChi(),
                        visible(
                            plan.basisContEeEng(),
                            config,
                            VestingScaleConfigItem.BASIS_CONT_EE_ENG),
                        visible(
                            plan.basisContEeChi(),
                            config,
                            VestingScaleConfigItem.BASIS_CONT_EE_CHI),
                        visible(
                            plan.basisContErEng(),
                            config,
                            VestingScaleConfigItem.BASIS_CONT_ER_ENG),
                        visible(
                            plan.basisContErChi(),
                            config,
                            VestingScaleConfigItem.BASIS_CONT_ER_CHI),
                        visible(plan.vestingScale(), config, VestingScaleConfigItem.VESTING_SCALE)))
            .toList();

    return new VestingScaleResult(member, plans, vestingScaleSectionVisible);
  }

  private static <T> Presence<T> visible(
      Presence<T> presence,
      Map<VestingScaleConfigItem, VestingScaleConfigValue> config,
      VestingScaleConfigItem item) {
    if (!presence.isPresent() || config.get(item) == VestingScaleConfigValue.HIDDEN) {
      return Presence.absent();
    }
    return presence;
  }
}

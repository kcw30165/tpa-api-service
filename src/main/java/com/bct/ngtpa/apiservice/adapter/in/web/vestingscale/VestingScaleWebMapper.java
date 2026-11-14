package com.bct.ngtpa.apiservice.adapter.in.web.vestingscale;

import com.bct.ngtpa.apiservice.adapter.in.web.support.RequestLanguageResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response.OverviewMemberResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response.OverviewPlanResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response.OverviewResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response.PlanServiceResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response.PlanTypeOptionResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response.ResponseField;
import com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response.VestingScaleDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response.VestingScalePlanResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response.VestingScaleResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response.VestingScaleRowResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response.VestingScaleSectionResponse;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.Presence;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScalePlan;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleResult;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleRow;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public final class VestingScaleWebMapper {

  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT)
          .withResolverStyle(ResolverStyle.STRICT);

  public VestingScaleResultResponse toResultResponse(VestingScaleResult result, String language) {
    Objects.requireNonNull(result, "result must not be null");
    boolean chinese = RequestLanguageResolver.isZhHk(language);

    List<PlanTypeOptionResponse> planTypes =
        result.plans().stream()
            .map(
                plan ->
                    new PlanTypeOptionResponse(plan.planNo(), planTypeDescription(plan, chinese)))
            .toList();
    List<OverviewPlanResponse> overviewPlans =
        result.plans().stream().map(plan -> overviewPlan(plan, chinese)).toList();
    ResponseField<VestingScaleSectionResponse> vestingScale =
        result.vestingScaleSectionVisible()
            ? ResponseField.present(vestingScaleSection(result.plans()))
            : ResponseField.absent();

    return new VestingScaleResultResponse(
        new VestingScaleDataResponse(
            planTypes,
            new OverviewResponse(overviewMember(result.member()), overviewPlans),
            vestingScale));
  }

  private static OverviewMemberResponse overviewMember(
      com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleMember member) {
    return new OverviewMemberResponse(
        map(member.memberCategory()),
        mapDate(member.dateJoinedCompany()),
        mapDate(member.dateJoinedScheme()),
        mapDate(member.employmentDate()));
  }

  private static OverviewPlanResponse overviewPlan(VestingScalePlan plan, boolean chinese) {
    return new OverviewPlanResponse(
        plan.planNo(),
        mapDate(plan.planEffectiveDate()),
        mapDate(plan.dateOfJoiningPlan()),
        mapped(plan.planService(), value -> new PlanServiceResponse(value.years(), value.months())),
        map(chinese ? plan.vestPeriodBaseChi() : plan.vestPeriodBaseEng()),
        map(chinese ? plan.basisContEeChi() : plan.basisContEeEng()),
        map(chinese ? plan.basisContErChi() : plan.basisContErEng()));
  }

  private static VestingScaleSectionResponse vestingScaleSection(List<VestingScalePlan> plans) {
    return new VestingScaleSectionResponse(
        plans.stream()
            .filter(plan -> plan.vestingScale().isPresent())
            .map(
                plan ->
                    new VestingScalePlanResponse(
                        plan.planNo(),
                        mapped(
                            plan.vestingScale(),
                            rows ->
                                rows.stream()
                                    .sorted(
                                        Comparator.comparingInt(VestingScaleRow::completedYears))
                                    .map(VestingScaleWebMapper::vestingRow)
                                    .toList())))
            .toList());
  }

  private static VestingScaleRowResponse vestingRow(VestingScaleRow row) {
    return new VestingScaleRowResponse(row.completedYears(), row.vestingPercentage());
  }

  private static String planTypeDescription(VestingScalePlan plan, boolean chinese) {
    String selected = chinese ? plan.planTypeDescChi() : plan.planTypeDescEng();
    if (chinese && (selected == null || selected.isBlank()) && hasText(plan.planTypeDescEng())) {
      return plan.planTypeDescEng();
    }
    return selected;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static ResponseField<String> mapDate(Presence<LocalDate> presence) {
    return mapped(presence, DATE_FORMATTER::format);
  }

  private static <T> ResponseField<T> map(Presence<T> presence) {
    return presence.isPresent() ? ResponseField.present(presence.value()) : ResponseField.absent();
  }

  private static <T, R> ResponseField<R> mapped(Presence<T> presence, Function<T, R> mapper) {
    if (!presence.isPresent()) {
      return ResponseField.absent();
    }
    return presence.value() == null
        ? ResponseField.present(null)
        : ResponseField.present(mapper.apply(presence.value()));
  }
}

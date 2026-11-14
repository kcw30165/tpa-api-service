package com.bct.ngtpa.apiservice.adapter.in.web.vestingscale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response.OverviewPlanResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response.PlanTypeOptionResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response.ResponseField;
import com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response.VestingScalePlanResponse;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.Presence;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleMember;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScalePlan;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScalePlanService;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleResult;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class VestingScaleWebMapperTest {

  private final VestingScaleWebMapper mapper = new VestingScaleWebMapper();

  @Test
  void preservesDsPlanOrderAndSortsVestingRowsStably() {
    VestingScalePlan secondPlan =
        plan(
            "P2",
            "Plan two English",
            "計劃二",
            Presence.present(List.of(row(3, "30.0"), row(1, "10.0"), row(1, "11.0"))));
    VestingScalePlan hiddenPlan = plan("P1", "Plan one English", "計劃一", Presence.absent());
    VestingScalePlan thirdPlan =
        plan("P3", "Plan three English", "計劃三", Presence.present(List.of()));

    var response =
        mapper.toResultResponse(result(List.of(secondPlan, hiddenPlan, thirdPlan), true), "en");

    assertThat(response.data().planTypes())
        .containsExactly(
            new PlanTypeOptionResponse("P2", "Plan two English"),
            new PlanTypeOptionResponse("P1", "Plan one English"),
            new PlanTypeOptionResponse("P3", "Plan three English"));
    assertThat(response.data().overview().plans())
        .extracting(OverviewPlanResponse::planNo)
        .containsExactly("P2", "P1", "P3");
    assertThat(response.data().vestingScale().present()).isTrue();
    assertThat(response.data().vestingScale().value().plans())
        .extracting(VestingScalePlanResponse::planNo)
        .containsExactly("P2", "P3");
    assertThat(response.data().vestingScale().value().plans().getFirst().scales().value())
        .containsExactly(rowResponse(1, "10.0"), rowResponse(1, "11.0"), rowResponse(3, "30.0"));
    assertThat(response.data().vestingScale().value().plans().get(1).scales().value()).isEmpty();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"en", "fr-FR"})
  void missingAndUnsupportedLanguagesSelectEnglish(String language) {
    VestingScalePlan plan = plan("P1", "English name", "中文名稱", Presence.absent());

    var response = mapper.toResultResponse(result(List.of(plan), false), language);

    assertThat(response.data().planTypes().getFirst().planType()).isEqualTo("English name");
    assertThat(response.data().overview().plans().getFirst().vestingPeriodBasedOn())
        .isEqualTo("English vesting basis");
  }

  @ParameterizedTest
  @ValueSource(strings = {"zh-HK", "zh"})
  void chineseLanguagesSelectChineseDescriptions(String language) {
    VestingScalePlan plan = plan("P1", "English name", "中文名稱", Presence.absent());

    var response = mapper.toResultResponse(result(List.of(plan), false), language);

    assertThat(response.data().planTypes().getFirst().planType()).isEqualTo("中文名稱");
    assertThat(response.data().overview().plans().getFirst().vestingPeriodBasedOn())
        .isEqualTo("中文歸屬基準");
  }

  @Test
  void requiresEnglishPlanTypeInApplicationPlan() {
    assertThatThrownBy(() -> plan("P1", null, "中文名稱", Presence.absent()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("planTypeDescEng must not be null");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " ", "  "})
  void fallsBackToEnglishPlanTypeWhenChineseDescriptionIsMissingOrBlank(String chineseName) {
    VestingScalePlan plan = plan("P1", "English name", chineseName, Presence.absent());

    var response = mapper.toResultResponse(result(List.of(plan), false), "zh-HK");

    assertThat(response.data().planTypes().getFirst().planType()).isEqualTo("English name");
    assertThat(response.data().overview().plans().getFirst().planNo()).isEqualTo("P1");
  }

  @Test
  void preservesNullSelectedLanguageValuesWithoutCrossLanguageFallback() {
    VestingScalePlan plan =
        new VestingScalePlan(
            "P1",
            "English name",
            null,
            Presence.absent(),
            Presence.absent(),
            Presence.absent(),
            Presence.present("English vesting basis"),
            Presence.present(null),
            Presence.present("English employee basis"),
            Presence.present(null),
            Presence.present("English employer basis"),
            Presence.present(null),
            Presence.absent());

    OverviewPlanResponse mapped =
        mapper
            .toResultResponse(result(List.of(plan), false), "zh-HK")
            .data()
            .overview()
            .plans()
            .getFirst();

    assertThat(mapped.vestingPeriodBasedOn()).isNull();
    assertThat(mapped.memberContributionBasis()).isEqualTo(ResponseField.present(null));
    assertThat(mapped.employerContributionBasis()).isEqualTo(ResponseField.present(null));
  }

  @Test
  void selectedLanguagePresenceDoesNotFallBackToTheOtherLanguage() {
    VestingScalePlan plan =
        new VestingScalePlan(
            "P1",
            "English name",
            "中文名稱",
            Presence.absent(),
            Presence.present(null),
            Presence.absent(),
            Presence.present("English vesting basis"),
            Presence.present("中文歸屬基準"),
            Presence.absent(),
            Presence.present("中文僱員供款基準"),
            Presence.present("English employer basis"),
            Presence.present(null),
            Presence.absent());

    var response = mapper.toResultResponse(result(List.of(plan), false), "zh-HK");
    OverviewPlanResponse mappedPlan = response.data().overview().plans().getFirst();

    assertThat(mappedPlan.planEffectiveDate().present()).isFalse();
    assertThat(mappedPlan.dateOfJoiningPlan()).isEqualTo(ResponseField.present(null));
    assertThat(mappedPlan.planService().present()).isFalse();
    assertThat(mappedPlan.memberContributionBasis()).isEqualTo(ResponseField.present("中文僱員供款基準"));
    assertThat(mappedPlan.employerContributionBasis()).isEqualTo(ResponseField.present(null));
    assertThat(mappedPlan.vestingPeriodBasedOn()).isEqualTo("中文歸屬基準");
  }

  @Test
  void formatsDatesAndPreservesAbsentNullAndValueStates() {
    VestingScaleMember member =
        new VestingScaleMember(
            Presence.absent(),
            Presence.present(null),
            Presence.present(LocalDate.of(1995, 10, 1)),
            Presence.absent());
    VestingScalePlan plan =
        new VestingScalePlan(
            "P1",
            "English name",
            "中文名稱",
            Presence.absent(),
            Presence.present(null),
            Presence.present(new VestingScalePlanService(2, 3)),
            Presence.present("English vesting basis"),
            Presence.present("中文歸屬基準"),
            Presence.absent(),
            Presence.present("中文僱員供款基準"),
            Presence.present(null),
            Presence.present("中文僱主供款基準"),
            Presence.absent());

    var response =
        mapper.toResultResponse(new VestingScaleResult(member, List.of(plan), false), "en");

    assertThat(response.data().overview().member().memberCategory().present()).isFalse();
    assertThat(response.data().overview().member().dateJoinedCompany())
        .isEqualTo(ResponseField.present(null));
    assertThat(response.data().overview().member().dateJoinedScheme())
        .isEqualTo(ResponseField.present("01/10/1995"));
    assertThat(response.data().overview().member().employmentDate().present()).isFalse();
    OverviewPlanResponse mappedPlan = response.data().overview().plans().getFirst();
    assertThat(mappedPlan.planEffectiveDate().present()).isFalse();
    assertThat(mappedPlan.dateOfJoiningPlan()).isEqualTo(ResponseField.present(null));
    assertThat(mappedPlan.planService().value())
        .isEqualTo(
            new com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response.PlanServiceResponse(
                2, 3));
    assertThat(mappedPlan.memberContributionBasis().present()).isFalse();
    assertThat(mappedPlan.employerContributionBasis()).isEqualTo(ResponseField.present(null));
    assertThat(response.data().vestingScale().present()).isFalse();
  }

  @Test
  void emptyPlansAndAllAbsentMemberRemainPresentAsEmptyStructures() {
    var response =
        mapper.toResultResponse(
            new VestingScaleResult(
                new VestingScaleMember(
                    Presence.absent(), Presence.absent(), Presence.absent(), Presence.absent()),
                List.of(),
                true),
            "en");

    assertThat(response.data().planTypes()).isEmpty();
    assertThat(response.data().overview().plans()).isEmpty();
    assertThat(response.data().overview().member().memberCategory().present()).isFalse();
    assertThat(response.data().overview().member().dateJoinedCompany().present()).isFalse();
    assertThat(response.data().vestingScale().present()).isTrue();
    assertThat(response.data().vestingScale().value().plans()).isEmpty();
  }

  private static VestingScaleResult result(List<VestingScalePlan> plans, boolean sectionVisible) {
    return new VestingScaleResult(
        new VestingScaleMember(
            Presence.present("ACTIVE"),
            Presence.present(LocalDate.of(1995, 10, 1)),
            Presence.present(null),
            Presence.absent()),
        plans,
        sectionVisible);
  }

  private static VestingScalePlan plan(
      String planNo,
      String englishName,
      String chineseName,
      Presence<List<VestingScaleRow>> scales) {
    return new VestingScalePlan(
        planNo,
        englishName,
        chineseName,
        Presence.present(LocalDate.of(2019, 2, 1)),
        Presence.present(null),
        Presence.present(new VestingScalePlanService(2, 3)),
        Presence.present("English vesting basis"),
        Presence.present("中文歸屬基準"),
        Presence.present("English employee basis"),
        Presence.present("中文僱員供款基準"),
        Presence.present("English employer basis"),
        Presence.present("中文僱主供款基準"),
        scales);
  }

  private static VestingScaleRow row(int years, String percentage) {
    return new VestingScaleRow(years, new BigDecimal(percentage));
  }

  private static com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response
          .VestingScaleRowResponse
      rowResponse(int years, String percentage) {
    return new com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response
        .VestingScaleRowResponse(years, new BigDecimal(percentage));
  }
}

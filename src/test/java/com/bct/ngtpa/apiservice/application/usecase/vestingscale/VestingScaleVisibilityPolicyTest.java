package com.bct.ngtpa.apiservice.application.usecase.vestingscale;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.vestingscale.Presence;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleConfigValue;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleData;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleMember;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScalePlan;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScalePlanService;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleResult;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class VestingScaleVisibilityPolicyTest {

  private static final LocalDate DATE = LocalDate.of(2020, 1, 2);
  private static final VestingScalePlanService PLAN_SERVICE = new VestingScalePlanService(2, 3);
  private static final List<VestingScaleRow> ROWS =
      List.of(new VestingScaleRow(1, new BigDecimal("25")));

  private final VestingScaleVisibilityPolicy policy = new VestingScaleVisibilityPolicy();

  @ParameterizedTest(name = "{0} with {1}")
  @MethodSource("fieldScenarios")
  void appliesPresenceAndVisibilityRulesToEveryConfigurableField(Field field, Scenario scenario) {
    var config =
        scenario.configValue == null
            ? Map.<String, VestingScaleConfigValue>of()
            : Map.of(field.itemId, scenario.configValue);
    var result = policy.apply(source(field, scenario, config));

    assertThat(valueAt(result, field))
        .as("%s with %s", field.itemId, scenario)
        .isEqualTo(expected(field, scenario));
  }

  @Test
  void appliesLanguagePairConfigurationIndependently() {
    var source = sourceWithAllBasisValues();

    var englishHidden =
        policy.apply(
            new VestingScaleData(
                source.member(),
                source.plans(),
                Map.of(
                    "basis-cont-ee-eng", VestingScaleConfigValue.HIDDEN,
                    "basis-cont-ee-chi", VestingScaleConfigValue.READONLY,
                    "basis-cont-er-eng", VestingScaleConfigValue.READONLY,
                    "basis-cont-er-chi", VestingScaleConfigValue.HIDDEN)));
    assertThat(englishHidden.plans().getFirst().basisContEeEng()).isEqualTo(Presence.absent());
    assertThat(englishHidden.plans().getFirst().basisContEeChi())
        .isEqualTo(Presence.present("ee-chi"));
    assertThat(englishHidden.plans().getFirst().basisContErEng())
        .isEqualTo(Presence.present("er-eng"));
    assertThat(englishHidden.plans().getFirst().basisContErChi()).isEqualTo(Presence.absent());

    var chineseHidden =
        policy.apply(
            new VestingScaleData(
                source.member(),
                source.plans(),
                Map.of(
                    "basis-cont-ee-eng", VestingScaleConfigValue.READONLY,
                    "basis-cont-ee-chi", VestingScaleConfigValue.HIDDEN,
                    "basis-cont-er-eng", VestingScaleConfigValue.HIDDEN,
                    "basis-cont-er-chi", VestingScaleConfigValue.READONLY)));
    assertThat(chineseHidden.plans().getFirst().basisContEeEng())
        .isEqualTo(Presence.present("ee-eng"));
    assertThat(chineseHidden.plans().getFirst().basisContEeChi()).isEqualTo(Presence.absent());
    assertThat(chineseHidden.plans().getFirst().basisContErEng()).isEqualTo(Presence.absent());
    assertThat(chineseHidden.plans().getFirst().basisContErChi())
        .isEqualTo(Presence.present("er-chi"));
  }

  @Test
  void hiddenVestingScaleMarksWholeSectionForOmission() {
    var result =
        policy.apply(
            source(
                Field.VESTING_SCALE,
                Scenario.VALUE_HIDDEN,
                Map.of("vesting-scale", VestingScaleConfigValue.HIDDEN)));

    assertThat(result.vestingScaleSectionVisible()).isFalse();
    assertThat(result.plans().getFirst().vestingScale()).isEqualTo(Presence.absent());
  }

  @Test
  void emptyConfigPreservesValuesAndKeepsVestingScaleSectionVisible() {
    var source = sourceWithAllBasisValues();
    var result = policy.apply(new VestingScaleData(source.member(), source.plans(), Map.of()));

    assertThat(result.member()).isEqualTo(source.member());
    assertThat(result.plans()).isEqualTo(source.plans());
    assertThat(result.vestingScaleSectionVisible()).isTrue();
  }

  @Test
  void nonConfigurablePlanFieldsRemainUnchanged() {
    var source = sourceWithAllBasisValues();
    var result =
        policy.apply(
            new VestingScaleData(
                source.member(),
                source.plans(),
                Map.of(
                    "plan-no", VestingScaleConfigValue.HIDDEN,
                    "plan-type-desc-eng", VestingScaleConfigValue.HIDDEN,
                    "plan-type-desc-chi", VestingScaleConfigValue.HIDDEN,
                    "vest-period-base-eng", VestingScaleConfigValue.HIDDEN,
                    "vest-period-base-chi", VestingScaleConfigValue.HIDDEN)));

    assertThat(result.plans().getFirst().planNo()).isEqualTo("plan-no");
    assertThat(result.plans().getFirst().planTypeDescEng()).isEqualTo("plan-type-eng");
    assertThat(result.plans().getFirst().planTypeDescChi()).isEqualTo("plan-type-chi");
    assertThat(result.plans().getFirst().vestPeriodBaseEng())
        .isEqualTo(Presence.present("base-eng"));
    assertThat(result.plans().getFirst().vestPeriodBaseChi())
        .isEqualTo(Presence.present("base-chi"));
  }

  @Test
  void memberWithAllFieldsAbsentRemainsAbsent() {
    var result =
        policy.apply(
            new VestingScaleData(
                new VestingScaleMember(
                    Presence.absent(), Presence.absent(), Presence.absent(), Presence.absent()),
                List.of(),
                Map.of()));

    assertThat(result.member())
        .isEqualTo(
            new VestingScaleMember(
                Presence.absent(), Presence.absent(), Presence.absent(), Presence.absent()));
  }

  @Test
  void emptyPlanListRemainsEmpty() {
    var result =
        policy.apply(
            new VestingScaleData(
                new VestingScaleMember(
                    Presence.absent(), Presence.absent(), Presence.absent(), Presence.absent()),
                List.of(),
                Map.of()));

    assertThat(result.plans()).isEmpty();
  }

  private static Stream<Arguments> fieldScenarios() {
    return Stream.of(Field.values())
        .flatMap(
            field -> Stream.of(Scenario.values()).map(scenario -> Arguments.of(field, scenario)));
  }

  private static VestingScaleData source(
      Field target, Scenario scenario, Map<String, VestingScaleConfigValue> config) {
    var member =
        new VestingScaleMember(
            presence(Field.MEMBER_CATEGORY, target, scenario, "member"),
            presence(Field.DATE_JOINED_COMPANY, target, scenario, DATE),
            presence(Field.DATE_JOINED_SCHEME, target, scenario, DATE),
            presence(Field.EMPLOYMENT_DATE, target, scenario, DATE));
    var plan =
        new VestingScalePlan(
            "plan-no",
            "plan-type-eng",
            "plan-type-chi",
            presence(Field.PLAN_EFFECTIVE_DATE, target, scenario, DATE),
            presence(Field.DATE_OF_JOINING_PLAN, target, scenario, DATE),
            presence(Field.PLAN_SERVICE, target, scenario, PLAN_SERVICE),
            Presence.present("base-eng"),
            Presence.present("base-chi"),
            presence(Field.BASIS_CONT_EE_ENG, target, scenario, "ee-eng"),
            presence(Field.BASIS_CONT_EE_CHI, target, scenario, "ee-chi"),
            presence(Field.BASIS_CONT_ER_ENG, target, scenario, "er-eng"),
            presence(Field.BASIS_CONT_ER_CHI, target, scenario, "er-chi"),
            presence(Field.VESTING_SCALE, target, scenario, ROWS));
    return new VestingScaleData(member, List.of(plan), config);
  }

  private static VestingScaleData sourceWithAllBasisValues() {
    return new VestingScaleData(
        new VestingScaleMember(
            Presence.present("member"),
            Presence.present(DATE),
            Presence.present(DATE),
            Presence.present(DATE)),
        List.of(
            new VestingScalePlan(
                "plan-no",
                "plan-type-eng",
                "plan-type-chi",
                Presence.present(DATE),
                Presence.present(DATE),
                Presence.present(PLAN_SERVICE),
                Presence.present("base-eng"),
                Presence.present("base-chi"),
                Presence.present("ee-eng"),
                Presence.present("ee-chi"),
                Presence.present("er-eng"),
                Presence.present("er-chi"),
                Presence.present(ROWS))),
        Map.of());
  }

  private static <T> Presence<T> presence(Field field, Field target, Scenario scenario, T value) {
    if (field != target || scenario.sourceAbsent) {
      return Presence.absent();
    }
    return scenario.sourceNull ? Presence.present(null) : Presence.present(value);
  }

  private static Presence<?> valueAt(VestingScaleResult result, Field field) {
    return switch (field) {
      case MEMBER_CATEGORY -> result.member().memberCategory();
      case DATE_JOINED_COMPANY -> result.member().dateJoinedCompany();
      case DATE_JOINED_SCHEME -> result.member().dateJoinedScheme();
      case EMPLOYMENT_DATE -> result.member().employmentDate();
      case PLAN_EFFECTIVE_DATE -> result.plans().getFirst().planEffectiveDate();
      case DATE_OF_JOINING_PLAN -> result.plans().getFirst().dateOfJoiningPlan();
      case PLAN_SERVICE -> result.plans().getFirst().planService();
      case BASIS_CONT_EE_ENG -> result.plans().getFirst().basisContEeEng();
      case BASIS_CONT_EE_CHI -> result.plans().getFirst().basisContEeChi();
      case BASIS_CONT_ER_ENG -> result.plans().getFirst().basisContErEng();
      case BASIS_CONT_ER_CHI -> result.plans().getFirst().basisContErChi();
      case VESTING_SCALE -> result.plans().getFirst().vestingScale();
    };
  }

  private static Presence<?> expected(Field field, Scenario scenario) {
    if (scenario.sourceAbsent || scenario.configValue == VestingScaleConfigValue.HIDDEN) {
      return Presence.absent();
    }
    if (scenario.sourceNull) {
      return Presence.present(null);
    }
    return switch (field) {
      case MEMBER_CATEGORY -> Presence.present("member");
      case DATE_JOINED_COMPANY,
          DATE_JOINED_SCHEME,
          EMPLOYMENT_DATE,
          PLAN_EFFECTIVE_DATE,
          DATE_OF_JOINING_PLAN ->
          Presence.present(DATE);
      case PLAN_SERVICE -> Presence.present(PLAN_SERVICE);
      case BASIS_CONT_EE_ENG -> Presence.present("ee-eng");
      case BASIS_CONT_EE_CHI -> Presence.present("ee-chi");
      case BASIS_CONT_ER_ENG -> Presence.present("er-eng");
      case BASIS_CONT_ER_CHI -> Presence.present("er-chi");
      case VESTING_SCALE -> Presence.present(ROWS);
    };
  }

  private enum Field {
    MEMBER_CATEGORY("member-category"),
    DATE_JOINED_COMPANY("date-joined-company"),
    DATE_JOINED_SCHEME("date-joined-scheme"),
    EMPLOYMENT_DATE("employment-date"),
    PLAN_EFFECTIVE_DATE("plan-effective-date"),
    DATE_OF_JOINING_PLAN("date-of-joining-plan"),
    PLAN_SERVICE("plan-service"),
    VESTING_SCALE("vesting-scale"),
    BASIS_CONT_EE_ENG("basis-cont-ee-eng"),
    BASIS_CONT_EE_CHI("basis-cont-ee-chi"),
    BASIS_CONT_ER_ENG("basis-cont-er-eng"),
    BASIS_CONT_ER_CHI("basis-cont-er-chi");

    private final String itemId;

    Field(String itemId) {
      this.itemId = itemId;
    }
  }

  private enum Scenario {
    ABSENT_READONLY(true, false, VestingScaleConfigValue.READONLY),
    ABSENT_HIDDEN(true, false, VestingScaleConfigValue.HIDDEN),
    NULL_HIDDEN(false, true, VestingScaleConfigValue.HIDDEN),
    VALUE_HIDDEN(false, false, VestingScaleConfigValue.HIDDEN),
    VALUE_READONLY(false, false, VestingScaleConfigValue.READONLY),
    VALUE_NO_ENTRY(false, false, null),
    NULL_NO_ENTRY(false, true, null);

    private final boolean sourceAbsent;
    private final boolean sourceNull;
    private final VestingScaleConfigValue configValue;

    Scenario(boolean sourceAbsent, boolean sourceNull, VestingScaleConfigValue configValue) {
      this.sourceAbsent = sourceAbsent;
      this.sourceNull = sourceNull;
      this.configValue = configValue;
    }
  }
}

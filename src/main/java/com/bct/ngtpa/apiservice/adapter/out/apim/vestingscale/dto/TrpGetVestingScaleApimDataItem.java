package com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TrpGetVestingScaleApimDataItem(
    @JsonProperty(MEMBER_KEY) ApimField<Member> member,
    @JsonProperty(PLANS_KEY) ApimField<List<Plan>> plans,
    @JsonProperty(CONFIG_KEY) ApimField<List<ConfigItem>> config) {

  public static final String RESPONSE_KEY = "response";
  public static final String ERR_MESSAGE_KEY = "err-message";
  public static final String DATA_KEY = "data";
  public static final String MEMBER_KEY = "member";
  public static final String PLANS_KEY = "plans";
  public static final String CONFIG_KEY = "config";

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Member(
      @JsonProperty(MEMBER_CATEGORY_KEY) ApimField<String> memberCategory,
      @JsonProperty(DATE_JOINED_COMPANY_KEY) ApimField<String> dateJoinedCompany,
      @JsonProperty(DATE_JOINED_SCHEME_KEY) ApimField<String> dateJoinedScheme,
      @JsonProperty(EMPLOYMENT_DATE_KEY) ApimField<String> employmentDate) {

    public static final String MEMBER_CATEGORY_KEY = "member-category";
    public static final String DATE_JOINED_COMPANY_KEY = "date-joined-company";
    public static final String DATE_JOINED_SCHEME_KEY = "date-joined-scheme";
    public static final String EMPLOYMENT_DATE_KEY = "employment-date";
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Plan(
      @JsonProperty(PLAN_NO_KEY) ApimField<String> planNo,
      @JsonProperty(PLAN_TYPE_DESC_ENG_KEY) ApimField<String> planTypeDescEng,
      @JsonProperty(PLAN_TYPE_DESC_CHI_KEY) ApimField<String> planTypeDescChi,
      @JsonProperty(PLAN_EFFECTIVE_DATE_KEY) ApimField<String> planEffectiveDate,
      @JsonProperty(DATE_OF_JOINING_PLAN_KEY) ApimField<String> dateOfJoiningPlan,
      @JsonProperty(PLAN_SERVICE_KEY) ApimField<PlanService> planService,
      @JsonProperty(VEST_PERIOD_BASE_ENG_KEY) ApimField<String> vestPeriodBaseEng,
      @JsonProperty(VEST_PERIOD_BASE_CHI_KEY) ApimField<String> vestPeriodBaseChi,
      @JsonProperty(BASIS_CONT_EE_ENG_KEY) ApimField<String> basisContEeEng,
      @JsonProperty(BASIS_CONT_EE_CHI_KEY) ApimField<String> basisContEeChi,
      @JsonProperty(BASIS_CONT_ER_ENG_KEY) ApimField<String> basisContErEng,
      @JsonProperty(BASIS_CONT_ER_CHI_KEY) ApimField<String> basisContErChi,
      @JsonProperty(VESTING_SCALE_KEY) ApimField<List<VestingRow>> vestingScale) {

    public static final String PLAN_NO_KEY = "plan-no";
    public static final String PLAN_TYPE_DESC_ENG_KEY = "plan-type-desc-eng";
    public static final String PLAN_TYPE_DESC_CHI_KEY = "plan-type-desc-chi";
    public static final String PLAN_EFFECTIVE_DATE_KEY = "plan-effective-date";
    public static final String DATE_OF_JOINING_PLAN_KEY = "date-of-joining-plan";
    public static final String PLAN_SERVICE_KEY = "plan-service";
    public static final String VEST_PERIOD_BASE_ENG_KEY = "vest-period-base-eng";
    public static final String VEST_PERIOD_BASE_CHI_KEY = "vest-period-base-chi";
    public static final String BASIS_CONT_EE_ENG_KEY = "basis-cont-ee-eng";
    public static final String BASIS_CONT_EE_CHI_KEY = "basis-cont-ee-chi";
    public static final String BASIS_CONT_ER_ENG_KEY = "basis-cont-er-eng";
    public static final String BASIS_CONT_ER_CHI_KEY = "basis-cont-er-chi";
    public static final String VESTING_SCALE_KEY = "vesting-scale";
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ConfigItem(
      @JsonProperty(ITEM_ID_KEY) ApimField<String> itemId,
      @JsonProperty(CONFIG_VALUE_KEY) ApimField<String> configValue) {

    public static final String ITEM_ID_KEY = "item-id";
    public static final String CONFIG_VALUE_KEY = "config-value";
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PlanService(
      @JsonProperty(YEARS_KEY) ApimField<Integer> years,
      @JsonProperty(MONTHS_KEY) ApimField<Integer> months) {

    public static final String YEARS_KEY = "years";
    public static final String MONTHS_KEY = "months";
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record VestingRow(
      @JsonProperty(COMPLETED_YEARS_KEY) ApimField<Integer> completedYears,
      @JsonProperty(VEST_PCNT_KEY) ApimField<BigDecimal> vestPcnt) {

    public static final String COMPLETED_YEARS_KEY = "completed-years";
    public static final String VEST_PCNT_KEY = "vest-pcnt";
  }
}

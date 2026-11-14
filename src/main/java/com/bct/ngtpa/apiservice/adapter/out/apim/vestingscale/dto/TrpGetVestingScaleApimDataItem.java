package com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TrpGetVestingScaleApimDataItem(
    @JsonProperty(MEMBER) ApimField<Member> member,
    @JsonProperty(PLANS) ApimField<List<Plan>> plans,
    @JsonProperty(CONFIG) ApimField<List<ConfigItem>> config) {

  public static final String RESPONSE = "response";
  public static final String ERR_MESSAGE = "err-message";
  public static final String DATA = "data";
  public static final String MEMBER = "member";
  public static final String PLANS = "plans";
  public static final String CONFIG = "config";

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Member(
      @JsonProperty(MEMBER_CATEGORY) ApimField<String> memberCategory,
      @JsonProperty(DATE_JOINED_COMPANY) ApimField<String> dateJoinedCompany,
      @JsonProperty(DATE_JOINED_SCHEME) ApimField<String> dateJoinedScheme,
      @JsonProperty(EMPLOYMENT_DATE) ApimField<String> employmentDate) {

    public static final String MEMBER_CATEGORY = "member-category";
    public static final String DATE_JOINED_COMPANY = "date-joined-company";
    public static final String DATE_JOINED_SCHEME = "date-joined-scheme";
    public static final String EMPLOYMENT_DATE = "employment-date";
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Plan(
      @JsonProperty(PLAN_NO) ApimField<String> planNo,
      @JsonProperty(PLAN_TYPE_DESC_ENG) ApimField<String> planTypeDescEng,
      @JsonProperty(PLAN_TYPE_DESC_CHI) ApimField<String> planTypeDescChi,
      @JsonProperty(PLAN_EFFECTIVE_DATE) ApimField<String> planEffectiveDate,
      @JsonProperty(DATE_OF_JOINING_PLAN) ApimField<String> dateOfJoiningPlan,
      @JsonProperty(PLAN_SERVICE) ApimField<PlanService> planService,
      @JsonProperty(VEST_PERIOD_BASE_ENG) ApimField<String> vestPeriodBaseEng,
      @JsonProperty(VEST_PERIOD_BASE_CHI) ApimField<String> vestPeriodBaseChi,
      @JsonProperty(BASIS_CONT_EE_ENG) ApimField<String> basisContEeEng,
      @JsonProperty(BASIS_CONT_EE_CHI) ApimField<String> basisContEeChi,
      @JsonProperty(BASIS_CONT_ER_ENG) ApimField<String> basisContErEng,
      @JsonProperty(BASIS_CONT_ER_CHI) ApimField<String> basisContErChi,
      @JsonProperty(VESTING_SCALE) ApimField<List<VestingRow>> vestingScale) {

    public static final String PLAN_NO = "plan-no";
    public static final String PLAN_TYPE_DESC_ENG = "plan-type-desc-eng";
    public static final String PLAN_TYPE_DESC_CHI = "plan-type-desc-chi";
    public static final String PLAN_EFFECTIVE_DATE = "plan-effective-date";
    public static final String DATE_OF_JOINING_PLAN = "date-of-joining-plan";
    public static final String PLAN_SERVICE = "plan-service";
    public static final String VEST_PERIOD_BASE_ENG = "vest-period-base-eng";
    public static final String VEST_PERIOD_BASE_CHI = "vest-period-base-chi";
    public static final String BASIS_CONT_EE_ENG = "basis-cont-ee-eng";
    public static final String BASIS_CONT_EE_CHI = "basis-cont-ee-chi";
    public static final String BASIS_CONT_ER_ENG = "basis-cont-er-eng";
    public static final String BASIS_CONT_ER_CHI = "basis-cont-er-chi";
    public static final String VESTING_SCALE = "vesting-scale";
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ConfigItem(
      @JsonProperty(ITEM_ID) ApimField<String> itemId,
      @JsonProperty(CONFIG_VALUE) ApimField<String> configValue) {

    public static final String ITEM_ID = "item-id";
    public static final String CONFIG_VALUE = "config-value";
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PlanService(
      @JsonProperty(YEARS) ApimField<Integer> years,
      @JsonProperty(MONTHS) ApimField<Integer> months) {

    public static final String YEARS = "years";
    public static final String MONTHS = "months";
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record VestingRow(
      @JsonProperty(COMPLETED_YEARS) ApimField<Integer> completedYears,
      @JsonProperty(VEST_PCNT) ApimField<BigDecimal> vestPcnt) {

    public static final String COMPLETED_YEARS = "completed-years";
    public static final String VEST_PCNT = "vest-pcnt";
  }
}

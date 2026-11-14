package com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale;

import static com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem.CONFIG;
import static com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem.ConfigItem.CONFIG_VALUE;
import static com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem.ConfigItem.ITEM_ID;
import static com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem.MEMBER;
import static com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem.Member.DATE_JOINED_COMPANY;
import static com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem.Member.DATE_JOINED_SCHEME;
import static com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem.Member.EMPLOYMENT_DATE;
import static com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem.Member.MEMBER_CATEGORY;
import static com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem.PLANS;
import static com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem.Plan.BASIS_CONT_EE_CHI;
import static com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem.Plan.BASIS_CONT_EE_ENG;
import static com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem.Plan.BASIS_CONT_ER_CHI;
import static com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem.Plan.BASIS_CONT_ER_ENG;
import static com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem.Plan.DATE_OF_JOINING_PLAN;
import static com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem.Plan.PLAN_EFFECTIVE_DATE;
import static com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem.Plan.PLAN_NO;
import static com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem.Plan.PLAN_SERVICE;
import static com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem.Plan.PLAN_TYPE_DESC_ENG;
import static com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem.Plan.VESTING_SCALE;
import static com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem.PlanService.MONTHS;
import static com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem.PlanService.YEARS;
import static com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem.VestingRow.COMPLETED_YEARS;
import static com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem.VestingRow.VEST_PCNT;

import com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.ApimField;
import com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem.ConfigItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem.Member;
import com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem.Plan;
import com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem.PlanService;
import com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem.VestingRow;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.Presence;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleConfigItem;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleConfigValue;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleData;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleMember;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScalePlan;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScalePlanService;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class TrpGetVestingScaleApimMapper {

  private static final Logger LOG = LoggerFactory.getLogger(TrpGetVestingScaleApimMapper.class);
  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
  private static final Map<String, VestingScaleConfigItem> CONFIG_ITEMS_BY_ID =
      Map.ofEntries(
          Map.entry(MEMBER_CATEGORY, VestingScaleConfigItem.MEMBER_CATEGORY),
          Map.entry(DATE_JOINED_COMPANY, VestingScaleConfigItem.DATE_JOINED_COMPANY),
          Map.entry(DATE_JOINED_SCHEME, VestingScaleConfigItem.DATE_JOINED_SCHEME),
          Map.entry(EMPLOYMENT_DATE, VestingScaleConfigItem.EMPLOYMENT_DATE),
          Map.entry(PLAN_EFFECTIVE_DATE, VestingScaleConfigItem.PLAN_EFFECTIVE_DATE),
          Map.entry(DATE_OF_JOINING_PLAN, VestingScaleConfigItem.DATE_OF_JOINING_PLAN),
          Map.entry(PLAN_SERVICE, VestingScaleConfigItem.PLAN_SERVICE),
          Map.entry(VESTING_SCALE, VestingScaleConfigItem.VESTING_SCALE),
          Map.entry(BASIS_CONT_EE_ENG, VestingScaleConfigItem.BASIS_CONT_EE_ENG),
          Map.entry(BASIS_CONT_EE_CHI, VestingScaleConfigItem.BASIS_CONT_EE_CHI),
          Map.entry(BASIS_CONT_ER_ENG, VestingScaleConfigItem.BASIS_CONT_ER_ENG),
          Map.entry(BASIS_CONT_ER_CHI, VestingScaleConfigItem.BASIS_CONT_ER_CHI));

  VestingScaleData map(TrpGetVestingScaleApimDataItem item) {
    if (item == null) {
      throw malformed(
          TrpGetVestingScaleApimDataItem.DATA,
          VestingScaleMalformedResponseException.MalformedReason.MISSING_REQUIRED_FIELD);
    }
    Member member = required(item.member(), MEMBER);
    List<Plan> plans = required(item.plans(), PLANS);
    List<ConfigItem> config = required(item.config(), CONFIG);
    return new VestingScaleData(mapMember(member), mapPlans(plans), mapConfig(config));
  }

  private static VestingScaleMember mapMember(Member member) {
    return new VestingScaleMember(
        presence(member.memberCategory(), TrpGetVestingScaleApimMapper::text),
        datePresence(member.dateJoinedCompany(), path(MEMBER, DATE_JOINED_COMPANY)),
        datePresence(member.dateJoinedScheme(), path(MEMBER, DATE_JOINED_SCHEME)),
        datePresence(member.employmentDate(), path(MEMBER, EMPLOYMENT_DATE)));
  }

  private static List<VestingScalePlan> mapPlans(List<Plan> planItems) {
    Set<String> planNumbers = new HashSet<>();
    List<VestingScalePlan> plans = new ArrayList<>();
    for (int index = 0; index < planItems.size(); index++) {
      String planPath = indexed(PLANS, index);
      Plan plan = planItems.get(index);
      if (plan == null) {
        throw malformed(
            planPath, VestingScaleMalformedResponseException.MalformedReason.INVALID_TYPE);
      }
      String planNo = requiredNonBlank(plan.planNo(), path(planPath, PLAN_NO));
      if (!planNumbers.add(planNo)) {
        throw malformed(
            path(planPath, PLAN_NO),
            VestingScaleMalformedResponseException.MalformedReason.DUPLICATE_VALUE);
      }
      String planTypeDescEng =
          requiredNonBlank(plan.planTypeDescEng(), path(planPath, PLAN_TYPE_DESC_ENG));
      String planTypeDescChi = optionalValue(plan.planTypeDescChi());
      plans.add(
          new VestingScalePlan(
              planNo,
              planTypeDescEng,
              planTypeDescChi,
              datePresence(plan.planEffectiveDate(), path(planPath, PLAN_EFFECTIVE_DATE)),
              datePresence(plan.dateOfJoiningPlan(), path(planPath, DATE_OF_JOINING_PLAN)),
              planService(plan.planService(), path(planPath, PLAN_SERVICE)),
              presence(plan.vestPeriodBaseEng(), Function.identity()),
              presence(plan.vestPeriodBaseChi(), Function.identity()),
              presence(plan.basisContEeEng(), Function.identity()),
              presence(plan.basisContEeChi(), Function.identity()),
              presence(plan.basisContErEng(), Function.identity()),
              presence(plan.basisContErChi(), Function.identity()),
              vestingScale(plan.vestingScale(), path(planPath, VESTING_SCALE))));
    }
    return List.copyOf(plans);
  }

  private static Map<VestingScaleConfigItem, VestingScaleConfigValue> mapConfig(
      List<ConfigItem> items) {
    Set<String> seen = new HashSet<>();
    Map<VestingScaleConfigItem, VestingScaleConfigValue> config = new LinkedHashMap<>();
    for (int index = 0; index < items.size(); index++) {
      String itemPath = indexed(CONFIG, index);
      ConfigItem item = items.get(index);
      if (item == null) {
        throw malformed(
            itemPath, VestingScaleMalformedResponseException.MalformedReason.INVALID_TYPE);
      }
      String itemId = requiredNonBlank(item.itemId(), path(itemPath, ITEM_ID));
      if (!seen.add(itemId)) {
        throw malformed(
            path(itemPath, ITEM_ID),
            VestingScaleMalformedResponseException.MalformedReason.DUPLICATE_VALUE);
      }
      VestingScaleConfigValue value = configValue(item.configValue(), path(itemPath, CONFIG_VALUE));
      VestingScaleConfigItem configItem = CONFIG_ITEMS_BY_ID.get(itemId);
      if (configItem != null) {
        config.put(configItem, value);
      } else {
        LOG.warn("event=vesting-scale.config.unknown_item item-id={}", itemId);
      }
    }
    return Map.copyOf(config);
  }

  private static VestingScaleConfigValue configValue(ApimField<String> field, String fieldPath) {
    String value = required(field, fieldPath);
    try {
      return VestingScaleConfigValue.valueOf(value);
    } catch (IllegalArgumentException exception) {
      throw malformed(
          fieldPath, VestingScaleMalformedResponseException.MalformedReason.INVALID_ENUM_VALUE);
    }
  }

  private static Presence<VestingScalePlanService> planService(
      ApimField<PlanService> field, String path) {
    return presence(
        field,
        service -> {
          if (service == null) {
            throw malformed(
                path, VestingScaleMalformedResponseException.MalformedReason.INVALID_TYPE);
          }
          return new VestingScalePlanService(
              nullableInteger(service.years(), path(path, YEARS)),
              nullableInteger(service.months(), path(path, MONTHS)));
        });
  }

  private static Integer nullableInteger(ApimField<Integer> field, String fieldPath) {
    return switch (state(field, fieldPath)) {
      case ABSENT ->
          throw malformed(
              fieldPath,
              VestingScaleMalformedResponseException.MalformedReason.MISSING_REQUIRED_FIELD);
      case NULL -> null;
      case VALUE -> field.value();
    };
  }

  private static Presence<List<VestingScaleRow>> vestingScale(
      ApimField<List<VestingRow>> field, String fieldPath) {
    return presence(field, rows -> mapRows(rows, fieldPath));
  }

  private static List<VestingScaleRow> mapRows(List<VestingRow> rows, String fieldPath) {
    if (rows == null) {
      throw malformed(
          fieldPath, VestingScaleMalformedResponseException.MalformedReason.INVALID_TYPE);
    }
    List<VestingScaleRow> mapped = new ArrayList<>();
    for (int index = 0; index < rows.size(); index++) {
      String rowPath = indexed(fieldPath, index);
      VestingRow row = rows.get(index);
      if (row == null) {
        throw malformed(
            rowPath, VestingScaleMalformedResponseException.MalformedReason.INVALID_TYPE);
      }
      Integer years = required(row.completedYears(), path(rowPath, COMPLETED_YEARS));
      BigDecimal percentage = required(row.vestPcnt(), path(rowPath, VEST_PCNT));
      mapped.add(new VestingScaleRow(years, percentage));
    }
    return List.copyOf(mapped);
  }

  private static Presence<LocalDate> datePresence(ApimField<String> field, String fieldPath) {
    return presence(field, value -> parseDate(value, fieldPath));
  }

  private static LocalDate parseDate(String value, String fieldPath) {
    try {
      return LocalDate.parse(value, DATE_FORMAT);
    } catch (DateTimeParseException exception) {
      throw malformed(
          fieldPath, VestingScaleMalformedResponseException.MalformedReason.INVALID_DATE_FORMAT);
    }
  }

  private static String text(String value) {
    return value;
  }

  private static String optionalValue(ApimField<String> field) {
    return field == null || field.state() != ApimField.State.VALUE ? null : field.value();
  }

  private static String requiredNonBlank(ApimField<String> field, String fieldPath) {
    String value = required(field, fieldPath);
    if (value.isBlank()) {
      throw malformed(
          fieldPath, VestingScaleMalformedResponseException.MalformedReason.REQUIRED_TEXT_BLANK);
    }
    return value;
  }

  private static <T> T required(ApimField<T> field, String fieldPath) {
    return switch (state(field, fieldPath)) {
      case ABSENT, NULL ->
          throw malformed(
              fieldPath,
              VestingScaleMalformedResponseException.MalformedReason.MISSING_REQUIRED_FIELD);
      case VALUE -> field.value();
    };
  }

  private static ApimField.State state(ApimField<?> field, String fieldPath) {
    if (field == null) {
      throw malformed(
          fieldPath, VestingScaleMalformedResponseException.MalformedReason.MISSING_REQUIRED_FIELD);
    }
    return field.state();
  }

  private static <T, R> Presence<R> presence(ApimField<T> field, Function<T, R> mapper) {
    if (field == null) {
      throw malformed(
          TrpGetVestingScaleApimDataItem.DATA,
          VestingScaleMalformedResponseException.MalformedReason.MISSING_REQUIRED_FIELD);
    }
    return switch (field.state()) {
      case ABSENT -> Presence.absent();
      case NULL -> Presence.present(null);
      case VALUE -> Presence.present(mapper.apply(field.value()));
    };
  }

  private static String indexed(String field, int index) {
    return field + "[" + index + "]";
  }

  private static String path(String parent, String field) {
    return parent + "." + field;
  }

  private static VestingScaleMalformedResponseException malformed(
      String fieldPath, VestingScaleMalformedResponseException.MalformedReason reason) {
    return new VestingScaleMalformedResponseException(fieldPath, reason);
  }
}

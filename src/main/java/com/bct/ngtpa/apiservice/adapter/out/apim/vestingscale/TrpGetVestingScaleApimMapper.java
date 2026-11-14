package com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale;

import com.bct.ngtpa.apiservice.application.dto.vestingscale.Presence;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleConfigValue;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleData;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleMember;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScalePlan;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScalePlanService;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
  private static final Set<String> CONFIG_ITEM_IDS =
      Set.of(
          "member-category",
          "plan-effective-date",
          "basis-cont-ee-chi",
          "basis-cont-ee-eng",
          "basis-cont-er-chi",
          "basis-cont-er-eng",
          "date-joined-company",
          "date-joined-scheme",
          "date-of-joining-plan",
          "employment-date",
          "plan-service",
          "vesting-scale");

  VestingScaleData map(JsonNode item) {
    ObjectNode root = requireObject(item);
    VestingScaleMember member = mapMember(requireObject(root.get("member")));
    List<VestingScalePlan> plans = mapPlans(requireArray(root.get("plans")));
    Map<String, VestingScaleConfigValue> config = mapConfig(requireArray(root.get("config")));
    return new VestingScaleData(member, plans, config);
  }

  private static VestingScaleMember mapMember(ObjectNode member) {
    return new VestingScaleMember(
        optional(member, "member-category", TrpGetVestingScaleApimMapper::requiredText),
        optional(member, "date-joined-company", node -> date(node, "member.date-joined-company")),
        optional(member, "date-joined-scheme", node -> date(node, "member.date-joined-scheme")),
        optional(member, "employment-date", node -> date(node, "member.employment-date")));
  }

  private static List<VestingScalePlan> mapPlans(JsonNode plansNode) {
    Set<String> planNumbers = new HashSet<>();
    List<VestingScalePlan> plans = new ArrayList<>();
    for (JsonNode planNode : plansNode) {
      ObjectNode plan = requireObject(planNode);
      String planNo = requiredNonBlankText(plan.get("plan-no"), "plans[].plan-no");
      if (!planNumbers.add(planNo)) {
        throw malformed();
      }
      plans.add(
          new VestingScalePlan(
              planNo,
              optionalText(plan.get("plan-type-desc-eng"), "plans[].plan-type-desc-eng"),
              optionalText(plan.get("plan-type-desc-chi"), "plans[].plan-type-desc-chi"),
              optional(
                  plan, "plan-effective-date", node -> date(node, "plans[].plan-effective-date")),
              optional(
                  plan, "date-of-joining-plan", node -> date(node, "plans[].date-of-joining-plan")),
              optional(plan, "plan-service", TrpGetVestingScaleApimMapper::planService),
              optional(plan, "vest-period-base-eng", TrpGetVestingScaleApimMapper::requiredText),
              optional(plan, "vest-period-base-chi", TrpGetVestingScaleApimMapper::requiredText),
              optional(plan, "basis-cont-ee-eng", TrpGetVestingScaleApimMapper::requiredText),
              optional(plan, "basis-cont-ee-chi", TrpGetVestingScaleApimMapper::requiredText),
              optional(plan, "basis-cont-er-eng", TrpGetVestingScaleApimMapper::requiredText),
              optional(plan, "basis-cont-er-chi", TrpGetVestingScaleApimMapper::requiredText),
              optional(plan, "vesting-scale", TrpGetVestingScaleApimMapper::vestingRows)));
    }
    return List.copyOf(plans);
  }

  private static Map<String, VestingScaleConfigValue> mapConfig(JsonNode configNode) {
    Set<String> seen = new HashSet<>();
    Map<String, VestingScaleConfigValue> config = new LinkedHashMap<>();
    for (JsonNode itemNode : configNode) {
      ObjectNode item = requireObject(itemNode);
      String itemId = requiredNonBlankText(item.get("item-id"));
      if (!seen.add(itemId)) {
        throw malformed();
      }
      VestingScaleConfigValue value = configValue(item.get("config-value"));
      if (CONFIG_ITEM_IDS.contains(itemId)) {
        config.put(itemId, value);
      } else {
        LOG.warn("event=vesting-scale.config.unknown_item item-id={}", itemId);
      }
    }
    return Map.copyOf(config);
  }

  private static VestingScaleConfigValue configValue(JsonNode valueNode) {
    String value = requiredText(valueNode);
    try {
      return VestingScaleConfigValue.valueOf(value);
    } catch (IllegalArgumentException exception) {
      throw malformed();
    }
  }

  private static VestingScalePlanService planService(JsonNode node) {
    ObjectNode service = requireObject(node);
    if (!service.has("years") || !service.has("months")) {
      throw malformed();
    }
    return new VestingScalePlanService(
        nullableInteger(service.get("years")), nullableInteger(service.get("months")));
  }

  private static Integer nullableInteger(JsonNode node) {
    if (node == null) {
      throw malformed();
    }
    if (node.isNull()) {
      return null;
    }
    if (!node.isIntegralNumber() || !node.canConvertToInt()) {
      throw malformed();
    }
    return node.intValue();
  }

  private static List<VestingScaleRow> vestingRows(JsonNode rowsNode) {
    if (!rowsNode.isArray()) {
      throw malformed();
    }
    List<VestingScaleRow> rows = new ArrayList<>();
    for (JsonNode rowNode : rowsNode) {
      ObjectNode row = requireObject(rowNode);
      JsonNode years = row.get("completed-years");
      JsonNode percentage = row.get("vest-pcnt");
      if (years == null
          || !years.isIntegralNumber()
          || !years.canConvertToInt()
          || percentage == null
          || !percentage.isNumber()) {
        throw malformed();
      }
      rows.add(new VestingScaleRow(years.intValue(), percentage.decimalValue()));
    }
    return List.copyOf(rows);
  }

  private static <T> Presence<T> optional(
      ObjectNode source, String field, Function<JsonNode, T> parser) {
    if (!source.has(field)) {
      return Presence.absent();
    }
    JsonNode node = source.get(field);
    return Presence.present(node.isNull() ? null : parser.apply(node));
  }

  private static LocalDate date(JsonNode node, String fieldPath) {
    if (node == null || !node.isTextual()) {
      throw malformed(
          fieldPath, VestingScaleMalformedResponseException.MalformedReason.REQUIRED_TEXT_TYPE);
    }
    try {
      return LocalDate.parse(node.textValue(), DATE_FORMAT);
    } catch (DateTimeParseException exception) {
      throw malformed(
          fieldPath, VestingScaleMalformedResponseException.MalformedReason.INVALID_DATE_FORMAT);
    }
  }

  private static String requiredNonBlankText(JsonNode node, String fieldPath) {
    if (node == null || !node.isTextual()) {
      throw malformed(
          fieldPath, VestingScaleMalformedResponseException.MalformedReason.REQUIRED_TEXT_TYPE);
    }
    String value = node.textValue();
    if (value.isBlank()) {
      throw malformed(
          fieldPath, VestingScaleMalformedResponseException.MalformedReason.REQUIRED_TEXT_BLANK);
    }
    return value;
  }

  private static String optionalText(JsonNode node, String fieldPath) {
    if (node == null || node.isNull()) {
      return null;
    }
    if (!node.isTextual()) {
      throw malformed(
          fieldPath, VestingScaleMalformedResponseException.MalformedReason.REQUIRED_TEXT_TYPE);
    }
    return node.textValue();
  }

  private static String requiredNonBlankText(JsonNode node) {
    String value = requiredText(node);
    if (value.isBlank()) {
      throw malformed();
    }
    return value;
  }

  private static String requiredText(JsonNode node) {
    if (node == null || !node.isTextual()) {
      throw malformed();
    }
    return node.textValue();
  }

  private static ObjectNode requireObject(JsonNode node) {
    if (node == null || !node.isObject()) {
      throw malformed();
    }
    return (ObjectNode) node;
  }

  private static JsonNode requireArray(JsonNode node) {
    if (node == null || !node.isArray()) {
      throw malformed();
    }
    return node;
  }

  private static VestingScaleMalformedResponseException malformed() {
    return new VestingScaleMalformedResponseException();
  }

  private static VestingScaleMalformedResponseException malformed(
      String fieldPath, VestingScaleMalformedResponseException.MalformedReason reason) {
    return new VestingScaleMalformedResponseException(fieldPath, reason);
  }
}

package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PageSchemaLinter {
  private static final String TYPE_MIN_LENGTH = "minLength";

  private static final String TYPE_MAX_LENGTH = "maxLength";

  private static final String PAGE_VARIANT_PREFIX = ": page variant ";

  private static final String IN_RULE_SUFFIX = " in rule ";

  private static final Set<String> SUPPORTED_TYPES =
      Set.of(
          "required",
          TYPE_MIN_LENGTH,
          TYPE_MAX_LENGTH,
          "pattern",
          "email",
          "blockedAddress",
          "conditionalRequired",
          "groupRequirement",
          "conditionalWarning",
          "noSpaces");
  private static final Set<String> SUPPORTED_WHEN =
      Set.of("notBlank", "blank", "changed", "all", "any", "allGroupsEmpty");
  private static final Set<String> SUPPORTED_THEN =
      Set.of("fail", "required", "allRequired", "showMessage");
  private static final Set<String> SUPPORTED_SEVERITY = Set.of("error", "warning", "info");

  public static LintResult lint(BffPagesProperties properties) {
    LintResult result = new LintResult();
    if (properties == null) return result;
    Map<String, PageSchemaProperties> pages = properties.getPages();
    if (pages == null) return result;
    for (Map.Entry<String, PageSchemaProperties> e : pages.entrySet()) {
      lintPage(result, e.getKey(), e.getValue());
    }
    return result;
  }

  private static void lintPage(LintResult result, String pageKey, PageSchemaProperties page) {
    if (page == null) return;
    validatePageVariants(result, pageKey, page);
    Set<String> fieldIds = collectFieldIds(page);
    validateFieldValidations(result, pageKey, fieldIds, page);
    validatePageLevelValidations(result, pageKey, fieldIds, page);
  }

  private static Set<String> collectFieldIds(PageSchemaProperties page) {
    Set<String> fieldIds = new HashSet<>();
    if (page.getForm() == null || page.getForm().getSections() == null) {
      return fieldIds;
    }
    for (SectionProperties s : page.getForm().getSections()) {
      if (s == null || s.getFields() == null) continue;
      for (FieldProperties f : s.getFields()) {
        if (f != null && f.getId() != null) fieldIds.add(f.getId());
      }
    }
    return fieldIds;
  }

  private static void validateFieldValidations(
      LintResult result, String pageKey, Set<String> fieldIds, PageSchemaProperties page) {
    if (page.getForm() == null || page.getForm().getSections() == null) return;
    for (SectionProperties s : page.getForm().getSections()) {
      if (s == null || s.getFields() == null) continue;
      for (FieldProperties f : s.getFields()) {
        if (f == null || f.getValidations() == null) continue;
        for (ValidationRuleProperties v : f.getValidations()) {
          validateRule(result, pageKey, fieldIds, v);
        }
      }
    }
  }

  private static void validatePageLevelValidations(
      LintResult result, String pageKey, Set<String> fieldIds, PageSchemaProperties page) {
    if (page.getValidations() == null) return;
    for (ValidationRuleProperties v : page.getValidations()) {
      validateRule(result, pageKey, fieldIds, v);
    }
  }

  private static void validatePageVariants(
      LintResult result, String pageKey, PageSchemaProperties page) {
    Map<String, PageVariantProperties> variants = page.getVariants();
    if (variants == null || variants.isEmpty()) {
      return;
    }
    for (Map.Entry<String, PageVariantProperties> entry : variants.entrySet()) {
      validateVariantEntry(result, pageKey, entry.getKey(), entry.getValue());
    }
  }

  private static void validateVariantEntry(
      LintResult result, String pageKey, String variantKey, PageVariantProperties variant) {
    if (variantKey == null || variantKey.isBlank()) {
      result.addError(pageKey + ": page variant has blank key");
    }
    if (variant == null) {
      result.addError(pageKey + PAGE_VARIANT_PREFIX + variantKey + " is null");
      return;
    }
    if (isBlankLocalizedMap(variant.getTitle())) {
      result.addError(pageKey + PAGE_VARIANT_PREFIX + variantKey + " requires title");
    }
    if (isBlankLocalizedMap(variant.getBody())) {
      result.addError(pageKey + PAGE_VARIANT_PREFIX + variantKey + " requires body");
    }
    if (variant.getActions() == null || variant.getActions().isEmpty()) {
      result.addError(pageKey + PAGE_VARIANT_PREFIX + variantKey + " requires actions");
      return;
    }
    for (ActionProperties action : variant.getActions()) {
      validateVariantAction(result, pageKey, variantKey, action);
    }
  }

  private static void validateVariantAction(
      LintResult result, String pageKey, String variantKey, ActionProperties action) {
    if (action == null) {
      result.addError(pageKey + PAGE_VARIANT_PREFIX + variantKey + " contains null action");
      return;
    }
    if (isBlank(action.getId()) && isBlank(action.getName())) {
      result.addError(pageKey + PAGE_VARIANT_PREFIX + variantKey + " action requires id or name");
    }
    if (isBlank(action.getType())) {
      result.addError(pageKey + PAGE_VARIANT_PREFIX + variantKey + " action requires type");
    }
    if (isBlank(action.getNextStep())) {
      result.addError(pageKey + PAGE_VARIANT_PREFIX + variantKey + " action requires nextStep");
    }
    if (isBlank(action.getLabelCode()) && isBlankLocalizedMap(action.getLabel())) {
      result.addError(pageKey + PAGE_VARIANT_PREFIX + variantKey + " action requires label");
    }
  }

  private static void validateRule(
      LintResult result, String pageKey, Set<String> fieldIds, ValidationRuleProperties v) {
    if (v == null) {
      return;
    }
    String ruleId = v.getId();
    if (ruleId == null || ruleId.isBlank()) {
      result.addError(pageKey + ": validation rule missing id");
    }
    validateType(result, pageKey, ruleId, v);
    validateCondition(result, pageKey, fieldIds, ruleId, v.getWhen());
    validateAction(result, pageKey, fieldIds, ruleId, v.getThen());
    if (v.getSeverity() != null && !SUPPORTED_SEVERITY.contains(v.getSeverity())) {
      result.addError(
          pageKey + ": unsupported severity " + v.getSeverity() + IN_RULE_SUFFIX + ruleId);
    }
    validateRuleValue(result, pageKey, ruleId, v);
    validateValueByVariant(result, pageKey, ruleId, v);
  }

  private static void validateType(
      LintResult result, String pageKey, String ruleId, ValidationRuleProperties v) {
    String type = v.getType();
    if (type != null && !type.isBlank() && !SUPPORTED_TYPES.contains(type)) {
      result.addError(pageKey + ": unsupported validation type " + type + IN_RULE_SUFFIX + ruleId);
    }
  }

  private static void validateCondition(
      LintResult result,
      String pageKey,
      Set<String> fieldIds,
      String ruleId,
      RuleConditionProperties condition) {
    if (condition == null) {
      return;
    }
    String whenOp = condition.getOperator();
    if (whenOp != null && !SUPPORTED_WHEN.contains(whenOp)) {
      result.addError(pageKey + ": unsupported when.operator " + whenOp + IN_RULE_SUFFIX + ruleId);
    }
    validateReferencedFields(result, pageKey, fieldIds, ruleId, condition.getFields());
    validateReferencedField(result, pageKey, fieldIds, ruleId, condition.getField());
    validateGroups(result, pageKey, fieldIds, ruleId, condition.getGroups());
    if (condition.getConditions() != null) {
      for (RuleConditionProperties nested : condition.getConditions()) {
        validateCondition(result, pageKey, fieldIds, ruleId, nested);
      }
    }
  }

  private static void validateAction(
      LintResult result,
      String pageKey,
      Set<String> fieldIds,
      String ruleId,
      RuleActionProperties action) {
    if (action == null) {
      return;
    }
    String thenOp = action.getOperator();
    if (thenOp != null && !SUPPORTED_THEN.contains(thenOp)) {
      result.addError(pageKey + ": unsupported then.operator " + thenOp + IN_RULE_SUFFIX + ruleId);
    }
    validateReferencedField(result, pageKey, fieldIds, ruleId, action.getField());
    validateReferencedFields(result, pageKey, fieldIds, ruleId, action.getFields());
    validateReferencedFields(result, pageKey, fieldIds, ruleId, action.getTargets());
  }

  private static void validateGroups(
      LintResult result,
      String pageKey,
      Set<String> fieldIds,
      String ruleId,
      List<Map<String, Object>> groups) {
    if (groups == null) {
      return;
    }
    for (Map<String, Object> group : groups) {
      if (group == null) {
        continue;
      }
      for (String ref : RuleGroupFields.of(group.get("fields"))) {
        validateReferencedField(result, pageKey, fieldIds, ruleId, ref);
      }
    }
  }

  private static void validateReferencedFields(
      LintResult result, String pageKey, Set<String> fieldIds, String ruleId, List<String> refs) {
    if (refs == null) {
      return;
    }
    for (String ref : refs) {
      validateReferencedField(result, pageKey, fieldIds, ruleId, ref);
    }
  }

  private static void validateReferencedField(
      LintResult result, String pageKey, Set<String> fieldIds, String ruleId, String ref) {
    if (ref != null && !fieldIds.contains(ref)) {
      result.addError(pageKey + ": rule " + ruleId + " references unknown field " + ref);
    }
  }

  private static void validateRuleValue(
      LintResult result, String pageKey, String ruleId, ValidationRuleProperties v) {
    String type = v.getType();
    Object value = v.getValue();
    if ("blockedAddress".equals(type) && isBlank(value)) {
      result.addError(pageKey + ": blockedAddress rule " + ruleId + " requires non-blank value");
    }
    if ((TYPE_MIN_LENGTH.equals(type) || TYPE_MAX_LENGTH.equals(type))
        && value != null
        && !isInteger(value)) {
      result.addError(pageKey + ": " + type + " rule " + ruleId + " requires numeric value");
    }
  }

  private static void validateValueByVariant(
      LintResult result, String pageKey, String ruleId, ValidationRuleProperties v) {
    Map<String, Object> variants = v.getValueByVariant();
    if (variants == null || variants.isEmpty()) {
      return;
    }
    for (Map.Entry<String, Object> entry : variants.entrySet()) {
      String variantKey = entry.getKey();
      if (variantKey == null || variantKey.isBlank()) {
        result.addError(pageKey + ": rule " + ruleId + " has blank valueByVariant key");
      }
      if ((TYPE_MIN_LENGTH.equals(v.getType()) || TYPE_MAX_LENGTH.equals(v.getType()))
          && !isInteger(entry.getValue())) {
        result.addError(
            pageKey
                + ": "
                + v.getType()
                + " rule "
                + ruleId
                + " requires numeric valueByVariant value for "
                + variantKey);
      }
    }
  }

  private static boolean isBlank(Object value) {
    return value == null || value.toString().isBlank();
  }

  private static boolean isBlankLocalizedMap(Map<String, String> value) {
    if (value == null || value.isEmpty()) {
      return true;
    }
    return value.values().stream().allMatch(PageSchemaLinter::isBlank);
  }

  private static boolean isInteger(Object value) {
    if (value instanceof Number) {
      return true;
    }
    if (value == null) {
      return false;
    }
    try {
      Integer.parseInt(value.toString());
      return true;
    } catch (NumberFormatException exception) {
      return false;
    }
  }
}

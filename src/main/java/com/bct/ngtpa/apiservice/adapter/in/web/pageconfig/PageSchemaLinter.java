package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
public class PageSchemaLinter {
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "required",
            "minLength",
            "maxLength",
            "pattern",
            "email",
            "blockedAddress",
            "conditionalRequired",
            "groupRequirement",
            "conditionalWarning",
            "noSpaces");
    private static final Set<String> SUPPORTED_WHEN = Set.of("notBlank", "blank", "changed", "all", "any", "allGroupsEmpty");
    private static final Set<String> SUPPORTED_THEN = Set.of("fail", "required", "allRequired", "showMessage");
    private static final Set<String> SUPPORTED_SEVERITY = Set.of("error", "warning", "info");
    public static LintResult lint(BffPagesProperties properties) {
        LintResult result = new LintResult();
        if (properties == null) return result;
        Map<String, PageSchemaProperties> pages = properties.getPages();
        if (pages == null) return result;
        for (Map.Entry<String, PageSchemaProperties> e : pages.entrySet()) {
            String pageKey = e.getKey();
            PageSchemaProperties page = e.getValue();
            if (page == null) continue;
            Set<String> fieldIds = new HashSet<>();
            if (page.getForm() != null && page.getForm().getSections() != null) {
                for (SectionProperties s : page.getForm().getSections()) {
                    if (s == null || s.getFields() == null) continue;
                    for (FieldProperties f : s.getFields()) {
                        if (f != null && f.getId() != null) fieldIds.add(f.getId());
                    }
                }
            }
            if (page.getForm() != null && page.getForm().getSections() != null) {
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
            if (page.getValidations() != null) {
                for (ValidationRuleProperties v : page.getValidations()) {
                    validateRule(result, pageKey, fieldIds, v);
                }
            }
        }
        return result;
    }
    private static void validateRule(LintResult result, String pageKey, Set<String> fieldIds, ValidationRuleProperties v) {
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
            result.addError(pageKey + ": unsupported severity " + v.getSeverity() + " in rule " + ruleId);
        }
        validateRuleValue(result, pageKey, ruleId, v);
        validateValueByVariant(result, pageKey, ruleId, v);
    }
    private static void validateType(LintResult result, String pageKey, String ruleId, ValidationRuleProperties v) {
        String type = v.getType();
        if (type != null && !type.isBlank() && !SUPPORTED_TYPES.contains(type)) {
            result.addError(pageKey + ": unsupported validation type " + type + " in rule " + ruleId);
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
            result.addError(pageKey + ": unsupported when.operator " + whenOp + " in rule " + ruleId);
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
            result.addError(pageKey + ": unsupported then.operator " + thenOp + " in rule " + ruleId);
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
            Object rawFields = group.get("fields");
            if (!(rawFields instanceof List<?> fields)) {
                continue;
            }
            for (Object ref : fields) {
                validateReferencedField(result, pageKey, fieldIds, ruleId, ref == null ? null : ref.toString());
            }
        }
    }
    private static void validateReferencedFields(
            LintResult result,
            String pageKey,
            Set<String> fieldIds,
            String ruleId,
            List<String> refs) {
        if (refs == null) {
            return;
        }
        for (String ref : refs) {
            validateReferencedField(result, pageKey, fieldIds, ruleId, ref);
        }
    }
    private static void validateReferencedField(
            LintResult result,
            String pageKey,
            Set<String> fieldIds,
            String ruleId,
            String ref) {
        if (ref != null && !fieldIds.contains(ref)) {
            result.addError(pageKey + ": rule " + ruleId + " references unknown field " + ref);
        }
    }
    private static void validateRuleValue(LintResult result, String pageKey, String ruleId, ValidationRuleProperties v) {
        String type = v.getType();
        Object value = v.getValue();
        if ("blockedAddress".equals(type) && isBlank(value)) {
            result.addError(pageKey + ": blockedAddress rule " + ruleId + " requires non-blank value");
        }
        if (("minLength".equals(type) || "maxLength".equals(type)) && value != null && !isInteger(value)) {
            result.addError(pageKey + ": " + type + " rule " + ruleId + " requires numeric value");
        }
    }
    private static void validateValueByVariant(LintResult result, String pageKey, String ruleId, ValidationRuleProperties v) {
        Map<String, Object> variants = v.getValueByVariant();
        if (variants == null || variants.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : variants.entrySet()) {
            String variantKey = entry.getKey();
            if (variantKey == null || variantKey.isBlank()) {
                result.addError(pageKey + ": rule " + ruleId + " has blank valueByVariant key");
            }
            if (("minLength".equals(v.getType()) || "maxLength".equals(v.getType())) && !isInteger(entry.getValue())) {
                result.addError(pageKey + ": " + v.getType() + " rule " + ruleId
                        + " requires numeric valueByVariant value for " + variantKey);
            }
        }
    }
    private static boolean isBlank(Object value) {
        return value == null || value.toString().isBlank();
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

package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PageSchemaLinter {

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

            // collect field ids in page
            Set<String> fieldIds = new HashSet<>();
            if (page.getForm() != null && page.getForm().getSections() != null) {
                for (SectionProperties s : page.getForm().getSections()) {
                    if (s.getFields() != null) {
                        for (FieldProperties f : s.getFields()) {
                            if (f.getId() != null) fieldIds.add(f.getId());
                        }
                    }
                }
            }

            // validate field-level validations
            if (page.getForm() != null && page.getForm().getSections() != null) {
                for (SectionProperties s : page.getForm().getSections()) {
                    if (s.getFields() == null) continue;
                    for (FieldProperties f : s.getFields()) {
                        if (f.getValidations() == null) continue;
                        for (ValidationRuleProperties v : f.getValidations()) {
                            validateRule(result, pageKey, fieldIds, v);
                        }
                    }
                }
            }

            // page-level validations
            if (page.getValidations() != null) {
                for (ValidationRuleProperties v : page.getValidations()) {
                    validateRule(result, pageKey, fieldIds, v);
                }
            }
        }

        return result;
    }

    private static void validateRule(LintResult result, String pageKey, Set<String> fieldIds, ValidationRuleProperties v) {
        String ruleId = v.getId();
        if (ruleId == null || ruleId.isBlank()) {
            result.addError(pageKey + ": validation rule missing id");
        }
        if (v.getWhen() != null) {
            String whenOp = v.getWhen().getOperator();
            if (whenOp != null && !SUPPORTED_WHEN.contains(whenOp)) {
                result.addError(pageKey + ": unsupported when.operator " + whenOp + " in rule " + ruleId);
            }
            List<String> fields = v.getWhen().getFields();
            if (fields != null) {
                for (String ref : fields) {
                    if (!fieldIds.contains(ref)) {
                        result.addError(pageKey + ": rule " + ruleId + " references unknown field " + ref);
                    }
                }
            }
        }
        if (v.getThen() != null) {
            String thenOp = v.getThen().getOperator();
            if (thenOp != null && !SUPPORTED_THEN.contains(thenOp)) {
                result.addError(pageKey + ": unsupported then.operator " + thenOp + " in rule " + ruleId);
            }
        }
        if (v.getSeverity() != null && !SUPPORTED_SEVERITY.contains(v.getSeverity())) {
            result.addError(pageKey + ": unsupported severity " + v.getSeverity() + " in rule " + ruleId);
        }
    }
}

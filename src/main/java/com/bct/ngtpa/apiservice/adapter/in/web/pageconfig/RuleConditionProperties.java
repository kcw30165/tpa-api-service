package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.List;
import java.util.Map;

public class RuleConditionProperties {

    private String operator;
    private String field;
    private List<String> fields;
    private Boolean fieldRequired;
    private String compareWith;
    private List<RuleConditionProperties> conditions;
    private List<Map<String, Object>> groups;

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }

    public Boolean getFieldRequired() {
        return fieldRequired;
    }

    public void setFieldRequired(Boolean fieldRequired) {
        this.fieldRequired = fieldRequired;
    }

    public String getCompareWith() {
        return compareWith;
    }

    public void setCompareWith(String compareWith) {
        this.compareWith = compareWith;
    }

    public List<RuleConditionProperties> getConditions() {
        return conditions;
    }

    public void setConditions(List<RuleConditionProperties> conditions) {
        this.conditions = conditions;
    }

    public List<Map<String, Object>> getGroups() {
        return groups;
    }

    public void setGroups(List<Map<String, Object>> groups) {
        this.groups = groups;
    }
}

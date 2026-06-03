package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.List;

public class RuleActionProperties {

    private String operator;
    private String field;
    private List<String> fields;
    private List<String> targets;

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

    public List<String> getTargets() {
        return targets;
    }

    public void setTargets(List<String> targets) {
        this.targets = targets;
    }
}

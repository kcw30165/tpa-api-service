package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.List;

public class RuleConditionProperties {

    private String operator;
    private List<String> fields;

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }
}

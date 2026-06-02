package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.List;

public class RuleConditionProperties {

    private WhenOperator operator;
    private List<String> fields;

    public WhenOperator getOperator() {
        return operator;
    }

    public void setOperator(WhenOperator operator) {
        this.operator = operator;
    }

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }
}

package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.List;

public class ValidationRuleProperties {

    private String id;
    private RuleConditionProperties when;
    private RuleActionProperties then;
    private String severity;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public RuleConditionProperties getWhen() {
        return when;
    }

    public void setWhen(RuleConditionProperties when) {
        this.when = when;
    }

    public RuleActionProperties getThen() {
        return then;
    }

    public void setThen(RuleActionProperties then) {
        this.then = then;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }
}

package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.Map;

public class ValidationRuleProperties {

    private String id;
    private String type;
    private Object value;
    private RuleConditionProperties when;
    private RuleActionProperties then;
    private String severity;
    private String code;
    private Map<String, String> message;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Map<String, String> getMessage() {
        return message;
    }

    public void setMessage(Map<String, String> message) {
        this.message = message;
    }
}

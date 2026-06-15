package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ValidationRuleProperties {
    private String messageCode;
    private String id;
    private String type;
    private Object value;
    private Map<String, Object> valueByVariant;
    private RuleConditionProperties when;
    private RuleActionProperties then;
    private String severity;
    private String code;
    private Map<String, String> message;
}

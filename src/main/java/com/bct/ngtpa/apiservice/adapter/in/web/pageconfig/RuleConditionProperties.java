package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RuleConditionProperties {

    private String operator;
    private String field;
    private List<String> fields;
    private Boolean fieldRequired;
    private String compareWith;
    private List<RuleConditionProperties> conditions;
    private List<Map<String, Object>> groups;
}

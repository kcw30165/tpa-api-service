package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RuleActionProperties {

    private String operator;
    private String field;
    private List<String> fields;
    private List<String> targets;
}

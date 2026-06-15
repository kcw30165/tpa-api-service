package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FieldProperties {

    private String placeholderCode;

    private String labelCode;

    private String id;
    private Integer displayOrder;
    private Map<String, String> label;
    private String dataType;
    private String controlType;
    private Integer minLength;
    private Integer maxLength;
    private String pattern;
    private Map<String, String> placeholder;
    private String optionSource;
    private Map<String, Object> copyWhenChecked;
    private ApimBindingProperties apimBinding;
    private List<ValidationRuleProperties> validations;
}

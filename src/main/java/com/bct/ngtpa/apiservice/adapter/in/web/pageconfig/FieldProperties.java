package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.List;
import java.util.Map;

public class FieldProperties {

    private String id;
    private Integer displayOrder;
    private Map<String, String> label;
    private String optionSource;
    private List<ValidationRuleProperties> validations;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Map<String, String> getLabel() {
        return label;
    }

    public void setLabel(Map<String, String> label) {
        this.label = label;
    }

    public String getOptionSource() {
        return optionSource;
    }

    public void setOptionSource(String optionSource) {
        this.optionSource = optionSource;
    }

    public List<ValidationRuleProperties> getValidations() {
        return validations;
    }

    public void setValidations(List<ValidationRuleProperties> validations) {
        this.validations = validations;
    }
}

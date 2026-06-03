package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.List;
import java.util.Map;

public class FieldProperties {

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

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getControlType() {
        return controlType;
    }

    public void setControlType(String controlType) {
        this.controlType = controlType;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public void setMinLength(Integer minLength) {
        this.minLength = minLength;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public Map<String, String> getPlaceholder() {
        return placeholder;
    }

    public void setPlaceholder(Map<String, String> placeholder) {
        this.placeholder = placeholder;
    }

    public String getOptionSource() {
        return optionSource;
    }

    public void setOptionSource(String optionSource) {
        this.optionSource = optionSource;
    }

    public Map<String, Object> getCopyWhenChecked() {
        return copyWhenChecked;
    }

    public void setCopyWhenChecked(Map<String, Object> copyWhenChecked) {
        this.copyWhenChecked = copyWhenChecked;
    }

    public ApimBindingProperties getApimBinding() {
        return apimBinding;
    }

    public void setApimBinding(ApimBindingProperties apimBinding) {
        this.apimBinding = apimBinding;
    }

    public List<ValidationRuleProperties> getValidations() {
        return validations;
    }

    public void setValidations(List<ValidationRuleProperties> validations) {
        this.validations = validations;
    }
}

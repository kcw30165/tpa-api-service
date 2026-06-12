package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.Map;

import java.util.LinkedHashMap;

import java.util.List;

public class PageSchemaProperties {

    private Map<String, Map<String, String>> display = new LinkedHashMap<>();


    private PageMetadataProperties metadata;
    private FormMetadataProperties form;
    private List<ValidationRuleProperties> validations;
    private ConfirmationProperties confirmation;

    public PageMetadataProperties getMetadata() {
        return metadata;
    }

    public void setMetadata(PageMetadataProperties metadata) {
        this.metadata = metadata;
    }

    public FormMetadataProperties getForm() {
        return form;
    }

    public void setForm(FormMetadataProperties form) {
        this.form = form;
    }

    public List<ValidationRuleProperties> getValidations() {
        return validations;
    }

    public void setValidations(List<ValidationRuleProperties> validations) {
        this.validations = validations;
    }

    public ConfirmationProperties getConfirmation() {
        return confirmation;
    }

    public void setConfirmation(ConfirmationProperties confirmation) {
        this.confirmation = confirmation;
    }
}

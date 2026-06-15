package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.Map;

import java.util.LinkedHashMap;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageSchemaProperties {

    private Map<String, Map<String, String>> display = new LinkedHashMap<>();


    private PageMetadataProperties metadata;
    private FormMetadataProperties form;
    private List<ValidationRuleProperties> validations;
    private ConfirmationProperties confirmation;

    public void setDisplay(Map<String, Map<String, String>> display) {
        this.display = display == null ? new LinkedHashMap<>() : display;
    }
}

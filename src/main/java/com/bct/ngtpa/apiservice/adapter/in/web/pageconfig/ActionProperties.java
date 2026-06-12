package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.Map;

public class ActionProperties {

    private String labelCode;


    private String name;
    private Map<String, String> label;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, String> getLabel() {
        return label;
    }

    public void setLabel(Map<String, String> label) {
        this.label = label;
    }


    public String getLabelCode() {
        return labelCode;
    }


    public void setLabelCode(String labelCode) {
        this.labelCode = labelCode;
    }
}

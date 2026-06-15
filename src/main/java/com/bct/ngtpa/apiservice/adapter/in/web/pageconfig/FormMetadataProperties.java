package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FormMetadataProperties {

    private String id;
    private String defaultMode;
    private Map<String, OptionSetProperties> optionSets = new LinkedHashMap<>();
    private List<ActionProperties> actions;
    private List<SectionProperties> sections;

    public void setOptionSets(Map<String, OptionSetProperties> optionSets) {
        this.optionSets = optionSets == null ? new LinkedHashMap<>() : optionSets;
    }
}

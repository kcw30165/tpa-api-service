package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.List;

public class FormMetadataProperties {

    private String id;
    private List<ActionProperties> actions;
    private List<SectionProperties> sections;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<ActionProperties> getActions() {
        return actions;
    }

    public void setActions(List<ActionProperties> actions) {
        this.actions = actions;
    }

    public List<SectionProperties> getSections() {
        return sections;
    }

    public void setSections(List<SectionProperties> sections) {
        this.sections = sections;
    }
}

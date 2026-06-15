package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.List;
import java.util.Map;

public class SectionProperties {

    private String titleCode;


    private String id;
    private Map<String, String> title;
    private List<FieldProperties> fields;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, String> getTitle() {
        return title;
    }

    public void setTitle(Map<String, String> title) {
        this.title = title;
    }

    public List<FieldProperties> getFields() {
        return fields;
    }

    public void setFields(List<FieldProperties> fields) {
        this.fields = fields;
    }


    public String getTitleCode() {
        return titleCode;
    }


    public void setTitleCode(String titleCode) {
        this.titleCode = titleCode;
    }
}

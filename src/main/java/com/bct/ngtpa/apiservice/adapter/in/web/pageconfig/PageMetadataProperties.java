package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.Map;

public class PageMetadataProperties {

    private String id;
    private Map<String, String> title;

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
}

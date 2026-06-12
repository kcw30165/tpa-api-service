package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.Map;

public class PageMetadataProperties {

    private String titleCode;


    private String id;
    private Map<String, String> title;
    private String version;

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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }


    public String getTitleCode() {
        return titleCode;
    }


    public void setTitleCode(String titleCode) {
        this.titleCode = titleCode;
    }
}

package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "bff-pages")
public class BffPagesProperties {

    private Map<String, Object> common;
    private Map<String, Object> pages;

    public Map<String, Object> getCommon() {
        return common;
    }

    public void setCommon(Map<String, Object> common) {
        this.common = common;
    }

    public Map<String, Object> getPages() {
        return pages;
    }

    public void setPages(Map<String, Object> pages) {
        this.pages = pages;
    }
}

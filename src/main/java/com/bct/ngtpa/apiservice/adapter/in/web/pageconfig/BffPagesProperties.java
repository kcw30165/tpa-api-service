package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "bff-pages")
public class BffPagesProperties {

    private CommonPageProperties common;

    // preserve insertion order for pages
    private Map<String, PageSchemaProperties> pages = new LinkedHashMap<>();

    public CommonPageProperties getCommon() {
        return common;
    }

    public void setCommon(CommonPageProperties common) {
        this.common = common;
    }

    public Map<String, PageSchemaProperties> getPages() {
        return pages;
    }

    public void setPages(Map<String, PageSchemaProperties> pages) {
        this.pages = pages;
    }
}

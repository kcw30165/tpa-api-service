package com.bct.ngtpa.apiservice.config.logging;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "logging-sanitizer")
public class LoggingSanitizerProperties {

    private List<String> sensitiveTokens = new ArrayList<>();

    public List<String> getSensitiveTokens() {
        return sensitiveTokens;
    }

    public void setSensitiveTokens(List<String> sensitiveTokens) {
        this.sensitiveTokens = sensitiveTokens == null ? new ArrayList<>() : new ArrayList<>(sensitiveTokens);
    }
}
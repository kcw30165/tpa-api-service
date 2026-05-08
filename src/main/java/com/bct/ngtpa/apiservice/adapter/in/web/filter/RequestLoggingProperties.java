package com.bct.ngtpa.apiservice.adapter.in.web.filter;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "request-logging")
public class RequestLoggingProperties {

    private boolean enabled = true;
    private boolean logHeaders = false;
    private List<String> headerAllowlist = new ArrayList<>(List.of("User-Agent", "Accept", "Content-Type"));
    private BodyLogging bodyLogging = new BodyLogging();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLogHeaders() {
        return logHeaders;
    }

    public void setLogHeaders(boolean logHeaders) {
        this.logHeaders = logHeaders;
    }

    public List<String> getHeaderAllowlist() {
        return headerAllowlist;
    }

    public void setHeaderAllowlist(List<String> headerAllowlist) {
        this.headerAllowlist = headerAllowlist == null ? new ArrayList<>() : new ArrayList<>(headerAllowlist);
    }

    public BodyLogging getBodyLogging() {
        return bodyLogging;
    }

    public void setBodyLogging(BodyLogging bodyLogging) {
        this.bodyLogging = bodyLogging == null ? new BodyLogging() : bodyLogging;
    }

    public static class BodyLogging {

        private boolean enabled = false;
        private int defaultMaxBodySizeBytes = 4096;
        private List<EndpointRule> endpoints = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getDefaultMaxBodySizeBytes() {
            return defaultMaxBodySizeBytes;
        }

        public void setDefaultMaxBodySizeBytes(int defaultMaxBodySizeBytes) {
            this.defaultMaxBodySizeBytes = defaultMaxBodySizeBytes;
        }

        public List<EndpointRule> getEndpoints() {
            return endpoints;
        }

        public void setEndpoints(List<EndpointRule> endpoints) {
            this.endpoints = endpoints == null ? new ArrayList<>() : new ArrayList<>(endpoints);
        }
    }

    public static class EndpointRule {

        private String method;
        private String pathPattern;
        private boolean logRequestBody = false;
        private boolean logResponseBody = false;
        private Integer maxBodySizeBytes;

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public String getPathPattern() {
            return pathPattern;
        }

        public void setPathPattern(String pathPattern) {
            this.pathPattern = pathPattern;
        }

        public boolean isLogRequestBody() {
            return logRequestBody;
        }

        public void setLogRequestBody(boolean logRequestBody) {
            this.logRequestBody = logRequestBody;
        }

        public boolean isLogResponseBody() {
            return logResponseBody;
        }

        public void setLogResponseBody(boolean logResponseBody) {
            this.logResponseBody = logResponseBody;
        }

        public Integer getMaxBodySizeBytes() {
            return maxBodySizeBytes;
        }

        public void setMaxBodySizeBytes(Integer maxBodySizeBytes) {
            this.maxBodySizeBytes = maxBodySizeBytes;
        }
    }
}

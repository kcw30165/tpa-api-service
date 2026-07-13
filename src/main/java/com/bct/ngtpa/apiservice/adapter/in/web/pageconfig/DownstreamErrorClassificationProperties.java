package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DownstreamErrorClassificationProperties {
    private String source;
    private String endpoint;
    private Map<String, DownstreamErrorClassificationMappingProperties> mappings = new LinkedHashMap<>();
    private DownstreamErrorClassificationMappingProperties fallback;

    public void setMappings(Map<String, DownstreamErrorClassificationMappingProperties> mappings) {
        this.mappings = mappings == null ? new LinkedHashMap<>() : mappings;
    }
}

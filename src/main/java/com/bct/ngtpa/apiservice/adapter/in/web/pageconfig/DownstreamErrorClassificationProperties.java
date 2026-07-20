package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DownstreamErrorClassificationProperties {
    private String source;
    private String endpoint;

    /**
     * Legacy map retained for existing non-registration configuration. Dotted
     * downstream codes must use explicitMappings because Spring property binding
     * does not preserve dotted map keys reliably.
     */
    private Map<String, DownstreamErrorClassificationMappingProperties> mappings = new LinkedHashMap<>();
    private List<DownstreamErrorClassificationMappingProperties> explicitMappings = new ArrayList<>();
    private DownstreamErrorClassificationMappingProperties fallback;

    public void setMappings(Map<String, DownstreamErrorClassificationMappingProperties> mappings) {
        this.mappings = mappings == null ? new LinkedHashMap<>() : new LinkedHashMap<>(mappings);
    }

    public void setExplicitMappings(List<DownstreamErrorClassificationMappingProperties> explicitMappings) {
        this.explicitMappings = explicitMappings == null ? new ArrayList<>() : new ArrayList<>(explicitMappings);
    }
}

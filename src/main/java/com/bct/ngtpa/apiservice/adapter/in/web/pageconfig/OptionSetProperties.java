package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OptionSetProperties {
    private String code;
    private Map<String, List<OptionProperties>> options = new LinkedHashMap<>();

    public void setOptions(Map<String, List<OptionProperties>> options) {
        this.options = options == null ? new LinkedHashMap<>() : options;
    }
}

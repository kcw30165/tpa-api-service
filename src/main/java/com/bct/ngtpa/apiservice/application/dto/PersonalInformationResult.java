package com.bct.ngtpa.apiservice.application.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public record PersonalInformationResult(
        Map<String, Object> data,
        Map<String, String> config,
        Map<String, MemberInfoConfigItem> configItems
) {
    public PersonalInformationResult {
        data = data == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(data));
        config = config == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(config));
        configItems = configItems == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(configItems));
    }

    public PersonalInformationResult(Map<String, Object> data, Map<String, String> config) {
        this(data, config, Map.of());
    }
}
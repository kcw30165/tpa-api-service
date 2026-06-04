package com.bct.ngtpa.apiservice.application.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public record PersonalInformationResult(
        Map<String, Object> data,
        Map<String, String> config
) {
    public PersonalInformationResult {
        data = data == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(data));
        config = config == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(config));
    }
}

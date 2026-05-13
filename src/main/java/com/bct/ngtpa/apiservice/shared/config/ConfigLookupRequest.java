package com.bct.ngtpa.apiservice.shared.config;

import java.util.Objects;

public record ConfigLookupRequest(
        ConfigCategory category,
        String code,
        ConfigLookupContext context,
        boolean required) {

    public ConfigLookupRequest {
        category = Objects.requireNonNull(category, "category must not be null");
        context = Objects.requireNonNull(context, "context must not be null");
        code = normalizeCode(code);
    }

    public static ConfigLookupRequest optional(ConfigCategory category, String code, ConfigLookupContext context) {
        return new ConfigLookupRequest(category, code, context, false);
    }

    public static ConfigLookupRequest required(ConfigCategory category, String code, ConfigLookupContext context) {
        return new ConfigLookupRequest(category, code, context, true);
    }

    private static String normalizeCode(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}